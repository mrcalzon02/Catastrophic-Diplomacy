package diplomacy.client;

import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;

/** Java-only polygon acceptance: proves that every real faction hull is filled exactly. */
public final class HullSilhouetteTest {
    public static void main(String[] args) {
        int checked=0;
        for(Battle.Faction faction:Battle.Faction.values()) {
            for(Shipyard.Hull hull:Shipyard.Hull.values()) {
                var outline=HullLayouts.outline(faction,hull);
                float[] triangles=HullSilhouette.of(faction,hull);
                if(triangles.length!=(outline.size()-2)*6)
                    throw new AssertionError("Wrong triangle count: "+faction+"/"+hull);
                double drawn=0;
                for(int i=0;i<triangles.length;i+=6) {
                    double ax=triangles[i], ay=triangles[i+1];
                    double bx=triangles[i+2],by=triangles[i+3];
                    double cx=triangles[i+4],cy=triangles[i+5];
                    drawn+=Math.abs((bx-ax)*(cy-ay)-(by-ay)*(cx-ax))/2;
                }
                double expected=0;
                for(int i=0;i<outline.size();i++) {
                    var a=outline.get(i);var b=outline.get((i+1)%outline.size());
                    expected+=a.x()*b.y()-a.y()*b.x();
                }
                expected=Math.abs(expected)*0.5*0.20*0.20;
                if(Math.abs(expected-drawn)>0.01)
                    throw new AssertionError("Overfill/holes: "+faction+"/"+hull+
                        " area "+drawn+" versus "+expected);
                checked++;
            }
        }
        if(HullSilhouette.of(Battle.Faction.TERRAN,null).length==0)
            throw new AssertionError("No legacy unfitted ship fallback");
        System.out.println("PASS "+checked+" complete faction-specific class silhouettes");
    }
}
