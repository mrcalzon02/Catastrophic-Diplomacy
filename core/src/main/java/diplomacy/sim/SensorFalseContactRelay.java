package diplomacy.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One scout's bounded historical FALSE-HYPOTHESIS radio transmissions.
 * Source observations and emitter photons precede sentTick; receivers cannot
 * obtain packets until a real sender-to-receiver light hop has elapsed.
 * This buffer does NOT contain combat targets or authorize weapon locks.
 *
 * Fixed 32 independent phantom identities x 32 reports. Inactive tombstones
 * are transmitted too: a later observed shutdown does not erase old photons,
 * but does eventually replace the false hypothesis after link propagation.
 */
public final class SensorFalseContactRelay {
    public static final int MAX_HYPOTHESES=32,REPORTS_PER_HYPOTHESIS=32;
    public static final int MAX_REPORT_AGE_TICKS=45;
    public record Packet(int emitterId,int index,boolean active,
                         double x,double y,double vx,double vy,
                         long observedTick,long sentTick,
                         double senderX,double senderY,long receivedTick){
        public Packet {
            if(emitterId<=0||index<0||index>2||observedTick<0||
                    sentTick<observedTick||receivedTick<sentTick||
                    !Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(senderX)||!Double.isFinite(senderY))
                throw new IllegalArgumentException("Invalid false-contact chronology");
        }
    }
    private static final class Ring {
        final int emitter,index;
        final Packet[] reports=new Packet[REPORTS_PER_HYPOTHESIS];
        int next,count;
        long lastSent;
        Ring(int emitter,int index){this.emitter=emitter;this.index=index;}
        void append(Packet packet){
            reports[next]=packet;
            next=(next+1)%REPORTS_PER_HYPOTHESIS;
            count=StrictMath.min(REPORTS_PER_HYPOTHESIS,count+1);
            lastSent=packet.sentTick();
        }
        Packet received(long now,double receiverX,double receiverY){
            for(int i=0;i<count;i++){
                Packet entry=reports[(next-1-i+REPORTS_PER_HYPOTHESIS)%
                        REPORTS_PER_HYPOTHESIS];
                int transit=SensorLightTimeHistory.propagationTicks(
                        StrictMath.hypot(receiverX-entry.senderX(),
                                receiverY-entry.senderY()));
                if(entry.sentTick()+transit>now)continue;
                if(!entry.active()||now-entry.sentTick()>MAX_REPORT_AGE_TICKS)
                    return null;
                return new Packet(entry.emitterId(),entry.index(),true,
                        entry.x(),entry.y(),entry.vx(),entry.vy(),
                        entry.observedTick(),entry.sentTick(),entry.senderX(),
                        entry.senderY(),now);
            }
            return null;
        }
    }
    private final Ring[] rings=new Ring[MAX_HYPOTHESES];
    private long signature=0xcbf29ce484222325L;
    public long fingerprint(){return signature;}
    private void mix(long value){signature=(signature^value)*0x100000001b3L;}
    /** The scout must have ALREADY received the measured emitter profile.
     * Active phantoms and inactive shutdown tombstones cost the same slot. */
    public void transmit(int emitterId,int index,boolean active,
            SensorLightTimeHistory.Sample measured,long sentTick,
            double senderX,double senderY,double hullScale,int capacity){
        if(measured==null||measured.tick()>sentTick||
                capacity<1||capacity>MAX_HYPOTHESES||
                !Double.isFinite(hullScale)||hullScale<=0)
            throw new IllegalArgumentException("Unobserved false-contact transmission");
        double x=active?DeceptionProjection.ghostX(
                emitterId,measured,hullScale,index):measured.x();
        double y=active?DeceptionProjection.ghostY(
                emitterId,measured,hullScale,index):measured.y();
        Packet packet=new Packet(emitterId,index,active,x,y,
                measured.vx(),measured.vy(),measured.tick(),sentTick,
                senderX,senderY,sentTick);
        int chosen=-1,empty=-1,oldest=-1;
        long old=Long.MAX_VALUE;
        for(int i=0;i<capacity;i++){
            Ring ring=rings[i];
            if(ring==null){if(empty<0)empty=i;continue;}
            if(ring.emitter==emitterId&&ring.index==index){chosen=i;break;}
            if(ring.lastSent<old){old=ring.lastSent;oldest=i;}
        }
        if(chosen<0)chosen=empty>=0?empty:oldest;
        if(chosen<0)throw new IllegalStateException("No false-contact radio slot");
        Ring ring=rings[chosen];
        if(ring==null||ring.emitter!=emitterId||ring.index!=index)
            rings[chosen]=ring=new Ring(emitterId,index);
        ring.append(packet);
        mix(emitterId);mix(index);mix(active?1:0);
        mix(measured.tick());mix(sentTick);
        mix(Double.doubleToLongBits(x));mix(Double.doubleToLongBits(y));
        mix(Double.doubleToLongBits(measured.vx()));
        mix(Double.doubleToLongBits(measured.vy()));
        mix(Double.doubleToLongBits(senderX));
        mix(Double.doubleToLongBits(senderY));
    }
    /** A read never changes replay state or creates a new observation. */
    public List<Packet> delivered(long now,double receiverX,
                                  double receiverY,int capacity){
        if(now<0||capacity<0||capacity>MAX_HYPOTHESES||
                !Double.isFinite(receiverX)||!Double.isFinite(receiverY))
            throw new IllegalArgumentException("Invalid false-contact radio read");
        List<Packet> visible=new ArrayList<>();
        for(int i=0;i<capacity;i++){
            Ring ring=rings[i];
            if(ring==null)continue;
            Packet report=ring.received(now,receiverX,receiverY);
            if(report!=null)visible.add(report);
        }
        visible.sort(Comparator.comparingInt(Packet::emitterId)
                .thenComparingInt(Packet::index));
        return List.copyOf(visible);
    }
}
