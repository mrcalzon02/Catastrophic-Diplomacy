package diplomacy.tech;

import java.util.List;

/**
 * Append-only hardware variants for nine independent sensor/deception channels.
 * Real equipment and research identities; orthogonal to manufacturing marks.
 */
public final class SensingVariantCatalog {
    private SensingVariantCatalog(){}
    public record Model(String code,String base,String name,int requiredTier,
                        double performance,double mass,double power,double heat,
                        double integrity,double price) {
        public Model {
            if(code==null||base==null||name==null||requiredTier<0||requiredTier>9
                    ||performance<=0||mass<=0||power<=0||heat<=0
                    ||integrity<=0||price<=0)
                throw new IllegalArgumentException("Bad sensing hardware variant");
        }
    }
    public static final List<Model> VARIANTS=List.of(
        new Model("RDP","RDR","Analog Echo Locator",0,0.51,1.08,0.73,1.22,1.08,0.48),
        new Model("RDC","RDR","Folded Compact Radar",2,0.78,0.6,0.79,0.83,0.83,0.86),
        new Model("RDS","RDR","Narrow-Beam Fire-Control Radar",4,1.43,1.14,1.35,1.24,0.92,1.36),
        new Model("RDA","RDR","Phased Multiband Radar",6,1.68,1.31,1.67,1.53,1.17,1.84),
        new Model("STP","STC","Chaff-Bonded Hull Paint",0,0.46,0.76,0.4,0.55,0.78,0.45),
        new Model("STM","STC","Lightweight Scatter Film",2,0.73,0.54,0.58,0.7,0.7,0.84),
        new Model("STS","STC","Broadband Absorber Panels",4,1.45,1.23,1.14,1.3,1.06,1.45),
        new Model("STA","STC","Adaptive Metamaterial Coating",6,1.68,1.28,1.4,1.31,1.17,1.91),
        new Model("IRP","IRT","Bolometric Heat Spotter",0,0.48,1.1,0.7,1.21,0.87,0.51),
        new Model("IRC","IRT","Pocket Thermal Telescope",2,0.73,0.59,0.73,0.81,0.85,0.88),
        new Model("IRS","IRT","Long-Wave Interferometer",4,1.43,1.2,1.29,1.22,1.04,1.43),
        new Model("IRA","IRT","Cryogenic Multi-Spectrum Imager",6,1.67,1.31,1.57,1.51,1.21,1.95),
        new Model("HTP","HTD","Exhaust Baffle Insulation",0,0.49,0.83,0.53,0.73,0.95,0.49),
        new Model("HTC","HTD","Compact Microchannel Sink",2,0.72,0.61,0.76,0.86,0.78,0.87),
        new Model("HTS","HTD","Thermal Ghost Diverter",4,1.4,1.22,1.37,1.34,1.08,1.5),
        new Model("HTA","HTD","Adaptive Cold-Skin Matrix",6,1.67,1.34,1.64,1.54,1.19,2),
        new Model("VLP","VCL","Diffraction Camouflage Mesh",0,0.42,0.75,0.54,0.7,0.86,0.53),
        new Model("VLC","VCL","Micro-Shimmer Skin",2,0.77,0.61,0.82,0.89,0.79,0.94),
        new Model("VLS","VCL","Directional Light-Bending Veil",4,1.47,1.25,1.4,1.36,0.97,1.58),
        new Model("VLA","VCL","Full-Spectrum Phase Cloak",6,1.7,1.4,1.78,1.72,1.05,2.14),
        new Model("ACP","ACU","Manual Target Plotting Computer",0,0.45,0.79,0.51,0.75,1.18,0.46),
        new Model("ACC","ACU","Compact Signal Parser",2,0.74,0.55,0.75,0.79,0.81,0.82),
        new Model("ACS","ACU","Predictive Targeting Processor",4,1.51,1.18,1.32,1.26,0.9,1.52),
        new Model("ACA","ACU","Quantum Correlation Analyzer",6,1.77,1.42,1.65,1.59,1.02,2.04),
        new Model("XRP","XRY","Ionizing Flux Counter",0,0.45,1.08,0.73,1.18,0.94,0.52),
        new Model("XRC","XRY","Compact Scintillator",2,0.76,0.65,0.77,0.8,0.8,0.85),
        new Model("XRS","XRY","Penetrating Spectrum Analyzer",4,1.46,1.26,1.42,1.36,1.04,1.54),
        new Model("XRA","XRY","Deep-Field X-Ray Array",6,1.73,1.47,1.81,1.75,1.18,2.09),
        new Model("GRP","GRA","Pendulum Mass Drift Detector",0,0.43,1.09,0.7,1.11,1.05,0.49),
        new Model("GRC","GRA","Compressed Tidal Meter",2,0.75,0.65,0.81,0.84,0.9,0.88),
        new Model("GRS","GRA","Resonant Gravitic Interferometer",4,1.46,1.26,1.48,1.39,1.1,1.6),
        new Model("GRV","GRA","Tensor Gradient Observatory",6,1.72,1.5,1.89,1.78,1.23,2.12),
        new Model("HLT","HLP","Basic Holographic Emitter",0,0.44,0.83,0.64,0.8,0.88,0.52),
        new Model("HLC","HLP","Distributed Compact Holo-Emitter",2,0.75,0.64,0.85,0.89,0.79,0.9),
        new Model("HLS","HLP","Mimetic False-Contact Array",4,1.47,1.25,1.43,1.38,1.07,1.59),
        new Model("HLA","HLP","Adaptive Phantom Fleet Projector",6,1.71,1.43,1.79,1.7,1.15,2.15)
    );
    public static Model find(String subsystem){
        if(subsystem==null)return null;
        for(Model model:VARIANTS)if(model.code().equals(subsystem))return model;
        return null;
    }
    public static String root(String subsystem){
        Model model=find(subsystem);
        return model==null?subsystem:model.base();
    }
    public static double performance(String subsystem){
        Model model=find(subsystem);
        return model==null?1:model.performance();
    }
    public static int requiredTier(String subsystem){
        Model model=find(subsystem);
        return model==null?0:model.requiredTier();
    }
}
