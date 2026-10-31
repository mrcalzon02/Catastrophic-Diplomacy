package diplomacy.sim;

import diplomacy.shipyard.HullProgression;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All 500 map nodes, nine-stage enemy fittings and actual multi-wave battles. */
public final class CampaignOppositionTest {
    private static void check(boolean pass,String explanation){
        if(!pass)throw new AssertionError(explanation);
    }

    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Set<CampaignOpposition.Operation> operations=new HashSet<>();
        Set<String> namedLocations=new HashSet<>();
        for(int mission=1;mission<=500;mission++){
            LevelEconomy.Level level=LevelEconomy.level(mission);
            var map=CampaignMap.page(CampaignMap.pageOf(mission));
            var node=map.get((mission-1)%CampaignMap.PAGE_SIZE);
            check(node.level()==mission&&node.sector()==level.sector(),
                    "Mission map index mismatch "+mission);
            check(node.enemyTier()==(mission-1)/50,
                    "Technology ceiling not aligned with 500-level progression "+mission);
            check(node.operation()==CampaignOpposition.operation(mission),
                    "Map advertises an operation different from its actual battle");
            check(node.sectorFinale()==(mission%50==0),
                    "Missing or misplaced sector capital "+mission);
            check(node.name().contains(node.operation().title),
                    "Map does not identify its battle type "+mission);
            check(node.name().contains(CampaignStoryAtlas.nodeName(mission)),
                    "Map did not display authored location "+mission);
            check(!CampaignStoryAtlas.regionName(mission).isBlank(),
                    "Region is missing for mission "+mission);
            check(!CampaignStoryAtlas.frontObjective(mission).isBlank(),
                    "Front objective is missing for mission "+mission);
            check(namedLocations.add(CampaignStoryAtlas.nodeName(mission)),
                    "Authored location name duplicated "+mission);
            if((mission-1)%10!=9)check(node.opponent()==map.get(9).opponent(),
                    "Opponent changes halfway through a contiguous map page");
            operations.add(node.operation());
        }
        check(namedLocations.size()==500,"Not all 500 locations have individual names");
        check("Glasswake Threshold".equals(CampaignStoryAtlas.nodeName(1)),
                "First mission lost its canonical story location");
        check("Article Zero".equals(CampaignStoryAtlas.nodeName(500)),
                "Final mission lost its canonical story location");
        check(operations.size()==CampaignOpposition.Operation.values().length,
                "Mission categories never appear across the map");
        check(CampaignOpposition.operation(1)==CampaignOpposition.Operation.PATROL,
                "The first mission must introduce the campaign with a patrol");
        for(int front=0;front<CampaignMap.PAGE_COUNT;front++){
            Set<CampaignOpposition.Operation> distinct=new HashSet<>();
            for(var node:CampaignMap.page(front))distinct.add(node.operation());
            check(distinct.size()==CampaignMap.PAGE_SIZE,
                    "Map front repeats a mission archetype instead of offering tactical variety");
        }
        for(int sector=1;sector<=10;sector++){
            int first=(sector-1)*50+1;
            int last=sector*50;
            check(CampaignOpposition.maximumTier(first)==sector-1
                            &&CampaignOpposition.maximumTier(last)==sector-1,
                    "Technology ceiling changed within a chapter");
            check(CampaignOpposition.operation(last)==CampaignOpposition.Operation.SECTOR_CAPITAL,
                    "Last system is not a capital engagement");
        }

