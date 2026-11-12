package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;

/** Real 200v200 A/B against the exact original full-fleet traversal. */
public final class OpponentIndexParityTest {
    private OpponentIndexParityTest(){}
    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }

    /**
     * Read the real 200v200 sensor and finite-solver state at 1s and 2s.
     * Both variants must observe exactly the same historical contacts. The
     * read-only tactical display must neither create information nor change
     * the battle. Work pools are bounded per vessel, not by an arbitrary
     * runner wall-clock/FPS target.
     */
    private record SensorSnapshot(int contacts,int relayed,int historical,
            long localSolves,long localLimited,long fcmSolves,long fcmLimited){}
    private static SensorSnapshot inspectSensors(Battle battle){
        long local=0,limited=0,shared=0,sharedLimited=0;
        for(Battle.Ship ship:battle.ships()){
            SensorComputeBudget.State own=ship.targetCompute;
            SensorComputeBudget.State fcm=ship.fleetCompute;
            check(own.lastTick()<=battle.tick&&fcm.lastTick()<=battle.tick,
                    "200v200 fire-control computation used a future tick");
            check(Double.isFinite(own.remaining())&&own.remaining()>=0
                            &&own.remaining()<=10+1e-8,
                    "200v200 vessel exceeded finite local compute capacity");
            check(Double.isFinite(fcm.remaining())&&fcm.remaining()>=0
                            &&fcm.remaining()<=5+1e-8,
                    "200v200 FCM minted unlimited shared compute");
            check(own.allocations>=0&&own.exhausted>=0&&
                            own.exhausted<=own.allocations&&
                            fcm.allocations>=0&&fcm.exhausted>=0&&
                            fcm.exhausted<=fcm.allocations,
                    "200v200 solver work counters were inconsistent");
            for(Battle.ContactTrack track:ship.knownTracks.values())
                check(track.tick>=0&&track.tick<=battle.tick&&
                                Double.isFinite(track.x)&&Double.isFinite(track.y),
                        "200v200 stored a future/non-finite sensor track");
            local+=own.allocations;limited+=own.exhausted;
            shared+=fcm.allocations;sharedLimited+=fcm.exhausted;
        }
        long unchanged=battle.fingerprint();
        int contacts=0,relayed=0,historical=0;
        for(int team=0;team<2;team++){
            Battle.Ship viewer=battle.tacticalViewer(team);
            if(viewer==null)continue;
            for(Battle.TacticalContact report:battle.tacticalPicture(team,viewer.id)){
                check(report.observedTick()<=battle.tick&&
                                report.receivedTick()<=battle.tick&&
                                report.emittedTick()<=report.observedTick(),
                        "200v200 command scope received future/unsent evidence");
                check(Double.isFinite(report.x())&&Double.isFinite(report.y())&&
                                Double.isFinite(report.uncertainty()),
                        "200v200 command scope received invalid observations");
                contacts++;
                if(report.relayed())relayed++;
                if(report.stale())historical++;
            }
        }
        check(battle.fingerprint()==unchanged,
                "Reading 200v200 command contacts changed live combat");
        return new SensorSnapshot(contacts,relayed,historical,
                local,limited,shared,sharedLimited);
    }


    /**
     * Two real 30Hz seconds using the paid DREADNOUGHT RDR/SHR/RES/TRK/LNK/
     * FCM/CAP fleet. Complement the separate ordinary demonstration A/B;
     * the latter deliberately has no such network equipment installed.
     * This is a functional causality/budget gate, never a GPU/FPS threshold.
     */
    private static void inspectFittedOpening(TechnologyCatalog catalog){
        Battle fitted=FleetSensorPhotonScaleDiagnostic.create(
                catalog,200,2026110308L);
        check(fitted.ships().size()==400 && fitted.alive(0)==200 &&
                        fitted.alive(1)==200,
                "Fitted sensor test did not construct 200v200 real ships");
        int fcmHulls=0,capHulls=0;
        for(Battle.Ship ship:fitted.ships()){
            if(ship.network.commandCapacity()>0)fcmHulls++;
            if(ship.network.targetCapacity()>1)capHulls++;
        }
        check(fcmHulls>0&&capHulls>0,
                "200v200 opening lacks physical FCM and CAP hardware");
        SensorSnapshot previous=null;
        for(int i=1;i<=60;i++){
            fitted.step();
            if(i!=30&&i!=60)continue;
            check(fitted.tick==i,"200v200 sensor simulation did not advance");
            SensorSnapshot snapshot=inspectSensors(fitted);
            if(previous!=null)
                check(snapshot.localSolves()>=previous.localSolves()&&
                                snapshot.fcmSolves()>=previous.fcmSolves(),
                        "200v200 work-counter history regressed");
            previous=snapshot;
            System.out.println("MEASURE fitted 200v200 30Hz sensor-budget tick="+
                    i+" FCM_hulls="+fcmHulls+" CAP_hulls="+capHulls+
                    " "+snapshot);
        }
    }

    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Battle indexed=DemonstrationBattle.create(catalog);
        Battle fullScan=DemonstrationBattle.create(catalog);
        fullScan.configureOpponentIndexForDiagnostic(false);
        // Opt-in event counts have no wall-clock calls or changes to captain
        // decisions. The normal uninstrumented game leaves counters absent.
        indexed.configureCaptainTargetingMetrics(true);
        fullScan.configureCaptainTargetingMetrics(true);
        int steps=90;
        long indexedNs=0,fullNs=0;
        for(int i=0;i<steps;i++){
            long firstElapsed,secondElapsed,started;
            // Alternate execution order to counter JIT/CPU-frequency bias.
            if((i&1)==0){
                started=System.nanoTime();indexed.step();
                firstElapsed=System.nanoTime()-started;
                started=System.nanoTime();fullScan.step();
                secondElapsed=System.nanoTime()-started;
            }else{
                started=System.nanoTime();fullScan.step();
                secondElapsed=System.nanoTime()-started;
                started=System.nanoTime();indexed.step();
                firstElapsed=System.nanoTime()-started;
            }
            if(i>=20){indexedNs+=firstElapsed;fullNs+=secondElapsed;}
            check(indexed.tick==fullScan.tick,
                    "Indexed traversal changed combat clock");
            check(indexed.fingerprint()==fullScan.fingerprint(),
                    "Indexed traversal changed deterministic battle at tick "+
                            indexed.tick);
            if(indexed.tick==30||indexed.tick==60){
                SensorSnapshot observed=inspectSensors(indexed);
                SensorSnapshot reference=inspectSensors(fullScan);
                check(observed.equals(reference),
                        "200v200 sensor or finite fire-control state diverged at tick "+
                                indexed.tick+": indexed="+observed+
                                " full-scan="+reference);
                System.out.println("MEASURE 200v200 sensor-budget tick="+
                        indexed.tick+" "+observed);
            }
        }
        check(indexed.ships().size()==400,
                "A/B must contain the real 200v200 fixture");
        var indexedTargeting=indexed.captainTargetingMetrics();
        var fullTargeting=fullScan.captainTargetingMetrics();
        check(indexedTargeting.decisions()>0&&
                indexedTargeting.fullSearches()>0&&
                indexedTargeting.receivedContacts()>0,
                "Opt-in captain diagnostic must observe real contact searches");
        check(indexedTargeting.decisions()==indexedTargeting.retained()+
                        indexedTargeting.fullSearches(),
                "Retained versus full target decisions are not exhaustive");
        check(indexedTargeting.eligibleCandidates()>=
                        indexedTargeting.authorizedContacts()&&
                indexedTargeting.fullSearches()>=
                        indexedTargeting.selectedContacts(),
                "Candidate/contact counters exceeded real authorization opportunities");
        check(indexedTargeting.decisions()==fullTargeting.decisions()&&
                indexedTargeting.retained()==fullTargeting.retained()&&
                indexedTargeting.fullSearches()==fullTargeting.fullSearches()&&
                indexedTargeting.eligibleCandidates()==
                        fullTargeting.eligibleCandidates()&&
                indexedTargeting.authorizedContacts()==
                        fullTargeting.authorizedContacts()&&
                indexedTargeting.selectedContacts()==fullTargeting.selectedContacts(),
                "Indexed scan changed real captain target eligibility/selection");
        check(fullTargeting.candidateSlots()==
                        indexedTargeting.candidateSlots()*2,
                "Indexed 200v200 opponent scan must visit half as many slots");
        System.out.println("MEASURE captain-targeting 200v200 indexed="+
                indexedTargeting+" fullFleet="+fullTargeting+
                " (counts functional; timings JVM diagnostic only)");
        inspectFittedOpening(catalog);
        System.out.println("PASS opponent-index 200v200 parity ticks="+steps+
                " fingerprint="+Long.toHexString(indexed.fingerprint())+
                " indexedMs="+indexedNs/1e6+
                " fullFleetScanMs="+fullNs/1e6+
                " (same-run JVM timing diagnostic only)");
    }
}
