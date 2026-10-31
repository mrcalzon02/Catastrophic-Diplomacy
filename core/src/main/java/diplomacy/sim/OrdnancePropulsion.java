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
    /** One-time ejection impulse. Thereafter a missile keeps world velocity. */
    public static double initialSpeedMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT -> .20;
            case BOOST -> .72;
            default -> 1;
        };
    }
    /** Desired powered speed cap, NOT an instantaneous phase speed. */
    public static double speedMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT -> .20;
            case BOOST -> 1.24;
            case COAST -> 1;
            case TERMINAL -> 1.55;
            case CRUISE -> 1;
        };
    }
    /** Acceleration in units of reference projectile speed per second. */
    public static double accelerationMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT, COAST -> 0;
            case BOOST -> 5.0;
            case TERMINAL -> 7.0;
            case CRUISE -> 3.6;
        };
    }
    /** Thermal/plume emission changes; radar cross section does not disappear. */
    public static double emissionMultiplier(Phase phase){
        return switch(phase){
            case COLD_EJECT -> .35;
            case BOOST -> 1.70;
            case COAST -> .33;
            case TERMINAL -> 2.20;
            case CRUISE -> 1;
        };
    }
    /** World velocity integration with bounded thrust and finite delta-v budget.
     * A cold/ejected/coasting body cannot change direction or speed by magic. */
    public record Velocity(double vx,double vy) {}
    public static Velocity advanceVelocity(double vx,double vy,double dx,double dy,
                                           double ratedSpeed,double guidance,Phase phase,
                                           double dt,double remainingDeltaV){
        if(phase==null||!Double.isFinite(vx)||!Double.isFinite(vy)
                ||!Double.isFinite(dx)||!Double.isFinite(dy)
                ||!Double.isFinite(ratedSpeed)||ratedSpeed<=0
                ||!Double.isFinite(guidance)||!Double.isFinite(dt)||dt<=0
                ||!Double.isFinite(remainingDeltaV)||remainingDeltaV<0)
            throw new IllegalArgumentException("Invalid missile velocity integration");
        if(!burning(phase)||remainingDeltaV==0)return new Velocity(vx,vy);
        double speed=StrictMath.hypot(vx,vy);
        double range=StrictMath.hypot(dx,dy);
        double hx=speed>1e-9?vx/speed:range>1e-9?dx/range:1;
        double hy=speed>1e-9?vy/speed:range>1e-9?dy/range:0;
        double blend=StrictMath.max(0,StrictMath.min(.35,guidance));
        double steerX=hx*(1-blend)+(range>1e-9?dx/range:hx)*blend;
        double steerY=hy*(1-blend)+(range>1e-9?dy/range:hy)*blend;
        double length=StrictMath.hypot(steerX,steerY);
        if(length<1e-9)return new Velocity(vx,vy);
        double cap=ratedSpeed*speedMultiplier(phase);
        double wantX=cap*steerX/length, wantY=cap*steerY/length;
        double changeX=wantX-vx, changeY=wantY-vy;
        double delta=StrictMath.hypot(changeX,changeY);
        double maxDelta=StrictMath.min(remainingDeltaV,
                ratedSpeed*accelerationMultiplier(phase)*dt);
        if(delta>maxDelta&&delta>0){
            changeX*=maxDelta/delta;changeY*=maxDelta/delta;
        }
        double nextX=vx+changeX,nextY=vy+changeY;
        double finalSpeed=StrictMath.hypot(nextX,nextY);
        if(finalSpeed>cap&&finalSpeed>0){
            nextX*=cap/finalSpeed;nextY*=cap/finalSpeed;
        }
        return new Velocity(nextX,nextY);
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
