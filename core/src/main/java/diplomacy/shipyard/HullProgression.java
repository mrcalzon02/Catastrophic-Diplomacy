package diplomacy.shipyard;

/** Shared technology authorization for physical base hulls and specialist variants. */
public final class HullProgression {
    private HullProgression() {}
    public static boolean base(Shipyard.Hull hull) {
        if(hull==null)throw new IllegalArgumentException("Missing hull");
        return hull==hull.spriteHull();
    }
    public static int requiredTier(Shipyard.Hull hull) {
        if(hull==null)throw new IllegalArgumentException("Missing hull");
        if(base(hull))return switch(hull) {
            case INTERCEPTOR,CORVETTE -> 0;
            case MISSILE_CORVETTE,FRIGATE,DESTROYER -> 1;
            case LIGHT_CRUISER,CRUISER,HEAVY_CRUISER,BATTLECRUISER -> 2;
            case CARRIER,DREADNOUGHT -> 3;
            default -> throw new IllegalArgumentException("Unknown base hull "+hull);
        };
        return switch(ShipLineages.of(hull).role()) {
            case FLAK_SCREEN,TORPEDO_STRIKER,APEX_VANGUARD -> 4;
            case ELECTRONIC_CONTROLLER,COMMAND_HUB -> 5;
            case REPAIR_TENDER,LOGISTICS_JUGGERNAUT -> 6;
        };
    }
    public static boolean available(Shipyard.Hull hull,int tier) {
        if(tier<0||tier>9)throw new IllegalArgumentException("Invalid technology tier");
        return hull!=null&&requiredTier(hull)<=tier;
    }
    public static int baseFamiliesAtTier(int tier) {
        int count=0;
        for(Shipyard.Hull hull:Shipyard.Hull.values())
            if(base(hull)&&available(hull,tier))count++;
        return count;
    }
    public static String tierLabel(int tier) {
        return switch(tier) {
            case 0 -> "STARTER HULLS";
            case 1 -> "ESCORT HULLS";
            case 2 -> "CRUISER HULLS";
            case 3 -> "CAPITAL HULLS";
            case 4 -> "STRIKE VARIANTS";
            case 5 -> "COMMAND VARIANTS";
            case 6 -> "SUPPORT VARIANTS";
            case 7 -> "ELITE SYSTEMS";
            case 8 -> "EXPERIMENTAL SYSTEMS";
            case 9 -> "APEX ENGINEERING";
            default -> throw new IllegalArgumentException("Invalid technology tier");
        };
    }
}
