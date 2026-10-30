package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/**
 * Small, repeatable inspection fixtures: one fitted ship of EVERY canonical
 * sprite-size class on each side (11v11), plus isolated 1v1 hull duels.
 * These are actual battles, not fake rendering-only proxies.
 */
public final class HullCoverageBattle {
    public static final long SEED=0x48454c4c5f43444cL;
    public static final List<Shipyard.Hull> SIZES=List.of(
            Shipyard.Hull.INTERCEPTOR,Shipyard.Hull.CORVETTE,
            Shipyard.Hull.MISSILE_CORVETTE,Shipyard.Hull.FRIGATE,
            Shipyard.Hull.DESTROYER,Shipyard.Hull.LIGHT_CRUISER,
            Shipyard.Hull.CRUISER,Shipyard.Hull.HEAVY_CRUISER,
            Shipyard.Hull.BATTLECRUISER,Shipyard.Hull.CARRIER,
            Shipyard.Hull.DREADNOUGHT);
    private static final String[] WEAPONS={"LAS","KIN","PLA","PAR","MSL","TOR"};
    /** Each side represents ONE civilization across every hull size. */
    public static final Battle.Faction[] SIDES={
            Battle.Faction.VAERITH_PRISM,Battle.Faction.AUREATE_SYNOD
    };
    private HullCoverageBattle(){}

    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int n){
        for(Shipyard.Slot point:hull.slots)
            if(point.mount()==mount&&n--==0)return point.index();
        return -1;
    }
    public static Shipyard.Design design(TechnologyCatalog tech,int size,int side){
        if(size<0||size>=SIZES.size()||side<0||side>1)
            throw new IllegalArgumentException("Invalid hull coverage fixture");
        Shipyard.Hull hull=SIZES.get(size);
        Battle.Faction faction=SIDES[side];
        Shipyard.Design base=Shipyard.starter(faction,hull);
        if(!Shipyard.inspect(base,tech).valid())
            throw new IllegalStateException("Starter must be buildable: "+faction+" "+hull);

        // Alternate weapon and support choices across the eleven ship sizes.
        // Only accept fully legal fitted combinations; no free test-only arms.
        String prefix="BASE-"+Shipyard.factionId(faction)+"-";
        Shipyard.Design preferred=base;
        for(int mount=0;mount<2;mount++){
            int index=slot(hull,Shipyard.Mount.WEAPON,mount);
            if(index<0)continue;
            for(int attempt=0;attempt<WEAPONS.length;attempt++){
                String type=WEAPONS[(size*2+side*3+mount+attempt)%WEAPONS.length];
                Shipyard.Design candidate=preferred.install(index,prefix+type);
                if(Shipyard.inspect(candidate,tech).valid()){
                    preferred=candidate;break;
                }
            }
        }
        int support=slot(hull,Shipyard.Mount.SUPPORT,0);
        if(support>=0){
            String[] choices={"FTR","DRN","MAG","REP"};
            for(int attempt=0;attempt<choices.length;attempt++){
                String kind=choices[(size+side+attempt)%choices.length];
                Shipyard.Design candidate=preferred.install(support,prefix+kind);
                if(Shipyard.inspect(candidate,tech).valid()){
                    preferred=candidate;break;
                }
            }
        }
        if(!Shipyard.inspect(preferred,tech).valid())
            throw new IllegalStateException("Hull test fitted invalid machinery");
        return preferred;
    }
    /** Isolated same-size, two correctly and consistently identified factions. */
    public static Battle duel(TechnologyCatalog tech,int size){
        return Battle.duel(SEED+size,1,design(tech,size,0),design(tech,size,1),tech);
    }
    /** Eleven differently sized ships per faction, ONE coherent art family per side. */
    public static Battle mixed(TechnologyCatalog tech){
        Battle battle=new Battle(SEED);
        for(int side=0;side<2;side++)for(int i=0;i<SIZES.size();i++){
            // Distributed vertically so no class obscures another at startup.
            // Both opposing formations are comfortably inside the 1280x720 view.
            double x=(side==0?290:990)+(side==0?1:-1)*(i%3)*28;
            double y=112+i*45;
            battle.spawnWing(side,design(tech,i,side),tech,1,x,y);
        }
        return battle;
    }
}
