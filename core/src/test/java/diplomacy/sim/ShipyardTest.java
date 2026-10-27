package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** No graphics, Android SDK, Gradle or network access needed for these acceptance gates. */
public final class ShipyardTest {
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
    private static void rejects(Runnable task, String message) {
        try { task.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection: " + message);
    }
    private static int find(Shipyard.Design design, Shipyard.Mount mount, int nth) {
        for (Shipyard.Slot slot : design.hull.slots) {
            if (slot.mount() == mount && nth-- == 0) return slot.index();
        }
        throw new AssertionError("Missing expected slot: " + mount);
    }
    public static void main(String[] ignored) throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        int checked=0;
        for (Battle.Faction faction:Battle.Faction.values()) {
            for (Shipyard.Hull hull:Shipyard.Hull.values()) {
                Shipyard.Design ship=Shipyard.starter(faction,hull);
                Shipyard.Report report=Shipyard.inspect(ship,catalog);
                require(report.valid(),faction+" "+hull+": "+report.explanation());
                require(report.mass()>0&&report.generation()>=report.demand(),"real closed power budget");
                require(report.generatedHeat()<=report.cooling(),"thermodynamic closed budget");
                // The original eleven generic starter blueprints are backward compatible.
                // Specialized new chassis deliberately REPLACE these weapon families.
                if(hull.ordinal()<11){
                    require(Shipyard.fit(ship,catalog).laserDamage()>0,
                            "Legacy starter laser lost during new-variant expansion");
                    require(Shipyard.fit(ship,catalog).missileDamage()>0,
                            "Legacy starter missile lost during new-variant expansion");
                }
                require(Shipyard.decode(Shipyard.encode(ship),catalog).faction==ship.faction,"round trip");
                checked++;
            }
        }
        Shipyard.Design normal=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int weapon=find(normal,Shipyard.Mount.WEAPON,0);
        int system=find(normal,Shipyard.Mount.SYSTEM,0);
        int radiator=find(normal,Shipyard.Mount.SYSTEM,2);
        rejects(()->Shipyard.fit(normal.install(weapon,"BASE-TER-REA"),catalog),"reactor in weapons slot");
        rejects(()->Shipyard.fit(normal.install(system,null),catalog),"reactor required");
        rejects(()->Shipyard.fit(normal.install(radiator,null),catalog),"radiator required");
        rejects(()->Shipyard.fit(normal.install(weapon,"BASE-DRA-LAS"),catalog),"unlicensed other faction");
        rejects(()->Shipyard.decode("CD-SHIP-1|UNKNOWN|CORVETTE|QQ\n",catalog),"bad faction");
        rejects(()->Shipyard.decode(Shipyard.encode(normal).replace("0|", "99|"),catalog),"out of range slot");
        Shipyard.Design heavy=Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE)
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.WEAPON,0),"BASE-CAR-FLK")
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.WEAPON,1),"BASE-CAR-FLK")
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.DEFENSE,0),"BASE-CAR-ARM");
        require(Shipyard.inspect(heavy,catalog).errors().contains("Hull displacement exceeded"),"mass budget must reject overloaded mounts");
        rejects(()->Shipyard.fit(heavy,catalog),"overweight ship may not deploy");
        Shipyard.Design demanding=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE)
                .install(find(Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE),Shipyard.Mount.SYSTEM,3),"BASE-TER-SEN")
                .install(find(Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE),Shipyard.Mount.WEAPON,2),"BASE-TER-FLK")
                .install(find(Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE),Shipyard.Mount.DEFENSE,1),"BASE-TER-ARM")
                .install(find(Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE),Shipyard.Mount.SUPPORT,0),"BASE-TER-REP");
        require(Shipyard.inspect(demanding,catalog).errors().contains("Insufficient reactor output"),"power budget must reject brownout");
        require(Shipyard.inspect(demanding,catalog).errors().contains("Thermal rejection insufficient"),"cooling budget must reject overheating");
        Shipyard.Design improved=normal.stage(weapon,Shipyard.Track.OUTPUT,3);
        require(Shipyard.inspect(improved,catalog).valid(),"three output stages should fit stock corvette");
        require(Shipyard.fit(improved,catalog).laserDamage()>Shipyard.fit(normal,catalog).laserDamage(),"upgrades must impact actual combat firepower");
        require(Shipyard.inspect(improved,catalog).research()>Shipyard.inspect(normal,catalog).research(),"upgrades have real research costs");
        Shipyard.Design noMissiles=normal.install(find(normal,Shipyard.Mount.WEAPON,1),null);
        require(Shipyard.fit(noMissiles,catalog).missileDamage()==0,"removing a missile rack removes missiles");
        Battle noMissilesBattle=new Battle(112L);
        noMissilesBattle.spawnWing(0,noMissiles,catalog,1,200,335);
        noMissilesBattle.spawnWing(1,normal,catalog,1,1070,335);
        for(int i=0;i<900;i++)noMissilesBattle.step();
        require(noMissilesBattle.ships().get(0).launched==0,"missing missile rack cannot fire missiles");
        Battle first=Battle.duel(2479L,8,improved,normal,catalog);
        Battle again=Battle.duel(2479L,8,improved,normal,catalog);
        for(int i=0;i<1400;i++){first.step();again.step();}
        require(first.hitCount>0,"fitted ships must fight");
        require(first.fingerprint()==again.fingerprint(),"fitted combat must be deterministic");
        require(first.ships().get(0).laserDamage>first.ships().get(8).laserDamage,"shipyard output must affect deployed ships");
        Shipyard.Design supportBase=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        Shipyard.Design supportRepair=supportBase.install(find(supportBase,Shipyard.Mount.SUPPORT,0),"BASE-TER-REP");
        require(Shipyard.inspect(supportRepair,catalog).valid(),"repair-equipped cruiser must fit");
        Battle maintenance=new Battle(391L);
        maintenance.spawnWing(0,supportBase,catalog,1,100,100);
        maintenance.spawnWing(0,supportRepair,catalog,1,200,100);
        Battle.Ship baseline=maintenance.ships().get(0);
        Battle.Ship repaired=maintenance.ships().get(1);
        baseline.hull-=30;repaired.hull-=30;
        for(int t=0;t<600;t++)maintenance.step();
        require(repaired.hull>baseline.hull+1.0,"non-Mandrally repair bays must repair faster than empty support slots");
        String encoded=Shipyard.encode(improved);
        Shipyard.Design loaded=Shipyard.decode(encoded,catalog);
        require(loaded.at(weapon).stage(Shipyard.Track.OUTPUT)==3,"upgrade stage serialization");
        System.out.println("PASS shipyard | "+checked+" faction/hull starters + invalid fitting + upgrades + blueprint round-trip + deterministic battles");
    }
}
