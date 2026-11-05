package diplomacy.client;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class BattleMusicPlaylistTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int count : new int[]{1, 2, 3, 6}) {
            BattleMusicPlaylist playlist = new BattleMusicPlaylist(count, new Random(67231));
            int prior = -1;
            for (int cycle = 0; cycle < 100; cycle++) {
                Set<Integer> batch = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    int index = playlist.nextTrack();
                    check(index >= 0 && index < count, "out of range");
                    check(count == 1 || index != prior, "consecutive duplicate");
                    check(batch.add(index), "repeated before full rotation");
                    prior = index;
                }
                check(batch.size() == count, "missing a song from rotation");
            }
        }
        try {
            new BattleMusicPlaylist(0);
            throw new AssertionError("empty playlist accepted");
        } catch (IllegalArgumentException expected) {
            // No battle music should be handled by the caller as silence/ambience.
        }
        System.out.println("PASS battle playlist shuffle-bag, exhaustion, and boundary repeat checks");
    }
}
