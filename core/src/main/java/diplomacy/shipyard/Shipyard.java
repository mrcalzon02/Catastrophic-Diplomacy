package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.sim.ProjectileModel;
import diplomacy.sim.AmmunitionResearch;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.FighterModel;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;
import diplomacy.sim.OrdnanceGuidance;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;
import diplomacy.tech.TechnologyCatalog.Upgrade;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared desktop/Android hull fitting and deterministic engineering calculations. */
public final class Shipyard {
    private Shipyard() { }
    public enum Mount { SYSTEM, WEAPON, DEFENSE, SUPPORT, HABITAT }
    public enum Track { OUTPUT, EFFICIENCY, RESILIENCE, SPECIALIST }
    public enum Hull {
        CORVETTE(120, 180, 155, 0.96, 3, 2, 1, 0),
        FRIGATE(195, 310, 325, 0.82, 4, 3, 2, 1),
        CRUISER(360, 570, 685, 0.65, 6, 5, 3, 2),
        DREADNOUGHT(630, 990, 1550, 0.46, 9, 9, 5, 4),
        INTERCEPTOR(145, 215, 110, 1.25, 4, 2, 1, 0),
        MISSILE_CORVETTE(175, 260, 190, 0.91, 4, 4, 1, 1),
        DESTROYER(270, 430, 460, 0.75, 5, 5, 3, 1),
        LIGHT_CRUISER(345, 550, 640, 0.68, 6, 5, 3, 2),
        HEAVY_CRUISER(445, 730, 870, 0.56, 7, 6, 4, 3),
        BATTLECRUISER(555, 890, 1170, 0.52, 8, 8, 4, 3),
        CARRIER(670, 1120, 1320, 0.44, 9, 4, 6, 8),
        // Distinct commissioned chassis sharing already approved sprite categories.
        // Append only: legacy hull ordinals, saved designs and fleet IDs remain stable.
        CORVETTE_SCOUT(125, 190, 132, 1.18, 4, 2, 1, 0),
        CORVETTE_GUNSHIP(175, 270, 218, 0.82, 4, 4, 2, 0),
        FRIGATE_RAIDER(175, 300, 285, 0.98, 4, 3, 2, 1),
        FRIGATE_MISSILE(215, 345, 320, 0.73, 4, 5, 2, 1),
        FRIGATE_ESCORT(285, 440, 405, 0.69, 5, 3, 4, 2),
        CRUISER_RAIDER(370, 590, 615, 0.77, 6, 5, 3, 2),
        CRUISER_SUPPORT(410, 690, 785, 0.57, 7, 3, 5, 5),
        DREADNOUGHT_SIEGE(700, 1150, 1710, 0.37, 10, 10, 6, 4),
        // Four specialist doctrines at odd generations, three compressed roles
        // at even generations. All share existing, verified art-size families.
        INTERCEPTOR_FLAK(155, 238, 145, 1.08, 4, 3, 2, 0),
        INTERCEPTOR_EW(139, 230, 112, 1.32, 5, 2, 1, 0),
        INTERCEPTOR_TENDER(165, 251, 150, 0.98, 4, 2, 1, 1),
        MISSILE_CORVETTE_FLAK(185, 294, 235, 0.80, 4, 4, 3, 1),
        MISSILE_CORVETTE_EW(157, 260, 177, 1.10, 5, 3, 1, 1),
        MISSILE_CORVETTE_TENDER(197, 315, 235, 0.79, 4, 2, 2, 2),
        DESTROYER_FLAK(300, 475, 525, 0.66, 6, 5, 4, 1),
        DESTROYER_TORPEDO(255, 420, 380, 0.85, 5, 6, 1, 2),
        DESTROYER_EW(260, 435, 410, 0.86, 7, 3, 2, 1),
        DESTROYER_TENDER(316, 505, 540, 0.62, 6, 3, 3, 4),
        LIGHT_CRUISER_VANGUARD(378, 595, 745, 0.60, 7, 6, 5, 2),
        LIGHT_CRUISER_COMMAND(365, 620, 665, 0.68, 8, 4, 3, 4),
        LIGHT_CRUISER_LOGISTICS(430, 715, 710, 0.59, 7, 3, 5, 5),
        CRUISER_FLAK(400, 635, 755, 0.60, 7, 5, 6, 2),
        CRUISER_CONTROLLER(370, 615, 675, 0.71, 8, 3, 3, 3),
        HEAVY_CRUISER_VANGUARD(485, 790, 1010, 0.49, 8, 8, 6, 3),
        HEAVY_CRUISER_COMMAND(465, 780, 905, 0.55, 9, 5, 4, 6),
        HEAVY_CRUISER_LOGISTICS(550, 900, 970, 0.48, 8, 4, 7, 7),
        BATTLECRUISER_FLAK(595, 940, 1315, 0.46, 9, 7, 8, 3),
        BATTLECRUISER_STRIKE(545, 865, 1070, 0.59, 8, 10, 3, 3),
        BATTLECRUISER_EW(565, 920, 1160, 0.57, 10, 5, 5, 4),
        BATTLECRUISER_TENDER(615, 980, 1330, 0.44, 9, 4, 5, 9),
        CORVETTE_LOGISTICS(225, 355, 248, 0.78, 5, 2, 2, 2),
        CARRIER_LOGISTICS(765, 1280, 1575, 0.40, 10, 4, 6, 9),
        INTERCEPTOR_TORPEDO(132, 202, 108, 1.32, 4, 3, 1, 0),
        MISSILE_CORVETTE_TORPEDO(167, 278, 180, 1.02, 4, 5, 1, 1),
        CARRIER_COMMAND(695, 1180, 1440, 0.46, 11, 5, 6, 8);

