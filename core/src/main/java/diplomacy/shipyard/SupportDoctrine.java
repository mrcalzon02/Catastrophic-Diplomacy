package diplomacy.shipyard;

import java.util.List;

/**
 * Purpose-fitted logistics equipment. No ship gets a remote projector, aura or
 * reusable service craft from its hull class or ordinary shield generator.
 * The same four service channels are independent in all three doctrines.
 */
public record SupportDoctrine(List<Emitter> direct,List<Emitter> drones,List<Emitter> auras) {
    public enum Channel { SHIELD, HULL, ARMOR, POWER }
    public record Emitter(Channel channel,double rate,double range,
                          double energyPerUnit,int magazines,double charge) {
        public Emitter {
            if(channel==null||!Double.isFinite(rate)||rate<=0||
                    !Double.isFinite(range)||range<=0||
                    !Double.isFinite(energyPerUnit)||energyPerUnit<=0||
                    magazines<0||!Double.isFinite(charge)||charge<0)
                throw new IllegalArgumentException("Invalid service projector");
        }
    }
    public SupportDoctrine {
        direct=List.copyOf(direct);
        drones=List.copyOf(drones);
        auras=List.copyOf(auras);
    }
    public static final SupportDoctrine NONE=new SupportDoctrine(List.of(),List.of(),List.of());
    public int droneCapacity(Channel channel) {
        int result=0;
        for(Emitter e:drones)if(e.channel()==channel)result+=e.magazines();
        return result;
    }
}
