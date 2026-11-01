package diplomacy.shipyard;

import java.util.List;

/**
 * Physical ammunition/propellant stores and hose/replenishment equipment.
 * Each installed support module carries finite inventory. No passive aura,
 * ammunition synthesis or remote transfer without an actual module.
 */
public record TenderDoctrine(List<Emitter> emitters) {
    public enum Cargo { KINETIC, MISSILE, TORPEDO, FUEL }
    public record Emitter(Cargo cargo,double capacity,double rate,
                          double range,double energyPerUnit) {
        public Emitter{
            if(cargo==null||!Double.isFinite(capacity)||capacity<=0
                    ||!Double.isFinite(rate)||rate<=0
                    ||!Double.isFinite(range)||range<=0
                    ||!Double.isFinite(energyPerUnit)||energyPerUnit<=0)
                throw new IllegalArgumentException("Invalid physical tender");
        }
    }
    public TenderDoctrine {
        emitters=List.copyOf(emitters);
    }
    public static final TenderDoctrine NONE=new TenderDoctrine(List.of());
    public double capacity(Cargo cargo) {
        double amount=0;
        for(Emitter e:emitters)if(e.cargo()==cargo)amount+=e.capacity();
        return amount;
    }
}
