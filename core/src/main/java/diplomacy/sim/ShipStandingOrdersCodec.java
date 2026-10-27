package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Versioned, checksummed per-faction standing orders; never rewrites fleet blueprints. */
public final class ShipStandingOrdersCodec {
    private static final String MAGIC="CD-STANDING-2";
    private static final String LEGACY="CD-STANDING-1";
    private ShipStandingOrdersCodec(){}
    public record File(Battle.Faction faction,Map<Long,ShipStandingOrders> policies) {
        public File {
            if(faction==null||policies==null)throw new IllegalArgumentException("Missing standing orders");
            policies=Map.copyOf(policies);
        }
    }
    public static String encode(Battle.Faction faction,Map<Long,ShipStandingOrders> records){
        if(faction==null||records==null)throw new IllegalArgumentException("Missing standing orders");
        StringBuilder content=new StringBuilder(MAGIC).append('\n').append(faction.name()).append('\n');
        for(var entry:new TreeMap<>(records).entrySet()){
            long slot=entry.getKey();
            ShipStandingOrders policy=entry.getValue();
            if((slot<0||slot>=200)&&!VesselIdentifiers.valid(slot)||policy==null)throw new IllegalArgumentException("Invalid hull slot");
            var o=policy.tactics();
            content.append(slot).append(',').append(policy.aggression()).append(',')
                    .append(policy.caution()).append(',').append(policy.shieldWithdrawPercent()).append(',')
                    .append(o.pointDefense()).append(',').append(o.targeting()).append(',')
                    .append(o.position()).append(',')
                    .append(o.fighters().priority()).append(',').append(o.fighters().range()).append(',')
                    .append(o.escorts().priority()).append(',').append(o.escorts().range()).append(',')
                    .append(o.capitals().priority()).append(',').append(o.capitals().range()).append(',')
                    .append(o.retreatAtHullFraction());
            var d=policy.directives();
            content.append(',').append(d.speedPercent()).append(',')
                    .append(d.interceptStrategy()).append(',').append(d.defenseScope())
                    .append(',').append(d.fighterMission()).append(',').append(d.escortSlot())
                    .append(',').append(d.escortRange());
            for(int defended:d.defendSlots())content.append(',').append(defended);
            content.append('\n');
        }
        return content.toString()+sha256(content.toString())+"\n";
    }
    public static File decode(String data){
        if(data==null||data.length()>60000)throw new IllegalArgumentException("Oversized standing orders file");
        String[] lines=data.split("\n",-1);
        if(lines.length<4||(!MAGIC.equals(lines[0])&&!LEGACY.equals(lines[0]))||
                !lines[lines.length-1].isEmpty())
            throw new IllegalArgumentException("Unsupported standing orders format");
        StringBuilder content=new StringBuilder();
        for(int i=0;i<lines.length-2;i++)content.append(lines[i]).append('\n');
        if(!MessageDigest.isEqual(sha256(content.toString()).getBytes(StandardCharsets.US_ASCII),
                lines[lines.length-2].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Standing orders checksum mismatch");
        try{
            Battle.Faction faction=Battle.Faction.valueOf(lines[1]);
            Map<Long,ShipStandingOrders> result=new TreeMap<>();
            for(int i=2;i<lines.length-2;i++){
                String[] p=lines[i].split(",",-1);
                if(p.length!=(MAGIC.equals(lines[0])?24:14))
                    throw new IllegalArgumentException("Invalid doctrine field count");
                long slot=Long.parseLong(p[0]);
                if(((slot<0||slot>=200)&&!VesselIdentifiers.valid(slot))||result.containsKey(slot))
                    throw new IllegalArgumentException("Invalid or duplicate doctrine slot");
                BattleOrders.Orders orders=new BattleOrders.Orders(
                        BattleOrders.PointDefense.valueOf(p[4]),
                        BattleOrders.Targeting.valueOf(p[5]),
                        BattleOrders.Position.valueOf(p[6]),
                        new BattleOrders.Engagement(Integer.parseInt(p[7]),Double.parseDouble(p[8])),
                        new BattleOrders.Engagement(Integer.parseInt(p[9]),Double.parseDouble(p[10])),
                        new BattleOrders.Engagement(Integer.parseInt(p[11]),Double.parseDouble(p[12])),
                        0,180,Double.parseDouble(p[13]));
                ShipTacticalDirectives directives=ShipTacticalDirectives.DEFAULT;
                if(p.length==24)
                    directives=new ShipTacticalDirectives(Integer.parseInt(p[14]),
                            ShipTacticalDirectives.InterceptStrategy.valueOf(p[15]),
                            ShipTacticalDirectives.DefenseScope.valueOf(p[16]),
                            ShipTacticalDirectives.FighterMission.valueOf(p[17]),
                            java.util.List.of(Integer.parseInt(p[20]),Integer.parseInt(p[21]),
                                    Integer.parseInt(p[22]),Integer.parseInt(p[23])),
                            Integer.parseInt(p[18]),Integer.parseInt(p[19]));
                result.put(slot,new ShipStandingOrders(orders,Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]),Integer.parseInt(p[3]),directives));
            }
            return new File(faction,result);
        }catch(RuntimeException malformed){
            throw new IllegalArgumentException("Invalid standing orders entries",malformed);
        }
    }
    private static String sha256(String source){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder(64);
            for(byte b:bytes){
                s.append(Character.forDigit((b>>>4)&15,16))
                        .append(Character.forDigit(b&15,16));
            }
            return s.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
