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
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.sim.Battle;
import diplomacy.shipyard.TouchGesture;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Compact, touch-and-mouse compatible single-screen ship fitting interface. */
final class ShipyardPanel {
    private static final int SLOT_ROWS=18;
    private static final float MAP_CX=563f, MAP_CY=343f, MAP_SCALE=2.65f;
    private final TechnologyCatalog catalog;
    private final TerranPlaceholderSprites fleetArt;
    private TacticalUiIcons uiIcons;
    private final BiConsumer<Integer,Shipyard.Design> deploy;
    private Shipyard.Design[] active = new Shipyard.Design[2];
    private Shipyard.Design draft;
    private int team, selectedSlot, scroll;
    private String notice="Select a hardpoint, choose machinery, then APPLY to deploy.";
    private boolean open;
    enum Usage { WORKSHOP, SKIRMISH, CAMPAIGN }
    private Usage usage=Usage.WORKSHOP;
    private int campaignRank=6,unlockedMission=1;
    private boolean playerOnly;
    Usage usage(){return usage;}
    private boolean artPreview;
    private final TouchGesture gesture = new TouchGesture();
    private boolean scrollGesture;

    ShipyardPanel(TechnologyCatalog tech, BiConsumer<Integer,Shipyard.Design> deployment,
                  Shipyard.Design first, Shipyard.Design second,TerranPlaceholderSprites art) {
        catalog=tech;deploy=deployment;fleetArt=art;active[0]=first;active[1]=second;
        draft=first;
    }
    void setIcons(TacticalUiIcons icons) { uiIcons = icons; }
    boolean open() { return open; }
    void show() {
        open=true;usage=Usage.WORKSHOP;playerOnly=false;
        draft=active[team];selectedSlot=0;scroll=0;artPreview=false;
        notice="FREE DESIGN WORKSHOP: all factions and technology available.";
    }
    void showSkirmish(){
        show();usage=Usage.SKIRMISH;
        notice="SKIRMISH: all factions, hull generations and research permitted.";
    }
    /** Campaign engineering edits a purchase blueprint, NOT an owned ship for free. */
    void showCampaignFleet(int researchRank,int mission){
        if(researchRank<0||researchRank>6)
            throw new IllegalArgumentException("Invalid campaign license");
        show();side(0);usage=Usage.CAMPAIGN;playerOnly=true;
        campaignRank=researchRank;unlockedMission=mission;
        notice="CAMPAIGN BLUEPRINT: APPLY queues design for paid purchase at dock.";
    }
    void close() { open=false; playerOnly=false; gesture.cancel(); }
    void sync(int side,Shipyard.Design design) {
        active[side]=design;
        if(side==team){draft=design;selectedSlot=0;scroll=0;}
    }
    private void side(int side){team=side;draft=active[side];selectedSlot=0;scroll=0;artPreview=false;}
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
        if(usage==Usage.CAMPAIGN&&!campaignPermitted())return;
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
            if(usage==Usage.CAMPAIGN)
                CampaignShipyardRules.requireAllowed(loaded,active[0].faction,
                        campaignRank,unlockedMission);
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
            CampaignShipyardRules.requireAllowed(draft,active[0].faction,
                    campaignRank,unlockedMission);
            return true;
        }catch(IllegalArgumentException denied){
            notice="LOCKED: "+denied.getMessage();return false;
        }
    }
    private void apply(){
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        if(!report.valid()){notice="Cannot approve: "+report.explanation();return;}
        if(usage==Usage.CAMPAIGN&&!campaignPermitted())return;
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
        if(code==com.badlogic.gdx.Input.Keys.K){cycleFighter();return true;}
        if(code==com.badlogic.gdx.Input.Keys.V){cyclePattern();return true;}
        if(code==com.badlogic.gdx.Input.Keys.B){cycleMark();return true;}
        if(code==com.badlogic.gdx.Input.Keys.A){cycleAmmunition();return true;}
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
        if(y>638){
            if(x<160){side(0);return true;}
            if(x<310){if(!playerOnly)side(1);return true;}
            if(x>=338&&x<377){hull(-1);return true;}
            if(x>=647&&x<699){hull(1);return true;}
            if(x>=705&&x<810){apply();return true;}
            if(x>=819&&x<910){save();return true;}
            if(x>=923&&x<1017){load();return true;}
            if(x>=1018&&x<=1120){if(!playerOnly)faction(1);return true;}
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
                        if(usage==Usage.CAMPAIGN&&level>campaignRank){
                            notice="LOCKED: purchase research rank "+level+" first";return true;
                        }
                        try{
                            draft=draft.stage(selectedSlot,track,level);
                            notice=level==0
                                    ?"Module research reset; choose any one technology branch"
                                    :track+" research stage "+level+" / 6 (other branches locked)";
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
    void draw(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font) {
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        List<Shipyard.Slot> layout=draft.placements();
        Shipyard.Slot selected=layout.get(selectedSlot);
        Shipyard.Installed installed=draft.at(selectedSlot);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rectangle(shapes,0,0,1280,720,new Color(0.018f,0.029f,0.05f,1));
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
            if(!playerOnly)uiIcons.drawButton(batch,"special",1019,642,109,42);
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
        font.draw(batch,usage==Usage.CAMPAIGN?"CAMPAIGN SHIPYARD":
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
        font.draw(batch,"GEN "+lineage.generation()+" / "+lineage.ethos()+
                " / "+lineage.role().name(),343,619);
        font.setColor(Color.WHITE);
        if(uiIcons == null || !uiIcons.has("next"))font.draw(batch,">",657,660);
        font.draw(batch,"APPLY",745,660);font.draw(batch,"SAVE",833,660);
        font.draw(batch,"LOAD",940,660);
        if(!playerOnly)font.draw(batch,"FACTION >",1023,660);
        font.draw(batch,"CLOSE",1174,660);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"HARDPOINTS ("+draft.slots()+")",38,590);
        font.draw(batch,artPreview?"HULL ART PREVIEW":"FACTION HULL SCHEMATIC",345,590);
        font.setColor(Color.GOLD);
        font.draw(batch,artPreview?"SHOW MAP":"SHOW ART",705,590);
        font.draw(batch,"COMPATIBLE MODULES",813,590);
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
        font.draw(batch,"EXCLUSIVE RESEARCH - "+selected.mount()+" #"+(selectedSlot+1),813,372);
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
        font.draw(batch,"COST "+report.industry()+" | RESEARCH "+report.research()+
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
