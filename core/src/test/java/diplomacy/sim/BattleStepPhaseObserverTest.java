package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;
import diplomacy.tech.TechnologyCatalog;

/** An optional profiler must never alter the deterministic physical battle. */
public final class BattleStepPhaseObserverTest {
    private BattleStepPhaseObserverTest(){}
    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    public static void main(String[] args)throws Exception{
        Battle traced=Battle.duel(7721L,12,
                Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle control=Battle.duel(7721L,12,
                Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        List<Battle.StepPhase> seen=new ArrayList<>();
        traced.configureStepPhaseObserver(seen::add);
        Battle.StepPhase[] expected=Battle.StepPhase.values();
        for(int tick=0;tick<40;tick++){
            traced.step();
            control.step();
            check(traced.fingerprint()==control.fingerprint(),
                    "Profiler changed combat at tick "+traced.tick);
            for(int i=0;i<expected.length;i++)
                check(seen.get(tick*expected.length+i)==expected[i],
                        "Unexpected performance phase at tick "+traced.tick+
                        " index "+i);
        }
        check(seen.size()==40*expected.length,
                "Profiler dropped/duplicated a phase");
        boolean refused=false;
        try{traced.configureStepPhaseObserver(x->{});}
        catch(IllegalStateException expectedFailure){refused=true;}
        check(refused,"Profiler must not be hot-installed after tick zero");
        System.out.println("PASS diagnostic-only ordered combat phases and "+
                "40-tick deterministic parity");

        // Same real 400-hull fitted fixture as the signed Android verifier.
        // Measure each subphase after the first 20 warmup ticks; no threshold
        // uses runner speed as a release blocker.
        Battle mass=DemonstrationBattle.create(TechnologyCatalog.loadDefault());
        final class PhaseClock implements java.util.function.Consumer<Battle.StepPhase>{
            final long[] nanos=new long[Battle.StepPhase.values().length];
            Battle.StepPhase previous;
            long startNs;
            @Override public void accept(Battle.StepPhase phase){
                long now=System.nanoTime();
                if(previous!=null&&mass.tick>20)
                    nanos[previous.ordinal()]+=now-startNs;
                previous=phase==Battle.StepPhase.END?null:phase;
                startNs=now;
            }
        }
        PhaseClock clock=new PhaseClock();
        mass.configureStepPhaseObserver(clock);
        for(int i=0;i<90&&!mass.finished();i++)mass.step();
        check(mass.ships().size()==400&&mass.tick>=65,
                "200v200 benchmark must exercise a substantial real battle");
        double sampledTicks=mass.tick-20.0;
        StringBuilder line=new StringBuilder("PASS diagnostic 200v200 phased combat ");
        for(Battle.StepPhase phase:Battle.StepPhase.values()){
            if(phase==Battle.StepPhase.END)continue;
            line.append(phase.name()).append("MsPerTick=")
                    .append(clock.nanos[phase.ordinal()]/(1_000_000.0*sampledTicks))
                    .append(' ');
        }
        line.append("ticks=").append(mass.tick)
                .append(" fingerprint=").append(Long.toHexString(mass.fingerprint()))
                .append(" (JVM timing diagnostic only)");
        System.out.println(line);
    }
}