        /** Preserve legacy enum ordinals and blueprint names; give designers size-sorted navigation. */
        public static Hull[] shipyardOrder() {
            return new Hull[]{
                INTERCEPTOR,INTERCEPTOR_FLAK,INTERCEPTOR_TORPEDO,INTERCEPTOR_EW,INTERCEPTOR_TENDER,
                CORVETTE,CORVETTE_SCOUT,CORVETTE_GUNSHIP,CORVETTE_LOGISTICS,
                MISSILE_CORVETTE,MISSILE_CORVETTE_FLAK,MISSILE_CORVETTE_TORPEDO,MISSILE_CORVETTE_EW,MISSILE_CORVETTE_TENDER,
                FRIGATE,FRIGATE_RAIDER,FRIGATE_MISSILE,FRIGATE_ESCORT,
                DESTROYER,DESTROYER_FLAK,DESTROYER_TORPEDO,DESTROYER_EW,DESTROYER_TENDER,
                LIGHT_CRUISER,LIGHT_CRUISER_VANGUARD,LIGHT_CRUISER_COMMAND,LIGHT_CRUISER_LOGISTICS,
                CRUISER,CRUISER_RAIDER,CRUISER_SUPPORT,CRUISER_FLAK,CRUISER_CONTROLLER,
                HEAVY_CRUISER,HEAVY_CRUISER_VANGUARD,HEAVY_CRUISER_COMMAND,HEAVY_CRUISER_LOGISTICS,
                BATTLECRUISER,BATTLECRUISER_FLAK,BATTLECRUISER_STRIKE,BATTLECRUISER_EW,BATTLECRUISER_TENDER,
                CARRIER,CARRIER_COMMAND,CARRIER_LOGISTICS,DREADNOUGHT,DREADNOUGHT_SIEGE
            };
        }
        /** Art category is independent of combat chassis; no copied or invented sprites. */
        public Hull spriteHull() {
            return switch(this) {
                case INTERCEPTOR_FLAK, INTERCEPTOR_TORPEDO, INTERCEPTOR_EW, INTERCEPTOR_TENDER -> INTERCEPTOR;
                case CORVETTE_SCOUT, CORVETTE_GUNSHIP,CORVETTE_LOGISTICS -> CORVETTE;
                case MISSILE_CORVETTE_FLAK, MISSILE_CORVETTE_TORPEDO,
                        MISSILE_CORVETTE_EW, MISSILE_CORVETTE_TENDER -> MISSILE_CORVETTE;
                case FRIGATE_RAIDER, FRIGATE_MISSILE, FRIGATE_ESCORT -> FRIGATE;
                case DESTROYER_FLAK, DESTROYER_TORPEDO, DESTROYER_EW, DESTROYER_TENDER -> DESTROYER;
                case LIGHT_CRUISER_VANGUARD,LIGHT_CRUISER_COMMAND,LIGHT_CRUISER_LOGISTICS -> LIGHT_CRUISER;
                case CRUISER_RAIDER, CRUISER_SUPPORT,CRUISER_FLAK,CRUISER_CONTROLLER -> CRUISER;
                case HEAVY_CRUISER_VANGUARD,HEAVY_CRUISER_COMMAND,HEAVY_CRUISER_LOGISTICS -> HEAVY_CRUISER;
                case BATTLECRUISER_FLAK,BATTLECRUISER_STRIKE,BATTLECRUISER_EW,BATTLECRUISER_TENDER -> BATTLECRUISER;
                case DREADNOUGHT_SIEGE -> DREADNOUGHT;
                case CARRIER_LOGISTICS,CARRIER_COMMAND -> CARRIER;
                default -> this;
            };
        }
        public double spriteScale() {
            return switch(this) {
                case CORVETTE_SCOUT, FRIGATE_RAIDER, CRUISER_RAIDER -> 0.91;
                case CORVETTE_GUNSHIP, FRIGATE_ESCORT, CRUISER_SUPPORT -> 1.08;
                case FRIGATE_MISSILE -> 1.03;
                case DREADNOUGHT_SIEGE -> 1.12;
                default -> 1;
            };
        }
        /** Subtle per-class livery shading; faction's original sprite remains intact. */
        public float tintRed() {
            return switch(this) {
                case CORVETTE_SCOUT, FRIGATE_RAIDER, CRUISER_RAIDER -> 0.83f;
                case FRIGATE_MISSILE, DREADNOUGHT_SIEGE -> 1.00f;
                default -> 0.96f;
            };
        }
        public float tintGreen() {
            return switch(this) {
                case CORVETTE_GUNSHIP, FRIGATE_MISSILE, DREADNOUGHT_SIEGE -> 0.85f;
                default -> 1f;
            };
        }
        public float tintBlue() {
            return switch(this) {
                case FRIGATE_ESCORT, CRUISER_SUPPORT -> 0.83f;
                default -> 1f;
            };
        }
        public final double massCapacity, volumeCapacity, baseHull, mobility;
        public final List<Slot> slots;
        Hull(double mass, double volume, double hull, double agility, int systems, int weapons, int defenses, int supports) {
            massCapacity = mass; volumeCapacity = volume; baseHull = hull; mobility = agility;
            List<Slot> positions = new ArrayList<>();
            for (int i=0;i<systems;i++) positions.add(new Slot(positions.size(), Mount.SYSTEM, -16+(i%3)*16, -18-(i/3)*16));
            for (int i=0;i<weapons;i++) positions.add(new Slot(positions.size(), Mount.WEAPON, (i%2==0?-27:27), -25+(i/2)*19));
            for (int i=0;i<defenses;i++) positions.add(new Slot(positions.size(), Mount.DEFENSE, -20+i*18, 35));
            for (int i=0;i<supports;i++) positions.add(new Slot(positions.size(), Mount.SUPPORT, -16+i*17, 15));
            // Append crew and life-support sockets, never renumber legacy hardpoints.
            positions.add(new Slot(positions.size(),Mount.HABITAT,-13,-12));
            positions.add(new Slot(positions.size(),Mount.HABITAT,13,-12));
            slots = Collections.unmodifiableList(positions);
        }
    }
    public record Slot(int index, Mount mount, int x, int y) { }

