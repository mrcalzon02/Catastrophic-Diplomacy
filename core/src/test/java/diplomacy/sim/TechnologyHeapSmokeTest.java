package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;

/** Release-blocking REAL startup allocation check on Android 10's observed
 * 16 MiB application heap. This intentionally avoids creating the game Wiki:
 * the separate wiki acceptance test validates page generation and navigation. */
public final class TechnologyHeapSmokeTest {
    private TechnologyHeapSmokeTest() { }
    public static void main(String[] args)throws Exception {
        long max=Runtime.getRuntime().maxMemory();
        if(max>20L*1024*1024)
            throw new AssertionError("Technology memory smoke did not run under a constrained heap");
        long before=used();
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        if(technology.componentCount()!=2013 || technology.upgradeCount()!=72468)
            throw new AssertionError("Memory optimization discarded real hardware or research");
        String[] late={"TECH-TER-KIN-OUTPUT-09","TECH-ZIP-HLP-SPECIALIST-07",
                "TECH-NYX-AMV-RESILIENCE-08","TECH-UMB-IRG-EFFICIENCY-09",
                "TECH-TER-EPDA-SPECIALIST-09","TECH-UMB-IRGH-RESILIENCE-08"};
        for(String id:late){
            TechnologyCatalog.Upgrade u=technology.get(id);
            if(u==null||u.stage<7||technology.get(u.prerequisite)==null)
                throw new AssertionError("Missing derived tier under Android heap: "+id);
        }
        System.gc();
        long retained=used();
        System.out.println("PASS 16MiB tech heap: components="+technology.componentCount()+
                " research="+technology.upgradeCount()+" heapLimit="+max+
                " baseline="+before+" retained="+retained);
    }
    private static long used(){
        Runtime r=Runtime.getRuntime();
        return r.totalMemory()-r.freeMemory();
    }
}
