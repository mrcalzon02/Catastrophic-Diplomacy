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
        return openingAt(localRow,localCol,row,col,row,col,exposure,structural,shipId);
    }
    static float openingAt(float localRow,float localCol,int row,int col,
                           float stampedRow,float stampedCol,
                           float exposure,float structural,int shipId){
        float wear=clamp(exposure),inside=clamp(structural);
        if(wear<=.70f&&inside<=.00001f)return 0f;
        float peeled=clamp((wear-.70f)/.20f);
        float puncture=(float)StrictMath.sqrt(inside);
        if(peeled<=0f&&puncture<=0f)return 0f;

        float dx=localCol-stampedCol,dy=localRow-stampedRow;
        // Multi-lobed, stable metal tearing, not a uniform grey disc.
        int hash=shipId*0x9e3779b9+row*0x7f4a7c15+col*0x6c8e9cf5;
        hash^=hash>>>16;
        // A bounded asymmetric combination of second/third radial harmonics.
        // No trig per pixel: damage-mask uploads must stay cheap in 400-ship
        // battles even as multiple plates change at once.
        float distance=(float)StrictMath.sqrt(dx*dx+dy*dy);
        float nx=dx/Math.max(.0001f,distance);
        float ny=dy/Math.max(.0001f,distance);
        float a=((hash&255)/255f-.5f)*.28f;
        float b=(((hash>>>8)&255)/255f-.5f)*.22f;
        float c=(((hash>>>16)&255)/255f-.5f)*.16f;
        float d=(((hash>>>24)&255)/255f-.5f)*.16f;
        float teeth=a*(nx*nx-ny*ny)+b*(2*nx*ny)
                +c*nx*(nx*nx-3*ny*ny)+d*ny*(3*nx*nx-ny*ny);
        float radius=(.12f+.50f*peeled+.20f*puncture)
                *Math.max(.76f,Math.min(1.24f,1f+teeth));
        float edge=clamp((radius-distance)/.095f);
        // Deeply eroded/stripped cells have a truly transparent center.
        // Bypass damage can expose some structure before armor reaches zero.
        float depth=Math.max(peeled,Math.min(.80f,puncture*.80f));
        return clamp(depth*edge);
    }
}
