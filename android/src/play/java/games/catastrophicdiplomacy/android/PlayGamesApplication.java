package games.catastrophicdiplomacy.android;

import com.google.android.gms.games.PlayGamesSdk;

/** Google Play-only application entry. Auth starts with PGS v2 during process creation. */
public final class PlayGamesApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        // The published game services APP_ID must be registered in Play Console.
        PlayGamesSdk.initialize(this);
    }
}
