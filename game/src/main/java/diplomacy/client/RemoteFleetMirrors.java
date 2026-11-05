package diplomacy.client;

import diplomacy.sim.Battle;
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

    Battle present(RemoteShipInterpolation interpolation,boolean newSession){
        var states=interpolation.ships();
        boolean rebuild=newSession||battle==null||states.size()!=byHostId.size();
        if(!rebuild)for(var state:states){
            Battle.Ship ship=byHostId.get(state.id());
            if(ship==null||ship.team!=state.team()||
                    ship.faction!=state.faction()){
                rebuild=true;
                break;
            }
        }
        if(rebuild){
            byHostId.clear();
            battle=new Battle(0L);
            for(var state:states){
                Battle.Ship ship=battle.addShip(state.team(),state.faction(),0,0);
                byHostId.put(state.id(),ship);
            }
        }
        for(var state:states){
            Battle.Ship ship=byHostId.get(state.id());
            if(ship==null)throw new IllegalStateException("Missing authoritative ship mirror");
            RemoteShipInterpolation.Pose rendered=interpolation.pose(state.id());
            if(rendered==null)throw new IllegalStateException("Missing network render pose");
            ship.x=rendered.x();
            ship.y=rendered.y();
            ship.facing=rendered.facing();
            ship.hull=state.hull();
            ship.shields=state.shields();
            ship.armor=state.armor();
            ship.power=state.power();
            ship.fuel=state.fuel();
            ship.hullClass=diplomacy.shipyard.Shipyard.Hull.CORVETTE;
        }
        battle.tick=interpolation.tick();
        return battle;
    }

    Battle.Ship byHostId(int hostId){return byHostId.get(hostId);}
    void reset(){battle=null;byHostId.clear();}
}
