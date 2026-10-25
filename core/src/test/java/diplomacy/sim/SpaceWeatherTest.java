package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/** JVM-only space weather acceptance; no device/GPU assumptions. */
public final class SpaceWeatherTest {
    private static final class Vessel implements SpaceWeather.Target {
        final int id; double x,y;
        Vessel(int id,double x,double y){this.id=id;this.x=x;this.y=y;}
        public int weatherId(){return id;}
        public double weatherX(){return x;}
        public double weatherY(){return y;}
        public boolean alive(){return true;}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static long fingerprint(SpaceWeather weather,List<Vessel> vessels,int ticks){
        long hash=0xcbf29ce484222325L;
        for(int t=1;t<=ticks;t++)for(var impact:weather.advance(t,vessels)){
            hash=(hash^impact.targetId())*0x100000001b3L;
            hash=(hash^Double.doubleToLongBits(impact.damage()))*0x100000001b3L;
            hash=(hash^impact.type().ordinal())*0x100000001b3L;
        }
        return hash^weather.meteorHits()^((long)weather.meteorMisses()<<32);
    }
    public static void main(String[] args){
        var vessels=new ArrayList<Vessel>();
        for(int i=0;i<20;i++)vessels.add(new Vessel(i+1,500+(i%5)*18,260+(i/5)*18));
        var types=java.util.EnumSet.noneOf(SpaceWeather.Kind.class);
        for(int i=1;i<=500;i++){
            var a=SpaceWeather.forLevel(i);
            var b=SpaceWeather.forLevel(i);
            check(a.equals(b)&&a.level()==i&&a.severity()>0,"Stage determinism "+i);
            types.add(a.primary());
        }
        check(types.size()==6,"Six distinct weather families");
        check(SpaceWeather.forLevel(1).primary()==SpaceWeather.Kind.ASTEROIDS,"Asteroids showcase first");
        var clear=new SpaceWeather(SpaceWeather.clear());
        check(clear.sensorFactor()==1&&clear.accuracyFactor()==1,"Clear preserves baseline");
        fingerprint(clear,vessels,1200);
        check(clear.impacts().isEmpty()&&clear.meteors().isEmpty(),"No hazards in clear");
        for(var type:SpaceWeather.Kind.values()){
            var preset=new SpaceWeather.Preset(11,1234,type,SpaceWeather.Kind.CLEAR,1);
            var a=new SpaceWeather(preset);
            var b=new SpaceWeather(preset);
            check(fingerprint(a,vessels,2400)==fingerprint(b,vessels,2400),"Determinism "+type);
            if(type==SpaceWeather.Kind.ASTEROIDS)
                check(a.meteorHits()>0&&a.meteorMisses()>0,"Asteroids hit and miss empty space");
            if(type==SpaceWeather.Kind.NEBULA)
                check(a.sensorFactor()<0.5&&a.accuracyFactor()<0.8,"Nebula sensor and aim penalties");
            if(type==SpaceWeather.Kind.RADIATION)check(a.irradiationPulses()>0,"Radiation pulses");
            if(type==SpaceWeather.Kind.NEUTRON_SWEEP)check(a.neutronHits()>0,"Rotating neutron beam");
        }
        var flare=new SpaceWeather(new SpaceWeather.Preset(4,5,SpaceWeather.Kind.SOLAR_FLARE,SpaceWeather.Kind.CLEAR,1));
        for(int t=1;t<=330;t++)flare.advance(t,vessels);
        check(flare.solarActive()&&flare.heatPerSecond()>0&&flare.coolingFactor()<1,"Flare cycle and heat");
        var ion=new SpaceWeather(new SpaceWeather.Preset(6,5,SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.CLEAR,1));
        check(ion.powerDrainPerSecond()>0&&ion.guidanceFactor()<1,"Ion power and guidance disruption");
        boolean rejected=false;
        try{ion.advance(3,vessels);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Weather must reject skipped simulation ticks");
        System.out.println("PASS 500 weather presets, seeded hazards, asteroid hits/misses and radiation effects");
    }
}
