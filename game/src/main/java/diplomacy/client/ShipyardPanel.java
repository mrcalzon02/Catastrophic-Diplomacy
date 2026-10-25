package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullLayouts;
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
    private final BiConsumer<Integer,Shipyard.Design> deploy;
    private Shipyard.Design[] active = new Shipyard.Design[2];
    private Shipyard.Design draft;
    private int team, selectedSlot, scroll;
    private String notice="Select a hardpoint, choose machinery, then APPLY to deploy.";
    private boolean open;
    private boolean artPreview;
    private final TouchGesture gesture = new TouchGesture();
    private boolean scrollGesture;

    ShipyardPanel(TechnologyCatalog tech, BiConsumer<Integer,Shipyard.Design> deployment,
                  Shipyard.Design first, Shipyard.Design second,TerranPlaceholderSprites art) {
        catalog=tech;deploy=deployment;fleetArt=art;active[0]=first;active[1]=second;
        draft=first;
    }
    boolean open() { return open; }
    void show() { open=true; draft=active[team]; selectedSlot=0; scroll=0; artPreview=false; }
    void close() { open=false; gesture.cancel(); }
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
        draft=Shipyard.starter(draft.faction,next);
        selectedSlot=0;scroll=0;artPreview=false;notice="New "+next+" frame; stock equipment installed";
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
            draft=loaded;selectedSlot=0;scroll=0;artPreview=false;notice="Loaded "+file.path()+"; APPLY to deploy";
        }catch(RuntimeException error){notice="Load refused: "+error.getMessage();}
    }
    private void apply(){
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        if(!report.valid()){notice="Cannot deploy: "+report.explanation();return;}
        active[team]=draft;
        deploy.accept(team,draft);
        notice="Battle fleet now uses fitted "+draft.hull+"s";
        open=false;
    }
    boolean key(int code){
        if(!open)return false;
        if(code==com.badlogic.gdx.Input.Keys.ESCAPE||code==com.badlogic.gdx.Input.Keys.TAB){close();return true;}
        if(code==com.badlogic.gdx.Input.Keys.ENTER){apply();return true;}
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
            if(x<310){side(1);return true;}
            if(x>=338&&x<377){hull(-1);return true;}
            if(x>=647&&x<699){hull(1);return true;}
            if(x>=705&&x<810){apply();return true;}
            if(x>=819&&x<910){save();return true;}
            if(x>=923&&x<1017){load();return true;}
            if(x>=1018&&x<=1120){faction(1);return true;}
            if(x>1120){close();return true;}
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
            if(index==0){draft=draft.install(selectedSlot,null);notice="Mount cleared";return true;}
            if(index>0&&index<=options.size()){
                Component part=options.get(index-1);
                draft=draft.install(selectedSlot,part.id);
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
                    int level=Math.max(0,Math.min(6,installed.stage(track)+delta));
                    draft=draft.stage(selectedSlot,track,level);
                    notice=track+" set to stage "+level;
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
        if(artPreview&&fleetArt!=null)
            fleetArt.drawPreview(batch,draft.faction,draft.hull,MAP_CX,MAP_CY,330,402);
        font.setColor(Color.WHITE);font.draw(batch,"SHIPYARD",22,702);
        font.setColor(team==0?Color.CYAN:Color.LIGHT_GRAY);font.draw(batch,"LEFT FLEET",35,660);
        font.setColor(team==1?Color.CYAN:Color.LIGHT_GRAY);font.draw(batch,"RIGHT FLEET",175,660);
        font.setColor(Color.WHITE);
        font.draw(batch,"<",345,660);
        font.draw(batch,draft.hull.name().replace('_',' '),392,660);
        font.draw(batch,">",657,660);
        font.draw(batch,"APPLY",711,660);font.draw(batch,"SAVE",833,660);
        font.draw(batch,"LOAD",940,660);font.draw(batch,"FACTION >",1023,660);font.draw(batch,"CLOSE",1140,660);
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
                "  SUP "+count(layout,Shipyard.Mount.SUPPORT),343,100);
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
            font.setColor(installed!=null&&c.id.equals(installed.component)?Color.GOLD:Color.WHITE);
            font.draw(batch,shortText(c.name,34),813,564-(n+1)*25);
        }
        font.setColor(Color.WHITE);
        font.draw(batch,"UPGRADES - "+selected.mount()+" #"+(selectedSlot+1),813,372);
        for(int n=0;n<Shipyard.Track.values().length;n++){
            Shipyard.Track track=Shipyard.Track.values()[n];
            int level=installed==null?0:installed.stage(track);
            font.draw(batch,track.name(),813,341-n*30);
            font.draw(batch,"-   "+level+" / 6   +",1097,341-n*30);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"MASS  "+units(report.mass())+" / "+units(draft.hull.massCapacity)+" t",813,209);
        font.draw(batch,"VOLUME "+units(report.volume())+" / "+units(draft.hull.volumeCapacity),813,185);
        font.draw(batch,"POWER "+units(report.demand())+" / "+units(report.generation())+" MW",813,161);
        font.draw(batch,"HEAT  "+units(report.generatedHeat())+" / "+units(report.cooling())+" MW",813,137);
        font.draw(batch,"COST "+report.industry()+" | RESEARCH "+report.research(),813,112);
        font.setColor(report.valid()?Color.GREEN:Color.SCARLET);
        font.draw(batch,shortText(report.explanation(),74),331,61);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,shortText(notice,130),29,38);
        batch.end();
    }
}
