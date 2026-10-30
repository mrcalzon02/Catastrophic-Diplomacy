package diplomacy.sim;

import diplomacy.shipyard.ShipTechnologyFit;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.Set;

/** Snapshot-specific technology fitting without authorizing or funding research. */
public final class ShipTechnologyFitTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private static void reject(Runnable runnable,String text){
        try{runnable.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(text);
    }
    public static void main(String[] args)throws Exception{
        var catalog=TechnologyCatalog.loadDefault();
        var hull=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int first=-1,second=-1;
        for(int i=0;i<hull.slots();i++)
            if(hull.at(i)!=null&&hull.at(i).component.equals("BASE-TER-LAS")){
                first=i;break;
            }
        check(first>=0,"Starter lacks laser mount");
        // A second copy makes the family-application policy observable.
        for(int i=0;i<hull.slots();i++)if(i!=first&&
                hull.placements().get(i).mount()==Shipyard.Mount.WEAPON){
            second=i;break;
        }
        check(second>=0,"No other weapon socket to test independent configurations");
        var twin=hull.install(second,"BASE-TER-LAS");
        Set<String> researched=new HashSet<>();
        researched.add("TECH-TER-LAS-OUTPUT-01");
        researched.add("TECH-TER-LAS-EFFICIENCY-01");
        var owned=new CampaignTechnology.State(Battle.Faction.TERRAN,researched,null,0,0);
        var unresearched=new CampaignTechnology.State(Battle.Faction.TERRAN);
        final int selected=first,other=second;
        reject(()->ShipTechnologyFit.fit(twin,catalog,unresearched,selected,
                Shipyard.Track.OUTPUT,1,ShipTechnologyFit.Scope.SOCKET),
                "Unresearched technology bypassed campaign authorization");
        reject(()->ShipTechnologyFit.fit(twin,catalog,owned,selected,
                Shipyard.Track.OUTPUT,2,ShipTechnologyFit.Scope.SOCKET),
                "An unavailable higher rank was selected");
        var firstOnly=ShipTechnologyFit.fit(twin,catalog,owned,first,
                Shipyard.Track.OUTPUT,1,ShipTechnologyFit.Scope.SOCKET);
        check(firstOnly.at(first).stage(Shipyard.Track.OUTPUT)==1&&
                firstOnly.at(second).branch()==null&&twin.at(first).branch()==null,
                "Socket fitting mutated an unrelated socket or original ship");
        var combined=ShipTechnologyFit.fit(firstOnly,catalog,owned,second,
                Shipyard.Track.EFFICIENCY,1,ShipTechnologyFit.Scope.SOCKET);
        check(combined.at(first).branch()==Shipyard.Track.OUTPUT&&
                combined.at(second).branch()==Shipyard.Track.EFFICIENCY,
                "Identical guns cannot hold different owned specializations");
        var matching=ShipTechnologyFit.fit(combined,catalog,owned,first,
                Shipyard.Track.OUTPUT,1,ShipTechnologyFit.Scope.MATCHING_COMPONENTS);
        check(matching.at(first).branch()==Shipyard.Track.OUTPUT&&
                matching.at(second).branch()==Shipyard.Track.OUTPUT,
                "Matching family template was not applied to all fitted copies");
        var cleared=ShipTechnologyFit.clear(combined,first);
        check(cleared.at(first).branch()==null&&
                cleared.at(second).branch()==Shipyard.Track.EFFICIENCY,
                "Clear specialization rewrote an unrelated socket");
        var doctrine=ShipTechnologyFit.doctrine(twin,catalog,owned,Shipyard.Track.OUTPUT);
        check(doctrine.at(first).stage(Shipyard.Track.OUTPUT)==1&&
                doctrine.at(second).stage(Shipyard.Track.OUTPUT)==1&&
                doctrine.at(0).branch()==null,
                "Ship doctrine did not select highest unlocked matching parts only");
        check(ShipTechnologyFit.highestAvailable(twin,catalog,owned,first,
                Shipyard.Track.OUTPUT)==1,"Unlocked technology stage calculation wrong");
        var wallet=new CampaignProgress.State(Battle.Faction.TERRAN,1,100000,0,1);
        CampaignShipyardRules.requireAllowed(doctrine,wallet,owned,catalog);
        check(Shipyard.decode(Shipyard.encode(doctrine),catalog).at(first)
                .stage(Shipyard.Track.OUTPUT)==1,
                "Blueprint snapshot did not preserve selected technologies");
        check(!Shipyard.encode(twin).equals(Shipyard.encode(doctrine)),
                "Technology selection failed to change the physical ship blueprint");
        check(owned.unlocked().equals(researched),"Fitting unexpectedly changed researched unlocks");
        System.out.println("PASS independent ship technology snapshots, per-socket/parallel/matching/doctrine unlock enforcement");
    }
}
