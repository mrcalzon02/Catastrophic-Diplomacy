package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class TechnologyCatalogTest {
    private static void require(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        TechnologyCatalog c = TechnologyCatalog.loadDefault();
        require(c.upgradeCount() == 5808, "full technology count");
        require(c.componentCount() == 242, "all base components");
        for (String faction : Set.of("TER","DRA","CAR","ZIP","UZI","MAN","VAE","ZEL","AUR","NYX","UMB")) {
            for (String track : Set.of("OUTPUT","EFFICIENCY","RESILIENCE","SPECIALIST")) {
                for (int stage = 1; stage <= 6; stage++) {
                    String id = "TECH-" + faction + "-KIN-" + track + "-" + String.format(java.util.Locale.ROOT, "%02d", stage);
                    require(c.get(id) != null, "missing branch entry " + id);
                }
            }
        }
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
