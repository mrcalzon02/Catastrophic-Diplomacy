package diplomacy.sim;

/** Finite real transmit history and receiver light-speed delivery. */
public final class SensorRelayBufferTest {
    private SensorRelayBufferTest(){}
    private static void require(boolean pass,String why){
        if(!pass)throw new AssertionError(why);
    }
    private static SensorLightTimeHistory.Observation observed(
            long emit,long measured,long received,double x){
        var sample=new SensorLightTimeHistory.Sample(measured,x,150,0,0,true);
        return new SensorLightTimeHistory.Observation(emit,measured,received,
                Battle.CONTACT_RADAR,sample);
    }
    /** Reference FIFO search over the most recent 32 accepted transmissions.
     * Exercise wrap, duplicate epochs and out-of-order reports without
     * requiring a synthetic enemy or altering real target discovery. */
    private record Sent(long tick,double x){}
    private static void assertChronology(SensorRelayBuffer buffer,
            java.util.List<Sent> last32,long now,int delay){
        Sent expected=null;
        long deadline=now-(long)delay;
        for(int i=last32.size()-1;i>=0;i--)
            if(last32.get(i).tick()<=deadline){
                expected=last32.get(i);break;
            }
        var actual=buffer.delivered(91,now,delay,1);
        require((expected==null)==(actual==null),
                "Packet search changed physical arrival at "+now+
                        " with delay "+delay);
        if(expected!=null)
            require(actual.sample().x()==expected.x()&&
                    actual.receivedTick()==now,
                    "Relay reordered history on ring wrap/duplicate tick");
    }
    private static void assertSequence(boolean reordered){
        var sender=new SensorRelayBuffer();
        java.util.List<Sent> last32=new java.util.ArrayList<>();
        int trials=reordered?126:180;
        for(int i=0;i<trials;i++){
            long sent=reordered?800+3L*i-(i%9==0?5:0):200+i/3;
            double x=i+1;
            sender.transmit(91,sent,observed(0,0,0,x),1);
            last32.add(new Sent(sent,x));
            if(last32.size()>SensorRelayBuffer.REPORTS_PER_TARGET)
                last32.remove(0);
            for(int delay:new int[]{0,1,2,5,17,31,70})
                for(long now:new long[]{sent-1,sent,sent+1,
                        sent+delay-1,sent+delay,sent+delay+1})
                    assertChronology(sender,last32,now,delay);
        }
    }
    private static void verifyChronologicalLookup(){
        assertSequence(false);
        assertSequence(true);
        // A signed long addition could previously wrap and claim a future
        // packet had already arrived. No tick overflow may grant a relay.
        var sender=new SensorRelayBuffer();
        long latest=Long.MAX_VALUE-3;
        sender.transmit(91,latest,observed(0,0,0,1234),1);
        require(sender.delivered(91,Long.MAX_VALUE,7,1)==null,
                "Long-tick overflow fabricated a future LNK arrival");
        require(sender.delivered(91,Long.MAX_VALUE,3,1)!=null,
                "Correct boundary arrival lost near maximum tick");
        require(sender.delivered(91,0,Integer.MAX_VALUE,1)==null,
                "Delay beyond the current clock created an old report");
        System.out.println("PASS bounded chronological relay lookup, 32-slot wrap, "
                +"duplicate/out-of-order stamps and overflow-safe arrival");
    }
    public static void main(String[] args){
        verifyChronologicalLookup();
        var buffer=new SensorRelayBuffer();
        int lag=SensorLightTimeHistory.propagationTicks(1200);
        require(buffer.delivered(41,100,lag,2)==null,
                "Receiver fabricated a scouting packet before transmission");
        var first=observed(4,6,9,420);
        long initialHash=buffer.fingerprint();
        buffer.transmit(41,9,first,2);
        require(buffer.fingerprint()!=initialHash,
                "Actual sender transmission did not change replay fingerprint");
        long signed=buffer.fingerprint();
        require(buffer.delivered(41,9+lag-1,lag,2)==null,
                "Relay jumped past one-way photon travel");
        var arrived=buffer.delivered(41,9+lag,lag,2);
        require(arrived!=null&&arrived.sample().x()==420&&
                arrived.emittedTick()==4&&arrived.measuredTick()==6&&
                arrived.receivedTick()==9+lag,
                "Relay modified scouting sample or invented reception timestamps");
        require(buffer.fingerprint()==signed,
                "Read-only tactical relay inspection mutated replay state");
        var replay=new SensorRelayBuffer();
        replay.transmit(41,9,first,2);
        require(replay.fingerprint()==signed,
                "Identical scout reports generated different deterministic signatures");
        var second=observed(5,7,10,440);
        buffer.transmit(41,10,second,2);
        require(buffer.delivered(41,9+lag,lag,2).sample().x()==420,
                "New sender observation displaced the older still-in-flight packet");
        require(buffer.delivered(41,10+lag,lag,2).sample().x()==440,
                "New real sender report failed to arrive after its transmission");
        buffer.transmit(42,11,observed(6,8,11,500),2);
        buffer.transmit(43,12,observed(7,9,12,600),2);
        require(buffer.delivered(41,60,lag,2)==null,
                "A sender exceeded finite two-target sharing capacity");
        require(buffer.delivered(42,60,lag,2)!=null&&
                buffer.delivered(43,60,lag,2)!=null,
                "Finite sender capacity discarded valid latest contacts");
        require(buffer.delivered(42,60,lag,0)==null,
                "Unfitted sharing link inherited free sender report capacity");
        System.out.println("PASS bounded actual scout transmissions, in-flight history and one-way receiver delay");
    }
}
