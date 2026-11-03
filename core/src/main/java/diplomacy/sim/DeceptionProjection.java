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
        return count(hologramStrength(host));
    }
    /** Emitter state as it was when the photons left the hull. A switched-off
     * or moved emitter cannot retroactively edit an already emitted ghost. */
    public static double hologramStrength(SensorLightTimeHistory.Profile emitted) {
        if(emitted==null||emitted.power()<=4||
                emitted.ecmCondition()<=.15||emitted.hologramNoise()<=0)return 0;
        return StrictMath.min(.75,emitted.hologramNoise()*
                StrictMath.max(0,emitted.ecmCondition()));
    }
    public static int falseContacts(SensorLightTimeHistory.Profile emitted) {
        return count(hologramStrength(emitted));
    }
    private static int count(double strength) {
        return strength<=0?0:strength>=.60?3:strength>=.35?2:1;
    }
    /** Higher-quality enemy acquisition computers defeat more of the spoof. */
    public static double lockMultiplier(Battle.Ship host,double hostileAcuity) {
        return 1-StrictMath.min(.8,hologramStrength(host)/
                StrictMath.max(.6,hostileAcuity));
    }
    /** One locked seeker acquires a coherent real-world false contact.
     * A 45-tick lock epoch keeps the phantom stable enough for a physical
     * missile to pursue, then allows normal electronic reacquisition.
     * Better quality seekers are less susceptible; no RNG is consumed.
     * Returns -1 for an unspoofed solution. */
    public static int seekerGhostIndex(Battle.Ship target,long missileId,
                                       int flightTicks,double seekerQuality){
        if(target==null)return -1;
        double strength=hologramStrength(target);
        return chooseSeekerGhost(target.id,strength,count(strength),
                missileId,flightTicks,seekerQuality);
    }
    /** Missile spoof selection MUST use the same historical emitter profile
     * as the actual photon-limited seeker return, never live target power. */
    public static int seekerGhostIndex(int emitterId,
            SensorLightTimeHistory.Profile emitted,long missileId,
            int flightTicks,double seekerQuality){
        if(emitted==null)return -1;
        double strength=hologramStrength(emitted);
        return chooseSeekerGhost(emitterId,strength,count(strength),
                missileId,flightTicks,seekerQuality);
    }
    private static int chooseSeekerGhost(int emitterId,double strength,
            int contacts,long missileId,int flightTicks,double seekerQuality){
        if(contacts==0||flightTicks<1)return -1;
        long epoch=(flightTicks-1)/45L;
        long v=missileId*0x9e3779b97f4a7c15L
                ^((long)emitterId*0xbf58476d1ce4e5b9L)
                ^(epoch*0x94d049bb133111ebL);
        v^=v>>>30;v*=0xbf58476d1ce4e5b9L;
        v^=v>>>27;v*=0x94d049bb133111ebL;v^=v>>>31;
        double roll=(v>>>11)*0x1.0p-53;
        if(roll>=StrictMath.min(.65,strength/
                StrictMath.max(.65,seekerQuality)))return -1;
        return (int)Math.floorMod(v>>>32,contacts);
    }
    /** Cloaks and image emitters are operating equipment, not perpetual
     * zero-cost passive buffs. Fitted, powered emitters debit the actual
     * reactor store and create physical heat; a depleted emitter shuts off.
     * Return actual energy charged so diagnostic tests can verify finiteness. */
    public static double operate(Battle.Ship host,double seconds){
        if(host==null||!host.alive()||seconds<=0)return 0;
        boolean cloak=cloakActive(host);
        double holo=hologramStrength(host);
        double draw=(cloak?1.3*(1-StrictMath.max(.08,host.opticalSignature)):0)
                +holo*2.1;
        if(draw<=0)return 0;
        double spent=StrictMath.min(StrictMath.max(0,host.power-4),draw*seconds);
        host.power-=spent;
        host.heat+=spent*.65;
        return spent;
    }
    /** Optical cloak fails without operating emitter, ECM circuits and power. */
    public static boolean cloakActive(Battle.Ship host) {
        return host!=null&&host.alive()&&host.opticalSignature<.995
                &&host.power>4&&host.ecmCondition>.15;
    }
    /** Independent fighter radar/optical acquisition. Carrier targeting data
     * can guide a fighter to an intercept, but cannot give its weapons an
     * omniscient lock on a cloaked or radar-suppressed hull. */
    public static double fighterContactRange(Battle.Ship target,
                                             double radarRange,double weatherFactor){
        if(target==null||radarRange<0||weatherFactor<0)
            throw new IllegalArgumentException("Invalid fighter acquisition");
        double rcs=StrictMath.max(.06,StrictMath.min(4,target.radarCrossSection));
        double radar=radarRange*weatherFactor*StrictMath.sqrt(StrictMath.sqrt(rcs));
        double silhouette=StrictMath.max(.85,StrictMath.min(1.24,
                StrictMath.sqrt(StrictMath.max(1,target.maxHull)/550)));
        double optic=390*silhouette*(cloakActive(target)?target.opticalSignature:1);
        return StrictMath.max(radar,optic);
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
        return host.x+radius(host.visualScale(),index)*
                StrictMath.cos(angle(host.id,index,tick));
    }
    public static double ghostY(Battle.Ship host,int index,long tick) {
        return host.y+radius(host.visualScale(),index)*
                StrictMath.sin(angle(host.id,index,tick));
    }
    /** The missile perceives this emitted phantom at its measured-time
     * position; today's rendered phantom is a different, later emission. */
    public static double ghostX(int emitterId,
            SensorLightTimeHistory.Sample emitted,double hullScale,int index){
        if(emitted==null||index<0)throw new IllegalArgumentException("No ghost sample");
        return emitted.x()+radius(hullScale,index)*
                StrictMath.cos(angle(emitterId,index,emitted.tick()));
    }
    public static double ghostY(int emitterId,
            SensorLightTimeHistory.Sample emitted,double hullScale,int index){
        if(emitted==null||index<0)throw new IllegalArgumentException("No ghost sample");
        return emitted.y()+radius(hullScale,index)*
                StrictMath.sin(angle(emitterId,index,emitted.tick()));
    }
    private static double radius(double hullScale,int index) {
        return (35+index*18)*StrictMath.max(.75,hullScale);
    }
    private static double angle(int emitterId,int index,long tick) {
        return (emitterId*1.61803398875+index*2.399963229728653+
                tick*.0075*(index%2==0?1:-1));
    }
}
