package diplomacy.sim;

import java.util.Map;

/**
 * Select the most recent physical TRK observations for a finite LNK socket.
 * The return order is EXACTLY the original full sort: observation tick
 * descending, numerical ship ID ascending on ties.
 *
 * Scratch is owned by one Battle and reused sequentially across its ships.
 * No per-tick lists, boxed comparator keys, or persistent unbounded cache.
 */
final class TrackPacketTopK {
    private TrackPacketTopK(){}

    static int select(Map<Integer,Battle.ContactTrack> known,long now,
            int window,int capacity,int[] ids,Battle.ContactTrack[] tracks){
        if(known==null||window<0||capacity<0||
                capacity>SensorTrackRelayBuffer.MAX_CONTACTS||
                ids==null||tracks==null||
                capacity>ids.length||capacity>tracks.length)
            throw new IllegalArgumentException("Invalid bounded LNK selection");
        if(capacity==0)return 0;
        int count=0;
        for(Map.Entry<Integer,Battle.ContactTrack> entry:known.entrySet()){
            Battle.ContactTrack track=entry.getValue();
            long age=now-track.tick;
            if(age<0||age>window)continue;
            int id=entry.getKey();
            int index=0;
            while(index<count){
                Battle.ContactTrack previous=tracks[index];
                // The original List.sort compares (tick DESC, id ASC).
                if(track.tick>previous.tick||
                        track.tick==previous.tick&&id<ids[index])break;
                index++;
            }
            if(index>=capacity)continue;
            if(count<capacity)count++;
            for(int j=count-1;j>index;j--){
                ids[j]=ids[j-1];
                tracks[j]=tracks[j-1];
            }
            ids[index]=id;
            tracks[index]=track;
        }
        return count;
    }
}
