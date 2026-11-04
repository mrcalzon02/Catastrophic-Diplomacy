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
        require(CombatTimeAudio.combatPitch(1f,1f)==1f,
                "Authored SFX playback must stay unchanged at 1x");
        require(CombatTimeAudio.combatPitch(.5f,1f)<
                CombatTimeAudio.combatPitch(1f,1f) &&
                CombatTimeAudio.combatPitch(3f,1f)>
                CombatTimeAudio.combatPitch(2f,1f),
                "Relative combat audio pitch must respond monotonically to speed");
        require(CombatTimeAudio.combatPitch(3f,1f)<1.30f,
                "Maximum combat audio speed must retain reasonable timbre");
        require(Math.abs(CombatTimeAudio.enginePitch(1f,.93f)-.93f)<1e-5f,
                "Engine loops must preserve authored tuning at 1x");

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
        // Actual sound instance identity is more precise than logical cue text.
        // Both PLASMA and TORPEDO can be routed to the same heavy_fire Sound.
        AudioPitchVariants played=new AudioPitchVariants(549L);
        Object sharedHeavyFire=new Object();
        float previous=Float.NaN,twoBack=Float.NaN;
        Set<Float> sameSampleVariants=new HashSet<>();
        for(int i=0;i<700;i++){
            float rate=played.nextSample(sharedHeavyFire,AudioPitchVariants.Profile.COMBAT);
            require(rate!=previous&&rate!=twoBack,
                    "One decoded sample shared by plasma and torpedoes must never repeat last two pitches");
            sameSampleVariants.add(rate);
            twoBack=previous;previous=rate;
        }
        require(sameSampleVariants.size()>=6,"Shared physical sample must vary in pitch");
        // Even equal() objects can represent different decoded Sound allocations.
        Object sampleA=new String("shared");
        Object sampleB=new String("shared");
        require(sampleA!=sampleB&&sampleA.equals(sampleB),"Test setup requires distinct identities");
        float aFirst=played.nextSample(sampleA,AudioPitchVariants.Profile.UI);
        float bFirst=played.nextSample(sampleB,AudioPitchVariants.Profile.UI);
        require(aFirst>=.95f&&bFirst>=.95f,"Distinct loaded samples receive independent histories");
        played.reset();
        require(played.nextSample(sharedHeavyFire,AudioPitchVariants.Profile.COMBAT)>=.86f,
                "Reset clears sample history without invalidating the decoded source");
        // Engine loops are continuously audible. Give each hull a fixed,
        // subtle detune instead of picking a new pitch every rendered frame.
        Set<Float> loopPitches=new HashSet<>();
        float base=.8f+.12f;
        for(int id=1;id<=400;id++){
            float pitch=AudioPitchVariants.engineLoopPitch(id,1.0);
            require(Math.abs(pitch-base)<=.02801f,
                    "Steady vessel loop must stay within +/-2.8% of its class pitch");
            require(pitch==AudioPitchVariants.engineLoopPitch(id,1.0),
                    "Existing engines must never wobble each frame");
            loopPitches.add(pitch);
        }
        require(loopPitches.size()>=12,"Nearby ships must not all phase-lock at one engine pitch");
        boolean rejected=false;
        try{a.next("",AudioPitchVariants.Profile.UI);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Cannot vary an unnamed cue");
        rejected=false;
        try{played.nextSample(null,AudioPitchVariants.Profile.COMBAT);}
        catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Null loaded sound must not consume pitch variants");
        System.out.println("PASS pitch variety, no last-two repeats, seeded replay, independent cues, profile bounds");
    }
}
