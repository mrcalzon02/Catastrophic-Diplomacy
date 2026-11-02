package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Real fitted dumbfire rockets, 4/8/16 shells, fixed heading, coast,
 * finite magazines, saved research IDs, collisions and laser PD. */
public final class RocketBarrageTest {
    private RocketBarrageTest(){}
    private static void require(boolean ok,String what){
        if(!ok)throw new AssertionError(what);
    }
    private static int missileSocket(Shipyard.Design design){
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed item=design.at(i);
            if(item!=null&&item.component.endsWith("-MSL"))return i;
        }
        throw new AssertionError("Starter dreadnought has no missile weapon socket");
    }
    private static Shipyard.Design mounted(TechnologyCatalog tech,RocketBarrage.Size size){
        Shipyard.Design starter=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        Shipyard.Design design=starter.install(missileSocket(starter),
                "BASE-TER-"+size.code);
        require(Shipyard.inspect(design,tech).valid(),
                "Rocket pod cannot fit actual weapon hardpoint "+size);
        require(Shipyard.rocketRacks(design,tech).size()==1,
                "Installed rocket hardware has no real launcher "+size);
        return design;
    }
    private static Battle scene(TechnologyCatalog catalog,RocketBarrage.Size size,
                                int count,RocketBarrage.Motor motor,boolean pd) {
        Battle battle=new Battle(5008L+size.ordinal()*19+count);
        battle.spawnWing(0,mounted(catalog,size),catalog,1,220,300);
        Battle.Ship enemy=battle.addShip(1,Battle.Faction.CARNELIAN,600,300);
        battle.registerDiagnosticStationaryTarget(enemy.id);
        Battle.Ship shooter=battle.ships().get(0);
        shooter.laserDamage=shooter.missileDamage=shooter.kineticDamage=0;
        shooter.plasmaDamage=shooter.particleDamage=shooter.torpedoDamage=0;
        shooter.pdRange=0;shooter.fighterReserve=shooter.droneReserve=0;
        shooter.maxPower=shooter.power=20000;
        shooter.maxHeat=20000;shooter.heat=0;
        enemy.maxHull=enemy.hull=20000;
        enemy.maxPower=enemy.power=20000;
        enemy.pdRange=pd?1200:0;
        if(pd){
            enemy.pointDefenseProfile=new ProjectileModel.Defense(2.9,3.7,35);
            enemy.pointDefenseCondition=1;
        }
        RocketBarrage.Rack rack=shooter.rocketRacks.get(0);
        battle.setShipRocketBarrage(shooter.id,rack.slot,count,motor);
        return battle;
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        for(RocketBarrage.Size size:RocketBarrage.Size.values()){
            require(tech.hasBase("BASE-TER-"+size.code)
                    &&tech.get("TECH-TER-"+size.code+"-OUTPUT-09")!=null,
                    "Real rocket hardware has no nine-rank research family "+size);
            var fitted=mounted(tech,size);
            String decoded=Shipyard.encode(fitted);
            require(Shipyard.encode(Shipyard.decode(decoded,tech)).equals(decoded),
                    "Rocket fitting not preserved by existing blueprint codec");
            for(int count:new int[]{4,8,16}){
                RocketBarrage.Motor motor=count==8?
                        RocketBarrage.Motor.SUSTAINED_BURN:RocketBarrage.Motor.BOOST_COAST;
                Battle battle=scene(tech,size,count,motor,false);
                Battle.Ship shooter=battle.ships().get(0);
                RocketBarrage.Rack rack=shooter.rocketRacks.get(0);
                int original=rack.rounds;
                for(int tick=0;tick<36;tick++)battle.step();
                require(battle.rocketRoundsFired==count,
                        "Failed to physically launch every rocket "+size+"/"+count);
                require(rack.rounds==original-count&&rack.reload>7,
                        "Cheap rocket magazine is not finite with a real long reload");
                require(battle.missiles().isEmpty(),
                        "Dumbfire rockets incorrectly created guided seeker entities");
                require(rack.magazineUnitCost<30,
                        "Unguided rocket unit replenishment is not economical");
                for(Battle.Rocket rocket:battle.rockets()){
                    require(rocket.spec.health()<4
                            &&rocket.targetId==battle.ships().get(1).id,
                            "Rocket lost actual fragile point-defense body");
                    require(rocket.motor==motor,
                            "Configured short boost or long burn did not enter actual body");
                }
            }
        }
        // Both motors must accelerate in a straight line then coast forever
        // without guidance. The target can simply vacate the line of fire.
        for(RocketBarrage.Motor motor:RocketBarrage.Motor.values()){
            Battle battle=scene(tech,RocketBarrage.Size.MEDIUM,4,motor,false);
            battle.step();
            require(!battle.rockets().isEmpty(),"Rocket did not launch at trigger");
            Battle.Rocket first=battle.rockets().get(0);
            double heading=first.heading;
            Battle.Ship target=battle.ships().get(1);
            target.y+=350; // hard evasive sidestep AFTER the immutable firing solution
            for(int i=0;i<78;i++)battle.step();
            require(heading==first.heading,
                    "Dumbfire rocket acquired steering toward moving target");
            require(battle.rocketHits==0,
                    "Rocket barrage magically tracked its evasive target");
        }
        Battle exposed=scene(tech,RocketBarrage.Size.SMALL,16,
                RocketBarrage.Motor.SUSTAINED_BURN,true);
        for(int i=0;i<110;i++)exposed.step();
        require(exposed.rocketRoundsFired==16&&exposed.rocketIntercepts>0,
                "High-visibility low-HP rocket barrage failed laser PD interception");
        Battle fixed=scene(tech,RocketBarrage.Size.MEDIUM,8,
                RocketBarrage.Motor.BOOST_COAST,false);
        for(int i=0;i<110;i++)fixed.step();
        require(fixed.rocketHits>0,
                "Real undefended target never suffered a rocket collision");
        Battle a=scene(tech,RocketBarrage.Size.LARGE,8,
                RocketBarrage.Motor.BOOST_COAST,false);
        Battle b=scene(tech,RocketBarrage.Size.LARGE,8,
                RocketBarrage.Motor.BOOST_COAST,false);
        for(int i=0;i<120;i++){
            a.step();b.step();
            require(a.fingerprint()==b.fingerprint(),
                    "Seeded rocket burst failed deterministic replay");
        }
        System.out.println("PASS four native rocket sizes, 4/8/16 cheap physical volleys, two fixed-bearing motor envelopes, long reloads, PD, dodge, hit and replay");
    }
}
