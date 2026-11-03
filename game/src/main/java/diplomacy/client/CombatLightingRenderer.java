package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.Battle;
import diplomacy.sim.LaserFamilies;
import diplomacy.sim.OrdnancePropulsion;

/**
 * World-space emissive lighting overlay shared by GL2/ES2 and GL3 paths.
 * One generated radial falloff texture and one SpriteBatch: no per-light FBO,
 * shaders, texture allocations or draw calls. Lighting is cosmetic; the
 * authoritative combat simulation does not depend on GPU effects.
 *
 * The additive pass brightens actual hull pixels near the sources but is NOT a
 * shadow-casting normal-map system. Beam/missile trajectories are simulation-owned.
 */
final class CombatLightingRenderer implements Disposable {
    private static final int GLOW_SIZE = 64;
    private static final int MAX_BEAM_LIGHTS = 256;
    private static final int MAX_MISSILE_LIGHTS = 192;
    // Per-frame caps and scratch positions are renderer-owned and never grow
    // with a 200 vs 200 combat. Recent hot wounds win over older stacked hits.
    private static final int MAX_HOT_METAL_LIGHTS = 24;
    private final float[] hotX=new float[MAX_HOT_METAL_LIGHTS];
    private final float[] hotY=new float[MAX_HOT_METAL_LIGHTS];
    private final float[] hotRadius=new float[MAX_HOT_METAL_LIGHTS];
    private final float[] flashX=new float[48],flashY=new float[48],flashR=new float[48];
    private final CombatLightingModel timeline = new CombatLightingModel();
    private final Texture glow;
    /** Steeper falloff only for impact/wound lights, not engines or missiles. */
    private final Texture tightGlow;
    private final Texture smokeMask;
    private int lastGlowQuads;
    private int lastSmokeQuads;

    CombatLightingRenderer() {
        Pixmap pixels = new Pixmap(GLOW_SIZE, GLOW_SIZE, Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                float dx = (x + .5f - GLOW_SIZE / 2f) / (GLOW_SIZE / 2f);
                float dy = (y + .5f - GLOW_SIZE / 2f) / (GLOW_SIZE / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                float falloff = Math.max(0, 1 - distance);
                falloff = falloff * falloff * (3 - 2 * falloff);
                pixels.setColor(1, 1, 1, falloff);
                pixels.drawPixel(x, y);
            }
        }
        glow = new Texture(pixels);
        pixels.dispose();
        glow.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        Pixmap tightPixels=new Pixmap(GLOW_SIZE,GLOW_SIZE,Pixmap.Format.RGBA8888);
        tightPixels.setBlending(Pixmap.Blending.None);
        for(int py=0;py<GLOW_SIZE;py++)for(int px=0;px<GLOW_SIZE;px++){
            float dx=(px+.5f-GLOW_SIZE*.5f)/(GLOW_SIZE*.5f);
            float dy=(py+.5f-GLOW_SIZE*.5f)/(GLOW_SIZE*.5f);
            float edge=Math.max(0f,1f-(float)StrictMath.hypot(dx,dy));
            float smooth=edge*edge*(3f-2f*edge);
            // The regular radial alpha is smooth; cubing it makes the
            // brightest part hug the metal incision instead of the hull.
            float tight=smooth*smooth*smooth;
            tightPixels.setColor(1f,1f,1f,tight);
            tightPixels.drawPixel(px,py);
        }
        tightGlow=new Texture(tightPixels);
        tightPixels.dispose();
        tightGlow.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        Pixmap cloud=new Pixmap(GLOW_SIZE,GLOW_SIZE,Pixmap.Format.RGBA8888);
        cloud.setBlending(Pixmap.Blending.None);
        for(int py=0;py<GLOW_SIZE;py++)for(int px=0;px<GLOW_SIZE;px++){
            float dx=(px+.5f-GLOW_SIZE*.5f)/(GLOW_SIZE*.5f);
            float dy=(py+.5f-GLOW_SIZE*.5f)/(GLOW_SIZE*.5f);
            float angle=(float)StrictMath.atan2(dy,dx);
            float wobble=1f+.13f*(float)StrictMath.sin(angle*5+1.2)
                    +.09f*(float)StrictMath.cos(angle*9-.7);
            float distance=(float)StrictMath.sqrt(dx*dx+dy*dy)/wobble;
            float density=Math.max(0,1-distance);
            density=density*density*(3-2*density);
            cloud.setColor(1,1,1,density*.78f);
            cloud.drawPixel(px,py);
        }
        smokeMask=new Texture(cloud);
        cloud.dispose();
        smokeMask.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
    }

