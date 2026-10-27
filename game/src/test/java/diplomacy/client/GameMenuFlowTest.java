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

        check(GameMenuPanel.Screen.valueOf("DOCK")!=null,
                "Separate campaign active/reserve dock is missing");
        check(GameMenuPanel.dockAction(88,60,0,4)==0,"Cannot exit owned fleet dock");
        check(GameMenuPanel.dockAction(1060,60,0,4)==1,"Cannot deploy owned active fleet");
        check(GameMenuPanel.dockAction(587,585,0,4)==2,"Previous roster page is inert");
        check(GameMenuPanel.dockAction(683,585,0,4)==3,"Next roster page is inert");
        for(int i=0;i<4;i++)
            check(GameMenuPanel.dockAction(300,545-i*43,0,4)==100+i,
                    "Owned vessel has no selectable roster row "+i);
        for(int i=0;i<10;i++)
            check(GameMenuPanel.dockAction(1000,546-i*46,0,4)==4+i,
                    "Fleet service/purchase/name control missing "+i);
        check(GameMenuPanel.dockAction(1000,546-9*46,0,4)==13,
                "Service All must be a usable button, not only a price label");
        check(GameMenuPanel.dockAction(1110,546-8*46,0,4)==14,
                "Manual renaming must have a separate native text-input action");
        check(GameMenuPanel.dockAction(1024,546-8*46,0,4)==-1,
                "Split rename controls must not have overlapping hit zones");
        check(GameMenuPanel.dockAction(1000,96,0,4)==-1,
                "Service All tap must not overlap the campaign navigation");
        check(GameMenuPanel.dockAction(400,500,0,0)==-1,
                "Empty roster offers a selectable phantom ship");
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
        check(GameMenuPanel.ordersAction(700,580)==0,
                "Previous ship standing-orders control unavailable");
        check(GameMenuPanel.ordersAction(1160,580)==1,
                "Next ship standing-orders control unavailable");
        for(int i=0;i<13;i++){
            float y=534-i*29;
            check(GameMenuPanel.ordersAction(700,y)==2+i,
                    "Cannot reduce standing doctrine row "+i);
            check(GameMenuPanel.ordersAction(1150,y)==2+i,
                    "Cannot increase standing doctrine row "+i);
        }
        check(GameMenuPanel.ordersAction(1050,60)==30,
                "Cannot return from standing-orders editor");
        check(GameMenuPanel.ordersAction(900,580)==31,
                "Advanced orders page button is inert");
        for(int i=13;i<diplomacy.sim.ShipStandingOrders.SETTINGS.length;i++){
            float y=534-(i-13)*29;
            check(GameMenuPanel.ordersAction(700,y,1)==2+i,
                    "Cannot decrease advanced behavior "+i);
            check(GameMenuPanel.ordersAction(1150,y,1)==2+i,
                    "Cannot increase advanced behavior "+i);
        }
        check(GameMenuPanel.ordersAction(1120,505,0)==3,
                "Original doctrine page no longer handles persistent caution");
        check(GameMenuPanel.ordersAction(385,376)==-1,
                "Map selection must not alter a doctrine setting");
        float[][] groupButtons={{110,580},{240,580},{370,580},{515,580}};
        for(int i=0;i<groupButtons.length;i++)
            check(GameMenuPanel.groupAction(groupButtons[i][0],groupButtons[i][1])==i,
                    "Fleet group action has no real touch surface "+i);
        check(GameMenuPanel.groupAction(385,375)==-1,
                "Ship selection surface should not activate a group command");
        check(GameMenuPanel.groupAction(700,580)==-1,
                "Captain navigation overlaps group controls");
        var formation=new diplomacy.sim.CampaignDeployment.Loadout(8,
                diplomacy.sim.CampaignDeployment.Formation.LINE);
        for(int i=0;i<formation.ships();i++){
            var point=formation.at(i);
            check(GameMenuPanel.shipHit(GameMenuPanel.placementUiX(point.x()),
                    GameMenuPanel.placementUiY(point.y()),formation,false)==i,
                    "Ship not selectable on drag-and-place canvas: "+i);
            check(GameMenuPanel.shipHit(GameMenuPanel.ordersUiX(point.x()),
                    GameMenuPanel.ordersUiY(point.y()),formation,true)==i,
                    "Ship not selectable for persistent standing doctrine: "+i);
        }
        check(Math.abs(GameMenuPanel.placementWorldX(GameMenuPanel.placementUiX(280))-280)<.01,
                "Deployment drag projection has no accurate inverse");
        check(Math.abs(GameMenuPanel.ordersWorldY(GameMenuPanel.ordersUiY(540))-540)<.01,
                "Standing orders map projection inverse failed");

        for(int page=0;page<CampaignMap.PAGE_COUNT;page++)
            for(var node:CampaignMap.page(page))
                check(GameMenuPanel.mapHit(node.x(),node.y(),page)==node.level(),
                        "Map selection did not return the displayed mission: "+node.level());
        System.out.println("PASS functional menu hit regions: six home actions, "+
                presets.length+" skirmishes, "+factions.length+" factions, 500 map nodes");
    }
}
