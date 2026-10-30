package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.ModuleBranchEffects;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.sim.ProjectileModel;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.FighterModel;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.sim.CampaignFleet;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.ShipTechnologyFit;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.shipyard.SkirmishShipExchange;
import diplomacy.sim.Battle;
import diplomacy.shipyard.TouchGesture;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Compact, touch-and-mouse compatible single-screen ship fitting interface. */
final class ShipyardPanel {
    private static final int SLOT_ROWS=18;
    private static final float MAP_CX=563f, MAP_CY=343f, MAP_SCALE=2.65f;
    private final TechnologyCatalog catalog;
    private final TerranPlaceholderSprites fleetArt;
    private TacticalUiIcons uiIcons;
    private Supplier<CampaignProgress.State> campaignWallet;
    private Supplier<CampaignTechnology.State> campaignDiscoveries;
    private Function<Shipyard.Design,Boolean> campaignPurchase;
    private Supplier<CampaignFleet.Roster> commissionedFleet;
    private BiFunction<Long,Shipyard.Design,Boolean> campaignRefit;
    private long refitShipId;
    // Read-only cache: the draft, wallet and roster are immutable snapshots.
    // Without this, refit quotes instantiate throwaway ships every UI frame.
    private CampaignBudgetQuotes.Quote cachedQuote;
    private Shipyard.Design cachedQuoteDraft;
    private CampaignProgress.State cachedQuoteWallet;
    private CampaignFleet.Roster cachedQuoteRoster;
    private Usage cachedQuoteMode;
    private long cachedQuoteShipId;
    private Shipyard.Design purchaseArmedDesign;
    private long purchaseArmedCredits=-1,purchaseArmedCost=-1;
    private final GrittyMenuBackgrounds backgrounds=new GrittyMenuBackgrounds();
    private final BiConsumer<Integer,Shipyard.Design> deploy;
    private Shipyard.Design[] active = new Shipyard.Design[2];
    private Shipyard.Design draft;
    private int team, selectedSlot, scroll;
    private String notice="Select a hardpoint, choose machinery, then APPLY to deploy.";
    private boolean open;
    enum Usage { WORKSHOP, SKIRMISH, CAMPAIGN, CAMPAIGN_REFIT }
    private Usage usage=Usage.WORKSHOP;
    private int campaignRank=6,unlockedMission=1;
    private boolean playerOnly;
    Usage usage(){return usage;}
    private boolean artPreview;
    private boolean technologyView;
    private Shipyard.Track technologyTrack=Shipyard.Track.OUTPUT;
    private int technologyStage=1;
    private Supplier<SkirmishTechnologyRules> skirmishRules=SkirmishTechnologyRules::unrestricted;
    private Consumer<SkirmishTechnologyRules> updateSkirmishRules=rules->{};
    private final TouchGesture gesture = new TouchGesture();
    private boolean scrollGesture;

