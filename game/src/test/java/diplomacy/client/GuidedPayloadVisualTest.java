package diplomacy.client;

import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;

/** Nonblocking art-mapping audit; physical missile mechanics live in core. */
public final class GuidedPayloadVisualTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static GuidedPayloadVisual.Appearance look(boolean torpedo,
            OrdnanceDoctrine.Payload payload,AmmunitionTable.Warhead warhead,
            OrdnancePropulsion.Phase phase,double cross){
        return GuidedPayloadVisual.choose(torpedo,payload,warhead,phase,cross);
    }
    public static void main(String[] args){
        var standard=look(false,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.CRUISE,.82);
        var micro=look(false,OrdnanceDoctrine.Payload.MICRO,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.BOOST,.31);
        var heavy=look(false,OrdnanceDoctrine.Payload.HEAVY,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.BOOST,1.52);
        var torpedo=look(true,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.CRUISE,1.35);
        var decoy=look(false,OrdnanceDoctrine.Payload.DECOY,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.CRUISE,1.8);
        check(standard.image().equals(GuidedPayloadVisual.GUIDED),
                "Standard missiles must use approved guided art");
        check(heavy.image().equals(GuidedPayloadVisual.HEAVY)
                &&torpedo.image().equals(GuidedPayloadVisual.HEAVY),
                "Heavy missile and torpedo bodies must use approved heavy art");
        check(micro.width()<standard.width()*.5f
                &&heavy.width()>standard.width()
                &&torpedo.width()>standard.width(),
                "Micro, standard and heavy ordnance must have genuinely different footprints");
        check(decoy.alpha()<standard.alpha()&&decoy.width()!=standard.width(),
                "Decoy must not be visually identical to a live guided warhead");
        var emp=look(false,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.EMP,OrdnancePropulsion.Phase.CRUISE,.82);
        var ap=look(false,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.ARMOR_PIERCING,OrdnancePropulsion.Phase.CRUISE,.82);
        var he=look(false,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.HIGH_EXPLOSIVE,OrdnancePropulsion.Phase.CRUISE,.82);
        var coast=look(false,OrdnanceDoctrine.Payload.STANDARD,
                AmmunitionTable.Warhead.STANDARD,OrdnancePropulsion.Phase.COAST,.82);
        check(emp.green()>emp.red()&&ap.width()<standard.width()
                &&he.width()>standard.width()&&coast.red()<standard.red(),
                "Warhead and motor phase must remain visible in projectile art");
        for(var payload:OrdnanceDoctrine.Payload.values())
            for(var warhead:AmmunitionTable.Warhead.values())
                for(var phase:OrdnancePropulsion.Phase.values()){
                    var style=look(false,payload,warhead,phase,.82);
                    check(style.image().equals(GuidedPayloadVisual.GUIDED)
                        ||style.image().equals(GuidedPayloadVisual.HEAVY),
                        "All visual variants must reuse approved original PNGs");
                    check(style.width()>0&&style.height()>0,
                        "Every physical loadout needs bounded positive sprite size");
                }
        System.out.println("PASS approved physical missile/torpedo art routing, micro/heavy/decoy/warheads and silent-coast material");
    }
}
