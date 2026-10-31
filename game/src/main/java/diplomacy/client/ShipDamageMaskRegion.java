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
        // Both bilinear scorch and stamp lips are confined within one
        // defense cell of a changed cell's center; 1.5 tile padding is safe
        // at mask-pixel boundaries and under linear texture filtering.
        int x=Math.max(0,(int)Math.floor((minCol-1.5)*width/GRID));
        int y=Math.max(0,(int)Math.floor((minRow-1.5)*height/GRID));
        int right=Math.min(width,(int)Math.ceil((maxCol+2.5)*width/GRID));
        int bottom=Math.min(height,(int)Math.ceil((maxRow+2.5)*height/GRID));
        return new Rect(x,y,right-x,bottom-y);
    }
}