        Set<Shipyard.Hull> encountered=new HashSet<>();
        Set<String> weapons=new HashSet<>();
        int upgraded=0,advancedBatteries=0;
        for(int mission:new int[]{1,10,50,51,91,101,151,201,250,251,301,351,401,451,499,500}){
            int ceiling=CampaignOpposition.maximumTier(mission);
            int sample=Math.min(16,LevelEconomy.level(mission).maximumFleet());
            for(Battle.Faction faction:new Battle.Faction[]{
                    Battle.Faction.TERRAN,Battle.Faction.VAERITH_PRISM}){
                List<Shipyard.Design> ships=CampaignOpposition.fleet(
                        mission,1,sample,faction,catalog);
                List<Shipyard.Design> replay=CampaignOpposition.fleet(
                        mission,1,sample,faction,catalog);
                check(ships.size()==replay.size(),"Enemy fleet count changed on seed replay");
                Set<String> variations=new HashSet<>();
                for(int i=0;i<ships.size();i++){
                    Shipyard.Design design=ships.get(i);
                    check(Shipyard.encode(design).equals(Shipyard.encode(replay.get(i))),
                            "Enemy ship hardware is not replayable");
                    check(design.faction==faction&&HullProgression.available(design.hull,ceiling),
                            "Foreign or technology-ineligible campaign hull at "+mission);
                    check(Shipyard.inspect(design,catalog).valid(),
                            "Unbuildable campaign enemy at "+mission+": "+
                                    Shipyard.inspect(design,catalog).explanation());
                    encountered.add(design.hull);
                    variations.add(Shipyard.encode(design));
                    int weaponsInstalled=0;
                    for(int slot=0;slot<design.slots();slot++){
                        Shipyard.Installed part=design.at(slot);
                        if(part==null)continue;
                        if(design.hull.slots.get(slot).mount()==Shipyard.Mount.WEAPON){
                            weapons.add(part.component);
                            weaponsInstalled++;
                        }
                        for(Shipyard.Track track:Shipyard.Track.values()){
                            check(part.stage(track)<=ceiling,
                                    "Enemy module outruns authorized level technology");
                            if(part.stage(track)>0)upgraded++;
                        }
                        check(part.mark<=Math.min(4,ceiling+1),
                                "Enemy hardware variant exceeds manufacturing ceiling");
                    }
                    if(mission>=451&&weaponsInstalled>2)advancedBatteries++;
                }
                if(sample>1)check(variations.size()>1,
                        "Entire campaign fleet is one cloned loadout");
            }
        }
        check(advancedBatteries>0,
                "Late capital ships still use only the two starter weapon hardpoints");
        check(encountered.size()>=8&&weapons.size()>=6&&upgraded>30,
                "Campaign still lacks hull, weapon, or engineering diversity");

        Shipyard.Design friendly=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,catalog);
        Shipyard.Design hostile=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,catalog);
        CampaignEncounter mission=CampaignEncounter.start(151,6,friendly,hostile,catalog);
        Battle battle=mission.battle();
        check(battle.alive(0)==6&&battle.alive(1)==
                        CampaignEncounter.enemyCount(LevelEconomy.level(151),6,1),
                "Live battle did not deploy mission-scaled hostile fleet");
        for(Battle.Ship ship:battle.ships())if(ship.team==1){
            check(ship.standingOrders!=null&&ship.orders==ship.standingOrders.tactics(),
                    "Mission-specific tactical orders not assigned to actual AI ships");
            check(ship.hullClass!=null&&ship.faction==Battle.Faction.DRAXIAN,
                    "Mission equipment failed to reach combat");
        }
        // Repeated waves preserve the same original friendly physical vessels.
        Battle.Ship surviving=battle.ships().get(0);
        for(Battle.Ship ship:battle.ships())if(ship.team==1)ship.hull=0;
        check(mission.advance()==CampaignEncounter.Advance.NEXT_WAVE,
                "Reinforcement wave did not launch");
        check(battle.ships().get(0)==surviving,
                "Enemy loadout generation replaced the player's vessel");
        check(battle.alive(1)==CampaignEncounter.enemyCount(LevelEconomy.level(151),6,2),
                "Wrong second-wave fleet size");
        System.out.println("PASS campaign opposition: 500 map systems, 0..IX physical tech, "+
                "mixed faction fleets, seeded replay and active battle reinforcements");
    }
}
