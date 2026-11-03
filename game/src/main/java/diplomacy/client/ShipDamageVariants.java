package diplomacy.client;

import diplomacy.sim.Battle;

/**
 * One reproducible shape and impact direction per real struck armor tile.
 * No mutable random generator, per-pixel allocations, additional hits, or
 * influence on Battle damage/armor. Renderer and alpha mask share this key.
 */
final class ShipDamageVariants {
    private ShipDamageVariants(){}
    private static final float[] AX={1f,.70710677f,0f,-.70710677f,-1f,
                                     -.70710677f,0f,.70710677f};
    private static final float[] AY={0f,.70710677f,1f,.70710677f,0f,
                                     -.70710677f,-1f,-.70710677f};
    // Precomputed one-degree sin/cos avoid expensive per-texel trigonometry.
    // Authoritative ship physics/impact positions never use these tables.
    private static final float[] COS=new float[360],SIN=new float[360];
    static {
        for(int degree=0;degree<360;degree++){
            COS[degree]=(float)StrictMath.cos(StrictMath.toRadians(degree));
            SIN[degree]=(float)StrictMath.sin(StrictMath.toRadians(degree));
        }
    }
    static int tileSeed(int shipId,int row,int col){
        return shipId*31+row*17+col*7;
    }
    static int hash(int seed,Battle.DamageType kind){
        int h=seed*0x9e3779b9 + (kind==null?0:kind.ordinal()+1)*0x7f4a7c15;
        h^=h>>>16;h*=0x85ebca6b;h^=h>>>13;h*=0xc2b2ae35;
        return h^(h>>>16);
    }
    static int variant(int seed,Battle.DamageType kind){
        return Math.floorMod(hash(seed,kind),6);
    }
    static int direction(int seed,Battle.DamageType kind){
        return (hash(seed,kind)>>>9)&7;
    }
    /** Seeded micro-rotation of an authored mask, NOT the actual hit heading. */
    static int maskAngleDegrees(int seed,Battle.DamageType kind){
        int spread=switch(kind){
            case LASER -> 12;
            case PARTICLE -> 16;
            case PLASMA -> 22;
            case TORPEDO -> 35;
            case MISSILE, KINETIC -> 30;
            case FIGHTER -> 25;
        };
        int salt=hash(seed^0x5aa5c393,kind);
        int offset=Math.floorMod(salt,spread*2+1)-spread;
        return Math.floorMod(direction(seed,kind)*45+offset,360);
    }
    /** Minor variation, with an unchanged global 2.667-cell bounding box. */
    static float maskScale(int seed,Battle.DamageType kind){
        int salt=hash(seed^0x2e73b48d,kind);
        float unit=(salt>>>8&65535)/65535f;
        float minimum=kind==Battle.DamageType.LASER?.94f:.91f;
        float maximum=kind==Battle.DamageType.LASER?1.06f:1.10f;
        return minimum+(maximum-minimum)*unit;
    }
    static float maskCos(int angle){return COS[Math.floorMod(angle,360)];}
    static float maskSin(int angle){return SIN[Math.floorMod(angle,360)];}
    static float localX(float dx,float dy,int seed,Battle.DamageType kind){
        int d=direction(seed,kind);
        return dx*AX[d]+dy*AY[d];
    }
    static float localY(float dx,float dy,int seed,Battle.DamageType kind){
        int d=direction(seed,kind);
        return dy*AX[d]-dx*AY[d];
    }
    static float clamp(float n){return Math.max(0,Math.min(1,n));}
    static float smooth(float a,float b,float x){
        float t=clamp((x-a)/(b-a));return t*t*(3f-2f*t);
    }
    static float distance(float x,float y){
        return (float)StrictMath.hypot(x,y);
    }
    /**
     * Distinct asymmetric physical opening for seven real weapon families.
     * Radii are measured in the existing 16x16 armor tile coordinate system.
     * The render caller also clips output against authored sprite alpha.
     */
    static float opening(Battle.DamageType kind,float dx,float dy,
                         float exposure,float structural,int seed){
        float wear=clamp(exposure),internal=clamp(structural);
        if((wear<=.70f&&internal<=.00001f)||kind==null)return 0f;
        float peeled=clamp((wear-.70f)/.20f);
        float punch=(float)StrictMath.sqrt(internal);
        float depth=Math.max(peeled,Math.min(.8f,punch*.8f));
        int variant=variant(seed,kind);
        float x=localX(dx,dy,seed,kind);
        float y=localY(dx,dy,seed,kind);
        float radius=distance(x,y);
        float dist;
        float edge;
        switch(kind){
            case PARTICLE:{
                // Grazing track approaches from -X. Only the terminal
                // precision puncture removes alpha; long drag is material.
                float tx=.11f+.055f*(variant%3),ty=(variant%2==0?.045f:-.06f);
                float px=(x-tx)/(.25f+.025f*(variant%2));
                float py=(y-ty)/(.19f+.02f*((variant/2)%2));
                dist=distance(px,py);
                return depth*(1f-smooth(.78f,1.12f,dist));
            }
            case FIGHTER:{
                float px=(x-.055f*(variant%3-1))/.19f;
                float py=(y+.045f*((variant/2)%3-1))/.16f;
                dist=distance(px,py);
                // Tiny notched puncture, never a scaled missile crater.
                float notch=1f+.10f*(px*px-py*py)/Math.max(.1f,dist*dist);
                return depth*(1f-smooth(.76f,1.15f,dist/Math.max(.83f,notch)));
            }
            case TORPEDO:{
                // Jagged multi-scale torn metal: discontinuous coarse teeth,
                // small notches, and asymmetrically branching ruptures.
                float ax=Math.abs(x),ay=Math.abs(y);
                float t=ay/Math.max(.0001f,ax+ay);
                float polar=x>=0?(y>=0?t:4f-t):(y>=0?2f-t:2f+t);
                float coarsePhase=polar*(3f+.5f*(variant%3));
                float finePhase=polar*(8f+(variant%4));
                int coarseSector=(int)StrictMath.floor(coarsePhase);
                int fineSector=(int)StrictMath.floor(finePhase);
                float coarse=1f-2f*Math.abs((coarsePhase-coarseSector)-.5f);
                float fine=1f-2f*Math.abs((finePhase-fineSector)-.5f);
                int ch=hash(seed+coarseSector*73,kind);
                int fh=hash(seed+fineSector*119,kind);
                float edgeScale=1f+
                    (((ch&255)/255f)-.58f)*.42f*coarse+
                    ((((fh>>>8)&255)/255f)-.52f)*.27f*fine;
                float base=.21f+.61f*peeled+.28f*punch;
                float rupture=depth*(1f-smooth(-.048f,.072f,radius-
                        base*Math.max(.70f,Math.min(1.24f,edgeScale))));
                // Broken uneven seams, with forks on alternating branches.
                // Some variants tear into one long narrow slot, others
                // into several sharp projecting plate teeth.
                int branches=4+variant%3;
                for(int i=0;i<branches;i++){
                    int branchSeed=hash(seed+i*193,kind);
                    int dir=(i+((branchSeed>>>5)&7))&7;
                    float ux=AX[dir],uy=AY[dir];
                    float along=x*ux+y*uy;
                    float across=x*uy-y*ux;
                    float max=1.11f+.035f*(i%3);
                    if(along<.62f||along>max)continue;
                    float bend=((branchSeed&1)==0?.18f:-.20f)*(along-.64f);
                    float width=(.11f+.022f*(variant%3))*
                            (1f-smooth(max-.23f,max,along));
                    float seam=depth*(1f-smooth(0f,.07f,
                            Math.abs(across-bend)-width))*
                            smooth(.61f,.74f,along);
                    rupture=Math.max(rupture,seam);
                    if((i&1)==0&&along>.88f){
                        float fork=Math.abs(across-bend-.43f*(along-.88f));
                        rupture=Math.max(rupture,depth*.75f*
                            (1f-smooth(.028f,.105f,fork))*
                            smooth(.88f,.97f,along)*
                            (1f-smooth(max-.15f,max,along)));
                    }
                }
                return clamp(rupture);
            }
            case MISSILE:{
                float px=(x-.11f*(variant%3-1))/.89f;
                float py=(y+.13f*((variant/2)%3-1))/1.11f;
                dist=distance(px,py);
                float angle=px/Math.max(.01f,dist);
                float broken=1f+.13f*angle-.10f*py/
                        Math.max(.01f,dist)+.11f*((variant%3)-1)*angle*py;
                edge=(.16f+.47f*peeled+.19f*punch)*broken;
                return depth*(1f-smooth(-.065f,.09f,dist-edge));
            }
            case PLASMA:{
                // Slanted melt-through and flow, not a radial blast bowl.
                float px=(x-.14f*(variant%3-1))/.82f;
                float py=(y-.11f)/1.18f;
                dist=distance(px,py);
                edge=.12f+.41f*peeled+.21f*punch;
                return depth*(1f-smooth(-.065f,.08f,dist-edge));
            }
            case LASER:{
                float px=(x-.065f*((variant%3)-1))/.89f;
                float py=(y-.065f*((variant/2)%3-1))/1.08f;
                dist=distance(px,py);
                edge=.12f+.43f*peeled+.20f*punch;
                return depth*(1f-smooth(-.075f,.10f,dist-edge));
            }
            case KINETIC:{
                // Keep the approved cold metallic/grey pockmark and
                // outer fractures. Vary only the physical hole perimeter.
                float px=x*(1f+.035f*((variant%3)-1));
                float py=y*(1f+.04f*((variant/2)%3-1));
                float a=px/Math.max(.001f,radius),b=py/Math.max(.001f,radius);
                float teeth=.07f*a+.05f*b+.075f*(a*a-b*b)*
                        ((variant&1)==0?1f:-1f);
                edge=(.12f+.50f*peeled+.20f*punch)*
                        (1f+teeth);
                return depth*(1f-smooth(-.08f,.095f,radius-edge));
            }
            default:return 0f;
        }
    }
}
