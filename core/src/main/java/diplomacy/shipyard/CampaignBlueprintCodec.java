package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Versioned, checksum-protected purchase blueprint, not a commissioned vessel.
 * A saved blueprint NEVER contains crew, ammunition, fuel, stored craft or ship IDs.
 */
public final class CampaignBlueprintCodec {
    private static final String MAGIC="CD-CAMPAIGN-BLUEPRINT-1\n";
    private static final String TRAILER="CHECKSUM|";
    private CampaignBlueprintCodec() {}

    public static String encode(Shipyard.Design design,TechnologyCatalog technology){
        requireValid(design,technology);
        String body=MAGIC+Shipyard.encode(design);
        return body+TRAILER+digest(body)+"\n";
    }
    public static Shipyard.Design decode(String data,TechnologyCatalog technology,
                                          Battle.Faction owner){
        if(data==null||data.length()>25000||!data.startsWith(MAGIC)||
                owner==null||technology==null)
            throw new IllegalArgumentException("Invalid campaign purchase blueprint");
        int marker=data.lastIndexOf(TRAILER);
        if(marker<MAGIC.length()||data.indexOf(TRAILER)!=marker)
            throw new IllegalArgumentException("Missing campaign blueprint checksum");
        String body=data.substring(0,marker);
        String checksum=data.substring(marker+TRAILER.length());
        if(!checksum.matches("[0-9a-f]{64}\\n")||
                !MessageDigest.isEqual(digest(body).getBytes(StandardCharsets.US_ASCII),
                        checksum.substring(0,64).getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Campaign blueprint checksum mismatch");
        Shipyard.Design design=Shipyard.decode(body.substring(MAGIC.length()),technology);
        requireValid(design,technology);
        if(design.faction!=owner)
            throw new IllegalArgumentException("Campaign blueprint belongs to another faction");
        return design;
    }
    private static void requireValid(Shipyard.Design design,TechnologyCatalog technology){
        Shipyard.Report report=Shipyard.inspect(design,technology);
        if(!report.valid())throw new IllegalArgumentException(
                "Campaign blueprint has unserviceable machinery: "+report.explanation());
    }
    private static String digest(String text){
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 required by Java 17",impossible);
        }
    }
}
