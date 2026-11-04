package diplomacy.sim;

import java.util.HashSet;
import java.util.Set;

/** Headless physical ammo/warhead tests: real flights, layered hits, collateral and replay. */
public final class AmmunitionTableTest {
    private AmmunitionTableTest(){}
    private static void check(boolean okay,String why){if(!okay)throw new AssertionError(why);}
    private static Battle scenario(boolean kinetic,boolean neighbor){
        Battle world=new Battle(440931);
        Battle.Ship a=world.addShip(0,Battle.Faction.TERRAN,200,300);
        Battle.Ship b=world.addShip(1,Battle.Faction.CARNELIAN,495,300);
        if(neighbor)world.addShip(1,Battle.Faction.CARNELIAN,515,340);
        for(Battle.Ship s:world.ships()){
            s.speed=0;s.vx=s.vy=0;
            s.laserDamage=s.kineticDamage=s.plasmaDamage=s.particleDamage=0;
            s.missileDamage=s.torpedoDamage=0;
            s.fighterReserve=s.droneReserve=s.repairDroneReserve=0;
            s.pdRange=0;s.maxPower=s.power=10000;s.powerRegen=0;
            s.maxHeat=10000;s.heat=0;
            s.missileAmmo=s.torpedoAmmo=s.kineticAmmo=100;
            s.defense.disruptShields(10000);s.shields=0;
            s.maxHull=s.hull=100000;
        }
        if(kinetic)a.kineticDamage=32;
        else a.missileDamage=42;
        return world;
    }
    private static void fly(Battle battle,int steps){
        for(int i=0;i<steps;i++)battle.step();
    }
    public static void main(String[] args){
        check(AmmunitionTable.kinetics().size()==5,"Five distinct ballistic payloads");
        check(AmmunitionTable.warheads().size()==6,"Six distinct guided warheads");
        var physical=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        var apWarhead=AmmunitionTable.warhead(AmmunitionTable.Warhead.ARMOR_PIERCING);
        var legacyAp=apWarhead.physical(physical);
        var fasterMissile=AmmunitionTable.guidedPhysical(
                Battle.DamageType.MISSILE,AmmunitionTable.Warhead.ARMOR_PIERCING,physical);
        var unchangedTorpedo=AmmunitionTable.guidedPhysical(
                Battle.DamageType.TORPEDO,AmmunitionTable.Warhead.ARMOR_PIERCING,physical);
        check(StrictMath.abs(fasterMissile.speed()/legacyAp.speed()-1.20)<1e-12,
                "AP missile flight speed must rise 20% relative to its old motor");
        check(unchangedTorpedo.equals(legacyAp),
                "Missile-only AP speed change modified existing AP torpedo physics");
        check(fasterMissile.health()==legacyAp.health()&&
                fasterMissile.crossSection()==legacyAp.crossSection()&&
                fasterMissile.signature()==legacyAp.signature(),
                "AP missile speed tuning unexpectedly changed HP, radar size or emissions");
        check(AmmunitionTable.guidedPhysical(Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.STANDARD,physical).equals(
                AmmunitionTable.warhead(AmmunitionTable.Warhead.STANDARD).physical(physical)),
                "Standard missiles must retain original physical statistics");
        check(AmmunitionTable.kinetic(AmmunitionTable.Kinetic.RELATIVISTIC).speed()>2,
                "Relativistic-style mass driver requires real additional velocity");
        check(AmmunitionTable.kinetic(AmmunitionTable.Kinetic.RELATIVISTIC).heatCost()>2,
                "High-velocity driver has real thermal price");
        check(AmmunitionTable.kinetic(AmmunitionTable.Kinetic.FRAGMENTATION).blastRadius()>0,
                "Fragmentation has bounded burst radius");
        check(AmmunitionTable.warhead(AmmunitionTable.Warhead.EMP).energyDrain()>0,
                "EMP warhead attacks systems");
        check(AmmunitionTable.warhead(AmmunitionTable.Warhead.SHIELD_BREAKER).shieldDrain()>0,
                "Shield breaker directly drains shield pool");
        var ordinary=new DirectionalDefense(0,20000,300);
        var penetrated=new DirectionalDefense(0,20000,300);
        var standard=ordinary.impact(1,0,0.8,0,35,Battle.DamageType.KINETIC);
        var ap=penetrated.impact(1,0,0.8,0,35,Battle.DamageType.KINETIC,.55);
        check(ap.hullDamage()>standard.hullDamage(),
                "Armor-piercing projectile bypasses real protective tile, not a fake number");
        check(penetrated.totalArmor()>ordinary.totalArmor(),
                "Penetration must divert energy past, not magically remove more armor");

        for(AmmunitionTable.Kinetic round:AmmunitionTable.Kinetic.values()){
            var p=AmmunitionTable.kinetic(round);
            Battle world=scenario(true,false);
            Battle.Ship firing=world.ships().get(0);
            world.setShipKineticAmmunition(firing.id,round);
            int before=firing.kineticAmmo;
            int elapsed=0;
            int contactWindow=2*SensorLightTimeHistory.propagationTicks(295)+30;
            check(!world.hasTargetContact(firing,world.ships().get(1),295),
                    "Fresh ships cannot fire at unreceived sensor contact");
            while(elapsed<contactWindow&&firing.kineticAmmo==before){
                world.step();
                elapsed++;
            }
            check(firing.kineticAmmo==before-1,
                    "Each sensor-authorized real shell consumes ammunition: "+round);
            check(world.ballistics().stream().allMatch(b->b.ammunition==round),
                    "The physical round retains its chosen ammo identity: "+round);
            check(world.ballistics().stream().allMatch(b->
                    b.spec.equals(p.physical(firing.kineticProjectile))),
                    "Ammo changes interceptor HP, speed, RCS and signature: "+round);
            check(world.visualEvents().stream().anyMatch(e->
                    e.phase()==Battle.VisualPhase.FIRE&&e.weapon().equals("KINETIC")),
                    "Shot must be a real FIRE event: "+round);
            Battle mirror=scenario(true,false);
            mirror.setShipKineticAmmunition(mirror.ships().get(0).id,round);
            fly(world,60);fly(mirror,elapsed+60);
            check(world.fingerprint()==mirror.fingerprint(),
                    "Ammunition and physical impacts replay deterministically: "+round);
        }
        for(AmmunitionTable.Warhead warhead:AmmunitionTable.Warhead.values()){
            var profile=AmmunitionTable.warhead(warhead);
            Battle world=scenario(false,false);
            Battle.Ship source=world.ships().get(0);
            world.setShipWarhead(source.id,Battle.DamageType.MISSILE,warhead);
            int before=source.missileAmmo,elapsed=0;
            int contactWindow=2*SensorLightTimeHistory.propagationTicks(295)+30;
            while(elapsed<contactWindow&&source.missileAmmo==before){
                world.step();
                elapsed++;
            }
            check(source.missileAmmo==before-1,
                    "A sensor-authorized guided warhead must consume actual ammunition: "+warhead);
            check(world.missiles().stream().anyMatch(m->m.sourceId==source.id
                    &&m.warhead==warhead&&m.spec.equals(
                        AmmunitionTable.guidedPhysical(Battle.DamageType.MISSILE,
                                warhead,source.missileProjectile))),
                    "Actual missile body must carry warhead's signature and integrity: "+warhead);
            Battle same=scenario(false,false);
            same.setShipWarhead(same.ships().get(0).id,Battle.DamageType.MISSILE,warhead);
            fly(world,80);fly(same,elapsed+80);
            check(world.fingerprint()==same.fingerprint(),"Warhead must replay exactly: "+warhead);
        }
        Battle blast=scenario(true,true);
        blast.setShipKineticAmmunition(blast.ships().get(0).id,
                AmmunitionTable.Kinetic.HIGH_EXPLOSIVE);
        double neighborBefore=blast.ships().get(2).hull;
        fly(blast,56);
        check(blast.ships().get(2).hull<neighborBefore,
                "Nearby actual hull receives explosive falloff, not a texture-only explosion");
        Set<Long> unique=new HashSet<>();
        for(var event:blast.visualEvents())if(event.phase()==Battle.VisualPhase.FIRE)
            check(unique.add(event.shotId()),"Each fired shell has independent shot identity");

        Battle emp=scenario(false,false);
        emp.setShipWarhead(emp.ships().get(0).id,Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.EMP);
        fly(emp,120);
        check(emp.ships().get(1).power<10000,
                "A physically striking EMP warhead drains target power");
        Battle shield=scenario(false,false);
        shield.ships().get(1).defense.receiveShieldTransfer(50);
        shield.ships().get(1).shields=shield.ships().get(1).defense.totalShields();
        shield.setShipWarhead(shield.ships().get(0).id,Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.SHIELD_BREAKER);
        fly(shield,120);
        check(shield.ships().get(1).shields<50,
                "Shield warhead actually reduces directional shield charge");
        System.out.println("PASS 5 ballistic ammunition and 6 missile payloads: physical rounds, AP penetration, explosions, EMP, shield attack and replay");
    }
}
