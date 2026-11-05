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
        VisualAtlasBuckets<Integer> buckets=new VisualAtlasBuckets<>(10);
        java.util.List<Integer> workload=new java.util.ArrayList<>();
        for(int i=0;i<384;i++)workload.add(i%10);
        buckets.rebuild(workload,Integer::intValue);
        int total=0;
        for(int atlas=0;atlas<10;atlas++){
            int size=buckets.atlas(atlas).size();
            require(size>=38&&size<=39,"Unbalanced active laser atlas group");
            for(int index:buckets.atlas(atlas))
                require(index==atlas,"Shot mapped to wrong texture atlas");
            total+=size;
        }
        require(total==384,"Shot atlas partition dropped entries");
        buckets.rebuild(java.util.List.of(2,2,7),Integer::intValue);
        require(buckets.atlas(2).size()==2&&buckets.atlas(7).size()==1&&
                buckets.atlas(0).isEmpty(),"Stale laser shots remained after regroup");
        buckets.clear();
        for(int i=0;i<10;i++)require(buckets.atlas(i).isEmpty(),
                "Laser atlas bucket failed cleanup");
        System.out.println("PASS all 10 authored laser sequences and reusable 384-shot atlas grouping");
    }
}
