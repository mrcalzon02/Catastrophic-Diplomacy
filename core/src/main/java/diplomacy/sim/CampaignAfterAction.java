package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.ArrayList;
import java.util.List;

/** Named, immutable postbattle account of the actual deployed commissioned fleet.
 * Destroyed vessels stay on the report even though removed from fleet ownership.
 */
public final class CampaignAfterAction {
    private CampaignAfterAction(){}
    public enum Outcome { SURVIVED, WITHDRAWING, ESCAPED, DESTROYED }
    public record Entry(long id,String name,Shipyard.Hull hull,Outcome outcome,
                        int hullPercent,int crewLost,int kineticSpent,int missileSpent,
                        int torpedoSpent,int fuelSpent) {
        public Entry{
            if(!VesselIdentifiers.valid(id)||name==null||name.isBlank()||hull==null||outcome==null||
                    hullPercent<0||hullPercent>100||crewLost<0||kineticSpent<0||
                    missileSpent<0||torpedoSpent<0||fuelSpent<0)
                throw new IllegalArgumentException("Invalid postbattle ship entry");
        }
    }
    /** Explicitly distinguishes rescued pilots from live aircraft crews
     * and from uncollected escape capsules. Missing personnel cannot return
     * to the roster through a visual ejection effect alone. */
    public record PilotSummary(int deployed,int recovered,int airborne,
                               int adrift,int missing){
        public PilotSummary {
            if(deployed<0||recovered<0||airborne<0||adrift<0||missing<0||
                    recovered+airborne+adrift+missing!=deployed)
                throw new IllegalArgumentException("Inconsistent pilot after-action tally");
        }
    }
    public record Report(List<Entry> entries,PilotSummary pilots) {
        /** Previous consumers using the ship-only report remain compatible. */
        public Report(List<Entry> entries){
            this(entries,new PilotSummary(0,0,0,0,0));
        }
        public Report {
            if(pilots==null)throw new IllegalArgumentException("Missing personnel settlement");
            if(entries==null||entries.size()>200)
                throw new IllegalArgumentException("Invalid fleet battle report");
            entries=List.copyOf(entries);
        }
        public int pages(int rows){
            if(rows<1||rows>200)throw new IllegalArgumentException("Invalid page size");
            return Math.max(1,(entries.size()+rows-1)/rows);
        }
        public List<Entry> page(int index,int rows){
            if(index<0||index>=pages(rows))throw new IllegalArgumentException("Unknown report page");
            int from=index*rows;
            return entries.subList(from,Math.min(entries.size(),from+rows));
        }
        public long count(Outcome type){
            return entries.stream().filter(e->e.outcome()==type).count();
        }
    }
    public static Report capture(CampaignFleet.Roster owned,List<Long> deployedIds,Battle battle){
        if(owned==null||deployedIds==null||battle==null)
            throw new IllegalArgumentException("Incomplete after-action sources");
        List<CampaignFleet.Vessel> active=owned.active();
        List<Battle.Ship> participants=battle.ships().stream()
                .filter(s->s.team==0).collect(java.util.stream.Collectors.toList());
        if(active.size()!=deployedIds.size()||participants.size()!=deployedIds.size())
            throw new IllegalArgumentException("Fleet deployment mismatch");
        ArrayList<Entry> entries=new ArrayList<>();
        for(int i=0;i<deployedIds.size();i++){
            CampaignFleet.Vessel prior=active.get(i);
            if(prior.id()!=deployedIds.get(i))
                throw new IllegalArgumentException("Commissioned ship identity mismatch");
            Battle.Ship now=participants.get(i);
            // Never attribute an uncommissioned (ID 0) or different hull's
            // combat record to a permanent 16-digit campaign vessel ID.
            if(now.commissionedId!=prior.id())
                throw new IllegalArgumentException("After-action identity mismatch");
            Outcome outcome=now.hull<=0?Outcome.DESTROYED:now.escaped?Outcome.ESCAPED:
                    now.withdrawing?Outcome.WITHDRAWING:Outcome.SURVIVED;
            var before=prior.stores();
            int pct=(int)Math.round(100*Math.max(0,Math.min(1,
                    now.maxHull<=0?0:now.hull/now.maxHull)));
            int returnedCrew=0;
            if(now.hull>0)for(Battle.Craft craft:battle.crafts())
                if(craft.fighter&&craft.crewEmbarked&&craft.integrity>0&&
                        craft.fuel>0&&craft.team==now.team&&craft.sourceId==now.id)
                    returnedCrew+=craft.crewSeats;
            entries.add(new Entry(prior.id(),prior.name(),prior.fitting().hull,outcome,pct,
                    (int)Math.ceil(Math.max(0,before.crew()-now.crewCount
                            -now.evacuatedCrew-returnedCrew)),
                    Math.max(0,before.kinetic()-now.kineticAmmo),
                    Math.max(0,before.missiles()-now.missileAmmo),
                    Math.max(0,before.torpedoes()-now.torpedoAmmo),
                    (int)Math.ceil(Math.max(0,before.fuel()-now.fuel))));
        }
        int deployed=0,airborne=0,adrift=0,recovered=0;
        for(Battle.Ship ship:participants)
            deployed+=ship.fighterPilotsCommitted;
        for(Battle.Craft craft:battle.crafts())
            if(craft.fighter&&craft.crewEmbarked&&craft.team==0&&
                    craft.integrity>0&&craft.fuel>0){
                Battle.Ship parent=null;
                for(Battle.Ship ship:participants)
                    if(ship.id==craft.sourceId){parent=ship;break;}
                if(parent!=null&&parent.hull>0)airborne+=craft.crewSeats;
            }
        for(FighterEscapePod pod:battle.fighterEscapePods())
            if(pod.campaignCrew&&pod.team==0&&pod.active())adrift++;
        for(Battle.FighterRecovery pickup:battle.fighterRecoveryRecords())
            if(pickup.rosterCrew()&&pickup.team()==0){
                for(Battle.Ship ship:participants)
                    if(ship.id==pickup.rescuerShipId()&&ship.hull>0){
                        recovered++;break;
                    }
            }
        int missing=deployed-airborne-adrift-recovered;
        if(missing<0)
            throw new IllegalArgumentException("Crew records manufactured rescue personnel");
        return new Report(entries,new PilotSummary(
                deployed,recovered,airborne,adrift,missing));
    }
}
