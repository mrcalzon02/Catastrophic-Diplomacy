package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Passive IR physics and genuine fighter-to-fighter interception regression. */
public final class FighterInfraredSeekerTest {
    private FighterInfraredSeekerTest(){}
    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount mount){
        for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==mount)return slot.index();
        throw new AssertionError("Physical mount missing");
    }
    private static void causalPhotonAndSpectralChoice(){
        Battle fixture=new Battle(815021);
        Battle.Ship parent=fixture.addShip(0,Battle.Faction.TERRAN,200,200);
        parent.fighterProfile=FighterModel.standard(FighterModel.Hull.INTERCEPTOR);
        Battle.Craft fighter=new Battle.Craft(80,parent,true,0);
        fighter.x=150;fighter.y=100;
        var history=fighter.infraredHistory;
        history.capture(0,fighter);
        history.capture(1,fighter);
        var fixed=FighterInfraredSeeker.Head.FIXED_FORWARD;
        var wide=FighterInfraredSeeker.Head.WIDE_ANGLE;
        check(FighterInfraredSeeker.installed(0)==fixed&&
                FighterInfraredSeeker.installed(9)==wide&&
                FighterInfraredSeeker.interceptorMagazine(
                        FighterModel.Hull.INTERCEPTOR,9)>
                FighterInfraredSeeker.interceptorMagazine(
                        FighterModel.Hull.INTERCEPTOR,0)&&
                FighterInfraredSeeker.interceptorMagazine(
                        FighterModel.Hull.TORPEDO_BOMBER,9)==0,
                "Research grants the wrong physical seeker or free bomber interceptors");
        check(FighterInfraredSeeker.receive(fixed,history,List.of(),
                fighter.id,1,0,100,0,100,250,0,0)==null,
                "Unemitted future fighter photons reached a missile at tick zero");
        var real=FighterInfraredSeeker.receive(fixed,history,List.of(),
                fighter.id,1,0,100,0,100,250,0,2);
        check(real!=null&&!real.decoy()&&real.emittedTick()==0&&
                real.receivedTick()==2&&real.x()==150,
                "Passive IR missile failed to wait for historical light-front arrival");
        var opposite=FighterInfraredSeeker.receive(fixed,history,List.of(),
                fighter.id,1,0,100,0,100,-250,0,2);
        check(opposite==null,
                "Fixed forward IR head acquired a fighter behind its physical boresight");
        var flare=new FighterCountermeasures.Cloud(
                219,FighterCountermeasures.Kind.FLARE,fighter.id,
                parent.id,parent.team,178,108,0,0,0,48,11);
        var chaff=new FighterCountermeasures.Cloud(
                220,FighterCountermeasures.Kind.CHAFF,fighter.id,
                parent.id,parent.team,178,108,0,0,0,66,50);
        var fooled=FighterInfraredSeeker.receive(fixed,history,
                List.of(flare,chaff),fighter.id,1,
                0,100,0,100,250,0,2);
        check(fooled!=null&&fooled.decoy()&&fooled.flareId()==flare.id(),
                "Physical emitted flare photon front failed to beat fighter heat");
        var wideReturn=FighterInfraredSeeker.receive(wide,history,
                List.of(flare,chaff),fighter.id,1,
                0,100,0,100,250,0,2);
        check(wideReturn!=null&&!wideReturn.decoy()&&wideReturn.x()==150,
                "Short-range wide-angle IR head incorrectly saw distant flare");
        check(FighterInfraredSeeker.receive(fixed,history,List.of(chaff),
                fighter.id,1,0,100,0,100,250,0,2).flareId()==0,
                "Radar chaff emitted fictitious infrared photons");
        check(FighterInfraredSeeker.receive(fixed,history,List.of(flare),
                fighter.id,1,0,100,0,100,250,0,90)==null,
                "Already-passed emissions reappeared as current target telemetry");
    }
    private static Battle realFighterCombat(TechnologyCatalog tech,long seed,
                                           boolean suppressFlares){
        Shipyard.Hull hull=Shipyard.Hull.CRUISER;
        var left=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(socket(hull,Shipyard.Mount.SUPPORT),"BASE-TER-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var right=Shipyard.starter(Battle.Faction.DRAXIAN,hull)
                .install(socket(hull,Shipyard.Mount.SUPPORT),"BASE-DRA-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        check(Shipyard.inspect(left,tech).valid()&&
                Shipyard.inspect(right,tech).valid(),
                "No physically fitted opposing interceptor catapults");
        Battle battle=new Battle(seed);
        battle.spawnWing(0,left,tech,1,290,350);
        battle.spawnWing(1,right,tech,1,480,350);
        for(Battle.Ship ship:battle.ships()){
            ship.speed=0;
            ship.fighterReserve=1;
            ship.fighterReload=0;
            ship.droneReserve=ship.repairDroneReserve=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.maxHull=ship.hull=45000;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.missileDamage=ship.torpedoDamage=ship.kineticDamage=0;
            ship.pdRange=0;
        }
        // Move both actual aircraft into the same combat envelope using
        // real catapult launches, NOT test-created phantom craft.
        for(int i=0;i<6&&battle.crafts().size()<2;i++)battle.step();
        check(battle.crafts().size()==2,"Both real fitted carrier lanes must launch");
        if(suppressFlares)for(Battle.Craft c:battle.crafts())
            c.flareRemaining=0;
        return battle;
    }
    private static void liveInterception(TechnologyCatalog tech){
        Battle battle=realFighterCombat(tech,815022L,false);
        Battle noFlares=realFighterCombat(tech,815022L,true);
        boolean missileSeen=false;
        boolean receivedDelayedPhotons=false;
        for(int tick=0;tick<190;tick++){
            battle.step();
            noFlares.step();
            for(Battle.Missile m:battle.missiles())if(m.targetCraftId>0){
                missileSeen=true;
                check(m.fighterIrHead!=null&&m.targetId>0&&
                        m.targetCraftId>0,
                        "Air-to-air missile lacked physical target class or seeker package");
                // Craft and ship identities are independent integer namespaces:
                // the same number may name both, without type confusion.
                check(battle.crafts().stream().anyMatch(c->
                        c.id==m.targetCraftId&&c.fighter&&c.team!=m.team),
                        "Interceptor target ID does not resolve to hostile physical fighter");
                if(m.lastFighterConsumedTick>=0){
                    receivedDelayedPhotons=true;
                    check(m.lastObservationTick<=battle.tick&&
                            m.lastFighterConsumedTick<=battle.tick,
                            "IR guided aircraft missile consulted future target history");
                }
            }
        }
        check(missileSeen&&battle.fighterInterceptorsLaunched>0,
                "Real interceptors did not launch finite fighter-targeting missiles");
        check(noFlares.fighterInterceptorsLaunched>0&&
                noFlares.fighterFlaresDeployed==0,
                "Unprotected fighter fabricated infrared defensive cartridges");
        check(receivedDelayedPhotons,
                "Physical fighter missile failed to consume any delayed infrared photons");
        check(battle.fighterFlaresDeployed>0,
                "IR-guided fighter missile failed to trigger physical defensive flares");
        check(battle.fighterFlareSeekerDiversions>0,
                "Flare photons never actually deceived any onboard missile seeker");
        System.out.println("PASS actual fitted fighter interceptors, passive photon tracks, finite flare competition");
        Battle a=realFighterCombat(tech,815023L,false);
        Battle b=realFighterCombat(tech,815023L,false);
        for(int tick=0;tick<110;tick++){
            a.step();b.step();
            check(a.fingerprint()==b.fingerprint(),
                    "Autonomous IR-guided fighter missile or flare replay diverged");
        }
        System.out.println("PASS deterministic live infrared fighter missiles and flares");
    }
    public static void main(String[] args)throws Exception{
        causalPhotonAndSpectralChoice();
        var tech=TechnologyCatalog.loadDefault();
        liveInterception(tech);
    }
}
