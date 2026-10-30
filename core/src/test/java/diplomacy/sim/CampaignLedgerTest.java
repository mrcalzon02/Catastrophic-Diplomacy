package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CampaignLedgerTest {
    private static void check(boolean condition,String message){
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
                            .replace("CD-CAMPAIGN-LEDGER-2","CD-CAMPAIGN-LEDGER-3"),tech),
                    "Unknown authority format accepted");
            check(Files.exists(directory.resolve("campaign.dat.bak")),
                    "Last-generation recovery record not retained");
            System.out.println("PASS atomic campaign ledger: purchase/research consistency, recovery, stale writes and corruption protection");
        }finally{
            try(var paths=Files.list(directory)){
                for(Path f:paths.toList())Files.deleteIfExists(f);
            }
            Files.deleteIfExists(directory);
        }
    }
}
