package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.Battle;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;

/**
 * 40 physically distinct high-resolution missile bodies and 40 independently
 * animated source-derived exhausts. All artwork was isolated from the original
 * Library sheets and reconstructed into two texture atlases at build time.
 *
 * Only the real simulation's motor state controls whether the plume is visible.
 * Drawing is cosmetic; missile position, hitbox, damage and tracking are untouched.
 */
final class OrdnanceLibrarySprites implements Disposable {
    static final String ROOT="effects/ordnance/library/";
    static final int COUNT=40, BODY_COLS=8, BODY_W=256, BODY_H=128;
    static final int PLUME_COLS=12, PLUME_W=128, PLUME_H=96, FRAMES=6;
    private static final int MICRO_START=0, MICRO_COUNT=8;
    private static final int STANDARD_START=8, STANDARD_COUNT=16;
    private static final int HEAVY_START=24, HEAVY_COUNT=10;
    private static final int TORPEDO_START=34, TORPEDO_COUNT=6;
    private Texture bodies,plumes;
    private TextureRegion[] bodyRegions,plumeRegions;
    private boolean checked;

    static int variantIndex(boolean torpedo, OrdnanceDoctrine.Payload payload,
                            AmmunitionTable.Warhead warhead, long visualId, int sourceId) {
        if(payload==null||warhead==null)throw new IllegalArgumentException("Missing ordnance variant");
        int start, count;
        if(torpedo){ start=TORPEDO_START;count=TORPEDO_COUNT; }
        else if(payload==OrdnanceDoctrine.Payload.MICRO ||
                payload==OrdnanceDoctrine.Payload.MICRO_CLUSTER_CARRIER ||
                payload==OrdnanceDoctrine.Payload.ULTRA_MICRO ||
                payload==OrdnanceDoctrine.Payload.DECOY){
            start=MICRO_START;count=MICRO_COUNT;
        } else if(payload==OrdnanceDoctrine.Payload.HEAVY ||
                payload==OrdnanceDoctrine.Payload.CLUSTER_CARRIER){
            start=HEAVY_START;count=HEAVY_COUNT;
        } else {start=STANDARD_START;count=STANDARD_COUNT;}
        long key=visualId*0x9E3779B97F4A7C15L ^ ((long)sourceId*0xBF58476D1CE4E5B9L)
                ^ ((long)warhead.ordinal()*0x94D049BB133111EBL);
        key^=key>>>29;
        return start+(int)Math.floorMod(key,(long)count);
    }

    static boolean burning(OrdnancePropulsion.Phase phase,double propellant){
        return phase!=null && propellant>0 && OrdnancePropulsion.burning(phase);
    }

