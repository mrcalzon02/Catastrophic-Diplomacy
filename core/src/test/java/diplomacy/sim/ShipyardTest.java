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
                Shipyard.Design empty=Shipyard.bareHull(faction,hull);
                Shipyard.Report emptyReport=Shipyard.inspect(empty,catalog);
                require(emptyReport.valid(),"Empty chassis cannot operate: "+faction+"/"+hull+
                        " "+emptyReport.explanation());
                require(emptyReport.generation()>0&&emptyReport.cooling()>0,
                        "A bare ship must have an integrated reactor and radiator");
                var fittedEmpty=Shipyard.fit(empty,catalog);
                require(fittedEmpty.crew().berths()>=fittedEmpty.crew().minimum()
                                &&fittedEmpty.crew().lifeSupport()>=fittedEmpty.crew().minimum(),
                        "An empty ship needs enough built-in accommodation and oxygen");
                require(fittedEmpty.speed()>0&&fittedEmpty.powerRegen()>0
                                &&fittedEmpty.coolingRate()>0,
                        "An empty ship must maneuver under its own power");
                require(fittedEmpty.laserDamage()==0&&fittedEmpty.missileDamage()==0
                                &&fittedEmpty.pdRange()==0,
                        "Intrinsic services must not conjure free weapons");
                var revived=Shipyard.decode(Shipyard.encode(empty),catalog);
                require(revived.slots()==empty.slots()&&Shipyard.inspect(revived,catalog).valid(),
                        "Bare-hull blueprint must round-trip without adding modules");
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
        // The unarmed factory-empty ship is operational in the actual
        // simulation, including crew, fuel, and rounds in its magazines.
        Shipyard.Design emptyScout=Shipyard.bareHull(
                Battle.Faction.TERRAN,Shipyard.Hull.INTERCEPTOR);
        Battle emptyBattle=new Battle(87021);
        emptyBattle.spawnWing(0,emptyScout,catalog,1,200,340);
        Battle.Ship emptyShip=emptyBattle.ships().get(0);
        require(emptyShip.crewReadiness()>=0.99&&emptyShip.power>0
                        &&emptyShip.fuel>0&&emptyShip.maxFuel>0,
                "Bare hull must enter combat crewed, powered and fueled");
        require(emptyShip.kineticAmmo>0&&emptyShip.missileAmmo>0
                        &&emptyShip.torpedoAmmo>0,
                "Physical magazines must be stocked even before guns are fitted");
        require(emptyShip.weaponEmitters.isEmpty(),
                "An unarmed hull must never shoot without a physical gun");
        double previousX=emptyShip.x;
        for(int i=0;i<90;i++)emptyBattle.step();
        require(emptyShip.alive()&&emptyShip.power>=0&&Double.isFinite(emptyShip.x),
                "Bare hull must survive the first ninety frames without refit");
        require(emptyShip.maxFuel>180&&emptyShip.maxFuel>=emptyShip.fuel,
                "Hull-class fuel stores must replace fixed 180-unit legacy fuel");
        Shipyard.Design normal=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int weapon=find(normal,Shipyard.Mount.WEAPON,0);
        int system=find(normal,Shipyard.Mount.SYSTEM,0);
        int radiator=find(normal,Shipyard.Mount.SYSTEM,2);
        require(Shipyard.accepts(normal.hull,weapon,catalog.base("BASE-TER-REA")),
                "Weapon emplacement must accept a reactor conversion");
        require(Shipyard.accepts(normal.hull,weapon,catalog.base("BASE-TER-SHD")),
                "Weapon emplacement must accept shield conversion");
        require(Shipyard.accepts(normal.hull,weapon,catalog.base("BASE-TER-ARM")),
                "Weapon emplacement must accept armor conversion");
        require(Shipyard.accepts(normal.hull,weapon,catalog.base("BASE-TER-ENG")),
                "Weapon emplacement must accept auxiliary thrusters");
        require(Shipyard.fit(normal.install(weapon,"BASE-TER-REA"),catalog).maxPower()
                        >Shipyard.fit(normal,catalog).maxPower(),
                "Trading away a gun for a reactor must increase available power");
        // Removing a factory reactor can cause a brownout under *existing*
        // weapons, but never destroys the underlying integrated bridge plant.
        require(Shipyard.inspect(Shipyard.bareHull(Battle.Faction.TERRAN,
                                Shipyard.Hull.CORVETTE),catalog).valid(),
                "Removing every installed reactor and radiator must remain viable");
        rejects(()->Shipyard.fit(normal.install(weapon,"BASE-DRA-LAS"),catalog),"unlicensed other faction");
        rejects(()->Shipyard.decode("CD-SHIP-1|UNKNOWN|CORVETTE|QQ\n",catalog),"bad faction");
        rejects(()->Shipyard.decode(Shipyard.encode(normal).replace("0|", "99|"),catalog),"out of range slot");
        Shipyard.Design heavy=Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE)
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.WEAPON,0),"BASE-CAR-FLK")
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.WEAPON,1),"BASE-CAR-FLK")
                .install(find(Shipyard.starter(Battle.Faction.CARNELIAN,Shipyard.Hull.CORVETTE),Shipyard.Mount.DEFENSE,0),"BASE-CAR-ARM");
        require(Shipyard.inspect(heavy,catalog).errors().contains("Hull displacement exceeded"),"mass budget must reject overloaded mounts");
        rejects(()->Shipyard.fit(heavy,catalog),"overweight ship may not deploy");
        Shipyard.Design demanding=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE);
        for(Shipyard.Slot slot:demanding.hull.slots){
            if(slot.mount()==Shipyard.Mount.WEAPON
                    ||slot.mount()==Shipyard.Mount.DEFENSE)
                demanding=demanding.install(slot.index(),"BASE-TER-SHD");
            if(slot.mount()==Shipyard.Mount.SYSTEM)
                demanding=demanding.install(slot.index(),"BASE-TER-ENG");
            if(slot.mount()==Shipyard.Mount.SUPPORT)
                demanding=demanding.install(slot.index(),"BASE-TER-REP");
        }
        require(Shipyard.inspect(demanding,catalog).errors().contains("Insufficient reactor output"),
                "Bare-hull core power cannot run unlimited shield and thruster augmentations");
        require(Shipyard.inspect(demanding,catalog).errors().contains("Thermal rejection insufficient"),
                "Bare-hull radiator cannot reject unlimited reactor and shield heat");
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
        // Physical specialization: same dreadnought hull, different
        // sacrificial module layouts; all systems are genuinely paid for.
        Shipyard.Design basicDread=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        Shipyard.Design armorFist=basicDread;
        for(Shipyard.Slot slot:basicDread.hull.slots){
            if(slot.mount()==Shipyard.Mount.DEFENSE)armorFist=armorFist.install(
                    slot.index(),"BASE-TER-ARM");
            if(slot.mount()==Shipyard.Mount.WEAPON){
                int number=slot.index()-find(basicDread,Shipyard.Mount.WEAPON,0);
                armorFist=armorFist.install(slot.index(),
                        number<3?"BASE-TER-KIN":"BASE-TER-ARM");
            }
            if(slot.mount()==Shipyard.Mount.SYSTEM){
                int number=slot.index()-find(basicDread,Shipyard.Mount.SYSTEM,0);
                if(number<3)armorFist=armorFist.install(slot.index(),"BASE-TER-REA");
                if(number==3)armorFist=armorFist.install(slot.index(),"BASE-TER-RAD");
            }
        }
        require(Shipyard.inspect(armorFist,catalog).valid(),
                "Armored-fist dreadnought needs a real power/heat budget: "+
                        Shipyard.inspect(armorFist,catalog).explanation());
        Shipyard.Design shieldSpire=basicDread;
        for(Shipyard.Slot slot:basicDread.hull.slots){
            if(slot.mount()==Shipyard.Mount.DEFENSE)
                shieldSpire=shieldSpire.install(slot.index(),"BASE-TER-SHD");
            if(slot.mount()==Shipyard.Mount.WEAPON){
                int number=slot.index()-find(basicDread,Shipyard.Mount.WEAPON,0);
                shieldSpire=shieldSpire.install(slot.index(),
                        number<2?"BASE-TER-LAS":"BASE-TER-SHD");
            }
            if(slot.mount()==Shipyard.Mount.SYSTEM){
                int number=slot.index()-find(basicDread,Shipyard.Mount.SYSTEM,0);
                if(number<4)shieldSpire=shieldSpire.install(slot.index(),"BASE-TER-REA");
                if(number>=4&&number<7)shieldSpire=shieldSpire.install(
                        slot.index(),"BASE-TER-RAD");
            }
        }
        require(Shipyard.inspect(shieldSpire,catalog).valid(),
                "Shield spire dreadnought needs genuine cooling/reactors: "+
                        Shipyard.inspect(shieldSpire,catalog).explanation());
        var armored=Shipyard.fit(armorFist,catalog);
        var shielded=Shipyard.fit(shieldSpire,catalog);
        require(armored.armor()>shielded.armor()*2,
                "Armored fist should absorb vastly more hull hits");
        require(armored.kineticDamage()>0,
                "Armored fist still requires physically fitted guns");
        require(shielded.shields()>armored.shields()*2
                        &&shielded.shieldRechargeBoost()>armored.shieldRechargeBoost()*2,
                "Shield spire must exchange armored fists for fast shield recovery");
        require(shielded.maxPower()>0&&shielded.coolingRate()>0,
                "Specialization cannot bypass real thermal and power budget");
        String encoded=Shipyard.encode(improved);
        Shipyard.Design loaded=Shipyard.decode(encoded,catalog);
        require(loaded.at(weapon).stage(Shipyard.Track.OUTPUT)==3,"upgrade stage serialization");
        System.out.println("PASS shipyard | "+checked+" faction/hull starters + invalid fitting + upgrades + blueprint round-trip + deterministic battles");
    }
}
