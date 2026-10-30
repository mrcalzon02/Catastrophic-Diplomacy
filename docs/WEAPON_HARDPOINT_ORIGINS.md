# Installed weapon hardpoint launch origins

Every actual fitted ship builds a typed emitter list from the shipyard's existing faction-specific HullLayouts positions, preserving the stable installed slot IDs. LAS, KIN, PLA, PAR, MSL, TOR, PD and FLK each map to their own weapon family. Different guns of a matching family cycle deterministically by shot ID; no new random calls or fake ship modules.

A module position is rotated from the ship's bow/starboard layout into authoritative world coordinates using the vessel's current heading and displayed hull scale. The same world muzzle is assigned to the physical shell or guided projectile spawn, beam endpoint A, and corresponding firing visual event. Long salvos use their actual allocated launch ID, not the first launcher on the ship. Point defense uses fitted PD/FLK sockets for both anti-missile and anti-fighter shots.

Unfitted legacy simulation fixtures have no installed module layout: their test-only fallback is still center-origin fire. This is NOT used to fake a mount on a properly fitted ship.

The change deliberately preserves weapon ranges, rate of fire, crew, ammunition, target acquisition, damage and salvo costs. The kinetic ballistic initial aim is computed from its true launch socket, so its real trajectory aligns with the muzzle.

`gradle :core:weaponMuzzleSmoke` validates module mapping, rotations, alternating sockets and real loaded ship FIRE-event origins. The existing `:core:projectileModuleSmoke` smoke chain invokes it. Android/Windows/Linux gameplay checks must compile and pass before this feature is considered release-ready.
