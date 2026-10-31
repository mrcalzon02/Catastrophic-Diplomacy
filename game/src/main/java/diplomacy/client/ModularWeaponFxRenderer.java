package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
    private final LaserRun01Fx lasers=new LaserRun01Fx();
    private Battle trackedBattle;
    private long lastSequence;
    private static final int ACTIVE_LIMIT=4096;
    private TextureRegion[] curatedMissileBodies;
    private boolean rejectedMissileAtlas;
    private TextureRegion[] curatedKineticBodies;
    private boolean rejectedKineticAtlas;
    private TextureRegion[] authoredFiringImpacts;
    private boolean rejectedFiringImpacts;

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
    /** Authored 64-frame muzzle and impact atlas: four weapon families,
     * two phases, eight frames each. All cells are source-art cuts. */
    private TextureRegion[] authoredFiringImpacts(){
        if(authoredFiringImpacts!=null)return authoredFiringImpacts;
        if(rejectedFiringImpacts)return null;
        Texture atlas=texture(WeaponStageArt.CURATED);
        if(atlas==null)return null;
        if(atlas.getWidth()!=WeaponStageArt.SIZE||atlas.getHeight()!=WeaponStageArt.SIZE){
            rejectedFiringImpacts=true;
            Gdx.app.error("WEAPON-FX","Original firing and impact atlas dimensions invalid");
            return null;
        }
        TextureRegion[] sprites=new TextureRegion[WeaponStageArt.ROWS*WeaponStageArt.COLS];
        for(int i=0;i<sprites.length;i++)
            sprites[i]=new TextureRegion(atlas,
                    (i%WeaponStageArt.COLS)*WeaponStageArt.CELL,
                    (i/WeaponStageArt.COLS)*WeaponStageArt.CELL,
                    WeaponStageArt.CELL,WeaponStageArt.CELL);
        return authoredFiringImpacts=sprites;
    }

    /** Stage clip indices stay bound to real weapon type and actual hit events. */
    private TextureRegion authoredStage(Clip clip,long elapsed){
        int row=WeaponStageArt.row(clip);
        int frame=clip.frame(elapsed);
        if(row<0||frame<0||frame>=WeaponStageArt.COLS)return null;
        TextureRegion[] cells=authoredFiringImpacts();
        return cells==null?null:cells[row*WeaponStageArt.COLS+frame];
    }

    /** Build six atlas regions once, never once per active projectile. */
    private TextureRegion[] missileBodies(){
        if(curatedMissileBodies!=null)return curatedMissileBodies;
        if(rejectedMissileAtlas)return null;
        Texture atlas=texture(GuidedPayloadVisual.CURATED);
        if(atlas==null)return null;
        if(atlas.getWidth()!=GuidedPayloadVisual.COLS*GuidedPayloadVisual.CELL_WIDTH ||
                atlas.getHeight()!=2*GuidedPayloadVisual.CELL_HEIGHT){
            rejectedMissileAtlas=true;
            Gdx.app.error("WEAPON-FX","Curated missile sprite atlas cell dimensions invalid");
            return null;
        }
        TextureRegion[] regions=new TextureRegion[GuidedPayloadVisual.CELLS];
        for(int i=0;i<regions.length;i++)
            regions[i]=new TextureRegion(atlas,
                    (i%GuidedPayloadVisual.COLS)*GuidedPayloadVisual.CELL_WIDTH,
                    (i/GuidedPayloadVisual.COLS)*GuidedPayloadVisual.CELL_HEIGHT,
                    GuidedPayloadVisual.CELL_WIDTH,GuidedPayloadVisual.CELL_HEIGHT);
        return curatedMissileBodies=regions;
    }

    /** Five source-art shell cells shared across every physical ballistic round. */
    private TextureRegion[] kineticBodies(){
        if(curatedKineticBodies!=null)return curatedKineticBodies;
        if(rejectedKineticAtlas)return null;
        Texture atlas=texture(KineticPayloadVisual.CURATED);
        if(atlas==null)return null;
        if(atlas.getWidth()!=KineticPayloadVisual.CELLS*KineticPayloadVisual.WIDTH ||
                atlas.getHeight()!=KineticPayloadVisual.HEIGHT){
            rejectedKineticAtlas=true;
            Gdx.app.error("WEAPON-FX","Kinetic sprite atlas dimensions invalid");
            return null;
        }
        TextureRegion[] regions=new TextureRegion[KineticPayloadVisual.CELLS];
        for(int i=0;i<regions.length;i++)
            regions[i]=new TextureRegion(atlas,i*KineticPayloadVisual.WIDTH,0,
                    KineticPayloadVisual.WIDTH,KineticPayloadVisual.HEIGHT);
        return curatedKineticBodies=regions;
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

    private static void drawCentered(SpriteBatch batch,TextureRegion image,
                                     float x,float y,float width,float height,float angle){
        width=CombatEffectScale.of(width);height=CombatEffectScale.of(height);
        batch.draw(image,x-width/2,y-height/2,width/2,height/2,
                width,height,1f,1f,angle);
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
        lasers.sync(battle);
        // The already integrated real atlas-derived strip is the BEAM PAYLOAD part.
        // Its animation is started per shot using Beam.spawnTick, not global tick.
        if(existingBeamPayload!=null && existingBeamPayload.loaded()>0)
            existingBeamPayload.render(battle,batch,
                    shell -> !hasBallisticPayload(shell,battle.tick),poses,view,
                    beam -> !lasers.handles(beam,battle.tick));
        batch.begin();
        batch.setColor(1,1,1,1);
        // Local FIRING and terminal IMPACT/INTERCEPT are separate, independent clips.
        for(OneShot shot:oneShots){
            Battle.VisualEvent event=shot.event();
            if(lasers.handlesFire(event))continue; // Full authored shot replaces old muzzle cutout.
            if(view!=null&&!view.circle(event.x(),event.y(),
                    Math.max(shot.clip().width(),shot.clip().height())*.5f))continue;
            long elapsed=battle.tick-event.tick();
            TextureRegion authored=authoredStage(shot.clip(),elapsed);
            Texture image=authored==null?frame(shot.clip(),elapsed):null;
            if(authored==null&&image==null)continue;
            float angle=(float)StrictMath.toDegrees(StrictMath.atan2(
                    event.targetY()-event.y(),event.targetX()-event.x()));
            diplomacy.sim.LaserFamilies.Type laser=
                    diplomacy.sim.LaserFamilies.fromWeapon(event.weapon());
            float size=laser==null?1f:switch(laser){
                case PULSE -> 1f;
                case RAPID -> .72f;
                case LANCE -> 1.25f;
                case OVERDRIVE -> 1.35f;
                case NEEDLE -> .62f;
            };
            if(laser!=null){
                var tint=CombatLightingRenderer.laserColor(laser);
                batch.setColor(tint.r,tint.g,tint.b,1);
            }
            if(authored!=null)
                drawCentered(batch,authored,(float)event.x(),(float)event.y(),
                        shot.clip().width()*size,shot.clip().height()*size,angle);
            else drawCentered(batch,image,(float)event.x(),(float)event.y(),
                    shot.clip().width()*size,shot.clip().height()*size,angle);
            if(laser!=null)batch.setColor(1,1,1,1);
        }
        // Guided PAYLOAD uses the real physical body's type, fitted payload,
        // warhead, and LIVE motor phase—not one universal missile image.
        for(Battle.Missile missile:battle.missiles()){
            GuidedPayloadVisual.Appearance style=GuidedPayloadVisual.forMissile(missile);
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)missile.x,(float)missile.y,
                            (float)StrictMath.atan2(missile.vy,missile.vx)):poses.missile(missile);
            float radius=CombatEffectScale.of(Math.max(style.width(),style.height()))*.5f;
            if(view!=null&&!view.circle(pose.x(),pose.y(),radius))continue;
            TextureRegion[] authored=missileBodies();
            Texture fallback=authored==null?texture(style.fallback()):null;
            if(authored==null&&fallback==null)continue; // Existing physical geometry.
            float angle=(float)StrictMath.toDegrees(pose.angle());
            batch.setColor(style.red(),style.green(),style.blue(),style.alpha());
            if(authored!=null)
                drawCentered(batch,authored[style.cell()],pose.x(),pose.y(),
                        style.width(),style.height(),angle);
            else drawCentered(batch,fallback,pose.x(),pose.y(),
                    style.width(),style.height(),angle);
            batch.setColor(1,1,1,1);
        }
        // Slow ballistic PAYLOADs use their real collision-tested world positions.
        // The atlas frame is oriented by immutable launch velocity, not a fake
        // interpolation between origin and target that would conceal PD hits.
        Profile kinetic=WeaponFxProfile.forType(Battle.DamageType.KINETIC);
        TextureRegion[] shells=kineticBodies();
        for(Battle.Ballistic shell:battle.ballistics()){
            Clip clip=kinetic.payload();
            KineticPayloadVisual.Appearance spec=KineticPayloadVisual.forType(shell.ammunition);
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)shell.x,(float)shell.y,
                            (float)StrictMath.atan2(shell.vy,shell.vx)):poses.shell(shell);
            if(view!=null&&!view.circle(pose.x(),pose.y(),
                    CombatEffectScale.of(Math.max(spec.width(),spec.height()))*.5f))continue;
            Texture fallback=shells==null?frame(clip,battle.tick-shell.spawnTick):null;
            if(shells==null&&fallback==null)continue;
            float angle=(float)StrictMath.toDegrees(pose.angle());
            if(shells!=null)
                drawCentered(batch,shells[spec.cell()],pose.x(),pose.y(),
                        spec.width(),spec.height(),angle);
            else drawCentered(batch,fallback,pose.x(),pose.y(),
                    clip.width(),clip.height(),angle);
        }
        lasers.draw(batch,battle,view);
        batch.setColor(1,1,1,1);
        batch.end();
    }

    boolean hasLaserBeam(Battle.Beam beam,long tick){return lasers.handles(beam,tick);}

    /** A missing ballistic payload also keeps live, positioned fallback geometry. */
    public boolean hasBallisticPayload(Battle.Ballistic shell,long tick){
        Profile kinetic=WeaponFxProfile.forType(Battle.DamageType.KINETIC);
        return kineticBodies()!=null || frame(kinetic.payload(),tick-shell.spawnTick)!=null;
    }

    /** A missing payload sprite keeps existing ordnance geometry visible. */
    public boolean hasMissilePayload(Battle.Missile missile,long tick){
        return missileBodies()!=null||
                texture(GuidedPayloadVisual.forMissile(missile).fallback())!=null;
    }
    int activeAnimations(){return oneShots.size();}
    int loadedTextures(){return textures.size();}
    @Override public void dispose(){
        for(Texture value:textures.values())value.dispose();
        textures.clear();absent.clear();oneShots.clear();trackedBattle=null;lastSequence=0;
        curatedMissileBodies=null;rejectedMissileAtlas=false;
        lasers.dispose();
        curatedKineticBodies=null;rejectedKineticAtlas=false;
        authoredFiringImpacts=null;rejectedFiringImpacts=false;
    }
}
