package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Render-only combat light/exhaust timeline. Samples authoritative battle poses;
 * never mutates ships, projectiles, ammunition, RNG, or combat time.
 */
final class CombatLightingModel {
    static final int SMOKE_LIFETIME_TICKS = 36;
    static final int MAX_SMOKE = 640;
    static final int MAX_FLASHES = 384;
    static final int MAX_TRACKED_MISSILES = 192;

    record Smoke(float x, float y, long bornTick, boolean heavy) { }
    record Flash(Battle.VisualEvent event) { }
    private record Position(float x, float y) { }

    private final ArrayDeque<Smoke> smoke = new ArrayDeque<>();
    private final ArrayDeque<Flash> flashes = new ArrayDeque<>();
    private Map<Long, Position> previousMissiles = new HashMap<>();
    private Battle trackedBattle;
    private long lastTick = -1;
    private long lastSequence;

    Iterable<Smoke> smoke() { return smoke; }
    Iterable<Flash> flashes() { return flashes; }
    int smokeCount() { return smoke.size(); }
    int flashCount() { return flashes.size(); }

    private void reset(Battle battle) {
        trackedBattle = battle;
        lastTick = -1;
        lastSequence = 0;
        smoke.clear();
        flashes.clear();
        previousMissiles.clear();
    }

    static int flashLife(Battle.VisualEvent event) {
        if (event.phase() == Battle.VisualPhase.TIMEOUT) return 0;
        if (event.phase() == Battle.VisualPhase.INTERCEPT) return 5;
        if (event.phase() == Battle.VisualPhase.IMPACT) return 9;
        return event.weapon().equals("KINETIC") ? 8 : 6;
    }

    void update(Battle battle) {
        if (battle != trackedBattle || (battle != null && battle.tick < lastTick))
            reset(battle);
        if (battle == null || battle.tick == lastTick) return;

        // The event ring is sequence-numbered; render catch-up never repeats a flash.
        for (Battle.VisualEvent event : battle.visualEvents()) {
            if (event.sequence() <= lastSequence) continue;
            lastSequence = event.sequence();
            int life = flashLife(event);
            if (life > 0 && battle.tick >= event.tick()
                    && battle.tick - event.tick() < life)
                flashes.addLast(new Flash(event));
        }
        while (!flashes.isEmpty()
                && battle.tick - flashes.peekFirst().event().tick()
                   >= flashLife(flashes.peekFirst().event()))
            flashes.removeFirst();
        while (flashes.size() > MAX_FLASHES) flashes.removeFirst();

        // Smoke is intentionally a stylized non-physical visual. The source is
        // ALWAYS a live, ignited, simulation-owned missile (never a fake tween).
        Map<Long, Position> current = new HashMap<>();
        for (Battle.Missile missile : battle.missiles()) {
            if (current.size() >= MAX_TRACKED_MISSILES) break;
            float x = (float) missile.x, y = (float) missile.y;
            Position previous = previousMissiles.get(missile.visualId);
            current.put(missile.visualId, new Position(x, y));
            if (missile.ignitionDelayTicks > 0 || missile.fuel <= 0
                    || (battle.tick & 1) != 0) continue;
            float velocityLength = (float) StrictMath.hypot(missile.vx, missile.vy);
            float ux = velocityLength > 0.0001f ? (float) missile.vx / velocityLength : 1;
            float uy = velocityLength > 0.0001f ? (float) missile.vy / velocityLength : 0;
            float tail = missile.torpedo ? 10 : 6;
            float tx = x - ux * tail, ty = y - uy * tail;
            if (previous == null) {
                smoke.addLast(new Smoke(tx, ty, battle.tick, missile.torpedo));
            } else {
                float px = previous.x() - ux * tail;
                float py = previous.y() - uy * tail;
                float distance = (float) StrictMath.hypot(tx - px, ty - py);
                // Catch-up frames interpolate only the REAL last/current positions.
                // No unbounded particles when the renderer misses many sim ticks.
                int steps = Math.min(3, Math.max(1, (int) (distance / 9)));
                for (int step = 1; step <= steps; step++) {
                    float f = step / (float) steps;
                    smoke.addLast(new Smoke(px + (tx - px) * f,
                            py + (ty - py) * f, battle.tick, missile.torpedo));
                }
            }
        }
        previousMissiles = current;
        while (!smoke.isEmpty()
                && battle.tick - smoke.peekFirst().bornTick() >= SMOKE_LIFETIME_TICKS)
            smoke.removeFirst();
        while (smoke.size() > MAX_SMOKE) smoke.removeFirst();
        lastTick = battle.tick;
    }
}
