package diplomacy.android;

import android.os.Bundle;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
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
        // updaterTest-only fixtures. A production APK cannot enter either path
        // because FLAG_DEBUGGABLE is absent even if an external Intent spoofs extras.
        boolean seedUpdateFixture=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.seed_update_fixture",false);
        boolean realUpdateReview=debug && getIntent()!=null &&
                getIntent().getBooleanExtra("cd.verify_real_update",false);
        if(seedUpdateFixture){
            try{
                UpdateUpgradeFixture.seed(this);
                Log.i("CD-UPDATER-E2E","SEEDED");
            }catch(Exception failure){
                Log.e("CD-UPDATER-E2E","Fixture seed failed",failure);
                throw new IllegalStateException("Updater fixture seed failed",failure);
            }
        }
        Log.i(LIFECYCLE_TAG,"BEFORE_GDX_INITIALIZE gl3="+gl3);
        initialize(new CatastrophicGame(updater::checkForUpdates,verify,review,
                this::sharePerformanceCapture), config);
        Log.i(LIFECYCLE_TAG,"AFTER_GDX_INITIALIZE");
        if(updateDialogReview)updater.showVerificationDialog();
        else if(realUpdateReview){
            Log.i("CD-UPDATER-E2E","PRODUCTION_CHECK");
            updater.checkForUpdates(true);
        }else if(!verify && !review && !seedUpdateFixture)
            updater.checkForUpdates(false); // prereleases eligible
    }
    /** Share the verified app-private ZIP using the existing read-grant FileProvider. */
    private void sharePerformanceCapture(String relativePath){
        if(relativePath==null||!relativePath.startsWith("diagnostics/")||
                !relativePath.endsWith(".zip"))return;
        runOnUiThread(()->{
            try{
                File file=new File(getFilesDir(),relativePath);
                if(!file.isFile())throw new java.io.IOException("Performance archive missing");
                Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",file);
                Intent share=new Intent(Intent.ACTION_SEND);
                share.setType("application/zip");
                share.putExtra(Intent.EXTRA_STREAM,uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share,"Share performance capture"));
            }catch(Exception failure){
                Log.e("CD-PERF-EXPORT","Cannot share capture",failure);
                android.widget.Toast.makeText(this,"Performance export could not be shared",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override protected void onDestroy(){
        Log.i(LIFECYCLE_TAG,"ON_DESTROY");
        if(updater!=null)updater.close();
        super.onDestroy();
    }
}
