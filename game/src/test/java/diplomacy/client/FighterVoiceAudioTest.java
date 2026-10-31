package diplomacy.client;

import diplomacy.sim.CrewChatter;
import diplomacy.sim.HullVoiceChatter;
import diplomacy.shipyard.Shipyard;
import java.util.HashSet;
import java.util.Set;

/** Headless test: ten recorded hull roles with explicit missile-frigate distinction. */
public final class FighterVoiceAudioTest {
    public static void main(String[] args) {
        if (RecordedHullVoiceAudio.registeredCues() != 260)
            throw new AssertionError("Expected 260 recorded hull voice cues");
        Set<String> paths = new HashSet<>();
        int n = 0;
        for (Shipyard.Hull hull : new Shipyard.Hull[]{
                Shipyard.Hull.INTERCEPTOR, Shipyard.Hull.CORVETTE, Shipyard.Hull.DESTROYER,
                Shipyard.Hull.CARRIER, Shipyard.Hull.DREADNOUGHT,
                Shipyard.Hull.BATTLECRUISER, Shipyard.Hull.FRIGATE_MISSILE,
                Shipyard.Hull.CRUISER, Shipyard.Hull.LIGHT_CRUISER,
                Shipyard.Hull.HEAVY_CRUISER}) {
            for (CrewChatter.Event event : CrewChatter.Event.values()) {
                for (CrewChatter.Tone tone : new CrewChatter.Tone[]{
                        CrewChatter.Tone.PROFESSIONAL, CrewChatter.Tone.VETERAN}) {
                    String words = HullVoiceChatter.line(hull,event,1L,42,tone);
                    String actual = RecordedHullVoiceAudio.assetPathForRadio(
                            "RADIO Active Vessel"+(hull==Shipyard.Hull.FRIGATE_MISSILE?
                                " / Missile Frigate":"")+": "+words);
                    String base = (hull==Shipyard.Hull.FRIGATE_MISSILE?
                            "missile_frigate":hull.name().toLowerCase(java.util.Locale.ROOT))
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
        // The normal frigate and missile corvette must never silently inherit
        // the distinct Missile Frigate recording by subtitle collision.
        String sameLine = HullVoiceChatter.line(Shipyard.Hull.FRIGATE,
                CrewChatter.Event.ATTACK,0,0,CrewChatter.Tone.PROFESSIONAL);
        if (RecordedHullVoiceAudio.assetPathForRadio("RADIO Ordinary Frigate: "+sameLine) != null)
            throw new AssertionError("Missile frigate voice leaked onto ordinary frigate");
        if(n != 260 || paths.size() != 260)
            throw new AssertionError("Incomplete ten-hull voice bank");
        System.out.println("PASS: 260 unique original recorded cues across 10 fixed hull voices");
    }
}
