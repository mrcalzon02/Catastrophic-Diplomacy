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

    /**
     * Paid SHR/TRK/FCM/CAP vessels must scan the same causally reachable
     * enemy contacts when traversing only physically hostile ship indexes.
     * The global-list A/B preserves original insertion ordering, delayed
     * photons, bounded relay bandwidth and real command recipient status.
     */
    private static void verifyRelayScanIndex(TechnologyCatalog catalog){
        long seed=20261112077L;
        Battle indexed=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        Battle full=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        full.configureOpponentIndexForDiagnostic(false);
        indexed.configureRelayScanMetrics(true);
        full.configureRelayScanMetrics(true);
        for(int t=0;t<65;t++){
            indexed.step();full.step();
            check(indexed.fingerprint()==full.fingerprint(),
                    "Fitted SHR/TRK command relay changed physical battle at tick "+
                    indexed.tick);
            if(indexed.tick==30||indexed.tick==60){
                SensorSnapshot a=inspectSensors(indexed);
                SensorSnapshot b=inspectSensors(full);
                check(a.equals(b),
                        "Indexed causal relay changed actual tactical display at "+
                        indexed.tick+" indexed="+a+" legacy="+b);
            }
        }
        Battle.RelayScanMetrics a=indexed.relayScanMetrics();
        Battle.RelayScanMetrics b=full.relayScanMetrics();
        check(a.activeScouts()>0&&a.receivedQueries()>0&&
                a.validContacts()>0,
                "Fitted relay index test did not read any physical photon returns");
        check(a.activeScouts()==b.activeScouts()&&
                a.receivedQueries()==b.receivedQueries()&&
                a.validContacts()==b.validContacts(),
                "Hostile index gained/lost actual scout/target contacts");
        check(b.candidateSlots()==a.candidateSlots()*2,
                "Fitted 16v16 relay should visit half as many global ship slots");
        System.out.println("PASS real fitted SHR/TRK/FCM/CAP relay index "+
                "16v16 65 ticks fingerprint="+
                Long.toHexString(indexed.fingerprint())+
                " indexed="+a+" fullFleet="+b+
                " (candidate counts functional; speed advisory only)");
    }

    /** Physical FCM command cells assign at most their fitted recipient
     * capacity. An ordered friendly index must reproduce the old global scan
     * even when a command emitter loses power or health mid-engagement. */
    private static void verifyCommandRecipientsIndex(TechnologyCatalog catalog){
        long seed=20261112088L;
        Battle indexed=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        Battle full=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        full.configureCommandRecipientIndexForDiagnostic(false);
        indexed.configureCommandRecipientIndexForDiagnostic(true);
        for(int t=0;t<65;t++){
            if(t==34){
                for(int side=0;side<2;side++){
                    for(Battle battle:new Battle[]{indexed,full}){
                        for(Battle.Ship ship:battle.ships())
                            if(ship.team==side&&ship.network.commandCapacity()>0){
                                // Both variants lose the very same physical
                                // command circuit; remaining allied hubs
                                // must take over without a ghost controller.
                                ship.commandCondition=0;break;
                            }
                    }
                }
            }
            indexed.step();full.step();
            check(indexed.fingerprint()==full.fingerprint(),
                    "FCM indexed recipient assignment changed battle at tick "+
                    indexed.tick);
            if(indexed.tick==30||indexed.tick==60)
                for(int i=0;i<indexed.ships().size();i++){
                    Battle.Ship a=indexed.ships().get(i);
                    Battle.Ship b=full.ships().get(i);
                    check(Double.doubleToLongBits(a.commandAssist)==
                            Double.doubleToLongBits(b.commandAssist)&&
                            a.commandSourceId==b.commandSourceId,
                        "Power-damaged FCM command source differs for ship "+a.id);
                }
        }
        var fast=indexed.commandRecipientMetrics();
        var legacy=full.commandRecipientMetrics();
        check(fast.activeCommanders()>0&&fast.assignments()>0&&
                fast.activeCommanders()==legacy.activeCommanders()&&
                fast.liveAllies()==legacy.liveAllies()&&
                fast.assignments()==legacy.assignments(),
                "FCM index altered the recipients or physical command assistance");
        check(legacy.candidateSlots()==fast.candidateSlots()*2,
                "FCM friendly-team index should halve scanned global slots");
        System.out.println("PASS fitted 16v16 command-cell recipient index 65 ticks "+
                "with disabled FCM damage: indexed="+fast+" fullFleet="+legacy+
                " fingerprint="+Long.toHexString(indexed.fingerprint())+
                " (visits diagnostic, not FPS gate)");
    }

    /**
     * Actual cooperative fire control must track each ship's selected target
     * with EXACTLY the same signed counts when a captain retargets, a focus
     * target dies, new ships arrive, or orders change mid-battle. The old map
     * remains available only for a same-seed A/B; release performance is
     * advisory, but real selected targets/fingerprints are functional.
     */
    private static void verifyCooperativeFocusIndex(TechnologyCatalog catalog){
        final long seed=202611121711L;
        Battle indexed=Battle.duel(seed,24,Battle.Faction.TERRAN,
                Battle.Faction.DRAXIAN);
        Battle reference=Battle.duel(seed,24,Battle.Faction.TERRAN,
                Battle.Faction.DRAXIAN);
        reference.configureFocusIndexForDiagnostic(false);
        indexed.configureFocusIndexForDiagnostic(true);
        for(Battle battle:new Battle[]{indexed,reference}){
            for(Battle.Ship ship:battle.ships()){
                ship.maxHull=ship.hull=50000;
                ship.missileAmmo=64;ship.maxMissileAmmo=64;
            }
            for(int team=0;team<2;team++)
                battle.setTeamOrders(team,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.COOPERATE));
        }
        for(int t=0;t<165;t++){
            if(t==52){
                // One more physical friendly combatant must be addressable by
                // the primitive index without losing existing focus values.
                indexed.addShip(0,Battle.Faction.TERRAN,120,290);
                reference.addShip(0,Battle.Faction.TERRAN,120,290);
            }
            if(t==82){
                // Controlled deletion exercises rebuilding after the focused
                // hostile is no longer alive; it is a diagnostic fixture only.
                indexed.ships().get(30).hull=0;
                reference.ships().get(30).hull=0;
            }
            if(t==96){
                indexed.setTeamOrders(1,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.VULTURE));
                reference.setTeamOrders(1,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.VULTURE));
            }
            if(t==122){
                indexed.setTeamOrders(1,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.COOPERATE));
                reference.setTeamOrders(1,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.COOPERATE));
            }
            indexed.step();reference.step();
            check(indexed.fingerprint()==reference.fingerprint(),
                    "Primitive focus changed actual targeting at tick "+
                            indexed.tick);
            for(int i=0;i<indexed.ships().size();i++){
                Battle.Ship ship=indexed.ships().get(i);
                Battle.Ship twin=reference.ships().get(i);
                check(ship.id==twin.id&&ship.lastTargetId==twin.lastTargetId,
                        "Retargeted captain differs at tick "+indexed.tick);
                check(indexed.focusCountForDiagnostic(ship.id)==
                        reference.focusCountForDiagnostic(ship.id),
                        "Cooperative target focus count diverged for id="+
                                ship.id+" tick="+indexed.tick);
            }
        }
        var fast=indexed.focusWorkMetrics();
        var old=reference.focusWorkMetrics();
        check(fast.rebuildEntries()==old.rebuildEntries()&&
                fast.lookups()==old.lookups()&&
                fast.transitions()==old.transitions()&&
                fast.lookups()>0&&fast.transitions()>0,
                "Focus-index A/B never exercised real cooperation/retargeting");
        System.out.println("PASS COOPERATE primitive focus index 24v24 165 ticks"+
                " reinforcement, death, VULTURE switch: "+fast+
                " fingerprint="+Long.toHexString(indexed.fingerprint()));
        // Actual 200v200 fitted/fighter/PD mass battle remains deterministic
        // when both fleets cooperate, not just the small legacy hull fixture.
        Battle dense=DemonstrationBattle.create(catalog);
        Battle denseMap=DemonstrationBattle.create(catalog);
        denseMap.configureFocusIndexForDiagnostic(false);
        for(Battle battle:new Battle[]{dense,denseMap})
            for(int team=0;team<2;team++)
                battle.setTeamOrders(team,BattleOrders.DEFAULT.withTargeting(
                        BattleOrders.Targeting.COOPERATE));
        for(int t=0;t<50;t++){
            dense.step();denseMap.step();
            check(dense.fingerprint()==denseMap.fingerprint(),
                    "200v200 fleet cooperation changed core battle tick "+
                            dense.tick);
            if(t==29||t==49)
                for(Battle.Ship ship:dense.ships())
                    check(dense.focusCountForDiagnostic(ship.id)==
                            denseMap.focusCountForDiagnostic(ship.id),
                            "200v200 focus array/map mismatch at tick "+
                                    dense.tick+" id="+ship.id);
        }
        System.out.println("PASS COOPERATE focus 200v200 exact replay "+
                "ticks=50 fingerprint="+Long.toHexString(dense.fingerprint()));
    }

    /**
     * Actual arrived photon bands are a finite subset of the five fitted
     * sensor families. Excluding impossible bands from historical signature
     * validation must produce bit-identical delayed contacts and targeting,
     * including powered, passive, focused and unlicensed X-ray modes.
     */
    private static void verifyHistoricalChannelMask(TechnologyCatalog catalog){
        long seed=202611121809L;
        Battle selective=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        Battle original=FleetSensorPhotonScaleDiagnostic.create(catalog,16,seed);
        original.configureHistoricalChannelsForDiagnostic(false);
        selective.configureHistoricalChannelsForDiagnostic(true);
        for(Battle battle:new Battle[]{selective,original}){
            int[] targets={0,0};
            for(Battle.Ship ship:battle.ships())if(targets[ship.team]==0)
                targets[ship.team]=ship.id;
            for(Battle.Ship ship:battle.ships()){
                int target=targets[1-ship.team];
                SensorActiveScan.Mode mode=switch(ship.id%5){
                    case 0 -> SensorActiveScan.Mode.PASSIVE;
                    case 1 -> SensorActiveScan.Mode.BOOSTED_RADAR;
                    case 2 -> SensorActiveScan.Mode.FOCUSED_RADAR;
                    case 3 -> SensorActiveScan.Mode.XRAY_BURST;
                    default -> SensorActiveScan.Mode.STANDARD;
                };
                battle.configureSensorScan(ship.id,mode,
                        mode==SensorActiveScan.Mode.FOCUSED_RADAR||
                        mode==SensorActiveScan.Mode.XRAY_BURST?target:0);
            }
        }
        for(int i=0;i<80;i++){
            selective.step();original.step();
            check(selective.fingerprint()==original.fingerprint(),
                    "Selective historical channels changed fitted battle at tick "+
                            selective.tick);
            if(selective.tick==30||selective.tick==60){
                var fast=inspectSensors(selective);
                var full=inspectSensors(original);
                check(fast.equals(full),
                        "Historical channel mask changed actual causal picture "+
                                selective.tick+" "+fast+" vs "+full);
            }
        }
        var a=selective.historicalChannelMetrics();
        var b=original.historicalChannelMetrics();
        check(a.validHistoryQueries()>0&&
                a.validHistoryQueries()==b.validHistoryQueries()&&
                a.possibleFamilyChecks()==b.possibleFamilyChecks()&&
                a.executedFamilyChecks()<b.executedFamilyChecks()&&
                b.executedFamilyChecks()==b.possibleFamilyChecks(),
                "Historical channel index failed to skip absent sensor bands "+
                        a+" / "+b);
        System.out.println("PASS selective historical photon bands fitted "+
                "16v16 ticks=80 optimized="+a+" original="+b+
                " fingerprint="+Long.toHexString(selective.fingerprint())+
                " (band checks diagnostic only)");
        // The original combat 200v200 fixture exercises standard RDR,
        // infrared and passive optical without requiring paid research.
        Battle large=DemonstrationBattle.create(catalog);
        Battle largeOld=DemonstrationBattle.create(catalog);
        largeOld.configureHistoricalChannelsForDiagnostic(false);
        large.configureHistoricalChannelsForDiagnostic(true);
        for(int i=0;i<36;i++){
            large.step();largeOld.step();
            check(large.fingerprint()==largeOld.fingerprint(),
                    "200v200 historical-channel optimization changed battle tick "+
                            large.tick);
        }
        System.out.println("PASS historical sensor-channel 200v200 parity "+
                "ticks=36 fingerprint="+Long.toHexString(large.fingerprint()));
    }

    /**
     * Exact reference comparator parity under repeated IDs, identical
     * observation epochs, stale/future reports, and LNK capacities 1..32.
     * No game uses these artificial reports; they are adversarial inputs to
     * the pure bounded selector, not fabricated in-battle sensor knowledge.
     */
    private static void verifyTrackPacketTopKEdges(){
        java.util.Random rng=new java.util.Random(0x07ACCA11L);
        int[] ids=new int[SensorTrackRelayBuffer.MAX_CONTACTS];
        Battle.ContactTrack[] selected=
                new Battle.ContactTrack[SensorTrackRelayBuffer.MAX_CONTACTS];
        int cases=0;
        int[] caps={0,1,2,3,4,7,8,16,31,32};
        for(int trial=0;trial<900;trial++){
            java.util.Map<Integer,Battle.ContactTrack> tracks=
                    new java.util.HashMap<>();
            long now=500+trial%13;
            int size=trial%7==0?145:rng.nextInt(85);
            for(int j=0;j<size;j++){
                // Many tracks have exactly equal timestamps: the ID ASC
                // secondary sort must be stable despite HashMap iteration.
                long at=now+(j%3==0?-(j%9):rng.nextInt(100)-26);
                tracks.put(j+1,new Battle.ContactTrack(
                        new SensorLightTimeHistory.Sample(at,
                                100+j,200-j,0,0,true)));
            }
            int cap=caps[trial%caps.length];
            int window=trial%11==0?0:1+rng.nextInt(55);
            java.util.List<java.util.Map.Entry<Integer,Battle.ContactTrack>>
                    full=new java.util.ArrayList<>(tracks.entrySet());
            full.removeIf(e->now-e.getValue().tick<0||
                    now-e.getValue().tick>window);
            full.sort((a,b)->{
                int newer=Long.compare(b.getValue().tick,a.getValue().tick);
                return newer!=0?newer:Integer.compare(a.getKey(),b.getKey());
            });
            int count=TrackPacketTopK.select(tracks,now,window,cap,
                    ids,selected);
            check(count==StrictMath.min(cap,full.size()),
                    "Top-K selected incorrect LNK report count in trial "+trial);
            for(int k=0;k<count;k++)
                check(ids[k]==full.get(k).getKey()&&
                        selected[k]==full.get(k).getValue(),
                        "Top-K reordered historical contact/ID on trial "+
                                trial+" at sorted index "+k);
            cases++;
        }
        boolean invalid=false;
        try{TrackPacketTopK.select(java.util.Map.of(),10,5,33,
                ids,selected);}
        catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"Excess LNK capacity bypassed finite socket limit");
        System.out.println("PASS LNK bounded top-K comparator parity "+
                cases+" randomized tie, stale, future and capacity cases");
    }

    /**
     * Physically fitted TRK/LNK transmitters must produce exactly the same
     * ordered finite packets as the original copy/filter/sort, including
     * delayed reception, a midbattle sensor failure and reactivation.
     */
    private static void verifyTrackPacketTopK(TechnologyCatalog catalog){
        verifyTrackPacketTopKEdges();
        for(int perSide:new int[]{16,200}){
            final long seed=202611121910L+perSide;
            Battle bounded=FleetSensorPhotonScaleDiagnostic.create(
                    catalog,perSide,seed);
            Battle full=FleetSensorPhotonScaleDiagnostic.create(
                    catalog,perSide,seed);
            bounded.configureTrackPacketSelectionForDiagnostic(true);
            full.configureTrackPacketSelectionForDiagnostic(false);
            int damagedId=0;
            for(Battle.Ship ship:bounded.ships())
                if(ship.network.trackRelayCapacity()>0){
                    damagedId=ship.id;break;
                }
            check(damagedId>0,"Real fitted LNK fixture has no transmitter");
            final int steps=perSide==16?78:40;
            for(int t=0;t<steps;t++){
                if(t==28||t==37){
                    for(Battle battle:new Battle[]{bounded,full}){
                        for(Battle.Ship ship:battle.ships())
                            if(ship.id==damagedId){
                                ship.sensorsCondition=t==28?0:1;
                                break;
                            }
                    }
                }
                bounded.step();full.step();
                check(bounded.fingerprint()==full.fingerprint(),
                        "LNK top-K changed physical battle in "+perSide+
                                "v"+perSide+" at tick "+bounded.tick);
                for(int i=0;i<bounded.ships().size();i++){
                    Battle.Ship a=bounded.ships().get(i);
                    Battle.Ship b=full.ships().get(i);
                    check(a.trackPackets.fingerprint()==
                            b.trackPackets.fingerprint(),
                            "LNK packet ID/order/epoch changed at tick "+
                                    bounded.tick+" emitter="+a.id);
                }
                if(bounded.tick==30||bounded.tick==60){
                    SensorSnapshot fast=inspectSensors(bounded);
                    SensorSnapshot legacy=inspectSensors(full);
                    check(fast.equals(legacy),
                            "LNK changed causal command picture at tick "+
                                    bounded.tick);
                }
            }
            var fast=bounded.trackPacketSelectionMetrics();
            var old=full.trackPacketSelectionMetrics();
            check(fast.transmitters()>0&&fast.packetsSent()>0&&
                    fast.transmitters()==old.transmitters()&&
                    fast.scannedContacts()==old.scannedContacts()&&
                    fast.packetsSent()==old.packetsSent()&&
                    fast.copiedEntries()==0&&old.copiedEntries()>0,
                    "Bounded LNK selection failed to preserve real packets "+
                            fast+" compared with "+old);
            System.out.println("PASS real fitted LNK "+perSide+"v"+perSide+
                    " top-K A/B steps="+steps+" indexed="+fast+
                    " fullSort="+old+" fingerprint="+
                    Long.toHexString(bounded.fingerprint())+
                    " (work counts diagnostic only)");
        }
    }

    /**
     * Physically fitted PASSIVE/FOCUSED_RADAR/XRAY_BURST targets can never
     * produce an active photon return for an unselected contact. The old
     * age-by-age scan is a causality reference, not a desired CPU workload.
     * A/B state, actual command reports and emission costs remain identical.
     */
    private static void verifyImpossibleActivePhotonPruning(
            TechnologyCatalog catalog){
        long reducedSlots=0,legacySlots=0,prunedRequests=0;
        for(int perSide:new int[]{16,200}){
            long seed=202611122032L+perSide;
            Battle fast=FleetSensorPhotonScaleDiagnostic.create(catalog,perSide,seed);
            Battle original=FleetSensorPhotonScaleDiagnostic.create(catalog,perSide,seed);
            fast.configureActivePhotonPruningForDiagnostic(true);
            original.configureActivePhotonPruningForDiagnostic(false);
            for(Battle battle:new Battle[]{fast,original}){
                int[] firstEnemy={0,0};
                for(Battle.Ship ship:battle.ships())
                    if(firstEnemy[ship.team]==0)firstEnemy[ship.team]=ship.id;
                for(Battle.Ship ship:battle.ships()){
                    SensorActiveScan.Mode mode=switch(ship.id%5){
                        case 0 -> SensorActiveScan.Mode.PASSIVE;
                        case 1 -> SensorActiveScan.Mode.FOCUSED_RADAR;
                        case 2 -> SensorActiveScan.Mode.XRAY_BURST;
                        case 3 -> SensorActiveScan.Mode.BOOSTED_RADAR;
                        default -> SensorActiveScan.Mode.STANDARD;
                    };
                    int focus=mode==SensorActiveScan.Mode.FOCUSED_RADAR||
                            mode==SensorActiveScan.Mode.XRAY_BURST?
                                    firstEnemy[1-ship.team]:0;
                    battle.configureSensorScan(ship.id,mode,focus);
                }
            }
            int ticks=perSide==16?72:35;
            for(int i=0;i<ticks;i++){
                fast.step();original.step();
                check(fast.fingerprint()==original.fingerprint(),
                        "Active photon pruning changed fitted "+perSide+
                                "v"+perSide+" battle at tick "+fast.tick);
                if(i==29||i==59){
                    for(int team=0;team<2;team++)
                        check(fast.tacticalPicture(team).equals(
                                original.tacticalPicture(team)),
                                "Pruned photon ages fabricated/staled "+
                                "a tactical report at tick "+fast.tick);
                }
            }
            var a=fast.activePhotonScanMetrics();
            var b=original.activePhotonScanMetrics();
            check(a.requests()>0&&a.requests()==b.requests()&&
                    a.impossibleActive()>0&&
                    a.historySlotsExamined()<b.historySlotsExamined()&&
                    b.impossibleActive()==0,
                    "Skipping an impossible active emitter failed to "+
                            "preserve actual request count or reduce work "+
                            a+" / "+b);
            reducedSlots+=a.historySlotsExamined();
            legacySlots+=b.historySlotsExamined();
            prunedRequests+=a.impossibleActive();
            System.out.println("PASS physically fitted active-return pruning "+
                    perSide+"v"+perSide+" ticks="+ticks+
                    " optimized="+a+" original="+b+" fingerprint="+
                    Long.toHexString(fast.fingerprint())+
                    " (history counts diagnostic only)");
        }
        check(prunedRequests>0&&reducedSlots<legacySlots,
                "Active-emission pruning never reduced historical age scans");
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
                indexedTargeting.authorizedContacts()>0,
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
        verifyRelayScanIndex(catalog);
        verifyCommandRecipientsIndex(catalog);
        verifyCooperativeFocusIndex(catalog);
        verifyHistoricalChannelMask(catalog);
        verifyTrackPacketTopK(catalog);
        verifyImpossibleActivePhotonPruning(catalog);
        inspectFittedOpening(catalog);
        System.out.println("PASS opponent-index 200v200 parity ticks="+steps+
                " fingerprint="+Long.toHexString(indexed.fingerprint())+
                " indexedMs="+indexedNs/1e6+
                " fullFleetScanMs="+fullNs/1e6+
                " (same-run JVM timing diagnostic only)");
    }
}
