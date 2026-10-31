package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.WeaponMuzzles;
import java.util.HashMap;
import java.util.Map;

/**
 * Actual authored top-down capital turret art. A sprite belongs to a fitted
 * physical hardpoint and rotates about its mounting ring; it does not create
 * synthetic shots, visual events, or damage. Battle's real weapon controllers
 * continue producing the beams, shells, missiles, and muzzle FX.
 */
public final class CapitalTurretSprites implements AutoCloseable {
    private static final String ATLAS="modules/turrets/capital_turrets_atlas.png";
    private static final int CELL=256,COLS=4;
    private final Texture atlas;
    private final TextureRegion[] tiles=new TextureRegion[12];
    public CapitalTurretSprites(){
        atlas=new Texture(Gdx.files.internal(ATLAS));
        atlas.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        if(atlas.getWidth()!=1024||atlas.getHeight()!=768){
            atlas.dispose();
            throw new IllegalArgumentException("Capital turret atlas must be 4 x 3 cells of 256 px");
        }
        for(int i=0;i<tiles.length;i++)
            tiles[i]=new TextureRegion(atlas,(i%COLS)*CELL,(i/COLS)*CELL,CELL,CELL);
    }
    /** Index agrees with approved Drive sprite pack and manifest row-major order. */
    static int tile(WeaponMuzzles.Family family,int slot){
        if(family==null)throw new IllegalArgumentException("Weapon family required");
        return switch(family){
            case LASER -> 2; // Never show the railgun atlas cell on a laser.
            case KINETIC -> switch(Math.floorMod(slot,4)){
                case 0 -> 0; case 1 -> 1; case 2 -> 10; default -> 11;
            };
            case PLASMA -> 4;
            case PARTICLE -> 3;
            case MISSILE -> 7;
            case TORPEDO -> 8;
            case POINT_DEFENSE -> (slot&1)==0?6:5;
        };
    }
    /** Actual fitted component suffix distinguishes flak from rotary PD.
     * Kinetic bore hardware is sized by ship class; no laser is a railgun. */
    static int tile(WeaponMuzzles.Emitter emitter,Shipyard.Hull hull){
        if(emitter==null)throw new IllegalArgumentException("Missing fitted turret");
        String id=emitter.component();
        if(id.endsWith("-FLK"))return 5;
        if(id.endsWith("-PD"))return 6;
        if(emitter.family()==WeaponMuzzles.Family.KINETIC && hull!=null)
            return switch(hull.spriteHull()){
                case DREADNOUGHT -> 11;
                case BATTLECRUISER,HEAVY_CRUISER -> 0;
                case CRUISER -> 1;
                default -> 10;
            };
        return tile(emitter.family(),emitter.slotId());
    }

    /** Divide the previous mounted-art footprint by THREE in each dimension.
     * Weapons and damage values stay untouched; sockets remain the pivot. */
    static float diameter(WeaponMuzzles.Family family,float shipScale){
        if(family==null||!Float.isFinite(shipScale)||shipScale<=0)
            throw new IllegalArgumentException("Invalid turret scale");
        float value=(6.4f/3f)*shipScale;
        if(family==WeaponMuzzles.Family.POINT_DEFENSE)value*=.75f;
        if(family==WeaponMuzzles.Family.TORPEDO)value*=1.14f;
        return value;
    }

    /** Turret overlay only for destroyer and larger hulls: tiny craft remain readable. */
    static boolean capital(Shipyard.Hull hull){
        if(hull==null)return false;
        return switch(hull.spriteHull()){
            case DESTROYER,LIGHT_CRUISER,CRUISER,HEAVY_CRUISER,BATTLECRUISER,
                 CARRIER,DREADNOUGHT -> true;
            default -> false;
        };
    }
    /** Source art faces toward -Y. A world aiming bearing of zero (+X)
     * therefore needs +90 degrees of rotation. */
    static float spriteAngle(float targetBearingDegrees){
        return targetBearingDegrees+90f;
    }
    public void render(Battle battle,SpriteBatch batch,
                       BattleRenderPoses poses,CombatViewBounds view,
                       float cameraZoom){
        if(battle==null||batch==null||poses==null||cameraZoom>2.3f)return;
        Map<Integer,Battle.Ship> ids=new HashMap<>();
        for(Battle.Ship ship:battle.ships())ids.put(ship.id,ship);
        batch.begin();
        for(Battle.Ship ship:battle.ships()){
            if(!ship.alive()||!capital(ship.hullClass)||ship.weaponEmitters.isEmpty())continue;
            BattleRenderPoses.Pose pose=poses.ship(ship);
            float scale=(float)ship.visualScale();
            if(view!=null&&!view.ship(pose,scale))continue;
            Battle.Ship enemy=ids.get(ship.lastTargetId);
            float direction=(float)pose.angle();
            if(enemy!=null&&enemy.team!=ship.team&&enemy.alive()){
                BattleRenderPoses.Pose targetPose=poses.ship(enemy);
                direction=(float)StrictMath.atan2(targetPose.y()-pose.y(),
                        targetPose.x()-pose.x());
            }
            float angle=spriteAngle((float)StrictMath.toDegrees(direction));
            float c=(float)StrictMath.cos(pose.angle());
            float s=(float)StrictMath.sin(pose.angle());
            float unit=34f/154f*scale;
            for(WeaponMuzzles.Emitter mount:ship.weaponEmitters){
                float forward=(float)mount.localY()*unit;
                float right=(float)mount.localX()*unit;
                float x=pose.x()+forward*c+right*s;
                float y=pose.y()+forward*s-right*c;
                float diameter=diameter(mount.family(),scale);
                // Only mounted physical emitters; rotation pivots at the base
                // centre of the 256-pixel sprite, not at the muzzle tip.
                batch.draw(tiles[tile(mount,ship.hullClass)],
                        x-diameter*0.5f,y-diameter*0.5f,
                        diameter*0.5f,diameter*0.5f,
                        diameter,diameter,1f,1f,angle);
            }
        }
        batch.end();
    }
    public int loaded(){return tiles.length;}
    @Override public void close(){atlas.dispose();}
}
