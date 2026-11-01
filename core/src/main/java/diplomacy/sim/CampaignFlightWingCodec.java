package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Lossless campaign roster + per-physical-bay fighter complement save bridge.
 * CD-ROSTER-1..4 remain readable; migration assigns only existing paid stock.
 * This envelope is not yet wired to live launches, refits or the game client's
 * current CampaignFleetCodec save call sites.
 */
public final class CampaignFlightWingCodec {
    private static final String MAGIC="CD-ROSTER-WINGS-1";
    private CampaignFlightWingCodec(){}

    public record Saved(CampaignFleet.Roster roster,Map<Long,FlightWingManifest> wings) {
        public Saved {
            if(roster==null||wings==null)
                throw new IllegalArgumentException("Incomplete flight-wing roster");
            for(var entry:wings.entrySet())
                if(entry.getKey()==null||entry.getValue()==null)
                    throw new IllegalArgumentException("Invalid ship flight wing");
            wings=Map.copyOf(wings);
        }
        public FlightWingManifest wing(long vesselId){
            FlightWingManifest found=wings.get(vesselId);
            if(found==null)throw new IllegalArgumentException("Unregistered flight wing");
            return found;
        }
    }

    /** Deterministic migration of every surviving paid aircraft in a legacy roster. */
    public static Saved migrate(CampaignFleet.Roster roster,TechnologyCatalog tech){
        if(roster==null||tech==null)throw new IllegalArgumentException("Missing campaign");
        Map<Long,FlightWingManifest> result=new HashMap<>();
        for(CampaignFleet.Vessel vessel:roster.ships())
            result.put(vessel.id(),FlightWingManifest.legacy(
                    vessel.fitting(),tech,vessel.stores().fighters()));
        return new Saved(roster,result);
    }

    /** Reject unknown hulls, missing manifests, overfilled bays and invented stock. */
    private static void validate(Saved saved,TechnologyCatalog tech){
        if(saved==null||tech==null)throw new IllegalArgumentException("Missing campaign");
        if(saved.wings().size()!=saved.roster().ships().size())
            throw new IllegalArgumentException("Missing or foreign ship flight wing");
        for(CampaignFleet.Vessel vessel:saved.roster().ships()){
            FlightWingManifest wing=saved.wings().get(vessel.id());
            if(wing==null)throw new IllegalArgumentException("Missing ship flight wing");
            FlightWingManifest.of(vessel.fitting(),tech,
                    vessel.stores().fighters(),wing.wings());
        }
    }

    /**
     * Encapsulate the unchanged, independently checksummed campaign roster and
     * each independently checked flight manifest in one checked envelope.
     * The vessel ID, not roster iteration order, owns each physical complement.
     */
    public static String encode(Saved saved,TechnologyCatalog tech){
        validate(saved,tech);
        StringBuilder body=new StringBuilder(MAGIC).append('\n')
                .append(pack(CampaignFleetCodec.encode(saved.roster()))).append('\n');
        saved.roster().ships().stream().sorted(java.util.Comparator.comparingLong(
                CampaignFleet.Vessel::id)).forEach(vessel->
                body.append(vessel.id()).append(',')
                        .append(pack(saved.wing(vessel.id()).encode())).append('\n'));
        return body.toString()+checksum(body.toString())+'\n';
    }

    /** Existing CD-ROSTER-1..4 saves migrate without adding or deleting aircraft. */
    public static Saved decode(String payload,TechnologyCatalog tech){
        if(payload==null||payload.length()>4_000_000||tech==null)
            throw new IllegalArgumentException("Invalid fighter roster");
        if(!payload.startsWith(MAGIC+"\n"))
            return migrate(CampaignFleetCodec.decode(payload,tech),tech);
        String[] lines=payload.split("\n",-1);
        if(lines.length<4||lines.length>205||!lines[lines.length-1].isEmpty())
            throw new IllegalArgumentException("Invalid fighter roster envelope");
        StringBuilder body=new StringBuilder();
        for(int i=0;i<lines.length-2;i++)body.append(lines[i]).append('\n');
        if(!MessageDigest.isEqual(checksum(body.toString()).getBytes(StandardCharsets.US_ASCII),
                lines[lines.length-2].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Fighter roster checksum mismatch");
        try{
            CampaignFleet.Roster roster=CampaignFleetCodec.decode(unpack(lines[1]),tech);
            Map<Long,FlightWingManifest> result=new HashMap<>();
            for(int i=2;i<lines.length-2;i++){
                String[] pair=lines[i].split(",",-1);
                if(pair.length!=2)throw new IllegalArgumentException("Invalid wing ownership row");
                long id=Long.parseLong(pair[0]);
                CampaignFleet.Vessel vessel=roster.get(id);
                FlightWingManifest manifest=FlightWingManifest.decode(
                        unpack(pair[1]),vessel.fitting(),tech,vessel.stores().fighters());
                if(result.putIfAbsent(id,manifest)!=null)
                    throw new IllegalArgumentException("Duplicate ship wing ownership");
            }
            Saved saved=new Saved(roster,result);
            validate(saved,tech);
            return saved;
        }catch(RuntimeException invalid){
            throw new IllegalArgumentException("Invalid campaign fighter complement",invalid);
        }
    }

    private static String pack(String source){
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(source.getBytes(StandardCharsets.UTF_8));
    }
    private static String unpack(String encoded){
        byte[] bytes=Base64.getUrlDecoder().decode(encoded);
        String decoded=new String(bytes,StandardCharsets.UTF_8);
        if(!pack(decoded).equals(encoded))
            throw new IllegalArgumentException("Noncanonical fighter roster encoding");
        return decoded;
    }
    private static String checksum(String source){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(hash.length*2);
            for(byte b:hash){
                hex.append(Character.forDigit((b>>>4)&15,16));
                hex.append(Character.forDigit(b&15,16));
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IllegalStateException("SHA-256 unavailable",unavailable);
        }
    }
}
