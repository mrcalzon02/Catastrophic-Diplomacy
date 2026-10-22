package diplomacy.sim;

/** Runs without junit, rendering drivers or downloaded dependencies. */
public final class BattleSmokeTest {
    private static void require(boolean predicate, String message) {
        if (!predicate) throw new AssertionError(message);
    }
    private static Battle trial(long seed) {
        Battle b = Battle.duel(seed, 16, Battle.Faction.TERRAN, Battle.Faction.DRAXIAN);
        for (int i = 0; i < 2400 && !b.finished(); i++) b.step();
        return b;
    }
    public static void main(String[] args) {
        Battle first = trial(2479L), again = trial(2479L), altered = trial(2480L);
        require(first.tick == again.tick, "tick determinism");
        require(first.fingerprint() == again.fingerprint(), "same seed must give same battle state");
        require(first.fingerprint() != altered.fingerprint(), "different seeds should differ");
        require(first.hitCount > 0, "guns must land hits");
        require(first.ships().stream().anyMatch(s -> s.launched > 0), "missiles must launch");
        require(first.ships().stream().anyMatch(s -> s.shields < s.maxShields || !s.alive()), "shields must absorb fire");
        require(first.ships().stream().anyMatch(s -> s.hull < s.maxHull), "component damage must reach hull");
        for (Battle.Ship s : first.ships()) {
            require(Double.isFinite(s.x) && Double.isFinite(s.y), "finite locations");
            require(s.hull >= 0 && s.power >= 0 && s.shields >= 0 && s.armor >= 0, "nonnegative components");
        }
        boolean distinct = false;
        for (Battle.Faction faction : Battle.Faction.values()) {
            Battle b = Battle.duel(17L, 1, faction, Battle.Faction.TERRAN);
            distinct |= Math.abs(b.ships().get(0).maxHull - 195) > 0.01 || Math.abs(b.ships().get(0).maxShields - 95) > 0.01;
        }
        require(Battle.Faction.values().length == 6 && distinct, "six materially distinct factions");
        System.out.println("PASS deterministic battles | ticks=" + first.tick
                + " hits=" + first.hitCount + " intercepts=" + first.missileIntercepts
                + " destroyed=" + first.destructions + " fingerprint=" + Long.toHexString(first.fingerprint()));
    }
}