    private static boolean visible(OrthographicCamera camera, double x, double y, double radius) {
        double halfW = camera.viewportWidth * camera.zoom * .5;
        double halfH = camera.viewportHeight * camera.zoom * .5;
        return x + radius >= camera.position.x - halfW
                && x - radius <= camera.position.x + halfW
                && y + radius >= camera.position.y - halfH
                && y - radius <= camera.position.y + halfH;
    }

    private static boolean visibleSegment(OrthographicCamera camera,
                                          double x1,double y1,double x2,double y2,
                                          double pad) {
        double hw=camera.viewportWidth*camera.zoom*.5;
        double hh=camera.viewportHeight*camera.zoom*.5;
        return Math.max(x1,x2)+pad>=camera.position.x-hw &&
                Math.min(x1,x2)-pad<=camera.position.x+hw &&
                Math.max(y1,y2)+pad>=camera.position.y-hh &&
                Math.min(y1,y2)-pad<=camera.position.y+hh;
    }

    private void spot(SpriteBatch batch, OrthographicCamera camera,
                      double x, double y, float radius, Color color, float alpha) {
        // Fully subpixel halos cannot illuminate a visible receiver or contribute
        // a legible source glow. Keep larger emitters and true beam cores.
        if (radius <= 0 || alpha <= 0 || radius*2f/camera.zoom < 1f
                || !visible(camera, x, y, radius)) return;
        batch.setColor(color.r, color.g, color.b, Math.min(1, alpha));
        batch.draw(glow, (float) x - radius, (float) y - radius,
                radius * 2, radius * 2);
        lastGlowQuads++;
    }

    private void tightSpot(SpriteBatch batch,OrthographicCamera camera,
                           double x,double y,float radius,Color color,float alpha){
        if(radius<=0||alpha<=0||radius*2f/camera.zoom<1f||
                !visible(camera,x,y,radius))return;
        // Never bleed alpha from a shared SpriteBatch color into the next draw.
        batch.setColor(color.r,color.g,color.b,Math.min(.25f,alpha));
        batch.draw(tightGlow,(float)x-radius,(float)y-radius,radius*2f,radius*2f);
        lastGlowQuads++;
    }

    /** Subpixel projected radius is allowed to disappear at tactical zoom. */
    static float hotMetalRadius(Battle.DamageType family){
        return CombatEffectScale.of(switch(family){
            case PLASMA -> 3.0f;
            case TORPEDO -> 2.7f;
            case MISSILE -> 2.2f;
            case PARTICLE -> 1.8f;
            case LASER -> 1.8f; // annealing radius is MATERIAL, not light
            default -> 0f;
        });
    }
    static float impactFlashRadius(boolean intercepted,boolean heavy){
        // Previous wide explosions used 47 world units before global scaling.
        // These tiny optical cues must not illuminate an entire hull.
        return CombatEffectScale.of(intercepted?(heavy?10f:7f):(heavy?19f:12f));
    }
    static float impactFlashAlpha(boolean intercepted){
        return intercepted?.13f:.18f;
    }

    private void streak(SpriteBatch batch, OrthographicCamera camera,
                        double x1, double y1, double x2, double y2,
                        float width, Color color, float alpha) {
        if (width <= 0 || alpha <= 0) return;
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        if (!visible(camera, (minX + maxX) * .5, (minY + maxY) * .5,
                (float) (StrictMath.hypot(maxX - minX, maxY - minY) * .5 + width)))
            return;
        float length = (float) StrictMath.hypot(x2 - x1, y2 - y1);
        if (length < .01f) return;
        float angle = (float) StrictMath.toDegrees(StrictMath.atan2(y2 - y1, x2 - x1));
        batch.setColor(color.r, color.g, color.b, Math.min(1, alpha));
        batch.draw(glow, (float) x1, (float) y1 - width / 2,
                0, width / 2, length, width, 1, 1, angle,
                0, 0, glow.getWidth(), glow.getHeight(), false, false);
        lastGlowQuads++;
    }

