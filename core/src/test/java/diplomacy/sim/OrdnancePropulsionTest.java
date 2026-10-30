package diplomacy.sim;

public final class OrdnancePropulsionTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        var p=OrdnancePropulsion.class;
        check(OrdnancePropulsion.forDoctrine(OrdnanceDoctrine.Mode.SINGLE)==
                OrdnancePropulsion.Mode.CONSTANT_BURN,"default unchanged: continuous burn");
        check(OrdnancePropulsion.forDoctrine(OrdnanceDoctrine.Mode.COLD_LAUNCH)==
                OrdnancePropulsion.Mode.COLD_START,"cold mode");
        check(OrdnancePropulsion.forDoctrine(OrdnanceDoctrine.Mode.HEAVY_STRIKE)==
                OrdnancePropulsion.Mode.BOOST_COAST_TERMINAL,"capital missile coast and terminal");
        check(OrdnancePropulsion.forDoctrine(OrdnanceDoctrine.Mode.MICRO_SWARM)==
                OrdnancePropulsion.Mode.PULSE_BURN,"micro swarm pulsed thrust");
        var c=OrdnancePropulsion.Mode.COLD_START;
        check(OrdnancePropulsion.phase(c,1,15,600,false)==
                OrdnancePropulsion.Phase.COLD_EJECT,"cold eject actually unpowered");
        check(!OrdnancePropulsion.burning(OrdnancePropulsion.Phase.COLD_EJECT),
                "cold phase has no engine glow");
        check(OrdnancePropulsion.phase(c,16,0,450,false)==
                OrdnancePropulsion.Phase.CRUISE,"ignite after cold launch");
        var heavy=OrdnancePropulsion.Mode.BOOST_COAST_TERMINAL;
        check(OrdnancePropulsion.phase(heavy,1,0,700,true)==
                OrdnancePropulsion.Phase.BOOST,"heavy strike initial booster");
        check(OrdnancePropulsion.phase(heavy,45,0,400,true)==
                OrdnancePropulsion.Phase.COAST,"booster expended, silent coasting");
        check(!OrdnancePropulsion.burning(OrdnancePropulsion.Phase.COAST),
                "coast produces no smoke or light");
        check(OrdnancePropulsion.guidanceMultiplier(OrdnancePropulsion.Phase.COAST)==0,
                "unpowered coasting cannot turn its velocity vector");
        check(OrdnancePropulsion.phase(heavy,60,0,160,true)==
                OrdnancePropulsion.Phase.TERMINAL,"terminal attack restarts engine");
        check(OrdnancePropulsion.speedMultiplier(OrdnancePropulsion.Phase.TERMINAL)>
                OrdnancePropulsion.speedMultiplier(OrdnancePropulsion.Phase.COAST),
                "terminal approach is accelerated physically");
        check(OrdnancePropulsion.guidanceMultiplier(OrdnancePropulsion.Phase.TERMINAL)>
                OrdnancePropulsion.guidanceMultiplier(OrdnancePropulsion.Phase.COAST),
                "terminal attack improves real maneuverability");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,3,0,700,false)==
                OrdnancePropulsion.Phase.BOOST,"first pulse burns");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,12,0,700,false)==
                OrdnancePropulsion.Phase.COAST,"pulse off");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,20,0,700,false)==
                OrdnancePropulsion.Phase.BOOST,"pulse restart");
        System.out.println("PASS constant burn, cold eject, boost-coast-terminal and pulse cycles");
    }
}
