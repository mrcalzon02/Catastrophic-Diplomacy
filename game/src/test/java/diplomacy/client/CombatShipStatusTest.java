package diplomacy.client;

import diplomacy.sim.Battle;

/** Selected-hull status uses real simulation values, never synthetic player health. */
public final class CombatShipStatusTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] unused){
        Battle b=new Battle(888L);
        Battle.Ship s=b.addShip(0,Battle.Faction.TERRAN,0,0);
        s.name="A Very Long Flagship Vessel Name";
        s.hull=s.maxHull*.5;
        s.shields=s.maxShields*.25;
        s.armor=s.maxArmor;
        s.kineticAmmo=12;s.missileAmmo=9;s.torpedoAmmo=3;s.lastTargetId=44;
        String line=CombatShipStatus.line(s);
        check(line.contains("H 50%"),"hull shows actual percentage");
        check(line.contains("S 25%"),"shields show actual percentage");
        check(line.contains("A 100%"),"armor shows actual percentage");
        check(line.contains("K 12  M 9  T 3"),"physical ammunition counts");
        check(line.endsWith("TARGET #44"),"actual latest target id");
        check(!line.contains("Flagship Vessel Name"),"long ship names kept compact");
        s.hull=-200;s.shields=Double.NaN;s.maxArmor=0;s.lastTargetId=0;
        String damaged=CombatShipStatus.line(s);
        check(damaged.contains("H 0%"),"never report negative hull percentage");
        check(damaged.contains("S 0%"),"nonfinite shield metric safe");
        check(damaged.contains("A 0%"),"zero denominator safe");
        check(!damaged.contains("TARGET #"),"no fictitious target");
        s.hull=s.maxHull;
        s.power=80;s.ecmCondition=1;
        s.opticalSignature=.35;s.thermalSignature=.52;
        s.hologramNoise=.65;s.infraredRange=580;s.xrayRange=600;
        s.holoSeekersDiverted=7;s.holoShotsDiverted=12;
        s.escapePods=12;s.recoveryBeacon=true;
        s.hazardInstability=.4;s.hazardFailures=3;
        s.lastFaultOrigin=diplomacy.tech.SurvivalHardwareCatalog.BATTERY_BRICK;
        String equipment=CombatShipStatus.equipment(s);
        check(equipment.contains("OPTICAL CLOAK") &&
                equipment.contains("THERMAL STEALTH") &&
                equipment.contains("HOLO CONTACTS 3") &&
                equipment.contains("SPOOFED 7 SEEKERS / 12 SHOTS") &&
                equipment.contains("IR SCAN") &&
                equipment.contains("X-RAY SCAN") &&
                equipment.contains("PODS 12") &&
                equipment.contains("RECOVERY BEACON") &&
                equipment.contains("UNSAFE RETROFIT 3 FAULTS (COMMS)"),
                "Player must see the actually installed combat and survival systems");
        s.power=0;
        equipment=CombatShipStatus.equipment(s);
        check(!equipment.contains("OPTICAL CLOAK") &&
                !equipment.contains("HOLO CONTACTS") &&
                !equipment.contains("IR SCAN") &&
                equipment.contains("SENSORS OFF"),
                "Unpowered detection and deception must disengage visibly");
        System.out.println("PASS selected-hull stats, live deception and engineering status");
    }
}
