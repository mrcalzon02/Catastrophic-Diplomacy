package diplomacy.client;

/** Pixel-only material specialization, never a second combat damage model. */
final class ShipSurfaceDamageVisual {
    private ShipSurfaceDamageVisual() {}
    private static float clamp(float n){return Math.max(0f,Math.min(1f,n));}

    static float opening(float armorExposure,float internalDamage,float grit) {
        float armor=clamp(armorExposure);
        // Floating-point threshold rounding and crater-lip noise must never
        // leave ghost plating on a physically exhausted armor cell.
        if(armor>=1f-0.00001f)return 1f;
        float stripped=clamp((armor-.72f)/.28f);
        float lip=stripped*(1f-stripped)*4f;
        float uneven=clamp(stripped+Math.max(-.5f,Math.min(.5f,grit))*.18f*lip);
        // Armor bypass can expose a limited interior patch before the plating
        // is fully stripped; merely scuffed armor remains completely opaque.
        float puncture=clamp((float)Math.sqrt(clamp(internalDamage))*.70f);
        return Math.max(uneven,puncture);
    }

    /**
     * Diffuse armor wear colors the original plating without a global alpha
     * fade. Only a localized impact crater can expose the structural layer.
     */
    static int stampedPixel(int original,float diffuseWear,float craterOpening,float grit){
        int originalAlpha=original&255;
        if(originalAlpha==0)return 0;
        int tinted=ShipDamageScorch.darken(original,diffuseWear,grit);
        int alpha=Math.round(originalAlpha*(1f-clamp(craterOpening)));
        return (tinted&0xffffff00)|Math.max(0,Math.min(originalAlpha,alpha));
    }

    /**
     * The exposed metal is a silhouette-clipped sublayer, not another ship
     * sprite. Darken RGB to 40% of the previous 30/70 composite; preserve
     * the original silhouette alpha. Empty PNG pixels must remain empty.
     * A fully transparent material sample must not turn an opaque source
     * pixel into undefined black/transparent data: use the source palette.
     */
    static int interiorPixel(int source,int material) {
        int a=source&255;
        if(a==0)return 0;
        boolean materialExists=(material&255)!=0;
        int r=materialExists?(((source>>>24)&255)*3+((material>>>24)&255)*7)/10
                            :(source>>>24)&255;
        int g=materialExists?(((source>>>16)&255)*3+((material>>>16)&255)*7)/10
                            :(source>>>16)&255;
        int b=materialExists?(((source>>>8)&255)*3+((material>>>8)&255)*7)/10
                            :(source>>>8)&255;
        return (Math.round(r*.40f)<<24)|(Math.round(g*.40f)<<16)
                |(Math.round(b*.40f)<<8)|a;
    }

    /**
     * Hot torn-plating rim: local mask alpha gradient, not a glow billboard.
     * There MUST be both a real source hull pixel and a clipped interior
     * pixel underneath. Never draw on transparent source artwork or an empty
     * damage underlayer.
     */
    static float breachRim(float opening,float left,float right,float up,
                           float down,int sourceAlpha,int interiorAlpha) {
        if(sourceAlpha<=0||interiorAlpha<=0||opening<=.07f||opening>=.98f)
            return 0;
        float gradient=Math.max(Math.max(Math.abs(opening-left),Math.abs(opening-right)),
                        Math.max(Math.abs(opening-up),Math.abs(opening-down)));
        float edge=clamp((gradient-.04f)*5f);
        // A worn but still present armor lip carries the heated metal,
        // while the center of the hole remains fully transparent.
        return edge*clamp((1f-opening)*2.2f);
    }

    /** Controlled hot-rim tint ONLY within the remaining armor alpha. */
    static int stampedPixel(int original,float diffuseWear,float craterOpening,
                            float edgeGlow,float grit) {
        int base=stampedPixel(original,diffuseWear,craterOpening,grit);
        int a=base&255;
        if(a==0)return 0;
        float glow=clamp(edgeGlow);
        if(glow==0)return base;
        int r=(base>>>24)&255,g=(base>>>16)&255,b=(base>>>8)&255;
        r=Math.round(r+(255-r)*glow);
        g=Math.round(g+(178-g)*glow*.90f);
        b=Math.round(b+(75-b)*glow*.75f);
        r=Math.max(0,Math.min(255,r));
        g=Math.max(0,Math.min(255,g));
        b=Math.max(0,Math.min(255,b));
        return (r<<24)|(g<<16)|(b<<8)|a;
    }

    /** R=dark, G=thermal, B=sheared silver, A=remaining plating. */
    static int maskPixel(float dark,float opening,float heat,float metal){
        return Math.round(clamp(dark)*255f)<<24|
                Math.round(clamp(heat)*255f)<<16|
                Math.round(clamp(metal)*255f)<<8|
                Math.round((1f-clamp(opening))*255f);
    }
    static int maskPixel(float dark,float opening,float heat){
        return maskPixel(dark,opening,heat,0);
    }
    /** Pixmap RGBA8888; nothing may extend beyond the original PNG alpha. */
    static int pixel(int original,float armorExposure,float internalDamage,float grit) {
        int originalAlpha=original&255;
        if(originalAlpha==0)return 0;
        int tinted=ShipDamageScorch.darken(original,armorExposure,grit);
        int alpha=Math.round(originalAlpha*(1f-opening(armorExposure,internalDamage,grit)));
        return (tinted&0xffffff00)|Math.max(0,Math.min(originalAlpha,alpha));
    }
}
