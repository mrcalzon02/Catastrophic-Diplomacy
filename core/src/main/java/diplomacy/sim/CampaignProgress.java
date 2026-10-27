package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Campaign credit bank, first-clear ledger, completed licenses, and one active
 * mission-clock research project stored together in a checksummed save.
 *
 * Both a newly cleared stage and a successful replay advance the ONE active
 * project. Failed/incomplete battles advance nothing. Mission rewards are
 * reduced to one quarter on repeats, with integer rounding down.
 */
public final class CampaignProgress {
    private static final String MAGIC="CD-CAMPAIGN-3";
    private static final String V2="CD-CAMPAIGN-2";
    private static final String V1="CD-CAMPAIGN-1";
    private static final int[] LICENSE_MISSIONS={0,1,2,3,4,5,5};
    private static final int[] BOOST_MISSIONS={0,2,3,4,5};
    private static final int[] BOOST_MILESTONES={0,25,75,150,250};
    private static final long[] BOOST_PRICES={0,250000L,1100000L,5000000L,25000000L};
    private CampaignProgress() { }

    public enum Project { NONE, LICENSE, ACCELERATOR }

    public record State(Battle.Faction faction,int unlocked,long credits,int victories,
                        int researchRank,int speedUpgrades,Project activeProject,
                        int projectProgress,boolean finalMissionCompleted) {
        public State(Battle.Faction faction,int unlocked,long credits,int victories) {
            this(faction,unlocked,credits,victories,0);
        }
        /** Preserve legacy construction and decoding of already-paid licenses. */
        public State(Battle.Faction faction,int unlocked,long credits,int victories,
                     int researchRank) {
            this(faction,unlocked,credits,victories,researchRank,0,Project.NONE,0,false);
        }
        public State {
            if(faction==null||unlocked<1||unlocked>LevelEconomy.LEVEL_COUNT
                    ||credits<0||victories<0||researchRank<0||researchRank>6
                    ||speedUpgrades<0||speedUpgrades>4||activeProject==null
                    ||projectProgress<0)
                throw new IllegalArgumentException("Invalid campaign state");
            if(activeProject==Project.NONE&&projectProgress!=0)
                throw new IllegalArgumentException("Inactive project cannot have elapsed mission time");
            if(activeProject==Project.LICENSE&&
                    (researchRank==6||projectProgress>=LICENSE_MISSIONS[researchRank+1]))
                throw new IllegalArgumentException("Invalid incomplete licensed technology");
            if(activeProject==Project.ACCELERATOR&&
                    (speedUpgrades==4||projectProgress>=BOOST_MISSIONS[speedUpgrades+1]))
                throw new IllegalArgumentException("Invalid incomplete accelerator");
            if(finalMissionCompleted&&unlocked!=LevelEconomy.LEVEL_COUNT)
                throw new IllegalArgumentException("Final mission cannot predate its unlock");
        }
        public boolean available(int level){return level>=1&&level<=unlocked;}
        /** Every level before the reachable frontier has been cleared once.
         * Level 500 uses its own completed flag because it has no successor. */
        public boolean completedMission(int level){
            return level>=1&&level<=LevelEconomy.LEVEL_COUNT&&
                    (level<unlocked||(level==LevelEconomy.LEVEL_COUNT&&finalMissionCompleted));
        }
        public boolean isReplay(int level){return completedMission(level);}
        public State spend(long cost){
            if(cost<0||cost>credits)throw new IllegalArgumentException("Insufficient campaign credits");
            return new State(faction,unlocked,credits-cost,victories,researchRank,
                    speedUpgrades,activeProject,projectProgress,finalMissionCompleted);
        }
        public long nextResearchCost(){
            return researchRank==6?0:LevelEconomy.draftResearchCost(researchRank+1);
        }
        public int nextResearchMissions(){
            return researchRank==6?0:LICENSE_MISSIONS[researchRank+1];
        }
        public long nextAccelerationCost(){
            return speedUpgrades==4?0:BOOST_PRICES[speedUpgrades+1];
        }
        public int nextAccelerationMilestone(){
            return speedUpgrades==4?0:BOOST_MILESTONES[speedUpgrades+1];
        }
        public int nextAccelerationMissions(){
            return speedUpgrades==4?0:BOOST_MISSIONS[speedUpgrades+1];
        }
        /** Research is measured in mission work, never wall-clock time. */
        public int currentProjectMissions(){
            return switch(activeProject){
                case NONE -> 0;
                case LICENSE -> nextResearchMissions();
                case ACCELERATOR -> nextAccelerationMissions();
            };
        }
        public int researchPerVictory(){return 1+speedUpgrades;}
        public int missionsUntilResearchComplete(){
            if(activeProject==Project.NONE)return 0;
            int remaining=currentProjectMissions()-projectProgress;
            return (remaining+researchPerVictory()-1)/researchPerVictory();
        }
        public boolean canStart(Project desired){
            if(desired==Project.NONE||activeProject!=Project.NONE)return false;
            return switch(desired){
                case LICENSE -> researchRank<6&&credits>=nextResearchCost();
                case ACCELERATOR -> speedUpgrades<4
                        &&completedMission(nextAccelerationMilestone())
                        &&credits>=nextAccelerationCost();
                case NONE -> false;
            };
        }
        /** Spend credits once to BEGIN research. No technology is instantly unlocked. */
        public State beginResearch(Project desired){
            if(desired==null||desired==Project.NONE)
                throw new IllegalArgumentException("Choose a research project");
            if(activeProject!=Project.NONE)
                throw new IllegalArgumentException("Finish the current research project first");
            if(desired==Project.ACCELERATOR&&speedUpgrades<4
                    &&!completedMission(nextAccelerationMilestone()))
                throw new IllegalArgumentException("Complete campaign mission "+
                        nextAccelerationMilestone()+" to unlock this accelerator");
            if(desired==Project.ACCELERATOR&&speedUpgrades==4)
                throw new IllegalArgumentException("All four research accelerators are complete");
            if(desired==Project.LICENSE&&researchRank==6)
                throw new IllegalArgumentException("All six technology licenses are complete");
            long cost=desired==Project.LICENSE?nextResearchCost():nextAccelerationCost();
            if(cost>credits)throw new IllegalArgumentException("Insufficient campaign credits for research");
            return new State(faction,unlocked,credits-cost,victories,researchRank,
                    speedUpgrades,desired,0,finalMissionCompleted);
        }
        /** Backwards API compatibility: now STARTS the next license project. */
        public State purchaseNextResearch(){return beginResearch(Project.LICENSE);}
        public State complete(int level,int deployed,int survivors){
            if(!available(level))throw new IllegalArgumentException("Mission is locked");
            if(survivors<1)throw new IllegalArgumentException("No victory without surviving ships");
            boolean replay=isReplay(level);
            long full=LevelEconomy.victoryCredits(LevelEconomy.level(level),deployed,survivors,true);
            long reward=replay?full/4:full;
            int nextRank=researchRank,nextBoost=speedUpgrades,nextProgress=projectProgress;
            Project nextProject=activeProject;
            if(activeProject!=Project.NONE){
                nextProgress=Math.addExact(projectProgress,researchPerVictory());
                if(nextProgress>=currentProjectMissions()){
                    if(activeProject==Project.LICENSE)nextRank++;
                    else nextBoost++;
                    nextProject=Project.NONE;
                    nextProgress=0;
                }
            }
            return new State(faction,Math.min(LevelEconomy.LEVEL_COUNT,
                    Math.max(unlocked,level+1)),Math.addExact(credits,reward),
                    Math.addExact(victories,1),nextRank,nextBoost,
                    nextProject,nextProgress,finalMissionCompleted||level==500);
        }
    }
    public static State start(Battle.Faction faction){return new State(faction,1,0,0,0);}
    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Campaign progress required");
        String body=MAGIC+"\n"+state.faction().name()+"\n"+state.unlocked()+"\n"+
                state.credits()+"\n"+state.victories()+"\n"+state.researchRank()+"\n"+
                state.speedUpgrades()+"\n"+state.activeProject().name()+"\n"+
                state.projectProgress()+"\n"+state.finalMissionCompleted()+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(checksum.getValue())+"\n";
    }
    public static State decode(String save){
        if(save==null||save.length()>600)
            throw new IllegalArgumentException("Campaign save missing or oversized");
        String[] rows=save.split("\n",-1);
        boolean v1=rows.length==7&&V1.equals(rows[0]);
        boolean v2=rows.length==8&&V2.equals(rows[0]);
        boolean v3=rows.length==12&&MAGIC.equals(rows[0]);
        if(!(v1||v2||v3)||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unsupported campaign save");
        int checksumLine=rows.length-2;
        StringBuilder body=new StringBuilder();
        for(int i=0;i<checksumLine;i++)body.append(rows[i]).append('\n');
        CRC32 checksum=new CRC32();checksum.update(body.toString().getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(checksum.getValue()).equals(rows[checksumLine]))
            throw new IllegalArgumentException("Campaign checksum mismatch");
        try {
            if(v3){
                if(!rows[9].equals("true")&&!rows[9].equals("false"))
                    throw new IllegalArgumentException("Invalid final mission bit");
                return new State(Battle.Faction.valueOf(rows[1]),Integer.parseInt(rows[2]),
                        Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                        Integer.parseInt(rows[5]),Integer.parseInt(rows[6]),
                        Project.valueOf(rows[7]),Integer.parseInt(rows[8]),
                        Boolean.parseBoolean(rows[9]));
            }
            return new State(Battle.Faction.valueOf(rows[1]),Integer.parseInt(rows[2]),
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                    v1?0:Integer.parseInt(rows[5]));
        }catch(RuntimeException wrong){
            throw new IllegalArgumentException("Invalid campaign save fields",wrong);
        }
    }
}
