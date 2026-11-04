package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Diagnostic-only seed replay, real fitting, tier, and fleet-diversity coverage. */
public final class ScreensaverBattleTest {
    private static void check(boolean result,String why){
        if(!result)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Set<Battle.Faction> seen=new HashSet<>();
        Set<Shipyard.Hull> hulls=new HashSet<>();
        Set<Integer> environments=new HashSet<>();
        Set<Integer> tiers=new HashSet<>();
        Set<String> loads=new HashSet<>();
        for(long seed:new long[]{7L,97L,327L,9420L,438731L,8293191L}){
            ScreensaverBattle.Round round=ScreensaverBattle.create(seed,catalog);
            ScreensaverBattle.Round replay=ScreensaverBattle.create(seed,catalog);
            check(round.left()!=round.right(),"Both fleets use the same faction");
            check(round.environmentLevel()==replay.environmentLevel(),"Background changed for seed");
            check(round.leftTier()==replay.leftTier()&&round.rightTier()==replay.rightTier(),
                    "Technology levels not replay-stable");
            check(round.battle().fingerprint()==replay.battle().fingerprint(),
                    "Initial battle is not deterministically replayable");
            check(round.battle().alive(0)==round.leftFleet().size() &&
                    round.battle().alive(1)==round.rightFleet().size(),"Fleet count not deployed");
            check(round.battle().ships().size()<=400,"Exhibition exceeds 200 per side");
            // Each ship receives an independent, valid, replayable tactical policy.
            // Escort slots are earlier allies only: the generated graph cannot cycle.
            check(round.battle().ships().size()==replay.battle().ships().size(),
                    "Seed replay changed individual ship count");
            for(int side=0;side<2;side++){
                List<Battle.Ship> allies=new ArrayList<>();
                for(Battle.Ship ship:round.battle().ships())
                    if(ship.team==side)allies.add(ship);
                Set<ShipStandingOrders> different=new HashSet<>();
                for(int slot=0;slot<allies.size();slot++){
                    Battle.Ship ship=allies.get(slot);
                    check(ship.standingOrders!=null&&ship.orders.equals(ship.standingOrders.tactics()),
                            "Exhibition captain has no active individual doctrine");
                    different.add(ship.standingOrders);
                    var d=ship.standingOrders.directives();
                    int leader=d.escortSlot();
                    check(leader<slot,"Generated escort relationship is cyclic or self-referential");
                    check(ship.escortShipId==(leader<0?0:allies.get(leader).id),
                            "Escort slot did not resolve to same-team physical ship");
                    List<Integer> expected=new ArrayList<>();
                    for(int targetSlot:d.defendSlots())if(targetSlot>=0){
                        check(targetSlot<allies.size()&&targetSlot!=slot,
                                "Captain protects an invalid or self ship");
                        expected.add(allies.get(targetSlot).id);
                    }
                    check(expected.equals(ship.defendedShipIds),
                            "Defended ship slots were not bound to battle IDs");
                    check(new HashSet<>(expected).size()==expected.size(),
                            "Captain received duplicate protected targets");
                    int index=round.battle().ships().indexOf(ship);
                    Battle.Ship again=replay.battle().ships().get(index);
                    check(ship.standingOrders.equals(again.standingOrders)
                                    &&ship.escortShipId==again.escortShipId
                                    &&ship.defendedShipIds.equals(again.defendedShipIds),
                            "Captains changed orders or protection relationships on replay");
                }
                check(different.size()>1,"All ships on a side share identical orders");
            }
            seen.add(round.left());seen.add(round.right());
            environments.add(round.environmentLevel());
            tiers.add(round.leftTier());tiers.add(round.rightTier());
            for(int side=0;side<2;side++){
                int max=side==0?round.leftTier():round.rightTier();
                var list=side==0?round.leftFleet():round.rightFleet();
                for(Shipyard.Design design:list){
                    check(Shipyard.inspect(design,catalog).valid(),
                            "Invalid screensaver fitting "+design.hull);
                    hulls.add(design.hull);
                    for(int slot=0;slot<design.slots();slot++){
                        Shipyard.Installed part=design.at(slot);
                        if(part==null)continue;
                        loads.add(part.component);
                        for(Shipyard.Track track:Shipyard.Track.values())
                            check(part.stage(track)<=max,"Module exceeds side's technology level");
                    }
                }
            }
            for(int tick=0;tick<30&&!round.battle().finished();tick++)
                round.battle().step();
            check(round.battle().tick>0,"Screensaver simulation did not advance");
        }
        // Intermittent failures occur on random new rounds, not on the six
        // traditional smoke seeds. Exercise additional deterministic starts
        // and the first live combat frames, and print the failing seed.
        // Deliberately create each round independently as on the Android HUD.
        for(long seed=0;seed<48;seed++){
            long replaySeed=0x53435245454E4C00L+seed*7919L;
            try{
                ScreensaverBattle.Round round=ScreensaverBattle.create(replaySeed,catalog);
                check(round.battle().alive(0)>0&&round.battle().alive(1)>0,
                        "Empty random exhibition fleet");
                // Match CatastrophicGame.nextScreensaverRound, including the
                // seeded environment before the first simulation step.
                round.battle().configureWeather(
                        LevelEnvironment.forLevel(round.environmentLevel()).weather());
                for(int tick=0;tick<24&&!round.battle().finished();tick++)
                    round.battle().step();
                check(round.battle().tick>0,"Random exhibition failed before first frame");
            }catch(RuntimeException|AssertionError failure){
                throw new AssertionError("SCREENSAVER CRASH REPRO seed="+
                        Long.toUnsignedString(replaySeed)+" tick=first24",failure);
            }
        }
        // Regression: inverted fleets outside the playable theater cannot
        // indefinitely flee one another when nebula obscures target acquisition.
        // This is the real Battle.step() search, not a test-only target teleport.
        Battle lost=new Battle(0x5EA2C4L);
        Battle.Ship scout=lost.addShip(0,Battle.Faction.TERRAN,1580,335);
        Battle.Ship hunter=lost.addShip(1,Battle.Faction.DRAXIAN,-300,335);
        lost.configureWeather(new SpaceWeather.Preset(1,17,
                SpaceWeather.Kind.NEBULA,SpaceWeather.Kind.CLEAR,1));
        scout.speed=85;hunter.speed=85;
        scout.sensorRange=600;hunter.sensorRange=600;
        // These two captains were already closing with physically valid
        // sublight inertia before the fog obscured their target bearings.
        // Engine acceleration alone cannot close 1880 units in 1300 ticks.
        scout.vx=-20;hunter.vx=20;
        check(StrictMath.abs(scout.vx)<=SublightPropulsion.maximumSpeed(scout)
                        &&StrictMath.abs(hunter.vx)<=SublightPropulsion.maximumSpeed(hunter),
                "Fog search fixture started with superluminal/unfitted velocity");
        double initialGap=StrictMath.hypot(scout.x-hunter.x,scout.y-hunter.y);
        boolean reacquired=false;
        for(int tick=0;tick<1300&&!lost.finished();tick++){
            lost.step();
            if(scout.lastTargetId==hunter.id||hunter.lastTargetId==scout.id){
                reacquired=true;
                break;
            }
        }
        check(reacquired,"Fog-lost ships never reacquired real sensor contact");
        check(StrictMath.hypot(scout.x-hunter.x,scout.y-hunter.y)<initialGap,
                "Search patrol pushed lost ships farther from combat");
        check(scout.fuel<scout.maxFuel&&hunter.fuel<hunter.maxFuel,
                "Active search should consume finite propulsion fuel");

        Battle stationary=new Battle(0xD15AB1EDL);
        Battle.Ship stranded=stationary.addShip(0,Battle.Faction.TERRAN,1580,335);
        Battle.Ship foe=stationary.addShip(1,Battle.Faction.DRAXIAN,-300,335);
        stationary.configureWeather(new SpaceWeather.Preset(1,17,
                SpaceWeather.Kind.NEBULA,SpaceWeather.Kind.CLEAR,1));
        stranded.sensorRange=1;foe.sensorRange=1;
        stranded.fuel=0;
        double strandedX=stranded.x,strandedY=stranded.y;
        for(int tick=0;tick<30;tick++)stationary.step();
        check(stranded.x==strandedX&&stranded.y==strandedY,
                "Empty fuel tank produced artificial search thrust");

        // Both pursuit navigation paths must draw from the same finite
        // onboard reaction-mass reserve. A captain's standing-orders branch
        // must not accidentally make fuel free.
        Battle chase=new Battle(0xF0E1L);
        Battle.Ship standard=chase.addShip(0,Battle.Faction.TERRAN,200,335);
        Battle.Ship ordered=chase.addShip(1,Battle.Faction.DRAXIAN,1070,335);
        ordered.orders=BattleOrders.DEFAULT.withPosition(BattleOrders.Position.KEEP_MOVING);
        double originalStandardFuel=standard.fuel,originalOrderedFuel=ordered.fuel;
        for(int tick=0;tick<60&&!chase.finished();tick++)chase.step();
        check(standard.fuel<originalStandardFuel && ordered.fuel<originalOrderedFuel,
                "Pursuit or ordered steering generated free engine thrust");
        check(standard.fuel>=0 && ordered.fuel>=0,
                "Steering spent more fuel than was physically available");

        Battle extraction=new Battle(0xE5C4FEL);
        Battle.Ship withdrawing=extraction.addShip(0,Battle.Faction.TERRAN,640,335);
        extraction.addShip(1,Battle.Faction.DRAXIAN,1000,335);
        withdrawing.withdrawing=true;
        double preEscapeFuel=withdrawing.fuel;
        for(int tick=0;tick<60;tick++)extraction.step();
        check(withdrawing.fuel<preEscapeFuel,
                "Physical retreat did not debit the engine's fuel reserve");
        check(withdrawing.fuel>=0,"Physical retreat overdrew fuel");

        // A captain ordered to stand off beyond every remaining weapon
        // must still be able to fight with the fitted battery. The inactive
        // missile and torpedo inventories must not create phantom reach.
        Battle reachBattle=new Battle(0xC105EF1E1DL);
        Battle.Ship laserOnly=reachBattle.addShip(0,Battle.Faction.TERRAN,200,335);
        Battle.Ship immobileTarget=reachBattle.addShip(1,Battle.Faction.DRAXIAN,740,335);
        laserOnly.missileDamage=0;laserOnly.torpedoDamage=0;
        laserOnly.kineticDamage=0;laserOnly.plasmaDamage=0;laserOnly.particleDamage=0;
        laserOnly.orders=BattleOrders.DEFAULT
                .withRetreat(0)
                .withEngagement(BattleOrders.Size.ESCORT,100,890);
        immobileTarget.orders=BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD);
        immobileTarget.laserDamage=0;immobileTarget.missileDamage=0;
        immobileTarget.kineticDamage=0;immobileTarget.plasmaDamage=0;
        immobileTarget.particleDamage=0;immobileTarget.torpedoDamage=0;
        // Acceleration is now bounded by actual gee, not the former
        // near-lightspeed instant-position adjustment.
        for(int tick=0;tick<2600&&!reachBattle.finished();tick++)
            reachBattle.step();
        check(StrictMath.hypot(laserOnly.x-immobileTarget.x,
                laserOnly.y-immobileTarget.y)<530,
                "Captain held beyond its last remaining laser's physical range");
        check(reachBattle.hitCount>0,
                "Range correction never produced live laser combat");

