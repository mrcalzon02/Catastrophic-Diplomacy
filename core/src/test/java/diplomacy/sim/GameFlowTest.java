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
        long rawReplay=LevelEconomy.victoryCredits(LevelEconomy.level(1),4,3,true);
        verify(won.complete(1,4,3).credits()-won.credits()==rawReplay/4,
                "Replay of completed mission must pay floor(full reward / 4)");
        var top=new CampaignProgress.State(Battle.Faction.TERRAN,500,0,499,
                0,499,0,CampaignProgress.Project.NONE,0);
        var topWin=top.complete(500,20,20);
        verify(topWin.highestCompleted()==500&&topWin.credits()>0,
                "Final campaign stage should award its first-clear reward only once");
        verify(topWin.complete(500,20,20).credits()-topWin.credits()==topWin.credits()/4,
                "Stage 500 replay must not pay first-clear credits a second time");

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
        for(int mission=1;mission<=500;mission++){
            var story=CampaignNarrative.forLevel(mission);
            verify(story.chapter()==LevelEconomy.level(mission).sector(),
                    "Narrative chapter does not match real star-map sector: "+mission);
            verify(!story.title().isBlank()&&!story.intelligence().isBlank()
                            &&!story.beat().isBlank(),
                    "Missing campaign briefing for stage "+mission);
        }
        // Campaign does not silently award success after the first wave.
        // Reinforcements enter the SAME battle, preserving hull damage and spent ammunition.
        Shipyard.Design player=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,tech);
        Shipyard.Design enemy=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,tech);
        CampaignEncounter encounter=CampaignEncounter.start(1,4,player,enemy,tech);
        Battle live=encounter.battle();
        verify(live.alive(0)==4&&live.alive(1)==4,"Campaign initial deployment");
        Battle.Ship surviving=live.ships().get(0);
        surviving.hull=surviving.maxHull/2;
        surviving.missileAmmo=2;
        for(int wave=1;wave<=encounter.waves();wave++) {
            verify(encounter.wave()==wave,"Wrong active campaign wave");
            for(Battle.Ship ship:live.ships())
                if(ship.team==1)ship.hull=0;
            CampaignEncounter.Advance result=encounter.advance();
            if(wave<encounter.waves()){
                verify(result==CampaignEncounter.Advance.NEXT_WAVE,
                        "Campaign ended before all mandatory waves");
                verify(live.alive(1)==CampaignEncounter.enemyCount(LevelEconomy.level(1),4,wave+1),
                        "Campaign reinforcements did not spawn");
                verify(live.ships().get(0)==surviving
                        && surviving.hull==surviving.maxHull/2
                        && surviving.missileAmmo==2,
                        "Campaign wave incorrectly reset friendly hull or ammunition");
            }else verify(result==CampaignEncounter.Advance.VICTORY,
                    "Campaign final wave not credited");
        }
        CampaignEncounter lost=CampaignEncounter.start(1,4,player,enemy,tech);
        for(Battle.Ship ship:lost.battle().ships())if(ship.team==0)ship.hull=0;
        verify(lost.advance()==CampaignEncounter.Advance.DEFEAT,"Dead fleet earned victory");
        System.out.println("PASS game flow: real skirmishes, sequential campaign waves, map, saves and rewards");
    }
}
