package diplomacy.client;

import java.util.HashSet;
import java.util.Set;

/** CLI smoke test for the game-wide voice catalog; no graphics/audio device. */
public final class AnnouncerAudioTest {
    public static void main(String[] args) {
        AnnouncerAudio.Cue[] cues = AnnouncerAudio.Cue.values();
        if (cues.length != 30) throw new AssertionError("Expected 30 original announcer cues");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < cues.length; i++) {
            String path = cues[i].assetPath();
            String prefix = String.format("audio/announcer/%02d_", i + 1);
            if (!path.startsWith(prefix) || !path.endsWith(".ogg") || !names.add(path))
                throw new AssertionError("Invalid cue path: " + path);
        }
        System.out.println("PASS: 30 unique ordered announcer voice cue paths");
    }
}
