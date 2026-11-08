package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
    public enum AuthorityStatus { MISSING, VALID, INVALID, UNAVAILABLE }
    /** UNAVAILABLE is not corruption: an unreadable copy might be the newest
     * valid generation, so neither a stale fallback nor an automatic reset is safe. */
    public record Inspection(AuthorityStatus status,Snapshot authority,String message) { }
    public enum CommitStatus {
        COMMITTED, ALREADY_COMMITTED, RETRYABLE_FAILURE, CONFLICT,
        INVALID_AUTHORITY, STORAGE_UNAVAILABLE, OUTCOME_UNKNOWN
    }
    /** A caller may adopt proposed state only when committed() is true.
     * Otherwise it must retain the same before/proposed transaction for recovery. */
    public record CommitResult(CommitStatus status,Snapshot authority,String message) {
        public boolean committed(){
            return status==CommitStatus.COMMITTED||status==CommitStatus.ALREADY_COMMITTED;
        }
    }
    /** archive is non-null only after every original copy was verified there. */
    public record ReplacementResult(CommitResult commit,Path archive) { }
    private record Copy(AuthorityStatus status,Snapshot snapshot,String message) { }
    private record Fingerprint(long size,byte[] digest) { }
    private record Archived(Path original,Path copy,Fingerprint fingerprint) { }

    /** Small filesystem boundary for deterministic write/readback fault tests. */
    static class Storage {
        Inspection inspect(Path path,TechnologyCatalog catalog){
            return CampaignLedger.inspect(path,catalog);
        }
        void write(Path path,String content)throws IOException { atomicWrite(path,content); }
        void archive(Path source,Path destination)throws IOException {
            try(InputStream input=Files.newInputStream(source);
                    FileOutputStream output=new FileOutputStream(destination.toFile())){
                byte[] buffer=new byte[8192];
                int length;
                while((length=input.read(buffer))!=-1)output.write(buffer,0,length);
                output.flush();output.getFD().sync();
            }
        }
    }
    private static final Storage DISK=new Storage();

    /** Inspect both authorities without treating access failures as damaged data. */
    public static Inspection inspect(Path path,TechnologyCatalog technology){
        requirePath(path,technology);
        Copy primary=readCopy(path,technology),secondary=readCopy(backup(path),technology);
        if(primary.status()==AuthorityStatus.UNAVAILABLE||secondary.status()==AuthorityStatus.UNAVAILABLE)
            return new Inspection(AuthorityStatus.UNAVAILABLE,null,
                    primary.status()==AuthorityStatus.UNAVAILABLE?primary.message():secondary.message());
        Snapshot first=primary.snapshot(),second=secondary.snapshot();
        if(first!=null||second!=null){
            Snapshot latest=first==null?second:second==null?first:
                    first.generation()>=second.generation()?first:second;
            return new Inspection(AuthorityStatus.VALID,latest,"Campaign authority verified.");
        }
        if(primary.status()==AuthorityStatus.MISSING&&secondary.status()==AuthorityStatus.MISSING)
            return new Inspection(AuthorityStatus.MISSING,null,"No campaign authority exists.");
        return new Inspection(AuthorityStatus.INVALID,null,
                "Campaign primary and backup contain no valid snapshot. Preserve them before an explicit reset.");
    }
    /** A missing authority permits one-time import of pre-ledger saves. Invalid
     * or inaccessible authorities never authorize a silent replacement/import. */
    public static Optional<Snapshot> load(Path path,TechnologyCatalog technology){
        Inspection found=inspect(path,technology);
        if(found.status()==AuthorityStatus.VALID)return Optional.of(found.authority());
        if(found.status()==AuthorityStatus.MISSING)return Optional.empty();
        throw new IllegalArgumentException(found.message());
    }
    private static void requirePath(Path path,TechnologyCatalog technology){
        if(path==null||path.getFileName()==null||technology==null)
            throw new IllegalArgumentException("Invalid ledger lookup");
    }
    private static Copy readCopy(Path path,TechnologyCatalog technology){
        byte[] data;
        try{
            BasicFileAttributes attributes=Files.readAttributes(path,BasicFileAttributes.class);
            if(!attributes.isRegularFile())
                return new Copy(AuthorityStatus.UNAVAILABLE,null,
                        "Campaign path is not a regular file: "+path.getFileName());
            // Bound the stream too: a file can grow after its size was checked.
            if(attributes.size()>MAX_BYTES)
                return new Copy(AuthorityStatus.INVALID,null,"Oversized campaign snapshot.");
            try(InputStream input=Files.newInputStream(path);
                    ByteArrayOutputStream output=new ByteArrayOutputStream((int)attributes.size())){
                byte[] buffer=new byte[8192];int length,total=0;
                while((length=input.read(buffer))!=-1){
                    total+=length;
                    if(total>MAX_BYTES)
                        return new Copy(AuthorityStatus.INVALID,null,"Oversized campaign snapshot.");
                    output.write(buffer,0,length);
                }
                data=output.toByteArray();
            }
        }catch(NoSuchFileException missing){
            // A dangling link or inaccessible ancestor is not a fresh campaign.
            if(knownMissing(path))
                return new Copy(AuthorityStatus.MISSING,null,"");
            return new Copy(AuthorityStatus.UNAVAILABLE,null,"Campaign storage is inaccessible.");
        }catch(IOException|RuntimeException failure){
            return new Copy(AuthorityStatus.UNAVAILABLE,null,
                    "Unable to read campaign storage: "+failureMessage(failure));
        }
        try{
            return new Copy(AuthorityStatus.VALID,
                    decode(new String(data,StandardCharsets.UTF_8),technology),"");
        }catch(IllegalArgumentException invalid){
            return new Copy(AuthorityStatus.INVALID,null,"Invalid campaign snapshot.");
        }
    }
    private static boolean knownMissing(Path path){
        try{return Files.notExists(path,LinkOption.NOFOLLOW_LINKS);}
        catch(SecurityException|UnsupportedOperationException unavailable){return false;}
    }
    private static Path backup(Path path){
        return path.resolveSibling(path.getFileName().toString()+".bak");
    }
    private static String checkedContent(Snapshot proposed,TechnologyCatalog technology){
        if(proposed==null)throw new IllegalArgumentException("Missing campaign transaction");
        String content=encode(proposed);
        if(!equivalent(decode(content,technology),proposed))
            throw new IllegalArgumentException("Wallet/fleet transaction failed validation");
        return content;
    }
    private static boolean matches(Inspection found,Snapshot snapshot){
        return snapshot==null?found.status()==AuthorityStatus.MISSING:
                found.status()==AuthorityStatus.VALID&&equivalent(found.authority(),snapshot);
    }
    private static CommitResult result(CommitStatus status,Inspection found,String message){
        return new CommitResult(status,found.authority(),message);
    }
    private static CommitResult unavailableOrConflict(Inspection found){
        return result(switch(found.status()){
            case INVALID -> CommitStatus.INVALID_AUTHORITY;
            case UNAVAILABLE -> CommitStatus.STORAGE_UNAVAILABLE;
            default -> CommitStatus.CONFLICT;
        },found,found.status()==AuthorityStatus.VALID||found.status()==AuthorityStatus.MISSING?
                "Campaign authority changed; the pending transaction was not applied.":found.message());
    }
    private static String failureMessage(Exception failure){
        String message=failure.getMessage();
        return message==null||message.isBlank()?failure.getClass().getSimpleName():message;
    }

    /** Strict compatibility entrypoint: duplicate/stale generations still fail.
     * New recoverable controllers should retain both snapshots and use the API below. */
    public static synchronized void commit(Path path,Snapshot proposed,TechnologyCatalog technology)
            throws IOException {
        requirePath(path,technology);
        if(proposed==null)throw new IllegalArgumentException("Missing campaign transaction");
        Inspection found=inspect(path,technology);
        if(found.status()==AuthorityStatus.INVALID)
            throw new IllegalArgumentException(found.message());
        if(found.status()==AuthorityStatus.UNAVAILABLE)throw new IOException(found.message());
        Snapshot before=found.authority();
        long expected=before==null?1:Math.addExact(before.generation(),1);
        if(proposed.generation()!=expected)
            throw new IllegalArgumentException("Stale or duplicate campaign transaction");
        CommitResult committed=commitRecoverably(path,before,proposed,technology,DISK);
        if(committed.committed())return;
        if(committed.status()==CommitStatus.CONFLICT||committed.status()==CommitStatus.INVALID_AUTHORITY)
            throw new IllegalArgumentException(committed.message());
        throw new IOException(committed.message());
    }
    /** Retry one immutable transaction. The exact proposed authority acknowledges
     * a lost successful write without a second generation/debit. Otherwise the
     * entire current authority must match before, not merely its generation.
     * before is null only for first creation, whose proposed generation is 1.
     * Writers are serialized in this process; all game writes use these methods. */
    public static synchronized CommitResult commitRecoverably(Path path,Snapshot before,
            Snapshot proposed,TechnologyCatalog technology){
        return commitRecoverably(path,before,proposed,technology,DISK);
    }
    static synchronized CommitResult commitRecoverably(Path path,Snapshot before,
            Snapshot proposed,TechnologyCatalog technology,Storage storage){
        requirePath(path,technology);
        String content=checkedContent(proposed,technology);
        long expected=before==null?1:Math.addExact(before.generation(),1);
        if(proposed.generation()!=expected)
            throw new IllegalArgumentException("Pending campaign must be the next generation");
        Inspection found=storage.inspect(path,technology);
        if(matches(found,proposed))
            return result(CommitStatus.ALREADY_COMMITTED,found,
                    "Campaign transaction was already saved; no duplicate was applied.");
        if(!matches(found,before))return unavailableOrConflict(found);
        try{
            Files.createDirectories(path.toAbsolutePath().getParent());
            if(before!=null)storage.write(backup(path),encode(before));
            // Recheck after preserving the backup, before replacing the primary.
            Inspection current=storage.inspect(path,technology);
            if(matches(current,proposed))
                return result(CommitStatus.ALREADY_COMMITTED,current,"Campaign transaction already saved.");
            if(!matches(current,before))return unavailableOrConflict(current);
            storage.write(path,content);
            Inspection written=storage.inspect(path,technology);
            if(matches(written,proposed))
                return result(CommitStatus.COMMITTED,written,"Campaign transaction saved and verified.");
            return reconcile(path,before,proposed,technology,storage,written.message());
        }catch(IOException|RuntimeException failure){
            return reconcile(path,before,proposed,technology,storage,failureMessage(failure));
        }
    }
    private static CommitResult reconcile(Path path,Snapshot before,Snapshot proposed,
            TechnologyCatalog technology,Storage storage,String error){
        Inspection actual=storage.inspect(path,technology);
        if(matches(actual,proposed))
            return result(CommitStatus.COMMITTED,actual,"Campaign write verified after interrupted acknowledgement.");
        if(matches(actual,before))
            return result(CommitStatus.RETRYABLE_FAILURE,actual,
                    "Campaign remains unchanged. Restore storage access/free space and retry the same transaction. "+error);
        if(actual.status()==AuthorityStatus.VALID)
            return result(CommitStatus.CONFLICT,actual,"Another campaign authority exists; pending transaction retained.");
        return result(CommitStatus.OUTCOME_UNKNOWN,actual,
                "Campaign write outcome could not be verified. Keep the pending transaction and retry. "+error);
    }

    /** Explicit player-authorized reset only. Copy and verify every existing
     * damaged authority into a unique sibling archive before replacing primary.
     * An ordinary load never calls this method. A readable valid backup forbids
     * reset; inaccessible paths are not presumed corrupt. Original backup bytes
     * remain in place until the next normal commit, as well as in the archive. */
    public static synchronized ReplacementResult archiveAndReplaceInvalid(Path path,
            Snapshot proposed,TechnologyCatalog technology){
        return archiveAndReplaceInvalid(path,proposed,technology,DISK);
    }
    static synchronized ReplacementResult archiveAndReplaceInvalid(Path path,
            Snapshot proposed,TechnologyCatalog technology,Storage storage){
        requirePath(path,technology);
        String content=checkedContent(proposed,technology);
        if(proposed.generation()!=1)
            throw new IllegalArgumentException("Explicit campaign reset requires generation 1");
        Inspection found=storage.inspect(path,technology);
        if(matches(found,proposed))
            return new ReplacementResult(result(CommitStatus.ALREADY_COMMITTED,found,
                    "Replacement campaign was already saved."),null);
        if(found.status()!=AuthorityStatus.INVALID)
            return new ReplacementResult(unavailableOrConflict(found),null);
        Path archive=null;
        List<Archived> originals=new ArrayList<>(2);
        try{
            Path folder=Files.createTempDirectory(path.toAbsolutePath().getParent(),
                    path.getFileName()+".recovery-");
            for(Path original:new Path[]{path,backup(path)}){
                if(knownMissing(original)){
                    originals.add(new Archived(original,null,null));continue;
                }
                Fingerprint before=fingerprint(original);
                Path copy=folder.resolve(original.getFileName().toString());
                storage.archive(original,copy);
                if(!same(before,fingerprint(copy))||!same(before,fingerprint(original)))
                    throw new IOException("Archived campaign copy could not be verified");
                originals.add(new Archived(original,copy,before));
            }
            if(originals.stream().allMatch(item->item.copy()==null))
                throw new IOException("Campaign files disappeared before archiving");
            Inspection current=storage.inspect(path,technology);
            if(current.status()!=AuthorityStatus.INVALID)
                return new ReplacementResult(unavailableOrConflict(current),archive);
            if(!originalsMatch(originals))
                return new ReplacementResult(result(CommitStatus.CONFLICT,current,
                        "Campaign files changed during archive; no replacement was written."),null);
            // Advertise the archive only after the final complete verification.
            archive=folder;
            storage.write(path,content);
            Inspection written=storage.inspect(path,technology);
            if(matches(written,proposed))
                return new ReplacementResult(result(CommitStatus.COMMITTED,written,
                        "Damaged campaign archived; replacement saved and verified."),archive);
            return reconcileReplacement(path,proposed,technology,storage,archive,originals,written.message());
        }catch(IOException|RuntimeException failure){
            if(archive==null)
                return new ReplacementResult(new CommitResult(CommitStatus.STORAGE_UNAVAILABLE,null,
                        "Campaign archive failed verification; originals were not replaced. "+failureMessage(failure)),null);
            return reconcileReplacement(path,proposed,technology,storage,archive,originals,failureMessage(failure));
        }
    }
    private static ReplacementResult reconcileReplacement(Path path,Snapshot proposed,
            TechnologyCatalog technology,Storage storage,Path archive,List<Archived> originals,String error){
        Inspection actual=storage.inspect(path,technology);
        if(matches(actual,proposed))
            return new ReplacementResult(result(CommitStatus.COMMITTED,actual,
                    "Archived replacement verified after interrupted acknowledgement."),archive);
        if(actual.status()==AuthorityStatus.VALID)
            return new ReplacementResult(result(CommitStatus.CONFLICT,actual,
                    "Campaign authority changed; archived originals were preserved."),archive);
        try{
            if(actual.status()==AuthorityStatus.INVALID&&originalsMatch(originals))
                return new ReplacementResult(result(CommitStatus.RETRYABLE_FAILURE,actual,
                        "Original campaign files remain unchanged and archived. Retry replacement after restoring storage. "+error),archive);
        }catch(IOException|RuntimeException unavailable){ /* Outcome is still unverified. */ }
        return new ReplacementResult(result(CommitStatus.OUTCOME_UNKNOWN,actual,
                "Replacement outcome could not be verified. Preserve the archive and retry. "+error),archive);
    }
    private static boolean originalsMatch(List<Archived> originals)throws IOException{
        for(Archived item:originals){
            if(item.copy()==null){
                if(!knownMissing(item.original()))return false;
            }else if(!same(item.fingerprint(),fingerprint(item.original()))||
                    !same(item.fingerprint(),fingerprint(item.copy())))return false;
        }
        return true;
    }
    private static Fingerprint fingerprint(Path file)throws IOException{
        if(!Files.readAttributes(file,BasicFileAttributes.class).isRegularFile())
            throw new IOException("Campaign archive source is not a regular file");
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 required",impossible);}
        long size=0;
        try(InputStream input=Files.newInputStream(file)){
            byte[] buffer=new byte[8192];int length;
            while((length=input.read(buffer))!=-1){digest.update(buffer,0,length);size+=length;}
        }
        return new Fingerprint(size,digest.digest());
    }
    private static boolean same(Fingerprint first,Fingerprint second){
        return first.size()==second.size()&&MessageDigest.isEqual(first.digest(),second.digest());
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
