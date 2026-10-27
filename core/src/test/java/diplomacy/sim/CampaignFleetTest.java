package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;

/** Actual owned hulls, paid stores, escaped recovery and checksum persistence. */
public final class CampaignFleetTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void reject(Runnable r,String why){
        try{r.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){}
    }
    public static void main(String[] ignored)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Battle.Faction faction=Battle.Faction.TERRAN;
        Shipyard.Design baseline=Shipyard.starter(faction,Shipyard.Hull.CORVETTE);
        var original=CampaignFleet.start(faction,baseline,tech);
        check(original.ships().size()==4&&original.active().size()==4&&original.reserves().isEmpty(),
                "Starting ships are not commissioned and supplied");
        check(original.ships().stream().map(CampaignFleet.Vessel::name).distinct().count()==4,
                "Initial registered vessel names must be individually distinguishable");
        var reserve=original.setActive(2,false,20);
        check(reserve.active().size()==3&&reserve.reserves().size()==1,
                "Moving vessel to reserve lost ownership or failed to update active group");
        check(original.active().size()==4,"Immutable original roster was changed");
        reserve=reserve.setActive(2,true,20);
        check(reserve.active().size()==4&&reserve.reserves().isEmpty(),
                "Reserve vessel was repurchased or failed to return to active duty");
        reject(()->original.setActive(999,true,20),"Nonexistent ship activated");
        reject(()->original.setActive(1,true,0),"Invalid deployment limit accepted");

        for(FleetNames.Style style:FleetNames.Style.values()){
            String first=FleetNames.generate(style,73,5);
            check(first.equals(FleetNames.generate(style,73,5))&&!first.isBlank(),
                    "Selected naming style isn't replay-stable");
            check(!first.equals(FleetNames.generate(style,73,6)),
                    "Neighboring commissioned ships received same call sign");
        }
        var styled=original.changeStyle(FleetNames.Style.CELESTIAL)
                .rename(1,FleetNames.generate(FleetNames.Style.CELESTIAL,73,21));
        check(styled.get(1).name().equals(FleetNames.generate(FleetNames.Style.CELESTIAL,73,21)),
                "Selected vessel name was not retained");

        var tested=original;
        Battle first=new Battle(1977);
        List<Integer> ids=new ArrayList<>();
        for(var ship:tested.active()){
            ids.add(ship.id());
            first.spawnWing(0,ship.fitting(),tech,1,310,330);
        }
        first.ships().get(0).hull=first.ships().get(0).maxHull*.45;
        first.ships().get(0).crewCount=Math.max(0,first.ships().get(0).crewCount-3);
        first.ships().get(0).fuel=17;
        first.ships().get(0).missileAmmo=1;
        first.ships().get(1).hull=0; // Actually destroyed, not kept for free.
        first.ships().get(2).escaped=true; // A physically evacuated and intact ship.
        var returned=tested.afterBattle(first,ids);
        check(returned.ships().size()==3&&returned.active().size()==2&&
                returned.reserves().size()==1,
                "Destroyed, survived and escaped hulls not handled separately");
        check(returned.get(3).stores().hull()>0&&!returned.get(3).deployed(),
                "Escaped ship must remain owned but move to AVAILABLE reserve");
        check(returned.get(1).stores().fuel()==17&&returned.get(1).stores().missiles()==1,
                "Spent fuel and missiles were mysteriously refilled after combat");
        check(returned.get(1).stores().crew()==first.ships().get(0).crewCount,
                "Lost crew were automatically replaced");
        reject(()->returned.get(2),"Destroyed hull was kept as owned for free");

        long repair=returned.cost(1,CampaignFleet.Service.REPAIR,tech);
        long crew=returned.cost(1,CampaignFleet.Service.CREW,tech);
        long ammo=returned.cost(1,CampaignFleet.Service.AMMUNITION,tech);
        long fuel=returned.cost(1,CampaignFleet.Service.FUEL,tech);
        check(repair>0&&crew>0&&ammo>0&&fuel>0,
                "Damaged ships have no actual servicing charges");
        CampaignProgress.State wallet=new CampaignProgress.State(faction,1,200000,0);
        wallet=wallet.spend(repair).spend(crew).spend(ammo).spend(fuel);
        long spent=repair+crew+ammo+fuel;
        check(wallet.credits()==200000-spent,"Dock costs were not debited from campaign");
        CampaignProgress.State remaining=wallet;
        reject(()->remaining.spend(200001),"Insufficient credits silently accepted");
        var restored=returned.service(1,CampaignFleet.Service.REPAIR,tech)
                .service(1,CampaignFleet.Service.CREW,tech)
                .service(1,CampaignFleet.Service.AMMUNITION,tech)
                .service(1,CampaignFleet.Service.FUEL,tech);
        for(CampaignFleet.Service type:new CampaignFleet.Service[]{
                CampaignFleet.Service.REPAIR,CampaignFleet.Service.CREW,
                CampaignFleet.Service.AMMUNITION,CampaignFleet.Service.FUEL})
            check(restored.cost(1,type,tech)==0,"Paid dock service did not restore physical supplies");
        check(restored.get(3).stores().equals(returned.get(3).stores()),
                "Servicing one ship regenerated a different reserve vessel");

        long purchasePrice=CampaignFleet.purchaseCost(baseline);
        check(purchasePrice>10000,"Ship replacements are implausibly cheap");
        var replacement=returned.purchase(baseline,tech);
        check(replacement.ships().size()==4&&replacement.reserves().size()==2,
                "New ship was not individually delivered to reserve");
        var latest=replacement.get(replacement.nextId()-1);
        check(latest.stores().crew()==0&&latest.stores().fuel()==0&&
                latest.stores().missiles()==0,
                "Purchased bare hull received uncharged crew, fuel or ordnance");
        check(replacement.cost(latest.id(),CampaignFleet.Service.CREW,tech)>0,
                "Recruitment is mysteriously free after buying a hull");

        String encoded=CampaignFleetCodec.encode(replacement);
        var decoded=CampaignFleetCodec.decode(encoded,tech);
        check(CampaignFleetCodec.encode(decoded).equals(encoded),
                "Full owned fleet checksum save round-trip lost inventory");
        reject(()->CampaignFleetCodec.decode(encoded.replace("CD-ROSTER-2","CD-ROSTER-1"),tech),
                "Tampered campaign ship registry was accepted");
        var copy=decoded.setActive(3,true,20);
        var listed=copy.active();
        check(listed.stream().anyMatch(v->v.id()==3),
                "Escaped preserved hull could not be recommissioned");
        // Captain escort/defend references must resolve through immutable IDs,
        // not whichever hull happens to occupy the first active slot.
        var linked=copy.setActive(1,false,20);
        var policy=ShipStandingOrders.DEFAULT.adjust(21,1,0,3);
        var save=new java.util.HashMap<Integer,ShipStandingOrders>();
        save.put(linked.active().get(0).id()-1,policy);
        check(linked.forDeployment(save).containsKey(0),
                "Captain orders did not map from stable ship ID to active slot");

        for(var scale:CrewChatter.Scale.values())
            for(var event:CrewChatter.Event.values())
                check(!CrewChatter.line(scale,event,55,4).isBlank(),
                        "Event-specific crew chatter missing for "+scale+"/"+event);
        check(!CrewChatter.line(CrewChatter.Scale.FIGHTER,CrewChatter.Event.DAMAGE,3,1)
                        .toLowerCase().contains("bulkheads"),
                "Battleship bridge chatter incorrectly sent from fighter cockpit");
        System.out.println("PASS persistent campaign fleet: active/reserve, safe retreat, destroyed replacement, "+
                "paid hull/crew/fuel/ammo service, five name styles, contextual fighter/capital chatter");
    }
}
