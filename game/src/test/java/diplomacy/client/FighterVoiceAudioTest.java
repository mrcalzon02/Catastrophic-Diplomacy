package diplomacy.client;

import diplomacy.sim.CrewChatter;
import diplomacy.sim.HullVoiceChatter;
import diplomacy.shipyard.Shipyard;
import java.util.HashSet;
import java.util.Set;

/** Headless test: five recorded banks, exact event/line mapping, no device. */
public final class FighterVoiceAudioTest {
    public static void main(String[] args) {
        if (RecordedHullVoiceAudio.registeredCues() != 130)
            throw new AssertionError("Expected 130 recorded hull voice cues");
        Set<String> paths = new HashSet<>();
        int n = 0;
        for (Shipyard.Hull hull : new Shipyard.Hull[]{
                Shipyard.Hull.INTERCEPTOR, Shipyard.Hull.CORVETTE, Shipyard.Hull.DESTROYER,
                Shipyard.Hull.CARRIER, Shipyard.Hull.DREADNOUGHT}) {
            for (CrewChatter.Event event : CrewChatter.Event.values()) {
                for (CrewChatter.Tone tone : new CrewChatter.Tone[]{
                        CrewChatter.Tone.PROFESSIONAL, CrewChatter.Tone.VETERAN}) {
                    String words = HullVoiceChatter.line(hull,event,1L,42,tone);
                    String actual = RecordedHullVoiceAudio.assetPathForRadio(
                            "RADIO Active Vessel: "+words);
                    String base = hull.name().toLowerCase(java.util.Locale.ROOT)
                            +"_"+event.name().toLowerCase(java.util.Locale.ROOT)
                            +(tone==CrewChatter.Tone.PROFESSIONAL?"_01.ogg":"_02.ogg");
                    String expected = (hull==Shipyard.Hull.INTERCEPTOR?
                            "audio/fighter/":"audio/hull_voice/")+base;
                    if (!expected.equals(actual) || !paths.add(actual))
                        throw new AssertionError("Unmatched or duplicate radio cue: "+expected+" got "+actual);
                    n++;
                }
            }
        }
        if (RecordedHullVoiceAudio.assetPathForRadio("RADIO Cruiser: Open fire.") != null)
            throw new AssertionError("Unrecorded ship chatter must remain text only");
        if(n != 130 || paths.size() != 130)
            throw new AssertionError("Incomplete five-hull voice bank");
        System.out.println("PASS: 130 unique original recorded lines assigned to 5 fixed hull voices");
    }
}
