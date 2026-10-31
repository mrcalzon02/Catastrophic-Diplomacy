package diplomacy.client;

import diplomacy.shipyard.Shipyard;
import diplomacy.sim.WeaponMuzzles;

/** Headless contract for mounted-capital sprite association and bearing. */
public final class CapitalTurretSpritesTest {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        check(!CapitalTurretSprites.capital(Shipyard.Hull.CORVETTE),"Do not crowd small-ship sprites");
        check(CapitalTurretSprites.capital(Shipyard.Hull.CRUISER),"Capital hull must draw fitted turrets");
        check(CapitalTurretSprites.capital(Shipyard.Hull.DREADNOUGHT_SIEGE),
                "Specialist capital hull must inherit mounted art");
        check(CapitalTurretSprites.tile(WeaponMuzzles.Family.MISSILE,6)==7,
                "Real missile racks must use the missile-pod artwork");
        check(CapitalTurretSprites.tile(WeaponMuzzles.Family.TORPEDO,7)==8,
                "Real torpedo racks must use the torpedo tube artwork");
        check(CapitalTurretSprites.tile(WeaponMuzzles.Family.LASER,1)==2
                && CapitalTurretSprites.tile(WeaponMuzzles.Family.LASER,2)==2,
                "Laser sockets must never receive the railgun image");
        check(CapitalTurretSprites.tile(new WeaponMuzzles.Emitter(5,
                WeaponMuzzles.Family.POINT_DEFENSE,0,0,"BASE-VP-FLK"),
                Shipyard.Hull.DESTROYER)==5,"Flak modules use flak hardware");
        check(CapitalTurretSprites.tile(new WeaponMuzzles.Emitter(5,
                WeaponMuzzles.Family.POINT_DEFENSE,0,0,"BASE-VP-PD"),
                Shipyard.Hull.DESTROYER)==6,"Rotary point defense uses gatling");
        check(CapitalTurretSprites.tile(new WeaponMuzzles.Emitter(8,
                WeaponMuzzles.Family.KINETIC,0,0,"BASE-VP-KIN"),
                Shipyard.Hull.DREADNOUGHT)==11,"Dreadnought kinetics use siege hardware");
        check(Math.abs(CapitalTurretSprites.diameter(WeaponMuzzles.Family.LASER,1f)
                *3f-6.4f)<.0001f,"Mounted turret diameter must be 1/3 former size");
        check(Math.abs(CapitalTurretSprites.diameter(WeaponMuzzles.Family.TORPEDO,1f)
                *3f-6.4f*1.14f)<.0001f,"Torpedo fittings retain relative sizing");
        check(CapitalTurretSprites.tile(WeaponMuzzles.Family.PLASMA,1)==4,
                "Plasma modules must show plasma cannon artwork");
        check(CapitalTurretSprites.tile(WeaponMuzzles.Family.POINT_DEFENSE,2)==6,
                "Point defense must use small rotary turret hardware");
        check(CapitalTurretSprites.spriteAngle(0f)==90f
                &&CapitalTurretSprites.spriteAngle(-90f)==0f
                &&CapitalTurretSprites.spriteAngle(90f)==180f,
                "Art points down; world bearings must rotate actual turret barrels");
        for(WeaponMuzzles.Family family:WeaponMuzzles.Family.values())
            for(int socket=0;socket<32;socket++)
                check(CapitalTurretSprites.tile(family,socket)>=0
                    &&CapitalTurretSprites.tile(family,socket)<12,
                    "Every fitted weapon family/socket must map to a real atlas cell");
        System.out.println("PASS capital turret mounted families, class LOD, target rotation and 12-frame atlas indices");
    }
}
