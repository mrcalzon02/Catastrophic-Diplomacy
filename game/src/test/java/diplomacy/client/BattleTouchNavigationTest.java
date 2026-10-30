package diplomacy.client;
/** Headless smoke coverage for Android pan/pinch, taps and FX sizing. */
public final class BattleTouchNavigationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void near(float a,float b){check(Math.abs(a-b)<.001f,a+" != "+b);}
    public static void main(String[] unused){
        var t=new BattleTouchNavigation();
        check(t.start(3,200,200),"start tap");
        check(!t.move(3,205,205).changed(),"tap slop");
        check(t.end(3,205,205),"tap on release");
        check(t.start(0,200,200),"start pan");
        var move=t.move(0,240,190);
        check(move.changed()&&!move.pinch(),"single-finger pan");
        near(move.dx(),40);near(move.dy(),-10);
        check(!t.end(0,240,190),"pan does not select");
        check(t.start(2,200,200),"pinch first finger");
        check(t.start(5,300,200),"pinch second finger");
        move=t.move(5,400,200);
        check(move.pinch(),"pinch recognized");
        near(move.scale(),.5f);near(move.oldX(),250);near(move.newX(),300);
        check(!t.end(5,400,200),"pinch cannot tap");
        check(!t.end(2,200,200),"remaining finger cannot tap");
        near(CombatEffectScale.of(48),16);
        near(CombatEffectScale.of(30),10);
        System.out.println("PASS battle gestures and 3x weapon effect scale");
    }
}
