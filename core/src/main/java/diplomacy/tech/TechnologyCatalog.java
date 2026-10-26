package diplomacy.tech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Verifiable, data-driven upgrade catalogue. Strict input parser, no external dependencies. */
public final class TechnologyCatalog {
    public static final String UPGRADES_RESOURCE = "/technology/upgrades-v0.1.csv";
    public static final String COMPONENTS_RESOURCE = "/technology/components-v0.1.csv";
    private static final int COLS = 20;
    private static final Set<String> TRACKS = Set.of("OUTPUT", "EFFICIENCY", "RESILIENCE", "SPECIALIST");
    private static final Set<String> FACTIONS = Set.of(
            "TER","DRA","CAR","ZIP","UZI","MAN","VAE","ZEL","AUR","NYX","UMB");

    public static final class Upgrade {
        public final String id, faction, subsystem, track, name, prerequisite, tradeoff;
        public final int stage, researchCost, industryCost;
        public final double mass, power, heat, output, accuracy, integrity, shield, thrust, cooling, maintenance;

        Upgrade(List<String> cells) {
            id = cells.get(0); faction = cells.get(1); subsystem = cells.get(2);
            track = cells.get(3); stage = integer(cells.get(4), "stage");
            name = cells.get(5); prerequisite = cells.get(6);
            researchCost = integer(cells.get(7), "research cost");
            industryCost = integer(cells.get(8), "industry cost");
            mass = positive(cells.get(9)); power = positive(cells.get(10));
            heat = positive(cells.get(11)); output = positive(cells.get(12));
            accuracy = positive(cells.get(13)); integrity = positive(cells.get(14));
            shield = positive(cells.get(15)); thrust = positive(cells.get(16));
            cooling = positive(cells.get(17)); maintenance = positive(cells.get(18));
            tradeoff = cells.get(19);
            if (!FACTIONS.contains(faction) || !TRACKS.contains(track) || stage < 1 || stage > 6
                    || subsystem.isBlank() || name.isBlank() || tradeoff.isBlank()
                    || researchCost <= 0 || industryCost <= 0)
                throw new IllegalArgumentException("Invalid upgrade metadata: " + id);
            String expected = "TECH-" + faction + "-" + subsystem + "-" + track + "-" + String.format(java.util.Locale.ROOT, "%02d", stage);
            if (!expected.equals(id)) throw new IllegalArgumentException("Noncanonical technology ID: " + id);
        }
    }

    /** Quantified base machinery, including the engineering category used for slot fitting. */
    public static final class Component {
        public final String id, faction, subsystem, name, category;
        public final double mass, power, heat, integrity, effect;
        public final int industryCost;
        Component(List<String> row) {
            id = row.get(0); faction = row.get(1); subsystem = row.get(2);
            name = row.get(3); category = row.get(4);
            mass = positive(row.get(6)); power = positive(row.get(7));
            heat = positive(row.get(8)); integrity = positive(row.get(9));
            industryCost = integer(row.get(10), "component cost"); effect = positive(row.get(11));
            if (!FACTIONS.contains(faction) || !Set.of("weapons", "defense", "systems", "support", "habitat").contains(category)
                    || !id.equals("BASE-" + faction + "-" + subsystem) || name.isBlank()
                    || industryCost <= 0) throw new IllegalArgumentException("Invalid base component: " + id);
        }
    }
    private final Map<String, Component> baseComponents;
    private final Map<String, Upgrade> upgrades;
    private final Set<String> components;
    private TechnologyCatalog(Map<String, Upgrade> map, Map<String, Component> base) {
        upgrades = Collections.unmodifiableMap(map);
        baseComponents = Collections.unmodifiableMap(base);
        components = Collections.unmodifiableSet(base.keySet());
    }
    public int upgradeCount() { return upgrades.size(); }
    public int componentCount() { return components.size(); }
    public Upgrade get(String id) { return upgrades.get(id); }
    public boolean hasBase(String id) { return components.contains(id); }
    public Component base(String id) { return baseComponents.get(id); }
    public Map<String, Component> baseComponents() { return baseComponents; }
    public Map<String, Upgrade> upgrades() { return upgrades; }

