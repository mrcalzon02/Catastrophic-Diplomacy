package diplomacy.tech;

import java.util.List;

/**
 * Six independent, socket-bound fleet information systems. Standard equipment
 * is a physical root component; primitive, compact, specialist, and advanced
 * alternatives retain independent research IDs and engineering costs.
 * Raw detection is NOT supplied by a processor or datalink.
 */
public final class SensorNetworkCatalog {
    private SensorNetworkCatalog(){}

    public record Family(String code,String name,String category,double mass,
                         double power,double heat,double integrity,
                         double output,double price){
        public Family {
            if(code==null||code.length()!=3||name==null||category==null
                    ||mass<=0||power<=0||heat<=0||integrity<=0||output<=0||price<=0)
                throw new IllegalArgumentException("Bad sensor-network family");
        }
    }
    public record Model(String code,String root,String name,int requiredTier,
                        double performance,double mass,double power,double heat,
                        double integrity,double price){
        public Model {
            if(code==null||code.length()!=3||root==null||name==null
                    ||requiredTier<0||requiredTier>9||performance<=0||mass<=0
                    ||power<=0||heat<=0||integrity<=0||price<=0)
                throw new IllegalArgumentException("Bad sensor-network model");
        }
    }

    public static final List<Family> FAMILIES=List.of(
        new Family("SHR","Sensor Sharing Transceiver","systems",1.04,1.06,1.05,0.97,1.00,1.12),
        new Family("RES","Sensor Contact Resolver","systems",1.12,1.18,1.10,0.94,1.05,1.20),
        new Family("TRK","Sensor Tracking Computer","systems",1.03,1.17,1.14,0.98,1.03,1.16),
        new Family("LNK","Tactical Track Datalink","systems",1.00,1.27,1.20,0.94,1.01,1.24),
        new Family("FCM","Fleet Command Fusion Center","support",1.37,1.54,1.33,1.08,1.14,1.64),
        new Family("CAP","Target Capacity Processor","systems",1.14,1.36,1.25,0.98,1.09,1.37)
    );

    public static final List<Model> VARIANTS=List.of(
        new Model("SHP","SHR","Primitive Narrowband Contact Relay",0,.42,.75,.58,.55,.82,.42),
        new Model("SHC","SHR","Compact Contact Radio",2,.77,.62,.77,.76,.78,.85),
        new Model("SHS","SHR","Dedicated Wideband Transceiver",4,1.38,1.22,1.40,1.26,1.02,1.48),
        new Model("SHA","SHR","Advanced Quantum-Key Transceiver",6,1.69,1.42,1.81,1.59,1.12,2.12),
        new Model("RSP","RES","Primitive Signal Plotter",0,.44,.78,.61,.64,.82,.45),
        new Model("RSC","RES","Compact Correlation Engine",2,.78,.59,.80,.79,.80,.89),
        new Model("RSS","RES","Multi-Spectrum Classification Suite",4,1.45,1.26,1.51,1.42,1.04,1.58),
        new Model("RSA","RES","Advanced Contact Resolution Array",6,1.77,1.51,1.94,1.73,1.13,2.23),
        new Model("TKP","TRK","Primitive Bearing Recorder",0,.42,.79,.55,.60,.80,.45),
        new Model("TKC","TRK","Compact Track Maintainer",2,.76,.61,.77,.81,.78,.84),
        new Model("TKS","TRK","Predictive Target Tracker",4,1.48,1.29,1.47,1.38,1.03,1.56),
        new Model("TKA","TRK","Advanced Continuous Track Computer",6,1.78,1.54,1.89,1.77,1.16,2.19),
        new Model("LKP","LNK","Primitive Track Courier",0,.42,.83,.64,.64,.81,.45),
        new Model("LKC","LNK","Compact Tactical Datalink",2,.75,.65,.82,.83,.79,.89),
        new Model("LKS","LNK","Low-Latency Fleet Track Relay",4,1.47,1.28,1.54,1.40,1.05,1.62),
        new Model("LKA","LNK","Advanced Distributed Track Grid",6,1.82,1.50,1.99,1.82,1.17,2.30),
        new Model("FMP","FCM","Primitive Fleet Plotting Table",0,.42,.77,.57,.61,.84,.43),
        new Model("FMC","FCM","Compact Squadron Command Node",2,.74,.59,.78,.83,.80,.87),
        new Model("FMS","FCM","Specialist Fleet Correlation Core",4,1.47,1.27,1.57,1.48,1.06,1.65),
        new Model("FMA","FCM","Advanced Battle Management Nexus",6,1.82,1.62,2.08,1.92,1.20,2.45),
        new Model("CTP","CAP","Primitive Target Index Board",0,.42,.76,.56,.59,.78,.43),
        new Model("CTC","CAP","Compact Target Scheduler",2,.76,.60,.77,.84,.80,.86),
        new Model("CTS","CAP","Specialist Parallel Fire-Control Core",4,1.47,1.29,1.49,1.43,1.05,1.58),
        new Model("CTA","CAP","Advanced Multi-Target Processor",6,1.79,1.52,1.93,1.77,1.18,2.22)
    );
    public static Family family(String root){
        for(Family f:FAMILIES)if(f.code().equals(root))return f;
        return null;
    }
    public static Model variant(String code){
        if(code==null)return null;
        for(Model v:VARIANTS)if(v.code().equals(code))return v;
        return null;
    }
    public static String root(String code){
        Model variant=variant(code);
        return variant==null?code:variant.root();
    }
    public static int requiredTier(String code){
        Model model=variant(code);
        if(model!=null)return model.requiredTier();
        return family(code)!=null?1:0;
    }
    public static double performance(String code){
        Model model=variant(code);
        return model==null?1:model.performance();
    }
}