    public static final class Installed {
        public final String component;
        private final Map<Track,Integer> stages;
        public final ModuleVersions.Pattern pattern;
        public final int mark;
        private Installed(String id,Map<Track,Integer> upgrades){
            this(id,upgrades,ModuleVersions.Pattern.STANDARD,1);
        }
        private Installed(String id,Map<Track,Integer> upgrades,
                          ModuleVersions.Pattern version,int generation) {
            if(id==null || upgrades==null)throw new IllegalArgumentException("Module identity required");
            ModuleVersions.of(version,generation); // validates both fields
            component=id;pattern=version;mark=generation;
            Map<Track,Integer> copy = new EnumMap<>(Track.class);
            Track chosen=null;
            for(Map.Entry<Track,Integer> entry:upgrades.entrySet()) {
                Track branch=entry.getKey();
                Integer level=entry.getValue();
                if(branch==null||level==null||level<1||level>9)
                    throw new IllegalArgumentException("Invalid module research stage");
                if(chosen!=null && chosen!=branch)
                    throw new IllegalArgumentException(
                            "Mutually exclusive research branches: "+chosen+" and "+branch);
                chosen=branch;copy.put(branch,level);
            }
            stages=Collections.unmodifiableMap(copy);
        }
        /** Null means an unmodified base component, free to choose a research branch. */
        public Track branch(){return stages.isEmpty()?null:stages.keySet().iterator().next();}
        public int stage(Track track) { return stages.getOrDefault(track,0); }
        public Map<Track,Integer> stages() { return stages; }
    }
    /** Selected shared-family ammunition, never a free hull characteristic. */
    public record AmmoSelection(AmmunitionTable.Kinetic kinetic,
                                AmmunitionTable.Warhead missile,
                                AmmunitionTable.Warhead torpedo) {
        public static final AmmoSelection STANDARD=new AmmoSelection(
                AmmunitionTable.Kinetic.STANDARD,
                AmmunitionTable.Warhead.STANDARD,
                AmmunitionTable.Warhead.STANDARD);
        public AmmoSelection {
            AmmunitionTable.kinetic(kinetic);
            AmmunitionTable.warhead(missile);
            AmmunitionTable.warhead(torpedo);
        }
    }
    public static final class Design {
        public final Battle.Faction faction;
        public final Hull hull;
        public final String name;
        private final List<Installed> parts;
        /** Hangar payload is a separately chosen launch loadout, not a cosmetic sprite. */
        public final FighterModel.Hull fighterHull;
        public final AmmoSelection ammoSelection;
        public final OrdnanceFitting.Guided missileOrdnance,torpedoOrdnance;
        public OrdnanceFitting.Guided ordnance(Battle.DamageType family){
            if(family==Battle.DamageType.MISSILE)return missileOrdnance;
            if(family==Battle.DamageType.TORPEDO)return torpedoOrdnance;
            throw new IllegalArgumentException("Not guided ordnance");
        }
        private Design(Battle.Faction group,Hull frame,String callSign,List<Installed> mounted){
            this(group,frame,callSign,mounted,FighterModel.Hull.ASSAULT_GUNSHIP,
                    AmmoSelection.STANDARD);
        }
        private Design(Battle.Faction group,Hull frame,String callSign,List<Installed> mounted,
                       FighterModel.Hull craftType){
            this(group,frame,callSign,mounted,craftType,AmmoSelection.STANDARD);
        }
        private Design(Battle.Faction group,Hull frame,String callSign,List<Installed> mounted,
                       FighterModel.Hull craftType,AmmoSelection loadout){
            this(group,frame,callSign,mounted,craftType,loadout,
                    OrdnanceFitting.Guided.STANDARD,OrdnanceFitting.Guided.STANDARD);
        }
        private Design(Battle.Faction group,Hull frame,String callSign,List<Installed> mounted,
                       FighterModel.Hull craftType,AmmoSelection loadout,
                       OrdnanceFitting.Guided missile,OrdnanceFitting.Guided torpedo){
            if(group==null||frame==null||callSign==null||callSign.isBlank()||callSign.length()>70)
                throw new IllegalArgumentException("Invalid vessel identity");
            if(mounted.size()!=frame.slots.size())
                throw new IllegalArgumentException("Incorrect hardpoint count");
            if(craftType==null||loadout==null||missile==null||torpedo==null)
                throw new IllegalArgumentException("Ship equipment selections required");
            faction=group;hull=frame;name=callSign;fighterHull=craftType;
            ammoSelection=loadout;missileOrdnance=missile;torpedoOrdnance=torpedo;
            parts=Collections.unmodifiableList(new ArrayList<>(mounted));
        }
        private Design verifyAmmo(){
            AmmunitionResearch.Access available=Shipyard.ammunitionAccess(this);
            if(!available.allows(ammoSelection.kinetic())
                    ||!available.allows(Battle.DamageType.MISSILE,ammoSelection.missile())
                    ||!available.allows(Battle.DamageType.TORPEDO,ammoSelection.torpedo()))
                throw new IllegalArgumentException(
                        "Selected ammunition locked: change it to STANDARD before changing its weapon research");
            OrdnanceFitting.requireAllowed(this,Battle.DamageType.MISSILE,missileOrdnance);
            OrdnanceFitting.requireAllowed(this,Battle.DamageType.TORPEDO,torpedoOrdnance);
            return this;
        }
        public Design withGuidedOrdnance(Battle.DamageType family,
                                         OrdnanceFitting.Guided choice){
            if(choice==null)throw new IllegalArgumentException("Missing guided fit");
            OrdnanceFitting.suffix(family);
            return new Design(faction,hull,name,parts,fighterHull,ammoSelection,
                    family==Battle.DamageType.MISSILE?choice:missileOrdnance,
                    family==Battle.DamageType.TORPEDO?choice:torpedoOrdnance).verifyAmmo();
        }
        public Design withAmmoSelection(AmmoSelection selected){
            return new Design(faction,hull,name,parts,fighterHull,selected,
                    missileOrdnance,torpedoOrdnance).verifyAmmo();
        }
        public Design withKineticAmmunition(AmmunitionTable.Kinetic selected){
            return withAmmoSelection(new AmmoSelection(selected,
                    ammoSelection.missile(),ammoSelection.torpedo()));
        }
        public Design withGuidedWarhead(Battle.DamageType family,AmmunitionTable.Warhead selected){
            if(family==Battle.DamageType.MISSILE)
                return withAmmoSelection(new AmmoSelection(ammoSelection.kinetic(),selected,
                        ammoSelection.torpedo()));
            if(family==Battle.DamageType.TORPEDO)
                return withAmmoSelection(new AmmoSelection(ammoSelection.kinetic(),
                        ammoSelection.missile(),selected));
            throw new IllegalArgumentException("Weapon family has no guided warheads");
        }
        /** Changing a hangar's launched craft does not overwrite ship module research. */
        /** Physical hardware mark is independent of the exclusive research path. */
        public Design withModuleVersion(int slot,ModuleVersions.Pattern pattern,int mark){
            Installed existing=at(slot);
            if(existing==null)throw new IllegalArgumentException("Empty socket cannot be versioned");
            List<Installed> changed=new ArrayList<>(parts);
            changed.set(slot,new Installed(existing.component,existing.stages,pattern,mark));
            return new Design(faction,hull,name,changed,fighterHull,ammoSelection,
                    missileOrdnance,torpedoOrdnance).verifyAmmo();
        }
        public Design withFighterHull(FighterModel.Hull type) {
            return new Design(faction,hull,name,parts,type,ammoSelection,
                    missileOrdnance,torpedoOrdnance).verifyAmmo();
        }
        public Installed at(int slot) { return parts.get(slot); }
        public int slots() { return parts.size(); }
        /** Slot coordinates are authoritative for this faction's physical fitting diagram. */
        public List<Slot> placements() { return HullLayouts.slots(faction,hull); }
        public Design install(int slot, String baseId) {
            if(baseId!=null && (baseId.length()>60||!baseId.matches("BASE-[A-Z]{3}-[A-Z]{2,3}")))
                throw new IllegalArgumentException("Invalid base component ID");
            List<Installed> changed=new ArrayList<>(parts);
            changed.set(slot,baseId==null?null:new Installed(baseId, Map.of()));
            return new Design(faction,hull,name,changed,fighterHull,ammoSelection,
                    missileOrdnance,torpedoOrdnance).verifyAmmo();
        }
        public Design stage(int slot, Track track, int level) {
            if(level<0||level>9||track==null) throw new IllegalArgumentException("Upgrade stage must be 0..9");
            Installed current=at(slot);
            if(current==null) throw new IllegalArgumentException("Empty slot has no technology");
            Track active=current.branch();
            if(level>0 && active!=null && active!=track)
                throw new IllegalArgumentException("Module "+(slot+1)+" is committed to "+active
                        +"; reset that branch to stage 0 before selecting "+track);
            Map<Track,Integer> updated=new EnumMap<>(Track.class);
            updated.putAll(current.stages);
            if(level==0) updated.remove(track); else updated.put(track,level);
            List<Installed> changed=new ArrayList<>(parts);
            changed.set(slot,new Installed(current.component,updated,current.pattern,current.mark));
            return new Design(faction,hull,name,changed,fighterHull,ammoSelection,
                    missileOrdnance,torpedoOrdnance).verifyAmmo();
        }
    }
    public static String factionId(Battle.Faction faction) {
        return switch(faction) {
            case TERRAN -> "TER"; case DRAXIAN -> "DRA"; case CARNELIAN -> "CAR";
            case ZIPPLEDROW -> "ZIP"; case UMBRA_FOUNDRY -> "UMB";
            case UZIPT -> "UZI"; case ZELTHAR_LUMINARI -> "ZEL";
            case MANDRALLY -> "MAN"; case AUREATE_SYNOD -> "AUR";
            case VAERITH_PRISM -> "VAE"; case NYX_BIOLITH -> "NYX";
        };
    }
    public static Mount requiredMount(Component component) {
        return switch(component.category) {
            case "systems" -> Mount.SYSTEM; case "weapons" -> Mount.WEAPON;
            case "defense" -> Mount.DEFENSE; case "support" -> Mount.SUPPORT;
            case "habitat" -> Mount.HABITAT;
            default -> throw new IllegalArgumentException("Unknown mounting class " + component.category);
        };
    }
    /** Factory template: genuinely buildable ship with reactor, drive, radiator, lasers and missiles. */
    public static Design starter(Battle.Faction faction, Hull hull) {
        List<Installed> parts=new ArrayList<>(Collections.nCopies(hull.slots.size(),null));
        String shipName=hull.name().replace('_',' ').toLowerCase(Locale.ROOT);
        Design ship = new Design(faction,hull,Character.toUpperCase(shipName.charAt(0))+shipName.substring(1)+" Prototype",parts);
        String id=factionId(faction);
        ship=ship.install(slotOf(hull,Mount.SYSTEM,0),"BASE-"+id+"-REA")
                 .install(slotOf(hull,Mount.SYSTEM,1),"BASE-"+id+"-ENG")
                 .install(slotOf(hull,Mount.SYSTEM,2),"BASE-"+id+"-RAD")
                 .install(slotOf(hull,Mount.WEAPON,0),"BASE-"+id+"-LAS")
                 .install(slotOf(hull,Mount.WEAPON,1),"BASE-"+id+"-MSL")
                 .install(slotOf(hull,Mount.DEFENSE,0),"BASE-"+id+"-SHD")
                 .install(slotOf(hull,Mount.HABITAT,0),"BASE-"+id+"-CRW")
                 .install(slotOf(hull,Mount.HABITAT,1),"BASE-"+id+"-LFS");
        return ShipLineages.prototype(ship);
    }
    private static int slotOf(Hull hull,Mount mount,int nth) {
        for(Slot slot:hull.slots)if(slot.mount==mount&&nth--==0)return slot.index;
        throw new IllegalArgumentException("Hull lacks mount " + mount);
    }
    public static List<Component> choices(TechnologyCatalog catalog,Battle.Faction faction,Mount mount) {
        List<Component> list=new ArrayList<>();
        for(Component c:catalog.baseComponents().values())
            if(c.faction.equals(factionId(faction))&&requiredMount(c)==mount)list.add(c);
        return List.copyOf(list);
    }

