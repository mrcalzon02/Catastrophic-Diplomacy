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
        return fixture(tech,seed,disabled,"FLK");
    }
    private static Battle fixture(TechnologyCatalog tech,long seed,
                                  boolean disabled,String defenseKind){
        return fixture(tech,seed,disabled,defenseKind,true);
    }
    private static Battle fixture(TechnologyCatalog tech,long seed,
                                  boolean disabled,String defenseKind,
                                  boolean indexedOpponents){
        return fixture(tech,seed,disabled,defenseKind,indexedOpponents,true);
    }
    private static Battle fixture(TechnologyCatalog tech,long seed,
                                  boolean disabled,String defenseKind,
                                  boolean indexedOpponents,boolean indexedRadarDefenders){
        return fixture(tech,seed,disabled,defenseKind,
                indexedOpponents,indexedRadarDefenders,true);
    }
    private static Battle fixture(TechnologyCatalog tech,long seed,
                                  boolean disabled,String defenseKind,
                                  boolean indexedOpponents,boolean indexedRadarDefenders,
                                  boolean boundedFlakRange){
        if(!defenseKind.equals("FLK")&&!defenseKind.equals("RCW"))
            throw new IllegalArgumentException("Only real fitted radar guns");
        var hull=Shipyard.Hull.CRUISER;
        var carrier=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(socket(hull,Shipyard.Mount.SUPPORT),"BASE-TER-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var flakHull=Shipyard.Hull.DREADNOUGHT;
        var flak=Shipyard.bareHull(Battle.Faction.DRAXIAN,flakHull)
                .install(socket(flakHull,Shipyard.Mount.WEAPON),
                        "BASE-DRA-"+defenseKind);
        check(Shipyard.inspect(carrier,tech).valid()&&
                Shipyard.inspect(flak,tech).valid(),
                "Fighter and flak must be physically legal installed hardware");
        Battle battle=new Battle(seed);
        battle.configureOpponentIndexForDiagnostic(indexedOpponents);
        battle.configureRadarDefenderIndexForDiagnostic(indexedRadarDefenders);
        battle.configureFlakRangeBroadphaseForDiagnostic(boundedFlakRange);
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

        Battle rotary=fixture(tech,810414L,false,"RCW");
        Battle withoutChaff=fixture(tech,810414L,true,"RCW");
        for(int step=0;step<180;step++){
            rotary.step();
            withoutChaff.step();
        }
        check(rotary.fighterChaffDeployed>0&&
                rotary.fighterChaffRotaryDiversions>0,
                "Fitted rotary radar did not fire actual rounds on chaff instead of a fighter");
        check(withoutChaff.fighterChaffDeployed==0&&
                withoutChaff.fighterChaffRotaryDiversions==0,
                "Rotary sight fabricated false radar returns without cartridges");
        check(rotary.visualEvents().stream().anyMatch(event->
                        event.weapon().equals("ROTARY_CIWS")&&
                        event.phase()==Battle.VisualPhase.FIRE),
                "Rotary chaff diversion test did not physically discharge CIWS");
        System.out.println("PASS independent real flak and rotary CHAFF diversions, flare spectra, finite ammo/cooldowns");
    }
    /**
     * Hostile-only chaff radar scans must have byte-identical effects to
     * full-ship traversal. FLK and RCW each provoke real fighter dispensers,
     * so a quiet zero-activity parity check cannot masquerade as acceptance.
     */
    private static void opponentIndexRadarParity(TechnologyCatalog tech){
        for(String kind:new String[]{"FLK","RCW"}){
            long seed=kind.equals("FLK")?810420L:810421L;
            Battle indexed=fixture(tech,seed,false,kind,true,true);
            Battle legacy=fixture(tech,seed,false,kind,false,false);
            for(int t=0;t<190;t++){
                indexed.step();legacy.step();
                check(indexed.fingerprint()==legacy.fingerprint(),
                        "Hostile-only radar/chaff lookup changed real combat "+
                        "at tick "+indexed.tick+" defense="+kind);
            }
            check(indexed.fighterChaffDeployed>0 &&
                    indexed.fighterChaffDeployed==legacy.fighterChaffDeployed,
                    "Actual "+kind+" radar did not exercise finite chaff parity");
            var fast=indexed.radarDefenderMetrics();
            var full=legacy.radarDefenderMetrics();
            check(fast.qualifiedThreats()>0 &&
                    fast.qualifiedThreats()==full.qualifiedThreats(),
                    "Radar defense index lost real fitted FLK/RCW threats");
            // Counts are advisory and must not become a release score gate.
            System.out.println("MEASURE "+kind+" fighter radar candidate visits "+
                    "indexed="+fast.visitedSlots()+
                    " fullFleet="+full.visitedSlots()+
                    " qualified="+fast.qualifiedThreats());
            boolean refused=false;
            try{indexed.configureOpponentIndexForDiagnostic(false);}
            catch(IllegalStateException expected){refused=true;}
            check(refused,"Opponent-index diagnostic accepted a midbattle toggle");
            refused=false;
            try{indexed.configureRadarDefenderIndexForDiagnostic(false);}
            catch(IllegalStateException expected){refused=true;}
            check(refused,"Radar-index diagnostic accepted a midbattle toggle");
        }
        System.out.println("PASS FLK/RCW chaff hostile-index full-scan parity: "+
                "2 fitted defenders, 190 ticks each, live finite countermeasures");
    }

    /** Fitted flak consumes real radar chaff while its conservative
     * range rejection removes only provably unreachable physical targets.
     * Compare the actual warhead/flak trajectory after every fixed tick. */
    private static void flakRangeLiveParity(TechnologyCatalog tech){
        Battle optimized=fixture(tech,810519L,false,"FLK",
                true,true,true);
        Battle original=fixture(tech,810519L,false,"FLK",
                true,true,false);
        check(optimized.fingerprint()==original.fingerprint(),
                "Flak broadphase changed launcher setup before trial");
        for(int frame=0;frame<190;frame++){
            optimized.step();original.step();
            check(optimized.fingerprint()==original.fingerprint(),
                    "Flak acquisition cull changed real fitted combat tick "+
                            optimized.tick);
        }
        var fast=optimized.flakRangeMetrics();
        var slow=original.flakRangeMetrics();
        check(fast.candidates()>0&&
                fast.candidates()==slow.candidates()&&
                fast.exactDistanceChecks()+fast.excluded()==fast.candidates()&&
                slow.excluded()==0&&slow.exactDistanceChecks()==slow.candidates(),
                "Flak scanned different real candidate bodies");
        check(optimized.fighterChaffDeployed>0&&
                optimized.fighterChaffFlakDiversions>0&&
                optimized.fighterChaffDeployed==original.fighterChaffDeployed&&
                optimized.fighterChaffFlakDiversions==
                        original.fighterChaffFlakDiversions,
                "Actual fitted flak/chaff cartridge diversion was altered");
        System.out.println("PASS fitted flak range exclusion A/B "+
                "200 fixed-tick fighter combat, candidates="+fast.candidates()+
                " excluded="+fast.excluded()+
                " exact="+fast.exactDistanceChecks()+
                " chaff="+optimized.fighterChaffFlakDiversions+
                " fingerprint="+Long.toHexString(optimized.fingerprint())+
                " (work counters advisory only)");
    }

    public static void main(String[] args)throws Exception{
        spectralHardware();
        var tech=TechnologyCatalog.loadDefault();
        actualRadarDiversion(tech);
        opponentIndexRadarParity(tech);
        flakRangeLiveParity(tech);
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
