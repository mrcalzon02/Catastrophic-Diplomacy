package diplomacy.client;

/**
 * Pure presentation-only shield material rules. Never modifies shield energy,
 * combat state, or source ship silhouette. No Pixmap/GL access: headless-testable.
 */
final class ShieldSurfaceModel {
    private ShieldSurfaceModel(){}
    static int clamp(int value){return Math.min(255,Math.max(0,value));}
    /**
     * Detect the *outer silhouette*, not the join between real shield sectors.
     * Zero-alpha source pixels remain empty; immediate neighbor transitions
     * are stronger than the second sample for a soft two-pixel energy fringe.
     */
    static int edge(int center,int left,int right,int up,int down,
                    int left2,int right2,int up2,int down2) {
        if(center<=0)return 0;
        int near=Math.max(Math.max(center-left,center-right),Math.max(center-up,center-down));
        int far=Math.max(Math.max(center-left2,center-right2),Math.max(center-up2,center-down2));
        return clamp(Math.max(near,far/2));
    }
    /** RGBA8888: RGB drawn from *existing* shield material, colored only at edge. */
    static int material(int shieldMaterial,int sourceAlpha,int edge) {
        if(sourceAlpha<=0)return 0;
        int materialAlpha=shieldMaterial&255;
        if(materialAlpha<=0)return 0;
        float rim=clamp(edge)/255f;
        int a=clamp(Math.round(sourceAlpha*materialAlpha/255f*(.73f+.27f*rim)));
        if(a==0)return 0;
        int r=(shieldMaterial>>>24)&255;
        int g=(shieldMaterial>>>16)&255;
        int b=(shieldMaterial>>>8)&255;
        // Original material is authoritative, with pale cyan edge interference.
        int rr=clamp(Math.round(r*(1f-.40f*rim)+110f*.40f*rim));
        int gg=clamp(Math.round(g*(1f-.56f*rim)+224f*.56f*rim));
        int bb=clamp(Math.round(b*(1f-.65f*rim)+255f*.65f*rim));
        return (rr<<24)|(gg<<16)|(bb<<8)|a;
    }
    /** Stable on simulation tick: pause cannot animate without battle advancing. */
    static float opacity(float energyFraction,long tick,int shipId,int facing) {
        if(!Float.isFinite(energyFraction)||energyFraction<=.003f)return 0f;
        float energy=Math.min(1f,energyFraction);
        double phase=tick*.055+shipId*.117+facing*.73;
        float modulation=(float)(.925+.075*Math.sin(phase));
        return .40f*(float)Math.sqrt(energy)*modulation;
    }
    /** Never spend overdraw on shield shells smaller than a handful of pixels. */
    static boolean drawable(float spriteHeight,double viewWidth) {
        if(!(spriteHeight>0)||!(viewWidth>0))return false;
        return spriteHeight*1280.0/viewWidth>=5.5;
    }
}
