package diplomacy.sim;

/**
 * Physical one-generation ordnance dispensers. A carrier transfers its finite
 * remaining damage and launch module properties into real PD-interceptable
 * bodies. Children have no deployment mode and cannot recursively split.
 */
public final class OrdnanceSubmunitions {
    private OrdnanceSubmunitions(){}
    public static int children(OrdnanceDoctrine.Payload payload) {
        if(payload==null)throw new IllegalArgumentException("Missing guided payload");
        return switch(payload) {
            case CLUSTER_CARRIER, MICRO_CLUSTER_CARRIER -> 6;
            default -> 0;
        };
    }
    public static boolean carrier(OrdnanceDoctrine.Payload payload){
        return children(payload)>0;
    }
    public static boolean ultra(OrdnanceDoctrine.Payload payload){
        return payload==OrdnanceDoctrine.Payload.MICRO_CLUSTER_CARRIER;
    }
    public static OrdnanceDoctrine.Payload childPayload(OrdnanceDoctrine.Payload parent){
        if(!carrier(parent))throw new IllegalArgumentException("Not a dispenser");
        return ultra(parent)?OrdnanceDoctrine.Payload.ULTRA_MICRO:OrdnanceDoctrine.Payload.MICRO;
    }
    /** Fires after the parent has travelled about half its original range. */
    public static double releaseDistance(OrdnanceDoctrine.Payload payload,double launchRange){
        if(!Double.isFinite(launchRange)||launchRange<0)
            throw new IllegalArgumentException("Invalid deployment range");
        if(!carrier(payload))return 0;
        return ultra(payload)?clamp(launchRange*.42,60,175)
                :clamp(launchRange*.48,70,225);
    }
    /** Fleet damage budget is conserved across the independent warheads. */
    public static double childDamage(double parentDamage,OrdnanceDoctrine.Payload parent){
        if(!carrier(parent)||!Double.isFinite(parentDamage)||parentDamage<0)
            throw new IllegalArgumentException("Invalid carrier warhead");
        return parentDamage*.88/children(parent);
    }
    /** Real radar, PD health and propulsion values—not sprite-scaled hitboxes. */
    public static ProjectileModel.Spec childSpec(ProjectileModel.Spec parent,
                                                 OrdnanceDoctrine.Payload payload){
        if(parent==null||!carrier(payload))
            throw new IllegalArgumentException("Not a fitted carrier body");
        boolean tiny=ultra(payload);
        return new ProjectileModel.Spec(
                clamp(parent.speed()*(tiny?1.65:1.35),10,1500),
                clamp(parent.health()*(tiny?.22:.38),.1,200),
                clamp(parent.crossSection()*(tiny?.16:.36),.04,8),
                clamp(parent.signature()*(tiny?.38:.65),.04,8));
    }
    private static double clamp(double v,double low,double high){
        return StrictMath.max(low,StrictMath.min(high,v));
    }
}
