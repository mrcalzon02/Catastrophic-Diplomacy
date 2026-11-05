package diplomacy.client;

import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable render-only faction/hull buckets. Instead of inspecting every ship
 * once per (faction, hull) draw batch, walk the real battle ship list once.
 * No battle state, fixed-tick order, culling, or rendering order is changed.
 */
final class ShipInstanceBuckets {
    private final int hullCount=Shipyard.Hull.values().length;
    private final ArrayList<Battle.Ship>[] groups;

    @SuppressWarnings("unchecked")
    ShipInstanceBuckets(){
        groups=(ArrayList<Battle.Ship>[])new ArrayList<?>[
                Battle.Faction.values().length*hullCount];
        for(int i=0;i<groups.length;i++)groups[i]=new ArrayList<>();
    }
    void collect(Battle battle,Predicate<Battle.Ship> hasSourceArt){
        for(ArrayList<Battle.Ship> group:groups)group.clear();
        for(Battle.Ship ship:battle.ships()){
            if(!ship.alive())continue;
            Shipyard.Hull hull=ship.hullClass==null?Shipyard.Hull.CORVETTE:ship.hullClass;
            // Exactly one sprite availability check per live hull, as before.
            if(hasSourceArt.test(ship))continue;
            groups[ship.faction.ordinal()*hullCount+hull.ordinal()].add(ship);
        }
    }
    List<Battle.Ship> group(Battle.Faction faction,Shipyard.Hull hull){
        return groups[faction.ordinal()*hullCount+hull.ordinal()];
    }
}
