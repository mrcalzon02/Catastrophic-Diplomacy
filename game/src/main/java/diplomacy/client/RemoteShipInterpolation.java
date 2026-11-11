package diplomacy.client;

import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import diplomacy.shipyard.Shipyard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display-only interpolation between authenticated, lower-frequency network
 * snapshots. The server alone owns hits, simulation ticks and real orders.
 * Tracks are keyed by authoritative ship ID, never by transient list index.
 */
final class RemoteShipInterpolation {
    record Pose(double x,double y,double facing){}
    /** Parsed once per NEW trusted snapshot, not once per GL frame. */
    record HostShip(int id,int team,Battle.Faction faction,Shipyard.Hull hullClass,
                    double hull,double maxHull,double shields,double maxShields,
                    double armor,double maxArmor,double power,double fuel,
                    int lastTargetId,Map<Integer,String> weaponFittings){}
    private static final class Track {
        final Pose from,to;
        Track(Pose first,Pose last){from=first;to=last;}
    }
    private final Map<Integer,Track> tracks=new HashMap<>();
    private List<HostShip> ships=List.of();
    List<HostShip> ships(){return ships;}
    private long tick=Long.MIN_VALUE;
    private float elapsed,duration;

    long tick(){return tick;}
    int tracked(){return tracks.size();}

    /** Identical snapshots leave the current presentation clock untouched.
     * A reset/out-of-order session start snaps to new identity safely. */
    void observe(long nextTick,List<BattleConnection.ShipEntity> shipRows){
        if(shipRows==null)throw new IllegalArgumentException("Ship rows required");
        if(nextTick==tick)return;
        boolean reset=tick==Long.MIN_VALUE||nextTick<tick;
        if(reset)tracks.clear();
        float oldFraction=duration>0?Math.min(1f,elapsed/duration):1f;
        Map<Integer,Track> next=new HashMap<>(Math.max(16,shipRows.size()*2));
        java.util.ArrayList<HostShip> parsed=new java.util.ArrayList<>(shipRows.size());
        for(BattleConnection.ShipEntity row:shipRows){
            int id=Math.toIntExact(row.id());
            Pose endpoint=new Pose(row.x(),row.y(),row.facing());
            if(id<=0||next.containsKey(id))
                throw new IllegalArgumentException("Invalid authoritative ship position");
            Track old=tracks.get(id);
            Pose start=reset||old==null?endpoint:blend(old.from,old.to,oldFraction);
            next.put(id,new Track(start,endpoint));
            parsed.add(new HostShip(id,row.team(),row.faction(),row.hullClass(),
                    row.hull(),row.maxHull(),row.shields(),row.maxShields(),
                    row.armor(),row.maxArmor(),row.power(),row.fuel(),
                    row.lastTargetId(),row.weaponFittings()));
        }
        ships=List.copyOf(parsed);
        long gap=reset?0:nextTick-tick;
        tracks.clear();
        tracks.putAll(next);
        tick=nextTick;
        duration=reset?0f:(float)Math.max(Battle.DT,Math.min(.25,gap*Battle.DT));
        elapsed=0f;
    }

    void advance(float wallSeconds){
        if(Float.isFinite(wallSeconds)&&wallSeconds>0)
            elapsed=Math.min(duration,elapsed+Math.min(.08f,wallSeconds));
    }

    Pose pose(int hostShipId){
        Track track=tracks.get(hostShipId);
        if(track==null)return null;
        return blend(track.from,track.to,
                duration<=0?1f:Math.min(1f,elapsed/duration));
    }

    private static Pose blend(Pose a,Pose b,float t){
        double f=Math.max(0,Math.min(1,t));
        double turn=Math.atan2(Math.sin(b.facing()-a.facing()),
                Math.cos(b.facing()-a.facing()));
        return new Pose(a.x()+(b.x()-a.x())*f,
                a.y()+(b.y()-a.y())*f,a.facing()+turn*f);
    }

    void reset(){
        tick=Long.MIN_VALUE;
        elapsed=duration=0;
        tracks.clear();
        ships=List.of();
    }
}
