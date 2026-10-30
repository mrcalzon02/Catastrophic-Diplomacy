package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.List;

/**
 * Bounded render-only weapon-light candidates, shared by hull and fighter passes.
 * No heap allocation or per-vessel search on the hot path, no combat-state edits.
 * Two strongest visible local sources are selected each frame, with deterministic ties.
 */
final class ShipLightProbes {
    static final int COUNT=2;
    private final float[] x=new float[COUNT],y=new float[COUNT],
            radius=new float[COUNT],power=new float[COUNT],
            red=new float[COUNT],green=new float[COUNT],blue=new float[COUNT],
            score=new float[COUNT];
    private CombatViewBounds bounds;
    private static final int MAX_EVENTS=96,MAX_BEAMS=128,MAX_MISSILES=128;
    void reset(CombatViewBounds camera) {
        bounds=camera;
        for(int i=0;i<COUNT;i++) {
            score[i]=-1f; power[i]=0f;radius[i]=1f;x[i]=y[i]=0f;
            red[i]=green[i]=blue[i]=0f;
        }
    }
    private void copy(int from,int to) {
        x[to]=x[from];y[to]=y[from];radius[to]=radius[from];
        power[to]=power[from];score[to]=score[from];
        red[to]=red[from];green[to]=green[from];blue[to]=blue[from];
    }
    /**
     * Pure bounded priority ranking. Require screen intersection (not just
     * camera-center lights); strong and nearby lights outrank distant flashes.
     */
    void offer(double px,double py,float reach,float strength,float r,float g,float b) {
        if(bounds==null||!Double.isFinite(px)||!Double.isFinite(py)||
                !(reach>0)||!(strength>0)||!bounds.circle(px,py,reach))return;
        double cx=(bounds.left+bounds.right)*.5,cy=(bounds.bottom+bounds.top)*.5;
        double dx=px-cx,dy=py-cy;
        double scale=Math.max(1,Math.min(bounds.right-bounds.left,bounds.top-bounds.bottom));
        float priority=(float)(strength/(1.0+(dx*dx+dy*dy)/(scale*scale)));
        int slot=-1;
        if(priority>score[0]) {
            copy(0,1);slot=0;
        }else if(priority>score[1])slot=1;
        if(slot<0)return;
        x[slot]=(float)px;y[slot]=(float)py;
        radius[slot]=reach;power[slot]=strength;score[slot]=priority;
        red[slot]=r;green[slot]=g;blue[slot]=b;
    }
    void select(Battle battle,CombatViewBounds view) {
        reset(view);
        if(battle==null||view==null)return;
        // Events are the authoritative FIRE, IMPACT and INTERCEPT records.
        // TIMEOUT is deliberately excluded: it must not create phantom impact light.
        List<Battle.VisualEvent> events=battle.visualEvents();
        for(int i=events.size()-1;i>=Math.max(0,events.size()-MAX_EVENTS);i--) {
            Battle.VisualEvent event=events.get(i);
            long age=battle.tick-event.tick();
            if(age<0)continue;
            if(age>9)break;
            if(event.phase()==Battle.VisualPhase.TIMEOUT)continue;
            float strength=event.phase()==Battle.VisualPhase.IMPACT?.75f:
                    event.phase()==Battle.VisualPhase.INTERCEPT?.56f:.39f;
            strength*=Math.max(0f,1f-age/10f);
            float reach=event.phase()==Battle.VisualPhase.IMPACT?145f:108f;
            offerWeapon(event.x(),event.y(),reach,strength,event.weapon());
        }
        int counted=0;
        for(Battle.Beam beam:battle.beams()) {
            if(++counted>MAX_BEAMS)break;
            offerWeapon(beam.x1,beam.y1,110f,.43f,beam.type.name());
            offerWeapon(beam.x2,beam.y2,150f,.55f,beam.type.name());
        }
        counted=0;
        for(Battle.Missile missile:battle.missiles()) {
            if(++counted>MAX_MISSILES)break;
            if(missile.ignitionDelayTicks>0||missile.fuel<=0)continue;
            offerWeapon(missile.x,missile.y,missile.torpedo?108f:74f,
                    missile.torpedo?.50f:.37f,missile.torpedo?"TORPEDO":"MISSILE");
        }
    }
    private void offerWeapon(double x,double y,float range,float gain,String kind) {
        switch(kind) {
            case "KINETIC","FLAK" -> offer(x,y,range,gain,1f,.69f,.28f);
            case "PLASMA" -> offer(x,y,range,gain,1f,.25f,.23f);
            case "PARTICLE" -> offer(x,y,range,gain,.68f,.48f,1f);
            case "MISSILE","TORPEDO","DECOY" -> offer(x,y,range,gain,1f,.53f,.19f);
            default -> offer(x,y,range,gain,.35f,.85f,1f);
        }
    }
    float x(int i){return x[i];}
    float y(int i){return y[i];}
    float radius(int i){return radius[i];}
    float power(int i){return power[i];}
    float red(int i){return red[i];}
    float green(int i){return green[i];}
    float blue(int i){return blue[i];}
    /** High zoom retains detail; wide tactical overview remains cheap. */
    static float detail(CombatViewBounds view) {
        if(view==null)return 1f;
        double width=view.right-view.left;
        if(width<=1000)return 1f;
        if(width>=4000)return 0f;
        return (float)((4000-width)/3000);
    }
}
