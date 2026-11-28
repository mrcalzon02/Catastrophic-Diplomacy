package diplomacy.client;

/** Presentation-only, screen-space shipyard camera. Never modifies a blueprint. */
final class ShipyardCanvas {
    static final float X=337f,Y=223f,W=454f,H=320f;
    static final float CX=X+W/2,CY=Y+H/2;
    private float zoom=1f,panX,panY;
    float zoom(){return zoom;}
    float x(float base){return CX+panX+(base-CX)*zoom;}
    float y(float base){return CY+panY+(base-CY)*zoom;}
    static boolean contains(float x,float y){
        return x>=X&&x<=X+W&&y>=Y&&y<=Y+H;
    }
    void fit(){zoom=1;panX=panY=0;}
    void pan(float dx,float dy){panX+=dx;panY+=dy;bound();}
    void scale(float factor,float oldX,float oldY,float newX,float newY){
        if(!Float.isFinite(factor)||factor<=0)return;
        float next=Math.max(1f,Math.min(4f,zoom*factor));
        float ratio=next/zoom;
        panX=newX-CX-(oldX-CX-panX)*ratio;
        panY=newY-CY-(oldY-CY-panY)*ratio;
        zoom=next;
        bound();
    }
    private void bound(){
        float bx=W*(zoom-1f)/2,by=H*(zoom-1f)/2;
        panX=Math.max(-bx,Math.min(bx,panX));
        panY=Math.max(-by,Math.min(by,panY));
    }
}
