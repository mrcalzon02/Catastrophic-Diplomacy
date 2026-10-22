package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Direct proofs that shipyard machinery has differentiated combat effects. */
public final class CombatMachineryTest {
    private static void require(boolean good,String reason){if(!good)throw new AssertionError(reason);}
    private static int slot(Shipyard.Design design,Shipyard.Mount mount,int position){
        for(Shipyard.Slot s:design.hull.slots)if(s.mount()==mount&&position--==0)return s.index();
        throw new AssertionError("Missing hardpoint "+mount);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        var baseline=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE);
        int support=slot(baseline,Shipyard.Mount.SUPPORT,0);
        var fighter=baseline.install(support,"BASE-TER-FTR");
        var drone=baseline.install(support,"BASE-TER-DRN");
        var magazine=baseline.install(support,"BASE-TER-MAG");
        for(var design:new Shipyard.Design[]{fighter,drone,magazine})
            require(Shipyard.inspect(design,catalog).valid(),"support machinery must fit");
        require(Shipyard.fit(fighter,catalog).fighterCapacity()>0,"hangar must have fighter reserves");
        require(Shipyard.fit(drone,catalog).droneCapacity()>0,"controller must have defensive drones");
        require(Shipyard.fit(magazine,catalog).magazineCapacity()>0,"magazine must supply ammunition");
        Battle b=new Battle(997);
        b.spawnWing(0,fighter,catalog,1,400,350);
        b.spawnWing(0,drone,catalog,1,450,350);
        b.spawnWing(0,magazine,catalog,1,500,350);
        b.spawnWing(1,baseline,catalog,1,850,350);
        require(b.ships().get(0).fighterReserve>0&&b.ships().get(1).droneReserve>0,"fitted hangar/drone reserves");
        require(b.ships().get(2).missileAmmo>b.ships().get(0).missileAmmo,"magazine adds ammunition");
        for(int i=0;i<600;i++)b.step();
        require(b.ships().get(0).fightersLaunched>0,"fighter hangar launches actual craft");
        require(b.ships().get(1).dronesLaunched>0,"drone controller launches active defense craft");
        require(b.ships().get(0).fighterReserve>=0&&b.ships().get(1).droneReserve>=0,"reserves bounded");
        Battle repeat=new Battle(997);
        repeat.spawnWing(0,fighter,catalog,1,400,350);
        repeat.spawnWing(0,drone,catalog,1,450,350);
        repeat.spawnWing(0,magazine,catalog,1,500,350);
        repeat.spawnWing(1,baseline,catalog,1,850,350);
        for(int i=0;i<600;i++)repeat.step();
        require(b.fingerprint()==repeat.fingerprint(),"fighters and drones deterministic across replay");
        var cruiser=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        int sensor=slot(cruiser,Shipyard.Mount.SYSTEM,3);
        var jammed=cruiser.install(sensor,"BASE-TER-ECM");
        require(Shipyard.inspect(jammed,catalog).valid(),"ECM retrofit must fit");
        require(Shipyard.fit(jammed,catalog).ecmStrength()>0,"ECM has actual disruption strength");
        int mount=slot(cruiser,Shipyard.Mount.WEAPON,0);
        for(String family:new String[]{"KIN","PLA","PAR","TOR"}){
            var candidate=cruiser.install(mount,"BASE-TER-"+family);
            require(Shipyard.inspect(candidate,catalog).valid(),family+" installation must fit");
            var fitted=Shipyard.fit(candidate,catalog);
            switch(family){
                case "KIN" -> require(fitted.kineticDamage()>0,"kinetic weapon damage");
                case "PLA" -> require(fitted.plasmaDamage()>0,"plasma damage/heat");
                case "PAR" -> require(fitted.particleDamage()>0,"particle disruption");
                case "TOR" -> require(fitted.torpedoDamage()>0,"torpedo distinct from missiles");
            }
        }
        var torpedo=cruiser.install(mount,"BASE-TER-TOR");
        Battle torpedoTest=new Battle(403);
        torpedoTest.spawnWing(0,torpedo,catalog,1,300,300);
        torpedoTest.spawnWing(1,cruiser,catalog,1,800,300);
        for(int i=0;i<3;i++)torpedoTest.step();
        require(torpedoTest.ships().get(0).torpedoAmmo<3,"fitted torpedo tube fires finite ammunition");
        require(torpedoTest.missiles().stream().anyMatch(m->m.torpedo),"heavy torpedoes use own speed/integrity");
        System.out.println("PASS combat machinery: fighters, defense drones, magazines, ECM, four distinct weapon branches and deterministic replay");
    }
}
