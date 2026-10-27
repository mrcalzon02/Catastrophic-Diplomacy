package diplomacy.shipyard;

import java.util.ArrayList;
import java.util.List;

/**
 * Ten artwork-size generations, not ten fixed ship designs.
 *
 * The source design doctrine alternates niche protection on odd (screen)
 * generations and niche compression on even (flagship) generations. This is
 * taxonomy; actual survivability, sensors, firepower and repair are determined
 * by the specific hull statistics and installed physical modules.
 *
 * No asset duplication: each chassis shares its faction/category sprite.
 */
public final class ShipLineages {
    private ShipLineages() { }
    public enum Ethos { SUPPORT_FLEET, FLAGSHIP_SQUADRON }
    public enum Role {
        FLAK_SCREEN("Tank / point-defense screen"),
        TORPEDO_STRIKER("Striker / torpedo attack"),
        ELECTRONIC_CONTROLLER("Controller / electronic warfare"),
        REPAIR_TENDER("Healer / fleet tender"),
        APEX_VANGUARD("Tank + striker / armored vanguard"),
        COMMAND_HUB("Leader + controller / fleet command"),
        LOGISTICS_JUGGERNAUT("Healer + mitigation / mobile support");
        public final String description;
        Role(String title){description=title;}
    }
    public record Lineage(int generation,Shipyard.Hull artwork,Ethos ethos,Role role){
        public Lineage {
            if(generation<1||generation>10||artwork==null||ethos==null||role==null)
                throw new IllegalArgumentException("Invalid ship lineage");
        }
    }
    public static Lineage of(Shipyard.Hull hull){
        if(hull==null)throw new IllegalArgumentException("Missing chassis");
        Shipyard.Hull artwork=hull.spriteHull();
        int generation=switch(artwork){
            case INTERCEPTOR -> 1;
            case CORVETTE -> 2;
            case MISSILE_CORVETTE -> 3;
            case FRIGATE -> 4;
            case DESTROYER -> 5;
            case LIGHT_CRUISER -> 6;
            case CRUISER -> 7;
            case HEAVY_CRUISER -> 8;
            case BATTLECRUISER -> 9;
            case CARRIER,DREADNOUGHT -> 10; // same curated capital art-size category
            default -> throw new IllegalArgumentException("Unmapped art-size generation: "+artwork);
        };
        Ethos ethos=generation%2==1?Ethos.SUPPORT_FLEET:Ethos.FLAGSHIP_SQUADRON;
        Role role=switch(hull){
            case INTERCEPTOR_FLAK,MISSILE_CORVETTE_FLAK,DESTROYER,DESTROYER_FLAK,
                    CRUISER,CRUISER_FLAK,BATTLECRUISER,BATTLECRUISER_FLAK -> Role.FLAK_SCREEN;
            case INTERCEPTOR,MISSILE_CORVETTE,DESTROYER_TORPEDO,
                    CRUISER_RAIDER,BATTLECRUISER_STRIKE -> Role.TORPEDO_STRIKER;
            case INTERCEPTOR_EW,MISSILE_CORVETTE_EW,DESTROYER_EW,CRUISER_CONTROLLER,
                    BATTLECRUISER_EW -> Role.ELECTRONIC_CONTROLLER;
            case INTERCEPTOR_TENDER,MISSILE_CORVETTE_TENDER,DESTROYER_TENDER,
                    CRUISER_SUPPORT,BATTLECRUISER_TENDER -> Role.REPAIR_TENDER;
            case CORVETTE,CORVETTE_GUNSHIP,FRIGATE,FRIGATE_RAIDER,
                    LIGHT_CRUISER,LIGHT_CRUISER_VANGUARD,HEAVY_CRUISER,
                    HEAVY_CRUISER_VANGUARD,DREADNOUGHT,DREADNOUGHT_SIEGE -> Role.APEX_VANGUARD;
            case CORVETTE_SCOUT,FRIGATE_MISSILE,LIGHT_CRUISER_COMMAND,
                    HEAVY_CRUISER_COMMAND,CARRIER -> Role.COMMAND_HUB;
            case CORVETTE_LOGISTICS,FRIGATE_ESCORT,LIGHT_CRUISER_LOGISTICS,
                    HEAVY_CRUISER_LOGISTICS,CARRIER_LOGISTICS -> Role.LOGISTICS_JUGGERNAUT;
        };
        return new Lineage(generation,artwork,ethos,role);
    }
    public static List<Shipyard.Hull> generation(int number){
        if(number<1||number>10)throw new IllegalArgumentException("Generation must be 1..10");
        List<Shipyard.Hull> result=new ArrayList<>();
        for(Shipyard.Hull hull:Shipyard.Hull.shipyardOrder())
            if(of(hull).generation()==number)result.add(hull);
        return List.copyOf(result);
    }
    /** Existing campaign starters at generation 2 remain legal at mission one. */
    public static int maximumCampaignGeneration(int unlockedMission){
        if(unlockedMission<1||unlockedMission>500)
            throw new IllegalArgumentException("Invalid campaign milestone");
        return Math.min(10,2+(unlockedMission-1)/50);
    }
    public static boolean availableInCampaign(Shipyard.Hull hull,int unlockedMission){
        return of(hull).generation()<=maximumCampaignGeneration(unlockedMission);
    }
}