        // Even if a vessel's radar hardware is destroyed, a nearby enemy
        // remains optically identifiable; storm visibility is short, not zero.
        Battle opticalBattle=new Battle(0x0F71CA1L);
        Battle.Ship watcher=opticalBattle.addShip(0,Battle.Faction.TERRAN,500,335);
        Battle.Ship silhouette=opticalBattle.addShip(1,Battle.Faction.DRAXIAN,655,335);
        watcher.sensorsCondition=0;silhouette.sensorsCondition=0;
        opticalBattle.configureWeather(new SpaceWeather.Preset(2,17,
                SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.CLEAR,1));
        for(int tick=0;tick<2*SensorLightTimeHistory.propagationTicks(155)+12
                &&watcher.lastTargetId!=silhouette.id;tick++)opticalBattle.step();
        check(watcher.lastTargetId==silhouette.id,
                "Destroyed radar concealed an enemy at direct optical range");

        // Unobserved ships must sweep several patrol lanes rather than
        // permanently waiting at a single sector beacon.
        Battle sweeps=new Battle(0x5EEA22L);
        Battle.Ship patrol=sweeps.addShip(0,Battle.Faction.TERRAN,700,300);
        patrol.sensorRange=1;
        for(int tick=0;tick<240;tick++)sweeps.step();
        double firstX=patrol.x,firstY=patrol.y,firstFuel=patrol.fuel;
        for(int tick=0;tick<300;tick++)sweeps.step();
        check(StrictMath.hypot(firstX-patrol.x,firstY-patrol.y)>.2
                        &&patrol.fuel<firstFuel,
                "Search patrol must navigate changing sectors with real gee-limited thrust");

