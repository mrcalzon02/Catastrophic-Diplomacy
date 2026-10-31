package diplomacy.client;

/**
 * Exact first-sample projection from an approved original PNG into the
 * fixed 96x144 damage/sector mask. Cache only those sampled source pixels,
 * never the entire decoded original atlas on an older Android device.
 */
final class ShipSurfaceSampleGrid {
    private ShipSurfaceSampleGrid(){}
    static int sourcePixel(int outputPixel,int sourceSize,int outputSize){
        if(outputPixel<0||outputPixel>=outputSize||sourceSize<1||outputSize<1)
            throw new IllegalArgumentException("Out-of-bounds original artwork sample");
        return (int)((long)outputPixel*sourceSize/outputSize);
    }
    static long originalBytes(int width,int height){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid source dimensions");
        return (long)width*height*4;
    }
    static long maskBytes(int width,int height){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid mask dimensions");
        return (long)width*height*4;
    }
}
