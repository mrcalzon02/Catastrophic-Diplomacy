package diplomacy.client;

import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/**
 * Optional low-heap profile of real arbitrary encyclopedia queries. Keep
 * separate from the universal research index test: broad category matches may
 * create many transient ID strings, and exact numeric searches exercise
 * the authored two-decimal article formatting path.
 *
 * Diagnostic only; correctness still belongs to GameWikiTest.
 */
public final class WikiArbitrarySearchProfile {
    private WikiArbitrarySearchProfile(){}
    private static long used(){
        Runtime runtime=Runtime.getRuntime();
        return runtime.totalMemory()-runtime.freeMemory();
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        GameWikiCatalog wiki=GameWikiCatalog.build(tech);
        for(String needle:new String[]{"plasma","power x1.00","tech"}){
            System.gc();
            long before=used();
            long start=System.nanoTime();
            List<GameWikiCatalog.Article> hits=wiki.search("UPGRADES",needle);
            long elapsed=System.nanoTime()-start;
            int count=hits.size();
            if(count>0){
                GameWikiCatalog.Article first=hits.get(0);
                GameWikiCatalog.Article last=hits.get(count-1);
                if(!first.searchable.contains(needle)||
                        !last.searchable.contains(needle))
                    throw new AssertionError("Arbitrary search returned incorrect article for "+needle);
            }
            long withResults=used();
            hits=null;
            System.gc();
            System.out.println("WIKI_QUERY_PROFILE query="+needle+
                    " matches="+count+
                    " millis="+elapsed/1_000_000.0+
                    " baselineBytes="+before+
                    " resultHeapBytes="+withResults+
                    " retainedBytes="+used()+
                    " heapLimitBytes="+Runtime.getRuntime().maxMemory());
        }
    }
}
