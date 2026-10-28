# Dedicated Java 17 host

Install JDK 17+ and Python 3. Set JAVA_HOME to that JDK (on Windows, use its bin before Oracle's launcher aliases). Build from the repository root:

```text
python tools/build_server.py
java -cp build/dedicated-server.jar hbnet.Setup server-network
```

Setup refuses to overwrite an existing server identity. It generates a persistent PKCS12 certificate/key, protected keystore-password file, salted room verifier and generated room secret. `--manual` reads a 12–128 printable ASCII password from an actual secret console prompt, preserving spaces exactly. No password is passed on argv or printed. Read `server-network/server.fingerprint` and, for generated keys, `server-network/room.secret` privately and deliver out of band. The room secret grants no administrator authority. Protect and back up this directory; Windows owner ACLs/POSIX owner permissions are applied. Keys remain exportable by the OS owner and administrators; no hardware/non-exportability promise is made. Identity/password rotation and recovery need operator review; do not delete identity files as an update procedure.

Start (same Java command works in Windows and Linux):

```text
java -jar build/dedicated-server.jar server-network 27817 16 127.0.0.1
```

`server.cmd` / `sh server.sh` pass the same arguments and supply a 1 GiB heap. Override heap flags with the direct Java command. Default binding is loopback. An explicit bind/listener choice is required for remote reachability; no firewall, UPnP, port forwarding, public-address lookup or discovery service is installed. Legacy listeners and clients restrict unauthenticated/plaintext connections to trusted private/link/loopback addresses; a user-managed private reverse proxy or NAT setup still requires operator judgment. Do not advertise this prototype on WAN while security acceptance is failing.

`:server` uses the existing core Battle at 30 Hz, with at most three catch-up steps per pump. Two server-assigned fleets and spectators receive complete bounded snapshot batches with tick/state hashes and an independent presentation-content hash; orders are acknowledged and constrained by fleet, tick window and sequence. The bounded slice accepts only host-fitted starter corvettes, so clients cannot submit unlicensed modules or mutate campaign resources. Server queues close slow clients rather than drop authoritative changes. Reconnect uses the proven signing identity and preserves its fleet/command watermark while that server process lives. Snapshot clients do not resimulate combat; cross-platform determinism is not claimed. Saved replays are checksummed order exports, not an implemented crash-resume/campaign restore system. The native MULTIPLAYER menu uses the existing hull/sprite renderer for authoritative ship poses; projectile rendering parity and physical Android input/TLS verification are outstanding. Explicit credential-free LAN: `java -cp build/dedicated-server.jar diplomacy.server.OpenLanBattleServer 127.0.0.1 27817 16`. This is not a fallback when TLS fails. Gradle builds: `gradlew :server:installDist :server:networkSmoke`; standalone build below needs neither Gradle, Android SDK nor libGDX.

For updates: stop through STDIN, verify exit/port release, back up data and the complete identity directory, build the reviewed source, replace the JAR, restart with unchanged identity and data paths, and run acceptance. For rollback retain the previous JAR and full coherent save/identity backup. Malefic keeps its existing validated world/checkpoint/backup migration constraints. No unauthenticated remote RCON or web administrator exists. OS systemd/Windows service wrappers and automatic restart policies are operator-managed and not installed by this change.
