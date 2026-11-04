package diplomacy.sim;

/**
 * One drifting crew escape capsule at a time, with its original fighter
 * identity and actual ejection location. A capsule is not a replacement
 * fighter, sensor platform, or free addition to a carrier's crew.
 *
 * Capsule motion is deterministic and finite. Rescue requires a real,
 * nearby, non-withdrawing friendly ship with a spare occupied-crew berth.
 */
public final class FighterEscapePod {
    public static final double PICKUP_RANGE=28.0;
    public static final long LIFETIME_TICKS=45*30;
    public final int craftId,seat,sourceShipId,team;
    public final long bornTick;
    public final boolean campaignCrew;
    public double x,y;
    public final double vx,vy;
    private long lastStepTick;
    private boolean recovered,expired;

    public FighterEscapePod(int craftId,int seat,int sourceShipId,int team,
                             long bornTick,double x,double y,
                             double vx,double vy,boolean campaignCrew){
        if(craftId<=0||seat<0||seat>3||sourceShipId<=0||
                (team!=0&&team!=1)||bornTick<0||
                !BattleArena.contains(x,y)||
                !Double.isFinite(vx)||!Double.isFinite(vy))
            throw new IllegalArgumentException("Invalid ejected crew capsule");
        this.craftId=craftId;this.seat=seat;
        this.sourceShipId=sourceShipId;this.team=team;
        this.bornTick=bornTick;this.x=x;this.y=y;
        // Seat impulses are a fraction of the aircraft's recorded velocity,
        // not thrust, free acceleration or a targeting beacon.
        this.vx=vx*.15;this.vy=vy*.15;
        this.campaignCrew=campaignCrew;
        lastStepTick=bornTick;
    }
    public boolean recovered(){return recovered;}
    public boolean expired(){return expired;}
    public boolean active(){return !recovered&&!expired;}
    public long expiryTick(){return bornTick+LIFETIME_TICKS;}
    public void advance(long tick){
        if(tick<lastStepTick)throw new IllegalArgumentException("Capsule clock reversed");
        if(!active())return;
        long dt=tick-lastStepTick;
        x+=vx*Battle.DT*dt;
        y+=vy*Battle.DT*dt;
        lastStepTick=tick;
        if(tick>=expiryTick()||!BattleArena.contains(x,y))expired=true;
    }
    public boolean inReach(Battle.Ship ship){
        return active()&&ship!=null&&ship.team==team&&
                ship.alive()&&!ship.escaped&&!ship.withdrawing&&
                StrictMath.hypot(ship.x-x,ship.y-y)<=PICKUP_RANGE&&
                ship.crewCount+1<=
                        StrictMath.min(ship.crewBerths,ship.lifeSupport)+1e-9;
    }
    public void collect(Battle.Ship ship){
        if(!inReach(ship))
            throw new IllegalArgumentException("No physical friendly capsule recovery");
        recovered=true;
        if(campaignCrew)ship.crewCount+=1;
    }
}
