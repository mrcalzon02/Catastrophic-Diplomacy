package diplomacy.client;

/**
 * Once-per-original-sprite visual registration for physical armor-tile craters.
 * A narrow source-alpha hull may not occupy an entire 16x16 protection cell.
 * Shift the visual crater center only to the NEAREST nearby opaque source pixel
 * (max 1 tile); never change the physical hit coordinate or the PNG alpha.
 *
 * Pure int alpha grid enables graphics-device-independent regression tests.
 */
final class ShipArmorStampAnchors {
    static final int GRID=16;
    private final float[] rowCenter=new float[GRID*GRID];
    private final float[] colCenter=new float[GRID*GRID];
    private ShipArmorStampAnchors(){}

    float row(int r,int c){return rowCenter[r*GRID+c];}
    float col(int r,int c){return colCenter[r*GRID+c];}

    static ShipArmorStampAnchors fromAlpha(int[] alpha,int width,int height){
        if(alpha==null||width<GRID||height<GRID||alpha.length!=width*height)
            throw new IllegalArgumentException("A valid source art alpha mask is required");
        ShipArmorStampAnchors result=new ShipArmorStampAnchors();
        float tw=width/(float)GRID,th=height/(float)GRID;
        for(int r=0;r<GRID;r++)for(int c=0;c<GRID;c++){
            int index=r*GRID+c;
            result.rowCenter[index]=r;
            result.colCenter[index]=c;
            float cx=(c+.5f)*tw-.5f,cy=(r+.5f)*th-.5f;
            int ix=Math.max(0,Math.min(width-1,Math.round(cx)));
            int iy=Math.max(0,Math.min(height-1,Math.round(cy)));
            if(solid(alpha,width,height,ix,iy))continue;
            int minX=Math.max(1,(int)Math.floor(cx-tw));
            int maxX=Math.min(width-2,(int)Math.ceil(cx+tw));
            int minY=Math.max(1,(int)Math.floor(cy-th));
            int maxY=Math.min(height-2,(int)Math.ceil(cy+th));
            float best=1.00001f;
            int foundX=-1,foundY=-1;
            for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
                if(!solid(alpha,width,height,x,y))continue;
                float dx=(x-cx)/tw,dy=(y-cy)/th;
                float distance=dx*dx+dy*dy;
                if(distance<best){
                    best=distance;foundX=x;foundY=y;
                }
            }
            if(foundX>=0){
                result.rowCenter[index]=r+(foundY-cy)/th;
                result.colCenter[index]=c+(foundX-cx)/tw;
            }
        }
        return result;
    }
    private static boolean solid(int[] alpha,int w,int h,int x,int y){
        if(x<=0||y<=0||x>=w-1||y>=h-1)return false;
        int at=y*w+x;
        // Only shift to a solid region, never to antialiased exhaust, thin
        // floating text, or the outside edge of the sprite silhouette.
        return alpha[at]>=200&&alpha[at-1]>=120&&alpha[at+1]>=120
                &&alpha[at-w]>=120&&alpha[at+w]>=120;
    }
}