    public record Report(List<String> errors, double mass, double volume, double demand, double generation,
                         double generatedHeat, double cooling, int industry, int research) {
        public boolean valid() { return errors.isEmpty(); }
        public String explanation() { return errors.isEmpty()?"Fitting approved":String.join("; ",errors); }
    }
    /** Aggregated multipliers are interpreted as total stage bonuses, NOT recursively multiplied stage-by-stage. */
    public record Part(Component base, double mass, double power, double heat, double effect,
                       double accuracy, double integrity, double shields, double thrust, double cooling,
                       double maintenance, int industry, int research,
                       ModuleBranchEffects.Tuning tuning) { }
    private static Part evaluate(Installed installed,TechnologyCatalog catalog,Battle.Faction faction) {
        Component base=catalog.base(installed.component);
        if(base==null) throw new IllegalArgumentException("Missing base: "+installed.component);
        if(!base.faction.equals(factionId(faction)))throw new IllegalArgumentException("Foreign technology requires a license: "+base.id);
        // Installed module constructor and blueprint decoder enforce exactly
        // one active research family. Defend at evaluation too, so costs and
        // projectile stats can never combine mutually exclusive bonuses.
        if(installed.stages.size()>1)
            throw new IllegalArgumentException("A module cannot combine research branches");
        double mass=1,power=1,heat=1,effect=1,accuracy=1,integrity=1,shields=1,thrust=1,cooling=1,maintenance=1;
        int industry=base.industryCost,research=0;
        for(Map.Entry<Track,Integer> selected:installed.stages.entrySet()) {
            int level=selected.getValue();
            if(level<1||level>9)throw new IllegalArgumentException("Bad upgrade stage");
            for(int stage=1;stage<=level;stage++) {
                String techId="TECH-"+factionId(faction)+"-"+base.subsystem+"-"+selected.getKey()+"-"+String.format(Locale.ROOT,"%02d",stage);
                Upgrade tech=catalog.get(techId);
                if(tech==null)throw new IllegalArgumentException("Unresearched upgrade: "+techId);
                industry=Math.addExact(industry,tech.industryCost);
                research=Math.addExact(research,tech.researchCost);
                if(stage==level){
                    mass*=tech.mass;power*=tech.power;heat*=tech.heat;effect*=tech.output;
                    accuracy*=tech.accuracy;integrity*=tech.integrity;shields*=tech.shield;
                    thrust*=tech.thrust;cooling*=tech.cooling;maintenance*=tech.maintenance;
                }
            }
        }
        Track branch=installed.branch();
        int rank=branch==null?0:installed.stage(branch);
        ModuleVersions.Modifiers generation=ModuleVersions.of(installed.pattern,installed.mark);
        industry=Math.toIntExact((long)StrictMath.ceil(industry*generation.cost()));
        return new Part(base,base.mass*mass*generation.mass(),
                base.power*power*generation.power(),
                base.heat*heat*generation.heat(),
                base.effect*effect*generation.output(),
                accuracy*generation.precision(),
                base.integrity*integrity*generation.integrity(),
                shields,thrust,cooling,maintenance*generation.maintenance(),
                industry,research,ModuleBranchEffects.tune(base.subsystem,branch,rank));
    }
    private static List<Part> fittedParts(Design design,TechnologyCatalog catalog,List<String> errors) {
        List<Part> parts=new ArrayList<>();
        for(int i=0;i<design.slots();i++) {
            Installed mounted=design.at(i);
            if(mounted==null)continue;
            try {
                Part p=evaluate(mounted,catalog,design.faction);
                if(requiredMount(p.base())!=design.hull.slots.get(i).mount())errors.add("Slot "+(i+1)+" requires "+design.hull.slots.get(i).mount());
                parts.add(p);
            }catch(IllegalArgumentException|ArithmeticException problem){errors.add("Slot "+(i+1)+": "+problem.getMessage());}
        }
        return parts;
    }
    public static Report inspect(Design design,TechnologyCatalog catalog) {
        if(design==null||catalog==null)throw new IllegalArgumentException("Missing ship or catalog");
        List<String> errors=new ArrayList<>();
        List<Part> parts=fittedParts(design,catalog,errors);
        double mass=0,volume=0,draw=0,generation=0,heat=0,cooling=0;
        int industry=0,research=0,reactors=0,engines=0,radiators=0,weapons=0;
        double crewBerths=0,lifeSupport=0;
        for(Part p:parts){
            mass+=p.mass;volume+=p.mass*1.5;heat+=p.heat;industry=Math.addExact(industry,p.industry);research=Math.addExact(research,p.research);
            draw+=p.power;
            switch(p.base.subsystem) {
                case "REA" -> {generation+=p.effect*3.0;reactors++;}
                case "RAD" -> {cooling+=p.effect*p.cooling*2.3;radiators++;}
                case "ENG" -> engines++;
                case "CRW" -> crewBerths+=p.effect*design.hull.massCapacity*0.12;
                case "LFS" -> lifeSupport+=p.effect*design.hull.massCapacity*0.12;
                case "KIN", "LAS", "MSL", "TOR", "PAR", "PLA" -> weapons++;
                default -> { }
            }
        }
        if(reactors==0)errors.add("A reactor is required");
        if(engines==0)errors.add("An engine is required");
        if(radiators==0)errors.add("A radiator is required");
        int staff=minimumCrew(design);
        if(crewBerths+1e-8<staff)errors.add("Insufficient fitted crew accommodation");
        if(lifeSupport+1e-8<staff)errors.add("Insufficient fitted life support");
        // A hull may be an unarmed scout, transport or support vessel.
        // Main batteries, point defense and fighter payloads require modules.
        if(mass>design.hull.massCapacity+1e-8)errors.add("Hull displacement exceeded");
        if(volume>design.hull.volumeCapacity+1e-8)errors.add("Hull internal volume exceeded");
        if(draw>generation+1e-8)errors.add("Insufficient reactor output");
        if(heat>cooling+1e-8)errors.add("Thermal rejection insufficient");
        if(!Double.isFinite(mass+volume+draw+generation+heat+cooling))errors.add("Nonfinite engineering values");
        for(Battle.DamageType family:new Battle.DamageType[]{
                Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO}){
            try{OrdnanceFitting.requireAllowed(design,family,design.ordnance(family));}
            catch(IllegalArgumentException invalid){errors.add(invalid.getMessage());}
        }
        return new Report(List.copyOf(errors),mass,volume,draw,generation,heat,cooling,industry,research);
    }

