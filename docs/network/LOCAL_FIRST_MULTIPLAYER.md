# Local-first multiplayer — bounded implementation, 2026-10-28

This change contains working local gameplay adapters and a secure-admission implementation. **It is not the completed handoff or an Internet-ready release.** See TEST_RECEIPTS.md for actual results and remaining gates.

There is no account service, public registry, platform launcher requirement, NAT scanner or cloud game authority. Solo remains offline. The host owns the existing game rules. Manual saved servers and local contacts live in a checksummed, versioned local profile with an atomic replacement and validated backup. `hbnet-vendor.json` pins the renderer-independent Java package; `python tools/verify_hbnet.py` rejects source drift. The canonical source is The-Beckenburg-Project/src/main/java/hbnet, version hbnet-1.0. Swing views are separate platform adapters, not part of the common manifest.

TLS admission uses TLS 1.3, an explicitly supplied certificate SHA-256 pin, P-256/SHA256withECDSA nonce proofs, a salted 210,000-iteration PBKDF2-HMAC-SHA256 verifier and bounded frames/auth concurrency/address attempt windows. Certificate changes fail closed. Game-specific role, transaction and replay rules still execute after admission. Optional standard-KeyStore client certificate support binds the offered certificate to the signed player key; it does not grant trust or administrator authority.

The original 2026-10-28 workstation receipts observed local TLS interception and correctly rejected the changed certificate before application credentials. That historical environment failure is no longer the current project boundary: later exact-source GitHub-hosted loopback tests pass the configured certificate/pin path at 2, 32 and 400 ships, including authenticated reconnect and the submitted-fleet lobby. No trust bypass or plaintext fallback was added. Physical multi-machine TLS and WAN forwarding remain separate acceptance gates.

## Feature status

| Area | Delivered | Remaining |
|---|---|---|
| LAN | Existing game authority retained; actual localhost socket regressions | Physical Ethernet/Wi-Fi two-computer acceptance |
| Dedicated | Java 17 GPU-free build, executable JAR, explicit binding/local console, pinned-TLS submitted-fleet lobby, READY/UNREADY and host-only START | Multi-machine soak and service-manager installation |
| Internet | TLS/pin/admission code, no automatic downgrade; real hosted loopback encrypted gameplay at 2/32/400 ships and custom-fleet lobby | Physical separate-machine traffic capture, adversarial auth/ACL/timeout soak, residential WAN/NAT |
| Saved servers | Local add/edit/remove/favorite/select/connect and pin-change refusal | Measured refresh/query, notes/groups/schema migration acceptance, complete recent-server observation integration |
| Friends | Stable public-key cards, local review/accept/decline/remove/block/unblock, labels/notes/favorites, unknown presence; contact backup API | Authenticated request exchange, UI-reviewed server ACL import, key migration/revocation, opt-in observed presence and every native backup control |
| Invitations | Self-contained server-signed, expiring endpoint API; contains no password | Native invitation creation/import, one-use admission capability, server-side revocation and friend-to-friend transport |
| Stream Safe | Persistent local toggle, masked endpoint/port/password/fingerprint/card entry, pseudonyms, guarded copy, neutral network diagnostics; Swing sentinel/accessibility tests | Full game-overlay/crash/screenshot/console capture matrix, native GPU/device IME inspection, every transition and third-party OS capture |

Allowlisting currently exists as a reviewed-key-set constructor API and requires the password as well. It is **not** a complete selectable FRIENDS_ALLOWLIST/PRIVATE_INVITE policy UI. Local contact blocking alone does not ban an account on independently operated servers. Secure Beckenburg resume is bound to the signing identity and its existing rotating seat token; UI process-restart secure-ticket persistence is deferred rather than downgraded into a plaintext reconnect. Malefic secure IDs derive from the installation key, while old endpoint-derived legacy IDs have no automatic migration. Catastrophic now supports host-validated submitted `.cdfleet` lobbies under a host-selected technology ceiling; campaign wallets/rewards remain outside the network authority.

Next gate: run unchanged strict TLS tests on a machine/network presenting the configured server certificate; repair any actual protocol failures revealed there. Then exercise all game roles/ownership and reconnect paths over the encrypted transport before WAN forwarding. The full handoff remains open.
