package diplomacy.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent owned fleet versus ships selected for a single mission.
 * Committed retreat and completed escape preserve ownership; destruction does not.
 * Every purchase, service and selection is immutable and validated atomically.
 */
public final class CampaignFleetRoster {
    private CampaignFleetRoster(){}
    public record Hull(long id,String name,String fitting,
                       double hull,double shields,double armor,double fuel,double crew,
                       int kinetic,int missiles,int torpedoes,int fighters,int drones,
                       String integrity,int missions,boolean inService) {
        public Hull {
            if(id<=0||name==null||name.isBlank()||name.length()>96||
                    fitting==null||fitting.isBlank()||fitting.length()>12000||
                    integrity==null||integrity.isBlank()||integrity.length()>6000||
                    missions<0||!Double.isFinite(hull)||!Double.isFinite(shields)||
                    !Double.isFinite(armor)||!Double.isFinite(fuel)||!Double.isFinite(crew)||
                    hull<0||shields<0||armor<0||fuel<0||crew<0||
                    kinetic<0||missiles<0||torpedoes<0||fighters<0||drones<0)
                throw new IllegalArgumentException("Invalid commissioned vessel");
        }
        public Hull returned(Battle.Ship ship){
            if(ship==null||ship.hull<=0||ship.fuel<0)
                throw new IllegalArgumentException("Cannot recover destroyed vessel");
            return new Hull(id,name,fitting,ship.hull,ship.shields,
                    ship.defense.totalArmor(),ship.fuel,ship.crewCount,
                    ship.kineticAmmo,ship.missileAmmo,ship.torpedoAmmo,
                    ship.fighterReserve,ship.droneReserve,
                    ship.defense.saveIntegrity(),missions+1,true);
        }
    }
    public record Fleet(List<Hull> owned,List<Long> deployment,long nextId) {
        public Fleet {
            if(owned==null||deployment==null||nextId<1||owned.size()>2000||deployment.size()>200)
                throw new IllegalArgumentException("Invalid fleet roster");
            owned=List.copyOf(owned);deployment=List.copyOf(deployment);
            Set<Long> seen=new HashSet<>();
            for(Hull h:owned)if(h==null||h.id()>=nextId||!seen.add(h.id()))
                throw new IllegalArgumentException("Repeated or invalid hull identity");
            Set<Long> active=new HashSet<>();
            for(Long id:deployment)
                if(id==null||!seen.contains(id)||!active.add(id))
                    throw new IllegalArgumentException("Deployment references unowned ship");
        }
        public Hull byId(long id){
            for(Hull hull:owned)if(hull.id()==id)return hull;
            throw new IllegalArgumentException("Hull is not owned");
        }
        public List<Hull> available(){return owned;}
        public List<Hull> deploying(){
            List<Hull> result=new ArrayList<>();
            for(long id:deployment)result.add(byId(id));
            return List.copyOf(result);
        }
        public Fleet select(long id,int cap){
            if(cap<1||cap>200)throw new IllegalArgumentException("Invalid mission capacity");
            Hull hull=byId(id);
            if(!hull.inService())throw new IllegalArgumentException("Ship not eligible for deployment");
            if(deployment.contains(id))return this;
            if(deployment.size()>=cap)throw new IllegalArgumentException("Mission fleet limit reached");
            List<Long> selected=new ArrayList<>(deployment);selected.add(id);
            return new Fleet(owned,selected,nextId);
        }
        public Fleet bench(long id){
            if(!deployment.contains(id))return this;
            List<Long> selected=new ArrayList<>(deployment);selected.remove(id);
            return new Fleet(owned,selected,nextId);
        }
        public Fleet replaceDeployment(List<Long> ids,int cap){
            if(ids==null||ids.size()>cap||cap<1||cap>200)
                throw new IllegalArgumentException("Invalid battle fleet size");
            Fleet changed=new Fleet(owned,List.of(),nextId);
            for(long id:ids)changed=changed.select(id,cap);
            return changed;
        }
        public Fleet commission(Hull hull){
            if(hull.id()!=nextId)throw new IllegalArgumentException("New vessel must receive next hull ID");
            List<Hull> collection=new ArrayList<>(owned);collection.add(hull);
            return new Fleet(collection,deployment,nextId+1);
        }
        public Fleet returnFromBattle(List<Battle.Ship> playerShips){
            if(playerShips==null||playerShips.size()!=deployment.size())
                throw new IllegalArgumentException("Actual deployment does not match roster");
            Map<Long,Hull> updates=new HashMap<>();
            for(int i=0;i<playerShips.size();i++){
                Battle.Ship ship=playerShips.get(i);
                if(ship==null||ship.team!=0)
                    throw new IllegalArgumentException("Untrusted battle fleet");
                long identity=deployment.get(i);
                // Withdrawing and escaped ships survive ownership unless actually
                // destroyed. Neither is credited as a battlefield survivor.
                if(ship.hull>0)updates.put(identity,byId(identity).returned(ship));
            }
            List<Hull> survivors=new ArrayList<>();
            for(Hull hull:owned){
                if(!deployment.contains(hull.id()))survivors.add(hull);
                else if(updates.containsKey(hull.id()))survivors.add(updates.get(hull.id()));
            }
            return new Fleet(survivors,List.of(),nextId);
        }
        public Fleet service(long id,Hull replacement){
            Hull previous=byId(id);
            if(replacement==null||replacement.id()!=id||
                    !previous.fitting().equals(replacement.fitting())||
                    replacement.missions()!=previous.missions())
                throw new IllegalArgumentException("Invalid depot service replacement");
            List<Hull> updated=new ArrayList<>(owned);
            updated.set(owned.indexOf(previous),replacement);
            return new Fleet(updated,deployment,nextId);
        }
    }
    public static Fleet empty(){return new Fleet(List.of(),List.of(),1);}
}
