package diplomacy.sim;

import diplomacy.shipyard.Shipyard;

/**
 * Cheap unguided rocket batteries: finite physical rounds, high-visibility
 * low-integrity bodies, no seeker, target relock, or midflight course change.
 * Their ONLY steering information is a launch-time inertial firing solution.
 *
 * Size controls damage, magazine and mount costs. Count is an independent
 * 4/8/16 volley selection, and motor is BOOST_COAST or SUSTAINED_BURN.
 */
public final class RocketBarrage {
    private RocketBarrage() { }

    public enum Size {
        SMALL("RKS",.68,500,.70,1.05,48,11,175,1100),
        MEDIUM("RKM",1.05,465,.90,1.18,48,15,160,1280),
        LARGE("RKL",1.65,430,1.05,1.34,32,21,145,1480),
        SIEGE("RKG",2.75,380,1.35,1.55,32,29,125,1680);
        public final String code;
        public final double damageMultiplier,terminalSpeed,health,signature;
        public final int magazine;
        public final double reloadSeconds,sustainedThrust,boostThrust;
        Size(String id,double damage,double speed,double hp,double emissions,
             int rounds,double seconds,double sustained,double boost) {
            code=id;damageMultiplier=damage;terminalSpeed=speed;health=hp;
            signature=emissions;magazine=rounds;reloadSeconds=seconds;
            sustainedThrust=sustained;boostThrust=boost;
        }
        public static Size forCode(String code){
            for(Size value:values())if(value.code.equals(code))return value;
            return null;
        }
    }
    public enum Motor { BOOST_COAST, SUSTAINED_BURN }

    /** Mounted hardware and finite magazines belong to one fitted weapon slot. */
    public static final class Rack {
        public final int slot;
        public final Size size;
        public final ProjectileModel.Spec body;
        public final double damage, reloadSeconds, magazineUnitCost;
        /** Existing campaign kinetic resupply costs 12 credits per unit.
         * Unlike a 55-credit seeker missile, rockets spend 1-4 simple units. */
        public final int cheapAmmoUnitsPerRocket;
        public int rounds,salvoCount,salvoRemaining;
        public double reload;
        public Motor motor;
        public long nextLaunchTick;
        public int intendedTarget;
        public double lockedBearing;
        public Rack(int slot,Size size,double output,double integrity,double tracking) {
            if(slot<0||size==null||!Double.isFinite(output)||output<=0
                    ||!Double.isFinite(integrity)||integrity<=0
                    ||!Double.isFinite(tracking)||tracking<=0)
                throw new IllegalArgumentException("Invalid fitted rocket rack");
            this.slot=slot;this.size=size;
            rounds=size.magazine;
            salvoCount=size==Size.SMALL?4:size==Size.SIEGE?16:8;
            // Both physically distinct motor envelopes appear in ordinary
            // AI fleets without needing a debug API or hidden control.
            motor=size==Size.MEDIUM||size==Size.SIEGE
                    ?Motor.SUSTAINED_BURN:Motor.BOOST_COAST;
            damage=24*size.damageMultiplier*output;
            reloadSeconds=size.reloadSeconds/StrictMath.min(1.4,StrictMath.max(.7,tracking));
            // Simplified, cheap non-seeker ammunition. This is the material
            // resupply equivalent of rounds, not a guided missile inventory.
            cheapAmmoUnitsPerRocket=size.ordinal()+1;
            magazineUnitCost=12.0*cheapAmmoUnitsPerRocket;
            body=new ProjectileModel.Spec(
                    StrictMath.min(900,size.terminalSpeed*StrictMath.min(1.35,tracking)),
                    StrictMath.max(.2,StrictMath.min(4,size.health*integrity)),
                    1.30+size.ordinal()*.28,size.signature);
        }
        /** Fresh per-ship mutable magazine from a shared researched prototype.
         * The source rack is not shared and cannot leak ammunition between
         * 200 identical ships in a wing. */
        public Rack(Rack from){
            if(from==null)throw new IllegalArgumentException("Rack source missing");
            slot=from.slot;size=from.size;body=from.body;damage=from.damage;
            reloadSeconds=from.reloadSeconds;magazineUnitCost=from.magazineUnitCost;
            cheapAmmoUnitsPerRocket=from.cheapAmmoUnitsPerRocket;
            rounds=from.rounds;salvoCount=from.salvoCount;motor=from.motor;
        }
        public void configure(int count,Motor burn){
            if(count!=4&&count!=8&&count!=16)
                throw new IllegalArgumentException("Rocket salvo must be 4, 8 or 16 rounds");
            if(burn==null)throw new IllegalArgumentException("Rocket motor absent");
            if(salvoRemaining>0)throw new IllegalStateException("Cannot reprogram a live barrage");
            salvoCount=count;motor=burn;
        }
        public boolean canStart(){
            return salvoRemaining==0&&reload<=0&&rounds>=salvoCount;
        }
        public double reloadSeconds(){
            return reloadSeconds*(salvoCount==16?1.35:salvoCount==8?1.14:1);
        }
    }
    public static double hotEmission(Motor motor,boolean burning) {
        return burning?(motor==Motor.BOOST_COAST?2.5:1.85):.55;
    }
    /** No guidance degrees of freedom: acceleration always points along the
     * immutable initial bearing. A moving hull can evade the entire barrage. */
    public static double acceleration(Size size,Motor motor,double age){
        if(age<0||size==null||motor==null)
            throw new IllegalArgumentException("Invalid rocket phase");
        if(motor==Motor.BOOST_COAST)
            return age<.46?size.boostThrust:0;
        return age<2.4?size.sustainedThrust:0;
    }
    public static boolean burning(Motor motor,double age){
        return acceleration(Size.SMALL,motor,age)>0;
    }
    public static double firingAngle(double startX,double startY,
                                     double targetX,double targetY,
                                     double targetVx,double targetVy,double nominalSpeed){
        double range=StrictMath.hypot(targetX-startX,targetY-startY);
        double lead=StrictMath.min(2.5,range/StrictMath.max(10,nominalSpeed))*.75;
        return StrictMath.atan2(targetY+targetVy*lead-startY,
                targetX+targetVx*lead-startX);
    }
}
