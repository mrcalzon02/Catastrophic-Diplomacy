package diplomacy.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import diplomacy.sim.Battle;
import diplomacy.sim.BattleOrders;
import diplomacy.sim.BattleOrdersCodec;
import diplomacy.sim.BattleView;
import diplomacy.sim.DemonstrationBattle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignEncounter;
import diplomacy.sim.CampaignDeployment;
import diplomacy.sim.ShipCommandQueue;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.LevelEconomy;
import diplomacy.sim.ScenarioCatalog;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import diplomacy.sim.SpaceWeather;
import diplomacy.sim.LevelEnvironment;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.FleetPresets;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;

/** Same interactive client on Windows/Linux desktop and Android; no duplicate rules. */
public final class CatastrophicGame extends ApplicationAdapter {
    private static final float W = 1280, H = 720;
    private OrthographicCamera camera, battleCamera;
    private FitViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private InstancedShipRenderer instanced;
    private TerranPlaceholderSprites terranSprites;
    private WeaponBeamSprites weaponBeams;
    private ModularWeaponFxRenderer weaponFx;
    private ProceduralStarfield starfield;
    private BattlefieldWeatherVfx weatherVfx;
    private CombatAudio audio;
    private int battlefieldLevel = 1;
    private boolean weatherEnabled = true;
    private TacticalUiIcons uiIcons;
    private Battle battle;
    private GameMenuPanel menuPanel;
    private GameMenuPanel.Screen screen=GameMenuPanel.Screen.HOME;
    private ScenarioCatalog.Preset scenario=ScenarioCatalog.Preset.CUSTOM;
    private CampaignProgress.State campaign;
    private Battle.Faction pendingFaction=Battle.Faction.TERRAN;
    private int campaignPage,selectedMission=1,activeMission;
    private int deployedAtLaunch,survivorsAtEnd;
    private CampaignEncounter campaignEncounter;
    private CampaignDeployment.Loadout deployment=CampaignDeployment.defaultFor(LevelEconomy.level(1));
    private int selectedDeploymentShip;
    private int placementDragPointer=-1;
    private boolean waitingForWaypoint;
    private boolean preparingCampaign=true,preparedSkirmish;
    private String deploymentNotice="Tap a ship then drag it anywhere within the non-overlapping deployment zone.";
    private long lastReward;
    private boolean playedCampaign,lastWon;
    private static final String CAMPAIGN_FILE="campaign/progress-v1.dat";
    private TechnologyCatalog technology;
    private Shipyard.Design leftDesign, rightDesign;
    private ShipyardPanel shipyard;
    private Battle.Faction left = Battle.Faction.TERRAN, right = Battle.Faction.DRAXIAN;
    private long nextSeed = 2479;
    private final BattleOrders.Orders[] fleetOrders={
            BattleOrders.DEFAULT,BattleOrders.DEFAULT};
    private FleetOrdersPanel fleetOrdersPanel;
    private boolean resumeAfterOrders;
    private float accumulator;
    private boolean running = true;
    private boolean tacticalMenuOpen;
    private boolean resumeAfterMenu;
    private int speed = 1;
    /** Keyboard [ ] and the compact header controls select actual deployed ships per side. */
    private static final int[] FLEET_SIZES = {1,2,4,8,16,32,64,100,150,200};
    private int fleetIndex = 4;
    private float manualZoom = 1f;
    private int focusedShipId = -1;
    private String renderer = "software geometry";
    private String saveState = "FLEETS NOT SAVED";
    private static final String FLEET_FILE = "fleet/fleets-v1.dat";
    private static final String ORDERS_FILE = "fleet/orders-v1.dat";
    private final boolean verifyDemo;
    private final Runnable updateCheck;
    private long verifiedSequence;
    private int verifiedFrames,verifiedShots,verifiedImpacts,peakAnimations,peakCraft,peakOrdnance;
    private boolean verificationReported;
    private final EnumSet<Battle.DamageType> verifiedTypes=EnumSet.noneOf(Battle.DamageType.class);
    private final Set<Long> verifiedShotIds=new HashSet<>();
    public CatastrophicGame(){this(null,false);}
    public CatastrophicGame(boolean verifyDemo){this(null,verifyDemo);}
    public CatastrophicGame(Runnable updater,boolean verifyDemo){
        this.updateCheck=updater;this.verifyDemo=verifyDemo;
        if(verifyDemo){fleetIndex=FLEET_SIZES.length-1;speed=8;}
    }

