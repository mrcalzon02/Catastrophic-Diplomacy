package diplomacy.sim;

/**
 * Distinct rotary kinetic CIWS: fitted turret, radar/IR-gated acquisition,
 * inertia-only led tracers, spin-up, finite discrete cartridges and recoil.
 * Neither a laser hit roll nor a flak burst; each fired round is an actual
 * moving body tested with a swept segment against hostile physical threats.
 */
public final class RotaryPointDefense {
    private RotaryPointDefense(){}
    public static final double BULLET_SPEED=705.0;
    public static final double MIN_RANGE=16.0;
    public static final int MAX_ACTIVE_ROUNDS=2048;

    /** A paid weapon socket has its own servo, barrel state and shot history. */
    public static final class Mount {
        public final int slotId,researchTier;
        public final double nominalRange,cycle;
        public final ProjectileModel.Defense profile;
        public double spin,shotCooldown,bearing=Double.NaN;
        public int roundsFired,acquisitions;
        public long trackedId=Long.MIN_VALUE;
        public Mount(int slot,int tier,double range,
                     ProjectileModel.Defense profile,double cycle){
            if(slot<0||tier<0||tier>9||!Double.isFinite(range)||range<35||range>350||
                    profile==null||!Double.isFinite(cycle)||cycle<=0)
                throw new IllegalArgumentException("Invalid installed rotary turret");
            slotId=slot;researchTier=tier;nominalRange=range;
            this.profile=profile;this.cycle=cycle;
        }
        public void tick(double dt){
            shotCooldown=StrictMath.max(0,shotCooldown-dt);
            spin=StrictMath.max(0,spin-dt*.12); // idle barrel drag
        }
        public void track(double dt,long bodyId){
            if(bodyId!=trackedId){acquisitions++;trackedId=bodyId;}
            spin=StrictMath.min(1.0,spin+dt*2.7);
        }
        public boolean ready(){
            return spin>=.40&&shotCooldown<=0;
        }
        public void fired(double weaponCondition){
            roundsFired++;
            // A discrete and finite stream. Research makes bearings track
            // faster without manufacturing free ammunition.
            shotCooldown=StrictMath.max(.054,.118*cycle/
                    StrictMath.max(.25,weaponCondition));
            spin=StrictMath.max(.40,spin-.021);
        }
    }
    /** Live inertial projectile; vx/vy never track the target after launch. */
    public static final class Round {
        public final long visualId,spawnTick;
        public final int sourceId,team,slotId;
        public final double vx,vy,damage;
        public double x,y,life;
        public Round(long id,long tick,int source,int team,int slot,
                     double x,double y,double angle,double damage,double range){
            if(id<=0||slot<0||!Double.isFinite(angle)||damage<=0||
                    !Double.isFinite(range)||range<=0)
                throw new IllegalArgumentException("Invalid rotary projectile");
            visualId=id;spawnTick=tick;sourceId=source;this.team=team;slotId=slot;
            this.x=x;this.y=y;this.damage=damage;
            vx=BULLET_SPEED*StrictMath.cos(angle);
            vy=BULLET_SPEED*StrictMath.sin(angle);
            life=range/BULLET_SPEED;
        }
    }
    /** Physical sensor signature and environmental attenuation are shared
     * with existing radar PD. This rotary camera's short mechanical horizon
     * stays independent of stronger laser emitter socket ranges. */
    public static double acquisitionRange(Battle.Ship ship,Mount gun,
                                          ProjectileModel.Spec target,
                                          double weather,double emission){
        if(ship==null||gun==null||target==null)return 0;
        if(ship.power<=3||ship.crewReadiness()<.05)return 0;
        double range=StrictMath.min(gun.nominalRange,
                ship.sensorRange*(.17+.035*StrictMath.min(2,ship.sensorAcuity)));
        return ProjectileModel.acquisitionRange(range,ship.sensorsCondition,
                weather,gun.profile,target,emission);
    }
    /** Predict one ballistic intercept using current observed velocity.
     * The round is not guided and needs enough real flight time to reach it. */
    public static double leadAngle(double muzzleX,double muzzleY,double targetX,
                                   double targetY,double vx,double vy){
        double dx=targetX-muzzleX,dy=targetY-muzzleY;
        double t=StrictMath.hypot(dx,dy)/BULLET_SPEED;
        for(int i=0;i<2;i++)
            t=StrictMath.hypot(dx+vx*t,dy+vy*t)/BULLET_SPEED;
        return StrictMath.atan2(dy+vy*t,dx+vx*t);
    }
    /** Heavy casings resist tiny machine-cannon penetrators; light rockets
     * and drones are vulnerable. No fixed random target-class immunity. */
    public static double directDamage(ProjectileModel.Spec target,
                                      ProjectileModel.Defense gun){
        if(target==null||gun==null)return 0;
        double hardening=StrictMath.max(0,target.health()-2.0);
        return gun.damage()*1.35/(1+.58*hardening);
    }
}
