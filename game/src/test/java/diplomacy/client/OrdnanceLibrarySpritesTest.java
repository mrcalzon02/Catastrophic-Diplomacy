package diplomacy.client;

import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;
import java.util.HashSet;
import java.util.Set;

/** Nonblocking authored-art diagnostic; no OpenGL required. */
public final class OrdnanceLibrarySpritesTest {
    private static void check(boolean ok,String problem){
        if(!ok)throw new AssertionError(problem);
    }
    public static void main(String[] args){
        Set<Integer> seen=new HashSet<>();
        for(int id=1;id<=1024;id++){
            long shot=id;
            for(OrdnanceDoctrine.Payload type:OrdnanceDoctrine.Payload.values()){
                int index=OrdnanceLibrarySprites.variantIndex(false,type,
                        AmmunitionTable.Warhead.STANDARD,shot,17);
                check(index>=0&&index<40,"Invalid missile body cell");
                seen.add(index);
            }
            int torpedo=OrdnanceLibrarySprites.variantIndex(true,OrdnanceDoctrine.Payload.HEAVY,
                    AmmunitionTable.Warhead.HIGH_EXPLOSIVE,shot,28);
            check(torpedo>=34&&torpedo<40,"Torpedo must use large torpedo silhouettes");
            seen.add(torpedo);
        }
        check(seen.size()>=38,"Physical variants must use nearly the entire authored atlas");
        check(!OrdnanceLibrarySprites.burning(OrdnancePropulsion.Phase.COLD_EJECT,10),
                "Ejection must have no attached main exhaust");
        check(!OrdnanceLibrarySprites.burning(OrdnancePropulsion.Phase.COAST,10),
                "Coasting missile must be truly plume-free");
        check(OrdnanceLibrarySprites.burning(OrdnancePropulsion.Phase.BOOST,10),
                "Boost plume must be animated");
        check(OrdnanceLibrarySprites.burning(OrdnancePropulsion.Phase.TERMINAL,10),
                "Terminal burn must reignite");
        check(!OrdnanceLibrarySprites.burning(OrdnancePropulsion.Phase.TERMINAL,0),
                "Empty engine may not fake an active plume");
        System.out.println("PASS: "+seen.size()+" distinct missile bodies reachable with propulsion-state plumes");
    }
}
