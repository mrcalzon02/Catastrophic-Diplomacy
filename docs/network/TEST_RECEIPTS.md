# Multiplayer test receipts — 2026-10-28

Source base: `94b328c206be99952e64336a44e787f69233cac7`. Platform: Windows, Oracle Java 17.0.12 targeting `--release 17`. Local implementation SHA and remote read-back are recorded below after publication. These are source-development results, not production release acceptance.

Final test invocations: **51 passed, 2 failed**. Compilation/package command invocations: 2 passed, 0 failed. Repeated manifest entries count as separate invocations. Exact argv, duration, exit code and local log hash are in [delivery.json](receipts/delivery.json); [output](receipts/delivery.log) retains failures. Full-suite failures are not waived. The `local` hbnet invocation deliberately does not run socket acceptance; its 39 local cases are independent results.

The failed tests are:
- `hbnet.HbnetTest`: exit 1, 1.391 s. The strict pin is rejected before application credentials; this environment presents a changed certificate. Actual encrypted gameplay remains unverified.
- `diplomacy.server.BattleServerTest`: exit 1, 1.109 s. The strict pin is rejected before application credentials; this environment presents a changed certificate. Actual encrypted gameplay remains unverified.

Total measured command duration: 56.438 s. This sums child-command elapsed times; concurrent work and setup are not included.

Reproduce from the repository root with JAVA_HOME set to JDK 17:

```text
python tools/verify_hbnet.py
python tools/build_server.py
python tools/network_checks.py diplomacy --label acceptance --full
```

The full renderer-independent core test directory and native game source compilation were exercised. `BattleLanTest` ran actual socket orders/snapshots for 2, 32 and 400 ships, including spectator ownership rejection. It measured maximum tick duration and queued-byte counts in the log. These are short localhost measurements, not CPU/RSS profiling, throughput guarantees or 30 Hz sustained-WAN acceptance. The native authoritative ship-view code compiled; no GPU or physical Android walkthrough is claimed.

A separate server JVM and two independent client JVMs passed gameplay observations, clean STDIN stop and port-release verification: [process receipt](receipts/processes.json). These runs explicitly used trusted unencrypted loopback; they do not demonstrate TLS or physical LAN. Reproduce with `python tools/process_checks.py diplomacy` after compilation.

## Build/start commands

[Dedicated server instructions](DEDICATED_SERVER.md) give exact Windows/Linux build/start/config commands. No secrets are present in those commands or receipts. `server.cmd` and `server.sh` are committed; standalone JARs were built locally without a GPU/model exporter.

## Not verified / remaining gates

Physical Windows↔Windows/Linux Ethernet/Wi-Fi; NAT-forwarded separate-network WAN; Android↔desktop and API-26/29 TLS/IME behavior; long soak and OS packet impairment; full concurrent adversarial authentication, ACL/invite-revocation and reconnect-theft/race matrix; all Stream Safe loading/diagnostic/crash/overlay/screenshot transitions; clean-machine installation and service-manager lifecycle. The certificate-interception observation is an environment diagnosis, not proof that no further TLS/game bugs exist. Run the unchanged tests on a network presenting the configured certificate and repair any failures. Detailed per-feature gaps are in [LOCAL_FIRST_MULTIPLAYER.md](LOCAL_FIRST_MULTIPLAYER.md).

## Artifact hashes

- `delivery.json` SHA-256 `e580ea9a217d1e79ed6845f6a6d674b06d7f26c3b891d578cd465888794c231f`
- `delivery.log` SHA-256 `61f420c75364b5469d3a93d244bb6b98b1cb3dccd9d911bd8b0af6d2f72b6309`
- `processes.json` SHA-256 `3576ae0dbab36837a6341ff2581bb8e54c77657f8f1c7b6784c7fc5517645da3`
