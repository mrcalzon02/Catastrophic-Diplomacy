package diplomacy.shipyard;

import java.util.Set;

/**
 * Per-component behavioral deltas from a single exclusive technology branch.
 * Six ranks at incremental 2-6% steps, not four branches applied simultaneously.
 * Generic research economics/accuracy/integrity remain in TechnologyCatalog.
 */
public final class ModuleBranchEffects {
    private ModuleBranchEffects(){}
    private static final Set<String> GUNS=Set.of("LAS","KIN","MSL","TOR","PAR","PLA");
    private static final Set<String> DEFENSE=Set.of("PD","FLK");
    public record Tuning(double damage,double cycle,double reach,double tracking,
                         double selfRepair,double remoteRepair,double shieldTransfer,
                         double supportEfficiency,double droneEndurance) {
        public Tuning {
            for(double n:new double[]{damage,cycle,reach,tracking,selfRepair,remoteRepair,
                    shieldTransfer,supportEfficiency,droneEndurance})
                if(!Double.isFinite(n)||n<0.25||n>2.6)
                    throw new IllegalArgumentException("Invalid technology tuning");
        }
    }
    public static final Tuning NEUTRAL=new Tuning(1,1,1,1,1,1,1,1,1);
    /** Branch and rank are the immutable current choice of THIS installed module. */
    public static Tuning tune(String subsystem,Shipyard.Track branch,int rank) {
        if(subsystem==null||rank<0||rank>6||(branch==null&&rank!=0)
                ||(branch!=null&&rank==0))
            throw new IllegalArgumentException("Invalid specialized module");
        if(rank==0)return NEUTRAL;
        // Every newly installed logistics family has its own research ID but
        // uses the corresponding calibrated doctrine rather than neutral tuning.
        if(Set.of("HPR","APR","PWR","AHL","ASH","AAR","APW").contains(subsystem))
            return tune("XFR",branch,rank);
        if(Set.of("DHL","DSH","DAR","DPW").contains(subsystem))
            return tune("DRN",branch,rank);
        double damage=1,cycle=1,reach=1,tracking=1,self=1,remote=1,shield=1,efficiency=1,drone=1;
        if(GUNS.contains(subsystem)){
            // Weapon-specific upgrades improve practical fire behavior, on top
            // of existing generic catalog mass/power/heat/effect multipliers.
            double weight=switch(subsystem){
                case "TOR" -> 0.80;case "KIN" -> 1.15;
                case "MSL" -> 0.95;case "PAR" -> 0.90;
                case "PLA" -> 1.05;default -> 1.0;
            };
            switch(branch){
                case OUTPUT -> damage+=0.038*rank*weight;
                case EFFICIENCY -> {cycle-=0.026*rank;efficiency+=0.045*rank;}
                case RESILIENCE -> {cycle-=0.010*rank;tracking+=0.008*rank;}
                case SPECIALIST -> {reach+=0.045*rank;tracking+=0.030*rank;}
            }
        }else if(DEFENSE.contains(subsystem)){
            // PD and flak share acquisition laws but flak rewards broad scans.
            double flak=subsystem.equals("FLK")?1.18:1.0;
            switch(branch){
                case OUTPUT -> damage+=0.050*rank;
                case EFFICIENCY -> {cycle-=0.030*rank;efficiency+=0.046*rank;}
                case RESILIENCE -> {tracking+=0.017*rank;cycle-=0.012*rank;}
                case SPECIALIST -> {reach+=0.050*rank*flak;tracking+=0.052*rank;}
            }
        }else switch(subsystem){
            case "REP" -> {
                switch(branch){
                    case OUTPUT -> {self+=0.060*rank;remote+=0.032*rank;}
                    case EFFICIENCY -> {efficiency+=0.065*rank;cycle-=0.013*rank;}
                    case RESILIENCE -> {self+=0.033*rank;remote+=0.055*rank;}
                    case SPECIALIST -> {reach+=0.063*rank;remote+=0.045*rank;}
                }
            }
            case "XFR" -> {
                switch(branch){
                    case OUTPUT -> shield+=0.050*rank;
                    case EFFICIENCY -> efficiency+=0.047*rank;
                    case RESILIENCE -> shield+=0.035*rank;
                    case SPECIALIST -> {reach+=0.055*rank;shield+=0.042*rank;}
                }
            }
            case "SHD" -> {
                // All four branches meaningfully evolve the owner's generator,
                // but only explicit XFR hardware can charge allied shields.
                switch(branch){
                    case OUTPUT -> shield+=0.050*rank;
                    case EFFICIENCY -> efficiency+=0.047*rank;
                    case RESILIENCE -> shield+=0.035*rank;
                    case SPECIALIST -> {shield+=0.042*rank;tracking+=0.010*rank;}
                }
            }
            case "DRN" -> {
                switch(branch){
                    case OUTPUT -> {drone+=0.045*rank;damage+=0.018*rank;}
                    case EFFICIENCY -> {drone+=0.045*rank;efficiency+=0.042*rank;}
                    case RESILIENCE -> drone+=0.067*rank;
                    case SPECIALIST -> {drone+=0.050*rank;remote+=0.050*rank;}
                }
            }
            case "SEN" -> {
                switch(branch){
                    case OUTPUT -> reach+=0.027*rank;
                    case EFFICIENCY -> efficiency+=0.040*rank;
                    case RESILIENCE -> tracking+=0.020*rank;
                    case SPECIALIST -> {reach+=0.055*rank;tracking+=0.032*rank;}
                }
            }
            default -> {
                // Utility / reactor / armor modules retain their catalog's
                // individually researched physics and economic multipliers.
                return NEUTRAL;
            }
        }
        return new Tuning(damage,cycle,reach,tracking,self,remote,shield,efficiency,drone);
    }
}
