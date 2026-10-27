package diplomacy.android;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import diplomacy.shipyard.FleetPresets;
import diplomacy.tech.TechnologyCatalog;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Play-only cloud transport for the SAME checksum-validated fleet preset used
 * by desktop/Android. Manual save and restore deliberately avoid background
 * overwrite of another device's fleet. Restore retains an on-device rollback.
 */
public final class PlayFleetCloudActivity extends AndroidLauncher {
    @Override protected boolean allowSideloadUpdater() { return false; }

    private static final String SLOT = "catastrophic-diplomacy-fleets-v1";
    private static final int LIMIT_BYTES = 65000;
    private Button cloudButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cloudButton = new Button(this);
        cloudButton.setText("CLOUD FLEETS");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        params.bottomMargin = 36;
        params.rightMargin = 12;
        addContentView(cloudButton, params);
        cloudButton.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Play Games fleet backup")
            .setMessage("Your local fleet remains playable offline. Cloud uploads or restores require a signed-in Play Games profile.")
            .setPositiveButton("BACK UP FLEETS", (d, which) -> backup())
            .setNeutralButton("CHECK CLOUD SAVE", (d, which) -> restore())
            .setNegativeButton("KEEP LOCAL", null).show());
    }

    private File fleetFile() {
        return new File(getFilesDir(), "fleet/fleets-v1.dat");
    }

    private void notifyStatus(String text) {
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    private void authenticated(String action, Runnable operation) {
        PlayGames.getGamesSignInClient(this).isAuthenticated()
            .addOnCompleteListener(this, result -> {
                if (!result.isSuccessful() || !result.getResult().isAuthenticated()) {
                    notifyStatus(action + ": Play Games account not authenticated. Check Games Services configuration.");
                } else {
                    operation.run();
                }
            });
    }

    private static void validate(byte[] data) throws Exception {
        if (data == null || data.length == 0 || data.length > LIMIT_BYTES)
            throw new IllegalArgumentException("Missing or oversized fleet save");
        FleetPresets.decode(new String(data, StandardCharsets.UTF_8),
                            TechnologyCatalog.loadDefault());
    }

    private void backup() {
        authenticated("Backup", () -> {
            final byte[] bytes;
            try {
                File file = fleetFile();
                if (!file.isFile())
                    throw new IllegalArgumentException("No local fleet exists; customize and save the shipyard first");
                bytes = Files.readAllBytes(file.toPath());
                validate(bytes);
            } catch (Exception error) {
                notifyStatus("Backup rejected: " + error.getMessage());
                return;
            }
            SnapshotsClient client = PlayGames.getSnapshotsClient(this);
            client.open(SLOT, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || task.getResult().isConflict()) {
                        notifyStatus("Cloud save conflict or network failure. Local fleets preserved; resolve before upload.");
                        return;
                    }
                    Snapshot snap = task.getResult().getData();
                    if (snap == null) {
                        notifyStatus("Play Games did not provide a writable snapshot.");
                        return;
                    }
                    try {
                        snap.getSnapshotContents().writeBytes(bytes);
                    } catch (Exception error) {
                        client.discardAndClose(snap);
                        notifyStatus("Cloud save write failed: " + error.getMessage());
                        return;
                    }
                    client.commitAndClose(snap, new SnapshotMetadataChange.Builder()
                        .setDescription("Checksum-validated fitted fleet backup").build())
                        .addOnCompleteListener(this, committed ->
                            notifyStatus(committed.isSuccessful()
                                ? "Fleet backup committed to Play Games."
                                : "Cloud commit failed. Local fleets preserved."));
                });
        });
    }

    private void restore() {
        authenticated("Restore", () -> {
            SnapshotsClient client = PlayGames.getSnapshotsClient(this);
            client.open(SLOT, false, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || task.getResult().isConflict()) {
                        notifyStatus("Cloud save unavailable or conflicting. Local fleets preserved.");
                        return;
                    }
                    Snapshot snap = task.getResult().getData();
                    if (snap == null) {
                        notifyStatus("No stored Play Games fleet found.");
                        return;
                    }
                    final byte[] saved;
                    try {
                        saved = snap.getSnapshotContents().readFully();
                        validate(saved);
                    } catch (Exception error) {
                        notifyStatus("Cloud fleet rejected: " + error.getMessage());
                        return;
                    } finally {
                        client.discardAndClose(snap);
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Restore cloud fleets?")
                        .setMessage("This replaces this device's fitted fleets after saving a local rollback copy. It will reload the shipyard. This cannot merge different fleet versions.")
                        .setPositiveButton("RESTORE FLEETS", (dialog, which) -> applyRestore(saved))
                        .setNegativeButton("KEEP LOCAL", null).show();
                });
        });
    }

    private void applyRestore(byte[] data) {
        try {
            validate(data);
            File target = fleetFile();
            File directory = target.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IllegalStateException("Cannot create fleet save folder");
            if (target.isFile()) {
                Files.copy(target.toPath(), new File(directory, "fleets-v1.dat.before-cloud-restore").toPath(),
                           StandardCopyOption.REPLACE_EXISTING);
            }
            File temp = new File(directory, "fleets-v1.dat.cloud.tmp");
            Files.write(temp.toPath(), data);
            validate(Files.readAllBytes(temp.toPath()));
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            notifyStatus("Fleet cloud restore completed. Previous local save preserved.");
            recreate();
        } catch (Exception error) {
            notifyStatus("Restore aborted; original fleet preserved where possible: " + error.getMessage());
        }
    }
}
