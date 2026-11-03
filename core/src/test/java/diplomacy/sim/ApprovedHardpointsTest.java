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
        System.out.println("PASS approved hardpoints: "+variants+
                " graphics/headless-identical hulls, "+sockets+
                " original socket anchors, fingerprint "+Long.toHexString(hash));
    }
}
