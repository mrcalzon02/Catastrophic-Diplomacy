package diplomacy.client;

import diplomacy.tech.TechnologyCatalog;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullProgression;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.sim.FighterModel;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.ShipStandingOrders;
import java.util.List;

/** Device-independent functional wiki acceptance: all real records, search,
 * pagination and touch actions, not pixel-perfect visual assertions. */
public final class GameWikiTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void finishQueued(GameWikiPanel ui)throws Exception{
        ui.flushQueuedQuery(Long.MAX_VALUE);
        long end=System.nanoTime()+5_000_000_000L;
        while(ui.queryPending()&&System.nanoTime()<end){
            Thread.sleep(2);
            ui.flushQueuedQuery(Long.MAX_VALUE);
        }
        check(!ui.queryPending(),"Asynchronous wiki query did not complete");
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        GameWikiCatalog catalog=GameWikiCatalog.build(technology);
        int hulls=Shipyard.Hull.shipyardOrder().length;
        int stages=technology.upgradeCount(),modules=technology.componentCount();
        check(stages>=13000&&modules>=350,"Wiki built with incomplete faction technology data");
        check(catalog.size()>=stages+modules+hulls+
                ModuleVersions.Pattern.values().length*4+
                ShipStandingOrders.SETTINGS.length,"Reference coverage is incomplete");
        // Capture the exact established lexicographic order before swapping
        // the 73k-ID resident list for compact component-derived paging.
        // Counts alone cannot detect dropped/reordered rank or track entries.
        check(stages==modules*4*9,
                "Every base component must own exactly four nine-stage research paths");
        java.util.List<String> expectedIds=new java.util.ArrayList<>(technology.upgradeIds());
        expectedIds.sort(String::compareTo);
        int firstResearchArticle=catalog.size()-stages;
        for(int index=0;index<stages;index++){
            String actual=catalog.articles().get(firstResearchArticle+index).id;
            check(actual.equals("upgrade."+expectedIds.get(index)),
                    "Research index ordering/canonical identity changed at "+index+
                            ": expected="+expectedIds.get(index)+" actual="+actual);
        }
        // Every persistent captain setting must open a real article. New ammo
        // and fuel rows must never break the encyclopedia on game startup.
        var orderArticles=catalog.search("ORDERS","");
        for(int index=0;index<ShipStandingOrders.SETTINGS.length;index++){
            String expectedId="order."+index;
            var order=orderArticles.stream().filter(a->a.id.equals(expectedId))
                    .findFirst().orElseThrow(()->new AssertionError(
                            "Wiki lost standing-order article "+expectedId));
            check(order.lines.size()>0&&order.lines.get(0).length()>30,
                    "Missing meaningful gameplay explanation for "+expectedId);
        }
        for(Shipyard.Hull hull:Shipyard.Hull.values()){
            List<GameWikiCatalog.Article> matches=catalog.search("HULLS",hull.name());
            check(matches.stream().anyMatch(e->e.id.equals("hull."+hull.name())),
                    "Missing actual hull/variant "+hull);
            var article=matches.stream().filter(e->e.id.equals("hull."+hull.name())).findFirst().orElseThrow();
            check(article.searchable.contains("required technology tier: "+HullProgression.requiredTier(hull)),
                    "Hull unlock cost detached from live tech tree: "+hull);
        }
        java.util.Set<String> indexed=new java.util.HashSet<>();
        for(var article:catalog.articles())check(indexed.add(article.id),
                "Duplicated encyclopedia record "+article.id);
        for(var c:technology.baseComponents().values())
            check(indexed.contains("module."+c.id),"Missing actual module "+c.id);
        for(var u:technology.upgrades().values())
            check(indexed.contains("upgrade."+u.id),"Missing nine-tier upgrade "+u.id);
        for(var type:FighterModel.Hull.values())
            check(catalog.search("FIGHTERS",type.name()).stream()
                    .anyMatch(e->e.id.equals("fighter."+type.name())),"Missing fighter "+type);
        for(var mode:OrdnanceDoctrine.Mode.values())
            check(catalog.search("ORDNANCE",mode.name()).stream()
                    .filter(e->e.id.equals("salvo.MISSILE."+mode)
                            ||e.id.equals("salvo.TORPEDO."+mode)).count()==2,
                    "Missile and torpedo salvo entries must both exist for "+mode);

