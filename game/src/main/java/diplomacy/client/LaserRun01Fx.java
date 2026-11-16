package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.Battle;
import diplomacy.sim.LaserFamilies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays the ten authored Library laser sequences against authoritative FIRE
 * events, not interpolated fake hits. The source archive remains byte-exact in
 * Git; generated Android atlases may be reduced in resolution for GPU memory.
 */
final class LaserRun01Fx implements Disposable {
    static final String ROOT="effects/lasers/run01/atlases/";
    static final String[] IDS={
        "01_pulse_laser","02_rapid_repeater","03_heavy_cannon",
        "04_cutting_beam","05_stuttering_burst","06_sniper_laser",
        "07_tri_beam","08_sweeping_laser","09_prism_split",
        "10_overcharged_flash"
    };
    static final int[] FRAMES={32,52,48,50,54,44,48,52,50,58};
    static final int COLUMNS=8;
    private static final int MAX_SHOTS=384;
    private record Frame(TextureRegion region,float left,float bottom,float width,float height){}
    private record Shot(Battle.VisualEvent event,int atlas) { }
    private final List<Shot> shots=new ArrayList<>();
    private final VisualAtlasBuckets<Shot> groupedShots=new VisualAtlasBuckets<>(IDS.length);
    private boolean groupsDirty=true;
    private final Map<Integer,Texture> textures=new HashMap<>();
    private final Map<Integer,Frame[]> regions=new HashMap<>();
    private final boolean[] checked=new boolean[IDS.length],exists=new boolean[IDS.length];
    private Battle tracked;
    private long lastSequence;
    private long processedTick=Long.MIN_VALUE;

