package diplomacy.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.TenderDoctrine;
import diplomacy.shipyard.OrdnanceFitting;
import diplomacy.shipyard.SupportDoctrine;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.HullBaseline;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.SurvivalHardwareCatalog;

/** Deterministic 30 Hz combat simulation. No graphics, files, time or Android APIs. */
public final class Battle {
    public static final double DT = 1.0 / 30.0;
    public enum Faction {
        TERRAN("Terran Confederacy", 0.30f, 0.70f, 0.95f),
        DRAXIAN("Draxian Empire", 0.50f, 0.36f, 0.65f),
        CARNELIAN("Carnelian Furlongs", 0.85f, 0.22f, 0.48f),
        ZIPPLEDROW("Zippledrow", 0.15f, 0.88f, 0.98f),
        UZIPT("Uzipt", 0.32f, 0.64f, 0.85f),
        MANDRALLY("Mandrally", 0.43f, 0.77f, 0.42f),
        VAERITH_PRISM("Vaerith Prism Dominion",0.61f,0.35f,0.90f),
        ZELTHAR_LUMINARI("Zelthar Luminari Accord",0.12f,0.84f,0.86f),
        AUREATE_SYNOD("Aureate Cathedral Synod",0.95f,0.80f,0.53f),
        NYX_BIOLITH("Nyx Biolith Enclave",0.78f,0.32f,0.83f),
        UMBRA_FOUNDRY("Umbra Foundry Compact",0.45f,0.34f,0.59f);
        public final String displayName;
        public final float r, g, b;
        Faction(String label, float red, float green, float blue) {
            displayName = label; r = red; g = green; b = blue;
        }
    }

    public enum Subsystem { REACTOR, ENGINES, WEAPONS, POINT_DEFENSE, SENSORS, COOLING, COMMAND, HANGAR, MAGAZINE, ELECTRONIC_WARFARE }
    public enum DamageType { LASER, KINETIC, PLASMA, PARTICLE, MISSILE, TORPEDO, FIGHTER }

    public static final class Ship implements SpaceWeather.Target {
        public final int id, team;
        public final Faction faction;
        public String name;
        /** Per-hull doctrine. POINT DEFENSE is independent of main gun fire orders. */
        public BattleOrders.Orders orders=BattleOrders.DEFAULT;
        /** Optional individual standing policy, continuously enforced, not consumed. */
        public ShipStandingOrders standingOrders;
        /** Explicit nondefault captain fire cadence, keyed by family or physical rack. */
        public final java.util.Map<Integer,Long> resourceNextFireTick=new java.util.HashMap<>();
        public int escortShipId;
        public List<Integer> defendedShipIds=List.of();
        /** Per-hull sequential mission plan, independent of weapons and fleet doctrine. */
        public ShipCommandQueue commandQueue=new ShipCommandQueue(List.of());
        public int lastTargetId,lastAttackerId;
        public double anchorOffsetX,anchorOffsetY;
        public Shipyard.Hull hullClass;
        /** Campaign-only visual override; weapon loadout and damage are still real fitted modules. */
        public String campaignBossArt;
        /** Installed and physically located shipyard modules available for hit targeting. */
        public List<Shipyard.Slot> impactHardpoints=List.of();
        /** Installed weapon-family emitters in the original faction hull layout. */
        public List<WeaponMuzzles.Emitter> weaponEmitters=List.of();
        /** Legacy ships use their original fitted/default beam PD; a fitted
         * FLK-only ship does not automatically fire an invisible laser turret. */
        public boolean laserPointDefenseEnabled=true;
        public int laserPointDefenseMountCount;
        /** Fitted flak is physical timed fragmentation with its own mount cycle. */
        public static final class FlakMount {
            public final int slotId;
            public final double nominalRange,cycle;
            public final ProjectileModel.Defense profile;
            public double reload;
            public FlakMount(int slot,double range,ProjectileModel.Defense spec,double firingCycle){
                if(slot<0||range<=0||spec==null||!Double.isFinite(firingCycle)||firingCycle<=0)
                    throw new IllegalArgumentException("Invalid flak hardware");
                slotId=slot;nominalRange=range;profile=spec;cycle=firingCycle;
            }
        }
        public List<FlakMount> flakMounts=List.of();
        /** RCW is a separate, paid rotary kinetic hardpoint with its own
         * spin-up, finite feed and researched short-range radar profile. */
        public List<RotaryPointDefense.Mount> rotaryMounts=List.of();
        /** Long-charge, long-range physically fitted anti-torpedo rail guns. */
        public List<RailPointDefense.Mount> railMounts=List.of();
        /** Sustained particle-ablation beams have their own gimbals, dwell
         * state and thermal reservoirs; not pulse-laser firing cycles. */
        public List<ParticlePointDefense.Mount> particlePdMounts=List.of();
        /** Each fitted precision CIWS module owns its socket, research and timer.
         * Legacy unfitted fixtures retain the original shared PD controller. */
        public static final class PrecisionMount {
            public final int slotId;
            public final double nominalRange,cycle;
            public final ProjectileModel.Defense profile;
            public double reload;
            /** World-space optical servo state persists across physical target switches. */
            public double bearing=Double.NaN,lockSeconds;
            public long trackedBodyId=Long.MIN_VALUE,lastAimTick=-1,reservedOrdnanceTick=-1;
            public int targetAcquisitions,slewTicks,lockWaitTicks;
            public PrecisionMount(int slot,double range,ProjectileModel.Defense spec,double firingCycle){
                if(slot<0||range<=0||spec==null||!Double.isFinite(firingCycle)||firingCycle<=0)
                    throw new IllegalArgumentException("Invalid precision CIWS hardware");
                slotId=slot;nominalRange=range;profile=spec;cycle=firingCycle;
            }
        }
        public List<PrecisionMount> precisionMounts=List.of();
        /** Per-hull scratch map reused at 30 Hz; bounded by actual turret count. */
        public final java.util.Map<Long,Double> precisionDamageReservations=
                new java.util.HashMap<>();
        /** Reused each simulation frame; expensive rail rounds avoid redundant
         * shots against the same already-committed fragile incoming body. */
        public final java.util.Map<Long,Double> railDamageReservations=
                new java.util.HashMap<>();
        /** Opt-in physically distinct rack controllers; legacy remains family-wide. */
        public List<Launcher> launchers=List.of();
        /** Separate cheap dumbfire magazine per installed rocket hardpoint. */
        public List<RocketBarrage.Rack> rocketRacks=List.of();
        public Launcher launcher(int slot){
            for(Launcher rack:launchers)if(rack.slot==slot)return rack;
            return null;
        }
        public static final class Launcher {
            public final int slot;
            public final DamageType family;
            public final OrdnanceFitting.Mounted fitting;
            public int magazine;
            public double reload;
            Launcher(int socket,DamageType type,OrdnanceFitting.Mounted load,int rounds){
                slot=socket;family=type;fitting=load;magazine=rounds;
            }
        }
        public Map<Integer,LaserFamilies.Type> laserFittings=Map.of();
        public Map<Integer,Subsystem> installedSubsystems=Map.of();
        /** Directional powered shields, localized armor plates and exposed structure. */
        public DirectionalDefense defense;
        /** Research-family presentation of the strongest installed SHD module.
         * The existing four-facing physical shield mechanics remain unchanged. */
        public Shipyard.Track shieldFieldBranch;
        public double x, y, vx, vy, facing;
        public double hull, maxHull, shields, maxShields, armor, maxArmor, sif, power, maxPower, heat, maxHeat;
        /** Persistent capital-ship reaction mass; unlike transient fighter flight fuel. */
        public double fuel=180,maxFuel=180;
        /** Actual fitted consumable limits; supplied rounds cannot exceed these. */
        public int maxKineticAmmo,maxMissileAmmo,maxTorpedoAmmo;
        public TenderDoctrine tenders=TenderDoctrine.NONE;
        /** Ammo fractional pulse budget: no fractional projectiles or stock cloning. */
        public final double[] tenderCredit=new double[TenderDoctrine.Cargo.values().length];
        public int roundsDelivered;
        public double fuelDelivered;
        public long lastChatterTick=-1000;
        public double engineCondition = 1, reactorCondition = 1, weaponsCondition = 1, pointDefenseCondition = 1;
        public double sensorsCondition = 1, coolingCondition = 1, commandCondition = 1;
        public double damageControl, speed, accuracy, laserDamage, missileDamage, pdRange;
        /** Physical engine mode, derived from actual installed and licensed ENG
         * systems. Legacy headless fixtures default to basic fusion thrust. */
        public SublightPropulsion.Drive propulsion=SublightPropulsion.Drive.FUSION_TORCH;
        /** Manned hulls have NO intrinsic complement; berths/oxygen are installed. */
        public double crewCount;
        public int crewRequired, crewBerths, lifeSupport;
        public boolean fittedCrewSystems;
        public double crewReadiness(){
            if(!fittedCrewSystems)return 1; // legacy unfitted simulation fixtures
            return Math.max(0,Math.min(1,crewCount/Math.max(1,crewRequired)));
        }
        /** Ship hull sensor and cross-section, independent from fitted scanner modules. */
        public double sensorRange=1100, radarCrossSection=1;
        /** Independent sensors/countermeasures, paid for by fitted modules. */
        public double infraredRange,xrayRange,graviticRange;
        public double thermalSignature=1,opticalSignature=1,sensorAcuity=1;
        public double hologramNoise;
        /** Six installed sensor-network modules; zeroed baseline grants no
         * extra spectrum, memory, command bandwidth or target processors. */
        public Shipyard.Network network=new Shipyard.Network(
                0,0,1,0,0,0,0,0,0,0,0,1);
        public double commandAssist;
        /** The ONE selected, powered FCM compute donor, if any. */
        public int commandSourceId;
        /** Only contacts actually acquired during this battle are recorded.
         * Never refresh these coordinates from a currently invisible enemy. */
        public final Map<Integer,ContactTrack> knownTracks=new HashMap<>();
        /** Fixed historical light-time samples; no instant telemetry in the HUD. */
        public final SensorLightTimeHistory sensorHistory=new SensorLightTimeHistory();
        /** A packet must be transmitted by a real scout before ally receipt. */
        public final SensorRelayBuffer relayPackets=new SensorRelayBuffer();
        /** Separate non-locking phantom reports share the actual SHR budget. */
        public final SensorFalseContactRelay falsePackets=new SensorFalseContactRelay();
        /** Fitted LNK sends actual remembered fixes with separate causal transit. */
        public final SensorTrackRelayBuffer trackPackets=new SensorTrackRelayBuffer();
        /** Installed acquisition doctrine. STANDARD retains old automatic sensing. */
        public final SensorActiveScan.State scan=new SensorActiveScan.State();
        /** Finite per-tick RES/TRK/ACU compute workload; CAP is not CPU. */
        public final SensorComputeBudget.State targetCompute=new SensorComputeBudget.State();
        /** Shared FCM processor cycles reside only on their owner and are
         * debited by competing allied target-solving requests. */
        public final SensorComputeBudget.State fleetCompute=new SensorComputeBudget.State();
        /** Actual kinetic, beam and fighter shots and physical guided seekers
         * fooled by this emitter, not fictional effects counters. */
        public int holoShotsDiverted,holoSeekersDiverted;
        /** Fitted crew, command, fuel, magazine, rescue and unsafe engineering. */
        public double crewProtection,commandProtection,fuelProtection,magazineContainment;
        /** Fractional physical loss carries across hits; small ammo stores
         * no longer ignore real penetration due to per-hit integer truncation. */
        public double kineticStoreExposure,missileStoreExposure,torpedoStoreExposure;
        public double hazardInstability,hazardSeverity;
        public int unsafeFlags,lastFaultOrigin;
        public int escapePods,evacuatedCrew,hazardFailures;
        /** Real simulation ticks for visible engineering faults and repairs. */
        public long lastHazardTick=-10000,lastRepairTick=-10000;
        public boolean recoveryBeacon,recoveryBeaconActivated;
        /** Effective strength of real fitted recovery hardware; zero without a beacon. */
        public double recoveryStrength;
        /** Physical ammunition comes from fitted launch modules, not sprite geometry. */
        public ProjectileModel.Spec kineticProjectile=ProjectileModel.standard(ProjectileModel.Kind.KINETIC);
        public ProjectileModel.Spec missileProjectile=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        public ProjectileModel.Spec torpedoProjectile=ProjectileModel.standard(ProjectileModel.Kind.TORPEDO);
        public ProjectileModel.Defense pointDefenseProfile=ProjectileModel.STANDARD_PD;
        public double pdCycle=1;
        public FighterModel.Profile fighterProfile=FighterModel.standard(FighterModel.Hull.ASSAULT_GUNSHIP);
        /** Highest purchased FTR avionics research stage. */
        public int fighterCountermeasureTier;
        public Shipyard.Weapons weapons=new Shipyard.Weapons(
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1);
        public Shipyard.Support support=new Shipyard.Support(0,0,180,0,175,false,0,1,1,0);
        public SupportDoctrine logistics=SupportDoctrine.NONE;
        public final int[] serviceDroneReserve=new int[SupportDoctrine.Channel.values().length];
        public double armorServiced,powerSupplied,auraService;
        public int serviceDronesRearmed;
        public double hullRepaired,shieldRecharged,remoteHullRepaired,shieldsTransferred;
        public int repairDronesLaunched;
        /** Installed machinery determines operating headroom and thermal rejection. */
        public double powerRegen = 16, coolingRate = 19, passiveHeat;
        public double laserReload, missileReload, pdReload;
        public double kineticDamage, plasmaDamage, particleDamage, torpedoDamage, ecmStrength;
        public double kineticReload, plasmaReload, particleReload, torpedoReload, fighterReload, droneReload,repairDroneReload;
        public double hangarCondition=1, magazineCondition=1, ecmCondition=1;
        public int missileAmmo=60, torpedoAmmo, fighterReserve, droneReserve,repairDroneReserve;
        /** Stable physical launch lanes: native catapults and individually fitted FTR sockets.
         * Damage disables only the affected lane, never its paid fighter inventory. */
        public static final class HangarBay {
            public final String id;
            public final int socketIndex;
            public final boolean organic;
            public double condition=1.0;
            HangarBay(String identity,int socket,boolean builtIn){
                id=identity;socketIndex=socket;organic=builtIn;
            }
            public boolean operational(){return condition>=0.20;}
        }
        public List<HangarBay> fighterBays=List.of();
        /** Legacy total launch-lane count remains available to existing UI/telemetry. */
        public int fighterLaunchBays;
        public HangarBay fighterBay(String identity){
            for(HangarBay bay:fighterBays)if(bay.id.equals(identity))return bay;
            return null;
        }
        /** A module hit targets its actual FTR socket; unlocated native-deck damage
         * targets one organic catapult. Other hangar subsystem hits retain the
         * shared legacy hangar-condition behavior. */
        public void damageFighterBay(int socket,double loss){
            if(!Double.isFinite(loss)||loss<0)throw new IllegalArgumentException("Invalid bay damage");
            for(HangarBay bay:fighterBays){
                if(socket>=0?bay.socketIndex==socket:bay.organic){
                    bay.condition=Math.max(0,bay.condition-loss);
                    return;
                }
            }
            hangarCondition=Math.max(0,hangarCondition-loss);
        }
        static List<HangarBay> baysFor(Shipyard.Design design){
            List<HangarBay> bays=new ArrayList<>();
            int nativeCount=diplomacy.shipyard.HullBaseline.builtInLaunchBays(design.hull);
            for(int n=0;n<nativeCount;n++)
                bays.add(new HangarBay("ORG:"+n,-1,true));
            for(Shipyard.Slot slot:design.hull.slots){
                Shipyard.Installed part=design.at(slot.index());
                if(slot.mount()==Shipyard.Mount.SUPPORT && part!=null
                        &&part.component.endsWith("-FTR"))
                    bays.add(new HangarBay("FTR:"+slot.index(),slot.index(),false));
            }
            return List.copyOf(bays);
        }
        public int kineticAmmo=100;
        public AmmunitionTable.Kinetic kineticType=AmmunitionTable.Kinetic.STANDARD;
        /** Null only on legacy addShip(faction) test fixtures; real fitted ships
         * are governed by the per-socket research intersection. */
        public AmmunitionResearch.Access ammoAccess;
        /** Weakest installed licensed socket: no higher-tech configurations
         * on real fitted fleets. Unfitted legacy simulation fixtures are exempt. */
        public int missileOrdnanceTier=-1,torpedoOrdnanceTier=-1;
        public int ordnanceTier(DamageType family){
            if(family==DamageType.MISSILE)return missileOrdnanceTier;
            if(family==DamageType.TORPEDO)return torpedoOrdnanceTier;
            throw new IllegalArgumentException("Not a guided launcher");
        }
        private void requireOrdnanceTier(DamageType family,int required){
            // Ships with no launcher must still accept the harmless stock
            // profile during fleet spawn. Non-stock profiles need hardware.
            if(ammoAccess!=null&&required>0&&ordnanceTier(family)<required)
                throw new IllegalArgumentException("Fitted "+family+
                        " lacks guided technology tier "+required);
        }
        public final EnumMap<DamageType,AmmunitionTable.Warhead> warheads=
                new EnumMap<>(DamageType.class);
        public AmmunitionTable.Warhead warhead(DamageType family){
            if(family!=DamageType.MISSILE&&family!=DamageType.TORPEDO)
                throw new IllegalArgumentException("Warheads require guided payloads");
            return warheads.getOrDefault(family,AmmunitionTable.Warhead.STANDARD);
        }
        public void setWarhead(DamageType family,AmmunitionTable.Warhead kind){
            warhead(family);AmmunitionTable.warhead(kind);
            if(ammoAccess!=null&&!ammoAccess.allows(family,kind))
                throw new IllegalArgumentException(
                        "Guided payload "+kind+" is locked for this fitted "+family+" weapon research");
            warheads.put(family,kind);
        }
        public void setKineticType(AmmunitionTable.Kinetic kind){
            AmmunitionTable.kinetic(kind);
            if(ammoAccess!=null&&!ammoAccess.allows(kind))
                throw new IllegalArgumentException(
                        "Kinetic ammunition "+kind+" is locked for this fitted weapon research");
            kineticType=kind;
        }
        /** Independent selectable guided firing schematic for each weapon family.
         * Defaults preserve all existing single-shot ammunition behavior. */
        public final EnumMap<DamageType,OrdnanceDoctrine.Mode> launchModes=
                new EnumMap<>(DamageType.class);
        public OrdnanceDoctrine.Mode launchMode(DamageType family){
            OrdnanceDoctrine.patterns(family); // reject unguided requests
            return launchModes.getOrDefault(family,OrdnanceDoctrine.Mode.SINGLE);
        }
        public void setLaunchMode(DamageType family,OrdnanceDoctrine.Mode mode){
            OrdnanceDoctrine.forWeapon(family,mode);
            requireOrdnanceTier(family,OrdnanceFitting.requiredTier(mode));
            launchModes.put(family,mode);
        }
        /** Motor and guidance are independent of salvo pattern. The old
         * doctrine-to-motor mapping remains the default for old designs. */
        public final EnumMap<DamageType,OrdnancePropulsion.Mode> propulsionModes=
                new EnumMap<>(DamageType.class);
        public final EnumMap<DamageType,OrdnanceGuidance.Mode> guidanceModes=
                new EnumMap<>(DamageType.class);
        public OrdnancePropulsion.Mode propulsionMode(DamageType family){
            OrdnanceDoctrine.patterns(family);
            return propulsionModes.getOrDefault(family,
                    OrdnancePropulsion.forDoctrine(launchMode(family)));
        }
        public OrdnanceGuidance.Mode guidanceMode(DamageType family){
            OrdnanceDoctrine.patterns(family);
            return guidanceModes.getOrDefault(family,OrdnanceGuidance.Mode.PURSUIT);
        }
        public void setPropulsionMode(DamageType family,OrdnancePropulsion.Mode mode){
            OrdnanceDoctrine.patterns(family);
            if(mode==null)throw new IllegalArgumentException("Missing propulsion plan");
            requireOrdnanceTier(family,OrdnanceFitting.requiredTier(mode));
            propulsionModes.put(family,mode);
        }
        public void setGuidanceMode(DamageType family,OrdnanceGuidance.Mode mode){
            OrdnanceDoctrine.patterns(family);
            if(mode==null)throw new IllegalArgumentException("Missing seeker doctrine");
            requireOrdnanceTier(family,OrdnanceFitting.requiredTier(mode));
            guidanceModes.put(family,mode);
        }
        /** Null clears an override and restores the legacy salvo default. */
        public void resetPropulsionMode(DamageType family){
            OrdnanceDoctrine.patterns(family);propulsionModes.remove(family);
        }
        /** Permanent sixteen-digit campaign identity; zero for uncommissioned
         * skirmish ships. Tactical combat target IDs remain short-lived ints. */
        public long commissionedId;
        /** Optional commissioned-squadron radio voice; skirmish inherits fleet. */
        public CrewChatter.Tone squadronRadio;
        public int fightersLaunched, dronesLaunched, launched, intercepted, kills;
        public boolean retired;
        /** Committed withdrawal remains active after field repairs; escaped hulls
         * remain in the battle registry but cannot fight or be targeted. */
        public boolean withdrawing, escaped;
        public long withdrawalTick=-1, escapeTick=-1;
        public int commanderSkill;
        Ship(int identifier, int side, Faction group, String callSign) {
            id = identifier; team = side; faction = group; name = callSign;
            maxHull = 195; maxShields = 95; maxArmor = 75; maxPower = 120; maxHeat = 100;
            speed = 62; accuracy = 0.65; laserDamage = 19; missileDamage = 52; sif = 0.20;
            pdRange = 115; damageControl = 0.014; commanderSkill = 2;
            switch (group) {
                case TERRAN: maxPower *= 1.13; pdRange *= 1.13; break;
                case DRAXIAN: case VAERITH_PRISM: maxShields *= 1.45; sif += 0.08; laserDamage *= 1.18; break;
                case CARNELIAN: case NYX_BIOLITH: maxHull *= 1.36; maxArmor *= 1.60; speed *= 0.74; break;
                case ZIPPLEDROW: case UMBRA_FOUNDRY: speed *= 1.65; maxHull *= 0.72; maxShields *= 0.68; break;
                case UZIPT: case ZELTHAR_LUMINARI: accuracy += 0.17; pdRange *= 1.34; commanderSkill = 4; break;
                case MANDRALLY: case AUREATE_SYNOD: damageControl *= 3.0; maxHull *= 1.11; maxHeat *= 0.85; break;
                default: break;
            }
            hull = maxHull; shields = maxShields; armor = maxArmor; power = maxPower;
            defense=new DirectionalDefense(maxShields,maxArmor,maxHull);
        }
        public boolean alive() { return hull > 0 && !retired && !escaped; }
        public int weatherId() { return id; }
        public double weatherX() { return x; }
        public double weatherY() { return y; }
        /** Visible top-down hull scale; larger fitted capital hulls read as larger ships. */
        public double visualScale() {
            double ordinary=Math.max(0.78, Math.min(1.90, StrictMath.sqrt(maxHull / 200.0)))
                    * (hullClass==null?1:hullClass.spriteScale());
            double size="warrant_spire".equals(campaignBossArt)?ordinary*2.5:ordinary;
            return size*WorldScale.SHIP_WORLD_SCALE;
        }
        public double combatReadiness() {
            return Math.max(0, hull / maxHull) * (0.5 + 0.5 * weaponsCondition)
                    * crewReadiness();
        }
        public String status() {
            return String.format(Locale.ROOT, "%s hull=%.0f shield=%.0f armor=%.0f heat=%.0f",
                    name, hull, shields, armor, heat);
        }
    }

    public static final class Missile {
        public final long visualId, spawnTick;
        public final int sourceId, team;
        public int targetId;
        /** vx/vy are world-units/second, never normalized heading components. */
        public double x, y, vx, vy, fuel, damage, integrity, propellant;
        /** Physical body position at the start of the current 30Hz step. */
        public double previousX,previousY;
        public final boolean torpedo;
        public final ProjectileModel.Spec spec;
        public final double guidanceQuality;
        /** Payload kind controls actual warhead/countermeasure effects. */
        public final OrdnanceDoctrine.Payload payload;
        /** Physical target distance at which this one-generation canister opens. */
        public final double releaseRange;
        public final AmmunitionTable.Warhead warhead;
        public final boolean decoy;
        /** Cold launches wait before ignition and can be killed by PD while coasting. */
        public int ignitionDelayTicks;
        public OrdnancePropulsion.Mode propulsionMode=OrdnancePropulsion.Mode.CONSTANT_BURN;
        public OrdnanceGuidance.Mode guidanceMode=OrdnanceGuidance.Mode.PURSUIT;
        /** Opposite lanes belong to physical missiles, not visual duplicates. */
        public int approachSide=1;
        /** Read by the effects renderer: true, simulation-owned engine state. */
        public OrdnancePropulsion.Phase thrustPhase=OrdnancePropulsion.Phase.CRUISE;
        public int flightTicks;
        /** The seeker tracks only its original assigned hull. Lost locks retain
         * the last actually observed position; scans never grant perfect telemetry. */
        public boolean seekerLocked=true;
        /** Physical-world holographic contact currently deceiving this seeker. */
        public int falseContactIndex=-1;
        public double lastKnownX,lastKnownY,lastKnownVX,lastKnownVY;
        /** Bounded uncertainty of the last historical seeker estimate. A
         * lost lock continues to age; CAP never grants perfect missile data. */
        public double seekerUncertainty;
        /** The missile's own last sampled receiver pose: no future enemy
         * telemetry and no costly full history allocated per missile. */
        public double seekerReceiverX,seekerReceiverY;
        public long seekerReceiverTick;
        /** Last physically received seeker sample, not target truth. */
        public long lastObservationTick;
        void setPropulsion(OrdnancePropulsion.Mode mode){
            if(mode==null)throw new IllegalArgumentException("No propulsion mode");
            propulsionMode=mode;
            thrustPhase=ignitionDelayTicks>0?OrdnancePropulsion.Phase.COLD_EJECT:
                    mode==OrdnancePropulsion.Mode.BOOST_COAST_TERMINAL
                    ||mode==OrdnancePropulsion.Mode.SILENT_GHOST
                    ||mode==OrdnancePropulsion.Mode.PULSE_BURN?
                    OrdnancePropulsion.Phase.BOOST:OrdnancePropulsion.Phase.CRUISE;
            double priorSpeed=StrictMath.hypot(vx,vy);
            double ejectSpeed=spec.speed()*OrdnancePropulsion.initialSpeedMultiplier(thrustPhase);
            if(priorSpeed>0){vx*=ejectSpeed/priorSpeed;vy*=ejectSpeed/priorSpeed;}
            propellant=spec.speed()*(mode==OrdnancePropulsion.Mode.BOOST_COAST_TERMINAL?1.85:
                    mode==OrdnancePropulsion.Mode.SILENT_GHOST?1.75:
                    mode==OrdnancePropulsion.Mode.PULSE_BURN?3.2:2.6);
        }
        Missile(long visualId,long spawnTick, int source, int owner, int target,
                double sx,double sy,double dx,double dy,double amount,
                boolean heavy,ProjectileModel.Spec profile,double launchGuidance){
            this(visualId,spawnTick,source,owner,target,sx,sy,dx,dy,amount,
                    heavy,profile,launchGuidance,OrdnanceDoctrine.Payload.STANDARD,0,
                    AmmunitionTable.Warhead.STANDARD);
        }
        Missile(long visualId,long spawnTick, int source, int owner, int target, double sx, double sy, double dx, double dy, double amount,
                boolean heavy, ProjectileModel.Spec profile,double launchGuidance,
                OrdnanceDoctrine.Payload launchPayload,int ignitionTicks) {
            this(visualId,spawnTick,source,owner,target,sx,sy,dx,dy,amount,
                    heavy,profile,launchGuidance,launchPayload,ignitionTicks,
                    AmmunitionTable.Warhead.STANDARD);
        }
        Missile(long visualId,long spawnTick,int source,int owner,int target,
                double sx,double sy,double dx,double dy,double amount,
                boolean heavy,ProjectileModel.Spec profile,double launchGuidance,
                OrdnanceDoctrine.Payload launchPayload,int ignitionTicks,
                AmmunitionTable.Warhead warheadType) {
            this.visualId=visualId;this.spawnTick=spawnTick;sourceId = source; team = owner; targetId = target; x = sx; y = sy;
            previousX=sx;previousY=sy;
            seekerReceiverX=sx;seekerReceiverY=sy;
            seekerReceiverTick=spawnTick;
            torpedo=heavy;
            double magnitude = StrictMath.hypot(dx, dy);
            vx = magnitude == 0 ? 1 : dx / magnitude;
            vy = magnitude == 0 ? 0 : dy / magnitude;
            if(profile==null)throw new IllegalArgumentException("Missile properties missing");
            if(!Double.isFinite(launchGuidance)||launchGuidance<0.2||launchGuidance>2.6)
                throw new IllegalArgumentException("Invalid missile guidance quality");
            if(launchPayload==null||ignitionTicks<0||ignitionTicks>90)
                throw new IllegalArgumentException("Invalid guided payload");
            payload=launchPayload;decoy=launchPayload==OrdnanceDoctrine.Payload.DECOY;
            releaseRange=OrdnanceSubmunitions.releaseDistance(launchPayload,magnitude);
            AmmunitionTable.warhead(warheadType);warhead=warheadType;
            if(decoy && amount!=0)
                throw new IllegalArgumentException("Countermeasures are forbidden from damaging ships");
            ignitionDelayTicks=ignitionTicks;
            if(ignitionTicks>0)thrustPhase=OrdnancePropulsion.Phase.COLD_EJECT;
            spec=profile;guidanceQuality=launchGuidance;
            lastKnownX=sx+dx;lastKnownY=sy+dy;
            // Legacy direct construction starts at rated speed; queued launch
            // schemes then set the appropriate ejection/boost impulse.
            vx*=spec.speed();vy*=spec.speed();
            propellant=spec.speed()*2.6;
            fuel=StrictMath.min(BattleArena.MAX_GUIDED_SECONDS,
                    WeaponBehaviorTable.gun(heavy?DamageType.TORPEDO:DamageType.MISSILE)
                            .flightSeconds()+ignitionTicks*DT);
            damage=amount;integrity=profile.health();
        }
    }

    /**
     * Slow kinetic rounds are real interceptable objects, NOT a visual beam with
     * instantaneous damage. All positions/velocities are world units per second.
     * A ballistic shell never homes after launch; its destination is predicted by
     * the firing ship at the instant it pulls the trigger.
     */
    public static final class Ballistic {
        /** Firing modules define the actual ballistic velocity and PD cross section. */
        public final ProjectileModel.Spec spec;
        public final long visualId,spawnTick;
        public final int sourceId,team,targetId;
        public final double vx,vy,damage;
        public final AmmunitionTable.Kinetic ammunition;
        public double x,y,life,integrity;
        public double previousX,previousY;
        Ballistic(long id,long firedAt,int source,int side,int target,
                  double px,double py,double heading,double amount,ProjectileModel.Spec profile){
            this(id,firedAt,source,side,target,px,py,heading,amount,profile,
                    AmmunitionTable.Kinetic.STANDARD);
        }
        Ballistic(long id,long firedAt,int source,int side,int target,
                  double px,double py,double heading,double amount,ProjectileModel.Spec profile,
                  AmmunitionTable.Kinetic type){
            if(profile==null)throw new IllegalArgumentException("Ballistic properties missing");
            AmmunitionTable.kinetic(type);ammunition=type;
            spec=profile;
            visualId=id;spawnTick=firedAt;sourceId=source;team=side;targetId=target;
            x=px;y=py;previousX=px;previousY=py;
            vx=profile.speed()*StrictMath.cos(heading);
            vy=profile.speed()*StrictMath.sin(heading);
            life=StrictMath.min(BattleArena.MAX_BALLISTIC_SECONDS,
                    StrictMath.max(1.5,760.0/profile.speed()));
            integrity=profile.health();damage=amount;
        }
    }

    /** Rockets are real point-defensible bodies. Heading is IMMUTABLE after
     * launch: neither the real target nor any hologram can redirect them. */
    public static final class Rocket {
        public final long visualId,spawnTick;
        public final int sourceId,team,targetId;
        public final RocketBarrage.Size size;
        public final RocketBarrage.Motor motor;
        public final ProjectileModel.Spec spec;
        public final double heading,damage,sustainedThrustFactor;
        public double x,y,vx,vy,life=RocketBarrage.MAX_FLIGHT_SECONDS,integrity;
        public double previousX,previousY;
        public int flightTicks;
        Rocket(long id,long start,Ship source,RocketBarrage.Rack rack,
               int aimedAt,double px,double py,double bearing){
            visualId=id;spawnTick=start;sourceId=source.id;team=source.team;
            targetId=aimedAt;size=rack.size;motor=rack.motor;
            spec=rack.body;damage=rack.damage;
            sustainedThrustFactor=rack.sustainedThrustFactor;
            x=px;y=py;previousX=px;previousY=py;heading=bearing;
            vx=StrictMath.cos(heading)*78;vy=StrictMath.sin(heading)*78;
            integrity=spec.health();
        }
        public boolean burning(){
            return RocketBarrage.acceleration(size,motor,flightTicks*DT)>0;
        }
    }

    /** Fighters attack hostile hulls. Defensive drones deliberately hunt hostile ordnance. */
    public static final class Craft {
        public final int id, sourceId, team;
        public final boolean fighter,repair;
        /** Physical bay that launched this craft; empty for non-fighter drones. */
        public String launchBayId="";
        public double x, y, integrity, maxIntegrity,armor,fuel,reload,repairReserve;
        /** Measured physical travel from the preceding completed simulation tick.
         * Flak uses this actual motion, not perfect future course knowledge. */
        public double lastObservedX,lastObservedY,observedVx,observedVy;
        public SupportDoctrine.Channel serviceChannel;
        public boolean reusable,returning;
        public double serviceRange,servicePulse,serviceMaxCharge;
        public int targetId,targetCraftId,ammunition,shotsFired;
        /** Per-airframe finite dispensers; drones carry neither. */
        public int flareRemaining,chaffRemaining;
        public long nextCountermeasureTick;
        /** Null for legacy defense and repair drones; non-null for autonomous fighters. */
        public final FighterModel.Profile profile;
        public ProjectileModel.Spec radarProfile(){
            return profile!=null?profile.radarProfile()
                    :new ProjectileModel.Spec(repair?145:205,maxIntegrity,repair?0.46:0.29,
                            repair?0.67:0.98);
        }
        Craft(int identifier, Ship source, boolean offensive, int target) {
            this(identifier,source,offensive,false,target);
        }
        Craft(int identifier, Ship source,boolean offensive,boolean service,int target) {
            id=identifier;sourceId=source.id;team=source.team;
            fighter=offensive;repair=service;targetId=target;x=source.x;y=source.y;
            profile=offensive?source.fighterProfile:null;
            if(offensive){
                var pack=FighterCountermeasures.forHull(profile.hull(),
                        source.fighterCountermeasureTier);
                flareRemaining=pack.flares();chaffRemaining=pack.chaff();
            }
            integrity=offensive?profile.health():service?20:15;
            maxIntegrity=integrity;armor=offensive?profile.armor():0;
            fuel=offensive?profile.fuel():service?52:22;
            ammunition=offensive?profile.ammunition():0;
            repairReserve=service?32*source.support.droneEndurance():0;
            serviceChannel=service?SupportDoctrine.Channel.HULL:null;
            serviceMaxCharge=repairReserve;
            servicePulse=service?source.support.repairDroneRate()*0.80:0;
            serviceRange=service?300:0;
            reusable=service;
        }
        Craft(int identifier,Ship source,SupportDoctrine.Emitter emitter,int target){
            this(identifier,source,false,true,target);
            serviceChannel=emitter.channel();
            reusable=true;
            fuel=52;
            repairReserve=emitter.charge();
            serviceMaxCharge=repairReserve;
            serviceRange=emitter.range();
            servicePulse=emitter.rate();
            integrity=26;maxIntegrity=integrity;
        }
    }

    public static final class Beam {
        public final long visualId, spawnTick;
        public final double x1, y1;
        /** Mutable visible photon front for main weapons only. */
        public double x2,y2;
        public boolean inFlight;
        public final int team;
        public final DamageType type;
        /** A distinct defensive emitter, not another full-sized main laser. */
        public final boolean pointDefense;
        public final LaserFamilies.Type laserType;
        public double lifetime = 0.13;
        Beam(long visualId,long spawnTick,double x1,double y1,double x2,double y2,int team,DamageType type) {
            this(visualId,spawnTick,x1,y1,x2,y2,team,type,false,LaserFamilies.Type.PULSE);
        }
        Beam(long visualId,long spawnTick,double x1,double y1,double x2,double y2,int team,DamageType type,boolean pointDefense) {
            this(visualId,spawnTick,x1,y1,x2,y2,team,type,pointDefense,LaserFamilies.Type.PULSE);
        }
        Beam(long visualId,long spawnTick,double startX,double startY,double endX,double endY,
             int side,DamageType weapon,boolean pd,LaserFamilies.Type laser){
            this.visualId=visualId;this.spawnTick=spawnTick;
            x1=startX;y1=startY;x2=endX;y2=endY;team=side;type=weapon;pointDefense=pd;
            laserType=laser==null?LaserFamilies.Type.PULSE:laser;
            if(weapon==DamageType.LASER&&!pd)lifetime=laserType.lifetime;
        }
    }

    /** Simulation-confirmed beam from a repair or shield support module. */
    public enum SupportKind { HULL, SHIELD, ARMOR, POWER, DRONE, DRONE_SHIELD, DRONE_ARMOR, DRONE_POWER }
    public static final class SupportLink {
        public final int sourceId,targetId,team;
        public final SupportKind kind;
        public final double x1,y1,x2,y2;
        public final long spawnTick;
        // A distinct short transfer pulse, not a continuously drawn tether.
        public double life=0.10;
        SupportLink(Ship source,Ship target,SupportKind type,long time,double fromX,double fromY){
            sourceId=source.id;targetId=target.id;team=source.team;kind=type;
            x1=fromX;y1=fromY;x2=target.x;y2=target.y;spawnTick=time;
        }
    }

    /** Read-only transient visual notifications; never change damage or RNG. */
    public enum VisualPhase { FIRE, IMPACT, INTERCEPT, TIMEOUT }
    public record VisualEvent(long sequence, long shotId, long tick, VisualPhase phase,
                              String weapon, int team, int sourceId, int targetId,
                              double x, double y, double targetX, double targetY) { }
    private static final int VISUAL_HISTORY_MAX=8192;

    private final Random random;
    private final List<Ship> ships = new ArrayList<>();
    /** Stable entity-ID index: fighter, missile and support loops frequently
     * resolve parent/target IDs; avoid 400-ship linear scans per craft/tick.
     * Ships remain in the battle registry after destruction, so historical
     * projectile owners and replay telemetry remain addressable.
     */
    private final Map<Integer,Ship> shipById = new HashMap<>();
    private final Map<Integer,Integer> focusCounts=new HashMap<>();
    private static final int MAX_GUIDED_BODIES=12000;
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Ballistic> ballistics = new ArrayList<>();
    private final List<Rocket> rockets = new ArrayList<>();
    /** Actual finite, unguided fragmentation shell. The blast is resolved
     * only when this paid, world-space projectile reaches its preset fuze
     * point, never at a time inferred from the target's last known range.
     * Projected lead/error are fixed at launch; the shell cannot retarget. */
    public static final double FLAK_SHELL_SPEED=390.0;
    public static final int MAX_ACTIVE_FLAK_SHELLS=3000;
    public static final class FlakRound {
        public final int team,shooterId,slotId;
        public final long shotId,spawnTick;
        public final double startX,startY,aimX,aimY,radius,damage,vx,vy,totalDistance;
        public double x,y,remainingDistance,life;
        public FlakRound(int team,int shooter,int slot,long shot,long at,
                         double startX,double startY,double targetX,double targetY,
                         double radius,double damage){
            double dx=targetX-startX,dy=targetY-startY;
            double distance=StrictMath.hypot(dx,dy);
            if(team<0||shooter<=0||slot<0||shot<=0||at<0||
                    !Double.isFinite(distance)||distance<.001||
                    !Double.isFinite(startX)||!Double.isFinite(startY)||
                    !Double.isFinite(radius)||radius<=0||
                    !Double.isFinite(damage)||damage<=0)
                throw new IllegalArgumentException("Invalid real flak shell");
            this.team=team;shooterId=shooter;slotId=slot;
            shotId=shot;spawnTick=at;
            x=startX;y=startY;this.startX=startX;this.startY=startY;
            aimX=targetX;aimY=targetY;totalDistance=distance;
            this.radius=radius;this.damage=damage;
            vx=FLAK_SHELL_SPEED*dx/distance;
            vy=FLAK_SHELL_SPEED*dy/distance;
            remainingDistance=distance;
            life=StrictMath.min(BattleArena.MAX_POINT_DEFENSE_SECONDS,
                    distance/FLAK_SHELL_SPEED+DT);
        }
        /** Move only within the actual travelled segment; no teleport to
         * fuze point and no burst beyond the fixed simulation world. */
        public boolean advance(double dt){
            if(!Double.isFinite(dt)||dt<=0)throw new IllegalArgumentException("Invalid shell timestep");
            double advance=StrictMath.min(remainingDistance,dt*FLAK_SHELL_SPEED);
            x+=vx/FLAK_SHELL_SPEED*advance;
            y+=vy/FLAK_SHELL_SPEED*advance;
            remainingDistance=StrictMath.max(0,remainingDistance-advance);
            life-=dt;
            return remainingDistance<=.000001;
        }
    }
    private final List<FlakRound> pendingFlak=new ArrayList<>();
    /** Travelling, collision-tested defensive machine-cannon rounds. */
    private final List<RotaryPointDefense.Round> rotaryRounds=new ArrayList<>();
    private final List<RailPointDefense.Penetrator> railPenetrators=new ArrayList<>();
    private static final int MAX_ROCKET_BODIES=8000;
    /** Queued physical salvo is owned by one socket, or -1 for legacy fleets. */
    private static final class PendingLaunch {
        final int sourceId,targetId,slotId;
        final DamageType family;
        final OrdnanceDoctrine.Pattern pattern;
        final OrdnancePropulsion.Mode propulsion;
        final OrdnanceGuidance.Mode guidance;
        final AmmunitionTable.Warhead loadedWarhead;
        /** Immutable contact captured when this salvo was actually authorized;
         * subsequent salvo rounds cannot consult hidden target coordinates. */
        SensorLightTimeHistory.Observation targetFix;
        int nextIndex;
        long nextTick;
        PendingLaunch(int source,int target,DamageType type,
                      OrdnanceDoctrine.Pattern doctrine,long start,
                      AmmunitionTable.Warhead loaded,
                      OrdnancePropulsion.Mode motor,OrdnanceGuidance.Mode seeker){
            this(source,target,type,doctrine,start,loaded,motor,seeker,-1);
        }
        PendingLaunch(int source,int target,DamageType type,
                      OrdnanceDoctrine.Pattern doctrine,long start,
                      AmmunitionTable.Warhead loaded,
                      OrdnancePropulsion.Mode motor,OrdnanceGuidance.Mode seeker,int socket){
            sourceId=source;targetId=target;family=type;pattern=doctrine;slotId=socket;
            if(loaded==null||motor==null||seeker==null)
                throw new IllegalArgumentException("Incomplete guided salvo configuration");
            loadedWarhead=loaded;propulsion=motor;guidance=seeker;nextTick=start;
        }
    }
    private final List<PendingLaunch> launchQueue=new ArrayList<>();
    public int decoysLaunched,decoysIntercepted,salvosStarted,roundsFiredBySalvos;
    public int rocketRoundsFired,rocketIntercepts,rocketHits;
    /** Actual in-flight deployments, not fictitious visual particle counts. */
    public int carriersSplit,submunitionsDeployed;
    private final List<Craft> crafts = new ArrayList<>();
    private static final int MAX_FIGHTER_CLOUDS=512;
    private final List<FighterCountermeasures.Cloud> fighterClouds=new ArrayList<>();
    public int fighterChaffDeployed,fighterFlaresDeployed;
    public int fighterChaffFlakDiversions,fighterChaffRotaryDiversions;
    private int nextCraftId=1;
    private final List<Beam> beams = new ArrayList<>();
    /** Real wavefronts; never store future hull hit decisions. */
    private static final class BeamPhoton {
        final long shotId,spawnTick;
        final int sourceId,team;
        final DamageType family;
        final BeamRayGeometry.Ray ray;
        final double range,damage;
        final String label;
        final Beam visual;
        double travelled;
        BeamPhoton(long id,long at,Ship source,DamageType type,
                   BeamRayGeometry.Ray direction,double raw,String weapon,Beam display){
            shotId=id;spawnTick=at;sourceId=source.id;team=source.team;
            family=type;ray=direction;
            range=StrictMath.hypot(ray.x2()-ray.x1(),ray.y2()-ray.y1());
            damage=raw;label=weapon;visual=display;
        }
    }
    private final List<BeamPhoton> pendingBeamPhotons=new ArrayList<>();
    public int pendingBeamPhotonCount(){return pendingBeamPhotons.size();}
    private final List<SupportLink> supportLinks=new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<Integer> eventSourceIds = new ArrayList<>();
    private final List<VisualEvent> visualEvents=new ArrayList<>();
    private long nextVisualShotId=1, nextVisualSequence=1;
    private int nextId = 1;
    private final BattleOrders.Orders[] teamOrders={
            BattleOrders.DEFAULT,BattleOrders.DEFAULT
    };
    /** Cosmetic radio preference. Does not influence simulation RNG or tactics. */
    private final CrewChatter.Tone[] teamRadioTones={
            CrewChatter.Tone.MIXED,CrewChatter.Tone.MIXED
    };
    private final long seed;
    public long tick;
    /** Unresolved termination is recorded in the authoritative deterministic
     * battle, not inferred from which side happened to have surviving hulls. */
    private BattleTermination.Reason terminationReason=BattleTermination.Reason.NONE;
    private long lastCombatProgressTick;
    private int lastObservedHits,lastObservedDestructions,lastObservedEscapes;
    public BattleTermination.Reason terminationReason(){return terminationReason;}
    public boolean unresolved(){return terminationReason!=BattleTermination.Reason.NONE;}
    public int hitCount, missileIntercepts, ballisticIntercepts, destructions, environmentalHits;
    /** Proof counters distinguish launched fighter rounds from mere animation. */
    public int fighterGunShots,fighterLaserShots,fighterParticleShots,
               fighterMissilesLaunched,fighterTorpedoesLaunched,craftLosses;
    /** Diagnostic counters: real PD hits and kills of hostile craft, excluding
     * ordinary fuel expiry, voluntary docking or other combat losses. */
    public int pointDefenseCraftHits,pointDefenseCraftKills;
    /** Kill attribution is recorded only after a real hostile body is
     * removed. These counters do not alter fire control, armor, probabilities,
     * ammunition or RNG. Decoys are distinct from damaging guided bodies. */
    public enum DefensiveGun { LASER_PD, FLAK, ROTARY_CIWS, RAIL_PD, PARTICLE_PD }
    public enum DefensiveTarget { MISSILE, TORPEDO, DECOY, ROCKET, KINETIC, CRAFT }
    private final int[][] defensiveKills=
            new int[DefensiveGun.values().length][DefensiveTarget.values().length];
    public int defensiveKills(DefensiveGun gun,DefensiveTarget target){
        if(gun==null||target==null)throw new IllegalArgumentException("Defense counter key required");
        return defensiveKills[gun.ordinal()][target.ordinal()];
    }
    private void recordDefensiveKill(DefensiveGun gun,DefensiveTarget target){
        defensiveKills[gun.ordinal()][target.ordinal()]++;
    }
    private static DefensiveTarget guidedDefenseTarget(Missile missile){
        return missile.decoy?DefensiveTarget.DECOY:
                missile.torpedo?DefensiveTarget.TORPEDO:DefensiveTarget.MISSILE;
    }
    private SpaceWeather weather = new SpaceWeather(SpaceWeather.clear());
    /** Physical TRK memory stores received historical samples only. */
    public static final class ContactTrack {
        public double x,y,vx,vy;
        public long tick;
        ContactTrack(SensorLightTimeHistory.Sample sample){update(sample);}
        void update(SensorLightTimeHistory.Sample sample){
            x=sample.x();y=sample.y();vx=sample.vx();vy=sample.vy();
            tick=sample.tick();
        }
    }

    /** One actual close-contact spotting ship per enemy for each faction.
     * Reused every tick, so 200v200 targeting has no per-ship relay allocation. */
    private final Ship[][] sharedSpotters={new Ship[16],new Ship[16]};
    private final double[][] sharedNearest={new double[16],new double[16]};
    /** Separate additional broadcasts, limited by paid transceiver capacity. */
    private final Ship[][] expandedSpotters={new Ship[16],new Ship[16]};
    private final double[][] expandedNearest={new double[16],new double[16]};
    /** Configure preordered scan doctrine without changing shipyard save IDs.
     * Later ship-order integration will serialize these policy selections. */
    public void configureSensorScan(int shipId,SensorActiveScan.Mode mode,int focusTargetId){
        if(tick!=0)throw new IllegalArgumentException("Scan doctrines are pre-engage orders");
        Ship ship=findShip(shipId);
        if(ship==null||!ship.alive())throw new IllegalArgumentException("Missing scanner");
        ship.scan.configure(mode,focusTargetId);
    }

    /** Optional physical precision-CIWS lock-time sweep. It changes how
     * long optics must hold a real body before firing, never shot accuracy or
     * weapon damage. Configure only before tick one in a diagnostic battle. */
    public void configurePrecisionLockForDiagnostic(double multiplier){
        if(tick!=0||!Double.isFinite(multiplier)||multiplier<.20||multiplier>2.0)
            throw new IllegalArgumentException("Invalid laboratory CIWS lock duration");
        diagnosticPrecisionLockMultiplier=multiplier;
    }
    /** Test sustained-burn ROCKET acceleration without changing live weapons.
     * Real rocket heading, researched speed cap, lifetime and ammunition remain
     * unchanged. Configure only before the simulation starts. */
    public void configureSustainedRocketThrustForDiagnostic(double multiplier){
        if(tick!=0||!Double.isFinite(multiplier)||multiplier<.50||multiplier>3.0)
            throw new IllegalArgumentException("Invalid laboratory rocket thrust");
        diagnosticSustainedRocketThrustMultiplier=multiplier;
    }
    /** Laboratory-only finite optical fire-control track capacity. A given
     * turret can track N simultaneous physical bodies at full acquisition
     * speed; excess tracks lengthen elapsed lock time, never force a miss. */
    public void configurePrecisionTrackCapacityForDiagnostic(int tracksPerMount){
        if(tick!=0||tracksPerMount<0||tracksPerMount>16)
            throw new IllegalArgumentException("Invalid CIWS optical track capacity");
        diagnosticPrecisionTracksPerMount=tracksPerMount;
    }
    /** Vary how simultaneous contacts share real fire-control processing
     * time, not hit chance or the number of free visual projectiles. */
    public void configurePrecisionTrackExponentForDiagnostic(double exponent){
        if(tick!=0||!Double.isFinite(exponent)||exponent<.25||exponent>1.25)
            throw new IllegalArgumentException("Invalid CIWS tracking workload exponent");
        diagnosticPrecisionTrackExponent=exponent;
    }
    /** Simulate radial fragment-density losses inside genuine timed flak bursts.
     * This A/B changes only fragment energy versus spatial distance, not
     * ammo, target acquisition, burst timing or number of physical bodies. */
    public void configureFlakFragmentFalloffForDiagnostic(boolean enabled){
        if(tick!=0)throw new IllegalStateException("Configure flak before combat");
        flakFragmentFalloff=enabled;
    }
    /** A/B compare old endpoint-only geometry with correct two-body
     * swept flight. Production defaults to physical relative motion. */
    public void configureRailRelativeSweepForDiagnostic(boolean enabled){
        if(tick!=0)throw new IllegalStateException("Configure rail collision before combat");
        diagnosticRailRelativeSweep=enabled;
    }
    /** Compare per-socket optical allocations to the old duplicate targeting.
     * This opt-in switch is only legal before the first simulation tick. */
    public void configurePrecisionReservationsForDiagnostic(boolean enabled){
        if(tick!=0)throw new IllegalStateException("Configure CIWS reservation before combat");
        diagnosticDisablePrecisionReservations=!enabled;
    }
    /** Opt-in screensaver policy only. Player/campaign battles never make
     * autonomous extraction decisions for the player's surviving ships. */
    private boolean unattendedExhibition;
    /** Diagnostic-only crowding trial, disabled for all ordinary battles. */
    private double diagnosticRocketPdPressure;
    /** Laboratory-only propulsion sensitivity; production stays neutral. */
    private double diagnosticSustainedRocketThrustMultiplier=1.0;
    /** Four simultaneously serviced incoming physical tracks per fitted
     * precision emitter. Crowded fire control spends more time acquiring
     * targets; it never forces a miss or grants a free interception. A
     * laboratory override may set zero for the pre-overload control. */
    private int diagnosticPrecisionTracksPerMount=4;
    /** Measured intermediate parallel/serial scheduling under saturation;
     * laboratory trials can override, all multipliers remain <=4x. */
    private double diagnosticPrecisionTrackExponent=.90;
    /** Physical burst density decreases smoothly toward the outer edge.
     * Verified against aircraft, swarms, fast shells and armored torpedoes;
     * a pre-battle laboratory override retains the old uniform-density A/B. */
    private boolean flakFragmentFalloff=true;
    /** Normal battle uses actual relative-motion sweeps. Controlled pre-tick
     * A/B can temporarily restore the historical endpoint-only method. */
    private boolean diagnosticRailRelativeSweep=true;
    /** Physical lock-time sensitivity is laboratory-only. Normal battles use 1. */
    private double diagnosticPrecisionLockMultiplier=1.0;
    /** Laboratory A/B against duplicate optical allocations. */
    private boolean diagnosticDisablePrecisionReservations;
    /**
     * Strictly opt-in stationary target vessels for local development firing
     * ranges. The vessel remains a regular damageable fitted Ship: only captain
     * decisions (navigation, target selection, retreat, fire and launch) cease.
     * Never enabled for campaign, skirmish or network play.
     */
    private final Set<Integer> diagnosticStationaryTargets=new HashSet<>();

    /** Register only at tick zero: never change a vessel mid-battle. */
    public void registerDiagnosticStationaryTarget(int shipId){
        if(tick!=0)throw new IllegalStateException("Configure diagnostic target before battle ticks");
        Ship target=findShip(shipId);
        if(target==null||!target.alive())
            throw new IllegalArgumentException("Diagnostic target must be a living ship");
        target.vx=0;target.vy=0;
        diagnosticStationaryTargets.add(shipId);
    }
    public boolean isDiagnosticStationaryTarget(int shipId){
        return diagnosticStationaryTargets.contains(shipId);
    }

    public Battle(long initialSeed) { seed = initialSeed; random = new Random(initialSeed); }
    public long seed() { return seed; }
    public SpaceWeather weather() { return weather; }
    /** Only the self-contained, unsaved exhibition simulator enables this.
     * Must be configured before the first combat tick. */
    /** Evaluate density-only PD effects without changing player combat balance.
     * Only callable at tick zero on laboratory battles. Zero is legacy PD. */
    public void configureRocketPdCrowdingForDiagnostic(double pressure){
        if(tick!=0||!Double.isFinite(pressure)||pressure<0||pressure>1)
            throw new IllegalArgumentException("Invalid PD diagnostic configuration");
        diagnosticRocketPdPressure=pressure;
    }
    public void enableUnattendedExhibition(){
        if(tick!=0)throw new IllegalStateException("Set exhibition policy before combat");
        unattendedExhibition=true;
    }
    /** Select weather before step 1; default is CLEAR for backward compatibility. */
    public void configureWeather(SpaceWeather.Preset preset) {
        if (tick != 0) throw new IllegalStateException("Weather must be configured before battle starts");
        weather = new SpaceWeather(preset);
    }
    public List<Ship> ships() { return Collections.unmodifiableList(ships); }
    public List<Missile> missiles() { return Collections.unmodifiableList(missiles); }
    public List<Rocket> rockets(){return Collections.unmodifiableList(rockets);}
    /** Live tactical selection; salvo count and motor do NOT affect other
     * independent launchers, guided missiles, or existing saved blueprints. */
    public void setShipRocketBarrage(int shipId,int slot,int count,RocketBarrage.Motor motor){
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        for(RocketBarrage.Rack rack:ship.rocketRacks)
            if(rack.slot==slot){rack.configure(count,motor);return;}
        throw new IllegalArgumentException("No fitted rocket rack in slot "+slot);
    }
    public int pendingSalvoCount(){return launchQueue.size();}
    public void setShipKineticAmmunition(int id,AmmunitionTable.Kinetic type){
        Ship ship=findShip(id);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        ship.setKineticType(type);
    }
    public void setShipWarhead(int id,DamageType family,AmmunitionTable.Warhead type){
        Ship ship=findShip(id);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        ship.setWarhead(family,type);
    }
    /** Applies to a real fitted ship or to legacy fixture ships. */
    public void setShipLaunchMode(int shipId,DamageType family,OrdnanceDoctrine.Mode mode){
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        ship.setLaunchMode(family,mode);
    }
    public void setShipPropulsionMode(int shipId,DamageType family,
                                      OrdnancePropulsion.Mode mode){
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        ship.setPropulsionMode(family,mode);
    }
    public void setShipGuidanceMode(int shipId,DamageType family,
                                    OrdnanceGuidance.Mode mode){
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship does not exist");
        ship.setGuidanceMode(family,mode);
    }
    public List<Ballistic> ballistics() { return Collections.unmodifiableList(ballistics); }
    /** Live fragments shells, at actual positions before physical fuze arrival. */
    public List<FlakRound> flakRounds(){return Collections.unmodifiableList(pendingFlak);}
    public List<RotaryPointDefense.Round> rotaryRounds(){
        return Collections.unmodifiableList(rotaryRounds);
    }
    public List<RailPointDefense.Penetrator> railPenetrators(){
        return Collections.unmodifiableList(railPenetrators);
    }
    public List<Craft> crafts() { return Collections.unmodifiableList(crafts); }
    /** Actual, independently aging physical decoy emissions. */
    public List<FighterCountermeasures.Cloud> fighterCountermeasures(){
        return Collections.unmodifiableList(fighterClouds);
    }
    public List<Beam> beams() { return Collections.unmodifiableList(beams); }
    public List<SupportLink> supportLinks(){return Collections.unmodifiableList(supportLinks);}
    public List<String> events() { return Collections.unmodifiableList(events); }
    public Ship radioSourceAt(int index) {
        if(index<0||index>=eventSourceIds.size())return null;
        int id=eventSourceIds.get(index);
        return id==0?null:findShip(id);
    }
    /** History allows renderers to catch up when more than one tick elapses per frame. */
    public List<VisualEvent> visualEvents() { return Collections.unmodifiableList(visualEvents); }
    public BattleOrders.Orders teamOrders(int team) {
        if(team<0||team>1)throw new IllegalArgumentException("Invalid team");
        return teamOrders[team];
    }
    public CrewChatter.Tone teamRadioTone(int team){
        if(team<0||team>1)throw new IllegalArgumentException("Invalid radio team");
        return teamRadioTones[team];
    }
    public void setTeamRadioTone(int team,CrewChatter.Tone tone){
        if(team<0||team>1||tone==null)
            throw new IllegalArgumentException("Invalid fleet communications tone");
        teamRadioTones[team]=tone;
    }
    /** Side-effect-free formatter shared by real battle radio and acceptance
     * checks. Fighter voices stay fighter-sized even when launched by capitals. */
    String radioMessage(Ship source,CrewChatter.Event event,boolean fighter){
        if(source==null||event==null)
            throw new IllegalArgumentException("Radio message requires a ship and situation");
        CrewChatter.Tone register=source.squadronRadio==null?
                teamRadioTone(source.team):source.squadronRadio;
        String spoken=fighter?
                HullVoiceChatter.line(diplomacy.shipyard.Shipyard.Hull.INTERCEPTOR,event,tick,source.id,register):
                HullVoiceChatter.line(source.hullClass,event,tick,source.id,register);
        return "RADIO "+source.name+(fighter?" / Flight":
                source.hullClass==diplomacy.shipyard.Shipyard.Hull.FRIGATE_MISSILE?
                        " / Missile Frigate":"")+": "+spoken;
    }
    /**
     * Team-wide edits update existing combatants and future fleet deployments.
     * Later UI can edit individual ship orders with setShipOrders below.
     */
    public void setTeamOrders(int team,BattleOrders.Orders value) {
        if(team<0||team>1||value==null)throw new IllegalArgumentException("Invalid fleet order");
        teamOrders[team]=value;
        // Fleet orders are defaults. Never erase a selected captain's
        // independent standing doctrine when a team policy changes.
        for(Ship ship:ships)if(ship.team==team&&ship.standingOrders==null)ship.orders=value;
    }
    /**
     * Apply only the fields changed in a fleet editor operation. A PD toggle
     * must NOT silently erase bespoke per-ship escort or formation orders.
     */
    public void patchTeamOrders(int team,BattleOrders.Orders revised) {
        if(team<0||team>1||revised==null)throw new IllegalArgumentException("Invalid fleet order");
        BattleOrders.Orders old=teamOrders[team];
        teamOrders[team]=revised;
        for(Ship ship:ships)if(ship.team==team&&ship.standingOrders==null){
            BattleOrders.Orders current=ship.orders;
            if(old.pointDefense()!=revised.pointDefense())
                current=current.withPointDefense(revised.pointDefense());
            if(old.targeting()!=revised.targeting())
                current=current.withTargeting(revised.targeting());
            if(old.position()!=revised.position())
                current=current.withPosition(revised.position());
            for(BattleOrders.Size size:BattleOrders.Size.values()){
                BattleOrders.Engagement before=old.forSize(size),after=revised.forSize(size);
                if(!before.equals(after))
                    current=current.withEngagement(size,after.priority(),after.range());
            }
            if(old.retreatAtHullFraction()!=revised.retreatAtHullFraction())
                current=current.withRetreat(revised.retreatAtHullFraction());
            ship.orders=current;
        }
    }
    /** Replace this vessel's entire ordered tactical program before or during combat. */
    public void setShipCommandQueue(int shipId,List<ShipCommandQueue.Command> commands) {
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship not found");
        ship.commandQueue=new ShipCommandQueue(commands);
    }
    public void setShipStandingOrders(int shipId,ShipStandingOrders profile) {
        if(profile==null)throw new IllegalArgumentException("Standing orders required");
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship not found");
        ship.standingOrders=profile;
        setShipOrders(shipId,profile.tactics());
    }
    /** Runtime bindings for up to four independent defend targets and one escort. */
    public void setShipProtection(int shipId,int escortId,List<Integer> targets){
        Ship ship=findShip(shipId);
        if(ship==null||targets==null||targets.size()>4)
            throw new IllegalArgumentException("Invalid ship defense assignment");
        if(escortId!=0)requireFriendly(ship,escortId);
        java.util.HashSet<Integer> unique=new java.util.HashSet<>();
        for(Integer target:targets) {
            if(target==null||!unique.add(target))
                throw new IllegalArgumentException("Duplicate protected ship");
            requireFriendly(ship,target);
        }
        // A captain cannot form an escort cycle; trace existing leaders first.
        int parent=escortId;
        for(int hops=0;parent!=0&&hops<=ships.size();hops++){
            if(parent==shipId)throw new IllegalArgumentException("Cyclic escort relationship");
            Ship leader=findShip(parent);
            if(leader==null)break;
            parent=leader.escortShipId;
        }
        ship.escortShipId=escortId;
        ship.defendedShipIds=List.copyOf(targets);
    }
    private void requireFriendly(Ship self,int otherId){
        Ship other=findShip(otherId);
        if(other==null||other.team!=self.team||other.id==self.id)
            throw new IllegalArgumentException("Order target must be a distinct friendly ship");
    }
    public void setShipOrders(int shipId,BattleOrders.Orders value) {
        if(value==null)throw new IllegalArgumentException("Orders missing");
        Ship ship=findShip(shipId);
        if(ship==null)throw new IllegalArgumentException("Ship not found");
        if(value.anchorShipId()!=0) {
            Ship parent=findShip(value.anchorShipId());
            if(parent==null||parent.team!=ship.team||parent.id==ship.id)
                throw new IllegalArgumentException("Escort parent must be a different allied ship");
        }
        if(value.anchorShipId()!=0){
            Ship parent=findShip(value.anchorShipId());
            ship.anchorOffsetX=ship.x-parent.x;
            ship.anchorOffsetY=ship.y-parent.y;
        }
        ship.orders=value;
    }
    public void setTeamPointDefense(int team,BattleOrders.PointDefense mode) {
        if(mode==null)throw new IllegalArgumentException("PD mode missing");
        patchTeamOrders(team,teamOrders(team).withPointDefense(mode));
    }
    private void visual(long shotId, VisualPhase phase, String weapon, int team,
                        int sourceId,int targetId,double x,double y,double tx,double ty) {
        visualEvents.add(new VisualEvent(nextVisualSequence++,shotId,tick,phase,weapon,
                team,sourceId,targetId,x,y,tx,ty));
        if(visualEvents.size()>VISUAL_HISTORY_MAX)
            visualEvents.subList(0,visualEvents.size()-VISUAL_HISTORY_MAX).clear();
    }

    /** Atomic preflight prevents partially deployed oversized fleets. */
    private void requireFleetCapacity(int team,int requested){
        checkTeam(team);
        if(requested<0||requested>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Fleet exceeds 200 ships per side");
        int placed=0;
        for(Ship entry:ships)if(entry.team==team)placed++;
        if(placed+requested>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Fleet exceeds 200 ships per side");
    }

    public Ship addShip(int team, Faction faction, double x, double y) {
        if (team != 0 && team != 1) throw new IllegalArgumentException("Only two teams in the skirmish mode");
        if(!BattleArena.contains(x,y))
            throw new IllegalArgumentException("Ship outside fixed combat arena");
        requireFleetCapacity(team,1);
        Ship ship = new Ship(nextId++, team, faction, faction.name().substring(0, 3) + "-" + nextId);
        ship.x = x; ship.y = y; ship.facing = team == 0 ? 0 : Math.PI;
        ship.orders=teamOrders[team];
        ships.add(ship); shipById.put(ship.id,ship); return ship;
    }
    /** Custom hull installations alter real combat stats; invalid fittings never enter battle. */
    private Ship addShip(int team, Shipyard.Design design, Shipyard.Fitted machinery,
                         SupportDoctrine doctrine,TechnologyCatalog catalog,
                         List<RocketBarrage.Rack> rocketTemplates,double x, double y) {
        if (machinery == null || design == null) throw new IllegalArgumentException("A fitted design is required");
        Ship s = addShip(team, design.faction, x, y);
        s.hullClass = design.hull;
        List<Shipyard.Slot> installed=new ArrayList<>();
        Map<Integer,Subsystem> located=new HashMap<>();
        for(Shipyard.Slot slot:HullLayouts.slots(design.faction,design.hull)) {
            Shipyard.Installed component=design.at(slot.index());
            if(component==null)continue;
            installed.add(slot);
            Subsystem system=ShipDamageTargeting.subsystem(component.component);
            if(system!=null)located.put(slot.index(),system);
        }
        s.impactHardpoints=List.copyOf(installed);
        s.weaponEmitters=WeaponMuzzles.equipped(design,HullLayouts.slots(design.faction,design.hull));
        // A real FLK socket is a different weapon from a precision PD laser.
        // The existing module IDs and fitted save data are unchanged.
        List<Ship.FlakMount> fittedFlak=new ArrayList<>();
        List<Ship.PrecisionMount> fittedPrecision=new ArrayList<>();
        List<RotaryPointDefense.Mount> fittedRotary=new ArrayList<>();
        List<RailPointDefense.Mount> fittedRail=new ArrayList<>();
        List<ParticlePointDefense.Mount> fittedParticle=new ArrayList<>();
        s.laserPointDefenseEnabled=false;
        for(WeaponMuzzles.Emitter emitter:s.weaponEmitters){
            if(emitter.component().endsWith("-PD")){
                s.laserPointDefenseEnabled=true;
                s.laserPointDefenseMountCount++;
                fittedPrecision.add(new Ship.PrecisionMount(emitter.slotId(),
                        Shipyard.pointDefenseRangeAt(design,catalog,emitter.slotId()),
                        Shipyard.defenseAt(design,catalog,emitter.slotId()),
                        Shipyard.pointDefenseCycleAt(design,catalog,emitter.slotId())));
            }
            if(emitter.component().endsWith("-FLK"))
                fittedFlak.add(new Ship.FlakMount(emitter.slotId(),
                        Shipyard.pointDefenseRangeAt(design,catalog,emitter.slotId()),
                        Shipyard.defenseAt(design,catalog,emitter.slotId()),
                        Shipyard.pointDefenseCycleAt(design,catalog,emitter.slotId())));
            if(emitter.family()==WeaponMuzzles.Family.ROTARY_CIWS){
                Shipyard.Installed rotaryPart=design.at(emitter.slotId());
                int tier=rotaryPart.branch()==null?0:
                        rotaryPart.stage(rotaryPart.branch());
                fittedRotary.add(new RotaryPointDefense.Mount(emitter.slotId(),tier,
                        Shipyard.pointDefenseRangeAt(design,catalog,emitter.slotId()),
                        Shipyard.defenseAt(design,catalog,emitter.slotId()),
                        Shipyard.pointDefenseCycleAt(design,catalog,emitter.slotId())));
            }
            if(emitter.family()==WeaponMuzzles.Family.PARTICLE_PD){
                Shipyard.Installed particlePart=design.at(emitter.slotId());
                int tier=particlePart.branch()==null?0:
                        particlePart.stage(particlePart.branch());
                fittedParticle.add(new ParticlePointDefense.Mount(emitter.slotId(),tier,
                        Shipyard.pointDefenseRangeAt(design,catalog,emitter.slotId()),
                        Shipyard.defenseAt(design,catalog,emitter.slotId()),
                        Shipyard.pointDefenseCycleAt(design,catalog,emitter.slotId())));
            }
            if(emitter.family()==WeaponMuzzles.Family.RAIL_PD){
                Shipyard.Installed railPart=design.at(emitter.slotId());
                int tier=railPart.branch()==null?0:
                        railPart.stage(railPart.branch());
                fittedRail.add(new RailPointDefense.Mount(emitter.slotId(),tier,
                        Shipyard.pointDefenseRangeAt(design,catalog,emitter.slotId()),
                        Shipyard.defenseAt(design,catalog,emitter.slotId()),
                        Shipyard.pointDefenseCycleAt(design,catalog,emitter.slotId())));
            }
        }
        s.flakMounts=List.copyOf(fittedFlak);
        s.precisionMounts=List.copyOf(fittedPrecision);
        s.rotaryMounts=List.copyOf(fittedRotary);
        s.railMounts=List.copyOf(fittedRail);
        s.particlePdMounts=List.copyOf(fittedParticle);
        if(!rocketTemplates.isEmpty()){
            List<RocketBarrage.Rack> fittedRacks=new ArrayList<>(rocketTemplates.size());
            for(RocketBarrage.Rack prototype:rocketTemplates)
                fittedRacks.add(new RocketBarrage.Rack(prototype));
            s.rocketRacks=List.copyOf(fittedRacks);
        }
        s.laserFittings=LaserFamilies.fitted(design);
        s.installedSubsystems=Map.copyOf(located);
        int strongestShieldRank=0;
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed part=design.at(i);
            if(part==null||!part.component.endsWith("-SHD")||part.branch()==null)continue;
            int rank=part.stage(part.branch());
            if(rank>strongestShieldRank){
                strongestShieldRank=rank;
                s.shieldFieldBranch=part.branch();
            }
        }
        s.fighterProfile=FighterModel.forFaction(design.fighterHull,design.faction);
        // An actual installed FTR socket supplies the upgrade; organic
        // carrier defaults retain the baseline expendable magazines.
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed part=design.at(i);
            if(part!=null&&part.component.endsWith("-FTR")&&part.branch()!=null)
                s.fighterCountermeasureTier=StrictMath.max(
                        s.fighterCountermeasureTier,part.stage(part.branch()));
        }
        s.fittedCrewSystems=true;
        s.crewRequired=machinery.crew().minimum();
        s.crewBerths=machinery.crew().berths();
        s.lifeSupport=machinery.crew().lifeSupport();
        s.crewCount=Math.min(s.crewBerths,s.lifeSupport);
        // Engine fuel and physical stores belong to the chassis; an empty
        // unarmed vessel can still maneuver and replenish at port.
        HullBaseline.CoreSystems core=HullBaseline.core(design.hull);
        s.maxFuel=core.fuel();s.fuel=s.maxFuel;
        s.maxHull = machinery.hull(); s.hull = s.maxHull;
        s.sensorRange=machinery.baseline().sensorRange()*machinery.sensing().radarGain();
        s.radarCrossSection=machinery.baseline().radarCrossSection()
                *machinery.sensing().radarSignature();
        s.infraredRange=machinery.sensing().infraredRange();
        s.xrayRange=machinery.sensing().xrayRange();
        s.graviticRange=machinery.sensing().graviticRange();
        s.thermalSignature=machinery.sensing().thermalSignature();
        s.opticalSignature=machinery.sensing().opticalSignature();
        s.sensorAcuity=machinery.sensing().acuity();
        s.hologramNoise=machinery.sensing().hologramNoise();
        s.network=machinery.network();
        s.crewProtection=machinery.survival().crewProtection();
        s.commandProtection=machinery.survival().commandProtection();
        s.fuelProtection=machinery.survival().fuelProtection();
        s.magazineContainment=machinery.survival().magazineContainment();
        s.escapePods=machinery.survival().escapePods();
        s.recoveryBeacon=machinery.survival().recoveryBeacon();
        s.recoveryStrength=machinery.survival().recoveryStrength();
        s.hazardInstability=machinery.survival().instability();
        s.hazardSeverity=machinery.survival().hazardSeverity();
        s.unsafeFlags=machinery.survival().unsafeFlags();
        s.maxShields = machinery.shields(); s.shields = s.maxShields;
        s.maxArmor = machinery.armor(); s.armor = s.maxArmor;
        s.sif = machinery.sif();
        s.defense = new DirectionalDefense(s.maxShields,s.maxArmor,s.maxHull);
        Shipyard.applyArmorMaterials(design,catalog,s.defense);
        if(s.maxShields>0)
            for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values())
                s.defense.configureRecharge(face,
                        1.25+machinery.baseline().shieldRecharge()/4
                                +machinery.shieldRechargeBoost()/4,2);
        s.maxPower = machinery.maxPower(); s.power = s.maxPower;
        s.maxHeat = machinery.maxHeat();
        // Defensive rail capacitors are charged before a warship deploys:
        // their energy and startup heat are debited from the real ship
        // stores ONCE, as ammunition is preloaded before launch. If the
        // reactor reserve cannot support a full charge, do not invent one.
        for(RailPointDefense.Mount gun:s.railMounts){
            double loaded=StrictMath.min(1,StrictMath.max(0,s.power)/1.8);
            gun.charge=loaded;
            s.power-=loaded*1.8;
            s.heat+=loaded*.45;
        }
        s.powerRegen = machinery.powerRegen();
        s.coolingRate = machinery.coolingRate(); s.passiveHeat = machinery.passiveHeat();
        s.speed = machinery.speed(); s.accuracy = machinery.accuracy();
        s.propulsion=SublightPropulsion.fitted(design);
        s.laserDamage = machinery.laserDamage(); s.missileDamage = machinery.missileDamage();
        s.pdRange = machinery.pdRange(); s.damageControl = machinery.damageControl();
        s.kineticProjectile=machinery.kineticProjectile();
        s.missileProjectile=machinery.missileProjectile();
        s.torpedoProjectile=machinery.torpedoProjectile();
        s.pointDefenseProfile=machinery.pointDefense();
        s.pdCycle=machinery.pdCycle();
        s.weapons=machinery.weapons();
        s.ammoAccess=machinery.ammunition();
        s.missileOrdnanceTier=OrdnanceFitting.fittedTier(design,DamageType.MISSILE);
        s.torpedoOrdnanceTier=OrdnanceFitting.fittedTier(design,DamageType.TORPEDO);
        for(DamageType family:new DamageType[]{DamageType.MISSILE,DamageType.TORPEDO}){
            OrdnanceFitting.Guided guided=design.ordnance(family);
            s.setLaunchMode(family,guided.salvo());
            s.setPropulsionMode(family,guided.motor());
            s.setGuidanceMode(family,guided.guidance());
        }
        // A fleet blueprint owns the chosen, research-licensed physical loads.
        // Stock ammo remains the exact default for legacy CD-SHIP-1/2 fleets.
        s.setKineticType(design.ammoSelection.kinetic());
        s.setWarhead(DamageType.MISSILE,design.ammoSelection.missile());
        s.setWarhead(DamageType.TORPEDO,design.ammoSelection.torpedo());
        s.support=machinery.support();
        s.logistics=doctrine;
        s.tenders=Shipyard.tenderDoctrine(design,catalog);
        for(SupportDoctrine.Channel channel:SupportDoctrine.Channel.values())
            s.serviceDroneReserve[channel.ordinal()]=s.logistics.droneCapacity(channel);
        s.kineticDamage=machinery.kineticDamage();s.plasmaDamage=machinery.plasmaDamage();
        s.particleDamage=machinery.particleDamage();s.torpedoDamage=machinery.torpedoDamage();
        s.ecmStrength=machinery.ecmStrength();
        s.fighterReserve=machinery.fighterCapacity();
        s.fighterBays=Ship.baysFor(design);
        s.fighterLaunchBays=s.fighterBays.size();
        s.droneReserve=machinery.droneCapacity();
        s.repairDroneReserve=machinery.support().repairDroneCapacity();
        s.maxKineticAmmo=core.kineticStores()+machinery.magazineCapacity()*4
                +(int)StrictMath.floor(s.tenders.capacity(TenderDoctrine.Cargo.KINETIC));
        s.maxMissileAmmo=core.missileStores()+machinery.magazineCapacity()
                +(int)StrictMath.floor(s.tenders.capacity(TenderDoctrine.Cargo.MISSILE));
        s.maxTorpedoAmmo=core.torpedoStores()+machinery.magazineCapacity()/3
                +(int)StrictMath.floor(s.tenders.capacity(TenderDoctrine.Cargo.TORPEDO));
        s.maxFuel+=s.tenders.capacity(TenderDoctrine.Cargo.FUEL)
                +machinery.survival().fuelBonus();
        s.fuel=s.maxFuel;
        s.kineticAmmo=s.maxKineticAmmo;
        s.missileAmmo=s.maxMissileAmmo;
        s.torpedoAmmo=s.maxTorpedoAmmo;
        if(!design.launcherFittings.isEmpty()){
            List<Ship.Launcher> mounted=new ArrayList<>();
            for(DamageType family:new DamageType[]{DamageType.MISSILE,DamageType.TORPEDO}){
                List<Integer> slots=new ArrayList<>();
                for(int i=0;i<design.slots();i++){
                    Shipyard.Installed part=design.at(i);
                    if(part!=null&&part.component.endsWith(OrdnanceFitting.suffix(family)))
                        slots.add(i);
                }
                int rounds=family==DamageType.MISSILE?s.missileAmmo:s.torpedoAmmo;
                for(int i=0;i<slots.size();i++){
                    int slot=slots.get(i);
                    mounted.add(new Ship.Launcher(slot,family,design.launcher(slot),
                            rounds/slots.size()+(i<rounds%slots.size()?1:0)));
                }
            }
            s.launchers=List.copyOf(mounted);
        }
        return s;
    }
    public void spawnWing(int team, Shipyard.Design design, TechnologyCatalog catalog,
                          int count, double centerX, double centerY) {
        requireFleetCapacity(team,count);
        Shipyard.Fitted machinery = Shipyard.fit(design, catalog);
        SupportDoctrine doctrine=Shipyard.supportDoctrine(design,catalog);
        List<RocketBarrage.Rack> rocketTemplates=Shipyard.rocketRacks(design,catalog);
        for (int i=0;i<count;i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team,design,machinery,doctrine,catalog,
                    rocketTemplates,point[0],point[1]);
        }
        log(design.faction.displayName+" deployed "+count+" fitted "+design.hull+"s");
    }
    /** Different fitted vessels may share a battle without losing their
     * individual technology, ammunition, hull class or installed systems. */
    public void spawnFleet(int team,List<Shipyard.Design> designs,TechnologyCatalog catalog,
                           double centerX,double centerY){
        if(designs==null||designs.isEmpty()||
                designs.size()>BattleArena.MAX_SHIPS_PER_SIDE||catalog==null||
                designs.stream().anyMatch(d->d==null))
            throw new IllegalArgumentException("Invalid custom fleet");
        requireFleetCapacity(team,designs.size());
        List<Shipyard.Fitted> fittings=new ArrayList<>(designs.size());
        List<SupportDoctrine> doctrines=new ArrayList<>(designs.size());
        List<List<RocketBarrage.Rack>> rocketsByHull=new ArrayList<>(designs.size());
        for(Shipyard.Design design:designs){
            fittings.add(Shipyard.fit(design,catalog));
            doctrines.add(Shipyard.supportDoctrine(design,catalog));
            rocketsByHull.add(Shipyard.rocketRacks(design,catalog));
        }
        for(int i=0;i<designs.size();i++){
            double[] point=formationPosition(i,designs.size(),centerX,centerY,random);
            addShip(team,designs.get(i),fittings.get(i),doctrines.get(i),catalog,
                    rocketsByHull.get(i),point[0],point[1]);
        }
        log("Team "+team+" deployed "+designs.size()+" individual engineering configurations");
    }
    public static Battle duel(long seed,List<Shipyard.Design> left,
                              List<Shipyard.Design> right,TechnologyCatalog catalog){
        if(left==null||right==null||left.isEmpty()||right.isEmpty()||
                left.size()>BattleArena.MAX_SHIPS_PER_SIDE||
                right.size()>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Both custom fleets must contain 1-200 vessels");
        Battle battle=new Battle(seed);
        battle.spawnFleet(0,left,catalog,310,335);
        battle.spawnFleet(1,right,catalog,970,335);
        return battle;
    }
    public static Battle duel(long seed, int shipsPerSide, Shipyard.Design left,
                              Shipyard.Design right, TechnologyCatalog catalog) {
        if(shipsPerSide<0||shipsPerSide>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Fleet exceeds 200 ships per side");
        Battle battle = new Battle(seed);
        battle.spawnWing(0,left,catalog,shipsPerSide,310,335);
        battle.spawnWing(1,right,catalog,shipsPerSide,970,335);
        return battle;
    }
    public void spawnWing(int team, Faction faction, int count, double centerX, double centerY) {
        requireFleetCapacity(team,count);
        for (int i = 0; i < count; i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team, faction, point[0], point[1]);
        }
        log(faction.displayName + " deployed " + count + " combatants");
    }
    public static Battle duel(long seed, int shipsPerSide, Faction left, Faction right) {
        if(shipsPerSide<0||shipsPerSide>BattleArena.MAX_SHIPS_PER_SIDE)
            throw new IllegalArgumentException("Fleet exceeds 200 ships per side");
        Battle battle = new Battle(seed);
        battle.spawnWing(0, left, shipsPerSide, 310, 335);
        battle.spawnWing(1, right, shipsPerSide, 970, 335);
        return battle;
    }
    /** Formation coordinates are bounded for 1–200 ships per side within a 1280×720 view.
     * Wider fleets compress their spacing instead of losing entire flanks off-screen.
     * The injected Random keeps old deterministic seed behavior. */
    private static double[] formationPosition(int index, int count, double cx, double cy, Random rng) {
        int columns = Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(count)));
        int rows = (count + columns - 1) / columns;
        double spacingX = Math.min(31, 440.0 / Math.max(1, columns-1));
        double spacingY = Math.min(41, 495.0 / Math.max(1, rows-1));
        double jitter = Math.min(2.0, Math.min(spacingX, spacingY) * 0.07);
        double x = cx + ((index % columns) - (columns - 1) / 2.0) * spacingX
                + (rng.nextDouble() - 0.5) * jitter;
        double y = cy + ((index / columns) - (rows - 1) / 2.0) * spacingY
                + (rng.nextDouble() - 0.5) * jitter;
        return new double[]{x,y};
    }
    public int alive(int team) { int count = 0; for (Ship s : ships) if (s.team == team && s.alive()) count++; return count; }
    /** Active captains still committed to holding the field. Withdrawing but
     * not-yet-escaped hulls cannot receive survivor rewards. */
    public int engaged(int team){
        checkTeam(team);
        int count=0;
        for(Ship ship:ships)if(ship.team==team&&ship.alive()&&!ship.withdrawing)count++;
        return count;
    }
    public int withdrawing(int team){
        checkTeam(team);
        int count=0;
        for(Ship ship:ships)if(ship.team==team&&ship.alive()&&ship.withdrawing)count++;
        return count;
    }
    /** Successfully escaped, still-intact hulls; not active mission survivors. */
    public int escaped(int team) {
        checkTeam(team);
        int count=0;
        for(Ship ship:ships)if(ship.team==team&&ship.escaped)count++;
        return count;
    }
    /** Hulls lost to actual destruction, excluding escaped vessels. */
    public int destroyed(int team) {
        checkTeam(team);
        int count=0;
        for(Ship ship:ships)if(ship.team==team&&ship.hull<=0)count++;
        return count;
    }
    private static void checkTeam(int team) {
        if(team!=0&&team!=1)throw new IllegalArgumentException("Invalid fleet side");
    }
    /** A mutual withdrawal or actual hull elimination still resolves normally.
     * Stalemates retain every physically surviving ship, not an invented loss. */
    public boolean finished() {
        return unresolved()||alive(0)==0||alive(1)==0;
    }

    public void step() {
        if(unresolved())return;
        tick++;
        // All active vessels enter contact and environmental simulation in
        // the same immutable world, including externally issued coordinates.
        containFleet();
        if(tick==1)captureSensorHistory(0);
        focusCounts.clear();
        for(Ship ship:ships)if(ship.alive()&&ship.lastTargetId!=0)
            focusCounts.merge(ship.lastTargetId,1,Integer::sum);
        for (int i = beams.size() - 1; i >= 0; i--) {
            Beam visual=beams.get(i);
            if ((visual.lifetime-=DT)<=0&&!visual.inFlight)beams.remove(i);
        }
        for(int i=supportLinks.size()-1;i>=0;i--)
            if((supportLinks.get(i).life-=DT)<=0)supportLinks.remove(i);
        // Environment is deterministic, separately seeded, and uses the same damage rules.
        for (SpaceWeather.Impact effect : weather.advance(tick, ships)) {
            Ship victim = findShip(effect.targetId());
            if (victim == null || !victim.alive()) continue;
            DamageType kind = switch (effect.type()) {
                case KINETIC -> DamageType.KINETIC;
                case PARTICLE -> DamageType.PARTICLE;
                case PLASMA -> DamageType.PLASMA;
            };
            hitAt(victim, effect.damage(), null, kind, effect.fromX(), effect.fromY());
            environmentalHits++;
        }
        // Pulses spend actual reactor power and heat at their emission tick;
        // incoming echoes can only later appear in causal sensor history.
        for(Ship scanner:ships)SensorActiveScan.advance(scanner,tick);
        refreshCommandAssistance();
        refreshSharedContacts();
        broadcastTrackPackets();
        for (Ship s : ships) if (s.alive()) {
            replenish(s);
            if(!s.alive())continue;
            if(diagnosticStationaryTargets.contains(s.id)){
                // NO AI: skip captain/commands/retreat/fire/launch entirely.
                // Keep real shields, armor, power, environmental effects,
                // projectile collision, penetration and hull mortality.
                s.vx=0;s.vy=0;
                continue;
            }
            // A lone, unarmed exhibition captain with viable engines has no
            // remaining offensive mission. Order a genuine physical extraction
            // instead of leaving two opposing fleets alive indefinitely.
            // No damage, simulated kills, teleport or invented terminal state.
            if(unattendedExhibition && lastUnarmedExhibitionShip(s)){
                s.withdrawing=true;
                s.withdrawalTick=tick;
                log(s.name+" disengaging: last ship has no offensive ordnance");
                radio(s,CrewChatter.Event.RETREAT);
            }
            // Retreat overrides waypoint queues and navigation doctrines.
            // Recharge/repair cannot reverse a committed escape under fire.
            // CAUTIOUS's hull threshold is an actual withdraw order, not an
            // endless powered flight away from the enemy until fuel runs dry.
            // Once committed, use the same real rear-boundary extraction as
            // explicit captain standing orders; it never fabricates an escape.
            boolean cautiousRetreat=s.orders.position()==BattleOrders.Position.CAUTIOUS
                    &&s.hull<s.maxHull*s.orders.retreatAtHullFraction();
            if(s.withdrawing||cautiousRetreat||
                    (s.standingOrders!=null&&s.standingOrders.mustWithdraw(s))) {
                if(!s.withdrawing) {
                    s.withdrawing=true;
                    s.withdrawalTick=tick;
                    log(s.name+" withdrawing from battle");
                    radio(s,CrewChatter.Event.RETREAT);
                }
                steerRetreat(s);
                if(s.alive())launchAuxiliary(s,null);
                continue;
            }
            Ship target = chooseTarget(s);
            boolean navigated=executeShipCommands(s,target);
            if (target != null) {
                if(!navigated)steer(s, target);
                if(s.standingOrders==null||!s.standingOrders.mustWithdraw(s)){
                    int before=visualEvents.size();
                    fire(s, target);
                    if(visualEvents.size()>before){
                        boolean guard=s.defendedShipIds.contains(target.lastTargetId)||
                                target.id==s.lastAttackerId;
                        radio(s,guard?CrewChatter.Event.COVER:CrewChatter.Event.ATTACK);
                    }
                }
            } else if(!navigated) {
                searchForContact(s);
            }
            launchAuxiliary(s,target);
        }
        // Pursuit, inertia, patrol and queued waypoint movement never grow
        // the simulation; preserve existing rear-line extraction behavior.
        containFleet();
        updateSupport();
        updateTenderSupport();
        updateQueuedLaunches();
        updateRocketLaunches();
        // Snapshot real physical bodies before advancing all weapons.
        // Diagnostic swept relative intersections need BOTH endpoints, not
        // only a fast incoming body's end-of-tick position.
        for(Missile body:missiles){body.previousX=body.x;body.previousY=body.y;}
        for(Rocket body:rockets){body.previousX=body.x;body.previousY=body.y;}
        for(Ballistic body:ballistics){body.previousX=body.x;body.previousY=body.y;}
        updateMissiles();
        updateRockets();
        updateBallistics();
        for(Craft craft:crafts){
            craft.lastObservedX=craft.x;
            craft.lastObservedY=craft.y;
        }
        updateCraft();
        for(Craft craft:crafts)BattleArena.contain(craft);
        advanceRotaryRounds();
        advanceRailPenetrators();
        advanceBeamPhotons();
        for(Craft craft:crafts){
            craft.observedVx=(craft.x-craft.lastObservedX)/DT;
            craft.observedVy=(craft.y-craft.lastObservedY)/DT;
        }
        captureSensorHistory(tick);
        updateTermination();
    }

    /** No quantized wall clock or random result: only observed battle ticks,
     * real hit/kill/escape progress, and live physical threats can end a fight.
     * Expensive capability inspection runs once per 30 simulation ticks. */
    private void updateTermination(){
        int escapes=escaped(0)+escaped(1);
        if(hitCount!=lastObservedHits||destructions!=lastObservedDestructions
                ||escapes!=lastObservedEscapes){
            lastCombatProgressTick=tick;
            lastObservedHits=hitCount;
            lastObservedDestructions=destructions;
            lastObservedEscapes=escapes;
        }
        if(alive(0)==0||alive(1)==0||tick%30!=0)return;
        boolean movingExtraction=false,anyOffense=false;
        for(Ship ship:ships)if(ship.alive()){
            if(ship.withdrawing && (ship.fuel>0&&ship.engineCondition>.1
                    ||StrictMath.hypot(ship.vx,ship.vy)>.05))
                movingExtraction=true;
            if(!anyOffense && retainsOffensiveCapability(ship))
                anyOffense=true;
        }
        boolean inFlight=!missiles.isEmpty()||!rockets.isEmpty()
                ||!ballistics.isEmpty()||!crafts.isEmpty()||!launchQueue.isEmpty()
                ||!pendingFlak.isEmpty()||!rotaryRounds.isEmpty()
                ||!railPenetrators.isEmpty()||!pendingBeamPhotons.isEmpty();
        terminationReason=BattleTermination.evaluate(tick,lastCombatProgressTick,
                !anyOffense,inFlight,movingExtraction);
        if(unresolved())log("Engagement unresolved: "+
                (terminationReason==BattleTermination.Reason.STALEMATE?
                        "both fleets remain but neither can secure a result":
                        "maximum finite battle time reached")+
                " (no victory or fabricated hull losses)");
    }
    /** Conservative: when hardware might ever rearm or resume, wait for the
     * long no-progress policy. Repair drones and tenders are NOT free weapons,
     * but active logistics could still replenish a genuinely armed ally. */
    private boolean retainsOffensiveCapability(Ship ship){
        if(ship.withdrawing||diagnosticStationaryTargets.contains(ship.id))
            return false;
        if(ship.laserDamage>0||ship.plasmaDamage>0||ship.particleDamage>0
                ||ship.kineticDamage>0&&ship.kineticAmmo>0
                ||ship.missileDamage>0&&ship.missileAmmo>0
                ||ship.torpedoDamage>0&&ship.torpedoAmmo>0
                ||ship.fighterReserve>0&&ship.fighterLaunchBays>0
                ||ship.droneReserve>0)return true;
        for(RocketBarrage.Rack rack:ship.rocketRacks)
            if(ship.kineticAmmo>0&&(rack.rounds>0||rack.salvoRemaining>0))
                return true;
        // Fuel and ammunition can be transferred by actual living tenders;
        // don't declare a quick irreversible disarmament while rearm exists.
        return ship.tenders!=TenderDoctrine.NONE||
                ship.logistics!=SupportDoctrine.NONE;
    }

    private void containFleet(){
        for(Ship ship:ships)BattleArena.contain(ship);
    }

    /** Voluntary disengagement is limited to a last armed-force survivor of a
     * screensaver fleet. All ammunition and active attack craft count. Pure
     * support ships remain in action while escorted allies still fight. */
    private boolean lastUnarmedExhibitionShip(Ship candidate) {
        if(candidate.withdrawing||candidate.fuel<=0||
                candidate.engineCondition<=0.1||candidate.crewReadiness()<0.05)
            return false;
        // A deliberate last stand remains an explicit order even when depleted.
        if(candidate.orders.position()==BattleOrders.Position.LAST_STAND)
            return false;
        for(Ship ally:ships)
            if(ally!=candidate && ally.team==candidate.team && ally.alive()
                    &&!ally.withdrawing)return false;
        if(candidate.weaponsCondition>0.05){
            if(candidate.laserDamage>0||candidate.plasmaDamage>0||
                    candidate.particleDamage>0||
                    (candidate.kineticDamage>0&&candidate.kineticAmmo>0)||
                    (candidate.missileDamage>0&&candidate.missileAmmo>0)||
                    (candidate.torpedoDamage>0&&candidate.torpedoAmmo>0))
                return false;
        }
        if(candidate.weaponsCondition>.05)
            for(RocketBarrage.Rack rack:candidate.rocketRacks)
                if((rack.salvoRemaining>0||rack.canStart()) &&
                        candidate.kineticAmmo>=rack.cheapAmmoUnitsPerRocket)
                    return false;
        if(candidate.fighterReserve>0&&candidate.fighterLaunchBays>0||
                candidate.droneReserve>0)return false;
        for(Craft craft:crafts)
            if(craft.sourceId==candidate.id &&!craft.repair)return false;
        for(Missile missile:missiles)
            if(missile.sourceId==candidate.id)return false;
        for(Rocket rocket:rockets)
            if(rocket.sourceId==candidate.id)return false;
        for(Ballistic shell:ballistics)
            if(shell.sourceId==candidate.id)return false;
        return true;
    }

    /**
     * A fog-blinded captain searches the finite combat theater instead of
     * accelerating blindly down its team's original heading and leaving the
     * battle by tens of thousands of units. No enemy identity or position is
     * leaked: each ship patrols an expanding, staggered sector until its own
     * real sensors reacquire a target. HOLD/LAST_STAND ships remain stationary;
     * mobile ships sweep broader sectors after prolonged loss of contact.
     */
    /** Prefer recent, nearby ACTUALLY OBSERVED fixes. Even an advanced
     * datalink exports only its finite highest-priority track records. */
    private static double contactSearchScore(Ship observer,ContactTrack track,long now){
        double age=StrictMath.max(0,now-track.tick);
        double distance=StrictMath.hypot(track.x-observer.x,track.y-observer.y);
        return age*3+distance*.045;
    }
    ContactTrack newestValidTrack(Ship observer){
        if(observer.power<=3||observer.sensorsCondition<=.05
                ||observer.crewReadiness()<.05)return null;
        ContactTrack best=null;
        double bestScore=Double.POSITIVE_INFINITY;
        int localWindow=(int)(observer.network.trackMemoryTicks()
                *StrictMath.min(1,observer.sensorsCondition));
        if(localWindow>0)for(ContactTrack track:observer.knownTracks.values()){
            long age=tick-track.tick;
            if(age<0||age>localWindow)continue;
            double score=contactSearchScore(observer,track,tick);
            if(score<bestScore){best=track;bestScore=score;}
        }
        // LNK receives only immutable packets that were actually sent by
        // powered transmitters, after the physical inter-ship propagation.
        // Historical tracks help search, but NEVER authorize weapon fire.
        if(observer.network.trackRelayRange()>0){
            for(Ship source:ships){
                if(source==observer||source.team!=observer.team||!source.alive()
                        ||source.power<=3||source.sensorsCondition<=.05||
                        source.crewReadiness()<.05||
                        source.network.trackRelayCapacity()<=0||
                        source.network.trackMemoryTicks()<=0)continue;
                double range=StrictMath.min(observer.network.trackRelayRange(),
                        source.network.trackRelayRange())*
                        StrictMath.min(source.sensorsCondition,
                                observer.sensorsCondition);
                if(range<=0||StrictMath.hypot(source.x-observer.x,
                        source.y-observer.y)>range)continue;
                int cap=StrictMath.min(SensorTrackRelayBuffer.MAX_CONTACTS,
                        source.network.trackRelayCapacity());
                int window=(int)(source.network.trackMemoryTicks()*
                        StrictMath.min(1,source.sensorsCondition));
                for(SensorTrackRelayBuffer.Packet packet:
                        source.trackPackets.deliveredAll(tick,observer.x,
                                observer.y,cap)){
                    long age=tick-packet.observedTick();
                    if(age<0||age>window)continue;
                    ContactTrack track=trackFromPacket(packet);
                    double score=contactSearchScore(observer,track,tick)+10;
                    if(score<bestScore){best=track;bestScore=score;}
                }
            }
        }
        return best;
    }

    private void searchForContact(Ship ship) {
        // Advance patrol waypoints on simulation ticks, not frame time or
        // hidden enemy coordinates. Both fleets search overlapping lanes;
        // otherwise impaired-sensor ships can park forever in disjoint posts.
        long phaseX=ship.id*7L+tick/240L;
        long phaseY=ship.id*11L+tick/360L;
        double rallyX=480+(phaseX%6)*65;
        double rallyY=145+(phaseY%7)*75;
        if(tick>6000){
            // Any autonomous tactical fleet must search beyond its starting
            // rally box. This remains a non-omniscient deterministic patrol,
            // not a read of hidden hostile coordinates.
            // A long, genuine loss of contact calls for a wider search,
            // not perpetual orbiting of the initial 325-unit rally strip.
            // Each physical captain patrols an independent, bounded lane;
            // enemy positions are never consulted to choose destinations.
            // Twenty/thirty-second legs give engines time to actually arrive.
            double expansion=StrictMath.min(4.0,1+(tick-6000)/4200.0);
            long legX=ship.id*7L+tick/600L;
            long legY=ship.id*11L+tick/900L;
            rallyX=640+((legX%9)-4)*145*expansion;
            rallyY=565+((legY%7)-3)*140*expansion;
        }
        ContactTrack lastFix=newestValidTrack(ship);
        if(lastFix!=null){
            // Dead reckoning only from OBSERVED velocity; do not read a
            // presently hidden target's true coordinates or heading.
            double age=StrictMath.min(1.5,(tick-lastFix.tick)*DT);
            rallyX=lastFix.x+lastFix.vx*age;
            rallyY=lastFix.y+lastFix.vy*age;
        }
        boolean hold=ship.orders.position()==BattleOrders.Position.HOLD
                ||ship.orders.position()==BattleOrders.Position.LAST_STAND;
        boolean thrust=!hold && ship.fuel>0 && ship.engineCondition>0.01
                && ship.crewReadiness()>0 &&
                StrictMath.hypot(rallyX-ship.x,rallyY-ship.y)>45;
        if(thrust) {
            steerToward(ship,rallyX,rallyY);
        } else {
            // Braking is powered thrust, never free drag in vacuum.
            // A depleted hull coasts on the last real momentum.
            SublightPropulsion.advance(ship,0,0,DT);
        }
    }

    /** Unsafe hardware only: deterministic per-ship risk, heat and crew consequences.
     * Does not use the global combat random stream. */
    private void checkUnsafeEngineering(Ship s) {
        if(s.hazardInstability<=0||tick%30!=0)return;
        double stress=StrictMath.max(0,s.heat/StrictMath.max(1,s.maxHeat));
        long noise=seed^(((long)s.id)<<32)^(tick*0x9e3779b97f4a7c15L);
        noise^=noise>>>33;noise*=0xff51afd7ed558ccdL;
        noise^=noise>>>33;noise*=0xc4ceb9fe1a85ec53L;noise^=noise>>>33;
        double roll=(noise>>>11)*0x1.0p-53;
        if(roll>=StrictMath.min(.68,s.hazardInstability*(.014+.082*stress)))return;
        double severity=StrictMath.max(.1,s.hazardSeverity);
        s.hazardFailures++;
        s.lastHazardTick=tick;
        // Choose one real installed illegal assembly, not an identical
        // generic malfunction for every engineering design. All branches use
        // the existing deterministic fault roll; no per-frame RNG or extras.
        int installed=Integer.bitCount(s.unsafeFlags);
        int selection=installed==0?0:Math.floorMod((int)(noise>>>32),installed);
        int origin=0;
        for(int flag=1;flag<=SurvivalHardwareCatalog.REACTOR_TAP;flag<<=1)
            if((s.unsafeFlags&flag)!=0 && selection--==0){origin=flag;break;}
        s.lastFaultOrigin=origin;
        String fault;
        switch(origin){
            case SurvivalHardwareCatalog.GOVERNOR -> {
                s.power=StrictMath.max(0,s.power-18*severity);
                s.heat+=12*severity;
                s.reactorCondition=StrictMath.max(0,s.reactorCondition-.025*severity);
                fault="governor override tripped reactor output";
            }
            case SurvivalHardwareCatalog.PLASMA_CELL -> {
                s.power=StrictMath.max(0,s.power-30*severity);
                s.heat+=25*severity;
                s.reactorCondition=StrictMath.max(0,s.reactorCondition-.09*severity);
                s.hull-=8*severity;
                if(s.fittedCrewSystems)
                    s.crewCount=StrictMath.max(0,s.crewCount-2.0*severity*
                            (1-s.crewProtection));
                fault="jailbroken plasma cell lost confinement";
            }
            case SurvivalHardwareCatalog.BATTERY_BRICK -> {
                s.power=StrictMath.max(0,s.power-24*severity);
                s.heat+=10*severity;
                s.sensorsCondition=StrictMath.max(0,s.sensorsCondition-.24*severity);
                s.commandCondition=StrictMath.max(0,s.commandCondition-
                        .29*severity*(1-s.commandProtection));
                fault="illegal battery brick overloaded communications";
            }
            case SurvivalHardwareCatalog.THERMAL_SHUNT -> {
                s.coolingCondition=StrictMath.max(0,s.coolingCondition-.19*severity);
                s.heat+=26*severity;
                s.hull-=4.0*severity;
                fault="improvised thermal shunt burned out cooling manifold";
            }
            case SurvivalHardwareCatalog.REACTOR_TAP -> {
                s.reactorCondition=StrictMath.max(0,s.reactorCondition-.23*severity);
                s.power=StrictMath.max(0,s.power-38*severity);
                s.heat+=30*severity;
                s.hull-=11*severity;
                if(s.fittedCrewSystems)
                    s.crewCount=StrictMath.max(0,s.crewCount-3*severity*
                            (1-s.crewProtection));
                fault="unlicensed reactor tap breached containment";
            }
            default -> {
                s.power=StrictMath.max(0,s.power-10*severity);
                s.heat+=6*severity;
                fault="unsafe power retrofit suffered internal fault";
            }
        }
        // No outer shield can suppress an already-internal hardware failure.
        s.hull-=StrictMath.min(s.maxHull*.06,1.8*severity);
        log(s.name+" "+fault);
        if(s.hull<=0)destroyHull(s,null);
    }

    /** A locator is worth salvage ONLY during a won postbattle recovery.
     * Destroyed hulls never return to the roster, and no in-battle credit
     * appears merely because a beacon flashes on the tactical screen. */
    public long recoverySalvageCredits(int friendlyTeam) {
        if(friendlyTeam<0||friendlyTeam>1)
            throw new IllegalArgumentException("Recovery side must be a real fleet");
        if(alive(1-friendlyTeam)>0||engaged(friendlyTeam)<=0)return 0;
        long salvaged=0;
        for(Ship wreck:ships){
            if(wreck.team!=friendlyTeam||!wreck.retired||
                    !wreck.recoveryBeaconActivated||wreck.hullClass==null)continue;
            // Modest hull-only scrap value, NOT free replacement modules, crew,
            // fuel or ammunition. Victory is the real recovery operation.
            long scrap=Math.min(8000L,
                    Math.round(wreck.hullClass.baseHull*4.0*
                            Math.max(0,Math.min(1.75,wreck.recoveryStrength))));
            salvaged=Math.addExact(salvaged,scrap);
        }
        return salvaged;
    }

    /** No random stock destruction, phantom refills or global armor bypass.
     * Real armor/shield resolution has already supplied penetratingDamage. */
    static void breachStores(Ship ship,DamageType kind,double penetratingDamage,
                             boolean magazineImpact){
        if(ship==null||kind==null||penetratingDamage<=6)return;
        double wound=StrictMath.min(.25,penetratingDamage/
                StrictMath.max(1,ship.maxHull)*(magazineImpact?.65:.30));
        ship.fuel=StrictMath.max(0,ship.fuel-
                ship.maxFuel*wound*(1-ship.fuelProtection));
        double exposed=wound*(1-ship.magazineContainment);
        double kinetic=ship.kineticStoreExposure+ship.maxKineticAmmo*exposed*.42;
        double missile=ship.missileStoreExposure+ship.maxMissileAmmo*exposed*.75;
        double torpedo=ship.torpedoStoreExposure+ship.maxTorpedoAmmo*exposed*.90;
        int lostKinetic=(int)StrictMath.floor(kinetic+1e-9);
        int lostMissile=(int)StrictMath.floor(missile+1e-9);
        int lostTorpedo=(int)StrictMath.floor(torpedo+1e-9);
        ship.kineticStoreExposure=StrictMath.max(0,kinetic-lostKinetic);
        ship.missileStoreExposure=StrictMath.max(0,missile-lostMissile);
        ship.torpedoStoreExposure=StrictMath.max(0,torpedo-lostTorpedo);
        ship.kineticAmmo=Math.max(0,ship.kineticAmmo-lostKinetic);
        ship.missileAmmo=Math.max(0,ship.missileAmmo-lostMissile);
        ship.torpedoAmmo=Math.max(0,ship.torpedoAmmo-lostTorpedo);
    }

    /** Shared genuine destruction path, also exercised by end-state tests. */
    void destroyHull(Ship defender,Ship attacker) {
        if(defender.retired)return;
        defender.hull=0;defender.retired=true;destructions++;
        if(attacker!=null)attacker.kills++;
        if(defender.fittedCrewSystems) {
            defender.evacuatedCrew=(int)StrictMath.floor(
                    StrictMath.min(defender.crewCount,defender.escapePods));
            defender.crewCount=0;
        }
        defender.recoveryBeaconActivated=defender.recoveryBeacon;
        if(defender.evacuatedCrew>0)
            log(defender.name+" evacuated "+defender.evacuatedCrew+" crew via escape pods");
        if(defender.recoveryBeaconActivated)
            log(defender.name+" recovery beacon transmitted wreck position");
        log(defender.name+" shattered at T+"+tick/30+"s");
    }

    private void replenish(Ship s) {
        s.power = Math.max(0, Math.min(s.maxPower * s.reactorCondition,
                s.power + s.powerRegen * DT * s.reactorCondition
                        * s.crewReadiness() - weather.powerDrainPerSecond() * DT));
        s.heat = Math.max(0, s.heat + (s.passiveHeat + weather.heatPerSecond()
                - s.coolingRate * s.coolingCondition * weather.coolingFactor()) * DT);
        DeceptionProjection.operate(s,DT);
        checkUnsafeEngineering(s);
        // Regenerate individual shield facings, subject to cooldown, reactor and power headroom.
        double rechargeCost=s.defense.recharge(DT,StrictMath.max(0,s.power-3),
                s.reactorCondition>0.15 && s.heat<s.maxHeat*0.9);
        s.power-=rechargeCost;
        s.shields=s.defense.totalShields();
        if (s.power > 5 && s.damageControl > 0) {
            // Preserve legacy low-level damage control for old/non-fitted ships.
            double restoration = s.damageControl * DT * 30
                    * (s.faction == Faction.MANDRALLY ? 1.0 : 0.20);
            double actual=Math.min(Math.max(0,s.maxHull-s.hull),restoration);
            s.hull+=actual;s.hullRepaired+=actual;
            if(actual>0)s.lastRepairTick=tick;
        }
        if(s.power>6 && s.support.selfRepairRate()>0 && s.hull<s.maxHull) {
            double desired=s.support.selfRepairRate()*DT;
            double efficiency=s.support.powerEfficiency();
            double actual=Math.min(Math.min(desired,s.maxHull-s.hull),
                    (s.power-6)*efficiency/0.85);
            s.power-=actual*0.85/efficiency;
            s.hull+=actual;s.hullRepaired+=actual;
            if(actual>0)s.lastRepairTick=tick;
            s.heat+=actual*0.11;
        }
        s.laserReload = Math.max(0, s.laserReload - DT);
        s.missileReload = Math.max(0, s.missileReload - DT);
        s.pdReload = Math.max(0, s.pdReload - DT);
        for(Ship.PrecisionMount mount:s.precisionMounts)
            mount.reload=Math.max(0,mount.reload-DT);
        for(Ship.FlakMount mount:s.flakMounts)
            mount.reload=Math.max(0,mount.reload-DT);
        for(RotaryPointDefense.Mount gun:s.rotaryMounts)
            gun.tick(DT);
        for(RailPointDefense.Mount gun:s.railMounts)
            gun.tick(DT);
        for(ParticlePointDefense.Mount gun:s.particlePdMounts)
            gun.cool(DT,s.coolingCondition);
        s.kineticReload=Math.max(0,s.kineticReload-DT);
        s.plasmaReload=Math.max(0,s.plasmaReload-DT);
        s.particleReload=Math.max(0,s.particleReload-DT);
        s.torpedoReload=Math.max(0,s.torpedoReload-DT);
        for(Ship.Launcher launcher:s.launchers)
            launcher.reload=Math.max(0,launcher.reload-DT);
        s.fighterReload=Math.max(0,s.fighterReload-DT);
        s.droneReload=Math.max(0,s.droneReload-DT);
        s.repairDroneReload=Math.max(0,s.repairDroneReload-DT);
    }

    /** Sensor channels are independent: coatings fool radar, heat dampers fool
     * infrared, powered cloaks fool optics, while x-ray and gravitic detection
     * supply shorter-range counters. All channels obey real range and weather. */
    private double targetAcquisitionRange(Ship observer,Ship target){
        // Active radar/IR/X-ray/gravitic arrays need reactor electricity and
        // working operators. Passive naked-eye optical identification does
        // not. Previously a ship at zero power could still aim through its
        // fully powered exotic scanner range; that was not a real module.
        double condition=(observer.power>3&&observer.crewReadiness()>=.05)
                ?StrictMath.max(0,observer.sensorsCondition):0;
        double weatherRange=weather.sensorFactor();
        double acuity=observer.sensorAcuity;
        double resolution=condition>0
                ?StrictMath.min(2.2,observer.network.resolutionGain()
                        *(1+.30*observer.commandAssist)):1;
        double rcs=StrictMath.max(0.06,StrictMath.min(4,target.radarCrossSection));
        double radar=observer.sensorRange*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.sqrt(rcs))*(1+0.09*(acuity-1))
                *SensorActiveScan.radarFactor(observer.scan,target.id);
        // Visual silhouettes remain discernible even when electronic sensors fail.
        // The cloak needs power and working electronic warfare electronics.
        double cloak=target.power>4&&target.ecmCondition>0.15
                ?target.opticalSignature:1;
        // A visible capital-hull silhouette is easier to identify optically
        // than a tiny escort at the same distance. This is ordinary passive
        // line-of-sight, not omniscient fleet targeting or a stealth bypass.
        // Broken/idle cloaks still fail open. The bound preserves optical
        // stealth and independent radar/IR/X-ray/gravitic sensor advantages.
        double silhouette=StrictMath.max(.85,StrictMath.min(1.24,
                StrictMath.sqrt(StrictMath.max(1,target.maxHull)/550)));
        double optical=(weather.preset().includes(SpaceWeather.Kind.NEBULA)?310:390)
                *cloak*silhouette;
        double thermalDamping=1-(1-target.thermalSignature)
                *StrictMath.max(0,target.coolingCondition);
        double infrared=observer.infraredRange*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.max(0.10,thermalDamping))
                *(1+0.08*(acuity-1));
        double xray=observer.xrayRange*condition*weatherRange*resolution
                *(1+0.10*(acuity-1))/
                (1+0.16*StrictMath.min(2,target.maxArmor/650))
                *SensorActiveScan.xrayFactor(observer.scan,target.id);
        double mass=StrictMath.max(0.45,StrictMath.min(2.4,target.maxHull/550));
        double gravitic=observer.graviticRange*condition*weatherRange*resolution
                *StrictMath.sqrt(mass)*(1+0.07*(acuity-1));
        return StrictMath.max(optical,StrictMath.max(radar,
                StrictMath.max(infrared,StrictMath.max(xray,gravitic))));
    }
    /** Lock quality from the SAME historical signal provenance that
     * authorizes the shot, not a present-time enemy stealth/ECM oracle.
     * Only channels in the actual arrived sample contribute. */
    private double receivedLockRange(Ship observer,
            SensorLightTimeHistory.Observation fix,
            SensorLightTimeHistory.Profile target){
        if(target==null)return 0;
        double condition=observer.power>3&&observer.crewReadiness()>=.05
                ?StrictMath.max(0,observer.sensorsCondition):0;
        double acuity=observer.sensorAcuity;
        double resolution=condition>0?StrictMath.min(2.2,
                observer.network.resolutionGain()*(1+.30*observer.commandAssist)):1;
        double weatherRange=weather.sensorFactor();
        int channels=fix.channels();
        double best=0;
        if((channels&CONTACT_RADAR)!=0){
            double rcs=StrictMath.max(.06,StrictMath.min(4,
                    target.radarCrossSection()));
            best=StrictMath.max(best,observer.sensorRange*condition*
                    weatherRange*resolution*StrictMath.sqrt(StrictMath.sqrt(rcs))*
                    (1+.09*(acuity-1)));
        }
        if((channels&CONTACT_OPTICAL)!=0){
            double cloak=target.power()>4&&target.ecmCondition()>.15?
                    target.opticalSignature():1;
            double silhouette=StrictMath.max(.85,StrictMath.min(1.24,
                    StrictMath.sqrt(StrictMath.max(1,target.maxHull())/550)));
            best=StrictMath.max(best,(weather.preset().includes(
                    SpaceWeather.Kind.NEBULA)?310:390)*cloak*silhouette);
        }
        if((channels&CONTACT_INFRARED)!=0){
            double damping=1-(1-target.thermalSignature())*
                    StrictMath.max(0,target.coolingCondition());
            best=StrictMath.max(best,observer.infraredRange*condition*
                    weatherRange*resolution*
                    StrictMath.sqrt(StrictMath.max(.10,damping))*
                    (1+.08*(acuity-1)));
        }
        if((channels&CONTACT_XRAY)!=0)
            best=StrictMath.max(best,observer.xrayRange*condition*
                    weatherRange*resolution*(1+.10*(acuity-1))/
                    (1+.16*StrictMath.min(2,target.maxArmor()/650)));
        if((channels&CONTACT_GRAVITIC)!=0){
            double mass=StrictMath.max(.45,StrictMath.min(2.4,
                    target.maxHull()/550));
            best=StrictMath.max(best,observer.graviticRange*condition*
                    weatherRange*resolution*StrictMath.sqrt(mass)*
                    (1+.07*(acuity-1)));
        }
        return best;
    }

    /** Render/UI classification of DIRECT detections, without granting locks or
     * introducing a fleet-wide sensor datalink. Multiple bits can be present.
     * Keep channel thresholds identical to targetAcquisitionRange above. */
    public static final int CONTACT_OPTICAL=1,CONTACT_RADAR=2,CONTACT_INFRARED=4,
            CONTACT_XRAY=8,CONTACT_GRAVITIC=16;
    public int directSensorChannels(Ship observer,Ship target,double distance){
        if(observer==null||target==null||!observer.alive()||!target.alive()
                ||!Double.isFinite(distance)||distance<0)return 0;
        double condition=(observer.power>3&&observer.crewReadiness()>=.05)
                ?StrictMath.max(0,observer.sensorsCondition):0;
        double weatherRange=weather.sensorFactor();
        double acuity=observer.sensorAcuity;
        double resolution=condition>0
                ?StrictMath.min(2.2,observer.network.resolutionGain()
                        *(1+.30*observer.commandAssist)):1;
        double rcs=StrictMath.max(.06,StrictMath.min(4,target.radarCrossSection));
        double radar=observer.sensorRange*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.sqrt(rcs))*(1+.09*(acuity-1))
                *SensorActiveScan.radarFactor(observer.scan,target.id);
        double cloak=target.power>4&&target.ecmCondition>.15
                ?target.opticalSignature:1;
        double silhouette=StrictMath.max(.85,StrictMath.min(1.24,
                StrictMath.sqrt(StrictMath.max(1,target.maxHull)/550)));
        double optical=(weather.preset().includes(SpaceWeather.Kind.NEBULA)?310:390)
                *cloak*silhouette;
        double damping=1-(1-target.thermalSignature)
                *StrictMath.max(0,target.coolingCondition);
        double infrared=observer.infraredRange*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.max(.10,damping))*(1+.08*(acuity-1));
        double xray=observer.xrayRange*condition*weatherRange*resolution
                *(1+.10*(acuity-1))/(1+.16*StrictMath.min(2,target.maxArmor/650))
                *SensorActiveScan.xrayFactor(observer.scan,target.id);
        double mass=StrictMath.max(.45,StrictMath.min(2.4,target.maxHull/550));
        double gravity=observer.graviticRange*condition*weatherRange*resolution
                *StrictMath.sqrt(mass)*(1+.07*(acuity-1));
        int channels=0;
        if(distance<=optical)channels|=CONTACT_OPTICAL;
        if(radar>0&&distance<=radar)channels|=CONTACT_RADAR;
        if(infrared>0&&distance<=infrared)channels|=CONTACT_INFRARED;
        if(xray>0&&distance<=xray)channels|=CONTACT_XRAY;
        if(gravity>0&&distance<=gravity)channels|=CONTACT_GRAVITIC;
        return channels;
    }

    /** Equipment and signature eligibility at the moment of observation,
     * not the hostile ship's current RCS, thermal output, cloak or armor.
     * Leave public directSensorChannels as an immediate HARDWARE diagnostics
     * API; fire control and the tactical picture use this historical version. */
    private int historicalSensorChannels(Ship source,Ship target,
            SensorLightTimeHistory.Observation fix){
        SensorLightTimeHistory.Profile targetProfile=
                target.sensorHistory.profileAt(fix.measuredTick());
        SensorLightTimeHistory.Profile sourceProfile=
                source.sensorHistory.profileAt(fix.receivedTick());
        SensorLightTimeHistory.Sample sourceState=
                source.sensorHistory.at(fix.receivedTick());
        if(sourceProfile==null&&fix.receivedTick()>0){
            sourceProfile=source.sensorHistory.profileAt(fix.receivedTick()-1);
            sourceState=source.sensorHistory.at(fix.receivedTick()-1);
        }
        if(sourceProfile==null||sourceState==null||targetProfile==null)
            return 0;
        double distance=StrictMath.hypot(
                fix.sample().x()-sourceState.x(),
                fix.sample().y()-sourceState.y());
        double condition=sourceProfile.power()>3&&
                sourceProfile.crewReadiness()>=.05
                ?StrictMath.max(0,sourceProfile.sensorsCondition()):0;
        double weatherRange=weather.sensorFactor();
        double acuity=sourceProfile.sensorAcuity();
        double resolution=condition>0
                ?StrictMath.min(2.2,sourceProfile.resolutionGain()*
                        (1+.30*sourceProfile.commandAssist())):1;
        double rcs=StrictMath.max(.06,StrictMath.min(4,targetProfile.radarCrossSection()));
        double radarFactor=switch(sourceProfile.scanMode()){
            case STANDARD -> 1;
            case BOOSTED_RADAR -> 1.55;
            case FOCUSED_RADAR -> sourceProfile.focusId()==target.id?1.28:0;
            default -> 0;
        };
        double xrayFactor=sourceProfile.scanMode()==SensorActiveScan.Mode.STANDARD?1:
                (sourceProfile.scanMode()==SensorActiveScan.Mode.XRAY_BURST&&
                 sourceProfile.focusId()==target.id?1.45:0);
        double radar=sourceProfile.sensorRange()*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.sqrt(rcs))*(1+.09*(acuity-1))
                *radarFactor;
        double cloak=targetProfile.power()>4&&targetProfile.ecmCondition()>.15
                ?targetProfile.opticalSignature():1;
        double silhouette=StrictMath.max(.85,StrictMath.min(1.24,
                StrictMath.sqrt(StrictMath.max(1,targetProfile.maxHull())/550)));
        double optical=(weather.preset().includes(SpaceWeather.Kind.NEBULA)?310:390)
                *cloak*silhouette;
        double damp=1-(1-targetProfile.thermalSignature())
                *StrictMath.max(0,targetProfile.coolingCondition());
        double infrared=sourceProfile.infraredRange()*condition*weatherRange*resolution
                *StrictMath.sqrt(StrictMath.max(.10,damp))*(1+.08*(acuity-1));
        double xray=sourceProfile.xrayRange()*condition*weatherRange*resolution
                *(1+.10*(acuity-1))/(1+.16*StrictMath.min(
                        2,targetProfile.maxArmor()/650))*xrayFactor;
        double mass=StrictMath.max(.45,StrictMath.min(
                2.4,targetProfile.maxHull()/550));
        double gravitic=sourceProfile.graviticRange()*condition*weatherRange*resolution
                *StrictMath.sqrt(mass)*(1+.07*(acuity-1));
        int channels=0;
        if(distance<=optical)channels|=CONTACT_OPTICAL;
        if(radar>0&&distance<=radar)channels|=CONTACT_RADAR;
        if(infrared>0&&distance<=infrared)channels|=CONTACT_INFRARED;
        if(xray>0&&distance<=xray)channels|=CONTACT_XRAY;
        if(gravitic>0&&distance<=gravitic)channels|=CONTACT_GRAVITIC;
        // Current receiver electronics still have to be functional, even
        // if they emitted photons earlier. Optical sight works without power.
        if(source.power<=3||source.sensorsCondition<=.05||
                source.crewReadiness()<.05)
            channels&=CONTACT_OPTICAL;
        // No retrospective hardware installs: the outward pulse itself
        // must have been possible using equipment available when emitted.
        SensorLightTimeHistory.Profile emitted=
                source.sensorHistory.profileAt(fix.emittedTick());
        if(emitted==null || emitted.power()<=3 ||
                emitted.sensorsCondition()<=.05){
            channels&=~(CONTACT_RADAR|CONTACT_XRAY);
        }else{
            if(emitted.sensorRange()<=0)channels&=~CONTACT_RADAR;
            if(emitted.xrayRange()<=0)channels&=~CONTACT_XRAY;
        }
        return channels;
    }

    /** One fixed-capacity historical snapshot per ship each simulation tick. */
    private void captureSensorHistory(long observedTick){
        for(Ship ship:ships)ship.sensorHistory.capture(observedTick,ship);
    }

    /** Local sensors read only arrived historical light. Remote ships read
     * the sender's finite actual packet archive. No allied receiver can
     * reconstruct a packet the scout never observed and transmitted. */
    private SensorLightTimeHistory.Observation receivedContact(
            Ship viewer,Ship target,Ship source){
        if(source==null)return null;
        if(source!=viewer){
            if(viewer.power<=3||source.power<=3||source.sensorsCondition<=.05||
                    viewer.crewReadiness()<.05||source.crewReadiness()<.05)
                return null;
            int fitted=(int)StrictMath.ceil(source.network.sharingCapacity()*
                    StrictMath.max(0,source.sensorsCondition));
            int capacity=StrictMath.min(SensorRelayBuffer.MAX_TARGETS,
                    StrictMath.max(weather.preset().enabled()?4:0,fitted));
            int linkTicks=SensorLightTimeHistory.propagationTicks(
                    StrictMath.hypot(viewer.x-source.x,viewer.y-source.y));
            return source.relayPackets.delivered(target.id,tick,linkTicks,capacity);
        }
        // All five historical candidates are evaluated independently against
        // the actual reflected/received profiles after photon delay is solved.
        // Current enemy signature and position are not an eligibility gate.
        SensorLightTimeHistory.Observation candidate=SensorLightTimeHistory.receive(
                source,target,tick,CONTACT_OPTICAL|CONTACT_RADAR|
                        CONTACT_INFRARED|CONTACT_XRAY|CONTACT_GRAVITIC,0);
        if(candidate==null)return null;
        int valid=candidate.channels()&historicalSensorChannels(
                source,target,candidate);
        if(valid==0)return null;
        long emitted=(valid&(CONTACT_RADAR|CONTACT_XRAY))!=0
                ?candidate.emittedTick():candidate.measuredTick();
        return new SensorLightTimeHistory.Observation(emitted,
                candidate.measuredTick(),candidate.receivedTick(),
                valid,candidate.sample());
    }

    /** Paid tracking retains measured-state history; a relayed fix must
     * not be replaced with invented local sensor coordinates. */
    private void rememberTrack(Ship observer,Ship target){
        SensorLightTimeHistory.Observation observation=
                receivedContact(observer,target,observer);
        rememberTrack(observer,target,observation);
    }
    private void rememberTrack(Ship observer,Ship target,
            SensorLightTimeHistory.Observation observation){
        int capacity=observer.network.trackMemoryCapacity();
        if(capacity<=0||observer.power<=3||observer.sensorsCondition<=.05
                ||observer.crewReadiness()<.05||observation==null)return;
        SensorLightTimeHistory.Sample measured=observation.sample();
        ContactTrack existing=observer.knownTracks.get(target.id);
        if(existing!=null){existing.update(measured);return;}
        if(observer.knownTracks.size()>=capacity){
            int oldestId=-1;
            long oldest=Long.MAX_VALUE;
            for(Map.Entry<Integer,ContactTrack> entry:observer.knownTracks.entrySet()){
                if(entry.getValue().tick<oldest ||
                        (entry.getValue().tick==oldest && entry.getKey()<oldestId)){
                    oldest=entry.getValue().tick;oldestId=entry.getKey();
                }
            }
            if(oldestId>=0)observer.knownTracks.remove(oldestId);
        }
        observer.knownTracks.put(target.id,new ContactTrack(measured));
    }

    /** Command cores assist only allied ships physically inside a functioning
     * powered local command cell. Each center has a finite recipient budget. */
    private void refreshCommandAssistance(){
        for(Ship ship:ships){
            ship.commandAssist=0;
            ship.commandSourceId=0;
        }
        Ship[] recipients=new Ship[40];
        double[] closest=new double[40];
        for(Ship command:ships){
            int slots=command.network.commandCapacity();
            if(!command.alive()||slots<=0||command.power<=3||
                    command.commandCondition<=.15||command.sensorsCondition<=.15
                    ||command.crewReadiness()<.05)continue;
            slots=StrictMath.min(slots,40);
            double power=StrictMath.min(command.commandCondition,command.sensorsCondition);
            double radius=command.network.fusionRange()*power;
            if(radius<=0)continue;
            double selfAssist=command.network.fusionStrength()*power;
            if(selfAssist>command.commandAssist){
                command.commandAssist=selfAssist;
                command.commandSourceId=command.id;
            }
            Arrays.fill(recipients,0,slots,null);
            Arrays.fill(closest,0,slots,Double.POSITIVE_INFINITY);
            for(Ship friend:ships){
                if(friend==command||friend.team!=command.team||!friend.alive())continue;
                double dx=friend.x-command.x,dy=friend.y-command.y;
                double d2=dx*dx+dy*dy;
                if(d2>radius*radius)continue;
                for(int k=0;k<slots;k++)if(d2<closest[k]){
                    for(int j=slots-1;j>k;j--){
                        closest[j]=closest[j-1];recipients[j]=recipients[j-1];
                    }
                    closest[k]=d2;recipients[k]=friend;
                    break;
                }
            }
            for(int k=0;k<slots;k++)if(recipients[k]!=null){
                Ship recipient=recipients[k];
                if(recipient.power<=3||recipient.crewReadiness()<.05)continue;
                double link=1-.25*StrictMath.sqrt(closest[k])/radius;
                double assist=command.network.fusionStrength()*power*link;
                if(assist>recipient.commandAssist){
                    recipient.commandAssist=assist;
                    recipient.commandSourceId=command.id;
                }
            }
        }
    }

    /** Existing short-range formation radio remains intact. Paid SHR hardware
     * additionally broadcasts a bounded number of its closest REAL contacts.
     * There are no fabricated phantom ships in the detection registry. */
    private void refreshSharedContacts(){
        for(int team=0;team<2;team++){
            if(sharedSpotters[team].length<nextId){
                sharedSpotters[team]=new Ship[nextId];
                sharedNearest[team]=new double[nextId];
                expandedSpotters[team]=new Ship[nextId];
                expandedNearest[team]=new double[nextId];
            }else{
                Arrays.fill(sharedSpotters[team],null);
                Arrays.fill(expandedSpotters[team],null);
            }
            Arrays.fill(sharedNearest[team],Double.POSITIVE_INFINITY);
            Arrays.fill(expandedNearest[team],Double.POSITIVE_INFINITY);
        }
        // Preserve the legacy no-weather short-radio behavior, but real
        // equipped datalinks and tracking processors must work in CLEAR space.
        boolean legacyRelay=weather.preset().enabled();
        Ship[] selected=new Ship[32];
        double[] distances=new double[32];
        for(Ship scout:ships){
            if(!scout.alive()||scout.power<=3||scout.crewReadiness()<.05)continue;
            if(!legacyRelay && scout.network.sharingCapacity()<=0
                    && scout.network.trackMemoryCapacity()<=0)continue;
            Ship[] contacts=sharedSpotters[scout.team];
            double[] closest=sharedNearest[scout.team];
            int capacity=(int)StrictMath.ceil(scout.network.sharingCapacity()
                    *StrictMath.max(0,scout.sensorsCondition));
            capacity=StrictMath.min(32,capacity);
            // The weather-enabled legacy short radio shares at most four
            // contacts; without SHR it never acquires a long-range broadcast.
            int broadcastCapacity=StrictMath.max(capacity,legacyRelay?4:0);
            if(broadcastCapacity>0){
                Arrays.fill(selected,0,broadcastCapacity,null);
                Arrays.fill(distances,0,broadcastCapacity,Double.POSITIVE_INFINITY);
            }
            for(Ship hostile:ships){
                if(!hostile.alive()||hostile.team==scout.team)continue;
                // Only a measured observation can justify selection for
                // broadcast, even if the enemy has since moved or cloaked.
                SensorLightTimeHistory.Observation sensed=
                        receivedContact(scout,hostile,scout);
                if(sensed==null)continue;
                double dx=sensed.sample().x()-scout.x;
                double dy=sensed.sample().y()-scout.y;
                double d2=dx*dx+dy*dy;
                rememberTrack(scout,hostile,sensed);
                if(legacyRelay && d2<closest[hostile.id]){
                    closest[hostile.id]=d2;
                    contacts[hostile.id]=scout;
                }
                if(broadcastCapacity<=0)continue;
                for(int k=0;k<broadcastCapacity;k++)if(d2<distances[k]){
                    for(int n=broadcastCapacity-1;n>k;n--){
                        distances[n]=distances[n-1];
                        selected[n]=selected[n-1];
                    }
                    distances[k]=d2;selected[k]=hostile;
                    break;
                }
            }
            // One fitted SHR budget must accommodate true contact packets
            // AND competing deceptive hypotheses. More false returns reduce
            // the number of distinct real targets this scout can relay.
            int fittedRemaining=capacity;
            for(int k=0;k<broadcastCapacity;k++){
                Ship hostile=selected[k];
                if(hostile==null)break;
                SensorLightTimeHistory.Observation received=
                        receivedContact(scout,hostile,scout);
                if(received==null)continue;
                boolean fitted=capacity>0&&fittedRemaining>0;
                // Legacy short-radio weather reports stay independent and
                // cannot conjure paid SHR false-contact bandwidth.
                if(fitted||legacyRelay)
                    scout.relayPackets.transmit(hostile.id,tick,received,broadcastCapacity);
                if(fitted){
                    fittedRemaining--;
                    if(distances[k]<expandedNearest[scout.team][hostile.id]){
                        expandedNearest[scout.team][hostile.id]=distances[k];
                        expandedSpotters[scout.team][hostile.id]=scout;
                    }
                    SensorLightTimeHistory.Profile emission=
                            hostile.sensorHistory.profileAt(received.measuredTick());
                    int active=DeceptionProjection.falseContacts(emission);
                    // Each phantom or shutdown tombstone costs one full
                    // transmitter slot. If bandwidth is exhausted, older
                    // false reports expire instead of living forever.
                    for(int ghost=0;ghost<3&&fittedRemaining>0;ghost++){
                        scout.falsePackets.transmit(hostile.id,ghost,
                                ghost<active,received.sample(),tick,scout.x,
                                scout.y,hostile.visualScale(),capacity);
                        fittedRemaining--;
                    }
                }
            }
        }
    }

    private static ContactTrack trackFromPacket(SensorTrackRelayBuffer.Packet p){
        return new ContactTrack(new SensorLightTimeHistory.Sample(
                p.observedTick(),p.x(),p.y(),p.vx(),p.vy(),true));
    }

    /** Copy the freshest actually measured TRK records into physical datalink
     * transmissions, subject to fitted LNK slots, power and expiry. */
    private void broadcastTrackPackets(){
        for(Ship source:ships){
            if(!source.alive()||source.power<=3||
                    source.sensorsCondition<=.05||source.crewReadiness()<.05||
                    source.network.trackRelayCapacity()<=0||
                    source.network.trackMemoryTicks()<=0)continue;
            int cap=StrictMath.min(SensorTrackRelayBuffer.MAX_CONTACTS,
                    source.network.trackRelayCapacity());
            int window=(int)(source.network.trackMemoryTicks()*
                    StrictMath.min(1,source.sensorsCondition));
            if(window<=0)continue;
            List<Map.Entry<Integer,ContactTrack>> records=
                    new ArrayList<>(source.knownTracks.entrySet());
            records.removeIf(e->tick-e.getValue().tick<0||
                    tick-e.getValue().tick>window);
            records.sort((a,b)->{
                int fresher=Long.compare(b.getValue().tick,a.getValue().tick);
                return fresher!=0?fresher:Integer.compare(a.getKey(),b.getKey());
            });
            for(int i=0;i<StrictMath.min(cap,records.size());i++){
                Map.Entry<Integer,ContactTrack> item=records.get(i);
                source.trackPackets.transmit(item.getKey(),tick,
                        item.getValue(),cap,source.x,source.y);
            }
        }
    }

    /** Direct detection or physically valid contact broadcast, never an
     * unexplained global enemy list. Extended links cost fitted equipment.
     * Stale tracks may guide searching but CANNOT authorize weapon firing. */
    /** Read-only counterpart of the actual fire-control link: no scanner
     * callbacks, ammunition, track memory mutation or random draws. This
     * route is shared by physical weapon acquisition and tactical telemetry. */
    private Ship liveContactSource(Ship actor,Ship hostile,double ignoredDistance){
        // Never select a source on hostile.x/y or present-time signature.
        // A direct observation can be visible even if the enemy has moved
        // beyond the sensor's present-day reach while its photons are enroute.
        if(receivedContact(actor,hostile,actor)!=null)return actor;
        if(actor.power<=3||actor.crewReadiness()<.05)return null;
        Ship[] contacts=sharedSpotters[actor.team];
        if(hostile.id>=contacts.length)return null;
        Ship scout=contacts[hostile.id];
        if(scout!=null&&scout.alive()&&scout.power>3&&
                StrictMath.hypot(actor.x-scout.x,actor.y-scout.y)<=1000){
            SensorLightTimeHistory.Observation packet=
                    receivedContact(actor,hostile,scout);
            if(packet!=null&&StrictMath.hypot(actor.x-packet.sample().x(),
                    actor.y-packet.sample().y())<=1250)return scout;
        }
        Ship transmitter=expandedSpotters[actor.team][hostile.id];
        if(transmitter==null||!transmitter.alive()||transmitter.power<=3||
                transmitter.sensorsCondition<=.05)return null;
        double condition=StrictMath.min(1,StrictMath.max(0,
                transmitter.sensorsCondition));
        double link=1000+transmitter.network.sharingRange()*condition
                +actor.network.trackRelayRange()*StrictMath.max(0,actor.sensorsCondition);
        if(StrictMath.hypot(actor.x-transmitter.x,actor.y-transmitter.y)>link)
            return null;
        SensorLightTimeHistory.Observation packet=
                receivedContact(actor,hostile,transmitter);
        if(packet==null)return null;
        double horizon=1250+StrictMath.min(950,
                transmitter.network.sharingRange()*.38+
                actor.network.trackRelayRange()*.32);
        double measuredDistance=StrictMath.hypot(actor.x-packet.sample().x(),
                actor.y-packet.sample().y());
        return measuredDistance<=horizon?transmitter:null;
    }

    /** Causal permission for ALL sensor doctrines, including STANDARD.
     * No fire-control path may treat an undelivered observation as a lock.
     * Stored last-known fixes remain navigational and never satisfy this gate. */
    private SensorLightTimeHistory.Observation authorizedContact(
            Ship actor,Ship hostile,double estimatedDistance){
        Ship source=liveContactSource(actor,hostile,estimatedDistance);
        SensorLightTimeHistory.Observation arrived=
                receivedContact(actor,hostile,source);
        if(arrived!=null)rememberTrack(actor,hostile,arrived);
        return arrived;
    }
    boolean hasTargetContact(Ship actor,Ship hostile,double distance){
        return authorizedContact(actor,hostile,distance)!=null;
    }

    /** Read-only instrument display fix. Historical coordinates are recorded
     * observations, never the hidden current world position. Relayed optical
     * contacts are NOT line-of-sight for the receiving camera. */
    public record TacticalContact(int targetId,int sourceId,int channels,
                                  double x,double y,double vx,double vy,
                                  long observedTick,float confidence,
                                  boolean relayed,boolean stale,
                                  long emittedTick,long receivedTick){
        public TacticalContact{
            if(targetId<=0||sourceId<=0||observedTick<0||channels<0||
                    emittedTick<0||emittedTick>observedTick||
                    receivedTick<observedTick||
                    !Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Float.isFinite(confidence)||confidence<0||confidence>1)
                throw new IllegalArgumentException("Invalid physical sensor observation");
            if(stale&&channels!=0)
                throw new IllegalArgumentException("Historical memory is not a live sensor");
        }
        /** Preserve constructor compatibility for older display adapters. */
        public TacticalContact(int targetId,int sourceId,int channels,
                double x,double y,double vx,double vy,long observedTick,
                float confidence,boolean relayed,boolean stale){
            this(targetId,sourceId,channels,x,y,vx,vy,observedTick,
                    confidence,relayed,stale,observedTick,observedTick);
        }
    }

    /** One possible phantom image, NOT a true ship or an authorized target.
     * Local observations and remote packet reception retain independent
     * measurement and arrival times. */
    public record TacticalFalseContact(int emitterId,int index,int sourceId,
            double x,double y,double vx,double vy,long observedTick,
            long receivedTick,boolean relayed){
        public TacticalFalseContact{
            if(emitterId<=0||index<0||index>2||sourceId<=0||
                    observedTick<0||receivedTick<observedTick||
                    !Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy))
                throw new IllegalArgumentException("Invalid false-contact picture");
        }
    }
    /**
     * READ-ONLY tactical false hypotheses, never added to tacticalPicture,
     * actual ship identity tables, or hasTargetContact weapon authorization.
     * Remote hypotheses require an existing fitted SHR scout transmission
     * and a separate sender->receiver photon transit.
     */
    public List<TacticalFalseContact> tacticalFalseContacts(int team,int viewerId){
        checkTeam(team);
        Ship viewer=findShip(viewerId);
        if(viewer==null||!viewer.alive()||viewer.team!=team)
            throw new IllegalArgumentException("False picture requires allied viewer");
        java.util.Map<Long,TacticalFalseContact> picture=new java.util.TreeMap<>();
        if(viewer.power>3&&viewer.sensorsCondition>.05&&viewer.crewReadiness()>.05)
            for(Ship source:ships){
                if(source==viewer||source.team!=team||!source.alive()||
                        source.power<=3||source.sensorsCondition<=.05||
                        source.crewReadiness()<.05||
                        source.network.sharingCapacity()<=0)continue;
                double link=1000+source.network.sharingRange()*
                        StrictMath.min(1,source.sensorsCondition)+
                        viewer.network.trackRelayRange()*
                        StrictMath.max(0,viewer.sensorsCondition);
                if(StrictMath.hypot(viewer.x-source.x,viewer.y-source.y)>link)
                    continue;
                int cap=StrictMath.min(SensorFalseContactRelay.MAX_HYPOTHESES,
                        (int)StrictMath.ceil(source.network.sharingCapacity()*
                                StrictMath.max(0,source.sensorsCondition)));
                for(SensorFalseContactRelay.Packet packet:
                        source.falsePackets.delivered(tick,viewer.x,viewer.y,cap)){
                    long key=((long)packet.emitterId()<<2)|packet.index();
                    var old=picture.get(key);
                    if(old==null||old.observedTick()<packet.observedTick())
                        picture.put(key,new TacticalFalseContact(
                                packet.emitterId(),packet.index(),source.id,
                                packet.x(),packet.y(),packet.vx(),packet.vy(),
                                packet.observedTick(),packet.receivedTick(),true));
                }
            }
        // Local passive/active sensor returns use only photons actually
        // received by this viewer; no current emitter state is consulted.
        for(Ship enemy:ships){
            if(enemy.team==team||!enemy.alive())continue;
            SensorLightTimeHistory.Observation seen=
                    receivedContact(viewer,enemy,viewer);
            if(seen==null)continue;
            SensorLightTimeHistory.Profile emitted=
                    enemy.sensorHistory.profileAt(seen.measuredTick());
            int count=DeceptionProjection.falseContacts(emitted);
            for(int index=0;index<count;index++){
                long key=((long)enemy.id<<2)|index;
                picture.put(key,new TacticalFalseContact(enemy.id,index,
                        viewer.id,DeceptionProjection.ghostX(enemy.id,
                                seen.sample(),enemy.visualScale(),index),
                        DeceptionProjection.ghostY(enemy.id,
                                seen.sample(),enemy.visualScale(),index),
                        seen.sample().vx(),seen.sample().vy(),
                        seen.measuredTick(),tick,false));
            }
        }
        // 200v200 HUD must never draw every possible enemy phantom.
        List<TacticalFalseContact> out=new ArrayList<>(32);
        for(TacticalFalseContact contact:picture.values()){
            if(out.size()==32)break;
            out.add(contact);
        }
        return List.copyOf(out);
    }

    /** A commander without a powered FCM receives only the first alive
     * designated ship's local scope and actual routed contacts. Prefer an
     * equipped functioning command core, but never make it a free sensor. */
    /** Same authoritative FCM/viewer selection used by the sensor overlay.
     * No UI may substitute a hidden target ship for the observing captain. */
    public Ship tacticalViewer(int team){
        checkTeam(team);
        Ship viewer=null;
        int priority=-1;
        for(Ship candidate:ships){
            if(candidate.team!=team||!candidate.alive())continue;
            int eligible=candidate.power>3&&candidate.commandCondition>.15
                    &&candidate.sensorsCondition>.15&&candidate.crewReadiness()>.05
                    ?candidate.network.commandCapacity():0;
            if(viewer==null||eligible>priority){
                viewer=candidate;
                priority=eligible;
            }
        }
        return viewer;
    }
    public List<TacticalContact> tacticalPicture(int team){
        Ship viewer=tacticalViewer(team);
        return viewer==null?List.of():tacticalPicture(team,viewer.id);
    }

    /** The same direct/SHR route that controls weapons supplies the command
     * picture, without invoking the mutating hasTargetContact() method.
     * TRK and LNK provide finitely retained historical fixes, not locks. */
    public List<TacticalContact> tacticalPicture(int team,int viewerId){
        checkTeam(team);
        Ship viewer=findShip(viewerId);
        if(viewer==null||!viewer.alive()||viewer.team!=team)
            throw new IllegalArgumentException("Command view must be a living allied hull");
        Map<Integer,TacticalContact> picture=new HashMap<>();
        boolean receiverPowered=viewer.power>3&&viewer.sensorsCondition>.05
                &&viewer.crewReadiness()>.05;
        if(receiverPowered){
            int localWindow=(int)(viewer.network.trackMemoryTicks()
                    *StrictMath.min(1,viewer.sensorsCondition));
            if(localWindow>0)for(Map.Entry<Integer,ContactTrack> entry:
                    viewer.knownTracks.entrySet()){
                ContactTrack track=entry.getValue();
                long age=tick-track.tick;
                if(age<0||age>localWindow)continue;
                picture.put(entry.getKey(),historicalContact(entry.getKey(),
                        viewer.id,track,false));
            }
            // Independent finite sender/receiver LNK range and transmitter
            // capacities. The top N freshest sender records only; sorted
            // ties avoid JVM HashMap iteration-dependent tactical displays.
            if(viewer.network.trackRelayRange()>0)
                for(Ship source:ships){
                    if(source==viewer||source.team!=team||!source.alive()||
                            source.power<=3||source.sensorsCondition<=.05||
                            source.crewReadiness()<.05||
                            source.network.trackRelayCapacity()<=0||
                            source.network.trackMemoryTicks()<=0)continue;
                    double range=StrictMath.min(viewer.network.trackRelayRange(),
                            source.network.trackRelayRange())
                            *StrictMath.min(viewer.sensorsCondition,
                                    source.sensorsCondition);
                    if(range<=0||StrictMath.hypot(viewer.x-source.x,
                            viewer.y-source.y)>range)continue;
                    int cap=StrictMath.min(SensorTrackRelayBuffer.MAX_CONTACTS,
                            source.network.trackRelayCapacity());
                    int window=(int)(source.network.trackMemoryTicks()
                            *StrictMath.min(1,source.sensorsCondition));
                    for(SensorTrackRelayBuffer.Packet packet:
                            source.trackPackets.deliveredAll(tick,viewer.x,viewer.y,cap)){
                        long age=tick-packet.observedTick();
                        if(age<0||age>window)continue;
                        TacticalContact previous=picture.get(packet.targetId());
                        if(previous==null||previous.observedTick()<packet.observedTick())
                            picture.put(packet.targetId(),historicalContact(
                                    packet.targetId(),source.id,
                                    trackFromPacket(packet),true));
                    }
                }
        }
        for(Ship target:ships){
            if(target.team==team||!target.alive())continue;
            double distance=StrictMath.hypot(viewer.x-target.x,viewer.y-target.y);
            Ship source=liveContactSource(viewer,target,distance);
            if(source==null)continue;
            // Source selection already requires genuinely arrived measured
            // sensor channels, not an instant current-position precheck.
            boolean relayed=source!=viewer;
            SensorLightTimeHistory.Observation observation=
                    receivedContact(viewer,target,source);
            if(observation==null)continue;
            SensorLightTimeHistory.Sample measured=observation.sample();
            float working=source.power>3&&source.sensorsCondition>.05
                    ?(float)StrictMath.min(1,source.sensorsCondition):0;
            float confidence=(float)StrictMath.max(0,StrictMath.min(1,
                    .20+.50*(source.network.resolutionGain()-1)*working
                    +.09*StrictMath.max(0,Integer.bitCount(observation.channels())-1)
                    +.20*source.commandAssist));
            picture.put(target.id,new TacticalContact(target.id,source.id,
                    observation.channels(),measured.x(),measured.y(),
                    measured.vx(),measured.vy(),observation.measuredTick(),
                    confidence,relayed,false,observation.emittedTick(),
                    observation.receivedTick()));
        }
        List<TacticalContact> sorted=new ArrayList<>(picture.values());
        sorted.sort((a,b)->Integer.compare(a.targetId(),b.targetId()));
        return List.copyOf(sorted);
    }

    private static TacticalContact historicalContact(int id,int sourceId,
            ContactTrack track,boolean relayed){
        return new TacticalContact(id,sourceId,0,track.x,track.y,track.vx,
                track.vy,track.tick,0,relayed,true,track.tick,track.tick);
    }

    /** Powered tracking and contact resolution reduce estimated position
     * error; broken or unpowered processors grant no phantom precision. */
    /** Nominal fitted processing precision for read-only HUD predictions.
     * The real fire-control allocation still consumes a finite compute pool. */
    public double targetingPrecision(int observerId){
        Ship ship=findShip(observerId);
        if(ship==null)throw new IllegalArgumentException("Unknown observer");
        return tacticalPrecision(ship);
    }

    private static double tacticalPrecision(Ship actor){
        if(actor.power<=3||actor.sensorsCondition<=.05||
                actor.crewReadiness()<.05)return 0;
        return StrictMath.max(0,StrictMath.min(1,actor.sensorsCondition))
                *(actor.network.trackingPrecision()
                  +.65*(actor.network.resolutionGain()-1)
                  +actor.commandAssist);
    }

    private Ship chooseTarget(Ship actor) {
        // Preserve old target selection and RNG stream for baseline fleet tests.
        if(actor.standingOrders==null&&
                actor.orders.equals(BattleOrders.DEFAULT.withPointDefense(actor.orders.pointDefense()))){
            Ship choice = null;
            double best = Double.POSITIVE_INFINITY;
            for (Ship s : ships) if (s.team != actor.team && s.alive()) {
                double routeDistance=StrictMath.hypot(s.x-actor.x,s.y-actor.y);
                SensorLightTimeHistory.Observation contact=
                        authorizedContact(actor,s,routeDistance);
                if(contact==null)continue;
                // Target preference cannot rank by an as-yet unseen trajectory.
                double distance=StrictMath.hypot(
                        contact.sample().x()-actor.x,contact.sample().y()-actor.y);
                double estimationError = (random.nextDouble() - 0.5) * 0.52 /
                        (1+actor.commanderSkill*.4+tacticalPrecision(actor));
                double assessed = distance * (1 + estimationError) + s.combatReadiness() * 32;
                if (assessed < best) { best = assessed; choice = s; }
            }
            updateFocus(actor,choice);
            return choice;
        }
        BattleOrders.Orders orders=actor.orders;
        Ship choice=null;double best=Double.POSITIVE_INFINITY;
        // Gather protected assets once per acting ship, never for each hostile
        // candidate: 200v200 targeting must not grow into a quadratic ally scan.
        java.util.ArrayList<Integer> guardedIds=new java.util.ArrayList<>(5);
        java.util.ArrayList<Integer> attackerIds=new java.util.ArrayList<>(5);
        if(actor.standingOrders!=null){
            for(int id:actor.defendedShipIds){
                Ship asset=findShip(id);
                if(asset!=null&&asset.alive()){
                    guardedIds.add(asset.id);
                    if(asset.lastAttackerId!=0)attackerIds.add(asset.lastAttackerId);
                }
            }
            Ship escort=findShip(actor.escortShipId);
            if(escort!=null&&escort.alive()){
                guardedIds.add(escort.id);
                if(escort.lastAttackerId!=0)attackerIds.add(escort.lastAttackerId);
            }
        }
        // Zero priority disables a class rather than silently turning it into
        // another target. PD continues according to its independent selector.
        for(Ship candidate:ships)if(candidate.team!=actor.team&&candidate.alive()){
            BattleOrders.Engagement classOrder=orders.forSize(BattleOrders.size(candidate));
            if(classOrder.priority()==0)continue;
            double routeDistance=StrictMath.hypot(
                    candidate.x-actor.x,candidate.y-actor.y);
            SensorLightTimeHistory.Observation contact=
                    authorizedContact(actor,candidate,routeDistance);
            if(contact==null)continue;
            double distance=StrictMath.hypot(contact.sample().x()-actor.x,
                    contact.sample().y()-actor.y);
            double error=(random.nextDouble()-.5)*.52/
                    (1+actor.commanderSkill*.4+tacticalPrecision(actor));
            double assessed=distance*(1+error)*100.0/classOrder.priority()
                    +candidate.combatReadiness()*32;
            if(actor.standingOrders!=null &&
                    (attackerIds.contains(candidate.id)||guardedIds.contains(candidate.lastTargetId)))
                assessed-=260;
            switch(orders.targeting()){
                case NEAREST -> { }
                case VULTURE -> assessed-=(1-Math.max(0,candidate.hull/candidate.maxHull))*160;
                case COOPERATE -> {
                    int allies=focusCounts.getOrDefault(candidate.id,0)
                            -(actor.lastTargetId==candidate.id?1:0);
                    assessed-=Math.min(6,Math.max(0,allies))
                            *(25+18*actor.commandAssist);
                }
                case RETALIATE -> {
                    if(candidate.id==actor.lastAttackerId)assessed-=225;
                }
                case PROTECT -> {
                    Ship friend=findShip(orders.anchorShipId());
                    if(friend!=null&&candidate.id==friend.lastAttackerId)assessed-=240;
                }
            }
            if(assessed<best){best=assessed;choice=candidate;}
        }
        updateFocus(actor,choice);
        return choice;
    }
    private void updateFocus(Ship actor,Ship selected){
        int old=actor.lastTargetId,next=selected==null?0:selected.id;
        if(old!=next){
            if(old!=0)focusCounts.merge(old,-1,Integer::sum);
            if(next!=0)focusCounts.merge(next,1,Integer::sum);
            actor.lastTargetId=next;
        }
    }

    /**
     * Execute one step of a vessel's preplanned command program each simulation
     * tick. Navigation overrides only steering; main weapons, PD, aircraft,
     * damage and target acquisition continue using the real Battle model.
     */
    private boolean executeShipCommands(Ship ship,Ship target) {
        ShipCommandQueue.Command command=ship.commandQueue.current();
        if(command==null)return false;
        switch(command.type()){
            case MOVE -> {
                double distance=StrictMath.hypot(command.x()-ship.x,command.y()-ship.y);
                if(distance<=12) {
                    SublightPropulsion.advance(ship,0,0,DT);
                    if(StrictMath.hypot(ship.vx,ship.vy)<.15)
                        ship.commandQueue.complete();
                }else steerToward(ship,command.x(),command.y());
                return true;
            }
            case HOLD -> {
                SublightPropulsion.advance(ship,0,0,DT);
                // Coasting remains physical if fuel is depleted.
                ship.commandQueue.tickTimed();
                return true;
            }
            case ENGAGE -> {
                // Autonomous tactics continue for the specified duration.
                ship.commandQueue.tickTimed();
                return false;
            }
        }
        return false;
    }
    private static double wrap(double angle) { return StrictMath.atan2(StrictMath.sin(angle), StrictMath.cos(angle)); }
    private void steer(Ship s, Ship target) {
        if(s.standingOrders!=null||
                !s.orders.equals(BattleOrders.DEFAULT.withPointDefense(s.orders.pointDefense()))){
            orderedSteer(s,target);return;
        }
        double dx = target.x - s.x, dy = target.y - s.y;
        double range = StrictMath.hypot(dx, dy);
        double heading = StrictMath.atan2(dy, dx);
        if (range < 210) heading += (s.team == 0 ? 1 : -1) * Math.PI * 0.48;
        double change = Math.max(-2.8 * DT, Math.min(2.8 * DT, wrap(heading - s.facing)));
        s.facing = wrap(s.facing + change);
        double rated=SublightPropulsion.maximumSpeed(s);
        SublightPropulsion.advance(s,StrictMath.cos(s.facing)*rated,
                StrictMath.sin(s.facing)*rated,DT);
    }

    private void orderedSteer(Ship ship,Ship target) {
        BattleOrders.Orders orders=ship.orders;
        if(orders.position()==BattleOrders.Position.HOLD){
            SublightPropulsion.advance(ship,0,0,DT);
            return;
        }
        Ship dedicated=findShip(ship.escortShipId);
        if(dedicated!=null&&dedicated.alive()&&dedicated.team==ship.team){
            double radius=ship.standingOrders.directives().escortRange();
            if(StrictMath.hypot(dedicated.x-ship.x,dedicated.y-ship.y)>radius){
                steerToward(ship,dedicated.x,dedicated.y);
                return;
            }
        }
        Ship parent=findShip(orders.anchorShipId());
        if(parent!=null&&parent.team==ship.team&&parent.alive()&&parent!=ship){
            if(orders.position()==BattleOrders.Position.FORMATION){
                steerToward(ship,parent.x+ship.anchorOffsetX,
                        parent.y+ship.anchorOffsetY);
                return;
            }
            if(orders.position()==BattleOrders.Position.ESCORT){
                double d=StrictMath.hypot(parent.x-ship.x,parent.y-ship.y);
                if(d>orders.escortRadius()){
                    steerToward(ship,parent.x,parent.y);
                    return;
                }
            }
        }
        BattleOrders.Engagement engage=orders.forSize(BattleOrders.size(target));
        double preferredRange=engage.range()*(ship.standingOrders==null?
                1:ship.standingOrders.engagementRangeMultiplier());
        // An autonomous captain must not indefinitely hold outside every
        // remaining fitted weapon's physical reach. As ordnance is expended,
        // recalculate the approach distance from surviving guns, not the
        // historical maximum missile/torpedo range of an empty magazine.
        double reach=activeWeaponReach(ship);
        if(reach>0)preferredRange=StrictMath.min(preferredRange,reach*.80);
        double distance=StrictMath.hypot(target.x-ship.x,target.y-ship.y);
        if(orders.position()!=BattleOrders.Position.KEEP_MOVING
                &&distance>=preferredRange*0.5&&distance<=preferredRange){
            SublightPropulsion.advance(ship,0,0,DT);
            return;
        }
        if(distance<preferredRange*0.5){
            double dx=ship.x-target.x,dy=ship.y-target.y;
            double denom=StrictMath.max(1,StrictMath.hypot(dx,dy));
            steerToward(ship,ship.x+dx/denom*preferredRange,
                    ship.y+dy/denom*preferredRange);
        }else if(orders.position()==BattleOrders.Position.KEEP_MOVING
                &&distance<preferredRange){
            double dx=target.x-ship.x,dy=target.y-ship.y;
            steerToward(ship,ship.x-dy,ship.y+dx);
        }else steerToward(ship,target.x,target.y);
    }
    /** Longest currently supplied main-battery range, in world units.
     * Support-only vessels retain their specified defensive standoff rather
     * than being driven into the enemy simply because they have no guns. */
    private static double activeWeaponReach(Ship ship) {
        if(ship.weaponsCondition<=0.05||ship.crewReadiness()<0.05)return 0;
        double reach=0;
        if(ship.laserDamage>0) {
            WeaponMuzzles.Origin laser=WeaponMuzzles.at(
                    ship,WeaponMuzzles.Family.LASER,0);
            LaserFamilies.Type type=LaserFamilies.selected(ship,laser.slotId());
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.LASER)
                    .range(ship.weapons.laserRange())*type.range);
        }
        if(ship.kineticDamage>0&&ship.kineticAmmo>0)
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.KINETIC)
                    .range(ship.weapons.kineticRange()));
        if(ship.plasmaDamage>0)
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.PLASMA)
                    .range(ship.weapons.plasmaRange()));
        if(ship.particleDamage>0)
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.PARTICLE)
                    .range(ship.weapons.particleRange()));
        if(ship.missileDamage>0&&ship.missileAmmo>0)
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.MISSILE)
                    .range(ship.weapons.missileRange()));
        if(ship.torpedoDamage>0&&ship.torpedoAmmo>0)
            reach=StrictMath.max(reach,WeaponBehaviorTable.gun(DamageType.TORPEDO)
                    .range(ship.weapons.torpedoRange()));
        return reach;
    }

    /** Real extraction requires physically crossing your team's own rear
     * battlefield edge at actual engine speed; enemy weapons remain dangerous. */
    private void steerRetreat(Ship ship) {
        steerToward(ship,ship.team==0?-120:1400,
                Math.max(90,Math.min(590,ship.y)));
        if((ship.team==0&&ship.x<=0)||(ship.team==1&&ship.x>=1280)) {
            ship.escaped=true;
            ship.escapeTick=tick;
            ship.vx=0;ship.vy=0;
            log(ship.name+" escaped combat; hull "+Math.round(ship.hull));
            radio(ship,CrewChatter.Event.ESCAPE);
        }
    }
    private void steerToward(Ship ship,double x,double y){
        double desired=StrictMath.atan2(y-ship.y,x-ship.x);
        double change=Math.max(-2.8*DT,Math.min(2.8*DT,wrap(desired-ship.facing)));
        ship.facing=wrap(ship.facing+change);
        double speedCap=ship.standingOrders==null?1:
                ship.standingOrders.directives().speedPercent()/100.0;
        double velocity=SublightPropulsion.maximumSpeed(ship)*speedCap;
        SublightPropulsion.advance(ship,StrictMath.cos(ship.facing)*velocity,
                StrictMath.sin(ship.facing)*velocity,DT);
    }

     /** Cost transfer occurs only for real FIRE events. All costs and rates
     * are shared with the public authoritative WeaponBehaviorTable. */
    private static void chargeGun(Ship s,WeaponBehaviorTable.Gun behavior){
        s.power-=behavior.powerPerShot();
        s.heat+=behavior.heatPerShot();
    }
    /** Module-directed beam endpoint, with visual and damage coordinates equal. */
    private record SurfaceAim(double x,double y) { }
    private SurfaceAim surfaceAim(Ship shooter,Ship target,long shotId) {
        double c=StrictMath.cos(target.facing),s=StrictMath.sin(target.facing);
        double dx=shooter.x-target.x,dy=shooter.y-target.y;
        double localX=dx*c+dy*s,localY=-dx*s+dy*c;
        var point=ShipDamageTargeting.module(target.impactHardpoints,localX,localY,
                shotId^((long)target.id*0x9e3779b97f4a7c15L));
        // Same local bow/port dimensions as physical() so an on-target beam
        // peels exactly the panel it is visually striking, not a second slot.
        double bow=point.x()*25.0,port=point.y()*19.0;
        return new SurfaceAim(target.x+bow*c-port*s,target.y+bow*s+port*c);
    }
    /** Main/kinetic/rocket weapons use one actual on-hull processor pool.
     * A fitted FCM may additionally lend finite shared work after a
     * round-trip command delay, never future target evidence or free CPU. */
    private SensorComputeBudget.Allocation targetWork(Ship observer,
            SensorLightTimeHistory.Observation received,double range,
            double bestPrecision,int competing){
        Ship command=observer.commandSourceId>0?
                findShip(observer.commandSourceId):null;
        return SensorComputeBudget.solveWithCommand(
                observer.targetCompute,observer,received,tick,range,
                bestPrecision,competing,command);
    }

    /** Count alternative hypotheses from the ACTUALLY MEASURED emission
     * epoch. Present emitter power/ECM cannot adjust earlier solve cost.
     * The generated workload is capped independently of CAP engagement slots. */
    int targetingHypothesisLoad(Ship observer,Ship intended,
                                SensorLightTimeHistory.Observation measured){
        if(observer==null||intended==null||measured==null||
                measured.receivedTick()>tick)
            throw new IllegalArgumentException("Targeting requires received photons");
        SensorLightTimeHistory.Profile profile=
                intended.sensorHistory.profileAt(measured.measuredTick());
        if(profile==null)throw new IllegalArgumentException("Expired emitter epoch");
        return SensorComputeBudget.competingHypotheses(
                observer.knownTracks.size(),DeceptionProjection.falseContacts(profile));
    }

    /** Historical aiming fixes an immutable ray; only a LATER physically
     * arriving photon front can intersect actual moving hull geometry. */
    private void emitMainBeam(Ship shooter,Ship intended,
            SensorLightTimeHistory.Observation track,double observedRange,
            long shotId,WeaponMuzzles.Origin muzzle,DamageType family,
            double maximumRange,double rawDamage,double lockChance,
            String label,LaserFamilies.Type laserType){
        SensorComputeBudget.Allocation allocation=targetWork(
                shooter,track,observedRange,tacticalPrecision(shooter),
                targetingHypothesisLoad(shooter,intended,track));
        SensorTargetingSolution.Solution solution=SensorTargetingSolution.solve(
                track,tick,muzzle.x(),muzzle.y(),
                1_000_000_000.0,allocation.precision(),shotId);
        double aimX=solution.aimX(),aimY=solution.aimY();
        boolean goodLock=random.nextDouble()<lockChance;
        if(!goodLock){
            var historical=intended.sensorHistory.profileAt(track.measuredTick());
            int phantoms=DeceptionProjection.falseContacts(historical);
            if(phantoms>0){
                intended.holoShotsDiverted++;
                int phantom=(int)Math.floorMod(shotId,phantoms);
                aimX=DeceptionProjection.ghostX(intended.id,track.sample(),
                        intended.visualScale(),phantom);
                aimY=DeceptionProjection.ghostY(intended.id,track.sample(),
                        intended.visualScale(),phantom);
            }else{
                double dx=aimX-muzzle.x(),dy=aimY-muzzle.y();
                double angle=StrictMath.atan2(dy,dx)+
                        (shotId%2==0?1:-1)*1.15;
                double reach=StrictMath.max(1,StrictMath.hypot(dx,dy));
                aimX=muzzle.x()+StrictMath.cos(angle)*reach;
                aimY=muzzle.y()+StrictMath.sin(angle)*reach;
            }
        }
        BeamRayGeometry.Ray ray=BeamRayGeometry.aim(
                muzzle.x(),muzzle.y(),aimX,aimY,maximumRange);
        Beam display=family==DamageType.LASER?
                new Beam(shotId,tick,muzzle.x(),muzzle.y(),muzzle.x(),muzzle.y(),
                        shooter.team,family,false,laserType):
                new Beam(shotId,tick,muzzle.x(),muzzle.y(),muzzle.x(),muzzle.y(),
                        shooter.team,family);
        display.inFlight=true;
        beams.add(display);
        pendingBeamPhotons.add(new BeamPhoton(shotId,tick,shooter,
                family,ray,rawDamage,label,display));
        // Record an immutable heading and maximum reach, not a future impact.
        visual(shotId,VisualPhase.FIRE,label,shooter.team,shooter.id,intended.id,
                muzzle.x(),muzzle.y(),ray.x2(),ray.y2());
    }

    /** Propagate at physical light speed; each step tests ONLY the part of
     * the ray that actually arrives this tick against CURRENT enemy geometry.
     * Point-defense/fighter beams retain their separate combat semantics. */
    private void advanceBeamPhotons(){
        final double lightStep=WorldScale.C_WORLD_UNITS_PER_SIM_SECOND*DT;
        for(int i=pendingBeamPhotons.size()-1;i>=0;i--){
            BeamPhoton photon=pendingBeamPhotons.get(i);
            if(photon.spawnTick>=tick)continue;
            double begin=photon.travelled;
            double end=StrictMath.min(photon.range,begin+lightStep);
            double ux=(photon.ray.x2()-photon.ray.x1())/photon.range;
            double uy=(photon.ray.y2()-photon.ray.y1())/photon.range;
            BeamRayGeometry.Ray segment=new BeamRayGeometry.Ray(
                    photon.ray.x1()+ux*begin,photon.ray.y1()+uy*begin,
                    photon.ray.x1()+ux*end,photon.ray.y1()+uy*end);
            BeamRayGeometry.Intersection first=null;
            Ship struck=null;
            for(Ship candidate:ships){
                if(!candidate.alive()||candidate.team==photon.team)continue;
                double radius=12*candidate.visualScale();
                if(candidate.x<StrictMath.min(segment.x1(),segment.x2())-radius||
                        candidate.x>StrictMath.max(segment.x1(),segment.x2())+radius||
                        candidate.y<StrictMath.min(segment.y1(),segment.y2())-radius||
                        candidate.y>StrictMath.max(segment.y1(),segment.y2())+radius)
                    continue;
                BeamRayGeometry.Intersection found=BeamRayGeometry.intersect(
                        segment,candidate.x,candidate.y,radius);
                if(found!=null&&(first==null||found.fraction()<first.fraction()||
                        (found.fraction()==first.fraction()&&candidate.id<struck.id))){
                    first=found;struck=candidate;
                }
            }
            photon.travelled=end;
            photon.visual.x2=first==null?segment.x2():first.x();
            photon.visual.y2=first==null?segment.y2():first.y();
            if(struck!=null){
                Ship attacker=findShip(photon.sourceId);
                double impactX=first.x(),impactY=first.y();
                struck.lastAttackerId=photon.sourceId;
                visual(photon.shotId,VisualPhase.IMPACT,photon.label,
                        photon.team,photon.sourceId,struck.id,
                        impactX,impactY,struck.x,struck.y);
                hitAt(struck,photon.damage,attacker,photon.family,
                        photon.ray.x1()-struck.x,
                        photon.ray.y1()-struck.y,0,impactX,impactY);
            }
            if(struck!=null||end>=photon.range-1e-9){
                photon.visual.inFlight=false;
                photon.visual.lifetime=StrictMath.max(.13,photon.visual.lifetime);
                pendingBeamPhotons.remove(i);
            }
        }
    }

    /** Queued rounds are already committed, but not paid until actually fired. */
    private int committedResource(Ship source,DamageType family){
        int n=0;
        if(family==DamageType.KINETIC)
            for(RocketBarrage.Rack rack:source.rocketRacks)
                n+=rack.salvoRemaining*rack.cheapAmmoUnitsPerRocket;
        if(family==DamageType.MISSILE||family==DamageType.TORPEDO)
            for(PendingLaunch pending:launchQueue)
                if(pending.sourceId==source.id&&pending.family==family)
                    n+=pending.pattern.count()-pending.nextIndex;
        return n;
    }
    private static int resourceKey(DamageType type,int slot){
        return slot<0?type.ordinal():100+type.ordinal()*100+slot;
    }
    /** New fire orders are opt-in; legacy battles do not acquire new RNG calls. */
    private boolean resourcePermit(Ship ship,DamageType type,int slot,int cost){
        ShipResourceOrders policy=ship.standingOrders==null?ShipResourceOrders.DEFAULT:
                ship.standingOrders.resources();
        if(policy.legacy())return true;
        int stock=type==DamageType.KINETIC?ship.kineticAmmo:
                type==DamageType.MISSILE?ship.missileAmmo:ship.torpedoAmmo;
        int max=type==DamageType.KINETIC?ship.maxKineticAmmo:
                type==DamageType.MISSILE?ship.maxMissileAmmo:ship.maxTorpedoAmmo;
        return ShipFireControl.mayBegin(policy,true,stock,Math.max(max,stock),
                committedResource(ship,type),cost,tick,
                ship.resourceNextFireTick.getOrDefault(resourceKey(type,slot),0L));
    }
    private void recordResourceShot(Ship ship,DamageType type,int slot,double cooldown){
        ShipResourceOrders policy=ship.standingOrders==null?ShipResourceOrders.DEFAULT:
                ship.standingOrders.resources();
        if(policy.legacy())return;
        int stock=type==DamageType.KINETIC?ship.kineticAmmo:
                type==DamageType.MISSILE?ship.missileAmmo:ship.torpedoAmmo;
        int max=type==DamageType.KINETIC?ship.maxKineticAmmo:
                type==DamageType.MISSILE?ship.maxMissileAmmo:ship.maxTorpedoAmmo;
        ship.resourceNextFireTick.put(resourceKey(type,slot),
                ShipFireControl.nextEligible(policy,stock,Math.max(max,stock),
                        cooldown,tick));
    }
    private void fire(Ship s, Ship target) {
        if(s.crewReadiness()<0.05||
                (s.standingOrders!=null&&s.standingOrders.resources().fireIntent()
                ==ShipResourceOrders.FireIntent.HOLD_OFFENSE))return;
        // An actual selected shot uses the captain's received measurement,
        // not the target's present hidden coordinates. The source/relay gate
        // remains the same one that authorized captain target selection.
        double routeDistance=StrictMath.hypot(target.x-s.x,target.y-s.y);
        SensorLightTimeHistory.Observation firingFix=receivedContact(
                s,target,liveContactSource(s,target,routeDistance));
        if(firingFix==null)return;
        double distance=StrictMath.hypot(firingFix.sample().x()-s.x,
                firingFix.sample().y()-s.y);
        SensorLightTimeHistory.Profile measuredSignature=
                target.sensorHistory.profileAt(firingFix.measuredTick());
        if(measuredSignature==null)return;
        double lockRange=receivedLockRange(s,firingFix,measuredSignature);
        double interference=StrictMath.min(.95,StrictMath.max(0,
                measuredSignature.ecmStrength()*measuredSignature.ecmCondition()));
        double lock=Math.max(0,1-distance/Math.max(1,lockRange))
                *s.crewReadiness()*s.sensorsCondition*weather.sensorFactor()
                *(1-interference)
                *DeceptionProjection.lockMultiplier(measuredSignature,
                        s.sensorAcuity*(s.power>3&&s.sensorsCondition>.05
                            ?s.network.resolutionGain()*(1+.30*s.commandAssist)
                            :1));
        double weatherAim=weather.accuracyFactor();
        var laser=WeaponBehaviorTable.gun(DamageType.LASER);
        var missile=WeaponBehaviorTable.gun(DamageType.MISSILE);
        var kinetic=WeaponBehaviorTable.gun(DamageType.KINETIC);
        var plasma=WeaponBehaviorTable.gun(DamageType.PLASMA);
        var particle=WeaponBehaviorTable.gun(DamageType.PARTICLE);
        var torpedo=WeaponBehaviorTable.gun(DamageType.TORPEDO);
        // Preserve firing-family ordering: LASER, MISSILE, KINETIC, PLASMA,
        // PARTICLE, TORPEDO. No extra RNG calls or synthetic salvo events.
        // Each shot uses the selected installed LAS module's true muzzle and pattern.
        WeaponMuzzles.Origin laserMuzzle=WeaponMuzzles.at(
                s,WeaponMuzzles.Family.LASER,nextVisualShotId);
        LaserFamilies.Type laserType=LaserFamilies.selected(s,laserMuzzle.slotId());
        double laserPower=laser.powerPerShot()*laserType.power;
        double laserHeat=laser.heatPerShot()*laserType.heat;
        boolean readyLaser=s.laserDamage>0
                &&distance<laser.range(s.weapons.laserRange())*laserType.range
                &&s.laserReload<=0 &&s.power>laserPower
                &&s.heat<s.maxHeat*laser.heatFractionLimit()
                &&s.heat+laserHeat<=s.maxHeat
                &&s.weaponsCondition>laser.requiredWeaponCondition();
        if(readyLaser){
            s.laserReload=laser.reload(s.weapons.laserCycle(),s.weaponsCondition)
                    *laserType.cycle;
            s.power-=laserPower;s.heat+=laserHeat;
            long shotId=nextVisualShotId++;
            double chance=laser.aim(
                    s.accuracy*laserType.aim,s.weapons.laserTracking(),weatherAim,lock);
            emitMainBeam(s,target,firingFix,distance,shotId,laserMuzzle,
                    DamageType.LASER,
                    laser.range(s.weapons.laserRange())*laserType.range,
                    s.laserDamage*laserType.damage,chance,
                    LaserFamilies.eventWeapon(laserType),laserType);
        }
        if(!s.launchers.isEmpty()){
            queueRackSalvos(s,target,DamageType.MISSILE,distance,missile);
        }else if(missile.available(s.missileDamage,s.missileAmmo,distance,s.weapons.missileRange(),
                s.missileReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&!hasPendingGuided(s.id,DamageType.MISSILE)){
            OrdnanceDoctrine.Pattern doctrine=OrdnanceDoctrine.forWeapon(
                    DamageType.MISSILE,s.launchMode(DamageType.MISSILE));
            if(s.missileAmmo>=doctrine.ammoNeeded()&&
                    resourcePermit(s,DamageType.MISSILE,-1,doctrine.ammoNeeded())){
                s.missileReload=missile.reload(s.weapons.missileCycle(),s.weaponsCondition)
                        *doctrine.reloadMultiplier();
                PendingLaunch salvo=new PendingLaunch(s.id,target.id,DamageType.MISSILE,
                        doctrine,tick,s.warhead(DamageType.MISSILE),
                        s.propulsionMode(DamageType.MISSILE),
                        s.guidanceMode(DamageType.MISSILE));
                salvo.targetFix=firingFix;
                launchQueue.add(salvo);
                salvosStarted++;
                recordResourceShot(s,DamageType.MISSILE,-1,s.missileReload);
            }
        }
        AmmunitionTable.Profile shell=AmmunitionTable.kinetic(s.kineticType);
        if(kinetic.available(s.kineticDamage,0,distance,s.weapons.kineticRange(),
                s.kineticReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&s.kineticAmmo>0
                &&resourcePermit(s,DamageType.KINETIC,-1,1)
                &&s.power>kinetic.powerPerShot()*shell.powerCost()
                &&s.heat+kinetic.heatPerShot()*shell.heatCost()<=s.maxHeat){
            s.kineticReload=kinetic.reload(s.weapons.kineticCycle(),s.weaponsCondition)
                    *shell.reloadCost();
            s.power-=kinetic.powerPerShot()*shell.powerCost();
            s.heat+=kinetic.heatPerShot()*shell.heatCost();
            s.kineticAmmo--;
            recordResourceShot(s,DamageType.KINETIC,-1,s.kineticReload);
            long shotId=nextVisualShotId++;
            WeaponMuzzles.Origin muzzle=WeaponMuzzles.at(s,WeaponMuzzles.Family.KINETIC,shotId);
            ProjectileModel.Spec body=shell.physical(s.kineticProjectile);
            SensorComputeBudget.Allocation work=targetWork(
                    s,firingFix,distance,tacticalPrecision(s),
                    targetingHypothesisLoad(s,target,firingFix));
            SensorTargetingSolution.Solution solution=SensorTargetingSolution.solve(
                    firingFix,tick,muzzle.x(),muzzle.y(),body.speed(),
                    work.precision(),shotId);
            // A slightly wrong estimated ray can STILL hit a real ship because
            // swept collision below is geometry-driven, not an aim flag.
            double angle=StrictMath.atan2(solution.aimY()-muzzle.y(),
                    solution.aimX()-muzzle.x());
            boolean onTarget=random.nextDouble()<kinetic.aim(
                    s.accuracy,s.weapons.kineticTracking(),weatherAim,lock);
            if(!onTarget){
                var emitted=target.sensorHistory.profileAt(firingFix.measuredTick());
                int phantoms=DeceptionProjection.falseContacts(emitted);
                if(phantoms>0){
                    target.holoShotsDiverted++;
                    int phantom=(int)Math.floorMod(shotId,phantoms);
                    angle=StrictMath.atan2(
                            DeceptionProjection.ghostY(target.id,firingFix.sample(),
                                    target.visualScale(),phantom)-muzzle.y(),
                            DeceptionProjection.ghostX(target.id,firingFix.sample(),
                                    target.visualScale(),phantom)-muzzle.x());
                }else angle+=(shotId%2==0?1:-1)*WeaponBehaviorTable.BALLISTIC_MISS_ANGLE_RADIANS;
            }
            ballistics.add(new Ballistic(shotId,tick,s.id,s.team,target.id,
                    muzzle.x(),muzzle.y(),angle,s.kineticDamage*shell.damage(),
                    body,s.kineticType));
            // Publish the physical emitted ray. Do not betray enemy truth in
            // tracer visuals or mistakenly draw toward a hidden current pose.
            visual(shotId,VisualPhase.FIRE,"KINETIC",s.team,s.id,target.id,
                    muzzle.x(),muzzle.y(),
                    muzzle.x()+StrictMath.cos(angle)*distance,
                    muzzle.y()+StrictMath.sin(angle)*distance);
        }
        if(plasma.available(s.plasmaDamage,0,distance,s.weapons.plasmaRange(),
                s.plasmaReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)){
            s.plasmaReload=plasma.reload(s.weapons.plasmaCycle(),s.weaponsCondition);
            chargeGun(s,plasma);
            long shotId=nextVisualShotId++;
            WeaponMuzzles.Origin muzzle=WeaponMuzzles.at(
                    s,WeaponMuzzles.Family.PLASMA,shotId);
            double chance=plasma.aim(s.accuracy,s.weapons.plasmaTracking(),
                    weatherAim,lock);
            emitMainBeam(s,target,firingFix,distance,shotId,muzzle,
                    DamageType.PLASMA,plasma.range(s.weapons.plasmaRange()),
                    s.plasmaDamage,chance,"PLASMA",null);
        }
        if(particle.available(s.particleDamage,0,distance,s.weapons.particleRange(),
                s.particleReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)){
            s.particleReload=particle.reload(s.weapons.particleCycle(),s.weaponsCondition);
            chargeGun(s,particle);
            long shotId=nextVisualShotId++;
            WeaponMuzzles.Origin muzzle=WeaponMuzzles.at(
                    s,WeaponMuzzles.Family.PARTICLE,shotId);
            double chance=particle.aim(s.accuracy,s.weapons.particleTracking(),
                    weatherAim,lock);
            emitMainBeam(s,target,firingFix,distance,shotId,muzzle,
                    DamageType.PARTICLE,particle.range(s.weapons.particleRange()),
                    s.particleDamage,chance,"PARTICLE",null);
        }
        queueRocketBarrage(s,target,distance,firingFix);
        if(!s.launchers.isEmpty()){
            queueRackSalvos(s,target,DamageType.TORPEDO,distance,torpedo);
        }else if(torpedo.available(s.torpedoDamage,s.torpedoAmmo,distance,s.weapons.torpedoRange(),
                s.torpedoReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&!hasPendingGuided(s.id,DamageType.TORPEDO)){
            OrdnanceDoctrine.Pattern doctrine=OrdnanceDoctrine.forWeapon(
                    DamageType.TORPEDO,s.launchMode(DamageType.TORPEDO));
            if(s.torpedoAmmo>=doctrine.ammoNeeded()&&
                    resourcePermit(s,DamageType.TORPEDO,-1,doctrine.ammoNeeded())){
                s.torpedoReload=torpedo.reload(s.weapons.torpedoCycle(),s.weaponsCondition)
                        *doctrine.reloadMultiplier();
                PendingLaunch salvo=new PendingLaunch(s.id,target.id,DamageType.TORPEDO,
                        doctrine,tick,s.warhead(DamageType.TORPEDO),
                        s.propulsionMode(DamageType.TORPEDO),
                        s.guidanceMode(DamageType.TORPEDO));
                salvo.targetFix=firingFix;
                launchQueue.add(salvo);
                salvosStarted++;
                recordResourceShot(s,DamageType.TORPEDO,-1,s.torpedoReload);
            }
        }
    }

    /** Each rocket battery freezes a single predictive bearing at trigger.
     * It will never track the victim again, even between salvo rounds. */
    private void queueRocketBarrage(Ship ship,Ship target,double distance,
            SensorLightTimeHistory.Observation firingFix){
        if(ship.rocketRacks.isEmpty()||ship.power<5||
                ship.weaponsCondition<=.16||ship.magazineCondition<=.16)return;
        for(RocketBarrage.Rack rack:ship.rocketRacks){
            if(!rack.canStart()||distance>1900+rack.size.ordinal()*200
                    ||ship.kineticAmmo<rack.salvoCount*rack.cheapAmmoUnitsPerRocket
                    ||!resourcePermit(ship,DamageType.KINETIC,rack.slot,
                            rack.salvoCount*rack.cheapAmmoUnitsPerRocket))
                continue;
            WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(
                    ship,WeaponMuzzles.Family.ROCKET,rack.slot);
            // A rocket rack freezes its aim before the first round. Build
            // the solution from received observations, not target truth.
            // Rocket flight lead uses the same discrete motor thrust as
            // the actual unguided body, not its terminal speed as a constant.
            // A committed salvo will NOT reacquire or alter its bearing.
            SensorComputeBudget.Allocation budget=targetWork(
                    ship,firingFix,distance,tacticalPrecision(ship),
                    targetingHypothesisLoad(ship,target,firingFix));
            long solutionId=1L+ship.id*1_000_003L+
                    ((long)rack.slot)*137L+tick*31L;
            SensorTargetingSolution.Solution estimate=
                    SensorTargetingSolution.solve(firingFix,tick,
                            muzzle.x(),muzzle.y(),rack.body.speed(),
                            budget.precision(),solutionId);
            RocketTrajectoryPrediction.Intercept physicalLead=
                    RocketTrajectoryPrediction.lead(
                            firingFix.sample().x(),firingFix.sample().y(),
                            firingFix.sample().vx(),firingFix.sample().vy(),
                            firingFix.measuredTick(),tick,muzzle.x(),muzzle.y(),rack);
            // Reuse the same deterministic finite-precision spread as the
            // kinetic solver. A slower motor's longer flight increases error,
            // but does not fabricate target truth or force every miss.
            double stretch=StrictMath.sqrt(StrictMath.max(1,
                    (physicalLead.seconds()+.5)/(estimate.flightSeconds()+.5)));
            double aimX=physicalLead.x()+
                    (estimate.aimX()-estimate.predictedX())*stretch;
            double aimY=physicalLead.y()+
                    (estimate.aimY()-estimate.predictedY())*stretch;
            rack.lockedBearing=StrictMath.atan2(
                    aimY-muzzle.y(),aimX-muzzle.x());
            rack.intendedTarget=target.id;
            rack.salvoRemaining=rack.salvoCount;
            rack.nextLaunchTick=tick;
            rack.reload=rack.reloadSeconds();
            recordResourceShot(ship,DamageType.KINETIC,rack.slot,rack.reload);
        }
    }
    /** Actual round release consumes rack inventory and ship power PER BODY.
     * A vanished target does not magically redirect already committed rockets. */
    private void updateRocketLaunches(){
        for(Ship source:ships){
            if(source.rocketRacks.isEmpty())continue;
            for(RocketBarrage.Rack rack:source.rocketRacks){
                rack.reload=StrictMath.max(0,rack.reload-DT);
                if(rack.salvoRemaining<=0||rack.nextLaunchTick>tick)continue;
                if(!source.alive()||source.withdrawing||rack.rounds<=0||
                        source.kineticAmmo<rack.cheapAmmoUnitsPerRocket||
                        source.magazineCondition<=.16||source.weaponsCondition<=.16||
                        source.power<2||source.heat>source.maxHeat-1){
                    rack.salvoRemaining=0;continue;
                }
                if(rockets.size()>=MAX_ROCKET_BODIES)continue;
                int index=rack.salvoCount-rack.salvoRemaining;
                double spread=(index-(rack.salvoCount-1)*.5)
                        *(0.012+(1-source.accuracy)*.014);
                double bearing=rack.lockedBearing+spread;
                WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(
                        source,WeaponMuzzles.Family.ROCKET,rack.slot);
                Rocket round=new Rocket(nextVisualShotId++,tick,source,rack,
                        rack.intendedTarget,muzzle.x(),muzzle.y(),bearing);
                rockets.add(round);
                rack.rounds--;rack.salvoRemaining--;rack.nextLaunchTick=tick+2;
                // Paid from persisted campaign stock: physical rocket reload
                // is 12-48 credits, always cheaper than a seeker missile.
                source.kineticAmmo-=rack.cheapAmmoUnitsPerRocket;
                source.power-=1.8;source.heat+=.68;
                rocketRoundsFired++;
                source.launched++;
                visual(round.visualId,VisualPhase.FIRE,"ROCKET",
                        source.team,source.id,round.targetId,round.x,round.y,
                        round.x+StrictMath.cos(bearing)*100,
                        round.y+StrictMath.sin(bearing)*100);
            }
        }
    }
    /** Paid CAP capacity gives separate guided hardpoints different real
     * targets. A launcher cannot shoot unseen/out-of-range hulls; without
     * CAP all racks retain the original common fleet target. */
    /** Expensive CAP electronics stop providing parallel tracking when their
     * actual sensor circuits or reactor are disabled. Baseline stays at one. */
    static int operationalTargetCapacity(Ship ship){
        if(ship.power<=3||ship.sensorsCondition<=.05||
                ship.crewReadiness()<.05)return 1;
        int hardware=ship.network.targetCapacity();
        double condition=StrictMath.max(0,StrictMath.min(1,ship.sensorsCondition));
        return 1+(int)StrictMath.floor((hardware-1)*condition);
    }
    private Ship independentRackTarget(Ship source,Ship primary,int lane,double limit){
        int capacity=operationalTargetCapacity(source);
        if(lane<=0||capacity<=1)return primary;
        int index=(lane-1)%StrictMath.max(1,capacity-1);
        Ship first=null;
        int seen=0;
        for(Ship alternative:ships){
            if(alternative==primary||!alternative.alive()||
                    alternative.team==source.team)continue;
            SensorLightTimeHistory.Observation measured=receivedContact(
                    source,alternative,liveContactSource(source,alternative,0));
            if(measured==null)continue;
            double distance=StrictMath.hypot(measured.sample().x()-source.x,
                    measured.sample().y()-source.y);
            if(distance>limit)continue;
            if(first==null)first=alternative;
            if(seen++==index)return alternative;
        }
        return first==null?primary:first;
    }

    private void queueRackSalvos(Ship source,Ship target,DamageType family,
                                 double distance,WeaponBehaviorTable.Gun gun){
        int ammo=family==DamageType.MISSILE?source.missileAmmo:source.torpedoAmmo;
        double damage=family==DamageType.MISSILE?source.missileDamage:source.torpedoDamage;
        double reach=family==DamageType.MISSILE?source.weapons.missileRange():
                source.weapons.torpedoRange();
        double cycle=family==DamageType.MISSILE?source.weapons.missileCycle():
                source.weapons.torpedoCycle();
        int lane=0;
        for(Ship.Launcher rack:source.launchers){
            if(rack.family!=family||rack.magazine<=0||rack.reload>0)continue;
            if(hasPendingGuided(source.id,family,rack.slot))continue;
            Ship assigned=independentRackTarget(source,target,lane++,gun.range(reach));
            SensorLightTimeHistory.Observation assignedFix=receivedContact(
                    source,assigned,liveContactSource(source,assigned,0));
            if(assignedFix==null)continue;
            double assignedDistance=StrictMath.hypot(
                    assignedFix.sample().x()-source.x,
                    assignedFix.sample().y()-source.y);
            if(!gun.available(damage,ammo,assignedDistance,reach,rack.reload,
                    source.power,source.heat,source.maxHeat,
                    source.weaponsCondition,source.magazineCondition))continue;
            OrdnanceFitting.Mounted loaded=rack.fitting;
            OrdnanceDoctrine.Pattern pattern=OrdnanceDoctrine.forWeapon(
                    family,loaded.guided().salvo());
            if(ammo<pattern.ammoNeeded()||rack.magazine<pattern.ammoNeeded()||
                    !resourcePermit(source,family,rack.slot,pattern.ammoNeeded()))continue;
            rack.reload=gun.reload(cycle,source.weaponsCondition)*pattern.reloadMultiplier();
            PendingLaunch salvo=new PendingLaunch(source.id,assigned.id,family,pattern,tick,
                    loaded.warhead(),loaded.guided().motor(),loaded.guided().guidance(),
                    rack.slot);
            salvo.targetFix=assignedFix;
            launchQueue.add(salvo);
            salvosStarted++;
            recordResourceShot(source,family,rack.slot,rack.reload);
        }
    }
    private boolean hasPendingGuided(int sourceId,DamageType family,int slot){
        for(PendingLaunch pending:launchQueue)
            if(pending.sourceId==sourceId&&pending.family==family
                    &&pending.slotId==slot)return true;
        return false;
    }
    private boolean hasPendingGuided(int sourceId,DamageType family){
        for(PendingLaunch pending:launchQueue)
            if(pending.sourceId==sourceId && pending.family==family)return true;
        return false;
    }
    /** Never create simulated hits or visuals from a mere scheduled shot.
     * Each actual launched body pays an ammunition AND power/heat cost. */
    private void updateQueuedLaunches(){
        for(int i=launchQueue.size()-1;i>=0;i--){
            PendingLaunch order=launchQueue.get(i);
            if(order.nextTick>tick)continue;
            Ship source=findShip(order.sourceId);
            Ship target=findShip(order.targetId);
            var gun=WeaponBehaviorTable.gun(order.family);
            boolean heavy=order.family==DamageType.TORPEDO;
            Ship.Launcher rack=source==null||order.slotId<0?null:source.launcher(order.slotId);
            if(order.slotId>=0&&(rack==null||rack.family!=order.family||
                    rack.magazine<=0)){
                launchQueue.remove(i);continue;
            }
            if(source==null||!source.alive()||source.withdrawing||source.crewReadiness()<0.05
                    ||target==null||!target.alive()
                    ||source.weaponsCondition<=gun.requiredWeaponCondition()
                    ||source.magazineCondition<=gun.requiredMagazineCondition()
                    ||source.power<=gun.powerPerShot()
                    ||(heavy?source.torpedoAmmo:source.missileAmmo)<=0){
                launchQueue.remove(i);
                continue;
            }
            // Bounded deployment protects large fleets from unbounded salvo queues.
            if(missiles.size()>=MAX_GUIDED_BODIES)continue;
            int index=order.nextIndex;
            boolean decoy=order.pattern.isDecoy(index);
            ProjectileModel.Spec standard=heavy?source.torpedoProjectile:source.missileProjectile;
            ProjectileModel.Spec actual=OrdnanceDoctrine.derived(standard,order.pattern,decoy);
            // A preloaded salvo retains its warhead even if the player changes
            // the next salvo's selection while these bodies are still queued.
            AmmunitionTable.Warhead warhead=order.loadedWarhead;
            AmmunitionTable.Profile payload=AmmunitionTable.warhead(warhead);
            if(!decoy)actual=payload.physical(actual);
            OrdnanceDoctrine.Payload subtype=decoy?OrdnanceDoctrine.Payload.DECOY
                    :order.pattern.payload();
            double spread=(index-(order.pattern.count()-1)*0.5)*0.045;
            WeaponMuzzles.Family family=heavy?WeaponMuzzles.Family.TORPEDO:WeaponMuzzles.Family.MISSILE;
            WeaponMuzzles.Origin muzzle=order.slotId>=0?
                    WeaponMuzzles.atSlot(source,family,order.slotId):
                    WeaponMuzzles.at(source,family,nextVisualShotId);
            // The salvo possesses its *original* received fix. Waiting for
            // a second or sixteenth round cannot reveal the enemy's current
            // position, even if its sensor contact has since disappeared.
            SensorLightTimeHistory.Observation fix=order.targetFix;
            if(fix==null){launchQueue.remove(i);continue;}
            double age=StrictMath.max(0,tick-fix.measuredTick())*DT;
            double predictedX=fix.sample().x()+fix.sample().vx()*age;
            double predictedY=fix.sample().y()+fix.sample().vy()*age;
            double angle=StrictMath.atan2(
                    predictedY-muzzle.y(),predictedX-muzzle.x())+spread;
            double distance=StrictMath.hypot(
                    predictedY-muzzle.y(),predictedX-muzzle.x());
            double velocityX=StrictMath.cos(angle)*Math.max(1,distance);
            double velocityY=StrictMath.sin(angle)*Math.max(1,distance);
            double baseDamage=heavy?source.torpedoDamage:source.missileDamage;
            double damage=decoy?0:baseDamage*order.pattern.damageMultiplier()*payload.damage();
            if(source.power<=gun.powerPerShot()*payload.powerCost()
                    ||source.heat+gun.heatPerShot()*payload.heatCost()>source.maxHeat){
                launchQueue.remove(i);continue;
            }
            source.power-=gun.powerPerShot()*payload.powerCost();
            source.heat+=gun.heatPerShot()*payload.heatCost();
            if(heavy)source.torpedoAmmo--;else source.missileAmmo--;
            if(rack!=null)rack.magazine--;
            source.launched++;roundsFiredBySalvos++;
            if(decoy)decoysLaunched++;
            long shot=nextVisualShotId++;
            Missile body=new Missile(shot,tick,source.id,source.team,target.id,
                    muzzle.x(),muzzle.y(),velocityX,velocityY,damage,heavy,actual,
                    heavy?source.weapons.torpedoTracking():source.weapons.missileTracking(),
                    subtype,order.propulsion==OrdnancePropulsion.Mode.COLD_START
                            ?StrictMath.max(15,order.pattern.ignitionDelayTicks()):0,warhead);
            body.guidanceMode=order.guidance;
            body.approachSide=index%2==0?1:-1;
            body.setPropulsion(order.propulsion);
            body.lastKnownX=fix.sample().x();
            body.lastKnownY=fix.sample().y();
            body.lastKnownVX=fix.sample().vx();
            body.lastKnownVY=fix.sample().vy();
            body.lastObservationTick=fix.measuredTick();
            body.seekerUncertainty=3+StrictMath.max(0,
                    tick-fix.measuredTick())*DT*.8;
            missiles.add(body);
            visual(shot,VisualPhase.FIRE,decoy?"DECOY":order.family.name(),
                    source.team,source.id,target.id,muzzle.x(),muzzle.y(),
                    predictedX,predictedY);
            if(++order.nextIndex>=order.pattern.count())launchQueue.remove(i);
            else order.nextTick=tick+order.pattern.intervalTicks();
        }
    }

    /** The neediest allied vessel within range, never an enemy or dead hull. */
    private Ship mostDamagedAlly(Ship source,double radius,boolean includeShields) {
        Ship best=null;double need=0;
        for(Ship friend:ships)if(friend.team==source.team&&friend.alive()
                &&friend!=source){
            double distance=StrictMath.hypot(source.x-friend.x,source.y-friend.y);
            if(distance>radius)continue;
            double hullMissing=(friend.maxHull-friend.hull)/Math.max(1,friend.maxHull);
            double shieldMissing=friend.maxShields>0
                    ?(friend.maxShields-friend.defense.totalShields())/friend.maxShields:0;
            double score=hullMissing+(includeShields?shieldMissing*0.28:0);
            if(score>need+1e-8){need=score;best=friend;}
        }
        return best;
    }
    private Ship lowestShieldsAlly(Ship source,double radius){
        Ship best=null;double need=0;
        for(Ship friend:ships)if(friend.team==source.team&&friend.alive()&&friend!=source
                &&friend.maxShields>0){
            if(StrictMath.hypot(source.x-friend.x,source.y-friend.y)>radius)continue;
            double missing=friend.maxShields-friend.defense.totalShields();
            double value=missing/friend.maxShields;
            if(missing>1e-8&&value>need){need=value;best=friend;}
        }
        return best;
    }
    /** Power is charged to the repairer, not the receiver. Only real
     * missing hit points can be restored. No healing wrecks or enemies. */
    private double healHull(Ship source,Ship target,double wanted) {
        if(source==null||target==null||!source.alive()||!target.alive()
                ||source.team!=target.team||wanted<=0||source.power<=5)return 0;
        double efficiency=source.support.powerEfficiency();
        double restored=Math.min(Math.min(wanted,target.maxHull-target.hull),
                (source.power-5)*efficiency/1.0);
        if(restored<=0)return 0;
        target.hull+=restored;source.power-=restored/efficiency;
        source.heat+=restored*0.18;
        return restored;
    }
    private void updateSupport(){
        // At 30Hz, 6 tick intervals make stable, limited 5Hz beam bursts,
        // cheaper than searching all allies on every tick in 200v200 battles.
        if(tick%6!=0)return;
        double seconds=6*DT;
        for(Ship source:ships)if(source.alive()&&source.reactorCondition>0.15
                &&source.crewReadiness()>=0.05
                &&source.heat<source.maxHeat*0.93) {
            Shipyard.Support support=source.support;
            if(support.remoteRepairRate()>0&&source.power>6) {
                Ship needy=mostDamagedAlly(source,support.repairRange(),false);
                if(needy!=null) {
                    double healed=healHull(source,needy,support.remoteRepairRate()*seconds);
                    if(healed>0){
                        source.remoteHullRepaired+=healed;
                        supportLinks.add(new SupportLink(source,needy,SupportKind.HULL,
                                tick,source.x,source.y));
                    }
                }
            }
            if(support.shieldTransferRate()>0 && source.power>6) {
                Ship weakest=lowestShieldsAlly(source,support.shieldRange());
                if(weakest!=null){
                    double desired=support.shieldTransferRate()*seconds;
                    double efficiency=support.powerEfficiency();
                    double affordable=Math.min(desired,(source.power-5)*efficiency/3.2);
                    double gained=weakest.defense.receiveShieldTransfer(affordable);
                    if(gained>0){
                        source.power-=gained*3.2/efficiency;source.heat+=gained*0.20;
                        weakest.shields=weakest.defense.totalShields();
                        source.shieldsTransferred+=gained;
                        supportLinks.add(new SupportLink(source,weakest,SupportKind.SHIELD,
                                tick,source.x,source.y));
                    }
                }
            }
        }
        updateExtendedSupport(seconds);
    }

    /** Existing shield/armor caps, not free health or unlimited aura buffers. */
    private static double serviceNeed(Ship target,SupportDoctrine.Channel channel){
        return switch(channel){
            case HULL -> StrictMath.max(0,target.maxHull-target.hull);
            case SHIELD -> StrictMath.max(0,target.maxShields-target.defense.totalShields());
            case ARMOR -> StrictMath.max(0,target.defense.maxArmor()-target.defense.totalArmor());
            case POWER -> StrictMath.max(0,target.maxPower*target.reactorCondition-target.power);
        };
    }
    private Ship serviceTarget(Ship source,SupportDoctrine.Channel channel,
                               double range,boolean includeSelf){
        Ship best=null;
        double score=0;
        for(Ship friend:ships)if(friend.team==source.team&&friend.alive()
                &&(includeSelf||friend!=source)){
            double distance=StrictMath.hypot(source.x-friend.x,source.y-friend.y);
            if(distance>range)continue;
            double missing=serviceNeed(friend,channel);
            if(missing<=1e-8)continue;
            double capacity=switch(channel){
                case HULL -> friend.maxHull;
                case SHIELD -> friend.maxShields;
                case ARMOR -> friend.maxArmor;
                case POWER -> friend.maxPower;
            };
            double urgency=missing/StrictMath.max(1,capacity);
            if(urgency>score){score=urgency;best=friend;}
        }
        return best;
    }
    /**
     * All support actually consumes a deficit. Powered beams/auras pay energy
     * at the originating ship; drone charges are paid for during rearming.
     * Armor restores depleted tiles instead of increasing their maximum.
     */
    private double deliverSupport(Ship source,Ship target,SupportDoctrine.Channel channel,
                                  double offered,double energyPerUnit,boolean powered){
        if(source==null||target==null||!source.alive()||!target.alive()
                ||source.team!=target.team||offered<=0)return 0;
        double supply=StrictMath.min(offered,serviceNeed(target,channel));
        if(powered){
            if(source.power<=7)return 0;
            supply=StrictMath.min(supply,(source.power-7)/energyPerUnit);
        }
        if(supply<=1e-10)return 0;
        double gained=switch(channel){
            case HULL -> {target.hull+=supply;yield supply;}
            case SHIELD -> target.defense.receiveShieldTransfer(supply);
            case ARMOR -> target.defense.receiveArmorRepair(supply);
            case POWER -> {target.power=StrictMath.min(
                    target.maxPower*target.reactorCondition,target.power+supply);yield supply;}
        };
        if(gained<=0)return 0;
        if(powered){
            source.power-=gained*energyPerUnit;
            source.heat+=gained*0.12;
        }
        target.shields=target.defense.totalShields();
        target.armor=target.defense.totalArmor();
        switch(channel){
            case HULL -> source.remoteHullRepaired+=gained;
            case SHIELD -> source.shieldsTransferred+=gained;
            case ARMOR -> source.armorServiced+=gained;
            case POWER -> source.powerSupplied+=gained;
        }
        return gained;
    }
    private static SupportKind supportKind(SupportDoctrine.Channel channel){
        return switch(channel){
            case HULL -> SupportKind.HULL;
            case SHIELD -> SupportKind.SHIELD;
            case ARMOR -> SupportKind.ARMOR;
            case POWER -> SupportKind.POWER;
        };
    }
    private void updateExtendedSupport(double seconds){
        for(Ship source:ships)if(source.alive()&&source.crewReadiness()>0.05
                &&source.reactorCondition>0.15&&source.power>7){
            for(SupportDoctrine.Emitter device:source.logistics.direct()){
                Ship target=serviceTarget(source,device.channel(),device.range(),false);
                if(target==null)continue;
                double received=deliverSupport(source,target,device.channel(),
                        device.rate()*seconds,device.energyPerUnit(),true);
                if(received>0)supportLinks.add(new SupportLink(source,target,
                        supportKind(device.channel()),tick,source.x,source.y));
            }
            // A small constant per-vessel benefit inside the spherical radius,
            // not an unlimited fleet multiplier or a new network of blue lines.
            if(tick%30==0)for(SupportDoctrine.Emitter field:source.logistics.auras()){
                for(Ship friend:ships)if(friend.alive()&&friend.team==source.team
                        &&(friend!=source||field.channel()!=SupportDoctrine.Channel.POWER)
                        &&StrictMath.hypot(friend.x-source.x,friend.y-source.y)
                                <=field.range()){
                    double received=deliverSupport(source,friend,field.channel(),
                            field.rate(),field.energyPerUnit(),true);
                    source.auraService+=received;
                }
                // Infrequent, short-radius discharge: substantially stronger
                // than the wide passive field, with the SAME energy limits.
                // It cannot jump between ships or bypass an actual deficit.
                if(tick%300==0){
                    double inner=StrictMath.min(85,field.range()*0.42);
                    for(Ship friend:ships)if(friend.alive()&&friend.team==source.team
                            &&(friend!=source||field.channel()!=SupportDoctrine.Channel.POWER)
                            &&StrictMath.hypot(friend.x-source.x,friend.y-source.y)<=inner){
                        double burst=deliverSupport(source,friend,field.channel(),
                                field.rate()*85,field.energyPerUnit()*1.6,true);
                        source.auraService+=burst;
                    }
                }
            }
        }
    }

    /**
     * Two physical hose/munition-barge transfer pulses per second. Search
     * local allies only for purpose-fitted tender modules. Nothing is minted:
     * donor and receiver have equal and opposite stock changes.
     *
     * Fuel is fractional; ammunition is counted in integer rounds. The
     * accumulated fractional throughput is bounded and never banked without
     * an actual compatible, deficient ally in physical range.
     */
    private void updateTenderSupport(){
        if(tick%15!=0)return;
        final double interval=15*DT;
        for(Ship donor:ships){
            if(!donor.alive()||donor.tenders.emitters().isEmpty()
                    ||donor.power<=7||donor.crewReadiness()<.05
                    ||donor.reactorCondition<.15||donor.heat>=donor.maxHeat*.93)
                continue;
            for(TenderDoctrine.Emitter emitter:donor.tenders.emitters()){
                TenderDoctrine.Cargo cargo=emitter.cargo();
                Ship recipient=tenderRecipient(donor,emitter);
                if(recipient==null)continue;
                double rate=emitter.rate()*interval;
                int index=cargo.ordinal();
                // A discrete magazine transfers only whole rounds.
                double offered=cargo==TenderDoctrine.Cargo.FUEL?rate:
                        StrictMath.floor(donor.tenderCredit[index]+rate);
                if(cargo!=TenderDoctrine.Cargo.FUEL){
                    donor.tenderCredit[index]=Math.min(1.0-1e-9,
                            donor.tenderCredit[index]+rate-offered);
                }
                if(offered<=0)continue;
                double moved=deliverTender(donor,recipient,cargo,offered,
                        emitter.energyPerUnit());
                if(moved>0){
                    donor.heat+=moved*(cargo==TenderDoctrine.Cargo.FUEL?.004:.045);
                    donor.power-=moved*emitter.energyPerUnit();
                    // Link is visual feedback, not another supply channel:
                    // existing support-link kinds are preserved unchanged.
                }
            }
        }
    }
    private static double tenderDeficit(Ship recipient,TenderDoctrine.Cargo cargo){
        return switch(cargo){
            // Unguided rocket racks draw the SAME paid kinetic store as guns.
            // Rocket-only ships are eligible for KTS resupply even when they
            // mount no KIN cannon. Rack magazines remain separately finite.
            case KINETIC -> (recipient.kineticDamage>0||!recipient.rocketRacks.isEmpty())?
                    Math.max(0,recipient.maxKineticAmmo-recipient.kineticAmmo):0;
            case MISSILE -> recipient.missileDamage>0?
                    Math.max(0,recipient.maxMissileAmmo-recipient.missileAmmo):0;
            case TORPEDO -> recipient.torpedoDamage>0?
                    Math.max(0,recipient.maxTorpedoAmmo-recipient.torpedoAmmo):0;
            case FUEL -> Math.max(0,recipient.maxFuel-recipient.fuel);
        };
    }
    private static boolean tenderCompatible(Ship donor,Ship recipient,
                                             TenderDoctrine.Cargo cargo){
        return switch(cargo){
            case KINETIC -> donor.kineticType==recipient.kineticType;
            case MISSILE -> donor.warhead(DamageType.MISSILE)==
                    recipient.warhead(DamageType.MISSILE);
            case TORPEDO -> donor.warhead(DamageType.TORPEDO)==
                    recipient.warhead(DamageType.TORPEDO);
            case FUEL -> true;
        };
    }
    private Ship tenderRecipient(Ship donor,TenderDoctrine.Emitter emitter){
        Ship best=null;
        double priority=-1;
        for(Ship other:ships){
            if(other==donor||!other.alive()||other.team!=donor.team)continue;
            double range=StrictMath.hypot(other.x-donor.x,other.y-donor.y);
            if(range>emitter.range()||
                    !tenderCompatible(donor,other,emitter.cargo()))continue;
            double deficit=tenderDeficit(other,emitter.cargo());
            if(deficit<=1e-8)continue;
            double max=switch(emitter.cargo()){
                case KINETIC -> other.maxKineticAmmo;
                case MISSILE -> other.maxMissileAmmo;
                case TORPEDO -> other.maxTorpedoAmmo;
                case FUEL -> other.maxFuel;
            };
            double urgency=deficit/Math.max(1,max);
            if(urgency>priority){priority=urgency;best=other;}
        }
        return best;
    }
    private static double deliverTender(Ship donor,Ship recipient,
                                       TenderDoctrine.Cargo cargo,double offered,
                                       double energyPerUnit){
        if(donor==recipient||!donor.alive()||!recipient.alive()||
                donor.team!=recipient.team||offered<=0||donor.power<=7)
            return 0;
        double stock=switch(cargo){
            case KINETIC -> donor.kineticAmmo;
            case MISSILE -> donor.missileAmmo;
            case TORPEDO -> donor.torpedoAmmo;
            case FUEL -> Math.max(0,donor.fuel-Math.min(10,donor.maxFuel*.08));
        };
        double quantity=Math.min(Math.min(offered,stock),
                Math.min(tenderDeficit(recipient,cargo),
                        (donor.power-7)/energyPerUnit));
        if(cargo!=TenderDoctrine.Cargo.FUEL)quantity=StrictMath.floor(quantity);
        if(quantity<=0)return 0;
        switch(cargo){
            case KINETIC -> {
                int amount=(int)quantity;
                donor.kineticAmmo-=amount;
                recipient.kineticAmmo+=amount;
                donor.roundsDelivered+=amount;
            }
            case MISSILE -> {
                int amount=(int)quantity;
                donor.missileAmmo-=amount;
                recipient.missileAmmo+=amount;
                replenishLauncherMagazines(recipient,DamageType.MISSILE,amount);
                donor.roundsDelivered+=amount;
            }
            case TORPEDO -> {
                int amount=(int)quantity;
                donor.torpedoAmmo-=amount;
                recipient.torpedoAmmo+=amount;
                replenishLauncherMagazines(recipient,DamageType.TORPEDO,amount);
                donor.roundsDelivered+=amount;
            }
            case FUEL -> {
                donor.fuel-=quantity;recipient.fuel+=quantity;
                donor.fuelDelivered+=quantity;
            }
        }
        return quantity;
    }
    private static void replenishLauncherMagazines(Ship ship,DamageType type,int rounds){
        for(int i=0;i<rounds;i++){
            Ship.Launcher next=null;
            for(Ship.Launcher launcher:ship.launchers)
                if(launcher.family==type && (next==null||
                        launcher.magazine<next.magazine))next=launcher;
            if(next!=null)next.magazine++;
        }
    }

    /** Screen nearby threats; protect responds to actual attack orders and last attackers. */
    private Ship protectedThreat(Ship source,boolean screen){
        Ship chosen=null;
        double best=Double.POSITIVE_INFINITY;
        java.util.ArrayList<Ship> assets=new java.util.ArrayList<>();
        Ship escort=findShip(source.escortShipId);
        if(escort!=null&&escort.alive())assets.add(escort);
        for(int id:source.defendedShipIds){
            Ship friend=findShip(id);
            if(friend!=null&&friend.alive())assets.add(friend);
        }
        if(assets.isEmpty())assets.add(source);
        for(Ship enemy:ships)if(enemy.team!=source.team&&enemy.alive()){
            for(Ship asset:assets){
                double distance=StrictMath.hypot(enemy.x-asset.x,enemy.y-asset.y);
                boolean attacking=enemy.lastTargetId==asset.id||asset.lastAttackerId==enemy.id;
                if(!attacking&&(!screen||distance>300))continue;
                double score=distance-(attacking?320:0);
                if(score<best){best=score;chosen=enemy;}
            }
        }
        return chosen;
    }
    private void launchAuxiliary(Ship ship, Ship enemy) {
        if(ship.crewReadiness()<0.05 || ship.hangarCondition<0.20
                || ship.power<7 || ship.heat>ship.maxHeat*0.9)return;
        boolean withdrawing=ship.withdrawing||
                (ship.standingOrders!=null&&ship.standingOrders.mustWithdraw(ship));
        // A withdrawing captain conserves fighter and attack-drone sorties.
        // Repair drones and actual point defense remain independently operational.
        Ship fighterTarget=enemy;
        var fighterPlan=ship.standingOrders==null?
                ShipTacticalDirectives.FighterMission.STRIKE:
                ship.standingOrders.directives().fighterMission();
        if(fighterPlan==ShipTacticalDirectives.FighterMission.SCREEN||
                fighterPlan==ShipTacticalDirectives.FighterMission.PROTECT)
            fighterTarget=protectedThreat(ship,fighterPlan==
                    ShipTacticalDirectives.FighterMission.SCREEN);
        if(!withdrawing&&fighterPlan!=ShipTacticalDirectives.FighterMission.RESERVE&&
                fighterTarget!=null&&ship.fighterReserve>0 &&
                ship.fighterLaunchBays>0&&ship.fighterReload<=0&&crafts.size()<2000) {
            // Each physical flight deck can launch one aircraft per sortie
            // cycle. An auxiliary hangar contributes one; true carriers have
            // multiple built-in decks. All launches pay their own power and
            // heat cost, and never exceed the available inventory or craft cap.
            int slots=Math.min(ship.fighterReserve,2000-crafts.size());
            int launched=0;
            for(Ship.HangarBay bay:ship.fighterBays){
                if(launched>=slots)break;
                if(!bay.operational())continue;
                if(ship.power<7||ship.heat+3>ship.maxHeat*0.9)break;
                Craft launchedCraft=new Craft(nextCraftId++,ship,true,fighterTarget.id);
                launchedCraft.launchBayId=bay.id;
                crafts.add(launchedCraft);
                visual(nextVisualShotId++,VisualPhase.FIRE,"FIGHTER",
                        ship.team,ship.id,fighterTarget.id,
                        ship.x,ship.y,fighterTarget.x,fighterTarget.y);
                ship.fighterReserve--;ship.fightersLaunched++;launched++;
                ship.power-=7;ship.heat+=3;
            }
            if(launched>0){
                ship.fighterReload=4.5/ship.hangarCondition;
                radio(ship,CrewChatter.Event.LAUNCH);
            }
        }
        if(!withdrawing&&ship.droneReserve>0 && ship.droneReload<=0 && crafts.size()<2000){
            crafts.add(new Craft(nextCraftId++,ship,false,enemy==null?0:enemy.id));
            visual(nextVisualShotId++,VisualPhase.FIRE,"DRONE",ship.team,ship.id,
                    enemy==null?0:enemy.id,ship.x,ship.y,ship.x,ship.y);
            ship.droneReserve--;ship.dronesLaunched++;ship.droneReload=5.5/ship.hangarCondition;
            ship.power-=5;ship.heat+=2;
        }
        if(ship.repairDroneReserve>0 && ship.repairDroneReload<=0
                && ship.power>=5 && crafts.size()<2000){
            Ship needy=mostDamagedAlly(ship,250,true);
            if(needy!=null){
                crafts.add(new Craft(nextCraftId++,ship,false,true,needy.id));
                visual(nextVisualShotId++,VisualPhase.FIRE,"REPAIR_DRONE",
                        ship.team,ship.id,needy.id,ship.x,ship.y,needy.x,needy.y);
                ship.repairDroneReserve--;ship.repairDronesLaunched++;
                ship.repairDroneReload=5.5/ship.hangarCondition;
                ship.power-=5;ship.heat+=2;
            }
        }
        // New dedicated support hangars launch reusable finite-charge drones,
        // not weapons. One launch controller per vessel prevents 400-ship spam.
        if(ship.power>=5 && ship.repairDroneReload<=0&&crafts.size()<2000)
            for(SupportDoctrine.Emitter emitter:ship.logistics.drones()){
                int channel=emitter.channel().ordinal();
                if(ship.serviceDroneReserve[channel]<=0)continue;
                Ship needy=serviceTarget(ship,emitter.channel(),emitter.range(),true);
                if(needy==null)continue;
                crafts.add(new Craft(nextCraftId++,ship,emitter,needy.id));
                ship.serviceDroneReserve[channel]--;
                ship.repairDronesLaunched++;
                ship.repairDroneReload=5.5/ship.hangarCondition;
                ship.power-=5;ship.heat+=2;
                visual(nextVisualShotId++,VisualPhase.FIRE,"SERVICE_DRONE",
                        ship.team,ship.id,needy.id,ship.x,ship.y,needy.x,needy.y);
                break;
            }
    }

    /** Small craft maintain independent sensor locks and fire real projectiles. */
    private void updateArmedFighter(Craft craft){
        FighterModel.Profile profile=craft.profile;
        craft.reload=Math.max(0,craft.reload-DT);
        Craft intercept=null;
        if(profile.antiCraft()){
            double best=Double.POSITIVE_INFINITY;
            for(Craft enemy:crafts)if(enemy.team!=craft.team&&enemy.integrity>0){
                double distance=StrictMath.hypot(enemy.x-craft.x,enemy.y-craft.y);
                double detected=ProjectileModel.acquisitionRange(profile.sensors(),1,
                        weather.sensorFactor(),ProjectileModel.STANDARD_PD,enemy.radarProfile());
                if(distance<=detected && distance<best){intercept=enemy;best=distance;}
            }
            if(intercept!=null)craft.targetCraftId=intercept.id;
        }
        if(intercept==null)craft.targetCraftId=0;
        Ship target=null;
        if(intercept==null) {
            target=findShip(craft.targetId);
            if(target==null||!target.alive()){
                Ship source=findShip(craft.sourceId);
                target=source==null?null:chooseTarget(source);
                craft.targetId=target==null?0:target.id;
            }
            if(target!=null){
                double range=StrictMath.hypot(target.x-craft.x,target.y-craft.y);
                // Carrier datalink supplies a coarse off-sensor intercept course.
                // Local fighter sensors govern actual lock and firing below.
                if(range>Math.max(600,profile.sensors()*weather.sensorFactor()*2))target=null;
            }
        }
        if(target==null&&intercept==null)return;
        double tx=intercept!=null?intercept.x:target.x;
        double ty=intercept!=null?intercept.y:target.y;
        double distance=StrictMath.hypot(tx-craft.x,ty-craft.y);
        // Standoff craft do not fly through targets at their maximum thrust.
        double desired=profile.range()*0.68;
        if(distance>desired && distance>1){
            double movement=Math.min(distance-desired,profile.speed()*DT);
            craft.x+=(tx-craft.x)/distance*movement;
            craft.y+=(ty-craft.y)/distance*movement;
        }
        if(craft.reload>0 || distance>profile.range()
                || (target!=null && distance>DeceptionProjection.fighterContactRange(
                        target,profile.sensors(),weather.sensorFactor()))
                || (intercept!=null && distance>profile.sensors()*weather.sensorFactor()))
            return;
        if(profile.ammunition()>0&&craft.ammunition<=0)return;
        Ship carrier=findShip(craft.sourceId);
        double accuracy=profile.accuracy()*weather.accuracyFactor();
        if(target!=null)accuracy*=(1-target.ecmStrength*target.ecmCondition)
                *DeceptionProjection.lockMultiplier(target,1.0);
        long shot=nextVisualShotId++;
        String shotName="FIGHTER_"+profile.weapon().name();
        double observedShotX=tx,observedShotY=ty;
        if(profile.ammunition()>0)craft.ammunition--;
        craft.reload=profile.cooldown();
        craft.shotsFired++;
        if(craft.shotsFired==1&&carrier!=null)
            radioFighter(carrier,CrewChatter.Event.FIGHTER_ATTACK);
        if(profile.weapon()==FighterModel.Weapon.CANNON){
            double heading=StrictMath.atan2(ty-craft.y,tx-craft.x);
            if(random.nextDouble()>accuracy){
                int ghosts=target==null?0:DeceptionProjection.falseContacts(target);
                if(ghosts>0){
                    target.holoShotsDiverted++;
                    int index=(int)Math.floorMod(shot,ghosts);
                    heading=StrictMath.atan2(
                            DeceptionProjection.ghostY(target,index,tick)-craft.y,
                            DeceptionProjection.ghostX(target,index,tick)-craft.x);
                }else heading+=(shot%2==0?0.32:-0.32);
            }
            // FIRE endpoints must match the actual physical cannon bearing,
            // including when a holo contact is fooling the gunner.
            observedShotX=craft.x+StrictMath.cos(heading)*distance;
            observedShotY=craft.y+StrictMath.sin(heading)*distance;
            // Kinetic shot has its own flight time, integrity and swept collision.
            ProjectileModel.Spec spec=ProjectileModel.module(
                    carrier==null?Faction.TERRAN:carrier.faction,
                    ProjectileModel.Kind.KINETIC,0.70,1,0.80);
            ballistics.add(new Ballistic(shot,tick,craft.sourceId,craft.team,
                    target==null?0:target.id,craft.x,craft.y,heading,profile.damage(),spec));
            fighterGunShots++;
        } else if(profile.weapon()==FighterModel.Weapon.LASER
                || profile.weapon()==FighterModel.Weapon.PARTICLE){
            DamageType kind=profile.weapon()==FighterModel.Weapon.LASER
                    ?DamageType.LASER:DamageType.PARTICLE;
            boolean landed=random.nextDouble()<accuracy;
            double beamX=tx,beamY=ty;
            if(!landed&&target!=null){
                int ghosts=DeceptionProjection.falseContacts(target);
                if(ghosts>0){
                    target.holoShotsDiverted++;
                    int index=(int)Math.floorMod(shot,ghosts);
                    beamX=DeceptionProjection.ghostX(target,index,tick);
                    beamY=DeceptionProjection.ghostY(target,index,tick);
                }else{
                    beamX+=shot%2==0?35:-35;
                    beamY+=shot%3==0?35:-35;
                }
            }
            beams.add(new Beam(shot,tick,craft.x,craft.y,beamX,beamY,craft.team,kind));
            observedShotX=beamX;observedShotY=beamY;
            if(landed){
                if(intercept!=null)hitCraft(intercept,profile.damage(),carrier,shot,shotName);
                else hit(target,profile.damage(),carrier,kind,shot,tx,ty);
            }
            if(kind==DamageType.LASER)fighterLaserShots++;else fighterParticleShots++;
        } else {
            // Missile/torpedo bombers launch real guided, damageable payloads.
            if(target==null)return;
            boolean heavy=profile.weapon()==FighterModel.Weapon.TORPEDO;
            ProjectileModel.Spec spec=ProjectileModel.module(
                    carrier==null?Faction.TERRAN:carrier.faction,
                    heavy?ProjectileModel.Kind.TORPEDO:ProjectileModel.Kind.MISSILE,
                    0.84,1,0.88);
            missiles.add(new Missile(shot,tick,craft.sourceId,craft.team,target.id,
                    craft.x,craft.y,target.x-craft.x,target.y-craft.y,
                    profile.damage(),heavy,spec,1));
            if(heavy)fighterTorpedoesLaunched++;else fighterMissilesLaunched++;
        }
        visual(shot,VisualPhase.FIRE,shotName,craft.team,craft.sourceId,
                target==null?0:target.id,craft.x,craft.y,observedShotX,observedShotY);
    }

    private void hitCraft(Craft target,double damage,Ship shooter,long shot,String weapon){
        if(target.integrity<=0)return;
        double absorbed=Math.min(target.armor,damage*0.48);
        target.armor-=absorbed;
        target.integrity-=Math.max(0,damage-absorbed);
        visual(shot,VisualPhase.IMPACT,weapon,shooter==null?-1:shooter.team,
                shooter==null?0:shooter.id,target.sourceId,target.x,target.y,target.x,target.y);
    }

    private void updateCraft() {
        for(int i=crafts.size()-1;i>=0;i--) {
            Craft craft=crafts.get(i);
            if((craft.fuel-=DT)<=0 || craft.integrity<=0){
                if(craft.integrity<=0)craftLosses++;
                crafts.remove(i);continue;
            }
            if(craft.fighter) {
                updateArmedFighter(craft);
            } else if(craft.repair){
                Ship source=findShip(craft.sourceId);
                if(source==null||!source.alive()){crafts.remove(i);continue;}
                // Finite charge and fuel mandate return to the owning carrier.
                if(craft.repairReserve<=1e-8||craft.fuel<9)craft.returning=true;
                if(craft.returning){
                    double dx=source.x-craft.x,dy=source.y-craft.y;
                    double distance=StrictMath.hypot(dx,dy);
                    // Keep docking tolerance wider than the movement stop
                    // radius; subpixel floating point residuals cannot trap a
                    // fully depleted craft forever just outside its carrier.
                    if(distance>18){
                        double advance=StrictMath.min(distance-12,220*DT);
                        if(distance>0){
                            craft.x+=dx/distance*advance;
                            craft.y+=dy/distance*advance;
                        }
                    }else{
                        // Dock, pay for every new service charge, replenish fuel
                        // and free a launch slot. Without energy, wait aboard.
                        double cost=StrictMath.max(12,craft.serviceMaxCharge*0.36);
                        if(source.power>=cost+5){
                            source.power-=cost;source.heat+=cost*0.17;
                            craft.repairReserve=craft.serviceMaxCharge;
                            craft.fuel=52;
                            source.serviceDronesRearmed++;
                            if(craft.serviceRange>300)
                                source.serviceDroneReserve[craft.serviceChannel.ordinal()]++;
                            else source.repairDroneReserve++;
                            crafts.remove(i);
                        }else craft.fuel=StrictMath.max(1,craft.fuel);
                    }
                    continue;
                }
                Ship target=findShip(craft.targetId);
                if(target==null||!target.alive()||target.team!=craft.team
                        ||serviceNeed(target,craft.serviceChannel)<=1e-8){
                    target=serviceTarget(source,craft.serviceChannel,
                            craft.serviceRange,true);
                    craft.targetId=target==null?0:target.id;
                }
                if(target==null){craft.returning=true;continue;}
                double dx=target.x-craft.x,dy=target.y-craft.y;
                double distance=StrictMath.hypot(dx,dy);
                if(distance>25){
                    double advance=StrictMath.min(distance-25,145*DT);
                    if(distance>0){
                        craft.x+=dx/distance*advance;
                        craft.y+=dy/distance*advance;
                    }
                }
                craft.reload=StrictMath.max(0,craft.reload-DT);
                if(StrictMath.hypot(target.x-craft.x,target.y-craft.y)<=29
                        &&craft.reload<=0&&craft.repairReserve>0){
                    double actual=deliverSupport(source,target,craft.serviceChannel,
                            StrictMath.min(craft.repairReserve,craft.servicePulse),
                            1,false);
                    if(actual>0){
                        craft.repairReserve-=actual;
                        SupportKind kind=switch(craft.serviceChannel){
                            case HULL -> SupportKind.DRONE;
                            case SHIELD -> SupportKind.DRONE_SHIELD;
                            case ARMOR -> SupportKind.DRONE_ARMOR;
                            case POWER -> SupportKind.DRONE_POWER;
                        };
                        supportLinks.add(new SupportLink(source,target,kind,
                                tick,craft.x,craft.y));
                    }
                    craft.reload=0.8;
                }
            } else {
                Ship controller=findShip(craft.sourceId);
                ProjectileModel.Defense suite=controller==null?ProjectileModel.STANDARD_PD
                        :controller.pointDefenseProfile;
                double sensor=controller==null?1:controller.sensorsCondition;
                Missile missileTarget=null;Ballistic shellTarget=null;Rocket rocketTarget=null;
                double nearest=Double.POSITIVE_INFINITY;
                for(Missile m:missiles)if(m.team!=craft.team){
                    double d=StrictMath.hypot(m.x-craft.x,m.y-craft.y);
                    double detection=ProjectileModel.acquisitionRange(150,sensor,
                            weather.sensorFactor(),suite,m.spec,
                            OrdnancePropulsion.emissionMultiplier(m.thrustPhase));
                    if(d<nearest&&d<=detection){
                        nearest=d;missileTarget=m;shellTarget=null;rocketTarget=null;
                    }
                }
                for(Ballistic p:ballistics)if(p.team!=craft.team){
                    double d=StrictMath.hypot(p.x-craft.x,p.y-craft.y);
                    double detection=ProjectileModel.acquisitionRange(150,sensor,
                            weather.sensorFactor(),suite,p.spec);
                    if(d<nearest&&d<=detection){
                        nearest=d;shellTarget=p;missileTarget=null;rocketTarget=null;
                    }
                }
                for(Rocket p:rockets)if(p.team!=craft.team){
                    double d=StrictMath.hypot(p.x-craft.x,p.y-craft.y);
                    double detection=ProjectileModel.acquisitionRange(150,sensor,
                            weather.sensorFactor(),suite,p.spec,
                            RocketBarrage.hotEmission(p.motor,p.burning()));
                    if(d<nearest&&d<=detection){
                        nearest=d;rocketTarget=p;missileTarget=null;shellTarget=null;
                    }
                }
                if(missileTarget!=null||shellTarget!=null||rocketTarget!=null){
                    double x=missileTarget!=null?missileTarget.x:
                            shellTarget!=null?shellTarget.x:rocketTarget.x;
                    double y=missileTarget!=null?missileTarget.y:
                            shellTarget!=null?shellTarget.y:rocketTarget.y;
                    if(nearest>0){
                        craft.x+=(x-craft.x)/nearest*205*DT;
                        craft.y+=(y-craft.y)/nearest*205*DT;
                    }
                    if(StrictMath.hypot(x-craft.x,y-craft.y)<28){
                        ProjectileModel.Spec incoming=missileTarget!=null
                                ?missileTarget.spec:shellTarget!=null?
                                shellTarget.spec:rocketTarget.spec;
                        if(random.nextDouble()<ProjectileModel.interceptionChance(
                                incoming,suite,controller==null?1:controller.pointDefenseCondition,
                                weather.accuracyFactor(),missileTarget!=null?
                                StrictMath.hypot(missileTarget.vx,missileTarget.vy):
                                incoming.speed())){
                        if(missileTarget!=null){
                            missileTarget.integrity-=Math.max(3,suite.damage());
                            if(missileTarget.integrity<=0 && missiles.remove(missileTarget)){
                                missileIntercepts++;
                                visual(missileTarget.visualId,VisualPhase.INTERCEPT,
                                        missileTarget.torpedo?"TORPEDO":"MISSILE",
                                        missileTarget.team,missileTarget.sourceId,missileTarget.targetId,
                                        missileTarget.x,missileTarget.y,missileTarget.x,missileTarget.y);
                            }
                        }else if(rocketTarget!=null){
                            rocketTarget.integrity-=Math.max(3,suite.damage());
                            if(rocketTarget.integrity<=0&&rockets.remove(rocketTarget)){
                                rocketIntercepts++;
                                visual(rocketTarget.visualId,VisualPhase.INTERCEPT,"ROCKET",
                                        rocketTarget.team,rocketTarget.sourceId,rocketTarget.targetId,
                                        rocketTarget.x,rocketTarget.y,rocketTarget.x,rocketTarget.y);
                            }
                        }else{
                            shellTarget.integrity-=Math.max(3,suite.damage());
                            if(shellTarget.integrity<=0 && ballistics.remove(shellTarget)){
                                ballisticIntercepts++;
                                visual(shellTarget.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                        shellTarget.team,shellTarget.sourceId,shellTarget.targetId,
                                        shellTarget.x,shellTarget.y,shellTarget.x,shellTarget.y);
                            }
                        }
                        }
                        crafts.remove(i);
                    }
                } else {
                    Ship source=findShip(craft.sourceId);
                    if(source!=null){
                        double dx=source.x-craft.x,dy=source.y-craft.y;
                        double distance=StrictMath.hypot(dx,dy);
                        if(distance>35){craft.x+=dx/distance*95*DT;craft.y+=dy/distance*95*DT;}
                    }
                }
            }
        }
    }

    /** Returns the first collision fraction in [0,1], or infinity if missed. */
    private static double segmentHit(double ax,double ay,double bx,double by,
                                     double cx,double cy,double radius) {
        double dx=bx-ax,dy=by-ay;
        double t=(dx*dx+dy*dy)<1e-10?0:
                Math.max(0,Math.min(1,((cx-ax)*dx+(cy-ay)*dy)/(dx*dx+dy*dy)));
        double closestX=ax+dx*t,closestY=ay+dy*t;
        if(StrictMath.hypot(cx-closestX,cy-closestY)>radius)return Double.POSITIVE_INFINITY;
        // Entry time, not nearest point: prevents projectiles passing through
        // two ships and hitting whichever happens to be listed first.
        double a=dx*dx+dy*dy;
        if(a<1e-10)return 0;
        double ox=ax-cx,oy=ay-cy;
        double discriminant=StrictMath.pow(dx*ox+dy*oy,2)-a*(ox*ox+oy*oy-radius*radius);
        if(discriminant<0)return t;
        return Math.max(0,Math.min(1,(-(dx*ox+dy*oy)-StrictMath.sqrt(discriminant))/a));
    }

    /** Exact swept relative flight for TWO moving bodies in the same tick.
     * Translate the projectile by the target's measured linear displacement.
     * An end-of-tick point-only check misses genuine high-speed crossings. */
    static double movingProjectileEntry(double shotX,double shotY,
                                        double nextShotX,double nextShotY,
                                        double targetOldX,double targetOldY,
                                        double targetNewX,double targetNewY,
                                        double tickFraction,double radius){
        if(!Double.isFinite(tickFraction)||tickFraction<0||tickFraction>1||
                !Double.isFinite(radius)||radius<=0)
            throw new IllegalArgumentException("Invalid swept projectile frame");
        double finishX=targetOldX+(targetNewX-targetOldX)*tickFraction;
        double finishY=targetOldY+(targetNewY-targetOldY)*tickFraction;
        return segmentHit(shotX-targetOldX,shotY-targetOldY,
                nextShotX-finishX,nextShotY-finishY,0,0,radius);
    }

    /** Entirely inertial flight, after straight-line acceleration. No seeker
     * update, target telemetry, retroburn, homing, or impact oracle. */
    private void updateRockets(){
        for(int i=rockets.size()-1;i>=0;i--){
            Rocket rocket=rockets.get(i);
            if((rocket.life-=DT)<=0 ||
                    tick-rocket.spawnTick>BattleArena.MAX_ROCKET_SECONDS/DT ||
                    !BattleArena.contains(rocket.x,rocket.y)){
                visual(rocket.visualId,VisualPhase.TIMEOUT,"ROCKET",
                        rocket.team,rocket.sourceId,rocket.targetId,
                        rocket.x,rocket.y,rocket.x,rocket.y);
                rockets.remove(i);continue;
            }
            double previousSpeed=StrictMath.hypot(rocket.vx,rocket.vy);
            double speed=RocketBarrage.nextSpeed(
                    rocket.size,rocket.motor,rocket.flightTicks++,
                    previousSpeed,rocket.spec.speed(),
                    rocket.sustainedThrustFactor*
                            diagnosticSustainedRocketThrustMultiplier);
            rocket.vx=StrictMath.cos(rocket.heading)*speed;
            rocket.vy=StrictMath.sin(rocket.heading)*speed;
            double nx=rocket.x+rocket.vx*DT,ny=rocket.y+rocket.vy*DT;
            Ship hit=null;double earliest=Double.POSITIVE_INFINITY;
            for(Ship enemy:ships)if(enemy.team!=rocket.team&&enemy.alive()){
                double contact=segmentHit(rocket.x,rocket.y,nx,ny,
                        enemy.x,enemy.y,12*enemy.visualScale());
                if(contact<earliest){earliest=contact;hit=enemy;}
            }
            if(hit!=null){
                rocket.x+=(nx-rocket.x)*earliest;
                rocket.y+=(ny-rocket.y)*earliest;
                hitPayload(hit,rocket.damage,findShip(rocket.sourceId),
                        DamageType.MISSILE,rocket.visualId,rocket.x,rocket.y,
                        AmmunitionTable.warhead(AmmunitionTable.Warhead.HIGH_EXPLOSIVE));
                rocketHits++;
                rockets.remove(i);
            }else {
                rocket.x=nx;rocket.y=ny;
                if(!BattleArena.contains(nx,ny)){
                    rocket.x=BattleArena.x(nx);rocket.y=BattleArena.y(ny);
                    visual(rocket.visualId,VisualPhase.TIMEOUT,"ROCKET",
                            rocket.team,rocket.sourceId,rocket.targetId,
                            rocket.x,rocket.y,rocket.x,rocket.y);
                    rockets.remove(i);
                }
            }
        }
    }
    private void updateBallistics() {
        for(int i=ballistics.size()-1;i>=0;i--){
            Ballistic p=ballistics.get(i);
            if((p.life-=DT)<=0 ||
                    tick-p.spawnTick>BattleArena.MAX_BALLISTIC_SECONDS/DT ||
                    !BattleArena.contains(p.x,p.y)){
                ballistics.remove(i);
                visual(p.visualId,VisualPhase.TIMEOUT,"KINETIC",p.team,p.sourceId,
                        p.targetId,p.x,p.y,p.x,p.y);
                continue;
            }
            double nextX=p.x+p.vx*DT,nextY=p.y+p.vy*DT;
            Ship closest=null;double earliest=Double.POSITIVE_INFINITY;
            for(Ship candidate:ships){
                if(candidate.team==p.team||!candidate.alive())continue;
                double contact=segmentHit(p.x,p.y,nextX,nextY,candidate.x,candidate.y,
                        12*candidate.visualScale());
                if(contact<earliest){earliest=contact;closest=candidate;}
            }
            Craft closestCraft=null;
            if(p.team==0||p.team==1)for(Craft craft:crafts)
                if(craft.team!=p.team&&craft.integrity>0){
                    double contact=segmentHit(p.x,p.y,nextX,nextY,craft.x,craft.y,
                            craft.fighter?5.5:4.2);
                    if(contact<earliest){earliest=contact;closest=null;closestCraft=craft;}
                }
            if(closestCraft!=null) {
                p.x+=(nextX-p.x)*earliest;p.y+=(nextY-p.y)*earliest;
                hitCraft(closestCraft,p.damage,findShip(p.sourceId),p.visualId,"KINETIC");
                ballistics.remove(i);
            }else if(closest!=null){
                p.x+=(nextX-p.x)*earliest;p.y+=(nextY-p.y)*earliest;
                hitPayload(closest,p.damage,findShip(p.sourceId),DamageType.KINETIC,
                        p.visualId,p.x,p.y,AmmunitionTable.kinetic(p.ammunition));
                ballistics.remove(i);
            }else{
                p.x=nextX;p.y=nextY;
                if(!BattleArena.contains(nextX,nextY)){
                    p.x=BattleArena.x(nextX);p.y=BattleArena.y(nextY);
                    visual(p.visualId,VisualPhase.TIMEOUT,"KINETIC",p.team,p.sourceId,
                            p.targetId,p.x,p.y,p.x,p.y);
                    ballistics.remove(i);
                }
            }
        }
    }

    /** Flight urgency uses the target's *actual* closing velocity rather than
     * rewarding outbound ordnance merely because it is nearby. Receding shots
     * remain legal targets after immediate threats, never acquire false negative ETAs. */
    private static double approachEta(double targetX,double targetY,
                                      double projectileX,double projectileY,
                                      double velocityX,double velocityY,double speed) {
        double dx=targetX-projectileX,dy=targetY-projectileY;
        double distance=StrictMath.hypot(dx,dy);
        if(distance<1)return 0;
        double v=StrictMath.hypot(velocityX,velocityY);
        if(v<1e-8)return Double.POSITIVE_INFINITY;
        double closing=(dx*velocityX+dy*velocityY)/distance/v*speed;
        return closing>0.1?distance/closing:1000+distance/speed;
    }
    /** Hardware detection range is never extended by tactical doctrine. */
    boolean coversThreat(Ship defender,int targetId){
        if(defender.standingOrders==null)return true;
        switch(defender.standingOrders.directives().defenseScope()){
            case ALL_ALLIES -> {return true;}
            case SELF_ONLY -> {return targetId==defender.id;}
            case PROTECTED_SHIPS -> {
                return targetId==defender.id||targetId==defender.escortShipId||
                        defender.defendedShipIds.contains(targetId);
            }
        }
        return false;
    }
    double defensiveScore(Ship defender,double distance,double eta){
        if(defender.standingOrders==null)return eta;
        return switch(defender.standingOrders.directives().interceptStrategy()){
            case EARLY_INTERCEPT -> -distance; // first detected, farthest within fitted PD range
            case CLOSE_IN -> distance;         // closest to firing hull
            case FASTEST_IMPACT -> eta;        // earliest arriving threat
        };
    }
    /** A live dispenser opens only if it survived point defense. Child bodies
     * have independent shot IDs, seekers, collisions and remaining fuel. */
    private void releaseSubmunitions(Missile parent,Ship primary) {
        int count=OrdnanceSubmunitions.children(parent.payload);
        if(count==0)return;
        carriersSplit++;
        visual(parent.visualId,VisualPhase.TIMEOUT,
                parent.torpedo?"TORPEDO":"MISSILE",parent.team,parent.sourceId,
                parent.targetId,parent.x,parent.y,parent.x,parent.y);
        // A local cluster can fan out across nearby enemy hulls. The assigned
        // target remains the fallback, so empty space never creates a projectile.
        List<Ship> candidates=new ArrayList<>();
        candidates.add(primary);
        for(Ship ship:ships)if(ship!=primary&&ship.team!=parent.team&&ship.alive()
                &&StrictMath.hypot(ship.x-primary.x,ship.y-primary.y)<=185)
            candidates.add(ship);
        int available=StrictMath.min(count,MAX_GUIDED_BODIES-missiles.size());
        ProjectileModel.Spec childSpec=OrdnanceSubmunitions.childSpec(parent.spec,parent.payload);
        OrdnanceDoctrine.Payload type=OrdnanceSubmunitions.childPayload(parent.payload);
        double heading=StrictMath.atan2(parent.vy,parent.vx);
        for(int j=0;j<available;j++){
            Ship target=candidates.get(j%candidates.size());
            // Physically separated launcher fans: not overlapping render copies.
            double fan=(j-(count-1)*.5)*(OrdnanceSubmunitions.ultra(parent.payload)?.17:.12);
            double angle=heading+fan;
            double dx=StrictMath.cos(angle),dy=StrictMath.sin(angle);
            double x=parent.x+dx*4,y=parent.y+dy*4;
            long id=nextVisualShotId++;
            Missile child=new Missile(id,tick,parent.sourceId,parent.team,target.id,
                    x,y,dx,dy,OrdnanceSubmunitions.childDamage(parent.damage,parent.payload),
                    false,childSpec,parent.guidanceQuality,type,0,parent.warhead);
            child.guidanceMode=parent.guidanceMode;
            child.approachSide=j%2==0?1:-1;
            child.setPropulsion(OrdnanceSubmunitions.ultra(parent.payload)
                    ?OrdnancePropulsion.Mode.PULSE_BURN
                    :OrdnancePropulsion.Mode.TERMINAL_SPRINT);
            child.lastKnownX=target.x;child.lastKnownY=target.y;
            child.lastKnownVX=target.vx;child.lastKnownVY=target.vy;
            child.fuel=StrictMath.min(child.fuel,StrictMath.max(2,parent.fuel));
            missiles.add(child);
            submunitionsDeployed++;
            visual(id,VisualPhase.FIRE,"MISSILE",parent.team,parent.sourceId,
                    target.id,x,y,target.x,target.y);
        }
    }

    /** Fraction of fragments physically overlapping a body near a burst.
     * The object's projected radius extends the cloud's overlap envelope.
     * The central 30% of the burst maintains full density. Outside that
     * core the fragment count falls smoothly to zero at the outer edge.
     * Used for actual damaged objects only, never a free RNG hit/miss roll. */
    static double flakFragmentCoverage(double distance,double burstRadius,
                                       double targetRadius){
        if(!Double.isFinite(distance)||distance<0||
                !Double.isFinite(burstRadius)||burstRadius<=0||
                !Double.isFinite(targetRadius)||targetRadius<0)
            throw new IllegalArgumentException("Invalid physical flak overlap");
        double overlap=(burstRadius+targetRadius-distance)/(burstRadius*.70);
        if(overlap<=0)return 0;
        if(overlap>=1)return 1;
        return overlap*overlap*(3-2*overlap);
    }

    /** A flak burst is a timed physical volume, not an instant free hit or
     * a second laser. It intersects only currently present hostile bodies. */
    private void resolveFlakBursts(){
        for(int k=pendingFlak.size()-1;k>=0;k--){
            FlakRound burst=pendingFlak.get(k);
            boolean arrived=burst.advance(DT);
            // Every shell has a fixed maximum lifetime and cannot grow the
            // arena. An outbound shell expires before its scheduled burst.
            if(burst.life<-.000001||!BattleArena.contains(burst.x,burst.y)){
                pendingFlak.remove(k);
                visual(burst.shotId,VisualPhase.TIMEOUT,"FLAK",burst.team,
                        burst.shooterId,0,burst.x,burst.y,burst.x,burst.y);
                continue;
            }
            if(!arrived)continue;
            pendingFlak.remove(k);
            // Precise arrival is evaluated from the shell's true integrated
            // position. Numerical tolerances cannot create a remote blast.
            if(StrictMath.hypot(burst.x-burst.aimX,burst.y-burst.aimY)>.001)
                throw new IllegalStateException("Flak fuze burst detached from live shell");
            Ship shooter=findShip(burst.shooterId);
            if(shooter==null)continue;
            visual(burst.shotId,VisualPhase.IMPACT,"FLAK_BURST",
                    burst.team,burst.shooterId,0,
                    burst.aimX,burst.aimY,burst.aimX,burst.aimY);
            for(int i=missiles.size()-1;i>=0;i--){
                Missile target=missiles.get(i);
                if(target.team==burst.team || 
                   StrictMath.hypot(target.x-burst.aimX,target.y-burst.aimY)>burst.radius)
                    continue;
                // A flak fragment spreads kinetic energy over an increasingly
                // hardened warhead casing; intact heavy torpedoes shrug off
                // individual fragments much more readily than micro-missiles.
                double fragmentFactor=flakFragmentFalloff?
                        flakFragmentCoverage(StrictMath.hypot(target.x-burst.aimX,
                                target.y-burst.aimY),burst.radius,0):1.0;
                target.integrity-=fragmentFactor*ProjectileModel.fragmentationPdDamage(
                        target.spec,burst.damage);
                if(target.integrity<=0){
                    missiles.remove(i);missileIntercepts++;shooter.intercepted++;
                    recordDefensiveKill(DefensiveGun.FLAK,guidedDefenseTarget(target));
                    if(target.decoy)decoysIntercepted++;
                    visual(target.visualId,VisualPhase.INTERCEPT,
                            target.decoy?"DECOY":target.torpedo?"TORPEDO":"MISSILE",
                            target.team,target.sourceId,target.targetId,
                            target.x,target.y,target.x,target.y);
                }
            }
            for(int i=rockets.size()-1;i>=0;i--){
                Rocket target=rockets.get(i);
                if(target.team==burst.team ||
                   StrictMath.hypot(target.x-burst.aimX,target.y-burst.aimY)>burst.radius)
                    continue;
                double fragmentFactor=flakFragmentFalloff?
                        flakFragmentCoverage(StrictMath.hypot(target.x-burst.aimX,
                                target.y-burst.aimY),burst.radius,0):1.0;
                target.integrity-=fragmentFactor*ProjectileModel.fragmentationPdDamage(
                        target.spec,burst.damage);
                if(target.integrity<=0){
                    rockets.remove(i);rocketIntercepts++;shooter.intercepted++;
                    recordDefensiveKill(DefensiveGun.FLAK,DefensiveTarget.ROCKET);
                    visual(target.visualId,VisualPhase.INTERCEPT,"ROCKET",
                            target.team,target.sourceId,target.targetId,
                            target.x,target.y,target.x,target.y);
                }
            }
            for(int i=ballistics.size()-1;i>=0;i--){
                Ballistic target=ballistics.get(i);
                if(target.team==burst.team ||
                   StrictMath.hypot(target.x-burst.aimX,target.y-burst.aimY)>burst.radius)
                    continue;
                // Dense inert metallic bullets have little exposed surface
                // for fragment impacts; shells should be handled by precision
                // CIWS or an actual rail interceptor, not flak shrapnel.
                double fragmentFactor=flakFragmentFalloff?
                        flakFragmentCoverage(StrictMath.hypot(target.x-burst.aimX,
                                target.y-burst.aimY),burst.radius,0):1.0;
                target.integrity-=fragmentFactor*ProjectileModel.fragmentationPdDamage(
                        target.spec,burst.damage)*.32;
                if(target.integrity<=0){
                    ballistics.remove(i);ballisticIntercepts++;shooter.intercepted++;
                    recordDefensiveKill(DefensiveGun.FLAK,DefensiveTarget.KINETIC);
                    visual(target.visualId,VisualPhase.INTERCEPT,"KINETIC",
                            target.team,target.sourceId,target.targetId,
                            target.x,target.y,target.x,target.y);
                }
            }
            for(int i=crafts.size()-1;i>=0;i--){
                Craft target=crafts.get(i);
                // Larger aircraft present more physical surface to a cloud
                // of fragments, while retaining their actual armor pool.
                double area=StrictMath.sqrt(target.radarProfile().crossSection());
                double hitRadius=burst.radius+22*area;
                if(target.team==burst.team ||
                   StrictMath.hypot(target.x-burst.aimX,target.y-burst.aimY)>hitRadius)
                    continue;
                double fragments=StrictMath.max(.65,StrictMath.min(2.4,area*2.2));
                double fragmentFactor=flakFragmentFalloff?
                        flakFragmentCoverage(StrictMath.hypot(target.x-burst.aimX,
                                target.y-burst.aimY),burst.radius,22*area):1.0;
                if(fragmentFactor<=0)continue;
                hitCraft(target,burst.damage*6.0*fragments*fragmentFactor,
                        shooter,burst.shotId,"FLAK");
                pointDefenseCraftHits++;
                if(target.integrity<=0){
                    crafts.remove(i);craftLosses++;pointDefenseCraftKills++;
                    recordDefensiveKill(DefensiveGun.FLAK,DefensiveTarget.CRAFT);
                }
            }
        }
    }

    private record FlakAim(double x,double y,double vx,double vy,
                           int sourceId,int targetId,double priority) { }

    /** Each real flak module independently loads and fires one finite shell.
     * Leading uses current physical velocity, not perfect future tracking.
     * Ballistic shells, torpedoes and craft may evade before the timed burst. */
    private void firePhysicalFlak(){
        var law=WeaponBehaviorTable.POINT_DEFENSE;
        for(Ship defender:ships){
            if(!defender.alive()||defender.flakMounts.isEmpty()||
               defender.crewReadiness()<0.05||
               defender.pointDefenseCondition<=law.minimumCondition())continue;
            for(Ship.FlakMount mount:defender.flakMounts){
                if(mount.reload>0||defender.kineticAmmo<=0||
                   defender.power<law.minimumPower()+1||
                   defender.heat>defender.maxHeat*.92||
                   pendingFlak.size()>=MAX_ACTIVE_FLAK_SHELLS)
                    continue;
                FlakAim best=null;
                double bestPriority=Double.POSITIVE_INFINITY;
                if(defender.orders.pointDefense().craft()){
                    for(Craft craft:crafts){
                        if(craft.team==defender.team||
                           !coversThreat(defender,craft.targetId))continue;
                        double d=StrictMath.hypot(craft.x-defender.x,craft.y-defender.y);
                        if(d<15 || d>ProjectileModel.acquisitionRange(
                                mount.nominalRange,defender.sensorsCondition,
                                weather.sensorFactor(),mount.profile,craft.radarProfile()))
                            continue;
                        if(d<bestPriority){
                            bestPriority=d;
                            // Flak preferentially interrupts aircraft before
                            // targeting smaller, more elusive physical bodies.
                            best=new FlakAim(craft.x,craft.y,
                                             craft.observedVx,craft.observedVy,
                                             craft.sourceId,craft.targetId,d);
                        }
                    }
                }
                // Ordnance fire-control follows the captain's ACTUAL PD
                // doctrine: earliest collision, longest-range interception
                // or close-in last-ditch screening. Aircraft remain preferred
                // when craft engagement is enabled, matching flak's role.
                if(best==null&&defender.orders.pointDefense().ordnance()){
                    for(Missile m:missiles){
                        if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
                        double d=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
                        if(d<15||d>ProjectileModel.acquisitionRange(
                                mount.nominalRange,defender.sensorsCondition,
                                weather.sensorFactor(),mount.profile,m.spec,
                                OrdnancePropulsion.emissionMultiplier(m.thrustPhase)))
                            continue;
                        double eta=approachEta(defender.x,defender.y,
                                m.x,m.y,m.vx,m.vy,StrictMath.hypot(m.vx,m.vy));
                        double priority=defensiveScore(defender,d,eta);
                        if(priority<bestPriority){bestPriority=priority;
                            best=new FlakAim(m.x,m.y,m.vx,m.vy,m.sourceId,m.targetId,priority);}
                    }
                    for(Rocket r:rockets){
                        if(r.team==defender.team||!coversThreat(defender,r.targetId))continue;
                        double d=StrictMath.hypot(r.x-defender.x,r.y-defender.y);
                        if(d<15||d>ProjectileModel.acquisitionRange(
                                mount.nominalRange,defender.sensorsCondition,
                                weather.sensorFactor(),mount.profile,r.spec,
                                RocketBarrage.hotEmission(r.motor,r.burning())))
                            continue;
                        double eta=approachEta(defender.x,defender.y,
                                r.x,r.y,r.vx,r.vy,StrictMath.hypot(r.vx,r.vy));
                        double priority=defensiveScore(defender,d,eta);
                        if(priority<bestPriority){bestPriority=priority;
                            best=new FlakAim(r.x,r.y,r.vx,r.vy,r.sourceId,r.targetId,priority);}
                    }
                    for(Ballistic p:ballistics){
                        if(p.team==defender.team||!coversThreat(defender,p.targetId))continue;
                        double d=StrictMath.hypot(p.x-defender.x,p.y-defender.y);
                        if(d<15||d>ProjectileModel.acquisitionRange(
                                mount.nominalRange,defender.sensorsCondition,
                                weather.sensorFactor(),mount.profile,p.spec))
                            continue;
                        double eta=approachEta(defender.x,defender.y,
                                p.x,p.y,p.vx,p.vy,p.spec.speed());
                        double priority=defensiveScore(defender,d,eta);
                        if(priority<bestPriority){bestPriority=priority;
                            best=new FlakAim(p.x,p.y,p.vx,p.vy,p.sourceId,p.targetId,priority);}
                    }
                }
                if(best==null)continue;
                double distance=StrictMath.hypot(best.x-defender.x,best.y-defender.y);
                double flight=distance/390.0;
                double speed=StrictMath.hypot(best.vx,best.vy);
                // Finite lead precision. A fast point-sized kinetic round
                // outruns prediction much more readily than a fighter.
                double drift=Math.min(125,Math.max(9,speed*flight*.56));
                double error=(random.nextDouble()-.5)*2*drift;
                double angle=random.nextDouble()*StrictMath.PI*2;
                double aimX=best.x+best.vx*flight+StrictMath.cos(angle)*error;
                double aimY=best.y+best.vy*flight+StrictMath.sin(angle)*error;
                long shot=nextVisualShotId++;
                WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(
                        defender,WeaponMuzzles.Family.FLAK,mount.slotId);
                visual(shot,VisualPhase.FIRE,"FLAK",defender.team,defender.id,
                       best.sourceId,muzzle.x(),muzzle.y(),aimX,aimY);
                // A physical shell consumes a real kinetic ammunition unit.
                defender.kineticAmmo--;
                defender.power-=law.powerPerShot()*1.25;
                defender.heat+=law.heatPerShot()*2;
                mount.reload=law.reload(false,
                        mount.cycle*1.8,defender.pointDefenseCondition);
                // One paid, unguided shell travels from this exact
                // authoritative socket to its already-chosen airburst point.
                // Target motion after launch cannot alter its bearing or fuze.
                pendingFlak.add(new FlakRound(defender.team,defender.id,
                        mount.slotId,shot,tick,
                        muzzle.x(),muzzle.y(),aimX,aimY,
                        18+mount.profile.tracking()*2,
                        mount.profile.damage()*.78));
            }
        }
    }

    private record RailTrack(long bodyId,double x,double y,double vx,double vy,
                             int targetId,int sourceId,ProjectileModel.Spec profile,
                             double emission) {}
    /** Physically visible ordnance tracks only: charged railguns should not
     * consume a three-cartridge penetrator against a fighter-sized target
     * unless the target physically crosses a shot's inertial path. */
    private RailTrack chooseRailContact(Ship defender,RailPointDefense.Mount gun,
                                       java.util.Map<Long,Double> committedDamage){
        RailTrack best=null;
        double bestPriority=Double.POSITIVE_INFINITY;
        if(!defender.orders.pointDefense().ordnance())return null;
        for(Missile m:missiles){
            if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
            if(committedDamage.getOrDefault(m.visualId,0.0)>=m.integrity)
                continue;
            double d=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
            double emission=OrdnancePropulsion.emissionMultiplier(m.thrustPhase);
            if(d<RailPointDefense.MIN_ENGAGEMENT_RANGE||
                    d>RailPointDefense.acquisitionRange(defender,gun,m.spec,
                            weather.sensorFactor(),emission,
                            RailPointDefense.radialClosing(defender,
                                    m.x,m.y,m.vx,m.vy)))continue;
            double eta=approachEta(defender.x,defender.y,m.x,m.y,
                    m.vx,m.vy,StrictMath.hypot(m.vx,m.vy));
            double score=defensiveScore(defender,d,
                    Double.isFinite(eta)?eta:1000+d/100);
            score-=StrictMath.min(15,m.spec.health()*2.5);
            if(score<bestPriority){
                bestPriority=score;
                best=new RailTrack(m.visualId,m.x,m.y,m.vx,m.vy,
                        m.targetId,m.sourceId,m.spec,emission);
            }
        }
        for(Rocket m:rockets){
            if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
            if(committedDamage.getOrDefault(m.visualId,0.0)>=m.integrity)
                continue;
            double d=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
            double emission=RocketBarrage.hotEmission(m.motor,m.burning());
            if(d<RailPointDefense.MIN_ENGAGEMENT_RANGE||
                    d>RailPointDefense.acquisitionRange(defender,gun,m.spec,
                            weather.sensorFactor(),emission,
                            RailPointDefense.radialClosing(defender,
                                    m.x,m.y,m.vx,m.vy)))continue;
            double eta=approachEta(defender.x,defender.y,m.x,m.y,
                    m.vx,m.vy,StrictMath.hypot(m.vx,m.vy));
            double score=defensiveScore(defender,d,
                    Double.isFinite(eta)?eta:1000+d/100);
            score-=StrictMath.min(15,m.spec.health()*2.5);
            if(score<bestPriority){
                bestPriority=score;
                best=new RailTrack(m.visualId,m.x,m.y,m.vx,m.vy,
                        m.targetId,m.sourceId,m.spec,emission);
            }
        }
        for(Ballistic m:ballistics){
            if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
            if(committedDamage.getOrDefault(m.visualId,0.0)>=m.integrity)
                continue;
            double d=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
            if(d<RailPointDefense.MIN_ENGAGEMENT_RANGE||
                    d>RailPointDefense.acquisitionRange(defender,gun,m.spec,
                            weather.sensorFactor(),1,
                            RailPointDefense.radialClosing(defender,
                                    m.x,m.y,m.vx,m.vy)))continue;
            double eta=approachEta(defender.x,defender.y,m.x,m.y,m.vx,m.vy,
                    StrictMath.hypot(m.vx,m.vy));
            double score=defensiveScore(defender,d,
                    Double.isFinite(eta)?eta:1000+d/100);
            score-=StrictMath.min(15,m.spec.health()*2.5);
            if(score<bestPriority){
                bestPriority=score;
                best=new RailTrack(m.visualId,m.x,m.y,m.vx,m.vy,
                        m.targetId,m.sourceId,m.spec,1);
            }
        }
        return best;
    }
    /** Charge the real fitted electromagnetic rails, mechanically track the
     * current physical body, then release a single unguided narrow projectile
     * with a long cooldown. No instant kill and no laser/rotary shared reload. */
    private void fireRailPointDefense(){
        for(Ship defender:ships){
            if(!defender.alive()||defender.railMounts.isEmpty()||
                    !defender.orders.pointDefense().ordnance()||
                    defender.crewReadiness()<.05||
                    defender.pointDefenseCondition<=.2)continue;
            // Keep expected damage reserved while the real penetrator is
            // airborne. No full-fleet scan or invented instantaneous kill.
            java.util.Map<Long,Double> committed=defender.railDamageReservations;
            for(RailPointDefense.Mount gun:defender.railMounts){
                if(defender.power<8||defender.heat>=defender.maxHeat*.88||
                        defender.kineticAmmo<RailPointDefense.CARTRIDGES_PER_SHOT||
                        railPenetrators.size()>=RailPointDefense.MAX_ACTIVE_PENETRATORS)
                    continue;
                // Precharge the real paid capacitor while standing watch,
                // NOT after a fast shell has almost reached the hull. No
                // energy is spent while captain orders HOLD_FIRE.
                double beforeCharge=gun.charge;
                gun.charge(DT);
                double charged=gun.charge-beforeCharge;
                defender.power-=1.8*charged;
                defender.heat+=.45*charged;
                gun.scans++;
                RailTrack target=chooseRailContact(defender,gun,committed);
                if(target==null)continue;
                gun.track(target.bodyId,DT);
                WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(defender,
                        WeaponMuzzles.Family.RAIL_PD,gun.slotId);
                double desired=RailPointDefense.leadAngle(muzzle.x(),muzzle.y(),
                        target.x,target.y,target.vx,target.vy);
                if(!Double.isFinite(gun.bearing))gun.bearing=defender.facing;
                double error=StrictMath.atan2(StrictMath.sin(desired-gun.bearing),
                        StrictMath.cos(desired-gun.bearing));
                double slew=StrictMath.max(.6,StrictMath.min(7.5,
                        3.5*gun.profile.tracking()*defender.pointDefenseCondition))*DT;
                gun.bearing+=StrictMath.max(-slew,StrictMath.min(slew,error));
                // Tracking the incoming body's trajectory and charging the
                // capacitor costs power even before a projectile can launch.
                defender.power-=.22*DT;defender.heat+=.14*DT;
                double remaining=StrictMath.atan2(StrictMath.sin(desired-gun.bearing),
                        StrictMath.cos(desired-gun.bearing));
                if(StrictMath.abs(remaining)>.038||!gun.ready())continue;
                double dispersion=(random.nextDouble()-.5)*.016/
                        StrictMath.max(.35,gun.profile.tracking());
                long id=nextVisualShotId++;
                double predicted=RailPointDefense.directDamage(
                        target.profile,gun.profile);
                railPenetrators.add(new RailPointDefense.Penetrator(id,tick,
                        defender.team,defender.id,gun.slotId,
                        muzzle.x(),muzzle.y(),gun.bearing+dispersion,
                        gun.nominalRange,gun.profile.damage(),target.bodyId,predicted));
                gun.fire(defender.pointDefenseCondition);
                defender.kineticAmmo-=RailPointDefense.CARTRIDGES_PER_SHOT;
                defender.power-=5.2;defender.heat+=5.4;
                committed.merge(target.bodyId,predicted,Double::sum);
                visual(id,VisualPhase.FIRE,"RAIL_PD",defender.team,defender.id,
                        target.sourceId,muzzle.x(),muzzle.y(),target.x,target.y);
            }
        }
    }
    /** Pending predicted damage ends with the actual projectile, whether
     * that shot collides with its target, hits something else, or expires. */
    private void releaseRailReservation(RailPointDefense.Penetrator shot){
        Ship shooter=findShip(shot.sourceId);
        if(shooter==null)return;
        java.util.Map<Long,Double> pending=shooter.railDamageReservations;
        double remaining=pending.getOrDefault(shot.assignedBodyId,0.0)-
                shot.reservedDamage;
        if(remaining<=1e-7)pending.remove(shot.assignedBodyId);
        else pending.put(shot.assignedBodyId,remaining);
    }
    /** Exact swept solid-penetrator collision with moving actual bodies.
     * The first world-space intersection absorbs the shot; an unhit projectile
     * remains on its fixed ballistic trajectory until physically out of range. */
    private void advanceRailPenetrators(){
        for(int i=railPenetrators.size()-1;i>=0;i--){
            RailPointDefense.Penetrator shot=railPenetrators.get(i);
            if(!BattleArena.contains(shot.x,shot.y)||
                    tick-shot.spawnTick>BattleArena.MAX_POINT_DEFENSE_SECONDS/DT){
                releaseRailReservation(shot);
                railPenetrators.remove(i);continue;
            }
            double dt=StrictMath.min(DT,shot.life);
            double nx=shot.x+shot.vx*dt,ny=shot.y+shot.vy*dt;
            double first=Double.POSITIVE_INFINITY;
            Missile missileHit=null;Rocket rocketHit=null;
            Ballistic shellHit=null;Craft craftHit=null;
            for(Missile target:missiles){
                if(target.team==shot.team)continue;
                double radius=.75+1.65*StrictMath.sqrt(target.spec.crossSection());
                double at=diagnosticRailRelativeSweep?
                        movingProjectileEntry(shot.x,shot.y,nx,ny,
                                target.previousX,target.previousY,
                                target.x,target.y,dt/DT,radius):
                        segmentHit(shot.x,shot.y,nx,ny,target.x,target.y,radius);
                if(at<first){first=at;missileHit=target;
                    rocketHit=null;shellHit=null;craftHit=null;}
            }
            for(Rocket target:rockets){
                if(target.team==shot.team)continue;
                double radius=.75+1.65*StrictMath.sqrt(target.spec.crossSection());
                double at=diagnosticRailRelativeSweep?
                        movingProjectileEntry(shot.x,shot.y,nx,ny,
                                target.previousX,target.previousY,
                                target.x,target.y,dt/DT,radius):
                        segmentHit(shot.x,shot.y,nx,ny,target.x,target.y,radius);
                if(at<first){first=at;rocketHit=target;
                    missileHit=null;shellHit=null;craftHit=null;}
            }
            for(Ballistic target:ballistics){
                if(target.team==shot.team)continue;
                double radius=.55+1.15*StrictMath.sqrt(target.spec.crossSection());
                double at=diagnosticRailRelativeSweep?
                        movingProjectileEntry(shot.x,shot.y,nx,ny,
                                target.previousX,target.previousY,
                                target.x,target.y,dt/DT,radius):
                        segmentHit(shot.x,shot.y,nx,ny,target.x,target.y,radius);
                if(at<first){first=at;shellHit=target;
                    missileHit=null;rocketHit=null;craftHit=null;}
            }
            for(Craft target:crafts){
                if(target.team==shot.team)continue;
                double radius=3.5+2.5*StrictMath.sqrt(target.radarProfile().crossSection());
                double at=diagnosticRailRelativeSweep?
                        movingProjectileEntry(shot.x,shot.y,nx,ny,
                                target.lastObservedX,target.lastObservedY,
                                target.x,target.y,dt/DT,radius):
                        segmentHit(shot.x,shot.y,nx,ny,target.x,target.y,radius);
                if(at<first){first=at;craftHit=target;
                    missileHit=null;rocketHit=null;shellHit=null;}
            }
            shot.life-=DT;
            if(Double.isFinite(first)){
                shot.x+=(nx-shot.x)*first;shot.y+=(ny-shot.y)*first;
                Ship shooter=findShip(shot.sourceId);
                if(craftHit!=null){
                    hitCraft(craftHit,shot.damage*2.4,shooter,shot.visualId,"RAIL_PD");
                    pointDefenseCraftHits++;
                    if(craftHit.integrity<=0){
                        crafts.remove(craftHit);
                        pointDefenseCraftKills++;craftLosses++;
                        recordDefensiveKill(DefensiveGun.RAIL_PD,DefensiveTarget.CRAFT);
                    }
                }else{
                    ProjectileModel.Spec signature=missileHit!=null?missileHit.spec:
                            rocketHit!=null?rocketHit.spec:shellHit.spec;
                    double depth=shot.damage*3.2*
                            (1+.15*StrictMath.min(8,signature.health()));
                    if(missileHit!=null){
                        missileHit.integrity-=depth;
                        if(missileHit.integrity<=0&&missiles.remove(missileHit)){
                            missileIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.RAIL_PD,guidedDefenseTarget(missileHit));
                            if(missileHit.decoy)decoysIntercepted++;
                            visual(missileHit.visualId,VisualPhase.INTERCEPT,
                                    missileHit.decoy?"DECOY":
                                            missileHit.torpedo?"TORPEDO":"MISSILE",
                                    missileHit.team,missileHit.sourceId,missileHit.targetId,
                                    missileHit.x,missileHit.y,missileHit.x,missileHit.y);
                        }
                    }else if(rocketHit!=null){
                        rocketHit.integrity-=depth;
                        if(rocketHit.integrity<=0&&rockets.remove(rocketHit)){
                            rocketIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.RAIL_PD,DefensiveTarget.ROCKET);
                            visual(rocketHit.visualId,VisualPhase.INTERCEPT,"ROCKET",
                                    rocketHit.team,rocketHit.sourceId,rocketHit.targetId,
                                    rocketHit.x,rocketHit.y,rocketHit.x,rocketHit.y);
                        }
                    }else{
                        shellHit.integrity-=depth;
                        if(shellHit.integrity<=0&&ballistics.remove(shellHit)){
                            ballisticIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.RAIL_PD,DefensiveTarget.KINETIC);
                            visual(shellHit.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                    shellHit.team,shellHit.sourceId,shellHit.targetId,
                                    shellHit.x,shellHit.y,shellHit.x,shellHit.y);
                        }
                    }
                }
                visual(shot.visualId,VisualPhase.IMPACT,"RAIL_PD",shot.team,
                        shot.sourceId,0,shot.x,shot.y,shot.x,shot.y);
                releaseRailReservation(shot);
                railPenetrators.remove(i);
            }else{
                shot.x=nx;shot.y=ny;
                if(shot.life<=0||!BattleArena.contains(nx,ny)){
                    releaseRailReservation(shot);
                    railPenetrators.remove(i);
                }
            }
        }
    }

    private record RotaryTrack(long bodyId,double x,double y,double vx,double vy,
                               int targetId,int sourceId,ProjectileModel.Spec profile,
                               double emission) { }
    /** The fitted short-range sensor head sees true bodies, not hypothetical
     * collision percentages. Per-mount radar/IR scan, research and weather
     * gate every possible target. Captain scope and doctrine remain active. */
    private RotaryTrack pickRotaryContact(Ship defender,RotaryPointDefense.Mount gun){
        RotaryTrack best=null;
        double rank=Double.POSITIVE_INFINITY;
        if(defender.orders.pointDefense().ordnance()){
            for(Rocket body:rockets){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))continue;
                double d=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                if(d<RotaryPointDefense.MIN_RANGE||d>
                        RotaryPointDefense.acquisitionRange(defender,gun,body.spec,
                                weather.sensorFactor(),
                                RocketBarrage.hotEmission(body.motor,body.burning())))continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,
                        StrictMath.hypot(body.vx,body.vy));
                double score=defensiveScore(defender,d,eta);
                if(score<rank){rank=score;best=new RotaryTrack(body.visualId,
                        body.x,body.y,body.vx,body.vy,body.targetId,body.sourceId,
                        body.spec,RocketBarrage.hotEmission(body.motor,body.burning()));}
            }
            for(Missile body:missiles){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))continue;
                double d=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                if(d<RotaryPointDefense.MIN_RANGE||d>
                        RotaryPointDefense.acquisitionRange(defender,gun,body.spec,
                                weather.sensorFactor(),
                                OrdnancePropulsion.emissionMultiplier(body.thrustPhase)))continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,
                        StrictMath.hypot(body.vx,body.vy));
                double score=defensiveScore(defender,d,eta);
                if(score<rank){rank=score;best=new RotaryTrack(body.visualId,
                        body.x,body.y,body.vx,body.vy,body.targetId,body.sourceId,
                        body.spec,OrdnancePropulsion.emissionMultiplier(body.thrustPhase));}
            }
            // Fast tiny kinetic slugs are allowed tracks, but lead and real
            // physical collision geometry make them a poor rotary target.
            for(Ballistic body:ballistics){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))continue;
                double d=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                if(d<RotaryPointDefense.MIN_RANGE||d>
                        RotaryPointDefense.acquisitionRange(defender,gun,body.spec,
                                weather.sensorFactor(),1.0))continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,body.spec.speed());
                double score=defensiveScore(defender,d,eta);
                if(score<rank){rank=score;best=new RotaryTrack(body.visualId,
                        body.x,body.y,body.vx,body.vy,body.targetId,body.sourceId,
                        body.spec,1.0);}
            }
        }
        // Rotary point defense screens drones and fighter hulls after any
        // ordnance threatened by the same fitted firing doctrine.
        if(best==null&&defender.orders.pointDefense().craft())for(Craft body:crafts){
            if(body.team==defender.team||!coversThreat(defender,body.targetId))continue;
            gun.aircraftVisibleFrames++;
            ProjectileModel.Spec radar=body.radarProfile();
            double d=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
            if(d<RotaryPointDefense.MIN_RANGE||d>
                    RotaryPointDefense.acquisitionRange(defender,gun,radar,
                            weather.sensorFactor(),1.0))continue;
            double closingTime=approachEta(defender.x,defender.y,
                    body.x,body.y,body.observedVx,body.observedVy,
                    StrictMath.hypot(body.observedVx,body.observedVy));
            // Aircraft can hold station at gun range while firing. They
            // remain legitimate ANTI_CRAFT tracks even with zero closing
            // velocity: an infinite impact ETA is not a missing contact.
            // Keep imminent ordnance ahead of loitering aircraft without
            // changing the captain's EARLY/CLOSE/FASTEST doctrine.
            if(!Double.isFinite(closingTime))closingTime=1000+d/100.0;
            double score=defensiveScore(defender,d,closingTime);
            if(score<rank){rank=score;best=new RotaryTrack(-(long)body.id,
                    body.x,body.y,body.observedVx,body.observedVy,body.targetId,
                    body.sourceId,radar,1.0);}
        }
        return best;
    }
    /** Real spool-up and servo tracking are paid by independent installed
     * RCW barrels. Each action emits ONE physical round from its own muzzle;
     * no beam, fragment-volume damage or arbitrary instant intercept occurs. */
    private void fireRotaryCiws(){
        for(Ship defender:ships){
            if(!defender.alive()||defender.rotaryMounts.isEmpty()||
                    defender.crewReadiness()<.05||defender.pointDefenseCondition<=.20)
                continue;
            for(RotaryPointDefense.Mount gun:defender.rotaryMounts){
                if(defender.power<=4||defender.heat>=defender.maxHeat*.93||
                        defender.kineticAmmo<=0||
                        rotaryRounds.size()>=RotaryPointDefense.MAX_ACTIVE_ROUNDS)continue;
                gun.eligibleScanFrames++;
                RotaryTrack target=pickRotaryContact(defender,gun);
                if(target==null)continue;
                gun.contactFrames++;
                gun.track(DT,target.bodyId);
                WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(
                        defender,WeaponMuzzles.Family.ROTARY_CIWS,gun.slotId);
                double desired=RotaryPointDefense.leadAngle(muzzle.x(),muzzle.y(),
                        target.x,target.y,target.vx,target.vy);
                if(!Double.isFinite(gun.bearing))gun.bearing=defender.facing;
                double diff=StrictMath.atan2(StrictMath.sin(desired-gun.bearing),
                        StrictMath.cos(desired-gun.bearing));
                double servo=StrictMath.max(.8,StrictMath.min(11,
                        6.4*gun.profile.tracking()*defender.pointDefenseCondition))*DT;
                gun.bearing+=StrictMath.max(-servo,StrictMath.min(servo,diff));
                defender.power-=.10*DT;
                defender.heat+=.04*DT;
                if(StrictMath.abs(StrictMath.atan2(
                        StrictMath.sin(desired-gun.bearing),
                        StrictMath.cos(desired-gun.bearing)))>.085||!gun.ready())continue;
                double scatter=(random.nextDouble()-.5)*.034/
                        StrictMath.max(.4,gun.profile.tracking());
                long id=nextVisualShotId++;
                rotaryRounds.add(new RotaryPointDefense.Round(id,tick,defender.id,
                        defender.team,gun.slotId,muzzle.x(),muzzle.y(),
                        gun.bearing+scatter,
                        gun.profile.damage()*.98,gun.nominalRange));
                gun.fired(defender.pointDefenseCondition);
                defender.kineticAmmo--;
                defender.power-=.44;defender.heat+=.52;
                visual(id,VisualPhase.FIRE,"ROTARY_CIWS",defender.team,defender.id,
                        target.sourceId,muzzle.x(),muzzle.y(),target.x,target.y);
            }
        }
    }
    /** Inertial one-shot tracer intersection AFTER hostile bodies moved.
     * Earliest swept crossing wins: a projectile cannot kill two distant
     * targets in the same frame, and bullets continue flying after target
     * death until they physically strike another hostile body or expire. */
    private void advanceRotaryRounds(){
        for(int index=rotaryRounds.size()-1;index>=0;index--){
            RotaryPointDefense.Round shot=rotaryRounds.get(index);
            if(!BattleArena.contains(shot.x,shot.y)||
                    tick-shot.spawnTick>BattleArena.MAX_POINT_DEFENSE_SECONDS/DT){
                rotaryRounds.remove(index);continue;
            }
            double dt=StrictMath.min(DT,shot.life);
            double nextX=shot.x+shot.vx*dt,nextY=shot.y+shot.vy*dt;
            double earliest=Double.POSITIVE_INFINITY;
            Missile hitMissile=null;Rocket hitRocket=null;
            Ballistic hitShell=null;Craft hitAircraft=null;
            for(Missile body:missiles){
                if(body.team==shot.team)continue;
                double radius=1.7+2.1*StrictMath.sqrt(body.spec.crossSection());
                double at=segmentHit(shot.x,shot.y,nextX,nextY,body.x,body.y,radius);
                if(at<earliest){earliest=at;hitMissile=body;
                    hitRocket=null;hitShell=null;hitAircraft=null;}
            }
            for(Rocket body:rockets){
                if(body.team==shot.team)continue;
                double radius=1.7+2.1*StrictMath.sqrt(body.spec.crossSection());
                double at=segmentHit(shot.x,shot.y,nextX,nextY,body.x,body.y,radius);
                if(at<earliest){earliest=at;hitRocket=body;
                    hitMissile=null;hitShell=null;hitAircraft=null;}
            }
            for(Ballistic body:ballistics){
                if(body.team==shot.team)continue;
                double radius=1.0+StrictMath.sqrt(body.spec.crossSection());
                double at=segmentHit(shot.x,shot.y,nextX,nextY,body.x,body.y,radius);
                if(at<earliest){earliest=at;hitShell=body;
                    hitMissile=null;hitRocket=null;hitAircraft=null;}
            }
            for(Craft body:crafts){
                if(body.team==shot.team)continue;
                double radius=5.0+3.1*StrictMath.sqrt(body.radarProfile().crossSection());
                double at=segmentHit(shot.x,shot.y,nextX,nextY,body.x,body.y,radius);
                if(at<earliest){earliest=at;hitAircraft=body;
                    hitMissile=null;hitRocket=null;hitShell=null;}
            }
            shot.life-=DT;
            if(Double.isFinite(earliest)){
                shot.x+=(nextX-shot.x)*earliest;
                shot.y+=(nextY-shot.y)*earliest;
                Ship shooter=findShip(shot.sourceId);
                if(hitAircraft!=null){
                    hitCraft(hitAircraft,shot.damage*2.2,shooter,shot.visualId,
                            "ROTARY_CIWS");
                    pointDefenseCraftHits++;
                    if(hitAircraft.integrity<=0){
                        crafts.remove(hitAircraft);craftLosses++;pointDefenseCraftKills++;
                        recordDefensiveKill(DefensiveGun.ROTARY_CIWS,DefensiveTarget.CRAFT);
                    }
                }else{
                    ProjectileModel.Spec spec=hitMissile!=null?hitMissile.spec:
                            hitRocket!=null?hitRocket.spec:hitShell.spec;
                    double kinetic=shot.damage/
                            (1+.58*StrictMath.max(0,spec.health()-2));
                    if(hitMissile!=null){
                        hitMissile.integrity-=kinetic;
                        if(hitMissile.integrity<=0&&missiles.remove(hitMissile)){
                            missileIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.ROTARY_CIWS,guidedDefenseTarget(hitMissile));
                            if(hitMissile.decoy)decoysIntercepted++;
                            visual(hitMissile.visualId,VisualPhase.INTERCEPT,
                                    hitMissile.decoy?"DECOY":
                                    hitMissile.torpedo?"TORPEDO":"MISSILE",
                                    hitMissile.team,hitMissile.sourceId,
                                    hitMissile.targetId,hitMissile.x,hitMissile.y,
                                    hitMissile.x,hitMissile.y);
                        }
                    }else if(hitRocket!=null){
                        hitRocket.integrity-=kinetic;
                        if(hitRocket.integrity<=0&&rockets.remove(hitRocket)){
                            rocketIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.ROTARY_CIWS,DefensiveTarget.ROCKET);
                            visual(hitRocket.visualId,VisualPhase.INTERCEPT,"ROCKET",
                                    hitRocket.team,hitRocket.sourceId,
                                    hitRocket.targetId,hitRocket.x,hitRocket.y,
                                    hitRocket.x,hitRocket.y);
                        }
                    }else{
                        hitShell.integrity-=kinetic*.68;
                        if(hitShell.integrity<=0&&ballistics.remove(hitShell)){
                            ballisticIntercepts++;if(shooter!=null)shooter.intercepted++;
                            recordDefensiveKill(DefensiveGun.ROTARY_CIWS,DefensiveTarget.KINETIC);
                            visual(hitShell.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                    hitShell.team,hitShell.sourceId,hitShell.targetId,
                                    hitShell.x,hitShell.y,hitShell.x,hitShell.y);
                        }
                    }
                    visual(shot.visualId,VisualPhase.IMPACT,"ROTARY_CIWS",
                            shot.team,shot.sourceId,0,shot.x,shot.y,shot.x,shot.y);
                }
                rotaryRounds.remove(index);
            }else{
                shot.x=nextX;shot.y=nextY;
                if(shot.life<=0||!BattleArena.contains(nextX,nextY))
                    rotaryRounds.remove(index);
            }
        }
    }

    /** One real observed point-defense contact, never an anonymous RNG
     * success roll. A field is nonnull only for the physical body it names. */
    private record ParticleContact(long bodyId,double x,double y,
                                   int targetId,int sourceId,
                                   ProjectileModel.Spec profile,
                                   Missile missile,Rocket rocket,
                                   Ballistic shell,Craft craft) {}

    /** Particle optical acquisition depends on physical body area, emission,
     * weather and current hull sensor damage. Continuous beam dwell cannot
     * begin through an invisible target or borrow the ship's laser controller. */
    private ParticleContact particleContact(Ship defender,
                                             ParticlePointDefense.Mount gun){
        ParticleContact chosen=null;
        double best=Double.POSITIVE_INFINITY;
        if(defender.orders.pointDefense().ordnance()){
            for(Missile body:missiles){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))
                    continue;
                double range=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                double reach=ProjectileModel.acquisitionRange(gun.nominalRange,
                        defender.sensorsCondition,weather.sensorFactor(),gun.profile,
                        body.spec,OrdnancePropulsion.emissionMultiplier(body.thrustPhase));
                if(range>reach||range<8)continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,
                        StrictMath.hypot(body.vx,body.vy));
                double score=defensiveScore(defender,range,eta)-
                        2.5*body.spec.health();
                if(body.visualId==gun.trackedBodyId)score-=5;
                if(score<best){
                    best=score;
                    chosen=new ParticleContact(body.visualId,body.x,body.y,
                            body.targetId,body.sourceId,body.spec,
                            body,null,null,null);
                }
            }
            for(Rocket body:rockets){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))
                    continue;
                double range=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                double reach=ProjectileModel.acquisitionRange(gun.nominalRange,
                        defender.sensorsCondition,weather.sensorFactor(),gun.profile,
                        body.spec,RocketBarrage.hotEmission(body.motor,body.burning()));
                if(range>reach||range<8)continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,
                        StrictMath.hypot(body.vx,body.vy));
                double score=defensiveScore(defender,range,eta)-
                        2.5*body.spec.health();
                if(body.visualId==gun.trackedBodyId)score-=5;
                if(score<best){
                    best=score;
                    chosen=new ParticleContact(body.visualId,body.x,body.y,
                            body.targetId,body.sourceId,body.spec,
                            null,body,null,null);
                }
            }
            for(Ballistic body:ballistics){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))
                    continue;
                double range=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                double reach=ProjectileModel.acquisitionRange(gun.nominalRange,
                        defender.sensorsCondition,weather.sensorFactor(),
                        gun.profile,body.spec);
                if(range>reach||range<8)continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.vx,body.vy,
                        StrictMath.hypot(body.vx,body.vy));
                double score=defensiveScore(defender,range,eta)-
                        2.5*body.spec.health();
                if(body.visualId==gun.trackedBodyId)score-=5;
                if(score<best){
                    best=score;
                    chosen=new ParticleContact(body.visualId,body.x,body.y,
                            body.targetId,body.sourceId,body.spec,
                            null,null,body,null);
                }
            }
        }
        if(chosen==null&&defender.orders.pointDefense().craft()){
            for(Craft body:crafts){
                if(body.team==defender.team||!coversThreat(defender,body.targetId))
                    continue;
                ProjectileModel.Spec profile=body.radarProfile();
                double range=StrictMath.hypot(body.x-defender.x,body.y-defender.y);
                if(range<8||range>ProjectileModel.acquisitionRange(gun.nominalRange,
                        defender.sensorsCondition,weather.sensorFactor(),
                        gun.profile,profile))continue;
                double eta=approachEta(defender.x,defender.y,
                        body.x,body.y,body.observedVx,body.observedVy,
                        StrictMath.hypot(body.observedVx,body.observedVy));
                double score=defensiveScore(defender,range,eta);
                if(-(long)body.id==gun.trackedBodyId)score-=5;
                if(score<best){
                    best=score;
                    chosen=new ParticleContact(-(long)body.id,body.x,body.y,
                            body.targetId,body.sourceId,profile,
                            null,null,null,body);
                }
            }
        }
        return chosen;
    }

    /** Real continuous, single-body ablation. Unlike pulsed laser CIWS,
     * this channel has NO randomized instantaneous hit or shared reload.
     * Damage grows only with observed uninterrupted dwell, stops on target
     * loss or turret slew, and spends real power/heat for every beam tick.
     * Thick guided hulls retain fractional integrity across frames. */
    private void fireParticlePointDefense(){
        for(Ship defender:ships){
            if(!defender.alive()||defender.particlePdMounts.isEmpty()||
                    defender.withdrawing||defender.crewReadiness()<.05||
                    defender.pointDefenseCondition<=.20||
                    defender.orders.pointDefense()==BattleOrders.PointDefense.HOLD_FIRE)
                continue;
            for(ParticlePointDefense.Mount gun:defender.particlePdMounts){
                double tickPower=ParticlePointDefense.POWER_PER_SECOND*DT;
                double tickHeat=ParticlePointDefense.HEAT_PER_SECOND*DT;
                if(defender.power<4+tickPower){
                    gun.powerLockouts++;gun.beamDwellSeconds=0;continue;
                }
                if(defender.heat>=defender.maxHeat*.91||!gun.available()){
                    gun.beamDwellSeconds=0;continue;
                }
                ParticleContact target=particleContact(defender,gun);
                if(target==null){gun.beamDwellSeconds=0;continue;}
                WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(
                        defender,WeaponMuzzles.Family.PARTICLE_PD,gun.slotId);
                if(!gun.track(target.bodyId,target.x,target.y,
                        muzzle.x(),muzzle.y(),defender.facing,
                        defender.pointDefenseCondition,tick,DT))continue;
                // A beam without actual stored energy does zero damage.
                defender.power-=tickPower;
                defender.heat+=tickHeat;
                double damage=gun.ablationPerSecond(target.profile)*DT;
                gun.illuminate(DT);
                long shot=nextVisualShotId++;
                visual(shot,VisualPhase.FIRE,"PARTICLE_PD",
                        defender.team,defender.id,target.sourceId,
                        muzzle.x(),muzzle.y(),target.x,target.y);
                beams.add(new Beam(shot,tick,muzzle.x(),muzzle.y(),
                        target.x,target.y,defender.team,DamageType.PARTICLE,true));
                if(target.missile!=null){
                    Missile body=target.missile;
                    body.integrity-=damage;
                    if(body.integrity<=0&&missiles.remove(body)){
                        missileIntercepts++;defender.intercepted++;
                        recordDefensiveKill(DefensiveGun.PARTICLE_PD,
                                guidedDefenseTarget(body));
                        if(body.decoy)decoysIntercepted++;
                        visual(body.visualId,VisualPhase.INTERCEPT,
                                body.decoy?"DECOY":body.torpedo?"TORPEDO":"MISSILE",
                                body.team,body.sourceId,body.targetId,
                                body.x,body.y,body.x,body.y);
                    }
                }else if(target.rocket!=null){
                    Rocket body=target.rocket;
                    body.integrity-=damage*.72;
                    if(body.integrity<=0&&rockets.remove(body)){
                        rocketIntercepts++;defender.intercepted++;
                        recordDefensiveKill(DefensiveGun.PARTICLE_PD,
                                DefensiveTarget.ROCKET);
                        visual(body.visualId,VisualPhase.INTERCEPT,"ROCKET",
                                body.team,body.sourceId,body.targetId,
                                body.x,body.y,body.x,body.y);
                    }
                }else if(target.shell!=null){
                    Ballistic body=target.shell;
                    body.integrity-=damage*.48;
                    if(body.integrity<=0&&ballistics.remove(body)){
                        ballisticIntercepts++;defender.intercepted++;
                        recordDefensiveKill(DefensiveGun.PARTICLE_PD,
                                DefensiveTarget.KINETIC);
                        visual(body.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                body.team,body.sourceId,body.targetId,
                                body.x,body.y,body.x,body.y);
                    }
                }else if(target.craft!=null){
                    Craft body=target.craft;
                    hitCraft(body,damage*.52,defender,shot,"PARTICLE_PD");
                    pointDefenseCraftHits++;
                    if(body.integrity<=0&&crafts.remove(body)){
                        craftLosses++;pointDefenseCraftKills++;
                        recordDefensiveKill(DefensiveGun.PARTICLE_PD,
                                DefensiveTarget.CRAFT);
                    }
                }
            }
        }
    }

    /** Optical track servicing is shared by the installed turrets on one
     * hull. A baseline capacity of N bodies per emitter is free; additional
     * physical contacts divide available tracking cycles. We take the square
     * root to model parallel fire-control electronics rather than a serial
     * one-contact-per-tick queue. This is TIME, not hit probability. */
    static double precisionTrackLoadMultiplier(int acquired,int emitters,int channels){
        return precisionTrackLoadMultiplier(acquired,emitters,channels,.5);
    }
    static double precisionTrackLoadMultiplier(int acquired,int emitters,int channels,
                                                double exponent){
        if(acquired<0||emitters<1||channels<0||channels>16
                ||!Double.isFinite(exponent)||exponent<.25||exponent>1.25)
            throw new IllegalArgumentException("Invalid precision track-load fixture");
        if(channels==0)return 1.0;
        // Bound crowded fleet behavior: acquisition can never grow without
        // limit due solely to optical scheduling, including in 200v200 tests.
        double ratio=StrictMath.max(1.0,acquired/(double)(emitters*channels));
        return StrictMath.min(4.0,StrictMath.pow(ratio,exponent));
    }
    /** Actual fitted CIWS acquisition: turn a physical turret toward the current
     * body before opening fire. Every new target consumes continuous tracking
     * time; a hard-lock only survives while the same body remains visible on
     * consecutive simulation ticks. Legacy unfitted fixtures bypass this path.
     * Aim state is per purchased socket, not a global random miss penalty. */
    boolean acquirePrecisionTarget(Ship defender,Ship.PrecisionMount mount,
                                         long bodyId,double x,double y,long atTick){
        return acquirePrecisionTarget(defender,mount,bodyId,x,y,atTick,1.0);
    }
    boolean acquirePrecisionTarget(Ship defender,Ship.PrecisionMount mount,
                                         long bodyId,double x,double y,long atTick,
                                         double trackLoad){
        if(defender==null||mount==null||bodyId==Long.MIN_VALUE||atTick<0
                ||!Double.isFinite(trackLoad)||trackLoad<1.0)
            throw new IllegalArgumentException("Invalid precision CIWS track");
        if(mount.trackedBodyId!=bodyId || mount.lastAimTick<atTick-1){
            mount.lockSeconds=0;
            if(mount.trackedBodyId!=bodyId)mount.targetAcquisitions++;
            mount.trackedBodyId=bodyId;
        }
        if(!Double.isFinite(mount.bearing))mount.bearing=defender.facing;
        // Turret bearings are physical world angles. Normalize the shortest
        // rotation so crossing +PI/-PI does not demand a full turn.
        double desired=StrictMath.atan2(y-defender.y,x-defender.x);
        double difference=StrictMath.atan2(StrictMath.sin(desired-mount.bearing),
                                           StrictMath.cos(desired-mount.bearing));
        double angularSpeed=StrictMath.max(2.5,StrictMath.min(9.0,
                4.0*mount.profile.tracking()))*StrictMath.max(.25,defender.pointDefenseCondition);
        double advance=StrictMath.max(-angularSpeed*DT,
                StrictMath.min(angularSpeed*DT,difference));
        mount.bearing+=advance;
        double remaining=StrictMath.abs(StrictMath.atan2(
                StrictMath.sin(desired-mount.bearing),StrictMath.cos(desired-mount.bearing)));
        if(mount.lastAimTick!=atTick){
            if(remaining<.065)mount.lockSeconds+=DT;
            else {mount.lockSeconds=0;mount.slewTicks++;}
            mount.lastAimTick=atTick;
            // Small genuine servo overhead, charged only to an active tracking
            // mount. Shot energy/heat is still charged separately upon firing.
            defender.power-=.08*DT;
            defender.heat+=.025*DT;
        }
        double requiredLock=(.055+.15/StrictMath.max(.40,mount.profile.tracking()))
                *diagnosticPrecisionLockMultiplier*trackLoad;
        boolean ready=remaining<.065&&mount.lockSeconds>=requiredLock;
        if(!ready)mount.lockWaitTicks++;
        return ready;
    }

    private void updateMissiles() {
        resolveFlakBursts();
        firePhysicalFlak();
        fireRotaryCiws();
        fireRailPointDefense();
        fireParticlePointDefense();
        // One tracking decision and one firing opportunity per mount/cooldown.
        // Hostile missiles, torpedoes and slow ballistic shells share the same
        // defensive attention. Threats are ranked by approximate time-to-range.
        var pdLaw=WeaponBehaviorTable.POINT_DEFENSE;
        for(Ship defender:ships)if(defender.alive()&&defender.laserPointDefenseEnabled
                &&defender.crewReadiness()>=0.05
                &&defender.pointDefenseCondition>pdLaw.minimumCondition()
                &&defender.orders.pointDefense().ordnance()) {
            List<Ship.PrecisionMount> sockets=defender.precisionMounts.isEmpty()
                    ?java.util.Collections.singletonList(null):defender.precisionMounts;
            // Reserve expected laser damage while each turret is still
            // acquiring its target. Prevent duplicate fragile-rocket locks,
            // but allow multiple lasers to suppress hardened torpedoes.
            java.util.Map<Long,Double> reservedDamage=defender.precisionDamageReservations;
            reservedDamage.clear();
            for(Ship.PrecisionMount pdSocket:sockets){
                if(defender.power<pdLaw.minimumPower())continue;
                double pdRange=pdSocket==null?defender.pdRange:pdSocket.nominalRange;
                ProjectileModel.Defense pdProfile=pdSocket==null?
                        defender.pointDefenseProfile:pdSocket.profile;
                double pdCycle=pdSocket==null?defender.pdCycle:pdSocket.cycle;
                if(pdRange<=0)continue;
            Missile chosenMissile=null;Ballistic chosenShell=null;Rocket chosenRocket=null;
            double quickest=Double.POSITIVE_INFINITY;
            int acquiredRockets=0,acquiredTracks=0;
            for(Missile m:missiles){
                if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
                double distance=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
                double detection=ProjectileModel.acquisitionRange(
                        pdRange,defender.sensorsCondition,weather.sensorFactor(),
                        pdProfile,m.spec,
                        OrdnancePropulsion.emissionMultiplier(m.thrustPhase));
                if(distance>detection)continue;
                acquiredTracks++;
                if(!diagnosticDisablePrecisionReservations&&
                        reservedDamage.getOrDefault(m.visualId,0.0)>=m.integrity)
                    continue;
                double eta=approachEta(defender.x,defender.y,
                        m.x,m.y,m.vx,m.vy,StrictMath.hypot(m.vx,m.vy));
                if(defender.standingOrders!=null&&eta>=1000 &&
                        defender.standingOrders.directives().interceptStrategy()!=
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)continue;
                double score=defensiveScore(defender,distance,eta);
                if(pdSocket!=null&&pdSocket.trackedBodyId==m.visualId)
                    score-=StrictMath.max(.15,StrictMath.abs(score)*.12);
                if(score<quickest){
                    quickest=score;chosenMissile=m;chosenShell=null;chosenRocket=null;
                }
            }
            for(Ballistic p:ballistics){
                if(p.team==defender.team||!coversThreat(defender,p.targetId))continue;
                double distance=StrictMath.hypot(p.x-defender.x,p.y-defender.y);
                double detection=ProjectileModel.acquisitionRange(
                        pdRange,defender.sensorsCondition,weather.sensorFactor(),
                        pdProfile,p.spec);
                if(distance>detection)continue;
                acquiredTracks++;
                if(!diagnosticDisablePrecisionReservations&&
                        reservedDamage.getOrDefault(p.visualId,0.0)>=p.integrity)
                    continue;
                double eta=approachEta(defender.x,defender.y,
                        p.x,p.y,p.vx,p.vy,p.spec.speed());
                if(defender.standingOrders!=null&&eta>=1000 &&
                        defender.standingOrders.directives().interceptStrategy()!=
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)continue;
                double score=defensiveScore(defender,distance,eta);
                if(pdSocket!=null&&pdSocket.trackedBodyId==p.visualId)
                    score-=StrictMath.max(.15,StrictMath.abs(score)*.12);
                if(score<quickest){
                    quickest=score;chosenShell=p;chosenMissile=null;chosenRocket=null;
                }
            }
            // Even a coasting rocket retains a large, hot physical signature.
            // Its flimsy construction makes one accurate laser hit lethal.
            for(Rocket r:rockets){
                if(r.team==defender.team||!coversThreat(defender,r.targetId))continue;
                double distance=StrictMath.hypot(r.x-defender.x,r.y-defender.y);
                double detection=ProjectileModel.acquisitionRange(
                        pdRange,defender.sensorsCondition,weather.sensorFactor(),
                        pdProfile,r.spec,
                        RocketBarrage.hotEmission(r.motor,r.burning()));
                if(distance>detection)continue;
                acquiredTracks++;
                if(!diagnosticDisablePrecisionReservations&&
                        reservedDamage.getOrDefault(r.visualId,0.0)>=r.integrity)
                    continue;
                acquiredRockets++;
                double eta=approachEta(defender.x,defender.y,
                        r.x,r.y,r.vx,r.vy,StrictMath.hypot(r.vx,r.vy));
                if(defender.standingOrders!=null&&eta>=1000 &&
                        defender.standingOrders.directives().interceptStrategy()!=
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)continue;
                double score=defensiveScore(defender,distance,eta);
                if(pdSocket!=null&&pdSocket.trackedBodyId==r.visualId)
                    score-=StrictMath.max(.15,StrictMath.abs(score)*.12);
                if(score<quickest){
                    quickest=score;chosenRocket=r;chosenMissile=null;chosenShell=null;
                }
            }
            if(chosenMissile==null && chosenShell==null && chosenRocket==null)continue;
            double tx=chosenMissile!=null?chosenMissile.x:
                    chosenShell!=null?chosenShell.x:chosenRocket.x;
            double ty=chosenMissile!=null?chosenMissile.y:
                    chosenShell!=null?chosenShell.y:chosenRocket.y;
            long physicalId=chosenMissile!=null?chosenMissile.visualId:
                    chosenShell!=null?chosenShell.visualId:chosenRocket.visualId;
            if(pdSocket!=null){
                pdSocket.reservedOrdnanceTick=tick;
                // A fractional reservation reflects real target hardness and
                // fitted hit probability; it is NOT a free intercept/kill.
                if(!diagnosticDisablePrecisionReservations&&pdSocket.reload<.6){
                    ProjectileModel.Spec planned=chosenMissile!=null?chosenMissile.spec:
                            chosenShell!=null?chosenShell.spec:chosenRocket.spec;
                    double velocity=chosenMissile!=null?
                            StrictMath.hypot(chosenMissile.vx,chosenMissile.vy):
                            chosenRocket!=null?StrictMath.hypot(chosenRocket.vx,chosenRocket.vy):
                            planned.speed();
                    double probability=ProjectileModel.interceptionChance(planned,
                            pdProfile,defender.pointDefenseCondition,
                            weather.accuracyFactor(),velocity);
                    double promised=probability*
                            ProjectileModel.precisionPdDamage(planned,pdProfile);
                    reservedDamage.merge(physicalId,promised,Double::sum);
                }
                double trackLoad=precisionTrackLoadMultiplier(acquiredTracks,
                        StrictMath.max(1,defender.precisionMounts.size()),
                        StrictMath.min(16,diagnosticPrecisionTracksPerMount+
                                StrictMath.max(0,operationalTargetCapacity(defender)-1)),
                        diagnosticPrecisionTrackExponent);
                if(!acquirePrecisionTarget(defender,pdSocket,physicalId,tx,ty,
                        tick,trackLoad))continue;
            }
            if((pdSocket==null?defender.pdReload:pdSocket.reload)>0)continue;
            defender.power-=pdLaw.powerPerShot();defender.heat+=pdLaw.heatPerShot();
            double nextPdReload=pdLaw.reload(false,pdCycle,defender.pointDefenseCondition);
            if(pdSocket==null)defender.pdReload=nextPdReload;
            else pdSocket.reload=nextPdReload;
            int hostileSource=chosenMissile!=null?chosenMissile.sourceId:
                    chosenShell!=null?chosenShell.sourceId:chosenRocket.sourceId;
            long defensiveShot=nextVisualShotId++;
            WeaponMuzzles.Origin pdMuzzle=pdSocket==null
                    ?WeaponMuzzles.at(defender,WeaponMuzzles.Family.POINT_DEFENSE,defensiveShot)
                    :WeaponMuzzles.atSlot(defender,WeaponMuzzles.Family.POINT_DEFENSE,pdSocket.slotId);
            visual(defensiveShot,VisualPhase.FIRE,"POINT_DEFENSE",
                    defender.team,defender.id,hostileSource,pdMuzzle.x(),pdMuzzle.y(),tx,ty);
            beams.add(new Beam(defensiveShot,tick,pdMuzzle.x(),pdMuzzle.y(),
                    tx,ty,defender.team,DamageType.LASER,true));
            // Fast shells are harder to track than guided ordnance, but genuinely
            // interceptable; visual glows alone cannot be shot down.
            ProjectileModel.Spec incoming=chosenMissile!=null?chosenMissile.spec:
                    chosenShell!=null?chosenShell.spec:chosenRocket.spec;
            double chance=ProjectileModel.interceptionChance(incoming,
                    pdProfile,defender.pointDefenseCondition,
                    weather.accuracyFactor(),chosenMissile!=null?
                    StrictMath.hypot(chosenMissile.vx,chosenMissile.vy):
                    chosenRocket!=null?StrictMath.hypot(chosenRocket.vx,chosenRocket.vy):
                    incoming.speed());
            if(chosenRocket!=null&&diagnosticRocketPdPressure>0){
                int fittedPd=0;
                for(WeaponMuzzles.Emitter emitter:defender.weaponEmitters)
                    if(emitter.family()==WeaponMuzzles.Family.POINT_DEFENSE)fittedPd++;
                chance=RocketPdSaturation.chance(chance,acquiredRockets,
                        Math.max(1,fittedPd),diagnosticRocketPdPressure);
            }
            if(random.nextDouble()<chance){
                if(chosenMissile!=null){
                    chosenMissile.integrity-=ProjectileModel.precisionPdDamage(
                            chosenMissile.spec,pdProfile);
                    if(chosenMissile.integrity<=0 && missiles.remove(chosenMissile)){
                        defender.intercepted++;missileIntercepts++;
                        recordDefensiveKill(DefensiveGun.LASER_PD,guidedDefenseTarget(chosenMissile));
                        if(chosenMissile.decoy)decoysIntercepted++;
                        visual(chosenMissile.visualId,VisualPhase.INTERCEPT,
                                chosenMissile.decoy?"DECOY":chosenMissile.torpedo?"TORPEDO":"MISSILE",
                                chosenMissile.team,chosenMissile.sourceId,chosenMissile.targetId,
                                chosenMissile.x,chosenMissile.y,chosenMissile.x,chosenMissile.y);
                    }
                }else if(chosenRocket!=null){
                    chosenRocket.integrity-=ProjectileModel.precisionPdDamage(
                            chosenRocket.spec,pdProfile);
                    if(chosenRocket.integrity<=0&&rockets.remove(chosenRocket)){
                        defender.intercepted++;rocketIntercepts++;
                        recordDefensiveKill(DefensiveGun.LASER_PD,DefensiveTarget.ROCKET);
                        visual(chosenRocket.visualId,VisualPhase.INTERCEPT,"ROCKET",
                                chosenRocket.team,chosenRocket.sourceId,
                                chosenRocket.targetId,chosenRocket.x,chosenRocket.y,
                                chosenRocket.x,chosenRocket.y);
                    }
                }else{
                    chosenShell.integrity-=ProjectileModel.precisionPdDamage(
                            chosenShell.spec,pdProfile);
                    if(chosenShell.integrity<=0 && ballistics.remove(chosenShell)){
                        defender.intercepted++;ballisticIntercepts++;
                        recordDefensiveKill(DefensiveGun.LASER_PD,DefensiveTarget.KINETIC);
                        visual(chosenShell.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                chosenShell.team,chosenShell.sourceId,chosenShell.targetId,
                                chosenShell.x,chosenShell.y,chosenShell.x,chosenShell.y);
                    }
                }
            }
        }
        }
        for(Ship defender:ships)if(defender.alive()&&defender.laserPointDefenseEnabled
                &&defender.crewReadiness()>=0.05
                &&defender.pointDefenseCondition>pdLaw.minimumCondition()
                &&defender.orders.pointDefense().craft()) {
            List<Ship.PrecisionMount> sockets=defender.precisionMounts.isEmpty()
                    ?java.util.Collections.singletonList(null):defender.precisionMounts;
            for(Ship.PrecisionMount pdSocket:sockets){
                if(defender.power<=pdLaw.minimumPower()||
                   (pdSocket!=null&&pdSocket.reservedOrdnanceTick==tick))continue;
                double pdRange=pdSocket==null?defender.pdRange:pdSocket.nominalRange;
                ProjectileModel.Defense pdProfile=pdSocket==null?
                        defender.pointDefenseProfile:pdSocket.profile;
                double pdCycle=pdSocket==null?defender.pdCycle:pdSocket.cycle;
                if(pdRange<=0)continue;
            for(int i=crafts.size()-1;i>=0;i--) {
                Craft craft=crafts.get(i);
                if(craft.team==defender.team||!coversThreat(defender,craft.targetId)
                        || StrictMath.hypot(craft.x-defender.x,craft.y-defender.y)>
                        ProjectileModel.acquisitionRange(pdRange,
                                defender.sensorsCondition,weather.sensorFactor(),
                                pdProfile,craft.radarProfile()))continue;
                if(pdSocket!=null && !acquirePrecisionTarget(defender,pdSocket,
                        -(long)craft.id,craft.x,craft.y,tick))break;
                if((pdSocket==null?defender.pdReload:pdSocket.reload)>0)break;
                defender.power-=pdLaw.powerPerShot();defender.heat+=pdLaw.heatPerShot();
                // Small precision beams can retarget fragile rockets quickly,
                // but an aircraft requires a longer stable acquisition/dwell.
                // Installed barrel count grants bounded extra opportunities;
                // it does NOT turn four mounts into a 30-Hz fighter shredder.
                double standardCycle=pdLaw.reload(true,pdCycle,
                                                   defender.pointDefenseCondition);
                double aircraftDwell=pdSocket==null?0:1.2;
                double nextPdReload=StrictMath.max(standardCycle,aircraftDwell);
                if(pdSocket==null)defender.pdReload=nextPdReload;
                else pdSocket.reload=nextPdReload;
                long defensiveShot=nextVisualShotId++;
                WeaponMuzzles.Origin pdMuzzle=pdSocket==null
                    ?WeaponMuzzles.at(defender,WeaponMuzzles.Family.POINT_DEFENSE,defensiveShot)
                    :WeaponMuzzles.atSlot(defender,WeaponMuzzles.Family.POINT_DEFENSE,pdSocket.slotId);
                visual(defensiveShot,VisualPhase.FIRE,"POINT_DEFENSE",
                        defender.team,defender.id,craft.sourceId,
                        pdMuzzle.x(),pdMuzzle.y(),craft.x,craft.y);
                beams.add(new Beam(defensiveShot,tick,pdMuzzle.x(),pdMuzzle.y(),
                        craft.x,craft.y,defender.team,DamageType.LASER,true));
                ProjectileModel.Spec signature=craft.radarProfile();
                double aircraftChance=ProjectileModel.interceptionChance(signature,
                        pdProfile,defender.pointDefenseCondition,
                        weather.accuracyFactor());
                if(!defender.weaponEmitters.isEmpty()){
                    // Aircraft are maneuvering large bodies, unlike point-like
                    // rockets. A tiny precision-beam spot is harder to hold as
                    // transverse speed increases and cross-section decreases.
                    double size=StrictMath.sqrt(signature.crossSection());
                    double velocity=StrictMath.max(60,
                            StrictMath.hypot(craft.observedVx,craft.observedVy));
                    double spotHold=StrictMath.max(.10,StrictMath.min(.65,
                            .70*size/(1+velocity/95)));
                    aircraftChance*=spotHold;
                }
                if(random.nextDouble()<aircraftChance) {
                    // Precision CIWS pulses ablate tiny ordnance very well,
                    // but their narrow single-body spot does not transfer the
                    // same damage into a large aircraft frame. Unfitted legacy
                    // fixtures retain their historical combat behavior.
                    double craftScale=defender.weaponEmitters.isEmpty()?
                            pdLaw.craftDamageMultiplier():0.85;
                    hitCraft(craft,pdProfile.damage()*craftScale,
                            defender,defensiveShot,"POINT_DEFENSE");
                    pointDefenseCraftHits++;
                    if(craft.integrity<=0){
                        crafts.remove(i);craftLosses++;pointDefenseCraftKills++;
                        recordDefensiveKill(DefensiveGun.LASER_PD,DefensiveTarget.CRAFT);
                    }
                }
                break;
            }
        }
        }
        for (int i = missiles.size() - 1; i >= 0; i--) {
            Missile m = missiles.get(i);
            Ship target = findShip(m.targetId);
            if (target == null || !target.alive() || (m.fuel -= DT) <= 0
                    || tick-m.spawnTick>BattleArena.MAX_GUIDED_SECONDS/DT
                    || !BattleArena.contains(m.x,m.y)) {
                missiles.remove(i);
                visual(m.visualId,VisualPhase.TIMEOUT,m.decoy?"DECOY":m.torpedo?"TORPEDO":"MISSILE",
                        m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                continue;
            }
            // Resolve real propulsion state before maneuvering. Cold-launched
            // canisters eject slowly without thrust, then light their main
            // engine. Heavy strikes boost/coast/restart for terminal attack.
            // Current enemy geometry is used ONLY for real hull collision and
            // physical submunition proximity. Guidance and terminal burn
            // decisions consume a delayed onboard photon measurement.
            double physicalDx=target.x-m.x,physicalDy=target.y-m.y;
            double physicalRange=StrictMath.hypot(physicalDx,physicalDy);
            // Receipt uses the same physical missile worldline over the
            // preceding tick. A body launched THIS tick has no earlier
            // physical trajectory and uses its just-created receiver pose.
            OrdnanceSeeker.Return sensed=m.seekerReceiverTick==tick-1?
                    OrdnanceSeeker.passiveReturn(target,
                            m.seekerReceiverX,m.seekerReceiverY,m.x,m.y,tick):
                    OrdnanceSeeker.passiveReturn(target,m.x,m.y,tick);
            m.seekerReceiverX=m.x;m.seekerReceiverY=m.y;
            m.seekerReceiverTick=tick;
            double measuredRange=sensed!=null?sensed.distance():
                    StrictMath.hypot(m.lastKnownX-m.x,m.lastKnownY-m.y);
            m.flightTicks++;
            if(OrdnanceSubmunitions.carrier(m.payload)&&physicalRange<=m.releaseRange){
                // The parent has not reached the ship: deploying is not a hit.
                // Children appended here are advanced starting on the NEXT tick.
                missiles.remove(i);
                releaseSubmunitions(m,target);
                continue;
            }
            m.thrustPhase=OrdnancePropulsion.phase(m.propulsionMode,m.flightTicks,
                    m.ignitionDelayTicks,measuredRange,m.torpedo);
            if(m.ignitionDelayTicks>0)m.ignitionDelayTicks--;
            // A seeker cannot see current target position or current ECM
            // merely because a target entity remains alive. Relock needs
            // a real delayed sample plus the normal staggered scan.
            boolean visible=OrdnanceSeeker.canTrackReturn(
                    sensed,m.guidanceQuality,m.torpedo,!m.seekerLocked);
            if(m.seekerLocked&&!visible)m.seekerLocked=false;
            else if(!m.seekerLocked&&visible
                    &&OrdnanceSeeker.scanDue(m.flightTicks,m.visualId))
                m.seekerLocked=true;
            if(m.seekerLocked){
                int phantom=sensed==null?-1:DeceptionProjection.seekerGhostIndex(
                        target.id,sensed.profile(),m.visualId,
                        m.flightTicks,m.guidanceQuality);
                if(phantom>=0&&phantom!=m.falseContactIndex)
                    target.holoSeekersDiverted++;
                m.falseContactIndex=phantom;
                // The finite seeker must estimate both a real emitter and a
                // false emitter from the SAME historical photon arrival.
                // Physics still collides against current true geometry.
                if(sensed!=null){
                    var estimated=OrdnanceSeeker.estimate(
                            sensed,target.id,m.visualId,m.guidanceQuality,
                            phantom,target.visualScale());
                    m.lastKnownX=estimated.x();m.lastKnownY=estimated.y();
                    m.lastKnownVX=estimated.vx();m.lastKnownVY=estimated.vy();
                    m.lastObservationTick=estimated.measuredTick();
                    m.seekerUncertainty=estimated.uncertaintyRadius();
                }
            }else{
                m.falseContactIndex=-1;
                m.seekerUncertainty=StrictMath.min(120,
                        m.seekerUncertainty+DT*
                        (1+StrictMath.hypot(m.lastKnownVX,m.lastKnownVY)*.08));
            }
            if (physicalRange < 13) {
                if(m.decoy){
                    // A decoy has NO warhead and may never apply hull damage.
                    visual(m.visualId,VisualPhase.TIMEOUT,"DECOY",
                            m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                }else hitPayload(target,m.damage,findShip(m.sourceId),
                        m.torpedo?DamageType.TORPEDO:DamageType.MISSILE,m.visualId,
                        m.x,m.y,AmmunitionTable.warhead(m.warhead));
                missiles.remove(i);continue;
            }
            if (measuredRange > 0) {
                double measuredInterference=sensed==null?1:
                        sensed.profile().ecmStrength()*
                                sensed.profile().ecmCondition();
                double guidance=Math.min(0.35,WeaponBehaviorTable.gun(
                        m.torpedo?DamageType.TORPEDO:DamageType.MISSILE).guidancePerTick()
                        *m.guidanceQuality
                        *weather.guidanceFactor()*(1-measuredInterference)
                        *OrdnancePropulsion.guidanceMultiplier(m.thrustPhase)
                        *(m.seekerLocked?1:0));
                // Apply bounded thrust/steering to persistent world-space
                // velocity. Coast and cold eject preserve inertial motion.
                if(m.propellant<=1e-9&&OrdnancePropulsion.burning(m.thrustPhase))
                    m.thrustPhase=OrdnancePropulsion.Phase.COAST;
                double predictedSeconds=StrictMath.max(0,
                        tick-m.lastObservationTick)*DT;
                var steering=OrdnanceGuidance.aim(m.guidanceMode,m.x,m.y,m.vx,m.vy,
                        m.lastKnownX+m.lastKnownVX*predictedSeconds,
                        m.lastKnownY+m.lastKnownVY*predictedSeconds,
                        m.lastKnownVX,m.lastKnownVY,
                        m.flightTicks,m.approachSide);
                var nextVelocity=OrdnancePropulsion.advanceVelocity(
                        m.vx,m.vy,steering.dx(),steering.dy(),
                        m.spec.speed(),guidance,m.thrustPhase,
                        DT,m.propellant);
                double spent=StrictMath.hypot(nextVelocity.vx()-m.vx,
                        nextVelocity.vy()-m.vy);
                m.propellant=StrictMath.max(0,m.propellant-spent);
                m.vx=nextVelocity.vx();m.vy=nextVelocity.vy();
                double nextX=m.x+m.vx*DT;
                double nextY=m.y+m.vy*DT;
                // Fast module-upgraded ordnance must never tunnel through the
                // target between two 30 Hz ticks. Use the same swept test as
                // independently tracked kinetic shells.
                double contact=segmentHit(m.x,m.y,nextX,nextY,
                        target.x,target.y,13*target.visualScale());
                if(Double.isFinite(contact)){
                    m.x+=(nextX-m.x)*contact;m.y+=(nextY-m.y)*contact;
                    if(m.decoy)
                        visual(m.visualId,VisualPhase.TIMEOUT,"DECOY",
                                m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                    else hitPayload(target,m.damage,findShip(m.sourceId),
                            m.torpedo?DamageType.TORPEDO:DamageType.MISSILE,
                            m.visualId,m.x,m.y,AmmunitionTable.warhead(m.warhead));
                    missiles.remove(i);
                }else {
                    m.x=nextX;m.y=nextY;
                    if(!BattleArena.contains(nextX,nextY)){
                        m.x=BattleArena.x(nextX);m.y=BattleArena.y(nextY);
                        visual(m.visualId,VisualPhase.TIMEOUT,
                                m.decoy?"DECOY":m.torpedo?"TORPEDO":"MISSILE",
                                m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                        missiles.remove(i);
                    }
                }
            }
        }
    }
    private Ship findShip(int id) { return shipById.get(id); }

    private void hit(Ship defender, double raw, Ship attacker, DamageType kind) {
        hit(defender,raw,attacker,kind,0);
    }
    private void hit(Ship defender, double raw, Ship attacker,DamageType kind,long shotId) {
        hit(defender,raw,attacker,kind,shotId,defender.x,defender.y);
    }
    private void hit(Ship defender, double raw, Ship attacker,DamageType kind,long shotId,
                     double impactX,double impactY) {
        if (!defender.alive()) return;
        if(attacker!=null)defender.lastAttackerId=attacker.id;
        visual(shotId,VisualPhase.IMPACT,kind.name(),attacker==null?-1:attacker.team,
                attacker==null?0:attacker.id,defender.id,impactX,impactY,
                defender.x,defender.y);
        double dx=attacker==null?-StrictMath.cos(defender.facing):attacker.x-defender.x;
        double dy=attacker==null?-StrictMath.sin(defender.facing):attacker.y-defender.y;
        hitAt(defender,raw,attacker,kind,dx,dy,0,impactX,impactY);
    }
    /** Variant labels follow the shot into its actual directional damage event. */
    private void hitLaser(Ship target,double damage,Ship attacker,long shotId,
                          double hitX,double hitY,String label){
        if(!target.alive())return;
        target.lastAttackerId=attacker.id;
        visual(shotId,VisualPhase.IMPACT,label,attacker.team,attacker.id,
                target.id,hitX,hitY,target.x,target.y);
        hitAt(target,damage,attacker,DamageType.LASER,
                attacker.x-target.x,attacker.y-target.y,0,hitX,hitY);
    }
    /** Physical warheads resolve AFTER collision. Shields, tile armor, and
     * secondary blast/EMP all operate on real actual impact coordinates.
     */
    private void hitPayload(Ship defender,double raw,Ship attacker,DamageType kind,
                            long shotId,double impactX,double impactY,
                            AmmunitionTable.Profile payload){
        if(!defender.alive())return;
        if(attacker!=null)defender.lastAttackerId=attacker.id;
        visual(shotId,VisualPhase.IMPACT,kind.name(),attacker==null?-1:attacker.team,
                attacker==null?0:attacker.id,defender.id,impactX,impactY,defender.x,defender.y);
        double dx=attacker==null?-StrictMath.cos(defender.facing):attacker.x-defender.x;
        double dy=attacker==null?-StrictMath.sin(defender.facing):attacker.y-defender.y;
        hitAt(defender,raw,attacker,kind,dx,dy,payload.armorBypass(),impactX,impactY);
        if(payload.shieldDrain()>0){
            defender.defense.disruptShields(raw*payload.shieldDrain());
            defender.shields=defender.defense.totalShields();
        }
        if(payload.energyDrain()>0){
            defender.power=StrictMath.max(0,defender.power-raw*payload.energyDrain());
            defender.sensorsCondition=StrictMath.max(0,defender.sensorsCondition-
                    raw/StrictMath.max(1,defender.maxHull)*payload.energyDrain()*.16);
        }
        if(payload.blastRadius()>0){
            for(Ship nearby:ships){
                if(nearby==defender||!nearby.alive())continue;
                // Blast is indiscriminate: friendly ships also take real damage.
                double distance=StrictMath.hypot(impactX-nearby.x,impactY-nearby.y);
                if(distance>=payload.blastRadius())continue;
                double splash=raw*payload.blastFraction()
                        *(1-distance/payload.blastRadius());
                if(splash>1e-8)
                    hit(nearby,splash,attacker,kind,shotId,impactX,impactY);
            }
        }
    }
    /** Environmental impacts share directional protection with actual weapons. */
    private void hitAt(Ship defender,double raw,Ship attacker,DamageType kind,double dx,double dy) {
        hitAt(defender,raw,attacker,kind,dx,dy,0,Double.NaN,Double.NaN);
    }
    private void hitAt(Ship defender,double raw,Ship attacker,DamageType kind,
                       double dx,double dy,double armorBypass) {
        hitAt(defender,raw,attacker,kind,dx,dy,armorBypass,Double.NaN,Double.NaN);
    }
    private void hitAt(Ship defender,double raw,Ship attacker,DamageType kind,
                       double dx,double dy,double armorBypass,
                       double impactWorldX,double impactWorldY) {
        if (!defender.alive()) return;
        hitCount++;
        double c=StrictMath.cos(defender.facing),s=StrictMath.sin(defender.facing);
        double localX=dx*c+dy*s, localY=-dx*s+dy*c;
        double distance=StrictMath.hypot(localX,localY);
        if(distance<1e-9){localX=1;localY=0;distance=1;}
        double radialX=localX/distance,radialY=localY/distance;
        long salt=((long)tick<<32)^((long)defender.id*0x9e3779b97f4a7c15L)
                ^((long)hitCount*0x632be59bd9b4e019L)^kind.ordinal();
        double physicalDistance=StrictMath.hypot(impactWorldX-defender.x,
                impactWorldY-defender.y);
        boolean exactContact=Double.isFinite(physicalDistance)
                &&physicalDistance>1e-6 &&physicalDistance<=30*defender.visualScale();
        ShipDamageTargeting.Point point=exactContact
                ?ShipDamageTargeting.physical(impactWorldX-defender.x,
                        impactWorldY-defender.y,defender.facing)
                :ShipDamageTargeting.module(defender.impactHardpoints,radialX,radialY,salt);
        DirectionalDefense.Impact impact=defender.defense.impactSplash(localX,localY,
                point.x(),point.y(),raw,kind,armorBypass,ShipDamageTargeting.radius(kind));
        defender.shields=defender.defense.totalShields();
        defender.armor=defender.defense.totalArmor();
        double damage=impact.hullDamage();
        if(damage<=0)return;
        if (defender.power > 3 && defender.sif > 0 && defender.reactorCondition > 0.15) {
            double fieldAbsorb = damage * defender.sif;
            defender.power -= Math.min(defender.power, fieldAbsorb * 0.6);
            damage -= fieldAbsorb;
        }
        // Interior stores are exposed only after actual plate penetration.
        // Vault containment and armored bunkers mitigate finite stock losses.
        breachStores(defender,kind,damage,
                defender.installedSubsystems.get(point.slotIndex())==Subsystem.MAGAZINE);
        defender.hull -= damage;
        radio(defender,CrewChatter.Event.DAMAGE);
        if(defender.fittedCrewSystems){
            // Penetrating hits may kill personnel or disrupt occupied decks.
            // No random free crew is conjured when shields subsequently recharge.
            defender.crewCount=Math.max(0,defender.crewCount
                    -damage/Math.max(1,defender.maxHull)*defender.crewRequired*0.38
                    *(1-defender.crewProtection));
        }
        var secondary=WeaponBehaviorTable.damage(kind);
        if(secondary.energyDrain()>0)
            defender.power=Math.max(0,defender.power-damage*secondary.energyDrain());
        if(secondary.sensorDegradation()>0)
            defender.sensorsCondition=Math.max(0,defender.sensorsCondition-
                    damage/defender.maxHull*secondary.sensorDegradation());
        if(secondary.addedHeat()>0)
            defender.heat+=damage*secondary.addedHeat();
        if (damage > 5 && random.nextDouble() < 0.24) {
            int socket=point.slotIndex()>=0?point.slotIndex():
                    ShipDamageTargeting.nearestModule(defender.impactHardpoints,point);
            Subsystem part=defender.installedSubsystems.get(socket);
            if(part==null)part=Subsystem.values()[random.nextInt(Subsystem.values().length)];
            double loss = Math.min(0.36, damage / defender.maxHull * 0.7);
            switch (part) {
                case REACTOR: defender.reactorCondition = Math.max(0, defender.reactorCondition - loss); break;
                case ENGINES: defender.engineCondition = Math.max(0, defender.engineCondition - loss); break;
                case WEAPONS: defender.weaponsCondition = Math.max(0, defender.weaponsCondition - loss); break;
                case POINT_DEFENSE: defender.pointDefenseCondition = Math.max(0, defender.pointDefenseCondition - loss); break;
                case SENSORS: defender.sensorsCondition = Math.max(0, defender.sensorsCondition - loss); break;
                case COOLING: defender.coolingCondition = Math.max(0, defender.coolingCondition - loss); break;
                case COMMAND: defender.commandCondition = Math.max(0,
                        defender.commandCondition-loss*(1-defender.commandProtection)); break;
                case HANGAR: defender.damageFighterBay(socket,loss); break;
                case MAGAZINE: defender.magazineCondition=Math.max(0,defender.magazineCondition-loss); break;
                case ELECTRONIC_WARFARE: defender.ecmCondition=Math.max(0,defender.ecmCondition-loss); break;
                default: break;
            }
        }
        if(defender.hull<=0)destroyHull(defender,attacker);
    }
    private void radio(Battle.Ship source,CrewChatter.Event event){
        if(source==null||tick-source.lastChatterTick<90||
                source.hull<=0)return;
        source.lastChatterTick=tick;
        log(radioMessage(source,event,false),source);
    }
    private void radioFighter(Battle.Ship carrier,CrewChatter.Event event){
        if(tick-carrier.lastChatterTick<90)return;
        carrier.lastChatterTick=tick;
        log(radioMessage(carrier,event,true),carrier);
    }
    private void log(String message) { log(message,null); }
    private void log(String message,Ship origin) {
        events.add(message);
        eventSourceIds.add(origin==null?0:origin.id);
        if(events.size()>90) {events.remove(0);eventSourceIds.remove(0);}
    }

    /** Stable across machines using Java's specified Double.doubleToLongBits. */
    public long fingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, tick); hash = mix(hash, hitCount);
        hash=mix(hash,terminationReason.ordinal());
        hash=mix(hash,lastCombatProgressTick);
        hash = mix(hash, environmentalHits); hash = mix(hash, weather.preset().seed());
        hash = mix(hash,fighterGunShots);hash=mix(hash,fighterLaserShots);
        hash = mix(hash,fighterParticleShots);hash=mix(hash,fighterMissilesLaunched);
        hash = mix(hash,fighterTorpedoesLaunched);hash=mix(hash,craftLosses);
        hash = mix(hash, weather.meteorHits()); hash = mix(hash, weather.meteorMisses());
        hash = mix(hash, weather.neutronHits());
        for (SpaceWeather.Meteor meteor : weather.meteors()) {
            hash = mix(hash, meteor.targetId()); hash = mix(hash, meteor.strikeTick());
            hash = mix(hash, meteor.resolved() ? 1 : 0);
        }
        for (Ship s : ships) {
            hash = mix(hash, s.id); hash = mix(hash, Double.doubleToLongBits(s.x));
            hash = mix(hash, Double.doubleToLongBits(s.y));
            hash = mix(hash,Double.doubleToLongBits(s.vx));
            hash = mix(hash,Double.doubleToLongBits(s.vy));
            hash = mix(hash,s.propulsion.ordinal());
            hash = mix(hash,s.scan.fingerprint());
            hash = mix(hash,s.relayPackets.fingerprint());
            hash = mix(hash,s.falsePackets.fingerprint());
            hash = mix(hash,s.trackPackets.fingerprint());
            hash = mix(hash,s.targetCompute.fingerprint());
            hash = mix(hash,s.fleetCompute.fingerprint());
            hash = mix(hash,s.commandSourceId);
            hash = mix(hash, Double.doubleToLongBits(s.hull));
            hash = mix(hash, Double.doubleToLongBits(s.crewCount));
            hash = mix(hash, Double.doubleToLongBits(s.fuel));
            hash=mix(hash,s.evacuatedCrew);
            hash=mix(hash,Double.doubleToLongBits(s.kineticStoreExposure));
            hash=mix(hash,Double.doubleToLongBits(s.missileStoreExposure));
            hash=mix(hash,Double.doubleToLongBits(s.torpedoStoreExposure));
            hash=mix(hash,s.hazardFailures);
            hash=mix(hash,s.unsafeFlags);
            hash=mix(hash,s.lastFaultOrigin);
            hash=mix(hash,s.holoShotsDiverted);
            hash=mix(hash,s.holoSeekersDiverted);
            hash=mix(hash,s.lastHazardTick);
            hash=mix(hash,s.lastRepairTick);
            hash=mix(hash,s.recoveryBeaconActivated?1:0);
            hash = mix(hash, Double.doubleToLongBits(s.shields));
            hash = mix(hash, Double.doubleToLongBits(s.armor));
            hash = mix(hash,s.defense.fingerprint());
            hash = mix(hash, Double.doubleToLongBits(s.power));
            hash = mix(hash, Double.doubleToLongBits(s.heat));
            hash = mix(hash,s.missileAmmo);hash=mix(hash,s.torpedoAmmo);
            hash=mix(hash,s.kineticAmmo);
            hash=mix(hash,s.kineticType.ordinal());
            hash=mix(hash,s.warhead(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.warhead(DamageType.TORPEDO).ordinal());
            for(WeaponMuzzles.Emitter emitter:s.weaponEmitters)
                if(emitter.family()==WeaponMuzzles.Family.LASER){
                    hash=mix(hash,emitter.slotId());
                    hash=mix(hash,LaserFamilies.selected(s,emitter.slotId()).ordinal());
                }
            hash=mix(hash,s.launchMode(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.launchMode(DamageType.TORPEDO).ordinal());
            hash=mix(hash,s.propulsionMode(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.propulsionMode(DamageType.TORPEDO).ordinal());
            hash=mix(hash,s.guidanceMode(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.guidanceMode(DamageType.TORPEDO).ordinal());
            for(Ship.Launcher rack:s.launchers){
                hash=mix(hash,rack.slot);
                hash=mix(hash,rack.family.ordinal());
                hash=mix(hash,rack.fitting.guided().salvo().ordinal());
                hash=mix(hash,rack.fitting.guided().motor().ordinal());
                hash=mix(hash,rack.fitting.guided().guidance().ordinal());
                hash=mix(hash,rack.fitting.warhead().ordinal());
                hash=mix(hash,rack.magazine);
                hash=mix(hash,Double.doubleToLongBits(rack.reload));
            }
            hash = mix(hash,s.fighterReserve);hash=mix(hash,s.droneReserve);
            for(Ship.HangarBay bay:s.fighterBays){
                hash=mix(hash,bay.id.hashCode());
                hash=mix(hash,Double.doubleToLongBits(bay.condition));
            }
            hash=mix(hash,s.repairDroneReserve);
            hash=mix(hash,Double.doubleToLongBits(s.repairDroneReload));
            hash=mix(hash,Double.doubleToLongBits(s.remoteHullRepaired));
            hash=mix(hash,Double.doubleToLongBits(s.shieldsTransferred));
            hash=mix(hash,Double.doubleToLongBits(s.hullRepaired));
            hash=mix(hash,Double.doubleToLongBits(s.hangarCondition));
            hash=mix(hash,Double.doubleToLongBits(s.magazineCondition));
        }
        for(PendingLaunch pending:launchQueue){
            hash=mix(hash,pending.sourceId);
            hash=mix(hash,pending.targetId);
            hash=mix(hash,pending.family.ordinal());
            hash=mix(hash,pending.slotId);
            hash=mix(hash,pending.pattern.mode().ordinal());
            hash=mix(hash,pending.propulsion.ordinal());
            hash=mix(hash,pending.guidance.ordinal());
            hash=mix(hash,pending.loadedWarhead.ordinal());
            hash=mix(hash,pending.nextIndex);
            hash=mix(hash,pending.nextTick);
            if(pending.targetFix!=null){
                hash=mix(hash,pending.targetFix.measuredTick());
                hash=mix(hash,Double.doubleToLongBits(pending.targetFix.sample().x()));
                hash=mix(hash,Double.doubleToLongBits(pending.targetFix.sample().y()));
                hash=mix(hash,Double.doubleToLongBits(pending.targetFix.sample().vx()));
                hash=mix(hash,Double.doubleToLongBits(pending.targetFix.sample().vy()));
            }
        }
        hash=mix(hash,decoysLaunched);
        hash=mix(hash,decoysIntercepted);
        hash=mix(hash,salvosStarted);
        hash=mix(hash,roundsFiredBySalvos);
        hash=mix(hash,rocketRoundsFired);
        hash=mix(hash,rocketHits);hash=mix(hash,rocketIntercepts);
        hash=mix(hash,carriersSplit);
        hash=mix(hash,submunitionsDeployed);
        for(Ship ship:ships)for(RocketBarrage.Rack rack:ship.rocketRacks){
            hash=mix(hash,rack.slot);
            hash=mix(hash,rack.size.ordinal());
            hash=mix(hash,rack.motor.ordinal());
            hash=mix(hash,rack.salvoCount);hash=mix(hash,rack.salvoRemaining);
            hash=mix(hash,rack.rounds);hash=mix(hash,rack.nextLaunchTick);
            hash=mix(hash,Double.doubleToLongBits(rack.reload));
            hash=mix(hash,Double.doubleToLongBits(rack.lockedBearing));
        }
        for(Rocket r:rockets){
            hash=mix(hash,r.visualId);hash=mix(hash,r.sourceId);
            hash=mix(hash,r.targetId);hash=mix(hash,r.motor.ordinal());
            hash=mix(hash,r.size.ordinal());hash=mix(hash,r.flightTicks);
            hash=mix(hash,Double.doubleToLongBits(r.x));
            hash=mix(hash,Double.doubleToLongBits(r.y));
            hash=mix(hash,Double.doubleToLongBits(r.vx));
            hash=mix(hash,Double.doubleToLongBits(r.vy));
            hash=mix(hash,Double.doubleToLongBits(r.integrity));
        }
        for(BeamPhoton photon:pendingBeamPhotons){
            hash=mix(hash,photon.shotId);hash=mix(hash,photon.spawnTick);
            hash=mix(hash,photon.sourceId);hash=mix(hash,photon.family.ordinal());
            hash=mix(hash,Double.doubleToLongBits(photon.travelled));
            hash=mix(hash,Double.doubleToLongBits(photon.damage));
            hash=mix(hash,Double.doubleToLongBits(photon.ray.x1()));
            hash=mix(hash,Double.doubleToLongBits(photon.ray.y1()));
            hash=mix(hash,Double.doubleToLongBits(photon.ray.x2()));
            hash=mix(hash,Double.doubleToLongBits(photon.ray.y2()));
        }
        for (Missile m : missiles) {
            hash=mix(hash,m.payload.ordinal());
            hash=mix(hash,Double.doubleToLongBits(m.releaseRange));
            hash=mix(hash,Double.doubleToLongBits(m.damage));
            hash=mix(hash,m.targetId);
            hash=mix(hash,m.warhead.ordinal());
            hash=mix(hash,m.ignitionDelayTicks);
            hash=mix(hash,m.flightTicks);
            hash=mix(hash,Double.doubleToLongBits(m.vx));
            hash=mix(hash,Double.doubleToLongBits(m.vy));
            hash=mix(hash,Double.doubleToLongBits(m.propellant));
            hash=mix(hash,m.propulsionMode.ordinal());
            hash=mix(hash,m.guidanceMode.ordinal());
            hash=mix(hash,m.approachSide);
            hash=mix(hash,m.thrustPhase.ordinal());
            hash=mix(hash,m.seekerLocked?1:0);
            hash=mix(hash,m.seekerReceiverTick);
            hash=mix(hash,Double.doubleToLongBits(m.seekerReceiverX));
            hash=mix(hash,Double.doubleToLongBits(m.seekerReceiverY));
            hash=mix(hash,m.lastObservationTick);
            hash=mix(hash,Double.doubleToLongBits(m.seekerUncertainty));
            hash=mix(hash,m.falseContactIndex);
            hash=mix(hash,Double.doubleToLongBits(m.lastKnownX));
            hash=mix(hash,Double.doubleToLongBits(m.lastKnownY));
            hash=mix(hash,Double.doubleToLongBits(m.lastKnownVX));
            hash=mix(hash,Double.doubleToLongBits(m.lastKnownVY));
            hash=mix(hash,m.visualId);
            hash=mix(hash,m.sourceId);hash=mix(hash,Double.doubleToLongBits(m.y));
            hash=mix(hash,Double.doubleToLongBits(m.x));
            hash=mix(hash,Double.doubleToLongBits(m.integrity));
            hash=mix(hash,Double.doubleToLongBits(m.guidanceQuality));
        }
        for (Ballistic p : ballistics) {
            hash=mix(hash,p.visualId);
            hash=mix(hash,p.ammunition.ordinal());
            hash=mix(hash,Double.doubleToLongBits(p.x));
            hash=mix(hash,Double.doubleToLongBits(p.y));
            hash=mix(hash,Double.doubleToLongBits(p.integrity));
        }
        for (Craft craft : crafts) {
            hash=mix(hash,craft.id);hash=mix(hash,Double.doubleToLongBits(craft.x));
            hash=mix(hash,Double.doubleToLongBits(craft.y));
            hash=mix(hash,craft.repair?1:0);
            hash=mix(hash,craft.launchBayId.hashCode());
            hash=mix(hash,Double.doubleToLongBits(craft.repairReserve));
            hash=mix(hash,craft.targetId);
            hash=mix(hash,craft.targetCraftId);hash=mix(hash,craft.ammunition);
            hash=mix(hash,craft.profile==null?-1:craft.profile.hull().ordinal());
            hash=mix(hash,Double.doubleToLongBits(craft.integrity));
            hash=mix(hash,Double.doubleToLongBits(craft.armor));
            hash=mix(hash,Double.doubleToLongBits(craft.fuel));
            hash=mix(hash,Double.doubleToLongBits(craft.reload));
        }
        return hash;
    }
    private static long mix(long hash, long n) { return (hash ^ n) * 0x100000001b3L; }
}
