package diplomacy.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight render-thread-only audio pitch shuffler.
 *
 * Preserves the authored sound file. Each cue receives its own recent-history
 * bag so repeated instances cannot reuse either of their last two pitches.
 * This uses a client-only PRNG: it never consumes authoritative battle RNG,
 * creates another decoded Sound, or changes timing / hit logic.
 */
final class AudioPitchVariants {
    enum Profile { COMBAT, UI, RADIO, THRUSTER }
    private static final float[] COMBAT =
            {.86f,.895f,.93f,.965f,1f,1.035f,1.07f,1.105f,1.14f};
    private static final float[] UI =
            {.95f,.966f,.983f,1f,1.017f,1.034f,1.05f};
    private static final float[] RADIO =
            {.96f,.973f,.986f,1f,1.014f,1.027f,1.04f};
    private static final float[] THRUSTER =
            {.91f,.94f,.97f,1f,1.03f,1.06f,1.09f};
    private static final class History {
        int previous=-1,last=-1;
    }
    private final Map<String,History> recent = new HashMap<>();
    private long state;

    AudioPitchVariants(){this(System.nanoTime() ^ 0x5DEECE66DL);}
    AudioPitchVariants(long seed){
        state=seed==0?0x9E3779B97F4A7C15L:seed;
    }
    private static float[] notes(Profile profile){
        return switch(profile){
            case COMBAT -> COMBAT;
            case UI -> UI;
            case RADIO -> RADIO;
            case THRUSTER -> THRUSTER;
        };
    }
    private long roll(){
        state ^= state >>> 12;
        state ^= state << 25;
        state ^= state >>> 27;
        return state * 2685821657736338717L;
    }

    float next(String key,Profile profile){
        if(key==null||key.isEmpty()||profile==null)
            throw new IllegalArgumentException("Sound identity and profile required");
        float[] values=notes(profile);
        History history=recent.computeIfAbsent(profile.name()+":"+key,k->new History());
        int available=0;
        for(int i=0;i<values.length;i++)
            if(i!=history.last&&i!=history.previous)available++;
        int pick=(int)Long.remainderUnsigned(roll(),available);
        int selected=-1;
        for(int i=0;i<values.length;i++){
            if(i==history.last||i==history.previous)continue;
            if(pick--==0){selected=i;break;}
        }
        if(selected<0)throw new IllegalStateException("Pitch bag exhausted");
        history.previous=history.last;
        history.last=selected;
        return values[selected];
    }

    /** A new battle clears cue history but does not repeat the PRNG sequence. */
    void reset(){recent.clear();}
}
