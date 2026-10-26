package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Exercise the same playable battle launch fixtures and campaign state as the menus. */
public final class GameFlowTest {
    private static void verify(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    public static void main(String[] args)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        for(ScenarioCatalog.Preset preset:ScenarioCatalog.Preset.values()) {
            Shipyard.Design first=ScenarioCatalog.defaultDesign(preset,Battle.Faction.TERRAN,true,tech);
            Shipyard.Design second=ScenarioCatalog.defaultDesign(preset,Battle.Faction.DRAXIAN,false,tech);
            Battle b=ScenarioCatalog.create(preset,first,second,tech,4);
            int count=preset==ScenarioCatalog.Preset.CUSTOM?4:preset.ships;
            verify(b.alive(0)==count&&b.alive(1)==count,"Wrong physical fleet count for "+preset);
            b.step();
            verify(b.tick==1,"Scenario did not run battle simulation: "+preset);
        }
        for(ScenarioCatalog.Preset p:ScenarioCatalog.Preset.values())
            for(Battle.Faction faction:Battle.Faction.values())
                verify(Shipyard.inspect(ScenarioCatalog.defaultDesign(p,faction,true,tech),tech).valid(),
                       "Unserviceable playable preset "+p+" / "+faction);
        var start=CampaignProgress.start(Battle.Faction.TERRAN);
        verify(start.available(1)&&!start.available(2),"Invalid starting mission access");
        verify(start.equals(CampaignProgress.decode(CampaignProgress.encode(start))),"Save round-trip");
        var won=start.complete(1,4,4);
        verify(won.available(2)&&won.credits()>0&&won.victories()==1,"Progress rewards");
        verify(won.equals(CampaignProgress.decode(CampaignProgress.encode(won))),"Won save round-trip");
        verify(won.complete(1,4,3).credits()>won.credits(),"Replay credits");
        boolean rejected=false;
        try{CampaignProgress.decode(CampaignProgress.encode(won).replace("TERRAN","DRAXIAN"));}
        catch(IllegalArgumentException valid){rejected=true;}
        verify(rejected,"Corrupted faction save accepted");
        for(int index=0;index<CampaignMap.PAGE_COUNT;index++){
            List<CampaignMap.SystemNode> page=CampaignMap.page(index);
            verify(page.size()==10,"Incomplete sector page");
            for(int n=0;n<page.size();n++)
                verify(page.get(n).level()==index*10+n+1,"Wrong stage for map node");
        }
        System.out.println("PASS game flow: real skirmishes, map, campaign save and victory unlocks");
    }
}
