package diplomacy.sim;

/**
 * Particle-beam point defense: a CONTINUOUS electrically powered hard-kill
 * channel, not a pulse laser, flak shell, or probabilistic instant deletion.
 *
 * A bought turret has its own physical bearing, optical lock, dwell history,
 * and local heat reservoir. Continuous exposure ablates one actual body
 * over successive 30 Hz frames. Switching or losing a target resets dwell.
 * The ship must actually spend power and reject heat every illuminated tick.
 */
public final class ParticlePointDefense {
    private ParticlePointDefense(){}
    public static final double MIN_RANGE=90.0, MAX_RANGE=275.0;
    public static final double POWER_PER_SECOND=9.0;
    public static final double HEAT_PER_SECOND=11.0;
    public static final double OVERHEAT=1.35;

    public static final class Mount {
        public final int slotId,researchTier;
        public final double nominalRange,cycle;
        public final ProjectileModel.Defense profile;
        public double bearing=Double.NaN,lockSeconds,thermalLoad,beamDwellSeconds;
        public long trackedBodyId=Long.MIN_VALUE,lastTrackTick=-1;
        public int acquisitions,illuminatedTicks,heatLockouts,powerLockouts;
        public Mount(int slot,int tier,double reach,ProjectileModel.Defense spec,double cycle){
            if(slot<0||tier<0||tier>9||!Double.isFinite(reach)||
                    reach<MIN_RANGE||reach>MAX_RANGE||spec==null||
                    !Double.isFinite(cycle)||cycle<=0)
                throw new IllegalArgumentException("Invalid installed particle beam");
            slotId=slot;researchTier=tier;nominalRange=reach;profile=spec;this.cycle=cycle;
        }
        /** Passive physical cooling, even while captain is holding fire. */
        public void cool(double dt,double coolingCondition){
            if(!Double.isFinite(dt)||dt<=0||!Double.isFinite(coolingCondition))
                throw new IllegalArgumentException("Invalid particle cooling interval");
            thermalLoad=StrictMath.max(0,thermalLoad-
                    dt*.50*StrictMath.max(.10,StrictMath.min(1,coolingCondition)));
        }
        /** One real gimballed optical contact, not an instantaneous hit roll.
         * No continuity is granted when track was absent in the previous tick.
         * A changed target has to traverse and acquire again. */
        public boolean track(long body,double targetX,double targetY,
                             double muzzleX,double muzzleY,double hullFacing,
                             double condition,long tick,double dt){
            if(body==Long.MIN_VALUE||!Double.isFinite(targetX)||
                    !Double.isFinite(targetY)||tick<0||dt<=0)
                throw new IllegalArgumentException("Invalid particle target");
            if(body!=trackedBodyId||lastTrackTick!=tick-1){
                lockSeconds=0;beamDwellSeconds=0;
                if(body!=trackedBodyId)acquisitions++;
                trackedBodyId=body;
            }
            lastTrackTick=tick;
            if(!Double.isFinite(bearing))bearing=hullFacing;
            double angle=StrictMath.atan2(targetY-muzzleY,targetX-muzzleX);
            double turn=StrictMath.atan2(StrictMath.sin(angle-bearing),
                    StrictMath.cos(angle-bearing));
            double slew=StrictMath.max(1.1,StrictMath.min(8.5,
                    4.2*profile.tracking()*StrictMath.max(.15,condition)))*dt;
            bearing+=StrictMath.max(-slew,StrictMath.min(slew,turn));
            double remaining=StrictMath.abs(StrictMath.atan2(
                    StrictMath.sin(angle-bearing),StrictMath.cos(angle-bearing)));
            if(remaining>.052){
                lockSeconds=0;beamDwellSeconds=0;
                return false;
            }
            lockSeconds+=dt;
            return lockSeconds>=.19*cycle/
                    StrictMath.sqrt(StrictMath.max(.35,profile.tracking()));
        }
        /** Accumulated exposure progressively ablates armor, rather than
         * instantly removing the same amount from every tiny target. */
        public double ablationPerSecond(ProjectileModel.Spec physical){
            if(physical==null)return 0;
            double exposure=StrictMath.min(1.55,.42+1.28*beamDwellSeconds);
            double hardness=1+.11*StrictMath.max(0,physical.health()-2.0);
            double saturation=1/(1+.32*thermalLoad);
            return profile.damage()*2.7*exposure*saturation/hardness;
        }
        public boolean available(){
            if(thermalLoad>=OVERHEAT){heatLockouts++;return false;}
            return true;
        }
        public void illuminate(double dt){
            if(!Double.isFinite(dt)||dt<=0)throw new IllegalArgumentException("Invalid beam duration");
            illuminatedTicks++;
            beamDwellSeconds=StrictMath.min(2,beamDwellSeconds+dt);
            thermalLoad+=.78*dt;
        }
    }
}
