package diplomacy.client;

/** Source-pixel preserving scorch coloration applied only to exposed armor. */
final class ShipDamageScorch {
    private ShipDamageScorch() {}
    /**
     * Pixmap RGBA8888 input and output. Actual damage drives the darkening;
     * procedural variation adds irregularity but never creates new alpha.
     */
    static int darken(int rgba,float exposure,float grit){
        if((rgba&255)==0||exposure<=0f)return rgba;
        float damage=Math.min(1f,Math.max(0f,exposure));
        float edge=Math.min(1f,Math.max(0f,damage*.85f+grit*.28f));
        float amount=edge*.42f;
        int r=(int)(((rgba>>>24)&255)*(1-amount)+.5f);
        int g=(int)(((rgba>>>16)&255)*(1-amount)+.5f);
        int b=(int)(((rgba>>>8)&255)*(1-amount)+.5f);
        return (r<<24)|(g<<16)|(b<<8)|(rgba&255);
    }
}
