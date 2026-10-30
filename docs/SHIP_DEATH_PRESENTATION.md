# Ship destruction and final-shot aftermath

Ship destruction triggers the render-only `ShipDeathTimeline` on the same simulation step that takes hull to zero. The ship disappears as an intact hull but breaks into eight UV-matched jagged fragments from its own original faction sprite, accompanied by ten soft smoke puffs and a short amber flash. Fallback geometry uses faction hull triangulation, not a square placeholder. Fragment lifetime is 2.55 seconds; smoke lifetime 3.75 seconds. Up to 24 bursts may be active, with camera culling and per-battle resets. The battle's damage, kills, ship inventory and combat RNG are untouched.

In ordinary campaign and skirmish engagements the final destruction is held on screen for 2.8 seconds before transitioning to victory/defeat or the next campaign wave. The existing screensaver 4-second end-of-round pause is unchanged. Pausing the engagement pauses the after-action transition timer.

Regression tasks `:game:shipDeathSmoke` and `:game:combatAftermathSmoke` run through the existing multiplatform `:game:menuFlowSmoke` release acceptance checks. Visual verification on physical Android remains unconfirmed.
