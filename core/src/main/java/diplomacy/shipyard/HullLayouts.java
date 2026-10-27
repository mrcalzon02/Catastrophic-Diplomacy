package diplomacy.shipyard;

import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative faction/hull-space hardpoint layout for the shared shipyard.
 * Forward is +Y. Positions are normalized design coordinates, NOT world pixels.
 * Stable slot IDs/categories come from Hull.slots; only spatial positioning varies.
 * Blueprint files therefore remain index-compatible with older ship designs.
 */
public final class HullLayouts {
    private HullLayouts() { }
    public record Point(int x, int y) { }
    private static final Map<String,List<Shipyard.Slot>> CACHE = new ConcurrentHashMap<>();

    /** Lazily cached; gameplay and per-frame rendering share identical immutable coordinates. */
    public static List<Shipyard.Slot> slots(Battle.Faction faction, Shipyard.Hull hull) {
        if(faction==null||hull==null)throw new IllegalArgumentException("Missing faction or hull");
        return CACHE.computeIfAbsent(faction.name()+"/"+hull.name(),
                key -> buildSlots(faction,hull));
    }
    /** A protected central plant, side/forward weapon arcs, peripheral defense, aft support. */
    private static List<Shipyard.Slot> buildSlots(Battle.Faction faction, Shipyard.Hull hull) {
        int[] counts = new int[Shipyard.Mount.values().length];
        for (Shipyard.Slot slot : hull.slots) counts[slot.mount().ordinal()]++;
        int[] seen = new int[counts.length];
        List<Shipyard.Slot> answer = new ArrayList<>();
        int size = hull.spriteHull() == Shipyard.Hull.INTERCEPTOR ? -7
                : hull.spriteHull() == Shipyard.Hull.CORVETTE || hull.spriteHull() == Shipyard.Hull.MISSILE_CORVETTE ? -4
                : hull.spriteHull() == Shipyard.Hull.FRIGATE ? -2 : 0;
        for (Shipyard.Slot slot : hull.slots) {
            int kind=slot.mount().ordinal(), n=seen[kind]++, total=counts[kind];
            int x,y;
            switch(slot.mount()) {
                case SYSTEM -> {
                    // 3 longitudinal rows of deep, central machinery compartments.
                    x=(n%3-1)*15;
                    y=24-(n/3)*17;
                }
                case WEAPON -> {
                    // Opposed banks; the odd last mount sits on the bow centerline.
                    int bank=n/2;
                    x=(n%2==0?-1:1)*(29+Math.min(2,bank)*3+size);
                    y=37-bank*14;
                    if(total%2==1 && n==total-1){x=0;y=40;}
                }
                case DEFENSE -> {
                    x=total==1?0:(int)Math.round(-34.0+68.0*n/(total-1));
                    y=-30-(n%2)*4;
                }
                case SUPPORT -> {
                    x=total==1?0:(int)Math.round(-30.0+60.0*(n%4)/Math.max(1,Math.min(total,4)-1));
                    y=-47-(n/4)*12;
                }
                case HABITAT -> {
                    // Internal crew decks lie on either side of the engine spine.
                    x=n%2==0?-17:17;
                    y=-10;
                }
                default -> throw new IllegalStateException("Unhandled mount");
            }
            // Each civilization has a different spatial doctrine, not merely a different tint.
            switch(faction) {
                case TERRAN -> { /* axial reactor spine with paired broadside batteries */ }
                case DRAXIAN, VAERITH_PRISM -> {
                    if(slot.mount()==Shipyard.Mount.WEAPON) x=(int)Math.round(x*1.24);
                    if(slot.mount()==Shipyard.Mount.SYSTEM) y+=5;
                    if(slot.mount()==Shipyard.Mount.DEFENSE) y+=3;
                }
                case CARNELIAN, NYX_BIOLITH -> {
                    if(slot.mount()==Shipyard.Mount.WEAPON) x=(int)Math.round(x*0.85);
                    if(slot.mount()==Shipyard.Mount.DEFENSE) {x=(int)Math.round(x*1.20);y-=4;}
                    if(slot.mount()==Shipyard.Mount.SYSTEM) y-=6;
                }
                case ZIPPLEDROW, UMBRA_FOUNDRY -> {
                    if(slot.mount()==Shipyard.Mount.WEAPON) {x=(int)Math.round(x*0.93);y+=9;}
                    if(slot.mount()==Shipyard.Mount.SUPPORT) y-=2;
                    if(slot.mount()==Shipyard.Mount.SYSTEM) y+=3;
                }
                case UZIPT, ZELTHAR_LUMINARI -> {
                    if(slot.mount()==Shipyard.Mount.WEAPON) {x=(int)Math.round(x*1.17);y+=4;}
                    // Defense remains on the separated aft perimeter.
                    if(slot.mount()==Shipyard.Mount.SYSTEM) x+=3;
                }
                case MANDRALLY, AUREATE_SYNOD -> {
                    if(slot.mount()==Shipyard.Mount.SYSTEM) x=(int)Math.round(x*1.35);
                    if(slot.mount()==Shipyard.Mount.SUPPORT) {x=(int)Math.round(x*1.16);y+=2;}
                    if(slot.mount()==Shipyard.Mount.WEAPON) {x=(int)Math.round(x*1.30);y-=6;}
                }
            }
            answer.add(new Shipyard.Slot(slot.index(),slot.mount(),x,y));
        }
        return projectToHull(answer,outline(faction,hull));
    }

