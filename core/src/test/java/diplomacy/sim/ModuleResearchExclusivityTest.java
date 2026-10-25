package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.Map;

/**
 * Every installed socket has exactly one research allegiance. Separate sockets
 * may freely specialize in different branches of the same module family.
 */
public final class ModuleResearchExclusivityTest {
    private ModuleResearchExclusivityTest(){}
    private static void check(boolean okay,String description){
        if(!okay)throw new AssertionError(description);
    }
    private static int weapon(Shipyard.Hull hull,int number){
        for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON&&number--==0)return slot.index();
        throw new IllegalArgumentException("Weapon index unavailable");
    }
    private static void rejected(Runnable action,String reason){
        try {action.run();throw new AssertionError("Accepted prohibited research: "+reason);}
        catch(IllegalArgumentException correct){
            check(correct.getMessage()!=null&&!correct.getMessage().isBlank(),
                    "Invalid upgrade should include actionable error");
        }
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        int first=weapon(hull,0),second=weapon(hull,1),third=weapon(hull,2);
        Shipyard.Design base=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(first,"BASE-TER-KIN")
                .install(second,"BASE-TER-KIN")
                .install(third,"BASE-TER-MSL");
        check(base.at(first).branch()==null,"New modules must remain branch-neutral");
        Shipyard.Design output=base.stage(first,Shipyard.Track.OUTPUT,2);
        check(output.at(first).branch()==Shipyard.Track.OUTPUT,
                "First ranked research commits that installed socket");
        check(output.at(second).branch()==null,
                "Changing one module never commits other installed modules");
        rejected(()->output.stage(first,Shipyard.Track.RESILIENCE,1),
                "Cannot mix OUTPUT with RESILIENCE");
        rejected(()->output.stage(first,Shipyard.Track.SPECIALIST,1),
                "Cannot mix OUTPUT with SPECIALIST");
        rejected(()->output.stage(first,Shipyard.Track.EFFICIENCY,1),
                "Cannot mix OUTPUT with EFFICIENCY");
        check(output.at(first).stage(Shipyard.Track.OUTPUT)==2,
                "Rejected research must not mutate the original immutable blueprint");

        Shipyard.Design specialized=output.stage(second,Shipyard.Track.RESILIENCE,2)
                .stage(third,Shipyard.Track.SPECIALIST,2);
        check(specialized.at(first).branch()==Shipyard.Track.OUTPUT
                &&specialized.at(second).branch()==Shipyard.Track.RESILIENCE
                &&specialized.at(third).branch()==Shipyard.Track.SPECIALIST,
                "Different identical modules must be able to specialize independently");
        ProjectileModel.Spec faster=Shipyard.projectileAt(specialized,catalog,first);
        ProjectileModel.Spec armored=Shipyard.projectileAt(specialized,catalog,second);
        check(faster.speed()>armored.speed(),"OUTPUT payload has distinct propulsion");
        check(armored.health()>faster.health(),"RESILIENCE payload has distinct projectile armor");

        int originalCost=Shipyard.inspect(base,catalog).research();
        int costA=Shipyard.inspect(output,catalog).research()-originalCost;
        int costB=Shipyard.inspect(base.stage(second,Shipyard.Track.RESILIENCE,2),
                catalog).research()-originalCost;
        int costC=Shipyard.inspect(base.stage(third,Shipyard.Track.SPECIALIST,2),
                catalog).research()-originalCost;
        check(Shipyard.inspect(specialized,catalog).research()==originalCost+costA+costB+costC,
                "Research costs must accumulate independently by installed socket");
        Shipyard.Design reset=output.stage(first,Shipyard.Track.OUTPUT,0);
        check(reset.at(first).branch()==null&&reset.at(first).stages().equals(Map.of()),
                "An explicit stage-zero respec releases a module's exclusive research lock");
        Shipyard.Design newPath=reset.stage(first,Shipyard.Track.RESILIENCE,2);
        check(newPath.at(first).branch()==Shipyard.Track.RESILIENCE,
                "Resetting makes another exclusive research branch available");
        check(Shipyard.projectileAt(newPath,catalog,first).equals(
                Shipyard.projectileAt(base.stage(first,Shipyard.Track.RESILIENCE,2),catalog,first)),
                "Respec must not retain any ghost bonuses from the previous branch");

        Shipyard.Design previous=Shipyard.decode(Shipyard.encode(output),catalog);
        check(previous.at(first).stage(Shipyard.Track.OUTPUT)==2
                &&previous.at(first).branch()==Shipyard.Track.OUTPUT,
                "Existing one-branch CD-SHIP-1 text blueprints remain compatible");
        String good=Shipyard.encode(output);
        String current=first+"|BASE-TER-KIN|2|0|0|0\n";
        check(good.contains(current),"Encoded fixture must include expected weapon socket");
        String stacked=good.replace(current,first+"|BASE-TER-KIN|2|0|2|0\n");
        rejected(()->Shipyard.decode(stacked,catalog),
                "Legacy stacked-branch blueprint must be rejected, not silently downgraded");
        check(good.equals(Shipyard.encode(output)),
                "Legacy rejection must not mutate or overwrite original in-memory blueprint");

        // Research catalogue remains broad; exclusivity is installed per socket,
        // not a global ban on pursuing diverse fleet-wide technology.
        for(Battle.Faction faction:Battle.Faction.values()){
            String id=Shipyard.factionId(faction);
            for(String kind:new String[]{"KIN","MSL","TOR","PD","SEN"}){
                Shipyard.Design d=Shipyard.starter(faction,hull)
                        .install(first,"BASE-"+id+"-"+kind);
                for(Shipyard.Track branch:Shipyard.Track.values()){
                    Shipyard.Design single=d.stage(first,branch,6);
                    check(single.at(first).branch()==branch
                            &&single.at(first).stage(branch)==6,
                            "All four 6-stage branches available individually: "+id+"/"+kind+"/"+branch);
                    check(single.at(first).stages().size()==1,
                            "No fixture may carry two mutually exclusive paths");
                    for(int rank=1;rank<=6;rank++)
                        check(catalog.get("TECH-"+id+"-"+kind+"-"+branch+"-"
                                +String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Every selected rank has a valid same-branch prerequisite chain");
                }
            }
        }
        System.out.println("PASS per-module exclusive research: 4 branches x 6 stages, independent sockets, switch lock, reset, physics, cumulative costs and legacy safety");
    }
}
