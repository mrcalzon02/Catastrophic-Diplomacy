package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Release-blocking actual campaign creation -> saved management -> mission deployment.
 * Runs all factions instead of the old one-faction UI hitbox-only check. */
public final class CampaignLaunchRuntimeTest {
    private CampaignLaunchRuntimeTest(){}
    private static void require(boolean valid,String reason){
        if(!valid)throw new AssertionError(reason);
    }
    public static void main(String[] ignored)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        var nodes=CampaignMap.page(0);
        require(nodes.size()==CampaignMap.PAGE_SIZE,"Real mission management nodes missing");
        int missions=0;
        Path temp=Files.createTempDirectory("cd-live-campaign-start-");
        try{
            for(Battle.Faction faction:Battle.Faction.values()){
                Shipyard.Design starter=Shipyard.starter(faction,Shipyard.Hull.CORVETTE);
                CampaignProgress.State progress=CampaignProgress.start(faction);
                CampaignFleet.Roster roster=CampaignFleet.startSolo(faction,starter,catalog,
                        0x5A7100200000L+faction.ordinal());
                require(roster.ships().size()==1&&roster.active().size()==1,
                        faction+" must start with a single genuinely fitted flagship");
                Path save=temp.resolve(faction.name()+".dat");
                var snapshot=new CampaignLedger.Snapshot(1,progress,roster,
                        new CampaignTechnology.State(faction));
                CampaignLedger.commit(save,snapshot,catalog);
                var restored=CampaignLedger.load(save,catalog).orElseThrow();
                require(CampaignLedger.equivalent(snapshot,restored),
                        faction+" campaign management save must be reloadable");
                require(restored.fleet().active().size()==1,
                        faction+" restored management screen lost active flagship");
                for(int level:new int[]{1,2}){
                    LevelEconomy.Level stage=LevelEconomy.level(level);
                    require(stage.maximumFleet()>=1,"First missions must accept single flagship");
                    var node=CampaignMap.page(CampaignMap.pageOf(level))
                            .get((level-1)%CampaignMap.PAGE_SIZE);
                    var hostile=CampaignOpposition.fleet(level,1,1,node.opponent(),catalog).get(0);
                    var deployment=new CampaignDeployment.Loadout(1,
                            level==1?CampaignDeployment.Formation.WEDGE:
                                    CampaignDeployment.Formation.LINE);
                    var encounter=CampaignEncounter.start(level,deployment,
                            List.of(restored.fleet().active().get(0).fitting()),
                            hostile,catalog);
                    Battle battle=encounter.battle();
                    require(battle.ships().size()>=2&&battle.alive(0)==1,
                            faction+" mission "+level+" failed to spawn combatants");
                    for(int tick=0;tick<30;tick++)battle.step();
                    require(battle.tick>=30&&battle.ships().size()>=2,
                            faction+" mission "+level+" did not run real combat ticks");
                    missions++;
                }
            }
        }finally{
            try(var paths=Files.walk(temp)){
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}catch(java.io.IOException cleanupFailure){}
                });
            }
        }
        System.out.println("PASS campaign start/save/restore + mission 1/2 live 30-tick combat for "+
                Battle.Faction.values().length+" factions, "+missions+" missions");
    }
}
