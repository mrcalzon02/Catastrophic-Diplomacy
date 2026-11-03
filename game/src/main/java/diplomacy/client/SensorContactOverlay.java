package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.DeceptionProjection;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.SensorLeadProjection;
import diplomacy.sim.GuidedTrajectoryPrediction;
import diplomacy.sim.RocketBarrage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Actual command-ship picture from Battle's authoritative, READ-ONLY sensor
 * routing. Direct optical contacts can reveal physical hull pixels. Remote
 * SHR reports, regardless of optical channel, remain instrument-only, while
 * TRK/LNK memory is drawn at its recorded coordinates, never target truth.
 */
final class SensorContactOverlay {
    private final Map<Integer,Battle.TacticalContact> contacts=new TreeMap<>();
    /** At most eight prioritized read-only lead predictions in 200v200. */
    private final List<Battle.TacticalContact> leadCandidates=new ArrayList<>();
    private static final int MAX_LEAD_PIPS=8;
    private static final int MAX_TRAJECTORY_LINES=3;
    private Battle source;
    private long scannedTick=Long.MIN_VALUE;
    private int scannedTeam=-1;

    void refresh(Battle battle,int team){
        if(battle==source&&scannedTeam==team&&scannedTick!=Long.MIN_VALUE
                &&battle.tick>=scannedTick&&battle.tick-scannedTick<6)return;
        source=battle;scannedTeam=team;scannedTick=battle.tick;
        contacts.clear();
        leadCandidates.clear();
        for(Battle.TacticalContact report:battle.tacticalPicture(team))
            contacts.put(report.targetId(),report);
        Battle.Ship captain=battle.tacticalViewer(team);
        if(captain!=null){
            leadCandidates.addAll(contacts.values());
            // Prefer received fresh tracks, then nearby contact fixes. The
            // ordering uses no hidden target current position/ship identity.
            leadCandidates.sort(Comparator
                    .comparing(Battle.TacticalContact::stale)
                    .thenComparingDouble(c->StrictMath.hypot(
                            c.x()-captain.x,c.y()-captain.y))
                    .thenComparingInt(Battle.TacticalContact::targetId));
            if(leadCandidates.size()>MAX_LEAD_PIPS)
                leadCandidates.subList(MAX_LEAD_PIPS,leadCandidates.size()).clear();
        }
    }

    boolean opticallyVisible(int id){
        Battle.TacticalContact report=contacts.get(id);
        return report!=null&&!report.stale()&&!report.relayed()
                &&(report.channels()&Battle.CONTACT_OPTICAL)!=0;
    }
    private static boolean instrumentOnly(Battle.TacticalContact report){
        return report.stale()||report.relayed()
                ||(report.channels()&Battle.CONTACT_OPTICAL)==0;
    }

    /** A physically resolved RAD return, not an invented fully rendered hull. */
    private static void radarHull(ShapeRenderer shapes,Battle.Ship ship,
                                  float x,float y,float bearing){
        Shipyard.Hull hull=ship.hullClass==null?Shipyard.Hull.CORVETTE:ship.hullClass;
        List<HullLayouts.Point> points=HullLayouts.outline(ship.faction,hull.spriteHull());
        float angle=(float)(bearing-Math.PI/2);
        float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
        float scale=(float)ship.visualScale()*.2f;
        for(int i=0;i<points.size();i++){
            HullLayouts.Point a=points.get(i),b=points.get((i+1)%points.size());
            float ax=x+scale*(a.x()*c-a.y()*s);
            float ay=y+scale*(a.x()*s+a.y()*c);
            float bx=x+scale*(b.x()*c-b.y()*s);
            float by=y+scale*(b.x()*s+b.y()*c);
            shapes.line(ax,ay,bx,by);
        }
    }
    private static String label(int mask){
        StringBuilder out=new StringBuilder();
        if((mask&Battle.CONTACT_OPTICAL)!=0)out.append("VIS");
        if((mask&Battle.CONTACT_RADAR)!=0){
            if(out.length()>0)out.append('/');
            out.append("RAD");
        }
        if((mask&Battle.CONTACT_INFRARED)!=0){
            if(out.length()>0)out.append('/');
            out.append("IR");
        }
        if((mask&Battle.CONTACT_XRAY)!=0){
            if(out.length()>0)out.append('/');
            out.append("XR");
        }
        if((mask&Battle.CONTACT_GRAVITIC)!=0){
            if(out.length()>0)out.append('/');
            out.append("GRAV");
        }
        return out.toString();
    }
    private static Battle.Ship ship(Battle battle,int id){
        for(Battle.Ship candidate:battle.ships())if(candidate.id==id)return candidate;
        return null;
    }
    private static float x(Battle.TacticalContact mark){return (float)mark.x();}
    private static float y(Battle.TacticalContact mark){return (float)mark.y();}

