package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A selected fleet is a list of actual ship blueprints, not a single
 * template cloned N times. A Tier VI match accepts any mix of Tier I-VI
 * fittings. All tiers use the same technology catalog as campaign research. */
public record SkirmishFleet(List<Shipyard.Design> ships) {
    public SkirmishFleet {
        if(ships==null||ships.isEmpty()||ships.size()>200||
                ships.stream().anyMatch(d->d==null))
            throw new IllegalArgumentException("Skirmish fleet must contain 1-200 fitted ships");
        ships=List.copyOf(ships);
        Battle.Faction faction=ships.get(0).faction;
        if(ships.stream().anyMatch(d->d.faction!=faction))
            throw new IllegalArgumentException("A fleet must belong to a single faction");
    }
    public static SkirmishFleet repeat(Shipyard.Design template,int count){
        if(template==null||count<1||count>200)
            throw new IllegalArgumentException("Invalid skirmish fleet template/count");
        return new SkirmishFleet(Collections.nCopies(count,template));
    }
    public Battle.Faction faction(){return ships.get(0).faction;}
    public int size(){return ships.size();}
    public Shipyard.Design ship(int index){return ships.get(index);}
    public SkirmishFleet with(int index,Shipyard.Design design){
        if(index<0||index>=ships.size()||design==null||design.faction!=faction())
            throw new IllegalArgumentException("Ship slot and faction must match");
        List<Shipyard.Design> next=new ArrayList<>(ships);
        next.set(index,design);
        return new SkirmishFleet(next);
    }
    public SkirmishFleet resize(int count){
        if(count<1||count>200)throw new IllegalArgumentException("Fleet size outside 1-200");
        List<Shipyard.Design> next=new ArrayList<>(count);
        for(int i=0;i<count;i++)
            next.add(i<ships.size()?ships.get(i):ships.get(ships.size()-1));
        return new SkirmishFleet(next);
    }
    public void requireAllowed(SkirmishTechnologyRules policy,TechnologyCatalog catalog){
        if(policy==null||catalog==null)
            throw new IllegalArgumentException("Missing host challenge policy or catalog");
        for(int i=0;i<ships.size();i++){
            Shipyard.Design design=ships.get(i);
            try{
                policy.requireAllowed(design,catalog);
                Shipyard.Report report=Shipyard.inspect(design,catalog);
                if(!report.valid())throw new IllegalArgumentException(report.explanation());
            }catch(IllegalArgumentException blocked){
                throw new IllegalArgumentException("Fleet ship "+(i+1)+" "+design.hull+
                        ": "+blocked.getMessage(),blocked);
            }
        }
    }
}
