package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.Set;

/** Proves campaign deployment edits alter real ship positions and obey mission fleet caps. */
public final class CampaignDeploymentTest {
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] ignored)throws Exception{
        for(int stageNumber:new int[]{1,50,150,500}){
            LevelEconomy.Level level=LevelEconomy.level(stageNumber);
            CampaignDeployment.Loadout original=CampaignDeployment.defaultFor(level);
            require(original.ships()>=1&&original.ships()<=level.maximumFleet(),
                    "Invalid first fleet for mission "+stageNumber);
            require(original.withShips(-1000,level).ships()==1,"Below 1 fleet");
            require(original.withShips(1000,level).ships()==level.maximumFleet(),
                    "Fleet count exceeds mission cap");
            for(CampaignDeployment.Formation formation:CampaignDeployment.Formation.values()){
                CampaignDeployment.Loadout max=new CampaignDeployment.Loadout(
                        level.maximumFleet(),formation);
                Set<String> locations=new HashSet<>();
                for(int i=0;i<max.ships();i++){
                    CampaignDeployment.Point p=max.at(i);
                    require(Double.isFinite(p.x())&&Double.isFinite(p.y())&&
                            p.x()>50&&p.x()<600&&p.y()>90&&p.y()<610,
                            "Off-battlefield deployment: "+formation+" "+stageNumber+" #"+i+" "+p);
                    require(locations.add(p.x()+":"+p.y()),
                            "Two deployed ships overlap: "+formation+" "+stageNumber+" #"+i);
                }
            }
        }
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        Shipyard.Design friendly=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,technology);
        Shipyard.Design hostile=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,technology);
        double[] firstX=new double[CampaignDeployment.Formation.values().length];
        int formationIndex=0;
        for(CampaignDeployment.Formation formation:CampaignDeployment.Formation.values()){
            CampaignDeployment.Loadout selected=new CampaignDeployment.Loadout(9,formation);
            CampaignEncounter encounter=CampaignEncounter.start(150,selected,friendly,hostile,technology);
            require(encounter.battle().alive(0)==9&&encounter.battle().alive(1)==
                    CampaignEncounter.enemyCount(LevelEconomy.level(150),9,1),
                    "Selected fleet size not reflected in actual battle");
            require(encounter.deployment().equals(selected),"Encounter lost deployment selection");
            for(int i=0;i<9;i++){
                Battle.Ship ship=encounter.battle().ships().get(i);
                CampaignDeployment.Point expected=selected.at(i);
                require(ship.team==0&&ship.x==expected.x()&&ship.y==expected.y(),
                        "Ship was not actually moved to chosen formation");
            }
            firstX[formationIndex++]=encounter.battle().ships().get(0).x;
        }
        require(firstX[0]!=firstX[1]&&firstX[1]!=firstX[2],
                "Formation selection has no physical result");
        require(CampaignDeployment.defaultFor(LevelEconomy.level(1))
                .withFormation(3).formation()==CampaignDeployment.Formation.LINE,
                "Formation cycle failed");
        require(CampaignDeployment.defaultFor(LevelEconomy.level(1)).ships()==1,
                "New campaign should deploy one ship on the first mission");
        LevelEconomy.Level mid=LevelEconomy.level(150);
        long compact=LevelEconomy.victoryCredits(mid,4,4,true);
        long full=LevelEconomy.victoryCredits(mid,mid.maximumFleet(),mid.maximumFleet(),true);
        require(compact>full,"Deployment size does not affect the underdog reward preview");
        System.out.println("PASS campaign deployment: 4 stages, all formations, real coordinates, "
                +"fleet caps, reward balance");
    }
}
