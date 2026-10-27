package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignDeployment;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.ShipStandingOrders;
import java.util.Set;
import diplomacy.sim.LevelEconomy;
import diplomacy.sim.ScenarioCatalog;
import diplomacy.sim.CampaignNarrative;
import diplomacy.shipyard.ShipLineages;
import java.util.List;

/** Lightweight native-font menu views; all hit regions are owned by the client flow. */
final class GameMenuPanel {
    enum Screen { HOME, CAMPAIGN_SETUP, SKIRMISH, CAMPAIGN, DOCK, RESEARCH, DEPLOYMENT, SHIP_ORDERS, OPTIONS, BATTLE, RESULTS }
    private static final float W=1280,H=720;
    private static final Color BG=new Color(.018f,.029f,.064f,1);
    private static final Color PANEL=new Color(.055f,.105f,.18f,1);
    private static final Color SELECT=new Color(.12f,.28f,.40f,1);
    private static final Color MUTED=new Color(.57f,.67f,.79f,1);
    private static final String[] HOME_ACTIONS={
        "CONTINUE CAMPAIGN","NEW CAMPAIGN","SKIRMISH","SHIPYARD","OPTIONS","EXIT"
    };

    static boolean hit(float x,float y,float bx,float by,float w,float h){
        return x>=bx&&x<=bx+w&&y>=by&&y<=by+h;
    }
    static int homeHit(float x,float y){
        for(int i=0;i<HOME_ACTIONS.length;i++)
            if(hit(x,y,438,487-i*64,404,50))return i;
        return -1;
    }
    static int skirmishHit(float x,float y){
        ScenarioCatalog.Preset[] values=ScenarioCatalog.Preset.values();
        for(int i=0;i<values.length;i++)
            if(hit(x,y,30,592-i*48,550,44))return i;
        return -1;
    }
    static Battle.Faction factionHit(float x,float y){
        Battle.Faction[] factions=Battle.Faction.values();
        int rows=(factions.length+1)/2;
        for(int i=0;i<factions.length;i++){
            float bx=80+(i/rows)*570;
            float by=502-(i%rows)*62;
            if(hit(x,y,bx,by,520,49))return factions[i];
        }
        return null;
    }
    /** The deployment input map is shared by UI, controller and functional tests. */
    static int deploymentAction(float x,float y) {
        if(hit(x,y,35,35,175,55))return 0;
        if(hit(x,y,845,430,99,51))return 1;
        if(hit(x,y,1098,430,99,51))return 2;
        if(hit(x,y,845,321,99,51))return 3;
        if(hit(x,y,1098,321,99,51))return 4;
        if(hit(x,y,845,214,352,55))return 5;
        if(hit(x,y,913,35,302,55))return 6;
        if(hit(x,y,845,143,352,55))return 7;
        return -1;
    }
    // Deployment preview uses the exact same world-to-screen projection
    // for rendering, click selection and dragging a physical hull.
    static float placementUiX(double worldX){return 60+(float)(worldX-70)*1.24f;}
    static float placementUiY(double worldY){return 166+(float)(worldY-90)*.80f;}
    static double placementWorldX(float uiX){return 70+(uiX-60)/1.24;}
    static double placementWorldY(float uiY){return 90+(uiY-166)/.80;}
    static float ordersUiX(double worldX){return 69+(float)(worldX-70)*1.05f;}
    static float ordersUiY(double worldY){return 170+(float)(worldY-90)*.75f;}
    static double ordersWorldX(float uiX){return 70+(uiX-69)/1.05;}
    static double ordersWorldY(float uiY){return 90+(uiY-170)/.75;}
    static int shipHit(float x,float y,CampaignDeployment.Loadout deployment,boolean fullBattle){
        int result=-1;double nearest=22*22;
        for(int i=0;i<deployment.ships();i++){
            var p=deployment.at(i);
            double sx=fullBattle?ordersUiX(p.x()):placementUiX(p.x());
            double sy=fullBattle?ordersUiY(p.y()):placementUiY(p.y());
            double dist=(x-sx)*(x-sx)+(y-sy)*(y-sy);
            if(dist<nearest){nearest=dist;result=i;}
        }
        return result;
    }
    /** Real hit routing: 0/1 select hull, 2..24 tune independent policies,
     * 30 returns, 31 toggles doctrine page. */
    static int ordersAction(float x,float y){return ordersAction(x,y,0);}
    static int ordersAction(float x,float y,int page){
        if(hit(x,y,650,561,110,40))return 0;
        if(hit(x,y,1108,561,112,40))return 1;
        if(hit(x,y,785,561,296,40))return 31;
        int first=page==0?0:13;
        int end=page==0?13:ShipStandingOrders.SETTINGS.length;
        for(int i=first;i<end;i++)
            if(hit(x,y,650,519-(i-first)*29,570,27))return 2+i;
        if(hit(x,y,900,32,300,55))return 30;
        return -1;
    }
    /** Four real controls on the order editor's left fleet pane. */
    static int groupAction(float x,float y){
        if(hit(x,y,52,560,132,40))return 0; // select members
        if(hit(x,y,192,560,105,40))return 1; // all except source
        if(hit(x,y,305,560,132,40))return 2; // apply captain
        if(hit(x,y,445,560,147,40))return 3; // clear selection
        return -1;
    }
    /** Fleet-dock functional hit map; rows use absolute indices in the owned roster. */
    static int dockAction(float x,float y,int page,int ownedCount){
        if(hit(x,y,35,35,165,50))return 0;
        if(hit(x,y,980,35,260,50))return 1;
        if(hit(x,y,310,35,200,50))return 15; // owned campaign shipyard
        if(hit(x,y,530,35,210,50))return 16; // paid research ledger
        if(hit(x,y,548,566,86,43))return 2;
        if(hit(x,y,642,566,86,43))return 3;
        for(int i=0;i<9;i++)
            if(hit(x,y,45,521-i*43,746,38)&&page*9+i<ownedCount)
                return 100+page*9+i;
        for(int i=0;i<10;i++){
            if(i==8){
                if(hit(x,y,840,525-i*46,180,42))return 12; // generated rename
                if(hit(x,y,1028,525-i*46,186,42))return 14; // manual rename
            }else if(hit(x,y,840,525-i*46,374,42))return 4+i;
        }
        return -1;
    }
    static int mapHit(float x,float y,int page){
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            if(Math.hypot(x-node.x(),y-node.y())<=26)return node.level();
        return -1;
    }
    private static void rectangle(ShapeRenderer shapes,float x,float y,float w,float h,Color color){
        shapes.setColor(color);shapes.rect(x,y,w,h);
    }
    private static void button(ShapeRenderer shapes,float x,float y,float w,float h,boolean selected){
        rectangle(shapes,x,y,w,h,selected?SELECT:PANEL);
    }
    private static void label(SpriteBatch batch,BitmapFont font,String text,float x,float y,Color color){
        font.setColor(color);font.draw(batch,text,x,y);
    }
    private static void title(SpriteBatch batch,BitmapFont font,String text,String subtitle){
        label(batch,font,text,37,680,Color.WHITE);
        label(batch,font,subtitle,38,650,MUTED);
    }
    void draw(Screen screen,ShapeRenderer shapes,SpriteBatch batch,BitmapFont font,
              ScenarioCatalog.Preset preset,Battle.Faction left,Battle.Faction right,Battle.Faction selectedFaction,
              int fleetCount,CampaignProgress.State progress,int page,int selectedLevel,
              CampaignDeployment.Loadout deployment,String friendlyHull,int selectedShip,
              boolean preparingCampaign,int standingPage,boolean selectingGroup,
              Set<Integer> doctrineGroup,boolean awaitingWaypoint,String editNotice,
              CampaignFleet.Roster ownedFleet,int dockPage,int dockSelected,long[] dockCosts,
              long purchaseCost,String dockNotice,
              boolean music,boolean effects,boolean trackFinal,boolean updaterAvailable,
              boolean won,long reward,int surviving,int deployed,int escaped,int destroyed,
              int withdrawing){
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rectangle(shapes,0,0,W,H,BG);
        // Stable UI frame; all text is genuine BitmapFont, never an icon substitute.
        rectangle(shapes,0,622,W,2,SELECT);
        switch(screen){
            case HOME -> {
                for(int i=0;i<HOME_ACTIONS.length;i++)button(shapes,438,487-i*64,404,50,false);
            }
            case CAMPAIGN_SETUP -> {
                Battle.Faction[] factions=Battle.Faction.values();
                int rows=(factions.length+1)/2;
                for(int i=0;i<factions.length;i++)
                    button(shapes,80+(i/rows)*570,502-(i%rows)*62,520,49,
                            factions[i]==selectedFaction);
                button(shapes,80,35,230,54,false);
                button(shapes,892,35,308,54,true);
            }
            case SKIRMISH -> {
                rectangle(shapes,610,113,638,524,PANEL);
                ScenarioCatalog.Preset[] presets=ScenarioCatalog.Preset.values();
                for(int i=0;i<presets.length;i++)
                    button(shapes,30,592-i*48,550,44,presets[i]==preset);
                button(shapes,30,35,160,49,false);
                if(preset!=ScenarioCatalog.Preset.PROVING_GROUND)
                    button(shapes,702,35,225,49,false);
                button(shapes,969,35,277,49,true);
                if(preset!=ScenarioCatalog.Preset.PROVING_GROUND){
                    button(shapes,661,332,545,42,false);
                    button(shapes,661,271,545,42,false);
                }
                if(preset==ScenarioCatalog.Preset.CUSTOM)
                    button(shapes,661,210,545,42,false);
            }
            case CAMPAIGN -> {
                rectangle(shapes,39,114,866,491,PANEL);
                rectangle(shapes,921,114,328,491,PANEL);
                List<CampaignMap.SystemNode> nodes=CampaignMap.page(page);
                shapes.setColor(.32f,.54f,.75f,1);
                for(int i=1;i<nodes.size();i++){
                    var a=nodes.get(i-1);var b=nodes.get(i);
                    shapes.rectLine(a.x(),a.y(),b.x(),b.y(),2);
                }
                for(var node:nodes){
                    Color c=node.level()<progress.unlocked()?Color.GREEN:
                        node.level()==progress.unlocked()?Color.CYAN:MUTED;
                    shapes.setColor(node.level()==selectedLevel?Color.GOLD:c);
                    shapes.circle(node.x(),node.y(),node.level()==selectedLevel?19:13,18);
                    shapes.setColor(BG);
                    shapes.circle(node.x(),node.y(),6,12);
                }
                button(shapes,35,35,165,50,false);
                button(shapes,214,35,136,50,false);
                button(shapes,366,35,136,50,false);
                button(shapes,525,35,200,50,false);
                button(shapes,741,35,190,50,false);
                button(shapes,964,35,278,50,true);
            }
            case RESEARCH -> {
                rectangle(shapes,110,174,1050,444,PANEL);
                button(shapes,35,35,250,54,false);
                button(shapes,906,35,330,54,true);
            }
            case DOCK -> {
                rectangle(shapes,33,108,771,509,PANEL);
                rectangle(shapes,818,108,427,509,PANEL);
                button(shapes,35,35,165,50,false);
                button(shapes,980,35,260,50,false);
                button(shapes,310,35,200,50,false);
                button(shapes,530,35,210,50,false);
                button(shapes,548,566,86,43,false);
                button(shapes,642,566,86,43,false);
                if(ownedFleet!=null)
                    for(int i=0;i<9;i++){
                        int index=dockPage*9+i;
                        if(index>=ownedFleet.ships().size())break;
                        button(shapes,45,521-i*43,746,38,index==dockSelected);
                    }
                for(int i=0;i<10;i++){
                    if(i==8){
                        button(shapes,840,525-i*46,180,42,false);
                        button(shapes,1028,525-i*46,186,42,false);
                    }else button(shapes,840,525-i*46,374,42,false);
                }
            }
            case DEPLOYMENT -> {
                rectangle(shapes,39,135,760,472,PANEL);
                rectangle(shapes,819,135,425,472,PANEL);
                // Boundary displayed is exactly the same box that placement rejects outside.
                shapes.setColor(.18f,.42f,.51f,1);
                shapes.rect(59,165,611,2);
                shapes.rect(59,566,611,2);
                shapes.rect(59,165,2,402);
                shapes.rect(668,165,2,402);
                // Every marker corresponds to a real ship deployed at this battle coordinate.
                // The same pure loadout.at() is used by CampaignEncounter to place vessels.
                for(int i=0;i<deployment.ships();i++){
                    CampaignDeployment.Point ship=deployment.at(i);
                    shapes.setColor(i==selectedShip?Color.GOLD:Color.CYAN);
                    shapes.circle(placementUiX(ship.x()),placementUiY(ship.y()),
                                  i==selectedShip?9:deployment.ships()>80?3:5,12);
                }
                if(preparingCampaign||preset==ScenarioCatalog.Preset.CUSTOM){
                    button(shapes,845,430,99,51,false);
                    button(shapes,1098,430,99,51,false);
                }
                button(shapes,845,321,99,51,false);
                button(shapes,1098,321,99,51,false);
                button(shapes,845,214,352,55,false);
                button(shapes,845,143,352,55,false);
                button(shapes,35,35,175,55,false);
                button(shapes,913,35,302,55,true);
            }
            case SHIP_ORDERS -> {
                rectangle(shapes,39,135,582,472,PANEL);
                rectangle(shapes,633,135,612,472,PANEL);
                shapes.setColor(.16f,.42f,.55f,1);
                shapes.rect(ordersUiX(CampaignDeployment.MIN_X),ordersUiY(CampaignDeployment.MIN_Y),
                        ordersUiX(CampaignDeployment.MAX_X)-ordersUiX(CampaignDeployment.MIN_X),2);
                shapes.rect(ordersUiX(CampaignDeployment.MIN_X),ordersUiY(CampaignDeployment.MAX_Y),
                        ordersUiX(CampaignDeployment.MAX_X)-ordersUiX(CampaignDeployment.MIN_X),2);
                for(int i=0;i<deployment.ships();i++){
                    var position=deployment.at(i);
                    shapes.setColor(i==selectedShip?Color.GOLD:
                            doctrineGroup.contains(i)?Color.GREEN:Color.CYAN);
                    shapes.circle(ordersUiX(position.x()),ordersUiY(position.y()),
                            i==selectedShip?9:deployment.ships()>80?3:5,12);
                }
                button(shapes,52,560,132,40,selectingGroup);
                button(shapes,192,560,105,40,false);
                button(shapes,305,560,132,40,!doctrineGroup.isEmpty());
                button(shapes,445,560,147,40,false);
                button(shapes,650,561,110,40,false);
                button(shapes,1108,561,112,40,false);
                button(shapes,785,561,296,40,true);
                int start=standingPage==0?0:13;
                int end=standingPage==0?13:ShipStandingOrders.SETTINGS.length;
                for(int i=start;i<end;i++)
                    button(shapes,650,519-(i-start)*29,570,27,false);
                button(shapes,900,32,300,55,false);
            }
            case OPTIONS -> {
                for(int i=0;i<5;i++)
                    if(i!=3||updaterAvailable)button(shapes,405,472-i*78,470,59,false);
            }
            case RESULTS -> {
                rectangle(shapes,300,240,680,270,PANEL);
                button(shapes,351,122,254,58,false);
                button(shapes,673,122,254,58,true);
            }
            default -> {}
        }
        shapes.end();
        batch.begin();
        switch(screen){
            case HOME -> {
                title(batch,font,"CATASTROPHIC DIPLOMACY","TACTICAL FLEET COMMAND");
                for(int i=0;i<HOME_ACTIONS.length;i++)
                    label(batch,font,HOME_ACTIONS[i],474,520-i*64,Color.WHITE);
                label(batch,font,"CAMPAIGN  /  SKIRMISH  /  SHIP ENGINEERING",438,73,MUTED);
            }
            case CAMPAIGN_SETUP -> {
                title(batch,font,"NEW CAMPAIGN","SELECT THE FACTION YOU WILL COMMAND");
                Battle.Faction[] factions=Battle.Faction.values();
                int rows=(factions.length+1)/2;
                for(int i=0;i<factions.length;i++)
                    label(batch,font,factions[i].displayName,
                          101+(i/rows)*570,536-(i%rows)*62,Color.WHITE);
                if(progress!=null)
                    label(batch,font,"STARTING WILL REPLACE THE EXISTING CAMPAIGN SAVE",325,133,Color.SCARLET);
                label(batch,font,"CANCEL",132,69,Color.WHITE);
                label(batch,font,progress==null?"BEGIN CAMPAIGN":"REPLACE AND BEGIN",919,69,Color.WHITE);
            }
            case SKIRMISH -> {
                title(batch,font,"SKIRMISH","CHOOSE A SCENARIO AND DEPLOY A FITTED FLEET");
                int index=0;
                for(var choice:ScenarioCatalog.Preset.values())
                    label(batch,font,choice.title,49,622-(index++)*48,Color.WHITE);
                label(batch,font,"SCENARIO",653,586,Color.CYAN);
                label(batch,font,preset.title,654,549,Color.WHITE);
                label(batch,font,preset.description,654,510,MUTED);
                label(batch,font,"FLEET SIZE  "+(preset==ScenarioCatalog.Preset.CUSTOM?fleetCount:preset.ships)+"v"+
                    (preset==ScenarioCatalog.Preset.CUSTOM?fleetCount:preset.ships),654,453,Color.GOLD);
                label(batch,font,"ENVIRONMENT LEVEL "+preset.environmentLevel,654,409,Color.WHITE);
                if(preset==ScenarioCatalog.Preset.PROVING_GROUND){
                    label(batch,font,"FACTIONS: CANONICAL SIX-WEAPON FLEETS",677,359,Color.CYAN);
                    label(batch,font,"LOADOUTS: FIXED VERIFICATION FIXTURE",677,298,MUTED);
                }else{
                    label(batch,font,"LEFT FACTION: "+left.displayName+"  >",677,359,Color.WHITE);
                    label(batch,font,"RIGHT FACTION: "+right.displayName+"  >",677,298,Color.WHITE);
                }
                label(batch,font,preset==ScenarioCatalog.Preset.CUSTOM?
                        "CUSTOM FLEET SIZE: -  "+fleetCount+"  +":
                        "FLEET SIZE DEFINED BY SCENARIO",677,237,MUTED);
                label(batch,font,"BACK",70,67,Color.WHITE);
                if(preset!=ScenarioCatalog.Preset.PROVING_GROUND)
                    label(batch,font,"SHIPYARD",743,67,Color.WHITE);
                label(batch,font,preset.demonstration?"LAUNCH TEST":"DEPLOY FLEET",998,67,Color.WHITE);
            }
            case CAMPAIGN -> drawCampaign(batch,font,progress,page,selectedLevel);
            case RESEARCH -> {
                title(batch,font,"RESEARCH AND ENGINEERING",
                        "PAID FACTION LICENSES APPLY TO CAMPAIGN FITTINGS; SKIRMISH IS UNRESTRICTED");
                label(batch,font,"FACTION: "+progress.faction().displayName,151,559,Color.CYAN);
                label(batch,font,"CURRENT TECHNOLOGY RANK: "+progress.researchRank()+" / 6",
                        151,510,Color.GOLD);
                label(batch,font,"ADVANCED MODULES REQUIRE PURCHASED RESEARCH RANKS",
                        151,457,Color.WHITE);
                label(batch,font,"HARDWARE MK II-IV AND SPECIALIZED FIGHTERS REQUIRE LICENSES",
                        151,410,MUTED);
                label(batch,font,"GENERATION ACCESS IS GAINED THROUGH CAMPAIGN MISSIONS",
                        151,368,MUTED);
                label(batch,font,"CURRENT CREDITS: "+progress.credits(),151,305,Color.WHITE);
                label(batch,font,progress.researchRank()==6?"ALL RESEARCH RANKS PURCHASED":
                                "NEXT RANK COST: "+progress.nextResearchCost()+" CREDITS",
                        151,248,Color.GOLD);
                label(batch,font,dockNotice,151,194,Color.CYAN);
                label(batch,font,"BACK TO CAMPAIGN",69,67,Color.WHITE);
                label(batch,font,progress.researchRank()==6?"RESEARCH COMPLETE":"PURCHASE NEXT RANK",
                        923,67,Color.WHITE);
            }
            case DOCK -> {
                title(batch,font,"CAMPAIGN FLEET DOCK",
                        "OWNED HULLS ARE PERSISTENT  |  SELECT ACTIVE SHIPS AND SERVICE RESERVES");
                if(ownedFleet!=null){
                    label(batch,font,"OWNED "+ownedFleet.ships().size()+
                            "   ACTIVE "+ownedFleet.active().size()+
                            "   RESERVE "+ownedFleet.reserves().size(),44,594,Color.CYAN);
                    label(batch,font,"<",573,592,Color.WHITE);
                    label(batch,font,">",670,592,Color.WHITE);
                    label(batch,font,"CREDITS: "+progress.credits(),842,609,Color.GOLD);
                    label(batch,font,"NAME STYLE: "+ownedFleet.style(),842,581,MUTED);
                    for(int i=0;i<9;i++){
                        int index=dockPage*9+i;
                        if(index>=ownedFleet.ships().size())break;
                        var ship=ownedFleet.ships().get(index);
                        label(batch,font,(ship.deployed()?"ACTIVE":"RESERVE")+"  #"+ship.id()+
                                "  "+ship.name()+"  ["+ship.fitting().hull+"]",
                                53,547-i*43,index==dockSelected?Color.GOLD:
                                        ship.deployed()?Color.GREEN:Color.WHITE);
                    }
                    String[] names={"TOGGLE ACTIVE / RESERVE","REPAIR HULL / ARMOR / SHIELDS",
                            "RESTOCK CREW","RESTOCK AMMUNITION","REFILL FUEL",
                            "REPLACE FIGHTERS / DRONES","BUY NEW FITTED HULL",
                            "CHANGE NAME STYLE","GENERATE SHIP NAME","SERVICE ALL"};
                    for(int i=0;i<names.length;i++){
                        String cost=i>=1&&i<=5&&dockCosts!=null?
                                "  "+dockCosts[i-1]+" cr":
                                i==6?"  "+purchaseCost+" cr":
                                i==9&&dockCosts!=null?"  "+java.util.Arrays.stream(dockCosts).sum()+" cr":"";
                        if(i==8){
                            label(batch,font,"GENERATE NAME",847,553-i*46,Color.WHITE);
                            label(batch,font,"MANUAL RENAME",1036,553-i*46,Color.WHITE);
                        }else label(batch,font,names[i]+cost,847,553-i*46,
                                (i==6||i==9)?Color.GOLD:Color.WHITE);
                    }
                }
                label(batch,font,"BACK TO STAR MAP",54,67,Color.WHITE);
                label(batch,font,"SHIPYARD",352,67,Color.WHITE);
                label(batch,font,"RESEARCH",570,67,Color.WHITE);
                label(batch,font,"DEPLOY ACTIVE FLEET",1002,67,Color.WHITE);
                label(batch,font,dockNotice,45,112,Color.CYAN);
            }
            case DEPLOYMENT -> {
                LevelEconomy.Level stage=preparingCampaign?LevelEconomy.level(selectedLevel):null;
                Battle.Faction opponent=preparingCampaign?
                        CampaignMap.page(CampaignMap.pageOf(selectedLevel))
                                .get((selectedLevel-1)%CampaignMap.PAGE_SIZE).opponent():right;
                title(batch,font,"FLEET DEPLOYMENT",preparingCampaign?
                        "MISSION "+selectedLevel+"  |  SET YOUR SHIP COUNT AND FORMATION":
                        "SKIRMISH  |  PLACE EACH VESSEL AND PROGRAM INDIVIDUAL ORDERS");
                label(batch,font,"YOUR "+left.displayName+" BATTLE GROUP",58,580,Color.CYAN);
                label(batch,font,"TACTICAL STARTING POSITIONS",58,161,MUTED);
                label(batch,font,"ENEMY: "+opponent.displayName,846,563,Color.WHITE);
                label(batch,font,"HULL: "+friendlyHull,846,532,MUTED);
                label(batch,font,preparingCampaign?"WAVES: "+stage.waves():"SINGLE ENGAGEMENT",846,505,Color.WHITE);
                label(batch,font,"DEPLOYED: "+deployment.ships()+
                        (preparingCampaign?" / "+stage.maximumFleet():" FLEET"),963,464,Color.GOLD);
                if(preparingCampaign||preset==ScenarioCatalog.Preset.CUSTOM){
                    label(batch,font,"-",886,464,Color.WHITE);
                    label(batch,font,"+",1141,464,Color.WHITE);
                }
                label(batch,font,deployment.formation().title,959,355,Color.CYAN);
                label(batch,font,"<",887,355,Color.WHITE);
                label(batch,font,">",1139,355,Color.WHITE);
                label(batch,font,preparingCampaign?
                        "MAX VICTORY PAYOUT: "+
                                LevelEconomy.victoryCredits(stage,deployment.ships(),deployment.ships(),true):
                        "FITTED SHIPS: "+deployment.ships(),843,290,Color.GOLD);
                label(batch,font,"EDIT FITTED SHIP",896,249,Color.WHITE);
                label(batch,font,"SHIP ORDERS",945,180,Color.WHITE);
                label(batch,font,"SELECT AND DRAG A VESSEL TO REPOSITION",61,139,MUTED);
                label(batch,font,"SELECTED VESSEL #"+(selectedShip+1),846,120,Color.GOLD);
                label(batch,font,editNotice,61,115,MUTED);
                label(batch,font,preparingCampaign?"BACK TO MAP":"BACK TO SKIRMISH",68,68,Color.WHITE);
                label(batch,font,"BEGIN MISSION",978,68,Color.WHITE);
            }
            case SHIP_ORDERS -> {
                title(batch,font,"PER-SHIP STANDING ORDERS",
                        "PERSISTENT COMBAT DOCTRINE - ALL RULES OPERATE TOGETHER");
                ShipStandingOrders policy=deployment.standingFor(selectedShip);
                label(batch,font,"SHIP "+(selectedShip+1)+" / "+deployment.ships(),822,585,Color.GOLD);
                label(batch,font,"< PREVIOUS",656,584,Color.WHITE);
                label(batch,font,"NEXT >",1122,584,Color.WHITE);
                label(batch,font,standingPage==0?"PAGE 1: COMBAT / NEXT >":
                        "< PAGE 2: SUPPORT / BACK",799,585,Color.GOLD);
                int start=standingPage==0?0:13;
                int end=standingPage==0?13:ShipStandingOrders.SETTINGS.length;
                for(int i=start;i<end;i++){
                    float y=538-(i-start)*29;
                    label(batch,font,"< "+ShipStandingOrders.SETTINGS[i],661,y,MUTED);
                    label(batch,font,policy.display(i)+" >",1006,y,Color.WHITE);
                }
                label(batch,font,"MARK SHIPS",58,586,Color.WHITE);
                label(batch,font,"ALL",212,586,Color.WHITE);
                label(batch,font,"APPLY",326,586,Color.WHITE);
                label(batch,font,"CLEAR",468,586,Color.WHITE);
                label(batch,font,"GROUP: "+doctrineGroup.size()+" SELECTED"+(selectingGroup?" - TAP SHIPS TO MARK":""),49,620,Color.GREEN);
                label(batch,font,"BACK TO DEPLOYMENT",927,68,Color.WHITE);
                label(batch,font,"G: MARK   H: ALL   ENTER: APPLY   DEL: CLEAR",48,114,MUTED);
                label(batch,font,editNotice,48,86,Color.GOLD);
            }
            case OPTIONS -> {
                title(batch,font,"OPTIONS","AUDIO AND APPLICATION");
                label(batch,font,"MUSIC: "+(music?"ON":"OFF"),445,510,Color.WHITE);
                label(batch,font,"SOUND EFFECTS: "+(effects?"ON":"OFF"),445,432,Color.WHITE);
                label(batch,font,"SOUNDTRACK: "+(trackFinal?"FINAL SURGE":"COLD DRONE"),445,354,Color.WHITE);
                label(batch,font,updaterAvailable?"CHECK FOR UPDATES":"UPDATES: NOT AVAILABLE ON DESKTOP",445,276,Color.WHITE);
                label(batch,font,"BACK",445,198,Color.WHITE);
            }
            case RESULTS -> {
                title(batch,font,"AFTER-ACTION REPORT","THE ENGAGEMENT HAS ENDED");
                label(batch,font,won?"MISSION ACCOMPLISHED":
                        escaped+withdrawing>0&&surviving==0?"FLEET WITHDREW":"FLEET DEFEATED",
                        416,494,won?Color.GREEN:Color.SCARLET);
                label(batch,font,"ACTIVE SURVIVORS: "+surviving+" / "+deployed,416,448,Color.WHITE);
                label(batch,font,"WITHDRAWING: "+withdrawing+" (NOT SURVIVORS)",416,412,Color.CYAN);
                label(batch,font,"ESCAPED SHIPS: "+escaped+" (NO VICTORY CREDIT)",416,380,Color.CYAN);
                label(batch,font,"DESTROYED HULLS: "+destroyed,416,348,Color.WHITE);
                label(batch,font,"CREDITS AWARDED: "+reward,416,312,Color.GOLD);
                label(batch,font,"RETRY",423,157,Color.WHITE);
                label(batch,font,"RETURN TO "+(progress!=null?"CAMPAIGN":"SKIRMISH"),699,157,Color.WHITE);
            }
            default -> {}
        }
        batch.end();
    }
    private static void drawCampaign(SpriteBatch batch,BitmapFont font,
                                      CampaignProgress.State progress,int page,int selected){
        LevelEconomy.Level level=LevelEconomy.level(selected);
        title(batch,font,"CAMPAIGN STAR MAP","SECTOR "+level.sector()+"  |  REACHABLE SYSTEMS AND FLEET MISSIONS");
        label(batch,font,"CREDITS: "+progress.credits()+"   COMPLETED: "+progress.victories(),39,590,Color.GOLD);
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            label(batch,font,""+node.level(),node.x()-12,node.y()+38,MUTED);
        label(batch,font,"MISSION "+selected,946,568,Color.WHITE);
        label(batch,font,"SECTOR "+level.sector(),946,524,MUTED);
        label(batch,font,"ENEMY: "+CampaignMap.page(CampaignMap.pageOf(selected)).get((selected-1)%10).opponent().name(),946,482,MUTED);
        label(batch,font,"FLEET CAP: "+level.maximumFleet(),946,440,Color.WHITE);
        label(batch,font,"WAVES PLANNED: "+level.waves(),946,398,MUTED);
        label(batch,font,progress.available(selected)?"AVAILABLE":"LOCKED",946,343,
              progress.available(selected)?Color.GREEN:Color.SCARLET);
        label(batch,font,"FACTION: "+progress.faction().name(),946,296,Color.WHITE);
        var briefing=CampaignNarrative.forLevel(selected);
        label(batch,font,"CHAPTER "+briefing.chapter()+": "+briefing.title(),946,262,Color.CYAN);
        label(batch,font,briefing.intelligence(),946,231,MUTED);
        label(batch,font,briefing.beat(),946,202,Color.WHITE);
        label(batch,font,"HULL SIZES: 1-"+ShipLineages.maximumCampaignGeneration(progress.unlocked()),
                946,167,Color.GOLD);
        label(batch,font,"BACK",62,67,Color.WHITE);
        label(batch,font,"PREV",248,67,Color.WHITE);
        label(batch,font,"NEXT",402,67,Color.WHITE);
        label(batch,font,"RESEARCH",560,67,Color.WHITE);
        label(batch,font,"SHIPYARD",771,67,Color.WHITE);
        label(batch,font,"DEPLOY FLEET",989,67,Color.WHITE);
    }
}
