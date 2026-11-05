package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/** An optional profiler must never alter the deterministic physical battle. */
public final class BattleStepPhaseObserverTest {
    private BattleStepPhaseObserverTest(){}
    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    public static void main(String[] args){
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
    }
}
