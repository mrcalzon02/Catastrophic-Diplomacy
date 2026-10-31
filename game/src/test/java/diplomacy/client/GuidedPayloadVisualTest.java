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
        check(standard.image().equals(GuidedPayloadVisual.CURATED)
                &&standard.cell()==0,"Standard missiles must use real arsenal art");
        check(heavy.cell()==1&&torpedo.cell()==4,
                "Heavy missile and torpedo must have different physical silhouettes");
        check(heavy.fallback().equals(GuidedPayloadVisual.HEAVY)
                &&standard.fallback().equals(GuidedPayloadVisual.GUIDED),
                "Preapproved standalone art remains safe fallback");
        check(micro.cell()==3&&decoy.cell()==3,
                "Micro and decoy bodies must use the compact original seeker cut");
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
                &&he.width()>standard.width()&&coast.red()<standard.red()
                &&emp.cell()==3&&ap.cell()==2&&he.cell()==5,
                "Warhead and motor phase must remain visible in projectile art");
        for(var payload:OrdnanceDoctrine.Payload.values())
            for(var warhead:AmmunitionTable.Warhead.values())
                for(var phase:OrdnancePropulsion.Phase.values()){
                    var style=look(false,payload,warhead,phase,.82);
                    check(style.image().equals(GuidedPayloadVisual.CURATED)
                        &&style.cell()>=0&&style.cell()<GuidedPayloadVisual.CELLS,
                        "All types must reference one of six real curated sprite cuts");
                    check(style.width()>0&&style.height()>0,
                        "Every physical loadout needs bounded positive sprite size");
                }
        System.out.println("PASS approved physical missile/torpedo art routing, micro/heavy/decoy/warheads and silent-coast material");
    }
}
