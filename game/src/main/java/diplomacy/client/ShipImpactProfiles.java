package diplomacy.client;

import diplomacy.sim.Battle;

/** Pure, deterministic local damage marks. R=dark, G=hot, B=bright metal. */
final class ShipImpactProfiles {
    private ShipImpactProfiles(){}
    private static final float[] DX={.98f,.50f,-.38f,-.91f,-.46f,.47f,.82f};
    private static final float[] DY={.17f,.87f,.92f,.41f,-.89f,-.88f,-.57f};
    private static float clamp(float n){return Math.max(0f,Math.min(1f,n));}
    private static float smooth(float a,float b,float v){
        float t=clamp((v-a)/(b-a));return t*t*(3f-2f*t);
    }
    private static int pack(float dark,float hot,float metal){
        return Math.round(clamp(dark)*255f)<<16|
               Math.round(clamp(hot)*255f)<<8|Math.round(clamp(metal)*255f);
    }
    static int dark(int v){return v>>>16&255;}
    static int hot(int v){return v>>>8&255;}
    static int metal(int v){return v&255;}
    static int merge(int a,int b){
        return (Math.max(dark(a),dark(b))<<16)|
                (Math.max(hot(a),hot(b))<<8)|Math.max(metal(a),metal(b));
    }
    /** Real armor-hole reach for the SAME physical mask geometry. */
    private static float breachRadius(float armor,float internal){
        float peeled=clamp((armor-.70f)/.20f);
        return .12f+.50f*peeled+.20f*(float)StrictMath.sqrt(clamp(internal));
    }
    /** A thin edge just OUTSIDE the physical hole. Never drawn inside missing alpha. */
    private static float survivingLip(float dist,float radius,float intensity){
        return intensity*smooth(radius-.09f,radius+.075f,dist)*
                (1f-smooth(radius+.16f,radius+.35f,dist));
    }
    /** Compact shadowed pit, thin bright sheared rim, raised short fractures. */
    private static int pock(float x,float y,float s,int id,boolean heavy,float scale,
                            float physicalRadius){
        float r=(float)StrictMath.hypot(x,y);
        // At full armor penetration the alpha opening is ~0.82 cells.
        // The OLD pock stopped at 0.73 cells, wholly INSIDE that hole:
        // the fragment shader correctly erased every intended highlight.
        // Keep the real crater as the dark pit, but move the sheared edge
        // to SURVIVING plating on the outside of the physical opening.
        float radius=Math.max((.22f+.23f*(float)StrictMath.sqrt(s))*scale,
                physicalRadius);
        // Raised fracture spokes must extend beyond the circular rim.
        // The old +.34 radius early-exit silently clipped strokes whose
        // explicit ends reach +.46. Keep every sample within 1.30 cells.
        if(r>Math.min(heavy?1.30f:1.12f,radius+.52f*scale))return 0;
        float q=r/Math.max(.01f,radius);
        float dark=s*.75f*(1-smooth(.12f,.56f,q));
        float bright=s*.60f*smooth(.88f,1.04f,q)*
                (1-smooth(1.16f,1.35f,q));
        if(physicalRadius>.45f)
            bright=Math.max(bright,survivingLip(r,physicalRadius,s*.63f));
        // Break the metallic ring at deterministic chipped sectors.
        int chip=(id*13+((int)(x*42))*31+((int)(y*42))*17)&7;
        if(chip<3)bright*=.38f;
        for(int i=0;i<(heavy?7:4);i++){
            int k=Math.floorMod(id+i,DX.length);
            float along=x*DX[k]+y*DY[k];
            float across=x*DY[k]-y*DX[k];
            // The mask is only 96x144 (6x9 samples per armor cell).
            // The former .07-cell hairline was narrower than one texel and
            // vanished in real captures. Use a short two-sided ridge: bright
            // sheared metal next to its darker fracture shadow, on plating
            // that survives the physical cutout.
            float end=Math.min(1.30f,radius+(.32f+(i%3)*.07f)*scale);
            float start=radius*.91f;
            if(along<start||along>end)continue;
            float ridge=smooth(start,radius+.035f*scale,along)*
                    (1f-smooth(end-.12f*scale,end,along));
            float crest=1f-smooth(.025f*scale,.17f*scale,
                    Math.abs(across+.045f*scale));
            float shadow=1f-smooth(.035f*scale,.18f*scale,
                    Math.abs(across-.16f*scale));
            bright=Math.max(bright,s*.91f*ridge*crest);
            dark=Math.max(dark,s*.62f*ridge*shadow);
        }
        return pack(dark,0,bright);
    }
    static int sample(Battle.DamageType kind,float row,float col,
                      float hr,float hc,float armor,float internal,
                      int seed,float rough){
        float intensity=clamp(Math.max(armor,internal*.65f));
        if(intensity<=0)return 0;
        float x=col-hc,y=row-hr;
        float d=(float)StrictMath.hypot(x,y);
        if(d>1.30f)return 0;
        if(kind==null)
            return pack(ShipDamageScorch.falloffAt(row,col,hr,hc,armor,internal,rough),0,0);
        if(kind==Battle.DamageType.LASER){
            // Cool, optically thin heat-oxide interference colors on the
            // *surviving* plating around the actual laser strike. A charcoal
            // center remains separate; the annealed annulus fades by 1.05 cells.
            // G carries gold -> bronze -> violet -> blue oxide phase while
            // B>0.64 tags the annealing coefficient in the existing mask.
            // No new textures, damage events, or per-frame animations.
            float charred=ShipDamageScorch.falloffAt(
                    row,col,hr,hc,armor,internal,rough);
            float annulus=smooth(.32f,.50f,d)*
                    (1f-smooth(.84f,1.05f,d));
            float strength=intensity*.56f*annulus;
            if(strength<.005f)return pack(charred,0,0);
            float oxidePhase=clamp(1f-smooth(.48f,1.30f,d)+rough*.035f);
            return pack(charred,oxidePhase,.64f+.36f*strength);
        }
        switch(kind){
            case KINETIC:{
                boolean heavy=internal>.15f||armor>.86f;
                float physical=breachRadius(armor,internal);
                int p=pock(x,y,intensity,seed,heavy,1f,physical);
                if(heavy){
                    // Two tiny subsidiary pocks near the real opening edge,
                    // rather than underneath the already-removed armor.
                    p=merge(p,pock(x-.76f,y+.16f,intensity*.62f,
                            seed+19,false,.43f,0));
                    p=merge(p,pock(x+.22f,y+.78f,intensity*.58f,
                            seed+29,false,.42f,0));
                }
                // Approved pre-1f92 material profile: compact dark
                // indentation with raised silver fractures, no broad
                // brown/amber rim. Reverted after real-framebuffer review.
                // Do not reintroduce the rejected thermal mask.
                return p;
            }
            case MISSILE, TORPEDO:{
                // Missiles leave compact soot and small fragmented spall.
                // Torpedoes leave fewer, coarser shards and a wider hot halo.
                boolean torpedo=kind==Battle.DamageType.TORPEDO;
                float scale=torpedo?1.08f:.90f;
                float q=d/scale;
                if(d>(torpedo?1.19f:.99f))return 0;
                float physical=breachRadius(armor,internal);
                float soot=intensity*(torpedo?.83f:.68f)*
                        (1-smooth(.15f,.83f,q));
                // Preserve original blast center, but show the ring ON intact
                // armor outside the transparent puncture. Torpedoes have a
                // slightly wider halo, still bounded to 1.3 cells.
                soot=Math.max(soot,survivingLip(d,physical,
                        intensity*(torpedo?.72f:.54f)));
                float heat=intensity*(torpedo?.54f:.21f)*
                        smooth(.22f,.42f,q)*(1-smooth(.68f,.94f,q));
                heat=Math.max(heat,survivingLip(d,physical,
                        intensity*(torpedo?.42f:.19f)));
                // A torpedo pushes out asymmetric, hot faceted plating;
                // a missile leaves many small cool shrapnel scratches.
                if(torpedo){
                    float facet=Math.max(Math.abs(x*.84f+y*.23f),
                            Math.abs(y*.90f-x*.21f));
                    heat=Math.max(heat,intensity*.36f*
                            smooth(.63f,.79f,facet)*
                            (1-smooth(.91f,1.12f,facet))*
                            smooth(-.20f,.55f,x-y*.4f));
                }
                float chips=0;
                for(int k=0;k<(torpedo?3:5);k++){
                    float outward=physical>.47f?(torpedo?1.7f:1.35f):1f;
                    float px=(k==0?.46f:k==1?-.52f:k==2?.14f:k==3?-.28f:.55f)*scale*outward;
                    float py=(k==0?.14f:k==1?-.24f:k==2?-.66f:k==3?.53f:.41f)*scale*outward;
                    float fragment=(float)StrictMath.hypot(x-px,y-py);
                    float mark=1-smooth(.028f,(torpedo?.18f:.11f)*scale,fragment);
                    if(!torpedo){
                        float tangent=Math.abs((x-px)*.64f-(y-py)*.77f);
                        float radial=(x-px)*.77f+(y-py)*.64f;
                        mark=Math.max(mark,(1-smooth(.03f,.105f,tangent))*
                                (1-smooth(.10f,.23f,Math.abs(radial)))*.68f);
                    }
                    chips=Math.max(chips,mark);
                }
                return pack(soot,heat,chips*intensity*(torpedo?.62f:.88f));
            }
            case PLASMA:{
                // Directional thermal melt, not a cold metal crack or
                // an explosive shrapnel field.
                if(d>1.06f)return 0;
                float physical=breachRadius(armor,internal);
                float flow=(float)StrictMath.hypot((x+.12f)*.78f,(y-.10f)*1.19f);
                float melt=intensity*(1-smooth(.13f,.82f,flow));
                float tail=intensity*.40f*
                        (1-smooth(.04f,.20f,Math.abs(x+.09f)))*
                        smooth(.07f,.23f,y)*(1-smooth(.65f,.93f,y));
                melt=Math.max(melt,tail);
                float rim=survivingLip(d,physical,intensity*.72f);
                return pack(intensity*.24f*(1-smooth(.15f,.49f,flow)),
                        Math.max(melt,rim),0);
            }
            case PARTICLE:{
                // Pin-sized central puncture, with 7 hairline fracture
                // spokes and short stress branches in surviving plating.
                // This does NOT fabricate additional combat impacts.
                if(d>1.02f)return 0;
                float dark=intensity*.74f*(1-smooth(.10f,.25f,d));
                float metal=intensity*.40f*smooth(.26f,.36f,d)*
                        (1-smooth(.38f,.48f,d));
                for(int i=0;i<7;i++){
                    // One horizontal spoke makes the difference measurable
                    // in the 96x144 damage mask; others radiate outward.
                    float ux=i==0?1f:DX[(i+seed)%DX.length];
                    float uy=i==0?0f:DY[(i+seed)%DY.length];
                    float along=x*ux+y*uy;
                    float across=x*uy-y*ux;
                    if(along<.24f||along>1.00f)continue;
                    float warp=(i%3-1)*.035f*along;
                    float stroke=smooth(.23f,.34f,along)*
                            (1-smooth(.87f,1.00f,along));
                    float fissure=1-smooth(.035f,.115f,Math.abs(across-warp));
                    dark=Math.max(dark,intensity*.77f*stroke*fissure);
                    float burr=1-smooth(.025f,.10f,
                            Math.abs(across-warp-.095f));
                    metal=Math.max(metal,intensity*.35f*stroke*burr);
                    if((i&1)==0){
                        // Branch takes a short angled step away from
                        // the main crack at ~0.62 radius.
                        float branchAlong=(along-.59f)*.93f+
                                (across-warp)*.36f;
                        float branchAcross=(across-warp)*.93f-
                                (along-.59f)*.36f;
                        if(branchAlong>0f&&branchAlong<.30f)
                            dark=Math.max(dark,intensity*.46f*
                                (1-smooth(.035f,.095f,Math.abs(branchAcross)))*
                                (1-smooth(.20f,.30f,branchAlong)));
                    }
                }
                return pack(dark,0,metal);
            }
            case FIGHTER:{
                // Small shallow munition contact, not a capital strike.
                if(d>1.00f)return 0;
                float physical=breachRadius(armor,internal);
                int body=pock(x,y,intensity*.48f,seed,false,.48f,0f);
                return merge(body,pack(0,0,survivingLip(d,physical,intensity*.40f)));
            }
            default:return 0;
        }
    }
}
