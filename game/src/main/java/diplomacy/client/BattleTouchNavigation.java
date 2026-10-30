package diplomacy.client;

/** Pointer-aware battlefield tap, drag and pinch with no libGDX dependency. */
final class BattleTouchNavigation {
    private static final float SLOP=12f;
    record Motion(boolean changed,boolean pinch,float dx,float dy,float scale,
                  float oldX,float oldY,float newX,float newY){
        static final Motion NONE=new Motion(false,false,0,0,1,0,0,0,0);
    }
    private int first=-1,second=-1;
    private float ax,ay,bx,by,originX,originY;
    private boolean moved,pinched;
    boolean owns(int id){return id==first||id==second;}
    boolean start(int id,float x,float y){
        if(owns(id))return true;
        if(first<0){first=id;ax=originX=x;ay=originY=y;moved=pinched=false;return true;}
        if(second<0){second=id;bx=x;by=y;moved=pinched=true;return true;}
        return false;
    }
    Motion move(int id,float x,float y){
        if(!owns(id))return Motion.NONE;
        float oldAx=ax,oldAy=ay,oldBx=bx,oldBy=by;
        if(id==first){ax=x;ay=y;}else{bx=x;by=y;}
        if(second<0){
            if(!moved && StrictMath.hypot(ax-originX,ay-originY)<SLOP)return Motion.NONE;
            moved=true;
            return new Motion(true,false,ax-oldAx,ay-oldAy,1,0,0,0,0);
        }
        float oldDistance=(float)StrictMath.hypot(oldAx-oldBx,oldAy-oldBy);
        float newDistance=(float)StrictMath.hypot(ax-bx,ay-by);
        float ratio=oldDistance<8||newDistance<8?1:oldDistance/newDistance;
        return new Motion(true,true,0,0,ratio,
                (oldAx+oldBx)*.5f,(oldAy+oldBy)*.5f,(ax+bx)*.5f,(ay+by)*.5f);
    }
    boolean end(int id,float x,float y){
        if(!owns(id))return false;
        boolean tap=id==first&&!moved&&!pinched
                &&StrictMath.hypot(x-originX,y-originY)<SLOP;
        if(id==first && second>=0){
            first=second;ax=originX=bx;ay=originY=by;
            second=-1;moved=pinched=true;
        }else if(id==second)second=-1;
        else cancel();
        return tap;
    }
    void cancel(){first=second=-1;moved=pinched=false;}
}
