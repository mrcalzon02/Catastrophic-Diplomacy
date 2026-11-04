package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Real finite fighter cartridges, radar flak diversion, replay and IR isolation. */
public final class FighterCountermeasureTest {
    private FighterCountermeasureTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount mount){
        for(Shipyard.Slot s:hull.slots)if(s.mount()==mount)return s.index();
        throw new AssertionError("No physical "+mount+" socket");
    }
    private static void spectralHardware(){
        for(FighterModel.Hull hull:FighterModel.Hull.values()){
            var base=FighterCountermeasures.forHull(hull,0);
            var advanced=FighterCountermeasures.forHull(hull,9);
            check(base.chaff()>0&&base.flares()>0&&
                    advanced.chaff()>base.chaff()&&
                    advanced.flares()>base.flares()&&
                    advanced.cooldownTicks()<base.cooldownTicks(),
                    "Fighter models lack distinctive finite research-gated inventory");
        }
        var chaff=FighterCountermeasures.release(
                19,FighterCountermeasures.Kind.CHAFF,3,7,0,
                100,200,70,0,300,200,12,3);
        check(chaff.signal(FighterCountermeasures.Kind.CHAFF,12)>5&&
                chaff.signal(FighterCountermeasures.Kind.FLARE,12)==0,
                "Chaff incorrectly impersonates a thermal seeker flare");
        check(chaff.spoofsRadar(3,100,200,.4,12)&&
                !chaff.spoofsRadar(4,100,200,.4,12),
                "Chaff target association invented or swapped enemy fighter identity");
        check(chaff.signal(FighterCountermeasures.Kind.CHAFF,45)<
                chaff.signal(FighterCountermeasures.Kind.CHAFF,12)&&
                !chaff.active(90),"Chaff must age and disperse physically");
        var flare=FighterCountermeasures.release(
                20,FighterCountermeasures.Kind.FLARE,3,7,0,
                100,200,70,0,300,200,12,3);
        check(flare.signal(FighterCountermeasures.Kind.FLARE,12)>5&&
                flare.signal(FighterCountermeasures.Kind.CHAFF,12)==0&&
                !flare.spoofsRadar(3,100,200,.4,12),
                "IR flare falsely spoofed fitted radar countermeasures");
    }
    private static Battle fixture(TechnologyCatalog tech,long seed,boolean disabled){
        var hull=Shipyard.Hull.CRUISER;
        var carrier=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(socket(hull,Shipyard.Mount.SUPPORT),"BASE-TER-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var flakHull=Shipyard.Hull.DREADNOUGHT;
        var flak=Shipyard.bareHull(Battle.Faction.DRAXIAN,flakHull)
                .install(socket(flakHull,Shipyard.Mount.WEAPON),"BASE-DRA-FLK");
        check(Shipyard.inspect(carrier,tech).valid()&&
                Shipyard.inspect(flak,tech).valid(),
                "Fighter and flak must be physically legal installed hardware");
        Battle battle=new Battle(seed);
        battle.spawnWing(0,carrier,tech,1,300,350);
        battle.spawnWing(1,flak,tech,1,510,350);
        Battle.Ship parent=battle.ships().get(0);
        Battle.Ship defender=battle.ships().get(1);
        parent.fighterReserve=1;parent.fighterReload=0;
        defender.fighterReserve=defender.droneReserve=0;
        defender.kineticAmmo=240;
        defender.power=defender.maxPower=1500;
        defender.powerRegen=100;
        defender.orders=defender.orders.withPointDefense(
                BattleOrders.PointDefense.ANTI_CRAFT);
        for(Battle.Ship ship:battle.ships()){
            ship.maxHull=ship.hull=30000;
            ship.speed=0;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
        }
        for(int step=0;step<12&&battle.crafts().isEmpty();step++)battle.step();
        check(!battle.crafts().isEmpty(),"Paid carrier never launched a real craft");
        if(disabled)for(Battle.Craft craft:battle.crafts())
            if(craft.fighter)craft.chaffRemaining=0;
        return battle;
    }
    private static void actualRadarDiversion(TechnologyCatalog tech){
        Battle defended=fixture(tech,810410L,false);
        Battle unprotected=fixture(tech,810410L,true);
        Battle.Craft aircraft=defended.crafts().get(0);
        int initial=aircraft.chaffRemaining;
        check(initial>0&&aircraft.flareRemaining>0,
                "True launched fighter has no finite countermeasure cartridges");
        boolean observed=false;
        for(int i=0;i<110;i++){
            defended.step();
            unprotected.step();
            for(var cloud:defended.fighterCountermeasures()){
                observed|=cloud.kind()==FighterCountermeasures.Kind.CHAFF;
                check(cloud.craftId()>0&&cloud.bornTick()<=defended.tick&&
                        Double.isFinite(cloud.xAt(defended.tick)),
                        "Real chaff cloud lacks owner or physical trajectory");
            }
        }
        check(observed&&defended.fighterChaffDeployed>0&&
                aircraft.chaffRemaining<initial,
                "Fighter never deployed paid radar chaff under real flak threat");
        check(defended.fighterChaffFlakDiversions>0,
                "Actual flak failed to aim any physically fired shell at chaff");
        check(unprotected.fighterChaffDeployed==0&&
                unprotected.fighterChaffFlakDiversions==0,
                "Empty decoy magazine fabricated physical radar clouds");
        check(defended.fighterFlaresDeployed==0,
                "Radar flak incorrectly triggered infrared flare expenditure");
        check(defended.visualEvents().stream().anyMatch(event->
                event.weapon().equals("FLAK")&&
                        event.phase()==Battle.VisualPhase.FIRE),
                "The tested countermeasure never faced a real fitted flak shell");

        Battle manual=fixture(tech,810411L,false);
        Battle.Craft craft=manual.crafts().get(0);
        check(manual.deployFighterCountermeasure(
                        craft.id,FighterCountermeasures.Kind.FLARE),
                "Fighter cannot launch finite infrared cartridge on command");
        check(manual.fighterFlaresDeployed==1&&
                manual.fighterCountermeasures().stream().anyMatch(cloud->
                cloud.kind()==FighterCountermeasures.Kind.FLARE&&
                cloud.signal(FighterCountermeasures.Kind.CHAFF,manual.tick)==0),
                "Flares must be separate IR sources, not free radar echoes");
        check(!manual.deployFighterCountermeasure(
                        craft.id,FighterCountermeasures.Kind.FLARE),
                "One fighter bypassed the finite dispenser cooldown");
        System.out.println("PASS physical chaff diverts real flak, flare spectra stay distinct, finite magazines/cooldowns");
    }
    public static void main(String[] args)throws Exception{
        spectralHardware();
        var tech=TechnologyCatalog.loadDefault();
        actualRadarDiversion(tech);
        Battle a=fixture(tech,810412L,false);
        Battle b=fixture(tech,810412L,false);
        for(int tick=0;tick<95;tick++){
            a.step();b.step();
            check(a.fingerprint()==b.fingerprint(),
                    "Finite countermeasures and radar fire are not deterministic");
        }
        System.out.println("PASS seeded independently replayed fighter/chaff/flak combat");
    }
}
