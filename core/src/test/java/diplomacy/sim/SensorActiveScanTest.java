package diplomacy.sim;

/** A/B of real sensor modes, emission history, physical costs, and delayed echo. */
public final class SensorActiveScanTest {
    private static void ok(boolean pass,String name){
        if(!pass)throw new AssertionError(name);
    }
    private static Battle.Ship[] duel(Battle b){
        Battle.Ship sensor=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.DRAXIAN,600,100);
        for(Battle.Ship ship:b.ships()){
            ship.power=100;
            b.registerDiagnosticStationaryTarget(ship.id);
        }
        sensor.infraredRange=sensor.graviticRange=0;
        return new Battle.Ship[]{sensor,hostile};
    }
    private static void passive(){
        Battle b=new Battle(92371);
        Battle.Ship[] pair=duel(b);
        pair[0].sensorRange=1600;pair[0].xrayRange=1200;
        b.configureSensorScan(pair[0].id,SensorActiveScan.Mode.PASSIVE,0);
        ok(b.directSensorChannels(pair[0],pair[1],500)==0,
                "PASSIVE used powered radar or active X-ray channel");
        int overlapping=b.directSensorChannels(pair[0],pair[1],0);
        ok((overlapping&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY))==0,
                "Missing active channel gained a free zero-range lock");
        for(int i=0;i<20;i++)b.step();
        ok(pair[0].scan.emissions==0&&pair[0].scan.powerSpent==0,
                "Passive sensor mode used active emission power");
    }
    private static void boosted(){
        Battle b=new Battle(92373);
        Battle.Ship[] pair=duel(b);
        pair[0].sensorRange=400; // Baseline cannot see radar at 500.
        ok((b.directSensorChannels(pair[0],pair[1],500)&Battle.CONTACT_RADAR)==0,
                "Baseline radar unexpectedly outranged 500");
        b.configureSensorScan(pair[0].id,SensorActiveScan.Mode.BOOSTED_RADAR,0);
        ok((b.directSensorChannels(pair[0],pair[1],500)&Battle.CONTACT_RADAR)!=0,
                "Boosted radar did not extend an actually installed scanner");
        b.step();
        ok(pair[0].scan.emissions==1&&pair[0].scan.powerSpent>0&&
                        pair[0].scan.heatGenerated>0,
                "Wide-area pulse emitted without real energy and thermal cost");
        ok(!b.hasTargetContact(pair[0],pair[1],500),
                "Boosted radar created an immediate superluminal weapon lock");
        int round=2*SensorLightTimeHistory.propagationTicks(500);
        for(int i=1;i<round;i++){
            b.step();
            ok(!b.hasTargetContact(pair[0],pair[1],500),
                    "Active pulse returned sooner than physical radar echo");
        }
        b.step();
        ok(b.hasTargetContact(pair[0],pair[1],500),
                "Paid boosted radar echo never reached fire-control");
        Battle.TacticalContact report=b.tacticalPicture(0,pair[0].id).stream()
                .filter(x->x.targetId()==pair[1].id).findFirst().orElse(null);
        ok(report!=null&&(report.channels()&Battle.CONTACT_RADAR)!=0&&
                        report.emittedTick()==1&&
                        report.observedTick()>report.emittedTick(),
                "Boosted radar report had no actual pulse provenance");
    }
    private static void focusAndXray(){
        Battle b=new Battle(92379);
        Battle.Ship[] pair=duel(b);
        Battle.Ship unrelated=b.addShip(1,Battle.Faction.DRAXIAN,600,180);
        b.registerDiagnosticStationaryTarget(unrelated.id);
        pair[0].sensorRange=900;pair[0].xrayRange=700;
        b.configureSensorScan(pair[0].id,SensorActiveScan.Mode.FOCUSED_RADAR,
                pair[1].id);
        ok((b.directSensorChannels(pair[0],pair[1],500)&Battle.CONTACT_RADAR)!=0&&
                        (b.directSensorChannels(pair[0],unrelated,507)&Battle.CONTACT_RADAR)==0,
                "Focused radar painted another target outside its commanded focus");
        b.step();
        ok(pair[0].scan.emissions==1&&
                        pair[0].scan.emittedFor(1,pair[1].id,Battle.CONTACT_RADAR)&&
                        !pair[0].scan.emittedFor(1,unrelated.id,Battle.CONTACT_RADAR),
                "Focused emitter failed to bind its real pulse to the target");
        Battle x=new Battle(92383);
        Battle.Ship[] xx=duel(x);
        xx[0].sensorRange=0;xx[0].xrayRange=500;
        x.configureSensorScan(xx[0].id,SensorActiveScan.Mode.XRAY_BURST,xx[1].id);
        ok((x.directSensorChannels(xx[0],xx[1],500)&Battle.CONTACT_XRAY)!=0,
                "High-output X-ray profile failed installed XRY range expansion");
        x.step();
        ok(xx[0].scan.emissions==1&&xx[0].scan.powerSpent>=12&&
                        xx[0].scan.heatGenerated>=7.5,
                "X-ray probing did not use its high-cost physical pulse");
        int round=2*SensorLightTimeHistory.propagationTicks(500);
        for(int i=1;i<=round;i++)x.step();
        ok(x.hasTargetContact(xx[0],xx[1],500),
                "Physical targeted X-ray echo was never available");
        Battle missing=new Battle(92389);
        Battle.Ship[] absent=duel(missing);
        absent[0].sensorRange=0;absent[0].xrayRange=0;
        int nullScan=missing.directSensorChannels(absent[0],absent[1],0);
        ok((nullScan&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY|
                        Battle.CONTACT_INFRARED|Battle.CONTACT_GRAVITIC))==0,
                "An unfitted zero-range electronic sensor reported contact");
        missing.configureSensorScan(absent[0].id,SensorActiveScan.Mode.XRAY_BURST,
                absent[1].id);
        for(int i=0;i<70;i++)missing.step();
        ok(absent[0].scan.emissions==0&&absent[0].scan.powerSpent==0,
                "A vessel without X-ray hardware acquired a free active sensor");
        try{
            x.configureSensorScan(xx[0].id,SensorActiveScan.Mode.PASSIVE,0);
            throw new AssertionError("Changed scan orders in the middle of battle");
        }catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        passive();
        boosted();
        focusAndXray();
        System.out.println("PASS powered passive, wide radar, focus radar, X-ray probe, pulse clock and no-free-hardware modes");
    }
}
