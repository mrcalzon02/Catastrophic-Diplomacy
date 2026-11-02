package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.DeceptionProjection;
import diplomacy.shipyard.HullBaseline;
import diplomacy.tech.SurvivalHardwareCatalog;

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
        if(ship.hullClass!=null && ship.radarCrossSection<
                HullBaseline.forHull(ship.faction,ship.hullClass).radarCrossSection()*.92)
            status.append("RADAR STEALTH  ");
        if(ship.thermalSignature<.98)status.append("THERMAL STEALTH  ");
        if(DeceptionProjection.falseContacts(ship)>0)
            status.append("HOLO CONTACTS ").append(DeceptionProjection.falseContacts(ship)).append("  ");
        if(ship.holoSeekersDiverted+ship.holoShotsDiverted>0)
            status.append("SPOOFED ").append(ship.holoSeekersDiverted)
                    .append(" SEEKERS / ").append(ship.holoShotsDiverted)
                    .append(" SHOTS  ");
        boolean exoticSensors=ship.infraredRange>0||ship.xrayRange>0
                ||ship.graviticRange>0;
        boolean operational=ship.power>3&&ship.sensorsCondition>0
                &&ship.crewReadiness()>=.05;
        if(exoticSensors&&!operational)status.append("SENSORS OFF  ");
        if(operational){
            if(ship.infraredRange>0)status.append("IR SCAN  ");
            if(ship.xrayRange>0)status.append("X-RAY SCAN  ");
            if(ship.graviticRange>0)status.append("GRAV SCAN  ");
        }
        if(ship.escapePods>0)status.append("PODS ").append(ship.escapePods).append("  ");
        if(ship.recoveryBeacon)status.append("RECOVERY BEACON  ");
        if(ship.hazardInstability>0){
            status.append("UNSAFE RETROFIT ").append(ship.hazardFailures).append(" FAULTS");
            if(ship.lastFaultOrigin!=0)status.append(" (").append(switch(ship.lastFaultOrigin){
                case SurvivalHardwareCatalog.GOVERNOR -> "GOVERNOR";
                case SurvivalHardwareCatalog.PLASMA_CELL -> "PLASMA";
                case SurvivalHardwareCatalog.BATTERY_BRICK -> "COMMS";
                case SurvivalHardwareCatalog.THERMAL_SHUNT -> "COOLING";
                case SurvivalHardwareCatalog.REACTOR_TAP -> "REACTOR";
                default -> "UNKNOWN";
            }).append(")");
        }
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
