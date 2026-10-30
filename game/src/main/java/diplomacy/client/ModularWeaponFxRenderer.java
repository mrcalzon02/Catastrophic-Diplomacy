package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.fx.WeaponFxProfile;
import diplomacy.fx.WeaponFxProfile.Clip;
import diplomacy.fx.WeaponFxProfile.Profile;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Three-part modular VFX composer using ONLY installed atlas-derived textures.
 * FIRING: a time-limited effect anchored at the origin supplied by the simulation.
 * PAYLOAD: travels with the real Beam or Missile entity, not a fake tween to target.
 * IMPACT: a separate one-shot anchored to actual collision/interception world coords.
 * Missing component assets are omitted; the existing beam/ordnance fallbacks remain.
 */
final class ModularWeaponFxRenderer implements Disposable {
    private record OneShot(Battle.VisualEvent event,Clip clip) { }
    private final Map<String,Texture> textures=new HashMap<>();
    private final Set<String> absent=new HashSet<>();
    private final List<OneShot> oneShots=new ArrayList<>();
    private Battle trackedBattle;
    private long lastSequence;
    private static final int ACTIVE_LIMIT=4096;

    private Texture texture(String path) {
        Texture cached=textures.get(path);
        if(cached!=null)return cached;
        if(absent.contains(path))return null;
        if(!Gdx.files.internal(path).exists()){absent.add(path);return null;}
        try {
            Texture next=new Texture(Gdx.files.internal(path));
            next.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            textures.put(path,next);
            return next;
        }catch(RuntimeException failed){
            Gdx.app.error("WEAPON-FX","Unable to load optional authored effect "+path,failed);
            absent.add(path);
            return null;
        }
    }
    private Texture frame(Clip clip,long elapsedTicks) {
        int n=clip.frame(elapsedTicks);
        return n<0?null:texture(clip.path(n));
    }

    private void sync(Battle battle) {
        if(trackedBattle!=battle) {
            trackedBattle=battle;
            lastSequence=0;
            oneShots.clear();
        }
        // Visual history is bounded in core. If rendering skips simulation ticks,
        // event sequence IDs still let us consume every retained event exactly once.
        for(Battle.VisualEvent event:battle.visualEvents()) {
            if(event.sequence()<=lastSequence)continue;
            lastSequence=event.sequence();
            Profile profile=WeaponFxProfile.forWeapon(event.weapon());
            if(profile==null)continue;
            Clip clip=switch(event.phase()){
                case FIRE -> profile.firing();
                case IMPACT -> profile.impact();
                case INTERCEPT -> profile.intercept();
                case TIMEOUT -> WeaponFxProfile.NONE;
            };
            if(clip.present() && clip.frame(battle.tick-event.tick())>=0)
                oneShots.add(new OneShot(event,clip));
        }
        for(Iterator<OneShot> iter=oneShots.iterator();iter.hasNext();) {
            OneShot shot=iter.next();
            if(shot.clip.frame(battle.tick-shot.event.tick())<0)iter.remove();
        }
        // Cap render-side instances only. This does NOT discard physics events or change
        // a shot's impact. Excess cosmetic bursts degrade by oldest-first eviction.
        if(oneShots.size()>ACTIVE_LIMIT)
            oneShots.subList(0,oneShots.size()-ACTIVE_LIMIT).clear();
    }

    private static void drawCentered(SpriteBatch batch,Texture image,
                                     float x,float y,float width,float height,float angle) {
        width=CombatEffectScale.of(width);
        height=CombatEffectScale.of(height);
        batch.draw(image,x-width/2,y-height/2,
                width/2,height/2,width,height,1,1,angle,
                0,0,image.getWidth(),image.getHeight(),false,false);
    }