    public static TechnologyCatalog loadDefault() throws IOException {
        try (InputStream upgrades = TechnologyCatalog.class.getResourceAsStream(UPGRADES_RESOURCE);
             InputStream components = TechnologyCatalog.class.getResourceAsStream(COMPONENTS_RESOURCE)) {
            if (upgrades == null || components == null) throw new IOException("Bundled technology resources missing");
            return load(upgrades,components,true);
        }
    }
    public static TechnologyCatalog load(InputStream upgradeStream, InputStream componentStream) throws IOException {
        return load(upgradeStream,componentStream,false);
    }
    private static TechnologyCatalog load(InputStream upgradeStream,InputStream componentStream,
                                          boolean includeNewCivilizations)throws IOException {
        if (upgradeStream == null || componentStream == null) throw new IllegalArgumentException("Both inputs required");
        List<List<String>> bases = readCsv(componentStream, 13);
        List<List<String>> rows = readCsv(upgradeStream, COLS);
        if(includeNewCivilizations){
            expandNativeFactions(bases,rows);
            addHabitatFamilies(bases,rows);
        }
        if (bases.size() < 2 || rows.size() < 2) throw new IllegalArgumentException("Missing technology table data");
        if (!bases.get(0).get(0).equals("Base ID") || !rows.get(0).get(0).equals("Tech ID")
                || !rows.get(0).get(6).equals("Prerequisite ID") || !rows.get(0).get(19).equals("Tradeoff"))
            throw new IllegalArgumentException("Unexpected technology CSV schema");
        Map<String, Component> baseMap = new LinkedHashMap<>();
        for (int i = 1; i < bases.size(); i++) {
            Component part = new Component(bases.get(i));
            if (baseMap.putIfAbsent(part.id, part) != null)
                throw new IllegalArgumentException("Duplicate base component ID: " + part.id);
        }
        Set<String> baseIds = baseMap.keySet();
        Map<String, Upgrade> upgrades = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            Upgrade upgrade = new Upgrade(rows.get(i));
            if (upgrades.putIfAbsent(upgrade.id, upgrade) != null)
                throw new IllegalArgumentException("Duplicate technology ID: " + upgrade.id);
        }
        for (Upgrade u : upgrades.values()) {
            String expected = u.stage == 1
                    ? "BASE-" + u.faction + "-" + u.subsystem
                    : "TECH-" + u.faction + "-" + u.subsystem + "-" + u.track + "-" + String.format(java.util.Locale.ROOT, "%02d", u.stage - 1);
            if (!expected.equals(u.prerequisite))
                throw new IllegalArgumentException("Broken parallel-track chain for " + u.id);
            if ((u.stage == 1 && !baseIds.contains(expected))
                    || (u.stage > 1 && !upgrades.containsKey(expected)))
                throw new IllegalArgumentException("Missing prerequisite for " + u.id);
        }
        return new TechnologyCatalog(upgrades, baseMap);
    }

    /**
     * Five additional civilizations have their OWN BASE/TECH identities,
     * costs, prerequisite chains and incremental specialist branches.
     * The original six CSV rosters stay untouched for save compatibility.
     * Derived families start from a known engineering philosophy, then gain
     * distinct native material, energy and targeting trade-offs.
     */
    private record NativeFamily(String inherited,String id,String name,
                                double mass,double power,double heat,
                                double integrity,double output,double aim) {}
    private static final NativeFamily[] NEW_FAMILIES={
        new NativeFamily("DRA","VAE","Vaerith",1.07,1.09,1.05,1.13,1.06,1.01),
        new NativeFamily("UZI","ZEL","Zelthar",0.97,1.04,0.95,0.92,1.02,1.11),
        new NativeFamily("MAN","AUR","Aureate",1.06,0.95,0.91,1.11,0.98,1.05),
        new NativeFamily("CAR","NYX","Nyx",0.98,1.05,1.02,1.14,1.04,0.97),
        new NativeFamily("ZIP","UMB","Umbra",1.12,1.03,1.07,1.17,1.03,0.94)
    };
    private static String ratio(double original,double multiplier){
        return String.format(Locale.ROOT,"%.6f",original*multiplier);
    }
    private static String foreign(String id,String from,String to){
        return id.replace("BASE-"+from+"-","BASE-"+to+"-")
                 .replace("TECH-"+from+"-","TECH-"+to+"-");
    }
    private static void expandNativeFactions(List<List<String>> bases,List<List<String>> upgrades){
        List<List<String>> parentBases=List.copyOf(bases);
        List<List<String>> parentUpgrades=List.copyOf(upgrades);
        for(NativeFamily f:NEW_FAMILIES){
            for(int i=1;i<parentBases.size();i++){
                List<String> original=parentBases.get(i);
                if(!f.inherited.equals(original.get(1)))continue;
                List<String> row=new ArrayList<>(original);
                row.set(0,foreign(row.get(0),f.inherited,f.id));
                row.set(1,f.id);
                row.set(3,f.name+" "+row.get(3));
                row.set(5,f.name+" native engineering lineage; not cross-faction equipment");
                row.set(6,ratio(Double.parseDouble(row.get(6)),f.mass));
                row.set(7,ratio(Double.parseDouble(row.get(7)),f.power));
                row.set(8,ratio(Double.parseDouble(row.get(8)),f.heat));
                row.set(9,ratio(Double.parseDouble(row.get(9)),f.integrity));
                row.set(11,ratio(Double.parseDouble(row.get(11)),f.output));
                bases.add(row);
            }
            for(int i=1;i<parentUpgrades.size();i++){
                List<String> original=parentUpgrades.get(i);
                if(!f.inherited.equals(original.get(1)))continue;
                List<String> row=new ArrayList<>(original);
                row.set(0,foreign(row.get(0),f.inherited,f.id));
                row.set(1,f.id);
                row.set(5,f.name+" "+row.get(5));
                row.set(6,foreign(row.get(6),f.inherited,f.id));
                // Alter the per-stage modifiers only modestly: preserve tradeoffs,
                // but ensure different civilizations aren't identical aliases.
                row.set(12,ratio(Double.parseDouble(row.get(12)),
                        1+(f.output-1)*0.22));
                row.set(13,ratio(Double.parseDouble(row.get(13)),
                        1+(f.aim-1)*0.18));
                row.set(14,ratio(Double.parseDouble(row.get(14)),
                        1+(f.integrity-1)*0.12));
                row.set(19,f.name+" branch: "+row.get(19));
                upgrades.add(row);
            }
        }
    }

    /**
     * Dedicated habitat modules are not native crew capacity. The two new
     * append-only HABITAT sockets require actual installed accommodation AND
     * life support, with independent per-module research for all 11 races.
     * Older six-race CSV files remain stable for blueprint compatibility.
     */
    private static void addHabitatFamilies(List<List<String>> bases,List<List<String>> upgrades){
        String[][] families={
            {"TER","Terran"},{"DRA","Draxian"},{"CAR","Carnelian"},
            {"ZIP","Zippledrow"},{"UZI","Uzipt"},{"MAN","Mandrally"},
            {"VAE","Vaerith"},{"ZEL","Zelthar"},{"AUR","Aureate"},
            {"NYX","Nyx"},{"UMB","Umbra"}
        };
        String[][] modules={
            {"CRW","Crew Accommodation","1.20","0.80","0.32","17.5","2.05"},
            {"LFS","Life Support","1.15","0.90","0.38","16.0","2.12"}
        };
        String[] paths={"OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"};
        String[] grades={"Primitive","Refined","Advanced","Masterwork","Exotic","Transcendent"};
        for(String[] family:families)for(String[] module:modules){
            String id=family[0],subsystem=module[0];
            String baseId="BASE-"+id+"-"+subsystem;
            double nativeVariation=1.0+((id.charAt(0)+id.charAt(1))%7-3)*0.008;
            String output=ratio(Double.parseDouble(module[6]),nativeVariation);
            bases.add(List.of(baseId,id,subsystem,family[1]+" "+module[1],
                    "habitat","Manned fleet operations require explicit accommodation and oxygen support",
                    module[2],module[3],module[4],module[5],"120",output,
                    "Provides no weapon or crew unless physically mounted"));
            for(String branch:paths)for(int rank=1;rank<=6;rank++){
                String level=String.format(Locale.ROOT,"%02d",rank);
                String techId="TECH-"+id+"-"+subsystem+"-"+branch+"-"+level;
                String parent=rank==1?baseId:"TECH-"+id+"-"+subsystem+"-"+branch+"-"+
                        String.format(Locale.ROOT,"%02d",rank-1);
                double outputBoost=switch(branch){
                    case "OUTPUT" -> 1+0.045*rank;
                    case "EFFICIENCY" -> 1;
                    case "RESILIENCE" -> 1+0.012*rank;
                    default -> 1+0.025*rank;
                };
                double mass=switch(branch){
                    case "EFFICIENCY" -> 1-0.020*rank;
                    case "RESILIENCE" -> 1+0.025*rank;
                    default -> 1+0.008*rank;
                };
                double power=branch.equals("EFFICIENCY")?1-0.035*rank:1+0.014*rank;
                double integrity=branch.equals("RESILIENCE")?1+0.045*rank:1;
                upgrades.add(List.of(techId,id,subsystem,branch,level,
                        family[1]+" "+module[1]+" — "+branch+" "+grades[rank-1],
                        parent,Integer.toString(115*rank*rank),
                        Integer.toString(100*rank),ratio(mass,1),ratio(power,1),
                        ratio(branch.equals("EFFICIENCY")?1-0.026*rank:1+0.012*rank,1),
                        ratio(outputBoost,1),"1.0",ratio(integrity,1),
                        "1.0","1.0","1.0",ratio(1+0.016*rank,1),
                        branch+" improves a physical habitat module with costs and tradeoffs"));
            }
        }
    }

    private static int integer(String text, String field) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid " + field + ": " + text); }
    }
    private static double positive(String text) {
        double x;
        try { x = Double.parseDouble(text); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid modifier: " + text); }
        if (!Double.isFinite(x) || x <= 0) throw new IllegalArgumentException("Unsafe modifier: " + text);
        return x;
    }

    /** RFC4180-style quoted fields; field/row size and count are bounded. */
    private static List<List<String>> readCsv(InputStream input, int width) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder field = new StringBuilder();
            List<String> row = new ArrayList<>();
            boolean quoted = false, afterQuote = false;
            int ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                if (quoted) {
                    if (c == '"') {
                        reader.mark(1); int next = reader.read();
                        if (next == '"') field.append('"');
                        else { quoted = false; afterQuote = true; if (next != -1) reader.reset(); }
                    } else field.append(c);
                } else if (c == '"') {
                    if (!afterQuote && field.length() == 0) quoted = true;
                    else throw new IllegalArgumentException("Unexpected quotation in CSV");
                } else if (c == ',' || c == '\n') {
                    row.add(field.toString()); field.setLength(0); afterQuote = false;
                    if (row.size() > width) throw new IllegalArgumentException("Too many CSV columns");
                    if (c == '\n') {
                        if (row.size() != width) throw new IllegalArgumentException("Wrong CSV width at row " + (result.size()+1));
                        result.add(row); row = new ArrayList<>();
                    }
                } else if (c == '\r') {
                    // CSV emitter uses LF. For CRLF files the next LF completes the row.
                } else {
                    if (afterQuote) throw new IllegalArgumentException("Unexpected data after CSV closing quote");
                    field.append(c);
                }
                if (field.length() > 10000 || result.size() > 20000)
                    throw new IllegalArgumentException("CSV input exceeds safe limits");
            }
            if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
            if (field.length() > 0 || !row.isEmpty()) {
                row.add(field.toString());
                if (row.size() != width) throw new IllegalArgumentException("Wrong final CSV width");
                result.add(row);
            }
        }
        return result;
    }
}
