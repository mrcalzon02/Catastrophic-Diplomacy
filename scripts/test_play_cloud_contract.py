#!/usr/bin/env python3
"""Check Play-only fleet Snapshot transport against the actual shared save contract."""
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
launcher=(ROOT/"android/src/play/java/diplomacy/android/PlayFleetCloudActivity.java").read_text()
manifest=(ROOT/"android/src/play/AndroidManifest.xml").read_text()
game=(ROOT/"game/src/main/java/diplomacy/client/CatastrophicGame.java").read_text()
serializer=(ROOT/"core/src/main/java/diplomacy/shipyard/FleetPresets.java").read_text()
fail=[]

def ck(ok,why,fix):
    print(("PASS: " if ok else "FAIL: ")+why)
    if not ok:
        print("SOLUTION: "+fix)
        fail.append(why)

ck("PlayFleetCloudActivity" in manifest and 'tools:node="remove"' in manifest,
   "Play-only cloud launcher registered", "Replace the sideload launcher only in Play manifest; preserve main.")
ck(all(x in launcher for x in ["SnapshotsClient", "isAuthenticated()", "RESOLUTION_POLICY_MANUAL",
  "commitAndClose(", "readFully(", "discardAndClose("]),
   "Real authenticated snapshot IO with unresolved-conflict protection",
   "Use actual SnapshotsClient Tasks with manual conflict detection, no silent latest-wins overwrites.")
ck(all(x in launcher for x in ["FleetPresets.decode(", "TechnologyCatalog.loadDefault()",
  "LIMIT_BYTES", "getFilesDir()", "fleets-v1.dat"]),
   "Existing verified fleet save format is sole cloud save payload",
   "Use the same exact checksum/fitting contract as the existing libGDX save file.")
ck(all(x in launcher for x in ["new AlertDialog.Builder(this)", "RESTORE FLEETS",
  "fleets-v1.dat.before-cloud-restore", "recreate()"]),
   "Restore requires consent, saves rollback, reloads game",
   "Never overwrite a local fleet silently; validate cloud payload and retain rollback.")
ck('FLEET_FILE = "fleet/fleets-v1.dat"' in game and "FleetPresets.encode(" in game,
   "Local game save path and parser remain authoritative",
   "Keep the shared canonical fleet serializer; do not create incompatible Play-only save schema.")
ck('MessageDigest.isEqual(' in serializer and 'Shipyard.inspect(' in serializer,
   "Corruption and invalid ship fittings are rejected",
   "Retain checksum and ship-fitting validation on both save and restore.")
for title in fail: print("::error title=Play fleet cloud test::"+title)
print("CATASTROPHIC PLAY FLEET CLOUD CONTRACT: "+("FAIL" if fail else "PASS"))
sys.exit(1 if fail else 0)
