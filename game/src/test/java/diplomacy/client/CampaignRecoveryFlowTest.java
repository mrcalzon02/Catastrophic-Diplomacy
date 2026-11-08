package diplomacy.client;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignDeployment;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignLedger;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.sim.LevelEconomy;
import diplomacy.tech.TechnologyCatalog;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real campaign controller actions against temporary local storage. These
 * tests exercise dispatch and the presentation values used by Results/setup;
 * they do not create a GL context or claim rendered/device acceptance.
 */
public final class CampaignRecoveryFlowTest {
    private static final String LEDGER="campaign/authoritative-ledger-v1.dat";
    private static final Battle.Faction FACTION=Battle.Faction.TERRAN;
    private static final String LASER_I="TECH-TER-LAS-OUTPUT-01";
    private static final String LASER_II="TECH-TER-LAS-OUTPUT-02";
    private static TechnologyCatalog catalog;

    private enum Outcome { WIN, DEFEAT, WITHDRAWN }
    private record Sortie(Path directory,CatastrophicGame game,Battle battle,
                          CampaignLedger.Snapshot before,
                          CampaignLedger.Snapshot expected) { }

    private static void check(boolean passed,String message){
        if(!passed)throw new AssertionError(message);
    }
    private static Field field(String name)throws ReflectiveOperationException{
        Field field=CatastrophicGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void set(CatastrophicGame game,String name,Object value)throws Exception{
        field(name).set(game,value);
    }
    private static Object get(CatastrophicGame game,String name)throws Exception{
        return field(name).get(game);
    }
    private static Object call(CatastrophicGame game,String name,
                               Class<?>[] types,Object... arguments)throws Exception{
        Method method=CatastrophicGame.class.getDeclaredMethod(name,types);
        method.setAccessible(true);
        try{return method.invoke(game,arguments);}
        catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();
            if(cause instanceof Exception exception)throw exception;
            if(cause instanceof Error error)throw error;
            throw new AssertionError("Controller action failed: "+name,cause);
        }
    }
    private static Object call(CatastrophicGame game,String name)throws Exception{
        return call(game,name,new Class<?>[0]);
    }
    private static void tap(CatastrophicGame game,float x,float y)throws Exception{
        call(game,"tapFrontEnd",new Class<?>[]{float.class,float.class},x,y);
    }
    private static void key(CatastrophicGame game,int key)throws Exception{
        call(game,"resultsKey",new Class<?>[]{int.class},key);
    }
    private static GameMenuPanel.Screen screen(CatastrophicGame game)throws Exception{
        return (GameMenuPanel.Screen)get(game,"screen");
    }
    private static boolean pending(CatastrophicGame game)throws Exception{
        return (Boolean)call(game,"campaignSettlementPending");
    }
    private static CampaignLedger.Snapshot current(CatastrophicGame game)throws Exception{
        return new CampaignLedger.Snapshot((Long)get(game,"ledgerGeneration"),
                (CampaignProgress.State)get(game,"campaign"),
                (CampaignFleet.Roster)get(game,"campaignFleet"),
                (CampaignTechnology.State)get(game,"discoveries"));
    }
    private static Path ledger(Path directory){return directory.resolve(LEDGER);}
    private static Path temporary(Path directory){return directory.resolve(LEDGER+".tmp");}
    private static void authority(CatastrophicGame game,Path directory,
                                  CampaignLedger.Snapshot expected,String reason)throws Exception{
        check(CampaignLedger.equivalent(current(game),expected),reason+": live authority differs");
        check(CampaignLedger.equivalent(
                CampaignLedger.load(ledger(directory),catalog).orElseThrow(),expected),
                reason+": durable authority differs");
    }
    private static Object primitiveDefault(Class<?> type){
        if(type==boolean.class)return false;
        if(type==int.class)return 0;
        if(type==long.class)return 0L;
        if(type==float.class)return 0f;
        if(type==double.class)return 0d;
        return null;
    }
    private static void localFiles(Path directory){
        Gdx.files=(com.badlogic.gdx.Files)Proxy.newProxyInstance(
                CampaignRecoveryFlowTest.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Files.class},(proxy,method,args)->{
                    if(method.getName().equals("local"))
                        return new FileHandle(directory.resolve((String)args[0]).toFile());
                    if(method.getName().equals("getLocalStoragePath"))return directory.toString();
                    if(method.getName().equals("isLocalStorageAvailable"))return true;
                    return primitiveDefault(method.getReturnType());
                });
    }
    private static Shipyard.Design starter(){
        return Shipyard.starter(FACTION,Shipyard.Hull.CORVETTE);
    }
    private static CampaignFleet.Roster fleet(int count){
        CampaignFleet.Roster roster=CampaignFleet.startSolo(FACTION,starter(),catalog,1234567L);
        for(int n=1;n<count;n++)roster=roster.purchase(starter(),catalog);
        return roster;
    }
    private static CatastrophicGame controller(Path directory)throws Exception{
        localFiles(directory);
        CatastrophicGame game=new CatastrophicGame();
        Shipyard.Design first=starter();
        Shipyard.Design second=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);
        set(game,"technology",catalog);
        set(game,"left",FACTION);
        set(game,"right",Battle.Faction.DRAXIAN);
        set(game,"pendingFaction",FACTION);
        set(game,"leftDesign",first);
        set(game,"rightDesign",second);
        set(game,"campaignDesign",first);
        // The real editor can synchronize deployment without graphics/assets.
        set(game,"shipyard",new ShipyardPanel(catalog,(side,fit)->{},first,second,null));
        call(game,"restoreCampaign");
        return game;
    }
    private static CatastrophicGame saved(Path directory,CampaignLedger.Snapshot initial)
            throws Exception{
        CampaignLedger.commit(ledger(directory),initial,catalog);
        CatastrophicGame game=controller(directory);
        authority(game,directory,initial,"Initial controller restore");
        return game;
    }
    private static Sortie sortie(Path directory,Outcome outcome)throws Exception{
        CampaignProgress.State wallet=new CampaignProgress.State(FACTION,2,5_000_000L,1,2);
        CampaignTechnology.State learned=new CampaignTechnology.State(
                FACTION,Set.of(LASER_I),null,0,0);
        CampaignTechnology.State funded=learned.begin(LASER_II,wallet,catalog);
        wallet=wallet.spend(learned.quote(LASER_II,wallet,catalog).cost())
                .beginResearch(CampaignProgress.Project.LICENSE);
        CampaignFleet.Roster roster=fleet(2);
        roster=roster.setActive(roster.ships().get(1).id(),true,5);
        CampaignLedger.Snapshot before=new CampaignLedger.Snapshot(1,wallet,roster,funded);
        CatastrophicGame game=saved(directory,before);
        Battle battle=Battle.duel(771L,2,starter(),
                Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE),catalog);
        List<Battle.Ship> participants=battle.ships().stream().filter(s->s.team==0).toList();
        List<Long> ids=roster.active().stream().map(CampaignFleet.Vessel::id).toList();
        for(int n=0;n<participants.size();n++){
            Battle.Ship ship=participants.get(n);
            CampaignFleet.Vessel owned=roster.active().get(n);
            owned.stores().apply(ship);
            ship.commissionedId=owned.id();
            ship.name=owned.name();
            ship.hull*=.70;
            ship.fuel*=.25;
            ship.missileAmmo=Math.max(0,ship.missileAmmo-2);
            if(n==1||outcome==Outcome.DEFEAT)ship.hull=0;
            if(n==0&&outcome==Outcome.WITHDRAWN)ship.escaped=true;
        }
        if(outcome==Outcome.WIN)
            for(Battle.Ship ship:battle.ships())if(ship.team==1)ship.hull=0;
        set(game,"battle",battle);
        set(game,"playedCampaign",true);
        set(game,"activeMission",31);
        set(game,"selectedMission",1); // Results must restore the actual sortie's mission.
        set(game,"deployedAtLaunch",2);
        set(game,"screen",GameMenuPanel.Screen.BATTLE);
        @SuppressWarnings("unchecked")
        List<Long> deployed=(List<Long>)get(game,"campaignSortie");
        deployed.addAll(ids);
        boolean victory=outcome==Outcome.WIN;
        CampaignProgress.State next=victory?wallet.complete(31,2,1)
                .awardAfterActionSalvage(battle.recoverySalvageCredits(0)):wallet;
        CampaignLedger.Snapshot expected=new CampaignLedger.Snapshot(2,next,
                roster.afterBattle(battle,ids),victory?funded.victory():funded);
        return new Sortie(directory,game,battle,before,expected);
    }
    private static void pendingResults(Sortie fixture,String reason)throws Exception{
        check(screen(fixture.game())==GameMenuPanel.Screen.RESULTS,reason+": left Results");
        check(pending(fixture.game()),reason+": pending outcome was discarded");
        check("RETRY SAVE".equals(call(fixture.game(),"resultsPrimaryLabel")),
                reason+": visible primary action is not Retry Save");
        String notice=(String)call(fixture.game(),"resultsNotice");
        check(notice!=null&&!notice.isBlank()&&notice.toLowerCase(Locale.ROOT).contains("save"),
                reason+": Results has no visible save explanation");
        authority(fixture.game(),fixture.directory(),fixture.before(),reason);
    }
    private static void failedSettlement(Path directory)throws Exception{
        Sortie fixture=sortie(directory,Outcome.WIN);
        Files.createDirectory(temporary(directory));
        call(fixture.game(),"finishEngagement");
        check((Boolean)get(fixture.game(),"lastWon"),"Winning fixture was not recognized");
        check((Long)get(fixture.game(),"lastReward")==0,"Failed write advertised a credited reward");
        pendingResults(fixture,"Initial failed winning settlement");
        tap(fixture.game(),800,145); // Visible Return.
        pendingResults(fixture,"Return while storage is unavailable");
        for(int input:new int[]{Input.Keys.ESCAPE,Input.Keys.BACK}){
            key(fixture.game(),input);
            pendingResults(fixture,"Keyboard exit while storage is unavailable");
        }
        for(int attempt=0;attempt<2;attempt++){
            tap(fixture.game(),480,145);
            pendingResults(fixture,"Repeated failed Retry Save");
        }
        key(fixture.game(),Input.Keys.ENTER);
        pendingResults(fixture,"Enter retries a pending save without abandoning it");
        // A pending settlement must not be recomputed from a changed mutable battle.
        for(Battle.Ship ship:fixture.battle().ships())if(ship.team==0){
            ship.hull=0;ship.fuel=0;ship.missileAmmo=0;
        }
        call(fixture.game(),"finishEngagement");
        check((Boolean)get(fixture.game(),"lastWon"),
                "Duplicate completion recomputed the already-frozen battle outcome");
        pendingResults(fixture,"Duplicate completion while pending");
        Files.delete(temporary(directory));
        tap(fixture.game(),800,145);
        pendingResults(fixture,"Ordinary Return after storage recovery must still retain the pending outcome");
        tap(fixture.game(),480,145);
        check(!pending(fixture.game())&&screen(fixture.game())==GameMenuPanel.Screen.RESULTS,
                "Successful Retry Save must commit and remain on Results");
        check("PREPARE SORTIE".equals(call(fixture.game(),"resultsPrimaryLabel")),
                "Committed Results still offers a save retry");
        authority(fixture.game(),directory,fixture.expected(),"Recovered frozen outcome");
        check((Long)get(fixture.game(),"lastReward")==
                        fixture.expected().progress().credits()-fixture.before().progress().credits(),
                "Recovered Results reward differs from the committed wallet delta");
        call(fixture.game(),"finishEngagement");
        check(!pending(fixture.game()),"Duplicate completion reopened a committed settlement");
        authority(fixture.game(),directory,fixture.expected(),"Duplicate completion after successful save");
        tap(fixture.game(),480,145); // A repeated click now prepares, never pays twice.
        check(screen(fixture.game())==GameMenuPanel.Screen.DEPLOYMENT,
                "Committed Results primary did not prepare the surviving fleet");
        tap(fixture.game(),480,145);
        authority(fixture.game(),directory,fixture.expected(),"Repeated primary input");
        CatastrophicGame reopened=controller(directory);
        authority(reopened,directory,fixture.expected(),"Reopen after recovered settlement");
        System.out.println("PASS campaign recovery: pending Results, blocked exits, frozen retry, exact-once stores/losses/research and reopen");
    }
    private static void lostAcknowledgement(Path directory)throws Exception{
        Sortie fixture=sortie(directory,Outcome.WIN);
        Files.createDirectory(temporary(directory));
        call(fixture.game(),"finishEngagement");
        pendingResults(fixture,"Lost-ack fixture before externally completed write");
        Files.delete(temporary(directory));
        // Emulate a completed atomic write whose success never reached the controller.
        CampaignLedger.commit(ledger(directory),fixture.expected(),catalog);
        check(CampaignLedger.equivalent(current(fixture.game()),fixture.before()),
                "Lost-ack fixture must retain its pre-commit live authority");
        byte[] committed=Files.readAllBytes(ledger(directory));
        byte[] backup=Files.readAllBytes(directory.resolve(LEDGER+".bak"));
        key(fixture.game(),Input.Keys.ENTER);
        check(!pending(fixture.game())&&screen(fixture.game())==GameMenuPanel.Screen.RESULTS,
                "An already-written exact proposal was not reconciled");
        authority(fixture.game(),directory,fixture.expected(),"Already committed proposal");
        check(Arrays.equals(committed,Files.readAllBytes(ledger(directory)))&&
                        Arrays.equals(backup,Files.readAllBytes(directory.resolve(LEDGER+".bak"))),
                "Lost acknowledgement changed the committed generation or recovery copy");
        key(fixture.game(),Input.Keys.R);
        check(screen(fixture.game())==GameMenuPanel.Screen.DEPLOYMENT,
                "Keyboard R did not prepare the committed sortie");
        authority(fixture.game(),directory,fixture.expected(),"Keyboard input after lost-ack reconciliation");
        System.out.println("PASS campaign recovery: already-written proposal reconciles without another reward/generation");
    }
    private static void resultsInputs(Path directory)throws Exception{
        Sortie fixture=sortie(directory.resolve("won"),Outcome.WIN);
        call(fixture.game(),"finishEngagement");
        authority(fixture.game(),fixture.directory(),fixture.expected(),"Normal winning settlement");
        for(boolean pointer:new boolean[]{true,false}){
            CatastrophicGame game=controller(fixture.directory());
            set(game,"playedCampaign",true);set(game,"activeMission",31);
            set(game,"selectedMission",1);set(game,"screen",GameMenuPanel.Screen.RESULTS);
            if(pointer)tap(game,480,145);else key(game,Input.Keys.R);
            check(screen(game)==GameMenuPanel.Screen.DEPLOYMENT&&
                            (Integer)get(game,"selectedMission")==31,
                    "Pointer and keyboard retry must select the actual mission and deployment");
            CampaignDeployment.Loadout deployment=(CampaignDeployment.Loadout)get(game,"deployment");
            check(deployment.ships()==fixture.expected().fleet().active().size(),
                    "Results retry deployed an unowned or destroyed hull");
            authority(game,fixture.directory(),fixture.expected(),"Results preparation preserves paid stores");
        }
        for(int input:new int[]{-1,Input.Keys.ENTER,Input.Keys.ESCAPE,Input.Keys.BACK}){
            CatastrophicGame game=controller(fixture.directory());
            set(game,"playedCampaign",true);set(game,"screen",GameMenuPanel.Screen.RESULTS);
            if(input==-1)tap(game,800,145);else key(game,input);
            check(screen(game)==GameMenuPanel.Screen.DOCK,"Committed Results exit did not open Dock");
            authority(game,fixture.directory(),fixture.expected(),"Results exit preserves authority");
        }
        for(Outcome outcome:new Outcome[]{Outcome.DEFEAT,Outcome.WITHDRAWN}){
            Sortie ended=sortie(directory.resolve(outcome.name().toLowerCase(Locale.ROOT)),outcome);
            call(ended.game(),"finishEngagement");
            check(!(Boolean)get(ended.game(),"lastWon")&&!pending(ended.game()),
                    "Non-victory settlement was treated as a win or left pending");
            authority(ended.game(),ended.directory(),ended.expected(),"Non-victory settlement");
            check(ended.expected().progress().equals(ended.before().progress())&&
                            ended.expected().technology().equals(ended.before().technology()),
                    "Non-victory advanced rewards or paid research");
            if(outcome==Outcome.DEFEAT)tap(ended.game(),480,145);
            else key(ended.game(),Input.Keys.R);
            check(screen(ended.game())==GameMenuPanel.Screen.DOCK,
                    "No active surviving hulls must return to management");
            authority(ended.game(),ended.directory(),ended.expected(),"Empty deployment retry");
        }
        System.out.println("PASS campaign Results: pointer/keyboard preparation and exits, defeat and withdrawn reserves");
    }
    private static List<Path> archives(Path directory)throws Exception{
        Path parent=ledger(directory).getParent();
        if(!Files.exists(parent))return List.of();
        String prefix=ledger(directory).getFileName()+".recovery-";
        try(var entries=Files.list(parent)){
            return entries.filter(Files::isDirectory)
                    .filter(path->path.getFileName().toString().startsWith(prefix)).toList();
        }
    }
    private static void corruptRecovery(Path directory)throws Exception{
        Files.createDirectories(ledger(directory).getParent());
        byte[] primary="invalid primary — retain these bytes".getBytes(StandardCharsets.UTF_8);
        byte[] backup="invalid backup — retain these different bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(ledger(directory),primary);
        Files.write(directory.resolve(LEDGER+".bak"),backup);
        CatastrophicGame game=controller(directory);
        check(get(game,"campaign")==null,"Invalid authorities silently created a free campaign");
        set(game,"screen",GameMenuPanel.Screen.CAMPAIGN_SETUP);
        tap(game,1040,60);
        check(screen(game)==GameMenuPanel.Screen.CAMPAIGN_SETUP&&
                        (Boolean)get(game,"campaignResetArmed")&&get(game,"campaign")==null,
                "First reset action must request explicit confirmation");
        String explanation=((String)get(game,"setupNotice")).toLowerCase(Locale.ROOT);
        check(explanation.contains("archive")&&explanation.contains("confirm"),
                "Setup must explain archive/replacement confirmation visibly");
        // Selecting another faction revokes the already-armed replacement.
        tap(game,101,466);
        check(!(Boolean)get(game,"campaignResetArmed"),"Faction change retained reset confirmation");
        tap(game,101,528); // Terran again.
        tap(game,1040,60);
        check((Boolean)get(game,"campaignResetArmed"),"Reset did not require fresh confirmation");
        tap(game,120,60); // Back cancels; it is not consent to destroy the old authority.
        check(screen(game)==GameMenuPanel.Screen.HOME&&
                        !(Boolean)get(game,"campaignResetArmed"),"Back retained an armed reset");
        check(Arrays.equals(primary,Files.readAllBytes(ledger(directory)))&&
                        Arrays.equals(backup,Files.readAllBytes(directory.resolve(LEDGER+".bak")))&&
                        archives(directory).isEmpty(),
                "Cancelled reset changed or archived the corrupt authority");
        call(game,"openCampaignSetup");
        tap(game,1040,60);
        check(get(game,"campaign")==null&&screen(game)==GameMenuPanel.Screen.CAMPAIGN_SETUP,
                "Reopened setup reused cancelled replacement consent");
        tap(game,1040,60);
        check(screen(game)==GameMenuPanel.Screen.CAMPAIGN,"Confirmed replacement did not open campaign");
        CampaignLedger.Snapshot replacement=CampaignLedger.load(ledger(directory),catalog).orElseThrow();
        check(replacement.generation()==1&&replacement.progress().credits()==0&&
                        replacement.progress().researchRank()==0&&replacement.fleet().ships().size()==1&&
                        replacement.fleet().active().size()==1&&replacement.progress().faction()==FACTION&&
                        replacement.technology().unlocked().isEmpty()&&replacement.technology().activeId()==null,
                "Replacement did not create exactly one unfunded starter authority");
        authority(game,directory,replacement,"Explicit replacement live/durable parity");
        List<Path> retained=archives(directory);
        check(retained.size()==1,"Confirmed reset did not preserve one recovery archive");
        Path archive=retained.get(0);
        check(Arrays.equals(primary,Files.readAllBytes(archive.resolve(ledger(directory).getFileName())))&&
                        Arrays.equals(backup,Files.readAllBytes(archive.resolve(
                                ledger(directory).getFileName()+".bak"))),
                "Recovery archive does not preserve both original authority files byte-for-byte");
        System.out.println("PASS campaign recovery: explicit corrupt-save archive/reset, faction change and cancelled consent");
    }
    private static void validBackup(Path directory)throws Exception{
        for(boolean missing:new boolean[]{false,true}){
            Path home=directory.resolve(missing?"missing-primary":"corrupt-primary");
            CampaignProgress.State progress=new CampaignProgress.State(FACTION,2,100_000L,1,2);
            CampaignLedger.Snapshot backup=new CampaignLedger.Snapshot(1,progress,fleet(2));
            CampaignLedger.commit(ledger(home),backup,catalog);
            CampaignLedger.commit(ledger(home),backup.next(progress.spend(1234),backup.fleet()),catalog);
            if(missing)Files.delete(ledger(home));
            else Files.writeString(ledger(home),"broken newer authority");
            CatastrophicGame game=controller(home);
            authority(game,home,backup,"Automatic valid backup recovery");
            check(!(Boolean)get(game,"campaignResetArmed")&&archives(home).isEmpty(),
                    "Valid backup incorrectly required or triggered destructive recovery");
        }
        System.out.println("PASS campaign recovery: a valid backup still restores automatically without reset");
    }
    /** Read the frozen randomized creation only to stage a lost acknowledgement. */
    private static CampaignLedger.Snapshot creationProposal(CatastrophicGame game)throws Exception{
        Object pending=get(game,"pendingCampaignCreation");
        check(pending!=null,"Failed creation did not retain its original proposal");
        Method proposed=pending.getClass().getDeclaredMethod("proposed");
        proposed.setAccessible(true);
        return CampaignLedger.decode((String)proposed.invoke(pending),catalog);
    }
    private static void creationExitReconciliation(Path directory)throws Exception{
        for(boolean changeFaction:new boolean[]{false,true}){
            Path home=directory.resolve(changeFaction?"faction-after-write":"cancel-after-write");
            CampaignLedger.Snapshot before=new CampaignLedger.Snapshot(1,
                    new CampaignProgress.State(FACTION,2,100_000L,1,2),fleet(2));
            CatastrophicGame game=saved(home,before);
            call(game,"openCampaignSetup");
            Files.createDirectory(temporary(home));
            tap(game,1040,60);
            CampaignLedger.Snapshot proposed=creationProposal(game);
            check(proposed.progress().credits()==0&&proposed.fleet().ships().size()==1,
                    "Creation fixture must differ from the previous wallet and owned fleet");
            authority(game,home,before,"Failed creation leaves the previous live authority");
            Files.delete(temporary(home));
            // The disk now contains the proposed creation, but the controller
            // has not received its successful acknowledgement.
            CampaignLedger.commit(ledger(home),proposed,catalog);
            if(changeFaction)tap(game,101,466);else tap(game,120,60);
            check(get(game,"pendingCampaignCreation")==null&&
                            !(Boolean)get(game,"campaignResetArmed"),
                    "Verified creation exit did not clear its acknowledged intent");
            authority(game,home,proposed,"Creation exit must reconcile the already-saved authority");
            if(changeFaction){
                check(screen(game)==GameMenuPanel.Screen.CAMPAIGN_SETUP&&
                                get(game,"pendingFaction")==Battle.Faction.DRAXIAN,
                        "Reconciled faction selection did not remain a separate setup choice");
            }else{
                check(screen(game)==GameMenuPanel.Screen.HOME,"Reconciled Cancel did not return Home");
                call(game,"showCampaign");
                check(screen(game)==GameMenuPanel.Screen.CAMPAIGN,
                        "Continue after reconciled creation did not open the saved campaign");
                authority(game,home,proposed,"Continue must not expose the previous campaign after a lost ack");
            }
        }
        Path home=directory.resolve("unavailable-exit");
        CampaignLedger.Snapshot before=new CampaignLedger.Snapshot(1,
                new CampaignProgress.State(FACTION,2,100_000L,1,2),fleet(2));
        CatastrophicGame game=saved(home,before);
        call(game,"openCampaignSetup");
        Files.createDirectory(temporary(home));tap(game,1040,60);
        CampaignLedger.Snapshot proposed=creationProposal(game);
        Path held=home.resolve("retained-primary.dat");
        Files.move(ledger(home),held);
        Files.createDirectory(ledger(home)); // A non-regular authority is unavailable, not corrupt.
        tap(game,120,60);
        check(screen(game)==GameMenuPanel.Screen.CAMPAIGN_SETUP&&
                        CampaignLedger.equivalent(creationProposal(game),proposed),
                "Cancel discarded a creation whose storage outcome cannot be checked");
        tap(game,101,466);
        check(get(game,"pendingFaction")==FACTION&&
                        CampaignLedger.equivalent(creationProposal(game),proposed),
                "Faction change discarded an unresolved creation intent");
        call(game,"openCampaignSetup");
        check(screen(game)==GameMenuPanel.Screen.CAMPAIGN_SETUP&&
                        CampaignLedger.equivalent(creationProposal(game),proposed)&&
                        !((String)get(game,"setupNotice")).isBlank(),
                "Reopening setup discarded unresolved creation or hid its reason");
        Files.delete(ledger(home));Files.move(held,ledger(home));Files.delete(temporary(home));
        tap(game,120,60);
        check(screen(game)==GameMenuPanel.Screen.HOME&&get(game,"pendingCampaignCreation")==null,
                "Verified unchanged authority did not permit creation cancellation");
        authority(game,home,before,"Recovered storage permits cancellation without writing a new campaign");
        System.out.println("PASS campaign creation: Cancel/faction changes reconcile lost acknowledgements and retain unverified intent");
    }
    private static void branchCaps(Path directory)throws Exception{
        CampaignProgress.State progress=CampaignProgress.start(FACTION).complete(1,1,1);
        check(progress.available(31)&&progress.unlocked()==2&&LevelEconomy.fleetCap(31)==5,
                "Branch-cap fixture no longer distinguishes availability from numeric frontier");
        Path dock=directory.resolve("dock");
        CatastrophicGame game=saved(dock,new CampaignLedger.Snapshot(1,progress,fleet(6)));
        set(game,"screen",GameMenuPanel.Screen.DOCK);set(game,"selectedMission",31);
        for(int index=1;index<5;index++){
            set(game,"dockSelected",index);
            tap(game,1000,546); // Actual dock active/reserve control.
            check(current(game).fleet().active().size()==index+1,
                    "Available mission 31 refused an owned hull before its real five-ship cap");
        }
        CampaignLedger.Snapshot full=current(game);
        set(game,"dockSelected",5);tap(game,1000,546);
        authority(game,dock,full,"Dock must reject the sixth ship without a partial transaction");
        Path squads=directory.resolve("squadrons");
        CampaignFleet.Roster roster=fleet(6);
        for(int index=0;index<6;index++)roster=roster.assignSquadron(
                roster.ships().get(index).id(),index<5?1:2);
        game=saved(squads,new CampaignLedger.Snapshot(1,progress,roster));
        set(game,"screen",GameMenuPanel.Screen.SQUADRONS);
        set(game,"selectedMission",31);set(game,"selectedSquadron",1);
        tap(game,820,149);
        check(current(game).fleet().active().size()==5,
                "Squadron activation did not use the selected available mission's five-ship cap");
        full=current(game);set(game,"selectedSquadron",2);tap(game,820,149);
        authority(game,squads,full,"Squadron over-cap rejection must be atomic");
        System.out.println("PASS campaign branch context: mission 31 permits five owned ships in dock and squadrons, rejects six");
    }
    private static void lateLicenseQuotes(Path directory)throws Exception{
        for(int tier=7;tier<=9;tier++){
            Path home=directory.resolve("tier-"+tier);
            int milestone=LevelEconomy.researchLicenseMilestone(tier);
            CampaignProgress.State progress=new CampaignProgress.State(FACTION,milestone+1,
                    1_000_000_000L,milestone,tier-1,milestone,0,CampaignProgress.Project.NONE,0);
            CampaignLedger.Snapshot before=new CampaignLedger.Snapshot(1,progress,fleet(1));
            CatastrophicGame game=saved(home,before);
            call(game,"openResearch");
            for(int attempt=0;attempt<2;attempt++){
                tap(game,1070,60); // Actual Authorize License action.
                int selected=(Integer)get(game,"selectedResearchNode");
                long quoted=(Long)get(game,"armedResearchCost");
                check(selected==tier-1&&LevelEconomy.draftResearchCost(selected+1)==quoted&&
                                quoted==progress.nextResearchCost(),
                        "Tech "+tier+" selected card and authorization quote disagree");
                check(get(game,"armedResearch")==CampaignProgress.Project.LICENSE,
                        "Late-license authorization was not armed");
                String notice=(String)get(game,"researchNotice");
                check(notice.contains(Long.toString(quoted))&&
                                notice.contains(Long.toString(progress.credits()-quoted)),
                        "Late-license confirmation lost its exact cost or remaining balance");
                authority(game,home,before,"First authorization must not debit");
                if(attempt==0){
                    tap(game,140,60); // Cancel through the visible Back action.
                    check(get(game,"armedResearch")==CampaignProgress.Project.NONE,
                            "Leaving research retained a spending confirmation");
                    call(game,"openResearch");
                }
            }
            tap(game,1070,60);
            CampaignLedger.Snapshot expected=new CampaignLedger.Snapshot(2,
                    progress.beginResearch(CampaignProgress.Project.LICENSE),before.fleet(),before.technology());
            authority(game,home,expected,"Funded late-license purchase");
            tap(game,1070,60);
            authority(game,home,expected,"Repeated authorization must not charge an active project twice");
        }
        System.out.println("PASS campaign research: Tech VII–IX card/quote parity, cancellation, paid confirmation and repeat-click protection");
    }
    private static void campaignMapInputs(Path directory)throws Exception{
        CampaignProgress.State progress=CampaignProgress.start(FACTION).complete(1,1,1);
        CampaignFleet.Roster roster=fleet(2);
        roster=roster.setActive(roster.ships().get(1).id(),true,5);
        CampaignLedger.Snapshot before=new CampaignLedger.Snapshot(1,progress,roster);
        saved(directory,before);
        for(boolean pointer:new boolean[]{true,false}){
            CatastrophicGame game=controller(directory);
            set(game,"screen",GameMenuPanel.Screen.CAMPAIGN);
            set(game,"selectedMission",31);set(game,"campaignPage",CampaignMap.pageOf(31));
            if(pointer){
                tap(game,1100,60); // Campaign map -> visible Dock action.
                check(screen(game)==GameMenuPanel.Screen.DOCK,"Map's visible Dock action is inert");
                tap(game,1100,60); // Dock -> visible Prepare action.
            }else call(game,"campaignMapKey",new Class<?>[]{int.class},Input.Keys.ENTER);
            check(screen(game)==GameMenuPanel.Screen.DEPLOYMENT&&
                            (Integer)get(game,"activeMission")==31&&
                            ((CampaignDeployment.Loadout)get(game,"deployment")).ships()==2&&
                            get(game,"battle")==null,
                    "Map Enter and pointer preparation must use mission 31 and its two owned active hulls");
            authority(game,directory,before,"Map preparation must not buy, repair or launch a fleet");
        }
        CatastrophicGame game=controller(directory);
        check(!progress.available(500),"Locked-map fixture unexpectedly became available");
        set(game,"screen",GameMenuPanel.Screen.CAMPAIGN);
        set(game,"selectedMission",500);set(game,"campaignPage",CampaignMap.pageOf(500));
        call(game,"campaignMapKey",new Class<?>[]{int.class},Input.Keys.ENTER);
        check(screen(game)==GameMenuPanel.Screen.CAMPAIGN&&get(game,"battle")==null,
                "Map Enter must leave an unavailable mission on the map without launching");
        authority(game,directory,before,"Locked mission input preserves campaign authority");
        System.out.println("PASS campaign map: Enter/pointer deployment parity and locked-mission rejection");
    }
    private static void deleteTree(Path root)throws Exception{
        try(var paths=Files.walk(root)){
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);
        }
    }
    public static void main(String[] args)throws Exception{
        Application previousApp=Gdx.app;
        com.badlogic.gdx.Files previousFiles=Gdx.files;
        Path root=Files.createTempDirectory("cd-campaign-recovery-");
        try{
            Gdx.app=(Application)Proxy.newProxyInstance(CampaignRecoveryFlowTest.class.getClassLoader(),
                    new Class<?>[]{Application.class},(proxy,method,arguments)->{
                        if(method.getName().equals("getType"))return Application.ApplicationType.Desktop;
                        if(method.getName().equals("postRunnable")){
                            ((Runnable)arguments[0]).run();return null;
                        }
                        return primitiveDefault(method.getReturnType());
                    });
            catalog=TechnologyCatalog.loadDefault();
            failedSettlement(root.resolve("write-fault"));
            lostAcknowledgement(root.resolve("lost-ack"));
            resultsInputs(root.resolve("results"));
            corruptRecovery(root.resolve("corrupt"));
            validBackup(root.resolve("backup"));
            creationExitReconciliation(root.resolve("creation-exit"));
            branchCaps(root.resolve("caps"));
            lateLicenseQuotes(root.resolve("research"));
            campaignMapInputs(root.resolve("map-input"));
            System.out.println("PASS campaign recovery flow: actual controllers and durable local transactions; no graphics/device claim");
        }finally{
            Gdx.app=previousApp;Gdx.files=previousFiles;
            deleteTree(root);
        }
    }
}
