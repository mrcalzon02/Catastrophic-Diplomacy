package diplomacy.client;

/** Source-pixel preserving scorch coloration applied only to exposed armor. */
final class ShipDamageScorch {
    private ShipDamageScorch() {}
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    private static float smooth(float start,float end,float v){
        float t=clamp((v-start)/(end-start));
        return t*t*(3f-2f*t);
    }
    /**
     * Three narrow burn bands (char, soot, fading heat) centered on ONE
     * physically damaged armor cell, in local 16x16 grid coordinates.
     * Maximum reach is below 0.70 cell for a severe hit, without a hull-wide wash.
     * No allocation and no trigonometry in the damage-mask pixel loop.
     */
    static float falloffAt(float row,float col,float hitRow,float hitCol,
                           float armor,float structure,float rough){
        float severity=clamp(Math.max(armor,structure*.65f));
        if(severity<=0)return 0;
        float dx=col-hitCol,dy=row-hitRow;
        float radius=(.21f+.47f*(float)StrictMath.sqrt(severity))*
                     (1f+.04f*Math.max(-.5f,Math.min(.5f,rough)));
        float unit=(float)StrictMath.sqrt(dx*dx+dy*dy)/Math.max(.01f,radius);
        if(unit>=1f)return 0;
        float core=1f-smooth(.10f,.38f,unit);
        float soot=1f-smooth(.28f,.77f,unit);
        float halo=1f-smooth(.52f,1f,unit);
        return severity*(core*.50f+soot*.34f+halo*.16f);
    }
    /**
     * Pixmap RGBA8888 input and output. Actual damage drives the darkening;
     * procedural variation adds irregularity but never creates new alpha.
     */
    static int darken(int rgba,float exposure,float grit){
        if((rgba&255)==0||exposure<=0f)return rgba;
        // All geometry is bounded by falloffAt; roughness affects hue subtly
        // but cannot darken a pixel by itself outside a real impact field.
        float damage=clamp(exposure);
        float amount=clamp(damage*(.52f+Math.max(-.5f,Math.min(.5f,grit))*.06f));
        int r=(int)(((rgba>>>24)&255)*(1-amount)+.5f);
        int g=(int)(((rgba>>>16)&255)*(1-amount)+.5f);
        int b=(int)(((rgba>>>8)&255)*(1-amount)+.5f);
        return (r<<24)|(g<<16)|(b<<8)|(rgba&255);
    }
}
