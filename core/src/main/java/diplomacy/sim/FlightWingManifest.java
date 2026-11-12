package diplomacy.sim;

import diplomacy.shipyard.HullBaseline;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned, lossless physical-bay fighter complement for campaign flight-deck
 * integration. The existing campaign Stores.fighters remains the paid inventory
 * authority until the campaign codec and combat launch lane consume this model.
 * This class never creates fighters or changes the live legacy launch rules.
 */
public final class FlightWingManifest {
    private static final String MAGIC="CD-FLIGHT-WINGS-1";
    private final List<FlightWing> wings;

    /** Destroyed is historical loss and is NOT part of paid surviving inventory. */
    public record FlightWing(String bayId,FighterModel.Hull type,
                             int stored,int ready,int launched,int returning,
                             int servicing,int destroyed) {
        public FlightWing {
            if(bayId==null||!bayId.matches("(ORG|FTR):[0-9]+")||type==null||
                    stored<0||ready<0||launched<0||returning<0||
                    servicing<0||destroyed<0||destroyed>1_000_000)
                throw new IllegalArgumentException("Invalid flight wing");
            Math.addExact(Math.addExact(Math.addExact(stored,ready),
                    Math.addExact(launched,returning)),servicing);
        }
        public int paid(){
            return Math.addExact(Math.addExact(Math.addExact(stored,ready),
                    Math.addExact(launched,returning)),servicing);
        }
    }

    private FlightWingManifest(List<FlightWing> source){
        wings=List.copyOf(source);
    }
    public List<FlightWing> wings(){return wings;}
    public int paid(){
        int count=0;
        for(FlightWing wing:wings)count=Math.addExact(count,wing.paid());
        return count;
    }

    /**
     * Physical capacity, not launch throughput. Native storage is distributed
     * across the real organic catapults; FTR storage is the exact marginal
     * contribution of that fitted socket under the actual technology catalog.
     * Removing one FTR for the calculation preserves every other fitted socket.
     */
    public static Map<String,Integer> bayCapacities(Shipyard.Design design,
                                                     TechnologyCatalog technology){
        if(design==null||technology==null)
            throw new IllegalArgumentException("Missing ship fitting or technology");
        int total=Shipyard.fit(design,technology).fighterCapacity();
        int nativeStock=HullBaseline.builtInFighterCapacity(design.hull);
        int nativeBays=HullBaseline.builtInLaunchBays(design.hull);
        if(nativeStock>0&&nativeBays==0)
            throw new IllegalStateException("Native fighter storage has no physical deck");
        LinkedHashMap<String,Integer> capacities=new LinkedHashMap<>();
        for(int n=0;n<nativeBays;n++)
            capacities.put("ORG:"+n,nativeStock/nativeBays+
                    (n<nativeStock%nativeBays?1:0));
        for(Shipyard.Slot slot:design.hull.slots){
            Shipyard.Installed installed=design.at(slot.index());
            if(slot.mount()!=Shipyard.Mount.SUPPORT||installed==null||
                    !installed.component.endsWith("-FTR"))continue;
            // Capacity probing removes this hangar only. A saved OPTICAL/RADAR
            // doctrine may be licensed solely by the removed FTR, making that
            // temporary comparison blueprint invalid even though seeker choice
            // cannot affect storage volume. Neutralize seeker doctrine for the
            // no-hangar comparison rather than rejecting the legal source fit.
            Shipyard.Design comparison=design.install(slot.index(),null)
                    .withFighterSeeker(FighterModel.Seeker.INFRARED);
            int without=Shipyard.fit(comparison,technology).fighterCapacity();
            int capacity=total-without;
            if(capacity<=0)throw new IllegalStateException("FTR socket has no storage");
            capacities.put("FTR:"+slot.index(),capacity);
        }
        int counted=0;
        for(int capacity:capacities.values())counted=Math.addExact(counted,capacity);
        if(counted!=total)
            throw new IllegalStateException("Physical fighter bays do not match fitted storage");
        return java.util.Collections.unmodifiableMap(capacities);
    }

