package diplomacy.sim;

import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.zip.CRC32;

/**
 * Every visual size family supports multiple physical chassis, and alternating
 * support/flagship generations have complete role coverage across all factions.
 */
public final class ShipLineagesTest {
    private static void check(boolean good,String detail){
        if(!good)throw new AssertionError(detail);
    }
    private static void rejects(Runnable code,String detail){
        try{code.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(detail);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        check(Shipyard.Hull.values().length>=40,"Expected expanded physical chassis roster");
        int checked=0;
        var invalidFittings=new java.util.ArrayList<String>();
        for(int generation=1;generation<=10;generation++){
            var hulls=ShipLineages.generation(generation);
            var kinds=EnumSet.noneOf(ShipLineages.Role.class);
            var visual=ShipLineages.of(hulls.get(0)).artwork();
            check(hulls.size()>=3,"Missing same-size chassis at generation "+generation);
            for(Shipyard.Hull h:hulls){
                var line=ShipLineages.of(h);
                check(line.generation()==generation&&line.artwork()==visual,
                        "Chassis drifted between artwork size families: "+h);
                check(line.ethos()==(generation%2==0?ShipLineages.Ethos.FLAGSHIP_SQUADRON:
                        ShipLineages.Ethos.SUPPORT_FLEET),"Wrong alternating ethos for "+h);
                kinds.add(line.role());
                for(Battle.Faction faction:Battle.Faction.values()){
                    var fitting=Shipyard.starter(faction,h);
                    var quality=Shipyard.inspect(fitting,catalog);
                    if(!quality.valid()){
                        invalidFittings.add("Unbuildable "+faction+"/"+h+": "+quality.explanation());
                        continue;
                    }
                    var actual=Shipyard.fit(fitting,catalog);
                    check(actual.speed()>0&&actual.hull()>0&&actual.maxPower()>0,
                            "Empty combat parameters for "+faction+"/"+h);
                    var saved=Shipyard.decode(Shipyard.encode(fitting),catalog);
                    check(Shipyard.encode(saved).equals(Shipyard.encode(fitting)),
                            "Chassis blueprint lost data on reload: "+faction+"/"+h);
                    if(h.ordinal()>=11){
                        switch(line.role()){
                            case FLAK_SCREEN -> check(actual.pdRange()>0,
                                    "Screen has no installed point defense: "+h);
                            case TORPEDO_STRIKER -> check(actual.torpedoDamage()>0&&actual.shields()==0,
                                    "Glass-cannon chassis lacks torpedo or mounts free shielding: "+h);
                            case ELECTRONIC_CONTROLLER,COMMAND_HUB ->
                                check(actual.ecmStrength()>0,"Controller has no installed EW: "+h);
                            case REPAIR_TENDER,LOGISTICS_JUGGERNAUT ->
                                check(actual.support().remoteRepairRate()>0,
                                        "Tender has no installed real repair module: "+h);
                            case APEX_VANGUARD -> check(actual.kineticDamage()>0,
                                    "Vanguard lacks physical kinetic gun: "+h);
                        }
                    }
                    checked++;
                }
            }
            if(generation%2==1)check(kinds.containsAll(EnumSet.of(
                    ShipLineages.Role.FLAK_SCREEN,ShipLineages.Role.TORPEDO_STRIKER,
                    ShipLineages.Role.ELECTRONIC_CONTROLLER,ShipLineages.Role.REPAIR_TENDER)),
                    "Incomplete four-role support group at generation "+generation);
            else check(kinds.containsAll(EnumSet.of(
                    ShipLineages.Role.APEX_VANGUARD,ShipLineages.Role.COMMAND_HUB,
                    ShipLineages.Role.LOGISTICS_JUGGERNAUT)),
                    "Incomplete compressed three-role flagship at generation "+generation);
        }
        check(invalidFittings.isEmpty(),
                "Invalid physical role prototypes ("+invalidFittings.size()+"): "+
                        String.join("; ",invalidFittings.subList(0,Math.min(invalidFittings.size(),50))));
        check(ShipLineages.maximumCampaignGeneration(1)==2,"Starting corvette access missing");
        check(ShipLineages.maximumCampaignGeneration(500)==10,"Top-level fleet locked forever");
        var base=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        CampaignShipyardRules.requireAllowed(base,Battle.Faction.TERRAN,0,1);
        rejects(()->CampaignShipyardRules.requireAllowed(base,Battle.Faction.DRAXIAN,0,1),
                "Cross-faction campaign shipyard bypass");
        rejects(()->CampaignShipyardRules.requireAllowed(Shipyard.starter(
                        Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT),Battle.Faction.TERRAN,6,1),
                "Future-generation ship commissioned in stage one");
        var advanced=base.stage(3,Shipyard.Track.OUTPUT,3);
        rejects(()->CampaignShipyardRules.requireAllowed(advanced,Battle.Faction.TERRAN,2,1),
                "Module level bypassed research rank");
        CampaignShipyardRules.requireAllowed(advanced,Battle.Faction.TERRAN,3,1);
        var versioned=base.withModuleVersion(3,ModuleVersions.Pattern.REINFORCED,2);
        rejects(()->CampaignShipyardRules.requireAllowed(versioned,Battle.Faction.TERRAN,1,1),
                "Advanced manufacturing available before license II");
        CampaignShipyardRules.requireAllowed(versioned,Battle.Faction.TERRAN,2,1);

        var progress=CampaignProgress.start(Battle.Faction.TERRAN);
        check(progress.researchRank()==0&&progress.nextResearchCost()==25000,
                "Starting research should not be free");
        rejects(progress::purchaseNextResearch,"Unaffordable research purchased");
        var funded=new CampaignProgress.State(Battle.Faction.TERRAN,1,25000,0,0);
        var licensed=funded.purchaseNextResearch();
        check(licensed.researchRank()==1&&licensed.credits()==0,"Research did not charge exact credits");
        check(CampaignProgress.decode(CampaignProgress.encode(licensed)).equals(licensed),
                "Purchased research not persisted");
        String legacyBody="CD-CAMPAIGN-1\nTERRAN\n1\n75000\n0\n";
        CRC32 crc=new CRC32();crc.update(legacyBody.getBytes(StandardCharsets.UTF_8));
        var migrated=CampaignProgress.decode(legacyBody+Long.toHexString(crc.getValue())+"\n");
        check(migrated.credits()==75000&&migrated.researchRank()==0,
                "Legacy campaign bank/research migration lost original credits");
        long quote=CampaignFleet.purchaseCost(base,catalog);
        check(quote>CampaignFleet.purchaseCost(base),
                "Campaign purchase ignores physical machinery costs");
        System.out.println("PASS "+checked+" faction/chassis prototypes in 10 alternating fleet-size generations");
    }
}
