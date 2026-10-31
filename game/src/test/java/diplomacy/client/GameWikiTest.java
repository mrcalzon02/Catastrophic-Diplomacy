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
    public static void main(String[] args)throws Exception {
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        GameWikiCatalog catalog=GameWikiCatalog.build(technology);
        int hulls=Shipyard.Hull.shipyardOrder().length;
        int stages=technology.upgradeCount(),modules=technology.componentCount();
        check(stages>=13000&&modules>=350,"Wiki built with incomplete faction technology data");
        check(catalog.size()>=stages+modules+hulls+
                ModuleVersions.Pattern.values().length*4+
                ShipStandingOrders.SETTINGS.length,"Reference coverage is incomplete");
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
                    .filter(e->e.id.startsWith("salvo.")).count()==2,
                    "Missile and torpedo salvo entries must both exist for "+mode);
        GameWikiPanel ui=new GameWikiPanel(catalog,true);
        check(ui.resultCount()>8&&ui.categoryIndex()==1,"Mobile guide landing page absent");
        ui.setCategory(0);
        check(ui.resultCount()==catalog.size(),"ALL must expose the complete index");
        ui.setQuery("TECH-TER-LAS-OUTPUT-01");
        check(ui.resultCount()>0&&ui.selectedId().contains("upgrade."),
                "Searching exact research IDs must select the actual upgrade");
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
