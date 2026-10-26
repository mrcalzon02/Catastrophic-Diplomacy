package diplomacy.shipyard;

/**
 * Physical generations and fit-for-purpose equipment marks. Orthogonal to the
 * ONE mutually exclusive research branch of a fitted socket.
 *
 * Each family has 5 manufacturing patterns x 4 marks. Changes reach mass,
 * power, heat, performance, integrity and crew-maintenance cost directly in
 * the Shipyard Part math, not only its label or icon.
 */
public final class ModuleVersions {
    private ModuleVersions(){}
    public enum Pattern {
        STANDARD, COMPACT, REINFORCED, OVERCHARGED, LOW_EMISSION
    }
    public record Modifiers(double mass,double power,double heat,
                            double output,double precision,double integrity,
                            double maintenance,double cost) {
        public Modifiers {
            for(double n:new double[]{mass,power,heat,output,precision,
                    integrity,maintenance,cost}){
                if(!Double.isFinite(n)||n<0.35||n>3.5)
                    throw new IllegalArgumentException("Unsafe equipment version");
            }
        }
    }
    public static Modifiers of(Pattern pattern,int mark){
        if(pattern==null||mark<1||mark>4)
            throw new IllegalArgumentException("Module mark must be 1..4");
        // Standard Mk I is identical to every existing CD-SHIP-1 fitting.
        int step=mark-1;
        double mass=1,power=1,heat=1,output=1,precision=1,integrity=1,
                maintenance=1,cost=1;
        switch(pattern){
            case STANDARD -> {
                // Incrementally more reliable fabrication has a modest cost.
                integrity+=0.025*step;precision+=0.01*step;
                cost+=0.12*step;
            }
            case COMPACT -> {
                mass-=0.045*mark;
                integrity-=0.020*mark;
                power+=0.012*mark;
                heat+=0.014*mark;
                cost+=0.08*mark;
            }
            case REINFORCED -> {
                mass+=0.055*mark;
                integrity+=0.11*mark;
                heat+=0.012*mark;
                cost+=0.09*mark;
            }
            case OVERCHARGED -> {
                output+=0.065*mark;
                power+=0.055*mark;
                heat+=0.075*mark;
                maintenance+=0.027*mark;
                cost+=0.13*mark;
            }
            case LOW_EMISSION -> {
                heat-=0.045*mark;
                power+=0.014*mark;
                precision+=0.020*mark;
                integrity-=0.012*mark;
                cost+=0.10*mark;
            }
        }
        return new Modifiers(mass,power,heat,output,precision,integrity,maintenance,cost);
    }
}
