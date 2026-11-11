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
        check(ShipStandingOrders.SETTINGS.length==34,
                "Missing persistent fuel or sensor doctrine controls");
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
        check(policy.adjust(4,5).tactics().position()==BattleOrders.Position.HOLD_RANGE
                        &&policy.adjust(4,5).display(4).equals("HOLD FIRING RANGE"),
                "Dedicated hold-firing-range standing order unavailable");
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
        Map<Long,ShipStandingOrders> saved=new HashMap<>();
        for(int slot=0;slot<200;slot++)
            saved.put((long)slot,slot%3==0?advanced:slot%2==0?attack:retreat);
        ShipStandingOrders fuelCaptain=policy.adjust(29,3).adjust(30,2).adjust(31,1);
        check(fuelCaptain.resources().fuelPolicy()==ShipResourceOrders.FuelPolicy.WITHDRAW_AT_RESERVE
                        &&fuelCaptain.resources().fuelReservePercent()==35
                        &&fuelCaptain.resources().economyStartPercent()==65
                        &&fuelCaptain.display(29).contains("WITHDRAW"),
                "Fuel posture, protected fraction or economy threshold editor inert");
        check(policy.resources().fuelPolicy()==ShipResourceOrders.FuelPolicy.UNRESTRICTED,
                "Existing captains should retain unrestricted historical thrust by default");
        check(policy.scanOrder().equals(SensorScanOrder.DEFAULT),
                "Existing captains should retain STANDARD sensor search by default");
        ShipStandingOrders focusedCaptain=policy.adjust(32,3).adjust(33,2);
        check(focusedCaptain.scanOrder().mode()==SensorActiveScan.Mode.FOCUSED_RADAR
                        &&focusedCaptain.scanOrder().hostileSlot()==2
                        &&focusedCaptain.display(32).equals("FOCUSED RADAR")
                        &&focusedCaptain.display(33).equals("HOSTILE 3"),
                "Focused radar mode/hostile reference editor is inert");
        ShipStandingOrders xrayCaptain=focusedCaptain.adjust(32,1);
        check(xrayCaptain.scanOrder().mode()==SensorActiveScan.Mode.XRAY_BURST
                        &&xrayCaptain.scanOrder().hostileSlot()==2,
                "X-ray scan mode lost the focused hostile reference");
        ShipStandingOrders passiveCaptain=policy.adjust(32,1);
        check(passiveCaptain.scanOrder().mode()==SensorActiveScan.Mode.PASSIVE
                        &&passiveCaptain.scanOrder().hostileSlot()==-1,
                "Passive scan retained an invalid focused target");
        Battle.Ship rationSample=new Battle(78901).addShip(0,Battle.Faction.TERRAN,200,320);
        rationSample.maxFuel=100;rationSample.fuel=20;
        rationSample.orders=fuelCaptain.tactics();
        check(fuelCaptain.resources().protectedFuel(rationSample)==35&&
                        fuelCaptain.resources().thrustSpeedFactor(rationSample)<1,
                "Low fuel did not protect reserves and throttle discretionary thrust");
        ShipStandingOrders fuelLastStand=fuelCaptain.adjust(4,4);
        rationSample.orders=fuelLastStand.tactics();
        check(fuelLastStand.resources().protectedFuel(rationSample)==0&&
                        fuelLastStand.resources().thrustSpeedFactor(rationSample)==1,
                "An explicit last-stand order must be able to spend all remaining fuel");
        String encoded=ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,saved);
        ShipStandingOrdersCodec.File decoded=ShipStandingOrdersCodec.decode(encoded);
        check(decoded.faction()==Battle.Faction.TERRAN&&decoded.policies().equals(saved),
                "200-hull standing orders failed checksum round-trip");
        check(encoded.equals(ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,saved)),
                "Nondeterministic doctrine save serialization");
        check(encoded.startsWith("CD-STANDING-5"+(char)10),
                "New versioned sensor policy format not written");
        ShipResourceOrders ration=ShipResourceOrders.DEFAULT
                .withAmmunition(ShipResourceOrders.AmmoPolicy.AUTO_RATION)
                .withRationStart(60).withReserve(10,1).withSpacingTicks(45)
                .withFuelPolicy(ShipResourceOrders.FuelPolicy.WITHDRAW_AT_RESERVE)
                .withFuelReserve(35).withEconomyStart(65);
        ShipStandingOrders rationCaptain=advanced.withResources(ration)
                .withScanOrder(new SensorScanOrder(
                        SensorActiveScan.Mode.XRAY_BURST,4));
        check(rationCaptain.adjust(1,1).resources().equals(ration),
                "Editing old standing rows discarded separate resource policy");
        saved.put(7L,rationCaptain);
        String resourceSave=ShipStandingOrdersCodec.encode(Battle.Faction.TERRAN,saved);
        ShipStandingOrders roundTripCaptain=
                ShipStandingOrdersCodec.decode(resourceSave).policies().get(7L);
        check(roundTripCaptain.resources().equals(ration),
                "CD-STANDING-5 lost ammunition rationing or fuel withdrawal policy");
        check(roundTripCaptain.scanOrder().equals(rationCaptain.scanOrder()),
                "CD-STANDING-5 lost sensor scan doctrine or hostile reference");
        // Authentic V1/V2/V3/V4 migration: V4 retains fuel/ammunition
        // policy; older formats inherit the scan STANDARD/no-focus default.
        for(int previous=1;previous<=4;previous++){
            String[] rows=resourceSave.split("\\n",-1);
            StringBuilder older=new StringBuilder("CD-STANDING-").append(previous)
                    .append('\n').append("TERRAN").append('\n');
            int fields=previous==1?14:previous==2?24:previous==3?30:33;
            for(int i=2;i<rows.length-2;i++){
                String[] values=rows[i].split(",",-1);
                older.append(String.join(",",java.util.Arrays.copyOf(values,fields)))
                        .append('\n');
            }
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
                    .digest(older.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for(byte value:digest){
                older.append(Character.forDigit((value>>>4)&15,16))
                        .append(Character.forDigit(value&15,16));
            }
            older.append('\n');
            var migrated=ShipStandingOrdersCodec.decode(older.toString());
            var priorResource=migrated.policies().get(7L).resources();
            check(migrated.policies().size()==200
                            &&migrated.policies().get(7L).scanOrder().equals(SensorScanOrder.DEFAULT)
                            &&(previous>=4?priorResource.equals(ration):
                               previous==3?priorResource.ammunition()==ShipResourceOrders.AmmoPolicy.AUTO_RATION
                                    &&priorResource.fuelPolicy()==ShipResourceOrders.FuelPolicy.UNRESTRICTED
                                    &&priorResource.fuelReservePercent()==25
                                    &&priorResource.economyStartPercent()==60
                               :priorResource.equals(ShipResourceOrders.DEFAULT)),
                    "CD-STANDING-"+previous+" migration lost captain/resource/scan defaults");
        }
        reject(()->ShipStandingOrdersCodec.decode(resourceSave.replace("AUTO_RATION","ENERGY_ONLY")),
                "An edited resource order bypassed the doctrine checksum");
        reject(()->ShipStandingOrdersCodec.decode(resourceSave.replace(
                        "WITHDRAW_AT_RESERVE","PROTECT_RESERVE")),
                "Tampering with saved fuel orders bypassed the doctrine checksum");
        reject(()->new ShipResourceOrders(null,ShipResourceOrders.AmmoPolicy.UNRESTRICTED,
                50,0,0,0),"Null fire control accepted");
        reject(()->new ShipResourceOrders(ShipResourceOrders.FireIntent.IMMEDIATE,
                ShipResourceOrders.AmmoPolicy.UNRESTRICTED,50,100,0,0),
                "Invalid ammunition reserve over 95% accepted");
        reject(()->ShipResourceOrders.DEFAULT.withFuelReserve(100),
                "A fuel reserve over 95% was accepted");
        reject(()->ShipResourceOrders.DEFAULT.withEconomyStart(0),
                "An invalid economy threshold was accepted");
        reject(()->ShipStandingOrdersCodec.decode(encoded.replace("TERRAN","DRAXIAN")),
                "Tampered faction save accepted");
        reject(()->ShipStandingOrdersCodec.decode(encoded.replaceFirst("90,20","30,20")),
                "Tampered doctrine payload accepted");
        reject(()->new ShipStandingOrders(BattleOrders.DEFAULT,101,50,0),
                "Invalid aggression outside slider accepted");

        var deployment=new CampaignDeployment.Loadout(3,CampaignDeployment.Formation.LINE)
                .withStanding(0,retreat.withScanOrder(new SensorScanOrder(
                        SensorActiveScan.Mode.FOCUSED_RADAR,0)))
                .withStanding(1,attack.withScanOrder(new SensorScanOrder(
                        SensorActiveScan.Mode.BOOSTED_RADAR,-1)));
        check(deployment.standingFor(0).tactics().equals(retreat.tactics())&&
                        deployment.standingFor(1).tactics().equals(attack.tactics())&&
                        deployment.standingFor(2).equals(policy),
                "Per-vessel standing order independence failed");
        check(deployment.withFormation(1).standingFor(1).tactics().equals(attack.tactics())&&
                        deployment.withFormation(1).standingFor(1).scanOrder().mode()==
                                SensorActiveScan.Mode.BOOSTED_RADAR,
                "Changing layout erased the standing orders or scan doctrine");
        check(deployment.withShips(-1,LevelEconomy.level(25)).standings().containsKey(1),
                "Ship count reduction erased a commissioned hull doctrine");
        check(!deployment.withShips(-2,LevelEconomy.level(25)).standings().containsKey(1),
                "Doctrine for a removed hull remained active");
        var restored=deployment.withStandingProfiles(
                decoded.policies().entrySet().stream().collect(
                        java.util.stream.Collectors.toMap(e->Math.toIntExact(e.getKey()),
                                java.util.Map.Entry::getValue)));
        check(restored.standings().size()==3,
                "Saved doctrines were not limited to commissioned ship slots");

        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design friendly=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.TERRAN,true,tech);
        Shipyard.Design hostile=ScenarioCatalog.defaultDesign(
                ScenarioCatalog.Preset.DUEL,Battle.Faction.DRAXIAN,false,tech);
        CampaignEncounter encounter=CampaignEncounter.start(25,deployment,friendly,hostile,tech);
        Battle battle=encounter.battle();
        var a=battle.ships().get(0);
        var b=battle.ships().get(1);
        var c=battle.ships().get(2);
        check(a.standingOrders!=null&&b.standingOrders!=null&&c.standingOrders==null,
                "Individual policies did not reach real ships");
        check(a.orders.equals(retreat.tactics())&&b.orders.equals(attack.tactics()),
                "Standing policy did not alter actual battle AI tactical orders");
        Battle.Ship firstHostile=battle.ships().stream().filter(ship->ship.team==1)
                .findFirst().orElseThrow();
        check(a.scan.mode()==SensorActiveScan.Mode.FOCUSED_RADAR
                        &&a.scan.focusTargetId()==firstHostile.id
                        &&b.scan.mode()==SensorActiveScan.Mode.BOOSTED_RADAR,
                "Saved scan doctrines did not resolve to real hostile identity before tick 1");
        a.sensorRange=1200;a.power=1000;a.maxHeat=1000;a.heat=0;
        b.sensorRange=1200;b.power=1000;b.maxHeat=1000;b.heat=0;
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
        check(a.standingOrders.scanOrder().mode()==SensorActiveScan.Mode.FOCUSED_RADAR
                        &&b.standingOrders.scanOrder().mode()==SensorActiveScan.Mode.BOOSTED_RADAR,
                "Reinforcements reset persistent ship/scan doctrine");
        battle.step();
        check(battle.tick==1&&a.scan.emissions==1&&b.scan.emissions==1
                        &&a.scan.powerSpent>0&&b.scan.powerSpent>0,
                "Persisted active scan doctrine did not perform paid tick-1 emission");
        Battle unavailable=Battle.duel(81234,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship unavailableScanner=unavailable.ships().get(0);
        SensorScanOrder unavailableOrder=new SensorScanOrder(
                SensorActiveScan.Mode.XRAY_BURST,5);
        check(unavailableOrder.disabledReason(unavailableScanner,1)
                        .equals("FOCUS TARGET UNAVAILABLE"),
                "Unavailable focused hostile had no disabled reason");
        new CampaignDeployment.Loadout(1,CampaignDeployment.Formation.LINE)
                .withStanding(0,policy.withScanOrder(unavailableOrder)).apply(unavailable);
        check(unavailableScanner.scan.mode()==SensorActiveScan.Mode.PASSIVE,
                "Missing focused target silently became broad STANDARD search");
        unavailableScanner.sensorRange=0;
        check(new SensorScanOrder(SensorActiveScan.Mode.BOOSTED_RADAR,-1)
                        .disabledReason(unavailableScanner,1).equals("NO RADAR FITTED"),
                "Unfitted boosted radar had no disabled reason");

        var connected=new CampaignDeployment.Loadout(6,CampaignDeployment.Formation.LINE)
                .withStanding(0,advanced);
        CampaignEncounter linked=CampaignEncounter.start(50,connected,friendly,hostile,tech);
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
        System.out.println("PASS individual standing orders: 34 controls, CD-STANDING-5 sensor migration, paid tick-1 scans, 200 saved hull policies and combat retention");
    }
}
