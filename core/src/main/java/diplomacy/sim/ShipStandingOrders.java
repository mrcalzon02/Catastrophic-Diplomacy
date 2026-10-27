package diplomacy.sim;

/**
 * Permanent individual captain doctrine; all settings remain active together
 * throughout the engagement rather than being consumed as scripted waypoints.
 */
public record ShipStandingOrders(
        BattleOrders.Orders tactics,int aggression,int caution,int shieldWithdrawPercent,
        ShipTacticalDirectives directives) {
    public ShipStandingOrders(BattleOrders.Orders tactics,int aggression,int caution,int shieldWithdrawPercent){
        this(tactics,aggression,caution,shieldWithdrawPercent,ShipTacticalDirectives.DEFAULT);
    }
    public static final ShipStandingOrders DEFAULT =
            new ShipStandingOrders(BattleOrders.DEFAULT,50,50,0);
    public static final String[] SETTINGS={
            "AGGRESSION", "CAUTION", "RETREAT BELOW HULL", "FALL BACK BELOW SHIELDS",
            "MOVEMENT DOCTRINE", "TARGET DOCTRINE", "POINT DEFENSE",
            "FIGHTER PRIORITY", "ESCORT PRIORITY", "CAPITAL PRIORITY",
            "FIGHTER RANGE", "ESCORT RANGE", "CAPITAL RANGE",
            "ENGINE SPEED LIMIT", "PD INTERCEPTION PLAN", "PD PROTECTION SCOPE",
            "FIGHTER OPERATIONS", "DEFEND TARGET 1", "DEFEND TARGET 2",
            "DEFEND TARGET 3", "DEFEND TARGET 4", "ESCORT ASSIGNMENT", "ESCORT DISTANCE"
    };
    private static final BattleOrders.Position[] MOVEMENTS={
            BattleOrders.Position.FREE,BattleOrders.Position.HOLD,
            BattleOrders.Position.KEEP_MOVING,BattleOrders.Position.CAUTIOUS,
            BattleOrders.Position.LAST_STAND
    };
    public ShipStandingOrders {
        if(tactics==null||aggression<0||aggression>100||caution<0||caution>100||
                shieldWithdrawPercent<0||shieldWithdrawPercent>100||directives==null)
            throw new IllegalArgumentException("Invalid standing doctrine");
        if(tactics.anchorShipId()!=0||tactics.position()==BattleOrders.Position.ESCORT
                ||tactics.position()==BattleOrders.Position.FORMATION)
            throw new IllegalArgumentException("Standing templates cannot store unbound escort anchors");
    }
    private static int clamp(int number,int min,int max){return Math.max(min,Math.min(max,number));}
    private static int moved(int current,int step,int amount,int min,int max){
        return clamp(current+step*amount,min,max);
    }
    public ShipStandingOrders adjust(int setting,int step){return adjust(setting,step,-1,200);}
    public ShipStandingOrders adjust(int setting,int step,int selfSlot,int deployed){
        BattleOrders.Orders next=tactics;
        ShipTacticalDirectives d=directives;
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
            case 6 -> {
                var modes=BattleOrders.PointDefense.values();
                next=next.withPointDefense(modes[Math.floorMod(
                        next.pointDefense().ordinal()+step,modes.length)]);
            }
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
            case 13 -> d=d.withSpeed(moved(d.speedPercent(),step,5,25,100));
            case 14 -> d=d.withStrategy(cycle(d.interceptStrategy(),step,
                    ShipTacticalDirectives.InterceptStrategy.values()));
            case 15 -> d=d.withScope(cycle(d.defenseScope(),step,
                    ShipTacticalDirectives.DefenseScope.values()));
            case 16 -> d=d.withFighters(cycle(d.fighterMission(),step,
                    ShipTacticalDirectives.FighterMission.values()));
            case 17,18,19,20 -> d=d.withDefendSlot(setting-17,
                    nextReference(d.defendSlots().get(setting-17),step,selfSlot,deployed,
                            d.defendSlots(),setting-17));
            case 21 -> d=d.withEscort(nextReference(d.escortSlot(),step,selfSlot,deployed,
                    java.util.List.of(),-1));
            case 22 -> d=d.withEscortRange(moved(d.escortRange(),step,25,30,1500));
            default -> throw new IllegalArgumentException("Unknown standing orders row");
        }
        return new ShipStandingOrders(next,a,c,shield,d);
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
            case 13 -> directives.speedPercent()+"%";
            case 14 -> directives.interceptStrategy().name().replace('_',' ');
            case 15 -> directives.defenseScope().name().replace('_',' ');
            case 16 -> directives.fighterMission().name();
            case 17,18,19,20 -> refLabel(directives.defendSlots().get(setting-17));
            case 21 -> refLabel(directives.escortSlot());
            case 22 -> directives.escortRange()+" units";
            default -> throw new IllegalArgumentException("Unknown standing orders row");
        };
    }
    private static String refLabel(int slot){return slot<0?"NONE":"SHIP "+(slot+1);}
    private static <E extends Enum<E>> E cycle(E selected,int step,E[] choices){
        return choices[Math.floorMod(selected.ordinal()+step,choices.length)];
    }
    private static int nextReference(int selected,int step,int self,int count,
                                     java.util.List<Integer> used,int editing){
        if(count<1||count>200)throw new IllegalArgumentException("Invalid fleet size");
        int candidate=selected;
        for(int i=0;i<=count+1;i++){
            candidate=Math.floorMod(candidate+1+step,count+1)-1;
            if(candidate==self)continue;
            if(candidate>=0&&used.contains(candidate)&&used.indexOf(candidate)!=editing)continue;
            return candidate;
        }
        return -1;
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
