package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.tech.TechnologyCatalog;

/** A saved fleet genuinely chooses and launches an ammo type its modules own. */
public final class AmmunitionSelectionTest {
    private AmmunitionSelectionTest(){}
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static void denied(Runnable action,String message){
        try{action.run();throw new AssertionError("Invalid ammunition selection accepted: "+message);}
        catch(IllegalArgumentException valid){
            check(valid.getMessage()!=null&&!valid.getMessage().isBlank(),
                    "Rejection must explain the research lock");
        }
    }
    private static int weapon(Shipyard.Hull hull,int ordinal){
        for(var slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON && ordinal--==0)return slot.index();
        throw new IllegalArgumentException("Weapon index not fitted");
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        int missileSlot=weapon(hull,1),first=weapon(hull,0),third=weapon(hull,2);
        var basic=Shipyard.starter(Battle.Faction.TERRAN,hull);
        check(Shipyard.encode(basic).startsWith("CD-SHIP-1|"),
                "Legacy save format must not change when stock ammo selected");
        var researched=basic.stage(missileSlot,Shipyard.Track.EFFICIENCY,5);
        denied(()->researched.withGuidedWarhead(Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.SHIELD_BREAKER),
                "Wrong research tree cannot access shield breaker");
        var chosen=researched.withGuidedWarhead(Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.EMP);
        check(chosen.ammoSelection.missile()==AmmunitionTable.Warhead.EMP,
                "Fleet stores actual researched EMP missile load");
        check(basic.ammoSelection.equals(Shipyard.AmmoSelection.STANDARD),
                "A previously saved design remains immutable");

        String encoded=Shipyard.encode(chosen);
        check(encoded.startsWith("CD-SHIP-3|"),
                "Nonstandard ammo requires explicit, self-describing save version");
        var restored=Shipyard.decode(encoded,catalog);
        check(Shipyard.encode(restored).equals(encoded)
                        &&restored.ammoSelection.missile()==AmmunitionTable.Warhead.EMP,
                "Exact selected warhead and research survive portable blueprint roundtrip");
        denied(()->chosen.stage(missileSlot,Shipyard.Track.EFFICIENCY,1),
                "A downgrade may not silently invalidate already loaded ammo");
        denied(()->chosen.install(third,"BASE-TER-MSL"),
                "A stock missile rack cannot borrow the advanced rack's ammo");
        denied(()->Shipyard.decode(encoded.replace("|EMP|","|SHIELD_BREAKER|"),catalog),
                "Forged blueprints cannot load off-tree ammo");

        var reset=chosen.withGuidedWarhead(Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.STANDARD)
                .stage(missileSlot,Shipyard.Track.EFFICIENCY,1);
        check(reset.ammoSelection.equals(Shipyard.AmmoSelection.STANDARD)
                        &&Shipyard.encode(reset).startsWith("CD-SHIP-1|"),
                "Explicitly resetting a loadout preserves legacy blueprint version");

        var manufactured=chosen.withModuleVersion(
                missileSlot,ModuleVersions.Pattern.COMPACT,2);
        var manufacturedSave=Shipyard.decode(Shipyard.encode(manufactured),catalog);
        check(manufacturedSave.ammoSelection.missile()==AmmunitionTable.Warhead.EMP
                        &&manufacturedSave.at(missileSlot).mark==2,
                "CD-SHIP-3 includes physical Mk/sidegrade and research simultaneously");

        var v2=basic.withModuleVersion(missileSlot,ModuleVersions.Pattern.COMPACT,2);
        check(Shipyard.encode(v2).startsWith("CD-SHIP-2|"),
                "CD-SHIP-2 with Mk-II and standard ammo must remain unchanged");

        for(Battle.Faction faction:Battle.Faction.values()){
            String id=Shipyard.factionId(faction);
            var own=Shipyard.starter(faction,hull)
                    .stage(missileSlot,Shipyard.Track.EFFICIENCY,5)
                    .withGuidedWarhead(Battle.DamageType.MISSILE,AmmunitionTable.Warhead.EMP);
            check(Shipyard.inspect(own,catalog).valid(),
                    "All eleven races can fit their own equivalent research ammo: "+id);
            check(Shipyard.decode(Shipyard.encode(own),catalog)
                            .ammoSelection.missile()==AmmunitionTable.Warhead.EMP,
                    "Every faction's saved loadout remains valid: "+id);
        }

        var kinetic=basic.install(first,"BASE-TER-KIN")
                .stage(first,Shipyard.Track.OUTPUT,5)
                .withKineticAmmunition(AmmunitionTable.Kinetic.RELATIVISTIC);
        check(Shipyard.decode(Shipyard.encode(kinetic),catalog)
                        .ammoSelection.kinetic()==AmmunitionTable.Kinetic.RELATIVISTIC,
                "Fitted high-velocity kinetic load survives save");
        denied(()->kinetic.withKineticAmmunition(AmmunitionTable.Kinetic.ARMOR_PIERCING),
                "Different kinetic technology branch must not be borrowed");

        Battle battle=Battle.duel(9147,1,restored,restored,catalog);
        var attacker=battle.ships().get(0);
        var enemy=battle.ships().get(1);
        attacker.x=200;attacker.y=310;attacker.speed=0;
        enemy.x=505;enemy.y=310;enemy.speed=0;enemy.pdRange=0;
        check(attacker.warhead(Battle.DamageType.MISSILE)==AmmunitionTable.Warhead.EMP
                        &&enemy.warhead(Battle.DamageType.MISSILE)==AmmunitionTable.Warhead.EMP,
                "AI and player share saved physical payloads and the same module research rules");
        for(int i=0;i<5;i++)battle.step();
        check(battle.missiles().stream().anyMatch(m->m.sourceId==attacker.id
                        &&m.warhead==AmmunitionTable.Warhead.EMP),
                "Runtime fires a real guided body with the saved researched warhead");
        System.out.println("PASS CD-SHIP-1/2/3 compatibility, player ammo fitting, 11 race save parity, anti-downgrade lockouts and live EMP launches");
    }
}
