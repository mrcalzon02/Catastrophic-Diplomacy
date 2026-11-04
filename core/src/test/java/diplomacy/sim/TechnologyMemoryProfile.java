package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;

/**
 * Optional workload profile: report timings/heap, never assert an FPS or
 * heap-size target. Functional catalog parity belongs to technologySmoke.
 */
public final class TechnologyMemoryProfile {
    private TechnologyMemoryProfile(){}
    static long used(){ Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory(); }
    public static void main(String[] args)throws Exception {
        Runtime runtime=Runtime.getRuntime();
        System.gc();
        long baseline=used();
        long start=System.nanoTime();
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        long loadedNs=System.nanoTime()-start;
        long afterLoad=used();
        long lookupStart=System.nanoTime();
        String[] factions={"TER","DRA","CAR","ZIP","UZI","MAN","VAE","ZEL","AUR","NYX","UMB"};
        String[] stages={"01","03","06","09"};
        int verified=0;
        for(String faction:factions)for(String stage:stages){
            TechnologyCatalog.Upgrade upgrade=tech.get("TECH-"+faction+"-KIN-OUTPUT-"+stage);
            if(upgrade==null||upgrade.stage!=Integer.parseInt(stage))
                throw new AssertionError("Missing canonical tier "+faction+"/"+stage);
            verified++;
        }
        long lookupNs=System.nanoTime()-lookupStart;
        System.gc();
        long retained=used();
        System.out.println("TECH_PROFILE maxHeapBytes="+runtime.maxMemory()+
                " baselineBytes="+baseline+" afterLoadBytes="+afterLoad+
                " retainedBytes="+retained+
                " loadMillis="+loadedNs/1_000_000.0+
                " lookupMillis="+lookupNs/1_000_000.0+
                " verifiedFactions="+factions.length+
                " verifiedTierIds="+verified+
                " components="+tech.componentCount()+
                " upgrades="+tech.upgradeCount());
    }
}
