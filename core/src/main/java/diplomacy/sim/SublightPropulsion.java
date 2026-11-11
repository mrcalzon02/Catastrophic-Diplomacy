package diplomacy.sim;

import diplomacy.shipyard.Shipyard;

/**
 * Relates existing installed ENG machinery to bounded tactical propulsion.
 * Top speeds still respect installed drive class and the compressed sublight
 * scale, but screen-space acceleration is deliberately a gameplay quantity.
 * Realistic g-to-world conversion made capital ships effectively stationary
 * while fighters used direct world-unit motion; battle maneuvering now wins
 * over that false precision.
 */
public final class SublightPropulsion {
    private SublightPropulsion(){}
    /**
     * Two 60 Hz physical integrations for every existing 30 Hz tactical tick.
     * This increases positional integration fidelity without advancing AI,
     * sensors, projectile firing, resource regeneration or tick counters twice.
     */
    public static final int POSITION_SUBSTEPS=2;
    public enum Drive {
        AUXILIARY_ELECTRIC(.001,0.3,4.0,0.65),
        FUSION_TORCH(.006,3.0,14.0,1.0),
        ADVANCED_FUSION(.012,6.0,22.0,1.4),
        HIGH_ENERGY_TORCH(.018,9.0,32.0,1.8);
        public final double maxFractionOfC,accelerationGee,
                tacticalAcceleration,fuelPerDeltaV;
        Drive(double maxFraction,double gee,double tacticalAccel,double fuelRate){
            maxFractionOfC=maxFraction;
            accelerationGee=gee;
            tacticalAcceleration=tacticalAccel;
            fuelPerDeltaV=fuelRate;
        }
    }

    /** Existing installed drive *count and tech stage* determine capability.
     * No new engine item, free hardpoint or save format is introduced. */
    public static Drive fitted(Shipyard.Design design){
        if(design==null)throw new IllegalArgumentException("Missing blueprint");
        int engines=0,maxStage=0;
        for(int socket=0;socket<design.slots();socket++){
            Shipyard.Installed part=design.at(socket);
            if(part==null||!part.component.endsWith("-ENG"))continue;
            engines++;
            for(int stage:part.stages().values())maxStage=Math.max(maxStage,stage);
        }
        if(engines==0)return Drive.AUXILIARY_ELECTRIC;
        if(maxStage>=7)return Drive.HIGH_ENERGY_TORCH;
        if(engines>=2||maxStage>=4)return Drive.ADVANCED_FUSION;
        return Drive.FUSION_TORCH;
    }

    /** Installed rated engine output remains meaningful below its
     * drive-family physical limit. A destroyed engine cannot create thrust. */
    public static double maximumSpeed(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Missing ship");
        if(ship.fuel<=0||ship.engineCondition<=.01||ship.crewReadiness()<=0)return 0;
        double physical=WorldScale.simSpeedForLightFraction(
                ship.propulsion.maxFractionOfC);
        return StrictMath.min(physical,StrictMath.max(0,ship.speed))
                *StrictMath.min(1,ship.engineCondition)*ship.crewReadiness();
    }

    /** A zero-thrust HOLD command coasts on existing momentum in vacuum.
     * This changes position but not velocity or finite reaction-mass fuel. */
    public static void coast(Battle.Ship ship,double dt){
        if(ship==null||!Double.isFinite(dt)||dt<=0)
            throw new IllegalArgumentException("Invalid coast timestep");
        ship.x+=ship.vx*dt;
        ship.y+=ship.vy*dt;
    }

    /** Applies acceleration-limited thrust vector, never instant braking.
     * Fuel is spent on actual delta-V rather than on passive coasting. */
    public static void advance(Battle.Ship ship,double wantedX,double wantedY,
                               double dt){
        advance(ship,wantedX,wantedY,dt,0);
    }

    /**
     * A captain can protect part of the ONE existing tank for an eventual
     * withdrawal. Only discretionary thrust is constrained; escape orders
     * supply zero protectedFuel and may burn the entire physical tank.
     * Actual thrust is always limited to fuel physically remaining.
     */
    public static void advance(Battle.Ship ship,double wantedX,double wantedY,
                               double dt,double protectedFuel){
        advance(ship,wantedX,wantedY,dt,protectedFuel,1);
    }

    /**
     * Reduced thrust fraction is an actual acceleration/propellant-spend
     * restriction, not merely a speed cap that low-gee ships never reach.
     */
    public static void advance(Battle.Ship ship,double wantedX,double wantedY,
                               double dt,double protectedFuel,double thrustFraction){
        advanceSubstepped(ship,wantedX,wantedY,dt,protectedFuel,
                thrustFraction,POSITION_SUBSTEPS);
    }

    /**
     * Package-private A/B integrator for deterministic precision diagnostics.
     * A substep may integrate POSITION only; the battle's authoritative tick
     * and all systems outside ship thrust stay at the existing 30 Hz.
     */
    static void advanceSubstepped(Battle.Ship ship,double wantedX,double wantedY,
                                  double dt,double protectedFuel,double thrustFraction,
                                  int substeps){
        if(ship==null||!Double.isFinite(wantedX)||!Double.isFinite(wantedY)||
                !Double.isFinite(dt)||dt<=0||!Double.isFinite(protectedFuel)||protectedFuel<0||
                !Double.isFinite(thrustFraction)||thrustFraction<=0||thrustFraction>1||
                substeps<1||substeps>32)
            throw new IllegalArgumentException("Invalid thrust demand, reserve or substeps");
        double maxSpeed=maximumSpeed(ship);
        double requested=StrictMath.hypot(wantedX,wantedY);
        if(requested>maxSpeed&&requested>0){
            wantedX*=maxSpeed/requested;wantedY*=maxSpeed/requested;
        }
        double halfDt=dt/substeps;
        // Tactical acceleration is intentionally NOT derived from real-world g.
        // Fighters already move directly in world units; capital ships need the
        // same readable tactical coordinate system while retaining slower caps,
        // finite fuel, engine damage and crew/throttle penalties.
        double acceleration=ship.propulsion.tacticalAcceleration*
                ship.engineCondition*ship.crewReadiness()*thrustFraction;
        for(int substep=0;substep<substeps;substep++){
            double differenceX=wantedX-ship.vx,differenceY=wantedY-ship.vy;
            double difference=StrictMath.hypot(differenceX,differenceY);
            double maxStep=ship.fuel>0&&ship.engineCondition>.01&&ship.crewReadiness()>0?
                    acceleration*halfDt:0;
            double spendable=StrictMath.max(0,ship.fuel-protectedFuel);
            double fuelStep=spendable/ship.propulsion.fuelPerDeltaV;
            double applied=StrictMath.min(difference,StrictMath.min(maxStep,fuelStep));
            if(applied>0&&difference>0){
                ship.vx+=differenceX*applied/difference;
                ship.vy+=differenceY*applied/difference;
                ship.fuel=StrictMath.max(0,ship.fuel-
                        applied*ship.propulsion.fuelPerDeltaV);
            }
            // Semi-implicit integration at half-DT reduces distance error
            // without modifying the total physical time or creating fuel.
            ship.x+=ship.vx*halfDt;
            ship.y+=ship.vy*halfDt;
        }
    }
}