    /** Base staffing demand is derived from equipment and displacement, never
     * an intrinsic complement or an invisible hull accommodation allowance. */
    public static int minimumCrew(Design design){
        int installed=0;
        for(int i=0;i<design.slots();i++){
            Installed p=design.at(i);
            if(p!=null && !p.component.endsWith("-CRW") && !p.component.endsWith("-LFS"))
                installed++;
        }
        return (int)StrictMath.ceil(5+design.hull.massCapacity*0.11+installed*0.75);
    }
    public record Crew(int minimum,int berths,int lifeSupport){
        public Crew {
            if(minimum<=0||berths<0||lifeSupport<0)
                throw new IllegalArgumentException("Invalid crew support");
        }
    }
    /** Module-distinct nine-rank offensive specialization; all ratios are bounded. */
    public record Weapons(double laserRange,double kineticRange,double plasmaRange,
                          double particleRange,double missileRange,double torpedoRange,
                          double laserCycle,double kineticCycle,double plasmaCycle,
                          double particleCycle,double missileCycle,double torpedoCycle,
                          double laserTracking,double kineticTracking,double plasmaTracking,
                          double particleTracking,double missileTracking,double torpedoTracking) { }
    /**
     * Passive hull repair; remotely powered hull and shield beams; specialist
     * repair-drone support. These are actual per-second combat capabilities.
     */
    public record Support(double selfRepairRate,double remoteRepairRate,double repairRange,
                          double shieldTransferRate,double shieldRange,
                          boolean repairDrones,double repairDroneRate,
                          double droneEndurance,double powerEfficiency,
                          int repairDroneCapacity) { }
    /**
     * Physical logistics is independent of starter shield hardware and ordinary
     * defensive drones. Each supported channel needs a fitted support module.
     * Projectors reach far but are energy-inefficient; service craft have finite
     * charges and a real return trip; auras have deliberately tiny individual
     * throughput and broad, but not infinite, local coverage.
     */
    public static SupportDoctrine supportDoctrine(Design design,TechnologyCatalog catalog){
        if(!inspect(design,catalog).valid())
            throw new IllegalArgumentException("Invalid specialized support fitting");
        List<SupportDoctrine.Emitter> direct=new ArrayList<>();
        List<SupportDoctrine.Emitter> drones=new ArrayList<>();
        List<SupportDoctrine.Emitter> auras=new ArrayList<>();
        for(Part p:fittedParts(design,catalog,new ArrayList<>())){
            String id=p.base.subsystem;
            SupportDoctrine.Channel channel=switch(id){
                case "APR","DAR","AAR" -> SupportDoctrine.Channel.ARMOR;
                case "HPR" -> SupportDoctrine.Channel.HULL;
                case "PWR","DPW","APW" -> SupportDoctrine.Channel.POWER;
                case "DSH","ASH" -> SupportDoctrine.Channel.SHIELD;
                case "DHL","AHL" -> SupportDoctrine.Channel.HULL;
                default -> null;
            };
            if(channel==null)continue;
            var tune=p.tuning();
            if(id.equals("APR")||id.equals("PWR")||id.equals("HPR")){
                direct.add(new SupportDoctrine.Emitter(channel,
                        Math.max(0.05,p.effect*0.030*tune.shieldTransfer()),
                        450*tune.reach(),3.3/tune.supportEfficiency(),0,0));
            }else if(id.startsWith("D")){
                drones.add(new SupportDoctrine.Emitter(channel,
                        Math.max(1.5,p.effect*0.10*tune.remoteRepair()),
                        1150*tune.reach(),1.6/tune.supportEfficiency(),
                        Math.max(1,Math.min(5,2+(int)(p.effect/42))),
                        26*tune.droneEndurance()));
            }else{
                auras.add(new SupportDoctrine.Emitter(channel,
                        Math.max(0.005,p.effect*0.00065*tune.shieldTransfer()),
                        200*tune.reach(),1.1/tune.supportEfficiency(),0,0));
            }
        }
        return new SupportDoctrine(direct,drones,auras);
    }

