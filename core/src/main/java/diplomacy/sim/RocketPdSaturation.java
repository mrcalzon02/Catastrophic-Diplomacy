package diplomacy.sim;

/**
 * Deterministic exploratory point-defense crowding model. Only rockets in
 * actual sensor/PD range count as incoming work. Each real fitted PD emitter
 * can manage four simultaneous rocket tracks before crowding reduces the
 * chance of a successful shot. No fake interceptions, ammunition, or RNG.
 *
 * Default use in Battle is OFF until the measured 16-round calibration has
 * been reviewed. No missile/torpedo/craft chance is modified by this model.
 */
public final class RocketPdSaturation {
    private RocketPdSaturation(){}
    public static double chance(double unsaturated,int visibleRockets,
                                int fittedPdEmitters,double pressure){
        if(!Double.isFinite(unsaturated)||unsaturated<0||unsaturated>1||
                visibleRockets<0||fittedPdEmitters<1||
                !Double.isFinite(pressure)||pressure<0||pressure>1)
            throw new IllegalArgumentException("Invalid rocket PD saturation model");
        if(pressure==0||visibleRockets<=4*fittedPdEmitters)return unsaturated;
        double crowd=visibleRockets/(double)fittedPdEmitters-4;
        return unsaturated/(1.0+pressure*crowd);
    }
}
