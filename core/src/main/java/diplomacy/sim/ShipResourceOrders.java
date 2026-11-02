package diplomacy.sim;

/**
 * Immutable resource/fire standing order. No ammunition, cooldown or target
 * state is saved here; those belong to individual physical combatants.
 * Every legacy captain gets DEFAULT and retains the old RNG/fire behavior.
 */
public record ShipResourceOrders(FireIntent fireIntent,AmmoPolicy ammunition,
        int rationStartPercent,int reservePercent,int minimumRounds,int spacingTicks) {
    public enum FireIntent { IMMEDIATE, HOLD_OFFENSE }
    public enum AmmoPolicy {
        UNRESTRICTED, AUTO_RATION, HOLD_RESERVE, SPACE_VOLLEYS, ENERGY_ONLY
    }
    public static final ShipResourceOrders DEFAULT=new ShipResourceOrders(
            FireIntent.IMMEDIATE,AmmoPolicy.UNRESTRICTED,50,0,0,0);
    public ShipResourceOrders {
        if(fireIntent==null||ammunition==null||rationStartPercent<10||
                rationStartPercent>90||reservePercent<0||reservePercent>95||
                minimumRounds<0||minimumRounds>10000||
                spacingTicks<0||spacingTicks>900)
            throw new IllegalArgumentException("Invalid ship resource order");
    }
    public ShipResourceOrders withFireIntent(FireIntent next){
        return new ShipResourceOrders(next,ammunition,rationStartPercent,
                reservePercent,minimumRounds,spacingTicks);
    }
    public ShipResourceOrders withAmmunition(AmmoPolicy next){
        return new ShipResourceOrders(fireIntent,next,rationStartPercent,
                reservePercent,minimumRounds,spacingTicks);
    }
    public ShipResourceOrders withRationStart(int percent){
        return new ShipResourceOrders(fireIntent,ammunition,percent,
                reservePercent,minimumRounds,spacingTicks);
    }
    public ShipResourceOrders withReserve(int percent,int rounds){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,
                percent,rounds,spacingTicks);
    }
    public ShipResourceOrders withSpacingTicks(int ticks){
        return new ShipResourceOrders(fireIntent,ammunition,rationStartPercent,
                reservePercent,minimumRounds,ticks);
    }
    public boolean legacy(){return equals(DEFAULT);}
}
