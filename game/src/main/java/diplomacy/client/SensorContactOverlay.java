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
import diplomacy.sim.FogOfWarModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actual direct multi-spectral returns drawn above the optical fog.
 * No ship artwork is substituted for infrared, X-ray or gravitic contacts.
 * This player-command display surveys owned hulls; it does not grant a
 * ship-to-ship datalink or change the combat targeting model.
 */
final class SensorContactOverlay {
    private final Map<Integer,Integer> contacts=new HashMap<>();
    private final Map<Integer,Float> classification=new HashMap<>();
    private Battle source;
    private long scannedTick=Long.MIN_VALUE;
    private int scannedTeam=-1;
    void refresh(Battle battle,int team){
        if(battle==source&&scannedTeam==team&&scannedTick!=Long.MIN_VALUE
                &&battle.tick>=scannedTick&&battle.tick-scannedTick<6)return;
        source=battle;scannedTeam=team;scannedTick=battle.tick;
        contacts.clear();
        classification.clear();
        for(Battle.Ship target:battle.ships()){
            if(!target.alive()||target.team==team)continue;
            int found=0;
            float bestQuality=0;
            for(Battle.Ship spotter:battle.ships()){
                if(!spotter.alive()||spotter.team!=team)continue;
                double range=Math.hypot(target.x-spotter.x,target.y-spotter.y);
                int channels=battle.directSensorChannels(spotter,target,range);
                found|=channels;
                if(channels!=0){
                    // RES improves classification through actual live channels.
                    // Several independent returns are easier to correlate;
                    // a fleet command core supplies additional correlation.
                    float live=spotter.power>3 && spotter.sensorsCondition>.05
                            ?(float)Math.min(1,spotter.sensorsCondition):0;
                    float quality=(float)(.20+.50*
                            (spotter.network.resolutionGain()-1)*live
                            +.09*Math.max(0,Integer.bitCount(channels)-1)
                            +.20*spotter.commandAssist);
                    bestQuality=Math.max(bestQuality,Math.min(1,quality));
                }
                if((found & Battle.CONTACT_OPTICAL)!=0)break;
            }
            if(found!=0){
                contacts.put(target.id,found);
                classification.put(target.id,bestQuality);
            }
        }
    }
    boolean opticallyVisible(int id){
        return (contacts.getOrDefault(id,0)&Battle.CONTACT_OPTICAL)!=0;
    }
    /** Radar displays the TRUE faction hull profile, never a solid fake ship. */
    private static void radarHull(ShapeRenderer shapes,Battle.Ship ship,
                                  BattleRenderPoses.Pose pose){
        radarHull(shapes,ship,pose.x(),pose.y(),pose.angle());
    }
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
        if((mask&Battle.CONTACT_RADAR)!=0)out.append("RAD");
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
    void render(Battle battle,OrthographicCamera world,OrthographicCamera hud,
                BattleRenderPoses poses,ShapeRenderer shapes,
                SpriteBatch batch,BitmapFont font,int team){
        refresh(battle,team);
        CombatViewBounds view=CombatViewBounds.from(world);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(world.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(Battle.Ship target:battle.ships()){
            int mask=contacts.getOrDefault(target.id,0);
            if(mask==0||(mask&Battle.CONTACT_OPTICAL)!=0)continue;
            BattleRenderPoses.Pose pose=poses.ship(target);
            if(!view.ship(pose,target.visualScale()))continue;
            float x=pose.x(),y=pose.y(),r=Math.max(2f,world.zoom*5f);
            // Thermal is a HEAT POINT, not an otherwise invisible hull sprite.
            if((mask&Battle.CONTACT_INFRARED)!=0){
                shapes.setColor(1f,.28f,.06f,.82f);
                shapes.circle(x,y,r*1.5f,18);
                shapes.setColor(1f,.82f,.27f,1f);
                shapes.circle(x,y,r*.52f,12);
            }else if((mask&Battle.CONTACT_RADAR)==0){
                if((mask&Battle.CONTACT_XRAY)!=0)
                    shapes.setColor(.75f,.44f,1f,.8f);
                else shapes.setColor(.28f,1f,.72f,.7f);
                shapes.circle(x,y,r*.55f,12);
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for(Battle.Ship target:battle.ships()){
            int mask=contacts.getOrDefault(target.id,0);
            if(mask==0||(mask&Battle.CONTACT_OPTICAL)!=0)continue;
            BattleRenderPoses.Pose pose=poses.ship(target);
            if(!view.ship(pose,target.visualScale()))continue;
            float x=pose.x(),y=pose.y(),r=Math.max(2.5f,world.zoom*8f);
            if((mask&Battle.CONTACT_RADAR)!=0){
                shapes.setColor(.28f,.85f,1f,.90f);
                radarHull(shapes,target,pose);
                // The installed holo projector already supplies real seeker
                // ghost positions. Its radar echoes mimic the same hull trace,
                // but never create an extra damageable simulation vessel.
                for(int echo=0;echo<DeceptionProjection.falseContacts(target);echo++){
                    float gx=(float)DeceptionProjection.ghostX(target,echo,battle.tick);
                    float gy=(float)DeceptionProjection.ghostY(target,echo,battle.tick);
                    if(view.circle(gx,gy,50*target.visualScale()))
                        radarHull(shapes,target,gx,gy,pose.angle());
                }
            }
            if((mask&Battle.CONTACT_XRAY)!=0){
                shapes.setColor(.76f,.46f,1f,.84f);
                shapes.line(x,y+r,x+r,y);
                shapes.line(x+r,y,x,y-r);
                shapes.line(x,y-r,x-r,y);
                shapes.line(x-r,y,x,y+r);
            }
            if((mask&Battle.CONTACT_GRAVITIC)!=0){
                shapes.setColor(.28f,1f,.72f,.67f);
                float pulse=1f+(battle.tick%45)/45f*.35f;
                shapes.circle(x,y,r*1.25f*pulse,24);
                shapes.circle(x,y,r*2.0f*pulse,32);
            }
        }
        shapes.end();
        if(world.zoom>2.25f||contacts.size()>64)return;
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        for(Battle.Ship target:battle.ships()){
            int mask=contacts.getOrDefault(target.id,0);
            if(mask==0||(mask&Battle.CONTACT_OPTICAL)!=0)continue;
            BattleRenderPoses.Pose pose=poses.ship(target);
            float sx=(pose.x()-world.position.x)/world.zoom+640f;
            float sy=(pose.y()-world.position.y)/world.zoom+360f;
            if(sx<16||sx>1180||sy<75||sy>600)continue;
            font.setColor(Color.LIGHT_GRAY);
            String description=label(mask);
            // An IR point is not automatically identified as a dreadnought.
            // Dedicated resolution or consistent multispectral correlation
            // permits a class label without revealing source hull textures.
            float quality=classification.getOrDefault(target.id,0f);
            if(quality>=.35f && target.hullClass!=null &&
                    (mask&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY))!=0)
                description+=" "+target.hullClass.spriteHull().name().replace('_',' ');
            font.draw(batch,description,sx+12,sy+20);
        }
        font.setColor(Color.WHITE);
        batch.end();
    }
}
