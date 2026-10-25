package diplomacy.client;

import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Deterministic filled tactical silhouettes from the same faction/hull outlines
 * used in Shipyard. Visual-only: no physics or hardpoint coordinates are changed.
 * Hull-space forward is +Y; renderers rotate by ship.facing - PI/2.
 */
public final class HullSilhouette {
    private static final float UNIT = 0.20f;
    private static final float EPS = 0.000001f;
    private static final EnumMap<Battle.Faction, EnumMap<Shipyard.Hull,float[]>> CACHE = build();
    private HullSilhouette() { }

    private static EnumMap<Battle.Faction, EnumMap<Shipyard.Hull,float[]>> build() {
        EnumMap<Battle.Faction, EnumMap<Shipyard.Hull,float[]>> all =
                new EnumMap<>(Battle.Faction.class);
        for (Battle.Faction faction : Battle.Faction.values()) {
            EnumMap<Shipyard.Hull,float[]> shapes = new EnumMap<>(Shipyard.Hull.class);
            for (Shipyard.Hull hull : Shipyard.Hull.values())
                shapes.put(hull, triangulate(HullLayouts.outline(faction, hull)));
            all.put(faction, shapes);
        }
        return all;
    }

    public static float[] of(Battle.Faction faction, Shipyard.Hull hull) {
        if (faction == null) throw new IllegalArgumentException("Faction required");
        return CACHE.get(faction).get(hull == null ? Shipyard.Hull.CORVETTE : hull);
    }

    /** Ear clipping retains concave native faction profiles without fill overhangs. */
    static float[] triangulate(List<HullLayouts.Point> source) {
        if (source == null || source.size() < 3)
            throw new IllegalArgumentException("Invalid hull outline");
        List<HullLayouts.Point> polygon = new ArrayList<>(source);
        float area = signedArea(polygon);
        if (Math.abs(area) <= EPS) throw new IllegalArgumentException("Degenerate hull outline");
        boolean ccw = area > 0;
        float[] out = new float[(polygon.size() - 2) * 6];
        int used = 0;
        while (polygon.size() > 3) {
            boolean found = false;
            int n = polygon.size();
            for (int i = 0; i < n; i++) {
                HullLayouts.Point a = polygon.get((i+n-1)%n);
                HullLayouts.Point b = polygon.get(i);
                HullLayouts.Point c = polygon.get((i+1)%n);
                float cross = cross(a,b,c);
                if (ccw ? cross <= EPS : cross >= -EPS) continue;
                boolean blocked = false;
                for (int j = 0; j < n; j++) {
                    if (j == i || j == (i+n-1)%n || j == (i+1)%n) continue;
                    if (insideOrOnTriangle(polygon.get(j),a,b,c,ccw)) { blocked=true; break; }
                }
                if (blocked) continue;
                used=add(out,used,a,b,c);
                polygon.remove(i);
                found=true;
                break;
            }
            if (!found) throw new IllegalStateException("Hull outline is not a simple polygon");
        }
        used=add(out,used,polygon.get(0),polygon.get(1),polygon.get(2));
        if (used != out.length) throw new IllegalStateException("Incomplete triangulation");
        return out;
    }

    private static float signedArea(List<HullLayouts.Point> polygon) {
        float twice=0;
        for(int i=0;i<polygon.size();i++) {
            HullLayouts.Point a=polygon.get(i), b=polygon.get((i+1)%polygon.size());
            twice+=a.x()*b.y()-a.y()*b.x();
        }
        return twice*0.5f;
    }
    private static float cross(HullLayouts.Point a,HullLayouts.Point b,HullLayouts.Point c) {
        return (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
    }
    private static boolean insideOrOnTriangle(HullLayouts.Point p,HullLayouts.Point a,
                                               HullLayouts.Point b,HullLayouts.Point c,boolean ccw) {
        float d1=cross(a,b,p),d2=cross(b,c,p),d3=cross(c,a,p);
        return ccw ? d1>=-EPS && d2>=-EPS && d3>=-EPS
                   : d1<=EPS && d2<=EPS && d3<=EPS;
    }
    private static int add(float[] dest,int pos,HullLayouts.Point a,
                           HullLayouts.Point b,HullLayouts.Point c) {
        dest[pos++]=a.x()*UNIT;dest[pos++]=a.y()*UNIT;
        dest[pos++]=b.x()*UNIT;dest[pos++]=b.y()*UNIT;
        dest[pos++]=c.x()*UNIT;dest[pos++]=c.y()*UNIT;
        return pos;
    }
}
