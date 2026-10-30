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
        System.out.println("PASS selected-hull actual stats, ammo and target display");
    }
}
