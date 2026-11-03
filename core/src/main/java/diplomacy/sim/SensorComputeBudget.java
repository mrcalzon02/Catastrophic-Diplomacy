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
        double command=StrictMath.max(0,ship.commandAssist);
        double result=1.0+1.2*acuity+1.5*resolution+
                2.0*tracking+.85*command;
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
