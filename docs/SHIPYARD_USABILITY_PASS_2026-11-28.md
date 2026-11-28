# Shipyard usability pass — 2026-11-28

The first batch isolates the fitting canvas from other controls on the existing
1280×720 UI. It uses one presentation-only transform for hull art, schematic,
socket markers and hit-testing, clipped with the real FitViewport. Drag/pinch
captures cannot commit a fitting or purchase. Wheel zoom is cursor-anchored;
FIT restores the entire hull; previous/next mount navigation remains available
even when sockets overlap. The displayed zoom is bounded to 100–400%.
The shared blueprint, engineering, campaign spend and combat rules are unchanged.

## Next batches (not implemented by this change)

1. Responsive landscape/mobile layout: collapsible palette and inspector,
   independently scrollable panels, larger touch targets, compact text.
2. Persistent hardpoint navigator: group by mount type, show fitted/empty/locked
   states, bring selected mount into view, retain selection while changing tabs.
3. Fitting comparison: candidate versus installed stats, capacity and power/heat
   margins, cost and unlock reasons before committing. Preserve explicit FIT.
4. Blueprint workflow: visible dirty state, undo/redo of draft edits, clear
   SAVE versus APPLY versus paid PURCHASE/REFIT, confirmation retained.
5. Real headed acceptance on Windows and Android: small phone, wide desktop,
   dense capital hull, art/schematic toggle, repeated pinches, edge dragging,
   tab transitions, cancellation, saved blueprint and campaign quote parity.
   Capture before/after views and touch gestures. Record actual runtime/build
   results; do not infer them from a source-only change.

No new release request, signing changes, or battle simulation changes.
