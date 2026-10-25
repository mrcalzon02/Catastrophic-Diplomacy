package diplomacy.sim;

/**
 * Visual-only, deterministic sky recipe for each of the 500 campaign levels.
 * Consumes no battle RNG, file assets, renderer objects or mutable global state.
 */
public final class LevelSky {
    public enum Formation { NEBULA, DUST_LANE, GALAXY, DEEP_VOID, STAR_CLUSTER, ION_STORM }
    public record Tint(float red, float green, float blue) {
        public Tint {
            if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)
                    || red < 0 || red > 1 || green < 0 || green > 1 || blue < 0 || blue > 1)
                throw new IllegalArgumentException("Sky tint must be in [0,1]");
        }
    }
    public record Recipe(int level, int sector, long seed, Formation formation,
                         Tint primary, Tint secondary, float angle,
                         float nebulaIntensity, float starDensity, float cloudScale) { }

    private static final long SKY_SALT = 0x534b594649454c44L;
    private LevelSky() { }

    public static Recipe forLevel(int index) {
        LevelEconomy.Level level = LevelEconomy.level(index);
        long seed = mix(level.seed() ^ SKY_SALT);
        // Sectors keep a recognizable visual identity; individual stages vary independently.
        Tint[] palette = palette(level.sector());
        Formation formation = Formation.values()[(int)(unit(mix(seed + 1)) * Formation.values().length)];
        float angle = (float)(unit(mix(seed + 2)) * (StrictMath.PI * 2));
        float opacity = 0.29f + (float)unit(mix(seed + 3)) * 0.35f;
        if (formation == Formation.DEEP_VOID) opacity *= 0.28f;
        if (formation == Formation.GALAXY) opacity *= 0.85f;
        float density = 0.7f + (float)unit(mix(seed + 4)) * 0.85f;
        if (formation == Formation.STAR_CLUSTER) density *= 1.55f;
        float scale = 0.65f + (float)unit(mix(seed + 5)) * 0.7f;
        float hueJitter = 0.89f + (float)unit(mix(seed + 6)) * 0.22f;
        return new Recipe(index, level.sector(), seed, formation,
                tint(palette[0], hueJitter), tint(palette[1], 2f - hueJitter),
                angle, opacity, density, scale);
    }

    private static Tint tint(Tint c, float value) {
        return new Tint(Math.min(1, c.red() * value), Math.min(1, c.green() * value),
                Math.min(1, c.blue() * value));
    }

    private static Tint[] palette(int sector) {
        return switch (sector) {
            case 1 -> new Tint[]{new Tint(.16f,.39f,.95f),new Tint(.68f,.21f,.77f)};
            case 2 -> new Tint[]{new Tint(.97f,.30f,.11f),new Tint(.97f,.67f,.19f)};
            case 3 -> new Tint[]{new Tint(.10f,.80f,.84f),new Tint(.15f,.46f,.91f)};
            case 4 -> new Tint[]{new Tint(.69f,.26f,.95f),new Tint(.93f,.40f,.63f)};
            case 5 -> new Tint[]{new Tint(.45f,.77f,.24f),new Tint(.88f,.74f,.21f)};
            case 6 -> new Tint[]{new Tint(.94f,.15f,.26f),new Tint(.88f,.42f,.18f)};
            case 7 -> new Tint[]{new Tint(.64f,.77f,.98f),new Tint(.25f,.52f,.95f)};
            case 8 -> new Tint[]{new Tint(.15f,.77f,.52f),new Tint(.24f,.84f,.87f)};
            case 9 -> new Tint[]{new Tint(.98f,.64f,.28f),new Tint(.69f,.34f,.94f)};
            case 10 -> new Tint[]{new Tint(.30f,.45f,.98f),new Tint(.95f,.72f,.40f)};
            default -> throw new IllegalArgumentException("Unknown sky sector: " + sector);
        };
    }

    /** SplitMix64 finalizer, used only for deterministic visual decoration. */
    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    public static double unit(long bits) { return (bits >>> 11) * 0x1.0p-53; }
}
