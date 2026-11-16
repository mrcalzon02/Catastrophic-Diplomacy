import diplomacy.server.BattleServer;
import diplomacy.net.BattleConnection;
import diplomacy.sim.BattleOrders;
import java.lang.management.*;
import java.util.*;

public class LanProbe {
    public static void main(String[] args) throws Exception {
        int count = Integer.parseInt(args[0]);
        long ticks = Long.parseLong(args[1]);
        try (BattleServer host = BattleServer.openLan("127.0.0.1", 0, 2479L, count);
             BattleConnection a = BattleConnection.openLan("127.0.0.1", host.port(), false);
             BattleConnection b = BattleConnection.openLan("127.0.0.1", host.port(), false)) {
            BattleConnection[] clients = {a, b};
            if (a.team != 0 || b.team != 1) throw new AssertionError("seats");
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            long[] readers = Arrays.stream(mx.getAllThreadIds()).filter(id -> {
                var info = mx.getThreadInfo(id);
                return info != null && info.getThreadName().equals("diplomacy-authoritative-events");
            }).toArray();
            if (readers.length != 2) throw new AssertionError("reader ids");
            long[] last = {-1, -1}, seen = {0, 0}, rows = {0, 0}, skipped = {0, 0};
            long[] prevNs = {0, 0}, cpuStart = {0, 0}, cpuEnd = {0, 0};
            boolean[] finished = {false, false};
            List<Map<Long,Long>> hashes = List.of(new HashMap<>(), new HashMap<>());
            long startTick = 90, finishTick = startTick + ticks;
            long deadline = System.nanoTime() + 90_000_000_000L;
            while (!finished[0] || !finished[1]) {
                if (System.nanoTime() > deadline) throw new AssertionError("deadline, ticks=" + Arrays.toString(last));
                for (int i=0; i<2; i++) {
                    var client = clients[i];
                    if (!client.connected()) throw new AssertionError(client.disconnectStatus());
                    var s = client.snapshot();
                    if (s == null || s.tick() <= last[i] || finished[i]) continue;
                    long now = System.nanoTime();
                    if (s.tick() >= startTick && s.tick() % 3 == 0) {
                        if (seen[i] == 0) {
                            cpuStart[i] = mx.getThreadCpuTime(readers[i]);
                        } else {
                            long gap = Math.max(0L, (s.tick() - last[i])/3 - 1);
                            skipped[i] += gap;
                            System.out.printf(Locale.ROOT, "ROW,%d,%d,%d,%.3f,%d,%d%n",i,s.tick(),s.simulationHash(),(now-prevNs[i])/1e6,s.decodedEntities().size(),gap);
                        }
                        hashes.get(i).put(s.tick(), s.simulationHash());
                        prevNs[i] = now;
                        rows[i] += s.decodedEntities().size();
                        seen[i]++;
                        if (s.tick() >= finishTick) {
                            cpuEnd[i] = mx.getThreadCpuTime(readers[i]);
                            finished[i] = true;
                        }
                    }
                    last[i] = s.tick();
                }
                Thread.sleep(1);
            }
            int common = 0;
            for (var e : hashes.get(0).entrySet()) {
                var other = hashes.get(1).get(e.getKey());
                if (other == null) continue;
                if (!other.equals(e.getValue())) throw new AssertionError("authoritative hashes diverged");
                common++;
            }
            if (common == 0) throw new AssertionError("no matched snapshots");
            long t = System.nanoTime();
            long seqA = a.order(BattleOrders.PointDefense.BALANCED,BattleOrders.Position.KEEP_MOVING);
            long seqB = b.order(BattleOrders.PointDefense.BALANCED,BattleOrders.Position.KEEP_MOVING);
            while(a.acknowledged()!=seqA || b.acknowledged()!=seqB) {
                if(System.nanoTime()-t>5_000_000_000L) throw new AssertionError("acks");
                Thread.sleep(1);
            }
            System.out.printf(Locale.ROOT,"PASS,%d,%d,%d,%d,%d,%.3f,%d,%.3f,%.3f,%d%n",
                2*count, seen[0]+seen[1], common, rows[0]+rows[1], skipped[0]+skipped[1],
                (cpuEnd[0]-cpuStart[0]+cpuEnd[1]-cpuStart[1])/1e6, host.bytesQueued(),
                host.maxTickNanos()/1e6, (System.nanoTime()-t)/1e6, host.tick());
        }
    }
}
