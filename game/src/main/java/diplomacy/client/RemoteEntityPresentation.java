package diplomacy.client;

import diplomacy.net.BattleConnection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Display positions are bounded by accepted host snapshots. All other state comes from the newest one. */
final class RemoteEntityPresentation {
    record Body<T extends BattleConnection.Entity>(T entity, double startX, double startY) {
        float x(float fraction) {
            return (float)(fraction == 1f ? entity.x() : startX + (entity.x() - startX) * fraction);
        }

        float y(float fraction) {
            return (float)(fraction == 1f ? entity.y() : startY + (entity.y() - startY) * fraction);
        }
    }

    record Frame(List<BattleConnection.ShipEntity> ships,
                 List<Body<BattleConnection.MissileEntity>> missiles,
                 List<Body<BattleConnection.BallisticEntity>> ballistics,
                 List<Body<BattleConnection.CraftEntity>> crafts) {}

    private BattleConnection.Snapshot last;
    private Frame frame;
    private Map<BattleConnection.EntityKind, Map<Long, Body<?>>> tracks = Map.of();

    Frame observe(BattleConnection.Snapshot snapshot, float previousFraction) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot required");
        if (snapshot == last) return frame;
        boolean sameSession = last != null && last.sessionId().equals(snapshot.sessionId());
        // A same-tick replacement uses the existing interval and origin.
        if (sameSession && last.tick() == snapshot.tick()) previousFraction = 0f;
        if (!sameSession || snapshot.tick() < last.tick()) tracks = Map.of();

        var ships = new ArrayList<BattleConnection.ShipEntity>();
        var missiles = new ArrayList<Body<BattleConnection.MissileEntity>>();
        var ballistics = new ArrayList<Body<BattleConnection.BallisticEntity>>();
        var crafts = new ArrayList<Body<BattleConnection.CraftEntity>>();
        var next = new EnumMap<BattleConnection.EntityKind, Map<Long, Body<?>>>(BattleConnection.EntityKind.class);
        for (var row : snapshot.decodedEntities()) {
            if (row instanceof BattleConnection.ShipEntity ship) ships.add(ship);
            else if (row instanceof BattleConnection.MissileEntity missile) missiles.add(track(missile, previousFraction, next));
            else if (row instanceof BattleConnection.BallisticEntity shell) ballistics.add(track(shell, previousFraction, next));
            else if (row instanceof BattleConnection.CraftEntity craft) crafts.add(track(craft, previousFraction, next));
            else throw new IllegalArgumentException("Unknown host entity");
        }
        tracks = next;
        last = snapshot;
        frame = new Frame(List.copyOf(ships), List.copyOf(missiles), List.copyOf(ballistics), List.copyOf(crafts));
        return frame;
    }

    private <T extends BattleConnection.Entity> Body<T> track(T entity, float fraction,
            Map<BattleConnection.EntityKind, Map<Long, Body<?>>> next) {
        var previousKind = tracks.get(entity.kind());
        var previous = previousKind == null ? null : previousKind.get(entity.id());
        var motion = new Body<>(entity,
                previous == null ? entity.x() : previous.x(fraction),
                previous == null ? entity.y() : previous.y(fraction));
        next.computeIfAbsent(entity.kind(), ignored -> new HashMap<>()).put(entity.id(), motion);
        return motion;
    }
}
