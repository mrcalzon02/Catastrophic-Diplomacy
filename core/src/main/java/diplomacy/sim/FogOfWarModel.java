package diplomacy.sim;

/** Presentation-only soft optical falloff. A sensor return is NOT visual sight.
 * The field never participates in target locks or other simulation mechanics. */
public final class FogOfWarModel {
    private FogOfWarModel(){}
    public static double opticalRange(SpaceWeather weather){
        return weather.preset().includes(SpaceWeather.Kind.NEBULA)?310:390;
    }
    /** Smoothstep: fully transparent within the readable core, entirely
     * concealed outside the fringe, with continuous first derivatives. */
    public static float visible(double distance,double visualRange){
        if(!Double.isFinite(distance)||!Double.isFinite(visualRange)
                ||visualRange<=0||distance<0)return 0;
        double inner=visualRange*.84,outer=visualRange*1.14;
        if(distance<=inner)return 1;
        if(distance>=outer)return 0;
        double t=(outer-distance)/(outer-inner);
        return (float)(t*t*(3-2*t));
    }
}
