package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.sim.ProjectileModel;
import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.TechnologyCatalog.Component;
import diplomacy.tech.TechnologyCatalog.Upgrade;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared desktop/Android hull fitting and deterministic engineering calculations. */
public final class Shipyard {
    private Shipyard() { }
    public enum Mount { SYSTEM, WEAPON, DEFENSE, SUPPORT }
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
        CARRIER(670, 1120, 1320, 0.44, 9, 4, 6, 8);

        /** Preserve legacy enum ordinals and blueprint names; give designers size-sorted navigation. */
        public static Hull[] shipyardOrder() {
            return new Hull[]{INTERCEPTOR,CORVETTE,MISSILE_CORVETTE,FRIGATE,DESTROYER,
                    LIGHT_CRUISER,CRUISER,HEAVY_CRUISER,BATTLECRUISER,CARRIER,DREADNOUGHT};
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
            slots = Collections.unmodifiableList(positions);
        }
    }
    public record Slot(int index, Mount mount, int x, int y) { }

    public static final class Installed {
        public final String component;
        private final Map<Track,Integer> stages;
        private Installed(String id, Map<Track,Integer> upgrades) {
            component=id;
            Map<Track,Integer> copy = new EnumMap<>(Track.class);
            copy.putAll(upgrades); stages=Collections.unmodifiableMap(copy);
        }
        public int stage(Track track) { return stages.getOrDefault(track,0); }
        public Map<Track,Integer> stages() { return stages; }
    }
    public static final class Design {
        public final Battle.Faction faction;
        public final Hull hull;
        public final String name;
        private final List<Installed> parts;
        private Design(Battle.Faction group, Hull frame, String callSign, List<Installed> mounted) {
            if(group==null||frame==null||callSign==null||callSign.isBlank()||callSign.length()>70) throw new IllegalArgumentException("Invalid vessel identity");
            if(mounted.size()!=frame.slots.size()) throw new IllegalArgumentException("Incorrect hardpoint count");
            faction=group;hull=frame;name=callSign;
            parts=Collections.unmodifiableList(new ArrayList<>(mounted));
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
            return new Design(faction,hull,name,changed);
        }
        public Design stage(int slot, Track track, int level) {
            if(level<0||level>6||track==null) throw new IllegalArgumentException("Upgrade stage must be 0..6");
            Installed current=at(slot);
            if(current==null) throw new IllegalArgumentException("Empty slot has no technology");
            Map<Track,Integer> updated=new EnumMap<>(Track.class);
            updated.putAll(current.stages);
            if(level==0) updated.remove(track); else updated.put(track,level);
            List<Installed> changed=new ArrayList<>(parts);
            changed.set(slot,new Installed(current.component,updated));
            return new Design(faction,hull,name,changed);
        }
    }
    public static String factionId(Battle.Faction faction) {
        return switch(faction) {
            case TERRAN -> "TER"; case DRAXIAN -> "DRA"; case CARNELIAN -> "CAR";
            case ZIPPLEDROW -> "ZIP"; case UZIPT -> "UZI"; case MANDRALLY -> "MAN";
        };
    }
    public static Mount requiredMount(Component component) {
        return switch(component.category) {
            case "systems" -> Mount.SYSTEM; case "weapons" -> Mount.WEAPON;
            case "defense" -> Mount.DEFENSE; case "support" -> Mount.SUPPORT;
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
                 .install(slotOf(hull,Mount.DEFENSE,0),"BASE-"+id+"-SHD");
        return ship;
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
                       double maintenance, int industry, int research) { }
    private static Part evaluate(Installed installed,TechnologyCatalog catalog,Battle.Faction faction) {
        Component base=catalog.base(installed.component);
        if(base==null) throw new IllegalArgumentException("Missing base: "+installed.component);
        if(!base.faction.equals(factionId(faction)))throw new IllegalArgumentException("Foreign technology requires a license: "+base.id);
        double mass=1,power=1,heat=1,effect=1,accuracy=1,integrity=1,shields=1,thrust=1,cooling=1,maintenance=1;
        int industry=base.industryCost,research=0;
        for(Map.Entry<Track,Integer> selected:installed.stages.entrySet()) {
            int level=selected.getValue();
            if(level<1||level>6)throw new IllegalArgumentException("Bad upgrade stage");
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
        return new Part(base,base.mass*mass,base.power*power,base.heat*heat,
                base.effect*effect,accuracy,base.integrity*integrity,shields,thrust,cooling,maintenance,industry,research);
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
        for(Part p:parts){
            mass+=p.mass;volume+=p.mass*1.5;heat+=p.heat;industry=Math.addExact(industry,p.industry);research=Math.addExact(research,p.research);
            draw+=p.power;
            switch(p.base.subsystem) {
                case "REA" -> {generation+=p.effect*3.0;reactors++;}
                case "RAD" -> {cooling+=p.effect*p.cooling*2.3;radiators++;}
                case "ENG" -> engines++;
                case "KIN", "LAS", "MSL", "TOR", "PAR", "PLA" -> weapons++;
                default -> { }
            }
        }
        if(reactors==0)errors.add("A reactor is required");
        if(engines==0)errors.add("An engine is required");
        if(radiators==0)errors.add("A radiator is required");
        if(weapons==0)errors.add("At least one main weapon required");
        if(mass>design.hull.massCapacity+1e-8)errors.add("Hull displacement exceeded");
        if(volume>design.hull.volumeCapacity+1e-8)errors.add("Hull internal volume exceeded");
        if(draw>generation+1e-8)errors.add("Insufficient reactor output");
        if(heat>cooling+1e-8)errors.add("Thermal rejection insufficient");
        if(!Double.isFinite(mass+volume+draw+generation+heat+cooling))errors.add("Nonfinite engineering values");
        return new Report(List.copyOf(errors),mass,volume,draw,generation,heat,cooling,industry,research);
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
                         ProjectileModel.Defense pointDefense) { }
    public static Fitted fit(Design design,TechnologyCatalog catalog) {
        Report report=inspect(design,catalog);
        if(!report.valid())throw new IllegalArgumentException("Fitting rejected: "+report.explanation());
        double hull=design.hull.baseHull,shields=0,armor=0,sif=0;
        double thrust=0,laser=0,missile=0,pd=0,accuracy=0.56,repair=0.001;
        double kinetic=0,plasma=0,particle=0,torpedo=0,ecm=0;
        int fighters=0,drones=0,magazines=0;
        List<ProjectileModel.Spec> kineticModules=new ArrayList<>();
        List<ProjectileModel.Spec> missileModules=new ArrayList<>();
        List<ProjectileModel.Spec> torpedoModules=new ArrayList<>();
        double pdWeight=0,pdOutput=0,pdTracking=0,sensorRatio=1;
        for(Part p:fittedParts(design,catalog,new ArrayList<>())) {
            switch(p.base.subsystem) {
                case "ARM" -> armor+=p.effect*2.0*p.integrity/p.base.integrity;
                case "SHD" -> shields+=p.effect*2.6*p.shields;
                case "SIF" -> sif+=p.effect*0.0065*p.integrity/p.base.integrity;
                case "ENG" -> thrust+=p.effect*p.thrust;
                case "KIN" -> {
                    kinetic+=p.effect*0.75*p.accuracy;
                    kineticModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.KINETIC,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "LAS" -> laser+=p.effect*0.78*p.accuracy;
                case "PAR" -> particle+=p.effect*0.82*p.accuracy;
                case "PLA" -> plasma+=p.effect*1.05*p.accuracy;
                case "MSL" -> {
                    missile+=p.effect*3.1;
                    missileModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.MISSILE,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "TOR" -> {
                    torpedo+=p.effect*5.4;
                    torpedoModules.add(ProjectileModel.module(design.faction,
                            ProjectileModel.Kind.TORPEDO,p.effect/p.base.effect,
                            p.accuracy,p.integrity/p.base.integrity));
                }
                case "PD", "FLK" -> {
                    pd+=p.effect*2.8;
                    pdWeight++;
                    pdOutput+=p.effect/p.base.effect;
                    pdTracking+=p.accuracy;
                }
                case "SEN" -> {
                    accuracy+=0.12*p.accuracy;
                    sensorRatio+=0.25*(p.accuracy-1);
                }
                case "REP" -> repair+=p.effect*0.0004;
                case "ECM" -> ecm+=p.effect*0.007*p.accuracy;
                case "FTR" -> fighters+=2+(int)(p.effect/18.0);
                case "DRN" -> drones+=2+(int)(p.effect/18.0);
                case "MAG" -> magazines+=(int)(p.effect/3.8);
                default -> { }
            }
            hull+=p.integrity*0.08;
        }
        double mobility=(36+thrust*1.3)*design.hull.mobility*(1-0.35*report.mass()/design.hull.massCapacity);
        double charge=Math.max(2,(report.generation()-report.demand())*0.36);
        return new Fitted(hull,shields,armor,Math.min(0.75,sif),Math.max(40,report.generation()*0.95),
                110+report.cooling()*0.3,charge,Math.max(6,report.cooling()*0.27),report.generatedHeat()*0.15,
                Math.max(9,mobility),Math.min(0.97,accuracy),laser,missile,pd==0?0:70+pd,
                repair,kinetic,plasma,particle,torpedo,Math.min(0.65,ecm),
                fighters,drones,magazines,
                ProjectileModel.average(kineticModules,ProjectileModel.Kind.KINETIC),
                ProjectileModel.average(missileModules,ProjectileModel.Kind.MISSILE),
                ProjectileModel.average(torpedoModules,ProjectileModel.Kind.TORPEDO),
                ProjectileModel.defense(pdWeight==0?1:pdOutput/pdWeight,
                        pdWeight==0?1:pdTracking/pdWeight,sensorRatio));
    }

    /** Portable, deterministic blueprint text (no Java object deserialization). */
    public static String encode(Design design) {
        StringBuilder out=new StringBuilder("CD-SHIP-1|").append(design.faction.name()).append('|')
            .append(design.hull.name()).append('|')
            .append(Base64.getUrlEncoder().withoutPadding().encodeToString(design.name.getBytes(StandardCharsets.UTF_8))).append('\n');
        for(int i=0;i<design.slots();i++) {
            Installed installed=design.at(i);
            if(installed==null)continue;
            out.append(i).append('|').append(installed.component);
            for(Track track:Track.values())out.append('|').append(installed.stage(track));
            out.append('\n');
        }
        return out.toString();
    }
    public static Design decode(String encoded,TechnologyCatalog catalog) {
        if(encoded==null||encoded.length()>20000)throw new IllegalArgumentException("Invalid blueprint size");
        String[] lines=encoded.split("\\n",-1);
        if(lines.length<2||lines.length>70)throw new IllegalArgumentException("Invalid blueprint lines");
        String[] header=lines[0].split("\\|",-1);
        if(header.length!=4||!header[0].equals("CD-SHIP-1"))throw new IllegalArgumentException("Wrong ship file version");
        final Design base;
        try{
            Battle.Faction faction=Battle.Faction.valueOf(header[1]);Hull hull=Hull.valueOf(header[2]);
            String name=new String(Base64.getUrlDecoder().decode(header[3]),StandardCharsets.UTF_8);
            base=new Design(faction,hull,name,new ArrayList<>(Collections.nCopies(hull.slots.size(),null)));
        }catch(RuntimeException err){throw new IllegalArgumentException("Invalid ship identity",err);}
        Design design=base;
        boolean[] seen=new boolean[base.slots()];
        for(int n=1;n<lines.length;n++) {
            if(lines[n].isEmpty())continue;
            String[] p=lines[n].split("\\|",-1);
            if(p.length!=6)throw new IllegalArgumentException("Invalid slot record");
            final int index;
            try{index=Integer.parseInt(p[0]);}catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid slot",bad);}
            if(index<0||index>=seen.length||seen[index])throw new IllegalArgumentException("Duplicate/out-of-range slot");
            seen[index]=true;
            design=design.install(index,p[1]);
            for(int j=0;j<4;j++) {
                final int stage;
                try{stage=Integer.parseInt(p[j+2]);}catch(NumberFormatException bad){throw new IllegalArgumentException("Invalid technology rank",bad);}
                if(stage!=0)design=design.stage(index,Track.values()[j],stage);
            }
        }
        if(!inspect(design,catalog).valid())throw new IllegalArgumentException("Blueprint is not battle ready: "+inspect(design,catalog).explanation());
        return design;
    }
}