        // In the same mixed nebula/ion-storm conditions that deadlocked
        // endurance seed 2026103106, ships 260 units apart still have a
        // physical short-range thermal/optical sighting. At 450, no such
        // contact exists: this does not make radar damage irrelevant.
        Battle dense=new Battle(0x10ABC1L);
        Battle.Ship near=dense.addShip(0,Battle.Faction.TERRAN,500,335);
        Battle.Ship far=dense.addShip(1,Battle.Faction.DRAXIAN,760,335);
        near.sensorsCondition=0;far.sensorsCondition=0;
        dense.configureWeather(new SpaceWeather.Preset(198,17,
                SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.NEBULA,1));
        for(int tick=0;tick<2*SensorLightTimeHistory.propagationTicks(260)+12
                &&near.lastTargetId!=far.id;tick++)dense.step();
        check(near.lastTargetId==far.id,
                "Double-interference weather hid an enemy at optical range");
        Battle hidden=new Battle(0x10ABC2L);
        Battle.Ship blind=hidden.addShip(0,Battle.Faction.TERRAN,200,335);
        Battle.Ship unseen=hidden.addShip(1,Battle.Faction.DRAXIAN,650,335);
        blind.sensorsCondition=0;unseen.sensorsCondition=0;
        hidden.configureWeather(new SpaceWeather.Preset(198,17,
                SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.NEBULA,1));
        for(int tick=0;tick<2*SensorLightTimeHistory.propagationTicks(450)+12;tick++)
            hidden.step();
        check(blind.lastTargetId==0,
                "Optical detection leaked enemy position through 450 units of nebula");

