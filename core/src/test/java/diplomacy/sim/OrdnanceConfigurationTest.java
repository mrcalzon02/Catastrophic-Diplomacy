package diplomacy.sim;

/** Live launch and steering integration: no renderer or simulated UI. */
public final class OrdnanceConfigurationTest {
    private OrdnanceConfigurationTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static Battle scene(){
        Battle b=new Battle(908172L);
        Battle.Ship a=b.addShip(0,Battle.Faction.TERRAN,200,300);
        Battle.Ship t=b.addShip(1,Battle.Faction.CARNELIAN,760,300);
        for(Battle.Ship s:b.ships()){
            s.speed=s.vx=s.vy=0;
            s.laserDamage=s.plasmaDamage=s.particleDamage=s.kineticDamage=0;
            s.missileDamage=s.torpedoDamage=0;
            s.missileAmmo=s.torpedoAmmo=0;
            s.pdRange=0;s.fighterReserve=s.droneReserve=s.repairDroneReserve=0;
            s.maxPower=s.power=20000;s.powerRegen=0;s.maxHeat=20000;s.heat=0;
        }
        a.missileAmmo=32;a.missileDamage=30;
        t.maxHull=t.hull=100000;
        return b;
    }
    private static void independentSelection(){
        Battle b=scene();
        var a=b.ships().get(0);
        b.setShipLaunchMode(a.id,Battle.DamageType.MISSILE,
                OrdnanceDoctrine.Mode.MICRO_SWARM);
        b.setShipPropulsionMode(a.id,Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.SILENT_GHOST);
        b.setShipGuidanceMode(a.id,Battle.DamageType.MISSILE,
                OrdnanceGuidance.Mode.PINCER);
        b.step();
        check(b.pendingSalvoCount()==1,"swarm has a physical queued launch");
        check(b.missiles().size()==1,"first salvo body immediately launches");
        check(b.missiles().get(0).propulsionMode==OrdnancePropulsion.Mode.SILENT_GHOST,
                "swarm motor independent of original pulse-burn default");
        check(b.missiles().get(0).guidanceMode==OrdnanceGuidance.Mode.PINCER,
                "swarm guidance independent of original pursuit default");
        // Change the ship's orders while the original ammunition is in flight.
        b.setShipPropulsionMode(a.id,Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.CONSTANT_BURN);
        b.setShipGuidanceMode(a.id,Battle.DamageType.MISSILE,
                OrdnanceGuidance.Mode.PURSUIT);
        a.missileReload=100000;
        for(int i=0;i<12;i++)b.step();
        check(a.launched==8&&a.missileAmmo==24,
                "all eight micro projectiles actually paid and launched");
        check(b.missiles().size()==8,"queued swarm missiles still physically exist");
        for(var m:b.missiles()){
            check(m.propulsionMode==OrdnancePropulsion.Mode.SILENT_GHOST
                    &&m.guidanceMode==OrdnanceGuidance.Mode.PINCER
                    &&m.payload==OrdnanceDoctrine.Payload.MICRO,
                    "in-flight salvo must not inherit subsequent ship edits");
        }
        check(b.missiles().get(0).approachSide!=b.missiles().get(1).approachSide,
                "pincer wings must split into opposite signed lanes");
    }
    private static void coldIndependence(){
        Battle b=scene();
        var a=b.ships().get(0);
        b.setShipLaunchMode(a.id,Battle.DamageType.MISSILE,
                OrdnanceDoctrine.Mode.SINGLE);
        b.setShipPropulsionMode(a.id,Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.COLD_START);
        b.step();
        check(b.missiles().get(0).ignitionDelayTicks==14
                &&b.missiles().get(0).thrustPhase==OrdnancePropulsion.Phase.COLD_EJECT,
                "ordinary single launcher can cold eject without cold-launch salvo");
        Battle c=scene();
        var src=c.ships().get(0);
        c.setShipLaunchMode(src.id,Battle.DamageType.MISSILE,
                OrdnanceDoctrine.Mode.COLD_LAUNCH);
        c.setShipPropulsionMode(src.id,Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.CONSTANT_BURN);
        c.step();
        check(c.missiles().get(0).ignitionDelayTicks==0,
                "old cold salvo can override motor and use immediate ignition");
        c.ships().get(0).resetPropulsionMode(Battle.DamageType.MISSILE);
        check(c.ships().get(0).propulsionMode(Battle.DamageType.MISSILE)
                ==OrdnancePropulsion.Mode.COLD_START,
                "removing override restores old doctrine defaults");
    }
    private static void guidanceIsPhysical(){
        var pursuit=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.PURSUIT,
                0,0,200,0,500,0,0,100,10,1);
        var lead=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.LEAD_INTERCEPT,
                0,0,200,0,500,0,0,100,10,1);
        check(pursuit.dy()==0&&lead.dy()>100,"lead seeker predicts moving target");
        var first=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.PINCER,
                0,0,150,0,500,0,0,0,5,1);
        var second=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.PINCER,
                0,0,150,0,500,0,0,0,5,-1);
        check(first.dy()>0&&second.dy()<0,"opposite intercept corridors");
        var terminal=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.PINCER,
                480,0,150,0,500,0,0,0,95,1);
        check(terminal.dy()==0,"pincer converges at terminal approach");
        var dogleg=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.DOGLEG,
                0,0,150,0,500,0,0,0,5,1);
        var doglegEnd=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.DOGLEG,
                0,0,150,0,500,0,0,0,95,1);
        check(dogleg.dy()>0&&doglegEnd.dy()==0,
                "dogleg stores an offset then resumes terminal intercept");
        var weaving=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.EVASIVE_WEAVE,
                0,0,150,0,500,0,0,0,8,1);
        var weavingLater=OrdnanceGuidance.aim(OrdnanceGuidance.Mode.EVASIVE_WEAVE,
                0,0,150,0,500,0,0,0,24,1);
        check(weaving.dy()*weavingLater.dy()<0,
                "evasion deliberately alternates thrust-bounded side demand");
        var coast=OrdnancePropulsion.advanceVelocity(150,0,first.dx(),first.dy(),
                220,.35,OrdnancePropulsion.Phase.COAST,Battle.DT,100);
        check(coast.vx()==150&&coast.vy()==0,
                "pincer/wave guidance cannot steer a coasting inertial missile");
    }
    private static void replay(){
        Battle a=scene(),b=scene();
        for(Battle w:new Battle[]{a,b}){
            var s=w.ships().get(0);
            w.setShipLaunchMode(s.id,Battle.DamageType.MISSILE,
                    OrdnanceDoctrine.Mode.RIPPLE);
            w.setShipPropulsionMode(s.id,Battle.DamageType.MISSILE,
                    OrdnancePropulsion.Mode.TERMINAL_SPRINT);
            w.setShipGuidanceMode(s.id,Battle.DamageType.MISSILE,
                    OrdnanceGuidance.Mode.LEAD_INTERCEPT);
        }
        for(int i=0;i<90;i++){a.step();b.step();}
        check(a.fingerprint()==b.fingerprint(),
                "independent saved salvo configuration must replay deterministically");
    }
    private static void invalid(){
        Battle b=scene();
        try {
            b.setShipGuidanceMode(b.ships().get(0).id,Battle.DamageType.LASER,
                    OrdnanceGuidance.Mode.PINCER);
            throw new AssertionError("Unguided beams cannot install missile guidance");
        }catch(IllegalArgumentException expected){/* correct */}
        try {
            b.setShipPropulsionMode(b.ships().get(0).id,Battle.DamageType.MISSILE,null);
            throw new AssertionError("Null motor cannot install");
        }catch(IllegalArgumentException expected){/* correct */}
    }
    public static void main(String[] args){
        invalid();guidanceIsPhysical();independentSelection();coldIndependence();replay();
        System.out.println("PASS independent salvo/propulsion/seeker configuration, real offset guidance, cold launch, ammo, snapshot and deterministic replay");
    }
}
