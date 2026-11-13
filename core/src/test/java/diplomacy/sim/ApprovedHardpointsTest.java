package diplomacy.sim;

import diplomacy.shipyard.ApprovedHardpointResource;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;

import java.util.HashSet;
import java.util.List;

/**
 * Headless artifact acceptance for ALL 506 original-art hardpoint configurations.
 * No GDX texture loader, OpenGL, cosmetic thresholds or alternate sprite art.
 */
public final class ApprovedHardpointsTest {
    private ApprovedHardpointsTest(){}
    private static void require(boolean good,String why){
        if(!good)throw new AssertionError(why);
    }
    public static void main(String[] args){
        HullLayouts.requireBakedHardpoints();
        int variants=0,sockets=0,changedFromSchematic=0;
        long hash=0xcbf29ce484222325L;
        for(Battle.Faction faction:Battle.Faction.values()){
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                List<Shipyard.Slot> baked=ApprovedHardpointResource.get(faction,hull);
                require(baked!=null&&baked.size()==hull.slots.size(),
                        "Missing original source-art positions "+faction+"/"+hull);
                List<Shipyard.Slot> actual=HullLayouts.slots(faction,hull);
                require(actual.equals(baked),"Client/server diverged from baked geometry");
                var seen=new HashSet<String>();
                for(int i=0;i<actual.size();i++){
                    Shipyard.Slot a=actual.get(i);
                    require(a.index()==i&&a.mount()==hull.slots.get(i).mount(),
                            "Baked geometry renumbered original saved socket "+i);
                    require(seen.add(a.x()+":"+a.y()),
                            "Baked physical hardpoints overlap: "+faction+"/"+hull);
                    hash=(hash^a.x())*0x100000001b3L;
                    hash=(hash^a.y())*0x100000001b3L;
                    hash=(hash^a.mount().ordinal())*0x100000001b3L;
                    sockets++;
                }
                Shipyard.Design blueprint=Shipyard.starter(faction,hull);
                require(blueprint.placements().equals(baked),
                        "Saved design does not share core hardpoints");
                for(var muzzle:WeaponMuzzles.equipped(blueprint,blueprint.placements())){
                    Shipyard.Slot fitted=baked.get(muzzle.slotId());
                    require(muzzle.localX()==fitted.x()&&muzzle.localY()==fitted.y(),
                            "Physical projectile origin differs from fitted socket");
                }
                variants++;
            }
        }
        // Rescanning or clearing a graphics diagnostic projector must leave
        // the authoritative resource unchanged, even in a headless server.
        HullLayouts.useSpriteProjector(null);
        require(ApprovedHardpointResource.available(),
                "Headless hardpoint map lost after cache invalidation");
        require(variants==506&&sockets>9000,
                "Incomplete 11-faction 46-variant original-art inventory");
        checkNarrowSilhouetteMuzzles();
        System.out.println("PASS approved hardpoints: "+variants+
                " graphics/headless-identical hulls, "+sockets+
                " original socket anchors, fingerprint "+Long.toHexString(hash));
    }

    /**
     * The all-506 inventory test above checks socket identity; this additional
     * headless battle-space check catches sign/rotation drift in real fitted
     * muzzle transforms on narrow original-art silhouettes. No renderer,
     * graphics backend, alternate projector or synthetic hulls.
     * The baked checksum is verified by requireBakedHardpoints() above.
     */
    private static void checkNarrowSilhouetteMuzzles(){
        Battle.Faction[] factions={
                Battle.Faction.VAERITH_PRISM,
                Battle.Faction.NYX_BIOLITH,
                Battle.Faction.AUREATE_SYNOD
        };
        Shipyard.Hull[] hulls={
                Shipyard.Hull.INTERCEPTOR,
                Shipyard.Hull.INTERCEPTOR_FLAK,
                Shipyard.Hull.LIGHT_CRUISER
        };
        Battle headless=new Battle(20261113L);
        int cases=0,origins=0;
        for(Battle.Faction faction:factions)for(Shipyard.Hull hull:hulls){
            Shipyard.Design fitted=Shipyard.starter(faction,hull);
            List<Shipyard.Slot> sockets=ApprovedHardpointResource.get(faction,hull);
            require(fitted.placements().equals(sockets),
                    "Narrow source-art shipyard layout differs from headless resource");
            var emitters=WeaponMuzzles.equipped(fitted,fitted.placements());
            require(emitters.size()>=2,
                    "Narrow original-art hull must retain real fitted weapon mounts");
            Battle.Ship ship=headless.addShip(0,faction,180+cases*28,240+cases*13);
            ship.hullClass=hull;
            ship.weaponEmitters=emitters;
            for(double heading:new double[]{0,Math.PI/2,Math.PI,3*Math.PI/2}){
                ship.facing=heading;
                double scale=34.0/154.0*ship.visualScale();
                double cos=StrictMath.cos(heading),sin=StrictMath.sin(heading);
                for(var emitter:emitters){
                    Shipyard.Slot slot=sockets.get(emitter.slotId());
                    require(slot.index()==emitter.slotId()
                                    &&slot.x()==emitter.localX()
                                    &&slot.y()==emitter.localY(),
                            "Narrow source-art fitted socket ordinal or coordinates changed");
                    var actual=WeaponMuzzles.atSlot(ship,emitter.family(),emitter.slotId());
                    double expectedX=ship.x+slot.y()*scale*cos+slot.x()*scale*sin;
                    double expectedY=ship.y+slot.y()*scale*sin-slot.x()*scale*cos;
                    require(actual.slotId()==slot.index()
                                    &&Math.abs(actual.x()-expectedX)<1e-9
                                    &&Math.abs(actual.y()-expectedY)<1e-9,
                            "Headless fitted muzzle differs from original-art world origin: "+
                                    faction+"/"+hull+"/"+slot.index()+"/"+heading);
                    origins++;
                }
            }
            cases++;
        }
        require(cases==9&&origins>=72,
                "Incomplete narrow original-art fitted-muzzle regression");
        System.out.println("PASS narrow original-art headless muzzle parity: "+
                cases+" faction/hull samples, "+origins+" rotated fitted origins");
    }
}
