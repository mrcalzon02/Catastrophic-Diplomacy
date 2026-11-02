package diplomacy.client;

import diplomacy.sim.Battle;

/**
 * Localized alpha-cut armor stamps derived exclusively from the authoritative
 * per-tile armor and structural integrity. Never changes source sprite pixels,
 * combat damage, shields, hit points, or persistent game state.
 */
final class ShipArmorStamps {
    private ShipArmorStamps(){}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}

    /**
     * Weapon-specific visual armor opening. Combat tile wear and internal
     * damage remain authoritative; only the alpha silhouette differs.
     * Tiny high-velocity penetrators leave a narrow pucker. Torpedoes tear
     * a large, irregular cavity exposing the already-authored structural
     * underlay. Normal weapons retain the original physical stamp.
     */
    static float typedOpeningAt(float localRow,float localCol,int row,int col,
                                float stampedRow,float stampedCol,
                                float exposure,float structural,int shipId,
                                Battle.DamageType kind){
        if(kind!=Battle.DamageType.PARTICLE&&kind!=Battle.DamageType.TORPEDO)
            return openingAt(localRow,localCol,row,col,stampedRow,stampedCol,
                    exposure,structural,shipId);
        float wear=clamp(exposure),internal=clamp(structural);
        if(wear<=.70f&&internal<=.00001f)return 0f;
        float peeled=clamp((wear-.70f)/.20f);
        float puncture=(float)StrictMath.sqrt(internal);
        float dx=localCol-stampedCol,dy=localRow-stampedRow;
        float radius=(float)StrictMath.hypot(dx,dy);
        int hash=shipId*0x9e3779b9+row*0x7f4a7c15+col*0x6c8e9cf5;
        hash^=hash>>>16;
        float nx=dx/Math.max(.0001f,radius),ny=dy/Math.max(.0001f,radius);
        float depth=Math.max(peeled,Math.min(.80f,puncture*.80f));
        if(kind==Battle.DamageType.PARTICLE){
            // ~0.36 grid-cell maximum puncture, with a tiny puckered lip.
            // Spider cracking belongs to the RGB material, NOT the alpha.
            float asym=1f+(.07f*nx*ny)+((hash&7)-3)*.008f;
            float edge=(.15f+.16f*peeled+.05f*puncture)*asym;
            return depth*clamp((edge-radius)/.065f);
        }
        // Break the previous smooth four-lobed outline into sharp,
        // scale-separated tears. Piecewise angular teeth produce a truly
        // fractured perimeter even at the small 96x144 GPU mask resolution.
        // No per-pixel trig, no procedural paint over transparent source art.
        float ax=Math.abs(nx),ay=Math.abs(ny);
        float t=ay/Math.max(.0001f,ax+ay);
        float sectorAngle=nx>=0f ?
                (ny>=0f?t:4f-t):
                (ny>=0f?2f-t:2f+t);
        float largePhase=sectorAngle*3.5f;
        int largeSector=(int)StrictMath.floor(largePhase);
        float largeTooth=1f-2f*Math.abs(
                largePhase-(float)StrictMath.floor(largePhase)-.5f);
        int largeHash=hash^(largeSector*0x7f4a7c15);
        largeHash^=largeHash>>>16;
        float coarse=((largeHash&255)/255f-.50f)*.30f;
        float largeJag=(.11f+coarse)*largeTooth;
        float finePhase=sectorAngle*9f;
        int fineSector=(int)StrictMath.floor(finePhase);
        float fineTooth=1f-2f*Math.abs(
                finePhase-(float)StrictMath.floor(finePhase)-.5f);
        int fineHash=hash^(fineSector*0x6c8e9cf5);
        fineHash^=fineHash>>>13;
        float fineJag=(((fineHash>>>8&255)/255f)-.50f)*.22f*fineTooth;
        float edge=Math.min(1.27f,(.22f+.61f*peeled+.27f*puncture)*
                Math.max(.88f,Math.min(1.21f,1f+largeJag+fineJag)));
        float torn=depth*clamp((edge-radius)/.075f);
        // Five tapered, slightly bent tearing seams lead out from the
        // original puncture. They all share ONE genuine physical hit center;
        // nothing here adds independent damage, armor wear, or extra hits.
        // Their branches remain inside the 1.30-cell dirty-mask radius.
        for(int i=0;i<5;i++){
            float ux=switch(i){
                case 0 -> .97f; case 1 -> -.76f; case 2 -> -.17f;
                case 3 -> .49f; default -> -.40f;
            };
            float uy=switch(i){
                case 0 -> .24f; case 1 -> .65f; case 2 -> -.985f;
                case 3 -> -.87f; default -> -.915f;
            };
            float along=dx*ux+dy*uy;
            if(along<.70f||along>1.30f)continue;
            float across=dx*uy-dy*ux;
            float bend=(i%2==0?.19f:-.16f)*(along-.76f);
            float side=Math.abs(across-bend);
            float taper=.15f*(1f-clamp((along-1.12f)/.18f));
            float seam=depth*clamp((taper-side)/.052f)*
                    clamp((along-.70f)/.12f);
            torn=Math.max(torn,seam);
            // A short off-axis fork on alternating major tears: the
            // branch should read as bent plating, not a soft lumpy rim.
            if((i&1)==0&&along>.98f){
                float fork=Math.abs(across-bend-.38f*(along-.98f));
                float forkEdge=.10f*(1f-clamp((along-1.18f)/.12f));
                torn=Math.max(torn,depth*clamp((forkEdge-fork)/.04f)*
                        clamp((along-.98f)/.08f));
            }
        }
        return torn;
    }

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
