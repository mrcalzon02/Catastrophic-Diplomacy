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
            // The last three point-defense families are generated from a
            // fixed FLK source range. Changing the expansion to avoid a
            // full List.copyOf snapshot must not change their complete
            // faction/branch/stage lineage or physical research modifiers.
            for(String pd:new String[]{"RCW","RPD","PPD"})
                for(String branch:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int stage=1;stage<=9;stage++){
                        String suffix="-"+branch+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",stage);
                        var candidate=c.get("TECH-"+faction+"-"+pd+suffix);
                        var source=c.get("TECH-"+faction+"-FLK"+suffix);
                        require(candidate!=null&&source!=null&&
                                candidate.faction.equals(faction)&&
                                candidate.subsystem.equals(pd)&&
                                candidate.track.equals(branch)&&
                                candidate.stage==stage,
                                "PD stage identity drift after source-bound snapshot removal: "+
                                        faction+"/"+pd+"/"+branch+"/"+stage);
                        String prior=stage==1?"BASE-"+faction+"-"+pd:
                                "TECH-"+faction+"-"+pd+"-"+branch+"-"+
                                        String.format(java.util.Locale.ROOT,"%02d",stage-1);
                        require(candidate.prerequisite.equals(prior)&&
                                candidate.researchCost==source.researchCost&&
                                candidate.industryCost==source.industryCost&&
                                Double.compare(candidate.mass,source.mass)==0&&
                                Double.compare(candidate.power,source.power)==0&&
                                Double.compare(candidate.heat,source.heat)==0&&
                                Double.compare(candidate.output,source.output)==0&&
                                Double.compare(candidate.accuracy,source.accuracy)==0&&
                                Double.compare(candidate.integrity,source.integrity)==0&&
                                Double.compare(candidate.shield,source.shield)==0&&
                                Double.compare(candidate.thrust,source.thrust)==0&&
                                Double.compare(candidate.cooling,source.cooling)==0&&
                                Double.compare(candidate.maintenance,source.maintenance)==0,
                                "PD stage economic/physics drift: "+candidate.id);
                    }
            for(String role: new String[]{"HPR","APR","PWR","DHL","DSH","DAR","DPW",
                    "AHL","ASH","AAR","APW"}){
                require(c.base("BASE-"+faction+"-"+role)!=null
                        && "support".equals(c.base("BASE-"+faction+"-"+role).category),
                        "Missing physical support module "+faction+"/"+role);
                // Capture the existing fully resident support-family behavior
                // before virtualizing its cloned research. This cross-check
                // covers all factions, all nine stages, exact research economics,
                // upgrade identity, name, tradeoff and physical multipliers.
                String parentCode=(role.equals("DHL")||role.equals("DSH")||
                        role.equals("DAR")||role.equals("DPW"))?"DRN":"XFR";
                String title=switch(role){
                    case "HPR" -> "Long-Range Hull Restoration Projector";
                    case "APR" -> "Armor Restoration Projector";
                    case "PWR" -> "Power Transfer Projector";
                    case "DHL" -> "Hull Service Drone Bay";
                    case "DSH" -> "Shield Service Drone Bay";
                    case "DAR" -> "Armor Service Drone Bay";
                    case "DPW" -> "Power Service Drone Bay";
                    case "AHL" -> "Hull Restoration Aura";
                    case "ASH" -> "Shield Restoration Aura";
                    case "AAR" -> "Armor Restoration Aura";
                    case "APW" -> "Power Restoration Aura";
                    default -> throw new IllegalStateException("Unknown support family");
                };
                String sourceName=parentCode.equals("DRN")?
                        "Drone Controller":"Shield Transfer Projector";
                for(String branch:Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST"))
                    for(int rank=1;rank<=9;rank++){
                        String suffix="-"+branch+"-"+
                                String.format(java.util.Locale.ROOT,"%02d",rank);
                        var actual=c.get("TECH-"+faction+"-"+role+suffix);
                        var parent=c.get("TECH-"+faction+"-"+parentCode+suffix);
                        require(actual!=null&&parent!=null&&
                                actual.stage==rank&&actual.faction.equals(faction)&&
                                actual.subsystem.equals(role)&&actual.track.equals(branch),
                                "Support evolution identity missing "+faction+"/"+role+"/"+suffix);
                        require(actual.prerequisite.equals(rank==1?"BASE-"+faction+"-"+role:
                                "TECH-"+faction+"-"+role+"-"+branch+"-"+
                                        String.format(java.util.Locale.ROOT,"%02d",rank-1)),
                                "Support family prerequisite drift "+actual.id);
                        require(actual.researchCost==parent.researchCost&&
                                actual.industryCost==parent.industryCost&&
                                Double.compare(actual.mass,parent.mass)==0&&
                                Double.compare(actual.power,parent.power)==0&&
                                Double.compare(actual.heat,parent.heat)==0&&
                                Double.compare(actual.output,parent.output)==0&&
                                Double.compare(actual.accuracy,parent.accuracy)==0&&
                                Double.compare(actual.integrity,parent.integrity)==0&&
                                Double.compare(actual.shield,parent.shield)==0&&
                                Double.compare(actual.thrust,parent.thrust)==0&&
                                Double.compare(actual.cooling,parent.cooling)==0&&
                                Double.compare(actual.maintenance,parent.maintenance)==0,
                                "Support family economic/physical drift "+actual.id);
                        require(actual.name.equals(parent.name.replace(sourceName,title))&&
                                actual.tradeoff.equals(title+": "+parent.tradeoff),
                                "Support family description drift "+actual.id);
                    }
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
                    for(int rank=1;rank<=9;rank++){
                        String rankId=String.format(java.util.Locale.ROOT,"%02d",rank);
                        String name="TECH-"+faction+"-"+family.code()+"-"+track+"-"+rankId;
                        var actual=c.get(name);
                        var parent=c.get("TECH-"+faction+"-"+family.parent()+"-"+track+"-"+rankId);
                        require(actual!=null&&parent!=null&&actual.stage==rank,
                                "Missing independent survival research "+name);
                        require(actual.faction.equals(faction)&&actual.subsystem.equals(family.code())&&
                                actual.track.equals(track)&&actual.prerequisite.equals(rank==1?baseId:
                                        "TECH-"+faction+"-"+family.code()+"-"+track+"-"+
                                        String.format(java.util.Locale.ROOT,"%02d",rank-1)),
                                "Virtual survival family identity or prerequisite drift "+name);
                        require(actual.researchCost==parent.researchCost&&
                                actual.industryCost==parent.industryCost&&
                                Double.compare(actual.mass,parent.mass)==0&&
                                Double.compare(actual.power,parent.power)==0&&
                                Double.compare(actual.heat,parent.heat)==0&&
                                Double.compare(actual.output,parent.output)==0&&
                                Double.compare(actual.accuracy,parent.accuracy)==0&&
                                Double.compare(actual.integrity,parent.integrity)==0&&
                                Double.compare(actual.shield,parent.shield)==0&&
                                Double.compare(actual.thrust,parent.thrust)==0&&
                                Double.compare(actual.cooling,parent.cooling)==0&&
                                Double.compare(actual.maintenance,parent.maintenance)==0,
                                "Virtual native survival family stats changed "+name);
                        require(actual.name.equals(family.name()+" "+track+" "+
                                (rank<=6?rank:"6 / Stage "+rank))&&
                                actual.tradeoff.equals(family.name()+": "+parent.tradeoff),
                                "Virtual survival research description changed "+name);
                    }
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
