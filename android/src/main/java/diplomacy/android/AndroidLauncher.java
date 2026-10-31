package diplomacy.android;

import android.os.Bundle;
import android.app.ActivityManager;
import android.content.Context;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class AndroidLauncher extends AndroidApplication {
    private SideloadUpdater updater;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        updater=new SideloadUpdater(this);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // Fire tablets and older Android GPUs may provide GLES 2 only. Keep
        // the existing GL3 instanced renderer when available, otherwise let
        // CatastrophicGame use its already-implemented GLES2 hull/FX fallback.
        ActivityManager manager=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        boolean gl3=manager!=null&&manager.getDeviceConfigurationInfo()!=null
                &&manager.getDeviceConfigurationInfo().reqGlEsVersion>=0x30000;
        config.useGL30 = gl3;
        config.numSamples = 0;
        config.useImmersiveMode = true;
        boolean debug=(getApplicationInfo().flags &
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        boolean verify=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_200v200",false);
        boolean review=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_surface_damage",false);
        initialize(new CatastrophicGame(updater::checkForUpdates,verify,review), config);
        if(!verify && !review)updater.checkForUpdates(false); // prereleases eligible
    }
    @Override protected void onDestroy(){
        if(updater!=null)updater.close();
        super.onDestroy();
    }
}
