package diplomacy.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignAfterAction;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignLedger;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.sim.LevelEconomy;
import diplomacy.tech.TechnologyCatalog;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Scratch-only capture of the actual production controller and framebuffer.
 * Uses a deterministic four-vessel campaign fixture and an actual filesystem
 * write fault. No surrogate UI, application mocks or modified render code.
 * The controlled finished-battle fixture is not a naturally played match.
 */
public final class RecoveryVisualCapture extends ApplicationAdapter {
    private static final String LEDGER="campaign/authoritative-ledger-v1.dat";
    private final CatastrophicGame game=new CatastrophicGame();
    private final Path output;
    private final String size;
    private final boolean proofOnly;
    private int frames,stage;
    private long beforeCredits,savedCredits;
    private TechnologyCatalog technology;
    private Path ledger,writeFault;

    private RecoveryVisualCapture(Path output,String size,boolean proofOnly){
        this.output=output;this.size=size;this.proofOnly=proofOnly;
    }
    private Object get(String name)throws Exception{
        Field f=CatastrophicGame.class.getDeclaredField(name);f.setAccessible(true);
        return f.get(game);
    }
    private void set(String name,Object value)throws Exception{
        Field f=CatastrophicGame.class.getDeclaredField(name);f.setAccessible(true);
        f.set(game,value);
    }
    private Object call(String name)throws Exception{
        Method m=CatastrophicGame.class.getDeclaredMethod(name);m.setAccessible(true);
        try{return m.invoke(game);}
        catch(InvocationTargetException failure){
            throw new IllegalStateException("Controller action failed: "+name,failure.getCause());
        }
    }
    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    public void create(){
        try{
            System.out.println("GL_PROOF version="+Gdx.gl.glGetString(GL20.GL_VERSION)+
                    " renderer="+Gdx.gl.glGetString(GL20.GL_RENDERER));
            game.create();
            Gdx.input.setCursorPosition(Gdx.graphics.getWidth()-6,6);
            if(!proofOnly)preparePendingSettlement();
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    @SuppressWarnings("unchecked")
    private void preparePendingSettlement()throws Exception{
        // Require the repaired methods before collecting any repair screenshots.
        CatastrophicGame.class.getDeclaredMethod("resultsPrimaryAction");
        CatastrophicGame.class.getDeclaredMethod("campaignSettlementPending");
        technology=(TechnologyCatalog)get("technology");
        CampaignProgress.State progress=CampaignProgress.start(Battle.Faction.TERRAN)
                .complete(1,1,1);
        require(progress.available(31),"Mission31 fixture must be legitimately available");
        var fitting=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        CampaignFleet.Roster roster=CampaignFleet.startSolo(Battle.Faction.TERRAN,
                fitting,technology,1234567L);
        for(int i=1;i<4;i++){
            roster=roster.purchase(fitting,technology);
            roster=roster.setActive(roster.ships().get(i).id(),true,LevelEconomy.fleetCap(31));
        }
        require(roster.active().size()==4,"Four real commissioned report rows required");
        var discovery=new CampaignTechnology.State(Battle.Faction.TERRAN);
        ledger=Gdx.files.local(LEDGER).file().toPath();
        CampaignLedger.commit(ledger,new CampaignLedger.Snapshot(1,progress,roster,discovery),technology);
        beforeCredits=progress.credits();
        set("campaign",progress);set("campaignFleet",roster);set("discoveries",discovery);
        set("ledgerGeneration",1L);set("ledgerAuthorityPresent",true);
        set("playedCampaign",true);set("activeMission",31);set("selectedMission",31);
        set("deployedAtLaunch",4);
        Battle battle=Battle.duel(771L,4,fitting,
                Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE),technology);
        List<Long> sortie=(List<Long>)get("campaignSortie");sortie.clear();
        int i=0;
        for(Battle.Ship ship:battle.ships()){
            if(ship.team!=0){ship.hull=0;continue;}
            CampaignFleet.Vessel vessel=roster.active().get(i);
            ship.commissionedId=vessel.id();ship.name=vessel.name();
            sortie.add(vessel.id());
            // Controlled after-action states exercise real loss/stores reporting.
            ship.fuel=Math.max(0,ship.fuel-(i+1)*10);
            ship.kineticAmmo=Math.max(0,ship.kineticAmmo-(i+1)*2);
            if(i==1)ship.hull*=.8;
            if(i==2)ship.hull*=.65;
            if(i==3)ship.hull=0;
            i++;
        }
        require(i==4,"Real battle must contain four commissioned friendlies");
        set("battle",battle);set("screen",GameMenuPanel.Screen.BATTLE);
        writeFault=ledger.resolveSibling(ledger.getFileName()+".tmp");
        Files.createDirectory(writeFault);
        call("finishEngagement");
        require((Boolean)call("campaignSettlementPending"),"Write fault must leave outcome pending");
        require(get("screen")==GameMenuPanel.Screen.RESULTS,"Pending outcome stays on Results");
        require(CampaignLedger.load(ledger,technology).orElseThrow().generation()==1,
                "Pending outcome must preserve previous committed generation");
        var report=(CampaignAfterAction.Report)get("campaignAfterAction");
        require(report.entries().size()==4,"Capture must show four actual report rows");
        System.out.println("STATE pending: generation=1 reportRows="+report.entries().size()+
                " credits="+((CampaignProgress.State)get("campaign")).credits()+
                " notice="+get("settlementNotice"));
    }
    public void resize(int width,int height){game.resize(width,height);}
    public void render(){
        try{
            game.render();
            if(++frames<5)return;
            frames=0;
            if(proofOnly){capture("gl-game-proof");Gdx.app.exit();return;}
            if(stage==0){
                capture("settlement-pending");
                Files.delete(writeFault);
                // The actual registered keyboard handler routes R through
                // resultsKey -> resultsPrimaryAction -> retryCampaignSettlement.
                require(Gdx.input.getInputProcessor().keyDown(Input.Keys.R),"Real retry key handled");
                require(!(Boolean)call("campaignSettlementPending"),"Real retry must commit");
                require(get("screen")==GameMenuPanel.Screen.RESULTS,"Saved report remains visible");
                var saved=CampaignLedger.load(ledger,technology).orElseThrow();
                savedCredits=saved.progress().credits();
                require(saved.generation()==2&&savedCredits>beforeCredits,
                        "Retry must persist one reward generation");
                System.out.println("STATE saved: generation="+saved.generation()+
                        " credits="+savedCredits+" owned="+saved.fleet().ships().size()+
                        " notice="+get("settlementNotice"));
            }else if(stage==1){
                capture("settlement-retry-saved");
                // Corruption is confined to this capture's isolated working directory.
                Files.writeString(ledger,"corrupt-primary-fixture");
                Files.writeString(ledger.resolveSibling(ledger.getFileName()+".bak"),"corrupt-backup-fixture");
                call("restoreCampaign");call("openCampaignSetup");
                require(Gdx.input.getInputProcessor().keyDown(Input.Keys.ENTER),"Real begin key handled");
                require((Boolean)get("campaignResetArmed"),"Reset must request confirmation");
                require(get("screen")==GameMenuPanel.Screen.CAMPAIGN_SETUP,"Confirmation stays in setup");
                require(Files.readString(ledger).equals("corrupt-primary-fixture"),
                        "First begin must not replace corrupt authorities");
                System.out.println("STATE reset-confirmation: armed=true filesPreserved=true notice="+
                        get("setupNotice"));
            }else{
                capture("corrupt-save-confirmation");
                require(Gdx.input.getInputProcessor().keyDown(Input.Keys.ESCAPE),"Real cancel key handled");
                require(get("screen")==GameMenuPanel.Screen.HOME&&
                        Files.readString(ledger).equals("corrupt-primary-fixture"),
                        "Cancel must preserve existing files and return home");
                System.out.println("PASS actual recovery framebuffer sequence "+size+
                        ": pending -> retry saved -> corrupted-save confirmation -> cancel");
                Gdx.app.exit();
            }
            stage++;
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private void capture(String name){
        Pixmap frame=ScreenUtils.getFrameBufferPixmap(0,0,
                Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try{PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name+"-"+size+".png").toString()),frame,-1,true);}
        finally{frame.dispose();}
        System.out.println("CAPTURED "+name+" "+size+" actual game framebuffer");
    }
    public void dispose(){game.dispose();}
    public static void main(String[] args){
        int width=Integer.parseInt(args[1]),height=Integer.parseInt(args[2]);
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setTitle("Catastrophic Diplomacy — Actual game recovery capture");
        config.setWindowedMode(width,height);config.setResizable(false);config.useVsync(false);
        config.setForegroundFPS(30);config.disableAudio(true);
        config.setPreferencesConfig("capture-preferences",com.badlogic.gdx.Files.FileType.Local);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new RecoveryVisualCapture(Path.of(args[0]),width+"x"+height,
                args.length>3&&args[3].equals("proof")),config);
    }
}