    /** Refuse missing, duplicate, or overfilled physical bays and phantom stock. */
    public static FlightWingManifest of(Shipyard.Design design,TechnologyCatalog technology,
                                        int paidFighters,List<FlightWing> source){
        if(paidFighters<0||source==null)throw new IllegalArgumentException("Invalid paid stock");
        Map<String,Integer> capacities=bayCapacities(design,technology);
        HashSet<String> seen=new HashSet<>();
        ArrayList<FlightWing> checked=new ArrayList<>(source.size());
        int accounted=0;
        for(FlightWing wing:source){
            if(wing==null||!seen.add(wing.bayId())||
                    !capacities.containsKey(wing.bayId())||
                    wing.paid()>capacities.get(wing.bayId()))
                throw new IllegalArgumentException("Unknown, repeated or overfilled fighter bay");
            accounted=Math.addExact(accounted,wing.paid());
            checked.add(wing);
        }
        if(accounted!=paidFighters)
            throw new IllegalArgumentException("Flight wings do not match paid fighter inventory");
        checked.sort(Comparator.comparing(FlightWing::bayId));
        return new FlightWingManifest(checked);
    }

    /** Legacy CD-SHIP-1/2 (and later single-type ships) retain only paid stock. */
    public static FlightWingManifest legacy(Shipyard.Design design,
                                             TechnologyCatalog technology,int paidFighters){
        if(paidFighters<0)throw new IllegalArgumentException("Negative paid fighter stock");
        ArrayList<FlightWing> allocated=new ArrayList<>();
        int remaining=paidFighters;
        for(var bay:bayCapacities(design,technology).entrySet()){
            int count=Math.min(remaining,bay.getValue());
            if(count>0)allocated.add(new FlightWing(bay.getKey(),design.fighterHull,
                    0,count,0,0,0,0));
            remaining-=count;
        }
        if(remaining!=0)throw new IllegalArgumentException("Paid fighters exceed fitted bays");
        return of(design,technology,paidFighters,allocated);
    }

    /**
     * Refits may preserve an existing complement only when all its real bay IDs
     * and paid capacities survive. Removing a stocked bay fails closed rather
     * than deleting or silently reallocating purchased fighters.
     */
    public FlightWingManifest forRefit(Shipyard.Design oldDesign,Shipyard.Design replacement,
                                       TechnologyCatalog technology,int paidFighters){
        if(oldDesign==null||replacement==null||
                oldDesign.faction!=replacement.faction||oldDesign.hull!=replacement.hull)
            throw new IllegalArgumentException("Refit changes vessel identity");
        of(oldDesign,technology,paidFighters,wings);
        return of(replacement,technology,paidFighters,wings);
    }

    /** Deterministic, SHA-256 checked standalone manifest for later roster embedding. */
    public String encode(){
        StringBuilder body=new StringBuilder(MAGIC).append((char)10);
        for(FlightWing w:wings)
            body.append(w.bayId()).append(',').append(w.type()).append(',')
                    .append(w.stored()).append(',').append(w.ready()).append(',')
                    .append(w.launched()).append(',').append(w.returning()).append(',')
                    .append(w.servicing()).append(',').append(w.destroyed())
                    .append((char)10);
        String source=body.toString();
        return source+sha256(source)+(char)10;
    }
    public static FlightWingManifest decode(String encoded,Shipyard.Design design,
                                             TechnologyCatalog technology,int paidFighters){
        if(encoded==null||encoded.length()>30000)
            throw new IllegalArgumentException("Invalid flight wing manifest");
        String[] lines=encoded.split(String.valueOf((char)10),-1);
        if(lines.length<3||lines.length>100||!MAGIC.equals(lines[0])||
                !lines[lines.length-1].isEmpty())
            throw new IllegalArgumentException("Unknown flight wing manifest version");
        StringBuilder body=new StringBuilder();
        for(int i=0;i<lines.length-2;i++)body.append(lines[i]).append((char)10);
        String actual=sha256(body.toString());
        if(!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                lines[lines.length-2].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Flight wing manifest checksum mismatch");
        try{
            ArrayList<FlightWing> result=new ArrayList<>();
            for(int i=1;i<lines.length-2;i++){
                String[] p=lines[i].split(",",-1);
                if(p.length!=8)throw new IllegalArgumentException("Malformed flight wing row");
                result.add(new FlightWing(p[0],FighterModel.Hull.valueOf(p[1]),
                        Integer.parseInt(p[2]),Integer.parseInt(p[3]),
                        Integer.parseInt(p[4]),Integer.parseInt(p[5]),
                        Integer.parseInt(p[6]),Integer.parseInt(p[7])));
            }
            return of(design,technology,paidFighters,result);
        }catch(RuntimeException invalid){
            throw new IllegalArgumentException("Invalid physical fighter complement",invalid);
        }
    }
    private static String sha256(String value){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte b:bytes){
                result.append(Character.forDigit((b>>>4)&15,16));
                result.append(Character.forDigit(b&15,16));
            }
            return result.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException("Java 17 SHA-256 unavailable",impossible);
        }
    }
}
