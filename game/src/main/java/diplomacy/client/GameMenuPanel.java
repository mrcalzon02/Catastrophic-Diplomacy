package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignStoryAtlas;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;
import java.util.Comparator;
import diplomacy.sim.CampaignDeployment;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignAfterAction;
import diplomacy.sim.FleetNames;
import diplomacy.sim.ShipStandingOrders;
import java.util.Set;
import diplomacy.sim.LevelEconomy;
import diplomacy.sim.ScenarioCatalog;
import diplomacy.sim.CampaignNarrative;
import diplomacy.shipyard.HullProgression;
import diplomacy.sim.LevelEconomy;
import java.util.List;

/** Lightweight native-font menu views; all hit regions are owned by the client flow. */
final class GameMenuPanel {
    enum Screen { HOME, CAMPAIGN_SETUP, SKIRMISH, CAMPAIGN, DOCK, NAME_THEMES, SQUADRONS, RESEARCH, TECHNOLOGY, DEPLOYMENT, SHIP_ORDERS, OPTIONS, BATTLE, RESULTS }
    private static final float W=1280,H=720;
    private static final Color PANEL=MilitaryUiTheme.PANEL;
    private static final Color SELECT=MilitaryUiTheme.SELECTION;
    private static final Color MUTED=MilitaryUiTheme.MUTED_TEXT;
    // No per-frame object allocation; one compact draw queue serves every menu.
    private final GrittyUiChrome grittyChrome=new GrittyUiChrome();
    private final GrittyMenuBackgrounds backgrounds=new GrittyMenuBackgrounds();
    private final float[] chromeCommands=new float[512*5];
    private int chromeCount;
    private float pointerX=-100,pointerY=-100;
    private boolean pointerActive,pointerDown;
    private Screen lastPressScreen,hoverScreen;
    private int recentPress=-1,lastHover=-1;
    private long pressUntilNs;

    private void enqueueChrome(float x,float y,float w,float h,int variant){
        if(chromeCount*5>=chromeCommands.length)
            throw new IllegalStateException("Native UI control queue overflow");
        int at=chromeCount++*5;
        chromeCommands[at]=x;chromeCommands[at+1]=y;
        chromeCommands[at+2]=w;chromeCommands[at+3]=h;
        chromeCommands[at+4]=variant;
    }

    void dispose(){grittyChrome.dispose();backgrounds.dispose();}
    void pointer(float x,float y,boolean active,boolean held){
        pointerX=x;pointerY=y;pointerActive=active;pointerDown=held;
    }
    /** -2 means a visible disabled button, -1 means no button at this point. */
    int controlAt(float x,float y){
        for(int i=chromeCount-1;i>=0;i--){
            int k=i*5;
            if(hit(x,y,chromeCommands[k],chromeCommands[k+1],
                    chromeCommands[k+2],chromeCommands[k+3]))
                return chromeCommands[k+4]<0?-2:i;
        }
        return -1;
    }
    void press(Screen screen,float x,float y){
        recentPress=controlAt(x,y);
        lastPressScreen=screen;
        pressUntilNs=System.nanoTime()+160_000_000L;
    }
    boolean enteredNewButton(Screen screen){
        int next=pointerActive&&!pointerDown?controlAt(pointerX,pointerY):-1;
        boolean entered=next>=0&&(next!=lastHover||hoverScreen!=screen);
        lastHover=next;hoverScreen=screen;
        return entered;
    }
    private void feedback(ShapeRenderer shapes,Screen screen){
        int hovering=pointerActive?controlAt(pointerX,pointerY):-1;
        int pressed=lastPressScreen==screen&&System.nanoTime()<pressUntilNs?recentPress:-1;
        for(int i=0;i<chromeCount;i++){
            int k=i*5;
            float x=chromeCommands[k],y=chromeCommands[k+1],
                    w=chromeCommands[k+2],h=chromeCommands[k+3];
            if(chromeCommands[k+4]<0){
                shapes.setColor(.035f,.044f,.048f,1f);
                shapes.rect(x+3,y+3,w-6,h-6);
                shapes.setColor(.18f,.20f,.20f,1f);
                shapes.rect(x+4,y+h-7,w-8,2);
                continue;
            }
            boolean down=i==pressed||(pointerDown&&i==hovering);
            if(i!=hovering&&!down)continue;
            shapes.setColor(down?MilitaryUiTheme.AMBER:MilitaryUiTheme.TELEMETRY);
            float thick=down?4:2;
            shapes.rect(x+2,y+2,w-4,thick);
            shapes.rect(x+2,y+h-2-thick,w-4,thick);
            shapes.rect(x+2,y+2,thick,h-4);
            shapes.rect(x+w-2-thick,y+2,thick,h-4);
            if(down)shapes.rect(x+10,y+8,w-20,3);
        }
    }

    private static final String[] HOME_ACTIONS={
        "CONTINUE CAMPAIGN","NEW CAMPAIGN","SKIRMISH","SHIPYARD","OPTIONS","EXIT","MULTIPLAYER"
    };

