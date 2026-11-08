# Campaign recovery repair checkpoints — 8 November 2026

## CP0 — scope and baseline

- Continued the committed audit and repair plan at `f887d8ba93459cabadcfe33a35e24fbd8669c75c`; production baseline was `0159042e47a9271e5e3b077756f5c4d57763a688`.
- Selected B0 verification coverage and B1/B2 campaign repairs: F01–F05, plus F20 report readability while changing Results. Preserved one main branch, one automatic smoke job, shared Java 17 core, existing save schema, nine tiers and all survival variants.
- Divided ownership between the core ledger, actual controller tests, existing workflow/task wiring, and client integration. No signing identity or release-request change.

## CP1 — implementation and local acceptance

- Added typed authority inspection and recoverable commits with full before/proposed comparison, acknowledgement reconciliation and explicit unknown outcomes. Existing strict commit and save encoding remain compatible.
- Added explicit damaged-save archive/reset. Original primary/backup bytes are streamed, fsynced and verified in a unique local archive before replacement. Valid backups still restore; inaccessible storage does not authorize reset.
- Results retain one frozen computed settlement until saved. Retry Save confirms an already-written proposal or commits that same proposal once. Return, Escape and Back retain pending state. A pending outcome cannot fund other campaign operations. Memory-only pending recovery explicitly tells the player to keep the app open.
- Unified Results preparation across pointer/keyboard and corrected Campaign-map Enter to open Deployment. Dock/squadron activation uses playable mission context; late research cards use the full nine-tier range.
- Reconciled pending campaign creation before Cancel/faction changes, preventing an already-written replacement from exposing the old live wallet/fleet after a lost acknowledgement.
- Results now use four measured report rows with distinct name/ID/expenditure baselines, plus visible save status and action labels. Genuine LWJGL/llvmpipe rendering through TCP Xvfb found an overlong filesystem error; shortened product messages and retained details in logs. Final captures follow in CP2.
- Existing affected-path suite passed: 99 named Smoke tasks, 109 actionable Gradle tasks, 83 seconds. Focused final source checks cover 64 ledger cases, actual controller flows, light behavior and desktop compilation; the final receipt is saved separately with the next checkpoint.
- Existing smoke workflow now covers runtime/build paths in all five modules and required assets/tools, with one job. It compiles desktop/Android and exercises campaign, update policies and networking. Shader-source spelling checks moved to the existing advisory diagnostic while behavioral lighting checks remain required. Broader audio/presentation convention classification remains open.

## Acceptance boundaries

- No new save format, pending-outcome journal, cross-process writer lock or hardware power-loss guarantee. A pending outcome cannot survive a forced process termination while storage is unable to save; the UI states this limitation.
- Desktop framebuffer evidence is real rendering against constructed temporary campaign fixtures. Phone-aspect desktop captures are not physical Android acceptance.
- Android compile and exact-source hosted CI remain to be observed after the production checkpoint. No APK/MSI release is claimed by this receipt.
- Historical defect-asserting audit probes remain unchanged. New positive controller/ledger tests verify repaired behavior.

Next: publish/read back the production checkpoint; preserve final screenshots, reproducible capture harness and logs; verify the one hosted job; update current repair status.
