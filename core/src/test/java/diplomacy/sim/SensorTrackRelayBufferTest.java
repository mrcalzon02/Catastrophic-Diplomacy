package diplomacy.sim;

/** Dedicated fitted LNK: track memory travels as packets, never teleportation. */
public final class SensorTrackRelayBufferTest {
    private static void require(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    private static Battle.ContactTrack track(long observed,double x){
        return new Battle.ContactTrack(new SensorLightTimeHistory.Sample(
                observed,x,100,3,1,true));
    }
    private static void purePacketTransit(){
        SensorTrackRelayBuffer sender=new SensorTrackRelayBuffer();
        double fromX=100,receiverX=450;
        int transit=SensorLightTimeHistory.propagationTicks(350);
        require(sender.delivered(71,12,receiverX,100,2)==null,
                "Absent physical LNK packet was manufactured by receiver");
        sender.transmit(71,10,track(3,650),2,fromX,100);
        long fingerprint=sender.fingerprint();
        require(sender.delivered(71,10+transit-1,receiverX,100,2)==null,
                "Historical TRK fix crossed fleet datalink faster than light");
        var first=sender.delivered(71,10+transit,receiverX,100,2);
        require(first!=null&&first.x()==650&&first.observedTick()==3&&
                first.sentTick()==10&&first.receivedTick()==10+transit,
                "LNK report lost physical observation/transmission/receipt chronology");
        require(sender.fingerprint()==fingerprint,
                "Read-only LNK receiver mutated authoritative replay state");
        sender.transmit(71,11,track(4,654),2,fromX,100);
        require(sender.delivered(71,10+transit,receiverX,100,2).x()==650,
                "New TRK fix replaced an older packet that had arrived first");
        require(sender.delivered(71,11+transit,receiverX,100,2).x()==654,
                "Subsequent TRK fix never arrived through the physical link");
        sender.transmit(72,12,track(5,700),2,fromX,100);
        sender.transmit(73,13,track(6,850),2,fromX,100);
        require(sender.delivered(71,50,receiverX,100,2)==null&&
                sender.deliveredAll(50,receiverX,100,2).size()==2,
                "Installed LNK capacity failed to bound tracked targets");
        require(sender.delivered(72,50,receiverX,100,0)==null,
                "An unfitted datalink acquired historical packets for free");
        SensorTrackRelayBuffer identical=new SensorTrackRelayBuffer();
        identical.transmit(71,10,track(3,650),2,fromX,100);
        require(identical.fingerprint()==fingerprint,
                "Equivalent transmissions produced different replay hashes");
        System.out.println("PASS finite actual LNK transmissions, order, replay and photon arrival");
    }
    private record Sent(long tick,double x,double sx,double sy){}
    private static void verifyAgainstReference(SensorTrackRelayBuffer buffer,
            java.util.List<Sent> retained,long now,double rx,double ry){
        Sent expected=null;
        for(int j=retained.size()-1;j>=0;j--){
            Sent row=retained.get(j);
            if(row.tick()>now)continue;
            int transit=SensorLightTimeHistory.propagationTicks(
                    StrictMath.hypot(rx-row.sx(),ry-row.sy()));
            if(now-row.tick()>=transit){expected=row;break;}
        }
        long before=buffer.fingerprint();
        var actual=buffer.delivered(71,now,rx,ry,1);
        var all=buffer.deliveredAll(now,rx,ry,1);
        require((expected==null)==(actual==null)&&
                all.size()==(expected==null?0:1),
                "LNK changed newest physically arrived packet at tick "+now);
        if(expected!=null)
            require(actual.sentTick()==expected.tick()&&
                    actual.x()==expected.x()&&
                    actual.senderX()==expected.sx()&&
                    actual.senderY()==expected.sy()&&
                    actual.receivedTick()==now&&all.get(0).equals(actual),
                    "LNK reordered a moving sender's delayed track packets");
        require(buffer.fingerprint()==before,
                "Read-only indexed LNK inspection changed replay identity");
    }
    private static void chronologicalAndMovingParity(){
        int queries=0;
        for(boolean outOfOrder:new boolean[]{false,true}){
            SensorTrackRelayBuffer buffer=new SensorTrackRelayBuffer();
            java.util.List<Sent> retained=new java.util.ArrayList<>();
            for(int i=0;i<120;i++){
                long tick=outOfOrder?500+3L*i-(i%7==0?5:0):200+i/3;
                double x=100+i,sx=(i%5)*350.0,sy=(i%3)*220.0;
                buffer.transmit(71,tick,track(0,x),1,sx,sy);
                retained.add(new Sent(tick,x,sx,sy));
                if(retained.size()>SensorTrackRelayBuffer.HISTORY)
                    retained.remove(0);
                for(double rx:new double[]{0,200,900,1800})
                    for(long now:new long[]{tick-1,tick,tick+3,
                            tick+8,tick+21,tick+45}){
                        verifyAgainstReference(buffer,retained,now,rx,500);
                        queries++;
                    }
            }
        }
        // Moving transmitter: a newer distant report can remain in flight
        // even though an older report from a nearby pose has arrived.
        SensorTrackRelayBuffer moving=new SensorTrackRelayBuffer();
        moving.transmit(71,30,track(0,11),1,0,0);
        moving.transmit(71,31,track(0,22),1,1800,0);
        require(moving.delivered(71,32,0,0,1).x==11,
                "LNK assumed physical arrival order matches send order");
        // Past Long.MAX_VALUE the old sent+light would overflow and
        // incorrectly make a future distant packet appear delivered.
        SensorTrackRelayBuffer high=new SensorTrackRelayBuffer();
        high.transmit(71,Long.MAX_VALUE-2,track(0,33),1,0,0);
        require(high.delivered(71,Long.MAX_VALUE,1800,0,1)==null,
                "Overflow fabricated a high-tick LNK packet arrival");
        require(high.delivered(71,Long.MAX_VALUE,0,0,1)!=null,
                "Zero-distance LNK packet failed near maximum tick");
        System.out.println("PASS LNK moving-sender chronology "+queries+
                " seeded reference reads, ring wrap, reorder and clock overflow");
    }
    public static void main(String[] args){
        purePacketTransit();
        chronologicalAndMovingParity();
    }
}
