package diplomacy.sim;

/** Objective finite history, one-way photon, active echo and relay fixtures. */
public final class SensorLightTimeHistoryTest {
    private static void ok(boolean valid,String why){
        if(!valid)throw new AssertionError(why);
    }
    public static void main(String[] args){
        Battle b=new Battle(712733);
        Battle.Ship source=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,600,100);
        int outward=SensorLightTimeHistory.propagationTicks(500);
        int echoTime=outward*2; // Two separately rounded photon legs.
        ok(outward>0&&echoTime>outward,"Active echo bypassed light time");
        int channels=Battle.CONTACT_OPTICAL|Battle.CONTACT_RADAR;
        source.sensorHistory.capture(0,source);
        target.sensorHistory.capture(0,target);
        target.x=608;target.vx=100; // A recorded maneuver after initial light emission.
        for(long tick=1;tick<=echoTime;tick++){
            source.sensorHistory.capture(tick,source);
            target.sensorHistory.capture(tick,target);
        }
        ok(SensorLightTimeHistory.receive(source,target,outward-1,channels,0)==null,
                "Premature passive light observation");
        var passive=SensorLightTimeHistory.receive(source,target,outward,channels,0);
        ok(passive!=null&&passive.channels()==Battle.CONTACT_OPTICAL&&
                        passive.measuredTick()==0,
                "Optical record was not one-way-old or radar leaked early");
        var premature=SensorLightTimeHistory.receive(source,target,echoTime-1,channels,0);
        ok(premature!=null&&(premature.channels()&Battle.CONTACT_RADAR)==0,
                "Radar reflection arrived before two individually rounded legs");
        var active=SensorLightTimeHistory.receive(source,target,echoTime,channels,0);
        ok(active!=null&&(active.channels()&Battle.CONTACT_RADAR)!=0&&
                        active.emittedTick()<active.measuredTick()&&
                        active.measuredTick()<active.receivedTick(),
                "Radar emission/reflection/receipt chronology wrong");
        ok(active.sample().x()==608&&active.sample().vx()==100,
                "Incorrect measured-state history");
        target.x=612;target.vx=-90000;
        var historical=SensorLightTimeHistory.receive(source,target,echoTime,channels,0);
        ok(historical!=null&&historical.sample().x()!=target.x&&
                        historical.sample().vx()!=target.vx,
                "Hidden target current truth leaked into historical sample");
        // The original active-return scan spends ages searching for a
        // pulse that PASSIVE, unfocused radar or unfocused X-ray can never
        // have emitted. The early-stop variant must return the identical
        // actual historical observation, not a synthetic fresh fix.
        for(SensorActiveScan.Mode mode:new SensorActiveScan.Mode[]{
                SensorActiveScan.Mode.PASSIVE,
                SensorActiveScan.Mode.FOCUSED_RADAR,
                SensorActiveScan.Mode.XRAY_BURST,
                SensorActiveScan.Mode.STANDARD}){
            int focused=mode==SensorActiveScan.Mode.FOCUSED_RADAR||
                    mode==SensorActiveScan.Mode.XRAY_BURST?target.id+17:0;
            source.scan.configure(mode,focused);
            long[] work=new long[3],old=new long[3];
            for(int age=0;age<=echoTime;age++){
                var quick=SensorLightTimeHistory.receive(source,target,age,
                        channels,0,true,work);
                var full=SensorLightTimeHistory.receive(source,target,age,
                        channels,0,false,old);
                ok(java.util.Objects.equals(quick,full),
                        "Impossible active echo pruning changed "+mode+
                                " observation at received tick "+age);
            }
            ok(work[0]==old[0]&&
                    (mode==SensorActiveScan.Mode.STANDARD||
                            work[1]>0&&work[2]<old[2]),
                    "No historical work avoided for incapable active "+mode);
            // An active-only query may be rejected immediately if there
            // is no possible physical emission; the original returns null.
            if(mode!=SensorActiveScan.Mode.STANDARD){
                var fast=SensorLightTimeHistory.receive(source,target,echoTime,
                        Battle.CONTACT_RADAR|Battle.CONTACT_XRAY,0,true,null);
                var control=SensorLightTimeHistory.receive(source,target,echoTime,
                        Battle.CONTACT_RADAR|Battle.CONTACT_XRAY,0,false,null);
                ok(fast==null&&control==null,
                        "No-emission active-only query created a phantom radar lock");
            }
        }
        source.scan.configure(SensorActiveScan.Mode.STANDARD,0);
        target.x=600;
        ok(SensorLightTimeHistory.receive(source,target,echoTime,channels,echoTime)==null,
                "Datalink relay granted unarrived photons");
        ok(target.sensorHistory.at(-1)==null&&
                target.sensorHistory.at(echoTime+1)==null,
                "History fabricated negative or future timestamps");
        for(long tick=echoTime+1;tick<SensorLightTimeHistory.CAPACITY+2;tick++)
            target.sensorHistory.capture(tick,target);
        ok(target.sensorHistory.at(0)==null,
                "Ancient contact did not expire at bounded ring capacity");
        System.out.println("PASS passive/focused/X-ray impossible active-return pruning, "+
                "exact delayed photon A/B, ring expiry and relay latency");
    }
}
