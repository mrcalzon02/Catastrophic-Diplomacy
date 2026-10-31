package diplomacy.client;

import java.util.HashSet;
import java.util.Set;

/** Portable pure-Java behavioral checks; no sound card or libGDX runtime. */
public final class AudioPitchVariantsTest {
    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static void exercise(AudioPitchVariants.Profile profile,float lo,float hi){
        AudioPitchVariants variants=new AudioPitchVariants(0x726164696F4C6F6FL);
        Set<Float> distinct=new HashSet<>();
        float previous=Float.NaN,twoBack=Float.NaN;
        for(int i=0;i<900;i++){
            float value=variants.next("same-weapon",profile);
            require(Float.isFinite(value)&&value>=lo&&value<=hi,
                    "Unexpected sound pitch for "+profile+": "+value);
            require(value!=previous&&value!=twoBack,
                    "Rapid repeated playback must never use either last two pitch variants");
            distinct.add(value);twoBack=previous;previous=value;
        }
        require(distinct.size()>=6,"Pitch randomizer collapsed to an inaudible pattern "+profile);
        // Independent cue history: its first use must not be limited by this cue.
        float independent=variants.next("other-weapon",profile);
        require(independent>=lo&&independent<=hi,"Independent sound identity");
        variants.reset();
        float restarted=variants.next("same-weapon",profile);
        require(restarted>=lo&&restarted<=hi,"Battle reset must retain a usable pitch bag");
    }
    public static void main(String[] args){
        exercise(AudioPitchVariants.Profile.COMBAT,.86f,1.14f);
        exercise(AudioPitchVariants.Profile.UI,.95f,1.05f);
        exercise(AudioPitchVariants.Profile.RADIO,.96f,1.04f);
        exercise(AudioPitchVariants.Profile.THRUSTER,.91f,1.09f);
        AudioPitchVariants a=new AudioPitchVariants(321L);
        AudioPitchVariants b=new AudioPitchVariants(321L);
        for(int i=0;i<150;i++)
            require(a.next("kinetic",AudioPitchVariants.Profile.COMBAT)==
                    b.next("kinetic",AudioPitchVariants.Profile.COMBAT),
                    "Seeded pitch sequence should be reproducible in diagnostics");
        boolean rejected=false;
        try{a.next("",AudioPitchVariants.Profile.UI);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Cannot vary an unnamed cue");
        System.out.println("PASS pitch variety, no last-two repeats, seeded replay, independent cues, profile bounds");
    }
}