    /** Android landscape command tiles, independent of the desktop mouse layout. */
    private static final int[] MOBILE_HOME_ORDER={0,1,2,3,4,6,7,5};
    private static final float MOBILE_COLUMN_WIDTH=552f;
    private static final float MOBILE_VOICE_LEFT=225f,MOBILE_VOICE_WIDTH=832f;
    private static float mobileColumnX(int slot){return 72+(slot%2)*584;}
    private static float mobileHomeY(int slot){return 464-(slot/2)*120;}
    private static float mobileOptionY(int slot){return 350-(slot/2)*115;}
    private final boolean mobileLayout;
    GameMenuPanel(){this(false);}
    GameMenuPanel(boolean mobileLayout){this.mobileLayout=mobileLayout;}
    boolean mobileLayout(){return mobileLayout;}
    static boolean voiceHit(float x,float y,boolean mobile){
        return mobile?hit(x,y,120,462,1040,108):VoiceVolumeControl.hit(x,y);
    }
    static float voiceAt(float x,boolean mobile){
        return mobile?VoiceVolumeControl.clamp((x-MOBILE_VOICE_LEFT)/MOBILE_VOICE_WIDTH):
                VoiceVolumeControl.valueAt(x);
    }
    static boolean hit(float x,float y,float bx,float by,float w,float h){
        return x>=bx&&x<=bx+w&&y>=by&&y<=by+h;
    }
    /** Shared options hit contract, including a real disabled updater state. */
    static int optionsAction(float x,float y,boolean updaterAvailable){
        return optionsAction(x,y,updaterAvailable,false);
    }
    static int optionsAction(float x,float y,boolean updaterAvailable,boolean mobile){
        if(voiceHit(x,y,mobile))return 0;
        for(int i=0;i<5;i++){
            boolean inside=mobile?hit(x,y,mobileColumnX(i),mobileOptionY(i),
                    MOBILE_COLUMN_WIDTH,94):hit(x,y,405,472-i*78,470,59);
            if(inside)return i==3&&!updaterAvailable?-2:i+1;
        }
        return -1;
    }
    static int homeHit(float x,float y){return homeHit(x,y,false);}
    static int homeHit(float x,float y,boolean mobile){
        if(hit(x,y,880,633,205,55))return 8; // searchable encyclopedia
        if(mobile){
            for(int slot=0;slot<MOBILE_HOME_ORDER.length;slot++)
                if(hit(x,y,mobileColumnX(slot),mobileHomeY(slot),
                        MOBILE_COLUMN_WIDTH,96))return MOBILE_HOME_ORDER[slot];
            return -1;
        }
        for(int i=0;i<HOME_ACTIONS.length;i++)
            if(hit(x,y,438,487-i*64,404,50))return i;
        if(hit(x,y,438,35,404,50))return 7; // Unattended battle screensaver
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
        if(hit(x,y,847,570,165,37))return 8;
        if(hit(x,y,1025,570,166,37))return 9;
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
        if(hit(x,y,751,35,201,50))return 18; // squadron operations
        if(hit(x,y,548,566,86,43))return 2;
        if(hit(x,y,642,566,86,43))return 3;
        for(int i=0;i<9;i++)
            if(hit(x,y,45,521-i*43,746,38)&&page*9+i<ownedCount)
                return 100+page*9+i;
        for(int i=0;i<10;i++){
            if(i==8){
                if(hit(x,y,840,525-i*46,180,42))return 12; // generated rename
                if(hit(x,y,1028,525-i*46,186,42))return 14; // manual rename
            }else if(i==7){
                if(hit(x,y,840,525-i*46,180,42))return 11; // name theme
                if(hit(x,y,1028,525-i*46,186,42))return 17; // radio voice
            }else if(i==9){
                if(hit(x,y,840,525-i*46,180,42))return 13; // service all
                if(hit(x,y,1028,525-i*46,186,42))return 19; // refit selected reserve ship
            }else if(hit(x,y,840,525-i*46,374,42))return 4+i;
        }
        return -1;
    }
    /** Disjoint paging controls on the large commissioned-vessel report. */
    static final int NAME_THEME_PAGE_SIZE=14;
    static int nameThemePages(){
        return (FleetNames.Style.values().length+NAME_THEME_PAGE_SIZE-1)/NAME_THEME_PAGE_SIZE;
    }
    static FleetNames.Style nameThemeAt(int page,int slot){
        int index=page*NAME_THEME_PAGE_SIZE+slot;
        if(page<0||page>=nameThemePages()||slot<0||slot>=NAME_THEME_PAGE_SIZE||
                index>=FleetNames.Style.values().length)return null;
        return FleetNames.Style.values()[index];
    }
    /** 100+ordinal previews a style, zero cancels, three applies to the fleet. */
    static int nameThemeAction(float x,float y,int page){
        if(hit(x,y,35,35,196,55))return 0;
        if(page>0&&hit(x,y,256,35,163,55))return 1;
        if(page<nameThemePages()-1&&hit(x,y,440,35,163,55))return 2;
        if(hit(x,y,945,35,290,55))return 3;
        for(int col=0;col<2;col++)for(int row=0;row<7;row++){
            FleetNames.Style style=nameThemeAt(page,col*7+row);
            if(style!=null&&hit(x,y,52+col*604,515-row*55,565,47))
                return 100+style.ordinal();
        }
        return -1;
    }
    /** Inspect campaign licenses/laboratories, or authorize an actual paid project. */
    static int researchAction(float x,float y){
        if(hit(x,y,35,35,250,54))return 0;
        if(hit(x,y,306,35,255,54))return 3;
        if(hit(x,y,592,35,294,54))return 1;
        if(hit(x,y,906,35,330,54))return 2;
        for(int i=0;i<9;i++)
            if(hit(x,y,62+(i%3)*232,515-(i/3)*68,213,60))return 100+i;
        for(int i=0;i<4;i++)
            if(hit(x,y,62+i*176,250,160,70))return 200+i;
        return -1;
    }

