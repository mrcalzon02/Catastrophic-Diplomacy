package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Versioned, checked standalone fleet-order presets. Ship fitting save
 * formats remain untouched, and missing/legacy order files use DEFAULT.
 */
public final class BattleOrdersCodec {
    private BattleOrdersCodec() { }
    private static final String MAGIC="CD-ORDERS-1";
    private static String encodeOne(BattleOrders.Orders x) {
        if(x==null||x.anchorShipId()!=0 ||
                x.position()==BattleOrders.Position.ESCORT||
                x.position()==BattleOrders.Position.FORMATION)
            throw new IllegalArgumentException("Fleet templates cannot reference individual ship IDs");
        return String.join(",",
                x.pointDefense().name(),x.targeting().name(),x.position().name(),
                Integer.toString(x.fighters().priority()),Double.toString(x.fighters().range()),
                Integer.toString(x.escorts().priority()),Double.toString(x.escorts().range()),
                Integer.toString(x.capitals().priority()),Double.toString(x.capitals().range()),
                Double.toString(x.escortRadius()),Double.toString(x.retreatAtHullFraction()));
    }
    public record Pair(BattleOrders.Orders left,BattleOrders.Orders right){}
    public static String encode(BattleOrders.Orders left,BattleOrders.Orders right){
        String body=MAGIC+"\n"+encodeOne(left)+"\n"+encodeOne(right)+"\n";
        return body+digest(body)+"\n";
    }
    public static Pair decode(String text){
        if(text==null||text.length()>4096)throw new IllegalArgumentException("Bad orders file");
        String[] lines=text.split("\n",-1);
        if(lines.length!=5||!MAGIC.equals(lines[0])||!lines[4].isEmpty())
            throw new IllegalArgumentException("Bad orders file schema");
        String body=lines[0]+"\n"+lines[1]+"\n"+lines[2]+"\n";
        if(!MessageDigest.isEqual(digest(body).getBytes(StandardCharsets.US_ASCII),
                lines[3].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Orders checksum mismatch");
        BattleOrders.Orders left=decodeOne(lines[1]),right=decodeOne(lines[2]);
        if(left.position()==BattleOrders.Position.ESCORT
                ||left.position()==BattleOrders.Position.FORMATION
                ||right.position()==BattleOrders.Position.ESCORT
                ||right.position()==BattleOrders.Position.FORMATION)
            throw new IllegalArgumentException("Fleet orders require anchor-free movement doctrine");
        return new Pair(left,right);
    }
    private static BattleOrders.Orders decodeOne(String s){
        String[] p=s.split(",",-1);
        if(p.length!=11)throw new IllegalArgumentException("Invalid tactical policy");
        try {
            return new BattleOrders.Orders(
                    BattleOrders.PointDefense.valueOf(p[0]),
                    BattleOrders.Targeting.valueOf(p[1]),
                    BattleOrders.Position.valueOf(p[2]),
                    new BattleOrders.Engagement(Integer.parseInt(p[3]),Double.parseDouble(p[4])),
                    new BattleOrders.Engagement(Integer.parseInt(p[5]),Double.parseDouble(p[6])),
                    new BattleOrders.Engagement(Integer.parseInt(p[7]),Double.parseDouble(p[8])),
                    0,Double.parseDouble(p[9]),Double.parseDouble(p[10]));
        }catch(RuntimeException bad){
            throw new IllegalArgumentException("Invalid tactical order field",bad);
        }
    }
    private static String digest(String data){
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte value:bytes){
                hex.append(Character.forDigit((value>>4)&15,16));
                hex.append(Character.forDigit(value&15,16));
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