    /**
     * Ensure every hardpoint really belongs to the chosen silhouette, with 10 units
     * of spacing. A marker may not float beside a narrow alien fuselage.
     * Nearest-point search is deterministic and executes only once per faction/class.
     */
    private static List<Shipyard.Slot> projectToHull(List<Shipyard.Slot> requested,List<Point> border) {
        List<Shipyard.Slot> placed=new ArrayList<>();
        for(Shipyard.Slot slot:requested){
            int bestX=0,bestY=0;
            long bestDistance=Long.MAX_VALUE;
            if(available(border,placed,slot.x(),slot.y())){
                bestX=slot.x();bestY=slot.y();bestDistance=0;
            }else{
                // Every candidate uses the same integer lattice on desktop and Android.
                for(int y=-73;y<=73;y+=2)for(int x=-73;x<=73;x+=2){
                    long dx=x-slot.x(),dy=y-slot.y(),distance=dx*dx+dy*dy;
                    if(distance>=bestDistance)continue;
                    if(available(border,placed,x,y)){
                        bestDistance=distance;bestX=x;bestY=y;
                    }
                }
            }
            if(bestDistance==Long.MAX_VALUE)
                throw new IllegalStateException("No geometric room for "+slot.mount()+" #"+slot.index());
            placed.add(new Shipyard.Slot(slot.index(),slot.mount(),bestX,bestY));
        }
        return List.copyOf(placed);
    }

