# Nested guided submunitions — physical cluster carriers

## Implemented simulation rules

The existing fitted missile and torpedo launchers support two additional selectable
salvo modes. Neither is an animated explosion pretending to be a missile.

| Doctrine | Fitted rank | Paid launcher ammunition | Carrier -> children | Runtime outcome |
| --- | ---: | ---: | --- | --- |
| CLUSTER_CARRIER | 4 | 1 | 1 large guided body -> 6 micro missiles | Deploys around 52% through flight |
| CASCADE_SWARM | 5 | 4 | 4 small guided carriers -> 24 ultra-micro missiles | Each carrier deploys around 58% through flight |

Deploy distance is bounded to keep releases readable across both short and
long engagements. A parent that is killed by PD, whose target is dead, or whose
flight expires **cannot** release children. Parent release itself causes no
impact or free damage. Each child is a new tracked world entity with its
own visual shot ID, actual position/velocity, life, target, seeker, fuel,
point-defense radar signature, intercept HP and swept collision checks. Nearby
enemy ships within 185 world units of the assigned hull can receive different
child targets. No child can deploy another cluster.

Each launched carrier costs one actual magazine round and power/heat; its
children are already carried internally and do not consume independent launcher
rounds. Total raw damage across all six children is 88% of that carrier's
pre-deployment damage. Warhead type and the *parent's fitted projectile
characteristics* are inherited; a child never applies ammunition damage or
physical multipliers twice. Multiple hits resolve against the real directional
shield, tile armor, and hull layers individually. Point-defense systems can
intercept children one by one.

A battlefield cap of 12,000 live guided objects is respected during carrier
release. When the cap blocks some children, the unspawned submunitions do not
turn into free impact damage. Physical simulations remain deterministic,
including their split counters and per-child target assignments.

## Already curated art — no fake effects or source-art resampling

The existing 40-body original Library atlas is authoritative for rendered
guided payloads. Carriers use the large-body family; micro-cluster carriers and
micro/ultra-micro children use the micro-body family. The existing curated
blue-seeker body is the fallback. The ultra-micro payload draws at **0.22**
of the ordinary missile-body width before the game's existing global
one-third combat FX scaling. Engine exhaust uses the real child motor state.

## Verification

`./gradlew :core:ordnanceSubmunitionsSmoke` checks live in-flight split
for both missile and torpedo dispensers, cascade fan-out, finite magazine
consumption, separate per-child IDs and hostile targets, conservative
damage budget, parent interception, physically smaller/faster children,
non-recursion and fixed-seed replay.

Visual appearance remains diagnostic-only and must be verified using **real**
desktop/Android combat screenshots. Functional failures—not aesthetic opinions—
are the release-blocking criteria.
