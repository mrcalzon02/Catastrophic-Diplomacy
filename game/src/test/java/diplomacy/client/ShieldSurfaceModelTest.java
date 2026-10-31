package diplomacy.client;

/** No OpenGL/Android emulator needed to validate shield material invariants. */
public final class ShieldSurfaceModelTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        check(ShieldSurfaceModel.edge(0,0,0,0,0,0,0,0,0)==0,
                "Outside silhouette cannot glow");
        check(ShieldSurfaceModel.edge(255,255,255,255,255,255,255,255,255)==0,
                "Shield interior must not form artificial sector seams");
        check(ShieldSurfaceModel.edge(255,0,255,255,255,0,255,255,255)==255,
                "Outer opaque hull boundary must create a rim");
        check(ShieldSurfaceModel.edge(255,255,255,255,255,0,255,255,255)>0,
                "Subsurface two-pixel edge should soften the outline");
        int shield=0x2576deff;
        check(ShieldSurfaceModel.material(shield,0,255)==0,
                "Transparent original pixel must stay transparent");
        check(ShieldSurfaceModel.material(0xFFFFFF00,255,255)==0,
                "Transparent original shield material must remain invisible");
        for(int alpha:new int[]{1,16,64,128,255}){
            for(int edge:new int[]{0,60,255}){
                int result=ShieldSurfaceModel.material(shield,alpha,edge);
                check((result&255)<=alpha,"Material exceeded original source alpha");
            }
        }
        check(ShieldSurfaceModel.opacity(0f,1,42,0)==0f,
                "Depleted facing cannot glow");
        check(ShieldSurfaceModel.opacity(.002f,1,42,0)==0f,
                "Nearly depleted facing must disappear");
        check(ShieldSurfaceModel.opacity(1,55,42,0)==
                ShieldSurfaceModel.opacity(1,55,42,0),
                "Same simulation tick must give identical shimmer");
        check(ShieldSurfaceModel.opacity(1,55,42,0) >
                ShieldSurfaceModel.opacity(.20f,55,42,0),
                "Low-charge shield must be dimmer");
        check(!ShieldSurfaceModel.drawable(12f,6000),
                "Far-away shield should not cost four blended quads");
        check(ShieldSurfaceModel.drawable(34f,1280),
                "Close camera must retain shield details");
        check(ShieldSurfaceModel.energizedOpacity(0,.8f,.4f)==0,
                "Collapsed facing cannot be relit by a lingering flash");
        check(ShieldSurfaceModel.energizedOpacity(.36f,0,0)==.36f,
                "No event must preserve authored shield material opacity");
        check(ShieldSurfaceModel.energizedOpacity(.36f,1,0)>.36f,
                "Real hit raises material luminosity");
        check(ShieldSurfaceModel.energizedOpacity(.36f,0,1)>.36f,
                "Real recharge creates a visible material pulse");
        check(ShieldSurfaceModel.energizedOpacity(.9f,1,1)<=.92f,
                "Full-charge effects remain capped to avoid overdraw blooms");
        // The original authored blue-hex texture must become an off-hull
        // field shell rather than only a hull-alpha tint.
        int brightHex=0x2A8AFFFF;
        int inner=ShieldSurfaceModel.shellMaterial(brightHex,.15);
        int rim=ShieldSurfaceModel.shellMaterial(brightHex,.91);
        check((inner&255)>0,"Shield PNG pattern must remain visible inside field");
        check((rim&255)>(inner&255),"Shield shell perimeter must be brighter than center");
        check(ShieldSurfaceModel.shellMaterial(brightHex,1.01)==0,
                "Shield shell must not draw a square PNG border");
        check(ShieldSurfaceModel.shellMaterial(0x2A8AFF00,.91)==0,
                "Transparent shield sprite pixel must remain transparent");
        check(ShieldSurfaceModel.shellMaterial(brightHex,Double.NaN)==0,
                "Bad source coordinates must not render");
        System.out.println("PASS silhouette shields, pulse, depletion, max alpha and strategic LOD");
    }
}