        check(catalog.search("MODULES","BASE-TER-EPDA").stream()
                .anyMatch(a->a.id.equals("module.BASE-TER-EPDA")),
                "Advanced rescue hardware is missing from actual module encyclopedia");
        check(catalog.search("UPGRADES","TECH-TER-EPDA-SPECIALIST-09").stream()
                .anyMatch(a->a.id.equals("upgrade.TECH-TER-EPDA-SPECIALIST-09")),
                "Virtual Tech IX rescue research cannot be browsed by exact ID");
        // A broad text query must keep the complete index, but must not
        // retain 73k formatted Article instances in the returned result set.
        long broadSearchStart=System.nanoTime();
        List<GameWikiCatalog.Article> broad=catalog.search("UPGRADES","research");
        long broadSearchMillis=(System.nanoTime()-broadSearchStart)/1_000_000;
        check(broad.size()==stages,"Broad research search lost authored upgrades");
        check(!(broad instanceof java.util.ArrayList),
                "Large filtered upgrade search must be a virtual ID-backed list");
        // The "Research cost " phrase is literally emitted by every
        // upgrade article. Its search must return the canonical virtual
        // index without scanning or formatting the research catalogue.
        check(broad==catalog.search("UPGRADES",""),
                "Universal research search must reuse the immutable upgrade index");
        check(catalog.search("UPGRADES","COST").size()==stages,
                "Case-insensitive research cost lookup lost an upgrade");
        for(int sample:new int[]{0,stages/2,stages-1})
            check(broad.get(sample).searchable.contains("research"),
                    "Universal research keyword must occur in every sampled article");
        var allResearch=catalog.search("ALL","research");
        int staticMatching=0;
        for(String kind:GameWikiCatalog.CATEGORIES)
            if(!kind.equals("ALL")&&!kind.equals("UPGRADES"))
                staticMatching+=catalog.search(kind,"research").size();
        check(allResearch.size()==stages+staticMatching,
                "ALL-category research search dropped static or virtual results");
        check(catalog.indexOfId(allResearch,broad.get(stages-1).id)==
                allResearch.size()-1,
                "Mixed-category research search lost canonical ID ordering");
        String lastUpgrade=broad.get(broad.size()-1).id;
        check(catalog.indexOfId(broad,lastUpgrade)==broad.size()-1,
                "Research search selection must index by ID without listing every article");
        check(catalog.indexOfId(catalog.articles(),lastUpgrade)>=modules,
                "Complete wiki selection must resolve canonical ID without materialization");
        check(catalog.search("UPGRADES","x_an_impossible_token").isEmpty(),
                "No-match full text search must not fabricate an entry");
        System.out.println("PROFILE wiki broad full-text search ms="+broadSearchMillis+
                " matchingIds="+broad.size());
        // Oracle parity for arbitrary FULL-TEXT searches. These intentionally
        // compare every entry against the authoritative Article.searchable text,
        // not a hand-picked list of expected IDs or a reduced search corpus.
        // Include metadata, physical numeric formatting and branch prose.
        for(String needle:new String[]{"plasma","power x1.00",
                "research branch purpose: increases"}){
            var matches=catalog.search("UPGRADES",needle);
            java.util.Set<String> returned=new java.util.HashSet<>();
            for(var article:matches)
                check(returned.add(article.id),"Duplicate free-text search result "+article.id);
            int expectedCount=0;
            for(int i=0;i<stages;i++){
                var article=catalog.articles().get(firstResearchArticle+i);
                boolean shouldMatch=article.searchable.contains(needle);
                if(shouldMatch)expectedCount++;
                check(shouldMatch==returned.contains(article.id),
                        "Full-text search parity drift for '"+needle+"' on "+article.id);
            }
            check(matches.size()==expectedCount,
                    "Full-text search result count drift for "+needle);
            if(!matches.isEmpty()){
                for(int resultIndex:new int[]{0,matches.size()/2,matches.size()-1}){
                    String selectedId=matches.get(resultIndex).id;
                    check(catalog.indexOfId(matches,selectedId)==resultIndex,
                            "Filtered wiki selection index drift "+needle+
                                    " at "+resultIndex);
                }
            }
        }
        // This phrase occurs in every canonical ID. Large filtered results
        // must remain complete and selectable, not retain 73k duplicate IDs.
        List<GameWikiCatalog.Article> broadTech=catalog.search("UPGRADES","tech");
        check(broadTech.size()==stages,"Broad technology search truncated upgrades");
        check(broadTech==catalog.search("UPGRADES",""),
                "Every research ID starts with TECH; broad ID queries must reuse the virtual index");
        for(int index:new int[]{0,stages/2,stages-1}){
            var selected=broadTech.get(index);
            check(selected.searchable.contains("tech")&&
                    catalog.indexOfId(broadTech,selected.id)==index,
                    "Broad technology results lost index "+index);
        }
        GameWikiPanel ui=new GameWikiPanel(catalog,true);
        check(ui.resultCount()>8&&ui.categoryIndex()==1,"Mobile guide landing page absent");
        ui.setCategory(0);
        check(ui.resultCount()==catalog.size(),"ALL must expose the complete index");
        ui.setQuery("TECH-TER-LAS-OUTPUT-01");
        check(ui.resultCount()>0&&ui.selectedId().contains("upgrade."),
                "Searching exact research IDs must select the actual upgrade");
        ui.setQuery("");
        ui.setCategory(5);
        check(ui.resultCount()==stages,
                "Virtual upgrades must expose all ranks without eager articles");
        ui.setQuery("TECH-TER-KIN-OUTPUT-09");
        check(ui.resultCount()==1 && ui.selectedId().equals(
                "upgrade.TECH-TER-KIN-OUTPUT-09"),
                "Exact tier IX search must retrieve the real on-demand article");
        ui.setQuery("");
        ui.pageList(300);
        check(ui.listPage()>0,"Virtual research articles must remain pageable");
        ui.select(2500);
        check(ui.selectedId()!=null && ui.selectedId().startsWith("upgrade."),
                "Virtual research selection lost the canonical identifier");
        ui.setCategory(0);
        ui.setQuery("___a_nonexistent_entry___");
        check(ui.resultCount()==0&&ui.selectedId()==null,
                "No-match query must not show stale article details");
        ui.setQuery("");
        ui.setCategory(3);
        check(ui.resultCount()==hulls,"HULLS category must contain every actual chassis");
        check(ui.tap(45,67)==0,"Paging control must be touch-interactive");
        ui.tap(250,67);
        check(ui.listPage()==1,"Next page must move through physical hulls");
        ui.tap(50,491);
        check(ui.selectedId()!=null&&ui.textScroll()==0,
                "Selecting a visible chassis must reset detail scroll");
        check(ui.tap(60,622)==1,"Search field must request native mobile keyboard");
        check(ui.tap(1150,673)==2,"Close control must restore prior game state");
        ui.setQuery("hull");
        ui.erase();ui.type('l');
        check(ui.query().equals("hull"),"Keyboard query editing regressed");
        check(ui.queryPending(),
                "Key-by-key wiki edit was not coalesced before full search");
        finishQueued(ui);
        check(ui.resultCount()>0,
                "Coalesced wiki query did not refresh after debounce");
        ui.setQuery("bad\ntext");
        check(!ui.query().contains("\n"),"Control characters must not enter native search");
        check(GameMenuPanel.homeHit(965,653,true)==8 &&
                GameMenuPanel.homeHit(965,653,false)==8,
                "Wiki launcher touch rectangle must match desktop and mobile rendering");
        System.out.println("PASS game wiki: "+catalog.size()+" articles including "+hulls+
                " physical hulls, "+modules+" actual modules, "+stages+
                " real research upgrades, searchable Android/desktop navigation");
    }
}
