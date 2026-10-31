package diplomacy.sim;

import java.util.HashSet;
import java.util.Set;

/** Real 30Hz missile/torpedo doctrine regression; no assets or rendering. */
public final class OrdnanceDoctrineTest {
    private OrdnanceDoctrineTest(){}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Battle scenario(Battle.DamageType family,OrdnanceDoctrine.Mode mode,int ammo) {
        Battle world=new Battle(190753);
        Battle.Ship attacker=world.addShip(0,Battle.Faction.TERRAN,200,300);
        Battle.Ship target=world.addShip(1,Battle.Faction.CARNELIAN,495,300);
        for(Battle.Ship ship:world.ships()){
            ship.speed=ship.vx=ship.vy=0;
            ship.laserDamage=ship.kineticDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.missileDamage=ship.torpedoDamage=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.pdRange=0;ship.fighterReserve=ship.droneReserve=ship.repairDroneReserve=0;
            ship.maxPower=ship.power=10000;
            ship.powerRegen=0;ship.maxHeat=10000;ship.heat=0;
        }
        target.maxHull=target.hull=100000;
        if(family==Battle.DamageType.MISSILE){
            attacker.missileDamage=30;attacker.missileAmmo=ammo;
        }else{
            attacker.torpedoDamage=60;attacker.torpedoAmmo=ammo;
        }
        world.setShipLaunchMode(attacker.id,family,mode);
        return world;
    }
    private static int ammo(Battle.Ship ship,Battle.DamageType family){
        return family==Battle.DamageType.MISSILE?ship.missileAmmo:ship.torpedoAmmo;
    }
    private static long fired(Battle world,String type){
        return world.visualEvents().stream().filter(e->
            e.phase()==Battle.VisualPhase.FIRE&&type.equals(e.weapon())&&e.team()==0).count();
    }
    private static void launches(){
        for(Battle.DamageType family:new Battle.DamageType[]{
                Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO}){
            for(OrdnanceDoctrine.Mode mode:OrdnanceDoctrine.Mode.values()){
                var p=OrdnanceDoctrine.forWeapon(family,mode);
                Battle world=scenario(family,mode,40);
                Battle.Ship attacker=world.ships().get(0);
                double power=attacker.power;
                world.step();
                // The first round fires immediately, not when its graphics loads.
                check(attacker.launched==1&&power>attacker.power,
                        "First physical launch must consume energy for "+family+"/"+mode);
                check(world.pendingSalvoCount()<=1,"Only one family launch sequence at a time");
                attacker.missileReload=attacker.torpedoReload=10000;
                for(int i=0;i<(int)p.spanTicks()+4;i++)world.step();
                check(attacker.launched==p.count(),"Exact ordnance body count "+family+"/"+mode);
                check(ammo(attacker,family)==40-p.ammoNeeded(),
                        "Decoys and micro-ordnance consume finite ammunition "+mode);
                check(world.roundsFiredBySalvos==p.count()&&world.salvosStarted==1,
                        "Each launch mode creates one ordered salvo");
                check(world.pendingSalvoCount()==0,"No orphaned launch queues");
                check(fired(world,family.name())==p.real(),
                        "Only live payloads count as real missiles "+mode);
                check(fired(world,"DECOY")==p.decoys(),
                        "Every decoy creates a distinct physical FIRE event");
                check(world.decoysLaunched==p.decoys(),
                        "Countermeasure records match actual launched bodies");
                if(mode==OrdnanceDoctrine.Mode.MICRO_SWARM){
                    check(world.missiles().stream().allMatch(m->
                            m.payload==OrdnanceDoctrine.Payload.MICRO
                            &&m.spec.health()<attacker.missileProjectile.health()
                            &&m.spec.crossSection() < 1.5),
                            "Micro rounds must be smaller, weaker individual entities");
                }
                if(mode==OrdnanceDoctrine.Mode.HEAVY_STRIKE)
                    check(world.missiles().stream().allMatch(m->
                        m.payload==OrdnanceDoctrine.Payload.HEAVY
                        &&m.spec.health()>2),
                        "Heavy salvo bodies must actually be hardened and slower");
                if(mode==OrdnanceDoctrine.Mode.DECOY_CLOUD)
                    check(world.missiles().stream().filter(m->m.decoy).allMatch(m->m.damage==0),
                        "Decoy countermeasures have zero terminal damage");
            }
        }
    }
    private static void coldIgnition(){
        Battle world=scenario(Battle.DamageType.MISSILE,OrdnanceDoctrine.Mode.COLD_LAUNCH,4);
        world.step();
        Battle.Missile m=world.missiles().get(0);
        double x=m.x,y=m.y,vx=m.vx,vy=m.vy;
        for(int i=0;i<8;i++)world.step();
        check(m.x>x && StrictMath.abs(m.x-(x+8*vx*Battle.DT))<1e-6
                &&StrictMath.abs(m.y-(y+8*vy*Battle.DT))<1e-6
                &&m.vx==vx&&m.vy==vy&&m.ignitionDelayTicks>0,
                "Cold ejection must preserve its small physical impulse without thrust");
        for(int i=0;i<20;i++)world.step();
        check(m.ignitionDelayTicks==0 &&m.x>x,
                "Delayed ignition produces propulsion later, not instant damage");
    }
    private static void insufficientAmmo(){
        Battle world=scenario(Battle.DamageType.MISSILE,OrdnanceDoctrine.Mode.DECOY_CLOUD,4);
        world.step();
        check(world.missiles().isEmpty()&&world.salvosStarted==0,
                "Launchers cannot invent four decoys and a warhead from four rounds");
    }
    private static void survivalAndReplay(){
        Battle a=scenario(Battle.DamageType.MISSILE,OrdnanceDoctrine.Mode.DECOY_CLOUD,40);
        Battle b=scenario(Battle.DamageType.MISSILE,OrdnanceDoctrine.Mode.DECOY_CLOUD,40);
        a.ships().get(1).pdRange=b.ships().get(1).pdRange=700;
        for(int i=0;i<210;i++){a.step();b.step();}
        check(a.fingerprint()==b.fingerprint(),"Launch queues, boost delays and decoys must replay exactly");
        check(a.decoysLaunched==4,"A genuine five-body countermeasure salvo launched");
        Set<Long> decoys=new HashSet<>();
        for(var event:a.visualEvents())
            if(event.phase()==Battle.VisualPhase.FIRE&&event.weapon().equals("DECOY"))
                decoys.add(event.shotId());
        check(decoys.size()==4,"Each decoy is independently trackable by shot ID");
        check(a.visualEvents().stream().noneMatch(e->
                e.phase()==Battle.VisualPhase.IMPACT&&decoys.contains(e.shotId())),
                "Decoy impact events must NEVER masquerade as kinetic/warhead damage");
    }
    private static void invalid(){
        try{
            OrdnanceDoctrine.forWeapon(Battle.DamageType.LASER,OrdnanceDoctrine.Mode.RIPPLE);
            throw new AssertionError("Beam cannot use a guided-body-only doctrine");
        }catch(IllegalArgumentException correct){/* expected */}
        try{
            OrdnanceDoctrine.forWeapon(Battle.DamageType.MISSILE,null);
            throw new AssertionError("Null pattern cannot install");
        }catch(IllegalArgumentException correct){/* expected */}
    }
    public static void main(String[] args){
        invalid();launches();coldIgnition();insufficientAmmo();survivalAndReplay();
        System.out.println("PASS 14 physical missile/torpedo schematics, finite reload and ammo, decoys, cold ignition, PD and replay");
    }
}