    private static boolean available(List<Point> border,List<Shipyard.Slot> placed,int x,int y) {
        if(!inside(border,x,y))return false;
        for(int i=0;i<border.size();i++){
            Point a=border.get(i), b=border.get((i+1)%border.size());
            double dx=b.x-a.x,dy=b.y-a.y;
            double t=Math.max(0,Math.min(1,((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy)));
            double px=a.x+t*dx,py=a.y+t*dy;
            if((x-px)*(x-px)+(y-py)*(y-py)<9)return false;
        }
        for(Shipyard.Slot other:placed){
            int dx=x-other.x(),dy=y-other.y();
            if(dx*dx+dy*dy<100)return false;
        }
        return true;
    }

    /** Standard even-odd point-in-polygon test, with no rendering dependency. */
    private static boolean inside(List<Point> border,int x,int y){
        boolean found=false;
        for(int i=0,j=border.size()-1;i<border.size();j=i++){
            Point a=border.get(i),b=border.get(j);
            if((a.y>y)!=(b.y>y) &&
               x<(double)(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)found=!found;
        }
        return found;
    }
    public static boolean contains(Battle.Faction faction,Shipyard.Hull hull,int x,int y){
        if(faction==null||hull==null)throw new IllegalArgumentException("Missing faction or hull");
        return inside(outline(faction,hull),x,y);
    }

    /** Pixel-space hit test shared by desktop pointer and Android touch. */
    public static int pick(List<Shipyard.Slot> slots, float x, float y,
                           float centerX, float centerY, float scale, float radius) {
        if(slots==null||scale<=0||radius<=0)throw new IllegalArgumentException("Invalid hardpoint map");
        double best=radius*radius;
        int result=-1;
        for(Shipyard.Slot slot:slots){
            double dx=x-(centerX+slot.x()*scale),dy=y-(centerY+slot.y()*scale);
            double distance=dx*dx+dy*dy;
            if(distance<best){best=distance;result=slot.index();}
        }
        return result;
    }

    /**
     * Schematic hull boundary for placement inspection, not a claim that unreleased
     * illustration assets are already cropped/usable game sprites.
     */
    public static List<Point> outline(Battle.Faction faction, Shipyard.Hull hull) {
        if(faction==null||hull==null)throw new IllegalArgumentException("Missing hull identity");
        int[][] shape = switch(faction) {
            case TERRAN -> new int[][]{{0,77},{16,53},{26,31},{39,-6},{48,-29},{25,-59},
                    {-25,-59},{-48,-29},{-39,-6},{-26,31},{-16,53}};
            case DRAXIAN, VAERITH_PRISM -> new int[][]{{0,77},{14,52},{36,34},{58,-2},{64,-40},
                    {34,-27},{17,-58},{0,-62},{-17,-58},{-34,-27},{-64,-40},{-58,-2},{-36,34},{-14,52}};
            case CARNELIAN, NYX_BIOLITH -> new int[][]{{0,77},{26,52},{49,25},{57,-34},
                    {36,-61},{-36,-61},{-57,-34},{-49,25},{-26,52}};
            case ZIPPLEDROW, UMBRA_FOUNDRY -> new int[][]{{0,86},{13,38},{24,10},{52,-33},{22,-28},
                    {12,-65},{-12,-65},{-22,-28},{-52,-33},{-24,10},{-13,38}};
            case UZIPT, ZELTHAR_LUMINARI -> new int[][]{{0,46},{15,46},{25,77},{43,55},{43,0},
                    {53,-21},{33,-60},{-33,-60},{-53,-21},{-43,0},{-43,55},{-25,77},{-15,46}};
            case MANDRALLY, AUREATE_SYNOD -> new int[][]{{0,78},{28,49},{42,14},{64,-10},{48,-44},
                    {23,-35},{13,-65},{-13,-65},{-23,-35},{-48,-44},{-64,-10},{-42,14},{-28,49}};
        };
        double width = switch(hull.spriteHull()) {
            case INTERCEPTOR -> 0.68;
            case CORVETTE -> 0.79;
            case MISSILE_CORVETTE -> 0.91;
            case FRIGATE -> 0.86;
            case DESTROYER -> 0.88;
            case LIGHT_CRUISER -> 0.93;
            case CRUISER -> 1.0;
            case HEAVY_CRUISER -> 1.08;
            case BATTLECRUISER -> 1.03;
            case CARRIER -> 1.17;
            case DREADNOUGHT -> 1.14;
        };
        double length = switch(hull.spriteHull()) {
            case INTERCEPTOR -> 0.77;
            case CORVETTE -> 0.86;
            case MISSILE_CORVETTE -> 0.94;
            case FRIGATE -> 0.95;
            case DESTROYER -> 1.08;
            case LIGHT_CRUISER -> 0.98;
            case CRUISER -> 1.0;
            case HEAVY_CRUISER -> 1.04;
            case BATTLECRUISER -> 1.16;
            case CARRIER -> 0.97;
            case DREADNOUGHT -> 1.15;
        };
        List<Point> vertices=new ArrayList<>();
        for(int[] xy:shape)vertices.add(new Point((int)Math.round(xy[0]*width),
                                                  (int)Math.round(xy[1]*length)));
        return List.copyOf(vertices);
    }
}