        // Automated exhibit: lone disarmed craft depart by real propulsion
        // and are counted as escaped, never silently killed. In ordinary
        // battles the identical unarmed ship stays available to the player.
        Battle exhibition=new Battle(0xE7A51L);
        Battle.Ship pacifist=exhibition.addShip(0,Battle.Faction.TERRAN,5,335);
        exhibition.addShip(1,Battle.Faction.DRAXIAN,1100,335);
        pacifist.laserDamage=0;pacifist.missileDamage=0;
        pacifist.kineticDamage=0;pacifist.plasmaDamage=0;
        pacifist.particleDamage=0;pacifist.torpedoDamage=0;
        pacifist.fighterReserve=0;pacifist.droneReserve=0;
        exhibition.enableUnattendedExhibition();
        for(int tick=0;tick<900&&!exhibition.finished();tick++)exhibition.step();
        check(pacifist.withdrawing &&pacifist.escaped&&pacifist.hull>0,
                "Exhibition's last defenseless captain never physically withdrew");
        check(exhibition.escaped(0)==1 && exhibition.destroyed(0)==0,
                "Voluntary exhibition withdrawal counted as a destroyed ship");
        Battle manual=new Battle(0xE7A52L);
        Battle.Ship manualShip=manual.addShip(0,Battle.Faction.TERRAN,5,335);
        manual.addShip(1,Battle.Faction.DRAXIAN,1100,335);
        manualShip.laserDamage=0;manualShip.missileDamage=0;
        manualShip.kineticDamage=0;manualShip.plasmaDamage=0;
        manualShip.particleDamage=0;manualShip.torpedoDamage=0;
        manualShip.fighterReserve=0;manualShip.droneReserve=0;
        for(int tick=0;tick<15;tick++)manual.step();
        check(!manualShip.withdrawing && !manualShip.escaped,
                "Autonomous exhibition withdrawal leaked into manual battles");

