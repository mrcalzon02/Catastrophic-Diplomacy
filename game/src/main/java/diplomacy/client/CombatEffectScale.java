package diplomacy.client;
/** Global linear screen footprint for render-only weapon particles and sprites. */
final class CombatEffectScale {
    static final float LINEAR=1f/3f;
    static float of(float size){return size*LINEAR;}
    private CombatEffectScale(){}
}
