package diplomacy.sim;

import diplomacy.fx.WeaponFxProfile;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Hard assertions that the design table is also the runtime combat contract. */
public final class WeaponBehaviorTableTest {
    private WeaponBehaviorTableTest(){}
    private static void check(boolean value,String why) {
        if(!value)throw new AssertionError(why);
    }
    private static void same(double expected,double actual,String why){
        check(Math.abs(expected-actual)<1e-9,why+" expected="+expected+" actual="+actual);
    }
    private static void rejects(Runnable action){
        try{action.run();throw new AssertionError("Invalid behavior was accepted");}
        catch(IllegalArgumentException correct){/* expected */}
    }
    private static Battle oneWeapon(Battle.DamageType type,double spacing){
        Battle test=new Battle(4505);
        Battle.Ship shooter=test.addShip(0,Battle.Faction.TERRAN,320,330);
        Battle.Ship enemy=test.addShip(1,Battle.Faction.CARNELIAN,320+spacing,330);
        for(Battle.Ship ship:test.ships()){
            ship.speed=0;ship.vx=ship.vy=0;ship.fighterReserve=ship.droneReserve=0;
            ship.repairDroneReserve=0;ship.pdRange=0;ship.maxPower=300;ship.power=300;
            ship.maxHeat=400;ship.heat=0;
            ship.laserDamage=ship.kineticDamage=ship.plasmaDamage=0;
            ship.particleDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.missileAmmo=ship.torpedoAmmo=2;
        }
        switch(type) {
            case LASER -> shooter.laserDamage=28;
            case KINETIC -> shooter.kineticDamage=28;
            case PLASMA -> shooter.plasmaDamage=28;
            case PARTICLE -> shooter.particleDamage=28;
            case MISSILE -> shooter.missileDamage=28;
            case TORPEDO -> shooter.torpedoDamage=28;
            default -> throw new IllegalArgumentException("Not a ship gun");
        }
        return test;
    }
    public static void main(String[] args){
        check(WeaponBehaviorTable.guns().size()==6,"Exactly six ship primary weapon families");
        EnumSet<Battle.DamageType> types=EnumSet.of(
                Battle.DamageType.LASER,Battle.DamageType.KINETIC,
                Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE,
                Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO);
        check(WeaponBehaviorTable.guns().keySet().equals(types),"No damage type missing or accidentally assigned");
        rejects(()->WeaponBehaviorTable.gun(Battle.DamageType.FIGHTER));
        Set<String> modules=new HashSet<>();
        for(Battle.DamageType type:types) {
            var gun=WeaponBehaviorTable.gun(type);
            check(modules.add(gun.module()),"No repeated module subsystem key");
            check(gun.damageType()==type && gun.maximumRange()>gun.minimumRange(),
                    "Invalid mapped gun "+type);
            same(gun.cooldownSeconds(),gun.reload(1,1),"Mk-I unmodified firing cycle");
            check(!gun.available(0,2,200,1,0,300,0,400,1,1),
                    "Bare hull MUST NOT fire inherited weapon "+type);
            check(!gun.available(10,2,gun.range(1),1,0,300,0,400,1,1),
                    "Max range is a strict firing boundary "+type);
            check(!gun.available(10,2,gun.minimumRange(),1,0,300,0,400,1,1)
                            ||gun.minimumRange()==0,
                    "Minimum standoff is a strict boundary "+type);
            check(!gun.available(10,2,gun.maximumRange()*0.9,1,0,
                            gun.powerPerShot(),0,400,1,1),
                    "Must have energy exceeding shot cost "+type);
            check(!gun.available(10,2,gun.maximumRange()*0.9,1,1,300,0,400,1,1),
                    "Positive cooldown blocks repeated fire "+type);
            check(gun.available(10,2,gun.maximumRange()*0.9,1,0,300,0,400,1,1),
                    "Healthy powered fitted equipment can fire "+type);
            if(gun.heatFractionLimit()<1)
                check(!gun.available(10,2,gun.maximumRange()*0.9,1,0,300,
                                400*gun.heatFractionLimit(),400,1,1),
                        "Thermal lockout uses family-specific thresholds "+type);
            if(gun.consumesAmmunition())
                check(!gun.available(10,0,gun.maximumRange()*0.9,1,0,300,0,400,1,1),
                        "Guided launchers require finite fitted ammunition "+type);
            var fx=WeaponFxProfile.forType(type);
            check(fx!=null,"No FX semantics for "+type);
            var expected=switch(gun.delivery()){
                case INSTANT_BEAM -> WeaponFxProfile.Motion.HITSCAN;
                case BALLISTIC_BODY -> WeaponFxProfile.Motion.BALLISTIC;
                case GUIDED_BODY -> WeaponFxProfile.Motion.GUIDED;
            };
            check(fx.motion()==expected,"FX and real projectile delivery disagree for "+type);
            if(gun.projectile()!=null){
                var spec=WeaponBehaviorTable.projectileStandard(type);
                check(spec!=null && spec.speed()>0 && spec.health()>0
                                && spec.crossSection()>0 && spec.signature()>0,
                        "Guided/ballistic physical ordnance must be detectable, destroyable");
            }else check(WeaponBehaviorTable.projectileStandard(type)==null,
                    "Instant fire may not spawn a fake independent projectile");
            if(gun.delivery()!=WeaponBehaviorTable.Delivery.GUIDED_BODY){
                double low=gun.aim(0.2,1,1,0);
                double high=gun.aim(0.9,1.2,1,1);
                check(low<high&&high<=gun.accuracyCap(),
                        "Target lock improves but caps main-gun accuracy");
            }else{
                check(gun.guidancePerTick()>0&&gun.flightSeconds()>0,
                        "Guided rounds have real fuel and changing direction");
            }
            Battle actual=oneWeapon(type,gun.minimumRange()+60);
            actual.step();
            check(actual.visualEvents().stream().anyMatch(e->
                            e.phase()==Battle.VisualPhase.FIRE && e.weapon().equals(type.name())
                                &&e.team()==0),
                    "Real simulation must use configured firing law for "+type);
        }
        same(530,WeaponBehaviorTable.gun(Battle.DamageType.LASER).maximumRange(),"Laser baseline reach");
        same(445,WeaponBehaviorTable.gun(Battle.DamageType.KINETIC).maximumRange(),"Kinetic baseline reach");
        same(245,WeaponBehaviorTable.gun(Battle.DamageType.PLASMA).maximumRange(),"Plasma baseline reach");
        same(565,WeaponBehaviorTable.gun(Battle.DamageType.PARTICLE).maximumRange(),"Particle baseline reach");
        same(690,WeaponBehaviorTable.gun(Battle.DamageType.MISSILE).maximumRange(),"Missile baseline reach");
        same(840,WeaponBehaviorTable.gun(Battle.DamageType.TORPEDO).maximumRange(),"Torpedo baseline reach");
        check(WeaponBehaviorTable.damageTable().size()==Battle.DamageType.values().length,
                "Every damage type, including fighter attacks, requires complete defense tables");
        DirectionalDefense baseline=new DirectionalDefense(80,100,200);
        for(Battle.DamageType kind:Battle.DamageType.values()){
            var consequences=WeaponBehaviorTable.damage(kind);
            for(DirectionalDefense.Facing facing:DirectionalDefense.Facing.values()){
                same(consequences.shield(),
                        baseline.damageFactor(DirectionalDefense.Layer.SHIELD,facing,kind),
                        "Directional shield behavior must come from its weapon table");
                same(consequences.armor(),
                        baseline.damageFactor(DirectionalDefense.Layer.ARMOR,facing,kind),
                        "Directional armor response must come from weapon table");
                same(consequences.structure(),
                        baseline.damageFactor(DirectionalDefense.Layer.STRUCTURE,facing,kind),
                        "Directional structure response must come from weapon table");
            }
        }
        check(WeaponBehaviorTable.damage(Battle.DamageType.PARTICLE).energyDrain()>0
                        &&WeaponBehaviorTable.damage(Battle.DamageType.PARTICLE).sensorDegradation()>0
                        &&WeaponBehaviorTable.damage(Battle.DamageType.PLASMA).addedHeat()>0,
                "Particle suppression and plasma heating must exist in the impact table");
        same(1.5,WeaponBehaviorTable.damage(Battle.DamageType.TORPEDO).shield(),
                "Heavy torpedo shield damage");
        same(1.25,WeaponBehaviorTable.damage(Battle.DamageType.KINETIC).armor(),
                "Kinetic penetrator armor damage");
        same(0.8,WeaponBehaviorTable.damage(Battle.DamageType.LASER).armor(),
                "Armor resilience against lasers");
        var pd=WeaponBehaviorTable.POINT_DEFENSE;
        same(0.16,pd.reload(false,1,1),"PD ordinance cycle");
        same(0.18,pd.reload(true,1,1),"PD anti-craft cycle");
        check(BattleOrders.PointDefense.BALANCED.ordnance()
                        &&BattleOrders.PointDefense.BALANCED.craft()
                        &&BattleOrders.PointDefense.INTERCEPT_ONLY.ordnance()
                        &&!BattleOrders.PointDefense.INTERCEPT_ONLY.craft()
                        &&!BattleOrders.PointDefense.ANTI_CRAFT.ordnance()
                        &&BattleOrders.PointDefense.ANTI_CRAFT.craft()
                        &&!BattleOrders.PointDefense.HOLD_FIRE.ordnance()
                        &&!BattleOrders.PointDefense.HOLD_FIRE.craft(),
                "All four independent PD toggle modes must match their actual coverage");
        int armaments=0;
        for(Battle.Faction faction:Battle.Faction.values())
            for(FighterModel.Hull hull:FighterModel.Hull.values()){
                var fighter=FighterModel.forFaction(hull,faction);
                check(fighter.damage()>0&&fighter.sensors()>0&&fighter.fuel()>0
                        &&fighter.speed()>0&&fighter.crossSection()>0,
                        "Autonomous fighter family needs real weapons and detectable physics");
                check(WeaponFxProfile.forWeapon(fighter.weapon().name())!=null
                        ||fighter.weapon()==FighterModel.Weapon.CANNON,
                        "Fighter armament must have FX family or explicit cannon convention");
                armaments++;
            }
        check(armaments==66,"Six fighter armaments for each of 11 races");
        System.out.println("PASS authoritative weapon behavior: 6 ship guns, 3 delivery modes, 4 PD toggles, 66 fighter loadouts, ammo/power/heat/range/reload/accuracy and live FIRE events");
    }
}
