package diplomacy.sim;

/**
 * Bounded sender-owned contact transmissions. Allied ships never synthesize
 * past scouting events from target world history. A packet can be read only
 * after its genuine scout-receipt timestamp plus physical link delay.
 *
 * At most 32 distinct targets with 32 immutable successive reports each.
 * This is a fixed per-hull memory ceiling, not a per-viewer enemy roster.
 */
public final class SensorRelayBuffer {
    public static final int MAX_TARGETS=32;
    public static final int REPORTS_PER_TARGET=32;
    private final Track[] tracks=new Track[MAX_TARGETS];
    /** Incremental identity includes real transmissions without rescanning
     * every queued packet for each LAN replay fingerprint. */
    private long replayHash=0xcbf29ce484222325L;
    public long fingerprint(){return replayHash;}
    private void hash(long part){
        replayHash=(replayHash^part)*0x100000001b3L;
    }
    private static final class Track {
        final int targetId;
        final long[] sentTicks=new long[REPORTS_PER_TARGET];
        final SensorLightTimeHistory.Observation[] data=
                new SensorLightTimeHistory.Observation[REPORTS_PER_TARGET];
        int next,size;
        long newestTick;
        // Normal simulation always transmits in order. Retain exact
        // newest-inserted lookup semantics for accepted out-of-order reports.
        boolean chronological=true;
        Track(int id){targetId=id;}
        void append(long at,SensorLightTimeHistory.Observation report){
            if(size>0&&at<newestTick)chronological=false;
            sentTicks[next]=at;data[next]=report;
            next=(next+1)%REPORTS_PER_TARGET;
            size=StrictMath.min(REPORTS_PER_TARGET,size+1);
            newestTick=at;
        }
        SensorLightTimeHistory.Observation delivered(long now,int delay){
            // Subtraction cannot overflow: validated now and delay are both
            // nonnegative, and delay is an int. Addition of sent+delay could
            // overflow near Long.MAX_VALUE and fabricate future deliveries.
            long deadline=now-(long)delay;
            int index=-1;
            if(chronological){
                // A full 32-report ring previously searched up to 32 packets
                // per allied viewer/target. Ordered reports are searchable in
                // at most six comparisons, including duplicates and wrap.
                int first=(next-size+REPORTS_PER_TARGET)%REPORTS_PER_TARGET;
                int lo=0,hi=size;
                while(lo<hi){
                    int mid=(lo+hi)>>>1;
                    int slot=(first+mid)%REPORTS_PER_TARGET;
                    if(sentTicks[slot]<=deadline)lo=mid+1;
                    else hi=mid;
                }
                if(lo>0)index=(first+lo-1)%REPORTS_PER_TARGET;
            }else{
                // The public transmitter accepts older timestamps; preserve
                // its original newest-inserted-wins semantics in that case.
                for(int i=0;i<size;i++){
                    int slot=(next-1-i+REPORTS_PER_TARGET)%REPORTS_PER_TARGET;
                    if(sentTicks[slot]<=deadline){index=slot;break;}
                }
            }
            if(index<0)return null;
            var packet=data[index];
            return new SensorLightTimeHistory.Observation(
                    packet.emittedTick(),packet.measuredTick(),now,
                    packet.channels(),packet.sample());
        }
    }
    /** Only actual arrived observations can enter a fitted scout transmitter. */
    public void transmit(int targetId,long sentTick,
            SensorLightTimeHistory.Observation received,int capacity){
        if(targetId<=0||sentTick<0||received==null||
                received.receivedTick()>sentTick||
                capacity<1||capacity>MAX_TARGETS)
            throw new IllegalArgumentException("Invalid physical sensor transmission");
        int chosen=-1,empty=-1,oldest=-1;
        long oldestTick=Long.MAX_VALUE;
        for(int i=0;i<capacity;i++){
            Track t=tracks[i];
            if(t==null){if(empty==-1)empty=i;continue;}
            if(t.targetId==targetId){chosen=i;break;}
            if(t.newestTick<oldestTick){
                oldestTick=t.newestTick;oldest=i;
            }
        }
        if(chosen<0)chosen=empty>=0?empty:oldest;
        if(chosen<0)throw new IllegalStateException("No bounded relay slot");
        Track ring=tracks[chosen];
        if(ring==null||ring.targetId!=targetId)
            tracks[chosen]=ring=new Track(targetId);
        ring.append(sentTick,received);
        hash(targetId);hash(sentTick);hash(received.emittedTick());
        hash(received.measuredTick());hash(received.channels());
        hash(Double.doubleToLongBits(received.sample().x()));
        hash(Double.doubleToLongBits(received.sample().y()));
        hash(Double.doubleToLongBits(received.sample().vx()));
        hash(Double.doubleToLongBits(received.sample().vy()));
    }
    /** Read-only physical delivery, never a weapon lock by itself. */
    public SensorLightTimeHistory.Observation delivered(
            int targetId,long now,int physicalDelay,int capacity){
        if(targetId<=0||now<0||physicalDelay<0||
                capacity<0||capacity>MAX_TARGETS)
            throw new IllegalArgumentException("Invalid datalink route");
        for(int i=0;i<capacity;i++){
            Track ring=tracks[i];
            if(ring!=null&&ring.targetId==targetId)
                return ring.delivered(now,physicalDelay);
        }
        return null;
    }
}
