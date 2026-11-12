package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single transition policy for campaign-owned physical fighter wings.
 * Aggregate CampaignFleet.Stores.fighters remains the paid count; this class
 * preserves which real bay owns each surviving airframe and fighter type.
 */
public final class CampaignFlightWingAuthority {
    private CampaignFlightWingAuthority(){}

    public static Map<Long,FlightWingManifest> normalize(
            CampaignFleet.Roster roster,Map<Long,FlightWingManifest> current,
            TechnologyCatalog technology){
        if(roster==null||technology==null)
            throw new IllegalArgumentException("Missing campaign flight authority");
        if(current==null||current.isEmpty())
            return CampaignFlightWingCodec.migrate(roster,technology).wings();
        if(current.size()!=roster.ships().size())
            throw new IllegalArgumentException("Incomplete campaign flight authority");
        LinkedHashMap<Long,FlightWingManifest> result=new LinkedHashMap<>();
        for(CampaignFleet.Vessel vessel:roster.ships()){
            FlightWingManifest manifest=current.get(vessel.id());
            if(manifest==null)
                throw new IllegalArgumentException("Missing vessel flight authority");
            result.put(vessel.id(),FlightWingManifest.of(vessel.fitting(),technology,
                    vessel.stores().fighters(),manifest.wings()));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static boolean sameFitting(Shipyard.Design a,Shipyard.Design b){
        return Shipyard.encode(a).equals(Shipyard.encode(b));
    }

    private static FlightWingManifest compatibleRefit(
            FlightWingManifest manifest,Shipyard.Design oldDesign,
            Shipyard.Design replacement,TechnologyCatalog technology,int paid){
        Map<String,Integer> nextBays=FlightWingManifest.bayCapacities(replacement,technology);
        ArrayList<FlightWingManifest.FlightWing> retained=new ArrayList<>();
        for(FlightWingManifest.FlightWing wing:manifest.wings())
            if(wing.paid()>0||nextBays.containsKey(wing.bayId()))
                retained.add(wing);
        FlightWingManifest.of(oldDesign,technology,paid,manifest.wings());
        return FlightWingManifest.of(replacement,technology,paid,retained);
    }

    private static FlightWingManifest topUp(
            FlightWingManifest manifest,Shipyard.Design design,
            TechnologyCatalog technology,int oldPaid,int newPaid){
        if(newPaid<oldPaid)throw new IllegalArgumentException(
                "Non-battle operation cannot discard paid fighters");
        FlightWingManifest checked=FlightWingManifest.of(
                design,technology,oldPaid,manifest.wings());
        if(newPaid==oldPaid)return checked;
        Map<String,Integer> capacities=FlightWingManifest.bayCapacities(design,technology);
        LinkedHashMap<String,FlightWingManifest.FlightWing> byBay=new LinkedHashMap<>();
        for(FlightWingManifest.FlightWing wing:checked.wings())byBay.put(wing.bayId(),wing);
        int remaining=newPaid-oldPaid;
        for(var entry:capacities.entrySet()){
            if(remaining<=0)break;
            FlightWingManifest.FlightWing wing=byBay.get(entry.getKey());
            if(wing==null)wing=new FlightWingManifest.FlightWing(
                    entry.getKey(),design.fighterHull,0,0,0,0,0,0);
            int add=StrictMath.min(remaining,entry.getValue()-wing.paid());
            if(add<=0)continue;
            byBay.put(entry.getKey(),new FlightWingManifest.FlightWing(
                    wing.bayId(),wing.type(),wing.stored(),wing.ready()+add,
                    wing.launched(),wing.returning(),wing.servicing(),wing.destroyed()));
            remaining-=add;
        }
        if(remaining!=0)throw new IllegalArgumentException(
                "Paid fighter stock exceeds surviving physical bays");
        return FlightWingManifest.of(design,technology,newPaid,
                new ArrayList<>(byBay.values()));
    }

    /**
     * Reconcile every ordinary dock/research/purchase transaction.
     * New ships receive the legacy/default fighter type; paid stock increases
     * fill free physical bay capacity while retaining existing wing types.
     * Paid stock may only decrease through afterBattle(), where real losses exist.
     */
    public static Map<Long,FlightWingManifest> reconcile(
            CampaignFleet.Roster before,CampaignFleet.Roster after,
            Map<Long,FlightWingManifest> current,TechnologyCatalog technology){
        if(after==null||technology==null)
            throw new IllegalArgumentException("Missing campaign fleet transition");
        if(before==null)return normalize(after,Map.of(),technology);
        Map<Long,FlightWingManifest> live=normalize(before,current,technology);
        HashSet<Long> afterIds=new HashSet<>();
        LinkedHashMap<Long,FlightWingManifest> result=new LinkedHashMap<>();
        for(CampaignFleet.Vessel vessel:after.ships()){
            afterIds.add(vessel.id());
            CampaignFleet.Vessel prior=null;
            try{prior=before.get(vessel.id());}catch(IllegalArgumentException absent){/* purchase */}
            if(prior==null){
                result.put(vessel.id(),FlightWingManifest.legacy(
                        vessel.fitting(),technology,vessel.stores().fighters()));
                continue;
            }
            FlightWingManifest manifest=live.get(vessel.id());
            int oldPaid=prior.stores().fighters(),newPaid=vessel.stores().fighters();
            if(newPaid<oldPaid)throw new IllegalArgumentException(
                    "Refit/service would discard paid fighter inventory");
            if(!sameFitting(prior.fitting(),vessel.fitting())){
                if(newPaid!=oldPaid)throw new IllegalArgumentException(
                        "Refit cannot transform paid fighter inventory");
                result.put(vessel.id(),compatibleRefit(manifest,prior.fitting(),
                        vessel.fitting(),technology,newPaid));
            }else result.put(vessel.id(),topUp(
                    manifest,vessel.fitting(),technology,oldPaid,newPaid));
        }
        for(CampaignFleet.Vessel vessel:before.ships())
            if(!afterIds.contains(vessel.id()))
                throw new IllegalArgumentException(
                        "Non-battle operation removed a commissioned fighter authority");
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Reconcile actual battle survival. All intact airframes—unlaunched,
     * recovered, or still airborne—remain paid inventory. Lost craft reduce
     * stock; no replacement is created here.
     */
    public static Map<Long,FlightWingManifest> afterBattle(
            CampaignFleet.Roster before,CampaignFleet.Roster after,
            Map<Long,FlightWingManifest> current,Battle battle,
            List<Long> deployedIds,TechnologyCatalog technology){
        if(before==null||after==null||battle==null||deployedIds==null)
            throw new IllegalArgumentException("Incomplete battle wing transition");
        Map<Long,FlightWingManifest> live=normalize(before,current,technology);
        Set<Long> deployed=Set.copyOf(deployedIds);
        HashMap<Long,Battle.Ship> participants=new HashMap<>();
        for(Battle.Ship ship:battle.ships())
            if(ship.team==0&&ship.commissionedId!=0)
                participants.put(ship.commissionedId,ship);
        LinkedHashMap<Long,FlightWingManifest> result=new LinkedHashMap<>();
        for(CampaignFleet.Vessel vessel:after.ships()){
            FlightWingManifest prior=live.get(vessel.id());
            if(!deployed.contains(vessel.id())){
                result.put(vessel.id(),FlightWingManifest.of(vessel.fitting(),technology,
                        vessel.stores().fighters(),prior.wings()));
                continue;
            }
            Battle.Ship ship=participants.get(vessel.id());
            if(ship==null)throw new IllegalArgumentException(
                    "Surviving commissioned ship is missing from battle");
            result.put(vessel.id(),fromBattle(
                    vessel,prior,ship,battle,technology));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static FlightWingManifest fromBattle(
            CampaignFleet.Vessel vessel,FlightWingManifest prior,
            Battle.Ship ship,Battle battle,TechnologyCatalog technology){
        LinkedHashMap<String,Integer> surviving=new LinkedHashMap<>();
        LinkedHashMap<String,FighterModel.Hull> types=new LinkedHashMap<>();
        for(Battle.Ship.HangarBay bay:ship.fighterBays){
            surviving.put(bay.id,bay.unlaunched());
            types.put(bay.id,bay.fighterHull);
        }
        for(Battle.Craft craft:battle.crafts())
            if(craft.fighter&&craft.sourceId==ship.id&&craft.integrity>0&&craft.fuel>0){
                if(!surviving.containsKey(craft.launchBayId)||craft.profile==null)
                    throw new IllegalArgumentException("Surviving fighter lacks a physical bay");
                surviving.merge(craft.launchBayId,1,Integer::sum);
                types.put(craft.launchBayId,craft.profile.hull());
            }
        for(Battle.Craft craft:ship.dockedFighters)
            if(craft.fighter&&craft.integrity>0){
                if(!surviving.containsKey(craft.launchBayId)||craft.profile==null)
                    throw new IllegalArgumentException("Docked fighter lacks a physical bay");
                surviving.merge(craft.launchBayId,1,Integer::sum);
                types.put(craft.launchBayId,craft.profile.hull());
            }
        int counted=0;
        for(int value:surviving.values())counted=Math.addExact(counted,value);
        int expected=vessel.stores().fighters();
        if(counted!=expected)throw new IllegalArgumentException(
                "Battle fighter survivors disagree with campaign inventory");

        HashMap<String,FlightWingManifest.FlightWing> oldByBay=new HashMap<>();
        int historicalDestroyed=0;
        for(FlightWingManifest.FlightWing wing:prior.wings()){
            oldByBay.put(wing.bayId(),wing);
            historicalDestroyed=Math.addExact(historicalDestroyed,wing.destroyed());
        }
        int newlyDestroyed=StrictMath.max(0,prior.paid()-expected);
        ArrayList<FlightWingManifest.FlightWing> wings=new ArrayList<>();
        for(var entry:surviving.entrySet()){
            FlightWingManifest.FlightWing old=oldByBay.get(entry.getKey());
            int destroyed=old==null?0:old.destroyed();
            wings.add(new FlightWingManifest.FlightWing(entry.getKey(),
                    types.get(entry.getKey()),0,entry.getValue(),0,0,0,destroyed));
        }
        if(!wings.isEmpty()&&newlyDestroyed>0){
            var first=wings.get(0);
            wings.set(0,new FlightWingManifest.FlightWing(first.bayId(),first.type(),
                    first.stored(),first.ready(),0,0,0,
                    Math.addExact(first.destroyed(),newlyDestroyed)));
        }
        FlightWingManifest result=FlightWingManifest.of(
                vessel.fitting(),technology,expected,wings);
        int destroyedTotal=0;
        for(var wing:result.wings())destroyedTotal=Math.addExact(destroyedTotal,wing.destroyed());
        if(destroyedTotal!=historicalDestroyed+newlyDestroyed)
            throw new IllegalStateException("Fighter loss history did not reconcile");
        return result;
    }
}
