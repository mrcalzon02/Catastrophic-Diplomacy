package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitted LNK sends historical TRK fixes, never sensor channels or weapon locks.
 * A sender snapshots its genuinely acquired track and physical transmit pose.
 * Receivers may retrieve only packets whose photons have had time to arrive.
 * Fixed 32 target slots x 32 transmissions: no unbounded network event graph.
 */
public final class SensorTrackRelayBuffer {
    public static final int MAX_CONTACTS=32, HISTORY=32;
    private final Ring[] rings=new Ring[MAX_CONTACTS];
    private long signature=0xcbf29ce484222325L;
    public long fingerprint(){return signature;}
    private void mix(long part){signature=(signature^part)*0x100000001b3L;}
    public record Packet(int targetId,double x,double y,double vx,double vy,
                         long observedTick,long sentTick,double senderX,
                         double senderY,long receivedTick) {
        public Packet{
            if(targetId<=0||observedTick<0||sentTick<observedTick||
                    receivedTick<sentTick||!Double.isFinite(x)||
                    !Double.isFinite(y)||!Double.isFinite(vx)||
                    !Double.isFinite(vy)||!Double.isFinite(senderX)||
                    !Double.isFinite(senderY))
                throw new IllegalArgumentException("Impossible tracking transmission");
        }
    }
    private static final class Ring {
        final int id;
        final Packet[] reports=new Packet[HISTORY];
        int next,count;
        long newestSent;
        // Number of backwards timestamp edges in the retained ring. This
        // returns to zero automatically when an out-of-order report expires.
        int backwardsEdges;
        Ring(int targetId){id=targetId;}
        void put(Packet report){
            if(count==HISTORY&&reports[next].sentTick()>
                    reports[(next+1)%HISTORY].sentTick())
                backwardsEdges--;
            if(count>0&&reports[(next-1+HISTORY)%HISTORY].sentTick()>
                    report.sentTick())
                backwardsEdges++;
            reports[next]=report;
            next=(next+1)%HISTORY;
            count=StrictMath.min(HISTORY,count+1);
            newestSent=report.sentTick();
        }
        Packet delivered(long now,double receiverX,double receiverY){
            int skip=0;
            if(backwardsEdges==0&&count>0&&
                    reports[(next-1+HISTORY)%HISTORY].sentTick()>now){
                // Only timestamps are ordered: light travel time is NOT,
                // because a ship changes position between transmissions.
                // Skip reports not yet sent, but examine every older
                // candidate until the first physically arrived photon.
                int oldest=(next-count+HISTORY)%HISTORY;
                int lo=0,hi=count;
                while(lo<hi){
                    int mid=(lo+hi)>>>1;
                    int slot=(oldest+mid)%HISTORY;
                    if(reports[slot].sentTick()<=now)lo=mid+1;
                    else hi=mid;
                }
                skip=count-lo;
            }
            for(int i=skip;i<count;i++){
                Packet candidate=reports[(next-1-i+HISTORY)%HISTORY];
                // The subtraction is safe after this check; sent+light
                // could overflow and fabricate a future received packet.
                if(candidate.sentTick()>now)continue;
                long elapsed=now-candidate.sentTick();
                double dx=receiverX-candidate.senderX();
                double dy=receiverY-candidate.senderY();
                // Most new LNK reports are sent but still in flight.
                // Squared signal-shell exclusion avoids hypot for those;
                // exact photon chronology remains final authority.
                if(SensorLightTimeHistory.definitelyNoLinkPhoton(dx,dy,elapsed))
                    continue;
                int light=SensorLightTimeHistory.propagationTicks(
                        StrictMath.hypot(dx,dy));
                if(elapsed>=light)
                    return new Packet(candidate.targetId(),candidate.x(),
                            candidate.y(),candidate.vx(),candidate.vy(),
                            candidate.observedTick(),candidate.sentTick(),
                            candidate.senderX(),candidate.senderY(),now);
            }
            return null;
        }
    }
    public void transmit(int targetId,long sentTick,Battle.ContactTrack known,
                         int capacity,double senderX,double senderY){
        if(targetId<=0||known==null||sentTick<known.tick||
                capacity<1||capacity>MAX_CONTACTS||
                !Double.isFinite(senderX)||!Double.isFinite(senderY))
            throw new IllegalArgumentException("Invalid finite LNK sender");
        int chosen=-1,free=-1,oldest=-1;
        long oldestTime=Long.MAX_VALUE;
        for(int i=0;i<capacity;i++){
            Ring existing=rings[i];
            if(existing==null){if(free<0)free=i;continue;}
            if(existing.id==targetId){chosen=i;break;}
            if(existing.newestSent<oldestTime){
                oldestTime=existing.newestSent;oldest=i;
            }
        }
        if(chosen<0)chosen=free>=0?free:oldest;
        if(chosen<0)throw new IllegalStateException("No datalink slots");
        if(rings[chosen]==null||rings[chosen].id!=targetId)
            rings[chosen]=new Ring(targetId);
        Packet report=new Packet(targetId,known.x,known.y,known.vx,known.vy,
                known.tick,sentTick,senderX,senderY,sentTick);
        rings[chosen].put(report);
        mix(targetId);mix(sentTick);mix(known.tick);
        mix(Double.doubleToLongBits(known.x));
        mix(Double.doubleToLongBits(known.y));
        mix(Double.doubleToLongBits(known.vx));
        mix(Double.doubleToLongBits(known.vy));
        mix(Double.doubleToLongBits(senderX));
        mix(Double.doubleToLongBits(senderY));
    }
    /** Read-only; absence of a received packet never authorizes a weapon lock. */
    public Packet delivered(int targetId,long now,double receiverX,
                             double receiverY,int capacity){
        if(targetId<=0||now<0||capacity<0||capacity>MAX_CONTACTS||
                !Double.isFinite(receiverX)||!Double.isFinite(receiverY))
            throw new IllegalArgumentException("Invalid LNK reader");
        for(int i=0;i<capacity;i++){
            Ring ring=rings[i];
            if(ring!=null&&ring.id==targetId)
                return ring.delivered(now,receiverX,receiverY);
        }
        return null;
    }
    public List<Packet> deliveredAll(long now,double receiverX,
                                      double receiverY,int capacity){
        if(now<0||capacity<0||capacity>MAX_CONTACTS||
                !Double.isFinite(receiverX)||!Double.isFinite(receiverY))
            throw new IllegalArgumentException("Invalid LNK receiver");
        List<Packet> found=new ArrayList<>();
        for(int i=0;i<capacity;i++){
            Ring ring=rings[i];
            if(ring==null)continue;
            Packet report=ring.delivered(now,receiverX,receiverY);
            if(report!=null)found.add(report);
        }
        found.sort((a,b)->Integer.compare(a.targetId(),b.targetId()));
        return List.copyOf(found);
    }
}
