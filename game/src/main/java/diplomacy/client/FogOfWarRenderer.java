package diplomacy.client;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import diplomacy.sim.Battle;
import diplomacy.sim.FogOfWarModel;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Soft, low-resolution optical visibility mask over the REAL completed battle
 * framebuffer. Texture filtering blends the gradient without a new FBO,
 * fullscreen shader, stencil buffer, or a per-ship material rewrite.
 * Sensor-only returns are drawn AFTER this mask by SensorContactOverlay.
 */
final class FogOfWarRenderer implements AutoCloseable {
    private static final int MASK_W=320,MASK_H=180;
    private static final float SCREEN_W=1280f,SCREEN_H=720f;
    private final float[] revealed=new float[MASK_W*MASK_H];
    // Reused scratch columns: a scout's horizontal distance is constant for
    // every mask row, so avoid calculating it once per pixel and ship.
    private final double[] columnDxSquared=new double[MASK_W];
    private final FogMaskCoordinates pixelWorld=new FogMaskCoordinates();
    private final Pixmap bitmap=new Pixmap(MASK_W,MASK_H,Pixmap.Format.RGBA8888);
    private final Texture texture=new Texture(bitmap);
    private Battle lastBattle;
    private long lastTick=Long.MIN_VALUE;
    private float lastX=Float.NaN,lastY=Float.NaN,lastZoom=Float.NaN;
    FogOfWarRenderer(){
        texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        // The concealed-space RGB color never changes. Initialize it once
        // and update only alpha on later mask rebuilds.
        FogMaskEncoding.initialize(bitmap.getPixels(),MASK_W*MASK_H);
    }
    void render(Battle battle,OrthographicCamera world,OrthographicCamera hud,
                SpriteBatch batch,BattleRenderPoses poses,int team,
                SensorContactOverlay known){
        if(battle==null)return;
        boolean moved=!Float.isFinite(lastX)
                ||Math.abs(world.position.x-lastX)>world.zoom*3
                ||Math.abs(world.position.y-lastY)>world.zoom*3
                ||Math.abs(world.zoom-lastZoom)>.005f;
        if(battle!=lastBattle || lastTick==Long.MIN_VALUE
                ||battle.tick<lastTick ||battle.tick-lastTick>=6 ||moved){
            updateMask(battle,world,poses,team,known);
            lastBattle=battle;lastTick=battle.tick;
            lastX=world.position.x;lastY=world.position.y;lastZoom=world.zoom;
        }
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texture,0,0,SCREEN_W,SCREEN_H);
        batch.end();
    }
    private void updateMask(Battle battle,OrthographicCamera camera,
                            BattleRenderPoses poses,int team,SensorContactOverlay known){
        Arrays.fill(revealed,0f);
        double range=FogOfWarModel.opticalRange(battle.weather());
        double inner=range*.84,outer=range*1.14;
        double inner2=inner*inner,outer2=outer*outer;
        float zoom=camera.zoom;
        float pxWorld=SCREEN_W*zoom/MASK_W;
        float pyWorld=SCREEN_H*zoom/MASK_H;
        pixelWorld.update(camera.position.x,camera.position.y,zoom);
        // Bounded per-source rectangles avoid 200 x full-mask work in 200v200.
        for(Battle.Ship scout:battle.ships()){
            if(!scout.alive()||scout.team!=team)continue;
            BattleRenderPoses.Pose pose=poses.ship(scout);
            double left=pose.x()-outer, right=pose.x()+outer;
            double bottom=pose.y()-outer,top=pose.y()+outer;
            int x0=Math.max(0,(int)Math.floor((left-camera.position.x)/pxWorld+MASK_W*.5));
            int x1=Math.min(MASK_W-1,(int)Math.ceil((right-camera.position.x)/pxWorld+MASK_W*.5));
            // Pixmap rows start at the TOP, unlike the battle camera.
            int y0=Math.max(0,(int)Math.floor(MASK_H*.5-(top-camera.position.y)/pyWorld));
            int y1=Math.min(MASK_H-1,(int)Math.ceil(MASK_H*.5-(bottom-camera.position.y)/pyWorld));
            if(x0>x1||y0>y1)continue;
            for(int x=x0;x<=x1;x++){
                double wx=pixelWorld.x(x);
                double dx=wx-pose.x();
                columnDxSquared[x]=dx*dx;
            }
            for(int y=y0;y<=y1;y++){
                double wy=pixelWorld.y(y);
                double dy=wy-pose.y();
                double dySquared=dy*dy;
                for(int x=x0;x<=x1;x++){
                    int offset=y*MASK_W+x;
                    if(revealed[offset]>=1f)continue;
                    double d2=columnDxSquared[x]+dySquared;
                    if(d2>=outer2)continue;
                    float v=d2<=inner2?1f:FogOfWarModel.visible(Math.sqrt(d2),range);
                    if(v>revealed[offset])revealed[offset]=v;
                }
            }
        }
        // Very large optically discernible silhouettes can exceed the generic
        // visibility radius. Reveal their REAL hull pixels with a local soft
        // optical patch, never a fabricated replacement silhouette.
        for(Battle.Ship target:battle.ships()){
            if(!target.alive()||target.team==team||!known.opticallyVisible(target.id))
                continue;
            BattleRenderPoses.Pose pose=poses.ship(target);
            double radius=Math.max(22,30*target.visualScale());
            double outerRadius=radius*1.35;
            double outerSq=outerRadius*outerRadius;
            int x0=Math.max(0,(int)Math.floor((pose.x()-outerRadius-camera.position.x)
                    /pxWorld+MASK_W*.5));
            int x1=Math.min(MASK_W-1,(int)Math.ceil((pose.x()+outerRadius-camera.position.x)
                    /pxWorld+MASK_W*.5));
            int y0=Math.max(0,(int)Math.floor(MASK_H*.5-
                    (pose.y()+outerRadius-camera.position.y)/pyWorld));
            int y1=Math.min(MASK_H-1,(int)Math.ceil(MASK_H*.5-
                    (pose.y()-outerRadius-camera.position.y)/pyWorld));
            if(x0>x1||y0>y1)continue;
            for(int x=x0;x<=x1;x++){
                double dx=pixelWorld.x(x)-pose.x();
                columnDxSquared[x]=dx*dx;
            }
            for(int y=y0;y<=y1;y++){
                double dy=pixelWorld.y(y)-pose.y();
                double dySquared=dy*dy;
                for(int x=x0;x<=x1;x++){
                    int index=y*MASK_W+x;
                    if(revealed[index]>=1f)continue;
                    double d2=columnDxSquared[x]+dySquared;
                    if(d2>outerSq)continue;
                    float value=d2<=radius*radius?1f:
                            FogOfWarModel.visible(Math.sqrt(d2),outerRadius/1.14);
                    if(value>revealed[index])revealed[index]=value;
                }
            }
        }
        FogMaskEncoding.updateAlpha(bitmap.getPixels(),revealed);
        texture.draw(bitmap,0,0);
    }
    @Override public void close(){
        texture.dispose();
        bitmap.dispose();
    }
}
