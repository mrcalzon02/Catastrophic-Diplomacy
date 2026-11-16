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
    private static final class Mirror {
        final Battle.Ship ship;
        Map<Integer,String> fittings;
        Mirror(Battle.Ship ship){this.ship=ship;}
    }
    private final Map<Integer,Mirror> byHostId=new HashMap<>();
    private Battle battle;
    private long appliedTick=Long.MIN_VALUE;

    Battle present(RemoteShipInterpolation interpolation,boolean newSession){
        var states=interpolation.ships();
        long networkTick=interpolation.tick();
        boolean refresh=networkTick!=appliedTick;
        boolean rebuild=newSession||battle==null;
        if(!rebuild&&refresh)for(var state:states){
            Battle.Ship ship=byHostId(state.id());
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
                    byHostId.put(state.id(),new Mirror(ship));
                }
            }
            java.util.Iterator<Map.Entry<Integer,Mirror>> iter=
                    byHostId.entrySet().iterator();
            while(iter.hasNext()){
                var entry=iter.next();
                if(!aliveIds.contains(entry.getKey())){
                    // Battle's render registry retains hull instances; hide
                    // retired host IDs instead of reassigning survivors.
                    entry.getValue().ship.hull=0;
                    iter.remove();
                }
            }
            appliedTick=networkTick;
        }
        for(var state:states){
            Mirror mirror=byHostId.get(state.id());
            if(mirror==null)throw new IllegalStateException("Missing authoritative ship mirror");
            Battle.Ship ship=mirror.ship;
            RemoteShipInterpolation.Pose rendered=interpolation.pose(state.id());
            if(rendered==null)throw new IllegalStateException("Missing network render pose");
            ship.x=rendered.x();
            ship.y=rendered.y();
            ship.facing=rendered.facing();
            if(refresh){
                ship.maxHull=state.maxHull();ship.hull=state.hull();
                ship.maxShields=state.maxShields();ship.shields=state.shields();
                ship.maxArmor=state.maxArmor();ship.armor=state.armor();
                ship.power=state.power();
                ship.fuel=state.fuel();
                if(ship.hullClass!=state.hullClass() || !state.weaponFittings().equals(mirror.fittings)){
                    ship.weaponEmitters=emitters(state);
                    mirror.fittings=state.weaponFittings();
                }
                ship.hullClass=state.hullClass();
                ship.lastTargetId=state.lastTargetId();
            }
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

    Battle.Ship byHostId(int hostId){
        Mirror mirror=byHostId.get(hostId);
        return mirror==null?null:mirror.ship;
    }
    void reset(){battle=null;byHostId.clear();appliedTick=Long.MIN_VALUE;}

}
