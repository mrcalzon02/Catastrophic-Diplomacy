package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Small, local shield arcs / plating sparks from real damage observations. */
final class CombatHitRenderer {
    private int drawn;
    void render(CombatHitFeedback feedback,ShapeRenderer shapes,
                OrthographicCamera camera,CombatViewBounds visible){
        drawn=0;
        if(feedback.count()==0)return;
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for(CombatHitFeedback.Pulse hit:feedback.pulses()){
            float t=hit.age/CombatHitFeedback.LIFETIME;
            if(t>=1)continue;
            float radius=hit.layer==CombatHitFeedback.Layer.SHIELD?
                    16*hit.scale:7*hit.scale;
            if(visible!=null&&!visible.circle(hit.x,hit.y,2*radius))continue;
            float fade=(1-t)*(1-t);
            if(hit.layer==CombatHitFeedback.Layer.SHIELD){
                // A partial sector on the correct side of the vessel, never a
                // huge full-screen shield disc or arbitrary omnidirectional hit.
                float angle=(float)Math.toDegrees(Math.atan2(hit.normalY,hit.normalX));
                shapes.setColor(.22f,.79f,1f,.85f*fade);
                shapes.arc(hit.shipX,hit.shipY,radius*(1+.20f*t),
                        angle-34,68,12);
                shapes.setColor(.70f,.94f,1f,.38f*fade);
                shapes.arc(hit.shipX,hit.shipY,radius*(.83f+.20f*t),
                        angle-22,44,9);
            }else{
                boolean armor=hit.layer==CombatHitFeedback.Layer.ARMOR;
                shapes.setColor(armor?
                        new Color(1f,.72f,.31f,.95f*fade):
                        new Color(1f,.38f,.31f,.72f*fade));
                float centerX=hit.x,centerY=hit.y;
                // Four coherent sparks emanate from the real impact position;
                // directional normal prevents the visual from bursting across
                // the opposite, unstruck side of the hull.
                for(int i=-2;i<=1;i++){
                    float deviation=i*.39f;
                    float dx=(float)(hit.normalX*Math.cos(deviation)-
                            hit.normalY*Math.sin(deviation));
                    float dy=(float)(hit.normalX*Math.sin(deviation)+
                            hit.normalY*Math.cos(deviation));
                    float length=(armor?6.2f:4.2f)*hit.scale*(1+.35f*t);
                    shapes.line(centerX,centerY,
                            centerX+dx*length,centerY+dy*length);
                }
            }
            drawn++;
        }
        shapes.setColor(Color.WHITE);
        shapes.end();
    }
    int rendered(){return drawn;}
}
