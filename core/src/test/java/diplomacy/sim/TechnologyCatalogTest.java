package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.SensingVariantCatalog;
import diplomacy.tech.SensorNetworkCatalog;
import diplomacy.tech.SurvivalHardwareCatalog;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class TechnologyCatalogTest {
    private static void require(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        TechnologyCatalog c = TechnologyCatalog.loadDefault();
        require(c.upgradeCount() == 73260, "full technology count including fleet sensor networks");
        require(c.componentCount() == 2035, "all base components including fleet sensor networks");
        for (String faction : Set.of("TER","DRA","CAR","ZIP","UZI","MAN","VAE","ZEL","AUR","NYX","UMB")) {
            // Four physical dumbfire racks, rotary CIWS, rail and particle
            // point defense each add an independently researchable 9-stage lineage.
            for(String code:new String[]{"RKS","RKM","RKL","RKG","RCW","RPD","PPD"}){
                require(c.base("BASE-"+faction+"-"+code)!=null,
                        "Missing physically distinct mounted ordnance "+faction+"/"+code);
                for(String branch:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++)
                        require(c.get("TECH-"+faction+"-"+code+"-"+branch+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Missing nine-stage ordnance research "+faction+"/"+code);
            }
            for(String role: new String[]{"HPR","APR","PWR","DHL","DSH","DAR","DPW",
                    "AHL","ASH","AAR","APW"}){
                require(c.base("BASE-"+faction+"-"+role)!=null
                        && "support".equals(c.base("BASE-"+faction+"-"+role).category),
                        "Missing physical support module "+faction+"/"+role);
                for(String branch:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++)
                        require(c.get("TECH-"+faction+"-"+role+"-"+branch+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Support evolution research missing "+faction+"/"+role);
            }
            for(String module:new String[]{"CMP","ABL","DEN","RAC","BLK"}){
                require(c.base("BASE-"+faction+"-"+module)!=null &&
                        "defense".equals(c.base("BASE-"+faction+"-"+module).category),
                        "Missing defensive module "+faction+"/"+module);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++){
                        String id="TECH-"+faction+"-"+module+"-"+track+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank);
                        require(c.get(id)!=null,"Missing defensive research "+id);
                    }
            }
            for(String module:new String[]{"RDR","STC","IRT","HTD","VCL",
                    "ACU","XRY","GRA","HLP"}){
                String category=module.equals("STC")?"defense":
                        module.equals("HLP")?"support":"systems";
                require(c.base("BASE-"+faction+"-"+module)!=null &&
                        category.equals(c.base("BASE-"+faction+"-"+module).category),
                        "Missing sensing/deception module "+faction+"/"+module);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++)
                        require(c.get("TECH-"+faction+"-"+module+"-"+track+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Missing sensing research "+faction+"/"+module+"/"+track);
            }
            for(var model:SensingVariantCatalog.VARIANTS){
                String id="BASE-"+faction+"-"+model.code();
                require(c.base(id)!=null,"Missing independent hardware "+id);
                require(c.base(id).category.equals(c.base("BASE-"+faction+"-"+model.base()).category),
                        "Variant changed required physical socket: "+id);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++){
                        String stage=String.format(java.util.Locale.ROOT,"%02d",rank);
                        var variant=c.get("TECH-"+faction+"-"+model.code()+"-"+track+"-"+stage);
                        var parent=c.get("TECH-"+faction+"-"+model.base()+"-"+track+"-"+stage);
                        require(variant!=null&&parent!=null,
                                "Missing virtual sensing tier "+id+" "+track+" "+rank);
                        require(variant.stage==rank&&variant.faction.equals(faction)&&
                                variant.subsystem.equals(model.code())&&variant.track.equals(track),
                                "Variant identity changed "+variant.id);
                        require(variant.prerequisite.equals(rank==1?id:
                                "TECH-"+faction+"-"+model.code()+"-"+track+"-"+
                                        String.format(java.util.Locale.ROOT,"%02d",rank-1)),
                                "Virtual sensing prerequisite changed "+variant.id);
                        require(variant.researchCost==parent.researchCost &&
                                variant.industryCost==parent.industryCost &&
                                Double.compare(variant.mass,parent.mass)==0 &&
                                Double.compare(variant.power,parent.power)==0 &&
                                Double.compare(variant.heat,parent.heat)==0 &&
                                Double.compare(variant.output,parent.output)==0 &&
                                Double.compare(variant.accuracy,parent.accuracy)==0 &&
                                Double.compare(variant.integrity,parent.integrity)==0 &&
                                Double.compare(variant.shield,parent.shield)==0 &&
                                Double.compare(variant.thrust,parent.thrust)==0 &&
                                Double.compare(variant.cooling,parent.cooling)==0 &&
                                Double.compare(variant.maintenance,parent.maintenance)==0,
                                "Virtual sensing stats drift from identical source "+variant.id);
                        require(variant.name.equals(model.name()+" "+track+" "+
                                (rank<=6?rank:"6 / Stage "+rank)) &&
                                variant.tradeoff.equals(model.name()+": "+parent.tradeoff),
                                "Virtual sensing descriptive fields changed "+variant.id);
                    }
            }

            for(var variant:SurvivalHardwareCatalog.VARIANTS){
                String id="BASE-"+faction+"-"+variant.code();
                var parent=c.base("BASE-"+faction+"-"+variant.root());
                require(parent!=null&&c.base(id)!=null &&
                        parent.category.equals(c.base(id).category),
                        "Survival variant lacks a valid native parent socket "+id);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int stage=1;stage<=9;stage++){
                        String tech="TECH-"+faction+"-"+variant.code()+"-"+track+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",stage);
                        var value=c.get(tech);
                        require(value!=null&&value.stage==stage&&
                                value.prerequisite.equals(stage==1?id:
                                        "TECH-"+faction+"-"+variant.code()+"-"+track+"-"+
                                        String.format(java.util.Locale.ROOT,"%02d",stage-1)),
                                "Broken independent survival research "+tech);
                    }
            }
            for(var family:SurvivalHardwareCatalog.FAMILIES){
                String baseId="BASE-"+faction+"-"+family.code();
                require(c.base(baseId)!=null&&c.base(baseId).category.equals(family.category()),
                        "Missing survival module "+baseId);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++)
                        require(c.get("TECH-"+faction+"-"+family.code()+"-"+track+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Missing independent survival research "+baseId);
            }
            for(String module:new String[]{"KTS","MTS","TTS","FTS"}){
                require(c.base("BASE-"+faction+"-"+module)!=null &&
                        "support".equals(c.base("BASE-"+faction+"-"+module).category),
                        "Missing stocked fleet tender "+faction+"/"+module);
                for(String track:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++)
                        require(c.get("TECH-"+faction+"-"+module+"-"+track+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                                "Missing fleet tender research "+faction+"/"+module+"/"+track);
            }
            require(c.base("BASE-"+faction+"-XFR")!=null &&
                    "support".equals(c.base("BASE-"+faction+"-XFR").category),
                    "Each civilization requires a dedicated shield-transfer support module");
            require(c.base("BASE-"+faction+"-SHD")!=null &&
                    "defense".equals(c.base("BASE-"+faction+"-SHD").category),
                    "Ordinary SHD generators retain their separate defensive mount");
            for (String track : Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST")) {
                for (int stage = 1; stage <= 9; stage++) {
                    String id = "TECH-" + faction + "-KIN-" + track + "-" + String.format(java.util.Locale.ROOT, "%02d", stage);
                    require(c.get(id) != null, "missing branch entry " + id);
                    String relay="TECH-"+faction+"-XFR-"+track+"-"+
                            String.format(java.util.Locale.ROOT,"%02d",stage);
                    require(c.get(relay)!=null,"Missing native relay upgrade "+relay);
                }
            }
        }
        // Enumerating the virtual catalog is used by the live field wiki.
        // Its size, prerequisites and exact IDs must remain complete even
        // though ranks VII–IX are now generated only when actually requested.
        int enumerated=0,highestStages=0;
        for(var entry:c.upgrades().entrySet()){
            TechnologyCatalog.Upgrade upgrade=entry.getValue();
            require(entry.getKey().equals(upgrade.id),
                    "Virtual technology index returned the wrong ID");
            if(upgrade.stage>=7){
                highestStages++;
                require(upgrade.prerequisite.endsWith(
                        String.format(java.util.Locale.ROOT,"%02d",upgrade.stage-1)),
                        "Virtual research lost its true direct prerequisite "+upgrade.id);
                require(upgrade.researchCost>0&&upgrade.industryCost>0 &&
                        c.get(upgrade.prerequisite)!=null,
                        "Virtual upgrade has invalid cost or missing actual prerequisite");
            }
            enumerated++;
        }
        require(enumerated==c.upgradeCount() && highestStages==24420,
                "Lazy technology enumeration missed or duplicated a stage: actual stages="+highestStages+" enumerated="+enumerated);
        TechnologyCatalog.Upgrade sixth=c.get("TECH-TER-KIN-OUTPUT-06");
        TechnologyCatalog.Upgrade ninth=c.get("TECH-TER-KIN-OUTPUT-09");
        require(ninth!=null && ninth.stage==9 &&
                ninth.researchCost>sixth.researchCost &&
                ninth.prerequisite.equals("TECH-TER-KIN-OUTPUT-08"),
                "Evolved technology is not actually stronger/costlier or directly chained");
        boolean rejected = false;
        String badUpgrades = "Tech ID,Faction ID,Subsystem,Parallel Track,Stage,Rank Name,Prerequisite ID,Research,Industry,Mass ×,Power Draw ×,Heat ×,Effect ×,Accuracy ×,Integrity ×,Shield Capacity ×,Thrust ×,Cooling ×,Maintenance ×,Tradeoff\n"
                + "TECH-TER-KIN-OUTPUT-01,TER,KIN,OUTPUT,1,Bad,BASE-NONEXISTENT,10,10,1,1,1,1,1,1,1,1,1,1,invalid\n";
        String sampleComponents = "Base ID,Faction ID,Subsystem ID,Subsystem,Category,Faction doctrine,Base Mass (t),Base Power (MW),Base Heat (MW),Base Integrity,Base Cost,Base Effect,Engineering drawback\n"
                + "BASE-TER-KIN,TER,KIN,Kinetic Battery,weapons,test,1,1,1,1,1,1,test\n";
        try {
            TechnologyCatalog.load(new ByteArrayInputStream(badUpgrades.getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayInputStream(sampleComponents.getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "broken prerequisites must be rejected");
        System.out.println("PASS technology catalog | components=" + c.componentCount()
                + " upgrades=" + c.upgradeCount() + " verified branches=" + (11*4));
    }
}