    static Color weaponColor(Battle.DamageType type) {
        return switch (type) {
            case KINETIC -> Color.GOLD;
            case PLASMA -> Color.SCARLET;
            case PARTICLE -> Color.VIOLET;
            case MISSILE, TORPEDO -> Color.ORANGE;
            default -> Color.CYAN;
        };
    }

    static Color laserColor(LaserFamilies.Type type){
        return switch(type){
            case PULSE -> Color.CYAN;
            case RAPID -> Color.GREEN;
            case LANCE -> Color.VIOLET;
            case OVERDRIVE -> Color.SCARLET;
            case NEEDLE -> Color.GOLD;
        };
    }

    static Color eventColor(String weapon) {
        LaserFamilies.Type laser=LaserFamilies.fromWeapon(weapon);
        if(laser!=null)return laserColor(laser);
        return switch (weapon) {
            case "KINETIC", "FLAK" -> Color.GOLD;
            case "PLASMA" -> Color.SCARLET;
            case "PARTICLE" -> Color.VIOLET;
            case "MISSILE", "TORPEDO", "DECOY" -> Color.ORANGE;
            default -> Color.CYAN;
        };
    }

    /** Capture source positions at every fixed simulation step, not per frame. */
    void captureTick(Battle battle){timeline.update(battle);}

