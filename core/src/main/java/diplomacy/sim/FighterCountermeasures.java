package diplomacy.sim;

/**
 * Fighter-owned finite flare/chaff cartridges. Radar echoes and infrared
 * emissions remain different spectral targets. These are independently
 * positioned, degrading physical emissions, never fabricated enemy hulls.
 */
public final class FighterCountermeasures {
    private FighterCountermeasures(){}
    public enum Kind { FLARE, CHAFF }
    public record Loadout(int flares,int chaff,int cooldownTicks){
        public Loadout{
            if(flares<0||flares>24||chaff<0||chaff>24||
                    cooldownTicks<8||cooldownTicks>90)
                throw new IllegalArgumentException("Invalid fighter magazines");
        }
    }
    /** Research from a physically fitted FTR hangar stage improves the
     * carried defensive cartridges without changing fighter identity. */
    public static Loadout forHull(FighterModel.Hull hull,int tier){
        if(hull==null||tier<0||tier>9)
            throw new IllegalArgumentException("Unknown fighter dispenser fitting");
        int flare,chaff;
        switch(hull){
            case INTERCEPTOR -> {flare=4;chaff=3;}
            case ASSAULT_GUNSHIP -> {flare=3;chaff=5;}
            case LASER_FIGHTER,PARTICLE_FIGHTER -> {flare=4;chaff=4;}
            case MISSILE_FIGHTER -> {flare=5;chaff=5;}
            case TORPEDO_BOMBER -> {flare=7;chaff=8;}
            default -> throw new IllegalArgumentException("Unknown fighter model");
        }
        int upgrade=tier/3;
        return new Loadout(flare+upgrade,chaff+upgrade,
                StrictMath.max(12,24-tier));
    }
    /** One emitted cartridge retains its initial world velocity after
     * release; future carrier/fighter position cannot drag it around. */
    public record Cloud(long id,Kind kind,int craftId,int sourceId,int team,
                        double x,double y,double vx,double vy,
                        long bornTick,int lifetimeTicks,double intensity){
        public Cloud{
            if(id<=0||kind==null||craftId<=0||sourceId<=0||
                    (team!=0&&team!=1)||bornTick<0||
                    lifetimeTicks<1||lifetimeTicks>150||
                    !Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(intensity)||intensity<=0)
                throw new IllegalArgumentException("Invalid real countermeasure");
        }
        public boolean active(long tick){
            return tick>=bornTick&&tick-bornTick<lifetimeTicks;
        }
        public double xAt(long tick){
            return x+vx*Battle.DT*StrictMath.max(0,tick-bornTick);
        }
        public double yAt(long tick){
            return y+vy*Battle.DT*StrictMath.max(0,tick-bornTick);
        }
        public double signal(Kind band,long tick){
            if(band==null)throw new IllegalArgumentException("Missing sensing band");
            if(kind!=band||!active(tick))return 0;
            double remaining=1-(tick-bornTick)/(double)lifetimeTicks;
            return intensity*remaining*remaining;
        }
        public boolean spoofsRadar(int fighterId,double fighterX,double fighterY,
                                   double trueCrossSection,long tick){
            if(fighterId!=craftId||trueCrossSection<0||
                    signal(Kind.CHAFF,tick)<1.25*trueCrossSection)return false;
            return StrictMath.hypot(xAt(tick)-fighterX,
                    yAt(tick)-fighterY)<=95;
        }
    }
    public static Cloud release(long id,Kind kind,int craftId,int sourceId,
                                int team,double aircraftX,double aircraftY,
                                double aircraftVX,double aircraftVY,
                                double threatX,double threatY,
                                long tick,int tier){
        if(kind==null||!Double.isFinite(threatX)||!Double.isFinite(threatY)||
                !Double.isFinite(aircraftX)||!Double.isFinite(aircraftY)||
                !Double.isFinite(aircraftVX)||!Double.isFinite(aircraftVY)||
                tick<0||tier<0||tier>9)
            throw new IllegalArgumentException("Invalid cartridge launch");
        double ax=aircraftX-threatX,ay=aircraftY-threatY;
        double n=StrictMath.hypot(ax,ay);
        if(n<=1e-9){ax=1;ay=0;}
        else{ax/=n;ay/=n;}
        double side=(id&1)==0?1:-1;
        double px=-side*ay,py=side*ax;
        double offset=kind==Kind.CHAFF?24:29;
        return new Cloud(id,kind,craftId,sourceId,team,
                aircraftX+ax*offset+px*8,aircraftY+ay*offset+py*8,
                aircraftVX*.28+ax*15+px*13,
                aircraftVY*.28+ay*15+py*13,
                tick,kind==Kind.CHAFF?66:48,
                (kind==Kind.CHAFF?7.5:11)*(1+.06*tier));
    }
}
