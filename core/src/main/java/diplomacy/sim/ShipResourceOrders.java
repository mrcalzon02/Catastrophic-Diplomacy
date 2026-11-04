package diplomacy.sim;

/**
 * Immutable per-captain ammunition AND thrust expenditure policy. The hull
 * has one physical fuel store: a reserve is an order about discretionary
 * thrust, never a second tank or a free source of reaction mass.
 */
public record ShipResourceOrders(FireIntent fireIntent,AmmoPolicy ammunition,
        int rationStartPercent,int reservePercent,int minimumRounds,int spacingTicks,
        FuelPolicy fuelPolicy,int fuelReservePercent,int economyStartPercent) {
    public enum FireIntent { IMMEDIATE, HOLD_OFFENSE }
    public enum AmmoPolicy {
        UNRESTRICTED, AUTO_RATION, HOLD_RESERVE, SPACE_VOLLEYS, ENERGY_ONLY
    }
    public enum FuelPolicy {
        UNRESTRICTED, ECONOMY, PROTECT_RESERVE, WITHDRAW_AT_RESERVE
    }
    public static final ShipResourceOrders DEFAULT=new ShipResourceOrders(
            FireIntent.IMMEDIATE,AmmoPolicy.UNRESTRICTED,50,0,0,0,
            FuelPolicy.UNRESTRICTED,25,60);

    /** V3 callers inherit the prior unlimited-thrust behavior. */
    public ShipResourceOrders(FireIntent fireIntent,AmmoPolicy ammunition,
            int rationStartPercent,int reservePercent,int minimumRounds,int spacingTicks) {
        this(fireIntent,ammunition,rationStartPercent,reservePercent,minimumRounds,
                spacingTicks,FuelPolicy.UNRESTRICTED,25,60);
    }
    public ShipResourceOrders {
        if(fireIntent==null||ammunition==null||fuelPolicy==null||
                rationStartPercent<10||rationStartPercent>90||
                reservePercent<0||reservePercent>95||
                minimumRounds<0||minimumRounds>10000||
                spacingTicks<0||spacingTicks>900||
                fuelReservePercent<0||fuelReservePercent>95||
                economyStartPercent<10||economyStartPercent>95)
            throw new IllegalArgumentException("Invalid ship resource order");
    }
    public ShipResourceOrders withFireIntent(FireIntent next){
        return new ShipResourceOrders(next,ammunition,rationStartPercent,reservePercent,
                minimumRounds,spacingTicks,fuelPolicy,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withAmmunition(AmmoPolicy next){
        return new ShipResourceOrders(fireIntent,next,rationStartPercent,reservePercent,
                minimumRounds,spacingTicks,fuelPolicy,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withRationStart(int percent){
        return new ShipResourceOrders(fireIntent,ammunition,percent,reservePercent,
                minimumRounds,spacingTicks,fuelPolicy,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withReserve(int percent,int rounds){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,percent,
                rounds,spacingTicks,fuelPolicy,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withSpacingTicks(int ticks){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,reservePercent,
                minimumRounds,ticks,fuelPolicy,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withFuelPolicy(FuelPolicy next){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,reservePercent,
                minimumRounds,spacingTicks,next,fuelReservePercent,economyStartPercent);
    }
    public ShipResourceOrders withFuelReserve(int percent){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,reservePercent,
                minimumRounds,spacingTicks,fuelPolicy,percent,economyStartPercent);
    }
    public ShipResourceOrders withEconomyStart(int percent){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,reservePercent,
                minimumRounds,spacingTicks,fuelPolicy,fuelReservePercent,percent);
    }

    /** A standing order, not an additional compartment or fuel allocation. */
    public double protectedFuel(Battle.Ship ship){
        if(ship.withdrawing||ship.orders.position()==BattleOrders.Position.LAST_STAND||
                fuelPolicy==FuelPolicy.UNRESTRICTED||
                fuelPolicy==FuelPolicy.ECONOMY)return 0;
        return Math.max(0,ship.maxFuel)*fuelReservePercent/100.0;
    }
    /** Only the selected automatic policy commits a fuel-triggered withdrawal. */
    public boolean withdrawForFuel(Battle.Ship ship){
        return fuelPolicy==FuelPolicy.WITHDRAW_AT_RESERVE&&fuelReservePercent>0
                &&ship.fuel<=ship.maxFuel*fuelReservePercent/100.0+1e-7;
    }
    /** Gradually reduce ordered velocity as fuel falls below the economy band. */
    public double thrustSpeedFactor(Battle.Ship ship){
        if(ship.withdrawing||ship.orders.position()==BattleOrders.Position.LAST_STAND||
                fuelPolicy==FuelPolicy.UNRESTRICTED||ship.maxFuel<=0)return 1;
        double percent=100.0*Math.max(0,ship.fuel)/ship.maxFuel;
        if(percent>=economyStartPercent)return 1;
        return .4+.6*percent/economyStartPercent;
    }
    public boolean legacy(){return equals(DEFAULT);}
}
