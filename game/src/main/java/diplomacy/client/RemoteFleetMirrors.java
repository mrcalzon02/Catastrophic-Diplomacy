package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.WeaponMuzzles;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import java.util.HashMap;
import java.util.Map;

/**
 * Stable rendering mirror of a host-authenticated snapshot fleet.
 * Mirrors retain their local sprite objects by HOST ID, not the row order.
 * All numerical properties are copied from host snapshots; only visible
 * x/y/facing are borrowed from the render-only interpolation buffer.
 */
final class RemoteFleetMirrors {
    private final Map<Integer,Battle.Ship> byHostId=new HashMap<>();
    private Battle battle;
    private long appliedTick=Long.MIN_VALUE;

    Battle present(RemoteShipInterpolation interpolation,boolean newSession){
        var states=interpolation.ships();
        long networkTick=interpolation.tick();
        boolean refresh=networkTick!=appliedTick;
        boolean rebuild=newSession||battle==null;
        if(!rebuild&&refresh)for(var state:states){
            Battle.Ship ship=byHostId.get(state.id());
            if(ship!=null&&(ship.team!=state.team()||
                    ship.faction!=state.faction())){
                // A host ID cannot silently change sides or species.
                rebuild=true;
                break;
            }
        }
        if(rebuild){
            byHostId.clear();
            battle=new Battle(0L);
            appliedTick=Long.MIN_VALUE;
            refresh=true;
        }
        if(refresh){
            java.util.Set<Integer> aliveIds=new java.util.HashSet<>();
            for(var state:states){
                aliveIds.add(state.id());
                if(!byHostId.containsKey(state.id())){
                    // Preserve every surviving ship. Creating a new vessel
                    // must not reset positions or visual sprites of its peers.
                    Battle.Ship ship=battle.addShip(state.team(),
                            state.faction(),0,0);
                    byHostId.put(state.id(),ship);
                }
            }
            java.util.Iterator<Map.Entry<Integer,Battle.Ship>> iter=
                    byHostId.entrySet().iterator();
            while(iter.hasNext()){
                var entry=iter.next();
                if(!aliveIds.contains(entry.getKey())){
                    // Battle's render registry retains hull instances; hide
                    // retired host IDs instead of reassigning survivors.
                    entry.getValue().hull=0;
                    iter.remove();
                }
            }
            appliedTick=networkTick;
        }
        for(var state:states){
            Battle.Ship ship=byHostId.get(state.id());
            if(ship==null)throw new IllegalStateException("Missing authoritative ship mirror");
            RemoteShipInterpolation.Pose rendered=interpolation.pose(state.id());
            if(rendered==null)throw new IllegalStateException("Missing network render pose");
            ship.x=rendered.x();
            ship.y=rendered.y();
            ship.facing=rendered.facing();
            ship.maxHull=state.maxHull();ship.hull=state.hull();
            ship.maxShields=state.maxShields();ship.shields=state.shields();
            ship.maxArmor=state.maxArmor();ship.armor=state.armor();
            ship.power=state.power();
            ship.fuel=state.fuel();
            ship.hullClass=state.hullClass();
            ship.lastTargetId=state.lastTargetId();
            ship.weaponEmitters=emitters(state);
        }
        battle.tick=networkTick;
        return battle;
    }

    private static java.util.List<WeaponMuzzles.Emitter> emitters(
            RemoteShipInterpolation.HostShip state){
        if(state.weaponFittings().isEmpty())return java.util.List.of();
        java.util.ArrayList<WeaponMuzzles.Emitter> result=new java.util.ArrayList<>();
        java.util.Map<Integer,Shipyard.Slot> slots=new java.util.HashMap<>();
        for(Shipyard.Slot slot:HullLayouts.slots(state.faction(),state.hullClass()))
            slots.put(slot.index(),slot);
        for(var entry:state.weaponFittings().entrySet()){
            Shipyard.Slot slot=slots.get(entry.getKey());
            WeaponMuzzles.Family family=WeaponMuzzles.family(entry.getValue());
            if(slot==null||family==null)
                throw new IllegalStateException("Host weapon fitting has no visible hull socket");
            result.add(new WeaponMuzzles.Emitter(slot.index(),family,
                    slot.x(),slot.y(),entry.getValue()));
        }
        return java.util.List.copyOf(result);
    }

    Battle.Ship byHostId(int hostId){return byHostId.get(hostId);}
    void reset(){battle=null;byHostId.clear();appliedTick=Long.MIN_VALUE;}

}
