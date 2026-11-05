package diplomacy.client;
/** Global linear screen footprint for render-only weapon particles and sprites. */
final class CombatEffectScale {
    static final float LINEAR=1f/3f;
    /** Original craft dimensions were authored before ship hulls were halved. */
    static final float FIGHTER_CRAFT=0.44f;
    /** Relative to the normal ship-fired projectile of the same art family. */
    static final float FIGHTER_GUIDED=0.26f;
    static final float FIGHTER_KINETIC=0.10f;
    static final float FIGHTER_BEAM=0.30f;
    static float of(float size){return size*LINEAR;}
    static float forLauncher(float size,boolean fighter,float factor){
        return fighter?size*factor:size;
    }
    private CombatEffectScale(){}
}
