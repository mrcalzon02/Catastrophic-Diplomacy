package diplomacy.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullLayouts;
import diplomacy.tech.TechnologyCatalog;

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
        public int escortShipId;
        public List<Integer> defendedShipIds=List.of();
        /** Per-hull sequential mission plan, independent of weapons and fleet doctrine. */
        public ShipCommandQueue commandQueue=new ShipCommandQueue(List.of());
        public int lastTargetId,lastAttackerId;
        public double anchorOffsetX,anchorOffsetY;
        public Shipyard.Hull hullClass;
        /** Installed and physically located shipyard modules available for hit targeting. */
        public List<Shipyard.Slot> impactHardpoints=List.of();
        public Map<Integer,Subsystem> installedSubsystems=Map.of();
        /** Directional powered shields, localized armor plates and exposed structure. */
        public DirectionalDefense defense;
        public double x, y, vx, vy, facing;
        public double hull, maxHull, shields, maxShields, armor, maxArmor, sif, power, maxPower, heat, maxHeat;
        /** Persistent capital-ship reaction mass; unlike transient fighter flight fuel. */
        public double fuel=180,maxFuel=180;
        public long lastChatterTick=-1000;
        public double engineCondition = 1, reactorCondition = 1, weaponsCondition = 1, pointDefenseCondition = 1;
        public double sensorsCondition = 1, coolingCondition = 1, commandCondition = 1;
        public double damageControl, speed, accuracy, laserDamage, missileDamage, pdRange;
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
        /** Physical ammunition comes from fitted launch modules, not sprite geometry. */
        public ProjectileModel.Spec kineticProjectile=ProjectileModel.standard(ProjectileModel.Kind.KINETIC);
        public ProjectileModel.Spec missileProjectile=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        public ProjectileModel.Spec torpedoProjectile=ProjectileModel.standard(ProjectileModel.Kind.TORPEDO);
        public ProjectileModel.Defense pointDefenseProfile=ProjectileModel.STANDARD_PD;
        public double pdCycle=1;
        public FighterModel.Profile fighterProfile=FighterModel.standard(FighterModel.Hull.ASSAULT_GUNSHIP);
        public Shipyard.Weapons weapons=new Shipyard.Weapons(
                1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1);
        public Shipyard.Support support=new Shipyard.Support(0,0,180,0,175,false,0,1,1,0);
        public double hullRepaired,shieldRecharged,remoteHullRepaired,shieldsTransferred;
        public int repairDronesLaunched;
        /** Installed machinery determines operating headroom and thermal rejection. */
        public double powerRegen = 16, coolingRate = 19, passiveHeat;
        public double laserReload, missileReload, pdReload;
        public double kineticDamage, plasmaDamage, particleDamage, torpedoDamage, ecmStrength;
        public double kineticReload, plasmaReload, particleReload, torpedoReload, fighterReload, droneReload,repairDroneReload;
        public double hangarCondition=1, magazineCondition=1, ecmCondition=1;
        public int missileAmmo=60, torpedoAmmo, fighterReserve, droneReserve,repairDroneReserve;
        public int kineticAmmo=100;
        public AmmunitionTable.Kinetic kineticType=AmmunitionTable.Kinetic.STANDARD;
        /** Null only on legacy addShip(faction) test fixtures; real fitted ships
         * are governed by the per-socket research intersection. */
        public AmmunitionResearch.Access ammoAccess;
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
            OrdnanceDoctrine.forWeapon(family,mode);launchModes.put(family,mode);
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
        public double visualScale() { return Math.max(0.78, Math.min(1.90, StrictMath.sqrt(maxHull / 200.0)))
                * (hullClass==null?1:hullClass.spriteScale()); }
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
        public double x, y, vx, vy, fuel, damage, integrity;
        public final boolean torpedo;
        public final ProjectileModel.Spec spec;
        public final double guidanceQuality;
        /** Payload kind controls actual warhead/countermeasure effects. */
        public final OrdnanceDoctrine.Payload payload;
        public final AmmunitionTable.Warhead warhead;
        public final boolean decoy;
        /** Cold launches wait before ignition and can be killed by PD while coasting. */
        public int ignitionDelayTicks;
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
            this.visualId=visualId;this.spawnTick=spawnTick;sourceId = source; team = owner; targetId = target; x = sx; y = sy;torpedo=heavy;
            double magnitude = StrictMath.hypot(dx, dy);
            vx = magnitude == 0 ? 1 : dx / magnitude;
            vy = magnitude == 0 ? 0 : dy / magnitude;
            if(profile==null)throw new IllegalArgumentException("Missile properties missing");
            if(!Double.isFinite(launchGuidance)||launchGuidance<0.2||launchGuidance>2.6)
                throw new IllegalArgumentException("Invalid missile guidance quality");
            if(launchPayload==null||ignitionTicks<0||ignitionTicks>90)
                throw new IllegalArgumentException("Invalid guided payload");
            payload=launchPayload;decoy=launchPayload==OrdnanceDoctrine.Payload.DECOY;
            AmmunitionTable.warhead(warheadType);warhead=warheadType;
            if(decoy && amount!=0)
                throw new IllegalArgumentException("Countermeasures are forbidden from damaging ships");
            ignitionDelayTicks=ignitionTicks;
            spec=profile;guidanceQuality=launchGuidance;
            fuel=WeaponBehaviorTable.gun(heavy?DamageType.TORPEDO:DamageType.MISSILE).flightSeconds()
                    +ignitionTicks*DT;
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
            x=px;y=py;vx=profile.speed()*StrictMath.cos(heading);
            vy=profile.speed()*StrictMath.sin(heading);
            life=Math.max(1.5,760.0/profile.speed());integrity=profile.health();damage=amount;
        }
    }

    /** Fighters attack hostile hulls. Defensive drones deliberately hunt hostile ordnance. */
    public static final class Craft {
        public final int id, sourceId, team;
        public final boolean fighter,repair;
        public double x, y, integrity, maxIntegrity,armor,fuel,reload,repairReserve;
        public int targetId,targetCraftId,ammunition,shotsFired;
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
            integrity=offensive?profile.health():service?20:15;
            maxIntegrity=integrity;armor=offensive?profile.armor():0;
            fuel=offensive?profile.fuel():service?26:22;
            ammunition=offensive?profile.ammunition():0;
            repairReserve=service?32*source.support.droneEndurance():0;
        }
    }

    public static final class Beam {
        public final long visualId, spawnTick;
        public final double x1, y1, x2, y2;
        public final int team;
        public final DamageType type;
        public double lifetime = 0.13;
        Beam(long visualId,long spawnTick, double startX, double startY, double endX, double endY, int side, DamageType weapon) {
            this.visualId=visualId;this.spawnTick=spawnTick;x1 = startX; y1 = startY; x2 = endX; y2 = endY; team = side; type = weapon;
        }
    }

    /** Simulation-confirmed beam from a repair or shield support module. */
    public enum SupportKind { HULL, SHIELD, DRONE }
    public static final class SupportLink {
        public final int sourceId,targetId,team;
        public final SupportKind kind;
        public final double x1,y1,x2,y2;
        public final long spawnTick;
        public double life=0.36;
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
    private final Map<Integer,Integer> focusCounts=new HashMap<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Ballistic> ballistics = new ArrayList<>();
    /** A single family-level launch sequencer, until true per-hardpoint guns exist. */
    private static final class PendingLaunch {
        final int sourceId,targetId;
        final DamageType family;
        final OrdnanceDoctrine.Pattern pattern;
        final AmmunitionTable.Warhead loadedWarhead;
        int nextIndex;
        long nextTick;
        PendingLaunch(int source,int target,DamageType type,
                      OrdnanceDoctrine.Pattern doctrine,long start,
                      AmmunitionTable.Warhead loaded){
            sourceId=source;targetId=target;family=type;pattern=doctrine;
            loadedWarhead=loaded;nextTick=start;
        }
    }
    private final List<PendingLaunch> launchQueue=new ArrayList<>();
    public int decoysLaunched,decoysIntercepted,salvosStarted,roundsFiredBySalvos;
    private final List<Craft> crafts = new ArrayList<>();
    private int nextCraftId=1;
    private final List<Beam> beams = new ArrayList<>();
    private final List<SupportLink> supportLinks=new ArrayList<>();
    private final List<String> events = new ArrayList<>();
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
    public int hitCount, missileIntercepts, ballisticIntercepts, destructions, environmentalHits;
    /** Proof counters distinguish launched fighter rounds from mere animation. */
    public int fighterGunShots,fighterLaserShots,fighterParticleShots,
               fighterMissilesLaunched,fighterTorpedoesLaunched,craftLosses;
    private SpaceWeather weather = new SpaceWeather(SpaceWeather.clear());

    public Battle(long initialSeed) { seed = initialSeed; random = new Random(initialSeed); }
    public long seed() { return seed; }
    public SpaceWeather weather() { return weather; }
    /** Select weather before step 1; default is CLEAR for backward compatibility. */
    public void configureWeather(SpaceWeather.Preset preset) {
        if (tick != 0) throw new IllegalStateException("Weather must be configured before battle starts");
        weather = new SpaceWeather(preset);
    }
    public List<Ship> ships() { return Collections.unmodifiableList(ships); }
    public List<Missile> missiles() { return Collections.unmodifiableList(missiles); }
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
    public List<Ballistic> ballistics() { return Collections.unmodifiableList(ballistics); }
    public List<Craft> crafts() { return Collections.unmodifiableList(crafts); }
    public List<Beam> beams() { return Collections.unmodifiableList(beams); }
    public List<SupportLink> supportLinks(){return Collections.unmodifiableList(supportLinks);}
    public List<String> events() { return Collections.unmodifiableList(events); }
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
        CrewChatter.Scale scale=fighter?CrewChatter.Scale.FIGHTER:
                CrewChatter.scale(source.hullClass);
        return "RADIO "+source.name+(fighter?" / Flight":"")+": "+
                CrewChatter.line(scale,event,tick,source.id,
                        source.squadronRadio==null?teamRadioTone(source.team):source.squadronRadio);
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

    public Ship addShip(int team, Faction faction, double x, double y) {
        if (team != 0 && team != 1) throw new IllegalArgumentException("Only two teams in the skirmish mode");
        Ship ship = new Ship(nextId++, team, faction, faction.name().substring(0, 3) + "-" + nextId);
        ship.x = x; ship.y = y; ship.facing = team == 0 ? 0 : Math.PI;
        ship.orders=teamOrders[team];
        ships.add(ship); return ship;
    }
    /** Custom hull installations alter real combat stats; invalid fittings never enter battle. */
    private Ship addShip(int team, Shipyard.Design design, Shipyard.Fitted machinery, double x, double y) {
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
        s.installedSubsystems=Map.copyOf(located);
        s.fighterProfile=FighterModel.forFaction(design.fighterHull,design.faction);
        s.fittedCrewSystems=true;
        s.crewRequired=machinery.crew().minimum();
        s.crewBerths=machinery.crew().berths();
        s.lifeSupport=machinery.crew().lifeSupport();
        s.crewCount=Math.min(s.crewBerths,s.lifeSupport);
        s.maxHull = machinery.hull(); s.hull = s.maxHull;
        s.sensorRange=machinery.baseline().sensorRange();
        s.radarCrossSection=machinery.baseline().radarCrossSection();
        s.maxShields = machinery.shields(); s.shields = s.maxShields;
        s.maxArmor = machinery.armor(); s.armor = s.maxArmor;
        s.sif = machinery.sif();
        s.defense = new DirectionalDefense(s.maxShields,s.maxArmor,s.maxHull);
        if(machinery.baseline().shieldRecharge()>0)
            for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values())
                s.defense.configureRecharge(face,
                        1.25+machinery.baseline().shieldRecharge()/4,2);
        s.maxPower = machinery.maxPower(); s.power = s.maxPower;
        s.maxHeat = machinery.maxHeat();
        s.powerRegen = machinery.powerRegen();
        s.coolingRate = machinery.coolingRate(); s.passiveHeat = machinery.passiveHeat();
        s.speed = machinery.speed(); s.accuracy = machinery.accuracy();
        s.laserDamage = machinery.laserDamage(); s.missileDamage = machinery.missileDamage();
        s.pdRange = machinery.pdRange(); s.damageControl = machinery.damageControl();
        s.kineticProjectile=machinery.kineticProjectile();
        s.missileProjectile=machinery.missileProjectile();
        s.torpedoProjectile=machinery.torpedoProjectile();
        s.pointDefenseProfile=machinery.pointDefense();
        s.pdCycle=machinery.pdCycle();
        s.weapons=machinery.weapons();
        s.ammoAccess=machinery.ammunition();
        // A fleet blueprint owns the chosen, research-licensed physical loads.
        // Stock ammo remains the exact default for legacy CD-SHIP-1/2 fleets.
        s.setKineticType(design.ammoSelection.kinetic());
        s.setWarhead(DamageType.MISSILE,design.ammoSelection.missile());
        s.setWarhead(DamageType.TORPEDO,design.ammoSelection.torpedo());
        s.support=machinery.support();
        s.kineticDamage=machinery.kineticDamage();s.plasmaDamage=machinery.plasmaDamage();
        s.particleDamage=machinery.particleDamage();s.torpedoDamage=machinery.torpedoDamage();
        s.ecmStrength=machinery.ecmStrength();
        s.fighterReserve=machinery.fighterCapacity();s.droneReserve=machinery.droneCapacity();
        s.repairDroneReserve=machinery.support().repairDroneCapacity();
        s.kineticAmmo=s.kineticDamage>0?64+machinery.magazineCapacity()*4:0;
        s.missileAmmo=s.missileDamage>0?8+machinery.magazineCapacity():0;
        s.torpedoAmmo=s.torpedoDamage>0?3+machinery.magazineCapacity()/3:0;
        return s;
    }
    public void spawnWing(int team, Shipyard.Design design, TechnologyCatalog catalog,
                          int count, double centerX, double centerY) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Invalid fleet size");
        Shipyard.Fitted machinery = Shipyard.fit(design, catalog);
        for (int i=0;i<count;i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team,design,machinery,point[0],point[1]);
        }
        log(design.faction.displayName+" deployed "+count+" fitted "+design.hull+"s");
    }
    public static Battle duel(long seed, int shipsPerSide, Shipyard.Design left,
                              Shipyard.Design right, TechnologyCatalog catalog) {
        Battle battle = new Battle(seed);
        battle.spawnWing(0,left,catalog,shipsPerSide,310,335);
        battle.spawnWing(1,right,catalog,shipsPerSide,970,335);
        return battle;
    }
    public void spawnWing(int team, Faction faction, int count, double centerX, double centerY) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Invalid fleet size");
        for (int i = 0; i < count; i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team, faction, point[0], point[1]);
        }
        log(faction.displayName + " deployed " + count + " combatants");
    }
    public static Battle duel(long seed, int shipsPerSide, Faction left, Faction right) {
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
    public boolean finished() { return alive(0) == 0 || alive(1) == 0; }

    public void step() {
        tick++;
        focusCounts.clear();
        for(Ship ship:ships)if(ship.alive()&&ship.lastTargetId!=0)
            focusCounts.merge(ship.lastTargetId,1,Integer::sum);
        for (int i = beams.size() - 1; i >= 0; i--) {
            if ((beams.get(i).lifetime -= DT) <= 0) beams.remove(i);
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
        for (Ship s : ships) if (s.alive()) {
            replenish(s);
            // Retreat overrides waypoint queues and navigation doctrines.
            // Recharge/repair cannot reverse a committed escape under fire.
            if(s.withdrawing||(s.standingOrders!=null&&s.standingOrders.mustWithdraw(s))) {
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
                if (weather.preset().enabled()) {
                    // Low sensor visibility must not cause a permanent standstill.
                    double heading = s.team == 0 ? 1 : -1;
                    s.vx += (heading * s.speed * s.engineCondition
                            * s.crewReadiness() * 0.38 - s.vx) * 0.055;
                }
                s.x += s.vx * DT; s.y += s.vy * DT;
        s.fuel=Math.max(0,s.fuel-StrictMath.hypot(s.vx,s.vy)*DT*.018);
            }
            launchAuxiliary(s,target);
        }
        updateSupport();
        updateQueuedLaunches();
        updateMissiles();
        updateBallistics();
        updateCraft();
    }

    private void replenish(Ship s) {
        s.power = Math.max(0, Math.min(s.maxPower * s.reactorCondition,
                s.power + s.powerRegen * DT * s.reactorCondition
                        * s.crewReadiness() - weather.powerDrainPerSecond() * DT));
        s.heat = Math.max(0, s.heat + (s.passiveHeat + weather.heatPerSecond()
                - s.coolingRate * s.coolingCondition * weather.coolingFactor()) * DT);
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
        }
        if(s.power>6 && s.support.selfRepairRate()>0 && s.hull<s.maxHull) {
            double desired=s.support.selfRepairRate()*DT;
            double efficiency=s.support.powerEfficiency();
            double actual=Math.min(Math.min(desired,s.maxHull-s.hull),
                    (s.power-6)*efficiency/0.85);
            s.power-=actual*0.85/efficiency;
            s.hull+=actual;s.hullRepaired+=actual;
            s.heat+=actual*0.11;
        }
        s.laserReload = Math.max(0, s.laserReload - DT);
        s.missileReload = Math.max(0, s.missileReload - DT);
        s.pdReload = Math.max(0, s.pdReload - DT);
        s.kineticReload=Math.max(0,s.kineticReload-DT);
        s.plasmaReload=Math.max(0,s.plasmaReload-DT);
        s.particleReload=Math.max(0,s.particleReload-DT);
        s.torpedoReload=Math.max(0,s.torpedoReload-DT);
        s.fighterReload=Math.max(0,s.fighterReload-DT);
        s.droneReload=Math.max(0,s.droneReload-DT);
        s.repairDroneReload=Math.max(0,s.repairDroneReload-DT);
    }

    /** Built-in radar and target cross-section matter without art-scale coupling. */
    private double targetAcquisitionRange(Ship observer,Ship target){
        double rcs=StrictMath.max(0.15,StrictMath.min(4.0,target.radarCrossSection));
        return observer.sensorRange*observer.sensorsCondition*weather.sensorFactor()
                *StrictMath.sqrt(StrictMath.sqrt(rcs));
    }
    private Ship chooseTarget(Ship actor) {
        // Preserve old target selection and RNG stream for baseline fleet tests.
        if(actor.standingOrders==null&&
                actor.orders.equals(BattleOrders.DEFAULT.withPointDefense(actor.orders.pointDefense()))){
            Ship choice = null;
            double best = Double.POSITIVE_INFINITY;
            for (Ship s : ships) if (s.team != actor.team && s.alive()) {
                double distance = StrictMath.hypot(s.x - actor.x, s.y - actor.y);
                if (weather.preset().enabled() &&
                        distance > targetAcquisitionRange(actor,s))continue;
                double estimationError = (random.nextDouble() - 0.5) * 0.52 / (1 + actor.commanderSkill * 0.4);
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
            double distance=StrictMath.hypot(candidate.x-actor.x,candidate.y-actor.y);
            if(weather.preset().enabled() && distance>targetAcquisitionRange(actor,candidate))continue;
            double error=(random.nextDouble()-0.5)*0.52/(1+actor.commanderSkill*0.4);
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
                    assessed-=Math.min(6,Math.max(0,allies))*25;
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
                    ship.vx=0;ship.vy=0;
                    ship.commandQueue.complete();
                }else steerToward(ship,command.x(),command.y());
                return true;
            }
            case HOLD -> {
                ship.vx*=0.82;ship.vy*=0.82;
                ship.x+=ship.vx*DT;ship.y+=ship.vy*DT;
        ship.fuel=Math.max(0,ship.fuel-StrictMath.hypot(ship.vx,ship.vy)*DT*.018);
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
        double thrust = s.fuel>0?s.speed * s.engineCondition * s.crewReadiness():0;
        double desiredVx = StrictMath.cos(s.facing) * thrust, desiredVy = StrictMath.sin(s.facing) * thrust;
        double smoothing = Math.min(1, 1.6 * DT);
        s.vx += (desiredVx - s.vx) * smoothing;
        s.vy += (desiredVy - s.vy) * smoothing;
        s.x += s.vx * DT; s.y += s.vy * DT;
    }

    private void orderedSteer(Ship ship,Ship target) {
        BattleOrders.Orders orders=ship.orders;
        if(orders.position()==BattleOrders.Position.HOLD){
            ship.vx*=0.82;ship.vy*=0.82;
            ship.x+=ship.vx*DT;ship.y+=ship.vy*DT;
            return;
        }
        if(orders.position()==BattleOrders.Position.CAUTIOUS &&
                ship.hull<ship.maxHull*orders.retreatAtHullFraction()){
            double dx=ship.x-target.x,dy=ship.y-target.y;
            double length=StrictMath.max(1,StrictMath.hypot(dx,dy));
            steerToward(ship,ship.x+dx/length*400,ship.y+dy/length*400);
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
        double distance=StrictMath.hypot(target.x-ship.x,target.y-ship.y);
        if(orders.position()!=BattleOrders.Position.KEEP_MOVING
                &&distance>=preferredRange*0.5&&distance<=preferredRange){
            ship.vx*=0.86;ship.vy*=0.86;
            ship.x+=ship.vx*DT;ship.y+=ship.vy*DT;return;
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
        double velocity=ship.fuel>0?ship.speed*speedCap*ship.engineCondition*ship.crewReadiness():0;
        double smooth=Math.min(1,1.6*DT);
        ship.vx+=(StrictMath.cos(ship.facing)*velocity-ship.vx)*smooth;
        ship.vy+=(StrictMath.sin(ship.facing)*velocity-ship.vy)*smooth;
        ship.x+=ship.vx*DT;ship.y+=ship.vy*DT;
    }

    /** Cost transfer occurs only for real FIRE events. All costs and rates
     * are shared with the public authoritative WeaponBehaviorTable. */
    private static void chargeGun(Ship s,WeaponBehaviorTable.Gun behavior){
        s.power-=behavior.powerPerShot();
        s.heat+=behavior.heatPerShot();
    }
    private void fire(Ship s, Ship target) {
        if(s.crewReadiness()<0.05)return;
        double distance=StrictMath.hypot(target.x-s.x,target.y-s.y);
        double lock=Math.max(0,1-distance/Math.max(1,s.sensorRange))
                *s.crewReadiness()*s.sensorsCondition*weather.sensorFactor()
                *(1-target.ecmStrength*target.ecmCondition);
        double weatherAim=weather.accuracyFactor();
        var laser=WeaponBehaviorTable.gun(DamageType.LASER);
        var missile=WeaponBehaviorTable.gun(DamageType.MISSILE);
        var kinetic=WeaponBehaviorTable.gun(DamageType.KINETIC);
        var plasma=WeaponBehaviorTable.gun(DamageType.PLASMA);
        var particle=WeaponBehaviorTable.gun(DamageType.PARTICLE);
        var torpedo=WeaponBehaviorTable.gun(DamageType.TORPEDO);
        // Preserve firing-family ordering: LASER, MISSILE, KINETIC, PLASMA,
        // PARTICLE, TORPEDO. No extra RNG calls or synthetic salvo events.
        if(laser.available(s.laserDamage,0,distance,s.weapons.laserRange(),
                s.laserReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)){
            s.laserReload=laser.reload(s.weapons.laserCycle(),s.weaponsCondition);
            chargeGun(s,laser);
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.LASER));
            visual(shotId,VisualPhase.FIRE,"LASER",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<laser.aim(s.accuracy,s.weapons.laserTracking(),weatherAim,lock))
                hit(target,s.laserDamage,s,DamageType.LASER,shotId);
        }
        if(missile.available(s.missileDamage,s.missileAmmo,distance,s.weapons.missileRange(),
                s.missileReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&!hasPendingGuided(s.id,DamageType.MISSILE)){
            OrdnanceDoctrine.Pattern doctrine=OrdnanceDoctrine.forWeapon(
                    DamageType.MISSILE,s.launchMode(DamageType.MISSILE));
            if(s.missileAmmo>=doctrine.ammoNeeded()){
                s.missileReload=missile.reload(s.weapons.missileCycle(),s.weaponsCondition)
                        *doctrine.reloadMultiplier();
                launchQueue.add(new PendingLaunch(s.id,target.id,DamageType.MISSILE,
                        doctrine,tick,s.warhead(DamageType.MISSILE)));
                salvosStarted++;
            }
        }
        AmmunitionTable.Profile shell=AmmunitionTable.kinetic(s.kineticType);
        if(kinetic.available(s.kineticDamage,0,distance,s.weapons.kineticRange(),
                s.kineticReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&s.kineticAmmo>0
                &&s.power>kinetic.powerPerShot()*shell.powerCost()
                &&s.heat+kinetic.heatPerShot()*shell.heatCost()<=s.maxHeat){
            s.kineticReload=kinetic.reload(s.weapons.kineticCycle(),s.weaponsCondition)
                    *shell.reloadCost();
            s.power-=kinetic.powerPerShot()*shell.powerCost();
            s.heat+=kinetic.heatPerShot()*shell.heatCost();
            s.kineticAmmo--;
            long shotId=nextVisualShotId++;
            double angle=StrictMath.atan2(target.y-s.y,target.x-s.x);
            boolean onTarget=random.nextDouble()<kinetic.aim(
                    s.accuracy,s.weapons.kineticTracking(),weatherAim,lock);
            if(!onTarget)angle+=(shotId%2==0?1:-1)*WeaponBehaviorTable.BALLISTIC_MISS_ANGLE_RADIANS;
            ballistics.add(new Ballistic(shotId,tick,s.id,s.team,target.id,
                    s.x,s.y,angle,s.kineticDamage*shell.damage(),
                    shell.physical(s.kineticProjectile),s.kineticType));
            visual(shotId,VisualPhase.FIRE,"KINETIC",s.team,s.id,target.id,
                    s.x,s.y,target.x,target.y);
        }
        if(plasma.available(s.plasmaDamage,0,distance,s.weapons.plasmaRange(),
                s.plasmaReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)){
            s.plasmaReload=plasma.reload(s.weapons.plasmaCycle(),s.weaponsCondition);
            chargeGun(s,plasma);
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.PLASMA));
            visual(shotId,VisualPhase.FIRE,"PLASMA",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<plasma.aim(s.accuracy,s.weapons.plasmaTracking(),weatherAim,lock))
                hit(target,s.plasmaDamage,s,DamageType.PLASMA,shotId);
        }
        if(particle.available(s.particleDamage,0,distance,s.weapons.particleRange(),
                s.particleReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)){
            s.particleReload=particle.reload(s.weapons.particleCycle(),s.weaponsCondition);
            chargeGun(s,particle);
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.PARTICLE));
            visual(shotId,VisualPhase.FIRE,"PARTICLE",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<particle.aim(s.accuracy,s.weapons.particleTracking(),weatherAim,lock))
                hit(target,s.particleDamage,s,DamageType.PARTICLE,shotId);
        }
        if(torpedo.available(s.torpedoDamage,s.torpedoAmmo,distance,s.weapons.torpedoRange(),
                s.torpedoReload,s.power,s.heat,s.maxHeat,
                s.weaponsCondition,s.magazineCondition)
                &&!hasPendingGuided(s.id,DamageType.TORPEDO)){
            OrdnanceDoctrine.Pattern doctrine=OrdnanceDoctrine.forWeapon(
                    DamageType.TORPEDO,s.launchMode(DamageType.TORPEDO));
            if(s.torpedoAmmo>=doctrine.ammoNeeded()){
                s.torpedoReload=torpedo.reload(s.weapons.torpedoCycle(),s.weaponsCondition)
                        *doctrine.reloadMultiplier();
                launchQueue.add(new PendingLaunch(s.id,target.id,DamageType.TORPEDO,
                        doctrine,tick,s.warhead(DamageType.TORPEDO)));
                salvosStarted++;
            }
        }
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
            if(missiles.size()>=12000)continue;
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
            double angle=StrictMath.atan2(target.y-source.y,target.x-source.x)+spread;
            double distance=StrictMath.hypot(target.y-source.y,target.x-source.x);
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
            source.launched++;roundsFiredBySalvos++;
            if(decoy)decoysLaunched++;
            long shot=nextVisualShotId++;
            Missile body=new Missile(shot,tick,source.id,source.team,target.id,
                    source.x,source.y,velocityX,velocityY,damage,heavy,actual,
                    heavy?source.weapons.torpedoTracking():source.weapons.missileTracking(),
                    subtype,order.pattern.ignitionDelayTicks(),warhead);
            missiles.add(body);
            visual(shot,VisualPhase.FIRE,decoy?"DECOY":order.family.name(),
                    source.team,source.id,target.id,source.x,source.y,target.x,target.y);
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
                    double affordable=Math.min(desired,(source.power-5)*efficiency/1.1);
                    double gained=weakest.defense.receiveShieldTransfer(affordable);
                    if(gained>0){
                        source.power-=gained*1.1/efficiency;source.heat+=gained*0.13;
                        weakest.shields=weakest.defense.totalShields();
                        source.shieldsTransferred+=gained;
                        supportLinks.add(new SupportLink(source,weakest,SupportKind.SHIELD,
                                tick,source.x,source.y));
                    }
                }
            }
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
                ship.fighterReload<=0 && crafts.size()<2000) {
            crafts.add(new Craft(nextCraftId++,ship,true,fighterTarget.id));
            visual(nextVisualShotId++,VisualPhase.FIRE,"FIGHTER",ship.team,ship.id,enemy.id,
                    ship.x,ship.y,fighterTarget.x,fighterTarget.y);
            ship.fighterReserve--;ship.fightersLaunched++;ship.fighterReload=4.5/ship.hangarCondition;
            radio(ship,CrewChatter.Event.LAUNCH);
            ship.power-=7;ship.heat+=3;
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
                || distance>profile.sensors()*weather.sensorFactor())return;
        if(profile.ammunition()>0&&craft.ammunition<=0)return;
        Ship carrier=findShip(craft.sourceId);
        double accuracy=profile.accuracy()*weather.accuracyFactor();
        if(target!=null)accuracy*=(1-target.ecmStrength*target.ecmCondition);
        long shot=nextVisualShotId++;
        String shotName="FIGHTER_"+profile.weapon().name();
        if(profile.ammunition()>0)craft.ammunition--;
        craft.reload=profile.cooldown();
        craft.shotsFired++;
        if(craft.shotsFired==1&&carrier!=null)
            radioFighter(carrier,CrewChatter.Event.FIGHTER_ATTACK);
        if(profile.weapon()==FighterModel.Weapon.CANNON){
            double heading=StrictMath.atan2(ty-craft.y,tx-craft.x);
            if(random.nextDouble()>accuracy)heading+=(shot%2==0?0.32:-0.32);
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
            beams.add(new Beam(shot,tick,craft.x,craft.y,tx,ty,craft.team,kind));
            if(random.nextDouble()<accuracy){
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
                target==null?0:target.id,craft.x,craft.y,tx,ty);
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
                Ship target=findShip(craft.targetId);
                if(target==null||!target.alive()||target.team!=craft.team
                        ||target.hull>=target.maxHull-1e-8){
                    target=mostDamagedAlly(source,300,false);
                    craft.targetId=target==null?0:target.id;
                }
                if(target==null){
                    double dx=source.x-craft.x,dy=source.y-craft.y;
                    double d=StrictMath.hypot(dx,dy);
                    if(d>26){craft.x+=dx/d*110*DT;craft.y+=dy/d*110*DT;}
                    continue;
                }
                double dx=target.x-craft.x,dy=target.y-craft.y;
                double distance=StrictMath.hypot(dx,dy);
                if(distance>25){
                    double advance=Math.min(distance-25,145*DT);
                    if(distance>0){craft.x+=dx/distance*advance;craft.y+=dy/distance*advance;}
                }
                craft.reload=Math.max(0,craft.reload-DT);
                if(StrictMath.hypot(target.x-craft.x,target.y-craft.y)<=29
                        &&craft.reload<=0&&craft.repairReserve>0){
                    double gained=Math.min(craft.repairReserve,Math.min(
                            source.support.repairDroneRate()*0.80,target.maxHull-target.hull));
                    if(gained>0){
                        target.hull+=gained;craft.repairReserve-=gained;
                        source.remoteHullRepaired+=gained;
                        supportLinks.add(new SupportLink(source,target,SupportKind.DRONE,
                                tick,craft.x,craft.y));
                    }
                    craft.reload=0.8;
                }
                if(craft.repairReserve<=1e-8)crafts.remove(i);
            } else {
                Ship controller=findShip(craft.sourceId);
                ProjectileModel.Defense suite=controller==null?ProjectileModel.STANDARD_PD
                        :controller.pointDefenseProfile;
                double sensor=controller==null?1:controller.sensorsCondition;
                Missile missileTarget=null;Ballistic shellTarget=null;
                double nearest=Double.POSITIVE_INFINITY;
                for(Missile m:missiles)if(m.team!=craft.team){
                    double d=StrictMath.hypot(m.x-craft.x,m.y-craft.y);
                    double detection=ProjectileModel.acquisitionRange(150,sensor,
                            weather.sensorFactor(),suite,m.spec);
                    if(d<nearest&&d<=detection){nearest=d;missileTarget=m;shellTarget=null;}
                }
                for(Ballistic p:ballistics)if(p.team!=craft.team){
                    double d=StrictMath.hypot(p.x-craft.x,p.y-craft.y);
                    double detection=ProjectileModel.acquisitionRange(150,sensor,
                            weather.sensorFactor(),suite,p.spec);
                    if(d<nearest&&d<=detection){nearest=d;shellTarget=p;missileTarget=null;}
                }
                if(missileTarget!=null||shellTarget!=null){
                    double x=missileTarget!=null?missileTarget.x:shellTarget.x;
                    double y=missileTarget!=null?missileTarget.y:shellTarget.y;
                    if(nearest>0){
                        craft.x+=(x-craft.x)/nearest*205*DT;
                        craft.y+=(y-craft.y)/nearest*205*DT;
                    }
                    if(StrictMath.hypot(x-craft.x,y-craft.y)<28){
                        ProjectileModel.Spec incoming=missileTarget!=null
                                ?missileTarget.spec:shellTarget.spec;
                        if(random.nextDouble()<ProjectileModel.interceptionChance(
                                incoming,suite,controller==null?1:controller.pointDefenseCondition,
                                weather.accuracyFactor())){
                        if(missileTarget!=null){
                            missileTarget.integrity-=Math.max(3,suite.damage());
                            if(missileTarget.integrity<=0 && missiles.remove(missileTarget)){
                                missileIntercepts++;
                                visual(missileTarget.visualId,VisualPhase.INTERCEPT,
                                        missileTarget.torpedo?"TORPEDO":"MISSILE",
                                        missileTarget.team,missileTarget.sourceId,missileTarget.targetId,
                                        missileTarget.x,missileTarget.y,missileTarget.x,missileTarget.y);
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

    private void updateBallistics() {
        for(int i=ballistics.size()-1;i>=0;i--){
            Ballistic p=ballistics.get(i);
            if((p.life-=DT)<=0){
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
            }else{p.x=nextX;p.y=nextY;}
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
    private void updateMissiles() {
        // One tracking decision and one firing opportunity per mount/cooldown.
        // Hostile missiles, torpedoes and slow ballistic shells share the same
        // defensive attention. Threats are ranked by approximate time-to-range.
        var pdLaw=WeaponBehaviorTable.POINT_DEFENSE;
        for(Ship defender:ships)if(defender.alive() && defender.pdReload<=0
                && defender.crewReadiness()>=0.05
                && defender.pointDefenseCondition>pdLaw.minimumCondition()
                && defender.pdRange>0 && defender.orders.pointDefense().ordnance()
                && defender.power>=pdLaw.minimumPower()) {
            Missile chosenMissile=null;Ballistic chosenShell=null;
            double quickest=Double.POSITIVE_INFINITY;
            for(Missile m:missiles){
                if(m.team==defender.team||!coversThreat(defender,m.targetId))continue;
                double distance=StrictMath.hypot(m.x-defender.x,m.y-defender.y);
                double detection=ProjectileModel.acquisitionRange(
                        defender.pdRange,defender.sensorsCondition,weather.sensorFactor(),
                        defender.pointDefenseProfile,m.spec);
                if(distance>detection)continue;
                double eta=approachEta(defender.x,defender.y,
                        m.x,m.y,m.vx,m.vy,m.spec.speed());
                if(defender.standingOrders!=null&&eta>=1000 &&
                        defender.standingOrders.directives().interceptStrategy()!=
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)continue;
                double score=defensiveScore(defender,distance,eta);
                if(score<quickest){
                    quickest=score;chosenMissile=m;chosenShell=null;
                }
            }
            for(Ballistic p:ballistics){
                if(p.team==defender.team||!coversThreat(defender,p.targetId))continue;
                double distance=StrictMath.hypot(p.x-defender.x,p.y-defender.y);
                double detection=ProjectileModel.acquisitionRange(
                        defender.pdRange,defender.sensorsCondition,weather.sensorFactor(),
                        defender.pointDefenseProfile,p.spec);
                if(distance>detection)continue;
                double eta=approachEta(defender.x,defender.y,
                        p.x,p.y,p.vx,p.vy,p.spec.speed());
                if(defender.standingOrders!=null&&eta>=1000 &&
                        defender.standingOrders.directives().interceptStrategy()!=
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)continue;
                double score=defensiveScore(defender,distance,eta);
                if(score<quickest){
                    quickest=score;chosenShell=p;chosenMissile=null;
                }
            }
            if(chosenMissile==null && chosenShell==null)continue;
            defender.power-=pdLaw.powerPerShot();defender.heat+=pdLaw.heatPerShot();
            defender.pdReload=pdLaw.reload(false,defender.pdCycle,defender.pointDefenseCondition);
            double tx=chosenMissile!=null?chosenMissile.x:chosenShell.x;
            double ty=chosenMissile!=null?chosenMissile.y:chosenShell.y;
            int hostileSource=chosenMissile!=null?chosenMissile.sourceId:chosenShell.sourceId;
            long defensiveShot=nextVisualShotId++;
            visual(defensiveShot,VisualPhase.FIRE,"POINT_DEFENSE",
                    defender.team,defender.id,hostileSource,defender.x,defender.y,tx,ty);
            beams.add(new Beam(defensiveShot,tick,defender.x,defender.y,
                    tx,ty,defender.team,DamageType.LASER));
            // Fast shells are harder to track than guided ordnance, but genuinely
            // interceptable; visual glows alone cannot be shot down.
            ProjectileModel.Spec incoming=chosenMissile!=null?chosenMissile.spec:chosenShell.spec;
            double chance=ProjectileModel.interceptionChance(incoming,
                    defender.pointDefenseProfile,defender.pointDefenseCondition,
                    weather.accuracyFactor());
            if(random.nextDouble()<chance){
                if(chosenMissile!=null){
                    chosenMissile.integrity-=defender.pointDefenseProfile.damage();
                    if(chosenMissile.integrity<=0 && missiles.remove(chosenMissile)){
                        defender.intercepted++;missileIntercepts++;
                        if(chosenMissile.decoy)decoysIntercepted++;
                        visual(chosenMissile.visualId,VisualPhase.INTERCEPT,
                                chosenMissile.decoy?"DECOY":chosenMissile.torpedo?"TORPEDO":"MISSILE",
                                chosenMissile.team,chosenMissile.sourceId,chosenMissile.targetId,
                                chosenMissile.x,chosenMissile.y,chosenMissile.x,chosenMissile.y);
                    }
                }else{
                    chosenShell.integrity-=defender.pointDefenseProfile.damage();
                    if(chosenShell.integrity<=0 && ballistics.remove(chosenShell)){
                        defender.intercepted++;ballisticIntercepts++;
                        visual(chosenShell.visualId,VisualPhase.INTERCEPT,"KINETIC",
                                chosenShell.team,chosenShell.sourceId,chosenShell.targetId,
                                chosenShell.x,chosenShell.y,chosenShell.x,chosenShell.y);
                    }
                }
            }
        }
        for(Ship defender:ships)if(defender.alive()&&defender.pdReload<=0
                &&defender.crewReadiness()>=0.05
                &&defender.pointDefenseCondition>pdLaw.minimumCondition()
                &&defender.pdRange>0
                &&defender.orders.pointDefense().craft()
                &&defender.power>pdLaw.minimumPower()){
            for(int i=crafts.size()-1;i>=0;i--) {
                Craft craft=crafts.get(i);
                if(craft.team==defender.team||!coversThreat(defender,craft.targetId)
                        || StrictMath.hypot(craft.x-defender.x,craft.y-defender.y)>
                        ProjectileModel.acquisitionRange(defender.pdRange,
                                defender.sensorsCondition,weather.sensorFactor(),
                                defender.pointDefenseProfile,craft.radarProfile()))continue;
                defender.power-=pdLaw.powerPerShot();defender.heat+=pdLaw.heatPerShot();
                defender.pdReload=pdLaw.reload(true,defender.pdCycle,defender.pointDefenseCondition);
                long defensiveShot=nextVisualShotId++;
                visual(defensiveShot,VisualPhase.FIRE,"POINT_DEFENSE",
                        defender.team,defender.id,craft.sourceId,
                        defender.x,defender.y,craft.x,craft.y);
                beams.add(new Beam(defensiveShot,tick,defender.x,defender.y,
                        craft.x,craft.y,defender.team,DamageType.LASER));
                ProjectileModel.Spec signature=craft.radarProfile();
                if(random.nextDouble()<ProjectileModel.interceptionChance(signature,
                        defender.pointDefenseProfile,defender.pointDefenseCondition,
                        weather.accuracyFactor())) {
                    hitCraft(craft,defender.pointDefenseProfile.damage()*pdLaw.craftDamageMultiplier(),
                            defender,defensiveShot,"POINT_DEFENSE");
                    if(craft.integrity<=0){crafts.remove(i);craftLosses++;}
                }
                break;
            }
        }
        for (int i = missiles.size() - 1; i >= 0; i--) {
            Missile m = missiles.get(i);
            Ship target = findShip(m.targetId);
            if (target == null || !target.alive() || (m.fuel -= DT) <= 0) {
                missiles.remove(i);
                visual(m.visualId,VisualPhase.TIMEOUT,m.decoy?"DECOY":m.torpedo?"TORPEDO":"MISSILE",
                        m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                continue;
            }
            if(m.ignitionDelayTicks>0){
                // Cold-launch canisters are live PD targets while ignition is delayed.
                m.ignitionDelayTicks--;
                continue;
            }
            double dx = target.x - m.x, dy = target.y - m.y, range = StrictMath.hypot(dx, dy);
            if (range < 13) {
                if(m.decoy){
                    // A decoy has NO warhead and may never apply hull damage.
                    visual(m.visualId,VisualPhase.TIMEOUT,"DECOY",
                            m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                }else hitPayload(target,m.damage,findShip(m.sourceId),
                        m.torpedo?DamageType.TORPEDO:DamageType.MISSILE,m.visualId,
                        m.x,m.y,AmmunitionTable.warhead(m.warhead));
                missiles.remove(i);continue;
            }
            if (range > 0) {
                double guidance=Math.min(0.35,WeaponBehaviorTable.gun(
                        m.torpedo?DamageType.TORPEDO:DamageType.MISSILE).guidancePerTick()
                        *m.guidanceQuality
                        *weather.guidanceFactor()*(1-target.ecmStrength*target.ecmCondition));
                m.vx = (1-guidance)*m.vx + guidance*dx/range;
                m.vy = (1-guidance)*m.vy + guidance*dy/range;
                double magnitude = StrictMath.hypot(m.vx,m.vy);
                if(magnitude<1e-9)continue;
                double nextX=m.x+(m.vx/magnitude)*m.spec.speed()*DT;
                double nextY=m.y+(m.vy/magnitude)*m.spec.speed()*DT;
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
                }else {m.x=nextX;m.y=nextY;}
            }
        }
    }
    private Ship findShip(int id) { for (Ship ship : ships) if (ship.id == id) return ship; return null; }

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
                &&physicalDistance>1e-6 &&physicalDistance<=16*defender.visualScale();
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
        defender.hull -= damage;
        radio(defender,CrewChatter.Event.DAMAGE);
        if(defender.fittedCrewSystems){
            // Penetrating hits may kill personnel or disrupt occupied decks.
            // No random free crew is conjured when shields subsequently recharge.
            defender.crewCount=Math.max(0,defender.crewCount
                    -damage/Math.max(1,defender.maxHull)*defender.crewRequired*0.38);
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
                case COMMAND: defender.commandCondition = Math.max(0, defender.commandCondition - loss); break;
                case HANGAR: defender.hangarCondition = Math.max(0,defender.hangarCondition-loss); break;
                case MAGAZINE: defender.magazineCondition=Math.max(0,defender.magazineCondition-loss); break;
                case ELECTRONIC_WARFARE: defender.ecmCondition=Math.max(0,defender.ecmCondition-loss); break;
                default: break;
            }
        }
        if (defender.hull <= 0) {
            defender.hull = 0; defender.retired = true; destructions++;
            if (attacker != null) attacker.kills++;
            log(defender.name + " shattered at T+" + tick / 30 + "s");
        }
    }
    private void radio(Battle.Ship source,CrewChatter.Event event){
        if(source==null||tick-source.lastChatterTick<90||
                source.hull<=0)return;
        source.lastChatterTick=tick;
        log(radioMessage(source,event,false));
    }
    private void radioFighter(Battle.Ship carrier,CrewChatter.Event event){
        if(tick-carrier.lastChatterTick<90)return;
        carrier.lastChatterTick=tick;
        log(radioMessage(carrier,event,true));
    }
    private void log(String message) { events.add(message); if (events.size() > 90) events.remove(0); }

    /** Stable across machines using Java's specified Double.doubleToLongBits. */
    public long fingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, tick); hash = mix(hash, hitCount);
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
            hash = mix(hash, Double.doubleToLongBits(s.hull));
            hash = mix(hash, Double.doubleToLongBits(s.crewCount));
            hash = mix(hash, Double.doubleToLongBits(s.fuel));
            hash = mix(hash, Double.doubleToLongBits(s.shields));
            hash = mix(hash, Double.doubleToLongBits(s.armor));
            hash = mix(hash,s.defense.fingerprint());
            hash = mix(hash, Double.doubleToLongBits(s.power));
            hash = mix(hash,s.missileAmmo);hash=mix(hash,s.torpedoAmmo);
            hash=mix(hash,s.kineticAmmo);
            hash=mix(hash,s.kineticType.ordinal());
            hash=mix(hash,s.warhead(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.warhead(DamageType.TORPEDO).ordinal());
            hash=mix(hash,s.launchMode(DamageType.MISSILE).ordinal());
            hash=mix(hash,s.launchMode(DamageType.TORPEDO).ordinal());
            hash = mix(hash,s.fighterReserve);hash=mix(hash,s.droneReserve);
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
            hash=mix(hash,pending.pattern.mode().ordinal());
            hash=mix(hash,pending.loadedWarhead.ordinal());
            hash=mix(hash,pending.nextIndex);
            hash=mix(hash,pending.nextTick);
        }
        hash=mix(hash,decoysLaunched);
        hash=mix(hash,decoysIntercepted);
        hash=mix(hash,salvosStarted);
        hash=mix(hash,roundsFiredBySalvos);
        for (Missile m : missiles) {
            hash=mix(hash,m.payload.ordinal());
            hash=mix(hash,m.warhead.ordinal());
            hash=mix(hash,m.ignitionDelayTicks);
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
