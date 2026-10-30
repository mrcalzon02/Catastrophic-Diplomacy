package diplomacy.sim;

import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** End-to-end no-GPU regression for actual funded discoveries and persisted ownership. */
public final class CampaignTechnologyTest {
    private static void check(boolean truth,String message){
        if(!truth)throw new AssertionError(message);
    }
    private static void reject(Runnable action,String reason){
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        CampaignProgress.State wallet=new CampaignProgress.State(
                Battle.Faction.TERRAN,2,100_000,1,2,1,0,CampaignProgress.Project.NONE,0);
        CampaignTechnology.State empty=new CampaignTechnology.State(wallet.faction());
        String first="TECH-TER-LAS-OUTPUT-01";
        String next="TECH-TER-LAS-OUTPUT-02";
        String rival="TECH-DRA-LAS-OUTPUT-01";
        check(!empty.has(first)&&empty.activeId()==null,"Fresh campaign received free discoveries");
        check(!empty.quote(rival,wallet,catalog).allowed(),"Other faction research accepted");
        check(!empty.quote(next,wallet,catalog).allowed(),"Second stage bypassed prerequisite");
        check(empty.quote(first,wallet,catalog).allowed(),"First native technology blocked");
        var due=empty.quote(first,wallet,catalog);
        var spending=wallet.spend(due.cost());
        var busy=empty.begin(first,wallet,catalog);
        check(spending.credits()==wallet.credits()-due.cost()&&busy.remaining()==1,
                "Technology research did not debit once or schedule mission work");
        check(!busy.quote(first,spending,catalog).allowed(),"Duplicate pending research accepted");
        var discovered=busy.victory();
        check(discovered.has(first)&&discovered.activeId()==null,"Victory did not award paid discovery");
        check(!discovered.quote(first,spending,catalog).allowed(),"Owned research may be repurchased");
        check(discovered.quote(next,spending,catalog).allowed(),
                "Research stage II did not honor its completed predecessor");
        var second=discovered.begin(next,spending,catalog);
        check(second.remaining()==2&&second.victory().remaining()==1,
                "Rank II project did not preserve per-victory progression");
        var finished=second.victory().victory();
        check(finished.has(next)&&finished.has(first),"Research completion lost its prerequisites");
        check(CampaignTechnology.decode(CampaignTechnology.encode(second),catalog).equals(second),
                "Active technology project did not survive codec round-trip");
        check(CampaignTechnology.decode(CampaignTechnology.encode(finished),catalog).equals(finished),
                "Researched inventory did not survive codec round-trip");
        reject(()->CampaignTechnology.decode(CampaignTechnology.encode(finished).
                        replace("TERRAN","DRAXIAN"),catalog),
                "Tampered faction or checksum accepted");

        Shipyard.Design ship=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int laserSlot=-1;
        for(int i=0;i<ship.slots();i++)
            if(ship.at(i)!=null&&ship.at(i).component.equals("BASE-TER-LAS")){
                laserSlot=i;break;
            }
        check(laserSlot>=0,"Factory prototype lacks the test laser mount");
        Shipyard.Design enhanced=ship.stage(laserSlot,Shipyard.Track.OUTPUT,1);
        reject(()->CampaignShipyardRules.requireAllowed(enhanced,wallet,empty,catalog),
                "Unresearched module was commissioned");
        CampaignShipyardRules.requireAllowed(enhanced,wallet,finished,catalog);
        check(finished.victory().equals(finished),"Idle victory invented another unlock");
        var roster=CampaignFleet.start(Battle.Faction.TERRAN,ship,catalog);
        var saved=new CampaignLedger.Snapshot(1,spending,roster,second);
        var loaded=CampaignLedger.decode(CampaignLedger.encode(saved),catalog);
        check(CampaignLedger.equivalent(saved,loaded)&&loaded.technology().equals(second),
                "Atomic campaign ledger lost paid research or fleet");
        System.out.println("PASS campaign technology: faction ownership, prerequisites, debit, victories, authorization, atomic ledger");
    }
}