    /** Bounded combat parameters directly consumed by the authoritative Battle simulation. */
    public record Fitted(double hull, double shields, double armor, double sif, double maxPower,
                         double maxHeat, double powerRegen, double coolingRate, double passiveHeat,
                         double speed, double accuracy, double laserDamage, double missileDamage,
                         double pdRange, double damageControl,
                         double kineticDamage, double plasmaDamage, double particleDamage,
                         double torpedoDamage, double ecmStrength, int fighterCapacity,
                         int droneCapacity, int magazineCapacity,
                         ProjectileModel.Spec kineticProjectile,
                         ProjectileModel.Spec missileProjectile,
                         ProjectileModel.Spec torpedoProjectile,
                         ProjectileModel.Defense pointDefense,
                         Weapons weapons,Support support,double pdCycle,
                         HullBaseline.Stats baseline,Crew crew,
                         AmmunitionResearch.Access ammunition) { }
    public static Fitted fit(Design design,TechnologyCatalog catalog) {
        Report report=inspect(design,catalog);
        if(!report.valid())throw new IllegalArgumentException("Fitting rejected: "+report.explanation());
        HullBaseline.Stats baseline=HullBaseline.forHull(design.faction,design.hull);
        double hull=baseline.structure(),shields=baseline.shields(),
                armor=baseline.armor(),sif=0;
        // Bare fitted hulls have no implicit repair module.
        double thrust=0,laser=0,missile=0,pd=0,accuracy=0.56,repair=0;
        double kinetic=0,plasma=0,particle=0,torpedo=0,ecm=0;
        int fighters=baseline.fighterBay(),drones=0,magazines=0;
        double accommodation=0,breathing=0;
        List<ProjectileModel.Spec> kineticModules=new ArrayList<>();
        List<ProjectileModel.Spec> missileModules=new ArrayList<>();
        List<ProjectileModel.Spec> torpedoModules=new ArrayList<>();
        double pdWeight=0,pdOutput=0,pdTracking=0,pdCycle=0,sensorRatio=1;
        double[] ranges=new double[6],cycles=new double[6],trackers=new double[6];
        int[] weaponCounts=new int[6];
        double selfRate=0,remoteRate=0,remoteRange=180;
        double shieldRate=0,shieldRange=175,efficiency=1;
        boolean repairDrones=false;
        double droneRepair=0,droneEndurance=1;
        int repairDroneCapacity=0;
        for(Part p:fittedParts(design,catalog,new ArrayList<>())) {
            ModuleBranchEffects.Tuning tuning=p.tuning();
            int weaponIndex=switch(p.base.subsystem){
                case "LAS" -> 0;case "KIN" -> 1;case "PLA" -> 2;
                case "PAR" -> 3;case "MSL" -> 4;case "TOR" -> 5;
                default -> -1;
            };
            if(weaponIndex>=0){
                ranges[weaponIndex]+=tuning.reach();
                cycles[weaponIndex]+=tuning.cycle();
                trackers[weaponIndex]+=tuning.tracking();
                weaponCounts[weaponIndex]++;
            }
            switch(p.base.subsystem) {
                case "ARM" -> armor+=p.effect*2.0*p.integrity/p.base.integrity;
                case "SHD" -> {
                    // A normal shield generator protects ONLY its owning hull.
                    // No allied transfer without a dedicated SUPPORT-slot XFR.
                    shields+=p.effect*2.6*p.shields;
                }
                case "XFR" -> {
                    shieldRate+=p.effect*0.028*tuning.shieldTransfer();
                    shieldRange=Math.max(shieldRange,450*tuning.reach());
                    efficiency=Math.max(efficiency,tuning.supportEfficiency());
                }
                case "SIF" -> sif+=p.effect*0.0065*p.integrity/p.base.integrity;
                case "ENG" -> thrust+=p.effect*p.thrust;
                case "KIN" -> {
                    kinetic+=p.effect*0.75*p.accuracy*tuning.damage();
                    kineticModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.KINETIC,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "LAS" -> laser+=p.effect*0.78*p.accuracy*tuning.damage();
                case "PAR" -> particle+=p.effect*0.82*p.accuracy*tuning.damage();
                case "PLA" -> plasma+=p.effect*1.05*p.accuracy*tuning.damage();
                case "MSL" -> {
                    missile+=p.effect*3.1*tuning.damage();
                    missileModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.MISSILE,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "TOR" -> {
                    torpedo+=p.effect*5.4*tuning.damage();
                    torpedoModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.TORPEDO,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "PD", "FLK" -> {
                    pd+=p.effect*2.8*tuning.reach();
                    pdWeight++;
                    pdOutput+=p.effect/p.base.effect*tuning.damage();
                    pdTracking+=p.accuracy*tuning.tracking();
                    pdCycle+=tuning.cycle();
                }
                case "SEN" -> {
                    accuracy+=0.12*p.accuracy;
                    // Even an unupgraded sensor array improves PD acquisition.
                    // SPECIALIST upgrades add further fire-control reach.
                    sensorRatio+=0.08+0.25*(p.accuracy-1);
                }
                case "REP" -> {
                    repair+=p.effect*0.0004;
                    selfRate+=p.effect*0.028*tuning.selfRepair();
                    remoteRate+=p.effect*0.019*tuning.remoteRepair();
                    remoteRange=Math.max(remoteRange,180*tuning.reach());
                    efficiency=Math.max(efficiency,tuning.supportEfficiency());
                }
                case "ECM" -> ecm+=p.effect*0.007*p.accuracy;
                case "FTR" -> fighters+=2+(int)(p.effect/18.0);
                case "DRN" -> {
                    int complement=2+(int)(p.effect/18.0);
                    droneEndurance=Math.max(droneEndurance,tuning.droneEndurance());
                    // Each physical controller owns its OWN specialist lineage.
                    // A repair controller does not convert unrelated PD drones.
                    if(tuning.remoteRepair()>1.0) {
                        repairDrones=true;
                        repairDroneCapacity+=complement;
                        droneRepair+=1.45*tuning.remoteRepair();
                    }else drones+=complement;
                }
                case "MAG" -> magazines+=(int)(p.effect/3.8);
                case "CRW" -> accommodation+=p.effect*design.hull.massCapacity*0.12;
                case "LFS" -> breathing+=p.effect*design.hull.massCapacity*0.12;
                default -> { }
            }
            hull+=p.integrity*0.08;
        }
        // Every hull can move and power essential avionics on its own.
        // Engines/reactors improve this baseline; neither supplies a weapon.
        double mobility=(36+thrust*1.3)*design.hull.mobility
                *(1-0.35*report.mass()/design.hull.massCapacity);
        double charge=baseline.powerRegen()
                +Math.max(2,(report.generation()-report.demand())*0.36);
        for(int i=0;i<6;i++){
            ranges[i]=weaponCounts[i]==0?1:ranges[i]/weaponCounts[i];
            cycles[i]=weaponCounts[i]==0?1:cycles[i]/weaponCounts[i];
            trackers[i]=weaponCounts[i]==0?1:trackers[i]/weaponCounts[i];
        }
        return new Fitted(hull,shields,armor,Math.min(0.75,sif),
                baseline.power()+report.generation()*0.95,
                110+report.cooling()*0.3,charge,Math.max(6,report.cooling()*0.27),report.generatedHeat()*0.15,
                baseline.speed()+Math.max(0,mobility)*0.65,
                Math.min(0.97,accuracy),laser,missile,pd==0?0:70+pd,
                repair,kinetic,plasma,particle,torpedo,Math.min(0.65,ecm),
                fighters,drones,magazines,
                ProjectileModel.average(kineticModules,ProjectileModel.Kind.KINETIC),
                ProjectileModel.average(missileModules,ProjectileModel.Kind.MISSILE),
                ProjectileModel.average(torpedoModules,ProjectileModel.Kind.TORPEDO),
                ProjectileModel.defense(pdWeight==0?1:pdOutput/pdWeight,
                        pdWeight==0?1:pdTracking/pdWeight,sensorRatio),
                new Weapons(ranges[0],ranges[1],ranges[2],ranges[3],ranges[4],ranges[5],
                        cycles[0],cycles[1],cycles[2],cycles[3],cycles[4],cycles[5],
                        trackers[0],trackers[1],trackers[2],trackers[3],trackers[4],trackers[5]),
                new Support(selfRate,remoteRate,remoteRange,shieldRate,shieldRange,
                        repairDrones,droneRepair,droneEndurance,efficiency,
                        repairDroneCapacity),
                pdWeight==0?1:pdCycle/pdWeight,baseline,
                new Crew(minimumCrew(design),(int)StrictMath.floor(accommodation),
                        (int)StrictMath.floor(breathing)),
                ammunitionAccess(design));
    }

    /**
     * We still simulate one firing controller per family/ship, not individual
     * mounts. Consequently shared-family ammunition must be compatible with
     * EVERY fitted launcher of that family. Intersection prevents a Specialist
     * gun granting its research ammo to an unrelated basic gun.
     */
    public static AmmunitionResearch.Access ammunitionAccess(Design design){
        if(design==null)throw new IllegalArgumentException("Missing vessel design");
        EnumSet<AmmunitionTable.Kinetic> k=EnumSet.allOf(AmmunitionTable.Kinetic.class);
        EnumSet<AmmunitionTable.Warhead> m=EnumSet.allOf(AmmunitionTable.Warhead.class);
        EnumSet<AmmunitionTable.Warhead> t=EnumSet.allOf(AmmunitionTable.Warhead.class);
        int kc=0,mc=0,tc=0;
        for(int i=0;i<design.slots();i++){
            Installed component=design.at(i);
            if(component==null)continue;
            Track path=component.branch();
            int rank=path==null?0:component.stage(path);
            if(component.component.endsWith("-KIN")){
                k.retainAll(AmmunitionResearch.kinetic(path,rank));kc++;
            }else if(component.component.endsWith("-MSL")){
                m.retainAll(AmmunitionResearch.warheads(Battle.DamageType.MISSILE,path,rank));mc++;
            }else if(component.component.endsWith("-TOR")){
                t.retainAll(AmmunitionResearch.warheads(Battle.DamageType.TORPEDO,path,rank));tc++;
            }
        }
        if(kc==0)k=EnumSet.of(AmmunitionTable.Kinetic.STANDARD);
        if(mc==0)m=EnumSet.of(AmmunitionTable.Warhead.STANDARD);
        if(tc==0)t=EnumSet.of(AmmunitionTable.Warhead.STANDARD);
        return new AmmunitionResearch.Access(k,m,t);
    }
    /** Research unlocks for exactly the selected socket, never another gun. */
    public static AmmunitionResearch.Access ammunitionAt(Design design,int slot){
        Installed component=design.at(slot);
        if(component==null)return AmmunitionResearch.STOCK;
        Track path=component.branch();
        int rank=path==null?0:component.stage(path);
        if(component.component.endsWith("-KIN"))
            return new AmmunitionResearch.Access(AmmunitionResearch.kinetic(path,rank),
                    AmmunitionResearch.STOCK.missiles(),AmmunitionResearch.STOCK.torpedoes());
        if(component.component.endsWith("-MSL"))
            return new AmmunitionResearch.Access(AmmunitionResearch.STOCK.kinetics(),
                    AmmunitionResearch.warheads(Battle.DamageType.MISSILE,path,rank),
                    AmmunitionResearch.STOCK.torpedoes());
        if(component.component.endsWith("-TOR"))
            return new AmmunitionResearch.Access(AmmunitionResearch.STOCK.kinetics(),
                    AmmunitionResearch.STOCK.missiles(),
                    AmmunitionResearch.warheads(Battle.DamageType.TORPEDO,path,rank));
        return AmmunitionResearch.STOCK;
    }

    /** Current exclusive research branch effect of one selected installed module. */
    public static ModuleBranchEffects.Tuning tuningAt(
            Design design,TechnologyCatalog catalog,int slot){
        Installed installed=design.at(slot);
        return installed==null?ModuleBranchEffects.NEUTRAL
                :evaluate(installed,catalog,design.faction).tuning();
    }

    /** Expose the exact fitted projectile parameters for an individual shipyard socket.
     * An empty or non-ordnance slot has no projectile body to inspect. */
    public static ProjectileModel.Spec projectileAt(Design design,TechnologyCatalog catalog,int slot) {
        Installed current=design.at(slot);
        if(current==null)return null;
        Part p=evaluate(current,catalog,design.faction);
        ProjectileModel.Kind kind=switch(p.base.subsystem) {
            case "KIN" -> ProjectileModel.Kind.KINETIC;
            case "MSL" -> ProjectileModel.Kind.MISSILE;
            case "TOR" -> ProjectileModel.Kind.TORPEDO;
            default -> null;
        };
        return kind==null?null:ProjectileModel.module(design.faction,kind,
                p.effect/p.base.effect,p.accuracy,p.integrity/p.base.integrity);
    }
    /** Nominal detection reach of one fitted PD/FLK mount, in world units,
     * before signatures, sensor condition, weather and supporting PD banks. */
    public static double pointDefenseRangeAt(Design design,TechnologyCatalog catalog,int slot){
        Installed current=design.at(slot);
        if(current==null)return 0;
        Part p=evaluate(current,catalog,design.faction);
        return p.base.subsystem.equals("PD")||p.base.subsystem.equals("FLK")
                ?70+p.effect*2.8*p.tuning().reach():0;
    }
    /** Individual PD module profile before fleet sensors and stacked batteries contribute. */
    public static ProjectileModel.Defense defenseAt(Design design,TechnologyCatalog catalog,int slot) {
        Installed current=design.at(slot);
        if(current==null)return null;
        Part p=evaluate(current,catalog,design.faction);
        if(!p.base.subsystem.equals("PD")&&!p.base.subsystem.equals("FLK"))return null;
        return ProjectileModel.defense(p.effect/p.base.effect*p.tuning().damage(),
                p.accuracy*p.tuning().tracking(),p.tuning().reach());
    }

    /** Portable, deterministic blueprint text (no Java object deserialization). */
    public static String encode(Design design) {
        boolean newer=false;
        for(int i=0;i<design.slots();i++){
            Installed equipped=design.at(i);
            if(equipped!=null&&(equipped.pattern!=ModuleVersions.Pattern.STANDARD || equipped.mark!=1))
                newer=true;
        }
        boolean specializedAmmo=!design.ammoSelection.equals(AmmoSelection.STANDARD);
        boolean customGuided=!design.missileOrdnance.equals(OrdnanceFitting.Guided.STANDARD)
                ||!design.torpedoOrdnance.equals(OrdnanceFitting.Guided.STANDARD);
        StringBuilder out=new StringBuilder(customGuided?"CD-SHIP-4|":
                specializedAmmo?"CD-SHIP-3|"
                :newer?"CD-SHIP-2|":"CD-SHIP-1|")
                .append(design.faction.name()).append('|')
                .append(design.hull.name()).append('|')
                .append(Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(design.name.getBytes(StandardCharsets.UTF_8)));
        // Preserve exact legacy CD-SHIP-1/2 when no purpose-loaded ammo.
        if(specializedAmmo||customGuided){
            out.append('|').append(design.fighterHull.name())
                    .append('|').append(design.ammoSelection.kinetic().name())
                    .append('|').append(design.ammoSelection.missile().name())
                    .append('|').append(design.ammoSelection.torpedo().name());
            if(customGuided){
                for(OrdnanceFitting.Guided fit:new OrdnanceFitting.Guided[]{
                        design.missileOrdnance,design.torpedoOrdnance})
                    out.append('|').append(fit.salvo().name())
                            .append('|').append(fit.motor().name())
                            .append('|').append(fit.guidance().name());
            }
        }
        else if(design.fighterHull!=FighterModel.Hull.ASSAULT_GUNSHIP)
            out.append('|').append(design.fighterHull.name());
        out.append('\n');
        for(int i=0;i<design.slots();i++) {
            Installed installed=design.at(i);
            if(installed==null)continue;
            out.append(i).append('|').append(installed.component);
            for(Track track:Track.values())out.append('|').append(installed.stage(track));
            if(newer||specializedAmmo||customGuided)out.append('|').append(installed.pattern.name())
                    .append('|').append(installed.mark);
            out.append('\n');
        }
        return out.toString();
    }
    public static Design decode(String encoded,TechnologyCatalog catalog) {
        if(encoded==null||encoded.length()>20000)throw new IllegalArgumentException("Invalid blueprint size");
        String[] lines=encoded.split("\\n",-1);
        if(lines.length<2||lines.length>70)throw new IllegalArgumentException("Invalid blueprint lines");
        String[] header=lines[0].split("\\|",-1);
        boolean hasGuided=header.length>=1&&"CD-SHIP-4".equals(header[0]);
        boolean hasAmmo=hasGuided||header.length>=1&&"CD-SHIP-3".equals(header[0]);
        boolean hasVersions=hasAmmo||header.length>=1&&"CD-SHIP-2".equals(header[0]);
        if((hasGuided?header.length!=14:hasAmmo?header.length!=8:
                (header.length!=4&&header.length!=5))
                ||(!hasVersions&&!"CD-SHIP-1".equals(header[0])))
            throw new IllegalArgumentException("Wrong ship file version");
        final Design base;
        try{
            Battle.Faction faction=Battle.Faction.valueOf(header[1]);Hull hull=Hull.valueOf(header[2]);
            String name=new String(Base64.getUrlDecoder().decode(header[3]),StandardCharsets.UTF_8);
            FighterModel.Hull aircraft=(hasAmmo||header.length==5)
                    ?FighterModel.Hull.valueOf(header[4]):FighterModel.Hull.ASSAULT_GUNSHIP;
            base=new Design(faction,hull,name,
                    new ArrayList<>(Collections.nCopies(hull.slots.size(),null)),aircraft);
        }catch(RuntimeException err){throw new IllegalArgumentException("Invalid ship identity",err);}
        Design design=base;
        boolean[] seen=new boolean[base.slots()];
        for(int n=1;n<lines.length;n++) {
            if(lines[n].isEmpty())continue;
            String[] p=lines[n].split("\\|",-1);
            if(p.length!=(hasVersions?8:6))
                throw new IllegalArgumentException("Invalid slot record");
            final int index;
            try{index=Integer.parseInt(p[0]);}catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid slot",bad);}
            if(index<0||index>=seen.length||seen[index])throw new IllegalArgumentException("Duplicate/out-of-range slot");
            seen[index]=true;
            // CD-SHIP-1 compatibility is retained. Reject (do not silently
            // downgrade!) legacy blueprints which stacked incompatible paths.
            int[] levels=new int[Track.values().length];
            Track chosen=null;
            for(int j=0;j<levels.length;j++){
                try{levels[j]=Integer.parseInt(p[j+2]);}
                catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid technology rank",bad);}
                if(levels[j]<0||levels[j]>9)
                    throw new IllegalArgumentException("Upgrade stage must be 0..9 at slot "+index);
                if(levels[j]>0){
                    Track branch=Track.values()[j];
                    if(chosen!=null)
                        throw new IllegalArgumentException("Blueprint slot "+(index+1)+
                                " contains incompatible "+chosen+" and "+branch+
                                " research paths; legacy data was not modified");
                    chosen=branch;
                }
            }
            design=design.install(index,p[1]);
            if(chosen!=null)design=design.stage(index,chosen,levels[chosen.ordinal()]);
            if(hasVersions){
                try{
                    design=design.withModuleVersion(index,
                            ModuleVersions.Pattern.valueOf(p[6]),Integer.parseInt(p[7]));
                }catch(RuntimeException invalid){
                    throw new IllegalArgumentException("Invalid physical module version in slot "+index,invalid);
                }
            }
        }
        // CD-SHIP-1 files created before the append-only habitat migration
        // legitimately had no accommodation sockets. Install the new physical
        // modules only for those older records, never rewrite an occupied slot
        // or bypass invalid CD-SHIP-2 crew engineering.
        if(!hasVersions){
            int quarters=slotOf(design.hull,Mount.HABITAT,0);
            int breathing=slotOf(design.hull,Mount.HABITAT,1);
            if(!seen[quarters] && !seen[breathing]){
                String id=factionId(design.faction);
                design=design.install(quarters,"BASE-"+id+"-CRW")
                        .install(breathing,"BASE-"+id+"-LFS");
            }
        }
        if(hasAmmo){
            try{
                design=design.withAmmoSelection(new AmmoSelection(
                        AmmunitionTable.Kinetic.valueOf(header[5]),
                        AmmunitionTable.Warhead.valueOf(header[6]),
                        AmmunitionTable.Warhead.valueOf(header[7])));
            }catch(IllegalArgumentException invalid){
                throw new IllegalArgumentException(
                        "Blueprint ammo is not licensed by the fitted module research",invalid);
            }
        }
        if(hasGuided){
            try{
                design=design.withGuidedOrdnance(Battle.DamageType.MISSILE,
                        new OrdnanceFitting.Guided(
                                OrdnanceDoctrine.Mode.valueOf(header[8]),
                                OrdnancePropulsion.Mode.valueOf(header[9]),
                                OrdnanceGuidance.Mode.valueOf(header[10])));
                design=design.withGuidedOrdnance(Battle.DamageType.TORPEDO,
                        new OrdnanceFitting.Guided(
                                OrdnanceDoctrine.Mode.valueOf(header[11]),
                                OrdnancePropulsion.Mode.valueOf(header[12]),
                                OrdnanceGuidance.Mode.valueOf(header[13])));
            }catch(RuntimeException bad){
                throw new IllegalArgumentException("Invalid or unauthorized guided weapon blueprint",bad);
            }
        }
        if(!inspect(design,catalog).valid())throw new IllegalArgumentException("Blueprint is not battle ready: "+inspect(design,catalog).explanation());
        return design;
    }
}