    ShipyardPanel(TechnologyCatalog tech, BiConsumer<Integer,Shipyard.Design> deployment,
                  Shipyard.Design first, Shipyard.Design second,TerranPlaceholderSprites art) {
        catalog=tech;deploy=deployment;fleetArt=art;active[0]=first;active[1]=second;
        draft=first;
    }
    void setIcons(TacticalUiIcons icons) { uiIcons = icons; }
    void setSkirmishRules(Supplier<SkirmishTechnologyRules> rules,
                          Consumer<SkirmishTechnologyRules> update){
        skirmishRules=rules;updateSkirmishRules=update;
    }
    void setCampaignPurchasing(Supplier<CampaignProgress.State> wallet,
                              Function<Shipyard.Design,Boolean> purchase){
        campaignWallet=wallet;campaignPurchase=purchase;
    }
    void setCampaignDiscovery(Supplier<CampaignTechnology.State> discovery){
        campaignDiscoveries=discovery;
    }
    void setCampaignRefitting(Supplier<CampaignFleet.Roster> roster,
                             BiFunction<Long,Shipyard.Design,Boolean> refit){
        commissionedFleet=roster;campaignRefit=refit;
    }
    private CampaignBudgetQuotes.Quote campaignQuote(){
        CampaignProgress.State wallet=campaignWallet==null?null:campaignWallet.get();
        if(wallet==null)throw new IllegalStateException("No active campaign budget");
        CampaignTechnology.State research=campaignDiscoveries==null?null:campaignDiscoveries.get();
        if(research==null)throw new IllegalStateException("Campaign discoveries not loaded");
        CampaignFleet.Roster roster=usage==Usage.CAMPAIGN_REFIT?
                commissionedFleet.get():null;
        if(cachedQuote!=null&&cachedQuoteDraft==draft&&cachedQuoteWallet==wallet&&
                cachedQuoteRoster==roster&&cachedQuoteMode==usage&&
                cachedQuoteShipId==refitShipId)return cachedQuote;
        cachedQuote=usage==Usage.CAMPAIGN_REFIT?
                CampaignBudgetQuotes.refit(wallet,research,roster,refitShipId,draft,catalog):
                CampaignBudgetQuotes.hull(wallet,research,draft,catalog);
        cachedQuoteDraft=draft;cachedQuoteWallet=wallet;
        cachedQuoteRoster=roster;cachedQuoteMode=usage;cachedQuoteShipId=refitShipId;
        return cachedQuote;
    }
    /** Purchase an owned hull only after a second explicit authorization. */
    private void purchaseCampaignHull(){
        if((usage!=Usage.CAMPAIGN&&usage!=Usage.CAMPAIGN_REFIT)||
                (usage==Usage.CAMPAIGN&&campaignPurchase==null)||
                (usage==Usage.CAMPAIGN_REFIT&&campaignRefit==null))return;
        CampaignBudgetQuotes.Quote quote=campaignQuote();
        if(!quote.allowed()){
            purchaseArmedDesign=null;
            notice=(usage==Usage.CAMPAIGN_REFIT?"REFIT":"PURCHASE")+
                    " BLOCKED: "+quote.reason();return;
        }
        if(purchaseArmedDesign!=draft||purchaseArmedCredits!=quote.wallet()
                ||purchaseArmedCost!=quote.cost()){
            purchaseArmedDesign=draft;
            purchaseArmedCredits=quote.wallet();
            purchaseArmedCost=quote.cost();
            notice="CONFIRM "+(usage==Usage.CAMPAIGN_REFIT?"REFIT":"PURCHASE")+
                    ": "+quote.cost()+" cr / REMAINING "+quote.remaining()+
                    " cr. Press the same action again.";
            return;
        }
        purchaseArmedDesign=null;
        boolean committed=usage==Usage.CAMPAIGN_REFIT?
                campaignRefit.apply(refitShipId,draft):campaignPurchase.apply(draft);
        if(committed)close();
        else notice="Campaign spending not committed. Review dock status.";
    }
    boolean open() { return open; }
    void show() {
        open=true;usage=Usage.WORKSHOP;playerOnly=false;
        draft=active[team];selectedSlot=0;scroll=0;artPreview=false;technologyView=false;
        purchaseArmedDesign=null;refitShipId=0;
        notice="DESIGN SANDBOX: configure fitted modules; campaign technology is researched separately.";
    }
    void showSkirmish(){
        show();usage=Usage.SKIRMISH;
        notice="SKIRMISH: fitted technology is restricted by this challenge ruleset.";
    }
    /** Campaign engineering edits a purchase blueprint, NOT an owned ship for free. */
    void showCampaignFleet(int researchRank,int mission){
        if(researchRank<0||researchRank>6)
            throw new IllegalArgumentException("Invalid campaign license");
        show();side(0);usage=Usage.CAMPAIGN;playerOnly=true;
        campaignRank=researchRank;unlockedMission=mission;
        notice="BLUEPRINT saves without spending; PURCHASE charges for a new reserve hull.";
    }
    void showCampaignRefit(int researchRank,int mission,long vesselId){
        if(vesselId<=0)throw new IllegalArgumentException("Commissioned ship required");
        showCampaignFleet(researchRank,mission);
        usage=Usage.CAMPAIGN_REFIT;
        refitShipId=vesselId;
        notice="REFIT EXISTING SHIP: reserves only; repairs are paid separately.";
    }
    void close() {
        open=false;playerOnly=false;purchaseArmedDesign=null;
        cachedQuote=null;cachedQuoteDraft=null;cachedQuoteWallet=null;cachedQuoteRoster=null;
        gesture.cancel();
    }
    void dispose(){close();backgrounds.dispose();}
    void sync(int side,Shipyard.Design design) {
        active[side]=design;
        if(side==team){draft=design;selectedSlot=0;scroll=0;}
    }
    private void side(int side){team=side;draft=active[side];selectedSlot=0;scroll=0;artPreview=false;technologyView=false;}
    private void hull(int step){
        Shipyard.Hull[] options=Shipyard.Hull.shipyardOrder();
        int current=0;
        for(int i=0;i<options.length;i++)if(options[i]==draft.hull){current=i;break;}
        Shipyard.Hull next=options[Math.floorMod(current+step,options.length)];
        if(usage==Usage.CAMPAIGN){
            for(int n=0;n<options.length;n++){
                if(ShipLineages.availableInCampaign(next,unlockedMission))break;
                current=Math.floorMod(current+step,options.length);
                next=options[Math.floorMod(current+step,options.length)];
            }
        }
        draft=Shipyard.starter(draft.faction,next);
        selectedSlot=0;scroll=0;artPreview=false;
        var line=ShipLineages.of(next);
        notice="GEN "+line.generation()+" "+line.ethos()+" / "+line.role().description;
    }
    /** Jump between ten asset-size generations rather than tapping past 46 chassis.
     * Campaign skips not-yet-unlocked tiers but remains fully navigable. */
    private void hullGroup(int change){
        int now=ShipLineages.of(draft.hull).generation();
        for(int n=1;n<=10;n++){
            int target=Math.floorMod(now-1+n*change,10)+1;
            if(usage==Usage.CAMPAIGN&&
                    target>ShipLineages.maximumCampaignGeneration(unlockedMission))continue;
            Shipyard.Hull next=ShipLineages.generation(target).get(0);
            draft=Shipyard.starter(draft.faction,next);
            selectedSlot=0;scroll=0;artPreview=false;
            notice="SIZE "+target+"/10: "+ShipLineages.of(next).ethos()+
                    " | choose specific chassis with hull arrows";
            return;
        }
    }
    private void faction(int step) {
        Battle.Faction[] options=Battle.Faction.values();
        Battle.Faction next=options[Math.floorMod(draft.faction.ordinal()+step,options.length)];
        draft=Shipyard.starter(next,draft.hull);
        selectedSlot=0;scroll=0;artPreview=false;
        notice="New "+next.displayName+" "+draft.hull+" frame; stock equipment installed";
    }
    private String legacyFileName(){return "shipyard/"+team+"-"+draft.faction.name().toLowerCase(Locale.ROOT)+".ship";}
    private String fileName(){return "shipyard/"+team+"-"+draft.faction.name().toLowerCase(Locale.ROOT)
            +"-"+draft.hull.name().toLowerCase(Locale.ROOT)+".ship";}
    private void save(){
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        if(!report.valid()){notice="Cannot save unserviceable ship: "+report.explanation();return;}
        if((usage==Usage.CAMPAIGN||usage==Usage.CAMPAIGN_REFIT)&&!campaignPermitted())return;
        if(usage==Usage.SKIRMISH){
            try{skirmishRules.get().requireAllowed(draft,catalog);}
            catch(IllegalArgumentException blocked){notice=blocked.getMessage();return;}
        }
        try{Gdx.files.local(fileName()).writeString(Shipyard.encode(draft),false,"UTF-8");notice="Blueprint saved locally: "+fileName();}
        catch(RuntimeException error){notice="Save failed: "+error.getMessage();}
    }
    private void load(){
        try{
            com.badlogic.gdx.files.FileHandle file=Gdx.files.local(fileName());
            if(!file.exists())file=Gdx.files.local(legacyFileName());
            Shipyard.Design loaded=Shipyard.decode(file.readString("UTF-8"),catalog);
            if(loaded.faction!=draft.faction||loaded.hull!=draft.hull)
                throw new IllegalArgumentException("Blueprint must match selected faction AND hull class");
            if(usage==Usage.CAMPAIGN||usage==Usage.CAMPAIGN_REFIT)
                CampaignShipyardRules.requireAllowed(loaded,campaignWallet.get(),
                        campaignDiscoveries.get(),catalog);
            draft=loaded;selectedSlot=0;scroll=0;artPreview=false;notice="Loaded "+file.path()+"; APPLY to deploy";
        }catch(RuntimeException error){notice="Load refused: "+error.getMessage();}
    }
    private void cycleFighter(){
        Shipyard.Installed installed=draft.at(selectedSlot);
        if(installed==null||!installed.component.endsWith("-FTR")){
            notice="Select an installed fighter hangar before choosing its craft type";return;
        }
        FighterModel.Hull[] options=FighterModel.Hull.values();
        draft=draft.withFighterHull(options[(draft.fighterHull.ordinal()+1)%options.length]);
        notice="Fighter hangars now carry "+draft.fighterHull.name().replace('_',' ')
                +"; APPLY to launch the new fighter loadout";
    }
    /** A shared-family controller can use only ammunition researched by all
     * of its installed guns. The blueprint stores this exact choice. */
    private void cycleAmmunition(){
        Shipyard.Installed module=draft.at(selectedSlot);
        if(module==null){notice="Select an installed kinetic, missile or torpedo module";return;}
        var available=Shipyard.ammunitionAccess(draft);
        try{
            if(module.component.endsWith("-KIN")){
                var options=new java.util.ArrayList<AmmunitionTable.Kinetic>();
                for(var kind:AmmunitionTable.Kinetic.values())
                    if(available.kinetics().contains(kind))options.add(kind);
                var current=draft.ammoSelection.kinetic();
                draft=draft.withKineticAmmunition(
                        options.get((options.indexOf(current)+1)%options.size()));
                notice="Shared kinetic magazine now carries "+draft.ammoSelection.kinetic();
            }else if(module.component.endsWith("-MSL")||module.component.endsWith("-TOR")){
                boolean missile=module.component.endsWith("-MSL");
                var options=new java.util.ArrayList<AmmunitionTable.Warhead>();
                var accepted=missile?available.missiles():available.torpedoes();
                for(var kind:AmmunitionTable.Warhead.values())
                    if(accepted.contains(kind))options.add(kind);
                var family=missile?Battle.DamageType.MISSILE:Battle.DamageType.TORPEDO;
                var current=missile?draft.ammoSelection.missile():draft.ammoSelection.torpedo();
                draft=draft.withGuidedWarhead(family,
                        options.get((options.indexOf(current)+1)%options.size()));
                notice=family+" magazine now carries "+
                        (missile?draft.ammoSelection.missile():draft.ammoSelection.torpedo());
            }else{
                notice="This module has no physical ammunition selection";
            }
        }catch(IllegalArgumentException problem){notice=problem.getMessage();}
    }
    private void cyclePattern(){
        Shipyard.Installed p=draft.at(selectedSlot);
        if(p==null){notice="Install a module first";return;}
        ModuleVersions.Pattern[] versions=ModuleVersions.Pattern.values();
        ModuleVersions.Pattern next=versions[(p.pattern.ordinal()+1)%versions.length];
        if(usage==Usage.CAMPAIGN&&next!=ModuleVersions.Pattern.STANDARD&&campaignRank<2){
            notice="LOCKED: advanced patterns require research rank II";return;
        }
        draft=draft.withModuleVersion(selectedSlot,next,p.mark);
        notice="Hardware: "+next.name().replace('_',' ')+" Mk "+p.mark+
                " (research branch preserved)";
    }
    private void cycleMark(){
        Shipyard.Installed p=draft.at(selectedSlot);
        if(p==null){notice="Install a module first";return;}
        int next=p.mark%4+1;
        if(usage==Usage.CAMPAIGN&&next>campaignRank+1){
            notice="LOCKED: hardware Mk "+next+" exceeds campaign research";return;
        }
        draft=draft.withModuleVersion(selectedSlot,p.pattern,next);
        notice="Hardware: "+p.pattern.name().replace('_',' ')+" Mk "+next+
                " (separate from six-rank research)";
    }
    private boolean campaignPermitted(){
        try{
            if(campaignWallet==null||campaignDiscoveries==null||
                    campaignWallet.get()==null||campaignDiscoveries.get()==null)
                throw new IllegalArgumentException("Campaign discoveries not loaded");
            CampaignShipyardRules.requireAllowed(draft,campaignWallet.get(),
                    campaignDiscoveries.get(),catalog);
            return true;
        }catch(IllegalArgumentException denied){
            notice="LOCKED: "+denied.getMessage();return false;
        }
    }
    private void apply(){
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        if(!report.valid()){notice="Cannot approve: "+report.explanation();return;}
        if(usage==Usage.CAMPAIGN_REFIT){
            var quote=campaignQuote();
            notice="REFIT QUOTE: "+quote.cost()+" cr. "+quote.reason()+
                    ". Select REFIT to authorize.";
            return;
        }
        if(usage==Usage.CAMPAIGN&&!campaignPermitted())return;
        if(usage==Usage.SKIRMISH){
            try{skirmishRules.get().requireAllowed(draft,catalog);}
            catch(IllegalArgumentException blocked){notice="CHALLENGE BLOCKED: "+blocked.getMessage();return;}
        }
        active[team]=draft;
        deploy.accept(team,draft);
        notice=usage==Usage.CAMPAIGN?"Purchase blueprint ready; no owned ship refitted":
                "Fitted "+draft.hull+" applied to skirmish roster";
        open=false;
    }
    boolean key(int code){
        if(!open)return false;
        if(code==com.badlogic.gdx.Input.Keys.ESCAPE||code==com.badlogic.gdx.Input.Keys.TAB){close();return true;}
        if(code==com.badlogic.gdx.Input.Keys.ENTER){apply();return true;}
        if(code==com.badlogic.gdx.Input.Keys.P&&playerOnly){purchaseCampaignHull();return true;}
        if(code==com.badlogic.gdx.Input.Keys.K){cycleFighter();return true;}
        if(code==com.badlogic.gdx.Input.Keys.V){cyclePattern();return true;}
        if(code==com.badlogic.gdx.Input.Keys.B){cycleMark();return true;}
        if(code==com.badlogic.gdx.Input.Keys.A){cycleAmmunition();return true;}
        if(code==com.badlogic.gdx.Input.Keys.PAGE_UP){hullGroup(-1);return true;}
        if(code==com.badlogic.gdx.Input.Keys.PAGE_DOWN){hullGroup(1);return true;}
        if(code==com.badlogic.gdx.Input.Keys.UP){selectedSlot=Math.max(0,selectedSlot-1);scroll=Math.min(scroll,selectedSlot);return true;}
        if(code==com.badlogic.gdx.Input.Keys.DOWN){selectedSlot=Math.min(draft.slots()-1,selectedSlot+1);scroll=Math.max(scroll,selectedSlot-SLOT_ROWS+1);return true;}
        return true;
    }
    boolean scroll(float dy) {
        if(!open)return false;
        scroll=Math.max(0,Math.min(Math.max(0,draft.slots()-SLOT_ROWS),scroll+(int)Math.signum(dy)));
        return true;
    }
    void startTouch(float x,float y,int pointer) {
        if(gesture.start(pointer,x,y))scrollGesture=x<321&&y>105&&y<590;
    }
    boolean drag(float x,float y,int pointer){
        if(!open)return false;
        int rows=gesture.drag(pointer,x,y,26,scrollGesture);
        if(rows!=0)scroll=Math.max(0,Math.min(Math.max(0,draft.slots()-SLOT_ROWS),scroll+rows));
        return true;
    }
    boolean endTouch(float x,float y,int pointer) {
        if(!open) { gesture.cancel(); return false; }
        if(gesture.end(pointer,x,y)) return tap(x,y);
        return true;
    }
    /** Stable, platform-independent touch targets for the engineering tree. */
    static int technologyHit(float x,float y){
        if(y>=578&&y<=607&&x>=807&&x<=1255)return x<998?0:1;
        for(int row=0;row<4;row++){
            float bottom=479-row*62;
            if(y>=bottom&&y<=bottom+43){
                if(x>=807&&x<937)return 30+row; // skirmish track gate
                for(int stage=0;stage<6;stage++)
                    if(x>=943+stage*49&&x<985+stage*49)return 100+row*6+stage;
            }
        }
        if(y>=211&&y<=257){
            if(x>=809&&x<945)return 2;
            if(x>=949&&x<1092)return 3;
            if(x>=1096&&x<=1255)return 4;
        }
        if(y>=159&&y<=202){
            if(x>=341&&x<564)return 12;
            if(x>=568&&x<=792)return 13;
            if(x>=809&&x<940)return 5;
            if(x>=943&&x<1076)return 6;
            if(x>=1080&&x<=1255)return 7;
        }
        if(y>=111&&y<=153){
            if(x>=809&&x<1000)return 8;
            if(x>=1003&&x<=1255)return 9;
            if(x>=341&&x<563)return 10;
            if(x>=568&&x<=792)return 11;
        }
        return -1;
    }
    private boolean campaignTechnology(){
        return usage==Usage.CAMPAIGN||usage==Usage.CAMPAIGN_REFIT;
    }
    private CampaignTechnology.State permittedResearch(){
        return campaignTechnology()&&campaignDiscoveries!=null?campaignDiscoveries.get():null;
    }
    private SkirmishTechnologyRules challenge(){
        return usage==Usage.SKIRMISH?skirmishRules.get():SkirmishTechnologyRules.unrestricted();
    }
    private String technologyId(){
        Shipyard.Installed selected=draft.at(selectedSlot);
        return selected==null?"NO INSTALLED MODULE":
                ShipTechnologyFit.id(selected,technologyTrack,technologyStage);
    }
    private boolean technologyAvailable(int slot,Shipyard.Track track,int stage){
        if(campaignTechnology())return ShipTechnologyFit.available(draft,catalog,
                permittedResearch(),slot,track,stage);
        return ShipTechnologyFit.available(draft,catalog,challenge(),slot,track,stage);
    }
    private void exportChallenge(){
        if(usage!=Usage.SKIRMISH){
            notice="Portable challenges are exported from the skirmish shipyard.";return;
        }
        try{
            String encoded=SkirmishShipExchange.encode(draft,challenge(),catalog);
            String path="shipyard/skirmish-"+(team==0?"left":"right")+".cdship";
            Gdx.files.local(path).writeString(encoded,false,"UTF-8");
            notice="EXPORTED "+path+" (copy file to another device to share)";
        }catch(RuntimeException rejected){notice="EXPORT BLOCKED: "+rejected.getMessage();}
    }
    private void importChallenge(){
        if(usage!=Usage.SKIRMISH){
            notice="Import a skirmish challenge from the skirmish shipyard.";return;
        }
        try{
            var file=Gdx.files.local("shipyard/import.cdship");
            var imported=SkirmishShipExchange.decode(file.readString("UTF-8"),catalog);
            updateSkirmishRules.accept(imported.rules());
            draft=imported.design();selectedSlot=0;scroll=0;
            notice="IMPORTED ship and challenge rules from shipyard/import.cdship; APPLY to roster.";
        }catch(RuntimeException blocked){notice="IMPORT REJECTED: "+blocked.getMessage();}
    }
    private void technologyInput(int action){
        if(action==0){technologyView=false;return;}
        if(action==1){technologyView=true;return;}
        if(action>=100&&action<124){
            technologyTrack=Shipyard.Track.values()[(action-100)/6];
            technologyStage=(action-100)%6+1;
            notice=technologyId()+(technologyAvailable(selectedSlot,technologyTrack,technologyStage)
                    ?" AVAILABLE TO FIT":" LOCKED BY CURRENT TECH RULES");
            return;
        }
        if(action>=30&&action<34){
            if(usage!=Usage.SKIRMISH){notice="Campaign research is owned in the laboratory.";return;}
            Shipyard.Track branch=Shipyard.Track.values()[action-30];
            updateSkirmishRules.accept(challenge().toggleTrack(branch));
            notice="SKIRMISH "+branch+" branch permitted: "+challenge().allowedTracks().contains(branch);
            return;
        }
        if(action==10){exportChallenge();return;}
        if(action==11){importChallenge();return;}
        if(action==12||action==13){
            if(usage!=Usage.SKIRMISH){notice="Campaign hull permissions are researched in the campaign.";return;}
            var current=challenge();
            java.util.Set<Shipyard.Hull> allowed=new java.util.HashSet<>(current.allowedHulls());
            if(action==13)allowed.clear();
            else if(allowed.isEmpty())allowed.add(draft.hull);
            else if(!allowed.remove(draft.hull))allowed.add(draft.hull);
            updateSkirmishRules.accept(current.withHulls(allowed));
            notice="SKIRMISH HULL CLASSES: "+
                    (allowed.isEmpty()?"ALL":allowed.size()+" explicitly allowed");
            return;
        }
        if(action==5||action==6||action==7||action==8||action==9){
            if(usage!=Usage.SKIRMISH){notice="Only skirmish challenges can change their tech gates.";return;}
            var rules=challenge();
            try{
                if(action==5)rules=rules.withStage(Math.max(0,rules.maxStage()-1));
                if(action==6)rules=rules.withStage(Math.min(6,rules.maxStage()+1));
                if(action==7){
                    if(draft.at(selectedSlot)==null){notice="Select a fitted module first";return;}
                    rules=rules.toggleTechnology(technologyId());
                }
                if(action==8)rules=rules.withHullGeneration(Math.max(1,rules.maxHullGeneration()-1));
                if(action==9)rules=rules.withHullGeneration(Math.min(10,rules.maxHullGeneration()+1));
                updateSkirmishRules.accept(rules);
                notice="CHALLENGE: TECH "+rules.maxStage()+"/6  HULL GEN <= "+rules.maxHullGeneration();
            }catch(IllegalArgumentException refused){notice=refused.getMessage();}
            return;
        }
        if(action!=2&&action!=3&&action!=4)return;
        if(draft.at(selectedSlot)==null){notice="Install a component and select its technology";return;}
        try{
            Shipyard.Design next;
            if(action==4){
                if(campaignTechnology())
                    next=ShipTechnologyFit.doctrine(draft,catalog,permittedResearch(),technologyTrack);
                else next=ShipTechnologyFit.doctrine(draft,catalog,challenge(),technologyTrack);
            }else{
                var scope=action==2?ShipTechnologyFit.Scope.SOCKET:
                        ShipTechnologyFit.Scope.MATCHING_COMPONENTS;
                if(campaignTechnology())
                    next=ShipTechnologyFit.fit(draft,catalog,permittedResearch(),selectedSlot,
                            technologyTrack,technologyStage,scope);
                else next=ShipTechnologyFit.fit(draft,catalog,challenge(),selectedSlot,
                            technologyTrack,technologyStage,scope);
            }
            draft=next;purchaseArmedDesign=null;cachedQuote=null;
            notice="FITTED "+technologyTrack+" / "+
                    (action==4?"whole draft":action==3?"all matching modules":"selected socket")+
                    " | changes apply only to this ship blueprint";
        }catch(IllegalArgumentException blocked){notice="FITTING DENIED: "+blocked.getMessage();}
    }

