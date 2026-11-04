package diplomacy.client;

import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/**
 * Standalone opt-in low-heap wiki/search measurement. Numbers are diagnostics;
 * article count, IDs and selection correctness are objective functional checks.
 */
public final class WikiMemoryProfile {
    private WikiMemoryProfile(){}
    private static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    public static void main(String[] args)throws Exception {
        System.gc();
        long start=System.nanoTime();
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        long loaded=System.nanoTime();
        GameWikiCatalog wiki=GameWikiCatalog.build(tech);
        long indexed=System.nanoTime();
        long afterIndex=used();
        List<GameWikiCatalog.Article> all=wiki.search("ALL","");
        if(all.size()!=wiki.size()||wiki.size()<tech.upgradeCount())
            throw new AssertionError("Wiki index truncated technology records");
        long queryStart=System.nanoTime();
        List<GameWikiCatalog.Article> matches=wiki.search("UPGRADES","research");
        long queried=System.nanoTime();
        if(matches.size()!=tech.upgradeCount())
            throw new AssertionError("Broad wiki query omitted research articles");
        String tail=matches.get(matches.size()-1).id;
        if(wiki.indexOfId(matches,tail)!=matches.size()-1)
            throw new AssertionError("Virtual research selection failed");
        long selected=System.nanoTime();
        System.gc();
        System.out.println("WIKI_PROFILE maxHeapBytes="+Runtime.getRuntime().maxMemory()+
                " retainedBytes="+used()+" indexedBytes="+afterIndex+
                " catalogLoadMillis="+(loaded-start)/1_000_000.0+
                " wikiIndexMillis="+(indexed-loaded)/1_000_000.0+
                " broadSearchMillis="+(queried-queryStart)/1_000_000.0+
                " selectedMillis="+(selected-queried)/1_000_000.0+
                " articleCount="+wiki.size()+" matchingIds="+matches.size());
    }
}
