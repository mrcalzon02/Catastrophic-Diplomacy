package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.List;

/**
 * The core retains a bounded chronological visual-event history.
 * Rendering runs many times for each slow combat tick: binary-search the
 * monotonically increasing sequence rather than rescanning all old events
 * on every single graphics frame.
 */
final class CombatEventCursor {
    private CombatEventCursor(){}

    /** A presentation frame with no new tick or event cannot advance the
     * clip age or active one-shot collection. Constant-time detection saves
     * active-list traversal on all those otherwise identical frames. */
    static boolean needsRefresh(long renderedTick,long lastSeen,
                                long authoritativeTick,
                                List<Battle.VisualEvent> history){
        if(history==null)throw new IllegalArgumentException("Event history required");
        if(renderedTick!=authoritativeTick)return true;
        return !history.isEmpty()&&
                history.get(history.size()-1).sequence()>lastSeen;
    }

    /** Index of the earliest event with sequence strictly above lastSeen.
     * Handles empty lists and a ring trimmed from its beginning. */
    static int firstUnseen(List<Battle.VisualEvent> history,long lastSeen){
        if(history==null)throw new IllegalArgumentException("Event history required");
        int low=0,high=history.size();
        while(low<high){
            int mid=low+(high-low)/2;
            if(history.get(mid).sequence()<=lastSeen)low=mid+1;
            else high=mid;
        }
        return low;
    }
}