    /** Shared pointer/touch hit test is intentionally independent of the renderer. */
    private int hitHardpoint(float x,float y) {
        return HullLayouts.pick(draft.placements(),x,y,MAP_CX,MAP_CY,MAP_SCALE,14);
    }
    private void select(int index) {
        selectedSlot=index;
        if(index<scroll)scroll=index;
        if(index>=scroll+SLOT_ROWS)scroll=index-SLOT_ROWS+1;
        notice="Hardpoint "+(index+1)+" / "+draft.placements().get(index).mount()+" selected";
    }
    boolean tap(float x,float y){
        if(!open)return false;
        if(y>=578&&y<=607&&x>=807&&x<=1255){
            technologyInput(technologyHit(x,y));return true;
        }
        if(technologyView&&y<638){
            if(x<320&&y>=105&&y<=590){
                int index=scroll+(int)((590-y)/26);
                if(index>=0&&index<draft.slots())select(index);
            }else technologyInput(technologyHit(x,y));
            return true;
        }
        if(y>=605&&y<=634){
            if(x>=337&&x<=382){hullGroup(-1);return true;}
            if(x>=646&&x<=691){hullGroup(1);return true;}
        }
        if(y>638){
            if(x<160){side(0);return true;}
            if(x<310){if(!playerOnly)side(1);return true;}
            if(x>=338&&x<377){hull(-1);return true;}
            if(x>=647&&x<699){hull(1);return true;}
            if(x>=705&&x<810){apply();return true;}
            if(x>=819&&x<910){save();return true;}
            if(x>=923&&x<1017){load();return true;}
            if(x>=1018&&x<=1120){if(playerOnly)purchaseCampaignHull();else faction(1);return true;}
            if(x>1120){close();return true;}
            return true;
        }
        if(x>=807&&x<=1256&&y>=62&&y<=103){
            if(x<1085)cyclePattern();else cycleMark();
            return true;
        }
        if(x>=690&&x<=795&&y>=580&&y<=635) {
            if(artPreview)artPreview=false;
            else if(fleetArt!=null && fleetArt.hasHull(draft.faction,draft.hull))artPreview=true;
            else notice="No approved individual hull artwork for "+draft.faction+" "+draft.hull;
            return true;
        }
        if(x<320 && y>=105&&y<=590){
            int index=scroll+(int)((590-y)/26);
            if(index>=0&&index<draft.slots())select(index);
            return true;
        }
        if(x>=335&&x<=796&&y>=180&&y<=215
                &&draft.at(selectedSlot)!=null
                &&draft.at(selectedSlot).component.endsWith("-FTR")){
            cycleFighter();return true;
        }
        if(x>=335&&x<=796&&y>=107&&y<=131
                &&draft.at(selectedSlot)!=null){
            cycleAmmunition();return true;
        }
        if(x>=335&&x<=796&&y>=110&&y<=580){
            if(artPreview){notice="Switch to SHOW MAP to select physical module hardpoints";return true;}
            int hit=hitHardpoint(x,y);
            if(hit>=0)select(hit);
            return true;
        }
        if(x>=805&&x<=1256&&y>=365&&y<=575){
            int index=(int)((575-y)/25);
            Shipyard.Mount mount=draft.placements().get(selectedSlot).mount();
            List<Component> options=Shipyard.choices(catalog,draft.faction,mount);
            if(index==0){
                try{draft=draft.install(selectedSlot,null);notice="Mount cleared";}
                catch(IllegalArgumentException blocked){notice=blocked.getMessage();}
                return true;
            }
            if(index>0&&index<=options.size()){
                Component part=options.get(index-1);
                try{draft=draft.install(selectedSlot,part.id);}
                catch(IllegalArgumentException blocked){notice=blocked.getMessage();return true;}
                notice="Installed "+part.name+" (upgrades reset for this mount)";
            }
            return true;
        }
        if(x>=807&&y>=220&&y<=351){
            int trackIndex=(int)((351-y)/30);
            if(trackIndex>=0&&trackIndex<Shipyard.Track.values().length){
                Shipyard.Installed installed=draft.at(selectedSlot);
                if(installed==null){notice="Choose a component first";return true;}
                Shipyard.Track track=Shipyard.Track.values()[trackIndex];
                int delta=x>=1185?1:x>=1090?-1:0;
                if(delta!=0){
                    Shipyard.Track chosen=installed.branch();
                    if(chosen!=null && chosen!=track){
                        notice="LOCKED: "+chosen+" research selected on this module. "
                                +"Reduce "+chosen+" to stage 0 to respec before choosing "+track;
                    }else{
                        int level=Math.max(0,Math.min(6,installed.stage(track)+delta));
                        if((usage==Usage.CAMPAIGN||usage==Usage.CAMPAIGN_REFIT)&&
                                level>campaignRank){
                            notice="LOCKED: purchase research rank "+level+" first";return true;
                        }
                        if(level>0&&(usage==Usage.CAMPAIGN||usage==Usage.CAMPAIGN_REFIT)){
                            String technologyId=installed.component.replaceFirst("^BASE-","TECH-")+
                                    "-"+track.name()+"-"+
                                    String.format(Locale.ROOT,"%02d",level);
                            if(campaignDiscoveries==null||campaignDiscoveries.get()==null||
                                    !campaignDiscoveries.get().has(technologyId)){
                                notice="NOT RESEARCHED: "+technologyId+
                                        " - complete discovery in Campaign Technology Lab";
                                return true;
                            }
                        }
                        try{
                            draft=draft.stage(selectedSlot,track,level);
                            notice=level==0
                                    ?"Fitted specialization cleared; choose another available variant"
                                    :track+" fitting stage "+level+" / 6 (other branches locked)";
                        }catch(IllegalArgumentException rejected){
                            notice=rejected.getMessage();
                        }
                    }
                }
            }
        }
        return true;
    }
    private static void rectangle(ShapeRenderer shapes,float x,float y,float w,float h,Color c){shapes.setColor(c);shapes.rect(x,y,w,h);}
    private static String shortText(String t,int max){return t.length()<=max?t:t.substring(0,max-3)+"...";}
    private static String units(double n){return String.format(Locale.ROOT,"%.1f",n);}
    private static Color mountColor(Shipyard.Mount mount){
        return switch(mount){
            case SYSTEM -> Color.CYAN;
            case WEAPON -> Color.ORANGE;
            case DEFENSE -> Color.GREEN;
            case SUPPORT -> Color.VIOLET;
            case HABITAT -> Color.SKY;
        };
    }
    /** Display-only category lookup; does not grant or change any module behavior. */
    private static String iconForModule(String label, Shipyard.Mount mount) {
        String item=label.toUpperCase(Locale.ROOT);
        if(item.contains("MISSILE") || item.contains("TORPEDO"))return "missile";
        if(item.contains("LASER") || item.contains("BEAM") || item.contains("PARTICLE"))return "laser";
        if(item.contains("POINT DEFENSE") || item.contains("FLAK") || item.contains("TURRET"))return "turret";
        if(item.contains("DRONE"))return "drone";
        if(item.contains("HANGAR") || item.contains("FIGHTER"))return "squadron";
        if(item.contains("EMP") || item.contains("ECM") || item.contains("JAMMER"))return "emp";
        if(item.contains("STEALTH") || item.contains("CLOAK"))return "stealth";
        if(item.contains("SENSOR") || item.contains("RADAR") || item.contains("SCANNER"))return "detect";
        if(item.contains("SHIELD"))return "shield";
        if(item.contains("ARMOR"))return "armor";
        if(item.contains("REPAIR"))return "repair";
        if(item.contains("ENGINE") || item.contains("THRUST") || item.contains("PROPULSION"))return "engine";
        if(item.contains("REACTOR") || item.contains("POWER"))return "energy";
        return switch(mount) {
            case SYSTEM -> "engine";
            case WEAPON -> "weapons";
            case DEFENSE -> "shield";
            case SUPPORT -> "repair";
            case HABITAT -> "shield";
        };
    }
    private static int count(List<Shipyard.Slot> slots,Shipyard.Mount mount){
        int result=0;
        for(Shipyard.Slot s:slots)if(s.mount()==mount)result++;
        return result;
    }
    /** A ship-specific interpretation of the same catalog as the campaign
     * research laboratory, but with its own skirmish challenge permissions. */
    private void drawTechnology(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font){
        var report=Shipyard.inspect(draft,catalog);
        Shipyard.Installed selected=draft.at(selectedSlot);
        SkirmishTechnologyRules rules=challenge();
        boolean campaign=campaignTechnology();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rectangle(shapes,0,0,1280,720,MilitaryUiTheme.BACKGROUND);
        rectangle(shapes,0,630,1280,90,new Color(.06f,.09f,.15f,1));
        rectangle(shapes,24,83,300,524,new Color(.045f,.075f,.11f,1));
        rectangle(shapes,332,83,463,524,new Color(.03f,.052f,.081f,1));
        rectangle(shapes,803,83,452,524,new Color(.045f,.075f,.11f,1));
        for(int n=0;n<SLOT_ROWS;n++){
            int index=scroll+n;
            if(index>=draft.slots())break;
            if(index==selectedSlot)rectangle(shapes,30,561-n*26,286,25,
                    new Color(.13f,.23f,.33f,1));
        }
        for(int branch=0;branch<4;branch++){
            float bottom=479-branch*62;
            Shipyard.Track track=Shipyard.Track.values()[branch];
            boolean branchEnabled=campaign||rules.allowedTracks().contains(track);
            rectangle(shapes,810,bottom,126,43,branchEnabled?
                    new Color(.10f,.20f,.27f,1):new Color(.25f,.08f,.12f,1));
            for(int rank=1;rank<=6;rank++){
                boolean allowed=selected!=null&&technologyAvailable(selectedSlot,track,rank);
                boolean active=selected!=null&&selected.stage(track)==rank;
                boolean chosen=track==technologyTrack&&rank==technologyStage;
                rectangle(shapes,943+(rank-1)*49,bottom,42,43,
                        active?new Color(.24f,.44f,.23f,1):
                        chosen?new Color(.30f,.25f,.12f,1):
                        allowed?new Color(.10f,.27f,.24f,1):
                                new Color(.13f,.13f,.16f,1));
            }
        }
        for(int i=0;i<3;i++)rectangle(shapes,809+i*145,211,i==2?159:136,46,
                new Color(.11f,.23f,.31f,1));
        for(int i=0;i<3;i++)rectangle(shapes,809+i*137,159,i==2?172:131,43,
                new Color(.12f,.18f,.25f,1));
        for(int i=0;i<2;i++)rectangle(shapes,809+i*194,111,i==1?250:191,42,
                new Color(.12f,.18f,.25f,1));
        rectangle(shapes,341,159,221,43,new Color(.12f,.18f,.25f,1));
        rectangle(shapes,568,159,224,43,new Color(.12f,.18f,.25f,1));
        rectangle(shapes,341,111,221,42,new Color(.12f,.25f,.29f,1));
        rectangle(shapes,568,111,224,42,new Color(.12f,.25f,.29f,1));
        shapes.end();
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch,campaign?"CAMPAIGN ENGINEERING / TECH LOADOUT":
                usage==Usage.SKIRMISH?"SKIRMISH ENGINEERING / CHALLENGE TECH":
                        "DESIGN WORKSHOP / TECH LOADOUT",22,704);
        font.draw(batch,"LEFT FLEET",34,660);
        if(!playerOnly)font.draw(batch,"RIGHT FLEET",175,660);
        font.draw(batch,"<",345,660);
        font.draw(batch,shortText(draft.hull.name().replace('_',' '),20),392,660);
        font.draw(batch,">",652,660);
        font.draw(batch,playerOnly?"BLUEPRINT":"APPLY",717,660);
        font.draw(batch,"SAVE",831,660);
        font.draw(batch,"LOAD",938,660);
        font.draw(batch,playerOnly?"PURCHASE":"FACTION >",1022,660);
        font.draw(batch,"CLOSE",1171,660);
        font.setColor(Color.CYAN);
        font.draw(batch,"SELECTED HARDPOINT",36,587);
        font.draw(batch,"VESSEL TECHNOLOGY PLAN",346,587);
        font.draw(batch,"< MODULES",812,587);
        font.draw(batch,"TECH LOADOUT",1004,587);
        for(int n=0;n<SLOT_ROWS;n++){
            int index=scroll+n;
            if(index>=draft.slots())break;
            Shipyard.Slot place=draft.placements().get(index);
            Shipyard.Installed part=draft.at(index);
            font.setColor(index==selectedSlot?Color.GOLD:mountColor(place.mount()));
            font.draw(batch,String.format(Locale.ROOT,"%02d  %-7s %s",
                    index+1,place.mount().name(),
                    part==null?"EMPTY":part.component.substring(5)),36,581-n*26);
        }
        font.setColor(draft.faction.r,draft.faction.g,draft.faction.b,1);
        font.draw(batch,draft.faction.displayName,345,553);
        font.setColor(Color.WHITE);
        font.draw(batch,"HULL "+draft.hull.name().replace('_',' '),345,516);
        font.draw(batch,"SOCKET "+(selectedSlot+1)+"/"+draft.slots(),345,483);
        font.draw(batch,selected==null?"NO COMPONENT":selected.component,345,449);
        font.setColor(Color.CYAN);
        font.draw(batch,"SELECTED TECHNOLOGY",345,402);
        font.setColor(Color.WHITE);
        font.draw(batch,shortText(technologyId(),47),345,373);
        font.setColor(technologyAvailable(selectedSlot,technologyTrack,technologyStage)?
                Color.GREEN:Color.GOLD);
        font.draw(batch,technologyAvailable(selectedSlot,technologyTrack,technologyStage)?
                "UNLOCKED FOR THIS BUILD":"LOCKED BY CURRENT ENTITLEMENT",345,346);
        if(selected!=null){
            var upgrade=catalog.get(technologyId());
            if(upgrade!=null){
                font.setColor(Color.LIGHT_GRAY);
                font.draw(batch,shortText(upgrade.name,53),345,316);
                font.draw(batch,shortText(upgrade.tradeoff,55),345,285);
            }
            font.setColor(Color.WHITE);
            font.draw(batch,"CURRENT "+(selected.branch()==null?"STOCK":
                    selected.branch()+" RANK "+selected.stage(selected.branch())),345,255);
        }
        font.setColor(Color.CYAN);
        font.draw(batch,campaign?"CAMPAIGN DISCOVERIES":
                "SKIRMISH RULES / INDEPENDENT OF CAMPAIGN",345,238);
        font.setColor(Color.WHITE);
        font.draw(batch,campaign?(permittedResearch()==null?"NO RESEARCH INVENTORY":
                permittedResearch().unlocked().size()+" PERMANENT RESEARCH UNLOCKS"):
                "TECH "+rules.maxStage()+"/6 / GEN "+rules.maxHullGeneration()+
                "/10 / HULLS "+(rules.allowedHulls().isEmpty()?"ALL":rules.allowedHulls().size()),345,212);
        font.draw(batch,"TOGGLE HULL CLASS",355,185);
        font.draw(batch,"ALLOW ALL HULLS",582,185);
        font.draw(batch,"EXPORT SHIP",357,136);
        font.draw(batch,"IMPORT + RULES",584,136);
        for(int branch=0;branch<4;branch++){
            Shipyard.Track track=Shipyard.Track.values()[branch];
            int bottom=479-branch*62;
            font.setColor(campaign||rules.allowedTracks().contains(track)?Color.CYAN:Color.SCARLET);
            font.draw(batch,shortText(track.name(),13),815,bottom+28);
            for(int rank=1;rank<=6;rank++){
                boolean unlocked=selected!=null&&technologyAvailable(selectedSlot,track,rank);
                font.setColor(selected!=null&&selected.stage(track)==rank?Color.GREEN:
                        unlocked?Color.WHITE:Color.GRAY);
                font.draw(batch,rank+"",959+(rank-1)*49,bottom+28);
            }
        }
        font.setColor(Color.WHITE);
        font.draw(batch,"FIT SOCKET",816,240);
        font.draw(batch,"FIT FAMILY",955,240);
        font.draw(batch,"SHIP DOCTRINE",1101,240);
        font.draw(batch,usage==Usage.SKIRMISH?"TECH CAP -":"CLEAR SLOT",814,185);
        font.draw(batch,usage==Usage.SKIRMISH?"TECH CAP +":"CONFIG",954,185);
        font.draw(batch,"BAN/ALLOW TECH",1086,185);
        font.draw(batch,"HULL GEN -",817,138);
        font.draw(batch,"HULL GEN +",1016,138);
        font.setColor(report.valid()?Color.GREEN:Color.SCARLET);
        font.draw(batch,shortText(report.explanation(),75),344,62);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,shortText(notice,128),25,35);
        batch.end();
    }

    void draw(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font) {
        if(technologyView){drawTechnology(shapes,batch,font);return;}
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        List<Shipyard.Slot> layout=draft.placements();
        Shipyard.Slot selected=layout.get(selectedSlot);
        Shipyard.Installed installed=draft.at(selectedSlot);
        boolean textured=backgrounds.prepareNamed("shipyard_hangar");
        if(textured){batch.begin();backgrounds.draw(batch);batch.end();}
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if(!textured)rectangle(shapes,0,0,1280,720,new Color(0.018f,0.029f,0.05f,1));
        rectangle(shapes,0,635,1280,85,new Color(0.06f,0.09f,0.15f,1));
        rectangle(shapes,24,83,300,524,new Color(0.045f,0.075f,0.11f,1));
        rectangle(shapes,332,83,463,524,new Color(0.03f,0.052f,0.081f,1));
        rectangle(shapes,803,83,452,524,new Color(0.045f,0.075f,0.11f,1));
        for(int n=0;n<SLOT_ROWS;n++){
            int index=scroll+n;
            if(index>=draft.slots())break;
            if(index==selectedSlot)rectangle(shapes,30,561-n*26,286,25,new Color(0.13f,0.23f,0.33f,1));
        }
        // Hardpoints are positioned in faction-specific normalized ship coordinates.
        if(!artPreview)for(Shipyard.Slot slot:layout){
            float px=MAP_CX+slot.x()*MAP_SCALE, py=MAP_CY+slot.y()*MAP_SCALE;
            shapes.setColor(slot.index()==selectedSlot?Color.WHITE:
                    draft.at(slot.index())==null?Color.GRAY:mountColor(slot.mount()));
            shapes.circle(px,py,slot.index()==selectedSlot?11:7,16);
            shapes.setColor(slot.index()==selectedSlot?Color.GOLD:new Color(0.035f,0.061f,0.09f,1));
            shapes.circle(px,py,slot.index()==selectedSlot?5:3,12);
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(draft.faction.r,draft.faction.g,draft.faction.b,1);
        List<HullLayouts.Point> border=HullLayouts.outline(draft.faction,draft.hull);
        float[] vertices=new float[border.size()*2];
        for(int n=0;n<border.size();n++){
            vertices[n*2]=MAP_CX+border.get(n).x()*MAP_SCALE;
            vertices[n*2+1]=MAP_CY+border.get(n).y()*MAP_SCALE;
        }
        if(!artPreview){
            shapes.polygon(vertices);
            // Prow indicator, never an imaginary weapon barrel.
            shapes.line(MAP_CX-6,562,MAP_CX,574);
            shapes.line(MAP_CX,574,MAP_CX+6,562);
        }
        shapes.end();
        batch.begin();
        if (uiIcons != null) {
            uiIcons.drawButton(batch,team==0?"primary":"secondary",24,642,137,42);
            if(!playerOnly)uiIcons.drawButton(batch,team==1?"primary":"secondary",169,642,139,42);
            uiIcons.drawButton(batch,"secondary",338,642,39,42);
            uiIcons.drawButton(batch,"secondary",643,642,51,42);
            uiIcons.drawButton(batch,"confirm",706,642,107,42);
            uiIcons.drawButton(batch,"secondary",819,642,96,42);
            uiIcons.drawButton(batch,"secondary",924,642,94,42);
            uiIcons.drawButton(batch,playerOnly?"confirm":"special",1019,642,109,42);
            uiIcons.drawButton(batch,"cancel",1135,642,116,42);
            uiIcons.drawButton(batch,"info",697,579,102,41);
        }
        if(artPreview&&fleetArt!=null)
            fleetArt.drawPreview(batch,draft.faction,draft.hull,MAP_CX,MAP_CY,330,402);
        if (uiIcons != null) {
            uiIcons.draw(batch, "back", 344, 649, 25);
            uiIcons.draw(batch, "next", 650, 649, 25);
            uiIcons.draw(batch, "complete", 713, 648, 25);
            uiIcons.draw(batch, "close", 1142, 649, 25);
            // Selected mount icon remains data-driven; never imply a weapon on a support slot.
            String mountIcon = installed==null
                    ? iconForModule("",selected.mount())
                    : iconForModule(installed.component,selected.mount());
            uiIcons.draw(batch,mountIcon,1218,357,23);
            uiIcons.draw(batch, "energy", 1213,148,22);
        }
        font.setColor(Color.WHITE);
        font.draw(batch,usage==Usage.CAMPAIGN_REFIT?"CAMPAIGN REFIT #"+refitShipId:
                usage==Usage.CAMPAIGN?"CAMPAIGN SHIPYARD":
                usage==Usage.SKIRMISH?"SKIRMISH SHIPYARD":"DESIGN WORKSHOP",22,702);
        font.setColor(team==0?Color.CYAN:Color.LIGHT_GRAY);font.draw(batch,"LEFT FLEET",35,660);
        if(!playerOnly){
            font.setColor(team==1?Color.CYAN:Color.LIGHT_GRAY);
            font.draw(batch,"RIGHT FLEET",175,660);
        }
        font.setColor(Color.WHITE);
        if(uiIcons == null || !uiIcons.has("back"))font.draw(batch,"<",345,660);
        font.draw(batch,draft.hull.name().replace('_',' '),392,660);
        var lineage=ShipLineages.of(draft.hull);
        font.setColor(Color.GOLD);
        font.draw(batch,"<",351,619);
        font.draw(batch,"GEN "+lineage.generation()+" / "+lineage.ethos()+
                " / "+lineage.role().name(),386,619);
        font.draw(batch,">",668,619);
        font.setColor(Color.WHITE);
        if(uiIcons == null || !uiIcons.has("next"))font.draw(batch,">",657,660);
        font.draw(batch,playerOnly?(usage==Usage.CAMPAIGN_REFIT?"QUOTE":"BLUEPRINT"):"APPLY",
                playerOnly?716:745,660);font.draw(batch,"SAVE",833,660);
        font.draw(batch,"LOAD",940,660);
        if(playerOnly){
            var quote=campaignQuote();
            font.setColor(quote.allowed()?Color.GREEN:Color.SCARLET);
            font.draw(batch,purchaseArmedDesign==draft&&
                    purchaseArmedCredits==quote.wallet()&&purchaseArmedCost==quote.cost()
                    ?"CONFIRM":usage==Usage.CAMPAIGN_REFIT?"REFIT":"PURCHASE",1022,660);
            font.setColor(Color.GOLD);
            font.draw(batch,"WALLET "+quote.wallet()+" cr   "+
                    (usage==Usage.CAMPAIGN_REFIT?"REFIT ":"NEW HULL ")+
                    quote.cost()+" cr   AFTER "+quote.remaining()+" cr",440,703);
            font.setColor(quote.allowed()?Color.GREEN:Color.GOLD);
            font.draw(batch,shortText(quote.reason(),58),815,612);
        }else font.draw(batch,"FACTION >",1023,660);
        font.draw(batch,"CLOSE",1174,660);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"HARDPOINTS ("+draft.slots()+")",38,590);
        font.draw(batch,artPreview?"HULL ART PREVIEW":"FACTION HULL SCHEMATIC",345,590);
        font.setColor(Color.GOLD);
        font.draw(batch,artPreview?"SHOW MAP":"SHOW ART",705,590);
        font.draw(batch,"MODULES",813,590);
        font.setColor(Color.CYAN);
        font.draw(batch,"TECH LOADOUT >",1004,590);
        for(int n=0;n<SLOT_ROWS;n++) {
            int index=scroll+n;
            if(index>=draft.slots())break;
            Shipyard.Slot slot=layout.get(index);
            Shipyard.Installed part=draft.at(index);
            font.setColor(index==selectedSlot?Color.GOLD:mountColor(slot.mount()));
            String name=part==null?"EMPTY":part.component.substring("BASE-".length());
            font.draw(batch,String.format(Locale.ROOT,"%02d  %-7s %s",index+1,slot.mount().name(),name),36,580-n*26);
        }
        font.setColor(draft.faction.r,draft.faction.g,draft.faction.b,1);
        font.draw(batch,draft.faction.displayName,346,555);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"SYS "+count(layout,Shipyard.Mount.SYSTEM)+
                "  WPN "+count(layout,Shipyard.Mount.WEAPON)+
                "  DEF "+count(layout,Shipyard.Mount.DEFENSE)+
                "  SUP "+count(layout,Shipyard.Mount.SUPPORT)+
                "  HAB "+count(layout,Shipyard.Mount.HABITAT),343,100);
        if(installed!=null){
            ProjectileModel.Spec projectile=Shipyard.projectileAt(draft,catalog,selectedSlot);
            ProjectileModel.Defense defense=Shipyard.defenseAt(draft,catalog,selectedSlot);
            font.setColor(Color.GOLD);
            if(installed.component.endsWith("-FTR")){
                FighterModel.Profile profile=FighterModel.forFaction(draft.fighterHull,draft.faction);
                font.draw(batch,"FIGHTER: "+profile.hull()+"  [K / TAP TO CYCLE]",342,209);
                font.draw(batch,"WEAPON "+profile.weapon()+"  HP "+units(profile.health())+
                        "  ARMOR "+units(profile.armor()),342,158);
                font.draw(batch,"SCAN "+units(profile.sensors())+"  SPEED "+units(profile.speed())+
                        "  AMMO "+profile.ammunition(),342,137);
            }else if(projectile!=null){
                font.draw(batch,String.format(Locale.ROOT,
                        "PROJECTILE SPEED %.0f  |  HEALTH %.2f",projectile.speed(),
                        projectile.health()),342,158);
                font.draw(batch,String.format(Locale.ROOT,
                        "CROSS SECTION %.2f  |  SIGNATURE %.2f",projectile.crossSection(),
                        projectile.signature()),342,137);
                var ammo=Shipyard.ammunitionAt(draft,selectedSlot);
                String enabled=installed.component.endsWith("-KIN")
                        ?ammo.kinetics().toString()
                        :installed.component.endsWith("-MSL")
                        ?ammo.missiles().toString():ammo.torpedoes().toString();
                font.setColor(Color.CYAN);
                String selectedAmmo=installed.component.endsWith("-KIN")
                        ?draft.ammoSelection.kinetic().name()
                        :installed.component.endsWith("-MSL")
                        ?draft.ammoSelection.missile().name()
                        :draft.ammoSelection.torpedo().name();
                font.draw(batch,shortText("A / TAP AMMO: "+selectedAmmo+
                        "  AVAILABLE "+enabled,64),342,116);
                font.setColor(Color.GOLD);
            }else if(defense!=null){
                font.draw(batch,String.format(Locale.ROOT,
                        "PD TRACKING x%.2f  |  PD DAMAGE %.2f",
                        defense.tracking(),defense.damage()),342,158);
                font.draw(batch,String.format(Locale.ROOT,
                        "SCAN REACH %.0f u  |  SENSOR x%.2f",
                        Shipyard.pointDefenseRangeAt(draft,catalog,selectedSlot),
                        defense.detectionMultiplier()),342,137);
            }else {
                ModuleBranchEffects.Tuning tuning=Shipyard.tuningAt(draft,catalog,selectedSlot);
                String kind=catalog.base(installed.component).subsystem;
                String heading=switch(kind){
                    case "REP" -> String.format(Locale.ROOT,
                            "SELF REPAIR x%.2f  |  ALLY REPAIR x%.2f",
                            tuning.selfRepair(),tuning.remoteRepair());
                    case "SHD" -> String.format(Locale.ROOT,
                            "SHIELD CHARGE x%.2f  |  TRANSFER RANGE x%.2f",
                            tuning.shieldTransfer(),tuning.reach());
                    case "DRN" -> tuning.remoteRepair()>1
                            ?"SPECIALIST SERVICE / REPAIR DRONES"
                            :"INTERCEPTOR / POINT DEFENSE DRONES";
                    case "LAS","PLA","PAR" -> String.format(Locale.ROOT,
                            "DAMAGE x%.2f  |  RANGE x%.2f",
                            tuning.damage(),tuning.reach());
                    default -> String.format(Locale.ROOT,
                            "OUTPUT x%.2f  |  RANGE x%.2f",
                            tuning.damage(),tuning.reach());
                };
                font.draw(batch,heading,342,158);
                font.draw(batch,String.format(Locale.ROOT,
                        "CYCLE x%.2f  TRACK x%.2f  EFFICIENCY x%.2f",
                        tuning.cycle(),tuning.tracking(),tuning.supportEfficiency()),342,137);
            }
        }
        if(!artPreview && draft.slots()<=18){
            font.setColor(Color.LIGHT_GRAY);
            for(Shipyard.Slot slot:layout){
                font.draw(batch,""+(slot.index()+1),MAP_CX+slot.x()*MAP_SCALE+10,
                                        MAP_CY+slot.y()*MAP_SCALE+6);
            }
        }else if(!artPreview){
            font.setColor(Color.GOLD);
            font.draw(batch,"#"+(selectedSlot+1),MAP_CX+selected.x()*MAP_SCALE+13,
                                            MAP_CY+selected.y()*MAP_SCALE+5);
        }
        List<Component> options=Shipyard.choices(catalog,draft.faction,selected.mount());
        font.setColor(Color.LIGHT_GRAY);font.draw(batch,"CLEAR MOUNT",813,564);
        for(int n=0;n<options.size();n++){
            Component c=options.get(n);
            if(uiIcons!=null && n<8)
                uiIcons.draw(batch,iconForModule(c.name,selected.mount()),1220,533-n*25,21);
            font.setColor(installed!=null&&c.id.equals(installed.component)?Color.GOLD:Color.WHITE);
            font.draw(batch,shortText(c.name,34),813,564-(n+1)*25);
        }
        Shipyard.Track activeResearch=installed==null?null:installed.branch();
        font.setColor(Color.WHITE);
        font.draw(batch,"MODULE SPECIALIZATION - "+selected.mount()+" #"+(selectedSlot+1),813,372);
        for(int n=0;n<Shipyard.Track.values().length;n++){
            Shipyard.Track track=Shipyard.Track.values()[n];
            int level=installed==null?0:installed.stage(track);
            boolean locked=activeResearch!=null && activeResearch!=track;
            font.setColor(locked?Color.GRAY:activeResearch==track?Color.GOLD:Color.WHITE);
            font.draw(batch,track.name()+(locked?" [LOCKED]":activeResearch==track?" [ACTIVE]":""),813,341-n*30);
            font.draw(batch,locked?"---":("-   "+level+" / 6   +"),1097,341-n*30);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"MASS  "+units(report.mass())+" / "+units(draft.hull.massCapacity)+" t",813,209);
        font.draw(batch,"VOLUME "+units(report.volume())+" / "+units(draft.hull.volumeCapacity),813,185);
        font.draw(batch,"POWER "+units(report.demand())+" / "+units(report.generation())+" MW",813,161);
        font.draw(batch,"HEAT  "+units(report.generatedHeat())+" / "+units(report.cooling())+" MW",813,137);
        font.draw(batch,"BUILD COST "+report.industry()+" | TECH VALUE "+report.research()+
                (usage==Usage.CAMPAIGN?" | LICENSE "+campaignRank+"/6":""),813,112);
        if(installed!=null){
            font.setColor(Color.GOLD);
            font.draw(batch,"V: "+installed.pattern.name().replace('_',' '),813,84);
            font.draw(batch,"B: Mk "+installed.mark+" / 4",1087,84);
        }else{
            font.setColor(Color.GRAY);
            font.draw(batch,"V: HARDWARE PATTERN     B: GENERATION",813,84);
        }
        font.setColor(report.valid()?Color.GREEN:Color.SCARLET);
        font.draw(batch,shortText(report.explanation(),74),331,61);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,shortText(notice,130),29,38);
        batch.end();
    }
}
