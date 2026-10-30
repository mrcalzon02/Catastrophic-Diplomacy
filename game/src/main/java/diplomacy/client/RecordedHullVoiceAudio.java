package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.CrewChatter;
import diplomacy.sim.HullVoiceChatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User-recorded hull voices across seven hull roles, including the missile frigate; 182 cues.
 * One clip at a time, with a global cooldown suitable for 200v200 engagements.
 * All routing is cosmetic; no simulation RNG, no synthetic pilot impersonation.
 */
public final class RecordedHullVoiceAudio implements AutoCloseable {
    private static final Map<String, String> SUBTITLE_TO_ASSET = buildIndex();
    private static final long MIN_SPACING_TICKS = 150L; // 5 sec at 30Hz
    private Music active;
    private long nextTick = Long.MIN_VALUE;
    private boolean suspended;
    private boolean resumeVoice;

    private static Map<String,String> buildIndex() {
        Map<String,String> result = new HashMap<>();
        Shipyard.Hull[] recorded = {
                Shipyard.Hull.INTERCEPTOR, Shipyard.Hull.CORVETTE, Shipyard.Hull.DESTROYER,
                Shipyard.Hull.CARRIER, Shipyard.Hull.DREADNOUGHT,
                Shipyard.Hull.BATTLECRUISER, Shipyard.Hull.FRIGATE_MISSILE
        };
        for (Shipyard.Hull hull : recorded) {
            for (CrewChatter.Event event : CrewChatter.Event.values()) {
                for (int i = 0; i < 2; i++) {
                    CrewChatter.Tone register = i == 0 ?
                            CrewChatter.Tone.PROFESSIONAL : CrewChatter.Tone.VETERAN;
                    String subtitle = HullVoiceChatter.line(hull, event, 0L, 0, register);
                    // Explicit class metadata prevents an ordinary frigate with
                    // the same spoken words from being voiced as a missile frigate.
                    if (hull == Shipyard.Hull.FRIGATE_MISSILE)
                        subtitle = "MISSILE_FRIGATE|" + subtitle;
                    String cue = (hull == Shipyard.Hull.FRIGATE_MISSILE ? "missile_frigate" :
                            hull.name().toLowerCase(java.util.Locale.ROOT)) + "_"
                            + event.name().toLowerCase(java.util.Locale.ROOT)
                            + "_0" + (i + 1);
                    String parent = hull == Shipyard.Hull.INTERCEPTOR ?
                            "audio/fighter/" : "audio/hull_voice/";
                    String path = parent + cue + ".ogg";
                    if (result.putIfAbsent(subtitle, path) != null)
                        throw new IllegalStateException(
                                "Non-unique hull voice subtitle: " + subtitle);
                }
            }
        }
        if (result.size() != 182)
            throw new IllegalStateException("Incomplete 7-hull voice bank");
        return Collections.unmodifiableMap(result);
    }

    /** Only the exact recorded dialogue is voiced; other radio remains text + bleep. */
    public static String assetPathForRadio(String eventLine) {
        if (eventLine == null || !eventLine.startsWith("RADIO ")) return null;
        int colon = eventLine.indexOf(": ");
        if (colon < 0) return null;
        String text = eventLine.substring(colon + 2);
        if (eventLine.substring(0, colon).endsWith(" / Missile Frigate"))
            text = "MISSILE_FRIGATE|" + text;
        return SUBTITLE_TO_ASSET.get(text);
    }
    public static int registeredCues() { return SUBTITLE_TO_ASSET.size(); }

    public boolean play(String eventLine, long tick) {
        String path = assetPathForRadio(eventLine);
        if (path == null || suspended || tick < nextTick || Gdx.audio == null || Gdx.files == null)
            return false;
        if (active != null) {
            if (active.isPlaying()) return false;
            closeActive();
        }
        try {
            var file = Gdx.files.internal(path);
            if (!file.exists()) throw new IllegalStateException("Missing recorded hull voice: " + path);
            active = Gdx.audio.newMusic(file);
            active.setLooping(false);
            active.setVolume(0.44f);
            active.play();
            nextTick = tick + MIN_SPACING_TICKS;
            return true;
        } catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("HULL-VOICE", "Playback failed: " + path, error);
            closeActive();
            nextTick = tick + MIN_SPACING_TICKS;
            return false;
        }
    }
    public void advance() {
        if (!suspended && active != null && !active.isPlaying()) closeActive();
    }
    public void stop() { closeActive(); resumeVoice = false; }
    public void reset() { stop(); nextTick = Long.MIN_VALUE; }
    public void pause() {
        suspended = true;
        resumeVoice = active != null && active.isPlaying();
        if (resumeVoice) active.pause();
    }
    public void resume() {
        suspended = false;
        if (resumeVoice && active != null) active.play();
        resumeVoice = false;
    }
    private void closeActive() {
        if (active == null) return;
        try { active.stop(); active.dispose(); }
        catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("HULL-VOICE","Cleanup failed",error);
        }
        finally { active = null; }
    }
    @Override public void close() { stop(); }
}
