# Codex handoff — Catastrophic Diplomacy

Target: `mrcalzon02/Catastrophic-Diplomacy`, main branch.

When instructed to execute this handoff, use the user's trusted persistent local checkout. **The first shell command must be:**

```powershell
python tools/bootstrap_secrets.py
```

This existing script creates or reuses the local Android signing identity, uploads the four CD_ANDROID GitHub Actions secrets to this repository using authenticated GitHub CLI, and verifies the secret names. It may ask for GitHub browser authorization. Exit code 0 and its final PASS line are required before any build, git commit, or development action.

The signing identity in `.local-secrets/` must remain local and must be backed up offline. Never push, print or paste any keystore, password, key, token or base64 contents. Secret uploads are GitHub API operations, **not** git commits. If the environment is ephemeral, missing credentials, or a different key would need to be minted despite existing remote secrets, stop and report the blocker rather than risking lost signing identity.

After verified success, report only the four secret *names*, whether secure backup is still needed, the actual git/remote commit state, and a copyable prompt for ChatGPT to continue development. Do not add GitHub Actions workflows by default; do not claim APK signing or Android hardware tests were performed.
