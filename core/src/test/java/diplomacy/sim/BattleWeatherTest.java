package diplomacy.sim;

/** Headless tests proving that weather reaches the actual Battle mechanics. */
public final class BattleWeatherTest {
    private static void check(boolean value,String why) {
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        var clear=Battle.duel(3781L,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        var nebula=Battle.duel(3781L,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        nebula.configureWeather(new SpaceWeather.Preset(2,991,SpaceWeather.Kind.NEBULA,
                SpaceWeather.Kind.CLEAR,1));
        clear.step();
        nebula.step();
        check(clear.missiles().size()>0,"Clear visibility permits first-volley missiles");
        check(nebula.missiles().isEmpty(),"Nebula blocks initial target acquisition");
        check(nebula.weather().sensorFactor()<1&&nebula.weather().accuracyFactor()<1,
                "Weather modifies sensors and aim");
        // Repeated encounters with identical weather and seeds must be bit-for-bit reproducible.
        var one=Battle.duel(478L,2,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        var two=Battle.duel(478L,2,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        var asteroid=new SpaceWeather.Preset(1,4567,SpaceWeather.Kind.ASTEROIDS,
                SpaceWeather.Kind.CLEAR,1);
        one.configureWeather(asteroid);
        two.configureWeather(asteroid);
        for(int i=0;i<900;i++){one.step();two.step();}
        check(one.fingerprint()==two.fingerprint(),"Weather battle replay must be deterministic");
        check(one.weather().meteorMisses()>0,"Asteroids can harmlessly strike empty space");
        // Test passive damage and thermal pressure without enemy fire or AI target differences.
        var flare=new Battle(21L);
        var exposed=flare.addShip(0,Battle.Faction.CARNELIAN,580,330);
        flare.configureWeather(new SpaceWeather.Preset(3,88,SpaceWeather.Kind.SOLAR_FLARE,
                SpaceWeather.Kind.CLEAR,1));
        for(int i=0;i<375;i++)flare.step();
        check(flare.environmentalHits>0,"Flares cause actual directed shield/armor impacts");
        check(exposed.heat>0,"Flares cause heat buildup through the ship thermal model");
        var radiation=new Battle(91L);
        radiation.addShip(0,Battle.Faction.CARNELIAN,580,330);
        radiation.configureWeather(new SpaceWeather.Preset(5,99,SpaceWeather.Kind.RADIATION,
                SpaceWeather.Kind.CLEAR,1));
        for(int i=0;i<150;i++)radiation.step();
        check(radiation.environmentalHits>0,"Radiation reaches actual damage pipeline");
        var plain=new Battle(91L);
        plain.addShip(0,Battle.Faction.CARNELIAN,580,330);
        for(int i=0;i<150;i++)plain.step();
        check(plain.environmentalHits==0,"Clear weather is a backward-compatible default");
        boolean denied=false;
        try{radiation.configureWeather(SpaceWeather.clear());}
        catch(IllegalStateException expected){denied=true;}
        check(denied,"Weather cannot be silently swapped during an active simulation");
        System.out.println("PASS environmental targeting, battle damage, thermal effects and replay");
    }
}
