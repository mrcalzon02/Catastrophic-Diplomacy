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
        // Tier IX must be paid, mission-earned and serialized as a real module
        // upgrade. Neither the inclusive skirmish cap nor a save import grants it.
        var mature=new CampaignProgress.State(Battle.Faction.TERRAN,426,
                1_000_000_000L,425,9,425,0,CampaignProgress.Project.NONE,0);
        java.util.Set<String> earlier=new java.util.HashSet<>();
        for(int level=1;level<=8;level++)
            earlier.add("TECH-TER-LAS-OUTPUT-"+String.format(java.util.Locale.ROOT,"%02d",level));
        var matureDiscoveries=new CampaignTechnology.State(Battle.Faction.TERRAN,
                earlier,null,0,0);
        String apex="TECH-TER-LAS-OUTPUT-09";
        check(matureDiscoveries.quote(apex,mature,catalog).allowed(),
                "Legitimate Tier IX module unavailable after eight researched predecessors");
        var apexProject=matureDiscoveries.begin(apex,mature,catalog);
        check(apexProject.duration()==9&&apexProject.remaining()==9,
                "Unaccelerated Tier IX research was not nine successful missions");
        for(int n=0;n<9;n++)apexProject=apexProject.victory();
        check(apexProject.has(apex),"Tier IX research completion did not unlock the module");
        check(CampaignTechnology.decode(CampaignTechnology.encode(apexProject),catalog)
                .equals(apexProject),"Tier IX unlock did not survive the technology codec");
        final int apexSlot=laserSlot;
        Shipyard.Design apexShip=ship.stage(apexSlot,Shipyard.Track.OUTPUT,9);
        CampaignShipyardRules.requireAllowed(apexShip,mature,apexProject,catalog);
        reject(()->CampaignShipyardRules.requireAllowed(apexShip,mature,
                matureDiscoveries,catalog),"Unresearched Tier IX was installed in campaign");

        // The 48 new survival lineages are permanently researched, not
        // bestowed for free by a high license or a skirmish ruleset.
        java.util.Set<String> variantAncestors=new java.util.HashSet<>();
        for(int rank=1;rank<=8;rank++)
            variantAncestors.add("TECH-TER-EPDA-SPECIALIST-"+
                    String.format(java.util.Locale.ROOT,"%02d",rank));
        var variantDiscoveries=new CampaignTechnology.State(Battle.Faction.TERRAN,
                variantAncestors,null,0,0);
        String rescueApex="TECH-TER-EPDA-SPECIALIST-09";
        check(variantDiscoveries.quote(rescueApex,mature,catalog).allowed(),
                "Tech IX rescue variant cannot begin after all eight prerequisites");
        var rescueProject=variantDiscoveries.begin(rescueApex,mature,catalog);
        for(int mission=0;mission<9;mission++)rescueProject=rescueProject.victory();
        check(rescueProject.has(rescueApex) &&
                CampaignTechnology.decode(CampaignTechnology.encode(rescueProject),catalog)
                        .equals(rescueProject),
                "Purchased variant research did not persist through campaign codec");
        var rescueHull=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int survivalSocket=-1;
        for(Shipyard.Slot slot:rescueHull.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                survivalSocket=slot.index();break;
            }
        check(survivalSocket>=0,"Survival research test needs a physical support mount");
        var rescueDesign=rescueHull.install(survivalSocket,"BASE-TER-EPDA")
                .stage(survivalSocket,Shipyard.Track.SPECIALIST,9);
        check(Shipyard.inspect(rescueDesign,catalog).valid(),
                "Purchased variant could not be fitted on a real hull");
        CampaignShipyardRules.requireAllowed(rescueDesign,mature,rescueProject,catalog);
        reject(()->CampaignShipyardRules.requireAllowed(rescueDesign,mature,
                variantDiscoveries,catalog),
                "Research license alone unlocked an unpurchased survival variant");
        // NPC research must pass the same native-faction inventory gate as the
        // player. This proves the shared authority path; actual enemy fleet
        // research progression is a separate, still-open integration task.
        var npcWallet=new CampaignProgress.State(Battle.Faction.DRAXIAN,2,
                100_000,1,2,1,0,CampaignProgress.Project.NONE,0);
        var npcUnresearched=new CampaignTechnology.State(Battle.Faction.DRAXIAN);
        var npcBase=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);
        int npcLaserSlot=-1;
        for(int slot=0;slot<npcBase.slots();slot++)
            if(npcBase.at(slot)!=null &&
                    "BASE-DRA-LAS".equals(npcBase.at(slot).component)){
                npcLaserSlot=slot;break;
            }
        check(npcLaserSlot>=0,"NPC faction lacks its native laser socket");
        var npcUpgraded=npcBase.stage(npcLaserSlot,Shipyard.Track.OUTPUT,1);
        check(Shipyard.inspect(npcUpgraded,catalog).valid(),
                "NPC native technology is physically unbuildable");
        CampaignShipyardRules.requireAllowed(npcBase,npcWallet,npcUnresearched,catalog);
        reject(()->CampaignShipyardRules.requireAllowed(
                npcUpgraded,npcWallet,npcUnresearched,catalog),
                "NPC received unresearched stage I hardware for free");
        check(!npcUnresearched.quote("TECH-TER-LAS-OUTPUT-01",npcWallet,catalog).allowed(),
                "NPC authorized another faction's similarly numbered technology");

        String npcNativeId="TECH-DRA-LAS-OUTPUT-01";
        var npcQuote=npcUnresearched.quote(npcNativeId,npcWallet,catalog);
        check(npcQuote.allowed()&&npcQuote.cost()>0,
                "NPC cannot fund its legitimate native technology project");
        var npcSpent=npcWallet.spend(npcQuote.cost());
        var npcActive=npcUnresearched.begin(npcNativeId,npcWallet,catalog);
        reject(()->CampaignShipyardRules.requireAllowed(
                npcUpgraded,npcSpent,npcActive,catalog),
                "NPC equipped technology before completing paid research");
        var npcEarned=npcActive.victory();
        check(npcEarned.has(npcNativeId)&&npcEarned.activeId()==null,
                "NPC successful mission failed to complete its native research");
        var npcRestored=CampaignTechnology.decode(
                CampaignTechnology.encode(npcEarned),catalog);
        CampaignShipyardRules.requireAllowed(npcUpgraded,npcSpent,npcRestored,catalog);
        reject(()->CampaignShipyardRules.requireAllowed(
                enhanced,npcSpent,npcRestored,catalog),
                "NPC researched inventory authorized a foreign faction hull");
        check(npcSpent.credits()==npcWallet.credits()-npcQuote.cost(),
                "NPC research did not debit its own wallet exactly once");
        System.out.println("PASS campaign technology: faction ownership, prerequisites, debit, victories, authorization, NPC native research gate, atomic ledger");
    }
}
