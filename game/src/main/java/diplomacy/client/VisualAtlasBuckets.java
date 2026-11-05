package diplomacy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Reusable per-atlas shot lists, rebuilt only when shot membership changes. */
final class VisualAtlasBuckets<T> {
    private final List<T>[] groups;
    @SuppressWarnings("unchecked")
    VisualAtlasBuckets(int atlasCount){
        if(atlasCount<1||atlasCount>256)
            throw new IllegalArgumentException("Invalid atlas count");
        groups=(List<T>[])new List<?>[atlasCount];
        for(int i=0;i<groups.length;i++)groups[i]=new ArrayList<>();
    }
    void rebuild(List<T> items,ToIntFunction<T> atlasOf){
        if(items==null||atlasOf==null)throw new IllegalArgumentException("Missing bucket input");
        for(T item:items){
            int index=atlasOf.applyAsInt(item);
            if(index<0||index>=groups.length)
                throw new IllegalArgumentException("Out of range atlas "+index);
        }
        clear();
        for(T item:items)groups[atlasOf.applyAsInt(item)].add(item);
    }
    List<T> atlas(int index){return groups[index];}
    void clear(){for(List<T> group:groups)group.clear();}
}
