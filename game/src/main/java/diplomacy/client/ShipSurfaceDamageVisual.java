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

    /** Pixmap RGBA8888; nothing may extend beyond the original PNG alpha. */
    static int pixel(int original,float armorExposure,float internalDamage,float grit) {
        int originalAlpha=original&255;
        if(originalAlpha==0)return 0;
        int tinted=ShipDamageScorch.darken(original,armorExposure,grit);
        int alpha=Math.round(originalAlpha*(1f-opening(armorExposure,internalDamage,grit)));
        return (tinted&0xffffff00)|Math.max(0,Math.min(originalAlpha,alpha));
    }
}
