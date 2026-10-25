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
            case PLASMA -> 2; // green
            case PARTICLE -> 3; // violet
            case LASER -> beam.team==0?0:1;
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

    /** World-aligned textured beam geometry; its origin remains the actual simulation muzzle. */
    public void render(Battle battle,SpriteBatch batch) {
        if(loaded==0)return;
        batch.begin();
        batch.setColor(1,1,1,1);
        for(Battle.Beam beam:battle.beams()){
            Texture image=texture(beam,battle.tick);
            if(image==null)continue;
            double dx=beam.x2-beam.x1,dy=beam.y2-beam.y1;
            float length=(float)StrictMath.hypot(dx,dy);
            if(length<0.01f)continue;
            float thickness=beam.type==Battle.DamageType.KINETIC?4.0f:6.3f;
            float rotation=(float)StrictMath.toDegrees(StrictMath.atan2(dy,dx));
            batch.draw(image,(float)beam.x1,(float)beam.y1-thickness/2,
                    0,thickness/2,length,thickness,1,1,rotation,
                    0,0,image.getWidth(),image.getHeight(),false,false);
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
