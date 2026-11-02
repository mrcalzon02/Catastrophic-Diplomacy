package diplomacy.client;

/**
 * Once-per-original-sprite visual registration for physical armor-tile craters.
 * A narrow source-alpha hull may not occupy an entire 16x16 protection cell.
 * Shift the visual crater center to the NEAREST valid source pixel.
 * First search within one armor cell as before (approved laser unchanged).
 * Only if the narrow authored hull has no art nearby, search at most 3.25
 * cells for the closest actual hull surface. Physical impact coordinates
 * and source PNG alpha are NEVER modified or invented.
 *
 * Pure int alpha grid enables graphics-device-independent regression tests.
 */
final class ShipArmorStampAnchors {
    static final int GRID=16;
    private final float[] rowCenter=new float[GRID*GRID];
    private final float[] colCenter=new float[GRID*GRID];
    // Nonlaser damage has an OUTER rim and short fractures. A nearest pixel
    // along a paper-thin wing edge is not enough painted surface to show them.
    // Compute one local inboard alternative per original source sprite.
    private final float[] profileRows=new float[GRID*GRID];
    private final float[] profileCols=new float[GRID*GRID];
    private ShipArmorStampAnchors(){}

    float row(int r,int c){return rowCenter[r*GRID+c];}
    float col(int r,int c){return colCenter[r*GRID+c];}
    float row(int r,int c,diplomacy.sim.Battle.DamageType type){
        return type==null||type==diplomacy.sim.Battle.DamageType.LASER
                ?row(r,c):profileRows[r*GRID+c];
    }
    float col(int r,int c,diplomacy.sim.Battle.DamageType type){
        return type==null||type==diplomacy.sim.Battle.DamageType.LASER
                ?col(r,c):profileCols[r*GRID+c];
    }

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
            // Real fitted hits can land on a model's collision ellipse or
            // projected hardpoint whose pixel is transparent in the much
            // narrower AUTHORED sprite. Such events previously made an
            // invisible mask (confirmed zero R/G/B/A marks for kinetic).
            // A second bounded nearest-surface pass is only used when the
            // approved one-cell registration found no actual source alpha.
            if(foundX<0){
                final float maxShift=3.25f;
                final float maximumSquared=maxShift*maxShift;
                int fromX=Math.max(1,(int)Math.floor(cx-maxShift*tw));
                int toX=Math.min(width-2,(int)Math.ceil(cx+maxShift*tw));
                int fromY=Math.max(1,(int)Math.floor(cy-maxShift*th));
                int toY=Math.min(height-2,(int)Math.ceil(cy+maxShift*th));
                best=maximumSquared+.00001f;
                for(int y=fromY;y<=toY;y++)for(int x=fromX;x<=toX;x++){
                    if(!solid(alpha,width,height,x,y))continue;
                    float dx=(x-cx)/tw,dy=(y-cy)/th;
                    float distance=dx*dx+dy*dy;
                    if(distance<best){
                        best=distance;foundX=x;foundY=y;
                    }
                }
            }
            if(foundX>=0){
                result.rowCenter[index]=r+(foundY-cy)/th;
                result.colCenter[index]=c+(foundX-cx)/tw;
            }
        }
        // Do NOT change the laser's already-approved registered coordinates.
        // The additional impact families need room for a visible cold metal
        // lip / soot / spall *outside* the physical alpha cutout.
        for(int r=0;r<GRID;r++)for(int c=0;c<GRID;c++){
            int id=r*GRID+c;
            result.profileRows[id]=result.rowCenter[id];
            result.profileCols[id]=result.colCenter[id];
            float ar=result.rowCenter[id],ac=result.colCenter[id];
            float sx=(ac+.5f)*tw-.5f,sy=(ar+.5f)*th-.5f;
            int bx=Math.round(sx),by=Math.round(sy);
            if(!solid(alpha,width,height,bx,by))continue;
            float baseSupport=paintedSupport(alpha,width,height,bx,by,tw,th);
            if(baseSupport>=11f)continue; // already enough local painted hull
            float best=baseSupport;
            int radiusX=(int)Math.ceil(tw*.95f),radiusY=(int)Math.ceil(th*.95f);
            for(int y=Math.max(1,by-radiusY);y<=Math.min(height-2,by+radiusY);y++)
                for(int x=Math.max(1,bx-radiusX);x<=Math.min(width-2,bx+radiusX);x++){
                    float dx=(x-bx)/tw,dy=(y-by)/th;
                    if(dx*dx+dy*dy>.95f*.95f||!solid(alpha,width,height,x,y))
                        continue;
                    // Avoid moving across separate transparent prongs, or
                    // relocating a real hit farther than the existing
                    // physical-to-paint safety envelope.
                    int midX=Math.round((x+bx)*.5f),midY=Math.round((y+by)*.5f);
                    if(!solid(alpha,width,height,midX,midY))continue;
                    float rx=(x-((c+.5f)*tw-.5f))/tw;
                    float ry=(y-((r+.5f)*th-.5f))/th;
                    if(rx*rx+ry*ry>3.25f*3.25f)continue;
                    float support=paintedSupport(alpha,width,height,x,y,tw,th);
                    float score=support-.33f*(float)Math.sqrt(dx*dx+dy*dy);
                    if(score>best+.07f){
                        best=score;
                        result.profileRows[id]=r+(y-((r+.5f)*th-.5f))/th;
                        result.profileCols[id]=c+(x-((c+.5f)*tw-.5f))/tw;
                    }
                }
        }
        return result;
    }
    /**
     * 3x3 small- and large-radius samples measure the actual usable painted
     * armor around a candidate. A thin edge scores poorly; a nearby interior
     * panel scores well without inventing pixels outside the approved PNG.
     */
    private static float paintedSupport(int[] alpha,int width,int height,
                                         int x,int y,float tw,float th){
        float points=0;
        for(float radius:new float[]{.38f,.80f})
            for(int oy=-1;oy<=1;oy++)for(int ox=-1;ox<=1;ox++){
                if(ox==0&&oy==0)continue;
                int px=Math.round(x+ox*radius*tw);
                int py=Math.round(y+oy*radius*th);
                if(px>=1&&py>=1&&px<width-1&&py<height-1&&
                        alpha[py*width+px]>=128)points++;
            }
        return points;
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