    /**
     * This stage is driven strictly by simulation tick, including when paused.
     * Rendering multiple frames per tick does not speed up animation.
     */
    public void render(Battle battle,SpriteBatch batch,WeaponBeamSprites existingBeamPayload) {
        render(battle,batch,existingBeamPayload,null);
    }
    public void render(Battle battle,SpriteBatch batch,WeaponBeamSprites existingBeamPayload,
                       BattleRenderPoses poses) {
        render(battle,batch,existingBeamPayload,poses,null);
    }
    public void render(Battle battle,SpriteBatch batch,WeaponBeamSprites existingBeamPayload,
                       BattleRenderPoses poses,CombatViewBounds view) {
        sync(battle);
        // The already integrated real atlas-derived strip is the BEAM PAYLOAD part.
        // Its animation is started per shot using Beam.spawnTick, not global tick.
        if(existingBeamPayload!=null && existingBeamPayload.loaded()>0)
            existingBeamPayload.render(battle,batch,
                    shell -> !hasBallisticPayload(shell,battle.tick),poses,view);
        batch.begin();
        batch.setColor(1,1,1,1);
        // Local FIRING and terminal IMPACT/INTERCEPT are separate, independent clips.
        for(OneShot shot:oneShots){
            Battle.VisualEvent event=shot.event();
            if(view!=null&&!view.circle(event.x(),event.y(),
                    Math.max(shot.clip().width(),shot.clip().height())*.5f))continue;
            Texture image=frame(shot.clip(),battle.tick-event.tick());
            if(image==null)continue;
            float angle=(float)StrictMath.toDegrees(StrictMath.atan2(
                    event.targetY()-event.y(),event.targetX()-event.x()));
            drawCentered(batch,image,(float)event.x(),(float)event.y(),
                    shot.clip().width(),shot.clip().height(),angle);
        }
        // Guided PAYLOAD uses the projectile's *current* simulation position,
        // rotation and own spawn tick. A missile does not teleport to its target.
        for(Battle.Missile missile:battle.missiles()){
            Profile profile=WeaponFxProfile.forType(
                    missile.torpedo?Battle.DamageType.TORPEDO:Battle.DamageType.MISSILE);
            Clip clip=profile.payload();
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)missile.x,(float)missile.y,
                            (float)StrictMath.atan2(missile.vy,missile.vx)):poses.missile(missile);
            if(view!=null&&!view.circle(pose.x(),pose.y(),
                    Math.max(clip.width(),clip.height())*.5f))continue;
            Texture image=frame(clip,battle.tick-missile.spawnTick);
            if(image==null)continue;
            float angle=(float)StrictMath.toDegrees(pose.angle());
            drawCentered(batch,image,pose.x(),pose.y(),
                    clip.width(),clip.height(),angle);
        }
        // Slow ballistic PAYLOADs use their real collision-tested world positions.
        // The atlas frame is oriented by immutable launch velocity, not a fake
        // interpolation between origin and target that would conceal PD hits.
        Profile kinetic=WeaponFxProfile.forType(Battle.DamageType.KINETIC);
        for(Battle.Ballistic shell:battle.ballistics()){
            Clip clip=kinetic.payload();
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)shell.x,(float)shell.y,
                            (float)StrictMath.atan2(shell.vy,shell.vx)):poses.shell(shell);
            if(view!=null&&!view.circle(pose.x(),pose.y(),
                    Math.max(clip.width(),clip.height())*.5f))continue;
            Texture image=frame(clip,battle.tick-shell.spawnTick);
            if(image==null)continue;
            float angle=(float)StrictMath.toDegrees(pose.angle());
            drawCentered(batch,image,pose.x(),pose.y(),
                    clip.width(),clip.height(),angle);
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }

    /** A missing ballistic payload also keeps live, positioned fallback geometry. */
    public boolean hasBallisticPayload(Battle.Ballistic shell,long tick){
        Profile kinetic=WeaponFxProfile.forType(Battle.DamageType.KINETIC);
        return frame(kinetic.payload(),tick-shell.spawnTick)!=null;
    }

    /** A missing payload sprite keeps existing ordnance geometry visible. */
    public boolean hasMissilePayload(Battle.Missile missile,long tick){
        Profile profile=WeaponFxProfile.forType(
                missile.torpedo?Battle.DamageType.TORPEDO:Battle.DamageType.MISSILE);
        return frame(profile.payload(),tick-missile.spawnTick)!=null;
    }
    int activeAnimations(){return oneShots.size();}
    int loadedTextures(){return textures.size();}
    @Override public void dispose(){
        for(Texture value:textures.values())value.dispose();
        textures.clear();absent.clear();oneShots.clear();trackedBattle=null;lastSequence=0;
    }
}
