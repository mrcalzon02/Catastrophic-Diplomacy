package diplomacy.sim;

/**
 * Pure authorization policy for a new offensive volley/shot. The Battle is
 * the only authority that spends ammunition and produces real firing events.
 * In-flight bodies and already-authorized salvos do not ask permission again.
 */
public final class ShipFireControl {
    private ShipFireControl(){}
    public static int protectedRounds(ShipResourceOrders order,int capacity){
        if(order==null||capacity<0)throw new IllegalArgumentException("Invalid resource budget");
        int percent=(int)Math.ceil(capacity*order.reservePercent()/100.0);
        return Math.max(order.minimumRounds(),percent);
    }
    public static boolean mayBegin(ShipResourceOrders order,boolean consumesAmmo,
            int stock,int capacity,int committed,int cost,long tick,long eligibleTick){
        if(order==null||stock<0||capacity<0||committed<0||cost<0)
            throw new IllegalArgumentException("Invalid resource permission");
        if(order.fireIntent()==ShipResourceOrders.FireIntent.HOLD_OFFENSE)return false;
        if(!consumesAmmo)return true; // Energy families consume POWER/HEAT through Battle.
        if(order.ammunition()==ShipResourceOrders.AmmoPolicy.ENERGY_ONLY)return false;
        if(tick<eligibleTick)return false;
        return stock-(long)committed-cost>=protectedRounds(order,capacity);
    }
    /** Relative firing frequency is continuous, including at 50% stock. */
    public static double rateFraction(ShipResourceOrders order,int stock,int capacity){
        if(order==null||stock<0||capacity<0)
            throw new IllegalArgumentException("Invalid ammunition fraction");
        if(order.ammunition()!=ShipResourceOrders.AmmoPolicy.AUTO_RATION)return 1.0;
        double fraction=capacity<=0?1.0:Math.min(1.0,stock/(double)capacity);
        double threshold=order.rationStartPercent()/100.0;
        return fraction>=threshold?1.0:Math.max(.20,fraction/threshold);
    }
    /** Actual physical reload remains mandatory; spacing cannot speed it up. */
    public static long nextEligible(ShipResourceOrders order,int stock,int capacity,
            double physicalCooldownSeconds,long tick){
        if(order==null||stock<0||capacity<0||
                !Double.isFinite(physicalCooldownSeconds)||physicalCooldownSeconds<0||
                tick<0)throw new IllegalArgumentException("Invalid firing cadence");
        double baseTicks=physicalCooldownSeconds*30.0;
        long ration=(long)Math.ceil(baseTicks/rateFraction(order,stock,capacity));
        return tick+Math.max(Math.max(0,order.spacingTicks()),ration);
    }
}