    private boolean load(){
        if(checked)return bodyRegions!=null;
        checked=true;
        if(!Gdx.files.internal(ROOT+"missile_bodies.png").exists() ||
                !Gdx.files.internal(ROOT+"missile_plumes.png").exists() ||
                !Gdx.files.internal(ROOT+"missile_manifest.json").exists())
            return false;
        Texture newBodies=null,newPlumes=null;
        try {
            JsonValue meta=new JsonReader().parse(Gdx.files.internal(ROOT+"missile_manifest.json"));
            if(!"cd-ordnance-separated-v1".equals(meta.getString("schema")) ||
                    meta.getInt("count")!=COUNT ||
                    meta.getInt("body_columns")!=BODY_COLS ||
                    meta.getInt("plume_columns")!=PLUME_COLS ||
                    meta.getInt("plume_frames")!=FRAMES)
                throw new IllegalArgumentException("Unexpected separated missile manifest");
            JsonValue entries=meta.get("variants");
            if(entries==null||entries.size!=COUNT)
                throw new IllegalArgumentException("Incomplete separated missile atlas mapping");
            newBodies=new Texture(Gdx.files.internal(ROOT+"missile_bodies.png"));
            newPlumes=new Texture(Gdx.files.internal(ROOT+"missile_plumes.png"));
            if(newBodies.getWidth()!=BODY_COLS*BODY_W ||
                    newBodies.getHeight()!=5*BODY_H ||
                    newPlumes.getWidth()!=PLUME_COLS*PLUME_W ||
                    newPlumes.getHeight()!=20*PLUME_H)
                throw new IllegalArgumentException("Incorrect original missile atlas geometry");
            newBodies.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            newPlumes.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            TextureRegion[] bodiesArray=new TextureRegion[COUNT];
            TextureRegion[] plumesArray=new TextureRegion[COUNT*FRAMES];
            for(int index=0;index<COUNT;index++){
                JsonValue item=entries.get(index);
                if(item.getInt("index")!=index)
                    throw new IllegalArgumentException("Variant IDs are not stable");
                int width=item.getInt("body_width"), height=item.getInt("body_height");
                if(width<1||width>BODY_W||height<1||height>BODY_H)
                    throw new IllegalArgumentException("Invalid separated body bounds");
                int bx=(index%BODY_COLS)*BODY_W+(BODY_W-width)/2;
                int by=(index/BODY_COLS)*BODY_H+(BODY_H-height)/2;
                bodiesArray[index]=new TextureRegion(newBodies,bx,by,width,height);
            }
            for(int index=0;index<plumesArray.length;index++)
                plumesArray[index]=new TextureRegion(newPlumes,
                        (index%PLUME_COLS)*PLUME_W,
                        (index/PLUME_COLS)*PLUME_H,PLUME_W,PLUME_H);
            bodies=newBodies;plumes=newPlumes;
            bodyRegions=bodiesArray;plumeRegions=plumesArray;
            return true;
        } catch(RuntimeException failure) {
            if(newBodies!=null)newBodies.dispose();
            if(newPlumes!=null)newPlumes.dispose();
            Gdx.app.error("MISSILE-FX","Unable to load separated Library missile art",failure);
            return false;
        }
    }

    boolean available(){return load();}

    /** Called with an open batch; returns false when the legacy atlas must draw. */
    boolean draw(SpriteBatch batch, Battle.Missile missile,
                 BattleRenderPoses.Pose pose, GuidedPayloadVisual.Appearance style,
                 long tick) {
        if(!load())return false;
        int index=variantIndex(missile.torpedo,missile.payload,missile.warhead,
                missile.visualId,missile.sourceId);
        TextureRegion body=bodyRegions[index];
        float width=CombatEffectScale.of(style.width());
        float height=width*body.getRegionHeight()/body.getRegionWidth();
        float angle=(float)StrictMath.toDegrees(pose.angle());
        if(burning(missile.thrustPhase,missile.propellant)){
            int frame=(int)Math.floorMod((tick-missile.spawnTick)/2,FRAMES);
            TextureRegion plume=plumeRegions[index*FRAMES+frame];
            float phaseScale=switch(missile.thrustPhase){
                case BOOST -> 1.45f;
                case TERMINAL -> 1.85f;
                case CRUISE -> 1.0f;
                default -> 0f;
            };
            float plumeWidth=width*1.75f*phaseScale;
            float plumeHeight=height*1.35f;
            double rad=pose.angle();
            float x=pose.x()-(float)StrictMath.cos(rad)*(width*.5f+plumeWidth*.12f);
            float y=pose.y()-(float)StrictMath.sin(rad)*(width*.5f+plumeWidth*.12f);
            batch.setColor(1f,1f,1f,missile.thrustPhase==OrdnancePropulsion.Phase.CRUISE?.77f:1f);
            batch.draw(plume,x-plumeWidth/2,y-plumeHeight/2,
                    plumeWidth/2,plumeHeight/2,plumeWidth,plumeHeight,1,1,angle);
        }
        // Coasting/cold-ejected projectiles display body ONLY, including engines
        // previously baked into their source art. All color identity survives.
        batch.setColor(1f,1f,1f,style.alpha());
        batch.draw(body,pose.x()-width/2,pose.y()-height/2,
                width/2,height/2,width,height,1,1,angle);
        batch.setColor(1f,1f,1f,1f);
        return true;
    }

    @Override public void dispose(){
        if(bodies!=null)bodies.dispose();
        if(plumes!=null)plumes.dispose();
        bodies=null;plumes=null;bodyRegions=null;plumeRegions=null;checked=false;
    }
}
