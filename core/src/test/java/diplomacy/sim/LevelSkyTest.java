package diplomacy.sim;

import java.util.HashSet;
import java.util.Set;

/** JVM-only tests: recipe stability and coverage, not GPU acceptance. */
public final class LevelSkyTest {
    private static void check(boolean yes, String why) { if (!yes) throw new AssertionError(why); }
    public static void main(String[] args) {
        Set<Long> seeds = new HashSet<>();
        Set<LevelSky.Formation> formations = new HashSet<>();
        Set<LevelSky.Tint> sectors = new HashSet<>();
        for (int i = 1; i <= 500; i++) {
            LevelSky.Recipe a = LevelSky.forLevel(i);
            LevelSky.Recipe b = LevelSky.forLevel(i);
            check(a.equals(b), "Recipe changed on replay " + i);
            check(a.level() == i && a.sector() == (i - 1) / 50 + 1, "Wrong sector " + i);
            check(a.seed() != LevelEconomy.level(i).seed(), "Sky must use its own seed " + i);
            check(seeds.add(a.seed()), "Duplicate sky seed " + i);
            check(Float.isFinite(a.angle()) && a.angle() >= 0 && a.angle() < 6.284f, "Angle " + i);
            check(a.nebulaIntensity() >= 0 && a.nebulaIntensity() <= 1, "Opacity " + i);
            check(a.starDensity() > 0 && a.cloudScale() > 0, "Invalid scaling " + i);
            formations.add(a.formation());
            if ((i - 1) % 50 == 0) sectors.add(a.primary());
        }
        check(seeds.size() == 500, "500 distinct visual seeds required");
        check(sectors.size() == 10, "Distinct palettes for all sectors required");
        check(formations.size() == LevelSky.Formation.values().length, "All formations must be reachable");
        for (int bad : new int[]{-1, 0, 501, Integer.MAX_VALUE}) {
            boolean threw = false;
            try { LevelSky.forLevel(bad); } catch (IllegalArgumentException expected) { threw = true; }
            check(threw, "Out of range level accepted: " + bad);
        }
        System.out.println("PASS 500 reproducible and distinct sky recipes, sector palettes and limits");
    }
}
