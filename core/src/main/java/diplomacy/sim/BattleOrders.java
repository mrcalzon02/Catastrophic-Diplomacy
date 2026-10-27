package diplomacy.sim;

import java.util.Objects;

/**
 * Immutable fleet/ship captain orders. Original implementation of tactical
 * behavior concepts; not a port of any other game's data or code.
 *
 * Default orders are deliberately equivalent to the existing autonomous
 * skirmish. Updating the PD selector alone does not change navigation AI.
 */
public final class BattleOrders {
    private BattleOrders() { }
    public enum PointDefense {
        BALANCED,          // both hostile ordnance and enemy craft
        INTERCEPT_ONLY,    // missiles, torpedoes, and slow ballistic projectiles
        ANTI_CRAFT,        // incoming hostile fighters/drones only
        HOLD_FIRE;         // no point defense expenditure
        public PointDefense next() {
            PointDefense[] all=values();
            return all[(ordinal()+1)%all.length];
        }
        public boolean ordnance(){return this==BALANCED||this==INTERCEPT_ONLY;}
        public boolean craft(){return this==BALANCED||this==ANTI_CRAFT;}
    }
    public enum Size { FIGHTER, ESCORT, CAPITAL }
    public enum Targeting { NEAREST, COOPERATE, VULTURE, RETALIATE, PROTECT }
    public enum Position { FREE, HOLD, KEEP_MOVING, ESCORT, FORMATION, CAUTIOUS, LAST_STAND }
    public record Engagement(int priority,double range) {
        public Engagement {
            if(priority<0||priority>100||!Double.isFinite(range)||range<30||range>1500)
                throw new IllegalArgumentException("Invalid engagement priority or distance");
        }
    }
    public record Orders(PointDefense pointDefense,Targeting targeting,Position position,
                         Engagement fighters,Engagement escorts,Engagement capitals,
                         int anchorShipId,double escortRadius,double retreatAtHullFraction) {
        public Orders {
            Objects.requireNonNull(pointDefense);Objects.requireNonNull(targeting);
            Objects.requireNonNull(position);Objects.requireNonNull(fighters);
            Objects.requireNonNull(escorts);Objects.requireNonNull(capitals);
            if(anchorShipId<0 || !Double.isFinite(escortRadius)||escortRadius<20||escortRadius>1600
                    || !Double.isFinite(retreatAtHullFraction)||retreatAtHullFraction<0
                    || retreatAtHullFraction>1)
                throw new IllegalArgumentException("Invalid escort/retreat policy");
        }
        public Engagement forSize(Size size) {
            return switch(size) {
                case FIGHTER -> fighters;
                case ESCORT -> escorts;
                case CAPITAL -> capitals;
            };
        }
        public Orders withPointDefense(PointDefense next) {
            return new Orders(next,targeting,position,fighters,escorts,capitals,
                    anchorShipId,escortRadius,retreatAtHullFraction);
        }
        public Orders withTargeting(Targeting next) {
            return new Orders(pointDefense,next,position,fighters,escorts,capitals,
                    anchorShipId,escortRadius,retreatAtHullFraction);
        }
        public Orders withPosition(Position next) {
            return new Orders(pointDefense,targeting,next,fighters,escorts,capitals,
                    anchorShipId,escortRadius,retreatAtHullFraction);
        }
        public Orders withEngagement(Size size,int priority,double range) {
            Engagement updated=new Engagement(priority,range);
            return new Orders(pointDefense,targeting,position,
                    size==Size.FIGHTER?updated:fighters,
                    size==Size.ESCORT?updated:escorts,
                    size==Size.CAPITAL?updated:capitals,
                    anchorShipId,escortRadius,retreatAtHullFraction);
        }
        public Orders withAnchor(int friendlyId,double radius) {
            return new Orders(pointDefense,targeting,position,fighters,escorts,capitals,
                    friendlyId,radius,retreatAtHullFraction);
        }
        public Orders withRetreat(double fraction) {
            return new Orders(pointDefense,targeting,position,fighters,escorts,capitals,
                    anchorShipId,escortRadius,fraction);
        }
        /** Orders can exclude a size category without forbidding defensive PD. */
        public boolean targets(Size size){return forSize(size).priority()>0;}
    }
    public static final Orders DEFAULT=new Orders(
            PointDefense.BALANCED,Targeting.NEAREST,Position.FREE,
            new Engagement(100,210),new Engagement(100,210),
            new Engagement(100,210),0,180,0.25);
    public static Size size(Battle.Ship ship) {
        if(ship.hullClass==null)return Size.ESCORT;
        return switch(ship.hullClass.spriteHull()) {
            case INTERCEPTOR -> Size.FIGHTER;
            case CORVETTE,MISSILE_CORVETTE,FRIGATE,DESTROYER -> Size.ESCORT;
            case LIGHT_CRUISER,CRUISER,HEAVY_CRUISER,BATTLECRUISER,CARRIER,DREADNOUGHT -> Size.CAPITAL;
            default -> throw new IllegalStateException("Unmapped sprite-size category");
        };
    }
}
