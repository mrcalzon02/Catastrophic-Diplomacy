package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Crew and atmosphere belong to fitted modules; legacy slot IDs survive. */
public final class CrewAccommodationTest {
    private CrewAccommodationTest(){}
    private static void check(boolean good,String why){if(!good)throw new AssertionError(why);}
    private static int habitat(Shipyard.Hull hull,int nth){
        for(var slot:hull.slots)if(slot.mount()==Shipyard.Mount.HABITAT && nth--==0)return slot.index();
        throw new IllegalArgumentException("Habitat socket absent");
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        check(catalog.componentCount()==242&&catalog.upgradeCount()==5808,
                "Eleven native lineages now include accommodation AND life support");
        int observed=0;
        for(Battle.Faction faction:Battle.Faction.values()){
            String id=Shipyard.factionId(faction);
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                var design=Shipyard.starter(faction,hull);
                int crew=habitat(hull,0),air=habitat(hull,1);
                check(air==hull.slots.size()-1 && crew==air-1,
                        "Append-only habitat sockets preserve all legacy weapon indices");
                check(design.at(crew).component.equals("BASE-"+id+"-CRW")
                        &&design.at(air).component.equals("BASE-"+id+"-LFS"),
                        "Every operational starter requires fitted crew quarters and air");
                check(Shipyard.inspect(design,catalog).valid(),
                        "Crewed starter invalid: "+faction+" / "+hull+" "+
                                Shipyard.inspect(design,catalog).explanation());
                var result=Shipyard.fit(design,catalog);
                check(result.crew().berths()>=result.crew().minimum()
                        &&result.crew().lifeSupport()>=result.crew().minimum(),
                        "Crew and air have separate physical and engineering budgets");
                var empty=design.install(crew,null);
                check(Shipyard.inspect(empty,catalog).errors()
                                .contains("Insufficient fitted crew accommodation"),
                        "No bare hull has free crew capacity");
                var vacuum=design.install(air,null);
                check(Shipyard.inspect(vacuum,catalog).errors()
                                .contains("Insufficient fitted life support"),
                        "No empty hull has free breathable air");
                var aired=design.stage(air,Shipyard.Track.OUTPUT,2);
                var accommodated=design.stage(crew,Shipyard.Track.OUTPUT,2);
                check(Shipyard.fit(aired,catalog).crew().lifeSupport()
                                >result.crew().lifeSupport(),
                        "Life support upgrade raises actual oxygen capacity");
                check(Shipyard.fit(accommodated,catalog).crew().berths()
                                >result.crew().berths(),
                        "Quarters upgrade raises actual personnel capacity");
                // New CD-SHIP-1 documents already include the habitat records.
                check(Shipyard.decode(Shipyard.encode(design),catalog).slots()
                        ==design.slots(),"Modern habitat blueprint round trip");
                // Real old blueprints contain only preexisting indices. Decoder
                // backfills the appended habitat slots without rewriting guns.
                String old=Shipyard.encode(design).replace(
                        crew+"|BASE-"+id+"-CRW|0|0|0|0\n","").replace(
                        air+"|BASE-"+id+"-LFS|0|0|0|0\n","");
                var migrated=Shipyard.decode(old,catalog);
                check(migrated.at(crew)!=null && migrated.at(air)!=null,
                        "Old v1 fleets must migrate with explicitly installed new life support");
                Battle battle=new Battle(712);
                battle.spawnWing(0,design,catalog,1,210,320);
                var deployed=battle.ships().get(0);
                check(deployed.fittedCrewSystems && deployed.crewRequired==result.crew().minimum()
                        &&deployed.crewBerths==result.crew().berths()
                        &&deployed.lifeSupport==result.crew().lifeSupport()
                        &&deployed.crewReadiness()==1,
                        "Same crew complement and life support actually enters the simulation");
                observed++;
            }
        }
        check(observed==121,"11 factions x 11 hulls");
        System.out.println("PASS crew habitat: 121 staffed hulls, 22 new module families, 528 upgrade nodes, zero free berths, version-1 fleet migration");
    }
}
