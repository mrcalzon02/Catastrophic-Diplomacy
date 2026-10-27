package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Explicitly versioned, tamper-detecting campaign-owned vessel registry. */
public final class CampaignFleetCodec {
    private static final String MAGIC="CD-ROSTER-3";
    private static final String PREVIOUS="CD-ROSTER-2";
    private static final String LEGACY="CD-ROSTER-1";
    private CampaignFleetCodec(){}
    private static String pack(String s){
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String unpack(String s){
        return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);
    }
    private static String checksum(String text){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte b:digest){
                result.append(Character.forDigit((b>>>4)&15,16));
                result.append(Character.forDigit(b&15,16));
            }
            return result.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String encode(CampaignFleet.Roster roster){
        if(roster==null)throw new IllegalArgumentException("Missing campaign fleet");
        StringBuilder s=new StringBuilder(MAGIC).append('\n').append(roster.faction())
                .append(',').append(roster.style()).append(',')
                .append(roster.nextSerial()).append(',').append(roster.namingSeed()).append(',')
                .append(roster.radioTone()).append('\n');
        for(var v:roster.ships()){
            var stores=v.stores();
            s.append(v.id()).append(',').append(v.deployed()?1:0).append(',')
                    .append(pack(v.name())).append(',')
                    .append(pack(Shipyard.encode(v.fitting()))).append(',')
                    .append(stores.hull()).append(',')
                    .append(pack(stores.integrity())).append(',')
                    .append(pack(stores.machinery())).append(',')
                    .append(stores.crew()).append(',')
                    .append(stores.kinetic()).append(',')
                    .append(stores.missiles()).append(',')
                    .append(stores.torpedoes()).append(',')
                    .append(stores.fighters()).append(',')
                    .append(stores.drones()).append(',')
                    .append(stores.repairDrones()).append(',')
                    .append(stores.fuel()).append('\n');
        }
        return s.toString()+checksum(s.toString())+"\n";
    }
    public static CampaignFleet.Roster decode(String payload,TechnologyCatalog tech){
        if(payload==null||payload.length()>3_000_000||tech==null)
            throw new IllegalArgumentException("Invalid roster file");
        String[] rows=payload.split("\n",-1);
        if(rows.length<4||rows.length>204||
                (!MAGIC.equals(rows[0])&&!PREVIOUS.equals(rows[0])&&!LEGACY.equals(rows[0]))
                ||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unknown roster encoding");
        StringBuilder body=new StringBuilder();
        for(int i=0;i<rows.length-2;i++)body.append(rows[i]).append('\n');
        if(!MessageDigest.isEqual(checksum(body.toString()).getBytes(StandardCharsets.US_ASCII),
                rows[rows.length-2].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Fleet roster checksum mismatch");
        try{
            String[] header=rows[1].split(",",-1);
            boolean v3=MAGIC.equals(rows[0]);
            if(header.length!=(v3?5:3))
                throw new IllegalArgumentException("Invalid roster header");
            Battle.Faction faction=Battle.Faction.valueOf(header[0]);
            FleetNames.Style style=FleetNames.Style.valueOf(header[1]);
            int next=Integer.parseInt(header[2]);
            long namingSeed=v3?Long.parseLong(header[3]):faction.ordinal();
            CrewChatter.Tone tone=v3?CrewChatter.Tone.valueOf(header[4]):
                    CrewChatter.Tone.MIXED;
            List<CampaignFleet.Vessel> vessels=new ArrayList<>();
            for(int i=2;i<rows.length-2;i++){
                String[] parts=rows[i].split(",",-1);
                int offset=LEGACY.equals(rows[0])?0:1;
                if(parts.length!=14+offset)
                    throw new IllegalArgumentException("Invalid ship inventory");
                long id=Long.parseLong(parts[0]);
                boolean deployed=switch(parts[1]){
                    case "0" -> false;case "1" -> true;
                    default -> throw new IllegalArgumentException("Invalid sortie selection");
                };
                Shipyard.Design design=Shipyard.decode(unpack(parts[3]),tech);
                CampaignFleet.Stores stores=new CampaignFleet.Stores(
                        Double.parseDouble(parts[4]),unpack(parts[5]),
                        offset==0?ShipSubsystems.full():unpack(parts[6]),
                        Double.parseDouble(parts[6+offset]),Integer.parseInt(parts[7+offset]),
                        Integer.parseInt(parts[8+offset]),Integer.parseInt(parts[9+offset]),
                        Integer.parseInt(parts[10+offset]),Integer.parseInt(parts[11+offset]),
                        Integer.parseInt(parts[12+offset]),Double.parseDouble(parts[13+offset]));
                stores.apply(CampaignFleet.fresh(design,tech));
                vessels.add(new CampaignFleet.Vessel(id,unpack(parts[2]),design,deployed,stores));
            }
            return new CampaignFleet.Roster(faction,style,next,vessels,namingSeed,tone);
        }catch(RuntimeException invalid){
            throw new IllegalArgumentException("Invalid or incompatible campaign vessel registry",invalid);
        }
    }
}
