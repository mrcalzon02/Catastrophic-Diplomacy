package diplomacy.client;

/**
 * Low-overhead CPU/render batching telemetry. No allocations during sample();
 * no platform-specific GPU timer extensions or arbitrary FPS assertions.
 */
final class RenderPerformanceCounters {
    static final int REPORT_INTERVAL=180;
    record Report(int samples,float meanCpuMs,float worstCpuMs,
                  float meanDrawCalls,int maximumDrawCalls,
                  float meanMaterialBinds,int maximumMaterialBinds,
                  float meanSimulationMs,float meanNonSimulationMs,
                  float meanLightingOverlayMs) {}
    private long cpuNs,drawCalls,materialBinds,simulationNs,lightingOverlayNs;
    private long peakCpuNs;
    private int samples,peakCalls,peakBindings;
    void sample(long nanos,int calls,int bindings){
        sample(nanos,0,0,calls,bindings);
    }
    /** Simulation and additive overlay are independently measured CPU phases.
     * The remaining budget includes hull shaders, weapon sprites, UI and submission;
     * it is NOT a GPU stopwatch. */
    void sample(long nanos,long simulation,long overlay,int calls,int bindings){
        if(nanos<0||simulation<0||overlay<0||simulation>nanos||overlay>nanos-simulation||
                calls<0||bindings<0)
            throw new IllegalArgumentException("Invalid render timing or counters");
        cpuNs+=nanos;
        simulationNs+=simulation;
        lightingOverlayNs+=overlay;
        drawCalls+=calls;
        materialBinds+=bindings;
        samples++;
        peakCpuNs=Math.max(peakCpuNs,nanos);
        peakCalls=Math.max(peakCalls,calls);
        peakBindings=Math.max(peakBindings,bindings);
    }
    boolean ready(){return samples>=REPORT_INTERVAL;}
    Report report(){
        if(samples==0)return new Report(0,0,0,0,0,0,0,0,0,0);
        return new Report(samples,(float)(cpuNs/(double)samples/1_000_000.0),
                (float)(peakCpuNs/1_000_000.0),drawCalls/(float)samples,
                peakCalls,materialBinds/(float)samples,peakBindings,
                (float)(simulationNs/(double)samples/1_000_000.0),
                (float)((cpuNs-simulationNs)/(double)samples/1_000_000.0),
                (float)(lightingOverlayNs/(double)samples/1_000_000.0));
    }
    void reset(){
        cpuNs=drawCalls=materialBinds=peakCpuNs=simulationNs=lightingOverlayNs=0;
        samples=peakCalls=peakBindings=0;
    }
}
