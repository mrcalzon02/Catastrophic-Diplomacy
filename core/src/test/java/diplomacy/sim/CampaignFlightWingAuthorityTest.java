package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Campaign fighter authority follows real inventory, refits and battle losses. */
public final class CampaignFlightWingAuthorityTest {
    private CampaignFlightWingAuthorityTest(){}
    private static void check(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    private static void reject(Runnable operation,String why){
        try{operation.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    private static FlightWingManifest mixed(Shipyard.Design design,
                                             TechnologyCatalog tech,int paid){
        FlightWingManifest legacy=FlightWingManifest.legacy(design,tech,paid);
        ArrayList<FlightWingManifest.FlightWing> wings=
                new ArrayList<>(legacy.wings());
        if(wings.size()>1){
            var first=wings.get(0);
            var second=wings.get(1);
            wings.set(0,new FlightWingManifest.FlightWing(first.bayId(),
                    FighterModel.Hull.INTERCEPTOR,first.stored(),first.ready(),0,0,0,0));
            wings.set(1,new FlightWingManifest.FlightWing(second.bayId(),
                    FighterModel.Hull.TORPEDO_BOMBER,second.stored(),second.ready(),0,0,0,0));
        }
        return FlightWingManifest.of(design,tech,paid,wings);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design carrier=Shipyard.starter(
                Battle.Faction.TERRAN,Shipyard.Hull.CARRIER);
        CampaignFleet.Roster roster=CampaignFleet.startSolo(
                Battle.Faction.TERRAN,carrier,tech,2026111202L);
        long id=roster.ships().get(0).id();
        int paid=roster.get(id).stores().fighters();
        FlightWingManifest manifest=mixed(carrier,tech,paid);
        Map<Long,FlightWingManifest> authority=Map.of(id,manifest);

        // A normal non-fighter transaction preserves exact physical wing types.
        CampaignFleet.Roster reserve=roster.setActive(id,false,200);
        var preserved=CampaignFlightWingAuthority.reconcile(
                roster,reserve,authority,tech);
        check(preserved.get(id).wings().equals(manifest.wings()),
                "Reserve transition changed mixed flight-wing identity");

        // Changing global fighter family would make CampaignFleet.refit discard
        // paid aircraft. The flight authority must fail closed instead.
        Shipyard.Design incompatible=carrier.withFighterHull(
                FighterModel.Hull.LASER_FIGHTER);
        CampaignFleet.Roster refitted=reserve.refit(id,incompatible,tech);
        check(refitted.get(id).stores().fighters()==0,
                "Refit fixture did not exercise aggregate fighter loss");
        reject(()->CampaignFlightWingAuthority.reconcile(
                reserve,refitted,preserved,tech),
                "Refit silently confiscated paid mixed fighter wings");

        // Put the paid complement into a real battle, launch fighters, destroy
        // one physical airframe, then reconcile only actual survivors.
        Battle battle=new Battle(2026111203L);
        battle.spawnWing(0,carrier,tech,1,290,350);
        battle.spawnWing(1,Shipyard.starter(
                Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER),tech,1,610,350);
        Battle.Ship live=battle.ships().get(0);
        roster.get(id).stores().apply(live);
        live.commissionedId=id;
        battle.setFlightWingManifest(live.id,manifest);
        for(int t=0;t<180&&live.fightersLaunched==0;t++)battle.step();
        check(live.fightersLaunched>0,"Authority fixture never launched a fighter");
        Battle.Craft casualty=null;
        for(Battle.Craft craft:battle.crafts())
            if(craft.fighter&&craft.sourceId==live.id){casualty=craft;break;}
        check(casualty!=null,"No physical fighter available for loss fixture");
        casualty.integrity=0;
        battle.step();

        CampaignFleet.Roster returned=roster.afterBattle(battle,List.of(id));
        check(returned.get(id).stores().fighters()<paid,
                "Destroyed fighter remained in paid campaign inventory");
        Map<Long,FlightWingManifest> after=
                CampaignFlightWingAuthority.afterBattle(
                        roster,returned,authority,battle,List.of(id),tech);
        check(after.get(id).paid()==returned.get(id).stores().fighters(),
                "Battle wing authority disagrees with aggregate campaign stores");
        check(after.get(id).wings().stream().map(
                        FlightWingManifest.FlightWing::type).distinct().count()>=2,
                "Surviving mixed wing types collapsed after battle");

        // Paid CRAFT service replaces losses into free physical capacity while
        // retaining the surviving bay type assignments.
        CampaignFleet.Roster serviced=returned.service(id,CampaignFleet.Service.CRAFT,tech);
        Map<Long,FlightWingManifest> replenished=
                CampaignFlightWingAuthority.reconcile(returned,serviced,after,tech);
        check(replenished.get(id).paid()==serviced.get(id).stores().fighters(),
                "CRAFT service did not restore authoritative paid fighter count");
        check(replenished.get(id).wings().stream().map(
                        FlightWingManifest.FlightWing::type).distinct().count()>=2,
                "CRAFT service replaced mixed wings with one generic fighter type");
        System.out.println("PASS campaign flight-wing authority: preserve, refit guard, real loss and paid replenishment");
    }
}
