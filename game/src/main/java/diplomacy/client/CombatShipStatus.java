package diplomacy.client;

import diplomacy.sim.Battle;

/** Compact, non-interactive detail line for the actual currently followed hull. */
final class CombatShipStatus {
    private CombatShipStatus(){}
    private static int percent(double value,double maximum){
        if(!Double.isFinite(value)||!Double.isFinite(maximum)||maximum<=0)return 0;
        return (int)Math.round(Math.min(100,Math.max(0,100*value/maximum)));
    }
    static String line(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Ship required");
        String name=ship.name==null?"UNNAMED":ship.name.replace('\n',' ').replace('\r',' ');
        if(name.length()>18)name=name.substring(0,17)+"…";
        String hull=ship.hullClass==null?"SHIP":ship.hullClass.name();
        if(hull.length()>15)hull=hull.substring(0,15);
        return name+" / "+hull+"  H "+percent(ship.hull,ship.maxHull)+
                "%  S "+percent(ship.shields,ship.maxShields)+
                "%  A "+percent(ship.armor,ship.maxArmor)+
                "%  K "+Math.max(0,ship.kineticAmmo)+
                "  M "+Math.max(0,ship.missileAmmo)+
                "  T "+Math.max(0,ship.torpedoAmmo)+
                (ship.lastTargetId>0?"  TARGET #"+ship.lastTargetId:"");
    }
}
