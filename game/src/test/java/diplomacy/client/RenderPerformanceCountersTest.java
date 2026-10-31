package diplomacy.client;

/** Deterministic arithmetic regression without a graphics or audio device. */
public final class RenderPerformanceCountersTest {
    public static void main(String[] args){
        RenderPerformanceCounters m=new RenderPerformanceCounters();
        if(m.report().samples()!=0||m.report().worstCpuMs()!=0)
            throw new AssertionError("Empty sample must be zero");
        for(int i=0;i<RenderPerformanceCounters.REPORT_INTERVAL;i++)
            m.sample(i==0?20_000_000L:10_000_000L,i==0?140:20,i==0?40:5);
        if(!m.ready())throw new AssertionError("Bounded report must become ready");
        var r=m.report();
        if(r.samples()!=180||r.maximumDrawCalls()!=140||
                r.maximumMaterialBinds()!=40||Math.abs(r.worstCpuMs()-20)>0.001)
            throw new AssertionError("Peak graphics costs not captured");
        if(r.meanCpuMs()<10f||r.meanCpuMs()>11f||r.meanDrawCalls()<20f||
                r.meanMaterialBinds()<5f)throw new AssertionError("Mean costs inaccurate");
        m.reset();
        m.sample(20_000_000L,9_000_000L,3_000_000L,12,3);
        var phases=m.report();
        if(Math.abs(phases.meanSimulationMs()-9f)>.001f
                ||Math.abs(phases.meanNonSimulationMs()-11f)>.001f
                ||Math.abs(phases.meanLightingOverlayMs()-3f)>.001f)
            throw new AssertionError("Independent combat and lighting timers are incorrect");
        m.reset();
        if(m.ready()||m.report().samples()!=0)throw new AssertionError("Reset must clear window");
        boolean threw=false;
        try{m.sample(1,-1,0);}catch(IllegalArgumentException ex){threw=true;}
        if(!threw)throw new AssertionError("Impossible GPU draw counts were accepted");
        try{m.sample(5,4,2,0,0);throw new AssertionError("Overlapping phase accounting accepted");}
        catch(IllegalArgumentException correct){/* expected */}
        System.out.println("PASS: full frame vs simulation/lighting split, per-frame batching, binds and reset");
    }
}