    /** One shared input map for desktop mouse and Android touch. */
    static int technologyAction(float x,float y){
        if(hit(x,y,35,35,208,55))return 0;
        if(hit(x,y,48,144,112,45))return 1;
        if(hit(x,y,174,144,125,45))return 2;
        if(hit(x,y,917,35,317,55))return 3;
        for(int row=0;row<8;row++)
            if(hit(x,y,48,551-row*51,270,44))return 100+row;
        for(int track=0;track<4;track++)for(int stage=0;stage<9;stage++)
            if(hit(x,y,374+stage*49,439-track*100,42,49))
                return 200+track*9+stage;
        return -1;
    }
    static List<Component> technologySubsystems(TechnologyCatalog catalog,Battle.Faction faction){
        String code=CampaignTechnology.factionCode(faction);
        return catalog.baseComponents().values().stream()
                .filter(c->c.faction.equals(code))
                .sorted(Comparator.comparing((Component c)->c.category)
                        .thenComparing(c->c.name)).toList();
    }
    static String technologyId(Battle.Faction faction,String subsystem,int track,int stage){
        if(track<0||track>=4||stage<1||stage>9)
            throw new IllegalArgumentException("Invalid technology branch selection");
        String[] branches={"OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"};
        return "TECH-"+CampaignTechnology.factionCode(faction)+"-"+subsystem+"-"+
                branches[track]+"-"+String.format(java.util.Locale.ROOT,"%02d",stage);
    }
    /** Eight persistent roster groups independent of temporary tactical slots. */
    static int squadronAction(float x,float y,int current){
        if(hit(x,y,35,35,210,54))return 0;
        for(int group=0;group<CampaignFleet.SQUADRON_COUNT;group++)
            if(hit(x,y,45,524-group*55,580,47))return 100+group;
        if(hit(x,y,700,446,505,51))return 1;
        if(current>0&&hit(x,y,700,368,505,51))return 2;
        if(current>0&&hit(x,y,700,290,505,51))return 3;
        if(hit(x,y,700,212,505,51))return 4;
        if(hit(x,y,700,124,243,50))return 5; // mobilize complete group
        if(hit(x,y,962,124,243,50))return 6; // stand down complete group
        return -1;
    }
    static int afterActionAction(float x,float y){
        if(hit(x,y,825,253,160,44))return -1;
        if(hit(x,y,1011,253,165,44))return 1;
        return 0;
    }
    static int mapHit(float x,float y,int page){
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            if(Math.hypot(x-node.x(),y-node.y())<=26)return node.level();
        return -1;
    }
    private static void rectangle(ShapeRenderer shapes,float x,float y,float w,float h,Color color){
        if(color==PANEL&&w>=200&&h>=80)
            MilitaryUiTheme.panel(shapes,x,y,w,h);
        else{
            shapes.setColor(color);shapes.rect(x,y,w,h);
        }
    }
    private void button(ShapeRenderer shapes,float x,float y,float w,float h,boolean selected){
        MilitaryUiTheme.button(shapes,x,y,w,h,selected,MilitaryUiTheme.Intent.NORMAL);
        enqueueChrome(x,y,w,h,selected?GrittyUiChrome.SELECTED:GrittyUiChrome.NORMAL);
    }
    private void disabledButton(ShapeRenderer shapes,float x,float y,float w,float h){
        MilitaryUiTheme.button(shapes,x,y,w,h,false,MilitaryUiTheme.Intent.NORMAL);
        enqueueChrome(x,y,w,h,-1);
    }
    private void actionButton(ShapeRenderer shapes,float x,float y,float w,float h,
                              MilitaryUiTheme.Intent intent){
        MilitaryUiTheme.button(shapes,x,y,w,h,false,intent);
        int type=switch(intent){
            case PRIMARY -> GrittyUiChrome.PRIMARY;
            case CONFIRM -> GrittyUiChrome.CONFIRM;
            case WARNING -> GrittyUiChrome.WARNING;
            case DANGER -> GrittyUiChrome.DANGER;
            case NORMAL -> GrittyUiChrome.NORMAL;
        };
        enqueueChrome(x,y,w,h,type);
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
              float voiceVolume,
              boolean won,long reward,int surviving,int deployed,int escaped,int destroyed,
              int withdrawing,CampaignAfterAction.Report report,int reportPage,
               int themePage,FleetNames.Style themeCandidate,
               int selectedSquadron,Screen namingReturn,
               int selectedResearchNode,CampaignProgress.Project armedResearch,Screen researchReturn,
               TechnologyCatalog techCatalog,CampaignTechnology.State discoveries,
               int technologyPage,int technologyModule,int technologyTrack,int technologyStage,
               String technologyNotice,String armedTechnology){
        chromeCount=0;
        boolean textured=backgrounds.prepare(screen);
        if(textured){
            batch.begin();
            backgrounds.draw(batch);
            batch.end();
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        MilitaryUiTheme.canvas(shapes,W,H,textured);
        // Paint-only change: original hit regions and native text remain authoritative.
        rectangle(shapes,0,622,W,2,MilitaryUiTheme.FRAME_BRIGHT);
        rectangle(shapes,37,622,132,2,MilitaryUiTheme.AMBER);
        // Same visible, touch-sized wiki launcher on every native menu.
        button(shapes,880,633,205,55,false);
        switch(screen){
            case HOME -> {
                if(mobileLayout){
                    rectangle(shapes,62,578,1156,3,MilitaryUiTheme.FRAME_BRIGHT);
                    rectangle(shapes,62,578,215,3,MilitaryUiTheme.AMBER);
                    for(int slot=0;slot<MOBILE_HOME_ORDER.length;slot++){
                        int action=MOBILE_HOME_ORDER[slot];
                        float x=mobileColumnX(slot),y=mobileHomeY(slot);
                        if(action==0||action==1||action==7)
                            actionButton(shapes,x,y,MOBILE_COLUMN_WIDTH,96,
                                    MilitaryUiTheme.Intent.PRIMARY);
                        else button(shapes,x,y,MOBILE_COLUMN_WIDTH,96,false);
                    }
                }else{
                    for(int i=0;i<HOME_ACTIONS.length;i++)
                        button(shapes,438,487-i*64,404,50,false);
                    button(shapes,438,35,404,50,false);
                }
            }
            case CAMPAIGN_SETUP -> {
                Battle.Faction[] factions=Battle.Faction.values();
                int rows=(factions.length+1)/2;
                for(int i=0;i<factions.length;i++)
                    button(shapes,80+(i/rows)*570,502-(i%rows)*62,520,49,
                            factions[i]==selectedFaction);
                button(shapes,80,35,230,54,false);
                actionButton(shapes,892,35,308,54,MilitaryUiTheme.Intent.PRIMARY);
            }
            case SKIRMISH -> {
                rectangle(shapes,610,113,638,524,PANEL);
                ScenarioCatalog.Preset[] presets=ScenarioCatalog.Preset.values();
                for(int i=0;i<presets.length;i++)
                    button(shapes,30,592-i*48,550,44,presets[i]==preset);
                button(shapes,30,35,160,49,false);
                if(!preset.demonstration)
                    button(shapes,702,35,225,49,false);
                actionButton(shapes,969,35,277,49,MilitaryUiTheme.Intent.PRIMARY);
                if(!preset.demonstration){
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
                    shapes.circle(node.x(),node.y(),node.level()==selectedLevel?19:
                            node.sectorFinale()?17:13,18);
                    shapes.setColor(MilitaryUiTheme.BACKGROUND);
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
                rectangle(shapes,35,163,748,453,PANEL);
                rectangle(shapes,797,163,447,453,PANEL);
                for(int i=0;i<9;i++)
                    button(shapes,62+(i%3)*232,515-(i/3)*68,213,60,
                            selectedResearchNode==i);
                for(int i=0;i<4;i++)
                    button(shapes,62+i*176,250,160,70,selectedResearchNode==100+i);
                button(shapes,35,35,250,54,false);
                button(shapes,306,35,255,54,false);
                actionButton(shapes,592,35,294,54,
                        armedResearch==CampaignProgress.Project.ACCELERATOR?
                                MilitaryUiTheme.Intent.CONFIRM:MilitaryUiTheme.Intent.NORMAL);
                actionButton(shapes,906,35,330,54,
                        armedResearch==CampaignProgress.Project.LICENSE?
                                MilitaryUiTheme.Intent.CONFIRM:MilitaryUiTheme.Intent.PRIMARY);
            }
            case TECHNOLOGY -> {
                rectangle(shapes,30,130,304,484,PANEL);
                rectangle(shapes,345,130,488,484,PANEL);
                rectangle(shapes,844,130,406,484,PANEL);
                int modules=technologySubsystems(techCatalog,progress.faction()).size();
                for(int row=0;row<8;row++){
                    int index=technologyPage*8+row;
                    if(index>=modules)break;
                    button(shapes,48,551-row*51,270,44,index==technologyModule);
                }
                shapes.setColor(MilitaryUiTheme.FRAME_BRIGHT);
                for(int track=0;track<4;track++)
                    for(int link=0;link<8;link++)
                        shapes.rectLine(416+link*49,463-track*100,
                                423+link*49,463-track*100,2f);
                for(int track=0;track<4;track++)
                    for(int stage=0;stage<9;stage++)
                        button(shapes,374+stage*49,439-track*100,42,49,
                                track==technologyTrack&&stage+1==technologyStage);
                button(shapes,48,144,112,45,false);
                button(shapes,174,144,125,45,false);
                button(shapes,35,35,208,55,false);
                actionButton(shapes,917,35,317,55,
                        armedTechnology!=null?MilitaryUiTheme.Intent.CONFIRM:
                                MilitaryUiTheme.Intent.PRIMARY);
            }
            case DOCK -> {
                rectangle(shapes,33,108,771,509,PANEL);
                rectangle(shapes,818,108,427,509,PANEL);
                button(shapes,35,35,165,50,false);
                actionButton(shapes,980,35,260,50,MilitaryUiTheme.Intent.PRIMARY);
                button(shapes,310,35,200,50,false);
                button(shapes,530,35,210,50,false);
                button(shapes,751,35,201,50,false);
                button(shapes,548,566,86,43,false);
                button(shapes,642,566,86,43,false);
                if(ownedFleet!=null)
                    for(int i=0;i<9;i++){
                        int index=dockPage*9+i;
                        if(index>=ownedFleet.ships().size())break;
                        button(shapes,45,521-i*43,746,38,index==dockSelected);
                    }
                for(int i=0;i<10;i++){
                    if(i==7||i==8||i==9){
                        button(shapes,840,525-i*46,180,42,false);
                        button(shapes,1028,525-i*46,186,42,false);
                    }else button(shapes,840,525-i*46,374,42,false);
                }
            }
            case SQUADRONS -> {
                rectangle(shapes,33,107,606,508,PANEL);
                rectangle(shapes,666,107,579,508,PANEL);
                for(int group=0;group<CampaignFleet.SQUADRON_COUNT;group++)
                    button(shapes,45,524-group*55,580,47,group==selectedSquadron);
                button(shapes,35,35,210,54,false);
                button(shapes,700,446,505,51,false);
                if(selectedSquadron>0){
                    button(shapes,700,368,505,51,false);
                    button(shapes,700,290,505,51,false);
                }
                button(shapes,700,212,505,51,false);
                actionButton(shapes,700,124,243,50,MilitaryUiTheme.Intent.CONFIRM);
                actionButton(shapes,962,124,243,50,MilitaryUiTheme.Intent.WARNING);
            }
            case NAME_THEMES -> {
                rectangle(shapes,34,103,1212,510,PANEL);
                rectangle(shapes,45,108,1190,80,SELECT);
                for(int col=0;col<2;col++)for(int row=0;row<7;row++){
                    FleetNames.Style style=nameThemeAt(themePage,col*7+row);
                    if(style!=null)
                        button(shapes,52+col*604,515-row*55,565,47,
                                style==themeCandidate);
                }
                button(shapes,35,35,196,55,false);
                if(themePage>0)button(shapes,256,35,163,55,false);
                if(themePage<nameThemePages()-1)button(shapes,440,35,163,55,false);
                actionButton(shapes,945,35,290,55,MilitaryUiTheme.Intent.PRIMARY);
            }
            case DEPLOYMENT -> {
                rectangle(shapes,39,135,760,472,PANEL);
                rectangle(shapes,819,135,425,472,PANEL);
                if(!preparingCampaign){
                    button(shapes,847,570,165,37,false);
                    button(shapes,1025,570,166,37,false);
                }
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
                actionButton(shapes,913,35,302,55,MilitaryUiTheme.Intent.PRIMARY);
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
                if(mobileLayout){
                    rectangle(shapes,120,462,1040,108,PANEL);
                    rectangle(shapes,MOBILE_VOICE_LEFT,485,MOBILE_VOICE_WIDTH,13,
                            MilitaryUiTheme.FRAME);
                    rectangle(shapes,MOBILE_VOICE_LEFT,485,
                            MOBILE_VOICE_WIDTH*VoiceVolumeControl.clamp(voiceVolume),13,
                            MilitaryUiTheme.TELEMETRY);
                    rectangle(shapes,MOBILE_VOICE_LEFT+
                            MOBILE_VOICE_WIDTH*VoiceVolumeControl.clamp(voiceVolume)-11,
                            474,22,35,MilitaryUiTheme.AMBER);
                    for(int i=0;i<5;i++){
                        float x=mobileColumnX(i),y=mobileOptionY(i);
                        if(i==3&&!updaterAvailable)
                            disabledButton(shapes,x,y,MOBILE_COLUMN_WIDTH,94);
                        else button(shapes,x,y,MOBILE_COLUMN_WIDTH,94,false);
                    }
                }else{
                for(int i=0;i<5;i++){
                    if(i==3&&!updaterAvailable)disabledButton(shapes,405,472-i*78,470,59);
                    else button(shapes,405,472-i*78,470,59,false);
                }
                rectangle(shapes,405,544,470,72,MilitaryUiTheme.PANEL);
                rectangle(shapes,VoiceVolumeControl.LEFT,560,VoiceVolumeControl.WIDTH,7,
                        MilitaryUiTheme.FRAME);
                rectangle(shapes,VoiceVolumeControl.LEFT,560,
                        VoiceVolumeControl.WIDTH*VoiceVolumeControl.clamp(voiceVolume),7,
                        MilitaryUiTheme.TELEMETRY);
                rectangle(shapes,VoiceVolumeControl.thumbX(voiceVolume)-7,552,14,24,
                        MilitaryUiTheme.AMBER);

                }
            }
            case RESULTS -> {
                if(report!=null){
                    rectangle(shapes,35,231,1210,379,PANEL);
                    rectangle(shapes,490,244,743,352,SELECT);
                    button(shapes,825,253,160,44,false);
                    button(shapes,1011,253,165,44,false);
                }else rectangle(shapes,300,240,680,270,PANEL);
                actionButton(shapes,351,122,254,58,MilitaryUiTheme.Intent.WARNING);
                actionButton(shapes,673,122,254,58,MilitaryUiTheme.Intent.PRIMARY);
            }
            default -> {}
        }
        shapes.end();
        batch.begin();
        for(int i=0;i<chromeCount;i++){
            int at=i*5;
            grittyChrome.draw(batch,(int)chromeCommands[at+4],
                    chromeCommands[at],chromeCommands[at+1],
                    chromeCommands[at+2],chromeCommands[at+3]);
        }
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        feedback(shapes,screen);
        shapes.end();
        batch.begin();
        switch(screen){
            case HOME -> {
                if(mobileLayout){
                    float original=font.getData().scaleX;
                    font.getData().setScale(2.05f);
                    label(batch,font,"CATASTROPHIC DIPLOMACY",73,667,Color.WHITE);
                    font.getData().setScale(1.52f);
                    label(batch,font,"TACTICAL FLEET COMMAND",74,613,MUTED);
                    font.getData().setScale(2.05f);
                    for(int slot=0;slot<MOBILE_HOME_ORDER.length;slot++){
                        int action=MOBILE_HOME_ORDER[slot];
                        String name=action==7?"BATTLE SCREENSAVER":HOME_ACTIONS[action];
                        label(batch,font,name,mobileColumnX(slot)+32,
                                mobileHomeY(slot)+60,Color.WHITE);
                    }
                    font.getData().setScale(original);
                }else{
                title(batch,font,"CATASTROPHIC DIPLOMACY","TACTICAL FLEET COMMAND");
                for(int i=0;i<HOME_ACTIONS.length;i++)
                    label(batch,font,HOME_ACTIONS[i],474,520-i*64,Color.WHITE);
                label(batch,font,"BATTLE SCREENSAVER",474,68,Color.WHITE);
                label(batch,font,"CAMPAIGN  /  SKIRMISH  /  SHIP ENGINEERING",438,15,MUTED);

                }
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
                if(!preset.demonstration){
                    label(batch,font,shortLabel(dockNotice,62),654,201,Color.CYAN);
                    label(batch,font,"TECH LOADOUT AND HULL GATES: EDIT IN SHIPYARD",654,166,MUTED);
                }
                if(preset.demonstration){
                    label(batch,font,preset==ScenarioCatalog.Preset.HULL_COVERAGE?
                            "HULLS: ALL 11 SIZES / 22 ACTUAL SHIPS":
                            "FACTIONS: CANONICAL SIX-WEAPON FLEETS",677,359,Color.CYAN);
                    label(batch,font,preset==ScenarioCatalog.Preset.HULL_COVERAGE?
                            "FITTINGS: MIXED MODULE AND FACTION LAYOUTS":
                            "LOADOUTS: FIXED VERIFICATION FIXTURE",677,298,MUTED);
                }else{
                    label(batch,font,"LEFT FACTION: "+left.displayName+"  >",677,359,Color.WHITE);
                    label(batch,font,"RIGHT FACTION: "+right.displayName+"  >",677,298,Color.WHITE);
                }
                label(batch,font,preset==ScenarioCatalog.Preset.CUSTOM?
                        "CUSTOM FLEET SIZE: -  "+fleetCount+"  +":
                        "FLEET SIZE DEFINED BY SCENARIO",677,237,MUTED);
                label(batch,font,"BACK",70,67,Color.WHITE);
                if(!preset.demonstration)
                    label(batch,font,"SHIPYARD",743,67,Color.WHITE);
                label(batch,font,preset.demonstration?"LAUNCH TEST":"DEPLOY FLEET",998,67,Color.WHITE);
            }
            case CAMPAIGN -> drawCampaign(batch,font,progress,page,selectedLevel);
            case RESEARCH -> {
                title(batch,font,"CAMPAIGN RESEARCH - LICENSES & LABS",
                        "CURRENT RESEARCH PROJECTS / FUNDED WITH CAMPAIGN CREDITS");
                label(batch,font,"ENGINEERING LICENSE TIERS",62,587,Color.CYAN);
                label(batch,font,"LABORATORY ACCELERATION",62,339,Color.CYAN);
                for(int i=0;i<9;i++){
                    int tier=i+1;
                    float x=80+(i%3)*232,y=552-(i/3)*68;
                    boolean done=tier<=progress.researchRank();
                    boolean next=tier==progress.researchRank()+1;
                    boolean running=next&&progress.activeProject()==CampaignProgress.Project.LICENSE;
                    label(batch,font,"LICENSE "+tier,x,y,done?Color.GREEN:
                            selectedResearchNode==i?Color.GOLD:Color.WHITE);
                    label(batch,font,done?"COMPLETED":running?"RESEARCHING":
                            next?"NEXT AVAILABLE":"PREREQUISITE LOCKED",
                            x,y-27,done?Color.GREEN:next?Color.CYAN:MUTED);
                    label(batch,font,HullProgression.tierLabel(tier),
                            x,y-47,tier<=3?Color.CYAN:MUTED);
                }
                for(int i=0;i<4;i++){
                    int tier=i+1;float x=75+i*176;
                    boolean done=tier<=progress.speedUpgrades();
                    boolean next=tier==progress.speedUpgrades()+1;
                    boolean running=next&&progress.activeProject()==CampaignProgress.Project.ACCELERATOR;
                    label(batch,font,"LAB "+tier,x,290,done?Color.GREEN:
                            selectedResearchNode==100+i?Color.GOLD:Color.WHITE);
                    label(batch,font,done?"INSTALLED":running?"RESEARCHING":
                            next?"NEXT PROJECT":"LOCKED",x,268,done?Color.GREEN:next?Color.CYAN:MUTED);
                }
                boolean license=selectedResearchNode<100;
                int tier=license?selectedResearchNode+1:selectedResearchNode-99;
                boolean done=license?tier<=progress.researchRank():tier<=progress.speedUpgrades();
                boolean next=license?tier==progress.researchRank()+1:
                        tier==progress.speedUpgrades()+1;
                long cost=license?LevelEconomy.draftResearchCost(tier):
                        LevelEconomy.researchSpeedCost(tier);
                var quote=CampaignBudgetQuotes.research(progress,license?
                        CampaignProgress.Project.LICENSE:CampaignProgress.Project.ACCELERATOR);
                label(batch,font,"CAMPAIGN RESEARCH BUDGET",819,585,Color.CYAN);
                label(batch,font,"WALLET  "+progress.credits()+" CR",819,548,Color.GOLD);
                label(batch,font,"SELECTED: "+(license?"LICENSE ":"LAB UPGRADE ")+tier,
                        819,508,Color.WHITE);
                label(batch,font,"PROJECT COST: "+cost+" CR",819,470,Color.WHITE);
                label(batch,font,"BALANCE AFTER: "+(progress.credits()-cost)+" CR",
                        819,436,progress.credits()>=cost?Color.GREEN:Color.SCARLET);
                label(batch,font,"STATUS: "+(done?"ALREADY OWNED":next?
                        quote.reason():"COMPLETE PREVIOUS TIER"),819,399,
                        done?Color.GREEN:next&&quote.allowed()?Color.CYAN:Color.GOLD);
                if(!license&&!done)
                    label(batch,font,"REQUIRES MISSION "+
                            LevelEconomy.researchSpeedMilestone(tier)+" CLEAR",
                            819,367,MUTED);
                label(batch,font,"ACTIVE: "+progress.activeProject()+
                        "  WORK LEFT "+progress.missionsUntilResearchComplete(),
                        819,332,Color.WHITE);
                label(batch,font,"ONE SUCCESSFUL MISSION = ONE RESEARCH TICK",
                        819,300,MUTED);
                label(batch,font,shortLabel(dockNotice,63),819,228,Color.CYAN);
                label(batch,font,"BASE HULL SIZES BY TECH III | SPECIALIZED CHASSIS IV-VI",
                        56,183,MUTED);
                label(batch,font,"MODULE UPGRADES: RESEARCH EACH BRANCH SEPARATELY",
                        56,158,MUTED);
                label(batch,font,researchReturn==Screen.DOCK?"BACK TO DOCK":"BACK TO CAMPAIGN",
                        66,68,Color.WHITE);
                label(batch,font,"TECHNOLOGY LAB",323,67,Color.CYAN);
                label(batch,font,armedResearch==CampaignProgress.Project.ACCELERATOR?
                        "CONFIRM LAB SPEND":"AUTHORIZE NEXT LAB",605,67,Color.WHITE);
                label(batch,font,armedResearch==CampaignProgress.Project.LICENSE?
                        "CONFIRM LICENSE SPEND":"AUTHORIZE NEXT LICENSE",921,67,Color.WHITE);
            }
            case TECHNOLOGY -> {
                title(batch,font,"FACTION TECHNOLOGY LAB",
                        "INDIVIDUAL DISCOVERIES / PAID ONCE / PERSISTENT CAMPAIGN UNLOCKS");
                List<Component> components=technologySubsystems(techCatalog,progress.faction());
                label(batch,font,"SUBSYSTEM CATALOG",50,588,Color.CYAN);
                label(batch,font,"BRANCH / SIX DEVELOPMENT STAGES",366,585,Color.CYAN);
                for(int row=0;row<8;row++){
                    int index=technologyPage*8+row;
                    if(index>=components.size())break;
                    Component c=components.get(index);
                    label(batch,font,shortLabel(c.name,30),57,580-row*51,
                            index==technologyModule?Color.GOLD:Color.WHITE);
                    label(batch,font,c.subsystem+"  /  "+c.category.toUpperCase(java.util.Locale.ROOT),
                            57,563-row*51,MUTED);
                }
                label(batch,font,"PREV",64,174,Color.WHITE);
                label(batch,font,"NEXT",191,174,Color.WHITE);
                label(batch,font,"PAGE "+(technologyPage+1)+" / "+
                        Math.max(1,(components.size()+7)/8),55,124,MUTED);
                Component selected=components.get(Math.min(components.size()-1,technologyModule));
                String[] tracks={"OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"};
                for(int track=0;track<4;track++){
                    label(batch,font,tracks[track],369,530-track*100,Color.CYAN);
                    for(int stage=1;stage<=9;stage++){
                        String id=technologyId(progress.faction(),selected.subsystem,track,stage);
                        boolean known=discoveries.has(id);
                        boolean current=id.equals(discoveries.activeId());
                        Color color=known?Color.GREEN:current?Color.CYAN:
                                technologyTrack==track&&technologyStage==stage?Color.GOLD:Color.WHITE;
                        label(batch,font,""+stage,382+(stage-1)*49,469-track*100,color);
                    }
                }
                String id=technologyId(progress.faction(),selected.subsystem,
                        technologyTrack,technologyStage);
                TechnologyCatalog.Upgrade upgrade=techCatalog.get(id);
                CampaignTechnology.Quote quote=discoveries.quote(id,progress,techCatalog);
                label(batch,font,"PROJECT DOSSIER",867,583,Color.CYAN);
                label(batch,font,shortLabel(selected.name,29),865,548,Color.GOLD);
                label(batch,font,tracks[technologyTrack]+" / STAGE "+technologyStage,
                        865,515,Color.WHITE);
                label(batch,font,"RESEARCH COST: "+quote.cost()+" CR",865,477,Color.WHITE);
                label(batch,font,"AVAILABLE: "+progress.credits()+" CR",865,447,Color.GOLD);
                label(batch,font,"AFTER PAYMENT: "+(progress.credits()-quote.cost())+" CR",
                        865,417,progress.credits()>=quote.cost()?Color.GREEN:Color.SCARLET);
                label(batch,font,"PREREQUISITE:",865,379,MUTED);
                label(batch,font,shortLabel(upgrade.prerequisite,37),865,350,Color.WHITE);
                label(batch,font,"STATUS: "+shortLabel(quote.reason(),29),865,312,
                        quote.allowed()?Color.CYAN:Color.GOLD);
                label(batch,font,"ACTIVE: "+
                        shortLabel(discoveries.activeId()==null?"NONE":discoveries.activeId(),27),
                        865,275,Color.WHITE);
                label(batch,font,"WORK: "+discoveries.remaining()+
                        " MISSIONS / UNLOCKED "+discoveries.unlocked().size(),865,245,Color.WHITE);
                label(batch,font,shortLabel(upgrade.tradeoff,48),865,215,MUTED);
                label(batch,font,String.format(java.util.Locale.ROOT,
                        "OUTPUT x%.2f / MASS x%.2f / HEAT x%.2f",
                        upgrade.output,upgrade.mass,upgrade.heat),865,184,Color.CYAN);
                label(batch,font,shortLabel(technologyNotice,100),352,104,Color.CYAN);
                label(batch,font,"BACK TO LICENSES",49,68,Color.WHITE);
                label(batch,font,armedTechnology!=null&&armedTechnology.equals(id)?
                        "CONFIRM RESEARCH":"AUTHORIZE PROJECT",945,68,Color.WHITE);
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
                    label(batch,font,"NAME STYLE: "+ownedFleet.style()+
                            "  |  RADIO: "+ownedFleet.radioTone(),842,581,MUTED);
                    for(int i=0;i<9;i++){
                        int index=dockPage*9+i;
                        if(index>=ownedFleet.ships().size())break;
                        var ship=ownedFleet.ships().get(index);
                        label(batch,font,(ship.deployed()?"ACTIVE":"RESERVE")+"  "+
                                ship.name()+"  ["+ship.fitting().hull+"]",
                                53,547-i*43,index==dockSelected?Color.GOLD:
                                        ship.deployed()?Color.GREEN:Color.WHITE);
                        label(batch,font,"ID "+ship.id(),59,530-i*43,MUTED);
                        if(index==dockSelected&&dockCosts!=null){
                            java.util.StringJoiner deficits=new java.util.StringJoiner("/");
                            var services=CampaignFleet.Service.values();
                            for(int service=0;service<services.length;service++)
                                if(dockCosts[service]>0)deficits.add(services[service].name());
                            boolean ready=deficits.length()==0;
                            label(batch,font,ready?"FULLY SERVICED":"NEEDS "+deficits,
                                    263,530-i*43,ready?Color.GREEN:Color.GOLD);
                        }
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
                        if(i==9){
                            label(batch,font,"SERVICE ALL"+cost,847,553-i*46,Color.GOLD);
                            label(batch,font,"REFIT HULL",1036,553-i*46,Color.WHITE);
                        }else if(i==8){
                            label(batch,font,"GENERATE NAME",847,553-i*46,Color.WHITE);
                            label(batch,font,"MANUAL RENAME",1036,553-i*46,Color.WHITE);
                        }else if(i==7){
                            label(batch,font,"NAME THEME",847,553-i*46,Color.WHITE);
                            label(batch,font,"RADIO VOICE",1036,553-i*46,Color.WHITE);
                        }else label(batch,font,names[i]+cost,847,553-i*46,
                                (i==6||i==9)?Color.GOLD:Color.WHITE);
                    }
                }
                label(batch,font,"BACK TO STAR MAP",54,67,Color.WHITE);
                label(batch,font,"SHIPYARD",352,67,Color.WHITE);
                label(batch,font,"RESEARCH",570,67,Color.WHITE);
                label(batch,font,"SQUADRONS",772,67,Color.WHITE);
                label(batch,font,"DEPLOY ACTIVE FLEET",1002,67,Color.WHITE);
                label(batch,font,dockNotice,45,112,Color.CYAN);
            }
            case SQUADRONS -> {
                title(batch,font,"COMMISSIONED SQUADRONS",
                        "ASSIGN OWNED SHIPS; NAME CULTURES AND RADIO VOICES BELONG TO GROUPS");
                if(ownedFleet!=null&&!ownedFleet.ships().isEmpty()){
                    var selected=ownedFleet.ships().get(Math.max(0,
                            Math.min(dockSelected,ownedFleet.ships().size()-1)));
                    label(batch,font,"SELECTED: "+shortLabel(selected.name(),42),
                            45,593,Color.CYAN);
                    label(batch,font,"ID "+selected.id()+"  |  CURRENT GROUP "+
                            ownedFleet.squadronOf(selected.id()),45,568,MUTED);
                    for(int group=0;group<CampaignFleet.SQUADRON_COUNT;group++){
                        var profile=ownedFleet.profile(group);
                        String title=group==0?"0  FLEET STANDARD":group+"  SQUADRON";
                        label(batch,font,title+"  |  "+
                                ownedFleet.squadronShips(group).size()+" HULLS / "+
                                ownedFleet.squadronShips(group).stream().filter(
                                        CampaignFleet.Vessel::deployed).count()+" ACTIVE",
                                63,554-group*55,
                                group==selectedSquadron?Color.GOLD:Color.WHITE);
                        label(batch,font,shortLabel(profile.naming().name()+" / "+
                                profile.radio().name(),45),
                                73,534-group*55,MUTED);
                    }
                    var selectedProfile=ownedFleet.profile(selectedSquadron);
                    label(batch,font,"GROUP "+selectedSquadron+
                            (selectedSquadron==0?" (INHERITS FLEET)":""),
                            688,576,Color.GOLD);
                    label(batch,font,"NAME CULTURE: "+selectedProfile.naming(),
                            688,547,Color.WHITE);
                    label(batch,font,"RADIO VOICE: "+selectedProfile.radio(),
                            688,523,Color.WHITE);
                    label(batch,font,"ASSIGN SELECTED SHIP TO THIS GROUP",718,477,Color.WHITE);
                    if(selectedSquadron>0){
                        label(batch,font,"CHOOSE GROUP NAMING CULTURE",718,399,Color.WHITE);
                        label(batch,font,"CYCLE GROUP RADIO VOICE",718,321,Color.WHITE);
                    }else label(batch,font,"Group 0 uses the fleet defaults",718,381,MUTED);
                    label(batch,font,"GENERATE NAME IN ASSIGNED GROUP",718,243,Color.WHITE);
                    label(batch,font,"Squadron mobilization obeys the mission fleet cap",
                            700,194,MUTED);
                    label(batch,font,"ACTIVATE GROUP",715,154,Color.WHITE);
                    label(batch,font,"RESERVE GROUP",978,154,Color.WHITE);
                    label(batch,font,shortLabel(dockNotice,57),700,103,Color.CYAN);
                }
                label(batch,font,"BACK TO DOCK",63,69,Color.WHITE);
            }
            case NAME_THEMES -> {
                title(batch,font,"SHIP NAMING TRADITIONS",
                        "SELECT A CULTURE, PREVIEW TWO SHIPS, THEN APPLY TO FUTURE NAMES");
                label(batch,font,"PAGE "+(themePage+1)+" / "+nameThemePages()+
                        "  |  "+FleetNames.Style.values().length+" AVAILABLE THEMES",
                        52,586,Color.CYAN);
                for(int col=0;col<2;col++)for(int row=0;row<7;row++){
                    FleetNames.Style style=nameThemeAt(themePage,col*7+row);
                    if(style==null)continue;
                    String status=ownedFleet!=null&&style==ownedFleet.style()?"  [ACTIVE]":
                            style==themeCandidate?"  [PREVIEW]":"";
                    label(batch,font,style.name().replace('_',' ')+status,
                            73+col*604,544-row*55,
                            style==themeCandidate?Color.GOLD:Color.WHITE);
                }
                label(batch,font,"PREVIEW: "+themeCandidate.name().replace('_',' ')+
                        (namingReturn==Screen.SQUADRONS?"  |  SQUADRON "+selectedSquadron:
                                "  |  FLEET DEFAULT"),
                        65,168,Color.GOLD);
                if(ownedFleet!=null){
                    label(batch,font,shortLabel(FleetNames.generate(themeCandidate,
                            ownedFleet.namingSeed(),ownedFleet.nextSerial()),73),
                            66,144,Color.WHITE);
                    label(batch,font,shortLabel(FleetNames.generate(themeCandidate,
                            ownedFleet.namingSeed(),ownedFleet.nextSerial()+1),73),
                            66,119,MUTED);
                }
                label(batch,font,"CANCEL",73,69,Color.WHITE);
                if(themePage>0)label(batch,font,"PREVIOUS",270,69,Color.WHITE);
                if(themePage<nameThemePages()-1)label(batch,font,"NEXT PAGE",456,69,Color.WHITE);
                label(batch,font,"APPLY THEME",990,69,Color.WHITE);
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
                if(!preparingCampaign){
                    label(batch,font,"EXPORT FLEETS",865,594,Color.CYAN);
                    label(batch,font,"IMPORT FLEETS",1041,594,Color.CYAN);
                }
                label(batch,font,"ENEMY: "+opponent.displayName,846,551,Color.WHITE);
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
                label(batch,font,"SELECTED VESSEL #"+(selectedShip+1)+
                        (preparingCampaign?"":" / INDIVIDUAL FITTING"),846,120,Color.GOLD);
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
                if(mobileLayout){
                    float original=font.getData().scaleX;
                    font.getData().setScale(2.10f);
                    label(batch,font,"OPTIONS",73,662,Color.WHITE);
                    font.getData().setScale(1.52f);
                    label(batch,font,"AUDIO AND APPLICATION",74,613,MUTED);
                    font.getData().setScale(1.85f);
                    label(batch,font,"VOICE VOLUME",153,532,Color.WHITE);
                    label(batch,font,Math.round(VoiceVolumeControl.clamp(voiceVolume)*100f)+"%",
                            1057,532,Color.WHITE);
                    String[] names={"MUSIC: "+(music?"ON":"OFF"),
                            "SOUND EFFECTS: "+(effects?"ON":"OFF"),
                            "SOUNDTRACK: "+(trackFinal?"FINAL SURGE":"COLD DRONE"),
                            updaterAvailable?"CHECK FOR UPDATES":"UPDATES UNAVAILABLE",
                            "BACK"};
                    for(int i=0;i<names.length;i++)
                        label(batch,font,names[i],mobileColumnX(i)+30,mobileOptionY(i)+58,
                                i==3&&!updaterAvailable?MUTED:Color.WHITE);
                    font.getData().setScale(original);
                }else{
                title(batch,font,"OPTIONS","AUDIO AND APPLICATION");
                label(batch,font,"VOICE VOLUME",427,598,Color.WHITE);
                label(batch,font,Math.round(VoiceVolumeControl.clamp(voiceVolume)*100f)+"%",817,598,Color.WHITE);
                label(batch,font,"MUSIC: "+(music?"ON":"OFF"),445,510,Color.WHITE);
                label(batch,font,"SOUND EFFECTS: "+(effects?"ON":"OFF"),445,432,Color.WHITE);
                label(batch,font,"SOUNDTRACK: "+(trackFinal?"FINAL SURGE":"COLD DRONE"),445,354,Color.WHITE);
                label(batch,font,updaterAvailable?"CHECK FOR UPDATES":"UPDATES UNAVAILABLE",445,276,
                        updaterAvailable?Color.WHITE:MUTED);
                label(batch,font,"BACK",445,198,Color.WHITE);

                }
            }
            case RESULTS -> {
                title(batch,font,"AFTER-ACTION REPORT","THE ENGAGEMENT HAS ENDED");
                if(report!=null){
                    label(batch,font,won?"MISSION ACCOMPLISHED":"BATTLE ENDED",
                            56,565,won?Color.GREEN:Color.SCARLET);
                    label(batch,font,"SURVIVED: "+
                            report.count(CampaignAfterAction.Outcome.SURVIVED),56,516,Color.WHITE);
                    label(batch,font,"WITHDRAWING: "+
                            report.count(CampaignAfterAction.Outcome.WITHDRAWING),56,473,Color.CYAN);
                    label(batch,font,"ESCAPED: "+
                            report.count(CampaignAfterAction.Outcome.ESCAPED),56,430,Color.CYAN);
                    label(batch,font,"DESTROYED: "+
                            report.count(CampaignAfterAction.Outcome.DESTROYED),56,387,Color.SCARLET);
                    label(batch,font,"CREDITS AWARDED: "+reward,56,344,Color.GOLD);
                    int pages=report.pages(5);
                    int safe=Math.max(0,Math.min(pages-1,reportPage));
                    label(batch,font,"COMMISSIONED SHIPS  PAGE "+(safe+1)+"/"+pages,
                            510,579,Color.WHITE);
                    int row=0;
                    for(var ship:report.page(safe,5)){
                        int y=542-row*48;
                        label(batch,font,shortLabel(ship.name(),23)+
                                " ["+ship.hull().name().replace('_',' ')+"] "+ship.outcome(),
                                510,y,ship.outcome()==CampaignAfterAction.Outcome.DESTROYED?
                                        Color.SCARLET:Color.WHITE);
                        label(batch,font,"ID "+ship.id(),510,y-17,MUTED);
                        label(batch,font,"HULL "+ship.hullPercent()+"% | CREW LOST "+ship.crewLost()+
                                " | AMMO K/M/T "+ship.kineticSpent()+"/"+
                                ship.missileSpent()+"/"+ship.torpedoSpent()+
                                " | FUEL "+ship.fuelSpent(),510,y-20,MUTED);
                        row++;
                    }
                    label(batch,font,"< PREVIOUS",843,284,Color.WHITE);
                    label(batch,font,"NEXT >",1034,284,Color.WHITE);
                }else{
                    label(batch,font,won?"MISSION ACCOMPLISHED":
                            escaped+withdrawing>0&&surviving==0?"FLEET WITHDREW":"FLEET DEFEATED",
                            416,494,won?Color.GREEN:Color.SCARLET);
                    label(batch,font,"ACTIVE SURVIVORS: "+surviving+" / "+deployed,416,448,Color.WHITE);
                    label(batch,font,"WITHDRAWING: "+withdrawing+" (NOT SURVIVORS)",416,412,Color.CYAN);
                    label(batch,font,"ESCAPED SHIPS: "+escaped+" (NO VICTORY CREDIT)",416,380,Color.CYAN);
                    label(batch,font,"DESTROYED HULLS: "+destroyed,416,348,Color.WHITE);
                    label(batch,font,"CREDITS AWARDED: "+reward,416,312,Color.GOLD);
                }
                label(batch,font,"RETRY",423,157,Color.WHITE);
                label(batch,font,"RETURN TO "+(progress!=null?"CAMPAIGN":"SKIRMISH"),699,157,Color.WHITE);
            }
            default -> {}
        }
        label(batch,font,"FIELD WIKI (F1)",897,670,Color.CYAN);
        batch.end();
    }
    private static String shortLabel(String value,int max){
        return value.length()<=max?value:value.substring(0,max-3)+"...";
    }
    private static void drawCampaign(SpriteBatch batch,BitmapFont font,
                                      CampaignProgress.State progress,int page,int selected){
        LevelEconomy.Level level=LevelEconomy.level(selected);
        title(batch,font,"CAMPAIGN STAR MAP","SECTOR "+level.sector()+"  |  REACHABLE SYSTEMS AND FLEET MISSIONS");
        label(batch,font,"CREDITS: "+progress.credits()+"   COMPLETED: "+progress.victories(),39,590,Color.GOLD);
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            label(batch,font,""+node.level(),node.x()-12,node.y()+38,MUTED);
        label(batch,font,"MISSION "+selected+": "+
                shortLabel(CampaignStoryAtlas.nodeName(selected),19),
                946,568,Color.WHITE);
        label(batch,font,"SECTOR "+level.sector()+": "+
                shortLabel(CampaignStoryAtlas.regionName(selected),21),
                946,524,MUTED);
        var node=CampaignMap.page(CampaignMap.pageOf(selected))
                .get((selected-1)%CampaignMap.PAGE_SIZE);
        label(batch,font,"ENEMY: "+node.opponent().name(),946,482,MUTED);
        label(batch,font,"OPERATION: "+node.operation().title.toUpperCase(java.util.Locale.ROOT),
                946,460,Color.CYAN);
        label(batch,font,"ENEMY TECH: "+node.enemyTier()+" / IX",946,439,Color.GOLD);
        label(batch,font,"FLEET CAP: "+level.maximumFleet(),946,414,Color.WHITE);
        label(batch,font,"WAVES PLANNED: "+level.waves(),946,389,MUTED);
        label(batch,font,progress.available(selected)?"AVAILABLE":"LOCKED",946,343,
              progress.available(selected)?Color.GREEN:Color.SCARLET);
        label(batch,font,progress.isReplay(selected)?
                "CLEARED: 25% REPLAY CREDITS":"FIRST CLEAR: FULL CREDITS",
                946,312,progress.isReplay(selected)?MUTED:Color.GOLD);
        label(batch,font,"FACTION: "+progress.faction().name(),946,285,Color.WHITE);
        var briefing=CampaignNarrative.forLevel(selected);
        label(batch,font,"CHAPTER "+briefing.chapter()+": "+briefing.title(),946,262,Color.CYAN);
        label(batch,font,briefing.intelligence(),946,231,MUTED);
        label(batch,font,shortLabel(CampaignStoryAtlas.frontObjective(selected),42),
                946,202,Color.WHITE);
        label(batch,font,"BASE HULLS: "+HullProgression.baseFamiliesAtTier(progress.researchRank())+
                "/11 | TECH "+progress.researchRank(),946,167,Color.GOLD);
        label(batch,font,"BACK",62,67,Color.WHITE);
        label(batch,font,"PREV",248,67,Color.WHITE);
        label(batch,font,"NEXT",402,67,Color.WHITE);
        label(batch,font,"RESEARCH",560,67,Color.WHITE);
        label(batch,font,"SHIPYARD",771,67,Color.WHITE);
        label(batch,font,"DEPLOY FLEET",989,67,Color.WHITE);
    }
}
