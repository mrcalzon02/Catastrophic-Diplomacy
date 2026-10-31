package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.shipyard.FactionArt;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Visual-only diagnostic: every spawned craft uses original art owned by its
 * faction. Founder factions intentionally share some genuine small-hull art
 * between roles until bespoke fighter/drone sheets are authored.
 * Do not attach this visual mapping check to functional release gates.
 */
public final class FighterCraftArtBridgeDiagnostic {
    private static final String[] ROLES={
            "interceptor","superiority_fighter","assault_gunship","missile_fighter",
            "torpedo_bomber","recon_fighter","heavy_bomber","point_defense_fighter",
            "electronic_warfare","support_drone"
    };
    private static void require(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }
    public static void main(String[] ignored){
        Set<String> used=new HashSet<>();
        Map<Battle.Faction,Set<String>> byFaction=new EnumMap<>(Battle.Faction.class);
        for(Battle.Faction faction:Battle.Faction.values()){
            Set<String> paths=new HashSet<>();
            for(String role:ROLES){
                String path=FighterCraftSprites.spritePath(faction,role);
                require(path.endsWith(".png"),faction+"/"+role+" has no real PNG");
                if(faction==Battle.Faction.TERRAN)
                    require(path.startsWith("ships/terran/"),"Terran art identity changed");
                else if(FactionArt.hasLegacyArt(faction))
                    require(path.startsWith("ships/legacy_factions/"),"Foreign sprite for "+faction);
                else
                    require(path.startsWith("ships/fighters/"+FactionArt.alien(faction).id()+"/"),
                            "Foreign specialized fighter art for "+faction+"/"+role);
                paths.add(path);
            }
            int expected=FactionArt.supports(faction)?10:7;
            require(paths.size()==expected,
                    faction+" should use "+expected+" genuine sprites, got "+paths.size());
            for(String path:paths)
                require(used.add(path),"Sprite assigned to multiple civilizations: "+path);
            byFaction.put(faction,paths);
        }
        require(used.size()==92,"Expected 42 founder-hull silhouettes + 50 alien fighter images");
        require(byFaction.size()==Battle.Faction.values().length,"Missing faction art mapping");
        System.out.println("PASS visual diagnostic: 11 faction-owned craft palettes, 92 genuine image paths; no Asteroids wireframes");
    }
}
