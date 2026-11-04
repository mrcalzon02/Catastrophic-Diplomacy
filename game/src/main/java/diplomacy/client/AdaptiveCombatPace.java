package diplomacy.client;

import java.util.Arrays;

/**
 * Presentation-only adaptive speed governor. Never modifies the 30 Hz Battle
 * timestep, fuel consumption or authoritative simulation state.
 *
 * Uses actual frame-to-frame elapsed time rather than CPU submission time
 * alone. A GPU-bound trace is reported, not concealed by lowering graphics
 * quality or by misreporting the effective speed.
 */
final class AdaptiveCombatPace {
    private static final double TARGET_FRAME_MS=1000.0/30.0;
    private static final double SLOW_FRAME_MS=36.0;
    private static final double RECOVER_FRAME_MS=28.0;
    private final double[] elapsedMs=new double[180];
    private int samples;
    private double elapsedSeconds,simulationNs,frameCpuNs;
    private double stableSeconds,wallP95Ms,simMeanMs,cpuMeanMs;
    private float requested=CombatPace.DEFAULT,effective=CombatPace.DEFAULT;
    private boolean enabled=true;
    private String reason="READY";
    private long transitions;
    float requested(){return requested;}
    float effective(){return effective;}
    boolean enabled(){return enabled;}
    double p95Ms(){return wallP95Ms;}
    double meanSimMs(){return simMeanMs;}
    double meanCpuMs(){return cpuMeanMs;}
    String reason(){return reason;}
    long transitions(){return transitions;}
    static double targetFrameMs(){return TARGET_FRAME_MS;}

    void request(float speed){
        // An explicit speed-up takes effect immediately when the governor was
        // not already holding the prior request below its cap.
        boolean previouslyAtCap=effective>=requested-.001f;
        requested=CombatPace.clamp(speed);
        if(previouslyAtCap||effective>requested||!enabled)effective=requested;
        if(!enabled)reason="MANUAL";
        else if(effective==requested)reason="REQUESTED";
        stableSeconds=0;
        clearWindow();
    }
    void setEnabled(boolean on){
        if(enabled==on)return;
        enabled=on;
        if(!on){effective=requested;reason="MANUAL";}
        else reason="MONITORING";
        stableSeconds=0;
        clearWindow();
    }
    private void clearWindow(){
        samples=0;elapsedSeconds=0;simulationNs=0;frameCpuNs=0;
    }
    /** Returns true exactly when the *effective* speed changed. */
    boolean frame(double realDeltaSeconds,long cpuFrameNs,long simFrameNs,boolean active){
        if(!active || !enabled){
            clearWindow();
            stableSeconds=0;
            return false;
        }
        if(!Double.isFinite(realDeltaSeconds)||realDeltaSeconds<=0||
                cpuFrameNs<0||simFrameNs<0||simFrameNs>cpuFrameNs)
            return false;
        double frameMs=realDeltaSeconds*1000.0;
        if(samples==elapsedMs.length) evaluate();
        elapsedMs[samples++]=frameMs;
        elapsedSeconds+=realDeltaSeconds;
        frameCpuNs+=cpuFrameNs;
        simulationNs+=simFrameNs;
        if(elapsedSeconds>=2.0&&samples>=12)return evaluate();
        return false;
    }
    private boolean evaluate(){
        if(samples==0)return false;
        double windowSeconds=elapsedSeconds;
        double[] sorted=Arrays.copyOf(elapsedMs,samples);
        Arrays.sort(sorted);
        wallP95Ms=sorted[Math.max(0,(int)Math.ceil(sorted.length*.95)-1)];
        simMeanMs=simulationNs/1_000_000.0/samples;
        cpuMeanMs=frameCpuNs/1_000_000.0/samples;
        double simFraction=frameCpuNs==0?0:simulationNs/frameCpuNs;
        clearWindow();
        if(wallP95Ms>SLOW_FRAME_MS){
            stableSeconds=0;
            if(simFraction>=.20 || simMeanMs>=6.0){
                float slower=CombatPace.slower(effective);
                if(slower<effective){
                    effective=slower;
                    reason="SIMULATION PRESSURE";
                    transitions++;
                    return true;
                }
                reason="MINIMUM SPEED / SIMULATION PRESSURE";
            }else{
                reason="RENDER OR DRIVER BOUND";
            }
        }else if(wallP95Ms<RECOVER_FRAME_MS && simMeanMs<14.0){
            stableSeconds+=windowSeconds;
            if(stableSeconds>=6.0 && effective<requested){
                effective=Math.min(requested,CombatPace.faster(effective));
                stableSeconds=0;
                reason="RECOVERED";
                transitions++;
                return true;
            }
            reason=effective<requested?"RECOVERY MONITORING":"TARGET MET";
        }else{
            stableSeconds=0;
            reason="MONITORING";
        }
        return false;
    }
}
