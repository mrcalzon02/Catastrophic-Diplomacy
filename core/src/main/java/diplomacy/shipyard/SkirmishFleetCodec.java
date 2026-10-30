package diplomacy.shipyard;

import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.HexFormat;

/** Portable fleet blueprint collection, independent of the challenge's tech
 * cap. A Tech II fleet may enter Tech II, III, IV, V or VI matches. The host
 * validates every ship against the current match restrictions before launch.
 * SHA-256 detects accidental corruption; it is not proof of trusted authorship. */
public final class SkirmishFleetCodec {
    private static final String MAGIC="CD-SKIRMISH-FLEET-1";
    private SkirmishFleetCodec(){}
    private static String b64(String s){
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String unb64(String s){
        return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);
    }
    private static String hash(String value){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static String encode(SkirmishFleet fleet,TechnologyCatalog catalog){
        if(fleet==null||catalog==null)throw new IllegalArgumentException("Fleet/catalog needed");
        StringBuilder body=new StringBuilder(MAGIC).append('\n')
                .append(fleet.size()).append('\n');
        for(var d:fleet.ships()){
            if(!Shipyard.inspect(d,catalog).valid())
                throw new IllegalArgumentException("Unserviceable fleet design");
            body.append(b64(Shipyard.encode(d))).append('\n');
        }
        String s=body.toString();
        String saved=s+hash(s)+"\n";
        if(saved.length()>2_000_000)throw new IllegalArgumentException("Fleet file too large");
        return saved;
    }
    public static SkirmishFleet decode(String data,TechnologyCatalog catalog){
        if(data==null||data.length()>2_000_000||catalog==null)
            throw new IllegalArgumentException("Invalid fleet file");
        String[] rows=data.split("\\n",-1);
        if(rows.length<4||!MAGIC.equals(rows[0])||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unknown skirmish fleet format");
        try{
            int count=Integer.parseInt(rows[1]);
            if(count<1||count>200||rows.length!=count+4)
                throw new IllegalArgumentException("Invalid fleet ship count");
            StringBuilder body=new StringBuilder();
            for(int i=0;i<rows.length-2;i++)body.append(rows[i]).append('\n');
            if(!MessageDigest.isEqual(hash(body.toString()).getBytes(StandardCharsets.US_ASCII),
                    rows[rows.length-2].getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("Fleet checksum mismatch");
            List<Shipyard.Design> designs=new ArrayList<>(count);
            for(int i=0;i<count;i++)designs.add(Shipyard.decode(unb64(rows[i+2]),catalog));
            SkirmishFleet fleet=new SkirmishFleet(designs);
            for(var d:fleet.ships())if(!Shipyard.inspect(d,catalog).valid())
                throw new IllegalArgumentException("Unserviceable imported hull");
            return fleet;
        }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid fleet package",bad);}
    }
}
