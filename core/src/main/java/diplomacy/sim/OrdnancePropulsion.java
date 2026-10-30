package diplomacy.sim;

/**
 * Physical engine schedules for guided ordnance, independent of salvo size.
 * Flight-time expiry, damage, ammunition and tracking remain authoritative.
 * Renderer consumes the ACTUAL phase chosen by the simulation each tick.
 */
public final class OrdnancePropulsion {
    private OrdnancePropulsion(){}
    public enum Mode { CONSTANT_BURN, COLD_START, BOOST_COAST_TERMINAL, PULSE_BURN }
    public enum Phase { COLD_EJECT, BOOST, COAST, TERMINAL, CRUISE }
    public static Mode forDoctrine(OrdnanceDoctrine.Mode doctrine){
        if(doctrine==null)throw new IllegalArgumentException("Launch doctrine required");
        return switch(doctrine){
            case COLD_LAUNCH -> Mode.COLD_START;
            case HEAVY_STRIKE -> Mode.BOOST_COAST_TERMINAL;
            case MICRO_SWARM -> Mode.PULSE_BURN;
            default -> Mode.CONSTANT_BURN;
        };
    }
    public static Phase phase(Mode mode,int flightTicks,int ignitionDelayTicks,
                              double targetDistance,boolean torpedo){
        if(mode==null||flightTicks<0||ignitionDelayTicks<0||
                !Double.isFinite(targetDistance)||targetDistance<0)
            throw new IllegalArgumentException("Invalid ordnance propulsion state");
        if(ignitionDelayTicks>0)return Phase.COLD_EJECT;
        return switch(mode){
            case COLD_START,CONSTANT_BURN -> Phase.CRUISE;
            case BOOST_COAST_TERMINAL ->
                targetDistance<=(torpedo?190:135)?Phase.TERMINAL:
                    flightTicks<=(torpedo?25:18)?Phase.BOOST:Phase.COAST;
            case PULSE_BURN -> flightTicks%18<9?Phase.BOOST:Phase.COAST;
        };
    }
    public static boolean burning(Phase phase){
        return phase==Phase.BOOST||phase==Phase.TERMINAL||phase==Phase.CRUISE;
    }
    public static double speedMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT -> .20;
            case BOOST -> 1.08;
            case COAST -> .86;
            case TERMINAL -> 1.16;
            case CRUISE -> 1;
        };
    }
    public static double guidanceMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT -> 0;
            case BOOST -> .78;
            case COAST -> 0;
            case TERMINAL -> 1.35;
            case CRUISE -> 1;
        };
    }
}
