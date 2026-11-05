package diplomacy.client;

import java.util.Random;

/** Shuffle-bag sequence for complete combat tracks; no immediate repeats. */
final class BattleMusicPlaylist {
    private final int[] bag;
    private final Random random;
    private int next;
    private int previous = -1;

    BattleMusicPlaylist(int trackCount) {
        this(trackCount, new Random());
    }

    BattleMusicPlaylist(int trackCount, Random random) {
        if (trackCount < 1) throw new IllegalArgumentException("No battle music tracks");
        if (random == null) throw new IllegalArgumentException("Missing music random source");
        bag = new int[trackCount];
        this.random = random;
        next = trackCount;
    }

    int nextTrack() {
        if (next == bag.length) refill();
        previous = bag[next++];
        return previous;
    }

    private void refill() {
        for (int i = 0; i < bag.length; i++) bag[i] = i;
        for (int i = bag.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = bag[i];
            bag[i] = bag[j];
            bag[j] = swap;
        }
        if (bag.length > 1 && bag[0] == previous) {
            int swap = bag[0];
            bag[0] = bag[1];
            bag[1] = swap;
        }
        next = 0;
    }
}
