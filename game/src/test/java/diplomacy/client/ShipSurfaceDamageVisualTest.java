package diplomacy.client;

/** Headless visual diagnostic; no OpenGL or synthetic gameplay screenshot. */
public final class ShipSurfaceDamageVisualTest {
    private ShipSurfaceDamageVisualTest(){}
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static int alpha(int rgba){return rgba&255;}
    public static void main(String[] args){
        int source=0xb0a090ff;
        check(ShipSurfaceDamageVisual.pixel(source,0,0,0)==source,"Intact hull pixels changed");
        check(ShipSurfaceDamageVisual.pixel(0x11223300,1,1,0)==0,"Original transparent silhouette expanded");
        int wear=ShipSurfaceDamageVisual.pixel(source,.35f,0,.2f);
        check(alpha(wear)==255 && (wear>>>8)<(source>>>8),
              "Nonpenetrating abrasion must scorch without cutting a hole");
        int crater=ShipSurfaceDamageVisual.pixel(source,.90f,0,0);
        check(alpha(crater)>0 && alpha(crater)<255,"Deep wear should make an irregular opening");
        int hole=ShipSurfaceDamageVisual.pixel(source,1,0,-.5f);
        check(alpha(hole)==0,"Stripped armor must reveal under-hull metal");
        int bypass=ShipSurfaceDamageVisual.pixel(source,.20f,.25f,0);
        check(alpha(bypass)>0 && alpha(bypass)<255,"Structural bypass must allow limited puncture");
        check(ShipSurfaceDamageVisual.pixel(source,0,0,.5f)==source,
              "Noise must not invent an intact ship's damage");
        for(float fraction:new float[]{0f,.35f,.72f,.90f,1f}){
            int semi=ShipSurfaceDamageVisual.pixel(0xb0a09080,fraction,0,.3f);
            check(alpha(semi)<=128,"Output alpha may never exceed original alpha");
        }
        // A transparent exterior and opaque silhouette-clipped sublayer are
        // separate physical render passes, not a cutout into empty space.
        check(alpha(hole)==0 && alpha(0x424c55ff)==255,
              "Structural layer must show through completely peeled exterior");
        check(ShipSurfaceDamageVisual.opening(.5f,0,.5f)==0,
              "Armor wear alone must not be a penetrating crater");
        check(ShipSurfaceDamageVisual.opening(1f,0,-.5f)==1,
              "Full armor depletion must remain visible despite pixel noise");
        System.out.println("PASS superficial scorch, penetrating alpha mask, bypass, source silhouette");
    }
}
