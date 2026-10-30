package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
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
        check(seen.size()>=3&&hulls.size()>=6&&environments.size()>=3
                &&tiers.size()>=3&&loads.size()>=9,
                "Insufficient faction, hull, environment, tier or module variation");
        System.out.println("PASS screensaver seeded fitted battles with rare optional support");
    }
}
