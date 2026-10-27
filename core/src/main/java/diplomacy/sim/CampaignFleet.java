package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/**
 * Campaign-owned ships: active sortie group and reserve are independent,
 * persistent sets. A surviving or escaped hull is never bought twice.
 * No free repairs, crews, expendables or fuel are minted on redeployment.
 */
public final class CampaignFleet {
    private CampaignFleet(){}
    public enum Service { REPAIR, CREW, AMMUNITION, FUEL, CRAFT }
    public record Stores(double hull,String integrity,double crew,int kinetic,int missiles,
                         int torpedoes,int fighters,int drones,int repairDrones,double fuel) {
        public Stores {
            if(!Double.isFinite(hull)||hull<0||integrity==null||integrity.isEmpty()||
                    !Double.isFinite(crew)||crew<0||kinetic<0||missiles<0||torpedoes<0||
                    fighters<0||drones<0||repairDrones<0||!Double.isFinite(fuel)||fuel<0)
                throw new IllegalArgumentException("Invalid ship stores");
        }
        public static Stores capture(Battle.Ship ship){
            return new Stores(Math.max(0,ship.hull),ship.defense.saveIntegrity(),
                    Math.max(0,ship.crewCount),ship.kineticAmmo,ship.missileAmmo,
                    ship.torpedoAmmo,ship.fighterReserve,ship.droneReserve,
                    ship.repairDroneReserve,Math.max(0,ship.fuel));
        }
        public void apply(Battle.Ship ship){
            if(hull>ship.maxHull+1e-6||crew>Math.min(ship.crewBerths,ship.lifeSupport)+1e-6||
                    kinetic>ship.kineticAmmo||missiles>ship.missileAmmo||
                    torpedoes>ship.torpedoAmmo||fighters>ship.fighterReserve||
                    drones>ship.droneReserve||repairDrones>ship.repairDroneReserve||
                    fuel>ship.maxFuel+1e-6)
                throw new IllegalArgumentException("Saved inventory exceeds this ship's fitted capacity");
            ship.defense.restoreIntegrity(integrity);
            ship.hull=hull;ship.shields=ship.defense.totalShields();
            ship.armor=ship.defense.totalArmor();
            ship.crewCount=crew;ship.kineticAmmo=kinetic;ship.missileAmmo=missiles;
            ship.torpedoAmmo=torpedoes;ship.fighterReserve=fighters;ship.droneReserve=drones;
            ship.repairDroneReserve=repairDrones;ship.fuel=fuel;
        }
    }
    public record Vessel(int id,String name,Shipyard.Design fitting,boolean deployed,Stores stores) {
        public Vessel {
            if(id<1||name==null||name.isBlank()||name.length()>70||fitting==null||stores==null)
                throw new IllegalArgumentException("Invalid commissioned ship");
        }
        public Vessel active(boolean selected){return new Vessel(id,name,fitting,selected,stores);}
        public Vessel withName(String value){return new Vessel(id,value,fitting,deployed,stores);}
        public Vessel withStores(Stores value){return new Vessel(id,name,fitting,deployed,value);}
    }
    public record Roster(Battle.Faction faction,FleetNames.Style style,int nextId,
                         List<Vessel> ships) {
        public Roster {
            if(faction==null||style==null||nextId<1||ships==null||ships.size()>200)
                throw new IllegalArgumentException("Invalid fleet roster");
            ships=List.copyOf(ships);
            HashSet<Integer> ids=new HashSet<>();
            for(Vessel v:ships)
                if(v==null||v.id()>=nextId||!ids.add(v.id())||v.fitting().faction!=faction)
                    throw new IllegalArgumentException("Duplicate or misplaced fleet hull");
        }
        public List<Vessel> active(){
            return ships.stream().filter(Vessel::deployed).toList();
        }
        public List<Vessel> reserves(){
            return ships.stream().filter(v->!v.deployed()).toList();
        }
        public Vessel get(int id){
            return ships.stream().filter(s->s.id()==id).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown ship ID"));
        }
        public Roster setActive(int id,boolean selected,int cap){
            if(cap<1||cap>200)throw new IllegalArgumentException("Invalid deployment cap");
            if(selected&&!get(id).deployed()&&active().size()>=cap)
                throw new IllegalArgumentException("Mission fleet cap reached");
            List<Vessel> result=new ArrayList<>();
            for(Vessel v:ships)result.add(v.id()==id?v.active(selected):v);
            return new Roster(faction,style,nextId,result);
        }
        public Roster changeStyle(FleetNames.Style desired){
            return new Roster(faction,desired,nextId,ships);
        }
        public Roster rename(int id,String name){
            List<Vessel> result=new ArrayList<>();
            for(Vessel v:ships)result.add(v.id()==id?v.withName(name):v);
            return new Roster(faction,style,nextId,result);
        }
        public Roster purchase(Shipyard.Design design,TechnologyCatalog technology) {
            if(ships.size()>=200||design==null||design.faction!=faction)
                throw new IllegalArgumentException("Fleet full or wrong faction");
            Battle.Ship newShip=fresh(design,technology);
            // A delivered hull has engines but needs paid personnel, fuel and
            // replacement ordnance; shipyard construction does not grant stock.
            newShip.crewCount=0;newShip.fuel=0;
            newShip.kineticAmmo=0;newShip.missileAmmo=0;newShip.torpedoAmmo=0;
            newShip.fighterReserve=0;newShip.droneReserve=0;newShip.repairDroneReserve=0;
            ArrayList<Vessel> result=new ArrayList<>(ships);
            result.add(new Vessel(nextId,FleetNames.generate(style,design.faction.ordinal(),nextId),
                    design,false,Stores.capture(newShip)));
            return new Roster(faction,style,nextId+1,result);
        }
        public Roster afterBattle(Battle battle,List<Integer> deployedIds){
            if(battle==null||deployedIds==null||active().size()!=deployedIds.size())
                throw new IllegalArgumentException("Incomplete campaign sortie");
            List<Battle.Ship> participants=battle.ships().stream()
                    .filter(s->s.team==0).toList();
            if(participants.size()!=deployedIds.size())
                throw new IllegalArgumentException("Battle's actual fleet size differs");
            for(int i=0;i<deployedIds.size();i++)
                if(active().get(i).id()!=deployedIds.get(i))
                    throw new IllegalArgumentException("Deployment identity mismatch");
            ArrayList<Vessel> result=new ArrayList<>(ships);
            for(int i=0;i<participants.size();i++){
                Battle.Ship s=participants.get(i);
                Vessel prior=get(deployedIds.get(i));
                result.remove(prior);
                if(s.hull>0){
                    // Escaped or committed-to-escape hulls are preserved but
                    // placed in RESERVE, never silently reactivated/replaced.
                    boolean active=prior.deployed()&&!s.escaped&&!s.withdrawing;
                    result.add(new Vessel(prior.id(),prior.name(),prior.fitting(),active,
                            Stores.capture(s)));
                }
            }
            result.sort(java.util.Comparator.comparingInt(Vessel::id));
            return new Roster(faction,style,nextId,result);
        }
        public Roster service(int id,Service category,TechnologyCatalog technology) {
            Vessel v=get(id);
            Battle.Ship fitted=fresh(v.fitting(),technology);
            v.stores().apply(fitted);
            switch(category){
                case REPAIR -> {
                    fitted.hull=fitted.maxHull;fitted.defense.overhaul();
                    fitted.shields=fitted.maxShields;fitted.armor=fitted.maxArmor;
                }
                case CREW -> fitted.crewCount=Math.min(fitted.crewBerths,fitted.lifeSupport);
                case AMMUNITION -> {
                    Battle.Ship fully=fresh(v.fitting(),technology);
                    fitted.kineticAmmo=fully.kineticAmmo;fitted.missileAmmo=fully.missileAmmo;
                    fitted.torpedoAmmo=fully.torpedoAmmo;
                }
                case FUEL -> fitted.fuel=fitted.maxFuel;
                case CRAFT -> {
                    Battle.Ship fully=fresh(v.fitting(),technology);
                    fitted.fighterReserve=fully.fighterReserve;fitted.droneReserve=fully.droneReserve;
                    fitted.repairDroneReserve=fully.repairDroneReserve;
                }
            }
            ArrayList<Vessel> updated=new ArrayList<>(ships);
            updated.replaceAll(item->item.id()==id?item.withStores(Stores.capture(fitted)):item);
            return new Roster(faction,style,nextId,updated);
        }
        public long cost(int id,Service service,TechnologyCatalog technology){
            Vessel vessel=get(id);
            Battle.Ship full=fresh(vessel.fitting(),technology);
            Stores current=vessel.stores();
            return switch(service){
                case REPAIR -> {
                    full.defense.restoreIntegrity(current.integrity());
                    long structure=(long)Math.ceil(Math.max(0,full.maxHull-current.hull())*18);
                    long armor=(long)Math.ceil(Math.max(0,full.maxArmor-full.defense.totalArmor())*6);
                    yield Math.addExact(structure,armor);
                }
                case CREW -> (long)Math.ceil(Math.max(0,
                        Math.min(full.crewBerths,full.lifeSupport)-current.crew())*95);
                case AMMUNITION -> 12L*(full.kineticAmmo-current.kinetic())
                        +55L*(full.missileAmmo-current.missiles())
                        +180L*(full.torpedoAmmo-current.torpedoes());
                case FUEL -> (long)Math.ceil(Math.max(0,full.maxFuel-current.fuel())*8);
                case CRAFT -> 750L*(full.fighterReserve-current.fighters())
                        +250L*(full.droneReserve-current.drones())
                        +300L*(full.repairDroneReserve-current.repairDrones());
            };
        }
    }
    public static long purchaseCost(Shipyard.Design design){
        if(design==null)throw new IllegalArgumentException("Missing design");
        return 7000L+(long)Math.ceil(design.hull.baseHull*72)+design.hull.slots.size()*1300L;
    }
    public static Battle.Ship fresh(Shipyard.Design fitted,TechnologyCatalog technology){
        if(fitted==null||technology==null)throw new IllegalArgumentException("Ship fitting required");
        Battle sandbox=new Battle(0);
        sandbox.spawnWing(0,fitted,technology,1,310,335);
        return sandbox.ships().get(0);
    }
    public static Roster start(Battle.Faction faction,Shipyard.Design starter,
                               TechnologyCatalog technology){
        if(faction==null||starter==null||starter.faction!=faction)
            throw new IllegalArgumentException("Starter faction mismatch");
        ArrayList<Vessel> vessels=new ArrayList<>();
        for(int i=1;i<=4;i++)
            vessels.add(new Vessel(i,FleetNames.generate(FleetNames.Style.NAVAL,
                    faction.ordinal(),i),starter,true,Stores.capture(fresh(starter,technology))));
        return new Roster(faction,FleetNames.Style.NAVAL,5,vessels);
    }
}
