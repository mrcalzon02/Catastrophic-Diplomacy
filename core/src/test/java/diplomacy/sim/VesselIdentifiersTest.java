package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;

/** 500 levels of commissioning: permanent 16-digit IDs are NOT vessel names,
 * blueprint designations or short-lived battle ship references. */
public final class VesselIdentifiersTest {
    private static void check(boolean pass,String note){if(!pass)throw new AssertionError(note);}
    private static void rejects(Runnable code,String note){
        try{code.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(note);
    }
    public static void main(String[] args)throws Exception{
        var all=new HashSet<Long>();
        long[] seeds={0L,73L,-1L,Long.MIN_VALUE,0x5b63ae00d217L};
        for(long seed:seeds){
            long namespace=VesselIdentifiers.prefix(seed);
            check(namespace>=10_000_000L&&namespace<=99_999_999L,
                    "Campaign namespace is not eight decimal digits");
            for(int mission=1;mission<=500;mission++)
                for(int hull=1;hull<=200;hull++){
                    int serial=(mission-1)*200+hull;
                    long id=VesselIdentifiers.commissioned(seed,serial);
                    check(VesselIdentifiers.valid(id)&&Long.toString(id).length()==16,
                            "Non-sixteen-digit commissioned ship ID");
                    check(VesselIdentifiers.serial(id)==serial,
                            "Commission number changed after ID allocation");
                    check(all.add(id),"Duplicate commissioned ship ID across campaigns");
                }
        }
        long finalId=VesselIdentifiers.commissioned(12,VesselIdentifiers.MAX_SERIAL);
        check(VesselIdentifiers.valid(finalId)&&VesselIdentifiers.serial(finalId)==
                VesselIdentifiers.MAX_SERIAL,"Final available unique serial truncated");
        rejects(()->VesselIdentifiers.commissioned(1,0),
                "Zero serial must never identify a commissioned vessel");
        rejects(()->VesselIdentifiers.commissioned(1,VesselIdentifiers.MAX_SERIAL+1),
                "A 17-digit/overflow serial should fail closed");
        rejects(()->VesselIdentifiers.serial(1234L),"Short tactical IDs accepted as permanent");
        var tech=TechnologyCatalog.loadDefault();
        var design=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var roster=CampaignFleet.start(Battle.Faction.TERRAN,design,tech,72L);
        long id=roster.ships().get(0).id();
        String originalName=roster.get(id).name();
        var renamed=roster.rename(id,"The Ashen Reliquary");
        check(renamed.get(id).name().equals("The Ashen Reliquary")&&
                !roster.get(id).name().equals(renamed.get(id).name())&&
                id==renamed.get(id).id(),"Changing ship names changed permanent IDs");
        var refitted=renamed.serviceAll(id,tech);
        check(refitted.get(id).id()==id&&refitted.get(id).name().equals("The Ashen Reliquary"),
                "Repairing a vessel changed its unique identity");
        var reserve=refitted.setActive(id,false,20).setActive(id,true,20);
        check(reserve.get(id).id()==id,"Changing deployment rewrote commissioned IDs");
        var next=reserve.purchase(design,tech);
        long newlyCommissioned=VesselIdentifiers.commissioned(72L,5);
        check(next.get(newlyCommissioned).id()==newlyCommissioned&&
                newlyCommissioned!=id&&next.get(id).name().equals("The Ashen Reliquary"),
                "Ship purchases recycled IDs or renamed other hulls");
        var recorded=CampaignFleetCodec.decode(CampaignFleetCodec.encode(next),tech);
        check(recorded.get(id).name().equals("The Ashen Reliquary")&&
                recorded.get(newlyCommissioned).id()==newlyCommissioned,
                "Roster encoding truncated long commissioned IDs");
        check(!originalName.isBlank(),"Original name should remain valid");
        System.out.println("PASS 500 missions x 200 ships x 5 campaign namespaces = "+
                all.size()+" permanent unique 16-digit IDs, independent names and battle slots");
    }
}
