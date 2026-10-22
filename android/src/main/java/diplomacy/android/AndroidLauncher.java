package diplomacy.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class AndroidLauncher extends AndroidApplication {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = true;
        config.numSamples = 0;
        config.useImmersiveMode = true;
        initialize(new CatastrophicGame(), config);
    }
}
