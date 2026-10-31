package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real group operations, persistence, independent references and battle application. */
public final class FleetDoctrineGroupsTest {
    private static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void rejected(Runnable r){
        try{r.run();throw new AssertionError("Invalid group was accepted");}
        catch(IllegalArgumentException expected){ }
    }
    public static void main(String[] ignored)throws Exception{
        ShipStandingOrders captain=ShipStandingOrders.DEFAULT.adjust(0,3)
                .adjust(1,-2).adjust(13,-5).adjust(14,1).adjust(16,1);
        ShipStandingOrders guard=new ShipStandingOrders(BattleOrders.DEFAULT,15,85,30,
                new ShipTacticalDirectives(70,
                        ShipTacticalDirectives.InterceptStrategy.CLOSE_IN,
                        ShipTacticalDirectives.DefenseScope.PROTECTED_SHIPS,
                        ShipTacticalDirectives.FighterMission.RESERVE,
                        List.of(3,4,-1,-1),5,400));
        var origin=new CampaignDeployment.Loadout(6,CampaignDeployment.Formation.WEDGE)
                .withStanding(0,captain).withStanding(1,guard)
                .withPlacement(0,290,280)
                .appendOrder(1,ShipCommandQueue.Command.hold(60));
        var result=FleetDoctrineGroups.apply(origin,0,Set.of(1,2,3));
        ok(origin.standingFor(1).equals(guard),"Group application mutated original blueprint");
        ok(result.standingFor(0).equals(captain),"Group application rewrote source captain");
        ok(result.standingFor(4).equals(ShipStandingOrders.DEFAULT),
                "Unselected ship received captain doctrine");
        ok(result.standingFor(1).aggression()==captain.aggression()&&
                result.standingFor(2).aggression()==captain.aggression()&&
                result.standingFor(3).aggression()==captain.aggression(),
                "Selected vessels did not receive leader aggression");
        ok(result.standingFor(1).directives().speedPercent()==
                captain.directives().speedPercent(),"Ship speed doctrine did not copy");
        ok(result.standingFor(1).directives().fighterMission()==
                captain.directives().fighterMission(),"Fighter doctrine did not copy");
        ok(result.standingFor(1).directives().defendSlots().equals(guard.directives().defendSlots())
                &&result.standingFor(1).directives().escortSlot()==5,
                "Group assignment destroyed independent protect/escort references");
        ok(result.standingFor(2).directives().escortSlot()==-1,
                "An unrelated vessel inherited another captain's escort target");
        ok(result.at(0).equals(origin.at(0))&&
                result.ordersFor(1).equals(origin.ordersFor(1)),
                "Group doctrine rewrote placement or sequential movement orders");
        rejected(()->FleetDoctrineGroups.apply(origin,0,Set.of()));
        rejected(()->FleetDoctrineGroups.apply(origin,0,Set.of(0)));
        rejected(()->FleetDoctrineGroups.apply(origin,0,Set.of(7)));
        rejected(()->FleetDoctrineGroups.apply(origin,10,Set.of(1)));
        rejected(()->FleetDoctrineGroups.apply(origin,0,null));

        Set<Integer> all=new HashSet<>();
        for(int slot=1;slot<200;slot++)all.add(slot);
        var mass=new CampaignDeployment.Loadout(200,CampaignDeployment.Formation.LINE)
                .withStanding(0,captain).withStanding(1,guard);
        var massResult=FleetDoctrineGroups.apply(mass,0,all);
        ok(massResult.standings().size()==200,"Full 200 ship group was truncated");
        ok(massResult.standingFor(199).directives().speedPercent()==75,
                "Final commissioned hull ignored group doctrine");
        String saved=ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,
                massResult.standings().entrySet().stream().collect(
                        java.util.stream.Collectors.toMap(e->(long)e.getKey(),
                                java.util.Map.Entry::getValue)));
        var loaded=ShipStandingOrdersCodec.decode(saved);
        ok(loaded.policies().size()==massResult.standings().size()&&
                loaded.policies().get(1L).equals(massResult.standingFor(1)),
                "Grouped captain doctrines did not survive a real save/reload");

        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        Shipyard.Design friendly=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,technology);
        Shipyard.Design hostile=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,technology);
        CampaignEncounter battle=CampaignEncounter.start(150,result,friendly,hostile,technology);
        ok(battle.battle().ships().get(1).escortShipId==battle.battle().ships().get(5).id,
                "Group doctrine broke actual escort relationship");
        ok(battle.battle().ships().get(1).defendedShipIds.size()==2,
                "Group doctrine broke actual four-target defense slots");
        ok(battle.battle().ships().get(3).standingOrders.aggression()==captain.aggression(),
                "Group doctrine not applied to actual simulated third fleet ship");
        battle.battle().step();
        ok(battle.battle().tick==1,"Real combat could not advance with grouped doctrines");
        System.out.println("PASS group standing orders: 200 hulls, independent targets, deployment, save, live battle");
    }
}
