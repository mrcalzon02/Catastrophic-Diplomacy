package diplomacy.sim;

import java.util.List;

/**
 * Persistent pre-engagement sensor doctrine. Focus references a HOSTILE
 * deployment slot rather than a transient Battle ship ID, so the choice
 * survives save/reopen and resolves only after both fleets physically exist.
 */
public record SensorScanOrder(SensorActiveScan.Mode mode,int hostileSlot) {
    public static final SensorScanOrder DEFAULT=
            new SensorScanOrder(SensorActiveScan.Mode.STANDARD,-1);

    public SensorScanOrder {
        if(mode==null||hostileSlot< -1||hostileSlot>=BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Invalid sensor scan order");
        boolean needsFocus=mode==SensorActiveScan.Mode.FOCUSED_RADAR||
                mode==SensorActiveScan.Mode.XRAY_BURST;
        if(needsFocus&&hostileSlot<0)
            throw new IllegalArgumentException("Focused scan requires a hostile slot");
        if(!needsFocus&&hostileSlot!=-1)
            throw new IllegalArgumentException("Non-focused scan cannot retain a target slot");
    }
    public boolean focused(){
        return mode==SensorActiveScan.Mode.FOCUSED_RADAR||
                mode==SensorActiveScan.Mode.XRAY_BURST;
    }
    public SensorScanOrder cycleMode(int step){
        SensorActiveScan.Mode[] values=SensorActiveScan.Mode.values();
        SensorActiveScan.Mode next=values[Math.floorMod(mode.ordinal()+step,values.length)];
        return new SensorScanOrder(next,
                next==SensorActiveScan.Mode.FOCUSED_RADAR||
                next==SensorActiveScan.Mode.XRAY_BURST
                        ?(focused()?hostileSlot:0):-1);
    }
    public SensorScanOrder adjustFocus(int step){
        if(!focused())return this;
        return new SensorScanOrder(mode,
                Math.floorMod(hostileSlot+step,BattleArena.MAX_SHIPS_PER_SIDE));
    }
    public String focusLabel(){return focused()?"HOSTILE "+(hostileSlot+1):"NOT USED";}

    /** Empty means the selected order can physically operate now. */
    public String disabledReason(Battle.Ship scanner,int hostileCount){
        if(scanner==null||hostileCount<0||hostileCount>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Missing scan eligibility context");
        if(focused()&&hostileSlot>=hostileCount)return "FOCUS TARGET UNAVAILABLE";
        return switch(mode){
            case BOOSTED_RADAR,FOCUSED_RADAR ->
                    scanner.sensorRange<=0?"NO RADAR FITTED":"";
            case XRAY_BURST -> scanner.xrayRange<=0?"NO X-RAY FITTED":"";
            default -> "";
        };
    }

    /** Apply only while Battle.configureSensorScan still accepts tick-0 orders. */
    public void apply(Battle battle,Battle.Ship scanner,List<Battle.Ship> hostiles){
        if(battle==null||scanner==null||hostiles==null)
            throw new IllegalArgumentException("Missing scan deployment context");
        if(focused()&&hostileSlot>=hostiles.size()){
            // Preserve the saved focused doctrine for a future larger battle,
            // but never turn a missing hypothesis into free broad STANDARD search.
            battle.configureSensorScan(scanner.id,SensorActiveScan.Mode.PASSIVE,0);
            return;
        }
        int target=focused()?hostiles.get(hostileSlot).id:0;
        battle.configureSensorScan(scanner.id,mode,target);
    }
}
