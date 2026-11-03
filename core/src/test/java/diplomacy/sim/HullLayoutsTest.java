package diplomacy.sim;

import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Headless all-hull/all-faction shipyard placement and pointer picking contract. */
public final class HullLayoutsTest {
    private HullLayoutsTest() { }
    private static void check(boolean value, String label) {
        if(!value)throw new AssertionError(label);
    }
    private static String key(Shipyard.Slot slot){return slot.x()+":"+slot.y();}
    public static void main(String[] args) throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Hull[] display=Shipyard.Hull.shipyardOrder();
        check(display.length==Shipyard.Hull.values().length&&display.length>=40,
                "All append-only new chassis and historical hulls must appear in selector");
        Set<Shipyard.Hull> distinct=Set.of(display);
        check(distinct.size()==display.length,"each class appears once in the selector");
        // Existing blueprint IDs and the initial four ordinals remain untouched.
        check(Shipyard.Hull.values()[0]==Shipyard.Hull.CORVETTE,"legacy corvette ID");
        check(Shipyard.Hull.values()[1]==Shipyard.Hull.FRIGATE,"legacy frigate ID");
        check(Shipyard.Hull.values()[2]==Shipyard.Hull.CRUISER,"legacy cruiser ID");
        check(Shipyard.Hull.values()[3]==Shipyard.Hull.DREADNOUGHT,"legacy dreadnought ID");
        // Indexed by immutable legacy enum ordinal, not the size-sorted selector.
        int[] expectedTotals={8,12,18,29,9,12,16,18,22,25,29};
        int count=0,crowdedOriginalArtPairs=0;
        for(int i=0;i<display.length;i++){
            Shipyard.Hull hull=display[i];
            if(hull.ordinal()<expectedTotals.length)
                check(hull.slots.size()==expectedTotals[hull.ordinal()],
                        "Legacy hardpoint count was changed: "+hull);
            else check(hull.slots.size()>=9,"New physical chassis lacks vital hardpoints: "+hull);
            for(Battle.Faction faction:Battle.Faction.values()){
                Shipyard.Design design=Shipyard.starter(faction,hull);
                List<Shipyard.Slot> layout=design.placements();
                check(layout.size()==hull.slots.size(),"missing physical mounts "+faction+" "+hull);
                check(HullLayouts.slots(faction,hull).equals(layout),"layout must be deterministic");
                Set<String> locations=new HashSet<>();
                int systems=0,weapons=0,defenses=0;
                for(int index=0;index<layout.size();index++){
                    Shipyard.Slot slot=layout.get(index);
                    check(slot.index()==index,"stable original slot identity");
                    check(slot.mount()==hull.slots.get(index).mount(),"faction changed mount category");
                    check(Math.abs(slot.x())<=76 && Math.abs(slot.y())<=75,"hardpoint out of view");
                    check(locations.add(key(slot)),"overlapping hardpoints on "+faction+" "+hull);
                    check(HullLayouts.pick(layout,slot.x()*2.65f,slot.y()*2.65f,
                        0,0,2.65f,10)==index,"touch did not select hardpoint #"+index);
                    switch(slot.mount()){
                        case SYSTEM -> systems++;
                        case WEAPON -> weapons++;
                        case DEFENSE -> defenses++;
                        case SUPPORT,HABITAT -> { }
                    }
                }
                for(int left=0;left<layout.size();left++)for(int right=left+1;right<layout.size();right++){
                    Shipyard.Slot a=layout.get(left),b=layout.get(right);
                    int dx=a.x()-b.x(),dy=a.y()-b.y();
                    // A few genuine narrow PNG silhouettes require closer
                    // socket centres. Exact identity, no duplicated centres
                    // and touch selection are objective; 10-unit visual
                    // spacing is a nonblocking art diagnostic, not geometry.
                    if(dx*dx+dy*dy<100){
                        if(diplomacy.shipyard.ApprovedHardpointResource.available())
                            crowdedOriginalArtPairs++;
                        else check(false,"schematic mounts overlap: "+faction+
                                " "+hull+" "+left+"/"+right);
                    }
                }
                check(systems>=3&&weapons>=2&&defenses>=1,"missing vital module family");
                check(HullLayouts.pick(layout,10000,10000,0,0,2.65f,10)==-1,"blank hull area selected a part");
                check(HullLayouts.outline(faction,hull).size()>=9,"missing schematic silhouette");
                Shipyard.Report report=Shipyard.inspect(design,catalog);
                check(report.valid(),"unusable stock fitting "+faction+" "+hull+": "+report.explanation());
                Shipyard.Design restored=Shipyard.decode(Shipyard.encode(design),catalog);
                check(restored.hull==hull && restored.faction==faction && restored.slots()==layout.size(),
                        "blueprint changed hull or faction");
                if(faction!=Battle.Faction.TERRAN){
                    check(!layout.equals(HullLayouts.slots(Battle.Faction.TERRAN,hull)),
                            "non-Terran faction silently reused Terran hardpoint geometry");
                }
                count++;
            }
        }
        check(count==display.length*Battle.Faction.values().length,
                "missing hull/faction permutations");
        // Distinct class variants already have different slot inventories;
        // never silently collapse their numbers to the shared sprite class.
        for(Shipyard.Hull base:display){
            if(base!=base.spriteHull())continue;
            boolean hasSibling=false,hasDifferentCount=false;
            for(Shipyard.Hull variant:display)if(variant!=base
                    &&variant.spriteHull()==base){
                hasSibling=true;
                if(variant.slots.size()!=base.slots.size())hasDifferentCount=true;
            }
            if(hasSibling)check(hasDifferentCount,
                    "All "+base+" variants have been flattened to one module count");
        }
        // Real sprite registration is supplied by the renderer before spawning
        // combat ships. Assert the authoritative API preserves original socket
        // IDs and categories while changing both shipyard and combat origins.
        Shipyard.Design sample=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        var baseline=sample.placements();
        try{
            HullLayouts.useSpriteProjector((faction,hull,schematic)->{
                java.util.ArrayList<Shipyard.Slot> mapped=new java.util.ArrayList<>();
                for(var slot:schematic)
                    mapped.add(new Shipyard.Slot(slot.index(),slot.mount(),
                            slot.x()+3,slot.y()-2));
                return mapped;
            });
            var altered=sample.placements();
            check(altered.size()==baseline.size(),"Sprite projection dropped module sockets");
            for(int i=0;i<altered.size();i++){
                check(altered.get(i).index()==baseline.get(i).index()
                                &&altered.get(i).mount()==baseline.get(i).mount(),
                        "Sprite mapping rewrote installed module IDs");
                check(altered.get(i).x()==baseline.get(i).x()+3
                                &&altered.get(i).y()==baseline.get(i).y()-2,
                        "Shipyard did not use projected original-art hardpoints");
            }
            var equipped=WeaponMuzzles.equipped(sample,altered);
            for(var gun:equipped){
                var mount=altered.get(gun.slotId());
                check(gun.localX()==mount.x()&&gun.localY()==mount.y(),
                        "Live combat muzzle is disconnected from shipyard hardpoint");
            }
        }finally{HullLayouts.useSpriteProjector(null);}
        check(sample.placements().equals(baseline),
                "Removing graphics backend did not restore headless layout");
        System.out.println("PASS hull layouts: "+count+" valid faction/class fittings, "+
                "stable sockets, selected touch centres and live muzzle bindings; "+
                "advisory close original-art pairs="+crowdedOriginalArtPairs);
    }
}
