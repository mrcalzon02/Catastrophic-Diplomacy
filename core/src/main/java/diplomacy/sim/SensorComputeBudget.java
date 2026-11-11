package diplomacy.sim;

/**
 * One deterministic, finite target-solution work pool per vessel and tick.
 * Hardware/captain skill buys processing; CAP buys engagement slots, NEVER
 * free compute. Insufficient compute yields a poorer physical aim solution
 * rather than an instant guaranteed miss or imaginary perfect sensor lock.
 */
public final class SensorComputeBudget {
    private SensorComputeBudget(){}
    public record Allocation(double precision,double requestedCost,
                             double spent,double remaining,boolean limited) {
        public Allocation{
            if(!Double.isFinite(precision)||precision<0||
                    !Double.isFinite(requestedCost)||requestedCost<=0||
                    !Double.isFinite(spent)||spent<0||
                    !Double.isFinite(remaining)||remaining<0)
                throw new IllegalArgumentException("Invalid finite solver allocation");
        }
    }
    public static final class State {
        private long lastTick=-1;
        private double remaining;
        public long allocations,exhausted;
        public long lastTick(){return lastTick;}
        public double remaining(){return remaining;}
        public long fingerprint(){
            long h=0xcbf29ce484222325L;
            h=(h^lastTick)*0x100000001b3L;
            h=(h^Double.doubleToLongBits(remaining))*0x100000001b3L;
            h=(h^allocations)*0x100000001b3L;
            return (h^exhausted)*0x100000001b3L;
        }
        private Allocation reserve(long tick,double perTick,double requested,
                                    double bestPrecision){
            if(tick<0||!Double.isFinite(perTick)||perTick<0||
                    !Double.isFinite(requested)||requested<=0||
                    !Double.isFinite(bestPrecision)||bestPrecision<0)
                throw new IllegalArgumentException("Invalid compute reservation");
            if(tick<lastTick)throw new IllegalArgumentException("Solver clock reversed");
            if(lastTick!=tick){lastTick=tick;remaining=perTick;}
            double paid=StrictMath.min(remaining,requested);
            remaining=StrictMath.max(0,remaining-paid);
            allocations++;
            boolean limited=paid+1e-9<requested;
            if(limited)exhausted++;
            // Incomplete solutions retain noisy real intercept rays.
            return new Allocation(bestPrecision*StrictMath.sqrt(paid/requested),
                    requested,paid,remaining,limited);
        }
    }
    /** Source electronics only. Deliberately ignores Network.targetCapacity()
     * and weapon count: target slots cannot invent processor cycles. */
    public static double capacity(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Ship required");
        if(!ship.alive()||ship.power<=3||ship.sensorsCondition<=.05||
                ship.reactorCondition<=.15||ship.crewReadiness()<.05)return 0;
        double acuity=StrictMath.max(0,ship.sensorAcuity-1);
        double resolution=StrictMath.max(0,ship.network.resolutionGain()-1);
        double tracking=StrictMath.max(0,ship.network.trackingPrecision());
        // FCM command assistance improves track correlation, NOT this
        // ship's intrinsic compute cycles. A shared FCM must be explicitly
        // reserved from its own separate finite pool below.
        double result=1.0+1.2*acuity+1.5*resolution+
                2.0*tracking;
        return StrictMath.min(10,result)*
                StrictMath.max(0,StrictMath.min(1,ship.sensorsCondition))*
                ship.crewReadiness();
    }
    /** A physically arrived hologram is NOT a separately targetable hull,
     * but correlating its alternatives costs actual processor cycles.
     * The target's own stored track occupies one normal slot; false contacts
     * occupy additional hypotheses. No invented future ghost data enters. */
    public static int competingHypotheses(int rememberedTracks,int emittedGhosts){
        if(rememberedTracks<0||emittedGhosts<0)
            throw new IllegalArgumentException("Invalid track/hypothesis count");
        return StrictMath.min(32,StrictMath.max(0,rememberedTracks-1)+emittedGhosts);
    }
    /** Physically fitted, powered FCM computer pool. Only this module's
     * ship can generate the shared work units; allies cannot each mint a
     * fresh copy. CAP increases engagement slots but not this capacity. */
    public static double fleetCapacity(Battle.Ship commander){
        if(commander==null||!commander.alive()||commander.power<=3||
                commander.reactorCondition<=.15||
                commander.commandCondition<=.15||
                commander.sensorsCondition<=.15||
                commander.crewReadiness()<.05||
                commander.network.commandCapacity()<=0)return 0;
        double strength=StrictMath.max(0,commander.network.fusionStrength());
        return StrictMath.min(5,2.5*strength)*
                StrictMath.min(1,commander.commandCondition)*
                StrictMath.min(1,commander.sensorsCondition)*
                commander.crewReadiness();
    }
    /**
     * Spend own local cycles FIRST; eligible FCM recipients then compete
     * for the ONE command ship's finite hardware pool. Target information
     * must be old enough for an outward measurement and return of the
     * command solution (two real light-speed hops).
     *
     * Additional work changes a noisy physical solution's precision,
     * never discovers an unmeasured target or grants independent fire.
     * Allocation.remaining continues to report the LOCAL reserve only.
     */
    public static Allocation solveWithCommand(State local,
            Battle.Ship actor,SensorLightTimeHistory.Observation received,
            long tick,double range,double bestPrecision,int hypotheses,
            Battle.Ship commander){
        // Compatibility for isolated callers which deliberately reuse one
        // historical report object. Production Battle supplies the retained
        // request tick from its bounded per-target work session below.
        return solveWithCommand(local,actor,received,tick,range,bestPrecision,
                hypotheses,commander,received.receivedTick());
    }

