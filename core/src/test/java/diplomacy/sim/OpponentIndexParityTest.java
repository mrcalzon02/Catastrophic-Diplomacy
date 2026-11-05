package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;

/** Real 200v200 A/B against the exact original full-fleet traversal. */
public final class OpponentIndexParityTest {
    private OpponentIndexParityTest(){}
    private static void check(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Battle indexed=DemonstrationBattle.create(catalog);
        Battle fullScan=DemonstrationBattle.create(catalog);
        fullScan.configureOpponentIndexForDiagnostic(false);
        int steps=90;
        long indexedNs=0,fullNs=0;
        for(int i=0;i<steps;i++){
            long firstElapsed,secondElapsed,started;
            // Alternate execution order to counter JIT/CPU-frequency bias.
            if((i&1)==0){
                started=System.nanoTime();indexed.step();
                firstElapsed=System.nanoTime()-started;
                started=System.nanoTime();fullScan.step();
                secondElapsed=System.nanoTime()-started;
            }else{
                started=System.nanoTime();fullScan.step();
                secondElapsed=System.nanoTime()-started;
                started=System.nanoTime();indexed.step();
                firstElapsed=System.nanoTime()-started;
            }
            if(i>=20){indexedNs+=firstElapsed;fullNs+=secondElapsed;}
            check(indexed.tick==fullScan.tick,
                    "Indexed traversal changed combat clock");
            check(indexed.fingerprint()==fullScan.fingerprint(),
                    "Indexed traversal changed deterministic battle at tick "+
                            indexed.tick);
        }
        check(indexed.ships().size()==400,
                "A/B must contain the real 200v200 fixture");
        System.out.println("PASS opponent-index 200v200 parity ticks="+steps+
                " fingerprint="+Long.toHexString(indexed.fingerprint())+
                " indexedMs="+indexedNs/1e6+
                " fullFleetScanMs="+fullNs/1e6+
                " (same-run JVM timing diagnostic only)");
    }
}
