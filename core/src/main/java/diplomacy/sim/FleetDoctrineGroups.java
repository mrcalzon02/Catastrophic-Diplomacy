package diplomacy.sim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Applies the editable combat doctrine of one captain to a selected group.
 * Individual protected-ship slots and escort leader references must never be
 * copied to unrelated hulls: that would create self-escorts, invalid carrier
 * assignments and reciprocal escort cycles.
 */
public final class FleetDoctrineGroups {
    private FleetDoctrineGroups() {}

    public static CampaignDeployment.Loadout apply(CampaignDeployment.Loadout fleet,
                                                     int source,Set<Integer> recipients) {
        if(fleet==null||source<0||source>=fleet.ships()||recipients==null)
            throw new IllegalArgumentException("Invalid doctrine group");
        HashSet<Integer> selected=new HashSet<>(recipients);
        for(Integer slot:selected)
            if(slot==null||slot<0||slot>=fleet.ships())
                throw new IllegalArgumentException("Unknown commissioned ship in group");
        selected.remove(source);
        if(selected.isEmpty())
            throw new IllegalArgumentException("Group must include a different commissioned ship");

        ShipStandingOrders leader=fleet.standingFor(source);
        ShipTacticalDirectives common=leader.directives();
        HashMap<Integer,ShipStandingOrders> revised=new HashMap<>(fleet.standings());
        for(int slot:selected){
            ShipTacticalDirectives own=fleet.standingFor(slot).directives();
            ShipTacticalDirectives combined=new ShipTacticalDirectives(
                    common.speedPercent(),common.interceptStrategy(),common.defenseScope(),
                    common.fighterMission(),
                    own.defendSlots(),own.escortSlot(),common.escortRange());
            revised.put(slot,new ShipStandingOrders(leader.tactics(),leader.aggression(),
                    leader.caution(),leader.shieldWithdrawPercent(),combined));
        }
        // Build once: atomic validation of every policy, relationship, fleet
        // position and old movement queue, even for a full 200-ship group.
        return new CampaignDeployment.Loadout(fleet.ships(),fleet.formation(),
                fleet.placements(),fleet.orders(),revised);
    }
}
