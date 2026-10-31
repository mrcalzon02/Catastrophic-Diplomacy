# Localized punched-through armor stamps

**Source of truth:** The 16×16 real per-vessel directional armor and structure
grid, not random visual explosions. Each damaged occupied grid tile cuts one
stable irregular crater centered on its physical position in the approved ship
sprite. Sprite rotations and sizes reuse the existing renderer.

- Surface wear darkens the actual source PNG without alpha changes.
- Deeply eroded armor opens a visible, fully transparent crater core.
- The revealed layer is already a silhouette-clipped structural material under
  the source sprite; no outline or alpha is added outside the original art.
- Repeated strikes and armor bypass can open or widen real breaches. Saved
  integrity and paid repairs continue to govern the visual damage.
- Masks retain the current cached 96×144 texture per damaged vessel and rebuild
  only when real armor/structure integrity changes. Source PNG is never edited.
- The four-ship Android damage showcase already has actual legal fitted
  dreadnoughts with authentic simulated impact states. Global hit points,
  weapon damage, armor balance, and normal gameplay are NOT increased or changed.

Run `gradle :game:shipDamageVisualSmoke` as a standalone **nonblocking**
visual diagnostic. Review real Android game-frame screenshots before
claiming display-level acceptance.