    /**
     * Production FCM path. commandRequestTick is the actor's first continuous
     * request for this target/source session, not the receipt timestamp of the
     * newest sensor sample. Fresh reports therefore cannot perpetually restart
     * the reciprocal command delay. The donor still spends one finite shared
     * pool and contributes no target discovery of its own.
     */
    public static Allocation solveWithCommand(State local,
            Battle.Ship actor,SensorLightTimeHistory.Observation received,
            long tick,double range,double bestPrecision,int hypotheses,
            Battle.Ship commander,long commandRequestTick){
        Allocation paid=solve(local,actor,received,tick,range,
                bestPrecision,hypotheses);
        if(!paid.limited()||commander==null||
                actor.commandSourceId!=commander.id||
                commander.team!=actor.team||fleetCapacity(commander)<=0||
                commandRequestTick<0)
            return paid;
        if(commandRequestTick>tick)
            throw new IllegalArgumentException("Command work request is from the future");
        double distance=StrictMath.hypot(actor.x-commander.x,
                actor.y-commander.y);
        double operationalRadius=commander.network.fusionRange()*
                StrictMath.min(commander.commandCondition,
                        commander.sensorsCondition);
        if(distance>operationalRadius)return paid;
        int roundTrip=2*SensorLightTimeHistory.propagationTicks(distance);
        if(tick-commandRequestTick<roundTrip)return paid;
        double outstanding=paid.requestedCost()-paid.spent();
        if(outstanding<=1e-9)return paid;
        Allocation shared=commander.fleetCompute.reserve(tick,
                fleetCapacity(commander),outstanding,1);
        double total=StrictMath.min(paid.requestedCost(),
                paid.spent()+shared.spent());
        return new Allocation(bestPrecision*StrictMath.sqrt(
                total/paid.requestedCost()),paid.requestedCost(),total,
                paid.remaining(),total+1e-9<paid.requestedCost());
    }

    /** Age is received track age in simulation seconds (60 physical seconds
     * per combat second). Farther and older targets and competing remembered
     * hypotheses have a real, non-linear compute burden. */
    public static double cost(double range,double ageSeconds,int hypotheses){
        if(!Double.isFinite(range)||range<0||
                !Double.isFinite(ageSeconds)||ageSeconds<0||
                hypotheses<0)throw new IllegalArgumentException("Invalid solve load");
        double normalized=StrictMath.min(10,range/600);
        double elapsed=StrictMath.min(120,ageSeconds);
        double competition=StrictMath.min(32,hypotheses);
        return (1+.30*normalized)*(1+.30*normalized)*
                (1+.35*elapsed)*(1+.08*competition);
    }
    public static Allocation solve(State state,Battle.Ship ship,
            SensorLightTimeHistory.Observation received,long tick,
            double range,double bestPrecision,int competingHypotheses){
        if(state==null||received==null||received.receivedTick()>tick)
            throw new IllegalArgumentException("Cannot compute unseen track");
        double age=(tick-received.measuredTick())*Battle.DT;
        return state.reserve(tick,capacity(ship),
                cost(range,age,competingHypotheses),bestPrecision);
    }
}
