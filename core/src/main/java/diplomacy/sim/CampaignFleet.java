package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Campaign-owned ships: active sortie group and reserve are independent,
 * persistent sets. A surviving or escaped hull is never bought twice.
 * No free repairs, crews, expendables or fuel are minted on redeployment.
 */
public final class CampaignFleet {
    private CampaignFleet(){}
    public enum Service { REPAIR, CREW, AMMUNITION, FUEL, CRAFT }
    public record Stores(double hull,String integrity,String machinery,double crew,int kinetic,int missiles,
                         int torpedoes,int fighters,int drones,int repairDrones,double fuel) {
        public Stores {
            if(!Double.isFinite(hull)||hull<0||integrity==null||integrity.isEmpty()||
                    machinery==null||machinery.isEmpty()||
                    !Double.isFinite(crew)||crew<0||kinetic<0||missiles<0||torpedoes<0||
                    fighters<0||drones<0||repairDrones<0||!Double.isFinite(fuel)||fuel<0)
                throw new IllegalArgumentException("Invalid ship stores");
        }
        public static Stores capture(Battle.Ship ship){
            return new Stores(Math.max(0,ship.hull),ship.defense.saveIntegrity(),
                    ShipSubsystems.capture(ship),Math.max(0,ship.crewCount),ship.kineticAmmo,ship.missileAmmo,
                    ship.torpedoAmmo,ship.fighterReserve,ship.droneReserve,
                    ship.repairDroneReserve,Math.max(0,ship.fuel));
        }
        public Stores recover(int additionalFighters,int additionalDrones,int additionalRepair){
            if(additionalFighters<0||additionalDrones<0||additionalRepair<0)
                throw new IllegalArgumentException("Invalid recovered spacecraft");
            return new Stores(hull,integrity,machinery,crew,kinetic,missiles,torpedoes,
                    Math.addExact(fighters,additionalFighters),
                    Math.addExact(drones,additionalDrones),
                    Math.addExact(repairDrones,additionalRepair),fuel);
        }
        public void apply(Battle.Ship ship){
            if(hull>ship.maxHull+1e-6||crew>Math.min(ship.crewBerths,ship.lifeSupport)+1e-6||
                    kinetic>ship.kineticAmmo||missiles>ship.missileAmmo||
                    torpedoes>ship.torpedoAmmo||fighters>ship.fighterReserve||
                    drones>ship.droneReserve||repairDrones>ship.repairDroneReserve||
                    fuel>ship.maxFuel+1e-6)
                throw new IllegalArgumentException("Saved inventory exceeds this ship's fitted capacity");
            ship.defense.restoreIntegrity(integrity);
            ShipSubsystems.restore(ship,machinery);
            ship.hull=hull;ship.shields=ship.defense.totalShields();
            ship.armor=ship.defense.totalArmor();
            ship.crewCount=crew;ship.kineticAmmo=kinetic;ship.missileAmmo=missiles;
            ship.torpedoAmmo=torpedoes;ship.fighterReserve=fighters;ship.droneReserve=drones;
            ship.repairDroneReserve=repairDrones;ship.fuel=fuel;
        }
    }
    public record Vessel(long id,String name,Shipyard.Design fitting,boolean deployed,Stores stores) {
        public Vessel {
            if(!VesselIdentifiers.valid(id)||name==null||name.isBlank()||
                    name.length()>70||fitting==null||stores==null)
                throw new IllegalArgumentException("Invalid commissioned ship");
        }
        public Vessel active(boolean selected){return new Vessel(id,name,fitting,selected,stores);}
        public Vessel withName(String value){return new Vessel(id,value,fitting,deployed,stores);}
        public Vessel withStores(Stores value){return new Vessel(id,name,fitting,deployed,value);}
    }
    /** Squadron 0 inherits the fleet-wide culture and radio voice.
     * Squadrons 1..7 may override those defaults independently. */
    /** Service deficits are calculated against the vessel's actual fitted
     * maximums. Energy-only ships do not get spurious ammunition warnings. */
    public record Readiness(Vessel vessel,List<Service> deficits){
        public Readiness {
            if(vessel==null||deficits==null||deficits.isEmpty())
                throw new IllegalArgumentException("Readiness requires a damaged or depleted vessel");
            deficits=List.copyOf(deficits);
        }
    }
    public record SquadronProfile(FleetNames.Style naming,CrewChatter.Tone radio){
        public SquadronProfile {
            if(naming==null||radio==null)
                throw new IllegalArgumentException("Squadron needs name and voice");
        }
    }
    public static final int SQUADRON_COUNT=8;
    public record Roster(Battle.Faction faction,FleetNames.Style style,int nextSerial,
                         List<Vessel> ships,long namingSeed,CrewChatter.Tone radioTone,
                         Map<Long,Integer> squadronAssignments,
                         Map<Integer,SquadronProfile> squadronProfiles) {
        public Roster(Battle.Faction faction,FleetNames.Style style,int nextSerial,
                      List<Vessel> ships,long namingSeed,CrewChatter.Tone radioTone){
            this(faction,style,nextSerial,ships,namingSeed,radioTone,Map.of(),Map.of());
        }
        /** Historical API and imported saves retain their former naming. */
        public Roster(Battle.Faction faction,FleetNames.Style style,int nextSerial,
                      List<Vessel> ships){
            this(faction,style,nextSerial,ships,faction==null?0:faction.ordinal(),
                    CrewChatter.Tone.MIXED);
        }
        public Roster {
            if(faction==null||style==null||radioTone==null||nextSerial<1||
                    nextSerial>VesselIdentifiers.MAX_SERIAL+1||ships==null||ships.size()>200||
                    squadronAssignments==null||squadronProfiles==null)
                throw new IllegalArgumentException("Invalid fleet roster");
            ships=List.copyOf(ships);
            HashSet<Long> ids=new HashSet<>();
            HashSet<String> names=new HashSet<>();
            for(Vessel v:ships)
                if(v==null||VesselIdentifiers.serial(v.id())>=nextSerial||
                        v.id()!=VesselIdentifiers.commissioned(namingSeed,
                                VesselIdentifiers.serial(v.id()))||
                        !ids.add(v.id())||
                        !names.add(v.name().toLowerCase(java.util.Locale.ROOT))||
                        v.fitting().faction!=faction)
                    throw new IllegalArgumentException("Duplicate or misplaced fleet hull");
            for(var entry:squadronAssignments.entrySet())
                if(entry.getKey()==null||!ids.contains(entry.getKey())||
                        entry.getValue()==null||entry.getValue()<1||
                        entry.getValue()>=SQUADRON_COUNT)
                    throw new IllegalArgumentException("Squadron references nonexistent owned ship");
            for(var entry:squadronProfiles.entrySet())
                if(entry.getKey()==null||entry.getKey()<1||
                        entry.getKey()>=SQUADRON_COUNT||entry.getValue()==null)
                    throw new IllegalArgumentException("Invalid squadron profile");
            squadronAssignments=Map.copyOf(squadronAssignments);
            squadronProfiles=Map.copyOf(squadronProfiles);
        }
        public int squadronOf(long id){
            get(id);
            return squadronAssignments.getOrDefault(id,0);
        }
        public SquadronProfile profile(int group){
            if(group<0||group>=SQUADRON_COUNT)
                throw new IllegalArgumentException("Invalid squadron number");
            return group==0?new SquadronProfile(style,radioTone):
                    squadronProfiles.getOrDefault(group,new SquadronProfile(style,radioTone));
        }
        public FleetNames.Style namingFor(long shipId){return profile(squadronOf(shipId)).naming();}
        public CrewChatter.Tone radioFor(long shipId){return profile(squadronOf(shipId)).radio();}
        public Roster assignSquadron(long shipId,int group){
            get(shipId);
            if(group<0||group>=SQUADRON_COUNT)
                throw new IllegalArgumentException("Invalid squadron group");
            Map<Long,Integer> updated=new HashMap<>(squadronAssignments);
            if(group==0)updated.remove(shipId);else updated.put(shipId,group);
            return new Roster(faction,style,nextSerial,ships,namingSeed,radioTone,
                    updated,squadronProfiles);
        }
        public Roster setSquadronProfile(int group,FleetNames.Style culture,
                                         CrewChatter.Tone voice){
            if(group<=0||group>=SQUADRON_COUNT)
                throw new IllegalArgumentException("Only squads 1..7 have overrides");
            var profile=new SquadronProfile(culture,voice);
            Map<Integer,SquadronProfile> updated=new HashMap<>(squadronProfiles);
            updated.put(group,profile);
            return new Roster(faction,style,nextSerial,ships,namingSeed,radioTone,
                    squadronAssignments,updated);
        }
        public List<Vessel> squadronShips(int group){
            profile(group);
            return ships.stream().filter(s->squadronOf(s.id())==group).toList();
        }
        public List<Vessel> active(){
            return ships.stream().filter(Vessel::deployed).toList();
        }
        public List<Vessel> reserves(){
            return ships.stream().filter(v->!v.deployed()).toList();
        }
        /** Saved captain targets use immutable owned IDs; battle references
         * use ordinal slots in the currently selected active fleet. */
        public java.util.Map<Integer,ShipStandingOrders> forDeployment(
                java.util.Map<Long,ShipStandingOrders> saved){
            if(saved==null)throw new IllegalArgumentException("Missing captain doctrines");
            List<Vessel> selected=active();
            java.util.HashMap<Integer,ShipStandingOrders> result=new java.util.HashMap<>();
            for(int i=0;i<selected.size();i++){
                ShipStandingOrders policy=saved.get(selected.get(i).id());
                if(policy!=null)
                    result.put(i,translate(policy,selected,ships,false));
            }
            return result;
        }
        public ShipStandingOrders toPersistent(int deployedIndex,ShipStandingOrders policy){
            if(policy==null||deployedIndex<0||deployedIndex>=active().size())
                throw new IllegalArgumentException("Unknown commissioned captain");
            return translate(policy,active(),ships,true);
        }
        private static ShipStandingOrders translate(ShipStandingOrders policy,
                List<Vessel> deployed,List<Vessel> all,boolean toPersistent){
            var d=policy.directives();
            java.util.ArrayList<Integer> defended=new java.util.ArrayList<>();
            for(int id:d.defendSlots())
                defended.add(reference(id,deployed,all,toPersistent));
            var moved=new ShipTacticalDirectives(d.speedPercent(),d.interceptStrategy(),
                    d.defenseScope(),d.fighterMission(),defended,
                    reference(d.escortSlot(),deployed,all,toPersistent),d.escortRange());
            return new ShipStandingOrders(policy.tactics(),policy.aggression(),
                    policy.caution(),policy.shieldWithdrawPercent(),moved);
        }
        private static int reference(int slot,List<Vessel> deployed,List<Vessel> all,
                                     boolean toPersistent){
            if(slot<0)return -1;
            if(toPersistent){
                if(slot>=deployed.size())return -1;
                long id=deployed.get(slot).id();
                for(int i=0;i<all.size();i++)if(all.get(i).id()==id)return i;
                return -1;
            }
            if(slot>=all.size())return -1;
            long id=all.get(slot).id();
            for(int i=0;i<deployed.size();i++)if(deployed.get(i).id()==id)return i;
            return -1;
        }
        public Vessel get(long id){
            return ships.stream().filter(s->s.id()==id).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown ship ID"));
        }
        public Roster setActive(long id,boolean selected,int cap){
            if(cap<1||cap>200)throw new IllegalArgumentException("Invalid deployment cap");
            if(selected&&!get(id).deployed()&&active().size()>=cap)
                throw new IllegalArgumentException("Mission fleet cap reached");
            List<Vessel> result=new ArrayList<>();
            for(Vessel v:ships)result.add(v.id()==id?v.active(selected):v);
            return new Roster(faction,style,nextSerial,result,namingSeed,radioTone,squadronAssignments,squadronProfiles);
        }

        /** Mobilize or stand down a commissioned squadron as one roster edit.
         * The cap check precedes any change, so partially mobilized groups
         * can never be persisted. */
        public Roster setSquadronActive(int group,boolean selected,int cap){
            profile(group);
            if(cap<1||cap>200)throw new IllegalArgumentException("Invalid deployment cap");
            List<Vessel> members=squadronShips(group);
            if(members.isEmpty())throw new IllegalArgumentException("Squadron has no commissioned ships");
            if(selected){
                long additions=members.stream().filter(v->!v.deployed()).count();
                if(active().size()+additions>cap)
                    throw new IllegalArgumentException("Squadron exceeds mission fleet cap");
            }
            List<Vessel> updated=new ArrayList<>(ships.size());
            for(Vessel vessel:ships)
                updated.add(squadronAssignments.getOrDefault(vessel.id(),0)==group?
                        vessel.active(selected):vessel);
            return new Roster(faction,style,nextSerial,updated,namingSeed,radioTone,
                    squadronAssignments,squadronProfiles);
        }
        public Roster changeStyle(FleetNames.Style desired){
            if(desired==null)throw new IllegalArgumentException("Name style required");
            return new Roster(faction,desired,nextSerial,ships,namingSeed,radioTone,squadronAssignments,squadronProfiles);
        }
        public Roster changeRadioTone(CrewChatter.Tone desired){
            if(desired==null)throw new IllegalArgumentException("Radio tone required");
            return new Roster(faction,style,nextSerial,ships,namingSeed,desired,squadronAssignments,squadronProfiles);
        }
        public Roster rename(long id,String name){
            get(id); // A stale dock selection must never report a successful rename.
            if(name==null||name.trim().isEmpty()||name.trim().length()>70||
                    name.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Ship name must contain 1–70 printable characters");
            String unique=name.trim();
            for(Vessel v:ships)
                if(v.id()!=id&&v.name().equalsIgnoreCase(unique))
                    throw new IllegalArgumentException("Another commissioned ship already uses that name");
            List<Vessel> result=new ArrayList<>();
            for(Vessel v:ships)result.add(v.id()==id?v.withName(unique):v);
            return new Roster(faction,style,nextSerial,result,namingSeed,radioTone,squadronAssignments,squadronProfiles);
        }
        /** Regenerate a distinctive name without altering the sixteen-digit ID. */
        public Roster generateName(long id){
            Vessel selected=get(id);
            long rerollSeed=namingSeed^((long)selected.name().hashCode()*0xd1b54a32d192ed03L);
            String generated=FleetNames.unique(namingFor(id),rerollSeed,
                    VesselIdentifiers.serial(id),ships.stream().map(Vessel::name).toList());
            return rename(id,generated);
        }
        public Roster purchase(Shipyard.Design design,TechnologyCatalog technology) {
            if(ships.size()>=200||nextSerial>VesselIdentifiers.MAX_SERIAL||design==null||design.faction!=faction)
                throw new IllegalArgumentException("Fleet full or wrong faction");
            Battle.Ship newShip=fresh(design,technology);
            // A delivered hull has engines but needs paid personnel, fuel and
            // replacement ordnance; shipyard construction does not grant stock.
            newShip.crewCount=0;newShip.fuel=0;
            newShip.kineticAmmo=0;newShip.missileAmmo=0;newShip.torpedoAmmo=0;
            newShip.fighterReserve=0;newShip.droneReserve=0;newShip.repairDroneReserve=0;
            ArrayList<Vessel> result=new ArrayList<>(ships);
            String uniqueName=FleetNames.unique(style,namingSeed,nextSerial,
                    ships.stream().map(Vessel::name).toList());
            result.add(new Vessel(VesselIdentifiers.commissioned(namingSeed,nextSerial),uniqueName,
                    design,false,Stores.capture(newShip)));
            return new Roster(faction,style,nextSerial+1,result,namingSeed,radioTone,squadronAssignments,squadronProfiles);
        }
        public Roster afterBattle(Battle battle,List<Long> deployedIds){
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
                // Zero identifies an uncommissioned skirmish hull; accepting it
                // would silently charge its damage to a different campaign ship.
                if(s.commissionedId!=deployedIds.get(i))
                    throw new IllegalArgumentException(
                            "Combat participant is not the commissioned hull");
                Vessel prior=get(deployedIds.get(i));
                result.remove(prior);
                if(s.hull>0){
                    // Escaped or committed-to-escape hulls are preserved but
                    // placed in RESERVE, never silently reactivated/replaced.
                    boolean active=prior.deployed()&&!s.escaped&&!s.withdrawing;
                    int fighters=0,drones=0,repairs=0;
                    for(Battle.Craft craft:battle.crafts()){
                        if(craft.sourceId!=s.id||craft.integrity<=0||craft.fuel<=0)continue;
                        if(craft.fighter)fighters++;
                        else if(craft.repair)repairs++;
                        else drones++;
                    }
                    // Only craft that actually survive in the battle can be
                    // recovered, and never more than this vessel launched.
                    Stores supplies=Stores.capture(s).recover(
                            Math.min(fighters,s.fightersLaunched),
                            Math.min(drones,s.dronesLaunched),
                            Math.min(repairs,s.repairDronesLaunched));
                    result.add(new Vessel(prior.id(),prior.name(),prior.fitting(),active,supplies));
                }
            }
            result.sort(java.util.Comparator.comparingLong(Vessel::id));
            Set<Long> surviving=result.stream().map(Vessel::id).collect(java.util.stream.Collectors.toSet());
            Map<Long,Integer> clean=new HashMap<>(squadronAssignments);
            clean.keySet().retainAll(surviving);
            return new Roster(faction,style,nextSerial,result,namingSeed,radioTone,
                    clean,squadronProfiles);
        }
        public Roster service(long id,Service category,TechnologyCatalog technology) {
            Vessel v=get(id);
            Battle.Ship fitted=fresh(v.fitting(),technology);
            v.stores().apply(fitted);
            switch(category){
                case REPAIR -> {
                    fitted.hull=fitted.maxHull;fitted.defense.overhaul();
                    fitted.shields=fitted.maxShields;fitted.armor=fitted.maxArmor;
                    ShipSubsystems.restore(fitted,ShipSubsystems.full());
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
            return new Roster(faction,style,nextSerial,updated,namingSeed,radioTone,squadronAssignments,squadronProfiles);
        }
        /** Report only paid deficits applicable to a vessel's fitted equipment. */
        public List<Service> requiredServices(long id,TechnologyCatalog technology){
            ArrayList<Service> needed=new ArrayList<>();
            for(Service service:Service.values())
                if(cost(id,service,technology)>0)needed.add(service);
            return List.copyOf(needed);
        }
        /** An advisory, not a blocking gate: captains may risk damaged ships. */
        public List<Readiness> activeServiceWarnings(TechnologyCatalog technology){
            ArrayList<Readiness> warnings=new ArrayList<>();
            for(Vessel vessel:active()){
                List<Service> deficits=requiredServices(vessel.id(),technology);
                if(!deficits.isEmpty())warnings.add(new Readiness(vessel,deficits));
            }
            return List.copyOf(warnings);
        }
        /** Quote the complete dock job before debiting credits. All five services
         * are calculated from this vessel's actual fitted capacity and stores. */
        public long costAll(long id,TechnologyCatalog technology){
            long total=0;
            for(Service type:Service.values())
                total=Math.addExact(total,cost(id,type,technology));
            return total;
        }
        /** Return one new immutable fleet only after every service succeeds.
         * The caller must debit the full quoted price exactly once. */
        public Roster serviceAll(long id,TechnologyCatalog technology){
            Roster updated=this;
            for(Service type:Service.values())
                updated=updated.service(id,type,technology);
            return updated;
        }
        public long cost(long id,Service service,TechnologyCatalog technology){
            Vessel vessel=get(id);
            Battle.Ship full=fresh(vessel.fitting(),technology);
            Stores current=vessel.stores();
            return switch(service){
                case REPAIR -> {
                    full.defense.restoreIntegrity(current.integrity());
                    long structure=(long)Math.ceil(Math.max(0,full.maxHull-current.hull())*18);
                    long armor=(long)Math.ceil(Math.max(0,full.maxArmor-full.defense.totalArmor())*6);
                    long shields=(long)Math.ceil(Math.max(0,full.maxShields-
                            full.defense.totalShields())*2);
                    yield Math.addExact(Math.addExact(Math.addExact(structure,armor),
                            shields),ShipSubsystems.repairCost(current.machinery()));
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
    /** New hull procurement charges for installed real machinery in addition
     * to the raw hull. Campaign research licenses are purchased separately. */
    public static long purchaseCost(Shipyard.Design design,TechnologyCatalog tech){
        if(tech==null)throw new IllegalArgumentException("Research catalog required");
        Shipyard.Report report=Shipyard.inspect(design,tech);
        if(!report.valid())
            throw new IllegalArgumentException("Unbuildable fitted hull: "+report.explanation());
        return Math.addExact(purchaseCost(design),Math.multiplyExact((long)report.industry(),2L));
    }
    public static Battle.Ship fresh(Shipyard.Design fitted,TechnologyCatalog technology){
        if(fitted==null||technology==null)throw new IllegalArgumentException("Ship fitting required");
        Battle sandbox=new Battle(0);
        sandbox.spawnWing(0,fitted,technology,1,310,335);
        return sandbox.ships().get(0);
    }
    public static Roster start(Battle.Faction faction,Shipyard.Design starter,
                               TechnologyCatalog technology){
        return start(faction,starter,technology,faction==null?0:faction.ordinal());
    }
    /** A truly new campaign chooses this seed once, then persists it in the
     * owned roster. Deterministic fixtures continue using the legacy overload. */
    public static Roster start(Battle.Faction faction,Shipyard.Design starter,
                               TechnologyCatalog technology,long namingSeed){
        if(faction==null||starter==null||starter.faction!=faction)
            throw new IllegalArgumentException("Starter faction mismatch");
        ArrayList<Vessel> vessels=new ArrayList<>();
        for(int i=1;i<=4;i++)
            vessels.add(new Vessel(VesselIdentifiers.commissioned(namingSeed,i),
                    FleetNames.unique(FleetNames.Style.NAVAL,namingSeed,i,
                            vessels.stream().map(Vessel::name).toList()),
                    starter,true,Stores.capture(fresh(starter,technology))));
        return new Roster(faction,FleetNames.Style.NAVAL,5,vessels,namingSeed,
                CrewChatter.Tone.MIXED);
    }
}
