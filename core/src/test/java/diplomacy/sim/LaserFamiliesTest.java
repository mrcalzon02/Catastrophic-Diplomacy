package diplomacy.sim;

import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullLayouts;
import java.util.List;
import java.util.Map;

/** Headless laser variants: actual fitted type, muzzle, costs and timing. */
public final class LaserFamiliesTest {
    private static void ok(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void near(double a,double b,String why){
        ok(Math.abs(a-b)<.000001,why+": "+a+" vs "+b);
    }
    private static final ModuleVersions.Pattern[] PATTERNS=ModuleVersions.Pattern.values();
    private static final LaserFamilies.Type[] TYPES={
            LaserFamilies.Type.PULSE,LaserFamilies.Type.RAPID,
            LaserFamilies.Type.LANCE,LaserFamilies.Type.OVERDRIVE,
            LaserFamilies.Type.NEEDLE};
    private static Battle.Ship fireOnce(LaserFamilies.Type type){
        Battle battle=new Battle(96000+type.ordinal());
        Battle.Ship source=battle.addShip(0,Battle.Faction.TERRAN,160,240);
        Battle.Ship target=battle.addShip(1,Battle.Faction.CARNELIAN,460,240);
        source.speed=target.speed=0;
        source.pdRange=target.pdRange=0;
        source.laserDamage=20;source.accuracy=3;
        source.missileDamage=source.kineticDamage=source.plasmaDamage=0;
        source.particleDamage=source.torpedoDamage=0;
        source.missileAmmo=source.kineticAmmo=source.torpedoAmmo=0;
        target.laserDamage=target.missileDamage=target.kineticDamage=0;
        target.plasmaDamage=target.particleDamage=target.torpedoDamage=0;
        target.missileAmmo=target.kineticAmmo=target.torpedoAmmo=0;
        target.maxHull=200000;target.hull=200000;
        source.weaponEmitters=List.of(
                new WeaponMuzzles.Emitter(13,WeaponMuzzles.Family.LASER,0,37));
        source.laserFittings=Map.of(13,type);
        battle.step();
        var fire=battle.visualEvents().stream()
                .filter(e->e.sourceId()==source.id && e.phase()==Battle.VisualPhase.FIRE)
                .findFirst().orElseThrow(()->new AssertionError(type+" did not fire"));
        ok(fire.weapon().equals(LaserFamilies.eventWeapon(type)),
                "real firing event must identify the selected variant");
        var beam=battle.beams().stream().filter(b->b.visualId==fire.shotId())
                .findFirst().orElseThrow(()->new AssertionError("No real beam"));
        ok(beam.laserType==type,"beam must carry real firing module type");
        near(beam.lifetime,type.lifetime,"beam lifetime");
        near(beam.x1,fire.x(),"beam muzzle X");
        near(beam.y1,fire.y(),"beam muzzle Y");
        ok(Math.abs(beam.x1-source.x)+Math.abs(beam.y1-source.y)>2,
                "laser fires from module, not center");
        near(source.laserReload,
                WeaponBehaviorTable.gun(Battle.DamageType.LASER)
                    .reload(source.weapons.laserCycle(),source.weaponsCondition)*type.cycle,
                "firing interval");
        ok(source.heat>0,"real weapon generates heat");
        // Every beam uses a real aim roll capped at 98%, even for expert
        // gunners. A regression must not depend on the FIRST shot hitting.
        boolean impactSeen=false;
        for(int frame=0;frame<220 && !impactSeen;frame++){
            for(Battle.VisualEvent e:battle.visualEvents())
                if(e.sourceId()==source.id && e.phase()==Battle.VisualPhase.IMPACT
                        &&e.weapon().equals(fire.weapon()))impactSeen=true;
            if(!impactSeen)battle.step();
        }
        ok(impactSeen,"fitted laser must eventually resolve a real impact with correct type");
        return source;
    }
    public static void main(String[] ignored){
        Shipyard.Design design=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CRUISER);
        int socket=-1;
        for(Shipyard.Slot slot:design.placements()){
            Shipyard.Installed component=design.at(slot.index());
            if(component!=null&&component.component.endsWith("-LAS")){
                socket=slot.index();break;
            }
        }
        ok(socket>=0,"starter has an installed LAS module");
        for(int i=0;i<PATTERNS.length;i++){
            var variant=design.withModuleVersion(socket,PATTERNS[i],1);
            ok(LaserFamilies.fitted(variant).get(socket)==TYPES[i],
                    "shipyard manufacturing selection must control laser type");
            ok(LaserFamilies.fromWeapon(LaserFamilies.eventWeapon(TYPES[i]))==TYPES[i],
                    "roundtrip laser event label");
            fireOnce(TYPES[i]);
        }
        ok(LaserFamilies.fromWeapon("LASER_GHOST")==null,"unrecognized tag safe");
        ok(LaserFamilies.fromWeapon("PLASMA")==null,"not all beams are lasers");
        ok(LaserFamilies.Type.RAPID.cycle<LaserFamilies.Type.PULSE.cycle,
                "rapid cycle is truly faster");
        ok(LaserFamilies.Type.LANCE.range>LaserFamilies.Type.PULSE.range,
                "lance range advantage");
        ok(LaserFamilies.Type.NEEDLE.aim>LaserFamilies.Type.PULSE.aim,
                "needle accuracy advantage");
        ok(LaserFamilies.Type.OVERDRIVE.heat>LaserFamilies.Type.PULSE.heat,
                "overdrive is thermally expensive");
        System.out.println("PASS five installed laser patterns, physical beams, real damage events, costs, and muzzles");
    }
}
