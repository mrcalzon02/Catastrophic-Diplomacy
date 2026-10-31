package diplomacy.sim;

import diplomacy.shipyard.OrdnanceFitting;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Genuine blueprint+world integration tests, not design-only validators. */
public final class OrdnanceBlueprintTest {
    private OrdnanceBlueprintTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    private static void reject(Runnable action,String reason){
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(reason);
    }
    private static int missileSlot(Shipyard.Design design){
        for(int i=0;i<design.slots();i++)
            if(design.at(i)!=null&&design.at(i).component.endsWith("-MSL"))return i;
        throw new AssertionError("Starter missing missile module");
    }
    public static void main(String[] ignored)throws Exception{
        var catalog=TechnologyCatalog.loadDefault();
        var old=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int socket=missileSlot(old);
        var selected=new OrdnanceFitting.Guided(OrdnanceDoctrine.Mode.MICRO_SWARM,
                OrdnancePropulsion.Mode.SILENT_GHOST,OrdnanceGuidance.Mode.PINCER);
        reject(()->old.withGuidedOrdnance(Battle.DamageType.MISSILE,selected),
                "Basic missiles may not use advanced propulsion");
        var upgraded=old.stage(socket,Shipyard.Track.SPECIALIST,4);
        check(Shipyard.inspect(upgraded,catalog).valid(),
                "Installed tier-4 missile research must still fit within the hull");
        var configured=upgraded.withGuidedOrdnance(Battle.DamageType.MISSILE,selected);
        String wire=Shipyard.encode(configured);
        check(wire.startsWith("CD-SHIP-4|"),
                "Non-stock missile launcher needs versioned blueprint encoding");
        var restored=Shipyard.decode(wire,catalog);
        check(restored.missileOrdnance.equals(selected)
                    &&Shipyard.encode(restored).equals(wire),
                "Whole missile launcher fit must survive exact deterministic readback");
        check(restored.torpedoOrdnance.equals(OrdnanceFitting.Guided.STANDARD),
                "Missile launcher editing cannot silently change torpedo configuration");
        check(Shipyard.encode(old).startsWith("CD-SHIP-1|")
                    &&Shipyard.decode(Shipyard.encode(old),catalog).missileOrdnance
                    .equals(OrdnanceFitting.Guided.STANDARD),
                "Version 1 designs must remain byte-compatible and preserve defaults");
        reject(()->configured.stage(socket,Shipyard.Track.SPECIALIST,3),
                "Removing required technology cannot leave a tier-4 motor installed");
        reject(()->configured.install(socket,null),
                "Uninstall must not leave an orphaned advanced launcher profile");
        reject(()->Shipyard.decode(wire.replace("SILENT_GHOST","NOT_AN_ENGINE"),catalog),
                "Invalid serialized guided engine must be rejected");
        reject(()->Shipyard.decode(wire.replace("|0|0|0|4|","|0|0|0|3|"),catalog),
                "Downgraded serialized launcher cannot bypass tier authorization");
        var next=restored.withFighterHull(FighterModel.Hull.ASSAULT_GUNSHIP)
                .withAmmoSelection(Shipyard.AmmoSelection.STANDARD);
        check(next.missileOrdnance.equals(selected),"Other shipyard edits must preserve missiles");
        Battle world=new Battle(71417L);
        world.spawnWing(0,restored,catalog,1,200,320);
        world.spawnWing(1,old,catalog,1,690,320);
        var s=world.ships().get(0);
        check(s.launchMode(Battle.DamageType.MISSILE)==selected.salvo()
                    &&s.propulsionMode(Battle.DamageType.MISSILE)==selected.motor()
                    &&s.guidanceMode(Battle.DamageType.MISSILE)==selected.guidance(),
                "Deployed ship must actually inherit all three selected combat controls");
        Battle legacy=new Battle(71418L);
        legacy.spawnWing(0,old,catalog,1,200,320);
        var stock=legacy.ships().get(0);
        reject(()->stock.setPropulsionMode(Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.SILENT_GHOST),
                "Live fitted ships must not bypass the technology gate");
        // Both sides are deployed, so the real missile controller has a target.
        for(int i=0;i<35;i++)world.step();
        check(s.launched>0&&world.missiles().stream().anyMatch(m->
                    m.sourceId==s.id
                    &&m.propulsionMode==selected.motor()
                    &&m.guidanceMode==selected.guidance()),
                "Physical missiles must use persisted engineering rather than defaults");
        System.out.println("PASS versioned launcher blueprint, 9-tier research, old designs,"
                +" editing invariants, real battle spawn, missile flight and authorization");
    }
}
