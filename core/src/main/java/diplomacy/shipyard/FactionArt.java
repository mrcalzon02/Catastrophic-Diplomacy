package diplomacy.shipyard;

import diplomacy.sim.Battle;
import java.util.EnumMap;
import java.util.Map;

/**
 * Curated 2026-10-25 ship-art contract. Five distinct alien civilizations from
 * five independently approved transparent 2x5 fleet sheets. No source art
 * modification is performed by the game; sprites are imported using the
 * checksum-protected extractor archive.
 *
 * Existing six playable civilizations remain. These five receive provisional
 * gameplay/tech archetypes but independent selectable faction identities.
 */
public final class FactionArt {
    private FactionArt(){}
    public record Entry(String id,String name,Battle.Faction mechanics){}
    private static final EnumMap<Battle.Faction,Entry> ALIENS=new EnumMap<>(Battle.Faction.class);
    static {
        ALIENS.put(Battle.Faction.VAERITH_PRISM,new Entry("vaerith_prism",
                "Vaerith Prism Dominion",Battle.Faction.DRAXIAN));
        ALIENS.put(Battle.Faction.ZELTHAR_LUMINARI,new Entry("zelthar_luminari",
                "Zelthar Luminari Accord",Battle.Faction.UZIPT));
        ALIENS.put(Battle.Faction.AUREATE_SYNOD,new Entry("aureate_synod",
                "Aureate Cathedral Synod",Battle.Faction.MANDRALLY));
        ALIENS.put(Battle.Faction.NYX_BIOLITH,new Entry("nyx_biolith",
                "Nyx Biolith Enclave",Battle.Faction.CARNELIAN));
        ALIENS.put(Battle.Faction.UMBRA_FOUNDRY,new Entry("umbra_foundry",
                "Umbra Foundry Compact",Battle.Faction.ZIPPLEDROW));
    }
    public static Map<Battle.Faction,Entry> alienFactions(){return Map.copyOf(ALIENS);}
    /** Null: an existing faction without a new alien sheet. */
    public static Entry alien(Battle.Faction faction){return ALIENS.get(faction);}
    public static boolean supports(Battle.Faction faction){return ALIENS.containsKey(faction);}
    public static String hullFile(Shipyard.Hull hull) {
        if(hull==null)throw new IllegalArgumentException("Hull required");
        return switch(hull.spriteHull()){
            case INTERCEPTOR -> "interceptor";
            case CORVETTE -> "corvette";
            case MISSILE_CORVETTE -> "missile_corvette";
            case FRIGATE -> "frigate";
            case DESTROYER -> "destroyer";
            case LIGHT_CRUISER -> "light_cruiser";
            case CRUISER -> "cruiser";
            case HEAVY_CRUISER -> "heavy_cruiser";
            case BATTLECRUISER -> "battlecruiser";
            case CARRIER, DREADNOUGHT -> "carrier"; // ten original silhouettes per sheet
            default -> throw new IllegalArgumentException("Invalid sprite family");
        };
    }
    /**
     * Identity is authoritative: an approved faction sprite belongs ONLY to its
     * named civilization. Sharing a mechanics archetype is NEVER an art license.
     * A null entry means this faction needs its own approved asset import;
     * renderers must not display a different civilization's hull instead.
     */
    public static Entry visualArt(Battle.Faction faction){
        return faction==null?null:ALIENS.get(faction);
    }
    /** Prefix relative to the shared assets directory, not the source sheet. */
    public static String runtimePath(Battle.Faction faction,Shipyard.Hull hull){
        Entry art=visualArt(faction);
        if(art==null)return null;
        return "ships/factions/"+art.id()+"/"+hullFile(hull)+".png";
    }
}
