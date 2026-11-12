package diplomacy.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import diplomacy.client.CatastrophicGame;

public final class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Catastrophic Diplomacy 0.1 — An Unreasonable Space Warfare Simulator");
        cfg.setWindowedMode(1280, 720);
        cfg.setResizable(true);
        cfg.useVsync(true);
        cfg.setForegroundFPS(60);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        // Correct GitHub repo, all published releases including prereleases.
        // Both the game menu UPDATES action and an unobtrusive startup check
        // use the very same downloader; desktop platform selects MSI or ZIP.
        String installedTag;
        try (java.io.InputStream stream=DesktopLauncher.class.getClassLoader()
                .getResourceAsStream("cd-update-tag.txt")) {
            installedTag=stream==null?"":new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to read packaged GitHub release identity",failure);
        }
        DesktopReleaseUpdater updater=new DesktopReleaseUpdater(installedTag,
                System.getProperty("os.name",""));
        // CI-only real-frame capture uses the same client and approved ship art.
        String captureDirectory=System.getenv("CD_ARMOR_CAPTURE_DIR");
        boolean captureArmor=captureDirectory!=null&&!captureDirectory.isBlank();
        String projectileDirectory=System.getenv("CD_PROJECTILE_CAPTURE_DIR");
        boolean captureProjectiles=projectileDirectory!=null&&
                !projectileDirectory.isBlank();
        if(captureArmor&&captureProjectiles)throw new IllegalArgumentException(
                "One real-frame diagnostic at a time");
        // This is the real native desktop renderer and real 200v200 demo.
        // Disabled for normal play; never delay visual tests on an updater HTTP
        // request or change the distribution's startup/update behavior.
        if(!captureArmor&&!captureProjectiles)updater.checkForUpdates(false);
        Runnable updateHandler=captureArmor||captureProjectiles?
                null:updater::checkForUpdates;
        new Lwjgl3Application(new CatastrophicGame(updateHandler,
                captureProjectiles,captureArmor),cfg);
    }
}
