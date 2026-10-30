package diplomacy.client;

/**
 * Touch release based wake detector. A tap is never recognized on touch-down;
 * drags, long holds and second fingers cannot accidentally wake controls.
 * Uses monotonically increasing nanoseconds supplied by the caller.
 */
final class ScreensaverWakeGesture {
    static final long DOUBLE_TAP_NS=420_000_000L;
    static final long MAX_TAP_NS=500_000_000L;
    static final float TAP_SLOP_PX=28f;
    static final float DOUBLE_TAP_DISTANCE_PX=48f;
    private int pointer=-1;
    private float downX,downY,previousX,previousY;
    private long downTime,previousTap=-1;
    private boolean invalid;

    void reset(){
        pointer=-1;
        previousTap=-1;
        invalid=false;
    }

    void down(int id,float x,float y,long now){
        if(pointer>=0){
            invalid=true;
            previousTap=-1;
            return;
        }
        pointer=id;
        downX=x;downY=y;downTime=now;
        invalid=false;
    }

    void move(int id,float x,float y){
        if(pointer==id&&distance(downX,downY,x,y)>TAP_SLOP_PX){
            invalid=true;
            previousTap=-1;
        }
    }

    boolean up(int id,float x,float y,long now){
        if(pointer!=id)return false;
        pointer=-1;
        boolean tap=!invalid&&now>=downTime&&now-downTime<=MAX_TAP_NS
                &&distance(downX,downY,x,y)<=TAP_SLOP_PX;
        if(!tap){previousTap=-1;return false;}
        boolean doubleTap=previousTap>=0&&now>=previousTap
                &&now-previousTap<=DOUBLE_TAP_NS
                &&distance(previousX,previousY,x,y)<=DOUBLE_TAP_DISTANCE_PX;
        if(doubleTap){
            previousTap=-1;
            return true;
        }
        previousTap=now;
        previousX=x;previousY=y;
        return false;
    }

    private static double distance(float ax,float ay,float bx,float by){
        return StrictMath.hypot(ax-bx,ay-by);
    }
}
