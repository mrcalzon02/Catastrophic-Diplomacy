package diplomacy.sim;

/**
 * Fixed world-space envelope for ALL fleet sizes. The original 1280x720
 * deployment viewport sits at its center; 200-vs-200 uses the same arena as a
 * duel, with substantial room beyond the initial formations to maneuver.
 * No moving camera, weapon or projectile can resize the simulation.
 */
public final class BattleArena {
    private BattleArena(){}
    public static final double MIN_X=-1280,MAX_X=2560;
    public static final double MIN_Y=-720,MAX_Y=1440;
    public static final int MAX_SHIPS_PER_SIDE=200;
    public static final double MAX_GUIDED_SECONDS=20;
    public static final double MAX_ROCKET_SECONDS=10;
    public static final double MAX_BALLISTIC_SECONDS=12;
    public static final double MAX_POINT_DEFENSE_SECONDS=3;

    public static boolean contains(double x,double y){
        return Double.isFinite(x)&&Double.isFinite(y)
                &&x>=MIN_X&&x<=MAX_X&&y>=MIN_Y&&y<=MAX_Y;
    }
    public static double x(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite battle X");
        return StrictMath.max(MIN_X,StrictMath.min(MAX_X,value));
    }
    public static double y(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite battle Y");
        return StrictMath.max(MIN_Y,StrictMath.min(MAX_Y,value));
    }
    /** Ships bounce neither elastically nor via a free steering impulse.
     * Inertial components into a hard boundary stop there without extra fuel. */
    public static void contain(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Missing ship");
        double cx=x(ship.x),cy=y(ship.y);
        if(cx!=ship.x){ship.vx=0;ship.x=cx;}
        if(cy!=ship.y){ship.vy=0;ship.y=cy;}
        if(ship.x==MIN_X&&ship.vx<0||ship.x==MAX_X&&ship.vx>0)ship.vx=0;
        if(ship.y==MIN_Y&&ship.vy<0||ship.y==MAX_Y&&ship.vy>0)ship.vy=0;
    }
    public static void contain(Battle.Craft craft){
        if(craft==null)throw new IllegalArgumentException("Missing craft");
        craft.x=x(craft.x);craft.y=y(craft.y);
    }
}
