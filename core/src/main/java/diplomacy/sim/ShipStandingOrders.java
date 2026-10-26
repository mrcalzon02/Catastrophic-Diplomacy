package diplomacy.sim;

/**
 * Permanent individual captain doctrine; all settings remain active together
 * throughout the engagement rather than being consumed as scripted waypoints.
 */
public record ShipStandingOrders(
        BattleOrders.Orders tactics,int aggression,int caution,int shieldWithdrawPercent) {
    public static final ShipStandingOrders DEFAULT =
            new ShipStandingOrders(BattleOrders.DEFAULT,50,50,0);
    public static final String[] SETTINGS={
            "AGGRESSION", "CAUTION", "RETREAT BELOW HULL", "FALL BACK BELOW SHIELDS",
            "MOVEMENT DOCTRINE", "TARGET DOCTRINE", "POINT DEFENSE",
            "FIGHTER PRIORITY", "ESCORT PRIORITY", "CAPITAL PRIORITY",
            "FIGHTER RANGE", "ESCORT RANGE", "CAPITAL RANGE"
    };
    private static final BattleOrders.Position[] MOVEMENTS={
            BattleOrders.Position.FREE,BattleOrders.Position.HOLD,
            BattleOrders.Position.KEEP_MOVING,BattleOrders.Position.CAUTIOUS,
            BattleOrders.Position.LAST_STAND
    };
    public ShipStandingOrders {
        if(tactics==null||aggression<0||aggression>100||caution<0||caution>100||
                shieldWithdrawPercent<0||shieldWithdrawPercent>100)
            throw new IllegalArgumentException("Invalid standing doctrine");
        if(tactics.anchorShipId()!=0||tactics.position()==BattleOrders.Position.ESCORT
                ||tactics.position()==BattleOrders.Position.FORMATION)
            throw new IllegalArgumentException("Standing templates cannot store unbound escort anchors");
    }
    private static int clamp(int number,int min,int max){return Math.max(min,Math.min(max,number));}
    private static int moved(int current,int step,int amount,int min,int max){
        return clamp(current+step*amount,min,max);
    }
    public ShipStandingOrders adjust(int setting,int step){
        BattleOrders.Orders next=tactics;
        int a=aggression,c=caution,shield=shieldWithdrawPercent;
        switch(setting){
            case 0 -> a=moved(a,step,10,0,100);
            case 1 -> c=moved(c,step,10,0,100);
            case 2 -> next=next.withRetreat(moved((int)Math.round(
                    next.retreatAtHullFraction()*100),step,5,0,100)/100.0);
            case 3 -> shield=moved(shield,step,10,0,100);
            case 4 -> {
                int index=0;
                for(int i=0;i<MOVEMENTS.length;i++)if(MOVEMENTS[i]==next.position())index=i;
                next=next.withPosition(MOVEMENTS[Math.floorMod(index+step,MOVEMENTS.length)]);
            }
            case 5 -> {
                var modes=BattleOrders.Targeting.values();
                next=next.withTargeting(modes[Math.floorMod(next.targeting().ordinal()+step,modes.length)]);
            }
            case 6 -> next=next.withPointDefense(next.pointDefense().next());
            case 7,8,9 -> {
                var size=BattleOrders.Size.values()[setting-7];
                var priority=next.forSize(size);
                next=next.withEngagement(size,moved(priority.priority(),step,10,0,100),priority.range());
            }
            case 10,11,12 -> {
                var size=BattleOrders.Size.values()[setting-10];
                var priority=next.forSize(size);
                next=next.withEngagement(size,priority.priority(),
                        moved((int)Math.round(priority.range()),step,30,30,1500));
            }
            default -> throw new IllegalArgumentException("Unknown standing orders row");
        }
        return new ShipStandingOrders(next,a,c,shield);
    }
    public String display(int setting){
        return switch(setting){
            case 0 -> aggression+"%";
            case 1 -> caution+"%";
            case 2 -> Math.round(tactics.retreatAtHullFraction()*100)+"% hull";
            case 3 -> shieldWithdrawPercent==0?"DISABLED":shieldWithdrawPercent+"% shields";
            case 4 -> tactics.position().name().replace('_',' ');
            case 5 -> tactics.targeting().name().replace('_',' ');
            case 6 -> tactics.pointDefense().name().replace('_',' ');
            case 7,8,9 -> tactics.forSize(BattleOrders.Size.values()[setting-7]).priority()+"%";
            case 10,11,12 -> Math.round(tactics.forSize(BattleOrders.Size.values()[setting-10]).range())+" units";
            default -> throw new IllegalArgumentException("Unknown standing orders row");
        };
    }
    public boolean mustWithdraw(Battle.Ship ship){
        if(ship==null)throw new IllegalArgumentException("Ship required");
        if(tactics.position()==BattleOrders.Position.LAST_STAND)return false;
        return (tactics.retreatAtHullFraction()>0&&
                    ship.hull<=ship.maxHull*tactics.retreatAtHullFraction())
                ||(shieldWithdrawPercent>0&&ship.maxShields>0&&
                    ship.shields<=ship.maxShields*shieldWithdrawPercent/100.0);
    }
    /** Aggression closes distance, caution extends it; default 50/50 is neutral. */
    public double engagementRangeMultiplier(){
        return Math.max(.45,Math.min(1.8,1+(caution-aggression)*.006));
    }
}
