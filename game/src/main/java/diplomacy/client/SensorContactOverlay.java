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
    private Battle source;
    private long scannedTick=Long.MIN_VALUE;
    private int scannedTeam=-1;

    void refresh(Battle battle,int team){
        if(battle==source&&scannedTeam==team&&scannedTick!=Long.MIN_VALUE
                &&battle.tick>=scannedTick&&battle.tick-scannedTick<6)return;
        source=battle;scannedTeam=team;scannedTick=battle.tick;
        contacts.clear();
        for(Battle.TacticalContact report:battle.tacticalPicture(team))
            contacts.put(report.targetId(),report);
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
