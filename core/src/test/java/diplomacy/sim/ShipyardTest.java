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
                require(Shipyard.fit(ship,catalog).laserDamage()>0,"starter lasers must work");
                require(Shipyard.fit(ship,catalog).missileDamage()>0,"starter missiles must work");
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
        String encoded=Shipyard.encode(improved);
        Shipyard.Design loaded=Shipyard.decode(encoded,catalog);
        require(loaded.at(weapon).stage(Shipyard.Track.OUTPUT)==3,"upgrade stage serialization");
        System.out.println("PASS shipyard | "+checked+" faction/hull starters + invalid fitting + upgrades + blueprint round-trip + deterministic battles");
    }
}
