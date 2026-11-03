package diplomacy.sim;

/**
 * Source-of-truth scale for the fixed arena. One simulation second represents
 * 60 physical seconds. The old 30Hz combat clock remains deterministic and
 * no world coordinates, ship IDs, saves or formation bounds are renumbered.
 *
 * Rendering hulls are tactical glyphs, NOT ship lengths of thousands of km.
 */
public final class WorldScale {
    private WorldScale(){}
    /** Global in-battle linear size of all hulls, hardpoints and hit geometry.
     * Source PNG pixels and large touch-friendly UI controls stay unchanged. */
    public static final double SHIP_WORLD_SCALE=0.50;
    public static final double C_KM_PER_SECOND=299_792.458;
    public static final double PHYSICAL_SECONDS_PER_SIM_SECOND=60;
    public static final double LIGHT_MINUTE_KM=C_KM_PER_SECOND*60;
    public static final double DIAGONAL_WORLD_UNITS=StrictMath.hypot(
            BattleArena.MAX_X-BattleArena.MIN_X,
            BattleArena.MAX_Y-BattleArena.MIN_Y);
    public static final double KM_PER_WORLD_UNIT=LIGHT_MINUTE_KM/DIAGONAL_WORLD_UNITS;
    /** In world units per PHYSICAL second. */
    public static final double C_WORLD_UNITS_PER_PHYSICAL_SECOND=
            C_KM_PER_SECOND/KM_PER_WORLD_UNIT;
    /** In world units per COMBAT simulation second. */
    public static final double C_WORLD_UNITS_PER_SIM_SECOND=
            C_WORLD_UNITS_PER_PHYSICAL_SECOND*PHYSICAL_SECONDS_PER_SIM_SECOND;

    public static double physicalSecondsForLight(double worldDistance){
        if(!Double.isFinite(worldDistance)||worldDistance<0)
            throw new IllegalArgumentException("Invalid distance");
        return worldDistance/C_WORLD_UNITS_PER_PHYSICAL_SECOND;
    }
    public static double signalDelayTicks(double worldDistance,boolean roundTrip){
        return physicalSecondsForLight(worldDistance)*(roundTrip?2:1)
                /PHYSICAL_SECONDS_PER_SIM_SECOND*30;
    }
    public static double fractionOfLightSpeed(double worldUnitsPerSimSecond){
        if(!Double.isFinite(worldUnitsPerSimSecond)||worldUnitsPerSimSecond<0)
            throw new IllegalArgumentException("Invalid speed");
        return worldUnitsPerSimSecond/C_WORLD_UNITS_PER_SIM_SECOND;
    }
    public static double simSpeedForLightFraction(double lightFraction){
        if(!Double.isFinite(lightFraction)||lightFraction<0||lightFraction>=1)
            throw new IllegalArgumentException("Invalid light fraction");
        return lightFraction*C_WORLD_UNITS_PER_SIM_SECOND;
    }
    public static double accelerationUnitsPerSimSecondSquared(double gee){
        if(!Double.isFinite(gee)||gee<0||gee>100)
            throw new IllegalArgumentException("Invalid propulsion acceleration");
        return 9.80665*gee*PHYSICAL_SECONDS_PER_SIM_SECOND*
                PHYSICAL_SECONDS_PER_SIM_SECOND/(KM_PER_WORLD_UNIT*1000);
    }
}
