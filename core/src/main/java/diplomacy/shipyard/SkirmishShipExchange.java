package diplomacy.shipyard;

import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Portable, integrity-checked skirmish ship plus its challenge restrictions.
 * Not a signature or proof of trusted server authorization. */
public final class SkirmishShipExchange {
    private static final String MAGIC="CD-SKIRMISH-SHIP-1";
    private SkirmishShipExchange(){}
    public record Package(Shipyard.Design design,SkirmishTechnologyRules rules){}
    private static String digest(String text){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 required",impossible);
        }
    }
    private static String base64(String text){
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    private static String plain(String text){
        return new String(Base64.getUrlDecoder().decode(text),StandardCharsets.UTF_8);
    }
    public static String encode(Shipyard.Design design,SkirmishTechnologyRules rules,
                                TechnologyCatalog catalog){
        if(design==null||rules==null||catalog==null)
            throw new IllegalArgumentException("Ship, challenge and catalog required");
        rules.requireAllowed(design,catalog);
        Shipyard.Report report=Shipyard.inspect(design,catalog);
        if(!report.valid())
            throw new IllegalArgumentException("Exported ship is not operational: "+report.explanation());
        String body=MAGIC+"\n"+base64(SkirmishTechnologyRules.encode(rules))+"\n"+
                base64(Shipyard.encode(design))+"\n";
        return body+digest(body)+"\n";
    }
    public static Package decode(String text,TechnologyCatalog catalog){
        if(text==null||text.length()>300000||catalog==null)
            throw new IllegalArgumentException("Missing or oversized skirmish ship");
        String[] lines=text.split("\\n",-1);
        if(lines.length!=5||!MAGIC.equals(lines[0])||!lines[4].isEmpty())
            throw new IllegalArgumentException("Unknown ship exchange format");
        String body=lines[0]+"\n"+lines[1]+"\n"+lines[2]+"\n";
        if(!MessageDigest.isEqual(digest(body).getBytes(StandardCharsets.US_ASCII),
                lines[3].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Ship exchange checksum mismatch");
        try{
            SkirmishTechnologyRules rules=SkirmishTechnologyRules.decode(plain(lines[1]));
            Shipyard.Design design=Shipyard.decode(plain(lines[2]),catalog);
            rules.requireAllowed(design,catalog);
            Shipyard.Report report=Shipyard.inspect(design,catalog);
            if(!report.valid())throw new IllegalArgumentException("Unserviceable imported ship");
            return new Package(design,rules);
        }catch(RuntimeException rejected){
            throw new IllegalArgumentException("Invalid or restricted ship exchange",rejected);
        }
    }
}
