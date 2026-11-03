package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/** Queued guided rounds retain one authentic launch fix after target maneuver. */
public final class GuidedSalvoCausalityTest {
    private GuidedSalvoCausalityTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static Battle scene(long seed){
        Battle b=new Battle(seed);
        Battle.Ship shooter=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,600,100);
        b.registerDiagnosticStationaryTarget(target.id);
        shooter.speed=0;
        shooter.maxPower=shooter.power=10000;shooter.powerRegen=0;
        shooter.maxHeat=10000;
        shooter.sensorRange=850;
        shooter.infraredRange=shooter.xrayRange=shooter.graviticRange=0;
        shooter.missileDamage=45;shooter.missileAmmo=12;
        shooter.laserDamage=shooter.kineticDamage=shooter.plasmaDamage=0;
        shooter.particleDamage=shooter.torpedoDamage=0;
        shooter.fighterReserve=shooter.droneReserve=0;
        shooter.pdRange=0;
        target.maxHull=target.hull=100000;
        target.laserDamage=target.kineticDamage=target.plasmaDamage=0;
        target.particleDamage=target.missileDamage=target.torpedoDamage=0;
        target.fighterReserve=target.droneReserve=0;
        target.pdRange=0;
        b.setShipLaunchMode(shooter.id,Battle.DamageType.MISSILE,
                OrdnanceDoctrine.Mode.MICRO_SWARM);
        return b;
    }
    private static List<Double> frozenVolley(Battle b){
        Battle.Ship source=b.ships().get(0),target=b.ships().get(1);
        for(int i=0;i<80&&source.launched==0;i++)b.step();
        check(source.launched>0,
                "Real guided micro-swarm never launched after radar light-time");
        check(b.pendingSalvoCount()>0,
                "Micro-swarm has no pending rounds to verify frozen launch fix");
        target.y+=420;
        for(int i=0;i<20&&source.launched<8;i++)b.step();
        check(source.launched==8,
                "Eight finite micro-swarm rounds failed independent queued launch");
        List<Double> bearings=new ArrayList<>();
        for(Battle.VisualEvent event:b.visualEvents())
            if(event.sourceId()==source.id&&
                    event.phase()==Battle.VisualPhase.FIRE&&
                    "MISSILE".equals(event.weapon()))
                bearings.add(event.targetY());
        check(bearings.size()==8,
                "Actual eight guided rounds did not publish distinct launch events");
        for(double recordedY:bearings)
            check(StrictMath.abs(recordedY-100)<1e-6,
                    "Queued guided launch cheated with target position after maneuver");
        check(source.missileAmmo==4,
                "Each real guided submunition must spend one physical round");
        return bearings;
    }
    public static void main(String[] args){
        Battle a=scene(816213),b=scene(816213);
        var x=frozenVolley(a);
        var y=frozenVolley(b);
        check(x.equals(y)&&a.fingerprint()==b.fingerprint(),
                "Causal guided volley did not replay identically");
        System.out.println("PASS eight guided rounds preserve original received fix after evasive target maneuver");
    }
}
