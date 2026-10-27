package diplomacy.sim;

/** Ensures every selectable level binds its real sky to its real battle conditions. */
public final class LevelEnvironmentTest {
    private static void require(boolean ok,String failure){if(!ok)throw new AssertionError(failure);}
    public static void main(String[] args) {
        for(int i=1;i<=500;i++) {
            LevelEnvironment.Stage a=LevelEnvironment.forLevel(i),b=LevelEnvironment.forLevel(i);
            require(a.equals(b),"nonrepeatable level "+i);
            require(a.level()==i&&a.sky().level()==i&&a.weather().level()==i,
                    "cross-wired background/weather for level "+i);
            require(a.weather().enabled(),"missing environmental hazard at level "+i);
        }
        boolean low=false,high=false;
        try { LevelEnvironment.forLevel(0); } catch(IllegalArgumentException expected){low=true;}
        try { LevelEnvironment.forLevel(501); } catch(IllegalArgumentException expected){high=true;}
        require(low&&high,"must reject out-of-range level indices");
        System.out.println("PASS all 500 level backgrounds linked to weather simulation");
    }
}
