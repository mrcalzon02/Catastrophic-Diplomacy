package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;

public final class CampaignAfterActionTest {
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    private static void reject(Runnable work,String reason){
        try{work.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        var tech=TechnologyCatalog.loadDefault();
        var original=CampaignFleet.start(Battle.Faction.TERRAN,
                Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),tech);
        var ids=original.active().stream().map(CampaignFleet.Vessel::id).toList();
        Battle battle=new Battle(1977);
        for(var v:original.active())battle.spawnWing(0,v.fitting(),tech,1,310,335);
        for(int i=0;i<ids.size();i++){
            original.active().get(i).stores().apply(battle.ships().get(i));
            battle.ships().get(i).commissionedId=ids.get(i);
        }
        var v=battle.ships().get(0);
        v.hull=v.maxHull*0.5;
        v.crewCount=Math.max(0,v.crewCount-3);
        v.missileAmmo=Math.max(0,v.missileAmmo-7);
        v.fuel=Math.max(0,v.fuel-24);
        battle.ships().get(1).hull=0;
        battle.ships().get(2).escaped=true;
        battle.ships().get(3).withdrawing=true;
        var report=CampaignAfterAction.capture(original,ids,battle);
        check(report.entries().size()==4 &&
                report.count(CampaignAfterAction.Outcome.SURVIVED)==1 &&
                report.count(CampaignAfterAction.Outcome.DESTROYED)==1 &&
                report.count(CampaignAfterAction.Outcome.ESCAPED)==1 &&
                report.count(CampaignAfterAction.Outcome.WITHDRAWING)==1,
                "Commissioned vessel loss outcomes are incorrect");
        var first=report.entries().get(0);
        check(first.id()==ids.get(0)&&first.name().equals(original.get(ids.get(0)).name())&&
                first.hullPercent()==50,"Stable ship identity/hull integrity changed");
        check(first.crewLost()==3&&first.missileSpent()==7&&first.fuelSpent()==24,
                "Battle expenses and personnel losses were not measured");
        // Regression: an uncommissioned tactical hull (ID 0) or a swapped
        // commissioned ID must never inherit another ship's losses or stores.
        long firstId=ids.get(0);
        battle.ships().get(0).commissionedId=0;
        reject(()->CampaignAfterAction.capture(original,ids,battle),
                "Uncommissioned hull was attributed to a campaign captain");
        reject(()->original.afterBattle(battle,ids),
                "Uncommissioned hull was accepted for permanent fleet recovery");
        battle.ships().get(0).commissionedId=ids.get(1);
        reject(()->CampaignAfterAction.capture(original,ids,battle),
                "Swapped 16-digit hull ID was accepted by after-action reporting");
        reject(()->original.afterBattle(battle,ids),
                "Swapped 16-digit hull ID was accepted by fleet recovery");
        battle.ships().get(0).commissionedId=firstId;
        var recovered=original.afterBattle(battle,ids);
        check(recovered.get(firstId).id()==firstId&&
                recovered.get(ids.get(2)).id()==ids.get(2)&&
                Long.toString(recovered.get(firstId).id()).length()==16,
                "Campaign recovery changed or truncated a surviving ship ID");
        check(recovered.ships().size()==3&&report.entries().get(1).outcome()==
                CampaignAfterAction.Outcome.DESTROYED,
                "Destroyed commissioned vessel disappeared from casualty ledger");
        check(recovered.get(ids.get(2)).name().equals(report.entries().get(2).name()),
                "Escaped named vessel did not match persisted reserve identity");
        var many=new ArrayList<CampaignAfterAction.Entry>();
        for(int i=0;i<13;i++)many.add(new CampaignAfterAction.Entry(VesselIdentifiers.commissioned(19,i+1),
                "Ship "+(i+1),Shipyard.Hull.CORVETTE,
                CampaignAfterAction.Outcome.SURVIVED,100,0,0,0,0,0));
        var pages=new CampaignAfterAction.Report(many);
        check(pages.pages(5)==3&&pages.page(2,5).size()==3,
                "Large sortie report pagination skipped vessels");
        reject(()->pages.page(3,5),"Off-page selection accepted");
        reject(()->CampaignAfterAction.capture(original,List.of(ids.get(0)),battle),
                "Mismatched sortie IDs fabricated casualties");
        System.out.println("PASS named after-action ledger: casualty ships, returned hulls, stores, 13-ship pagination");
    }
}
