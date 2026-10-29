# Local-first multiplayer — bounded implementation, 2026-10-28

This change contains working local gameplay adapters and a secure-admission implementation. **It is not the completed handoff or an Internet-ready release.** See TEST_RECEIPTS.md for actual results and remaining gates.

There is no account service, public registry, platform launcher requirement, NAT scanner or cloud game authority. Solo remains offline. The host owns the existing game rules. Manual saved servers and local contacts live in a checksummed, versioned local profile with an atomic replacement and validated backup. `hbnet-vendor.json` pins the renderer-independent Java package; `python tools/verify_hbnet.py` rejects source drift. The canonical source is The-Beckenburg-Project/src/main/java/hbnet, version hbnet-1.0. Swing views are separate platform adapters, not part of the common manifest.

TLS admission uses TLS 1.3, an explicitly supplied certificate SHA-256 pin, P-256/SHA256withECDSA nonce proofs, a salted 210,000-iteration PBKDF2-HMAC-SHA256 verifier and bounded frames/auth concurrency/address attempt windows. Certificate changes fail closed. Game-specific role, transaction and replay rules still execute after admission. Optional standard-KeyStore client certificate support binds the offered certificate to the signed player key; it does not grant trust or administrator authority.

The tests on this workstation observed a changed certificate: the presented subject matched the generated certificate, while its issuer no longer matched its subject. This indicates local TLS interception; exact-certificate pinning rejected it before any application password was sent. IPv4 and IPv6 were both rejected. No trust bypass or plaintext fallback was added. **The two secure socket acceptance commands fail here and remain required gates.** Local cryptography/storage tests and trusted-loopback gameplay tests are separate results, not substitutes.

## Feature status

| Area | Delivered | Remaining |
|---|---|---|
| LAN | Existing game authority retained; actual localhost socket regressions | Physical Ethernet/Wi-Fi two-computer acceptance |
| Dedicated | Java 17 GPU-free build, executable JAR, explicit binding and local console | Multi-machine soak, secure client acceptance and service-manager installation |
| Internet | TLS/pin/admission code, no automatic downgrade; legacy clients reject public addresses | Successful encrypted gameplay, traffic capture, adversarial auth/ACL/timeout/slow-client soak, residential WAN/NAT |
| Saved servers | Local add/edit/remove/favorite/select/connect and pin-change refusal | Measured refresh/query, notes/groups/schema migration acceptance, complete recent-server observation integration |
| Friends | Stable public-key cards, local review/accept/decline/remove/block/unblock, labels/notes/favorites, unknown presence; contact backup API | Authenticated request exchange, UI-reviewed server ACL import, key migration/revocation, opt-in observed presence and every native backup control |
| Invitations | Self-contained server-signed, expiring endpoint API; contains no password | Native invitation creation/import, one-use admission capability, server-side revocation and friend-to-friend transport |
| Stream Safe | Persistent local toggle, masked endpoint/port/password/fingerprint/card entry, pseudonyms, guarded copy, neutral network diagnostics; Swing sentinel/accessibility tests | Full game-overlay/crash/screenshot/console capture matrix, native GPU/device IME inspection, every transition and third-party OS capture |

Allowlisting currently exists as a reviewed-key-set constructor API and requires the password as well. It is **not** a complete selectable FRIENDS_ALLOWLIST/PRIVATE_INVITE policy UI. Local contact blocking alone does not ban an account on independently operated servers. Secure Beckenburg resume is bound to the signing identity and its existing rotating seat token; UI process-restart secure-ticket persistence is deferred rather than downgraded into a plaintext reconnect. Malefic secure IDs derive from the installation key, while old endpoint-derived legacy IDs have no automatic migration. Catastrophic hosts server-fitted starter corvettes; campaign fleets, arbitrary blueprints, campaign research/wallet transfers and rewards are deliberately outside this battle slice.

Next gate: run unchanged strict TLS tests on a machine/network presenting the configured server certificate; repair any actual protocol failures revealed there. Then exercise all game roles/ownership and reconnect paths over the encrypted transport before WAN forwarding. The full handoff remains open.
