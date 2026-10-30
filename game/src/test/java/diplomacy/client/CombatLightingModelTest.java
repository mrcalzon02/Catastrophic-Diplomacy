package diplomacy.client;

import diplomacy.sim.Battle;

/** No GPU context required: render state must remain bounded and simulation-owned. */
public final class CombatLightingModelTest {
    private static void require(boolean result, String why) {
        if (!result) throw new AssertionError(why);
    }

    private static Battle scenario(boolean missile) {
        Battle battle = new Battle(missile ? 5151 : 5152);
        Battle.Ship attacker = battle.addShip(0, Battle.Faction.TERRAN, 200, 350);
        Battle.Ship target = battle.addShip(1, Battle.Faction.CARNELIAN, 490, 350);
        attacker.speed = 0;
        target.speed = 0;
        attacker.pdRange = 0;
        target.pdRange = 0;
        target.laserDamage = 0;
        target.missileDamage = 0;
        attacker.laserDamage = missile ? 0 : 18;
        attacker.missileDamage = missile ? 42 : 0;
        attacker.missileAmmo = missile ? 1 : 0;
        attacker.kineticAmmo = 0;
        attacker.torpedoAmmo = 0;
        attacker.kineticDamage = 0;
        attacker.plasmaDamage = 0;
        attacker.particleDamage = 0;
        attacker.torpedoDamage = 0;
        target.missileAmmo = 0;
        target.kineticAmmo = 0;
        target.kineticDamage = 0;
        target.plasmaDamage = 0;
        target.particleDamage = 0;
        target.torpedoDamage = 0;
        attacker.accuracy = 1.8;
        target.maxHull = 99999;
        target.hull = 99999;
        return battle;
    }

    public static void main(String[] args) {
        Battle missileBattle = scenario(true);
        CombatLightingModel lighting = new CombatLightingModel();
        int maxSmoke = 0;
        boolean sawIgnition = false;
        boolean sawFlash = false;
        boolean wasLive = false;
        boolean sawLingeringSmoke = false;
        for (int i = 0; i < 240; i++) {
            missileBattle.step();
            lighting.update(missileBattle);
            int count = lighting.smokeCount();
            lighting.update(missileBattle); // two render frames in one sim tick
            require(count == lighting.smokeCount(),
                    "paused/repeated rendered frames cannot generate new smoke");
            require(count <= CombatLightingModel.MAX_SMOKE,
                    "smoke budget must remain bounded");
            require(lighting.flashCount() <= CombatLightingModel.MAX_FLASHES,
                    "event flashes must remain bounded");
            if (count > 0) sawIgnition = true;
            if (lighting.flashCount() > 0) sawFlash = true;
            if (!missileBattle.missiles().isEmpty()) wasLive = true;
            else if (wasLive && count > 0) sawLingeringSmoke = true;
            maxSmoke = Math.max(maxSmoke, count);
        }
        require(sawIgnition && maxSmoke > 0,
                "live ignited missile must leave visual exhaust");
        require(sawFlash, "real weapon fire or impact must illuminate the scene");
        require(sawLingeringSmoke, "smoke must linger briefly after projectile is removed");
        require(lighting.smokeCount() == 0 && lighting.flashCount() == 0,
                "all cosmetic effects must expire on simulation ticks");

        Battle lasers = scenario(false);
        for (int i = 0; i < 100; i++) {
            lasers.step();
            lighting.update(lasers);
        }
        require(lighting.smokeCount() == 0,
                "beam-only engagements must not invent missile smoke");
        require(CombatLightingModel.flashLife(new Battle.VisualEvent(
                1, 1, 0, Battle.VisualPhase.TIMEOUT, "MISSILE",
                0, 1, 2, 100, 100, 200, 200)) == 0,
                "timeout is not a fabricated explosion");
        require(lighting.smokeCount() <= CombatLightingModel.MAX_SMOKE,
                "switching engagements must discard stale exhaust");
        System.out.println("PASS combat lighting: event correlation, missile smoke, expiration, pause and budgets");
    }
}
