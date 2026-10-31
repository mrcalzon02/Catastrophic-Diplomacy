package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import diplomacy.sim.WeaponBehaviorTable;
import java.util.Locale;

/**
 * Read-only inspection of one authoritative battle vessel. Tap a weapon row
 * to toggle its actual firing reach on the world camera. No simulation edits.
 * Shared by skirmish, campaign and exhibition battle rendering.
 */
final class ShipInspectionPanel {
    static final float X=753f,Y=69f,W=515f,H=540f;
    private static final int ALL_RANGES=127;
    private static final Battle.DamageType[] GUNS={
            Battle.DamageType.LASER,Battle.DamageType.KINETIC,
            Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE,
            Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO
    };
    private static final String[] LABELS={
            "LASER","KINETIC","PLASMA","PARTICLE","MISSILE","TORPEDO","PD / FLAK"
    };
    private static final Color[] COLORS={
            new Color(.35f,.84f,1f,1),new Color(1f,.76f,.28f,1),
            new Color(1f,.48f,.32f,1),new Color(.77f,.48f,1f,1),
            new Color(.37f,1f,.67f,1),new Color(1f,.6f,.24f,1),
            new Color(.92f,.92f,.55f,1)
    };
    private int shipId=-1;
    private int rangeMask=ALL_RANGES;
    int selectedId(){return shipId;}
    void select(int id){
        shipId=id;
        rangeMask=ALL_RANGES;
    }
    void close(){shipId=-1;}
    Battle.Ship selected(Battle battle){
        if(battle==null||shipId<0)return null;
        for(Battle.Ship s:battle.ships())if(s.id==shipId)return s;
        return null;
    }
    /** All coordinates in 1280x720 logical UI points. */
    static boolean contains(float x,float y){
        return x>=X&&x<=X+W&&y>=Y&&y<=Y+H;
    }
    static int action(float x,float y){
        if(!contains(x,y))return -1;
        if(x>=1210&&y>=565)return 1; // Close (also works at low DPI)
        for(int i=0;i<7;i++){
            float baseline=375-i*23;
            if(y>=baseline-15&&y<=baseline+7)return 10+i;
        }
        if(y<104&&x>1090)return 2; // All ranges on/off
        return 0; // Prevent tap-through into battle/menu HUD.
    }
    boolean tap(float x,float y){
        if(shipId<0||!contains(x,y))return false;
        int click=action(x,y);
        if(click==1)close();
        else if(click==2)rangeMask=rangeMask==0?ALL_RANGES:0;
        else if(click>=10&&click<17)rangeMask^=1<<(click-10);
        return true;
    }
    boolean enabled(int index){return index>=0&&index<7&&(rangeMask&(1<<index))!=0;}
    static int percent(double current,double maximum){
        if(!Double.isFinite(current)||!Double.isFinite(maximum)||maximum<=0)return 0;
        return (int)Math.round(Math.max(0,Math.min(100,current/maximum*100)));
    }
    static double range(Battle.Ship ship,int index){
        if(ship==null||index<0||index>=7)throw new IllegalArgumentException("Invalid weapon range query");
        if(index==6)return Math.max(0,ship.pdRange);
        Battle.DamageType family=GUNS[index];
        double damage=switch(family){
            case LASER -> ship.laserDamage;
            case KINETIC -> ship.kineticDamage;
            case PLASMA -> ship.plasmaDamage;
            case PARTICLE -> ship.particleDamage;
            case MISSILE -> ship.missileDamage;
            case TORPEDO -> ship.torpedoDamage;
            default -> 0;
        };
        double scale=switch(family){
            case LASER -> ship.weapons.laserRange();
            case KINETIC -> ship.weapons.kineticRange();
            case PLASMA -> ship.weapons.plasmaRange();
            case PARTICLE -> ship.weapons.particleRange();
            case MISSILE -> ship.weapons.missileRange();
            case TORPEDO -> ship.weapons.torpedoRange();
            default -> 0;
        };
        if(damage<=0||!Double.isFinite(scale)||scale<=0)return 0;
        return WeaponBehaviorTable.gun(family).range(scale);
    }
    static double reload(Battle.Ship s,int index){
        if(index==6)return Math.max(0,s.pdReload);
        if(index==4||index==5){
            Battle.DamageType type=index==4?Battle.DamageType.MISSILE:Battle.DamageType.TORPEDO;
            double minimum=Double.POSITIVE_INFINITY;
            for(Battle.Ship.Launcher rack:s.launchers)
                if(rack.family==type&&rack.magazine>0)
                    minimum=Math.min(minimum,rack.reload);
            if(Double.isFinite(minimum))return Math.max(0,minimum);
        }
        return Math.max(0,switch(index){
            case 0 -> s.laserReload; case 1 -> s.kineticReload;
            case 2 -> s.plasmaReload; case 3 -> s.particleReload;
            case 4 -> s.missileReload; case 5 -> s.torpedoReload;
            default -> 0;
        });
    }
    static int ammo(Battle.Ship s,int index){
        return switch(index){
            case 1 -> Math.max(0,s.kineticAmmo);
            case 4 -> Math.max(0,s.missileAmmo);
            case 5 -> Math.max(0,s.torpedoAmmo);
            default -> -1;
        };
    }
    /** Ship picking uses real interpolated positions and a minimum touch
     * radius, but never treats a drag or pinch as a tap. */
    static int pick(Battle battle,BattleRenderPoses poses,
                    float uiX,float uiY,float camX,float camY,float zoom){
        if(battle==null||poses==null||!Float.isFinite(zoom)||zoom<=0)return -1;
        double x=camX+(uiX-640)*zoom,y=camY+(uiY-360)*zoom;
        int picked=-1;
        double best=Double.POSITIVE_INFINITY;
        for(Battle.Ship ship:battle.ships())if(ship.alive()){
            BattleRenderPoses.Pose pose=poses.ship(ship);
            double radius=Math.max(14*ship.visualScale(),17*zoom);
            double dist=StrictMath.hypot(pose.x()-x,pose.y()-y);
            if(dist<=radius&&dist<best){best=dist;picked=ship.id;}
        }
        return picked;
    }
    void ranges(ShapeRenderer shapes,Battle battle,BattleRenderPoses poses,
                CombatViewBounds view){
        Battle.Ship ship=selected(battle);
        if(ship==null||!ship.alive()||rangeMask==0)return;
        BattleRenderPoses.Pose pose=poses.ship(ship);
        for(int i=0;i<7;i++)if(enabled(i)){
            double radius=range(ship,i);
            if(radius<=0||!Double.isFinite(radius)||!view.circle(pose.x(),pose.y(),radius))continue;
            Color c=COLORS[i];
            shapes.setColor(c.r,c.g,c.b,.45f);
            shapes.circle(pose.x(),pose.y(),(float)radius,80);
        }
    }
    private static String shortName(String value,int limit){
        if(value==null)return "UNNAMED";
        String safe=value.replace('\n',' ').replace('\r',' ');
        return safe.length()<=limit?safe:safe.substring(0,limit-1)+"…";
    }
    private static void bar(ShapeRenderer shapes,float x,float y,float w,
                            double current,double max,Color color){
        shapes.setColor(.12f,.17f,.23f,.95f);
        shapes.rect(x,y,w,9);
        float fill=(float)(Math.max(0,Math.min(100,percent(current,max)))/100.0*w);
        shapes.setColor(color);
        shapes.rect(x,y,fill,9);
    }
    private static String stat(String label,double current,double max){
        return label+" "+Math.round(Math.max(0,current))+" / "+Math.round(Math.max(0,max))
                +"  "+percent(current,max)+"%";
    }
    private static String condition(String label,double ratio){
        return label+" "+percent(ratio,1)+"%";
    }
    void draw(Battle battle,ShapeRenderer shapes,SpriteBatch batch,BitmapFont font){
        Battle.Ship s=selected(battle);
        if(s==null)return;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.025f,.045f,.075f,.96f);
        shapes.rect(X,Y,W,H);
        shapes.setColor(.11f,.23f,.33f,1f);
        shapes.rect(X,Y+H-49,W,49);
        shapes.setColor(.17f,.26f,.34f,1f);
        shapes.rect(1210,568,49,35);
        for(int i=0;i<3;i++){
            float y=527-i*27;
            bar(shapes,772,y,218,
                    i==0?s.hull:i==1?s.shields:s.armor,
                    i==0?s.maxHull:i==1?s.maxShields:s.maxArmor,
                    i==0?Color.ORANGE:i==1?Color.CYAN:Color.GOLD);
        }
        for(int i=0;i<7;i++){
            float y=375-i*23;
            if((i&1)==0){
                shapes.setColor(.07f,.12f,.18f,.86f);
                shapes.rect(762,y-16,497,23);
            }
            if(enabled(i)){
                Color c=COLORS[i];shapes.setColor(c.r,c.g,c.b,.95f);
                shapes.rect(764,y-13,4,18);
            }
        }
        shapes.setColor(.13f,.26f,.33f,.95f);
        shapes.rect(1101,76,156,30);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch,shortName(s.name,29),769,589);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,(s.hullClass==null?"SHIP":s.hullClass.name().replace('_',' '))+
                "  /  #"+s.id+"  /  "+(s.alive()?"ACTIVE":"DESTROYED"),769,564);
        font.setColor(Color.WHITE);
        font.draw(batch,"X",1227,594);
        font.setColor(Color.CYAN);
        font.draw(batch,"DEFENSE",772,550);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,stat("HULL",s.hull,s.maxHull),773,539);
        font.draw(batch,stat("SHIELD",s.shields,s.maxShields),773,512);
        font.draw(batch,stat("ARMOR",s.armor,s.maxArmor),773,485);
        font.setColor(Color.WHITE);
        font.draw(batch,stat("POWER",s.power,s.maxPower),1008,544);
        font.draw(batch,stat("HEAT",s.heat,s.maxHeat),1008,520);
        font.draw(batch,stat("FUEL",s.fuel,s.maxFuel),1008,496);
        font.draw(batch,"CREW "+Math.round(s.crewCount)+"/"+s.crewBerths+
                "  NEED "+s.crewRequired,1008,472);

        font.setColor(Color.CYAN);
        font.draw(batch,"SHIELD FACINGS  F / P / A / S",770,456);
        font.setColor(Color.LIGHT_GRAY);
        if(s.defense!=null){
            DirectionalDefense.Facing[] f=DirectionalDefense.Facing.values();
            font.draw(batch,percent(s.defense.shields(f[0]),s.defense.maxShields(f[0]))+"%    "+
                    percent(s.defense.shields(f[1]),s.defense.maxShields(f[1]))+"%    "+
                    percent(s.defense.shields(f[2]),s.defense.maxShields(f[2]))+"%    "+
                    percent(s.defense.shields(f[3]),s.defense.maxShields(f[3]))+"%",775,432);
        }
        font.setColor(Color.CYAN);
        font.draw(batch,"WEAPON / ACTUAL RANGE / RELOAD / AMMO",771,406);
        for(int i=0;i<7;i++){
            int y=375-i*23;
            double reach=range(s,i);
            font.setColor(reach>0?COLORS[i]:Color.GRAY);
            font.draw(batch,LABELS[i],773,y);
            font.setColor(Color.WHITE);
            font.draw(batch,reach>0?Math.round(reach)+"u":"--",907,y);
            font.draw(batch,reach>0?String.format(Locale.ROOT,"%.1fs",reload(s,i)):"--",1018,y);
            int n=ammo(s,i);
            font.draw(batch,n<0?"--":Integer.toString(n),1105,y);
            font.setColor(enabled(i)&&reach>0?Color.GREEN:Color.GRAY);
            font.draw(batch,enabled(i)&&reach>0?"RNG":"OFF",1194,y);
        }
        font.setColor(Color.CYAN);
        font.draw(batch,"SUBSYSTEM CONDITION",771,204);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,condition("ENGINE",s.engineCondition),773,183);
        font.draw(batch,condition("WEAPONS",s.weaponsCondition),1010,183);
        font.draw(batch,condition("REACTOR",s.reactorCondition),773,160);
        font.draw(batch,condition("SENSORS",s.sensorsCondition),1010,160);
        font.draw(batch,condition("COOLING",s.coolingCondition),773,137);
        font.draw(batch,condition("POINT DEF",s.pointDefenseCondition),1010,137);
        font.draw(batch,condition("COMMAND",s.commandCondition),773,114);
        font.draw(batch,condition("MAGAZINE",s.magazineCondition),1010,114);
        font.draw(batch,condition("HANGAR",s.hangarCondition),773,91);
        font.setColor(Color.CYAN);
        font.draw(batch,rangeMask==0?"SHOW RANGES":"HIDE RANGES",1110,96);
        batch.end();
    }
}
