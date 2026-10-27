package diplomacy.sim;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;
import java.util.Map;

/** Guard rails for real physical orders and schema compatibility. */
public final class ShipTacticalDirectivesTest {
    private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Invalid doctrine accepted");}
        catch(IllegalArgumentException valid){ }}
    public static void main(String[] args){
        var d=ShipTacticalDirectives.DEFAULT;
        ok(d.speedPercent()==100&&d.defendSlots().size()==4,"Default behavior changed");
        rejects(()->d.withDefendSlot(4,3));
        rejects(()->d.withSpeed(110));
        rejects(()->d.withDefendSlot(0,200));
        rejects(()->d.withDefendSlot(0,2).withDefendSlot(1,2));
        var policy=ShipStandingOrders.DEFAULT;
        // Legacy versions stored 14 fields and must still decode to sane defaults.
        String v2=ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,Map.of(0,policy));
        String[] lines=v2.split("\\n");
        String[] values=lines[2].split(",");
        ok(values.length==24,"Missing new saved order fields");
        ok(ShipStandingOrdersCodec.decode(v2).policies().get(0).equals(policy),
                "Extended orders lost in round trip");
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design ally=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.TERRAN,true,tech);
        Shipyard.Design foe=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.DRAXIAN,false,tech);
        var active=new CampaignDeployment.Loadout(6,CampaignDeployment.Formation.LINE)
                .withStanding(0,policy.adjust(13,-4).adjust(17,1,0,6)
                        .adjust(21,1,0,6));
        var encounter=CampaignEncounter.start(1,active,ally,foe,tech);
        var battle=encounter.battle();
        var ship=battle.ships().get(0);
        ok(ship.defendedShipIds.equals(List.of(battle.ships().get(1).id)),
                "Protect order bound wrong live vessel");
        ok(ship.escortShipId==battle.ships().get(1).id,
                "Escort relationship not bound");
        rejects(()->battle.setShipProtection(ship.id,battle.ships().get(6).id,List.of()));
        rejects(()->battle.setShipProtection(ship.id,ship.id,List.of()));
        rejects(()->battle.setShipProtection(ship.id,0,List.of(ship.defendedShipIds.get(0),
                ship.defendedShipIds.get(0))));
        battle.step();
        ok(battle.tick==1,"Orders stopped combat");
        System.out.println("PASS rich tactical directives: slot binding, relation guards, fighter, PD and speed controls");
    }
}
