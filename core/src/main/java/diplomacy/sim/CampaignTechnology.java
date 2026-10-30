package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;

/**
 * Faction-owned, individually funded technology discoveries. The shipyard only
 * configures already discovered technology; it never awards discoveries.
 * Saved alongside wallet and fleet in the authoritative atomic campaign ledger.
 */
public final class CampaignTechnology {
    private static final String MAGIC="CD-TECHNOLOGY-1";
    private static final int MAX_SAVE=300_000;
    private CampaignTechnology(){}

    public static String factionCode(Battle.Faction faction){
        return switch(faction){
            case TERRAN -> "TER";
            case DRAXIAN -> "DRA";
            case CARNELIAN -> "CAR";
            case ZIPPLEDROW -> "ZIP";
            case UZIPT -> "UZI";
            case MANDRALLY -> "MAN";
            case VAERITH_PRISM -> "VAE";
            case ZELTHAR_LUMINARI -> "ZEL";
            case AUREATE_SYNOD -> "AUR";
            case NYX_BIOLITH -> "NYX";
            case UMBRA_FOUNDRY -> "UMB";
        };
    }
    public record Quote(int cost,boolean allowed,String reason) { }

    public record State(Battle.Faction faction,Set<String> unlocked,
                        String activeId,int progress,int duration) {
        public State(Battle.Faction faction){this(faction,Set.of(),null,0,0);}
        public State {
            if(faction==null||unlocked==null||unlocked.size()>14000||
                    progress<0||duration<0||duration>9)
                throw new IllegalArgumentException("Invalid technology inventory");
            unlocked=Set.copyOf(unlocked);
            String prefix="TECH-"+factionCode(faction)+"-";
            for(String id:unlocked)
                if(id==null||!id.startsWith(prefix)||!id.matches("TECH-[A-Z]{3}-[A-Z]{2,4}-(OUTPUT|EFFICIENCY|RESILIENCE|SPECIALIST)-0[1-9]"))
                    throw new IllegalArgumentException("Foreign or malformed technology unlock "+id);
            if(activeId==null){
                if(progress!=0||duration!=0)
                    throw new IllegalArgumentException("Idle technology has outstanding research work");
            }else if(!activeId.startsWith(prefix)||unlocked.contains(activeId)||
                    !activeId.matches("TECH-[A-Z]{3}-[A-Z]{2,4}-(OUTPUT|EFFICIENCY|RESILIENCE|SPECIALIST)-0[1-9]")||
                    duration<1||progress>=duration)
                throw new IllegalArgumentException("Invalid funded technology project");
        }
        public boolean has(String id){return unlocked.contains(id);}
        public int remaining(){return activeId==null?0:duration-progress;}
        public Quote quote(String id,CampaignProgress.State wallet,TechnologyCatalog catalog){
            if(wallet==null||catalog==null||wallet.faction()!=faction)
                throw new IllegalArgumentException("Campaign technology and wallet faction mismatch");
            TechnologyCatalog.Upgrade choice=catalog.get(id);
            if(choice==null)return new Quote(0,false,"UNKNOWN TECHNOLOGY");
            if(!choice.faction.equals(factionCode(faction)))
                return new Quote(choice.researchCost,false,"FOREIGN FACTION TECHNOLOGY");
            String blocker=null;
            if(has(id))blocker="ALREADY RESEARCHED";
            else if(activeId!=null)blocker="COMPLETE CURRENT TECHNOLOGY PROJECT";
            else if(choice.stage>wallet.researchRank())
                blocker="REQUIRES ENGINEERING LICENSE "+choice.stage;
            else if(choice.stage>1&&!has(choice.prerequisite))
                blocker="REQUIRES "+choice.prerequisite;
            else if(wallet.credits()<choice.researchCost)
                blocker="INSUFFICIENT RESEARCH BUDGET";
            return new Quote(choice.researchCost,blocker==null,
                    blocker==null?"READY TO AUTHORIZE":blocker);
        }
        /** Caller atomically commits wallet.spend(quote.cost) with returned state. */
        public State begin(String id,CampaignProgress.State wallet,TechnologyCatalog catalog){
            Quote quote=quote(id,wallet,catalog);
            if(!quote.allowed())throw new IllegalArgumentException(quote.reason());
            TechnologyCatalog.Upgrade upgrade=catalog.get(id);
            // Each victory is one research tick; accelerators never eliminate
            // the need for a successful mission for a new discovery.
            int work=Math.max(1,upgrade.stage-wallet.speedUpgrades());
            return new State(faction,unlocked,id,0,work);
        }
        /** Only called as part of a successful mission settlement. */
        public State victory(){
            if(activeId==null)return this;
            if(progress+1<duration)return new State(faction,unlocked,activeId,progress+1,duration);
            Set<String> discovered=new HashSet<>(unlocked);
            discovered.add(activeId);
            return new State(faction,discovered,null,0,0);
        }
        public void validateCatalog(TechnologyCatalog catalog){
            if(catalog==null)throw new IllegalArgumentException("Technology catalog required");
            for(String id:unlocked){
                TechnologyCatalog.Upgrade u=catalog.get(id);
                if(u==null||!u.faction.equals(factionCode(faction))||
                        (u.stage>1&&!unlocked.contains(u.prerequisite)))
                    throw new IllegalArgumentException("Invalid researched prerequisite chain "+id);
            }
            if(activeId!=null){
                TechnologyCatalog.Upgrade u=catalog.get(activeId);
                if(u==null||!u.faction.equals(factionCode(faction))||
                        (u.stage>1&&!unlocked.contains(u.prerequisite)))
                    throw new IllegalArgumentException("Invalid active technology project "+activeId);
            }
        }
    }

    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Missing technology state");
        String ids=String.join(",",new TreeSet<>(state.unlocked()));
        String body=MAGIC+"\n"+state.faction().name()+"\n"+
                (state.activeId()==null?"-":state.activeId())+"\n"+
                state.progress()+"\n"+state.duration()+"\n"+
                (ids.isEmpty()?"-":ids)+"\n";
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        String result=body+Long.toHexString(crc.getValue())+"\n";
        if(result.length()>MAX_SAVE)throw new IllegalArgumentException("Technology save exceeds capacity");
        return result;
    }
    public static State decode(String input,TechnologyCatalog catalog){
        if(input==null||input.length()>MAX_SAVE)
            throw new IllegalArgumentException("Missing or oversized technology save");
        String[] rows=input.split("\\n",-1);
        if(rows.length!=8||!MAGIC.equals(rows[0])||!rows[7].isEmpty())
            throw new IllegalArgumentException("Unsupported technology save");
        String body=String.join("\n",rows[0],rows[1],rows[2],rows[3],rows[4],rows[5])+"\n";
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(crc.getValue()).equals(rows[6]))
            throw new IllegalArgumentException("Technology save checksum mismatch");
        try{
            Set<String> ids="-".equals(rows[5])?Set.of():new HashSet<>(List.of(rows[5].split(",",-1)));
            State state=new State(Battle.Faction.valueOf(rows[1]),ids,
                    "-".equals(rows[2])?null:rows[2],
                    Integer.parseInt(rows[3]),Integer.parseInt(rows[4]));
            state.validateCatalog(catalog);
            return state;
        }catch(RuntimeException bad){
            throw new IllegalArgumentException("Invalid technology project or unlocks",bad);
        }
    }
}
