package diplomacy.sim;

import diplomacy.shipyard.CampaignBlueprintCodec;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

public final class CampaignBlueprintTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void reject(Runnable action,String message){
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(message);
    }
    public static void main(String[] args)throws Exception{
        var technology=TechnologyCatalog.loadDefault();
        var original=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var wire=CampaignBlueprintCodec.encode(original,technology);
        var recovered=CampaignBlueprintCodec.decode(wire,technology,Battle.Faction.TERRAN);
        check(Shipyard.encode(recovered).equals(Shipyard.encode(original)),
                "Approved purchase blueprint changed between game sessions");
        CampaignShipyardRules.requireAllowed(recovered,Battle.Faction.TERRAN,0,1);
        var second=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE_SCOUT);
        check(!Shipyard.encode(second).equals(Shipyard.encode(original)),
                "Distinct chassis blueprint was silently collapsed");
        check(CampaignBlueprintCodec.decode(CampaignBlueprintCodec.encode(second,technology),
                technology,Battle.Faction.TERRAN).hull==second.hull,
                "Same-size sub-chassis was not preserved");
        reject(()->CampaignBlueprintCodec.decode(wire,technology,Battle.Faction.DRAXIAN),
                "Different faction reused the paid campaign's engineering plan");
        reject(()->CampaignBlueprintCodec.decode(
                wire.replace("CD-CAMPAIGN-BLUEPRINT-1","CD-CAMPAIGN-BLUEPRINT-0"),
                technology,Battle.Faction.TERRAN),
                "Unsupported blueprint version accepted");
        reject(()->CampaignBlueprintCodec.decode(
                wire.replace("CORVETTE","DREADNOUGHT"),
                technology,Battle.Faction.TERRAN),
                "Tampered hull class bypassed blueprint checksum");
        reject(()->CampaignBlueprintCodec.decode(wire.substring(0,wire.length()-2),
                technology,Battle.Faction.TERRAN),"Truncated transaction accepted");
        var late=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        var lateWire=CampaignBlueprintCodec.encode(late,technology);
        var restored=CampaignBlueprintCodec.decode(lateWire,technology,Battle.Faction.TERRAN);
        reject(()->CampaignShipyardRules.requireAllowed(restored,Battle.Faction.TERRAN,0,1),
                "Blueprint from another campaign bypassed new-campaign hull milestones");
        CampaignShipyardRules.requireAllowed(restored,Battle.Faction.TERRAN,6,500);
        check(wire.equals(CampaignBlueprintCodec.encode(recovered,technology)),
                "Blueprint persistence was not deterministic");
        System.out.println("PASS persistent campaign purchase blueprint: checksum, sub-chassis, faction, milestone, restart and corruption");
    }
}
