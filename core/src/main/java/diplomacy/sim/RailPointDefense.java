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
        public double charge,reload,bearing=Double.NaN;
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
            if(trackedBodyId==Long.MIN_VALUE)
                charge=StrictMath.max(0,charge-dt*.12);
        }
        /** An interrupted/reacquired target loses part of its fire-control
         * capacitor alignment; previous tracking cannot become a free shot. */
        public void track(long physicalId,double dt){
            if(physicalId!=trackedBodyId){
                charge*=trackedBodyId==Long.MIN_VALUE?1:.45;
                trackedBodyId=physicalId;acquisitions++;
            }
            charge=StrictMath.min(1,charge+dt/
                    StrictMath.max(.72,1.25*cycle));
            if(charge<1)chargeWaitTicks++;
        }
        public boolean ready(){return charge>=1&&reload<=0;}
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
        public final double vx,vy,damage;
        public double x,y,life;
        public Penetrator(long shot,long tick,int team,int source,int slot,
                          double x,double y,double bearing,
                          double reach,double damage){
            if(shot<=0||slot<0||!Double.isFinite(bearing)||damage<=0||
                    !Double.isFinite(reach)||reach<MIN_ENGAGEMENT_RANGE)
                throw new IllegalArgumentException("Invalid rail penetrator");
            visualId=shot;spawnTick=tick;this.team=team;sourceId=source;
            slotId=slot;this.x=x;this.y=y;this.damage=damage;
            vx=PENETRATOR_SPEED*StrictMath.cos(bearing);
            vy=PENETRATOR_SPEED*StrictMath.sin(bearing);
            life=reach/PENETRATOR_SPEED;
        }
    }
    /** Larger hard targets resolve more strongly on active radar. Tiny kinetic
     * slugs remain legal but have a very small collision aperture. */
    public static double acquisitionRange(Battle.Ship ship,Mount mount,
                                          ProjectileModel.Spec target,
                                          double weather,double emission){
        if(ship==null||mount==null||target==null)return 0;
        if(ship.power<=6||ship.crewReadiness()<.05)return 0;
        double radarHorizon=StrictMath.min(mount.nominalRange,
                ship.sensorRange*(.25+.035*StrictMath.min(2,ship.sensorAcuity)));
        return ProjectileModel.acquisitionRange(radarHorizon,
                ship.sensorsCondition,weather,mount.profile,target,emission);
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
