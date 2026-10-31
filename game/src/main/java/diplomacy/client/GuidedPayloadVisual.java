package diplomacy.client;

import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.Battle;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;

/**
 * Read-only mapping from REAL guided-body loadouts to the two author-approved
 * projectile-body sprite assets. No missile is synthesized from particles and
 * no simulation balance, tracking, or hitbox depends on sprite dimensions.
 *
 * Existing approved PNGs are archived under effects/weapon_fx/{missile,torpedo}
 * and are never overwritten to manufacture artificial variant artwork.
 */
final class GuidedPayloadVisual {
    static final String GUIDED="effects/weapon_fx/missile/payload_01.png";
    static final String HEAVY="effects/weapon_fx/torpedo/payload_01.png";
    static final String CURATED="effects/projectile_fx/curated/missile_bodies_6.png";
    static final int COLS=3,CELL_WIDTH=64,CELL_HEIGHT=40,CELLS=6;
    record Appearance(String image,float width,float height,
                      float red,float green,float blue,float alpha,
                      int cell,String fallback) { }
    private GuidedPayloadVisual(){}

    static Appearance forMissile(Battle.Missile missile){
        return choose(missile.torpedo,missile.payload,missile.warhead,
                missile.thrustPhase,missile.spec.crossSection());
    }

    /** Physical size and fitted payload—not an image-only RNG—drive the look. */
    static Appearance choose(boolean torpedo,OrdnanceDoctrine.Payload payload,
                             AmmunitionTable.Warhead warhead,
                             OrdnancePropulsion.Phase phase,double crossSection){
        if(payload==null||warhead==null||phase==null||
                !Double.isFinite(crossSection)||crossSection<=0)
            throw new IllegalArgumentException("Invalid physical guided-body appearance");
        boolean heavy=torpedo||payload==OrdnanceDoctrine.Payload.HEAVY
                ||payload==OrdnanceDoctrine.Payload.CLUSTER_CARRIER;
        // Six independent silhouettes cut from the archived ballistic arsenal.
        int cell=torpedo?4:payload==OrdnanceDoctrine.Payload.HEAVY
                ||payload==OrdnanceDoctrine.Payload.CLUSTER_CARRIER?1:
                payload==OrdnanceDoctrine.Payload.MICRO||
                payload==OrdnanceDoctrine.Payload.MICRO_CLUSTER_CARRIER||
                payload==OrdnanceDoctrine.Payload.ULTRA_MICRO||
                payload==OrdnanceDoctrine.Payload.DECOY?3:
                switch(warhead){
                    case ARMOR_PIERCING -> 2;
                    case EMP,SHIELD_BREAKER -> 3;
                    case FRAGMENTATION,HIGH_EXPLOSIVE -> 5;
                    default -> 0;
                };
        String fallback=heavy?HEAVY:GUIDED;
        // The source texture's proportions and provenance are retained.
        // Values below are BEFORE the common one-third combat-FX draw scale.
        float size=heavy?1.25f:1f;
        if(payload==OrdnanceDoctrine.Payload.MICRO)size=.43f;
        if(payload==OrdnanceDoctrine.Payload.MICRO_CLUSTER_CARRIER)size=.55f;
        if(payload==OrdnanceDoctrine.Payload.ULTRA_MICRO)size=.22f;
        if(payload==OrdnanceDoctrine.Payload.CLUSTER_CARRIER)size=1.28f;
        if(payload==OrdnanceDoctrine.Payload.DECOY)size=.66f;
        if(payload==OrdnanceDoctrine.Payload.HEAVY)size=1.38f;
        if(payload==OrdnanceDoctrine.Payload.STANDARD&&!torpedo)
            size*=(float)Math.max(.85,Math.min(1.12,Math.sqrt(crossSection/.82)));
        float r=1f,g=1f,b=1f,a=1f;
        switch(warhead){
            case ARMOR_PIERCING -> {r=.86f;g=.91f;b=1f;size*=.92f;}
            case FRAGMENTATION -> {r=1f;g=.87f;b=.66f;size*=.88f;}
            case HIGH_EXPLOSIVE -> {r=1f;g=.73f;b=.64f;size*=1.11f;}
            case EMP -> {r=.67f;g=1f;b=.82f;}
            case SHIELD_BREAKER -> {r=.65f;g=.84f;b=1f;}
            case STANDARD -> {}
        }
        if(payload==OrdnanceDoctrine.Payload.DECOY){
            r=.45f;g=.78f;b=1f;a=.75f;
        }
        // Preserve a visible metal body while its engine is dormant.
        if(phase==OrdnancePropulsion.Phase.COLD_EJECT||
                phase==OrdnancePropulsion.Phase.COAST){
            r*=.82f;g*=.82f;b*=.82f;
        }
        return new Appearance(CURATED,36f*size,28f*size,r,g,b,a,cell,fallback);
    }
}
