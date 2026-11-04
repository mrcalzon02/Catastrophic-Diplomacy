package diplomacy.android;

import android.os.Bundle;
import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class AndroidLauncher extends AndroidApplication {
    private SideloadUpdater updater;
    private static final String LIFECYCLE_TAG="CD-ANDROID-LIFECYCLE";
    @Override public void onCreate(Bundle state) {
        Log.i(LIFECYCLE_TAG,"ON_CREATE_BEGIN");
        super.onCreate(state);
        Log.i(LIFECYCLE_TAG,"ON_CREATE_AFTER_SUPER");
        updater=new SideloadUpdater(this);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // Fire tablets and older Android GPUs may provide GLES 2 only. Keep
        // the existing GL3 instanced renderer when available, otherwise let
        // CatastrophicGame use its already-implemented GLES2 hull/FX fallback.
        ActivityManager manager=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        // The Android 10 campaign regression uses a 16 MiB default heap.
        // Log the actual manifest-selected limit to distinguish GPU stalls
        // from Java resource-decoder OOMs on older phones and emulators.
        Log.i(LIFECYCLE_TAG,"HEAP_CLASS normalMiB="+
                (manager==null?-1:manager.getMemoryClass())+
                " largeMiB="+(manager==null?-1:manager.getLargeMemoryClass())+
                " runtimeMaxMiB="+(Runtime.getRuntime().maxMemory()/(1024*1024)));
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
        // Emulator-only functional UI test; this flag cannot activate in
        // a signed production APK even if passed through an external Intent.
        boolean updateDialogReview=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_update_dialog",false);
        Log.i(LIFECYCLE_TAG,"BEFORE_GDX_INITIALIZE gl3="+gl3);
        initialize(new CatastrophicGame(updater::checkForUpdates,verify,review), config);
        Log.i(LIFECYCLE_TAG,"AFTER_GDX_INITIALIZE");
        if(updateDialogReview)updater.showVerificationDialog();
        else if(!verify && !review)updater.checkForUpdates(false); // prereleases eligible
    }
    @Override protected void onDestroy(){
        Log.i(LIFECYCLE_TAG,"ON_DESTROY");
        if(updater!=null)updater.close();
        super.onDestroy();
    }
}
