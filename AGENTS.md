# Catastrophic Diplomacy — Codex instructions

The authoritative repository is `mrcalzon02/Catastrophic-Diplomacy` on `main`. The game uses Java 17 with shared deterministic core/game logic across desktop and Android. Do not copy proprietary reference-game assets or rewrite unrelated project files. Do not add GitHub Actions workflows without an explicit request.

## Required FIRST action for the pending signing handoff

Only when the user requests the **Codex signing handoff**, **Android secret upload**, or execution of `CODEX_HANDOFF.md`:

- Your first shell command, from the root of the **trusted persistent local checkout**, is exactly `python tools/bootstrap_secrets.py`. Do not run builds, git commands, inspections, edits, or commits before this step.
- Run on a machine where the created `.local-secrets/` can be retained and securely backed up. Never create a release signing identity in an ephemeral Codex container.
- Require the script's successful exit and its final confirmation that all four secret names exist at the target repository. A missing dependency, failed authorization, or partial upload is a blocker; repair safely and rerun the same command, with no bypasses or signing-key rotation.
- Never print, commit, upload to Google Drive, or include secret *values*, keystores, tokens, or signing property contents in messages, logs, patches or build artifacts. The local folder and keystore extensions are gitignored.
- Only then follow `CODEX_HANDOFF.md` and return a safe continuation prompt. A successful secrets upload is **not** proof of a signed Android build or Git commit.

This instruction is **limited to the pending signing handoff**; never automatically upload or rotate secrets in unrelated future tasks.
