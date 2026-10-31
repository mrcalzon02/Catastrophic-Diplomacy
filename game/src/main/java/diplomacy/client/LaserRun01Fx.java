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
    private record Shot(Battle.VisualEvent event,int atlas) { }
    private final List<Shot> shots=new ArrayList<>();
    private final Map<Integer,Texture> textures=new HashMap<>();
    private final Map<Integer,TextureRegion[]> regions=new HashMap<>();
    private final boolean[] checked=new boolean[IDS.length],exists=new boolean[IDS.length];
    private Battle tracked;
    private long lastSequence;

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
        if(tracked!=battle){tracked=battle;lastSequence=0;shots.clear();}
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.sequence()<=lastSequence)continue;
            lastSequence=event.sequence();
            if(event.phase()!=Battle.VisualPhase.FIRE)continue;
            LaserFamilies.Type family=LaserFamilies.fromWeapon(event.weapon());
            if(family==null)continue;
            int selected=effectIndex(family,event.shotId());
            if(frameAt(selected,battle.tick-event.tick())>=0&&available(selected))
                shots.add(new Shot(event,selected));
        }
        for(Iterator<Shot> it=shots.iterator();it.hasNext();){
            Shot shot=it.next();
            if(frameAt(shot.atlas(),battle.tick-shot.event().tick())<0)it.remove();
        }
        if(shots.size()>MAX_SHOTS)
            shots.subList(0,shots.size()-MAX_SHOTS).clear();
    }
    private TextureRegion[] load(int atlas){
        TextureRegion[] cached=regions.get(atlas);
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
            TextureRegion[] frames=new TextureRegion[FRAMES[atlas]];
            for(int i=0;i<frames.length;i++)
                frames[i]=new TextureRegion(image,(i%COLUMNS)*w,(i/COLUMNS)*h,w,h);
            textures.put(atlas,image);regions.put(atlas,frames);
            return frames;
        }catch(RuntimeException error){
            exists[atlas]=false;
            Gdx.app.error("LASER-VFX","Rejected invalid authored laser atlas "+path(atlas),error);
            return null;
        }
    }
    /** Caller owns SpriteBatch.begin/end; artwork is anchored at the true muzzle. */
    void draw(SpriteBatch batch,Battle battle,CombatViewBounds view){
        if(shots.isEmpty())return;
        batch.setColor(1,1,1,1);
        // Group by atlas to limit texture binds during large fleet battles.
        for(int index=0;index<IDS.length;index++){
            boolean active=false;
            for(Shot shot:shots)if(shot.atlas()==index){active=true;break;}
            if(!active)continue;
            TextureRegion[] frames=load(index);
            if(frames==null)continue;
            for(Shot shot:shots){
                if(shot.atlas()!=index)continue;
                Battle.VisualEvent event=shot.event();
                int frame=frameAt(index,battle.tick-event.tick());
                if(frame<0)continue;
                if(view!=null&&!view.segment(event.x(),event.y(),
                        event.targetX(),event.targetY(),8))continue;
                float dx=(float)(event.targetX()-event.x());
                float dy=(float)(event.targetY()-event.y());
                float span=(float)StrictMath.hypot(dx,dy);
                if(span<0.01f)continue;
                float width=span*512f/430f;
                float height=CombatEffectScale.of(21f);
                float originX=width*35f/512f,originY=height*.5f;
                float rotation=(float)StrictMath.toDegrees(StrictMath.atan2(dy,dx));
                batch.draw(frames[frame],(float)event.x()-originX,
                        (float)event.y()-originY,originX,originY,
                        width,height,1f,1f,rotation);
            }
        }
        batch.setColor(1,1,1,1);
    }
    int activeShots(){return shots.size();}
    int loadedAtlases(){return textures.size();}
    @Override public void dispose(){
        for(Texture value:textures.values())value.dispose();
        textures.clear();regions.clear();shots.clear();
        java.util.Arrays.fill(checked,false);
        java.util.Arrays.fill(exists,false);
        tracked=null;lastSequence=0;
    }
}
