package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.TouchGesture;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Compact, touch-and-mouse compatible single-screen ship fitting interface. */
final class ShipyardPanel {
    private static final int SLOT_ROWS=18;
    private final TechnologyCatalog catalog;
    private final BiConsumer<Integer,Shipyard.Design> deploy;
    private Shipyard.Design[] active = new Shipyard.Design[2];
    private Shipyard.Design draft;
    private int team, selectedSlot, scroll;
    private String notice="Select a hardpoint, choose machinery, then APPLY to deploy.";
    private boolean open;
    private final TouchGesture gesture = new TouchGesture();
    private boolean scrollGesture;

    ShipyardPanel(TechnologyCatalog tech, BiConsumer<Integer,Shipyard.Design> deployment,
                  Shipyard.Design first, Shipyard.Design second) {
        catalog=tech;deploy=deployment;active[0]=first;active[1]=second;
        draft=first;
    }
    boolean open() { return open; }
    void show() { open=true; draft=active[team]; selectedSlot=0; scroll=0; }
    void close() { open=false; gesture.cancel(); }
    void sync(int side,Shipyard.Design design) {
        active[side]=design;
        if(side==team){draft=design;selectedSlot=0;scroll=0;}
    }
    private void side(int side){team=side;draft=active[side];selectedSlot=0;scroll=0;}
    private void hull(int step){
        Shipyard.Hull[] options=Shipyard.Hull.values();
        Shipyard.Hull next=options[Math.floorMod(draft.hull.ordinal()+step,options.length)];
        draft=Shipyard.starter(draft.faction,next);
        selectedSlot=0;scroll=0;notice="New "+next+" frame; stock equipment installed";
    }
    private String fileName(){return "shipyard/"+team+"-"+draft.faction.name().toLowerCase(Locale.ROOT)+".ship";}
    private void save(){
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        if(!report.valid()){notice="Cannot save unserviceable ship: "+report.explanation();return;}
        try{Gdx.files.local(fileName()).writeString(Shipyard.encode(draft),false,"UTF-8");notice="Blueprint saved locally: "+fileName();}
        catch(RuntimeException error){notice="Save failed: "+error.getMessage();}
    }
    private void load(){
        try{
            String content=Gdx.files.local(fileName()).readString("UTF-8");
            Shipyard.Design loaded=Shipyard.decode(content,catalog);
            if(loaded.faction!=draft.faction)throw new IllegalArgumentException("Wrong faction");
            draft=loaded;selectedSlot=0;scroll=0;notice="Loaded blueprint; APPLY to deploy";
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
        if(gesture.start(pointer,x,y))scrollGesture=x<370&&y>105&&y<590;
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
    boolean tap(float x,float y){
        if(!open)return false;
        if(y>638){
            if(x<160){side(0);return true;}
            if(x<310){side(1);return true;}
            if(x>=338&&x<377){hull(-1);return true;}
            if(x>=590&&x<635){hull(1);return true;}
            if(x>=695&&x<805){apply();return true;}
            if(x>=819&&x<910){save();return true;}
            if(x>=923&&x<1017){load();return true;}
            if(x>1120){close();return true;}
            return true;
        }
        if(x<370 && y>=105&&y<=590){
            int index=scroll+(int)((590-y)/26);
            if(index>=0&&index<draft.slots()){selectedSlot=index;notice="Slot "+(index+1)+": choose compatible machinery";}
            return true;
        }
        if(x>=390&&x<680&&y>=105&&y<=570){
            int index=(int)((570-y)/27);
            Shipyard.Mount mount=draft.hull.slots.get(selectedSlot).mount();
            List<Component> options=Shipyard.choices(catalog,draft.faction,mount);
            if(index==0){draft=draft.install(selectedSlot,null);notice="Mount cleared";return true;}
            if(index>0&&index<=options.size()){
                Component part=options.get(index-1);
                draft=draft.install(selectedSlot,part.id);
                notice="Installed "+part.name+" (upgrades reset for this mount)";
            }
            return true;
        }
        if(x>=700&&y>=306&&y<=450){
            int trackIndex=(int)((450-y)/35);
            if(trackIndex>=0&&trackIndex<Shipyard.Track.values().length){
                Shipyard.Installed installed=draft.at(selectedSlot);
                if(installed==null){notice="Choose a component first";return true;}
                Shipyard.Track track=Shipyard.Track.values()[trackIndex];
                int delta=x>1135?1:x>1060?-1:0;
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
    void draw(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font) {
        Shipyard.Report report=Shipyard.inspect(draft,catalog);
        Shipyard.Slot selected=draft.hull.slots.get(selectedSlot);
        Shipyard.Installed installed=draft.at(selectedSlot);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rectangle(shapes,0,0,1280,720,new Color(0.018f,0.029f,0.05f,1));
        rectangle(shapes,0,635,1280,85,new Color(0.06f,0.09f,0.15f,1));
        rectangle(shapes,24,83,346,524,new Color(0.045f,0.075f,0.11f,1));
        rectangle(shapes,383,83,299,524,new Color(0.045f,0.075f,0.11f,1));
        rectangle(shapes,694,83,561,524,new Color(0.045f,0.075f,0.11f,1));
        for(int n=0;n<SLOT_ROWS;n++){
            int index=scroll+n;
            if(index>=draft.slots())break;
            if(index==selectedSlot)rectangle(shapes,30,561-n*26,332,25,new Color(0.13f,0.23f,0.33f,1));
        }
        shapes.end();
        batch.begin();font.setColor(Color.WHITE);
        font.draw(batch,"SHIPYARD",22,702);
        font.setColor(team==0?Color.CYAN:Color.LIGHT_GRAY);font.draw(batch,"LEFT FLEET",35,660);
        font.setColor(team==1?Color.CYAN:Color.LIGHT_GRAY);font.draw(batch,"RIGHT FLEET",175,660);
        font.setColor(Color.WHITE);
        font.draw(batch,"<",345,660);
        font.draw(batch,draft.hull.name(),390,660);
        font.draw(batch,">",603,660);
        font.draw(batch,"APPLY",711,660);font.draw(batch,"SAVE",833,660);
        font.draw(batch,"LOAD",940,660);font.draw(batch,"CLOSE",1140,660);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"HARDPOINTS",38,590);
        font.draw(batch,"AVAILABLE MACHINERY",392,590);
        font.draw(batch,"ENGINEERING / UPGRADES",706,590);
        for(int n=0;n<SLOT_ROWS;n++) {
            int index=scroll+n;
            if(index>=draft.slots())break;
            Shipyard.Slot slot=draft.hull.slots.get(index);
            Shipyard.Installed part=draft.at(index);
            font.setColor(index==selectedSlot?Color.GOLD:Color.LIGHT_GRAY);
            String name=part==null?"EMPTY":part.component.substring("BASE-".length());
            font.draw(batch,String.format(Locale.ROOT,"%02d  %-7s %s",index+1,slot.mount().name(),name),39,580-n*26);
        }
        List<Component> options=Shipyard.choices(catalog,draft.faction,selected.mount());
        font.setColor(Color.LIGHT_GRAY);font.draw(batch,"CLEAR MOUNT",397,563);
        for(int n=0;n<options.size();n++){
            Component c=options.get(n);
            font.setColor(installed!=null&&c.id.equals(installed.component)?Color.GOLD:Color.WHITE);
            font.draw(batch,shortText(c.name,27),397,563-(n+1)*27);
        }
        font.setColor(Color.WHITE);
        font.draw(batch,"Selected: "+selected.mount()+" #"+(selectedSlot+1),705,559);
        if(installed!=null){
            Component part=catalog.base(installed.component);
            font.draw(batch,part.name+" / "+part.subsystem,705,532);
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch,"BASE: mass "+units(part.mass)+"t | power "+units(part.power)+"MW | heat "+units(part.heat)+"MW",705,506);
        } else {font.setColor(Color.GRAY);font.draw(batch,"Empty hardpoint",705,527);}
        font.setColor(Color.WHITE);
        for(int n=0;n<Shipyard.Track.values().length;n++){
            Shipyard.Track track=Shipyard.Track.values()[n];
            int level=installed==null?0:installed.stage(track);
            font.draw(batch,track.name(),710,436-n*35);
            font.draw(batch,"-    "+level+" / 6    +",1070,436-n*35);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"MASS  "+units(report.mass())+" / "+units(draft.hull.massCapacity)+" t",708,273);
        font.draw(batch,"VOLUME "+units(report.volume())+" / "+units(draft.hull.volumeCapacity),708,249);
        font.draw(batch,"POWER "+units(report.demand())+" / "+units(report.generation())+" MW",708,225);
        font.draw(batch,"HEAT  "+units(report.generatedHeat())+" / "+units(report.cooling())+" MW",708,201);
        font.draw(batch,"COST "+report.industry()+" | RESEARCH "+report.research(),708,177);
        font.setColor(report.valid()?Color.GREEN:Color.SCARLET);
        font.draw(batch,shortText(report.explanation(),72),706,144);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,shortText(notice,150),29,62);
        font.draw(batch,"SCROLL SLOT LIST | CLICK COMPONENT TO FIT | +/- PER TRACK | APPLY BUILDS FLEET",29,38);
        batch.end();
    }
}
