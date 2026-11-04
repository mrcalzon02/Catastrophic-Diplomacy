package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Physical capsule recovery and campaign personnel conservation, headless. */
public final class FighterEscapePodTest {
    private FighterEscapePodTest(){}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable fn,String why){
        try{fn.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    private static void worldlineAndCapacity(){
        Battle scene=new Battle(88900);
        Battle.Ship friendly=scene.addShip(0,Battle.Faction.TERRAN,400,350);
        Battle.Ship hostile=scene.addShip(1,Battle.Faction.DRAXIAN,400,350);
        double full=StrictMath.min(friendly.crewBerths,friendly.lifeSupport);
        friendly.crewCount=full;
        FighterEscapePod pod=new FighterEscapePod(9,0,friendly.id,0,
                0,400,350,20,0,true);
        check(!pod.inReach(friendly),"Full ship magically accepted an extra pilot");
        check(!pod.inReach(hostile),"Opposing ship captured a friendly pilot");
        reject(()->pod.collect(friendly),"Capsule boarded a full ship");
        friendly.crewCount=full-1;
        check(pod.inReach(friendly),"Near friendly hull did not detect a real capsule");
        pod.advance(30);
        check(StrictMath.abs(pod.x-403)<1e-9&&pod.y==350,
                "Ejection capsule crossed space with engine-like acceleration");
        pod.collect(friendly);
        check(pod.recovered()&&!pod.active()&&friendly.crewCount==full,
                "Actual finite available crew berth was not filled by rescue");
        reject(()->pod.collect(friendly),"Same pilot was rescued twice");
        FighterEscapePod expired=new FighterEscapePod(10,0,friendly.id,0,
                0,400,350,0,0,true);
        expired.advance(FighterEscapePod.LIFETIME_TICKS);
        check(expired.expired()&&!expired.active()&&
                !expired.inReach(friendly),
                "Unrecovered capsules remain immortal or auto-return to the carrier");
        FighterEscapePod far=new FighterEscapePod(11,0,friendly.id,0,
                0,400,350,0,0,true);
        friendly.crewCount=full-1;
        friendly.x=400+FighterEscapePod.PICKUP_RANGE+.001;
        check(!far.inReach(friendly),"Remote fighter rescue exceeded 28-unit range");
        reject(()->far.advance(-1),"Escape capsule accepted a reversed clock");
        System.out.println("PASS physical drift, exact pickup range, enemy isolation, crew capacity and capsule expiry");
    }
    private static int support(){
        for(var slot:Shipyard.Hull.CRUISER.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT)return slot.index();
        throw new AssertionError("No installed FTR socket");
    }
    private record Sortie(Battle battle,CampaignFleet.Roster owned,long id,
                          Battle.Ship parent,Battle.Craft pilot){}
    private static Sortie launch(TechnologyCatalog tech,long seed){
        var design=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER)
                .install(support(),"BASE-TER-FTR")
                .stage(support(),Shipyard.Track.SPECIALIST,9)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var owned=CampaignFleet.startSolo(
                Battle.Faction.TERRAN,design,tech,911);
        long id=owned.active().get(0).id();
        Battle scene=new Battle(seed);
        scene.spawnWing(0,design,tech,1,300,350);
        scene.spawnWing(1,Shipyard.starter(
                Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER),
                tech,1,540,350);
        Battle.Ship carrier=scene.ships().get(0);
        carrier.commissionedId=id;
        carrier.fighterReserve=1;
        for(var ship:scene.ships()){
            ship.speed=0;ship.maxHull=ship.hull=30000;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.pdRange=0;ship.missileAmmo=ship.torpedoAmmo=0;
            ship.droneReserve=ship.repairDroneReserve=0;
        }
        for(int i=0;i<140;i++){
            scene.step();
            for(Battle.Craft c:scene.crafts())
                if(c.fighter&&c.sourceId==carrier.id)
                    return new Sortie(scene,owned,id,carrier,c);
        }
        throw new AssertionError("A real paid carrier never launched its crewed fighter");
    }
    private static void returnedAircraft(TechnologyCatalog tech){
        Sortie sortie=launch(tech,88901);
        check(sortie.pilot.crewEmbarked&&sortie.pilot.crewSeats==1&&
                sortie.parent.fighterPilotsCommitted==1,
                "Real fighter launch failed to debit exactly one crew member");
        double original=sortie.owned.get(sortie.id).stores().crew();
        check(sortie.parent.crewCount==original-1,
                "A paid fighter was staffed by crew invented outside the roster");
        var ids=List.of(sortie.id);
        var report=CampaignAfterAction.capture(sortie.owned,ids,sortie.battle);
        check(report.pilots().deployed()==1&&report.pilots().airborne()==1&&
                report.pilots().recovered()==0&&report.pilots().missing()==0,
                "Surviving aircraft's crew was misreported as a dead pilot");
        var restored=sortie.owned.afterBattle(sortie.battle,ids);
        check(restored.get(sortie.id).stores().crew()==original&&
                report.entries().get(0).crewLost()==0,
                "Returning living fighter did not restore its actually debited pilot");
        // Reconciliation is immutable and cannot mint personnel on re-report.
        check(sortie.owned.afterBattle(sortie.battle,ids)
                        .get(sortie.id).stores().crew()==original,
                "Repeated settlement minted duplicate flight crew");
        System.out.println("PASS real launched pilot debit, intact fighter retrieval and idempotent campaign roster");
    }
    private static void recoveredEjection(TechnologyCatalog tech){
        int id=1;
        double survival=FighterSurvivability.installed(
                FighterModel.standard(FighterModel.Hull.INTERCEPTOR),9)
                .ejectionChance();
        long seed=88902;
        while(!FighterSurvivability.survived(seed,id,0,survival))seed++;
        Sortie sortie=launch(tech,seed);
        Battle battle=sortie.battle;
        var craft=sortie.pilot;
        check(craft.id==1&&
                FighterSurvivability.survived(seed,craft.id,0,
                        craft.pilotEjectionChance),
                "Survivor fixture did not select a real successful ejection");
        double original=sortie.owned.get(sortie.id).stores().crew();
        craft.x=sortie.parent.x+5;craft.y=sortie.parent.y;
        craft.observedVx=craft.observedVy=0;
        battle.hitCraft(craft,10000,sortie.parent,123456,"RAIL_PD");
        check(battle.fighterEscapePods().size()==1&&
                battle.fighterPilotsEjected==1&&craft.integrity<=0,
                "Fatal aircraft failed to spawn a physical escaped pilot");
        // No same-tick teleport directly into a carrier's personnel account.
        check(sortie.parent.crewCount==original-1,
                "Ejection alone magically credited a carrier's crew");
        for(int i=0;i<10;i++)battle.step();
        check(battle.fighterPilotsRecovered==1&&
                battle.fighterEscapePods().isEmpty()&&
                battle.fighterRecoveryRecords().size()==1,
                "Nearby friendly ship did not physically recover a real pod");
        var rescue=battle.fighterRecoveryRecords().get(0);
        check(rescue.rescuerShipId()==sortie.parent.id&&
                rescue.rosterCrew()&&rescue.originalCarrierId()==sortie.parent.id,
                "Recovered personnel lost source ownership or crew provenance");
        check(sortie.parent.crewCount==original,
                "Recovered pilot was not restored to the actual carrier crew");
        var report=CampaignAfterAction.capture(
                sortie.owned,List.of(sortie.id),battle);
        check(report.pilots().deployed()==1&&report.pilots().recovered()==1&&
                report.pilots().missing()==0&&report.pilots().adrift()==0,
                "After-action report counted rescued pilot as missing or duplicated");
        var result=sortie.owned.afterBattle(battle,List.of(sortie.id));
        check(result.get(sortie.id).stores().crew()==original&&
                report.entries().get(0).crewLost()==0,
                "Campaign persistence failed to keep a physically recovered pilot");
        check(battle.fighterPilotsRecovered==1,
                "Recovered pilot was duplicated by continuing simulation");
        System.out.println("PASS real ejection capsule, finite friendly pickup, after-action crew settlement");
    }
    private static void unrescuedDoesNotCreateCrew(TechnologyCatalog tech){
        long seed=88991;
        double chance=FighterSurvivability.installed(
                FighterModel.standard(FighterModel.Hull.INTERCEPTOR),9)
                .ejectionChance();
        while(!FighterSurvivability.survived(seed,1,0,chance))seed++;
        Sortie sortie=launch(tech,seed);
        double original=sortie.owned.get(sortie.id).stores().crew();
        sortie.pilot.x=1200;sortie.pilot.y=1000;
        sortie.pilot.observedVx=sortie.pilot.observedVy=0;
        sortie.battle.hitCraft(sortie.pilot,10000,sortie.parent,3,"FLAK");
        check(sortie.battle.fighterEscapePods().size()==1,
                "Unrescued test did not produce a genuine capsule");
        var report=CampaignAfterAction.capture(
                sortie.owned,List.of(sortie.id),sortie.battle);
        check(report.pilots().adrift()==1&&report.pilots().missing()==0,
                "Uncollected pilot was falsely pronounced recovered or dead");
        var restored=sortie.owned.afterBattle(sortie.battle,List.of(sortie.id));
        check(restored.get(sortie.id).stores().crew()==original-1&&
                report.entries().get(0).crewLost()==1,
                "Uncollected escape pod was credited to persistent crew stores");
        System.out.println("PASS distant ejection remains adrift and cannot mint campaign personnel");
    }
    public static void main(String[] args)throws Exception{
        worldlineAndCapacity();
        var tech=TechnologyCatalog.loadDefault();
        returnedAircraft(tech);
        recoveredEjection(tech);
        unrescuedDoesNotCreateCrew(tech);
    }
}
