package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/**
 * A single authoritative durable snapshot for credits, research, faction and
 * all commissioned ships. No pairing of independently updated save files.
 * This does not model payment twice when a process crashes after a successful
 * atomic rename: the entire transaction is already committed on next boot.
 */
public final class CampaignLedger {
    private static final String MAGIC="CD-CAMPAIGN-LEDGER-2";
    private static final String PREVIOUS="CD-CAMPAIGN-LEDGER-1";
    private static final int MAX_BYTES=4_500_000;
    private CampaignLedger(){}
    public record Snapshot(long generation,CampaignProgress.State progress,
                           CampaignFleet.Roster fleet,CampaignTechnology.State technology){
        public Snapshot(long generation,CampaignProgress.State progress,CampaignFleet.Roster fleet){
            this(generation,progress,fleet,new CampaignTechnology.State(progress.faction()));
        }
        public Snapshot {
            if(generation<1||progress==null||fleet==null||technology==null||
                    progress.faction()!=fleet.faction()||progress.faction()!=technology.faction())
                throw new IllegalArgumentException("Campaign wallet/fleet/technology identity mismatch");
        }
        public Snapshot next(CampaignProgress.State state,CampaignFleet.Roster roster){
            return new Snapshot(Math.addExact(generation,1),state,roster,technology);
        }
    }
    private static String checksum(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte b:bytes){
                result.append(Character.forDigit((b>>>4)&15,16));
                result.append(Character.forDigit(b&15,16));
            }
            return result.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 required",e);}
    }
    private static String b64(String s){
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String unb64(String s){
        return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);
    }
    public static String encode(Snapshot snapshot){
        if(snapshot==null)throw new IllegalArgumentException("Missing campaign snapshot");
        String body=MAGIC+"\n"+snapshot.generation()+"\n"+
                b64(CampaignProgress.encode(snapshot.progress()))+"\n"+
                b64(CampaignFleetCodec.encode(snapshot.fleet()))+"\n"+
                b64(CampaignTechnology.encode(snapshot.technology()))+"\n";
        String payload=body+checksum(body)+"\n";
        if(payload.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Campaign snapshot exceeds capacity");
        return payload;
    }
    /** Shipyard.Design intentionally has identity equality. Compare the
     * complete canonical serialized wallet and fitted ships instead. */
    public static boolean equivalent(Snapshot left,Snapshot right){
        return left!=null&&right!=null&&encode(left).equals(encode(right));
    }
    public static Snapshot decode(String data,TechnologyCatalog catalog){
        if(data==null||catalog==null||data.length()>MAX_BYTES)
            throw new IllegalArgumentException("Missing or oversized campaign snapshot");
        String[] rows=data.split("\n",-1);
        boolean old=rows.length==6&&PREVIOUS.equals(rows[0]);
        boolean current=rows.length==7&&MAGIC.equals(rows[0]);
        if((!old&&!current)||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unknown campaign snapshot version");
        StringBuilder payload=new StringBuilder();
        for(int i=0;i<rows.length-2;i++)payload.append(rows[i]).append('\n');
        if(!MessageDigest.isEqual(checksum(payload.toString()).getBytes(StandardCharsets.US_ASCII),
                rows[rows.length-2].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Campaign snapshot checksum mismatch");
        try{
            long generation=Long.parseLong(rows[1]);
            CampaignProgress.State progress=CampaignProgress.decode(unb64(rows[2]));
            CampaignFleet.Roster fleet=CampaignFleetCodec.decode(unb64(rows[3]),catalog);
            CampaignTechnology.State research=old?
                    new CampaignTechnology.State(progress.faction()):
                    CampaignTechnology.decode(unb64(rows[4]),catalog);
            return new Snapshot(generation,progress,fleet,research);
        }catch(RuntimeException invalid){
            throw new IllegalArgumentException("Invalid saved campaign wallet or fleet",invalid);
        }
    }
    /** A missing authority permits one-time import of pre-ledger saves. If either
     * authority copy exists but both fail validation, do not load stale legacies. */
    public static Optional<Snapshot> load(Path path,TechnologyCatalog technology){
        if(path==null||technology==null)throw new IllegalArgumentException("Invalid ledger lookup");
        Path backup=backup(path);
        boolean present=Files.exists(path)||Files.exists(backup);
        Snapshot primary=tryRead(path,technology);
        Snapshot secondary=tryRead(backup,technology);
        if(primary==null&&secondary==null){
            if(present)throw new IllegalArgumentException(
                    "Campaign primary and backup are invalid. Do not issue replacement ships.");
            return Optional.empty();
        }
        if(primary==null)return Optional.of(secondary);
        if(secondary==null)return Optional.of(primary);
        return Optional.of(primary.generation()>=secondary.generation()?primary:secondary);
    }
    private static Snapshot tryRead(Path path,TechnologyCatalog technology){
        if(!Files.exists(path))return null;
        try{
            // Android API 26 provides readAllBytes, but Files.readString is API 33+.
            // Reject oversized authorities before allocation; a valid backup can recover.
            if(Files.size(path)>MAX_BYTES)return null;
            byte[] data=Files.readAllBytes(path);
            if(data.length>MAX_BYTES)return null;
            return decode(new String(data,StandardCharsets.UTF_8),technology);
        }catch(IOException|RuntimeException broken){return null;}
    }
    private static Path backup(Path path){
        return path.resolveSibling(path.getFileName().toString()+".bak");
    }
    /** Save a checked next generation. Write and fsync a temporary complete
     * snapshot, atomically replace its primary, then read back. Copy a valid old
     * authority to .bak using its own temp+atomic replacement.
     *
     * A filesystem without atomic rename support fails closed; we will not
     * silently delete an existing primary to fake atomic replacement.
     */
    public static void commit(Path path,Snapshot proposed,TechnologyCatalog technology)
            throws IOException {
        if(path==null||proposed==null||technology==null)
            throw new IllegalArgumentException("Invalid campaign transaction");
        Path parent=path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Optional<Snapshot> before=load(path,technology);
        long expected=before.map(s->Math.addExact(s.generation(),1)).orElse(1L);
        if(proposed.generation()!=expected)
            throw new IllegalArgumentException("Stale or duplicate campaign transaction");
        String content=encode(proposed);
        if(!equivalent(decode(content,technology),proposed))
            throw new IllegalArgumentException("Wallet/fleet transaction failed validation");
        Path previous=backup(path);
        // Use the highest valid prior snapshot as the recovery copy.
        if(before.isPresent()){
            String prior=encode(before.get());
            atomicWrite(previous,prior);
        }
        atomicWrite(path,content);
        Snapshot actual=load(path,technology).orElseThrow();
        if(!equivalent(actual,proposed))
            throw new IOException("Written campaign snapshot verification failed");
    }
    private static void atomicWrite(Path path,String content)throws IOException{
        Path temp=path.resolveSibling(path.getFileName().toString()+".tmp");
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream stream=new FileOutputStream(temp.toFile())){
            stream.write(bytes);
            stream.flush();
            stream.getFD().sync();
        }
        try{
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }catch(AtomicMoveNotSupportedException unsupported){
            throw new IOException("Atomic campaign save is unsupported on this filesystem",unsupported);
        }
    }
}
