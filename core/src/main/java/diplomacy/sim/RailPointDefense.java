package diplomacy.sim;

/**
 * High-velocity defensive rail interceptor, deliberately independent of
 * precision laser PD, flak clouds and short-range rotary machine cannons.
 * A mounted rail gun predicts the intercept, charges a capacitor, slews its
 * barrel and releases ONE finite, unguided solid penetrator per expensive shot.
 */
public final class RailPointDefense {
    private RailPointDefense(){}
    public static final double PENETRATOR_SPEED=1210.0;
    public static final double MIN_ENGAGEMENT_RANGE=120.0;
    public static final int CARTRIDGES_PER_SHOT=3;
    public static final int MAX_ACTIVE_PENETRATORS=512;

    public static final class Mount {
        public final int slotId,researchTier;
        public final double nominalRange,cycle;
        public final ProjectileModel.Defense profile;
        public double charge,reload,lockSeconds,bearing=Double.NaN;
        public long trackedBodyId=Long.MIN_VALUE;
        public int scans,acquisitions,shotsFired,chargeWaitTicks;
        public Mount(int slot,int rank,double range,
                     ProjectileModel.Defense defense,double cycle){
            if(slot<0||rank<0||rank>9||!Double.isFinite(range)||
                    range<MIN_ENGAGEMENT_RANGE||range>335||
                    defense==null||!Double.isFinite(cycle)||cycle<=0)
                throw new IllegalArgumentException("Invalid fitted rail interceptor");
            slotId=slot;researchTier=rank;nominalRange=range;
            profile=defense;this.cycle=cycle;
        }
        public void tick(double dt){
            reload=StrictMath.max(0,reload-dt);
        }
        /** The capacitor is powered independently of fire-control locks.
         * Without precharge, a late small-shell track cannot be intercepted
         * even by an otherwise capable long-range defensive rail weapon. */
        public void charge(double dt){
            charge=StrictMath.min(1,charge+dt/
                    StrictMath.max(.72,1.25*cycle));
            if(charge<1)chargeWaitTicks++;
        }
        /** Mechanical pointing lock needs real continuous observation. A
         * target switch resets the lock, but does NOT discharge an already
         * energized electromagnetic capacitor for no physical reason. */
        public void track(long physicalId,double dt){
            if(physicalId!=trackedBodyId){
                trackedBodyId=physicalId;acquisitions++;lockSeconds=0;
            }
            lockSeconds=StrictMath.min(2,lockSeconds+dt);
        }
        public boolean ready(){
            return charge>=1&&reload<=0&&lockSeconds>=.20;
        }
        public void fire(double condition){
            shotsFired++;charge=0;
            reload=StrictMath.max(1.25,2.4*cycle/
                    StrictMath.max(.3,condition));
        }
    }
    /** Real solid-body defensive shell: never homes or retargets in flight. */
    public static final class Penetrator {
        public final long visualId,spawnTick;
        public final int team,sourceId,slotId;
        public final long assignedBodyId;
        /** Expected solid-penetrator damage reservation expires with THIS
         * actual in-flight projectile; it is not a guaranteed hit. */
        public final double reservedDamage;
        public final double vx,vy,damage;
        /** Immutable actual launch point for render-only first-tick travel. */
        public final double startX,startY;
        public double x,y,life;
        public Penetrator(long shot,long tick,int team,int source,int slot,
                          double x,double y,double bearing,
                          double reach,double damage,long assignedBody,
                          double reservedDamage){
            if(shot<=0||slot<0||assignedBody<=0||!Double.isFinite(bearing)||
                    damage<=0||!Double.isFinite(reservedDamage)||
                    reservedDamage<=0||!Double.isFinite(reach)||
                    reach<MIN_ENGAGEMENT_RANGE)
                throw new IllegalArgumentException("Invalid rail penetrator");
            visualId=shot;spawnTick=tick;this.team=team;sourceId=source;
            slotId=slot;this.x=x;this.y=y;startX=x;startY=y;this.damage=damage;
            assignedBodyId=assignedBody;this.reservedDamage=reservedDamage;
            vx=PENETRATOR_SPEED*StrictMath.cos(bearing);
            vy=PENETRATOR_SPEED*StrictMath.sin(bearing);
            life=reach/PENETRATOR_SPEED;
        }
    }
    /** Larger hard targets resolve more strongly on active radar. Tiny kinetic
     * slugs remain legal but have a very small collision aperture. */
    /** A fast body produces useful Doppler shift only when its actual
     * velocity closes toward this detector. High lateral or outbound speed
     * cannot magically extend acquisition merely from its rated top speed. */
    public static double radialClosing(Battle.Ship observer,double x,double y,
                                       double vx,double vy){
        if(observer==null)return 0;
        double dx=observer.x-x,dy=observer.y-y;
        double d=StrictMath.hypot(dx,dy);
        if(d<.001)return 0;
        return StrictMath.max(0,(dx*(vx-observer.vx)+
                dy*(vy-observer.vy))/d);
    }
    public static double acquisitionRange(Battle.Ship ship,Mount mount,
                                          ProjectileModel.Spec target,
                                          double weather,double emission){
        return acquisitionRange(ship,mount,target,weather,emission,
                target==null?0:target.speed());
    }
    /** Production sensors receive ACTUAL radial closing speed from the
     * observed velocity vector, not a nominal motor specification. */
    public static double acquisitionRange(Battle.Ship ship,Mount mount,
                                          ProjectileModel.Spec target,
                                          double weather,double emission,
                                          double radialSpeed){
        if(ship==null||mount==null||target==null)return 0;
        if(ship.power<=6||ship.crewReadiness()<.05)return 0;
        double radarHorizon=StrictMath.min(mount.nominalRange,
                ship.sensorRange*(.25+.035*StrictMath.min(2,ship.sensorAcuity)));
        // Coherent pulse-Doppler processing is this railgun sensor's
        // distinguishing high-closing-speed channel. Fast compact solids
        // have weak cross-sections but produce strong measured frequency
        // shifts; finite coherent processing recovers part of that signal.
        // Never evade current sensor damage, interference or emitter limits.
        double doppler=1+.75*StrictMath.max(0,StrictMath.min(1,
                (StrictMath.max(0,radialSpeed)-180)/100.0));
        double echo=ProjectileModel.acquisitionRange(radarHorizon,
                ship.sensorsCondition,weather,mount.profile,target,emission);
        double hardwareLimit=radarHorizon*
                StrictMath.max(0,StrictMath.min(1,ship.sensorsCondition))*
                StrictMath.max(0,StrictMath.min(2.5,weather))*
                mount.profile.detectionMultiplier();
        return StrictMath.min(hardwareLimit,echo*doppler);
    }
    /** Two-step physical lead prediction, not a guaranteed collision.
     * The projectile keeps this launch bearing after electromagnetic exit. */
    public static double leadAngle(double muzzleX,double muzzleY,
                                   double targetX,double targetY,
                                   double vx,double vy){
        double dx=targetX-muzzleX,dy=targetY-muzzleY;
        double t=StrictMath.hypot(dx,dy)/PENETRATOR_SPEED;
        for(int n=0;n<2;n++)
            t=StrictMath.hypot(dx+vx*t,dy+vy*t)/PENETRATOR_SPEED;
        return StrictMath.atan2(dy+vy*t,dx+vx*t);
    }
    /** Concentrated solid penetrator excels against thick missile casings:
     * it delivers high depth-specific damage rather than broad flak area. */
    public static double directDamage(ProjectileModel.Spec target,
                                      ProjectileModel.Defense gun){
        if(target==null||gun==null)return 0;
        return gun.damage()*3.2*(1+.15*StrictMath.min(8,target.health()));
    }
}
