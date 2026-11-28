package diplomacy.client;

/** Geometry and pointer behavior only; no rendering or device acceptance claim. */
final class ShipyardCanvasTest {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static boolean near(float a,float b){return Math.abs(a-b)<0.001f;}
    public static void main(String[] args){
        var c=new ShipyardCanvas();
        check(near(c.x(500),500)&&near(c.y(380),380),"fit identity");
        c.scale(2,500,380,500,380);
        check(near(c.zoom(),2)&&near(c.x(500),500)&&near(c.y(380),380),"wheel anchor");
        c.scale(1.25f,500,380,520,400);
        check(near(c.x(500),520)&&near(c.y(380),400),"moving pinch midpoint");
        c.pan(20,15);
        check(near(c.x(500),540)&&near(c.y(380),415),"pan transform");
        c.scale(100,520,400,520,400);
        check(near(c.zoom(),4),"maximum zoom");
        c.pan(10000,-10000);
        check(near(c.x(ShipyardCanvas.X),ShipyardCanvas.X)&&
                near(c.y(ShipyardCanvas.Y+ShipyardCanvas.H),ShipyardCanvas.Y+ShipyardCanvas.H),"bounded pan");
        c.scale(.0001f,500,380,500,380);
        check(near(c.zoom(),1)&&near(c.x(500),500)&&near(c.y(380),380),"minimum zoom fit");
        var t=new BattleTouchNavigation();
        check(t.start(7,500,380)&&t.start(9,600,380)&&!t.start(11,550,380),"two pointers only");
        var m=t.move(9,650,380);
        c.scale(1/m.scale(),m.oldX(),m.oldY(),m.newX(),m.newY());
        check(near(c.zoom(),1.5f),"spread zooms in");
        check(!t.end(7,500,380)&&!t.end(9,650,380),"pinch release not a tap");
        check(t.start(4,540,380)&&t.end(4,540,380),"new tap after pinch");
        t.start(4,540,380);t.move(4,570,380);
        check(!t.end(4,570,380),"drag release not a tap");
        t.start(4,540,380);t.cancel();
        check(!t.end(4,540,380),"cancel release not a tap");
        c.fit();
        check(near(c.zoom(),1)&&near(c.x(500),500)&&near(c.y(380),380),"reset");
        System.out.println("PASS shipyard canvas: fit, anchor, moving midpoint, pan, bounds, pointer capture, cancellation");
    }
}
