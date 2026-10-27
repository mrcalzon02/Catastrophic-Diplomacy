package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Durable campaign progression and purchased faction research licenses.
 * Research debit and entitlement share the same checksummed save record.
 */
public final class CampaignProgress {
    private static final String MAGIC="CD-CAMPAIGN-2";
    private static final String LEGACY="CD-CAMPAIGN-1";
    private CampaignProgress() {}
    public record State(Battle.Faction faction,int unlocked,long credits,int victories,int researchRank) {
        public State(Battle.Faction faction,int unlocked,long credits,int victories) {
            this(faction,unlocked,credits,victories,0);
        }
        public State {
            if(faction==null||unlocked<1||unlocked>LevelEconomy.LEVEL_COUNT
                    ||credits<0||victories<0||researchRank<0||researchRank>6)
                throw new IllegalArgumentException("Invalid campaign state");
        }
        public boolean available(int level){return level>=1&&level<=unlocked;}
        public State spend(long cost){
            if(cost<0||cost>credits)throw new IllegalArgumentException("Insufficient campaign credits");
            return new State(faction,unlocked,credits-cost,victories,researchRank);
        }
        public long nextResearchCost(){
            return researchRank==6?0:LevelEconomy.draftResearchCost(researchRank+1);
        }
        /** Sequential license acquisition: no skipping and no credit duplication. */
        public State purchaseNextResearch(){
            if(researchRank>=6)throw new IllegalArgumentException("All six research ranks unlocked");
            long cost=nextResearchCost();
            if(credits<cost)throw new IllegalArgumentException("Insufficient campaign credits for research");
            return new State(faction,unlocked,credits-cost,victories,researchRank+1);
        }
        public State complete(int level,int deployed,int survivors){
            if(!available(level))throw new IllegalArgumentException("Mission is locked");
            if(survivors<1)throw new IllegalArgumentException("No victory without surviving ships");
            long reward=LevelEconomy.victoryCredits(LevelEconomy.level(level),deployed,survivors,true);
            return new State(faction,Math.min(LevelEconomy.LEVEL_COUNT,Math.max(unlocked,level+1)),
                    Math.addExact(credits,reward),Math.addExact(victories,1),researchRank);
        }
    }
    public static State start(Battle.Faction faction){return new State(faction,1,0,0,0);}
    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Campaign progress required");
        String body=MAGIC+"\n"+state.faction().name()+"\n"+state.unlocked()+"\n"+
                state.credits()+"\n"+state.victories()+"\n"+state.researchRank()+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(checksum.getValue())+"\n";
    }
    public static State decode(String save){
        if(save==null||save.length()>500)throw new IllegalArgumentException("Campaign save missing or oversized");
        String[] rows=save.split("\\n",-1);
        boolean legacy=rows.length==7&&LEGACY.equals(rows[0]);
        if(!(legacy||rows.length==8&&MAGIC.equals(rows[0]))||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unsupported campaign save");
        int checksumLine=rows.length-2;
        StringBuilder body=new StringBuilder();
        for(int i=0;i<checksumLine;i++)body.append(rows[i]).append('\n');
        CRC32 checksum=new CRC32();checksum.update(body.toString().getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(checksum.getValue()).equals(rows[checksumLine]))
            throw new IllegalArgumentException("Campaign checksum mismatch");
        try {
            return new State(Battle.Faction.valueOf(rows[1]),Integer.parseInt(rows[2]),
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                    legacy?0:Integer.parseInt(rows[5]));
        }catch(RuntimeException wrong){
            throw new IllegalArgumentException("Invalid campaign save fields",wrong);
        }
    }
}
