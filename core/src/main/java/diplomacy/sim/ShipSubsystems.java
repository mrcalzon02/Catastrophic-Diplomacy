package diplomacy.sim;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/** Exact physical operating condition of the ten fitted shipboard systems. */
public final class ShipSubsystems {
    private ShipSubsystems(){}
    public static String capture(Battle.Ship s){
        ByteBuffer data=ByteBuffer.allocate(10*8).order(ByteOrder.BIG_ENDIAN);
        for(double v:values(s))data.putDouble(v);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.array());
    }
    public static String full(){
        ByteBuffer data=ByteBuffer.allocate(10*8).order(ByteOrder.BIG_ENDIAN);
        for(int i=0;i<10;i++)data.putDouble(1);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.array());
    }
    private static double[] values(Battle.Ship s){
        return new double[]{s.engineCondition,s.reactorCondition,s.weaponsCondition,
                s.pointDefenseCondition,s.sensorsCondition,s.coolingCondition,
                s.commandCondition,s.hangarCondition,s.magazineCondition,s.ecmCondition};
    }
    private static double[] unpack(String packed){
        byte[] raw;
        try{raw=Base64.getUrlDecoder().decode(packed);}
        catch(RuntimeException broken){throw new IllegalArgumentException("Invalid machinery encoding",broken);}
        if(raw.length!=80)throw new IllegalArgumentException("Incorrect machinery length");
        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        double[] values=new double[10];
        for(int i=0;i<10;i++){
            values[i]=b.getDouble();
            if(!Double.isFinite(values[i])||values[i]<0||values[i]>1)
                throw new IllegalArgumentException("Stored ship machinery exceeds physical limits");
        }
        return values;
    }
    public static void restore(Battle.Ship s,String packed){
        double[] v=unpack(packed);
        s.engineCondition=v[0];s.reactorCondition=v[1];s.weaponsCondition=v[2];
        s.pointDefenseCondition=v[3];s.sensorsCondition=v[4];
        s.coolingCondition=v[5];s.commandCondition=v[6];s.hangarCondition=v[7];
        s.magazineCondition=v[8];s.ecmCondition=v[9];
    }
    /** Paid replacement of damaged machinery, more expensive than consumables. */
    public static long repairCost(String packed){
        double[] v=unpack(packed);
        long total=0;
        for(double condition:v)total+=Math.round((1-condition)*1700);
        return total;
    }
}
