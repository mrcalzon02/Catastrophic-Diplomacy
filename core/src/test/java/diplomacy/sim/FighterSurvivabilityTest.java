package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * Objective real-Craft survivability regression: no segmented fighter armor
 * or directional shields, no free technology, finite shields and genuine
 * per-seat escape rolls applied only once at aircraft destruction.
 */
public final class FighterSurvivabilityTest {
    private FighterSurvivabilityTest(){}
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static void reject(Runnable attempt,String message){
        try{attempt.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(message);
    }
    private static void fittingGrades(){
        for(FighterModel.Hull hull:FighterModel.Hull.values()){
            FighterModel.Profile profile=FighterModel.standard(hull);
            var stock=FighterSurvivability.installed(profile,0);
            var middle=FighterSurvivability.installed(profile,5);
            var elite=FighterSurvivability.installed(profile,9);
            check(stock.crew()>=1&&stock.crew()<=3,
                    "No pilot seats for "+hull);
            check(stock.ejectionChance()>0&&
                    stock.ejectionChance()<middle.ejectionChance()&&
                    middle.ejectionChance()<elite.ejectionChance()&&
                    elite.ejectionChance()<1,
                    "Pilot escape chance does not increase with FTR research: "+hull);
            check(stock.shieldCapacity()==0&&middle.shieldCapacity()==0,
                    "Unresearched fighter received shield hardware: "+hull);
            check(FighterSurvivability.installed(profile,6).shieldCapacity()>0&&
                    elite.shieldCapacity()>
                    FighterSurvivability.installed(profile,6).shieldCapacity()&&
                    elite.shieldRechargePerSecond()>0,
                    "Top research did not fit its whole-fighter shield capacitor: "+hull);
            for(int seat=0;seat<stock.crew();seat++){
                int low=0,high=0;
                for(int id=1;id<=480;id++){
                    boolean l=FighterSurvivability.survived(123456,id,seat,
                            stock.ejectionChance());
                    boolean h=FighterSurvivability.survived(123456,id,seat,
                            elite.ejectionChance());
                    check(!l||h,"Improving an ejection seat killed an otherwise surviving pilot");
                    if(l)low++;
                    if(h)high++;
                    check(h==FighterSurvivability.survived(123456,id,seat,
                            elite.ejectionChance()),
                            "Pilot survival lottery is not reproducible");
                }
                check(low>0&&high>low&&high<480,
                        "Pilot protection isn't probabilistic at "+hull+
                        " stock="+low+" elite="+high);
            }
        }
        reject(()->FighterSurvivability.installed(
                FighterModel.standard(FighterModel.Hull.INTERCEPTOR),10),
                "Unlicensed FTR rank ten accepted");
        reject(()->FighterSurvivability.survived(12,5,0,1),
                "Pilot ejection was made an automatic guaranteed outcome");
        System.out.println("PASS all six fighter hulls, paid rank progression and bounded independent pilot ejection probabilities");
    }
    private static Battle.Craft craft(Battle battle,int id,int tier,
                                      FighterModel.Hull hull){
        var carrier=battle.addShip(0,Battle.Faction.TERRAN,280,350);
        carrier.fighterProfile=FighterModel.standard(hull);
        carrier.fighterCountermeasureTier=tier;
        return new Battle.Craft(id,carrier,true,0);
    }
    private static void scalarCellsAndRecharge(){
        Battle battle=new Battle(908810);
        var fighter=craft(battle,33,9,FighterModel.Hull.TORPEDO_BOMBER);
        var shooter=battle.ships().get(0);
        double shields=fighter.shields;
        double armor=fighter.armor;
        double hull=fighter.integrity;
        check(shields>0&&shields==fighter.maxShields,
                "Top-rank aircraft did not spawn with its single capacitor");
        battle.hitCraft(fighter,shields*.70,shooter,1,"KINETIC");
        check(StrictMath.abs(fighter.shields-shields*.30)<1e-7&&
                fighter.armor==armor&&fighter.integrity==hull,
                "A shielded fighter took armor or hull damage before its shield was depleted");
        // No facings or per-pixel masks: attack direction/position has no
        // influence on the ONE shield/armor budget.
        fighter.x=995;fighter.y=410;
        battle.hitCraft(fighter,shields*.50,shooter,2,"PLASMA");
        check(fighter.shields==0&&fighter.armor<armor&&fighter.integrity<hull,
                "Shield overflow failed to reach the global armor cell");
        double armorAfter=fighter.armor,shieldsAfter=fighter.shields;
        FighterSurvivability.recharge(fighter,
                FighterSurvivability.SHIELD_RECHARGE_DELAY_TICKS-1,Battle.DT);
        check(fighter.shields==shieldsAfter&&fighter.armor==armorAfter,
                "Whole-craft shield recharged during hit cooldown");
        FighterSurvivability.recharge(fighter,
                FighterSurvivability.SHIELD_RECHARGE_DELAY_TICKS,Battle.DT);
        check(fighter.shields>0&&fighter.shields<=fighter.maxShields&&
                fighter.armor==armorAfter,
                "Shield recharge created armor or failed to restart");
        for(int i=61;i<10000;i++)
            FighterSurvivability.recharge(fighter,i,Battle.DT);
        check(fighter.shields==fighter.maxShields,
                "Fighter capacitor overcharged or never reached its physical limit");
        var basic=craft(new Battle(908811),22,0,FighterModel.Hull.INTERCEPTOR);
        check(basic.shields==0&&basic.maxShields==0,
                "Basic fighter falsely received elite research hardware");
        var unshielded=FighterSurvivability.absorb(basic,5,1);
        check(unshielded.shieldAbsorbed()==0&&
                unshielded.armorAbsorbed()>0&&
                unshielded.hullDamage()>0,
                "Stock fighter lost its ordinary one-cell armor");
        reject(()->FighterSurvivability.absorb(basic,-4,1),
                "Negative aircraft damage was accepted");
        System.out.println("PASS actual single shield then single armor cell, hit cooldown, finite recharge and stock fallback");
    }
    private static void ejectionAndReplay(){
        final long seed=908812;
        Battle first=new Battle(seed),copy=new Battle(seed);
        Battle.Craft a=craft(first,87,9,FighterModel.Hull.TORPEDO_BOMBER);
        Battle.Craft b=craft(copy,87,9,FighterModel.Hull.TORPEDO_BOMBER);
        var shooterA=first.ships().get(0);
        var shooterB=copy.ships().get(0);
        check(a.crewSeats==3&&a.pilotEjectionChance>.60,
                "Elite torpedo bomber was assigned a single pilot/poor ejection seats");
        first.hitCraft(a,10000,shooterA,17,"RAIL_PD");
        copy.hitCraft(b,10000,shooterB,17,"RAIL_PD");
        check(a.integrity<=0&&a.pilotEjectionResolved,
                "Fatal fighter hit failed to resolve ejection system");
        int survivors=0;
        for(int seat=0;seat<a.crewSeats;seat++)
            if(FighterSurvivability.survived(seed,a.id,seat,
                    a.pilotEjectionChance))survivors++;
        check(first.fighterPilotsEjected==survivors&&
                first.fighterPilotsLost==a.crewSeats-survivors&&
                first.fighterEscapeRecords().size()==survivors,
                "Lost fighter did not individually resolve all pilot seats");
        for(var survivor:first.fighterEscapeRecords())
            check(survivor.craftId()==a.id&&
                    survivor.carrierId()==a.sourceId&&
                    survivor.team==a.team&&survivor.tick()==first.tick,
                    "A survivor was assigned to the wrong aircraft or future tick");
        first.hitCraft(a,10000,shooterA,18,"FLAK");
        check(first.fighterPilotsEjected==survivors&&
                first.fighterPilotsLost==a.crewSeats-survivors,
                "Multiple weapons or immediate removal rolled ejection twice");
        check(first.fingerprint()==copy.fingerprint(),
                "Double processing a destroyed fighter altered replay state");
        System.out.println("PASS real fatal-hit ejection across three seats, exact-once survivor bookkeeping and deterministic replay");
    }
    private static void realResearchInheritance()throws Exception{
        var tech=TechnologyCatalog.loadDefault();
        var starter=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        int support=-1;
        for(var slot:starter.placements())
            if(slot.mount()==Shipyard.Mount.SUPPORT){support=slot.index();break;}
        check(support>=0,"No paid fighter module socket");
        var premium=starter.install(support,"BASE-TER-FTR")
                .stage(support,Shipyard.Track.SPECIALIST,9)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        check(Shipyard.inspect(premium,tech).valid(),
                "Legitimate tier-nine FTR loadout was not deployable");
        Battle battle=new Battle(908813);
        battle.spawnWing(0,Shipyard.decode(Shipyard.encode(premium),tech),
                tech,1,290,350);
        var carrier=battle.ships().get(0);
        check(carrier.fighterCountermeasureTier==9,
                "FTR research was discarded during battle construction");
        var fighter=new Battle.Craft(198,carrier,true,0);
        var fitting=FighterSurvivability.installed(fighter.profile,9);
        check(fighter.maxShields==fitting.shieldCapacity()&&
                fighter.shields==fitting.shieldCapacity()&&
                fighter.pilotEjectionChance==fitting.ejectionChance()&&
                fighter.crewSeats==fitting.crew(),
                "Actual paid carrier failed to transfer crew protection and one-cell shielding");
        System.out.println("PASS fitted FTR rank 9 inherited by physically constructed fighter");
    }
    public static void main(String[] ignored)throws Exception{
        fittingGrades();
        scalarCellsAndRecharge();
        ejectionAndReplay();
        realResearchInheritance();
    }
}
