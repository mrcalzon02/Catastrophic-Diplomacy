package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;

/**
 * Bounded shield response geometry, positioned on interpolated moving hulls.
 * Existing original-art shield sector textures supply the actual shell surface;
 * these fine arcs merely add localized flashes, cracks and charge filaments.
 */
final class ShieldFxRenderer {
    private int drawn;
    void render(ShieldFxTimeline timeline,ShapeRenderer shapes,
                OrthographicCamera camera,BattleRenderPoses poses,
                CombatViewBounds visible){
        drawn=0;
        if(timeline.count()==0)return;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for(ShieldFxTimeline.Effect fx:timeline.events()){
            if(!fx.ship.alive()&&fx.kind!=ShieldFxTimeline.Kind.COLLAPSE)continue;
            BattleRenderPoses.Pose pose=poses.ship(fx.ship);
            float x=pose.x(),y=pose.y();
            float radius=(float)(16*fx.ship.visualScale());
            if(visible!=null){
                if(!visible.circle(x,y,radius*1.5f))continue;
                // Never fill a crowded far-field battle with subpixel overdraw.
                if(radius*1280.0/(visible.right-visible.left)<3.2)continue;
            }
            float progress=fx.progress();
            float decay=(1-progress)*(1-progress);
            float center=fx.localAngle+(float)StrictMath.toDegrees(pose.angle());
            float energy=fx.intensity;
            switch(fx.kind){
                case IMPACT -> impact(shapes,x,y,radius,center,progress,decay,energy,fx.weapon);
                case STRAIN -> strain(shapes,x,y,radius,center,progress,decay);
                case COLLAPSE -> collapse(shapes,x,y,radius,center,progress,decay);
                case RECHARGE -> recharge(shapes,x,y,radius,center,progress,decay,energy);
                case RESTART -> restart(shapes,x,y,radius,center,progress,decay);
            }
            drawn++;
        }
        shapes.setColor(Color.WHITE);
        shapes.end();
    }
    private static float radians(float degrees){return (float)StrictMath.toRadians(degrees);}
    /** Perimeter-only arc: ShapeRenderer.arc adds center spokes in Line mode. */
    static void arc(ShapeRenderer shapes,float x,float y,float radius,
                    float start,float sweep,int segments){
        if(radius<=0||sweep<=0||segments<1)return;
        float step=sweep/segments;
        float priorX=x+radius*(float)StrictMath.cos(radians(start));
        float priorY=y+radius*(float)StrictMath.sin(radians(start));
        for(int i=1;i<=segments;i++){
            float a=start+step*i;
            float nextX=x+radius*(float)StrictMath.cos(radians(a));
            float nextY=y+radius*(float)StrictMath.sin(radians(a));
            shapes.line(priorX,priorY,nextX,nextY);
            priorX=nextX;priorY=nextY;
        }
    }
    private static void lineAt(ShapeRenderer shapes,float x,float y,float radius,
                               float a,float length){
        float c=(float)StrictMath.cos(radians(a)),s=(float)StrictMath.sin(radians(a));
        shapes.line(x+c*radius,y+s*radius,x+c*(radius+length),y+s*(radius+length));
    }
    private static void impact(ShapeRenderer shapes,float x,float y,float radius,
                               float angle,float t,float fade,float power,
                               Battle.DamageType weapon){
        float r=radius*(1f+.12f*t);
        // Weapon response is compact and physically localized, never an
        // omnidirectional ring larger than the vessel.
        float red=.22f,green=.84f,blue=1f;
        if(weapon==Battle.DamageType.KINETIC){red=.85f;green=.88f;blue=1f;}
        else if(weapon==Battle.DamageType.PLASMA){red=1f;green=.55f;blue=.84f;}
        else if(weapon==Battle.DamageType.PARTICLE){red=.70f;green=.58f;blue=1f;}
        else if(weapon==Battle.DamageType.MISSILE||weapon==Battle.DamageType.TORPEDO){
            red=.67f;green=.88f;blue=1f;
        }
        shapes.setColor(red,green,blue,Math.min(1f,fade*(.60f+power*.35f)));
        arc(shapes,x,y,r,angle-30f-t*6f,60f+12f*t,10);
        shapes.setColor(.83f,.96f,1f,fade*.68f);
        arc(shapes,x,y,r*.90f,angle-17f,34f,7);
        if(weapon==Battle.DamageType.PARTICLE){
            shapes.setColor(.75f,.58f,1f,fade*.78f);
            // Two short deterministic electrical cracks across the contact.
            lineAt(shapes,x,y,r,angle-12f,2.2f*power);
            lineAt(shapes,x,y,r*.94f,angle+13f,2.8f*power);
        }else if(weapon==Battle.DamageType.KINETIC){
            shapes.setColor(1f,.94f,.73f,fade*.8f);
            for(int i=-1;i<=1;i++)lineAt(shapes,x,y,r,angle+i*12f,
                    2.7f*power*(1-t));
        }else if(weapon==Battle.DamageType.PLASMA){
            shapes.setColor(1f,.68f,.91f,fade*.73f);
            arc(shapes,x,y,r*1.04f,angle-13f,26f,5);
        }else if(weapon==Battle.DamageType.MISSILE||weapon==Battle.DamageType.TORPEDO){
            shapes.setColor(.58f,.83f,1f,fade*.52f);
            arc(shapes,x,y,r*(1f+.25f*t),angle-26f,52f,8);
        }else if(weapon==Battle.DamageType.LASER){
            shapes.setColor(.8f,.98f,1f,fade*.7f);
            lineAt(shapes,x,y,r*.76f,angle,r*.17f);
        }
    }
    /** Critical energy: restrained tangential discontinuities, not a collapse. */
    private static void strain(ShapeRenderer shapes,float x,float y,float radius,
                               float angle,float t,float fade){
        float r=radius*(1f+.045f*t);
        shapes.setColor(.72f,.88f,1f,.85f*fade);
        for(int i=0;i<4;i++){
            float phase=angle-36+i*21+(i%2==0?1:-1)*t*4f;
            arc(shapes,x,y,r,phase,9f,3);
        }
        shapes.setColor(.35f,.66f,1f,.45f*fade);
        for(int i=0;i<3;i++){
            float shift=angle-22+i*22f;
            lineAt(shapes,x,y,r*.97f,shift,(1-t)*radius*.065f);
        }
    }
    private static void collapse(ShapeRenderer shapes,float x,float y,float radius,
                                 float angle,float t,float fade){
        float r=radius*(1+.19f*t);
        shapes.setColor(.37f,.69f,1f,.89f*fade);
        // Fractured field sectors separate tangentially instead of a full
        // circular force-field explosion.
        for(int i=0;i<5;i++){
            float a=angle-38+i*18f+(i%2==0?-1:1)*t*8f;
            arc(shapes,x,y,r,a,10f-3f*t,3);
        }
        shapes.setColor(.85f,.97f,1f,.67f*fade);
        for(int i=-2;i<=2;i++)
            lineAt(shapes,x,y,r,angle+i*17f,
                    (1-t)*radius*(.09f+.02f*(i+2)));
    }
    private static void recharge(ShapeRenderer shapes,float x,float y,float radius,
                                 float angle,float t,float fade,float strength){
        float sweep=angle-36f+t*61f;
        shapes.setColor(.27f,.80f,1f,fade*.53f*strength);
        arc(shapes,x,y,radius*.98f,sweep,24f,5);
        shapes.setColor(.78f,.97f,1f,fade*.30f*strength);
        arc(shapes,x,y,radius*.90f,sweep+7f,10f,3);
    }
    private static void restart(ShapeRenderer shapes,float x,float y,float radius,
                                float angle,float t,float fade){
        // A small sweeping sector weld, not a globally flashed solid sphere.
        float sweep=Math.max(2f,78f*t);
        shapes.setColor(.46f,.91f,1f,.35f+.50f*fade);
        arc(shapes,x,y,radius,angle-39f,sweep,12);
        shapes.setColor(.88f,1f,1f,fade*.67f);
        lineAt(shapes,x,y,radius*.92f,angle-39f+sweep,radius*.08f);
    }
    int rendered(){return drawn;}
}
