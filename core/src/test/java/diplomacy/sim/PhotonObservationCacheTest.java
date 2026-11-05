package diplomacy.sim;

import java.util.List;

/**
 * Compare the complete deterministic physics state with and without reuse of
 * immutable historical photon arrivals. No wall-clock/FPS threshold: runner
 * CPU, JIT warmup and Android emulation vary independently of correctness.
 */
public final class PhotonObservationCacheTest {
    private PhotonObservationCacheTest(){}
    private static void check(boolean actual,String reason){
        if(!actual)throw new AssertionError(reason);
    }
    private static void compare(int perSide,int steps,long seed){
        Battle cached=Battle.duel(seed,perSide,
                Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle uncached=Battle.duel(seed,perSide,
                Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        uncached.configurePhotonCacheForDiagnostic(false);
        long cachedNs=0,baselineNs=0;
        for(int i=0;i<steps;i++){
            long started=System.nanoTime();
            cached.step();
            cachedNs+=System.nanoTime()-started;
            started=System.nanoTime();
            uncached.step();
            baselineNs+=System.nanoTime()-started;
            check(cached.tick==uncached.tick,
                    "Cache changed simulation tick at "+perSide+"v"+perSide);
            check(cached.fingerprint()==uncached.fingerprint(),
                    "Cache altered deterministic battle state at tick "+cached.tick+
                    " in "+perSide+"v"+perSide);
            for(int k=0;k<cached.ships().size();k++){
                Battle.Ship a=cached.ships().get(k),b=uncached.ships().get(k);
                check(a.id==b.id&&a.lastTargetId==b.lastTargetId&&
                        a.lastAttackerId==b.lastAttackerId&&
                        a.knownTracks.size()==b.knownTracks.size(),
                        "Cached photon result changed target/track authorization on ship "+a.id);
            }
            // HUD receives a fresh post-step history. Compare those actual
            // read-only observed records, including light-time and confidence.
            if(i==steps-1||perSide<=16&&i%10==0)
                for(int team=0;team<2;team++){
                    List<Battle.TacticalContact> a=cached.tacticalPicture(team);
                    List<Battle.TacticalContact> b=uncached.tacticalPicture(team);
                    check(a.equals(b),
                            "Cache changed received tactical sensor records at tick "+
                            cached.tick+" team "+team);
                }
        }
        check(cached.photonCacheHits()>0&&cached.photonCacheMisses()>0,
                "Shared-contact and fire-control scans did not exercise cached observations");
        check(uncached.photonCacheHits()==0 &&
                uncached.photonCacheMisses()>=cached.photonCacheMisses(),
                "A/B baseline did not bypass the photon observation cache");
        System.out.println("PASS cache parity "+perSide+"v"+perSide+
                " ticks="+steps+" fingerprint="+Long.toHexString(cached.fingerprint())+
                " hits="+cached.photonCacheHits()+
                " misses="+cached.photonCacheMisses()+
                " cachedMs="+cachedNs/1_000_000.0+
                " uncachedMs="+baselineNs/1_000_000.0+
                " (timings diagnostic only)");
    }
    public static void main(String[] args){
        compare(4,45,901L);
        compare(16,35,902L);
        compare(200,6,903L);
    }
}
