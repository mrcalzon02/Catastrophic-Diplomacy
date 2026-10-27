package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Squadron identities and radio preferences belong to commissioned IDs,
 * not transient combat slots, and cannot be minted by a damaged save. */
public final class SquadronOperationsTest {
    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError(reason);
    }
    private static void reject(Runnable operation,String reason){
        try{operation.run();}catch(IllegalArgumentException rejected){return;}
        throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception{
        var tech=TechnologyCatalog.loadDefault();
        var design=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var initial=CampaignFleet.start(Battle.Faction.TERRAN,design,tech,0xabcde987654321L);
        long first=initial.ships().get(0).id();
        long second=initial.ships().get(1).id();
        long third=initial.ships().get(2).id();
        long fourth=initial.ships().get(3).id();
        var groups=initial.assignSquadron(first,1)
                .assignSquadron(second,1).assignSquadron(third,2)
                .setSquadronProfile(1,FleetNames.Style.GRIMDARK,
                        CrewChatter.Tone.LIGHTHEARTED)
                .setSquadronProfile(2,FleetNames.Style.NEOCLASSICAL,
                        CrewChatter.Tone.PROFESSIONAL);
        check(groups.squadronOf(first)==1&&groups.squadronOf(second)==1&&
                        groups.squadronOf(third)==2&&groups.squadronOf(fourth)==0,
                "Eight-group assignments were not recorded against permanent IDs");
        check(groups.namingFor(first)==FleetNames.Style.GRIMDARK&&
                        groups.namingFor(third)==FleetNames.Style.NEOCLASSICAL&&
                        groups.radioFor(first)==CrewChatter.Tone.LIGHTHEARTED&&
                        groups.radioFor(third)==CrewChatter.Tone.PROFESSIONAL&&
                        groups.radioFor(fourth)==CrewChatter.Tone.MIXED,
                "Squadron naming or voice overrides leaked into other groups");
        check(groups.squadronShips(1).size()==2&&groups.squadronShips(2).size()==1,
                "Squadron selection used tactical slot instead of ship ID");
        check(groups.get(first).id()==first,
                "Assigning a squadron mutated permanent 16-digit vessel identity");
        String before=groups.get(first).name();
        var renamed=groups.generateName(first);
        String newName=renamed.get(first).name();
        check(!newName.equals(before)&&newName.equals(
                        FleetNames.generate(FleetNames.Style.GRIMDARK,
                                groups.namingSeed() ^ ((long)before.hashCode()*
                                        0xd1b54a32d192ed03L),
                                VesselIdentifiers.serial(first))),
                "Squadron name regeneration ignored the group's culture");
        var serviced=renamed.serviceAll(first,tech).setActive(second,false,20);
        check(serviced.squadronOf(first)==1&&serviced.squadronOf(second)==1&&
                        serviced.squadronOf(third)==2&&
                        serviced.radioFor(second)==CrewChatter.Tone.LIGHTHEARTED,
                "Services or reserve transfers lost squadron assignments");
        var replenished=serviced.setActive(second,true,20);
        var bought=replenished.purchase(design,tech);
        long purchasedId=bought.ships().get(4).id();
        check(bought.squadronOf(purchasedId)==0&&bought.squadronOf(first)==1&&
                        bought.squadronOf(third)==2,
                "New hull inherited a prior captain's squadron or moved another hull");
        String encoded=CampaignFleetCodec.encode(bought);
        var decoded=CampaignFleetCodec.decode(encoded,tech);
        check(encoded.startsWith("CD-ROSTER-4\n")&&
                        encoded.equals(CampaignFleetCodec.encode(decoded))&&
                        decoded.squadronOf(first)==1&&decoded.squadronOf(second)==1&&
                        decoded.profile(2).naming()==FleetNames.Style.NEOCLASSICAL,
                "Atomic roster serialization lost group culture, members or radio voice");
        reject(()->CampaignFleetCodec.decode(encoded.replace(",GRIMDARK:",",SATIRICAL:"),tech),
                "Mutating an encoded squadron should invalidate the checksum");
        reject(()->groups.assignSquadron(12345L,1),
                "Unknown tactical ID allowed as commissioned squadron member");
        reject(()->groups.assignSquadron(first,CampaignFleet.SQUADRON_COUNT),
                "Squadron number outside group range accepted");
        reject(()->groups.setSquadronProfile(0,FleetNames.Style.ABSURDIST,
                CrewChatter.Tone.VETERAN),"Default squadron override accepted");
        Map<Long,Integer> stolen=new HashMap<>(groups.squadronAssignments());
        stolen.put(VesselIdentifiers.commissioned(10,22),1);
        reject(()->new CampaignFleet.Roster(groups.faction(),groups.style(),
                groups.nextSerial(),groups.ships(),groups.namingSeed(),groups.radioTone(),
                stolen,groups.squadronProfiles()),
                "An unrelated sixteen-digit hull received membership without ownership");
        Battle combat=new Battle(2187);
        List<Long> sortie=bought.active().stream().map(CampaignFleet.Vessel::id).toList();
        for(var owned:bought.active()){
            combat.spawnWing(0,owned.fitting(),tech,1,310,330);
            var ship=combat.ships().get(combat.ships().size()-1);
            owned.stores().apply(ship);
            ship.commissionedId=owned.id();
            ship.name=owned.name();
            ship.squadronRadio=bought.radioFor(owned.id());
        }
        var shipOne=combat.ships().get(0);
        var shipTwo=combat.ships().get(1);
        var shipThree=combat.ships().get(2);
        combat.setTeamRadioTone(0,CrewChatter.Tone.PROFESSIONAL);
        String overridden=combat.radioMessage(shipOne,CrewChatter.Event.DAMAGE,false);
        shipOne.squadronRadio=null;
        String fleetDefault=combat.radioMessage(shipOne,CrewChatter.Event.DAMAGE,false);
        check(!overridden.equals(fleetDefault)&&
                combat.radioMessage(shipThree,CrewChatter.Event.DAMAGE,true)
                        .contains(" / Flight: "),
                "Squadron radio override did not reach class-specific live combat subtitle");
        shipOne.hull=0;
        shipTwo.escaped=true;
        var recovered=bought.afterBattle(combat,sortie);
        check(recovered.ships().size()==bought.ships().size()-1&&
                recovered.squadronOf(second)==1&&
                recovered.squadronOf(third)==2&&
                recovered.squadronOf(purchasedId)==0&&
                !recovered.squadronAssignments().containsKey(first)&&
                !recovered.get(second).deployed(),
                "Destroyed hull kept group membership, or survivor lost squadron on retreat");
        check(recovered.profile(1).radio()==CrewChatter.Tone.LIGHTHEARTED,
                "Casualty settlement reset squadron voice profile");
        var moved=recovered.assignSquadron(second,0);
        check(moved.squadronOf(second)==0&&moved.squadronShips(1).isEmpty(),
                "Moving an owned hull back to fleet default did not clear its group");
        System.out.println("PASS commissioned squadrons: 8 persistent groups, mixed styles/voices, "+
                "actual battle subtitle overrides, unique ID ownership, v4 checksum, "+
                "paid service, reserve, casualties and 16-digit name independence");
    }
}
