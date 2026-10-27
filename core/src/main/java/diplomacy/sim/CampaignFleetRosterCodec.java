package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Checksum-protected, unambiguous platform-neutral owned/deployed roster save. */
public final class CampaignFleetRosterCodec {
    private CampaignFleetRosterCodec(){}
    private static final String MAGIC="CD-CAMPAIGN-FLEET-1";
    private static String pack(String s){
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String unpack(String s){
        return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);
    }
    private static String hash(String message) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256")
                    .digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:digest)out.append(Character.forDigit((b>>>4)&15,16))
                    .append(Character.forDigit(b&15,16));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static String encode(CampaignFleetRoster.Fleet fleet){
        if(fleet==null)throw new IllegalArgumentException("Missing roster");
        StringBuilder b=new StringBuilder(MAGIC).append('\n').append(fleet.nextId()).append('\n');
        b.append(fleet.owned().size()).append('\n');
        for(var h:fleet.owned()){
            b.append(h.id()).append(',').append(pack(h.name())).append(',')
                    .append(pack(h.fitting())).append(',').append(h.hull()).append(',')
                    .append(h.shields()).append(',').append(h.armor()).append(',')
                    .append(h.fuel()).append(',').append(h.crew()).append(',')
                    .append(h.kinetic()).append(',').append(h.missiles()).append(',')
                    .append(h.torpedoes()).append(',').append(h.fighters()).append(',')
                    .append(h.drones()).append(',').append(h.integrity()).append(',')
                    .append(h.missions()).append(',').append(h.inService()).append('\n');
        }
        b.append(fleet.deployment().size()).append('\n');
        for(long id:fleet.deployment())b.append(id).append('\n');
        return b.toString()+hash(b.toString())+"\n";
    }
    public static CampaignFleetRoster.Fleet decode(String text){
        if(text==null||text.length()>20000000)throw new IllegalArgumentException("Invalid roster file size");
        String[] l=text.split("\n",-1);
        try{
            int i=0;
            if(!MAGIC.equals(l[i++]))throw new IllegalArgumentException("Unknown roster version");
            long nextId=Long.parseLong(l[i++]);
            int size=Integer.parseInt(l[i++]);
            if(size<0||size>2000)throw new IllegalArgumentException("Invalid owned fleet size");
            List<CampaignFleetRoster.Hull> owned=new ArrayList<>();
            for(int k=0;k<size;k++){
                String[] p=l[i++].split(",",-1);
                if(p.length!=16)throw new IllegalArgumentException("Invalid hull entry");
                owned.add(new CampaignFleetRoster.Hull(Long.parseLong(p[0]),unpack(p[1]),
                        unpack(p[2]),Double.parseDouble(p[3]),Double.parseDouble(p[4]),
                        Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),
                        Integer.parseInt(p[8]),Integer.parseInt(p[9]),Integer.parseInt(p[10]),
                        Integer.parseInt(p[11]),Integer.parseInt(p[12]),p[13],
                        Integer.parseInt(p[14]),Boolean.parseBoolean(p[15])));
            }
            int activeCount=Integer.parseInt(l[i++]);
            if(activeCount<0||activeCount>200)throw new IllegalArgumentException("Invalid selected fleet");
            List<Long> selected=new ArrayList<>();
            for(int k=0;k<activeCount;k++)selected.add(Long.parseLong(l[i++]));
            if(i+2!=l.length||!l[i+1].isEmpty())
                throw new IllegalArgumentException("Invalid roster footer");
            StringBuilder body=new StringBuilder();
            for(int n=0;n<i;n++)body.append(l[n]).append('\n');
            if(!MessageDigest.isEqual(hash(body.toString()).getBytes(StandardCharsets.US_ASCII),
                    l[i].getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Roster checksum mismatch");
            return new CampaignFleetRoster.Fleet(owned,selected,nextId);
        }catch(RuntimeException error){throw new IllegalArgumentException("Invalid campaign fleet roster",error);}
    }
}