        // Fleet contact sharing requires a real active local spotter. A blind
        // squadmate can receive that identification, but a remote vessel and
        // an unobserved hostile remain hidden through the same dense nebula.
        Battle net=new Battle(0x7A7A1L);
        Battle.Ship spotter=net.addShip(0,Battle.Faction.TERRAN,500,335);
        Battle.Ship squadmate=net.addShip(0,Battle.Faction.TERRAN,380,335);
        Battle.Ship remote=net.addShip(0,Battle.Faction.TERRAN,-1100,335);
        Battle.Ship contact=net.addShip(1,Battle.Faction.DRAXIAN,760,335);
        squadmate.sensorsCondition=0;
        remote.sensorsCondition=0;
        contact.sensorsCondition=0;
        net.configureWeather(new SpaceWeather.Preset(198,17,
                SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.NEBULA,1));
        // Optical observation and local radio packets take nonzero time.
        for(int tick=0;tick<40&&squadmate.lastTargetId!=contact.id;tick++)
            net.step();
        check(spotter.lastTargetId==contact.id,
                "Actual nearby spotter never detected the target");
        check(squadmate.lastTargetId==contact.id,
                "Local squadmate did not receive the real sensor contact");
        check(remote.lastTargetId==0,
                "Fleet network leaked contact beyond local radio reach");

        Battle alone=new Battle(0x7A7A2L);
        Battle.Ship noSpotter=alone.addShip(0,Battle.Faction.TERRAN,380,335);
        Battle.Ship distant=alone.addShip(1,Battle.Faction.DRAXIAN,760,335);
        noSpotter.sensorsCondition=0;distant.sensorsCondition=0;
        alone.configureWeather(new SpaceWeather.Preset(198,17,
                SpaceWeather.Kind.ION_STORM,SpaceWeather.Kind.NEBULA,1));
        for(int tick=0;tick<40;tick++)alone.step();
        check(noSpotter.lastTargetId==0,
                "Fleet network fabricated contact without a detecting ally");

        // Long-running unmanned exhibitions must eventually search outside
        // the central rally rectangle for a genuine stranded enemy.
        // Detection still occurs at short optical range, with no teleport.
        Battle fringe=new Battle(0xF21A6EL);
        Battle.Ship searcher=fringe.addShip(0,Battle.Faction.TERRAN,640,335);
        // A replay of endurance seed 2026103103's isolated LAST_STAND
        // captain, whose actual last observed position was (1257,1448).
        Battle.Ship sentry=fringe.addShip(1,Battle.Faction.DRAXIAN,1257,1448);
        searcher.sensorRange=1;sentry.sensorRange=1;
        // This fixture represents a long-range scout with enough *finite*
        // reaction mass for 24000 actual search ticks; default legacy ships
        // carry just 180 and exhaust themselves before the search expands.
        searcher.maxFuel=1200;searcher.fuel=1200;
        sentry.orders=BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD)
                .withRetreat(0);
        fringe.configureWeather(new SpaceWeather.Preset(1,17,
                SpaceWeather.Kind.NEBULA,SpaceWeather.Kind.CLEAR,1));
        fringe.enableUnattendedExhibition();
        fringe.step();
        check(searcher.lastTargetId==0,
                "Distant stationary enemy appeared without physical observation");
        boolean encountered=false;
        for(int tick=0;tick<24000&&!fringe.finished();tick++){
            fringe.step();
            if(searcher.lastTargetId==sentry.id){encountered=true;break;}
        }
        check(encountered,
                "Extended exhibition search never discovered a stationary fringe ship");
        check(StrictMath.hypot(searcher.x-sentry.x,searcher.y-sentry.y)<340,
                "Fringe enemy was acquired without a real close-range encounter");

        check(seen.size()>=3&&hulls.size()>=6&&environments.size()>=3
                &&tiers.size()>=3&&loads.size()>=9,
                "Insufficient faction, hull, environment, tier or module variation");
        System.out.println("PASS screensaver seeded fitted battles, distinct replayable captain orders and safe escorts");
    }
}