    void render(Battle battle,OrthographicCamera world,OrthographicCamera hud,
                BattleRenderPoses poses,ShapeRenderer shapes,
                SpriteBatch batch,BitmapFont font,int team){
        refresh(battle,team);
        CombatViewBounds view=CombatViewBounds.from(world);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(world.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(Battle.TacticalContact mark:contacts.values()){
            if(!instrumentOnly(mark)||!view.circle(x(mark),y(mark),45))continue;
            float cx=x(mark),cy=y(mark),r=Math.max(2f,world.zoom*5f);
            int channels=mark.channels();
            if(mark.stale()){
                // History is a recorded point fix, not a texture or live heat.
                shapes.setColor(.58f,.62f,.70f,.38f);
                shapes.circle(cx,cy,r*.65f,12);
            }else if((channels&Battle.CONTACT_INFRARED)!=0){
                shapes.setColor(1f,.28f,.06f,.82f);
                shapes.circle(cx,cy,r*1.5f,18);
                shapes.setColor(1f,.82f,.27f,1f);
                shapes.circle(cx,cy,r*.52f,12);
            }else if((channels&Battle.CONTACT_RADAR)==0){
                if((channels&Battle.CONTACT_XRAY)!=0)
                    shapes.setColor(.75f,.44f,1f,.8f);
                else if((channels&Battle.CONTACT_GRAVITIC)!=0)
                    shapes.setColor(.28f,1f,.72f,.7f);
                else
                    shapes.setColor(.54f,.82f,1f,.78f);
                shapes.circle(cx,cy,r*.55f,12);
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for(Battle.TacticalContact mark:contacts.values()){
            if(!instrumentOnly(mark)||!view.circle(x(mark),y(mark),55))continue;
            float cx=x(mark),cy=y(mark),r=Math.max(2.5f,world.zoom*8f);
            int channels=mark.channels();
            if(mark.stale()){
                // Uncertainty expands with elapsed observation age. Never
                // silently project the current target's true position.
                float age=(float)Math.max(0,battle.tick-mark.observedTick())/30f;
                float uncertainty=Math.min(80f,r+age*2.4f);
                shapes.setColor(.56f,.65f,.77f,.46f);
                shapes.circle(cx,cy,uncertainty,24);
                shapes.line(cx-r*.5f,cy,cx+r*.5f,cy);
                shapes.line(cx,cy-r*.5f,cx,cy+r*.5f);
                continue;
            }
            if((channels&Battle.CONTACT_RADAR)!=0){
                Battle.Ship target=ship(battle,mark.targetId());
                shapes.setColor(.28f,.85f,1f,.90f);
                if(target!=null&&mark.confidence()>=.35f){
                    // Only a sufficiently classified LIVE radar fix gets
                    // a real hull outline. Low-confidence returns stay dots.
                    BattleRenderPoses.Pose pose=poses.ship(target);
                    radarHull(shapes,target,cx,cy,pose.angle());
                    if(!mark.relayed())
                        for(int echo=0;echo<DeceptionProjection.falseContacts(target);echo++){
                            float gx=(float)DeceptionProjection.ghostX(target,echo,battle.tick);
                            float gy=(float)DeceptionProjection.ghostY(target,echo,battle.tick);
                            if(view.circle(gx,gy,50*target.visualScale()))
                                radarHull(shapes,target,gx,gy,pose.angle());
                        }
                }else shapes.circle(cx,cy,r,20);
            }
            if((channels&Battle.CONTACT_XRAY)!=0){
                shapes.setColor(.76f,.46f,1f,.84f);
                shapes.line(cx,cy+r,cx+r,cy);
                shapes.line(cx+r,cy,cx,cy-r);
                shapes.line(cx,cy-r,cx-r,cy);
                shapes.line(cx-r,cy,cx,cy+r);
            }
            if((channels&Battle.CONTACT_GRAVITIC)!=0){
                shapes.setColor(.28f,1f,.72f,.67f);
                float pulse=1f+(battle.tick%45)/45f*.35f;
                shapes.circle(cx,cy,r*1.25f*pulse,24);
                shapes.circle(cx,cy,r*2.0f*pulse,32);
            }
        }
        // Fire-control leading pips use ONLY received contacts, not ship(target)
        // or an actual future position. Render restrained lines for the first
        // three high-priority tracks; other contacts get a small lead reticle.
        Battle.Ship captain=battle.tacticalViewer(team);
        if(captain!=null&&world.zoom<=3.0f){
            // Every tracked contact gets a pip, including on beam-only
            // command ships. A ballistic trajectory line is drawn only
            // when an actual kinetic or dumbfire-rocket weapon is fitted.
            boolean gun=captain.kineticDamage>0;
            boolean rocket=!gun&&!captain.rocketRacks.isEmpty();
            boolean ballistic=gun||rocket;
            RocketBarrage.Rack rack=rocket?captain.rocketRacks.get(0):null;
            double speed=gun?AmmunitionTable.kinetic(captain.kineticType)
                    .physical(captain.kineticProjectile).speed():
                    1_000_000_000.0; // Current main beams are instant ray hits.
            double precision=battle.targetingPrecision(captain.id);
            int count=0;
            for(Battle.TacticalContact mark:leadCandidates){
                SensorLeadProjection.Lead pip=rocket?
                        SensorLeadProjection.predictRocket(
                                mark,battle.tick,captain.x,captain.y,rack,precision):
                        SensorLeadProjection.predict(
                                mark,battle.tick,captain.x,captain.y,speed,precision);
                float px=(float)pip.predictedX(),py=(float)pip.predictedY();
                if(!view.circle(px,py,80))continue;
                float extent=Math.max(world.zoom*4.0f,2.0f);
                float sigma=(float)StrictMath.min(80,pip.uncertaintyRadius());
                if(!pip.reachable())shapes.setColor(.72f,.45f,.45f,.46f);
                else if(mark.stale())shapes.setColor(.65f,.69f,.78f,.55f);
                else shapes.setColor(1f,.83f,.36f,.78f);
                shapes.line(px-extent,py,px+extent,py);
                shapes.line(px,py-extent,px,py+extent);
                shapes.circle(px,py,extent*.65f,12);
                shapes.circle(px,py,Math.max(extent*1.3f,sigma),28);
                shapes.line((float)mark.x(),(float)mark.y(),px,py);
                if(ballistic&&pip.reachable()&&count<MAX_TRAJECTORY_LINES){
                    // Straight unguided projectile path, not an invented
                    // missile guidance envelope or omniscient actual hit.
                    shapes.setColor(.94f,.74f,.32f,mark.stale()?.18f:.30f);
                    shapes.line((float)pip.launchX(),(float)pip.launchY(),
                            (float)pip.trajectoryX(1),(float)pip.trajectoryY(1));
                }
                count++;
            }
        }
        // Up to three friendly, actually launched guided bodies. These
        // trajectories use each body's last measured/noisy photon fix and
        // paid propulsion. They NEVER reveal target truth or promise impact.
        if(captain!=null&&world.zoom<=3.0f){
            int guided=0;
            for(Battle.Missile body:battle.missiles()){
                if(guided>=3)break;
                if(body.team!=team||body.sourceId!=captain.id||
                        !view.circle((float)body.x,(float)body.y,100))
                    continue;
                GuidedTrajectoryPrediction.Forecast forecast=
                        GuidedTrajectoryPrediction.predict(body,battle.tick);
                if(forecast.path().size()<2)continue;
                if(forecast.plausible())shapes.setColor(.52f,.80f,1f,.26f);
                else shapes.setColor(.86f,.52f,.52f,.23f);
                var route=forecast.path();
                for(int p=1;p<route.size();p++){
                    var from=route.get(p-1);var to=route.get(p);
                    shapes.line((float)from.x(),(float)from.y(),
                            (float)to.x(),(float)to.y());
                }
                float px=(float)forecast.estimatedTargetX();
                float py=(float)forecast.estimatedTargetY();
                if(view.circle(px,py,90)){
                    float sigma=(float)StrictMath.min(90,forecast.uncertaintyRadius());
                    shapes.circle(px,py,StrictMath.max(5f,sigma),24);
                    shapes.line(px-4,py,px+4,py);
                    shapes.line(px,py-4,px,py+4);
                }
                guided++;
            }
        }
        shapes.end();
        if(world.zoom>2.25f||contacts.size()>64)return;
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        for(Battle.TacticalContact mark:contacts.values()){
            if(!instrumentOnly(mark))continue;
            float sx=(x(mark)-world.position.x)/world.zoom+640f;
            float sy=(y(mark)-world.position.y)/world.zoom+360f;
            if(sx<16||sx>1180||sy<75||sy>600)continue;
            font.setColor(mark.stale()?Color.LIGHT_GRAY:Color.WHITE);
            String description;
            if(mark.stale()){
                long age=Math.max(0,battle.tick-mark.observedTick())/30;
                description="LAST FIX "+age+"s";
            }else{
                description=label(mark.channels());
                if(mark.relayed())description+=" LINK";
                if(mark.confidence()>=.35f &&
                        (mark.channels()&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY))!=0){
                    Battle.Ship target=ship(battle,mark.targetId());
                    if(target!=null&&target.hullClass!=null)
                        description+=" "+target.hullClass.spriteHull().name().replace('_',' ');
                }
            }
            font.draw(batch,description,sx+12,sy+20);
        }
        font.setColor(Color.WHITE);
        batch.end();
    }
}
