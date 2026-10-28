package diplomacy.client;

import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignProgress;
import diplomacy.tech.TechnologyCatalog;

/** Read-only campaign budget preview. Ledger methods remain the only writers. */
final class CampaignBudgetQuotes {
    private CampaignBudgetQuotes(){}
    record Quote(long cost,long wallet,long remaining,boolean allowed,String reason){
        Quote{
            if(cost<0||wallet<0)throw new IllegalArgumentException("Negative campaign quote");
        }
    }
    private static Quote quote(long wallet,long price,String blocker){
        String problem=blocker;
        if(problem==null&&price>wallet)problem="INSUFFICIENT CREDITS";
        return new Quote(price,wallet,wallet-price,problem==null,
                problem==null?"READY TO AUTHORIZE":problem);
    }
    static Quote research(CampaignProgress.State campaign,CampaignProgress.Project project){
        if(campaign==null||project==null||project==CampaignProgress.Project.NONE)
            throw new IllegalArgumentException("Choose a campaign research project");
        boolean license=project==CampaignProgress.Project.LICENSE;
        long cost=license?campaign.nextResearchCost():campaign.nextAccelerationCost();
        String blocker=null;
        if(campaign.activeProject()!=CampaignProgress.Project.NONE)
            blocker="COMPLETE EXISTING PROJECT FIRST";
        else if(license&&campaign.researchRank()==6)
            blocker="ALL SIX LICENSES COMPLETED";
        else if(!license&&campaign.speedUpgrades()==4)
            blocker="ALL FOUR LAB UPGRADES COMPLETED";
        else if(!license&&!campaign.completed(campaign.nextAccelerationMilestone()))
            blocker="COMPLETE MISSION "+campaign.nextAccelerationMilestone()+" FIRST";
        return quote(campaign.credits(),cost,blocker);
    }
    static Quote refit(CampaignProgress.State campaign,CampaignFleet.Roster roster,
                       long shipId,Shipyard.Design design,TechnologyCatalog technology){
        if(campaign==null||roster==null||design==null||technology==null)
            throw new IllegalArgumentException("Refit needs a campaign, roster and design");
        try{
            CampaignShipyardRules.requireAllowed(design,campaign.faction(),
                    campaign.researchRank(),campaign.unlocked());
            return quote(campaign.credits(),roster.refitCost(shipId,design,technology),null);
        }catch(IllegalArgumentException blocked){
            return quote(campaign.credits(),0,blocked.getMessage());
        }
    }
    static Quote hull(CampaignProgress.State campaign,Shipyard.Design design,
                      TechnologyCatalog technology){
        if(campaign==null||design==null||technology==null)
            throw new IllegalArgumentException("Campaign fitting and technology required");
        try{
            CampaignShipyardRules.requireAllowed(design,campaign.faction(),
                    campaign.researchRank(),campaign.unlocked());
            long cost=CampaignFleet.purchaseCost(design,technology);
            return quote(campaign.credits(),cost,null);
        }catch(IllegalArgumentException blocked){
            return quote(campaign.credits(),0,blocked.getMessage());
        }
    }
}
