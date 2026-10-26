package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.ScenarioCatalog;

/** Functional hit-target routing for the desktop/Android front-end (no graphics required). */
public final class GameMenuFlowTest {
    private static void check(boolean pass,String detail) {
        if(!pass)throw new AssertionError(detail);
    }
    public static void main(String[] args) {
        // The exact coordinates that users tap must resolve to the displayed action.
        for(int i=0;i<6;i++)
            check(GameMenuPanel.homeHit(600,512-i*64)==i,"Home action "+i);
        check(GameMenuPanel.homeHit(15,650)==-1,"Off-menu tap activated an action");
        check(GameMenuPanel.Screen.valueOf("CAMPAIGN_SETUP")!=null,
                "New-campaign confirmation view is missing");
        ScenarioCatalog.Preset[] presets=ScenarioCatalog.Preset.values();
        for(int i=0;i<presets.length;i++)
            check(GameMenuPanel.skirmishHit(110,615-i*48)==i,
                    "Skirmish preset hit target "+presets[i]);
        check(GameMenuPanel.skirmishHit(700,600)==-1,"Out-of-list skirmish selection");

        Battle.Faction[] factions=Battle.Faction.values();
        int rows=(factions.length+1)/2;
        for(int i=0;i<factions.length;i++){
            float x=101+(i/rows)*570;
            float y=528-(i%rows)*62;
            check(GameMenuPanel.factionHit(x,y)==factions[i],
                    "Campaign faction hit target "+factions[i]);
        }
        check(GameMenuPanel.factionHit(1250,650)==null,"Invalid faction hit region");

        check(GameMenuPanel.Screen.valueOf("DEPLOYMENT")!=null,
                "Pre-battle deployment screen missing");
        float[][] deploymentTargets={
                {90,61},{894,452},{1151,452},{895,344},
                {1142,344},{990,242},{1076,60}};
        for(int i=0;i<deploymentTargets.length;i++){
            float[] point=deploymentTargets[i];
            check(GameMenuPanel.deploymentAction(point[0],point[1])==i,
                    "Deployment control has no functional touch target "+i);
        }
        check(GameMenuPanel.deploymentAction(605,480)==-1,
                "Deployment preview area must not trigger a button");
        check(GameMenuPanel.deploymentAction(1000,171)==7,
                "Fleet order editor has no real touch target");
        check(GameMenuPanel.Screen.valueOf("SHIP_ORDERS")!=null,
                "Per-ship order editor state missing");
        float[][] orderTargets={{890,555},{1140,555},{1000,484},{1000,412},
                {1000,343},{1000,275},{1000,202},{1050,58}};
        for(int i=0;i<orderTargets.length;i++){
            float[] point=orderTargets[i];
            check(GameMenuPanel.ordersAction(point[0],point[1])==i,
                    "Per-ship queued command button unavailable: "+i);
        }
        var formation=new diplomacy.sim.CampaignDeployment.Loadout(8,
                diplomacy.sim.CampaignDeployment.Formation.LINE);
        for(int i=0;i<formation.ships();i++){
            var point=formation.at(i);
            check(GameMenuPanel.shipHit(GameMenuPanel.placementUiX(point.x()),
                    GameMenuPanel.placementUiY(point.y()),formation,false)==i,
                    "Ship not selectable on drag-and-place canvas: "+i);
            check(GameMenuPanel.shipHit(GameMenuPanel.ordersUiX(point.x()),
                    GameMenuPanel.ordersUiY(point.y()),formation,true)==i,
                    "Ship not selectable in its command plan: "+i);
        }
        check(Math.abs(GameMenuPanel.placementWorldX(GameMenuPanel.placementUiX(280))-280)<.01,
                "Deployment drag projection has no accurate inverse");
        check(Math.abs(GameMenuPanel.ordersWorldY(GameMenuPanel.ordersUiY(540))-540)<.01,
                "Waypoint projection inverse failed");

        for(int page=0;page<CampaignMap.PAGE_COUNT;page++)
            for(var node:CampaignMap.page(page))
                check(GameMenuPanel.mapHit(node.x(),node.y(),page)==node.level(),
                        "Map selection did not return the displayed mission: "+node.level());
        System.out.println("PASS functional menu hit regions: six home actions, "+
                presets.length+" skirmishes, "+factions.length+" factions, 500 map nodes");
    }
}
