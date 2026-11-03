package diplomacy.sim;

/**
 * Fitted sensor search doctrine, selected before ENGAGE.
 * No free detectors: RADAR requires existing RDR range, X-ray requires XRY.
 * Pulse history is fixed-size and replay deterministic; no mission timer or
 * asynchronous wall clock is involved.
 */
public final class SensorActiveScan {
    private SensorActiveScan(){}
    public enum Mode {
        STANDARD,       // Existing always-available equipment baseline.
        PASSIVE,        // No radar/X-ray emission.
        BOOSTED_RADAR,  // Broad, lower-resolution high-energy sweep.
        FOCUSED_RADAR,  // Longer dwell restricted to an ordered target.
        XRAY_BURST      // Expensive narrow identification illumination.
    }
    private static final int HISTORY=16;
    public static final class State {
        private Mode mode=Mode.STANDARD;
        private int focusTargetId;
        private long nextPulseTick=1;
        private final long[] emittedTicks=new long[HISTORY];
        private final int[] emittedFocus=new int[HISTORY];
        private int stored;
        public int emissions;
        public double powerSpent,heatGenerated;
        public long lastEmissionTick=-1;
        public Mode mode(){return mode;}
        public int focusTargetId(){return focusTargetId;}
        public void configure(Mode value,int targetId){
            if(value==null||targetId<0)
                throw new IllegalArgumentException("Bad sensor search order");
            if((value==Mode.FOCUSED_RADAR||value==Mode.XRAY_BURST)&&targetId==0)
                throw new IllegalArgumentException("Focused search requires an identified hypothesis");
            mode=value;focusTargetId=targetId;nextPulseTick=1;
            stored=0;emissions=0;powerSpent=0;heatGenerated=0;lastEmissionTick=-1;
        }
        /** Replay signature also includes the bounded historical pulse queue. */
        public long fingerprint(){
            long value=0xcbf29ce484222325L;
            value=(value^mode.ordinal())*0x100000001b3L;
            value=(value^focusTargetId)*0x100000001b3L;
            value=(value^nextPulseTick)*0x100000001b3L;
            value=(value^stored)*0x100000001b3L;
            value=(value^Double.doubleToLongBits(powerSpent))*0x100000001b3L;
            value=(value^Double.doubleToLongBits(heatGenerated))*0x100000001b3L;
            for(int i=0;i<StrictMath.min(HISTORY,stored);i++){
                value=(value^emittedTicks[i])*0x100000001b3L;
                value=(value^emittedFocus[i])*0x100000001b3L;
            }
            return value;
        }
        /** Every active return must originate in a real paid historical pulse. */
        public boolean emittedFor(long when,int targetId,int channel){
            if(mode==Mode.STANDARD)return true; // Baseline uninterrupted RDR/XRY.
            if(mode==Mode.PASSIVE)return false;
            if(mode==Mode.XRAY_BURST){
                if((channel&Battle.CONTACT_XRAY)==0||targetId!=focusTargetId)
                    return false;
            }else{
                if((channel&Battle.CONTACT_RADAR)==0)return false;
                if(mode==Mode.FOCUSED_RADAR&&targetId!=focusTargetId)
                    return false;
            }
            for(int i=0;i<StrictMath.min(HISTORY,stored);i++){
                if(emittedTicks[i]==when &&
                        (mode==Mode.BOOSTED_RADAR||emittedFocus[i]==targetId))
                    return true;
            }
            return false;
        }
    }
    public static double radarFactor(State state,int targetId){
        return switch(state.mode()){
            case STANDARD -> 1;
            case PASSIVE,XRAY_BURST -> 0;
            case BOOSTED_RADAR -> 1.55;
            case FOCUSED_RADAR -> state.focusTargetId()==targetId?1.28:0;
        };
    }
    public static double xrayFactor(State state,int targetId){
        return switch(state.mode()){
            case STANDARD -> 1;
            case XRAY_BURST -> state.focusTargetId()==targetId?1.45:0;
            default -> 0;
        };
    }
    /** Physical emitter operation; executes at most once per cooldown.
     * This does not invent target coordinates or grant instant radar returns. */
    public static void advance(Battle.Ship ship,long tick){
        State state=ship.scan;
        Mode mode=state.mode();
        if(mode==Mode.STANDARD||mode==Mode.PASSIVE||tick<state.nextPulseTick||
                !ship.alive()||ship.power<=3||ship.sensorsCondition<=.15||
                ship.reactorCondition<=.15||ship.crewReadiness()<.05)
            return;
        boolean radar=mode!=Mode.XRAY_BURST;
        if((radar?ship.sensorRange:ship.xrayRange)<=0)return;
        double power=switch(mode){
            case BOOSTED_RADAR -> 2.5;
            case FOCUSED_RADAR -> 3.8;
            case XRAY_BURST -> 12.0;
            default -> 0;
        };
        double heat=switch(mode){
            case BOOSTED_RADAR -> 1.1;
            case FOCUSED_RADAR -> 1.8;
            case XRAY_BURST -> 7.5;
            default -> 0;
        };
        int cadence=switch(mode){
            case BOOSTED_RADAR -> 18;
            case FOCUSED_RADAR -> 12;
            case XRAY_BURST -> 60;
            default -> Integer.MAX_VALUE;
        };
        // Fitted electronics, reactor, crew and thermal headroom are mandatory.
        double condition=StrictMath.max(.25,ship.sensorsCondition);
        double powerCost=power/condition,heatCost=heat/condition;
        if(ship.power<=3+powerCost||ship.heat+heatCost>ship.maxHeat*.92)return;
        ship.power-=powerCost;ship.heat+=heatCost;
        int index=state.stored%HISTORY;
        state.emittedTicks[index]=tick;
        state.emittedFocus[index]=state.focusTargetId;
        state.stored++;
        state.emissions++;
        state.powerSpent+=powerCost;
        state.heatGenerated+=heatCost;
        state.lastEmissionTick=tick;
        state.nextPulseTick=tick+cadence;
    }
}
