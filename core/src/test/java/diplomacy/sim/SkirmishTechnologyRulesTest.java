package diplomacy.sim;

import diplomacy.shipyard.*;
import diplomacy.tech.TechnologyCatalog;
import java.util.EnumSet;
import java.util.Set;

/** Skirmish restrictions cannot borrow campaign discoveries or be bypassed via import. */
public final class SkirmishTechnologyRulesTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void reject(Runnable runnable,String why){
        try{runnable.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception{
        var catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design ship=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int laser=-1;
        for(int slot=0;slot<ship.slots();slot++)if(ship.at(slot)!=null&&
                "BASE-TER-LAS".equals(ship.at(slot).component)){laser=slot;break;}
        check(laser>=0,"Prototype missing mounted test laser");
        final int selected=laser;
        var open=SkirmishTechnologyRules.unrestricted();
        var tierOne=open.withStage(1);
        check(tierOne.permits(ship,catalog,laser,Shipyard.Track.OUTPUT,1),
                "Skirmish tier one technology incorrectly unavailable");
        check(!tierOne.permits(ship,catalog,laser,Shipyard.Track.OUTPUT,2),
                "Skirmish cap one accepted rank two");
        var upgraded=ShipTechnologyFit.fit(ship,catalog,tierOne,laser,
                Shipyard.Track.OUTPUT,1,ShipTechnologyFit.Scope.SOCKET);
        tierOne.requireAllowed(upgraded,catalog);
        reject(()->tierOne.withStage(0).requireAllowed(upgraded,catalog),
                "Ship evaded strict zero-tech skirmish");
        reject(()->tierOne.toggleTrack(Shipyard.Track.OUTPUT).requireAllowed(upgraded,catalog),
                "Excluded branch was accepted");
        var blocked=tierOne.toggleTechnology("TECH-TER-LAS-OUTPUT-01");
        reject(()->blocked.requireAllowed(upgraded,catalog),"Individual technology ban bypassed");
        reject(()->ShipTechnologyFit.fit(ship,catalog,blocked,selected,
                Shipyard.Track.OUTPUT,1,ShipTechnologyFit.Scope.SOCKET),
                "Fitting engine bypassed skirmish tech ban");
        reject(()->tierOne.withHulls(Set.of(Shipyard.Hull.FRIGATE))
                .requireAllowed(ship,catalog),"Challenge hull whitelist ignored");
        reject(()->tierOne.withHullGeneration(1).requireAllowed(ship,catalog),
                "Ship generation cap ignored");
        var high=open.withStage(6);
        var independent=ShipTechnologyFit.fit(ship,catalog,high,laser,
                Shipyard.Track.OUTPUT,4,ShipTechnologyFit.Scope.SOCKET);
        reject(()->tierOne.requireAllowed(independent,catalog),
                "Over-tier exported ship entered weaker challenge");
        check(ship.at(laser).stage(Shipyard.Track.OUTPUT)==0,
                "Rule selection mutated source blueprint");
        var rules=new SkirmishTechnologyRules(1,10,EnumSet.allOf(Shipyard.Track.class),
                Set.of("TECH-TER-LAS-SPECIALIST-01"),Set.of(Shipyard.Hull.CORVETTE));
        check(SkirmishTechnologyRules.decode(SkirmishTechnologyRules.encode(rules)).equals(rules),
                "Skirmish challenge rules did not round-trip");
        var exchanged=SkirmishShipExchange.encode(upgraded,rules,catalog);
        var imported=SkirmishShipExchange.decode(exchanged,catalog);
        check(imported.rules().equals(rules)&&
                imported.design().at(laser).stage(Shipyard.Track.OUTPUT)==1,
                "Shared ship failed rule/design roundtrip");
        reject(()->SkirmishShipExchange.decode(exchanged.replace("CD-SKIRMISH-SHIP-1",
                "CD-SKIRMISH-SHIP-2"),catalog),"Tampered challenge format was accepted");
        reject(()->SkirmishShipExchange.encode(independent,tierOne,catalog),
                "Export ignored the challenge cap");
        var campaign=new CampaignTechnology.State(Battle.Faction.TERRAN);
        check(!campaign.has("TECH-TER-LAS-OUTPUT-01"),
                "Skirmish training incorrectly awarded permanent campaign research");
        System.out.println("PASS skirmish challenge: independent caps, hulls, branch/ID bans, fitting and portable exchange");
    }
}
