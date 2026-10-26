package diplomacy.sim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully authored starting positions and individual prebattle order queues.
 * One deterministic blueprint drives both the deployment UI and live battle.
 * No overlaps, out-of-zone coordinates, or orders for absent hulls are accepted.
 */
public final class CampaignDeployment {
    public static final double MIN_X=70, MAX_X=560, MIN_Y=90, MAX_Y=590;
    public static final double MIN_SPACING=23;
    private CampaignDeployment() {}

    public enum Formation {
        LINE("Battle line"), WEDGE("Attack wedge"), ECHELON("Echelon");
        public final String title;
        Formation(String title){this.title=title;}
    }
    public record Point(double x,double y) { }
    public record Loadout(int ships, Formation formation,
                          Map<Integer,Point> placements,
                          Map<Integer,List<ShipCommandQueue.Command>> orders,
                          Map<Integer,ShipStandingOrders> standings) {
        public Loadout(int ships,Formation formation) {
            this(ships,formation,Map.of(),Map.of(),Map.of());
        }
        public Loadout(int ships,Formation formation,Map<Integer,Point> placements,
                       Map<Integer,List<ShipCommandQueue.Command>> orders) {
            this(ships,formation,placements,orders,Map.of());
        }
        public Loadout {
            if(ships<1||ships>200||formation==null||placements==null||orders==null||standings==null)
                throw new IllegalArgumentException("Invalid campaign deployment");
            placements=Map.copyOf(placements);
            Map<Integer,List<ShipCommandQueue.Command>> sequences=new HashMap<>();
            for(var entry:orders.entrySet()){
                if(entry.getKey()==null||entry.getKey()<0||entry.getKey()>=ships||
                        entry.getValue()==null||entry.getValue().stream().anyMatch(x->x==null))
                    throw new IllegalArgumentException("Orders reference invalid ship index");
                sequences.put(entry.getKey(),List.copyOf(entry.getValue()));
            }
            orders=Map.copyOf(sequences);
            standings=Map.copyOf(standings);
            for(var entry:standings.entrySet()){
                if(entry.getKey()==null||entry.getKey()<0||entry.getKey()>=ships||entry.getValue()==null)
                    throw new IllegalArgumentException("Standing doctrine refers to absent ship");
            }
            for(var entry:placements.entrySet()){
                if(entry.getKey()==null||entry.getKey()<0||entry.getKey()>=ships)
                    throw new IllegalArgumentException("Placement refers to absent ship");
                checkZone(entry.getValue());
            }
            // Validate the *entire* resulting fleet; moving a ship may collide
            // with an unedited preset position as well as with another custom position.
            for(int i=0;i<ships;i++){
                Point a=placements.getOrDefault(i,defaultPoint(ships,formation,i));
                checkZone(a);
                for(int j=0;j<i;j++){
                    Point b=placements.getOrDefault(j,defaultPoint(ships,formation,j));
                    if(Math.hypot(a.x()-b.x(),a.y()-b.y())<MIN_SPACING)
                        throw new IllegalArgumentException("Deployment ships overlap: "+i+" / "+j);
                }
            }
        }
        public Loadout withShips(int change,LevelEconomy.Level mission) {
            if(mission==null)throw new IllegalArgumentException("Mission required");
            return withShips(change,mission.maximumFleet());
        }
        public Loadout withShips(int change,int fleetLimit) {
            if(fleetLimit<1||fleetLimit>200)throw new IllegalArgumentException("Invalid fleet cap");
            int selected=Math.max(1,Math.min(fleetLimit,ships+change));
            Map<Integer,Point> positions=new HashMap<>(placements);
            positions.keySet().removeIf(i->i>=selected);
            Map<Integer,List<ShipCommandQueue.Command>> queues=new HashMap<>(orders);
            queues.keySet().removeIf(i->i>=selected);
            Map<Integer,ShipStandingOrders> standing=new HashMap<>(standings);
            standing.keySet().removeIf(i->i>=selected);
            return new Loadout(selected,formation,positions,queues,standing);
        }
        public Loadout withFormation(int steps) {
            Formation[] options=Formation.values();
            // Changing to a new preset explicitly resets any custom positions
            // but retains existing orders for surviving vessels.
            return new Loadout(ships,options[Math.floorMod(formation.ordinal()+steps,options.length)],
                    Map.of(),orders,standings);
        }
        public Loadout withPlacement(int ship,double x,double y) {
            if(ship<0||ship>=ships)throw new IllegalArgumentException("Ship index out of range");
            Map<Integer,Point> copy=new HashMap<>(placements);
            copy.put(ship,new Point(x,y));
            return new Loadout(ships,formation,copy,orders,standings);
        }
        public Loadout appendOrder(int ship,ShipCommandQueue.Command command) {
            if(ship<0||ship>=ships||command==null)
                throw new IllegalArgumentException("Cannot assign command to missing ship");
            Map<Integer,List<ShipCommandQueue.Command>> copy=new HashMap<>(orders);
            java.util.ArrayList<ShipCommandQueue.Command> items=new java.util.ArrayList<>(ordersFor(ship));
            items.add(command);
            copy.put(ship,List.copyOf(items));
            return new Loadout(ships,formation,placements,copy,standings);
        }
        public Loadout undoOrder(int ship) {
            if(ship<0||ship>=ships)throw new IllegalArgumentException("Missing ship");
            List<ShipCommandQueue.Command> current=ordersFor(ship);
            if(current.isEmpty())return this;
            Map<Integer,List<ShipCommandQueue.Command>> copy=new HashMap<>(orders);
            copy.put(ship,List.copyOf(current.subList(0,current.size()-1)));
            return new Loadout(ships,formation,placements,copy,standings);
        }
        public Loadout clearOrders(int ship) {
            if(ship<0||ship>=ships)throw new IllegalArgumentException("Missing ship");
            Map<Integer,List<ShipCommandQueue.Command>> copy=new HashMap<>(orders);
            copy.remove(ship);
            return new Loadout(ships,formation,placements,copy,standings);
        }
        public List<ShipCommandQueue.Command> ordersFor(int ship) {
            if(ship<0||ship>=ships)throw new IllegalArgumentException("Missing ship");
            return orders.getOrDefault(ship,List.of());
        }
        public ShipStandingOrders standingFor(int ship){
            if(ship<0||ship>=ships)throw new IllegalArgumentException("Missing ship");
            return standings.getOrDefault(ship,ShipStandingOrders.DEFAULT);
        }
        public Loadout withStanding(int ship,ShipStandingOrders policy) {
            if(ship<0||ship>=ships||policy==null)throw new IllegalArgumentException("Missing ship doctrine");
            Map<Integer,ShipStandingOrders> copy=new HashMap<>(standings);
            copy.put(ship,policy);
            return new Loadout(ships,formation,placements,orders,copy);
        }
        /** Restore only the policies belonging to currently commissioned hull slots. */
        public Loadout withStandingProfiles(Map<Integer,ShipStandingOrders> policies) {
            if(policies==null)throw new IllegalArgumentException("Missing profiles");
            Map<Integer,ShipStandingOrders> selected=new HashMap<>();
            for(var entry:policies.entrySet())if(entry.getKey()>=0&&entry.getKey()<ships)
                selected.put(entry.getKey(),entry.getValue());
            return new Loadout(ships,formation,placements,orders,selected);
        }
        public Point at(int index) {
            if(index<0||index>=ships)throw new IllegalArgumentException("Unknown deployed ship");
            return placements.getOrDefault(index,defaultPoint(ships,formation,index));
        }
        public void apply(Battle battle) {
            if(battle==null)throw new IllegalArgumentException("Battle required");
            int i=0;
            for(Battle.Ship ship:battle.ships())if(ship.team==0) {
                if(i>=ships)throw new IllegalArgumentException("Too many allied ships in battle");
                Point point=at(i);
                ship.x=point.x();ship.y=point.y();
                ship.vx=0;ship.vy=0;
                battle.setShipCommandQueue(ship.id,ordersFor(i));
                ShipStandingOrders policy=standings.get(i);
                if(policy!=null)battle.setShipStandingOrders(ship.id,policy);
                i++;
            }
            if(i!=ships)throw new IllegalArgumentException("Insufficient allied ships in battle");
        }
    }
    private static void checkZone(Point p) {
        if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||
                p.x()<MIN_X||p.x()>MAX_X||p.y()<MIN_Y||p.y()>MAX_Y)
            throw new IllegalArgumentException("Ship outside deployment zone");
    }
    private static Point defaultPoint(int ships,Formation formation,int index) {
        int columns=Math.min(20,(int)Math.ceil(Math.sqrt(ships*1.5)));
        int rows=(ships+columns-1)/columns;
        int col=index%columns,row=index/columns;
        double spacingX=Math.min(25.0,280.0/Math.max(1,rows-1));
        double spacingY=Math.min(27.0,430.0/Math.max(1,columns-1));
        double centered=col-(columns-1)*0.5;
        return switch(formation){
            case LINE -> new Point(425-row*spacingX,335+centered*spacingY);
            case WEDGE -> new Point(450-row*spacingX-Math.abs(centered)*9,335+centered*spacingY);
            case ECHELON -> new Point(410-row*spacingX+centered*6,335+centered*spacingY);
        };
    }
    public static Loadout defaultFor(LevelEconomy.Level level) {
        if(level==null)throw new IllegalArgumentException("Mission required");
        return new Loadout(Math.min(level.maximumFleet(),4+(level.index()-1)/2),Formation.LINE);
    }
}
