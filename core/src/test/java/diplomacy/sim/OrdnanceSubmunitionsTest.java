package diplomacy.sim;

import java.util.HashSet;
import java.util.Set;

/** Deterministic 30 Hz runtime assertions; no images or decorative gating. */
public final class OrdnanceSubmunitionsTest {
    private OrdnanceSubmunitionsTest(){}
    private static void check(boolean b,String message) {
        if(!b)throw new AssertionError(message);
    }
    private static Battle scene(OrdnanceDoctrine.Mode doctrine,boolean torpedo,boolean pd) {
        Battle b=new Battle(11335577);
        Battle.Ship source=b.addShip(0,Battle.Faction.TERRAN,180,300);
        Battle.Ship target=b.addShip(1,Battle.Faction.CARNELIAN,560,300);
        Battle.Ship wingman=b.addShip(1,Battle.Faction.CARNELIAN,580,345);
        for(Battle.Ship ship:b.ships()){
            ship.speed=ship.vx=ship.vy=0;
            ship.laserDamage=ship.kineticDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.missileDamage=ship.torpedoDamage=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.fighterReserve=ship.droneReserve=ship.repairDroneReserve=0;
            ship.pdRange=0;
            ship.power=ship.maxPower=10000;
            ship.maxHeat=10000;ship.heat=0;
            ship.powerRegen=0;
            ship.maxHull=ship.hull=100000;
        }
        source.missileDamage=torpedo?0:60;
        source.torpedoDamage=torpedo?90:0;
        source.missileAmmo=torpedo?0:12;
        source.torpedoAmmo=torpedo?12:0;
        b.setShipLaunchMode(source.id,torpedo?Battle.DamageType.TORPEDO
                :Battle.DamageType.MISSILE,doctrine);
        if(pd){
            target.pdRange=1000;
            target.pointDefenseProfile=new ProjectileModel.Defense(2.9,4,40);
        }
        return b;
    }
    private static void deploy(OrdnanceDoctrine.Mode mode,boolean torpedo){
        Battle b=scene(mode,torpedo,false);
        Battle.Ship source=b.ships().get(0);
        b.step();
        source.missileReload=source.torpedoReload=10000;
        int carrierCount=mode==OrdnanceDoctrine.Mode.CASCADE_SWARM?4:1;
        int expected=carrierCount*6;
        int startAmmo=12;
        for(int i=0;i<125&&b.carriersSplit<carrierCount;i++)b.step();
        check(b.carriersSplit==carrierCount,"All surviving dispensers must open in flight");
        check(b.submunitionsDeployed==expected,"Each carrier creates six real physical children");
        check(source.launched==carrierCount,"Children never charge duplicate launcher ammunition");
        check((torpedo?source.torpedoAmmo:source.missileAmmo)==startAmmo-carrierCount,
                "Only the carrier consumes magazine rounds");
        check(b.missiles().size()==expected,"Parent bodies are replaced with children");
        Set<Long> identifiers=new HashSet<>();
        Set<Integer> targets=new HashSet<>();
        double budget=0;
        for(Battle.Missile m:b.missiles()){
            check(identifiers.add(m.visualId),"Every child has a unique physical shot ID");
            check(m.payload==(mode==OrdnanceDoctrine.Mode.CASCADE_SWARM
                    ?OrdnanceDoctrine.Payload.ULTRA_MICRO:OrdnanceDoctrine.Payload.MICRO),
                    "Dispensers release the requested child tier");
            check(!OrdnanceSubmunitions.carrier(m.payload)&&!m.torpedo,
                    "Submunitions must not split recursively or become torpedoes");
            check(m.spec.crossSection()<1&&m.spec.health()<6,
                    "Micro bodies need physically small PD targets and HP");
            targets.add(m.targetId);
            budget+=m.damage;
        }
        check(targets.size()>1,"Cluster target selection should spread to nearby hostile hulls");
        check(budget>0,"Children carry actual damage");
        long deployedFire=b.visualEvents().stream().filter(e->
                e.phase()==Battle.VisualPhase.FIRE&&e.weapon().equals("MISSILE")
                &&identifiers.contains(e.shotId())).count();
        check(deployedFire==expected,"All real submunitions emit distinct launch events");
        check(b.visualEvents().stream().noneMatch(e->
                e.phase()==Battle.VisualPhase.IMPACT&&
                (identifiers.contains(e.shotId())||e.weapon().equals("TORPEDO"))),
                "Splitting cannot inflict immediate fictional impact damage");
        double ancestorDamage= (torpedo?source.torpedoDamage:source.missileDamage)
                *OrdnanceDoctrine.forWeapon(
                torpedo?Battle.DamageType.TORPEDO:Battle.DamageType.MISSILE,mode)
                .damageMultiplier()*carrierCount;
        check(budget<=ancestorDamage*.89+1e-5,
                "Splitting conserves the original destructive budget");
    }
    private static void interception(){
        Battle b=scene(OrdnanceDoctrine.Mode.CLUSTER_CARRIER,false,true);
        Battle.Ship source=b.ships().get(0);
        b.step();
        source.missileReload=10000;
        for(int i=0;i<40;i++)b.step();
        check(b.carriersSplit==0 && b.submunitionsDeployed==0,
                "An intercepted dispenser must never spawn imaginary children");
        check(b.missileIntercepts>0,"Actual point defense should destroy the carrier");
    }
    private static void replay(){
        Battle a=scene(OrdnanceDoctrine.Mode.CASCADE_SWARM,false,false);
        Battle b=scene(OrdnanceDoctrine.Mode.CASCADE_SWARM,false,false);
        for(int i=0;i<105;i++){a.step();b.step();}
        check(a.carriersSplit>0&&a.submunitionsDeployed>0,
                "Replay fixture must actually exercise child deployment");
        check(a.fingerprint()==b.fingerprint(),
                "New individual targets, ordnance child attributes and splits must replay");
    }
    private static void physics(){
        ProjectileModel.Spec standard=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        var micro=OrdnanceSubmunitions.childSpec(standard,OrdnanceDoctrine.Payload.CLUSTER_CARRIER);
        var ultra=OrdnanceSubmunitions.childSpec(standard,OrdnanceDoctrine.Payload.MICRO_CLUSTER_CARRIER);
        check(micro.speed()>standard.speed()&&ultra.speed()>micro.speed(),
                "Child propulsion must correspond to smaller faster physical rounds");
        check(ultra.crossSection()<micro.crossSection()&&ultra.health()<micro.health(),
                "Ultra-micro bodies must be harder to acquire and easier to destroy");
        check(OrdnanceSubmunitions.children(OrdnanceDoctrine.Payload.ULTRA_MICRO)==0,
                "Only first-generation dispensers can split");
    }
    public static void main(String[] args){
        physics();
        deploy(OrdnanceDoctrine.Mode.CLUSTER_CARRIER,false);
        deploy(OrdnanceDoctrine.Mode.CLUSTER_CARRIER,true);
        deploy(OrdnanceDoctrine.Mode.CASCADE_SWARM,false);
        interception();replay();
        System.out.println("PASS live cluster carrier and cascade swarm: PD, ammo, damage, art-independent split and replay");
    }
}
