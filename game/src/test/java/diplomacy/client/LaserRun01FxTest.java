package diplomacy.client;

import diplomacy.sim.LaserFamilies;

/** Headless event-to-original-atlas routing checks; no GL context needed. */
public final class LaserRun01FxTest {
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        java.util.Set<Integer> selected=new java.util.HashSet<>();
        for(LaserFamilies.Type type:LaserFamilies.Type.values()){
            int even=LaserRun01Fx.effectIndex(type,2);
            int odd=LaserRun01Fx.effectIndex(type,3);
            require(even!=odd,"Every simulated laser family must access two distinct original animations");
            selected.add(even);selected.add(odd);
            require(LaserRun01Fx.frameAt(even,0)==0,"New shot starts on frame zero");
            require(LaserRun01Fx.frameAt(odd,LaserRun01Fx.FRAMES[odd]-1)>=0,
                    "Last authored frame must be reachable");
            require(LaserRun01Fx.frameAt(odd,LaserRun01Fx.FRAMES[odd])<0,
                    "One-shot may never loop after its last frame");
        }
        require(selected.size()==10,"All ten authored laser atlases must be reachable");
        for(int n:selected)require(LaserRun01Fx.path(n).startsWith("effects/lasers/run01/"),
                "Laser art paths must be relative to runtime assets");
        System.out.println("PASS: all 10 laser animations selected by real shot events");
    }
}
