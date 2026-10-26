package diplomacy.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class AndroidLauncher extends AndroidApplication {
    private SideloadUpdater updater;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        updater=new SideloadUpdater(this);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useGL30 = true;
        config.numSamples = 0;
        config.useImmersiveMode = true;
        boolean debug=(getApplicationInfo().flags &
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        boolean verify=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_200v200",false);
        initialize(new CatastrophicGame(updater::checkForUpdates,verify), config);
        updater.checkForUpdates(false); // non-blocking; prereleases are eligible
    }
    @Override protected void onDestroy(){
        if(updater!=null)updater.close();
        super.onDestroy();
    }
}
