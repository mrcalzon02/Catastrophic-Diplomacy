package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.Battle;
import java.util.EnumMap;

/**
 * Optional curated sustained-beam sprites. Never draws emitter/weapon hardware.
 * Original art remains in the source library; only transparent effect strips
 * installed at assets/effects/beams/ are loaded here.
 */
public final class WeaponBeamSprites implements Disposable {
    private static final String[] COLORS={"blue","red","green","violet","gold"};
    private static final int FRAMES=7;
    private final Texture[][] beams = new Texture[COLORS.length][FRAMES];
    private int loaded;

    public WeaponBeamSprites() {
        for(int color=0;color<COLORS.length;color++)for(int frame=0;frame<FRAMES;frame++){
            String filename=String.format(java.util.Locale.ROOT,"effects/beams/%s_%02d.png",
                    COLORS[color],frame+1);
            if(!Gdx.files.internal(filename).exists())continue;
            try{
                Texture texture=new Texture(Gdx.files.internal(filename));
                texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                beams[color][frame]=texture;
                loaded++;
            }catch(RuntimeException failure){
                Gdx.app.error("BEAM-FX","Could not load optional curated beam: "+filename,failure);
            }
        }
    }
    public int loaded(){return loaded;}

    private static int color(Battle.Beam beam){
        return switch(beam.type){
            case KINETIC -> 4; // gold
            case PLASMA -> 1; // red, matching crimson plasma atlas
            case PARTICLE -> 3; // violet
            case LASER -> 0; // blue, matching both firing and impact sprite rows
            case MISSILE,TORPEDO,FIGHTER -> -1;
        };
    }
    private Texture texture(Battle.Beam beam,long tick) {
        int color=color(beam);
        if(color<0)return null;
        // A fresh shot starts at its own first frame; never cycle from a global
        // clock (which could make a new muzzle flash appear mid-animation).
        int frame=(int)Math.min(FRAMES-1,Math.max(0,tick-beam.spawnTick));
        Texture selected=beams[color][frame];
        if(selected!=null)return selected;
        // A partial installation degrades to the first frame without producing blank shots.
        return beams[color][0];
    }
    public boolean canDraw(Battle.Beam beam,long tick){
        return texture(beam,tick)!=null;
    }

    private Texture supportTexture(Battle.SupportLink link,long tick){
        int color=switch(link.kind){
            case SHIELD,DRONE_SHIELD -> 0; // blue
            case ARMOR,DRONE_ARMOR -> 4; // gold
            case POWER,DRONE_POWER -> 3; // violet
            case HULL,DRONE -> 2; // green
        };
        int frame=(int)Math.min(FRAMES-1,Math.max(0,tick-link.spawnTick));
        Texture image=beams[color][frame];
        return image!=null?image:beams[color][0];
    }
    public boolean canDrawSupport(Battle.SupportLink link,long tick){
        return supportTexture(link,tick)!=null;
    }
    // A real gold beam-strip frame doubles as a SHORT ballistic tracer, not a
    // fabricated physical missile or a line drawn by ShapeRenderer.
    private Texture ballisticTexture(Battle.Ballistic shell,long tick) {
        int frame=(int)Math.min(FRAMES-1,Math.max(0,tick-shell.spawnTick));
        Texture selected=beams[4][frame];
        return selected!=null?selected:beams[4][0];
    }
    public boolean canDrawBallistic(Battle.Ballistic shell,long tick) {
        return ballisticTexture(shell,tick)!=null;
    }

    /** World-aligned textured beam geometry; its origin remains the actual simulation muzzle. */
    public void render(Battle battle,SpriteBatch batch) {
        render(battle,batch,shell -> true);
    }
    public void render(Battle battle,SpriteBatch batch,
                       java.util.function.Predicate<Battle.Ballistic> showTracer) {
        render(battle,batch,showTracer,null);
    }
    public void render(Battle battle,SpriteBatch batch,
                       java.util.function.Predicate<Battle.Ballistic> showTracer,
                       BattleRenderPoses poses) {
        if(loaded==0)return;
        batch.begin();
        batch.setColor(1,1,1,1);
        for(Battle.Beam beam:battle.beams()){
            Texture image=texture(beam,battle.tick);
            if(image==null)continue;
            double dx=beam.x2-beam.x1,dy=beam.y2-beam.y1;
            float length=(float)StrictMath.hypot(dx,dy);
            if(length<0.01f)continue;
            float thickness=CombatEffectScale.of(beam.type==Battle.DamageType.KINETIC?4.0f:6.3f);
            float rotation=(float)StrictMath.toDegrees(StrictMath.atan2(dy,dx));
            batch.draw(image,(float)beam.x1,(float)beam.y1-thickness/2,
                    0,thickness/2,length,thickness,1,1,rotation,
                    0,0,image.getWidth(),image.getHeight(),false,false);
        }
        for(Battle.SupportLink link:battle.supportLinks()){
            Texture image=supportTexture(link,battle.tick);
            if(image==null)continue;
            double dx=link.x2-link.x1,dy=link.y2-link.y1;
            float length=(float)StrictMath.hypot(dx,dy);
            if(length<0.01f)continue;
            float thickness=CombatEffectScale.of(link.kind==Battle.SupportKind.SHIELD?4.0f:2.8f);
            float rotation=(float)StrictMath.toDegrees(StrictMath.atan2(dy,dx));
            batch.draw(image,(float)link.x1,(float)link.y1-thickness/2,
                    0,thickness/2,length,thickness,1,1,rotation,
                    0,0,image.getWidth(),image.getHeight(),false,false);
        }
        // Spatially correct 2D tracer. Real ballistic collision remains entirely
        // simulation-controlled; never interpolate along a fictional hit line.
        int traced=0;
        for(Battle.Ballistic shell:battle.ballistics()){
            if(traced>=512)break;
            if(!showTracer.test(shell))continue;
            Texture image=ballisticTexture(shell,battle.tick);
            if(image==null)continue;
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)shell.x,(float)shell.y,
                            (float)StrictMath.atan2(shell.vy,shell.vx)):poses.shell(shell);
            float angle=(float)StrictMath.toDegrees(pose.angle());
            float length=CombatEffectScale.of(11.5f),thickness=CombatEffectScale.of(3.1f);
            batch.draw(image,pose.x()-length/2,pose.y()-thickness/2,
                    length/2,thickness/2,length,thickness,1,1,angle,
                    0,0,image.getWidth(),image.getHeight(),false,false);
            traced++;
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }
    @Override public void dispose(){
        for(Texture[] row:beams)for(int i=0;i<row.length;i++)if(row[i]!=null){
            row[i].dispose();row[i]=null;
        }
        loaded=0;
    }
}
