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
        // Only debuggable APKs accept the automation harness switch.
        boolean debug=(getApplicationInfo().flags &
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        boolean verify=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_200v200",false);
        initialize(new CatastrophicGame(verify), config);
    }
}
