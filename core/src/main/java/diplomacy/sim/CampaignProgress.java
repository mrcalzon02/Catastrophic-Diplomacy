package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Checksummed campaign progress with paid, mission-timed research.
 * Each successful mission, including a replay, advances one active research
 * project by ONE mission tick. An unfinished or lost battle advances none.
 * Research acceleration can reduce duration to zero but never below zero.
 */
public final class CampaignProgress {
    private static final String MAGIC="CD-CAMPAIGN-3";
    private static final String PREVIOUS="CD-CAMPAIGN-2";
    private static final String LEGACY="CD-CAMPAIGN-1";
    private CampaignProgress() {}

    public record State(Battle.Faction faction,int unlocked,long credits,int victories,
                        int researchRank,int highestCompleted,int researchTarget,
                        int researchProgress,int researchSpeed) {
        /** Legacy API: keeps old tests, saves and callers source compatible. */
        public State(Battle.Faction faction,int unlocked,long credits,int victories){
            this(faction,unlocked,credits,victories,0,Math.max(0,unlocked-1),0,0,0);
        }
        public State(Battle.Faction faction,int unlocked,long credits,int victories,int researchRank){
            this(faction,unlocked,credits,victories,researchRank,Math.max(0,unlocked-1),0,0,0);
        }
        public State {
            if(faction==null || unlocked<1 || unlocked>LevelEconomy.LEVEL_COUNT
                    || credits<0 || victories<0 || researchRank<0 || researchRank>6
                    || highestCompleted<0 || highestCompleted>LevelEconomy.LEVEL_COUNT
                    || unlocked!=Math.min(LevelEconomy.LEVEL_COUNT,highestCompleted+1)
                    || researchSpeed<0 || researchSpeed>4
                    || researchTarget<0 || researchTarget>6 || researchProgress<0
                    || (researchTarget==0&&researchProgress!=0)
                    || (researchTarget!=0&&
                        (researchRank>=6 || researchTarget!=researchRank+1
                         || researchProgress>=LevelEconomy.researchMissionCost(researchTarget)-researchSpeed
                            && LevelEconomy.researchMissionCost(researchTarget)-researchSpeed>0
                         || LevelEconomy.researchMissionCost(researchTarget)<=researchSpeed)))
                throw new IllegalArgumentException("Invalid campaign or research state");
        }
        public boolean available(int level){return level>=1&&level<=unlocked;}
        public boolean completed(int level){return level>=1&&level<=highestCompleted;}
        public State spend(long cost){
            if(cost<0||cost>credits)throw new IllegalArgumentException("Insufficient campaign credits");
            return new State(faction,unlocked,credits-cost,victories,researchRank,
                    highestCompleted,researchTarget,researchProgress,researchSpeed);
        }
        public long nextResearchCost(){
            return researchRank==6?0:LevelEconomy.draftResearchCost(researchRank+1);
        }
        public int researchDuration(){
            return researchTarget==0?0:
                    Math.max(0,LevelEconomy.researchMissionCost(researchTarget)-researchSpeed);
        }
        public int researchRemaining(){
            return researchTarget==0?0:Math.max(0,researchDuration()-researchProgress);
        }
        public boolean researchActive(){return researchTarget!=0;}
        /** Starts a paid technology project; a zero-duration project unlocks now. */
        public State purchaseNextResearch(){
            if(researchTarget!=0)throw new IllegalArgumentException("Research already underway");
            if(researchRank>=6)throw new IllegalArgumentException("All six research ranks unlocked");
            long cost=nextResearchCost();
            if(credits<cost)throw new IllegalArgumentException("Insufficient campaign credits for research");
            int target=researchRank+1;
            int duration=Math.max(0,LevelEconomy.researchMissionCost(target)-researchSpeed);
            return new State(faction,unlocked,credits-cost,victories,
                    duration==0?target:researchRank,highestCompleted,
                    duration==0?0:target,0,researchSpeed);
        }
        public int nextSpeedMilestone(){
            return researchSpeed==4?0:LevelEconomy.researchSpeedMilestone(researchSpeed+1);
        }
        public long nextSpeedCost(){
            return researchSpeed==4?0:LevelEconomy.researchSpeedCost(researchSpeed+1);
        }
        /** Four optional expensive engineering investments; active research benefits immediately. */
        public State purchaseResearchSpeed(){
            if(researchSpeed==4)throw new IllegalArgumentException("All four research accelerators owned");
            int next=researchSpeed+1;
            int milestone=nextSpeedMilestone();
            if(highestCompleted<milestone)
                throw new IllegalArgumentException("Complete campaign mission "+milestone+
                        " to unlock research accelerator "+next);
            long price=nextSpeedCost();
            if(credits<price)throw new IllegalArgumentException("Insufficient credits for research acceleration");
            int duration=researchTarget==0?0:Math.max(0,
                    LevelEconomy.researchMissionCost(researchTarget)-next);
            boolean unlockedNow=researchTarget!=0&&researchProgress>=duration;
            return new State(faction,unlocked,credits-price,victories,
                    unlockedNow?researchTarget:researchRank,highestCompleted,
                    unlockedNow?0:researchTarget,unlockedNow?0:researchProgress,next);
        }
        /** Both first-time and repeated victories advance exactly one research tick. */
        public State complete(int level,int deployed,int survivors){
            if(!available(level))throw new IllegalArgumentException("Mission is locked");
            if(survivors<1)throw new IllegalArgumentException("No victory without surviving ships");
            long full=LevelEconomy.victoryCredits(LevelEconomy.level(level),deployed,survivors,true);
            long payout=completed(level)?full/4:full; // Integer floor; no rounding inflation.
            int high=Math.max(highestCompleted,level);
            int elapsed=researchTarget==0?0:researchProgress+1;
            boolean done=researchTarget!=0&&elapsed>=researchDuration();
            return new State(faction,Math.min(LevelEconomy.LEVEL_COUNT,high+1),
                    Math.addExact(credits,payout),Math.addExact(victories,1),
                    done?researchTarget:researchRank,high,
                    done?0:researchTarget,done?0:elapsed,researchSpeed);
        }
    }
    public static State start(Battle.Faction faction){return new State(faction,1,0,0);}
    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Campaign progress required");
        String body=MAGIC+"\n"+state.faction().name()+"\n"+state.unlocked()+"\n"+
                state.credits()+"\n"+state.victories()+"\n"+state.researchRank()+"\n"+
                state.highestCompleted()+"\n"+state.researchTarget()+"\n"+
                state.researchProgress()+"\n"+state.researchSpeed()+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(checksum.getValue())+"\n";
    }
    public static State decode(String save){
        if(save==null||save.length()>800)throw new IllegalArgumentException("Campaign save missing or oversized");
        String[] rows=save.split("\\n",-1);
        boolean legacy=rows.length==7&&LEGACY.equals(rows[0]);
        boolean old=rows.length==8&&PREVIOUS.equals(rows[0]);
        boolean current=rows.length==12&&MAGIC.equals(rows[0]);
        if(!(legacy||old||current)||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unsupported campaign save");
        int checksumLine=rows.length-2;
        StringBuilder body=new StringBuilder();
        for(int i=0;i<checksumLine;i++)body.append(rows[i]).append('\n');
        CRC32 checksum=new CRC32();checksum.update(body.toString().getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(checksum.getValue()).equals(rows[checksumLine]))
            throw new IllegalArgumentException("Campaign checksum mismatch");
        try{
            int unlocked=Integer.parseInt(rows[2]);
            int high=current?Integer.parseInt(rows[6]):Math.max(0,unlocked-1);
            return new State(Battle.Faction.valueOf(rows[1]),unlocked,
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                    legacy?0:Integer.parseInt(rows[5]),high,
                    current?Integer.parseInt(rows[7]):0,
                    current?Integer.parseInt(rows[8]):0,
                    current?Integer.parseInt(rows[9]):0);
        }catch(RuntimeException wrong){
            throw new IllegalArgumentException("Invalid campaign save fields",wrong);
        }
    }
}
