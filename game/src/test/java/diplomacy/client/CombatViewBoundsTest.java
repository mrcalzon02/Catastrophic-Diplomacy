package diplomacy.client;
/** Large-fleet visual culling: edge intersections, zoom and panning. */
public final class CombatViewBoundsTest {
    private static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] ignored){
        var full=new CombatViewBounds(640,360,1280,720);
        ok(full.circle(640,360,0),"center");
        ok(full.circle(1290,360,11),"partially overlapping edge");
        ok(!full.circle(1400,360,10),"outside edge");
        ok(full.segment(-100,360,1400,360,0),"long beam crossing viewport");
        ok(!full.segment(-100,-100,1400,-100,1),"offscreen beam rejected");
        var close=new CombatViewBounds(50,10,200,100);
        ok(close.circle(150,10,0),"zoom edge included");
        ok(!close.circle(200,10,1),"far hull culled at zoom");
        var p=new BattleRenderPoses.Pose(205,20,0);
        ok(close.ship(p,1),"wide hull near edge retained");
        ok(!close.ship(new BattleRenderPoses.Pose(2000,20,0),1),
                "far-off fleet eliminated");
        ok(!close.circle(Double.NaN,0,10),"invalid coordinates rejected");
        ok(!close.segment(0,0,Double.POSITIVE_INFINITY,0,1),
                "invalid beam rejected");
        System.out.println("PASS combat view bounds: edge-safe hulls, long beams, zoom and pan");
    }
}
