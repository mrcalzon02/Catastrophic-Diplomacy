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

        check(seen.size()>=3&&hulls.size()>=6&&environments.size()>=3
                &&tiers.size()>=3&&loads.size()>=9,
                "Insufficient faction, hull, environment, tier or module variation");
        System.out.println("PASS screensaver seeded fitted battles, distinct replayable captain orders and safe escorts");
    }
}
