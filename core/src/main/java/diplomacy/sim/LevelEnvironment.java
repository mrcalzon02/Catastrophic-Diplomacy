package diplomacy.sim;

/** Single campaign index controls both visual and gameplay environments. */
public final class LevelEnvironment {
    private LevelEnvironment() { }
    public record Stage(int level, LevelSky.Recipe sky, SpaceWeather.Preset weather) { }
    public static Stage forLevel(int index) {
        LevelEconomy.level(index); // use the canonical 1..500 bounds, never infer from UI.
        return new Stage(index, LevelSky.forLevel(index), SpaceWeather.forLevel(index));
    }
}
