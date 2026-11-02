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
        // Material and alpha use one tile-seeded six-variant choice and
        // one impact heading. Variant selection does not depend on sample
        // position or animation time, so results survive reload/repaint.
        int variant=ShipDamageVariants.variant(seed,kind);
        float ax=ShipDamageVariants.localX(x,y,seed,kind);
        float ay=ShipDamageVariants.localY(x,y,seed,kind);
        if(kind==Battle.DamageType.LASER){
            // Angled thermal score with uneven iridescent oxide. The
            // annealed band stays tagged B>0.64 and remains non-emissive.
            float mark=ShipDamageVariants.distance(
                    (ax-.09f*((variant%3)-1))*.90f,
                    (ay+.07f*((variant/2)%3-1))*1.10f);
            float charred=ShipDamageScorch.falloffAt(
                    row,col,hr,hc,armor,internal,rough);
            float stripe=1f+.15f*ax-.09f*ay+
                    .07f*((variant%3)-1)*ax*ay;
            float annulus=smooth(.32f,.50f,mark)*
                    (1f-smooth(.84f,1.05f,mark));
            float strength=intensity*.56f*annulus*clamp(stripe);
            if(strength<.005f)return pack(charred,0,0);
            float phase=clamp(1f-smooth(.48f,1.30f,mark)+rough*.035f);
            return pack(charred,phase,.64f+.36f*strength);
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
                boolean torpedo=kind==Battle.DamageType.TORPEDO;
                float shiftedX=ax-.10f*((variant%3)-1);
                float shiftedY=ay+.08f*((variant/2)%3-1);
                float ellipse=ShipDamageVariants.distance(
                        shiftedX*(torpedo?.90f:.96f),
                        shiftedY*(torpedo?1.03f:1.13f));
                float limit=torpedo?1.28f:1.10f;
                if(d>1.30f)return 0;
                float physical=breachRadius(armor,internal);
                float soot=intensity*(torpedo?.75f:.66f)*
                    (1-smooth(.16f,torpedo?.83f:.70f,ellipse));
                float directionalRim=survivingLip(ellipse,physical,
                        intensity*(torpedo?.64f:.63f));
                soot=Math.max(soot,directionalRim);
                // A missile forms a raised crater with broken rim and
                // patchy hot ejecta. Torpedoes keep heat secondary to
                // the torn alpha hull, with glowing sheared plate teeth.
                float thermal=intensity*(torpedo?.42f:.49f)*
                    smooth(.29f,.49f,ellipse)*
                    (1-smooth(.84f,limit,ellipse))*
                    clamp(.67f+.24f*ax-.14f*ay);
                if(torpedo)
                    thermal=Math.max(thermal,survivingLip(ellipse,physical,
                            intensity*.38f));
                float fragments=0f;
                int count=torpedo?4+variant%3:5+variant%2;
                for(int i=0;i<count;i++){
                    int k=(i+variant*2)%DX.length;
                    float ux=DX[k],uy=DY[k];
                    float along=ax*ux+ay*uy;
                    float across=ax*uy-ay*ux;
                    float begin=.48f+.07f*(i%3);
                    float finish=(torpedo?1.25f:1.04f)-.04f*((i+variant)%3);
                    if(along<begin||along>finish)continue;
                    float skew=.14f*((i%3)-1)*(along-begin);
                    float fracture=(1-smooth(.026f,.102f,
                            Math.abs(across-skew)))*
                            smooth(begin,begin+.11f,along)*
                            (1-smooth(finish-.14f,finish,along));
                    fragments=Math.max(fragments,fracture);
                }
                // Sideward ridge/ejecta is uneven, not a concentric ring.
                float spall=intensity*.36f*
                    (1-smooth(.025f,.18f,Math.abs(ay-.32f*ax)))*
                    smooth(.47f,.66f,ax)*
                    (1-smooth(.84f,1.08f,ax));
                fragments=Math.max(fragments,spall);
                return pack(soot,thermal,
                        fragments*intensity*(torpedo?.61f:.80f));
            }
            case PLASMA:{
                // Flow direction, kink, and melt length depend on the
                // tile variant; this is not an orange circular outline.
                float bend=.13f*((variant%3)-1);
                float px=(ax+.11f*(variant%2==0?1f:-1f))*.83f;
                float py=(ay-.16f)*1.19f;
                float flow=ShipDamageVariants.distance(px,py);
                float melt=intensity*(1-smooth(.14f,.78f,flow));
                float tailX=px-bend*(ay-.12f);
                float tail=intensity*.72f*
                    (1-smooth(.05f,.21f,Math.abs(tailX)))*
                    smooth(.04f,.24f,ay)*
                    (1-smooth(.69f,1.12f,ay));
                float physical=breachRadius(armor,internal);
                float lip=survivingLip(flow,physical,intensity*.33f);
                float soot=intensity*.26f*
                    (1-smooth(.15f,.61f,flow));
                return pack(soot,Math.max(Math.max(melt,tail),lip),0);
            }
            case PARTICLE:{
                // Precision terminal puncture at the END of a narrow
                // off-axis approach scour. Variants differ in length,
                // bend, displaced edge, and crack topology. Dark cracks
                // and burrs lie on surviving plating; no second hit.
                float ty=.055f*((variant%3)-1);
                float px=.14f+.045f*(variant%3);
                float py=ty;
                float distance=ShipDamageVariants.distance(ax-px,ay-py);
                float dark=intensity*.78f*(1-smooth(.10f,.24f,distance));
                float metal=intensity*.58f*smooth(.16f,.23f,distance)*
                    (1-smooth(.28f,.37f,distance));
                float length=.50f+.09f*(variant%3);
                float roughTrack=ay-ty-
                    (.13f*((variant&1)==0?1f:-1f))*(ax-px);
                float along=px-ax;
                if(along>.03f&&along<length){
                    float track=(1-smooth(.025f,.125f,
                            Math.abs(roughTrack)))*
                            smooth(.02f,.14f,along)*
                            (1-smooth(length-.17f,length,along));
                    dark=Math.max(dark,intensity*.68f*track);
                    // Asymmetrical raised lip only on one side of the
                    // entry trough; opposite side stays dark and recessed.
                    metal=Math.max(metal,intensity*.53f*
                        (1-smooth(.018f,.105f,
                            Math.abs(roughTrack-.115f)))*
                        track);
                }
                for(int i=0;i<4+variant%3;i++){
                    int dir=(i+variant)%DX.length;
                    float ux=DX[dir],uy=DY[dir];
                    float rx=ax-px,ry=ay-py;
                    float a=rx*ux+ry*uy,b=rx*uy-ry*ux;
                    float limit=.55f+.10f*((i+variant)%3);
                    if(a<.18f||a>limit)continue;
                    float bend=.10f*((i%3)-1)*a;
                    float crack=(1-smooth(.027f,.10f,Math.abs(b-bend)))*
                        smooth(.15f,.29f,a)*
                        (1-smooth(limit-.12f,limit,a));
                    dark=Math.max(dark,intensity*.59f*crack);
                    metal=Math.max(metal,intensity*.29f*crack*
                        (1-smooth(.025f,.11f,Math.abs(b-bend-.075f))));
                }
                return pack(dark,0,metal);
            }
            case FIGHTER:{
                // Smallest scar: off-center pinhole plus unequal radial
                // needles, not a miniature copy of the kinetic crater.
                float px=.045f*((variant%3)-1),py=.04f*((variant/2)%3-1);
                float xx=ax-px,yy=ay-py;
                float r=ShipDamageVariants.distance(xx,yy);
                float dark=intensity*.74f*(1-smooth(.055f,.19f,r));
                float metal=intensity*.58f*smooth(.14f,.22f,r)*
                    (1-smooth(.27f,.33f,r));
                for(int i=0;i<4+variant%3;i++){
                    int dir=(i+variant*2)%DX.length;
                    float ux=DX[dir],uy=DY[dir];
                    float along=xx*ux+yy*uy;
                    float across=xx*uy-yy*ux;
                    float reach=.40f+.10f*((i+variant)%3);
                    if(along<.15f||along>reach)continue;
                    float spike=(1-smooth(.025f,.095f,
                            Math.abs(across+.07f*along)))*
                            smooth(.14f,.23f,along)*
                            (1-smooth(reach-.11f,reach,along));
                    dark=Math.max(dark,intensity*.59f*spike);
                    metal=Math.max(metal,intensity*.40f*spike);
                }
                return pack(dark,0,metal);
            }
            default:return 0;
        }
    }
}
