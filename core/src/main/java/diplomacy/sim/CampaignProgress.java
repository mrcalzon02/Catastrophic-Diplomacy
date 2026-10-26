package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Checksum-protected single-player progression, independent of rendering. */
public final class CampaignProgress {
    private static final String MAGIC="CD-CAMPAIGN-1";
    private CampaignProgress() {}
    public record State(Battle.Faction faction,int unlocked,long credits,int victories) {
        public State {
            if(faction==null||unlocked<1||unlocked>LevelEconomy.LEVEL_COUNT
                    ||credits<0||victories<0)throw new IllegalArgumentException("Invalid campaign state");
        }
        public boolean available(int level){return level>=1&&level<=unlocked;}
        public State complete(int level,int deployed,int survivors){
            if(!available(level))throw new IllegalArgumentException("Mission is locked");
            if(survivors<1)throw new IllegalArgumentException("No victory without surviving ships");
            long reward=LevelEconomy.victoryCredits(LevelEconomy.level(level),deployed,survivors,true);
            return new State(faction,Math.min(LevelEconomy.LEVEL_COUNT,Math.max(unlocked,level+1)),
                    Math.addExact(credits,reward),Math.addExact(victories,1));
        }
    }
    public static State start(Battle.Faction faction){return new State(faction,1,0,0);}
    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Campaign progress required");
        String body=MAGIC+"\n"+state.faction().name()+"\n"+state.unlocked()+"\n"+
                state.credits()+"\n"+state.victories()+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(checksum.getValue())+"\n";
    }
    public static State decode(String save){
        if(save==null)throw new IllegalArgumentException("Campaign save missing");
        String[] rows=save.split("\\n",-1);
        if(rows.length!=7||!MAGIC.equals(rows[0])||!rows[6].isEmpty())
            throw new IllegalArgumentException("Unsupported campaign save");
        String body=String.join("\n",rows[0],rows[1],rows[2],rows[3],rows[4])+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(checksum.getValue()).equals(rows[5]))
            throw new IllegalArgumentException("Campaign checksum mismatch");
        try {
            return new State(Battle.Faction.valueOf(rows[1]),Integer.parseInt(rows[2]),
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]));
        }catch(RuntimeException wrong){
            throw new IllegalArgumentException("Invalid campaign save fields",wrong);
        }
    }
}