    @Override public void create() {
        camera = new OrthographicCamera();
        battleCamera = new OrthographicCamera();
        battleCamera.setToOrtho(false,W,H);
        viewport = new FitViewport(W, H, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        shapes = new ShapeRenderer(); batch = new SpriteBatch(); font = new BitmapFont();
        audio = new CombatAudio();
        terranSprites = new TerranPlaceholderSprites();
        weaponBeams = new WeaponBeamSprites();
        weaponFx = new ModularWeaponFxRenderer();
        uiIcons = new TacticalUiIcons();
        try {
            starfield = new ProceduralStarfield();
            starfield.useLevel(battlefieldLevel);
        } catch (RuntimeException error) {
            Gdx.app.error("SKY", "Starfield disabled; continuing in plain background", error);
            if (starfield != null) starfield.dispose();
            starfield = null;
        }
        try {
            weatherVfx = new BattlefieldWeatherVfx();
        } catch (RuntimeException error) {
            Gdx.app.error("WEATHER-FX", "Visual hazard renderer unavailable", error);
            weatherVfx = null;
        }
        font.getData().setScale(1.23f);
        try {
            instanced = new InstancedShipRenderer();
            renderer = "OpenGL 3 instancing";
        } catch (RuntimeException error) {
            Gdx.app.error("GPU", "Instanced renderer unavailable; falling back", error);
        }
        try { technology = TechnologyCatalog.loadDefault(); }
        catch (IOException error) { throw new IllegalStateException("Technology resources missing from game distribution", error); }
        leftDesign=Shipyard.starter(left,Shipyard.Hull.CORVETTE);
        rightDesign=Shipyard.starter(right,Shipyard.Hull.CORVETTE);
        restoreFleets();
        restoreOrderPresets();
        restoreCampaign();
        shipyard = new ShipyardPanel(technology,(team,design)->{
            if(team==0){leftDesign=design;left=design.faction;} else {rightDesign=design;right=design.faction;}
            saveFleets();
            reset();
        },leftDesign,rightDesign,terranSprites);
        shipyard.setIcons(uiIcons);
        fleetOrdersPanel=new FleetOrdersPanel(side->fleetOrders[side],this::applyFleetOrders);
        fleetOrdersPanel.setIcons(uiIcons);
        menuPanel=new GameMenuPanel();
        if(verifyDemo){screen=GameMenuPanel.Screen.BATTLE;reset();}
        else{running=false;screen=GameMenuPanel.Screen.HOME;Gdx.app.log("CD-GAME-FLOW","MAIN_MENU");}
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if(!verifyDemo&&screen!=GameMenuPanel.Screen.BATTLE&&!shipyard.open()){
                    if(key==Input.Keys.ESCAPE||key==Input.Keys.BACK){
                        if(screen==GameMenuPanel.Screen.RESULTS)leaveResults();
                        else if(screen==GameMenuPanel.Screen.SHIP_ORDERS){
                            screen=GameMenuPanel.Screen.DEPLOYMENT;waitingForWaypoint=false;
                        }else if(screen==GameMenuPanel.Screen.DEPLOYMENT)
                            screen=preparingCampaign?GameMenuPanel.Screen.CAMPAIGN:GameMenuPanel.Screen.SKIRMISH;
                        else screen=GameMenuPanel.Screen.HOME;
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.HOME){
                        if(key>=Input.Keys.NUM_1 && key<=Input.Keys.NUM_6)
                            tapFrontEnd(610,512-(key-Input.Keys.NUM_1)*64);
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.OPTIONS){
                        if(key==Input.Keys.U)audio.toggleMusic();
                        if(key==Input.Keys.I)audio.toggleEffects();
                        if(key==Input.Keys.J)audio.toggleTrack();
                        if(key==Input.Keys.K&&updateCheck!=null)updateCheck.run();
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.RESULTS){
                        if(key==Input.Keys.R){
                            if(playedCampaign)launchCampaignMission();else launchSkirmish();
                        }else if(key==Input.Keys.ENTER)leaveResults();
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.CAMPAIGN_SETUP){
                        if(key==Input.Keys.ENTER){newCampaign();return true;}
                        if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT||
                                key==Input.Keys.UP||key==Input.Keys.DOWN){
                            Battle.Faction[] choices=Battle.Faction.values();
                            int delta=(key==Input.Keys.RIGHT||key==Input.Keys.DOWN)?1:-1;
                            pendingFaction=choices[Math.floorMod(pendingFaction.ordinal()+delta,choices.length)];
                        }
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.SKIRMISH){
                        if(key==Input.Keys.Q&&scenario!=ScenarioCatalog.Preset.PROVING_GROUND)changeFaction(0);
                        if(key==Input.Keys.E&&scenario!=ScenarioCatalog.Preset.PROVING_GROUND)changeFaction(1);
                        if(key==Input.Keys.LEFT_BRACKET&&scenario==ScenarioCatalog.Preset.CUSTOM)changeFleetSize(-1);
                        if(key==Input.Keys.RIGHT_BRACKET&&scenario==ScenarioCatalog.Preset.CUSTOM)changeFleetSize(1);
                        if((key==Input.Keys.S||key==Input.Keys.TAB)&&scenario!=ScenarioCatalog.Preset.PROVING_GROUND)shipyard.show();
                        if(key==Input.Keys.ENTER){openSkirmishDeployment();return true;}
                        if(key==Input.Keys.UP||key==Input.Keys.DOWN){
                            int step=key==Input.Keys.UP?-1:1;
                            var values=ScenarioCatalog.Preset.values();
                            selectScenario(values[Math.floorMod(scenario.ordinal()+step,values.length)]);
                            return true;
                        }
                    }
                    if(screen==GameMenuPanel.Screen.SHIP_ORDERS){
                        if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT)
                            selectedDeploymentShip=Math.floorMod(selectedDeploymentShip+
                                    (key==Input.Keys.LEFT?-1:1),deployment.ships());
                        if(key==Input.Keys.M)waitingForWaypoint=true;
                        if(key==Input.Keys.H)appendShipCommand(ShipCommandQueue.Command.hold(300));
                        if(key==Input.Keys.E)appendShipCommand(ShipCommandQueue.Command.engage(300));
                        if(key==Input.Keys.Z)deployment=deployment.undoOrder(selectedDeploymentShip);
                        if(key==Input.Keys.C)deployment=deployment.clearOrders(selectedDeploymentShip);
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.DEPLOYMENT){
                        if(key==Input.Keys.O)screen=GameMenuPanel.Screen.SHIP_ORDERS;
                        if(key==Input.Keys.A||key==Input.Keys.D)
                            selectedDeploymentShip=Math.floorMod(selectedDeploymentShip+
                                    (key==Input.Keys.A?-1:1),deployment.ships());
                        if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT){
                            int delta=key==Input.Keys.LEFT?-1:1;
                            if(preparingCampaign||scenario==ScenarioCatalog.Preset.CUSTOM)
                                deployment=deployment.withShips(delta,
                                        preparingCampaign?LevelEconomy.level(activeMission).maximumFleet():200);
                            selectedDeploymentShip=Math.min(selectedDeploymentShip,deployment.ships()-1);
                        }
                        if(key==Input.Keys.UP||key==Input.Keys.DOWN){
                            deployment=deployment.withFormation(key==Input.Keys.UP?-1:1);
                        }
                        if(key==Input.Keys.TAB||key==Input.Keys.S)shipyard.showCampaignFleet();
                        if(key==Input.Keys.ENTER){
                            if(preparingCampaign)launchCampaignMission();else launchSkirmish();
                        }
                        return true;
                    }
                    if(screen==GameMenuPanel.Screen.CAMPAIGN){
                        if(key==Input.Keys.LEFT||key==Input.Keys.RIGHT){
                            selectedMission=Math.max(1,Math.min(LevelEconomy.LEVEL_COUNT,
                                    selectedMission+(key==Input.Keys.LEFT?-1:1)));
                            campaignPage=CampaignMap.pageOf(selectedMission);
                        }
                        if(key==Input.Keys.ENTER)launchCampaignMission();
                        return true;
                    }
                    return true;
                }
                if (key == Input.Keys.U) { audio.toggleMusic(); audio.ui(); return true; }
                if (key == Input.Keys.I) { audio.toggleEffects(); audio.ui(); return true; }
                if (key == Input.Keys.J) { audio.toggleTrack(); audio.ui(); return true; }
                if (key == Input.Keys.K && updateCheck!=null){updateCheck.run();return true;}
                if(fleetOrdersPanel!=null && fleetOrdersPanel.open()) {
                    if(key==Input.Keys.ESCAPE || key==Input.Keys.F)closeFleetOrders();
                    return true;
                }
                if (tacticalMenuOpen) {
                    if (key == Input.Keys.ESCAPE || key == Input.Keys.M) closeTacticalMenu();
                    return true;
                }
                if (shipyard.open()) return shipyard.key(key);
                if (key == Input.Keys.M || key == Input.Keys.ESCAPE || key == Input.Keys.BACK) { openTacticalMenu(); return true; }
                if (key == Input.Keys.TAB || key == Input.Keys.Y) { shipyard.show(); return true; }
                if (key == Input.Keys.F) { openFleetOrders(); return true; }
                if (key == Input.Keys.SPACE) { running = !running; return true; }
                if (key == Input.Keys.R) { reset(); return true; }
                if (key == Input.Keys.N) { changeBattlefieldLevel(1); return true; }
                if (key == Input.Keys.B) { changeBattlefieldLevel(-1); return true; }
                if (key == Input.Keys.W) { if(!playedCampaign){weatherEnabled = !weatherEnabled; reset();} return true; }
                if (key == Input.Keys.Z) { zoomBy(-1); return true; }
                if (key == Input.Keys.X) { zoomBy(1); return true; }
                if (key == Input.Keys.C) { manualZoom=1f; focusedShipId=-1; return true; }
                if (key == Input.Keys.LEFT_BRACKET) { changeFleetSize(-1); return true; }
                if (key == Input.Keys.RIGHT_BRACKET) { changeFleetSize(1); return true; }
                if (key == Input.Keys.PLUS || key == Input.Keys.EQUALS) { speed = Math.min(16, speed * 2); return true; }
                if (key == Input.Keys.MINUS) { speed = Math.max(1, speed / 2); return true; }
                if (key == Input.Keys.P) { togglePd(0); return true; }
                if (key == Input.Keys.O) { togglePd(1); return true; }
                if (key == Input.Keys.Q) { changeFaction(0); return true; }
                if (key == Input.Keys.E) { changeFaction(1); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                com.badlogic.gdx.math.Vector2 world = viewport.unproject(new com.badlogic.gdx.math.Vector2(x, y));
                if(!verifyDemo&&screen!=GameMenuPanel.Screen.BATTLE&&!shipyard.open()){
                    if(screen==GameMenuPanel.Screen.DEPLOYMENT &&
                            world.x>=45&&world.x<=795&&world.y>=155&&world.y<=606){
                        int hit=GameMenuPanel.shipHit(world.x,world.y,deployment,false);
                        if(hit>=0){
                            selectedDeploymentShip=hit;
                            placementDragPointer=pointer;
                            deploymentNotice="Selected ship #"+(hit+1)+"; drag inside deployment zone.";
                            return true;
                        }
                    }
                    if(screen==GameMenuPanel.Screen.SHIP_ORDERS &&
                            world.x>=60&&world.x<=776&&world.y>=161&&world.y<=593){
                        if(waitingForWaypoint){
                            appendShipCommand(ShipCommandQueue.Command.move(
                                    GameMenuPanel.ordersWorldX(world.x),
                                    GameMenuPanel.ordersWorldY(world.y)));
                            waitingForWaypoint=false;
                        }else{
                            int hit=GameMenuPanel.shipHit(world.x,world.y,deployment,true);
                            if(hit>=0)selectedDeploymentShip=hit;
                        }
                        return true;
                    }
                    tapFrontEnd(world.x,world.y);
                    return true;
                }
                if (fleetOrdersPanel!=null&&fleetOrdersPanel.open()) {
                    if(world.y>=622){closeFleetOrders();return true;}
                    return fleetOrdersPanel.tap(world.x,world.y);
                }
                if (tacticalMenuOpen) { tapTacticalMenu(world.x,world.y); return true; }
                if(!shipyard.open() && updateCheck!=null
                        && world.x>=1010 && world.x<1135
                        && world.y>=71 && world.y<=124){
                    updateCheck.run();return true;
                }
                if (!shipyard.open() && world.x >= 1140 && world.y >= 71 && world.y <= 124) {
                    openTacticalMenu(); return true;
                }
                if (shipyard.open()) {
                    shipyard.startTouch(world.x,world.y,pointer);
                    return true;
                }
                if (world.y >= 72 && world.y <= 120) {
                    if (world.x>=18 && world.x<118) { zoomBy(-1); return true; }
                    if (world.x>=126 && world.x<226) { zoomBy(1); return true; }
                    if (world.x>=235 && world.x<330) {
                        focusedShipId=-1;manualZoom=1f;return true;
                    }
                }
                if (world.y >= 613 && world.y < 649) {
                    if (world.x < 830) { if(!playedCampaign){weatherEnabled = !weatherEnabled; reset();} return true; }
                    if (world.x >= 900 && world.x < 1040) { changeBattlefieldLevel(-1); return true; }
                    if (world.x >= 1170) { changeBattlefieldLevel(1); return true; }
                }
                if (world.y > 650) {
                    if (world.x > 1160) { shipyard.show(); return true; }
                    if (world.x >= 1080) changeFleetSize(1);
                    else if (world.x >= 990) changeFleetSize(-1);
                    else if (world.x < 125) running = !running;
                    else if (world.x < 250) reset();
                    else if (world.x < 365) speed = Math.max(1, speed / 2);
                    else if (world.x < 475) speed = Math.min(16, speed * 2);
                    else if (world.x < 745) changeFaction(0);
                    else changeFaction(1);
                    return true;
                }
                if (world.y>25 && world.y<610) {
                    focusNearest(world.x,world.y);
                    return true;
                }
                return false;
            }
            @Override public boolean touchDragged(int x,int y,int pointer) {
                com.badlogic.gdx.math.Vector2 world=viewport.unproject(new com.badlogic.gdx.math.Vector2(x,y));
                if(screen==GameMenuPanel.Screen.DEPLOYMENT&&pointer==placementDragPointer){
                    try{
                        deployment=deployment.withPlacement(selectedDeploymentShip,
                                GameMenuPanel.placementWorldX(world.x),
                                GameMenuPanel.placementWorldY(world.y));
                        deploymentNotice="Ship #"+(selectedDeploymentShip+1)+" position accepted.";
                    }catch(IllegalArgumentException invalid){
                        deploymentNotice="Position rejected: overlap or outside deployment zone.";
                    }
                    return true;
                }
                if(!shipyard.open())return false;
                return shipyard.drag(world.x,world.y,pointer);
            }
            @Override public boolean touchUp(int x,int y,int pointer,int button) {
                if(pointer==placementDragPointer){
                    placementDragPointer=-1;
                    return true;
                }
                com.badlogic.gdx.math.Vector2 world=viewport.unproject(new com.badlogic.gdx.math.Vector2(x,y));
                boolean activated = shipyard.endTouch(world.x,world.y,pointer);
                if (activated && audio != null) audio.ui();
                return activated;
            }
            @Override public boolean scrolled(float amountX,float amountY) {
                if(shipyard.open())return shipyard.scroll(amountY);
                if(tacticalMenuOpen || (fleetOrdersPanel!=null&&fleetOrdersPanel.open()))return false;
                zoomBy(amountY);
                return true;
            }
        });
    }

    /** Manual zoom leaves the renderer-independent automatic framing untouched. */
    private void zoomBy(float steps) {
        manualZoom=(float)Math.max(0.18,Math.min(4.0,manualZoom*Math.pow(1.18,steps)));
    }
    /** Tap a vessel to follow it while zoomed in. Empty space clears focus. */
    private void focusNearest(float uiX,float uiY) {
        if(battle==null)return;
        double x=battleCamera.position.x+(uiX-W*0.5)*battleCamera.zoom;
        double y=battleCamera.position.y+(uiY-H*0.5)*battleCamera.zoom;
        double nearest=Math.max(24,34*battleCamera.zoom);
        int chosen=-1;
        for(Battle.Ship ship:battle.ships())if(ship.alive()){
            double d=Math.hypot(ship.x-x,ship.y-y);
            if(d<nearest){nearest=d;chosen=ship.id;}
        }
        focusedShipId=chosen;
    }

    private void selectScenario(ScenarioCatalog.Preset choice){
        preparedSkirmish=false;
        scenario=choice;
        if(choice!=ScenarioCatalog.Preset.CUSTOM&&!choice.demonstration){
            leftDesign=ScenarioCatalog.defaultDesign(choice,left,true,technology);
            rightDesign=ScenarioCatalog.defaultDesign(choice,right,false,technology);
            shipyard.sync(0,leftDesign);shipyard.sync(1,rightDesign);
        }
    }
    private void openSkirmishDeployment(){
        if(scenario.demonstration){
            preparedSkirmish=false;
            launchSkirmish(); // Canonical 200v200 verification never receives player overrides.
            return;
        }
        preparingCampaign=false;
        preparedSkirmish=true;
        deployment=new CampaignDeployment.Loadout(
                scenario==ScenarioCatalog.Preset.CUSTOM?FLEET_SIZES[fleetIndex]:scenario.ships,
                CampaignDeployment.Formation.LINE);
        selectedDeploymentShip=0;
        placementDragPointer=-1;
        waitingForWaypoint=false;
        deploymentNotice="Drag your fleet within the zone. Each hull has its own ordered commands.";
        screen=GameMenuPanel.Screen.DEPLOYMENT;
        Gdx.app.log("CD-GAME-FLOW","SKIRMISH_DEPLOYMENT "+scenario.name());
    }
    private void launchSkirmish(){
        playedCampaign=false;
        battlefieldLevel=scenario.environmentLevel;
        screen=GameMenuPanel.Screen.BATTLE;
        deployedAtLaunch=preparedSkirmish?deployment.ships():
                scenario==ScenarioCatalog.Preset.CUSTOM?FLEET_SIZES[fleetIndex]:scenario.ships;
        reset();
        Gdx.app.log("CD-GAME-FLOW","BATTLE "+scenario.name());
    }
    private void openCampaignSetup(){
        pendingFaction=campaign==null?left:campaign.faction();
        screen=GameMenuPanel.Screen.CAMPAIGN_SETUP;
        Gdx.app.log("CD-GAME-FLOW","CAMPAIGN_FACTION_SETUP");
    }
    private void newCampaign(){
        campaign=CampaignProgress.start(pendingFaction);
        saveCampaign();
        campaignPage=0;selectedMission=1;
        screen=GameMenuPanel.Screen.CAMPAIGN;
        Gdx.app.log("CD-GAME-FLOW","CAMPAIGN_MAP 1");
    }
    private void showCampaign(){
        if(campaign==null){openCampaignSetup();return;}
        selectedMission=campaign.unlocked();
        campaignPage=CampaignMap.pageOf(selectedMission);
        screen=GameMenuPanel.Screen.CAMPAIGN;
    }
    private void openDeployment(){
        if(campaign==null||!campaign.available(selectedMission))return;
        activeMission=selectedMission;
        LevelEconomy.Level stage=LevelEconomy.level(activeMission);
        left=campaign.faction();
        if(leftDesign.faction!=left)leftDesign=Shipyard.starter(left,Shipyard.Hull.CORVETTE);
        shipyard.sync(0,leftDesign);
        preparingCampaign=true;
        deployment=CampaignDeployment.defaultFor(stage);
        selectedDeploymentShip=0;
        placementDragPointer=-1;
        waitingForWaypoint=false;
        deploymentNotice="Drag ships inside the deployment zone. No overlaps allowed.";
        screen=GameMenuPanel.Screen.DEPLOYMENT;
        Gdx.app.log("CD-GAME-FLOW","DEPLOYMENT "+activeMission);
    }
    private void appendShipCommand(ShipCommandQueue.Command command){
        deployment=deployment.appendOrder(selectedDeploymentShip,command);
        deploymentNotice="Ship #"+(selectedDeploymentShip+1)+" has "+
                deployment.ordersFor(selectedDeploymentShip).size()+" sequential orders.";
    }
    private void launchCampaignMission(){
        if(campaign==null||!campaign.available(activeMission))return;
        if(screen!=GameMenuPanel.Screen.DEPLOYMENT&&screen!=GameMenuPanel.Screen.RESULTS)return;
        LevelEconomy.Level stage=LevelEconomy.level(activeMission);
        var node=CampaignMap.page(CampaignMap.pageOf(activeMission)).get((activeMission-1)%10);
        left=campaign.faction();right=node.opponent();
        if(leftDesign.faction!=left)leftDesign=Shipyard.starter(left,Shipyard.Hull.CORVETTE);
        // The opposing fleet is authored by the mission, never by the shipyard editor.
        rightDesign=Shipyard.starter(right,stage.index()<15?
                Shipyard.Hull.CORVETTE:stage.index()<75?Shipyard.Hull.FRIGATE:Shipyard.Hull.CRUISER);
        shipyard.sync(0,leftDesign);shipyard.sync(1,rightDesign);
        deployedAtLaunch=deployment.ships();
        battlefieldLevel=activeMission;
        playedCampaign=true;screen=GameMenuPanel.Screen.BATTLE;
        reset();
        Gdx.app.log("CD-GAME-FLOW","CAMPAIGN_BATTLE "+activeMission+" "+deployment.ships()+" "+deployment.formation());
    }
    private void finishEngagement(){
        lastWon=battle.alive(0)>0&&battle.alive(1)==0;
        survivorsAtEnd=battle.alive(0);
        lastReward=0;
        if(playedCampaign&&lastWon){
            long before=campaign.credits();
            campaign=campaign.complete(activeMission,deployedAtLaunch,survivorsAtEnd);
            lastReward=campaign.credits()-before;
            saveCampaign();
        }
        running=false;screen=GameMenuPanel.Screen.RESULTS;Gdx.app.log("CD-GAME-FLOW","AFTER_ACTION");
    }
    private void leaveResults(){
        if(playedCampaign){campaignPage=CampaignMap.pageOf(selectedMission);screen=GameMenuPanel.Screen.CAMPAIGN;}
        else screen=GameMenuPanel.Screen.SKIRMISH;
    }
    private void tapFrontEnd(float x,float y){
        if(screen==GameMenuPanel.Screen.HOME){
            switch(GameMenuPanel.homeHit(x,y)){
                case 0 -> showCampaign();
                case 1 -> openCampaignSetup();
                case 2 -> {screen=GameMenuPanel.Screen.SKIRMISH;Gdx.app.log("CD-GAME-FLOW","SKIRMISH_SETUP");}
                case 3 -> shipyard.show();
                case 4 -> screen=GameMenuPanel.Screen.OPTIONS;
                case 5 -> Gdx.app.exit();
            }
            return;
        }
        if(screen==GameMenuPanel.Screen.CAMPAIGN_SETUP){
            if(GameMenuPanel.hit(x,y,80,35,230,54)){
                screen=GameMenuPanel.Screen.HOME;return;
            }
            if(GameMenuPanel.hit(x,y,892,35,308,54)){
                newCampaign();return;
            }
            Battle.Faction selected=GameMenuPanel.factionHit(x,y);
            if(selected!=null)pendingFaction=selected;
            return;
        }
        if(screen==GameMenuPanel.Screen.SKIRMISH){
            if(GameMenuPanel.hit(x,y,30,35,160,49)){screen=GameMenuPanel.Screen.HOME;return;}
            if(GameMenuPanel.hit(x,y,702,35,225,49)){
                if(scenario!=ScenarioCatalog.Preset.PROVING_GROUND)shipyard.show();
                return;
            }
            if(GameMenuPanel.hit(x,y,969,35,277,49)){openSkirmishDeployment();return;}
            int selected=GameMenuPanel.skirmishHit(x,y);
            if(selected>=0){selectScenario(ScenarioCatalog.Preset.values()[selected]);return;}
            if(GameMenuPanel.hit(x,y,661,332,545,42)){
                if(scenario!=ScenarioCatalog.Preset.PROVING_GROUND)changeFaction(0);
                return;
            }
            if(GameMenuPanel.hit(x,y,661,271,545,42)){
                if(scenario!=ScenarioCatalog.Preset.PROVING_GROUND)changeFaction(1);
                return;
            }
            if(GameMenuPanel.hit(x,y,661,210,545,42)){
                if(scenario==ScenarioCatalog.Preset.CUSTOM)changeFleetSize(x<934?-1:1);
                return;
            }
            return;
        }
        if(screen==GameMenuPanel.Screen.CAMPAIGN){
            if(GameMenuPanel.hit(x,y,35,35,165,50)){screen=GameMenuPanel.Screen.HOME;return;}
            if(GameMenuPanel.hit(x,y,214,35,136,50)){campaignPage=Math.max(0,campaignPage-1);selectedMission=campaignPage*10+1;return;}
            if(GameMenuPanel.hit(x,y,366,35,136,50)){campaignPage=Math.min(CampaignMap.PAGE_COUNT-1,campaignPage+1);selectedMission=campaignPage*10+1;return;}
            if(GameMenuPanel.hit(x,y,964,35,278,50)){openDeployment();return;}
            int mission=GameMenuPanel.mapHit(x,y,campaignPage);
            if(mission>=1)selectedMission=mission;
            return;
        }
        if(screen==GameMenuPanel.Screen.DEPLOYMENT){
            switch(GameMenuPanel.deploymentAction(x,y)){
                case 0 -> screen=preparingCampaign?GameMenuPanel.Screen.CAMPAIGN:GameMenuPanel.Screen.SKIRMISH;
                case 1 -> {
                    if(preparingCampaign||scenario==ScenarioCatalog.Preset.CUSTOM)
                        deployment=deployment.withShips(-1,
                                preparingCampaign?LevelEconomy.level(activeMission).maximumFleet():200);
                    selectedDeploymentShip=Math.min(selectedDeploymentShip,deployment.ships()-1);
                }
                case 2 -> {
                    if(preparingCampaign||scenario==ScenarioCatalog.Preset.CUSTOM)
                        deployment=deployment.withShips(1,
                                preparingCampaign?LevelEconomy.level(activeMission).maximumFleet():200);
                }
                case 3 -> deployment=deployment.withFormation(-1);
                case 4 -> deployment=deployment.withFormation(1);
                case 5 -> shipyard.showCampaignFleet();
                case 6 -> {
                    if(preparingCampaign)launchCampaignMission();else launchSkirmish();
                }
                case 7 -> {
                    screen=GameMenuPanel.Screen.SHIP_ORDERS;
                    waitingForWaypoint=false;
                    deploymentNotice="Select a vessel and append as many orders as needed.";
                }
                default -> { }
            }
            return;
        }
        if(screen==GameMenuPanel.Screen.SHIP_ORDERS){
            switch(GameMenuPanel.ordersAction(x,y)){
                case 0 -> selectedDeploymentShip=Math.floorMod(selectedDeploymentShip-1,deployment.ships());
                case 1 -> selectedDeploymentShip=(selectedDeploymentShip+1)%deployment.ships();
                case 2 -> {
                    waitingForWaypoint=true;
                    deploymentNotice="Tap anywhere on the battlefield to append a MOVE waypoint.";
                }
                case 3 -> appendShipCommand(ShipCommandQueue.Command.hold(300));
                case 4 -> appendShipCommand(ShipCommandQueue.Command.engage(300));
                case 5 -> deployment=deployment.undoOrder(selectedDeploymentShip);
                case 6 -> deployment=deployment.clearOrders(selectedDeploymentShip);
                case 7 -> {
                    waitingForWaypoint=false;
                    screen=GameMenuPanel.Screen.DEPLOYMENT;
                }
                default -> { }
            }
            return;
        }
        if(screen==GameMenuPanel.Screen.OPTIONS){
            if(GameMenuPanel.hit(x,y,405,472,470,59)){audio.toggleMusic();return;}
            if(GameMenuPanel.hit(x,y,405,394,470,59)){audio.toggleEffects();return;}
            if(GameMenuPanel.hit(x,y,405,316,470,59)){audio.toggleTrack();return;}
            if(GameMenuPanel.hit(x,y,405,238,470,59)){
                if(updateCheck!=null)updateCheck.run();
                return;
            }
            if(GameMenuPanel.hit(x,y,405,160,470,59))screen=GameMenuPanel.Screen.HOME;
            return;
        }
        if(screen==GameMenuPanel.Screen.RESULTS){
            if(GameMenuPanel.hit(x,y,351,122,254,58)){
                if(playedCampaign)launchCampaignMission();else launchSkirmish();
            }else if(GameMenuPanel.hit(x,y,673,122,254,58))leaveResults();
        }
    }
    private void restoreCampaign(){
        for(FileHandle f:new FileHandle[]{Gdx.files.local(CAMPAIGN_FILE),Gdx.files.local(CAMPAIGN_FILE+".bak")}){
            if(!f.exists())continue;
            try{
                campaign=CampaignProgress.decode(f.readString("UTF-8"));
                return;
            }catch(RuntimeException damaged){
                Gdx.app.error("CAMPAIGN","Rejected invalid campaign progress: "+f.path(),damaged);
            }
        }
    }
    private void saveCampaign(){
        if(campaign==null)return;
        try{
            FileHandle primary=Gdx.files.local(CAMPAIGN_FILE);
            FileHandle backup=Gdx.files.local(CAMPAIGN_FILE+".bak");
            FileHandle temp=Gdx.files.local(CAMPAIGN_FILE+".tmp");
            temp.writeString(CampaignProgress.encode(campaign),false,"UTF-8");
            CampaignProgress.decode(temp.readString("UTF-8"));
            if(primary.exists()){
                try{
                    CampaignProgress.decode(primary.readString("UTF-8"));
                    primary.copyTo(backup);
                }catch(RuntimeException corrupted){
                    Gdx.app.log("CAMPAIGN","Preserving last good backup instead of corrupt primary");
                }
            }
            temp.moveTo(primary);
        }catch(RuntimeException error){
            Gdx.app.error("CAMPAIGN","Unable to save campaign progress",error);
        }
    }

    private void openTacticalMenu() {
        if (tacticalMenuOpen || shipyard.open()) return;
        resumeAfterMenu = running;
        running = false;
        tacticalMenuOpen = true;
        if (audio != null) audio.ui();
    }
    private void closeTacticalMenu() {
        if (!tacticalMenuOpen) return;
        tacticalMenuOpen = false;
        running = resumeAfterMenu;
    }
    private void applyFleetOrders(int side,BattleOrders.Orders orders){
        if(side<0||side>1||orders==null)throw new IllegalArgumentException("Invalid fleet orders");
        fleetOrders[side]=orders;
        if(battle!=null)battle.patchTeamOrders(side,orders);
        saveOrderPresets();
    }
    private void togglePd(int side){
        BattleOrders.Orders current=fleetOrders[side];
        applyFleetOrders(side,current.withPointDefense(current.pointDefense().next()));
    }
    private void openFleetOrders(){
        if(shipyard.open()||fleetOrdersPanel.open())return;
        resumeAfterOrders=tacticalMenuOpen?resumeAfterMenu:running;
        tacticalMenuOpen=false;running=false;
        fleetOrdersPanel.show();
    }
    private void closeFleetOrders(){
        if(!fleetOrdersPanel.open())return;
        fleetOrdersPanel.close();running=resumeAfterOrders;
    }
    private void tapTacticalMenu(float x, float y) {
        if(x<420||x>860){closeTacticalMenu();return;}
        final float top=550, step=40;
        for(int n=0;n<12;n++) {
            float y0=top-step*n;
            if(y<y0||y>=y0+36)continue;
            if (audio != null) audio.ui();
            switch(n){
                case 0 -> closeTacticalMenu();
                case 1 -> {reset();closeTacticalMenu();}
                case 2 -> {closeTacticalMenu();shipyard.show();}
                case 3 -> openFleetOrders();
                case 4 -> changeFleetSize(-1);
                case 5 -> changeFleetSize(1);
                case 6 -> togglePd(0);
                case 7 -> togglePd(1);
                case 8 -> audio.toggleMusic();
                case 9 -> audio.toggleEffects();
                case 10 -> audio.toggleTrack();
                case 11 -> {tacticalMenuOpen=false;running=false;screen=GameMenuPanel.Screen.HOME;}
            }
            return;
        }
    }
    private void drawTacticalMenu() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.008f,0.014f,0.034f,0.97f);
        shapes.rect(408,88,464,559);
        shapes.setColor(0.10f,0.20f,0.32f,1);
        shapes.rect(421,621,438,2);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch,"TACTICAL COMMAND",518,609);
        String[] labels={"RESUME ENGAGEMENT","RESTART ENGAGEMENT","OPEN SHIPYARD",
                "FLEET ORDERS EDITOR","REDUCE FLEET SIZE","INCREASE FLEET SIZE",
                "LEFT PD: "+fleetOrders[0].pointDefense().name().replace('_',' '),
                "RIGHT PD: "+fleetOrders[1].pointDefense().name().replace('_',' '),
                "MUSIC: "+(audio.musicEnabled()?"ON":"MUTED")+" (U)",
                "SOUND EFFECTS: "+(audio.effectsEnabled()?"ON":"MUTED")+" (I)",
                "SOUNDTRACK: "+(audio.battleTrack()?"FINAL SURGE":"COLD DRONE")+" (J)",
                "RETURN TO MAIN MENU"};
        // Semantic icons only, sourced from the approved 24-image selection.
        final String[] menuIcons={
                "speed","restart","shipyard_ship","fleet","back","next",
                "shield","shield",null,null,null,"back"
        };
        for(int n=0;n<labels.length;n++){
            float y=550-40*n;
            if(uiIcons!=null){
                uiIcons.drawButton(batch,n==1?"danger":n==3?"special":"primary",438,y,405,36);
                if(menuIcons[n]!=null)uiIcons.draw(batch,menuIcons[n],445,y+5,25);
            }
            font.setColor(Color.WHITE);
            font.draw(batch,labels[n],480,y+25);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"P/O: point defense  |  F: fleet orders  |  U/I/J: audio",439,99);
        batch.end();
    }

    /**
     * Actual environment selection. Every change restarts the current encounter
     * with the newly selected level's weather; backgrounds never remain preview-only.
     * Campaign waves/economy are separate future work.
     */
    private void changeBattlefieldLevel(int delta) {
        if(playedCampaign && screen==GameMenuPanel.Screen.BATTLE)return;
        int selected = Math.max(1,Math.min(500,battlefieldLevel+delta));
        if (selected == battlefieldLevel) return;
        battlefieldLevel = selected;
        if (starfield != null) {
            try { starfield.useLevel(selected); }
            catch (RuntimeException err) { Gdx.app.error("SKY","Cannot select level background "+selected,err); }
        }
        reset();
    }

    private void changeFleetSize(int delta) {
        if(playedCampaign && screen==GameMenuPanel.Screen.BATTLE)return;
        int selected = Math.max(0,Math.min(FLEET_SIZES.length-1,fleetIndex+delta));
        if(selected!=fleetIndex){fleetIndex=selected;reset();}
    }

    private void changeFaction(int team) {
        if(playedCampaign && screen==GameMenuPanel.Screen.BATTLE)return;
        if(team==0) {
            left=next(left);
            leftDesign=screen==GameMenuPanel.Screen.SKIRMISH && scenario!=ScenarioCatalog.Preset.CUSTOM?
                    ScenarioCatalog.defaultDesign(scenario,left,true,technology):
                    Shipyard.starter(left,leftDesign.hull);
            shipyard.sync(0,leftDesign);
        } else {
            right=next(right);
            rightDesign=screen==GameMenuPanel.Screen.SKIRMISH && scenario!=ScenarioCatalog.Preset.CUSTOM?
                    ScenarioCatalog.defaultDesign(scenario,right,false,technology):
                    Shipyard.starter(right,rightDesign.hull);
            shipyard.sync(1,rightDesign);
        }
        saveFleets();
        reset();
    }

    /** Local storage is private to this app on Android and the user's home directory on desktop. */
    private void restoreFleets() {
        FileHandle primary=Gdx.files.local(FLEET_FILE);
        FileHandle backup=Gdx.files.local(FLEET_FILE+".bak");
        if(!primary.exists()&&!backup.exists())return;
        for(FileHandle file:new FileHandle[]{primary,backup}) {
            if(!file.exists())continue;
            try {
                FleetPresets.Fleets saved=FleetPresets.decode(file.readString("UTF-8"),technology);
                leftDesign=saved.left();rightDesign=saved.right();
                left=leftDesign.faction;right=rightDesign.faction;
                saveState=file==primary?"FLEETS RESTORED":"BACKUP FLEETS RESTORED";
                return;
            }catch(RuntimeException damaged){
                Gdx.app.error("SAVE", "Fleet preset failed checksum/fitting validation: "+file.path(), damaged);
            }
        }
        saveState="SAVES INVALID - STARTER FLEETS USED";
    }
    private void restoreOrderPresets(){
        for(FileHandle candidate:new FileHandle[]{
                Gdx.files.local(ORDERS_FILE),Gdx.files.local(ORDERS_FILE+".bak")}){
            if(!candidate.exists())continue;
            try{
                BattleOrdersCodec.Pair result=BattleOrdersCodec.decode(candidate.readString("UTF-8"));
                fleetOrders[0]=result.left();fleetOrders[1]=result.right();
                return;
            }catch(RuntimeException broken){
                Gdx.app.error("ORDERS","Rejected invalid fleet orders "+candidate.path(),broken);
            }
        }
    }
    private void saveOrderPresets(){
        try{
            String payload=BattleOrdersCodec.encode(fleetOrders[0],fleetOrders[1]);
            FileHandle output=Gdx.files.local(ORDERS_FILE);
            FileHandle temp=Gdx.files.local(ORDERS_FILE+".tmp");
            FileHandle backup=Gdx.files.local(ORDERS_FILE+".bak");
            temp.writeString(payload,false,"UTF-8");
            BattleOrdersCodec.decode(temp.readString("UTF-8"));
            if(output.exists()){
                try {
                    BattleOrdersCodec.decode(output.readString("UTF-8"));
                    output.copyTo(backup);
                }catch(IllegalArgumentException invalid){
                    Gdx.app.log("ORDERS","Retained previous valid backup");
                }
            }
            temp.moveTo(output);
        }catch(RuntimeException failed){
            Gdx.app.error("ORDERS","Unable to persist tactical settings",failed);
        }
    }
    private void saveFleets() {
        try {
            String data=FleetPresets.encode(leftDesign,rightDesign,technology);
            FileHandle destination=Gdx.files.local(FLEET_FILE);
            FileHandle temporary=Gdx.files.local(FLEET_FILE+".tmp");
            FileHandle backup=Gdx.files.local(FLEET_FILE+".bak");
            temporary.writeString(data,false,"UTF-8");
            FleetPresets.decode(temporary.readString("UTF-8"),technology);
            if(destination.exists()) {
                try {
                    FleetPresets.decode(destination.readString("UTF-8"),technology);
                    destination.copyTo(backup);
                } catch(IllegalArgumentException damaged) {
                    Gdx.app.log("SAVE", "Refusing to overwrite a valid backup with an invalid primary save");
                }
            }
            temporary.moveTo(destination);
            saveState="FLEETS SAVED";
        } catch(RuntimeException failed) {
            Gdx.app.error("SAVE", "Unable to persist fitted fleets", failed);
            saveState="FLEET SAVE FAILED";
        }
    }

    private static Battle.Faction next(Battle.Faction f) {
        Battle.Faction[] values = Battle.Faction.values();
        return values[(f.ordinal() + 1) % values.length];
    }
    private void reset() {
        // Menus and the shipyard must not instantiate a combat sandbox on startup.
        if(!verifyDemo&&screen!=GameMenuPanel.Screen.BATTLE)return;
        LevelEnvironment.Stage environment = LevelEnvironment.forLevel(battlefieldLevel);
        if(verifyDemo)battle=DemonstrationBattle.create(technology);
        else if(screen==GameMenuPanel.Screen.BATTLE && playedCampaign) {
            campaignEncounter=CampaignEncounter.start(activeMission,deployment,
                    leftDesign,rightDesign,technology);
            battle=campaignEncounter.battle();
        }
        else if(screen==GameMenuPanel.Screen.BATTLE) {
            battle=ScenarioCatalog.create(scenario,leftDesign,rightDesign,technology,
                    preparedSkirmish?deployment.ships():FLEET_SIZES[fleetIndex]);
            if(preparedSkirmish && !scenario.demonstration)deployment.apply(battle);
        }
        else battle=Battle.duel(nextSeed++,FLEET_SIZES[fleetIndex],leftDesign,rightDesign,technology);
        for(int side=0;side<2;side++)battle.setTeamOrders(side,fleetOrders[side]);
        if (weatherEnabled || playedCampaign) battle.configureWeather(environment.weather());
        // The same validated stage index drives both GPU background and simulation.
        if (starfield != null) starfield.useLevel(environment.level());
        accumulator = 0;
        if (audio != null) audio.resetForBattle();
        running = true;
        if(verifyDemo){
            verifiedSequence=0;verifiedFrames=verifiedShots=verifiedImpacts=peakAnimations=peakCraft=peakOrdnance=0;
            verificationReported=false;verifiedTypes.clear();verifiedShotIds.clear();
            Gdx.app.log("CD-DEMO-VERIFY","START 200v200 module/weapon/service suite seed="+DemonstrationBattle.SEED);
        }
    }

    @Override public void render() {
        if(!verifyDemo&&screen==GameMenuPanel.Screen.BATTLE&&battle.finished()) {
            if(playedCampaign && campaignEncounter!=null) {
                CampaignEncounter.Advance advance=campaignEncounter.advance();
                if(advance==CampaignEncounter.Advance.VICTORY ||
                        advance==CampaignEncounter.Advance.DEFEAT)finishEngagement();
            }else finishEngagement();
        }
        if(!verifyDemo&&screen!=GameMenuPanel.Screen.BATTLE){
            Gdx.gl.glClearColor(.018f,.029f,.064f,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
            viewport.apply();camera.update();
            shapes.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);
            if(shipyard.open())shipyard.draw(shapes,batch,font);
            else menuPanel.draw(screen,shapes,batch,font,scenario,left,right,pendingFaction,
                    FLEET_SIZES[fleetIndex],screen==GameMenuPanel.Screen.RESULTS && !playedCampaign?null:campaign,campaignPage,
                    screen==GameMenuPanel.Screen.DEPLOYMENT?activeMission:selectedMission,
                    deployment,leftDesign.hull.name(),selectedDeploymentShip,
                    preparingCampaign,waitingForWaypoint,deploymentNotice,
                    audio.musicEnabled(),audio.effectsEnabled(),audio.battleTrack(),updateCheck!=null,lastWon,lastReward,
                    survivorsAtEnd,deployedAtLaunch);
            return;
        }
        float delta = Math.min(0.08f, Gdx.graphics.getDeltaTime());
        if (running && !tacticalMenuOpen && !shipyard.open() && !battle.finished()) {
            accumulator += delta * speed;
            int turns = 0;
            while (accumulator >= Battle.DT && turns++ < 64) {
                battle.step(); accumulator -= Battle.DT;
            }
            if (turns >= 64) accumulator = 0; // Frame cannot create unbounded catch-up latency.
        }
        updateBattleCamera();
        if (audio != null) audio.advance(battle,battleCamera.position.x,
                battleCamera.position.y,battleCamera.zoom,
                running && !tacticalMenuOpen && !shipyard.open()
                && (fleetOrdersPanel==null || !fleetOrdersPanel.open())
                );
        Gdx.gl.glClearColor(0.015f, 0.025f, 0.055f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        viewport.apply(); camera.update();
        if(fleetOrdersPanel!=null&&fleetOrdersPanel.open()){
            shapes.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);
            fleetOrdersPanel.draw(shapes,batch,font);
            return;
        }
        if (shipyard.open()) {
            shapes.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);
            shipyard.draw(shapes,batch,font);
            return;
        }
        // World-space sky & hazard layers render before tactical grid, weapons and hulls.
        if (starfield != null) starfield.render(batch,camera,battleCamera,W,H);
        if (weatherVfx != null) weatherVfx.behind(shapes,batch,battleCamera,battle.weather());
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.11f, 0.16f, 0.26f, 0.75f);
        for (int x = 40; x <= 1240; x += 40) shapes.line(x, 25, x, 645);
        for (int y = 25; y <= 645; y += 40) shapes.line(40, y, 1240, y);
        for (Battle.Beam beam : battle.beams()) {
            if(weaponBeams!=null && weaponBeams.canDraw(beam,battle.tick))continue;
            shapes.setColor(switch(beam.type) {
                case KINETIC -> Color.GOLD;
                case PLASMA -> Color.ORANGE;
                case PARTICLE -> Color.VIOLET;
                default -> beam.team==0?Color.CYAN:Color.CORAL;
            });
            shapes.line((float) beam.x1, (float) beam.y1, (float) beam.x2, (float) beam.y2);
        }
        // Preserve visibility of genuine service links when an approved
        // atlas strip is absent (no fabricated replacement sprite).
        for(Battle.SupportLink link:battle.supportLinks()){
            if(weaponBeams!=null&&weaponBeams.canDrawSupport(link,battle.tick))continue;
            shapes.setColor(link.kind==Battle.SupportKind.SHIELD?Color.SKY:Color.GREEN);
            shapes.line((float)link.x1,(float)link.y1,(float)link.x2,(float)link.y2);
        }
        // Engine exhaust is displayed by both GPU-instanced and fallback hull paths.
        for (Battle.Ship ship : battle.ships()) if(ship.alive()) {
            double scale=ship.visualScale(),dx=StrictMath.cos(ship.facing),dy=StrictMath.sin(ship.facing);
            shapes.setColor(ship.team==0?Color.SKY:Color.ORANGE);
            shapes.line((float)(ship.x-dx*10*scale),(float)(ship.y-dy*10*scale),
                    (float)(ship.x-dx*23*scale),(float)(ship.y-dy*23*scale));
        }
        shapes.end();
        if (instanced == null) drawShipsFallback();
        else {
            try {
                instanced.render(battle,battleCamera,ship -> terranSprites!=null && terranSprites.hasSprite(ship));
                int expected=0;
                for(Battle.Ship ship:battle.ships())
                    if(ship.alive() && (terranSprites==null || !terranSprites.hasSprite(ship)))expected++;
                if(instanced.instances!=expected)
                    throw new IllegalStateException("GPU instance count disagrees with non-sprite ships");
            }
            catch (RuntimeException error) {
                Gdx.app.error("GPU", "Switching to per-ship rendering", error);
                instanced.close(); instanced = null; renderer = "software geometry";
                drawShipsFallback();
            }
        }
        // The original GPU/geometry path remains the non-Terran and missing-asset fallback.
        batch.setProjectionMatrix(battleCamera.combined);
        if(terranSprites!=null && terranSprites.loaded()>0)terranSprites.render(battle,batch);
        if(weaponFx!=null)weaponFx.render(battle,batch,weaponBeams);
        drawArmorDamage();
        if (weatherVfx != null) weatherVfx.above(shapes,batch,battleCamera,battle.weather());
        // Query sprite availability before opening ShapeRenderer's GL batch.
        java.util.Set<Long> texturedMissiles=new java.util.HashSet<>();
        java.util.Set<Long> texturedBallistics=new java.util.HashSet<>();
        if(weaponFx!=null){
            for(Battle.Missile missile:battle.missiles())
                if(weaponFx.hasMissilePayload(missile,battle.tick))
                    texturedMissiles.add(missile.visualId);
            for(Battle.Ballistic shell:battle.ballistics())
                if(weaponFx.hasBallisticPayload(shell,battle.tick))
                    texturedBallistics.add(shell.visualId);
        }
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Missile m : battle.missiles()) {
            if(texturedMissiles.contains(m.visualId))continue;
            shapes.setColor(m.torpedo?Color.ORANGE:m.team==0?Color.CYAN:Color.SCARLET);
            shapes.circle((float) m.x, (float) m.y, m.torpedo?4.4f:2.8f, 7);
        }
        for (Battle.Ballistic shell:battle.ballistics()) {
            if(texturedBallistics.contains(shell.visualId))continue;
            shapes.setColor(Color.GOLD);
            shapes.circle((float)shell.x,(float)shell.y,2.1f,6);
        }
        for (Battle.Craft craft : battle.crafts()) {
            float x=(float)craft.x,y=(float)craft.y;
            shapes.setColor(craft.repair?Color.GREEN:craft.team==0?Color.CYAN:Color.CORAL);
            if(!craft.fighter){
                shapes.circle(x,y,craft.repair?3.0f:2.4f,8);
                continue;
            }
            // Weapon-class silhouettes are functional art fallbacks until
            // each faction receives individually approved strike-craft sprites.
            switch(craft.profile.hull()) {
                case INTERCEPTOR -> shapes.triangle(x+5,y,x-4,y+2,x-4,y-2);
                case ASSAULT_GUNSHIP -> shapes.rect(x-4,y-3,8,6);
                case LASER_FIGHTER -> {
                    shapes.triangle(x+5,y,x-3,y+4,x-3,y-4);
                    shapes.circle(x,y,1.3f,6);
                }
                case PARTICLE_FIGHTER -> {
                    shapes.triangle(x+5,y,x-4,y+4,x-4,y-4);
                    shapes.rect(x-1,y-1,2,2);
                }
                case MISSILE_FIGHTER -> {
                    shapes.rect(x-4,y-2,8,4);
                    shapes.circle(x-3,y+3,1.5f,6);
                    shapes.circle(x-3,y-3,1.5f,6);
                }
                case TORPEDO_BOMBER -> {
                    shapes.rect(x-6,y-4,12,8);
                    shapes.circle(x+3,y,2.3f,7);
                }
            }
        }
        shapes.end();
        // HUD is screen-anchored; do not draw fixed bars in the moving battle camera.
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.13f, 0.22f, 0.94f);
        shapes.rect(0, 649, W, 71);
        shapes.setColor(0.035f, 0.085f, 0.15f, 0.93f);
        shapes.rect(0, 613, W, 36);
        shapes.end();
        batch.setProjectionMatrix(camera.combined); batch.begin();
        if (uiIcons != null) {
            uiIcons.drawButton(batch,running?"primary":"special",4,663,121,38);
            uiIcons.drawButton(batch,"secondary",132,663,117,38);
            uiIcons.drawButton(batch,"secondary",262,663,106,38);
            uiIcons.drawButton(batch,"secondary",374,663,104,38);
            uiIcons.drawButton(batch,"special",1158,663,120,38);
            uiIcons.drawButton(batch,"primary",1138,78,132,41);
            if(updateCheck!=null)uiIcons.drawButton(batch,"special",1010,78,124,41);
            uiIcons.draw(batch,"restart",139,669,23);
            uiIcons.draw(batch,"speed",378,669,23);
            uiIcons.draw(batch,"shipyard_ship",1165,669,23);
            uiIcons.draw(batch,"information",1145,86,23);
        }
        font.setColor(Color.WHITE);
        font.draw(batch, running ? "PAUSE" : "RESUME", 15, 693);
        font.draw(batch, "RESTART", 167, 693);
        font.draw(batch, "SLOW", 270, 693);
        font.draw(batch, "FAST x" + speed, 405, 693);
        font.setColor(left.r, left.g, left.b, 1);
        font.draw(batch, (playedCampaign?"":"< ") + left.displayName, 505, 693);
        font.setColor(right.r, right.g, right.b, 1);
        font.draw(batch, (playedCampaign?"":"< ") + right.displayName, 751, 693);
        font.setColor(Color.GOLD);
        if(playedCampaign)font.draw(batch,"FLEET "+deployedAtLaunch,1003,693);
        else {
            font.draw(batch,"-",1000,693);
            font.draw(batch,""+FLEET_SIZES[fleetIndex]+"v"+FLEET_SIZES[fleetIndex],1022,693);
            font.draw(batch,"+",1112,693);
        }
        font.draw(batch,"SHIPYARD",1195,693);
        font.setColor(Color.WHITE);
        font.draw(batch,"MENU (M)",1174,107);
        if(updateCheck!=null){
            font.setColor(Color.CYAN);
            font.draw(batch,"UPDATES (K)",1015,107);
            font.setColor(Color.WHITE);
        }
        font.setColor(Color.CYAN);
        font.draw(batch,"ZOOM +",24,106);
        font.draw(batch,"ZOOM -",133,106);
        font.draw(batch,"AUTO",242,106);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,String.format(java.util.Locale.ROOT,"%.2fx  %s",battleCamera.zoom,
                focusedShipId>=0?"FOLLOW #"+focusedShipId:"FLEET VIEW"),338,106);
        font.setColor(Color.GOLD);
        SpaceWeather.Preset activeWeather = battle.weather().preset();
        String condition = activeWeather.enabled()
                ? activeWeather.primary().name().replace('_',' ') : "CLEAR";
        if (activeWeather.secondary() != SpaceWeather.Kind.CLEAR)
            condition += " + " + activeWeather.secondary().name().replace('_',' ');
        font.draw(batch,playedCampaign?"MISSION CONDITIONS   "+condition:
                "ENV " + (weatherEnabled ? "ON" : "OFF") + "   " + condition +
                "   (W / TAP TO TOGGLE)",22,638);
        font.setColor(Color.CYAN);
        if(playedCampaign && campaignEncounter!=null)
            font.draw(batch,"MISSION "+activeMission+" WAVE "+campaignEncounter.wave()+"/"+campaignEncounter.waves(),948,638);
        else {
            font.draw(batch,"<",945,638);
            font.draw(batch,"LEVEL " + battlefieldLevel + "/500",998,638);
            font.draw(batch,">",1235,638);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "FLEETS " + battle.alive(0) + " / " + battle.alive(1)
                + "   T+" + (battle.tick / 30) + "s   HITS " + battle.hitCount
                + "   PD " + (battle.missileIntercepts+battle.ballisticIntercepts)
                + "   CRAFT " + battle.crafts().size()
                + "   " + renderer + "   " + saveState, 22, 38);
        if (battle.finished()) {
            font.setColor(Color.GOLD);
            font.draw(batch, "ENGAGEMENT COMPLETE  —  " + (battle.alive(0) > 0 ? left.displayName : right.displayName)
                    + " HOLDS THE FIELD", 330, 617);
        }
        batch.end();
        if (tacticalMenuOpen) drawTacticalMenu();
        if(verifyDemo)verifyRenderedDemoFrame();
    }

    private void verifyRenderedDemoFrame(){
        if(verificationReported)return;
        verifiedFrames++;
        if(weaponFx!=null)peakAnimations=Math.max(peakAnimations,weaponFx.activeAnimations());
        peakCraft=Math.max(peakCraft,battle.crafts().size());
        peakOrdnance=Math.max(peakOrdnance,battle.missiles().size()+battle.ballistics().size());
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.sequence()<=verifiedSequence)continue;
            verifiedSequence=event.sequence();
            if(event.phase()==Battle.VisualPhase.FIRE){
                verifiedShots++;verifiedShotIds.add(event.shotId());
                try{verifiedTypes.add(Battle.DamageType.valueOf(event.weapon()));}
                catch(IllegalArgumentException auxiliary){/* Distinct PD/fighter firing */ }
            }
            if(event.phase()==Battle.VisualPhase.IMPACT&&verifiedShotIds.contains(event.shotId()))
                verifiedImpacts++;
        }
        if(battle.tick<480&&!battle.finished())return;
        double self=0,remote=0,shield=0;
        int serviceDrones=0;
        for(Battle.Ship ship:battle.ships()){
            self+=ship.hullRepaired;remote+=ship.remoteHullRepaired;
            shield+=ship.shieldsTransferred;serviceDrones+=ship.repairDronesLaunched;
        }
        StringBuilder failure=new StringBuilder();
        if(battle.ships().size()!=400)failure.append("ship count; ");
        for(Battle.DamageType kind:new Battle.DamageType[]{
                Battle.DamageType.LASER,Battle.DamageType.KINETIC,Battle.DamageType.PLASMA,
                Battle.DamageType.PARTICLE,Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO})
            if(!verifiedTypes.contains(kind))failure.append("missing ").append(kind).append("; ");
        if(verifiedFrames<6||verifiedShots==0||verifiedImpacts==0||battle.hitCount==0)
            failure.append("unverified rendered combat; ");
        if(peakOrdnance==0||peakCraft==0)failure.append("no ordnance/craft; ");
        if(self<=0||remote<=0||shield<=0||serviceDrones==0)failure.append("inactive support; ");
        if(peakAnimations==0)failure.append("VFX compositor unused; ");
        verificationReported=true;
        String facts=" tick="+battle.tick+" frames="+verifiedFrames+
            " shots="+verifiedShots+" hits="+battle.hitCount+
            " impacts="+verifiedImpacts+" weapons="+verifiedTypes+
            " ordnance="+peakOrdnance+" craft="+peakCraft+
            " self="+self+" remote="+remote+" shield="+shield+
            " repairDrones="+serviceDrones+
            " pdInterceptions="+(battle.missileIntercepts+battle.ballisticIntercepts)+
            " VFX="+peakAnimations+" textureFrames="+
            (weaponFx==null?0:weaponFx.loadedTextures())+
            " beamFrames="+(weaponBeams==null?0:weaponBeams.loaded());
        if(failure.length()>0){
            Gdx.app.error("CD-DEMO-VERIFY","FAIL 200v200 "+failure+facts);
            throw new IllegalStateException("CD 200v200 in-game validation failed: "+failure);
        }
        Gdx.app.log("CD-DEMO-VERIFY","PASS 200v200 mechanics+render-loop"+facts);
        if(weaponFx==null||weaponFx.loadedTextures()==0||
                weaponBeams==null||weaponBeams.loaded()==0)
            Gdx.app.log("CD-DEMO-VERIFY","ART_PENDING: real atlas PNGs not packaged; fallback is not quality approval");
    }

    /** World camera follows the active engagement; UI coordinates never move. */
    private void updateBattleCamera() {
        BattleView.Frame shot=BattleView.of(battle);
        battleCamera.zoom=Math.max(0.16f,Math.min(8f,(float)shot.zoom()*manualZoom));
        double cx=shot.centerX(),cy=shot.centerY();
        if(focusedShipId>=0){
            Battle.Ship followed=null;
            for(Battle.Ship ship:battle.ships())if(ship.alive()&&ship.id==focusedShipId){
                followed=ship;break;
            }
            if(followed==null)focusedShipId=-1;
            else {cx=followed.x;cy=followed.y;}
        }
        battleCamera.position.set((float)cx,(float)cy,0);
        battleCamera.update();
    }

    /**
     * Temporary ship-local damage indicators until an exterior alpha mask and
     * structural sublayer shader are introduced for the final sprite pipeline.
     */
    private void drawArmorDamage() {
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(Battle.Ship ship:battle.ships())if(ship.alive()) {
            double length=23*ship.visualScale(), breadth=13*ship.visualScale();
            double c=StrictMath.cos(ship.facing),s=StrictMath.sin(ship.facing);
            for(int row=0;row<diplomacy.sim.DirectionalDefense.GRID;row++)
                for(int col=0;col<diplomacy.sim.DirectionalDefense.GRID;col++) {
                    var tile=ship.defense.tile(row,col);
                    if(!tile.occupied || tile.exposure()<0.45)continue;
                    double x=(1-(row+0.5)*2/diplomacy.sim.DirectionalDefense.GRID)*length;
                    double y=(1-(col+0.5)*2/diplomacy.sim.DirectionalDefense.GRID)*breadth;
                    float wx=(float)(ship.x+x*c-y*s),wy=(float)(ship.y+x*s+y*c);
                    float radius=(float)(0.6+tile.exposure()*0.8);
                    shapes.setColor(tile.internalDamage()>0.65?com.badlogic.gdx.graphics.Color.SCARLET:
                            com.badlogic.gdx.graphics.Color.ORANGE);
                    shapes.circle(wx,wy,radius,5);
                }
        }
        shapes.end();
    }

    private void drawShipsFallback() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Ship s : battle.ships()) if (s.alive()
                && (terranSprites==null||!terranSprites.hasSprite(s))) {
            shapes.setColor(s.faction.r, s.faction.g, s.faction.b, 1);
            float x = (float) s.x, y = (float) s.y;
            float scale=(float)s.visualScale();
            // Keep Stage 2's distinct faction/class art in the GL2 fallback path.
            float angle=(float)(s.facing-Math.PI/2);
            float c=(float)Math.cos(angle), d=(float)Math.sin(angle);
            float[] triangles=HullSilhouette.of(s.faction,s.hullClass);
            for(int i=0;i<triangles.length;i+=6) {
                float ax=x+scale*(triangles[i]*c-triangles[i+1]*d);
                float ay=y+scale*(triangles[i]*d+triangles[i+1]*c);
                float bx=x+scale*(triangles[i+2]*c-triangles[i+3]*d);
                float by=y+scale*(triangles[i+2]*d+triangles[i+3]*c);
                float cx=x+scale*(triangles[i+4]*c-triangles[i+5]*d);
                float cy=y+scale*(triangles[i+4]*d+triangles[i+5]*c);
                shapes.triangle(ax,ay,bx,by,cx,cy);
            }
        }
        shapes.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void pause() {
        if (audio != null) audio.pause();
    }
    @Override public void resume() {
        if (audio != null) audio.resume();
    }
    @Override public void dispose() {
        if (audio != null) audio.close();
        if (instanced != null) instanced.close();
        if(terranSprites!=null)terranSprites.dispose();
        if(weaponBeams!=null)weaponBeams.dispose();
        if(weaponFx!=null)weaponFx.dispose();
        if(uiIcons!=null)uiIcons.dispose();
        if(starfield!=null)starfield.dispose();
        if(weatherVfx!=null)weatherVfx.dispose();
        shapes.dispose(); batch.dispose(); font.dispose();
    }
}
