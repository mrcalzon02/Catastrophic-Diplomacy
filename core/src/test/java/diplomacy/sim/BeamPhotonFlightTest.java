package diplomacy.sim;

/** Objective main-beam wavefront tests, never visual-quality build gates. */
public final class BeamPhotonFlightTest {
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static Battle scene(long seed){
        Battle b=new Battle(seed);
        Battle.Ship gun=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship victim=b.addShip(1,Battle.Faction.DRAXIAN,260,100);
        b.registerDiagnosticStationaryTarget(victim.id);
        gun.speed=0;gun.accuracy=3;
        gun.maxPower=gun.power=1000;gun.powerRegen=1000;
        gun.sensorRange=500;gun.laserDamage=30;
        gun.missileDamage=gun.kineticDamage=gun.plasmaDamage=
                gun.particleDamage=gun.torpedoDamage=0;
        victim.laserDamage=victim.missileDamage=victim.kineticDamage=
                victim.plasmaDamage=victim.particleDamage=victim.torpedoDamage=0;
        victim.maxHull=victim.hull=100000;
        return b;
    }
    private static Battle.VisualEvent firstShot(Battle b){
        for(int i=0;i<40;i++){
            b.step();
            for(Battle.VisualEvent event:b.visualEvents())
                if(event.phase()==Battle.VisualPhase.FIRE&&
                        event.sourceId()==b.ships().get(0).id&&
                        event.weapon().startsWith("LASER"))return event;
        }
        throw new AssertionError("No historically authorized laser launch");
    }
    private static void flightAndDodge(){
        Battle hit=scene(90131);
        var fired=firstShot(hit);
        hit.ships().get(0).laserDamage=0;
        check(hit.hitCount==0&&hit.pendingBeamPhotonCount()>0,
                "Main beam caused an instantaneous fire-tick hull impact");
        for(int i=0;i<9&&hit.pendingBeamPhotonCount()>0;i++)hit.step();
        check(hit.pendingBeamPhotonCount()==0,"Finite photon did not expire");
        check(hit.visualEvents().stream().anyMatch(e->e.shotId()==fired.shotId()
                &&e.phase()==Battle.VisualPhase.IMPACT&&e.tick()>fired.tick()),
                "Stationary hull did not receive its delayed laser energy");
        Battle dodge=scene(90131);
        var shot=firstShot(dodge);
        dodge.ships().get(0).laserDamage=0;
        dodge.ships().get(1).y+=240;
        for(int i=0;i<9&&dodge.pendingBeamPhotonCount()>0;i++)dodge.step();
        check(dodge.pendingBeamPhotonCount()==0,
                "Evaded beam remained in flight beyond its range");
        check(dodge.visualEvents().stream().noneMatch(e->
                e.shotId()==shot.shotId()&&e.phase()==Battle.VisualPhase.IMPACT),
                "Beam assigned an obsolete automatic hit after target maneuver");
        System.out.println("PASS delayed laser photons, real hull impact, physical dodge");
    }
    private static void deterministic(){
        Battle a=scene(90132),b=scene(90132);
        for(int i=0;i<36;i++){
            if(i==20){a.ships().get(1).y+=180;b.ships().get(1).y+=180;}
            a.step();b.step();
            check(a.fingerprint()==b.fingerprint(),
                    "Identical beam photon paths diverged in replay");
        }
        System.out.println("PASS delayed photon replay and pending pulse fingerprint");
    }
    public static void main(String[] args){flightAndDodge();deterministic();}
}
