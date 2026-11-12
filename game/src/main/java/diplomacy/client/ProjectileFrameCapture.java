package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import diplomacy.sim.Battle;
import java.util.Locale;

/**
 * Explicit desktop visual diagnostic: capture the ACTUAL OpenGL battle
 * framebuffer with a simulated projectile centered at native, tactical, and
 * close-up camera scales. No generated artwork, physics adjustments, new
 * gameplay controls or synthetic projectile body is involved.
 *
 * This class is constructed only for CD_PROJECTILE_CAPTURE_DIR in the desktop
 * 200v200 verifier; it is absent in Android and ordinary desktop sessions.
 */
final class ProjectileFrameCapture {
    private static final int[] AT_TICK={15,45,85};
    private static final float[] ZOOM={1f,.42f,.20f};
    private static final String[] VIEW={"overview","tactical","detail"};
    private final String directory;
    private int stage;
    private long lastTick=-1,shotId;
    private double x,y;
    private String kind;
    private boolean pending;

    static ProjectileFrameCapture fromEnvironment(){
        String dir=System.getenv("CD_PROJECTILE_CAPTURE_DIR");
        return dir==null||dir.isBlank()?null:new ProjectileFrameCapture(dir);
    }
    private ProjectileFrameCapture(String directory){this.directory=directory;}

    void beforeDraw(Battle battle,OrthographicCamera camera){
        pending=false;
        if(stage>=AT_TICK.length||battle==null||camera==null||
                battle.tick<AT_TICK[stage]||
                lastTick>=0&&battle.tick<lastTick+10)return;
        // Pick actual simulator bodies. Each written filename also records the
        // physical class actually selected; no type is faked if none is live.
        if(stage==1&&!battle.ballistics().isEmpty()){
            Battle.Ballistic b=battle.ballistics().get(0);
            kind="KINETIC";shotId=b.visualId;x=b.x;y=b.y;
        }else if(stage==2&&!battle.rockets().isEmpty()){
            Battle.Rocket b=battle.rockets().get(0);
            kind="ROCKET";shotId=b.visualId;x=b.x;y=b.y;
        }else if(!battle.missiles().isEmpty()){
            Battle.Missile b=battle.missiles().get(0);
            kind=b.torpedo?"TORPEDO":"MISSILE";shotId=b.visualId;x=b.x;y=b.y;
        }else if(!battle.ballistics().isEmpty()){
            Battle.Ballistic b=battle.ballistics().get(0);
            kind="KINETIC";shotId=b.visualId;x=b.x;y=b.y;
        }else if(!battle.rockets().isEmpty()){
            Battle.Rocket b=battle.rockets().get(0);
            kind="ROCKET";shotId=b.visualId;x=b.x;y=b.y;
        }else return;
        // Actual in-game camera and renderers remain authoritative. Zooming
        // allows subpixel missiles to be inspected without rescaling sprites.
        camera.position.set((float)x,(float)y,0);
        camera.zoom=ZOOM[stage];
        camera.update();
        pending=true;
    }
    void afterDraw(Battle battle,int loadedWeaponTextures){
        if(!pending)return;
        pending=false;
        int width=Gdx.graphics.getBackBufferWidth();
        int height=Gdx.graphics.getBackBufferHeight();
        if(width<=0||height<=0)throw new IllegalStateException(
                "Projectile capture requires a live GL backbuffer");
        var folder=Gdx.files.absolute(directory);
        folder.mkdirs();
        String filename=String.format(Locale.ROOT,"%02d-%s-%s.png",
                stage+1,VIEW[stage],kind.toLowerCase(Locale.ROOT));
        byte[] rgba=ScreenUtils.getFrameBufferPixels(0,0,width,height,true);
        Pixmap pixmap=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try{
            pixmap.getPixels().put(rgba);
            PixmapIO.writePNG(folder.child(filename),pixmap);
        }finally{pixmap.dispose();}
        var csv=folder.child("frames.csv");
        if(!csv.exists())csv.writeString(
                "file,tick,kind,shot_id,world_x,world_y,zoom,width,height,loaded_weapon_textures\n",false);
        csv.writeString(String.format(Locale.ROOT,
                "%s,%d,%s,%d,%.6f,%.6f,%.4f,%d,%d,%d%n",
                filename,battle.tick,kind,shotId,x,y,
                ZOOM[stage],width,height,loadedWeaponTextures),true);
        Gdx.app.log("CD-PROJECTILE-CAPTURE",
                "ACTUAL-FRAME file="+filename+" tick="+battle.tick+
                " body="+kind+" id="+shotId+" zoom="+ZOOM[stage]+
                " loadedWeaponTextures="+loadedWeaponTextures);
        lastTick=battle.tick;
        stage++;
        if(stage==AT_TICK.length){
            Gdx.app.log("CD-PROJECTILE-CAPTURE",
                    "PASS three real desktop OpenGL projectile framebuffers");
            Gdx.app.exit();
        }
    }
}
