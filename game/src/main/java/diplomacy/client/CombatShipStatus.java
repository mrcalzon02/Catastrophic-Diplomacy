package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.DeceptionProjection;

/** Compact, non-interactive detail line for the actual currently followed hull. */
final class CombatShipStatus {
    private CombatShipStatus(){}
    private static int percent(double value,double maximum){
        if(!Double.isFinite(value)||!Double.isFinite(maximum)||maximum<=0)return 0;
        return (int)Math.round(Math.min(100,Math.max(0,100*value/maximum)));
    }
    static String equipment(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Ship required");
        StringBuilder status=new StringBuilder();
        if(DeceptionProjection.cloakActive(ship))status.append("OPTICAL CLOAK  ");
        if(ship.radarCrossSection<.7)status.append("RADAR LOW  ");
        if(ship.thermalSignature<.7)status.append("THERMAL LOW  ");
        if(DeceptionProjection.falseContacts(ship)>0)
            status.append("HOLO CONTACTS ").append(DeceptionProjection.falseContacts(ship)).append("  ");
        if(ship.infraredRange>0)status.append("IR SCAN  ");
        if(ship.xrayRange>0)status.append("X-RAY SCAN  ");
        if(ship.graviticRange>0)status.append("GRAV SCAN  ");
        if(ship.escapePods>0)status.append("PODS ").append(ship.escapePods).append("  ");
        if(ship.recoveryBeacon)status.append("RECOVERY BEACON  ");
        if(ship.hazardInstability>0)
            status.append("UNSAFE RETROFIT ").append(ship.hazardFailures).append(" FAULTS");
        return status.toString().stripTrailing();
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