    /** Dusty decorative smoke in space: alpha blended, persistent and tick-aged. */
    void renderSmoke(Battle battle, SpriteBatch batch, OrthographicCamera camera) {
        renderSmoke(battle,batch,camera,null);
    }
    /** Small smoke puffs are reserved for actual hull penetration. */
    void renderSmoke(Battle battle, SpriteBatch batch, OrthographicCamera camera,
                     CombatHitFeedback impacts) {
        timeline.update(battle);
        lastSmokeQuads = 0;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        for (CombatLightingModel.Smoke smoke : timeline.smoke()) {
            float age = (float) (battle.tick - smoke.bornTick())
                    / CombatLightingModel.SMOKE_LIFETIME_TICKS;
            if (age < 0 || age >= 1) continue;
            float radius = CombatEffectScale.of((smoke.heavy() ? 7.5f : 4.8f) * (1 + age * 1.65f));
            if (radius*2f/camera.zoom < 1f
                    || !visible(camera, smoke.x(), smoke.y(), radius)) continue;
            float fade=1-age;
            if(smoke.decoy())batch.setColor(.36f,.53f,.69f,.25f*fade*fade);
            else if(smoke.heavy())batch.setColor(.47f,.42f,.40f,.50f*fade*fade);
            else batch.setColor(.55f,.59f,.65f,.39f*fade*fade);
            batch.draw(smokeMask,smoke.x()-radius,smoke.y()-radius,
                    radius * 2, radius * 2);
            lastSmokeQuads++;
        }
        // Local smoke only from actual HULL penetration, not impacts
        // absorbed by shields or superficial plating. An individual puncture
        // emits at most two wisps and expires without per-pixel allocations.
        int breachWisps=0;
        if(impacts!=null)for(CombatHitFeedback.Afterglow wound:impacts.afterglows()){
            if(wound.layer!=CombatHitFeedback.Layer.HULL || breachWisps>=64)continue;
            float age=wound.age/CombatHitFeedback.AFTERGLOW_LIFETIME;
            if(age<0||age>=1)continue;
            int count=smokeWisps(wound.weapon);
            for(int i=0;i<count && breachWisps<64;i++){
                int seed=ShipDamageVariants.hash(wound.visualSeed+i*1039,wound.weapon);
                float tangent=(seed>>>8&255)/255f-.5f;
                float drift=(2.1f+age*6f)*wound.scale;
                float x=wound.x+wound.nx*drift*.29f-wound.ny*tangent*drift*.62f;
                float y=wound.y+wound.ny*drift*.29f+wound.nx*tangent*drift*.62f;
                float radius=CombatEffectScale.of(2.6f+age*5.6f)*wound.scale;
                if(radius*2f/camera.zoom<1f||!visible(camera,x,y,radius))continue;
                float fade=(1f-age)*(1f-age);
                float opacity=(wound.weapon==Battle.DamageType.TORPEDO?.28f:
                        wound.weapon==Battle.DamageType.PLASMA?.24f:.13f)*fade;
                batch.setColor(.46f,.45f,.48f,opacity);
                batch.draw(smokeMask,x-radius,y-radius,radius*2,radius*2);
                lastSmokeQuads++;breachWisps++;
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Called after hulls, real beam/ordnance textures and geometric fallbacks.
     * Emissive falloff lightens nearby ship surfaces as well as space around them.
     */
    void renderGlow(Battle battle, SpriteBatch batch, OrthographicCamera camera) {
        renderGlow(battle,batch,camera,null);
    }
    void renderGlow(Battle battle, SpriteBatch batch, OrthographicCamera camera,
                    BattleRenderPoses poses) {
        renderGlow(battle,batch,camera,poses,null);
    }
    void renderGlow(Battle battle, SpriteBatch batch, OrthographicCamera camera,
                    BattleRenderPoses poses, ShieldFxTimeline shields) {
        renderGlow(battle,batch,camera,poses,shields,null);
    }
    void renderGlow(Battle battle, SpriteBatch batch, OrthographicCamera camera,
                    BattleRenderPoses poses, ShieldFxTimeline shields,
                    CombatHitFeedback impactsForGlow) {
        timeline.update(battle);
        lastGlowQuads = 0;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();

        // Engine lights follow actual facing, rendered speed and hull visual scale.
        for (Battle.Ship ship : battle.ships()) {
            if (!ship.alive()) continue;
            float scale = (float) Math.min(3.5, ship.visualScale());
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            // Reject the entire engine emitter before trigonometry, speed
            // normalization or submitting up to three glowing quads.
            if(!visible(camera,pose.x(),pose.y(),105*scale))continue;
            float ratio = (float) Math.min(1, StrictMath.hypot(ship.vx, ship.vy)
                    / Math.max(1, ship.speed));
            float intensity = .14f + ratio * .7f;
            double dx=StrictMath.cos(pose.angle()),dy=StrictMath.sin(pose.angle());
            double x=pose.x()-dx*12*scale,y=pose.y()-dy*12*scale;
            Color tint = ship.team == 0 ? Color.SKY : Color.ORANGE;
            spot(batch, camera, x, y, 12 * scale, tint, .65f * intensity);
            spot(batch, camera, x, y, 4.5f * scale, Color.WHITE, .52f * intensity);
            if (ratio > .05f)
                streak(batch, camera, x, y,
                        x - dx * (13 + 24 * ratio) * scale,
                        y - dy * (13 + 24 * ratio) * scale,
                        10 * scale, tint, .50f * intensity);
        }

        // Broad, tinted optical corona around the real live beam, never a second
        // synthetic beam or a light appearing before the weapon actually fires.
        int beams = 0;
        for (Battle.Beam beam : battle.beams()) {
            if (++beams > MAX_BEAM_LIGHTS) break;
            // No beam corona work when the complete lit segment is offscreen.
            if(!visibleSegment(camera,beam.x1,beam.y1,beam.x2,beam.y2,45))continue;
            Color tint=beam.pointDefense?Color.GREEN:
                    beam.type==Battle.DamageType.LASER?
                    laserColor(beam.laserType):weaponColor(beam.type);
            // True beam family controls core/corona width, not an unrelated
            // second light strip. Existing shared atlas remains authoritative.
            float scale=beam.pointDefense?.30f:
                    beam.type==Battle.DamageType.LASER?
                    beam.laserType.thickness/6.3f:1f;
            streak(batch,camera,beam.x1,beam.y1,beam.x2,beam.y2,
                    CombatEffectScale.of(12)*scale,
                    tint,beam.pointDefense?.035f:.085f);
            streak(batch,camera,beam.x1,beam.y1,beam.x2,beam.y2,
                    CombatEffectScale.of(5)*scale,
                    tint,beam.pointDefense?.08f:.14f);
            tightSpot(batch,camera,beam.x1,beam.y1,
                    CombatEffectScale.of(8)*scale,
                    tint,beam.pointDefense?.07f:.10f);
            tightSpot(batch,camera,beam.x2,beam.y2,
                    CombatEffectScale.of(9)*scale,
                    tint,beam.pointDefense?.08f:.12f);
        }

        // A new ignition burst whenever the actual engine switches from
        // cold/coasting to thrust, including the terminal motor restart.
        for(CombatLightingModel.Ignition ignition:timeline.ignitions()){
            float age=(float)(battle.tick-ignition.bornTick())
                    /CombatLightingModel.IGNITION_LIFETIME_TICKS;
            if(age<0||age>=1)continue;
            Color tint=ignition.decoy()?Color.SKY:
                    ignition.terminal()?Color.SCARLET:
                    ignition.heavy()?Color.ORANGE:Color.GOLD;
            float base=CombatEffectScale.of(
                    ignition.terminal()?29:ignition.heavy()?24:15);
            float fade=(1-age)*(1-age);
            spot(batch,camera,ignition.x(),ignition.y(),
                    base*(.8f+age*.9f),tint,.72f*fade);
            spot(batch,camera,ignition.x(),ignition.y(),
                    base*.42f,Color.WHITE,.76f*fade);
            streak(batch,camera,ignition.x(),ignition.y(),
                    ignition.x()-ignition.dx()*base*(1+age*1.5f),
                    ignition.y()-ignition.dy()*base*(1+age*1.5f),
                    base*.42f,tint,.50f*fade);
        }

        // Active emitters only: no exhaust glow during cold eject or coasting.
        int missiles = 0;
        for (Battle.Missile missile : battle.missiles()) {
            if (++missiles > MAX_MISSILE_LIGHTS) break;
            if (!OrdnancePropulsion.burning(missile.thrustPhase)
                    || missile.fuel<=0
                    || !visible(camera,missile.x,missile.y,55))continue;
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)missile.x,(float)missile.y,
                            (float)StrictMath.atan2(missile.vy,missile.vx)):poses.missile(missile);
            double dx=StrictMath.cos(pose.angle()),dy=StrictMath.sin(pose.angle());
            double x=pose.x()-dx*CombatEffectScale.of(missile.torpedo ? 8 : 5);
            double y=pose.y()-dy*CombatEffectScale.of(missile.torpedo ? 8 : 5);
            boolean terminal=missile.thrustPhase==OrdnancePropulsion.Phase.TERMINAL;
            Color color=missile.decoy?Color.SKY:
                    terminal?Color.SCARLET:missile.torpedo?Color.ORANGE:Color.GOLD;
            float strength=missile.thrustPhase==OrdnancePropulsion.Phase.BOOST?1.16f:
                    terminal?1.28f:1f;
            spot(batch,camera,x,y,
                    CombatEffectScale.of(missile.torpedo?30:20)*strength,color,.53f);
            spot(batch,camera,x,y,
                    CombatEffectScale.of(missile.torpedo?11:7)*strength,Color.WHITE,.70f);
        }

        // Only true simulation FIRE, IMPACT or INTERCEPT events cause bursts.
        // Timeouts deliberately do not become hits or unexplained explosions.
        // The former 47-unit bright impact corona repeatedly covered much of
        // a ship. Use small, steep, low-alpha flashes instead. Multiple hits
        // at the same coordinate share a single visual glow per rendered frame.
        int flashLights=0;
        for (CombatLightingModel.Flash flash : timeline.flashes()) {
            if(flashLights>=48)break;
            Battle.VisualEvent e=flash.event();
            int lifetime=CombatLightingModel.flashLife(e);
            if(lifetime<=0)continue;
            float age=(float)(battle.tick-e.tick())/lifetime;
            if(age<0||age>=1)continue;
            boolean intercepted=e.phase()==Battle.VisualPhase.INTERCEPT;
            boolean heavy=e.weapon().equals("KINETIC")||e.weapon().equals("TORPEDO");
            float radius=impactFlashRadius(intercepted,heavy);
            boolean near=false;
            for(int i=0;i<flashLights;i++){
                float dx=flashX[i]-(float)e.x(),dy=flashY[i]-(float)e.y();
                float d=Math.max(radius,flashR[i]);
                if(dx*dx+dy*dy<d*d){near=true;break;}
            }
            if(near)continue;
            Color tint=intercepted?Color.GREEN:eventColor(e.weapon());
            float fade=(1f-age)*(1f-age);
            tightSpot(batch,camera,e.x(),e.y(),radius*(.8f+age*.25f),
                    tint,impactFlashAlpha(intercepted)*fade);
            tightSpot(batch,camera,e.x(),e.y(),radius*.25f,
                    Color.WHITE,.085f*fade*fade);
            flashX[flashLights]=(float)e.x();
            flashY[flashLights]=(float)e.y();
            flashR[flashLights++]=radius;
        }
        // Fresh hot metal glows at the actual lost-armor or breached-hull
        // impact site. The broad LASER anneal stays permanent *oxide color*,
        // never a world-space halo. Cold kinetic damage emits no persistent light.
        int hotLights=0;
        if(impactsForGlow!=null){
            var wounds=impactsForGlow.afterglows();
            // Most recent real wounds win, with overlapping halos suppressed
            // and a hard global cap independent of fleet size.
            for(int n=wounds.size()-1;n>=0&&hotLights<MAX_HOT_METAL_LIGHTS;n--){
                CombatHitFeedback.Afterglow wound=wounds.get(n);
                if(wound.weapon==null||wound.weapon==Battle.DamageType.KINETIC||
                        wound.weapon==Battle.DamageType.FIGHTER)continue;
                float age=wound.age/CombatHitFeedback.AFTERGLOW_LIFETIME;
                if(age<0||age>=1)continue;
                float strength=emissionStrength(wound.weapon);
                float radius=hotMetalRadius(wound.weapon)*wound.scale;
                if(radius<=0f||radius*2f/camera.zoom<1f||
                        !visible(camera,wound.x,wound.y,radius))continue;
                boolean stacked=false;
                for(int i=0;i<hotLights;i++){
                    float dx=hotX[i]-wound.x,dy=hotY[i]-wound.y;
                    float d=Math.max(radius,hotRadius[i])*1.25f;
                    if(dx*dx+dy*dy<d*d){stacked=true;break;}
                }
                if(stacked)continue;
                float fade=(1f-age)*(1f-age)*(1f-age);
                Color color=wound.weapon==Battle.DamageType.PARTICLE?Color.VIOLET:
                        wound.weapon==Battle.DamageType.PLASMA?Color.ORANGE:Color.GOLD;
                tightSpot(batch,camera,wound.x,wound.y,radius,color,
                        strength*fade);
                // The material shader already draws glowing lips, so never
                // add a second broad white disc over intact plating.
                hotX[hotLights]=wound.x;
                hotY[hotLights]=wound.y;
                hotRadius[hotLights++]=radius;
            }
        }
        // Tiny additive shield spill follows the same authoritative per-facing
        // timeline as the art overlay. Capped, camera-culled and mobile-friendly.
        if(shields!=null){
            int shieldLights=0;
            for(ShieldFxTimeline.Effect fx:shields.events()){
                if(++shieldLights>96)break;
                if(!fx.ship.alive()&&fx.kind!=ShieldFxTimeline.Kind.COLLAPSE)continue;
                float life=1f-fx.progress();
                if(life<=0)continue;
                if(fx.kind==ShieldFxTimeline.Kind.RECHARGE && camera.zoom>1.8f)continue;
                BattleRenderPoses.Pose pose=poses==null?
                        new BattleRenderPoses.Pose((float)fx.ship.x,(float)fx.ship.y,(float)fx.ship.facing):
                        poses.ship(fx.ship);
                double facing=pose.angle()+StrictMath.toRadians(fx.localAngle);
                float shell=(float)(16*fx.ship.visualScale());
                float x=pose.x()+(float)StrictMath.cos(facing)*shell;
                float y=pose.y()+(float)StrictMath.sin(facing)*shell;
                Color tint=fx.weapon==Battle.DamageType.PLASMA?Color.PINK:
                        fx.weapon==Battle.DamageType.PARTICLE?Color.VIOLET:
                        fx.weapon==Battle.DamageType.KINETIC?Color.GOLD:Color.CYAN;
                float small=CombatEffectScale.of(12f)*(float)fx.ship.visualScale();
                spot(batch,camera,x,y,small*(1f+.22f*fx.progress()),tint,
                        .28f*life*fx.intensity);
            }
        }
        batch.setColor(Color.WHITE);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    static int smokeWisps(Battle.DamageType family){
        return family==Battle.DamageType.TORPEDO||
                family==Battle.DamageType.PLASMA||
                family==Battle.DamageType.MISSILE?2:1;
    }
    static float emissionStrength(Battle.DamageType family){
        if(family==null)return 0f;
        return switch(family){
            case PLASMA -> .12f;
            case LASER -> .055f;
            case PARTICLE -> .075f;
            case TORPEDO -> .095f;
            case MISSILE -> .075f;
            case FIGHTER, KINETIC -> 0f;
        };
    }
    int glowQuads() { return lastGlowQuads; }
    int smokeQuads() { return lastSmokeQuads; }
    int smokeParticles() { return timeline.smokeCount(); }
    @Override public void dispose() { glow.dispose();tightGlow.dispose();smokeMask.dispose(); }
}
