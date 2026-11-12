package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public final class CampaignLedgerTest {
    private static int assertions;
    private static void check(boolean condition,String message){
        assertions++;
        if(!condition)throw new AssertionError(message);
    }
    private static void reject(Runnable attempt,String message){
        try{attempt.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        var tech=TechnologyCatalog.loadDefault();
        Path directory=Files.createTempDirectory("cd-atomic-ledger-test-");
        try{
            Path path=directory.resolve("campaign.dat");
            check(CampaignLedger.load(path,tech).isEmpty(),"Fresh campaign had hidden authority");
            var state=new CampaignProgress.State(Battle.Faction.TERRAN,1,1_000_000,0);
            var fleet=CampaignFleet.start(Battle.Faction.TERRAN,
                    Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),tech);
            var initial=new CampaignLedger.Snapshot(1,state,fleet);
            CampaignLedger.commit(path,initial,tech);
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),initial),
                    "First commissioned campaign did not persist");
            var blueprint=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE_SCOUT);
            long quote=CampaignFleet.purchaseCost(blueprint,tech);
            var bought=initial.next(state.spend(quote),fleet.purchase(blueprint,tech));
            CampaignLedger.commit(path,bought,tech);
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),bought),
                    "New vessel and debit did not persist as one transaction");
            check(bought.fleet().ships().size()==fleet.ships().size()+1 &&
                    bought.progress().credits()==state.credits()-quote,
                    "Paid purchase issued free hull or lost credits");
            reject(()->{
                try{CampaignLedger.commit(path,bought,tech);}
                catch(java.io.IOException e){throw new IllegalStateException(e);}
            },"Duplicate purchase transaction applied twice");
            // An interrupted temp write is explicitly uncommitted; previous snapshot wins.
            Files.writeString(path.resolveSibling("campaign.dat.tmp"),"partial transaction");
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),bought),
                    "Uncommitted temporary save was treated as accepted");
            var research=bought.next(bought.progress().purchaseNextResearch(),bought.fleet());
            CampaignLedger.commit(path,research,tech);
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),research),
                    "Research debit and fleet state did not persist together");
            // A too-large authority is rejected before reading it into memory;
            // recovery still uses the entire previous valid generation.
            Files.write(path,new byte[4_500_001]);
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),bought),
                    "Oversized primary did not recover the valid backup generation");
            // A damaged current file falls back to the *entire* prior snapshot,
            // never a mismatched wallet/roster pair or free ship delivery.
            Files.writeString(path,"damaged primary save");
            check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),bought),
                    "Corrupt primary recovered credits from a different fleet generation");
            Files.writeString(path.resolveSibling("campaign.dat.bak"),"also damaged");
            reject(()->CampaignLedger.load(path,tech),
                    "Two corrupt authoritative copies must never reimport stale saves");
            reject(()->new CampaignLedger.Snapshot(1,state,
                    CampaignFleet.start(Battle.Faction.DRAXIAN,
                            Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE),tech)),
                    "Mixing campaigns across factions accepted");
            reject(()->CampaignLedger.decode(CampaignLedger.encode(initial)
                            .replace("CD-CAMPAIGN-LEDGER-2","CD-CAMPAIGN-LEDGER-99"),tech),
                    "Unknown authority format accepted");
            // Ledger v3 carries the exact per-physical-bay fighter complement
            // atomically with wallet/fleet/research while v1/v2 remain readable.
            Shipyard.Design carrier=Shipyard.starter(
                    Battle.Faction.TERRAN,Shipyard.Hull.CARRIER);
            CampaignFleet.Roster wingFleet=CampaignFleet.startSolo(
                    Battle.Faction.TERRAN,carrier,tech,771122L);
            long wingId=wingFleet.ships().get(0).id();
            int paid=wingFleet.get(wingId).stores().fighters();
            FlightWingManifest legacyWings=FlightWingManifest.legacy(carrier,tech,paid);
            java.util.ArrayList<FlightWingManifest.FlightWing> mixed=
                    new java.util.ArrayList<>(legacyWings.wings());
            if(!mixed.isEmpty()){
                var first=mixed.get(0);
                mixed.set(0,new FlightWingManifest.FlightWing(first.bayId(),
                        FighterModel.Hull.TORPEDO_BOMBER,first.stored(),first.ready(),
                        first.launched(),first.returning(),first.servicing(),first.destroyed()));
            }
            FlightWingManifest manifest=FlightWingManifest.of(carrier,tech,paid,mixed);
            var wingSnapshot=new CampaignLedger.Snapshot(1,
                    CampaignProgress.start(Battle.Faction.TERRAN),wingFleet,
                    new CampaignTechnology.State(Battle.Faction.TERRAN),
                    java.util.Map.of(wingId,manifest));
            String wingEncoded=CampaignLedger.encode(wingSnapshot);
            check(wingEncoded.startsWith("CD-CAMPAIGN-LEDGER-3\n"),
                    "Explicit wing authority did not select ledger v3");
            var wingRestored=CampaignLedger.decode(wingEncoded,tech);
            check(wingRestored.flightWings().get(wingId).wings().equals(manifest.wings()),
                    "Ledger v3 lost mixed fighter bay identity");
            check(CampaignLedger.equivalent(wingRestored,wingSnapshot),
                    "Ledger v3 wing authority did not round-trip canonically");
            check(CampaignLedger.decode(CampaignLedger.encode(initial),tech)
                            .flightWings().isEmpty(),
                    "Ledger v2 migration fabricated explicit wing state before game migration");

            check(Files.exists(directory.resolve("campaign.dat.bak")),
                    "Last-generation recovery record not retained");
            recoverableTransactions(directory,initial,bought,tech);
            explicitRecovery(directory,tech);
            System.out.println("PASS atomic campaign ledger: strict compatibility, exactly-once retry, lost acknowledgement, "+
                    "readback recovery, verified corrupt-save archive and inaccessible-storage protection ("+assertions+" checks)");
        }finally{
            try(var paths=Files.walk(directory)){
                for(Path f:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);
            }
        }
    }
    private static void authority(Path path,CampaignLedger.Snapshot expected,TechnologyCatalog tech,
                                  String message){
        check(CampaignLedger.equivalent(CampaignLedger.load(path,tech).orElseThrow(),expected),message);
    }
    private static Path backup(Path path){return path.resolveSibling(path.getFileName()+".bak");}
    private static void recoverableTransactions(Path directory,CampaignLedger.Snapshot initial,
            CampaignLedger.Snapshot bought,TechnologyCatalog tech)throws Exception{
        Path fresh=directory.resolve("recoverable-first.dat");
        var first=CampaignLedger.commitRecoverably(fresh,null,initial,tech);
        check(first.status()==CampaignLedger.CommitStatus.COMMITTED&&first.committed(),
                "Recoverable first generation was not committed");
        check(CampaignLedger.commitRecoverably(fresh,null,initial,tech).status()==
                CampaignLedger.CommitStatus.ALREADY_COMMITTED,"First-generation lost acknowledgement duplicated a save");

        Path retry=directory.resolve("retry.dat");
        CampaignLedger.commit(retry,initial,tech);
        Path blocked=retry.resolveSibling(retry.getFileName()+".tmp");
        Files.createDirectory(blocked);
        var failed=CampaignLedger.commitRecoverably(retry,initial,bought,tech);
        check(failed.status()==CampaignLedger.CommitStatus.RETRYABLE_FAILURE&&!failed.committed(),
                "Known unchanged write failure did not permit retry");
        authority(retry,initial,tech,"Failed write changed wallet or roster");
        Files.delete(blocked);
        check(CampaignLedger.commitRecoverably(retry,initial,bought,tech).committed(),
                "Same immutable transaction could not be retried after storage recovery");
        authority(retry,bought,tech,"Retried purchase did not restore its exact debit/owned fleet");
        byte[] previous=Files.readAllBytes(backup(retry));
        Files.createDirectory(blocked); // Already-committed recovery must not perform another write.
        check(CampaignLedger.commitRecoverably(retry,initial,bought,tech).status()==
                CampaignLedger.CommitStatus.ALREADY_COMMITTED,"Duplicate retry was not acknowledged");
        check(Arrays.equals(previous,Files.readAllBytes(backup(retry))),
                "Duplicate retry rewrote the previous-generation recovery copy");
        Files.delete(blocked);
        authority(retry,bought,tech,"Duplicate retry advanced the generation or paid twice");

        Path conflict=directory.resolve("conflict.dat");
        var another=new CampaignLedger.Snapshot(initial.generation(),initial.progress().spend(1),
                initial.fleet(),initial.technology());
        CampaignLedger.commit(conflict,another,tech);
        byte[] unchanged=Files.readAllBytes(conflict);
        var stale=CampaignLedger.commitRecoverably(conflict,initial,bought,tech);
        check(stale.status()==CampaignLedger.CommitStatus.CONFLICT&&!stale.committed(),
                "Same generation with a different wallet was treated as the expected before state");
        check(Arrays.equals(unchanged,Files.readAllBytes(conflict)),"Conflicting authority was overwritten");
        check(CampaignLedger.equivalent(stale.authority(),another),"Conflict did not identify readable authority");
        var anotherNext=new CampaignLedger.Snapshot(bought.generation(),bought.progress().spend(1),
                bought.fleet(),bought.technology());
        check(CampaignLedger.commitRecoverably(retry,initial,anotherNext,tech).status()==
                CampaignLedger.CommitStatus.CONFLICT,"Same proposed generation with different content was acknowledged");
        authority(retry,bought,tech,"Conflicting proposed snapshot rewrote a saved purchase");

        Path lost=directory.resolve("lost-ack.dat");
        CampaignLedger.commit(lost,initial,tech);
        var afterWriteFailure=new CampaignLedger.Storage(){
            @Override void write(Path path,String content)throws IOException{
                super.write(path,content);
                if(path.equals(lost))throw new IOException("Injected acknowledgement loss after atomic write");
            }
        };
        var recovered=CampaignLedger.commitRecoverably(lost,initial,bought,tech,afterWriteFailure);
        check(recovered.committed(),"Successful atomic write was lost when acknowledgement failed");
        authority(lost,bought,tech,"Lost acknowledgement duplicated or dropped the purchase");

        Path readback=directory.resolve("readback.dat");
        CampaignLedger.commit(readback,initial,tech);
        var transientReadFailure=new CampaignLedger.Storage(){
            boolean wrote,failed;
            @Override void write(Path path,String content)throws IOException{
                super.write(path,content);if(path.equals(readback))wrote=true;
            }
            @Override CampaignLedger.Inspection inspect(Path path,TechnologyCatalog catalog){
                if(wrote&&!failed){failed=true;return new CampaignLedger.Inspection(
                        CampaignLedger.AuthorityStatus.UNAVAILABLE,null,"Injected transient readback error");}
                return super.inspect(path,catalog);
            }
        };
        check(CampaignLedger.commitRecoverably(readback,initial,bought,tech,transientReadFailure).committed(),
                "A transient readback failure did not reconcile the exact written proposal");
        authority(readback,bought,tech,"Readback reconciliation changed transaction identity");

        Path uncertain=directory.resolve("uncertain.dat");
        CampaignLedger.commit(uncertain,initial,tech);
        var inaccessibleAfterWrite=new CampaignLedger.Storage(){
            boolean wrote;
            @Override void write(Path path,String content)throws IOException{
                super.write(path,content);if(path.equals(uncertain))wrote=true;
            }
            @Override CampaignLedger.Inspection inspect(Path path,TechnologyCatalog catalog){
                return wrote?new CampaignLedger.Inspection(CampaignLedger.AuthorityStatus.UNAVAILABLE,
                        null,"Injected unavailable authority after write"):super.inspect(path,catalog);
            }
        };
        var unknown=CampaignLedger.commitRecoverably(uncertain,initial,bought,tech,inaccessibleAfterWrite);
        check(unknown.status()==CampaignLedger.CommitStatus.OUTCOME_UNKNOWN&&!unknown.committed(),
                "Unverifiable write outcome claimed success or a known-unchanged state");
        check(CampaignLedger.commitRecoverably(uncertain,initial,bought,tech).status()==
                CampaignLedger.CommitStatus.ALREADY_COMMITTED,"Unknown outcome could not reconcile after access returned");
        authority(uncertain,bought,tech,"Unknown outcome retry duplicated a transaction");

        Path invalid=directory.resolve("invalid-retry.dat");
        Files.writeString(invalid,"damaged");Files.writeString(backup(invalid),"also damaged");
        check(CampaignLedger.commitRecoverably(invalid,initial,bought,tech).status()==
                CampaignLedger.CommitStatus.INVALID_AUTHORITY,"Retry silently reset damaged authorities");
        check(Files.readString(invalid).equals("damaged"),"Retry replaced corrupt authority without explicit reset");
    }
    private static void explicitRecovery(Path directory,TechnologyCatalog tech)throws Exception{
        var fresh=new CampaignLedger.Snapshot(1,CampaignProgress.start(Battle.Faction.TERRAN),
                CampaignFleet.startSolo(Battle.Faction.TERRAN,
                        Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),tech,9911));
        byte[] damaged={(byte)0xff,0,13,10,(byte)0x80,42};
        byte[] previous={7,6,5,4,3,2,1};
        Path path=directory.resolve("reset.dat");
        Files.write(path,damaged);Files.write(backup(path),previous);
        var replacement=CampaignLedger.archiveAndReplaceInvalid(path,fresh,tech);
        check(replacement.commit().committed()&&replacement.archive()!=null,
                "Explicit damaged-authority replacement did not produce a verified archive");
        check(replacement.archive().getFileName().toString().startsWith("reset.dat.recovery-"),
                "Recovery archive was not uniquely named beside the authority");
        check(Arrays.equals(damaged,Files.readAllBytes(replacement.archive().resolve("reset.dat"))),
                "Primary was text-normalized instead of archived byte-for-byte");
        check(Arrays.equals(previous,Files.readAllBytes(replacement.archive().resolve("reset.dat.bak"))),
                "Backup archive did not retain exact original bytes");
        check(Arrays.equals(previous,Files.readAllBytes(backup(path))),
                "Reset removed the old backup before a normal generation could replace it");
        authority(path,fresh,tech,"Explicit reset did not persist generation 1 and its exact starter state");
        check(CampaignLedger.archiveAndReplaceInvalid(path,fresh,tech).commit().status()==
                CampaignLedger.CommitStatus.ALREADY_COMMITTED,"Reset retry lost acknowledgement and reset again");
        Files.write(path,damaged);Files.write(backup(path),previous);
        var repeated=CampaignLedger.archiveAndReplaceInvalid(path,fresh,tech);
        check(repeated.commit().committed()&&!replacement.archive().equals(repeated.archive()),
                "A second deliberate recovery reused/overwrote the first archive");
        check(Arrays.equals(damaged,Files.readAllBytes(replacement.archive().resolve("reset.dat"))),
                "A later explicit reset changed the original recovery archive");

        Path onlyBackup=directory.resolve("backup-only.dat");
        Files.write(backup(onlyBackup),previous);
        var backupReset=CampaignLedger.archiveAndReplaceInvalid(onlyBackup,fresh,tech);
        check(backupReset.commit().committed()&&
                Arrays.equals(previous,Files.readAllBytes(backupReset.archive().resolve("backup-only.dat.bak"))),
                "Missing primary plus damaged backup was not recoverable with exact archive");
        check(!Files.exists(backupReset.archive().resolve("backup-only.dat")),
                "Recovery invented bytes for a previously absent primary");

        Path oversized=directory.resolve("oversized-reset.dat");
        byte[] huge=new byte[4_500_001];huge[0]=55;huge[huge.length-1]=99;
        Files.write(oversized,huge);
        var largeReset=CampaignLedger.archiveAndReplaceInvalid(oversized,fresh,tech);
        check(largeReset.commit().committed()&&Arrays.equals(huge,
                Files.readAllBytes(largeReset.archive().resolve("oversized-reset.dat"))),
                "Oversized invalid authority was truncated or not archived");
        check(!Files.exists(largeReset.archive().resolve("oversized-reset.dat.bak")),
                "Recovery invented a previously absent backup");

        Path validBackup=directory.resolve("valid-backup.dat");
        Files.writeString(validBackup,"bad primary");
        var valid=new CampaignLedger.Snapshot(2,fresh.progress(),fresh.fleet(),fresh.technology());
        Files.writeString(backup(validBackup),CampaignLedger.encode(valid));
        check(CampaignLedger.inspect(validBackup,tech).status()==CampaignLedger.AuthorityStatus.VALID,
                "Valid backup did not recover a corrupt primary");
        check(CampaignLedger.archiveAndReplaceInvalid(validBackup,fresh,tech).commit().status()==
                CampaignLedger.CommitStatus.CONFLICT,"Reset overwrote an existing valid backup authority");
        authority(validBackup,valid,tech,"Attempted reset destroyed a recoverable generation");
        check(Files.readString(validBackup).equals("bad primary"),"Rejected reset changed the damaged primary");
        Path absent=directory.resolve("never-saved.dat");
        check(CampaignLedger.inspect(absent,tech).status()==CampaignLedger.AuthorityStatus.MISSING,
                "Absent authority was confused with damaged data");
        check(CampaignLedger.archiveAndReplaceInvalid(absent,fresh,tech).commit().status()==
                CampaignLedger.CommitStatus.CONFLICT&&!Files.exists(absent),
                "Archive/reset silently created a campaign when no damaged authority existed");

        Path nonregular=directory.resolve("nonregular.dat");
        Files.createDirectory(nonregular);
        Files.writeString(backup(nonregular),CampaignLedger.encode(fresh));
        check(CampaignLedger.inspect(nonregular,tech).status()==CampaignLedger.AuthorityStatus.UNAVAILABLE,
                "An unreadable primary was mistaken for corruption despite a potentially stale backup");
        check(CampaignLedger.archiveAndReplaceInvalid(nonregular,fresh,tech).commit().status()==
                CampaignLedger.CommitStatus.STORAGE_UNAVAILABLE&&Files.isDirectory(nonregular),
                "Reset replaced an inaccessible/nonregular primary");
        Path blockedParent=directory.resolve("not-a-directory");Files.writeString(blockedParent,"untouched");
        check(CampaignLedger.inspect(blockedParent.resolve("campaign.dat"),tech).status()==
                CampaignLedger.AuthorityStatus.UNAVAILABLE,"Inaccessible ancestor was treated as no existing campaign");

        Path badArchive=directory.resolve("bad-archive.dat");
        Files.write(badArchive,damaged);Files.write(backup(badArchive),previous);
        var corruptArchive=new CampaignLedger.Storage(){
            @Override void archive(Path source,Path destination)throws IOException{
                super.archive(source,destination);Files.write(destination,new byte[]{42});
            }
        };
        var refused=CampaignLedger.archiveAndReplaceInvalid(badArchive,fresh,tech,corruptArchive);
        check(!refused.commit().committed()&&refused.archive()==null,
                "An unverified/partial archive was advertised as a safe recovery copy");
        check(Arrays.equals(damaged,Files.readAllBytes(badArchive))&&
                Arrays.equals(previous,Files.readAllBytes(backup(badArchive))),
                "Archive verification failure replaced original bytes");

        Path changedArchive=directory.resolve("changed-archive.dat");
        Files.write(changedArchive,damaged);Files.write(backup(changedArchive),previous);
        var laterArchiveDamage=new CampaignLedger.Storage(){
            @Override void archive(Path source,Path destination)throws IOException{
                super.archive(source,destination);
                if(source.equals(backup(changedArchive)))
                    Files.write(destination.resolveSibling(changedArchive.getFileName()),new byte[]{3});
            }
        };
        var changed=CampaignLedger.archiveAndReplaceInvalid(changedArchive,fresh,tech,laterArchiveDamage);
        check(!changed.commit().committed()&&changed.archive()==null,
                "An archive damaged before final verification was advertised as safe");
        check(Arrays.equals(damaged,Files.readAllBytes(changedArchive)),
                "Original was replaced after an earlier archived copy changed");

        Path writeFailure=directory.resolve("reset-write-failure.dat");
        Files.write(writeFailure,damaged);Files.write(backup(writeFailure),previous);
        Path blocked=writeFailure.resolveSibling(writeFailure.getFileName()+".tmp");Files.createDirectory(blocked);
        var kept=CampaignLedger.archiveAndReplaceInvalid(writeFailure,fresh,tech);
        check(kept.commit().status()==CampaignLedger.CommitStatus.RETRYABLE_FAILURE&&kept.archive()!=null,
                "Verified archive plus known failed replacement did not retain retry information");
        check(Arrays.equals(damaged,Files.readAllBytes(writeFailure)),"Failed replacement changed its original primary");
        Files.delete(blocked);
        check(CampaignLedger.archiveAndReplaceInvalid(writeFailure,fresh,tech).commit().committed(),
                "Explicit replacement could not be retried after restoring storage");

        Path resetUnknown=directory.resolve("reset-unknown.dat");
        Files.write(resetUnknown,damaged);Files.write(backup(resetUnknown),previous);
        var uncertainReset=new CampaignLedger.Storage(){
            boolean wrote;
            @Override void write(Path target,String content)throws IOException{
                super.write(target,content);if(target.equals(resetUnknown))wrote=true;
            }
            @Override CampaignLedger.Inspection inspect(Path target,TechnologyCatalog catalog){
                return wrote?new CampaignLedger.Inspection(CampaignLedger.AuthorityStatus.UNAVAILABLE,
                        null,"Injected replacement readback error"):super.inspect(target,catalog);
            }
        };
        var unacknowledged=CampaignLedger.archiveAndReplaceInvalid(resetUnknown,fresh,tech,uncertainReset);
        check(unacknowledged.commit().status()==CampaignLedger.CommitStatus.OUTCOME_UNKNOWN&&
                unacknowledged.archive()!=null,"Unknown reset outcome discarded its archive or claimed success");
        check(CampaignLedger.archiveAndReplaceInvalid(resetUnknown,fresh,tech).commit().status()==
                CampaignLedger.CommitStatus.ALREADY_COMMITTED,"Lost reset acknowledgement did not reconcile generation 1");
        authority(resetUnknown,fresh,tech,"Reset reconciliation changed the new campaign identity");
    }

}
