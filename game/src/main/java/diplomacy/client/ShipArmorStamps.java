package diplomacy.client;

/**
 * Localized alpha-cut armor stamps derived exclusively from the authoritative
 * per-tile armor and structural integrity. Never changes source sprite pixels,
 * combat damage, shields, hit points, or persistent game state.
 */
final class ShipArmorStamps {
    private ShipArmorStamps(){}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}

    /**
     * Pixel coordinates in defense-grid tile units; tile centers are integer
     * row/column. Crater lips are jagged but stable across rendering and saves.
     */
    static float openingAt(float localRow,float localCol,int row,int col,
                           float exposure,float structural,int shipId){
        float wear=clamp(exposure),inside=clamp(structural);
        if(wear<=.70f&&inside<=.00001f)return 0f;
        float peeled=clamp((wear-.70f)/.20f);
        float puncture=(float)StrictMath.sqrt(inside);
        if(peeled<=0f&&puncture<=0f)return 0f;

        float dx=localCol-col,dy=localRow-row;
        // Multi-lobed, stable metal tearing, not a uniform grey disc.
        int hash=shipId*0x9e3779b9+row*0x7f4a7c15+col*0x6c8e9cf5;
        hash^=hash>>>16;
        float phase=(hash&0xffff)*(float)(2*StrictMath.PI/65536.0);
        float angle=(float)StrictMath.atan2(dy,dx);
        float teeth=(float)(.13*StrictMath.sin(5*angle+phase)
                        +.065*StrictMath.sin(9*angle-phase*.73)
                        +.05*StrictMath.cos(3*angle+phase*.43));
        float radius=(.12f+.50f*peeled+.20f*puncture)
                *Math.max(.72f,Math.min(1.23f,1f+teeth));
        float distance=(float)StrictMath.hypot(dx,dy);
        float edge=clamp((radius-distance)/.095f);
        // Deeply eroded/stripped cells have a truly transparent center.
        // Bypass damage can expose some structure before armor reaches zero.
        float depth=Math.max(peeled,Math.min(.80f,puncture*.80f));
        return clamp(depth*edge);
    }
}
