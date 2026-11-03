package diplomacy.tech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
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
            if (!FACTIONS.contains(faction) || !TRACKS.contains(track) || stage < 1 || stage > 9
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
    private final Map<String, Upgrade> sixStages;
    private final boolean deferredHigherStages;

    private static final List<String> VARIANT_FACTIONS=List.of(
            "TER","DRA","CAR","ZIP","UZI","MAN","VAE","ZEL","AUR","NYX","UMB");
    private static final List<String> VARIANT_TRACKS=List.of(
            "OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST");
    private static final int VIRTUAL_SURVIVAL_NODES=48*11*4*9;
    private static final int VIRTUAL_NETWORK_NODES=24*11*4*9;
    private final int upgradeTotal;
    private final Set<String> components;
    private TechnologyCatalog(Map<String, Upgrade> map, Map<String, Component> base,
                              boolean generateHigherStages) {
        // Do NOT allocate 13,200 Stage VII-IX objects, CSV rows and Strings
        // while an Android 10 VM is limited to a 16 MiB Java heap. These stages
        // are direct, deterministic derivations of their Stage V/VI ancestors.
        sixStages=Collections.unmodifiableMap(map);
        deferredHigherStages=generateHigherStages;
        int finalBranches=0;
        if(generateHigherStages)for(Upgrade item:map.values())
            if(item.stage==6){
                String stem=item.id.substring(0,item.id.length()-2);
                if(!map.containsKey(stem+"05"))
                    throw new IllegalArgumentException("Missing fifth-stage source "+item.id);
                finalBranches++;
            }
        upgradeTotal=Math.addExact(Math.addExact(map.size(),Math.multiplyExact(finalBranches,3)),
                generateHigherStages?VIRTUAL_SURVIVAL_NODES+VIRTUAL_NETWORK_NODES:0);
        upgrades=generateHigherStages?Collections.unmodifiableMap(new LazyUpgrades()):
                sixStages;
        baseComponents=Collections.unmodifiableMap(base);
        components=Collections.unmodifiableSet(base.keySet());
    }
    public int upgradeCount() { return upgradeTotal; }
    public int componentCount() { return components.size(); }
    public Upgrade get(String id) {
        Upgrade virtual=survivalVariantUpgrade(id);
        if(virtual!=null)return virtual;
        virtual=networkVariantUpgrade(id);
        if(virtual!=null)return virtual;
        Upgrade found=sixStages.get(id);
        if(found!=null||!deferredHigherStages||id==null||id.length()<4)return found;
        int end=id.length();
        char tens=id.charAt(end-2),unit=id.charAt(end-1);
        if(tens!='0'||unit<'7'||unit>'9')return null;
        String stem=id.substring(0,end-2);
        Upgrade stageSix=sixStages.get(stem+"06");
        Upgrade stageFive=sixStages.get(stem+"05");
        if(stageSix==null||stageFive==null)return null;
        int stage=unit-'0';
        long research=Math.round(stageSix.researchCost*StrictMath.pow(stage/6.0,2));
        long industry=Math.round(stageSix.industryCost*StrictMath.pow(stage/6.0,2));
        if(research>Integer.MAX_VALUE||industry>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Higher-tier technology cost overflows "+id);
        double[] current={stageSix.mass,stageSix.power,stageSix.heat,stageSix.output,
                stageSix.accuracy,stageSix.integrity,stageSix.shield,stageSix.thrust,
                stageSix.cooling,stageSix.maintenance};
        double[] previous={stageFive.mass,stageFive.power,stageFive.heat,stageFive.output,
                stageFive.accuracy,stageFive.integrity,stageFive.shield,stageFive.thrust,
                stageFive.cooling,stageFive.maintenance};
        ArrayList<String> cells=new ArrayList<>(COLS);
        Collections.addAll(cells,id,stageSix.faction,stageSix.subsystem,
                stageSix.track,Integer.toString(stage),
                stageSix.name+" / Stage "+stage,
                stem+"0"+(stage-1),
                Long.toString(Math.max(1,research)),
                Long.toString(Math.max(1,industry)));
        for(int i=0;i<current.length;i++)
            cells.add(ratio(Math.max(.05,current[i]+(current[i]-previous[i])*(stage-6)),1));
        cells.add(stageSix.tradeoff+"; higher-stage engineering and upkeep");
        return new Upgrade(cells);
    }

    /** All nine ranks have their own IDs, costs and precursor chain while
     * inheriting the original branch's balanced physical multiplier curve. */
    private Upgrade survivalVariantUpgrade(String id){
        if(!deferredHigherStages||id==null)return null;
        String[] parts=id.split("-",5);
        if(parts.length!=5||!"TECH".equals(parts[0])||
                !FACTIONS.contains(parts[1])||!TRACKS.contains(parts[3])||
                !parts[4].matches("0[1-9]"))return null;
        SurvivalHardwareCatalog.Variant v=SurvivalHardwareCatalog.findVariant(parts[2]);
        if(v==null||!baseComponents.containsKey("BASE-"+parts[1]+"-"+v.code()))
            return null;
        int stage=parts[4].charAt(1)-'0';
        Upgrade source=get("TECH-"+parts[1]+"-"+v.root()+"-"+
                parts[3]+"-"+parts[4]);
        if(source==null)return null;
        String predecessor=stage==1?"BASE-"+parts[1]+"-"+v.code():
                "TECH-"+parts[1]+"-"+v.code()+"-"+parts[3]+"-"+
                        String.format(Locale.ROOT,"%02d",stage-1);
        long research=Math.round(source.researchCost*v.price());
        long industry=Math.round(source.industryCost*v.price());
        if(research>Integer.MAX_VALUE||industry>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Technology cost overflow "+id);
        ArrayList<String> cells=new ArrayList<>(COLS);
        Collections.addAll(cells,id,parts[1],v.code(),parts[3],
                Integer.toString(stage),v.name()+" / "+parts[3]+" "+stage,
                predecessor,Long.toString(Math.max(1,research)),
                Long.toString(Math.max(1,industry)));
        for(double multiplier:new double[]{source.mass,source.power,source.heat,
                source.output,source.accuracy,source.integrity,source.shield,
                source.thrust,source.cooling,source.maintenance})
            cells.add(ratio(multiplier,1));
        cells.add(v.name()+": "+source.tradeoff);
        return new Upgrade(cells);
    }

    /** Network hardware generations have their own independent IX-rank
     * research paths, resolved lazily to protect small Android heaps. */
    private Upgrade networkVariantUpgrade(String id){
        if(!deferredHigherStages||id==null)return null;
        String[] parts=id.split("-",5);
        if(parts.length!=5||!"TECH".equals(parts[0])||
                !FACTIONS.contains(parts[1])||!TRACKS.contains(parts[3])||
                !parts[4].matches("0[1-9]"))return null;
        SensorNetworkCatalog.Model v=SensorNetworkCatalog.variant(parts[2]);
        if(v==null||!baseComponents.containsKey("BASE-"+parts[1]+"-"+v.code()))
            return null;
        int stage=parts[4].charAt(1)-'0';
        Upgrade source=get("TECH-"+parts[1]+"-"+v.root()+"-"+
                parts[3]+"-"+parts[4]);
        if(source==null)return null;
        String predecessor=stage==1?"BASE-"+parts[1]+"-"+v.code():
                "TECH-"+parts[1]+"-"+v.code()+"-"+parts[3]+"-"+
                        String.format(Locale.ROOT,"%02d",stage-1);
        long research=Math.round(source.researchCost*v.price());
        long industry=Math.round(source.industryCost*v.price());
        if(research>Integer.MAX_VALUE||industry>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Network research overflows "+id);
        ArrayList<String> cells=new ArrayList<>(COLS);
        Collections.addAll(cells,id,parts[1],v.code(),parts[3],
                Integer.toString(stage),v.name()+" / "+parts[3]+" "+stage,
                predecessor,Long.toString(Math.max(1,research)),
                Long.toString(Math.max(1,industry)));
        for(double multiplier:new double[]{source.mass,source.power,source.heat,
                source.output,source.accuracy,source.integrity,source.shield,
                source.thrust,source.cooling,source.maintenance})
            cells.add(ratio(multiplier,1));
        cells.add(v.name()+": "+source.tradeoff);
        return new Upgrade(cells);
    }

    public boolean hasBase(String id) { return components.contains(id); }
    public Component base(String id) { return baseComponents.get(id); }
    public Map<String, Component> baseComponents() { return baseComponents; }
    public Map<String, Upgrade> upgrades() { return upgrades; }
    /** Only canonical IDs are needed for navigation. Avoid allocating all
     * 13,200 derived Upgrade records just to populate the field-wiki index. */
    public List<String> upgradeIds(){
        ArrayList<String> ids=new ArrayList<>(upgradeTotal);
        ids.addAll(sixStages.keySet());
        if(deferredHigherStages)for(Upgrade u:sixStages.values())
            if(u.stage==6){
                String stem=u.id.substring(0,u.id.length()-2);
                ids.add(stem+"07");ids.add(stem+"08");ids.add(stem+"09");
            }
        if(deferredHigherStages)
            for(String faction:VARIANT_FACTIONS)
                for(SurvivalHardwareCatalog.Variant variant:SurvivalHardwareCatalog.VARIANTS)
                    for(String track:VARIANT_TRACKS)
                        for(int rank=1;rank<=9;rank++)
                            ids.add("TECH-"+faction+"-"+variant.code()+"-"+track+"-"+
                                    String.format(Locale.ROOT,"%02d",rank));
        if(deferredHigherStages)
            for(String faction:VARIANT_FACTIONS)
                for(SensorNetworkCatalog.Model variant:SensorNetworkCatalog.VARIANTS)
                    for(String track:VARIANT_TRACKS)
                        for(int rank=1;rank<=9;rank++)
                            ids.add("TECH-"+faction+"-"+variant.code()+"-"+track+"-"+
                                    String.format(Locale.ROOT,"%02d",rank));
        if(ids.size()!=upgradeTotal)
            throw new IllegalStateException("Virtual stage count mismatch");
        return ids;
    }

    /** Read-only enumeration generates one late-rank value at a time. */
    private final class LazyUpgrades extends AbstractMap<String,Upgrade> {
        @Override public int size(){return upgradeTotal;}
        @Override public Upgrade get(Object key){
            return key instanceof String ? TechnologyCatalog.this.get((String)key):null;
        }
        @Override public Set<Entry<String,Upgrade>> entrySet(){
            return new AbstractSet<>() {
                @Override public int size(){return upgradeTotal;}
                @Override public Iterator<Entry<String,Upgrade>> iterator(){
                    return new Iterator<>() {
                        private final Iterator<Entry<String,Upgrade>> early=
                                sixStages.entrySet().iterator();
                        private final Iterator<Upgrade> branch=
                                sixStages.values().iterator();
                        private Upgrade nextBranch;
                        private int higher=7;
                        private int virtualIndex=0;
                        @Override public boolean hasNext(){
                            if(early.hasNext())return true;
                            if(nextBranch!=null&&higher<=9)return true;
                            while(branch.hasNext()){
                                Upgrade test=branch.next();
                                if(test.stage==6){nextBranch=test;higher=7;return true;}
                            }
                            return deferredHigherStages&&virtualIndex<VIRTUAL_SURVIVAL_NODES+VIRTUAL_NETWORK_NODES;
                        }
                        @Override public Entry<String,Upgrade> next(){
                            if(early.hasNext())return early.next();
                            if(!hasNext())throw new NoSuchElementException();
                            if(nextBranch!=null&&higher<=9){
                                Upgrade value=TechnologyCatalog.this.get(
                                        nextBranch.id.substring(0,nextBranch.id.length()-2)+
                                        "0"+higher++);
                                return new SimpleImmutableEntry<>(value.id,value);
                            }
                            int index=virtualIndex++;
                            boolean survival=index<VIRTUAL_SURVIVAL_NODES;
                            int n=survival?index:index-VIRTUAL_SURVIVAL_NODES;
                            int stage=n%9+1;
                            n/=9;
                            String track=VARIANT_TRACKS.get(n%4);
                            n/=4;
                            String code;
                            if(survival){
                                code=SurvivalHardwareCatalog.VARIANTS.get(
                                        n%SurvivalHardwareCatalog.VARIANTS.size()).code();
                                n/=SurvivalHardwareCatalog.VARIANTS.size();
                            }else{
                                code=SensorNetworkCatalog.VARIANTS.get(
                                        n%SensorNetworkCatalog.VARIANTS.size()).code();
                                n/=SensorNetworkCatalog.VARIANTS.size();
                            }
                            String id="TECH-"+VARIANT_FACTIONS.get(n)+"-"+code+"-"+
                                    track+"-"+String.format(Locale.ROOT,"%02d",stage);
                            Upgrade value=TechnologyCatalog.this.get(id);
                            return new SimpleImmutableEntry<>(id,value);
                        }
                    };
                }
            };
        }
    }

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
            addShieldTransferProjectors(bases,rows);
            addSpecializedSupportFamilies(bases,rows);
            addTenderFamilies(bases,rows);
            addArmorAndBulkheadFamilies(bases,rows);
            addSensingFamilies(bases,rows);
            addSensingHardwareVariants(bases,rows);
            addSensorNetworkFamilies(bases,rows);
            addSensorNetworkVariants(bases);
            addSurvivalAndUnsafeFamilies(bases,rows);
            addSurvivalHardwareVariants(bases);
            addDumbfireRocketFamilies(bases,rows);
            addRotaryPointDefenseFamilies(bases,rows);
            // Ranks VII-IX are synthesized on demand. No 13,200 extra
            // temporary CSV arrays/strings retained at Android startup.
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
            // Discard expanded CSV row as soon as its immutable Upgrade is
            // parsed. This avoids retaining both formats on constrained VMs.
            rows.set(i,null);
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
        return new TechnologyCatalog(upgrades, baseMap,includeNewCivilizations);
    }

    /**
     * Extend each existing native branch with VII-IX without rewriting the
     * six-stage CSV. Stage VI is retained exactly as released. Every higher
     * stage requires its direct predecessor and extrapolates existing physical
     * tradeoffs: no free weapons, slots or intrinsic hull bonuses.
     */
    private static void extendToNineStages(List<List<String>> rows) {
        Map<String,List<String>> original=new LinkedHashMap<>();
        for(int i=1;i<rows.size();i++)original.put(rows.get(i).get(0),rows.get(i));
        for(List<String> stageSix:List.copyOf(original.values())) {
            if(Integer.parseInt(stageSix.get(4))!=6)continue;
            String stem=stageSix.get(0).substring(0,stageSix.get(0).length()-2);
            List<String> stageFive=original.get(stem+"05");
            if(stageFive==null)throw new IllegalArgumentException(
                    "Missing fifth-stage source for "+stageSix.get(0));
            for(int stage=7;stage<=9;stage++) {
                List<String> entry=new ArrayList<>(stageSix);
                entry.set(0,stem+String.format(Locale.ROOT,"%02d",stage));
                entry.set(4,Integer.toString(stage));
                entry.set(5,stageSix.get(5)+" / Stage "+stage);
                entry.set(6,stem+String.format(Locale.ROOT,"%02d",stage-1));
                for(int index=7;index<=8;index++) {
                    long baseline=Long.parseLong(stageSix.get(index));
                    long price=Math.round(baseline*StrictMath.pow(stage/6.0,2.0));
                    if(price>Integer.MAX_VALUE)throw new IllegalArgumentException(
                            "Technology cost overflows "+entry.get(0));
                    entry.set(index,Long.toString(Math.max(1L,price)));
                }
                for(int index=9;index<=18;index++) {
                    double fifth=Double.parseDouble(stageFive.get(index));
                    double sixth=Double.parseDouble(stageSix.get(index));
                    entry.set(index,ratio(Math.max(0.05,
                            sixth+(sixth-fifth)*(stage-6)),1.0));
                }
                entry.set(19,stageSix.get(19)+"; higher-stage engineering and upkeep");
                rows.add(entry);
            }
        }
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

    /**
     * Shield generation is NOT shield transfer. Each civilization gets one
     * distinct SUPPORT-slot shield-transfer projector with its own engineering
     * cost and exclusive upgrade branches. Derive from the existing full SHD
     * catalog so all 11 factions retain native prices and research chains.
     * Do not modify the legacy six-faction CSV or older saved blueprints.
     */
    private static void addShieldTransferProjectors(List<List<String>> bases,
                                                     List<List<String>> upgrades) {
        List<List<String>> generators=List.copyOf(bases);
        List<List<String>> generatorResearch=List.copyOf(upgrades);
        for(int i=1;i<generators.size();i++){
            List<String> source=generators.get(i);
            if(!"SHD".equals(source.get(2)))continue;
            List<String> module=new ArrayList<>(source);
            module.set(0,module.get(0).replace("-SHD","-XFR"));
            module.set(2,"XFR");
            module.set(3,"Shield Transfer Projector");
            module.set(4,"support");
            module.set(6,ratio(Double.parseDouble(module.get(6)),0.80));
            module.set(7,ratio(Double.parseDouble(module.get(7)),0.85));
            module.set(8,ratio(Double.parseDouble(module.get(8)),0.90));
            module.set(10,Integer.toString((int)Math.ceil(Integer.parseInt(module.get(10))*1.35)));
            module.set(11,ratio(Double.parseDouble(module.get(11)),0.82));
            module.set(12,"Dedicated powered allied shield transfer; supplies no independent shields");
            bases.add(module);
        }
        for(int i=1;i<generatorResearch.size();i++){
            List<String> source=generatorResearch.get(i);
            if(!"SHD".equals(source.get(2)))continue;
            List<String> tech=new ArrayList<>(source);
            tech.set(0,tech.get(0).replace("-SHD-","-XFR-"));
            tech.set(2,"XFR");
            tech.set(5,tech.get(5).replace("Shield Generator","Shield Transfer Projector"));
            tech.set(6,tech.get(6).replace("-SHD-","-XFR-").replace("-SHD","-XFR"));
            tech.set(19,"Dedicated transfer projector: "+tech.get(19));
            upgrades.add(tech);
        }
    }

    /**
     * Explicitly researched, PURPOSE-FITTED logistics modules. All are placed
     * in real support hardpoints, own their energy/heat/mass budgets, and have
     * independent six-stage upgrades per track and civilization.
     * Legacy SHD and REP remain distinct and do not acquire these abilities.
     */
    private static void addSpecializedSupportFamilies(List<List<String>> bases,
                                                       List<List<String>> upgrades){
        String[][] types={
            {"HPR","Long-Range Hull Restoration Projector","XFR"},
            {"APR","Armor Restoration Projector","XFR"},
            {"PWR","Power Transfer Projector","XFR"},
            {"DHL","Hull Service Drone Bay","DRN"},
            {"DSH","Shield Service Drone Bay","DRN"},
            {"DAR","Armor Service Drone Bay","DRN"},
            {"DPW","Power Service Drone Bay","DRN"},
            {"AHL","Hull Restoration Aura","XFR"},
            {"ASH","Shield Restoration Aura","XFR"},
            {"AAR","Armor Restoration Aura","XFR"},
            {"APW","Power Restoration Aura","XFR"}
        };
        // Include already-generated native families and XFR projectors.
        List<List<String>> sourceBases=List.copyOf(bases);
        List<List<String>> sourceUpgrades=List.copyOf(upgrades);
        for(String[] type:types){
            String code=type[0],parent=type[2];
            for(int i=1;i<sourceBases.size();i++){
                List<String> source=sourceBases.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> entry=new ArrayList<>(source);
                entry.set(0,entry.get(0).replace("-"+parent,"-"+code));
                entry.set(2,code);
                entry.set(3,type[1]);
                entry.set(4,"support");
                entry.set(6,ratio(Double.parseDouble(entry.get(6)),1.04));
                entry.set(7,ratio(Double.parseDouble(entry.get(7)),1.12));
                entry.set(8,ratio(Double.parseDouble(entry.get(8)),1.10));
                entry.set(10,Integer.toString((int)Math.ceil(Integer.parseInt(entry.get(10))*1.15)));
                entry.set(11,ratio(Double.parseDouble(entry.get(11)),0.80));
                entry.set(12,"Physical specialized service module; no passive hull-wide transfer");
                bases.add(entry);
            }
            for(int i=1;i<sourceUpgrades.size();i++){
                List<String> source=sourceUpgrades.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> entry=new ArrayList<>(source);
                entry.set(0,entry.get(0).replace("-"+parent+"-","-"+code+"-"));
                entry.set(2,code);
                entry.set(5,entry.get(5).replace(parent.equals("DRN")?"Drone Controller":
                        "Shield Transfer Projector",type[1]));
                entry.set(6,entry.get(6).replace("-"+parent+"-","-"+code+"-")
                        .replace("-"+parent,"-"+code));
                entry.set(19,type[1]+": "+entry.get(19));
                upgrades.add(entry);
            }
        }
    }

    /**
     * Five new defensive module families across all 11 native engineering
     * catalogs. Legacy IDs, saved blueprints, and the four exclusive research
     * tracks stay intact. Extend each source six-rank chain to ranks 7-9
     * using the existing catalog extrapolator, not new research rules.
     */
    private static void addArmorAndBulkheadFamilies(List<List<String>> bases,
                                                     List<List<String>> upgrades) {
        String[][] families={
            // code, title, source, mass, power, heat, integrity, output, price
            {"CMP","Composite Plating","ARM","1.04","0.90","0.94","1.12","0.90","1.06"},
            {"ABL","Ceramic Ablative Laminate","ARM","0.86","0.92","0.98","0.90","0.79","1.04"},
            {"DEN","Dense Kinetic Plating","ARM","1.36","1.04","1.12","1.30","1.10","1.30"},
            {"RAC","Reactive Armor Cells","ARM","1.17","1.20","1.16","1.03","0.87","1.24"},
            {"BLK","Reinforced Bulkheads","SIF","1.28","0.72","0.85","1.20","0.84","1.18"}
        };
        List<List<String>> parentBases=List.copyOf(bases);
        List<List<String>> parentUpgrades=List.copyOf(upgrades);
        for(String[] kind:families){
            String code=kind[0],parent=kind[2];
            for(int i=1;i<parentBases.size();i++){
                List<String> original=parentBases.get(i);
                if(!parent.equals(original.get(2)))continue;
                List<String> row=new ArrayList<>(original);
                row.set(0,row.get(0).replace("-"+parent,"-"+code));
                row.set(2,code);
                row.set(3,kind[1]);
                row.set(4,"defense");
                for(int column=6;column<=9;column++)
                    row.set(column,ratio(Double.parseDouble(row.get(column)),
                            Double.parseDouble(kind[column-3])));
                row.set(10,Long.toString(Math.round(Long.parseLong(row.get(10))*
                        Double.parseDouble(kind[8]))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),
                        Double.parseDouble(kind[7])));
                row.set(12,kind[1]+": physical defense socket and finite mass/power budget");
                bases.add(row);
            }
            for(int i=1;i<parentUpgrades.size();i++){
                List<String> original=parentUpgrades.get(i);
                if(!parent.equals(original.get(2)))continue;
                List<String> row=new ArrayList<>(original);
                row.set(0,row.get(0).replace("-"+parent+"-","-"+code+"-"));
                row.set(2,code);
                row.set(5,kind[1]+" - "+original.get(3)+" rank "+original.get(4));
                row.set(6,row.get(6).replace("-"+parent+"-","-"+code+"-")
                        .replace("-"+parent,"-"+code));
                int stage=Integer.parseInt(original.get(4));
                String branch=original.get(3);
                double mass=Double.parseDouble(row.get(9));
                double power=Double.parseDouble(row.get(10));
                double effect=Double.parseDouble(row.get(12));
                double integrity=Double.parseDouble(row.get(14));
                switch(branch){
                    case "OUTPUT" -> {effect*=1+.045*stage;mass*=1+.015*stage;}
                    case "EFFICIENCY" -> {mass*=1-.025*stage;power*=1-.022*stage;}
                    case "RESILIENCE" -> integrity*=1+.035*stage;
                    case "SPECIALIST" -> {effect*=1+.018*stage;integrity*=1+.022*stage;}
                    default -> throw new IllegalStateException("Unknown research branch");
                }
                row.set(9,ratio(mass,1));row.set(10,ratio(power,1));
                row.set(12,ratio(effect,1));row.set(14,ratio(integrity,1));
                row.set(19,kind[1]+": "+branch+
                        " upgrade; mass, heat, power and industry compete with armament");
                upgrades.add(row);
            }
        }
    }

    /**
     * Four distinct fleet-tender storerooms; inherit each faction's genuine
     * native XFR line, and add all four paid exclusive upgrade paths. The
     * existing nine-rank catalog extension applies to these source chains.
     */
    private static void addTenderFamilies(List<List<String>> bases,
                                          List<List<String>> upgrades){
        String[][] kinds={
            {"KTS","Kinetic Ammunition Tender","1.17","0.84","0.94","1.13"},
            {"MTS","Missile Magazine Tender","1.28","1.00","1.10","1.25"},
            {"TTS","Torpedo Magazine Tender","1.32","1.06","1.14","1.34"},
            {"FTS","Propellant Fleet Tender","1.21","0.86","0.96","1.19"}
        };
        List<List<String>> originalBases=List.copyOf(bases);
        List<List<String>> originalUpgrades=List.copyOf(upgrades);
        for(String[] kind:kinds){
            for(int i=1;i<originalBases.size();i++){
                List<String> source=originalBases.get(i);
                if(!"XFR".equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-XFR","-"+kind[0]));
                row.set(2,kind[0]);row.set(3,kind[1]);row.set(4,"support");
                row.set(6,ratio(Double.parseDouble(row.get(6)),Double.parseDouble(kind[2])));
                row.set(7,ratio(Double.parseDouble(row.get(7)),Double.parseDouble(kind[3])));
                row.set(8,ratio(Double.parseDouble(row.get(8)),Double.parseDouble(kind[4])));
                row.set(10,Integer.toString((int)StrictMath.ceil(
                        Integer.parseInt(row.get(10))*Double.parseDouble(kind[5]))));
                row.set(12,"Finite stocked cargo. Allied replenishment uses real inventory, power, range, and research.");
                bases.add(row);
            }
            for(int i=1;i<originalUpgrades.size();i++){
                List<String> source=originalUpgrades.get(i);
                if(!"XFR".equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-XFR-","-"+kind[0]+"-"));
                row.set(2,kind[0]);
                row.set(5,kind[1]+" / "+row.get(3)+" stage "+row.get(4));
                row.set(6,row.get(6).replace("-XFR-","-"+kind[0]+"-")
                        .replace("-XFR","-"+kind[0]));
                row.set(19,kind[1]+": "+row.get(19)+"; finite stores and support-slot tradeoffs");
                upgrades.add(row);
            }
        }
    }


    /**
     * Distinct detection and counter-detection hardware for all eleven native
     * factions. Source SEN/ECM engineering tables are cloned, never modified:
     * existing save IDs retain their meaning, and each new device gets its
     * own four mutually exclusive nine-rank research branches.
     */
    private static void addSensingFamilies(List<List<String>> bases,
                                           List<List<String>> upgrades) {
        // code, name, parent, mount category, mass, power, heat, integrity, effect, price
        String[][] types={
            {"RDR","Long-Range Radar Array","SEN","systems","1.23","1.19","1.12","0.98","1.15","1.18"},
            {"STC","Radar-Stealth Coating","ECM","defense","0.89","0.64","0.75","1.10","0.82","1.16"},
            {"IRT","Infrared Telescope","SEN","systems","0.92","1.04","1.04","0.92","0.94","1.04"},
            {"HTD","Thermal Emission Damper","ECM","systems","1.10","1.23","1.31","1.07","0.91","1.23"},
            {"VCL","Active Visual Cloak","ECM","systems","1.21","1.45","1.32","0.87","1.10","1.42"},
            {"ACU","Sensor Acuity Amplifier","SEN","systems","1.04","1.24","1.13","0.96","1.08","1.22"},
            {"XRY","X-Ray Detector","SEN","systems","1.22","1.38","1.37","1.01","0.96","1.39"},
            {"GRA","Gravitic Anomaly Sensor","SEN","systems","1.46","1.54","1.42","1.13","1.10","1.58"},
            {"HLP","Holo-Decoy Projector","ECM","support","1.09","1.36","1.24","0.94","1.04","1.34"}
        };
        List<List<String>> sourceBases=List.copyOf(bases);
        List<List<String>> sourceUpgrades=List.copyOf(upgrades);
        for(String[] type:types) {
            String code=type[0],parent=type[2];
            for(int i=1;i<sourceBases.size();i++) {
                List<String> base=sourceBases.get(i);
                if(!parent.equals(base.get(2)))continue;
                List<String> row=new ArrayList<>(base);
                row.set(0,row.get(0).replace("-"+parent,"-"+code));
                row.set(2,code);
                row.set(3,type[1]);
                row.set(4,type[3]);
                for(int col=6;col<=9;col++)
                    row.set(col,ratio(Double.parseDouble(row.get(col)),
                            Double.parseDouble(type[col-2])));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*Double.parseDouble(type[9])))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),
                        Double.parseDouble(type[8])));
                row.set(12,"Dedicated powered detection or deception machinery; fitted socket required");
                bases.add(row);
            }
            for(int i=1;i<sourceUpgrades.size();i++) {
                List<String> source=sourceUpgrades.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+parent+"-","-"+code+"-"));
                row.set(2,code);
                row.set(5,type[1]+" - "+row.get(3)+" rank "+row.get(4));
                row.set(6,row.get(6).replace("-"+parent+"-","-"+code+"-")
                        .replace("-"+parent,"-"+code));
                row.set(19,type[1]+": "+row.get(19));
                upgrades.add(row);
            }
        }
    }


    /** Each root sensor line gains four independent hardware research chains.
     * Clone six-stage root data, then pass through the existing IX-rank engine. */
    private static void addSensingHardwareVariants(List<List<String>> bases,
                                                   List<List<String>> upgrades) {
        List<List<String>> roots=List.copyOf(bases);
        List<List<String>> research=List.copyOf(upgrades);
        for(SensingVariantCatalog.Model model:SensingVariantCatalog.VARIANTS) {
            String parent=model.base(),code=model.code();
            for(int i=1;i<roots.size();i++) {
                List<String> source=roots.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+parent,"-"+code));
                row.set(2,code);row.set(3,model.name());
                row.set(5,model.name()+"; technology tier "+model.requiredTier());
                row.set(6,ratio(Double.parseDouble(row.get(6)),model.mass()));
                row.set(7,ratio(Double.parseDouble(row.get(7)),model.power()));
                row.set(8,ratio(Double.parseDouble(row.get(8)),model.heat()));
                row.set(9,ratio(Double.parseDouble(row.get(9)),model.integrity()));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*model.price()))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),model.performance()));
                row.set(12,"Purpose-built physical "+parent+" alternative; engineering tier "+
                        model.requiredTier()+" with mass, heat, power and efficiency tradeoffs");
                bases.add(row);
            }
            for(int i=1;i<research.size();i++) {
                List<String> source=research.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+parent+"-","-"+code+"-"));
                row.set(2,code);row.set(5,model.name()+" "+row.get(3)+" "+row.get(4));
                row.set(6,row.get(6).replace("-"+parent+"-","-"+code+"-")
                        .replace("-"+parent,"-"+code));
                row.set(19,model.name()+": "+row.get(19));
                upgrades.add(row);
            }
        }
    }


    /** Native faction modules based on the paid SEN engineering budget.
     * Each root owns independent research; no replacement of existing SEN. */
    private static void addSensorNetworkFamilies(List<List<String>> bases,
                                                 List<List<String>> upgrades){
        List<List<String>> originals=List.copyOf(bases);
        List<List<String>> research=List.copyOf(upgrades);
        for(SensorNetworkCatalog.Family family:SensorNetworkCatalog.FAMILIES){
            for(int i=1;i<originals.size();i++){
                List<String> source=originals.get(i);
                if(!"SEN".equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-SEN","-"+family.code()));
                row.set(2,family.code());row.set(3,family.name());
                row.set(4,family.category());
                row.set(5,"Standard fleet sensor-network equipment; tier I");
                row.set(6,ratio(Double.parseDouble(row.get(6)),family.mass()));
                row.set(7,ratio(Double.parseDouble(row.get(7)),family.power()));
                row.set(8,ratio(Double.parseDouble(row.get(8)),family.heat()));
                row.set(9,ratio(Double.parseDouble(row.get(9)),family.integrity()));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*family.price()))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),family.output()));
                row.set(12,"Physical information equipment; requires actual sensor contacts");
                bases.add(row);
            }
            for(int i=1;i<research.size();i++){
                List<String> source=research.get(i);
                if(!"SEN".equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-SEN-","-"+family.code()+"-"));
                row.set(2,family.code());
                row.set(5,family.name()+" "+row.get(3)+" "+row.get(4));
                row.set(6,row.get(6).replace("-SEN-","-"+family.code()+"-")
                        .replace("-SEN","-"+family.code()));
                row.set(19,family.name()+": "+row.get(19));
                upgrades.add(row);
            }
        }
    }

    /** 264 fitted variants; IX-rank research instantiated only when requested. */
    private static void addSensorNetworkVariants(List<List<String>> bases){
        List<List<String>> originals=List.copyOf(bases);
        for(SensorNetworkCatalog.Model model:SensorNetworkCatalog.VARIANTS){
            for(int i=1;i<originals.size();i++){
                List<String> source=originals.get(i);
                if(!model.root().equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+model.root(),"-"+model.code()));
                row.set(2,model.code());row.set(3,model.name());
                row.set(5,model.name()+"; tier "+model.requiredTier());
                row.set(6,ratio(Double.parseDouble(row.get(6)),model.mass()));
                row.set(7,ratio(Double.parseDouble(row.get(7)),model.power()));
                row.set(8,ratio(Double.parseDouble(row.get(8)),model.heat()));
                row.set(9,ratio(Double.parseDouble(row.get(9)),model.integrity()));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*model.price()))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),model.performance()));
                row.set(12,"Independent fleet-information hardware; no fictitious sensor channel");
                bases.add(row);
            }
        }
    }

    /** Native, socket-bound damage control and unsafe retrofit families.
     * Clone root equipment and its independent four engineering tracks.
     * Existing components and saved design IDs are not modified. */
    private static void addSurvivalAndUnsafeFamilies(List<List<String>> bases,
                                                     List<List<String>> upgrades) {
        List<List<String>> sourceBases=List.copyOf(bases);
        List<List<String>> sourceUpgrades=List.copyOf(upgrades);
        for(SurvivalHardwareCatalog.Family family:SurvivalHardwareCatalog.FAMILIES) {
            String code=family.code(),parent=family.parent();
            for(int i=1;i<sourceBases.size();i++){
                List<String> source=sourceBases.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+parent,"-"+code));
                row.set(2,code);row.set(3,family.name());row.set(4,family.category());
                row.set(5,"Native "+family.name()+"; engineering tier "+family.requiredTier());
                row.set(6,ratio(Double.parseDouble(row.get(6)),family.mass()));
                row.set(7,ratio(Double.parseDouble(row.get(7)),family.power()));
                row.set(8,ratio(Double.parseDouble(row.get(8)),family.heat()));
                row.set(9,ratio(Double.parseDouble(row.get(9)),family.integrity()));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*family.cost()))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),family.output()));
                row.set(12,"Physical survival or risky retrofit equipment; real fitting budget");
                bases.add(row);
            }
            for(int i=1;i<sourceUpgrades.size();i++){
                List<String> source=sourceUpgrades.get(i);
                if(!parent.equals(source.get(2)))continue;
                List<String> row=new ArrayList<>(source);
                row.set(0,row.get(0).replace("-"+parent+"-","-"+code+"-"));
                row.set(2,code);row.set(5,family.name()+" "+row.get(3)+" "+row.get(4));
                row.set(6,row.get(6).replace("-"+parent+"-","-"+code+"-")
                        .replace("-"+parent,"-"+code));
                row.set(19,family.name()+": "+row.get(19));
                upgrades.add(row);
            }
        }
    }


    /** Generate 528 physical bases but resolve variant research lazily on
     * Android's constrained 16 MiB Java heap. */
    private static void addSurvivalHardwareVariants(List<List<String>> bases){
        List<List<String>> original=List.copyOf(bases);
        for(SurvivalHardwareCatalog.Variant v:SurvivalHardwareCatalog.VARIANTS){
            for(int i=1;i<original.size();i++){
                List<String> parent=original.get(i);
                if(!v.root().equals(parent.get(2)))continue;
                List<String> row=new ArrayList<>(parent);
                row.set(0,row.get(0).replace("-"+v.root(),"-"+v.code()));
                row.set(2,v.code());row.set(3,v.name());
                row.set(5,"Native "+v.name()+"; tier "+v.requiredTier());
                row.set(6,ratio(Double.parseDouble(row.get(6)),v.mass()));
                row.set(7,ratio(Double.parseDouble(row.get(7)),v.power()));
                row.set(8,ratio(Double.parseDouble(row.get(8)),v.heat()));
                row.set(9,ratio(Double.parseDouble(row.get(9)),v.integrity()));
                row.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(row.get(10))*v.price()))));
                row.set(11,ratio(Double.parseDouble(row.get(11)),v.performance()));
                row.set(12,"Physical survival alternative; separate IX-rank research");
                bases.add(row);
            }
        }
    }

    /** Four independent physical dumbfire rocket sizes, not renamed guided MSL.
     * The source weapon research is cloned with its real four 1-6 research
     * tracks; VII-IX remain virtual like every other hardware line.
     * Append-only codes keep existing purchased component IDs stable. */
    private static void addDumbfireRocketFamilies(List<List<String>> bases,
                                                   List<List<String>> upgrades) {
        String[][] rocket={
            // code, name, mass, power, heat, integrity, cost, output
            {"RKS","Small Dumbfire Rocket Pod","0.70","0.52","0.64","0.54","0.45","0.65"},
            {"RKM","Medium Dumbfire Rocket Rack","0.85","0.68","0.78","0.68","0.63","0.85"},
            {"RKL","Large Dumbfire Rocket Battery","1.15","0.90","1.02","0.82","0.81","1.06"},
            {"RKG","Siege Rocket Barrage Battery","1.52","1.17","1.30","0.96","1.05","1.28"}
        };
        List<List<String>> originals=List.copyOf(bases);
        List<List<String>> research=List.copyOf(upgrades);
        for(String[] spec:rocket){
            for(int i=1;i<originals.size();i++){
                List<String> root=originals.get(i);
                if(!"MSL".equals(root.get(2)))continue;
                List<String> part=new ArrayList<>(root);
                part.set(0,root.get(0).replace("-MSL","-"+spec[0]));
                part.set(2,spec[0]);part.set(3,spec[1]);
                part.set(5,"No-seeker rapid barrage; 4/8/16 fixed-bearing rockets");
                for(int col=6;col<=9;col++)
                    part.set(col,ratio(Double.parseDouble(root.get(col)),
                            Double.parseDouble(spec[col-4])));
                part.set(10,Long.toString(Math.max(1,Math.round(
                        Long.parseLong(root.get(10))*Double.parseDouble(spec[6])))));
                part.set(11,ratio(Double.parseDouble(root.get(11)),
                        Double.parseDouble(spec[7])));
                part.set(12,"Unguided cheap explosive rounds; long reload and laser-vulnerable bodies");
                bases.add(part);
            }
            for(int i=1;i<research.size();i++){
                List<String> root=research.get(i);
                if(!"MSL".equals(root.get(2)))continue;
                List<String> rank=new ArrayList<>(root);
                rank.set(0,root.get(0).replace("-MSL-","-"+spec[0]+"-"));
                rank.set(2,spec[0]);rank.set(5,spec[1]+" "+root.get(3)+" "+root.get(4));
                rank.set(6,root.get(6).replace("-MSL-","-"+spec[0]+"-")
                        .replace("-MSL","-"+spec[0]));
                rank.set(19,spec[1]+": "+root.get(19));
                upgrades.add(rank);
            }
        }
    }

    /** A distinct, mounted rotary kinetic CIWS for every supported faction.
     * Clone only economic/research lineage from the FLK fabrication plant:
     * the actual weapon is a travelling single-round kinetic stream, never
     * a timed fragmentation cloud or a hidden precision laser. Each of its
     * four independent research branches remains separately upgradeable.
     * The initial source CSV and old blueprint IDs are preserved untouched. */
    private static void addRotaryPointDefenseFamilies(List<List<String>> bases,
                                                        List<List<String>> upgrades){
        List<List<String>> originals=List.copyOf(bases);
        List<List<String>> research=List.copyOf(upgrades);
        for(int i=1;i<originals.size();i++){
            List<String> source=originals.get(i);
            if(!"FLK".equals(source.get(2)))continue;
            List<String> row=new ArrayList<>(source);
            row.set(0,source.get(0).replace("-FLK","-RCW"));
            row.set(2,"RCW");
            row.set(3,"Rotary Kinetic CIWS");
            row.set(5,"Physical spin-up, tracked ballistic bullet stream, short radar/IR gate");
            row.set(6,ratio(Double.parseDouble(source.get(6)),.88));
            row.set(7,ratio(Double.parseDouble(source.get(7)),.83));
            row.set(8,ratio(Double.parseDouble(source.get(8)),1.02));
            row.set(9,ratio(Double.parseDouble(source.get(9)),.94));
            row.set(10,Integer.toString(Math.max(1,(int)StrictMath.ceil(
                    Integer.parseInt(source.get(10))*.90))));
            row.set(11,ratio(Double.parseDouble(source.get(11)),.74));
            row.set(12,"Finite per-round kinetic stock; close-in lead, spool, recoil and tracers");
            bases.add(row);
        }
        for(int i=1;i<research.size();i++){
            List<String> source=research.get(i);
            if(!"FLK".equals(source.get(2)))continue;
            List<String> row=new ArrayList<>(source);
            row.set(0,source.get(0).replace("-FLK-","-RCW-"));
            row.set(2,"RCW");
            row.set(5,"Rotary kinetic CIWS "+row.get(3)+" stage "+row.get(4));
            row.set(6,source.get(6).replace("-FLK-","-RCW-").replace("-FLK","-RCW"));
            row.set(19,"Rotary CIWS: "+row.get(19)+"; paid cartridges and sustained spin");
            upgrades.add(row);
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
