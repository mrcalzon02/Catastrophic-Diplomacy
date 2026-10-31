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
        check(OrdnancePropulsion.accelerationMultiplier(OrdnancePropulsion.Phase.TERMINAL)>0
                &&OrdnancePropulsion.accelerationMultiplier(OrdnancePropulsion.Phase.COAST)==0,
                "terminal approach has thrust; coast has none");
        check(OrdnancePropulsion.guidanceMultiplier(OrdnancePropulsion.Phase.TERMINAL)>
                OrdnancePropulsion.guidanceMultiplier(OrdnancePropulsion.Phase.COAST),
                "terminal attack improves real maneuverability");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,3,0,700,false)==
                OrdnancePropulsion.Phase.BOOST,"first pulse burns");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,12,0,700,false)==
                OrdnancePropulsion.Phase.COAST,"pulse off");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.PULSE_BURN,20,0,700,false)==
                OrdnancePropulsion.Phase.BOOST,"pulse restart");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.SILENT_GHOST,10,0,600,false)==
                OrdnancePropulsion.Phase.BOOST,"ghost booster");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.SILENT_GHOST,32,0,600,false)==
                OrdnancePropulsion.Phase.COAST,"ghost persists without exhaust");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.SILENT_GHOST,60,0,130,false)==
                OrdnancePropulsion.Phase.TERMINAL,"ghost terminal motor relights");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.TERMINAL_SPRINT,10,0,600,false)==
                OrdnancePropulsion.Phase.CRUISE,"sprinter cruises initially");
        check(OrdnancePropulsion.phase(OrdnancePropulsion.Mode.TERMINAL_SPRINT,40,0,150,false)==
                OrdnancePropulsion.Phase.TERMINAL,"sprinter accelerates terminally");
        // Physics contract: no velocity or heading change without propellant/thrust.
        var coast=OrdnancePropulsion.advanceVelocity(180,40,-900,0,220,1,
                OrdnancePropulsion.Phase.COAST,Battle.DT,1000);
        check(coast.vx()==180&&coast.vy()==40,"inertial coast preserves full velocity");
        var eject=OrdnancePropulsion.advanceVelocity(44,0,0,500,220,1,
                OrdnancePropulsion.Phase.COLD_EJECT,Battle.DT,1000);
        check(eject.vx()==44&&eject.vy()==0,"cold eject cannot steer or accelerate");
        var noFuel=OrdnancePropulsion.advanceVelocity(180,40,-900,0,220,1,
                OrdnancePropulsion.Phase.TERMINAL,Battle.DT,0);
        check(noFuel.vx()==180&&noFuel.vy()==40,"exhausted thrusters cannot turn");
        var sprint=OrdnancePropulsion.advanceVelocity(180,40,900,0,220,1,
                OrdnancePropulsion.Phase.TERMINAL,Battle.DT,1000);
        double spent=StrictMath.hypot(sprint.vx()-180,sprint.vy()-40);
        check(spent>0&&spent<=220*7.0*Battle.DT+1e-7,
                "terminal thrust consumes bounded delta-v");
        var capped=OrdnancePropulsion.advanceVelocity(180,40,900,0,220,1,
                OrdnancePropulsion.Phase.TERMINAL,Battle.DT,2);
        check(StrictMath.hypot(capped.vx()-180,capped.vy()-40)<=2+1e-7,
                "finite propellant bounds actual velocity change");
        check(OrdnancePropulsion.emissionMultiplier(OrdnancePropulsion.Phase.COAST)
                    <OrdnancePropulsion.emissionMultiplier(OrdnancePropulsion.Phase.BOOST),
                "coasting missile has lower real emission than active booster");
        var profile=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        double quiet=ProjectileModel.acquisitionRange(250,1,1,
                ProjectileModel.STANDARD_PD,profile,
                OrdnancePropulsion.emissionMultiplier(OrdnancePropulsion.Phase.COAST));
        double burning=ProjectileModel.acquisitionRange(250,1,1,
                ProjectileModel.STANDARD_PD,profile,
                OrdnancePropulsion.emissionMultiplier(OrdnancePropulsion.Phase.TERMINAL));
        check(quiet>0&&quiet<burning,"quiet coast remains detectable by real cross section");
        check(ProjectileModel.interceptionChance(profile,ProjectileModel.STANDARD_PD,
                1,1,440)<ProjectileModel.interceptionChance(profile,
                ProjectileModel.STANDARD_PD,1,1,110),
                "fast terminal missile is actually harder to track");
        System.out.println("PASS inertial coasting, cold ejection, finite acceleration,"
                +" dynamic PD signatures, physical terminal sprint and motor phases");
    }
}
