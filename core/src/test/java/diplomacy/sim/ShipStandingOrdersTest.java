package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashMap;
import java.util.Map;

/** Functional per-hull always-on doctrine, persistence and live battle integration. */
public final class ShipStandingOrdersTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void reject(Runnable f,String message){
        try{f.run();throw new AssertionError(message);}
        catch(IllegalArgumentException expected){/* rejected */}
    }
    public static void main(String[] args)throws Exception {
        ShipStandingOrders policy=ShipStandingOrders.DEFAULT;
        check(ShipStandingOrders.SETTINGS.length==23,"Missing advanced independent combat policies");
        for(int i=0;i<ShipStandingOrders.SETTINGS.length;i++)
            check(!policy.display(i).isBlank(),"Invisible standing policy row "+i);
        ShipStandingOrders attack=policy.adjust(0,4).adjust(1,-3);
        check(attack.aggression()==90&&attack.caution()==20
                        &&attack.engagementRangeMultiplier()<1,
                "Aggression or caution does not affect actual engagement ranges");
        ShipStandingOrders cautious=policy.adjust(0,-3).adjust(1,4);
        check(cautious.engagementRangeMultiplier()>1,
                "Cautious ship did not maintain greater engagement separation");
        var retreat=policy.adjust(2,9).adjust(3,5);
        check(Math.round(retreat.tactics().retreatAtHullFraction()*100)==70
                        &&retreat.shieldWithdrawPercent()==50,
                "Independent hull and shield retreat minimums not saved");
        check(retreat.adjust(4,4).tactics().position()==BattleOrders.Position.LAST_STAND,
                "Last-stand doctrine unavailable");
        check(policy.adjust(6,1).tactics().pointDefense()!=policy.tactics().pointDefense(),
                "Point defense doctrine did not change");
        check(policy.adjust(7,-10).tactics().fighters().priority()==0,
                "Cannot exclude fighters as offensive targets");
        check(policy.adjust(8,-10).tactics().escorts().priority()==0,
                "Cannot exclude escorts as offensive targets");
        check(policy.adjust(9,-10).tactics().capitals().priority()==0,
                "Cannot exclude capitals as offensive targets");
        for(int i=10;i<=12;i++)
            check(policy.adjust(i,4).display(i).endsWith("units"),
                    "Independent weapon engagement range cannot be tuned");
        ShipStandingOrders advanced=policy.adjust(13,-4).adjust(14,1)
                .adjust(15,-1).adjust(16,1)
                .adjust(17,1,0,8).adjust(18,1,0,8).adjust(19,1,0,8)
                .adjust(20,1,0,8).adjust(21,1,0,8).adjust(22,2);
        check(advanced.directives().speedPercent()==80,
                "Per-ship engine limit did not change");
        check(advanced.directives().defendSlots().size()==4&&
                advanced.directives().defendSlots().stream().distinct().count()==4,
                "Four independent defend targets failed");
        check(advanced.directives().escortSlot()==1,
                "Escort leader incorrectly targets self or is not selectable");
        check(advanced.directives().escortRange()==300,
                "Escort max distance slider did not change");
        check(advanced.directives().fighterMission()!=policy.directives().fighterMission(),
                "Carrier fighter mission failed to change");
        check(advanced.directives().defenseScope()!=policy.directives().defenseScope()&&
                advanced.directives().interceptStrategy()!=policy.directives().interceptStrategy(),
                "Point defense strategy and protection scope must be independent");
        Map<Integer,ShipStandingOrders> saved=new HashMap<>();
        for(int slot=0;slot<200;slot++)
            saved.put(slot,slot%3==0?advanced:slot%2==0?attack:retreat);
        String encoded=ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,saved);
        ShipStandingOrdersCodec.File decoded=ShipStandingOrdersCodec.decode(encoded);
        check(decoded.faction()==Battle.Faction.TERRAN&&decoded.policies().equals(saved),
                "200-hull standing orders failed checksum round-trip");
        check(encoded.equals(ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,saved)),
                "Nondeterministic doctrine save serialization");
        check(encoded.startsWith("CD-STANDING-2\\n".replace("\\\\n","\\n")),
                "New versioned policy format not written");
        reject(()->ShipStandingOrdersCodec.decode(encoded.replace("TERRAN","DRAXIAN")),
                "Tampered faction save accepted");
        reject(()->ShipStandingOrdersCodec.decode(encoded.replaceFirst("90,20","30,20")),
                "Tampered doctrine payload accepted");
        reject(()->new ShipStandingOrders(BattleOrders.DEFAULT,101,50,0),
                "Invalid aggression outside slider accepted");

        var deployment=new CampaignDeployment.Loadout(3,CampaignDeployment.Formation.LINE)
                .withStanding(0,retreat).withStanding(1,attack);
        check(deployment.standingFor(0).equals(retreat)&&
                        deployment.standingFor(1).equals(attack)&&
                        deployment.standingFor(2).equals(policy),
                "Per-vessel standing order independence failed");
        check(deployment.withFormation(1).standingFor(1).equals(attack),
                "Changing layout erased the standing orders");
        check(deployment.withShips(-1,LevelEconomy.level(1)).standings().containsKey(1),
                "Ship count reduction erased a commissioned hull doctrine");
        check(!deployment.withShips(-2,LevelEconomy.level(1)).standings().containsKey(1),
                "Doctrine for a removed hull remained active");
        var restored=deployment.withStandingProfiles(decoded.policies());
        check(restored.standings().size()==3,
                "Saved doctrines were not limited to commissioned ship slots");

        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design friendly=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,tech);
        Shipyard.Design hostile=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,tech);
        CampaignEncounter encounter=CampaignEncounter.start(1,deployment,friendly,hostile,tech);
        Battle battle=encounter.battle();
        var a=battle.ships().get(0);
        var b=battle.ships().get(1);
        var c=battle.ships().get(2);
        check(a.standingOrders.equals(retreat)&&b.standingOrders.equals(attack)
                        &&c.standingOrders==null,
                "Individual policies did not reach real ships");
        check(a.orders.equals(retreat.tactics())&&b.orders.equals(attack.tactics()),
                "Standing policy did not alter actual battle AI tactical orders");
        battle.setTeamOrders(0,BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD));
        check(a.orders.equals(retreat.tactics())&&b.orders.equals(attack.tactics())
                        &&c.orders.position()==BattleOrders.Position.HOLD,
                "Changing fleet doctrine erased an independent captain policy");
        battle.patchTeamOrders(0,BattleOrders.DEFAULT.withPosition(
                BattleOrders.Position.KEEP_MOVING));
        check(a.orders.equals(retreat.tactics())&&b.orders.equals(attack.tactics()),
                "Patching fleet doctrine overwrote per-ship persistent settings");
        a.shields=a.maxShields*.4;
        check(retreat.mustWithdraw(a),"Low shields did not activate standing withdrawal");
        a.shields=a.maxShields;
        a.hull=a.maxHull*.6;
        check(retreat.mustWithdraw(a),"Minimum hull threshold did not trigger withdrawal");
        a.hull=a.maxHull;
        check(!retreat.mustWithdraw(a),"Ship withdrew while healthy");
        var lastStand=retreat.adjust(4,4);
        check(!lastStand.mustWithdraw(a),"Last stand failed to override threshold");
        // The same hulls and orders survive a stage's real multi-wave combat.
        for(Battle.Ship enemy:encounter.battle().ships())if(enemy.team==1)enemy.hull=0;
        check(encounter.advance()==CampaignEncounter.Advance.NEXT_WAVE,
                "First campaign wave failed to deploy real reinforcements");
        check(a.standingOrders.equals(retreat)&&b.standingOrders.equals(attack),
                "Reinforcements reset persistent ship doctrine");
        battle.step();
        check(battle.tick==1,"Standing doctrines prevented live combat tick");
        var connected=new CampaignDeployment.Loadout(6,CampaignDeployment.Formation.LINE)
                .withStanding(0,advanced);
        CampaignEncounter linked=CampaignEncounter.start(1,connected,friendly,hostile,tech);
        Battle.Ship captain=linked.battle().ships().get(0);
        check(captain.escortShipId==linked.battle().ships().get(1).id,
                "Escort deployment slot did not resolve to physical ship ID");
        check(captain.defendedShipIds.size()==4,
                "Four defense relationships did not reach live battle");
        check(captain.standingOrders.directives().speedPercent()==80,
                "Engine speed restriction was not wired into ship");
        linked.battle().setTeamOrders(0,BattleOrders.DEFAULT);
        check(captain.standingOrders.equals(advanced),
                "Fleet order edits must not erase complex individual behavior stacks");
        System.out.println("PASS individual standing orders: 23 simultaneous doctrines, "+
                "200 saved hull policies, withdrawal behavior, real combat and wave retention");
    }
}
