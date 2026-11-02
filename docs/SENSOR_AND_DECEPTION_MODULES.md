# Shipboard sensing, signature control and projected decoys

All nine module families are physically fitted, faction-specific components
in the shared shipyard, with their own four exclusive nine-rank research tracks.
They use the existing mass, internal volume, power, heat, industry, technology,
and module damage rules; an empty hull retains normal radar and close optical
spotting only. No new mount category or blueprint format is introduced.

| Code | Module | Socket | In-combat effect |
| --- | --- | --- | --- |
| RDR | Long-Range Radar Array | System | Extends powered radar acquisition against radar cross-sections |
| STC | Radar-Stealth Coating | Defense | Reduces radar signature and guided seeker tracking |
| IRT | Infrared Telescope | System | Detects heat emitters through optical concealment |
| HTD | Thermal Emission Damper | System | Suppresses thermal detectability while cooling remains functional |
| VCL | Active Visual Cloak | System | Reduces close optical detection while powered and ECM intact |
| ACU | Sensor Acuity Amplifier | System | Improves fire-control lock, acquisition and point defense tracking |
| XRY | X-Ray Detector | System | Short-range penetrating detection, reduced by heavy armor |
| GRA | Gravitic Anomaly Sensor | System | Detects displacement proportional to actual hull mass |
| HLP | Holo-Decoy Projector | Support | Disrupts enemy fire-control and seeker locks while powered |

Radar, infrared, x-ray, gravitic and optical spotting are independently
evaluated. No sensor is omniscient. Atmospheric/nebular weather and sensor
damage attenuate powered channels; visual spotting remains a short-range
physical floor. Cloaks and decoys require power and functioning EW systems.
Hologram projectors currently generate **spoofing noise in targeting and missile
seeker calculations**: visible duplicate ship sprites and selectable false
contacts are not yet implemented. No phantom hulls enter the simulation.

Tests: `diplomacy.sim.TechnologyCatalogTest` validates every faction's
module/research row and `diplomacy.sim.SensingModulesTest` validates real
socket fitting, persistence, hardware effects and channel counterplay.

## Four alternative hardware designs per family

Each of the nine original sensors now has **PRECURSOR (tier 0)**,
**COMPACT (tier II)**, **SPECIALIST (tier IV)**, and **ADVANCED (tier VI)**
alternatives with independent physical IDs. Original modules remain intact.
Each new alternative has all four exclusive nine-stage research branches.
The existing 5 manufacturing patterns × 4 marks still apply separately.

| Parent | Precursor | Compact | Specialist | Advanced |
| --- | --- | --- | --- | --- |
| Radar RDR | RDP Analog Echo | RDC Folded Radar | RDS Narrow-Beam | RDA Phased Multiband |
| Stealth STC | STP Chaff-Bonded Paint | STM Scatter Film | STS Broadband Panels | STA Metamaterial |
| Infrared IRT | IRP Heat Spotter | IRC Pocket Thermal | IRS Interferometer | IRA Cryogenic Imager |
| Heat HTD | HTP Exhaust Baffles | HTC Microchannel | HTS Ghost Diverter | HTA Cold-Skin Matrix |
| Cloak VCL | VLP Diffraction Mesh | VLC Micro-Shimmer | VLS Directional Veil | VLA Phase Cloak |
| Acuity ACU | ACP Plotting Computer | ACC Signal Parser | ACS Predictive Targeter | ACA Correlation Analyzer |
| X-ray XRY | XRP Flux Counter | XRC Scintillator | XRS Spectrum Analyzer | XRA Deep-Field Array |
| Gravitic GRA | GRP Mass Drift | GRC Tidal Meter | GRS Interferometer | GRV Tensor Observatory |
| Hologram HLP | HLT Basic Emitter | HLC Compact Holo | HLS False Contacts | HLA Phantom Fleet |

Primitive designs trade reach for minimal price and power.
Compact designs trade effectiveness and durability for lower mass.
Specialists focus strongly on their primary channel with heavier equipment.
Advanced designs demand substantially more power, heat disposal and funds.
Long-beam radars and advanced holographic arrays raise the host's radar
signature; advanced visual cloaks leak extra heat to infrared sensors.
Variants are actual combat machinery and preserve save/load identity.
Visible holographic phantom sprites remain outside this implementation.
