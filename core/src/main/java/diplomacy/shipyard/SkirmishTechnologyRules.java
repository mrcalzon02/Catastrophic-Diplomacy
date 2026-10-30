package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import diplomacy.tech.TechnologyCatalog;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * A challenge-specific technology entitlement, deliberately independent of
 * the campaign's permanently discovered faction inventory.
 *
 * Rules are applied to fitted blueprints both in the editor and at battle
 * creation. A saved ship never acquires a new technology simply because
 * the challenge's rank cap is increased.
 */
public record SkirmishTechnologyRules(
        int maxStage, int maxHullGeneration,
        Set<Shipyard.Track> allowedTracks,Set<String> bannedTechnologyIds,
        Set<Shipyard.Hull> allowedHulls) {
    private static final String MAGIC="CD-SKIRMISH-RULES-1";
    public SkirmishTechnologyRules {
        if(maxStage<0||maxStage>9||maxHullGeneration<1||maxHullGeneration>10||
                allowedTracks==null||bannedTechnologyIds==null||allowedHulls==null||
                bannedTechnologyIds.size()>13464)
            throw new IllegalArgumentException("Invalid skirmish technology challenge");
        allowedTracks=Set.copyOf(allowedTracks);
        bannedTechnologyIds=Set.copyOf(bannedTechnologyIds);
        allowedHulls=Set.copyOf(allowedHulls);
        for(String id:bannedTechnologyIds)
            if(id==null||!id.matches("TECH-[A-Z]{3}-[A-Z]{2,4}-(OUTPUT|EFFICIENCY|RESILIENCE|SPECIALIST)-0[1-9]"))
                throw new IllegalArgumentException("Invalid barred technology: "+id);
    }
    public static SkirmishTechnologyRules unrestricted(){
        return new SkirmishTechnologyRules(9,10,EnumSet.allOf(Shipyard.Track.class),Set.of(),Set.of());
    }
    public SkirmishTechnologyRules withStage(int level){
        return new SkirmishTechnologyRules(level,maxHullGeneration,allowedTracks,bannedTechnologyIds,allowedHulls);
    }
    public SkirmishTechnologyRules withHullGeneration(int level){
        return new SkirmishTechnologyRules(maxStage,level,allowedTracks,bannedTechnologyIds,allowedHulls);
    }
    public SkirmishTechnologyRules toggleTrack(Shipyard.Track track){
        if(track==null)throw new IllegalArgumentException("No technology branch selected");
        var next=EnumSet.noneOf(Shipyard.Track.class);
        next.addAll(allowedTracks);
        if(!next.remove(track))next.add(track);
        return new SkirmishTechnologyRules(maxStage,maxHullGeneration,next,bannedTechnologyIds,allowedHulls);
    }
    public SkirmishTechnologyRules toggleTechnology(String id){
        var next=new TreeSet<>(bannedTechnologyIds);
        if(!next.remove(id))next.add(id);
        return new SkirmishTechnologyRules(maxStage,maxHullGeneration,allowedTracks,next,allowedHulls);
    }
    public SkirmishTechnologyRules withHulls(Set<Shipyard.Hull> hulls){
        return new SkirmishTechnologyRules(maxStage,maxHullGeneration,allowedTracks,bannedTechnologyIds,hulls);
    }
    public boolean permitsHull(Shipyard.Hull hull){
        return hull!=null&&HullProgression.available(hull,maxStage)&&
                ShipLineages.of(hull).generation()<=maxHullGeneration&&
                (allowedHulls.isEmpty()||allowedHulls.contains(hull));
    }
    public boolean permits(Shipyard.Design design,TechnologyCatalog catalog,int slot,
                           Shipyard.Track track,int stage){
        if(design==null||catalog==null||slot<0||slot>=design.slots()||
                stage<1||stage>maxStage||!allowedTracks.contains(track))return false;
        Shipyard.Installed module=design.at(slot);
        if(module==null)return false;
        String id=ShipTechnologyFit.id(module,track,stage);
        return catalog.get(id)!=null&&!bannedTechnologyIds.contains(id);
    }
    public void requireAllowed(Shipyard.Design design,TechnologyCatalog catalog){
        if(design==null||catalog==null)
            throw new IllegalArgumentException("Challenge needs a ship and technology catalog");
        if(!permitsHull(design.hull))
            throw new IllegalArgumentException("Hull "+design.hull+
                    " requires Tech "+HullProgression.requiredTier(design.hull)+
                    " or exceeds optional hull restrictions");
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed module=design.at(i);
            if(module==null)continue;
            if(module.mark>Math.min(4,maxStage+1))
                throw new IllegalArgumentException("Hardware mark exceeds challenge technology limit");
            if(maxStage<2&&module.pattern!=ModuleVersions.Pattern.STANDARD)
                throw new IllegalArgumentException("Advanced hardware pattern barred by skirmish rules");
            for(Shipyard.Track track:Shipyard.Track.values())
                if(module.stage(track)>0&&!permits(design,catalog,i,track,module.stage(track)))
                    throw new IllegalArgumentException("Technology restricted: "+
                            ShipTechnologyFit.id(module,track,module.stage(track)));
        }
        if(maxStage<2&&design.fighterHull!=FighterModel.Hull.ASSAULT_GUNSHIP)
            throw new IllegalArgumentException("Specialist fighter hull barred by skirmish tech limit");
    }
    public static String encode(SkirmishTechnologyRules rules){
        if(rules==null)throw new IllegalArgumentException("Rules required");
        var names=new TreeSet<String>();
        for(var track:rules.allowedTracks)names.add(track.name());
        var hulls=new TreeSet<String>();
        for(var hull:rules.allowedHulls)hulls.add(hull.name());
        String body=MAGIC+"\n"+rules.maxStage+"\n"+rules.maxHullGeneration+"\n"+
                String.join(",",names)+"\n"+String.join(",",new TreeSet<>(rules.bannedTechnologyIds))+"\n"+
                String.join(",",hulls)+"\n";
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(crc.getValue())+"\n";
    }
    public static SkirmishTechnologyRules decode(String encoded){
        if(encoded==null||encoded.length()>300000)
            throw new IllegalArgumentException("Missing or oversized skirmish rules");
        String[] rows=encoded.split("\\n",-1);
        if(rows.length!=8||!MAGIC.equals(rows[0])||!rows[7].isEmpty())
            throw new IllegalArgumentException("Unsupported skirmish rules version");
        String body=String.join("\n",rows[0],rows[1],rows[2],rows[3],rows[4],rows[5])+"\n";
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(crc.getValue()).equals(rows[6]))
            throw new IllegalArgumentException("Corrupted skirmish rules");
        try{
            var tracks=EnumSet.noneOf(Shipyard.Track.class);
            if(!rows[3].isEmpty())for(String name:rows[3].split(",",-1))
                tracks.add(Shipyard.Track.valueOf(name));
            var hulls=EnumSet.noneOf(Shipyard.Hull.class);
            if(!rows[5].isEmpty())for(String name:rows[5].split(",",-1))
                hulls.add(Shipyard.Hull.valueOf(name));
            Set<String> banned=rows[4].isEmpty()?Set.of():Set.of(rows[4].split(",",-1));
            return new SkirmishTechnologyRules(Integer.parseInt(rows[1]),
                    Integer.parseInt(rows[2]),tracks,banned,hulls);
        }catch(RuntimeException invalid){
            throw new IllegalArgumentException("Malformed skirmish challenge",invalid);
        }
    }
}
