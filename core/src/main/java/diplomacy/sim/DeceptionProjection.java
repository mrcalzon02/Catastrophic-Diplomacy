package diplomacy.sim;

/**
 * Shared deterministic state for powered optical camouflage and holographic
 * false contacts. Every displayed phantom uses the actual emitter hull art;
 * it is NOT an independently damageable ship, and never inherits hit points.
 * The physical benefit is electronic lock/seeker confusion in Battle.
 *
 * No allocations or random-stream consumption in the per-frame render path.
 */
public final class DeceptionProjection {
    private DeceptionProjection() { }

    public static double hologramStrength(Battle.Ship host) {
        if(host==null||!host.alive()||host.power<=4||
                host.ecmCondition<=0.15||host.hologramNoise<=0)return 0;
        return StrictMath.min(.75,host.hologramNoise*
                StrictMath.max(0,host.ecmCondition));
    }
    public static int falseContacts(Battle.Ship host) {
        double strength=hologramStrength(host);
        if(strength<=0)return 0;
        return strength>=0.60?3:strength>=0.35?2:1;
    }
    /** Higher-quality enemy acquisition computers defeat more of the spoof. */
    public static double lockMultiplier(Battle.Ship host,double hostileAcuity) {
        return 1-StrictMath.min(.8,hologramStrength(host)/
                StrictMath.max(.6,hostileAcuity));
    }
    /** Optical cloak fails without operating emitter, ECM circuits and power. */
    public static boolean cloakActive(Battle.Ship host) {
        return host!=null&&host.alive()&&host.opticalSignature<.995
                &&host.power>4&&host.ecmCondition>.15;
    }
    /** Ghosted original art remains visible to the PLAYER for tactical clarity.
     * Electronic sensors use the actual signature; this is visual presentation. */
    public static float hullOpacity(Battle.Ship host,long tick) {
        if(!cloakActive(host))return 1f;
        double strength=1-StrictMath.max(.12,host.opticalSignature);
        double shimmer=.028*StrictMath.sin(tick*.13+host.id*2.3);
        return (float)StrictMath.max(.18,StrictMath.min(.92,1-.66*strength+shimmer));
    }
    /** Ghost positions move on a smooth, bounded orbit about the actual hull.
     * No interpolation state, allocations or second copies of source textures. */
    public static double ghostX(Battle.Ship host,int index,long tick) {
        return host.x+radius(host,index)*StrictMath.cos(angle(host,index,tick));
    }
    public static double ghostY(Battle.Ship host,int index,long tick) {
        return host.y+radius(host,index)*StrictMath.sin(angle(host,index,tick));
    }
    private static double radius(Battle.Ship host,int index) {
        return (35+index*18)*StrictMath.max(.75,host.visualScale());
    }
    private static double angle(Battle.Ship host,int index,long tick) {
        return (host.id*1.61803398875+index*2.399963229728653+
                tick*.0075*(index%2==0?1:-1));
    }
}