    /** Two deliberately distinct Library animations per modeled laser family. */
    static int effectIndex(LaserFamilies.Type family,long shotId){
        if(family==null)throw new IllegalArgumentException("Missing laser family");
        boolean second=(shotId&1L)!=0;
        return switch(family){
            case PULSE -> second?9:0;       // pulse / overcharged flash
            case RAPID -> second?4:1;       // rapid repeater / stuttering burst
            case LANCE -> second?5:2;       // heavy cannon / sniper
            case OVERDRIVE -> second?7:3;   // cutting / sweeping
            case NEEDLE -> second?8:6;      // tri-beam / prism split
        };
    }
    static int frameAt(int atlas,long age){
        return atlas<0||atlas>=FRAMES.length||age<0||age>=FRAMES[atlas]?-1:(int)age;
    }
    static String path(int atlas){
        if(atlas<0||atlas>=IDS.length)throw new IllegalArgumentException("Bad FX index");
        return ROOT+IDS[atlas]+".png";
    }
    private boolean available(int atlas){
        if(!checked[atlas]){
            checked[atlas]=true;
            exists[atlas]=Gdx.files.internal(path(atlas)).exists();
        }
        return exists[atlas];
    }
    /** Skip the short legacy strip only where an authored sequence is present. */
    boolean handles(Battle.Beam beam,long tick){
        if(beam.pointDefense||beam.type!=Battle.DamageType.LASER)return false;
        int atlas=effectIndex(beam.laserType,beam.visualId);
        return frameAt(atlas,tick-beam.spawnTick)>=0&&available(atlas);
    }
    boolean handlesFire(Battle.VisualEvent event){
        if(event.phase()!=Battle.VisualPhase.FIRE)return false;
        LaserFamilies.Type family=LaserFamilies.fromWeapon(event.weapon());
        return family!=null&&available(effectIndex(family,event.shotId()));
    }
    void sync(Battle battle){
        if(tracked!=battle||battle.tick<processedTick){
            tracked=battle;
            lastSequence=0;
            shots.clear();
            groupsDirty=true;
            processedTick=Long.MIN_VALUE;
        }
        List<Battle.VisualEvent> history=battle.visualEvents();
        if(!CombatEventCursor.needsRefresh(processedTick,lastSequence,
                battle.tick,history))return;
        processedTick=battle.tick;
        for(int i=CombatEventCursor.firstUnseen(history,lastSequence);
                i<history.size();i++){
            Battle.VisualEvent event=history.get(i);
            lastSequence=event.sequence();
            if(event.phase()!=Battle.VisualPhase.FIRE)continue;
            LaserFamilies.Type family=LaserFamilies.fromWeapon(event.weapon());
            if(family==null)continue;
            int selected=effectIndex(family,event.shotId());
            if(frameAt(selected,battle.tick-event.tick())>=0&&available(selected)){
                shots.add(new Shot(event,selected));
                groupsDirty=true;
            }
        }
        for(Iterator<Shot> it=shots.iterator();it.hasNext();){
            Shot shot=it.next();
            if(frameAt(shot.atlas(),battle.tick-shot.event().tick())<0){
                it.remove();
                groupsDirty=true;
            }
        }
        if(shots.size()>MAX_SHOTS){
            shots.subList(0,shots.size()-MAX_SHOTS).clear();
            groupsDirty=true;
        }
    }
    private Frame[] load(int atlas){
        Frame[] cached=regions.get(atlas);
        if(cached!=null)return cached;
        if(!available(atlas))return null;
        try{
            Texture image=new Texture(Gdx.files.internal(path(atlas)));
            int w=image.getWidth()/COLUMNS,h=w*160/512;
            if(image.getWidth()!=w*COLUMNS || (w!=512&&w!=256)
                    || image.getHeight()!=((FRAMES[atlas]+COLUMNS-1)/COLUMNS)*h){
                image.dispose();
                throw new IllegalArgumentException("Unexpected laser atlas cell geometry");
            }
            image.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            Frame[] frames=frames(image,atlas,w,h);
            textures.put(atlas,image);regions.put(atlas,frames);
            return frames;
        }catch(RuntimeException error){
            exists[atlas]=false;
            Gdx.app.error("LASER-VFX","Rejected invalid authored laser atlas "+path(atlas),error);
            return null;
        }
    }
    private Frame[] frames(Texture image,int atlas,int w,int h){
        int count=FRAMES[atlas];
        int[] bounds=readBounds(atlas,w,h,count);
        Frame[] result=new Frame[count];
        for(int i=0;i<count;i++){
            int x=bounds==null?0:bounds[i*4],y=bounds==null?0:bounds[i*4+1];
            int width=bounds==null?w:bounds[i*4+2],height=bounds==null?h:bounds[i*4+3];
            if(width==0||height==0)continue;
            TextureRegion region=new TextureRegion(image,(i%COLUMNS)*w+x,(i/COLUMNS)*h+y,width,height);
            result[i]=new Frame(region,x/(float)w,1-(y+height)/(float)h,width/(float)w,height/(float)h);
        }
        return result;
    }
    /** Missing or incompatible optional metadata keeps the complete authored
     * cell drawable. The source atlas remains the sole source of visual pixels. */
    private int[] readBounds(int atlas,int w,int h,int count){
        var file=Gdx.files.internal(path(atlas)+".bounds");
        if(!file.exists())return null;
        try(var input=new java.io.DataInputStream(file.read(4096))){
            if(input.readInt()!=w||input.readInt()!=h)
                throw new java.io.IOException("Atlas cell size mismatch");
            int[] bounds=new int[count*4];
            for(int i=0;i<bounds.length;i+=4){
                int x=input.readUnsignedShort(),y=input.readUnsignedShort();
                int width=input.readUnsignedShort(),height=input.readUnsignedShort();
                if(x+width>w||y+height>h)
                    throw new java.io.IOException("Frame exceeds atlas cell");
                bounds[i]=x;bounds[i+1]=y;bounds[i+2]=width;bounds[i+3]=height;
            }
            if(input.read()!=-1)throw new java.io.IOException("Unexpected extra frame bounds");
            return bounds;
        }catch(java.io.IOException|com.badlogic.gdx.utils.GdxRuntimeException unavailable){
            Gdx.app.log("LASER-VFX","Using full frames for "+IDS[atlas]+": "+unavailable.getMessage());
            return null;
        }
    }
    /** Caller owns SpriteBatch.begin/end; artwork is anchored at the true muzzle. */
    void draw(SpriteBatch batch,Battle battle,CombatViewBounds view){
        if(shots.isEmpty())return;
        if(groupsDirty){
            groupedShots.rebuild(shots,Shot::atlas);
            groupsDirty=false;
        }
        batch.setColor(1,1,1,1);
        // Draw each grouped shot once per frame, retaining atlas painter order.
        for(int index=0;index<IDS.length;index++){
            List<Shot> group=groupedShots.atlas(index);
            if(group.isEmpty())continue;
            Frame[] frames=load(index);
            if(frames==null)continue;
            for(Shot shot:group){
                Battle.VisualEvent event=shot.event();
                int frame=frameAt(index,battle.tick-event.tick());
                if(frame<0||frames[frame]==null)continue;
                if(view!=null&&!view.segment(event.x(),event.y(),
                        event.targetX(),event.targetY(),8))continue;
                float dx=(float)(event.targetX()-event.x());
                float dy=(float)(event.targetY()-event.y());
                float span=(float)StrictMath.hypot(dx,dy);
                if(span<0.01f)continue;
                float width=span*512f/430f;
                float height=CombatEffectScale.of(21f);
                Frame shown=frames[frame];
                float originX=width*(35f/512f-shown.left),originY=height*(.5f-shown.bottom);
                float rotation=(float)StrictMath.toDegrees(StrictMath.atan2(dy,dx));
                batch.draw(shown.region,(float)event.x()-originX,
                        (float)event.y()-originY,originX,originY,
                        width*shown.width,height*shown.height,1f,1f,rotation);
            }
        }
        batch.setColor(1,1,1,1);
    }
    int activeShots(){return shots.size();}
    int loadedAtlases(){return textures.size();}
    @Override public void dispose(){
        for(Texture value:textures.values())value.dispose();
        textures.clear();regions.clear();shots.clear();
        groupedShots.clear();groupsDirty=true;
        java.util.Arrays.fill(checked,false);
        java.util.Arrays.fill(exists,false);
        tracked=null;lastSequence=0;processedTick=Long.MIN_VALUE;
    }
}
