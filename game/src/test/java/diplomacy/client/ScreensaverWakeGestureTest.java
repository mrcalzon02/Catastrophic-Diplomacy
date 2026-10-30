package diplomacy.client;

/** No libGDX required: real double-release and gesture rejection. */
public final class ScreensaverWakeGestureTest {
    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args){
        ScreensaverWakeGesture g=new ScreensaverWakeGesture();
        long ms=1_000_000L;
        g.down(0,200,200,0);
        require(!g.up(0,200,200,40*ms),"first tap must not wake");
        g.down(0,205,202,130*ms);
        require(g.up(0,205,202,160*ms),"nearby quick second tap must wake");
        g.down(0,200,200,200*ms);
        require(!g.up(0,200,200,240*ms),"second pair requires fresh first tap");
        g.down(0,201,201,800*ms);
        require(!g.up(0,201,201,830*ms),"expired previous tap must not wake");
        g.down(0,201,201,900*ms);
        g.move(0,260,260);
        require(!g.up(0,260,260,950*ms),"drag cannot count as a tap");
        g.down(0,202,202,980*ms);
        require(!g.up(0,202,202,1000*ms),"drag must clear double-tap history");
        g.down(0,202,202,1010*ms);
        require(g.up(0,202,202,1025*ms),"two new taps work after a drag");
        g.reset();
        g.down(0,10,10,0);
        require(!g.up(0,10,10,600*ms),"long press cannot wake");
        g.down(0,10,10,650*ms);
        require(!g.up(0,10,10,680*ms),"long press clears previous tap");
        g.down(0,500,500,700*ms);
        require(!g.up(0,500,500,730*ms),"remote second tap cannot wake");
        g.reset();
        g.down(0,20,20,0);
        g.down(1,25,25,15*ms);
        require(!g.up(1,25,25,30*ms),"second finger cannot wake");
        require(!g.up(0,20,20,40*ms),"pinch cannot form first tap");
        g.down(0,20,20,50*ms);
        require(!g.up(0,20,20,80*ms),"pinch clears pending pair");
        g.down(0,20,20,100*ms);
        require(g.up(0,20,20,140*ms),"fresh pair still works after pinch");
        System.out.println("PASS: double tap wake; single tap, drag, pinch, hold and distance safety");
    }
}
