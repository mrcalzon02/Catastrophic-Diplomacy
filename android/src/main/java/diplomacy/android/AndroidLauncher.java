package diplomacy.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class AndroidLauncher extends AndroidApplication {
    private SideloadUpdater updater;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        updater = new SideloadUpdater(this);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = true;
        config.numSamples = 0;
        config.useImmersiveMode = true;
        initialize(new CatastrophicGame(updater::checkForUpdates), config);
        // Silent background check: failures do not interrupt gameplay or disable manual checks.
        updater.checkForUpdates(false);
    }

    @Override protected void onDestroy() {
        if (updater != null) updater.close();
        super.onDestroy();
    }
}