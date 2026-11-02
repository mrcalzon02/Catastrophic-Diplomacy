package diplomacy.client;

/**
 * Pure damage-mask rectangle invalidation, shared by desktop and Android.
 * A real changed 16x16 protection tile dirties its nearby scorch interpolation
 * and the irregular cutout lips; unrelated original sprite pixels are untouched.
 */
final class ShipDamageMaskRegion {
    static final int GRID=16;
    record Rect(int x,int y,int width,int height){
        Rect{
            if(x<0||y<0||width<=0||height<=0)
                throw new IllegalArgumentException("Invalid mask upload rectangle");
        }
        int pixels(){return width*height;}
    }
    private ShipDamageMaskRegion(){}
    /**
     * Expands ONLY when the source-alpha impact registration has to project
     * a physical collision onto nearby actual hull artwork. The old compact
     * dirty rect is preserved for ordinary one-cell cases.
     */
    static Rect changed(int[] previous,int[] next,int width,int height,
                        ShipArmorStampAnchors alphaAnchors){
        Rect original=changed(previous,next,width,height);
        if(original==null||alphaAnchors==null||previous==null)return original;
        int left=original.x(),top=original.y();
        int right=left+original.width(),bottom=top+original.height();
        for(int r=0;r<GRID;r++)for(int c=0;c<GRID;c++){
            int i=(r*GRID+c)*2;
            if(previous[i]==next[i]&&previous[i+1]==next[i+1])continue;
            float visibleRow=alphaAnchors.row(r,c);
            float visibleCol=alphaAnchors.col(r,c);
            // The outer kinetic, shrapnel and plasma effects stop at 1.3
            // cell units. A small extra 0.2 covers linear mask filtering.
            left=Math.min(left,Math.max(0,(int)Math.floor((visibleCol-1.5)*width/GRID)));
            right=Math.max(right,Math.min(width,(int)Math.ceil((visibleCol+2.5)*width/GRID)));
            top=Math.min(top,Math.max(0,(int)Math.floor((visibleRow-1.5)*height/GRID)));
            bottom=Math.max(bottom,Math.min(height,(int)Math.ceil((visibleRow+2.5)*height/GRID)));
        }
        return new Rect(left,top,right-left,bottom-top);
    }
    static Rect changed(int[] previous,int[] next,int width,int height){
        if(next==null||next.length!=GRID*GRID*2||width<GRID||height<GRID)
            throw new IllegalArgumentException("Invalid defense image or mask size");
        if(previous==null||previous.length!=next.length)
            return new Rect(0,0,width,height);
        int minRow=GRID,minCol=GRID,maxRow=-1,maxCol=-1;
        for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++){
            int i=(row*GRID+col)*2;
            if(previous[i]==next[i]&&previous[i+1]==next[i+1])continue;
            minRow=Math.min(minRow,row);maxRow=Math.max(maxRow,row);
            minCol=Math.min(minCol,col);maxCol=Math.max(maxCol,col);
        }
        if(maxRow<0)return null;
        // Physical damage registration may move a visual stamp up to one
        // tile to meet an irregular original hull silhouette. Include that
        // one-tile displacement, maximum crater radius and scorch spill
        // before issuing a subrectangle GPU upload.
        int x=Math.max(0,(int)Math.floor((minCol-2.5)*width/GRID));
        int y=Math.max(0,(int)Math.floor((minRow-2.5)*height/GRID));
        int right=Math.min(width,(int)Math.ceil((maxCol+3.5)*width/GRID));
        int bottom=Math.min(height,(int)Math.ceil((maxRow+3.5)*height/GRID));
        return new Rect(x,y,right-x,bottom-y);
    }
}
