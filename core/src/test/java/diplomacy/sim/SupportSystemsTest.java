package diplomacy.sim;

import diplomacy.shipyard.ModuleBranchEffects;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Deterministic acceptance of real hull/shield service and specialist repair craft. */
public final class SupportSystemsTest {
    private SupportSystemsTest(){}
    private static void check(boolean good,String message){
        if(!good)throw new AssertionError(message);
    }
    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int ordinal){
        for(Shipyard.Slot s:hull.slots)if(s.mount()==mount&&ordinal--==0)return s.index();
        throw new IllegalArgumentException("Missing mount");
    }
    private static Battle makeCombat(boolean active,boolean specialistDrone){
        Battle b=new Battle(7783);
        Battle.Ship medic=b.addShip(0,Battle.Faction.TERRAN,260,325);
        Battle.Ship friend=b.addShip(0,Battle.Faction.TERRAN,300,325);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.CARNELIAN,1000,520);
        for(Battle.Ship s:b.ships()){
            s.speed=0;s.damageControl=0;
            s.laserDamage=s.kineticDamage=s.missileDamage=0;
            s.plasmaDamage=s.particleDamage=s.torpedoDamage=0;
            s.pdRange=0;s.fighterReserve=0;s.droneReserve=0;
        }
        if(active){
            medic.support=new Shipyard.Support(1.5,2.5,190,3.3,190,
                    specialistDrone,2.0,1.2,1.15,specialistDrone?1:0);
            if(specialistDrone)medic.repairDroneReserve=1;
        }
        medic.hull-=12;friend.hull-=35;
        // A physically depleted shield facing, not a fake HUD shield counter.
        friend.defense.impact(1,0,0.7,0,18,Battle.DamageType.LASER);
        friend.shields=friend.defense.totalShields();
        return b;
    }
    private static void checkBranches(){
        for(String kind:new String[]{"LAS","KIN","MSL","TOR","PAR","PLA",
                "PD","FLK","REP","XFR","SHD","DRN","SEN"}){
            for(Shipyard.Track track:Shipyard.Track.values()){
                ModuleBranchEffects.Tuning first=ModuleBranchEffects.tune(kind,track,1);
                ModuleBranchEffects.Tuning last=ModuleBranchEffects.tune(kind,track,6);
                check(!first.equals(ModuleBranchEffects.NEUTRAL),
                        "Specialization must create behavior for "+kind+"/"+track);
                check(!first.equals(last),"Six levels must increment "+kind+"/"+track);
            }
        }
        check(ModuleBranchEffects.tune("PD",Shipyard.Track.SPECIALIST,6).tracking()
                >ModuleBranchEffects.tune("PD",Shipyard.Track.SPECIALIST,1).tracking(),
                "PD branch needs incremental target tracking");
        check(ModuleBranchEffects.tune("LAS",Shipyard.Track.OUTPUT,6).damage()
                >ModuleBranchEffects.tune("LAS",Shipyard.Track.OUTPUT,1).damage(),
                "Lasers require gradual, not all-at-once, damage upgrades");
        check(ModuleBranchEffects.tune("REP",Shipyard.Track.SPECIALIST,6).remoteRepair()
                >ModuleBranchEffects.tune("REP",Shipyard.Track.SPECIALIST,1).remoteRepair(),
                "Repair specialists require six levels of remote repair");
        check(ModuleBranchEffects.tune("XFR",Shipyard.Track.SPECIALIST,6).shieldTransfer()
                >ModuleBranchEffects.tune("XFR",Shipyard.Track.SPECIALIST,1).shieldTransfer(),
                "Shield charger specialist ranks increase actual transfer");
    }
    private static void checkFitted()throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        int rep=slot(hull,Shipyard.Mount.SUPPORT,0);
        int drone=slot(hull,Shipyard.Mount.SUPPORT,1);
        int shield=slot(hull,Shipyard.Mount.DEFENSE,0);
        int relay=slot(hull,Shipyard.Mount.SUPPORT,2);
        Shipyard.Design base=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(rep,"BASE-TER-REP")
                .install(drone,"BASE-TER-DRN")
                // The full support fit has meaningful reactor/cooling costs.
                .install(slot(hull,Shipyard.Mount.SYSTEM,3),"BASE-TER-REA")
                .install(slot(hull,Shipyard.Mount.SYSTEM,4),"BASE-TER-RAD");
        // Ordinary shield generators protect their OWN vessels, never allies.
        Shipyard.Fitted withoutRelay=Shipyard.fit(base,tech);
        check(withoutRelay.shields()>0 && withoutRelay.support().shieldTransferRate()==0,
                "Basic SHD incorrectly grants an entire fleet free shield beams");
        check(Shipyard.fit(Shipyard.starter(Battle.Faction.TERRAN,hull),tech)
                        .support().shieldTransferRate()==0,
                "Starter ships must have no remote shield-transfer machinery");
        Shipyard.Design transfer=base.install(relay,"BASE-TER-XFR");
        if(!Shipyard.inspect(transfer,tech).valid())
            transfer=transfer.install(slot(hull,Shipyard.Mount.SYSTEM,5),"BASE-TER-REA");
        check(Shipyard.inspect(transfer,tech).valid(),
                "Dedicated physical shield-transfer fitting must be buildable");
        Shipyard.Fitted fitted=Shipyard.fit(transfer,tech);
        check(fitted.support().selfRepairRate()>0
                        &&fitted.support().remoteRepairRate()>0
                        &&fitted.support().shieldTransferRate()>0,
                "Fitted repair and shield modules must provide combat actions");
        check(!fitted.support().repairDrones(),
                "Default defense drones must NOT silently turn into repair drones");
        Shipyard.Design service=base.stage(drone,Shipyard.Track.SPECIALIST,3);
        check(Shipyard.fit(service,tech).support().repairDrones(),
                "DRN specialist converts specific controller's drones to repair craft");
        check(Shipyard.fit(service,tech).support().repairDroneRate()>0,
                "Repair drones must gain actual finite service capability");
        check(Shipyard.fit(service,tech).droneCapacity()==0
                        &&Shipyard.fit(service,tech).support().repairDroneCapacity()>0,
                "Specialist controller must count only its own service drones");
        int secondDrone=slot(hull,Shipyard.Mount.SUPPORT,2);
        Shipyard.Design mixed=service.install(secondDrone,"BASE-TER-DRN");
        check(Shipyard.fit(mixed,tech).droneCapacity()>0
                        &&Shipyard.fit(mixed,tech).support().repairDroneCapacity()>0,
                "Distinct controllers must carry independent regular and repair drone stores");
        Shipyard.Design remote=base.stage(rep,Shipyard.Track.SPECIALIST,4);
        check(Shipyard.fit(remote,tech).support().repairRange()>fitted.support().repairRange(),
                "Remote repair research must modify actual service reach");
        Shipyard.Design charge=transfer.stage(relay,Shipyard.Track.SPECIALIST,4);
        check(Shipyard.fit(charge,tech).support().shieldRange()>fitted.support().shieldRange(),
                "Dedicated projector research must affect real allied charging beam range");
        Shipyard.Design ownShield=base.stage(shield,Shipyard.Track.SPECIALIST,4);
        check(Shipyard.fit(ownShield,tech).support().shieldTransferRate()==0,
                "Even a specialized self-shield generator cannot conjure an external projector");
        Shipyard.Design laser=base.stage(slot(hull,Shipyard.Mount.WEAPON,0),
                Shipyard.Track.SPECIALIST,4);
        check(Shipyard.fit(laser,tech).weapons().laserRange()>fitted.weapons().laserRange(),
                "Six stage beam range research must feed actual combat limits");
        Shipyard.Design rapid=base.stage(slot(hull,Shipyard.Mount.WEAPON,0),
                Shipyard.Track.EFFICIENCY,4);
        check(Shipyard.fit(rapid,tech).weapons().laserCycle()<fitted.weapons().laserCycle(),
                "Efficient laser upgrades must affect actual reload time");
    }
    private static void checkCombat(){
        Battle b=makeCombat(true,false);
        Battle.Ship medic=b.ships().get(0),friend=b.ships().get(1);
        double hullStart=friend.hull,shieldStart=friend.shields,selfStart=medic.hull;
        for(int i=0;i<24;i++)b.step();
        check(medic.hull>selfStart,"powered self-repair must restore its own hull");
        check(friend.hull>hullStart,"remote beam must repair a damaged ally");
        check(friend.shields>shieldStart,"remote shield charging must restore depleted facings");
        check(medic.remoteHullRepaired>0&&medic.shieldsTransferred>0,
                "real support actions must be separately measurable");
        check(friend.hull<=friend.maxHull&&friend.shields<=friend.maxShields+1e-8,
                "support actions must never exceed hull or shield capacities");
        check(b.supportLinks().stream().allMatch(link->link.team==0
                        &&link.sourceId==medic.id&&link.targetId==friend.id),
                "support beam links must never connect to hostile/dead ships");

        Battle none=makeCombat(false,false);
        Battle.Ship unrepaired=none.ships().get(1);
        double originalHull=unrepaired.hull,originalShield=unrepaired.shields;
        for(int i=0;i<24;i++)none.step();
        check(unrepaired.hull==originalHull && unrepaired.shields==originalShield,
                "support without installed machinery must not conjure repairs");

        Battle repair=makeCombat(true,true);
        Battle.Ship controller=repair.ships().get(0);
        controller.support=new Shipyard.Support(0,0,200,0,200,true,2,1.2,1,1);
        for(int i=0;i<140;i++)repair.step();
        check(controller.repairDronesLaunched>0,"service drone controller must launch craft");
        check(controller.remoteHullRepaired>0,
                "repair drones must physically approach and repair allied hulls");
        check(repair.crafts().stream().noneMatch(c->c.repair&&c.repairReserve<0),
                "service drones cannot spend negative charge");
        check(repair.supportLinks().stream().allMatch(l->l.kind!=Battle.SupportKind.SHIELD),
                "a repair-only drone must not secretly refill shields");

        Battle copy=makeCombat(true,true);
        copy.ships().get(0).support=new Shipyard.Support(0,0,200,0,200,true,2,1.2,1,1);
        for(int i=0;i<140;i++)copy.step();
        check(repair.fingerprint()==copy.fingerprint(),
                "repeated seeded service-drone sorties must be deterministic");
    }
    public static void main(String[] args)throws Exception{
        checkBranches();checkFitted();checkCombat();
        System.out.println("PASS six-rank module doctrine + self repair + allied repair beams + shield transfers + finite service drones");
    }
}
