# Five installed laser families

This is an **existing shipyard manufacturing-pattern selection**, not another technology tree or an automatic unlock. Every fitted LAS socket keeps its independent ModuleVersions.Pattern and its nine-stage research branch. Each real firing event reads the next installed LAS hardpoint, then the selected manufacturing pattern:

| Module pattern | Laser type | Combat law (relative to the already-researched module) | Visual |
|---|---|---|---|
| STANDARD | PULSE | 1× range/damage/reload/power/heat | blue balanced beam |
| COMPACT | RAPID | .68× damage; .62× reload; .90× range; .72× energy/heat | narrow green pulses |
| REINFORCED | LANCE | 1.58× damage; 1.75× reload; 1.34× range; 1.50× power | broad violet beam |
| OVERCHARGED | OVERDRIVE | 1.86× damage; 1.28× reload; .88× range; 1.74× power; 2.05× heat | heavy red beam |
| LOW_EMISSION | NEEDLE | .79× damage; 1.12× reload; 1.45× range; 1.18× aim; .67× heat | thin gold needle beam |

Existing module version bonuses (research generation, cost, reliability and accuracy) remain active and stack multiplicatively with these new laser-specific firing properties. Existing standard hardware is unchanged except for the existing globally shared visual rendering. The fleet can mix laser types by fitting different patterns on different LAS sockets; the authoritative nextVisualShotId cycles among those physical sockets.

Firing uses **actual power draw, heat, range, cooldown, accuracy, damage, impact point and real slot muzzle**. Charge validation occurs before firing; no paid cost occurs for an ineligible shot. Both FIRE and IMPACT visual events carry the selected type, while collision/directional defenses continue using the common LASER damage category, avoiding a fake independent hit system. Beam lifetime, curated beam-strip color, thickness, glow corona and secondary destruction-event classification follow the type.

This phase intentionally does not introduce free multi-target laser chains or uncharged sustained damage, because those would require real separate hit and energy accounting.

Test: `:core:laserFamiliesSmoke` exercises all five types through a live Battle step, including real muzzle/beam geometry, actual hit event labels and cooldown. It runs within Linux/Windows release checks. Android visual performance and shader review remain pending until the latest release passes.
