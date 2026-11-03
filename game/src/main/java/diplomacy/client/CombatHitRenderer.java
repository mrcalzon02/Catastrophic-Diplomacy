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
            // The authoritative per-facing ShieldFxRenderer owns shield
            // contacts. The legacy duplicate arc would overexpose every hit.
            if(hit.layer==CombatHitFeedback.Layer.SHIELD)continue;
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
                ShieldFxRenderer.arc(shapes,hit.shipX,hit.shipY,radius*(1+.20f*t),
                        angle-34,68,12);
                shapes.setColor(.70f,.94f,1f,.38f*fade);
                ShieldFxRenderer.arc(shapes,hit.shipX,hit.shipY,radius*(.83f+.20f*t),
                        angle-22,44,9);
            }else{
                boolean armor=hit.layer==CombatHitFeedback.Layer.ARMOR;
                float centerX=hit.x,centerY=hit.y;
                // No generic crater circle: the actual authored alpha/melt
                // mask owns the shape and the laser anneal's smooth outer edge.
                // Seeded fan scatter is stable across frames and replays.
                int sparks=sparkCount(hit.weapon,armor);
                float lengthScale=hit.weapon==diplomacy.sim.Battle.DamageType.PARTICLE?.68f:
                        hit.weapon==diplomacy.sim.Battle.DamageType.TORPEDO?1.12f:1f;
                float rr=1f,gg=.72f,bb=.31f;
                if(hit.weapon==diplomacy.sim.Battle.DamageType.KINETIC){
                    rr=.82f;gg=.87f;bb=.95f;
                }else if(hit.weapon==diplomacy.sim.Battle.DamageType.PLASMA){
                    rr=1f;gg=.43f;bb=.17f;
                }else if(hit.weapon==diplomacy.sim.Battle.DamageType.PARTICLE){
                    rr=.85f;gg=.58f;bb=1f;
                }else if(hit.weapon==diplomacy.sim.Battle.DamageType.LASER){
                    rr=1f;gg=.87f;bb=.64f;
                }
                float fade=(1-t)*(1-t);
                shapes.setColor(rr,gg,bb,(armor?.82f:.94f)*fade);
                for(int i=0;i<sparks;i++){
                    int h=ShipDamageVariants.hash(hit.visualSeed+i*719,
                            hit.weapon);
                    float spread=((h>>>9&1023)/1023f-.5f)*1.8f;
                    float cosine=(float)StrictMath.cos(spread);
                    float sine=(float)StrictMath.sin(spread);
                    float dx=hit.normalX*cosine-hit.normalY*sine;
                    float dy=hit.normalX*sine+hit.normalY*cosine;
                    float distance=(.34f+(h>>>20&255)/255f*.66f)*
                            (armor?6f:5.2f)*hit.scale*lengthScale;
                    // Sparks LEAVE the puncture, never fan across the entire hull.
                    float drift=distance*(.2f+.75f*t);
                    float startX=centerX+dx*drift;
                    float startY=centerY+dy*drift;
                    shapes.line(startX,startY,
                            startX+dx*distance*(.16f+.14f*(1f-t)),
                            startY+dy*distance*(.16f+.14f*(1f-t)));
                }
            }
            drawn++;
        }
        shapes.setColor(Color.WHITE);
        shapes.end();
    }
    static int sparkCount(diplomacy.sim.Battle.DamageType weapon,boolean armor){
        return switch(weapon==null?diplomacy.sim.Battle.DamageType.KINETIC:weapon){
            case PARTICLE, FIGHTER -> armor?3:4;
            case TORPEDO, MISSILE -> armor?5:6;
            case PLASMA -> armor?4:5;
            case LASER -> armor?3:4;
            default -> armor?4:5;
        };
    }
    int rendered(){return drawn;}
}
