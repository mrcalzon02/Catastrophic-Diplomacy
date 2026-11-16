import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

public final class PairedCpuProfile {
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static final int TICKS = 480;
    private record Build(Object catalog, Method create, Method step, Method fingerprint, Method observe) {
        static Build load(String classes, String hardpoints, String technology) throws Exception {
            URL[] urls = new URL[3];
            String[] paths = {classes, hardpoints, technology};
            for (int i = 0; i < paths.length; i++) urls[i] = Path.of(paths[i]).toUri().toURL();
            var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            var catalogType = loader.loadClass("diplomacy.tech.TechnologyCatalog");
            var battle = loader.loadClass("diplomacy.sim.Battle");
            return new Build(catalogType.getMethod("loadDefault").invoke(null),
                    loader.loadClass("diplomacy.sim.DemonstrationBattle").getMethod("create", catalogType),
                    battle.getMethod("step"), battle.getMethod("fingerprint"),
                    battle.getMethod("configureStepPhaseObserver", Consumer.class));
        }
    }
    private static final class PhaseTimer implements Consumer<Object> {
        final long[] duration = new long[6];
        int previous = -1;
        long start;
        @Override public void accept(Object phase) {
            int ordinal = ((Enum<?>) phase).ordinal();
            long now = CPU.getCurrentThreadCpuTime();
            if (previous >= 0) duration[previous] = now - start;
            previous = ordinal == duration.length ? -1 : ordinal;
            start = now;
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 6)
            throw new IllegalArgumentException("baseClasses candidateClasses hardpointRoot technologyRoot outputCsv clear|asteroids");
        if (!args[5].equals("clear") && !args[5].equals("asteroids"))
            throw new IllegalArgumentException("Expected clear or asteroids");
        CPU.setThreadCpuTimeEnabled(true);
        Build[] build = {Build.load(args[0], args[2], args[3]), Build.load(args[1], args[2], args[3])};
        try (var out = new PrintWriter(args[4])) {
            out.print("pair,tick,baseCpuNs,candidateCpuNs,baseWallNs,candidateWallNs,fingerprint");
            for (String side : new String[]{"base", "candidate"})
                for (String phase : new String[]{"Preparation","Sensors","Captains","Support","Projectiles","CraftCleanup"})
                    out.print("," + side + phase + "CpuNs");
            out.println();
            for (int pair = 0; pair < 5; pair++) {
                Object[] battle = {build[0].create.invoke(null, build[0].catalog),
                        build[1].create.invoke(null, build[1].catalog)};
                if (args[5].equals("asteroids")) {
                    for (Object current : battle) {
                        var loader = current.getClass().getClassLoader();
                        var weather = loader.loadClass("diplomacy.sim.SpaceWeather");
                        var preset = weather.getMethod("forLevel", int.class).invoke(null, 1);
                        current.getClass().getMethod("configureWeather", preset.getClass()).invoke(current, preset);
                    }
                }
                PhaseTimer[] timer = {new PhaseTimer(), new PhaseTimer()};
                for (int side = 0; side < 2; side++)
                    build[side].observe.invoke(battle[side], timer[side]);
                long[] fp = new long[2], wall = new long[2], cpu = new long[2];
                long[][] hist = new long[2][TICKS];
                for (int i = 0; i < TICKS; i++) {
                    for (int turn = 0; turn < 2; turn++) {
                        int side = (pair + i + turn) % 2;
                        long t = CPU.getCurrentThreadCpuTime(), w = System.nanoTime();
                        build[side].step.invoke(battle[side]);
                        wall[side] = System.nanoTime() - w;
                        hist[side][i] = cpu[side] = CPU.getCurrentThreadCpuTime() - t;
                        fp[side] = (long)build[side].fingerprint.invoke(battle[side]);
                    }
                    if (fp[0] != fp[1]) throw new AssertionError("Battle diverged at tick " + (i + 1));
                    out.printf("%d,%d,%d,%d,%d,%d,%x", pair, i + 1, cpu[0], cpu[1], wall[0], wall[1], fp[0]);
                    for (PhaseTimer each : timer)
                        for (long duration : each.duration) out.print("," + duration);
                    out.println();
                }
                out.flush();
                for (int side = 0; side < 2; side++) {
                    Arrays.sort(hist[side]);
                    System.out.printf("pair=%d side=%d mean=%.4f p50=%.4f p95=%.4f p99=%.4f fp=%x%n",
                            pair, side, Arrays.stream(hist[side]).average().orElseThrow()/1e6,
                            hist[side][239]/1e6, hist[side][455]/1e6, hist[side][475]/1e6, fp[side]);
                }
            }
        }
    }
}
