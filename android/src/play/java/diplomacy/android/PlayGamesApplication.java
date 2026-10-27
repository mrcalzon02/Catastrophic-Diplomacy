package diplomacy.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.PlayGamesSdk;

/**
 * Play-only platform authentication. Play Games v2 authenticates automatically;
 * its player ID is NOT a primary in-game account or an authorization token.
 * Cloud save and desktop identity must use an independently verified account.
 */
public final class PlayGamesApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "PlayGamesPlatform";
    private volatile boolean platformAuthenticated;
    private volatile String platformPlayerId;

    @Override public void onCreate() {
        super.onCreate();
        try {
            PlayGamesSdk.initialize(this);
            registerActivityLifecycleCallbacks(this);
        } catch (RuntimeException failure) {
            // A Google-free device must still be able to use its local saves.
            platformAuthenticated = false;
            Log.w(TAG, "Play Games unavailable; local play continues", failure);
        }
    }

    /** This ID may be used for Play Games features, never for save ownership. */
    public String getPlatformPlayerId() { return platformPlayerId; }
    public boolean isPlatformAuthenticated() { return platformAuthenticated; }

    @Override public void onActivityResumed(Activity activity) {
        PlayGames.getGamesSignInClient(activity).isAuthenticated()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful() || task.getResult() == null ||
                        !task.getResult().isAuthenticated()) {
                    platformAuthenticated = false;
                    platformPlayerId = null;
                    return;
                }
                PlayGames.getPlayersClient(activity).getCurrentPlayer()
                    .addOnSuccessListener(player -> {
                        platformPlayerId = player.getPlayerId();
                        platformAuthenticated = platformPlayerId != null &&
                            !platformPlayerId.isEmpty();
                    })
                    .addOnFailureListener(error -> {
                        platformAuthenticated = false;
                        platformPlayerId = null;
                        Log.w(TAG, "Authenticated player metadata unavailable", error);
                    });
            })
            .addOnFailureListener(error -> {
                platformAuthenticated = false;
                platformPlayerId = null;
                Log.w(TAG, "Play Games platform authentication failed", error);
            });
    }

    @Override public void onActivityCreated(Activity a, Bundle state) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
