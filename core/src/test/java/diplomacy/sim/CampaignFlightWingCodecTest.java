package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact paid-aircraft migration and mixed organic/FTR campaign save bridge. */
public final class CampaignFlightWingCodecTest {
    private CampaignFlightWingCodecTest(){}
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static void reject(Runnable operation,String message){
        boolean refused=false;
        try{operation.run();}catch(IllegalArgumentException expected){refused=true;}
        check(refused,message);
    }
    public static void run(TechnologyCatalog tech){
        Shipyard.Design nativeCarrier=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CARRIER);
        var legacyRoster=CampaignFleet.startSolo(Battle.Faction.TERRAN,
                nativeCarrier,tech,998711L);
        long legacyId=legacyRoster.ships().get(0).id();
        int nativePaid=legacyRoster.get(legacyId).stores().fighters();
        var migrated=CampaignFlightWingCodec.decode(
                CampaignFleetCodec.encode(legacyRoster),tech);
        check(migrated.wing(legacyId).paid()==nativePaid&&
                        migrated.wing(legacyId).wings().stream().allMatch(
                                w->w.bayId().startsWith("ORG:")&&
                                        w.type()==nativeCarrier.fighterHull),
                "Legacy campaign must retain purchased aircraft on native decks");
        check(CampaignFleetCodec.encode(migrated.roster()).equals(
                        CampaignFleetCodec.encode(legacyRoster)),
                "Legacy migration must not rewrite commissioned ship inventory");

        int ftrSlot=-1;
        for(Shipyard.Slot slot:nativeCarrier.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                ftrSlot=slot.index();
                break;
            }
        check(ftrSlot>=0,"Carrier needs a real support socket for auxiliary flight deck");
        Shipyard.Design mixedDesign=nativeCarrier.install(ftrSlot,"BASE-TER-FTR");
        check(Shipyard.inspect(mixedDesign,tech).valid(),
                "Carrier with physical auxiliary hangar must be serviceable");
        var roster=CampaignFleet.startSolo(Battle.Faction.TERRAN,
                mixedDesign,tech,998712L);
        long id=roster.ships().get(0).id();
        int paid=roster.get(id).stores().fighters();
        var bays=FlightWingManifest.bayCapacities(mixedDesign,tech);
        check(bays.containsKey("ORG:0")&&bays.containsKey("FTR:"+ftrSlot),
                "Both organic and installed physical bays must exist");
        check(bays.values().stream().mapToInt(Integer::intValue).sum()==paid,
                "Commissioned fighter inventory must match real bay capacities");
        List<FlightWingManifest.FlightWing> wings=new ArrayList<>();
        int index=0;
        for(var bay:bays.entrySet()){
            int amount=bay.getValue();
            wings.add(new FlightWingManifest.FlightWing(bay.getKey(),
                    index++%2==0?FighterModel.Hull.INTERCEPTOR:
                            FighterModel.Hull.TORPEDO_BOMBER,
                    amount/2,amount-amount/2,0,0,0,0));
        }
        var complement=FlightWingManifest.of(mixedDesign,tech,paid,wings);
        var saved=new CampaignFlightWingCodec.Saved(roster,Map.of(id,complement));
        String encoded=CampaignFlightWingCodec.encode(saved,tech);
        var restored=CampaignFlightWingCodec.decode(encoded,tech);
        check(restored.roster().get(id).stores().fighters()==paid,
                "Campaign reload changed paid fighter inventory");
        check(restored.wing(id).wings().equals(complement.wings())&&
                        restored.wing(id).wings().stream().map(
                                FlightWingManifest.FlightWing::type).distinct().count()==2,
                "Mixed physical fighter types and bay IDs lost across reload");
        check(CampaignFlightWingCodec.encode(restored,tech).equals(encoded),
                "Campaign flight wing envelope must round-trip byte-identically");
        check(restored.wing(id).forRefit(mixedDesign,mixedDesign,tech,paid)
                        .wings().equals(complement.wings()),
                "Compatible equipment refit must retain paid physical wings");
        Shipyard.Design removed=mixedDesign.install(ftrSlot,null);
        reject(()->restored.wing(id).forRefit(mixedDesign,removed,tech,paid),
                "Removing stocked FTR bay must not confiscate aircraft");
        reject(()->CampaignFlightWingCodec.encode(
                        new CampaignFlightWingCodec.Saved(roster,Map.of()),tech),
                "Missing wing ownership must not silently replace stock");
        reject(()->CampaignFlightWingCodec.encode(
                        new CampaignFlightWingCodec.Saved(roster,Map.of(
                                id,FlightWingManifest.legacy(mixedDesign,tech,0))),tech),
                "Save must reject a manifest that omits purchased fighters");
        reject(()->CampaignFlightWingCodec.encode(
                        new CampaignFlightWingCodec.Saved(roster,Map.of(
                                id+1,complement)),tech),
                "Unknown commissioned ship must not inherit fighter inventory");
        List<FlightWingManifest.FlightWing> duplicate=new ArrayList<>(wings);
        duplicate.add(wings.get(0));
        reject(()->FlightWingManifest.of(mixedDesign,tech,paid,duplicate),
                "Repeated physical bay ID must fail");
        var first=bays.entrySet().iterator().next();
        reject(()->FlightWingManifest.of(mixedDesign,tech,first.getValue()+1,
                List.of(new FlightWingManifest.FlightWing(first.getKey(),
                        FighterModel.Hull.INTERCEPTOR,0,first.getValue()+1,0,0,0,0))),
                "Overcapacity wing must fail");
        int dataStart=encoded.indexOf('\n')+1;
        char altered=encoded.charAt(dataStart)=='A'?'B':'A';
        String tampered=encoded.substring(0,dataStart)+altered+
                encoded.substring(dataStart+1);
        reject(()->CampaignFlightWingCodec.decode(tampered,tech),
                "Tampered campaign flight manifest must fail checksum");
        System.out.println("PASS campaign flight wing codec: legacy stock, organic/FTR mixed bays, "
                +"save/reload, refit guard, ownership and checksum");
    }
}
