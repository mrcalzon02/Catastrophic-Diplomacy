package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic A/B for the COST of physical ship-position substeps.
 *
 * Both variants run identically sized fleets on the SAME JVM and host with
 * alternating order. Setup is outside the timer; 2 warmups and five measured
 * paired runs prevent a single cold JIT pass from becoming the verdict.
 * No release threshold is attached to performance variation.
 */
public final class PositionSubstepPerformanceDiagnostic {
    private static final int SHIPS_PER_TEAM=200;
    private static final int TICKS=600;
    private static final int WARMUP_PAIRS=2;
    private static final int MEASURED_PAIRS=5;
    private static final double[] WANTED_X=new double[TICKS];
    private static final double[] WANTED_Y=new double[TICKS];
    static {
        for(int t=0;t<TICKS;t++){
            WANTED_X[t]=42+17*StrictMath.sin(t*.029);
            WANTED_Y[t]=11*StrictMath.cos(t*.017);
        }
    }
    private PositionSubstepPerformanceDiagnostic(){}
    private record Trial(int substeps,int pair,double meanTickMs,double p95TickMs,
                         double totalMs,double fuelSpent,double positionChecksum){}

    private static Trial run(int substeps,int pair){
        Battle battle=Battle.duel(20261105L,SHIPS_PER_TEAM,
                Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        for(Battle.Ship ship:battle.ships()){
            ship.propulsion=SublightPropulsion.Drive.HIGH_ENERGY_TORCH;
            ship.engineCondition=1;
            ship.speed=120;
            ship.maxFuel=500;
            ship.fuel=500;
            ship.vx=0;
            ship.vy=0;
        }
        final int ships=battle.ships().size();
        if(ships!=SHIPS_PER_TEAM*2)throw new AssertionError("Not a 200v200 fleet");
        long[] tickDurations=new long[TICKS];
        long total=0;
        for(int tick=0;tick<TICKS;tick++){
            double requestedX=WANTED_X[tick],requestedY=WANTED_Y[tick];
            long start=System.nanoTime();
            for(Battle.Ship ship:battle.ships()){
                double teamDirection=ship.team==0?1:-1;
                SublightPropulsion.advanceSubstepped(ship,
                        teamDirection*requestedX,teamDirection*requestedY,
                        Battle.DT,0,1,substeps);
            }
            long nanos=System.nanoTime()-start;
            tickDurations[tick]=nanos;
            total+=nanos;
        }
        Arrays.sort(tickDurations);
        double fuelSpent=0,positionChecksum=0;
        for(Battle.Ship ship:battle.ships()){
            fuelSpent+=500-ship.fuel;
            positionChecksum+=ship.x*.63+ship.y*.37+
                    ship.vx*.011+ship.vy*.013;
        }
        return new Trial(substeps,pair,total/(1e6*TICKS),
                tickDurations[(int)Math.ceil(TICKS*.95)-1]/1e6,
                total/1e6,fuelSpent,positionChecksum);
    }

    private static double median(List<Double> values){
        if(values.size()!=MEASURED_PAIRS)throw new AssertionError("Missing trial");
        double[] sorted=new double[values.size()];
        for(int i=0;i<values.size();i++)sorted[i]=values.get(i);
        Arrays.sort(sorted);
        return sorted[sorted.length/2];
    }

    public static void main(String[] args)throws Exception{
        StringBuilder csv=new StringBuilder("pair,substeps,ships,ticks,mean_tick_ms,"+
                "p95_tick_ms,total_ms,fuel_spent,position_checksum\n");
        List<Double> one=new ArrayList<>(),two=new ArrayList<>();
        List<Double> oneP95=new ArrayList<>(),twoP95=new ArrayList<>();
        for(int pair=-WARMUP_PAIRS;pair<MEASURED_PAIRS;pair++){
            int first=(pair&1)==0?1:2;
            for(int n=0;n<2;n++){
                int steps=n==0?first:(3-first);
                Trial trial=run(steps,pair);
                if(pair<0)continue;
                csv.append(String.format(Locale.ROOT,
                        "%d,%d,%d,%d,%.7f,%.7f,%.5f,%.8f,%.8f%n",
                        pair,steps,SHIPS_PER_TEAM*2,TICKS,trial.meanTickMs(),
                        trial.p95TickMs(),trial.totalMs(),trial.fuelSpent(),
                        trial.positionChecksum()));
                (steps==1?one:two).add(trial.meanTickMs());
                (steps==1?oneP95:twoP95).add(trial.p95TickMs());
            }
        }
        Path out=Path.of("test-output","position-fidelity");
        Files.createDirectories(out);
        Files.writeString(out.resolve("ab-runs.csv"),csv,StandardCharsets.UTF_8);
        double median30=median(one),median60=median(two);
        System.out.printf(Locale.ROOT,
                "POSITION_SUBSTEP_AB: 200v200 ship thrust only; "+
                "30Hz median_mean_tick_ms=%.6f p95_tick_ms=%.6f; "+
                "60Hz median_mean_tick_ms=%.6f p95_tick_ms=%.6f; "+
                "cost_ratio_60_to_30=%.4f; paired_runs=%d warmup_pairs=%d%n",
                median30,median(oneP95),median60,median(twoP95),
                median60/median30,MEASURED_PAIRS,WARMUP_PAIRS);
        System.out.println("PASS bounded same-process position integration A/B; "+
                "report is a nonblocking CPU diagnostic, not rendered FPS");
        System.out.println("Evidence: "+out.toAbsolutePath());
    }
}
