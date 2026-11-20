package diplomacy.client;

/**
 * Native-resolution world coordinates for the 320x180 fog mask.
 * Shared between all scouting and visible-target patches in one rebuild.
 * Each expression retains the original float/double evaluation order.
 */
final class FogMaskCoordinates {
    static final int WIDTH=320, HEIGHT=180;
    private final double[] x=new double[WIDTH];
    private final double[] y=new double[HEIGHT];

    void update(float centerX,float centerY,float zoom){
        float pxWorld=1280f*zoom/WIDTH;
        float pyWorld=720f*zoom/HEIGHT;
        for(int column=0;column<WIDTH;column++)
            x[column]=centerX+(column+.5-WIDTH*.5)*pxWorld;
        for(int row=0;row<HEIGHT;row++)
            y[row]=centerY+(HEIGHT*.5-row-.5)*pyWorld;
    }

    double x(int column){return x[column];}
    double y(int row){return y[row];}
}
