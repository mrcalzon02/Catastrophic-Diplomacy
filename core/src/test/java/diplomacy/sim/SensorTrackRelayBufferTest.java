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
    public static void main(String[] args){
        purePacketTransit();
    }
}
