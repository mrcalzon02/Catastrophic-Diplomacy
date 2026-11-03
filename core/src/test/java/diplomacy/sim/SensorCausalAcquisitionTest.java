package diplomacy.sim;

/** Functional proof: STANDARD fire control has no instantaneous enemy sight. */
public final class SensorCausalAcquisitionTest {
    private SensorCausalAcquisitionTest(){}
    private static void require(boolean test,String reason){
        if(!test)throw new AssertionError(reason);
    }
    private static Battle.Ship[] stationaryDuel(long seed,double separation){
        Battle battle=new Battle(seed);
        Battle.Ship observer=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,100+separation,100);
        battle.registerDiagnosticStationaryTarget(observer.id);
        battle.registerDiagnosticStationaryTarget(target.id);
        observer.power=100;target.power=100;
        observer.infraredRange=observer.xrayRange=observer.graviticRange=0;
        return new Battle.Ship[]{observer,target};
    }
    private static void radarEcho(){
        Battle battle=new Battle(93571);
        Battle.Ship source=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,600,100);
        battle.registerDiagnosticStationaryTarget(source.id);
        battle.registerDiagnosticStationaryTarget(target.id);
        source.power=100;
        source.sensorRange=700;
        source.infraredRange=source.xrayRange=source.graviticRange=0;
        int roundTrip=2*SensorLightTimeHistory.propagationTicks(500);
        require(!battle.hasTargetContact(source,target,500),
                "STANDARD fire control granted a time-zero radar lock");
        for(int tick=1;tick<roundTrip;tick++){
            battle.step();
            require(!battle.hasTargetContact(source,target,500),
                    "STANDARD fired before its active radar echo could return (tick "+tick+")");
        }
        battle.step();
        require(battle.hasTargetContact(source,target,500),
                "STANDARD did not acquire a physically arrived radar echo");
        require(source.knownTracks.isEmpty(),
                "Bare hull with no installed TRK silently received permanent track memory");
        Battle.TacticalContact reported=battle.tacticalPicture(0,source.id).stream()
                .filter(fix->fix.targetId()==target.id).findFirst().orElse(null);
        require(reported!=null&&reported.observedTick()<battle.tick&&
                reported.emittedTick()<reported.observedTick()&&
                reported.receivedTick()==battle.tick,
                "Commander and weapon lock disagree about physical observation chronology");
        source.power=0;
        require(!battle.hasTargetContact(source,target,500),
                "Unpowered radar still authorized fresh fire control");
        System.out.println("PASS STANDARD radar obeys physical roundtrip, powered hardware and no-free-TRK");
    }
    private static void passiveOptical(){
        Battle battle=new Battle(93573);
        Battle.Ship observer=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,170,100);
        battle.registerDiagnosticStationaryTarget(observer.id);
        battle.registerDiagnosticStationaryTarget(target.id);
        observer.sensorRange=0;
        observer.infraredRange=observer.xrayRange=observer.graviticRange=0;
        observer.power=0; // Plain optical observation does not require radar power.
        require(!battle.hasTargetContact(observer,target,70),
                "Passive optical sensor detected before photon arrival");
        int lightTicks=SensorLightTimeHistory.propagationTicks(70);
        for(int tick=1;tick<lightTicks;tick++){
            battle.step();
            require(!battle.hasTargetContact(observer,target,70),
                    "Optical sight preceded one-way light travel");
        }
        battle.step();
        require(battle.hasTargetContact(observer,target,70),
                "Nearby passive optical detection failed after photon arrival");
        System.out.println("PASS non-powered passive optics cannot outrun light");
    }
    public static void main(String[] args){
        radarEcho();
        passiveOptical();
        System.out.println("PASS causal STANDARD acquisition and passive optical contact");
    }
}
