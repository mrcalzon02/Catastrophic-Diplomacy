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
    public static void main(String[] args){
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
