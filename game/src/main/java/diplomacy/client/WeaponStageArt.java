package diplomacy.client;

import diplomacy.fx.WeaponFxProfile;

/** Original authored weapon muzzle and impact sprite atlas routing. */
final class WeaponStageArt {
    static final String CURATED="effects/projectile_fx/curated/weapon_fire_impact_64.png";
    static final int CELL=128,COLS=8,ROWS=8,SIZE=CELL*COLS;
    private WeaponStageArt(){}

    /** Return atlas row or -1 for other original shot families. */
    static int row(WeaponFxProfile.Clip clip){
        if(clip==null)return -1;
        return switch(clip.pattern()){
            case "effects/weapon_fx/laser/firing_%02d.png" -> 0;
            case "effects/weapon_fx/laser/impact_%02d.png" -> 1;
            case "effects/weapon_fx/kinetic/firing_%02d.png" -> 2;
            case "effects/weapon_fx/kinetic/impact_%02d.png" -> 3;
            case "effects/weapon_fx/plasma/firing_%02d.png" -> 4;
            case "effects/weapon_fx/plasma/impact_%02d.png" -> 5;
            case "effects/weapon_fx/particle/firing_%02d.png" -> 6;
            case "effects/weapon_fx/particle/impact_%02d.png" -> 7;
            default -> -1;
        };
    }
}
