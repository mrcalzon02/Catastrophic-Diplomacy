package diplomacy.sim;

import diplomacy.shipyard.FactionArt;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.Set;

/** No GL/GPU dependency: verifies five selectable factions and 50 art bindings. */
public final class AlienFactionArtTest {
    private AlienFactionArtTest(){}
    private static void check(boolean okay,String why){if(!okay)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        check(Battle.Faction.values().length==11,
                "Six established civilizations plus five distinct new art factions");
        check(FactionArt.alienFactions().size()==5,"All five art families registered");
        Set<String> names=new HashSet<>(),paths=new HashSet<>();
        int images=0;
        for(var item:FactionArt.alienFactions().entrySet()){
            Battle.Faction faction=item.getKey();
            FactionArt.Entry art=item.getValue();
            check(names.add(art.id()),"No alien art ID collision");
            check(!FactionArt.supports(art.mechanics()),"Archetypes must retain independent original factions");
            check(art.name().equals(faction.displayName),"Faction selector shows real civilization name");
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                String path=FactionArt.runtimePath(faction,hull);
                check(path!=null && path.startsWith("ships/factions/"+art.id()+"/"),
                        "Missing distinct art family for "+faction+"/"+hull);
                if(hull.spriteHull()==hull&&hull!=Shipyard.Hull.DREADNOUGHT){
                    check(paths.add(path),"Original art categories must retain unique PNG references");
                    images++;
                }else {
                    check(path.equals(FactionArt.runtimePath(faction,hull.spriteHull())),
                            "Variant must reuse the approved source-category sprite");
                }
                if(hull==Shipyard.Hull.DREADNOUGHT)
                    check(path.endsWith("/carrier.png"),"Capital fallback path changed");
                check(!HullLayouts.outline(faction,hull).isEmpty(),
                        "New faction lacks procedural fallback silhouette");
                Shipyard.Design basic=Shipyard.starter(faction,hull);
                check(Shipyard.inspect(basic,tech).valid(),
                        "New civilization must have a playable shipyard fitting: "+faction+"/"+hull);
                check(Shipyard.decode(Shipyard.encode(basic),tech).faction==faction,
                        "Faction must survive portable fleet save roundtrip");
            }
            Battle test=Battle.duel(1447,2,Shipyard.starter(faction,Shipyard.Hull.CORVETTE),
                    Shipyard.starter(art.mechanics(),Shipyard.Hull.CORVETTE),tech);
            for(int i=0;i<12;i++)test.step();
            check(test.ships().size()==4,"Distinct race must work in live combat");
        }
        check(images==50&&paths.size()==50,"Require exact 50 original non-placeholder sprites");
        System.out.println("PASS alien factions: 5 playable races, 50 original PNG mappings and shared new chassis artwork");
    }
}
