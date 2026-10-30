package diplomacy.client;

import diplomacy.sim.CrewChatter;
import diplomacy.sim.HullVoiceChatter;
import diplomacy.shipyard.Shipyard;
import java.util.HashSet;
import java.util.Set;

/** No audio hardware required: verify every recorded pilot line resolves to a runtime clip. */
public final class FighterVoiceAudioTest {
    public static void main(String[] args) {
        if (FighterVoiceAudio.registeredCues() != 26)
            throw new AssertionError("Expected 26 recorded interceptor cues");
        Set<String> paths = new HashSet<>();
        int n=0;
        for (CrewChatter.Event event : CrewChatter.Event.values()) {
            for (CrewChatter.Tone tone : new CrewChatter.Tone[]{
                    CrewChatter.Tone.PROFESSIONAL, CrewChatter.Tone.VETERAN}) {
                String words = HullVoiceChatter.line(
                        Shipyard.Hull.INTERCEPTOR,event,1L,42,tone);
                String path = FighterVoiceAudio.assetPathForRadio(
                        "RADIO Fighter One / Flight: "+words);
                String expected = "audio/fighter/interceptor_"
                        +event.name().toLowerCase(java.util.Locale.ROOT)
                        +(tone==CrewChatter.Tone.PROFESSIONAL?"_01.ogg":"_02.ogg");
                if (!expected.equals(path) || !paths.add(path))
                    throw new AssertionError("Missing, duplicate or wrong recorded cue: "+expected+" got "+path);
                n++;
            }
        }
        if (FighterVoiceAudio.assetPathForRadio(
                    "RADIO Big Cruiser: All batteries, engage.") != null)
            throw new AssertionError("Must not voice unrecorded ship radio");
        if(n!=26 || paths.size()!=26)throw new AssertionError("Incomplete recorded bank");
        System.out.println("PASS: all 26 original fighter voice clips mapped to recorded subtitles");
    }
}
