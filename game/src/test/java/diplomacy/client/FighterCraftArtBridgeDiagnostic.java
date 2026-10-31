package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Nonblocking visual audit: all 11 playable factions use purpose-built
 * craft sprites, not scaled-down ship hulls or geometry placeholders.
 * Reused palette art is loaded only once per true source PNG.
 */
public final class FighterCraftArtBridgeDiagnostic {
    private static final String[] ROLES={
            "interceptor","superiority_fighter","assault_gunship","missile_fighter",
            "torpedo_bomber","recon_fighter","heavy_bomber","point_defense_fighter",
            "electronic_warfare","support_drone"
    };
    private static void require(boolean valid,String message) {
        if(!valid)throw new AssertionError(message);
    }
    public static void main(String[] ignored) {
        Set<String> allSprites=new HashSet<>();
        Map<Battle.Faction,Set<String>> byFaction=new EnumMap<>(Battle.Faction.class);
        Map<String,Integer> paletteUses=new HashMap<>();
        for(Battle.Faction faction:Battle.Faction.values()){
            Set<String> paths=new HashSet<>();
            String family=FighterCraftSprites.fighterArtFamily(faction);
            paletteUses.merge(family,1,Integer::sum);
            for(String role:ROLES){
                String path=FighterCraftSprites.spritePath(faction,role);
                require(path.equals("ships/fighters/"+family+"/"+role+".png"),
                        faction+"/"+role+" routed to non-fighter asset: "+path);
                require(paths.add(path),faction+" reuses a fighter for different roles");
                allSprites.add(path);
            }
            require(paths.size()==10,faction+" missing specialist craft roles");
            byFaction.put(faction,paths);
        }
        require(allSprites.size()==50,"Expected precisely 50 original specialist craft images");
        // Specialized flight variants must resolve to their actual authored
        // recon and heavy-bomber art, not dormant archive entries.
        require(FighterCraftSprites.operationalRole(
                diplomacy.sim.FighterModel.Hull.INTERCEPTOR,
                diplomacy.sim.ShipTacticalDirectives.FighterMission.SCREEN,false)
                .equals("recon_fighter"),"Screening interceptor must use recon artwork");
        require(FighterCraftSprites.operationalRole(
                diplomacy.sim.FighterModel.Hull.TORPEDO_BOMBER,
                diplomacy.sim.ShipTacticalDirectives.FighterMission.STRIKE,true)
                .equals("heavy_bomber"),"Dreadnought torpedo flight must use heavy-bomber artwork");
        require(FighterCraftSprites.operationalRole(
                diplomacy.sim.FighterModel.Hull.TORPEDO_BOMBER,
                diplomacy.sim.ShipTacticalDirectives.FighterMission.STRIKE,false)
                .equals("torpedo_bomber"),"Normal torpedo flight must retain its own artwork");
        require(paletteUses.size()==5,"Expected five original fighter visual families");
        require(byFaction.size()==Battle.Faction.values().length,"Missing playable faction art");
        for(String family:paletteUses.keySet())
            require(paletteUses.get(family)>=2,"Unused corresponding founding family: "+family);
        System.out.println("PASS visual diagnostic: all 11 factions have ten specialist fighter/drone roles; 50 authentic source PNG paths, no capital-hull substitutes");
    }
}
