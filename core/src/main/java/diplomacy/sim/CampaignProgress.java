package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * A single checksummed authority for first-clear payouts, campaign progression,
 * paid research projects, four optional laboratory speed upgrades and mission
 * work. No wall-clock timers, free research or failed-mission work credit.
 */
public final class CampaignProgress {
    private static final String MAGIC="CD-CAMPAIGN-4";
    private static final String PREVIOUS="CD-CAMPAIGN-3";
    private static final String V2="CD-CAMPAIGN-2";
    private static final String V1="CD-CAMPAIGN-1";
    private static final int[] ACCELERATOR_WORK={0,2,3,4,5};
    private CampaignProgress() { }
    private static int requiredMissions(Project kind,int rank,int speed){
        int base=switch(kind){
            case NONE -> 0;
            case LICENSE -> rank>=6?0:LevelEconomy.researchMissionCost(rank+1);
            case ACCELERATOR -> speed>=4?0:ACCELERATOR_WORK[speed+1];
        };
        // At most four accelerators can shorten research to ONE required victory.
        return Math.max(0,base-speed);
    }
    /** A previous save can contain a project which now qualifies for immediate
     * completion after the duration-floor policy. Apply the paid entitlement once. */
    private static State restored(Battle.Faction faction,int unlocked,long credits,
            int victories,int rank,int high,int speed,Project kind,int elapsed){
        if(kind==null||speed<0||speed>4||elapsed<0||elapsed>5)
            throw new IllegalArgumentException("Invalid stored research");
        boolean finished=kind!=Project.NONE&&elapsed>=requiredMissions(kind,rank,speed);
        return new State(faction,unlocked,credits,victories,
                finished&&kind==Project.LICENSE?rank+1:rank,high,
                finished&&kind==Project.ACCELERATOR?speed+1:speed,
                finished?Project.NONE:kind,finished?0:elapsed);
    }

    public enum Project { NONE, LICENSE, ACCELERATOR }
    public record State(Battle.Faction faction,int unlocked,long credits,int victories,
                        int researchRank,int highestCompleted,int speedUpgrades,
                        Project activeProject,int projectProgress) {
        public State(Battle.Faction faction,int unlocked,long credits,int victories){
            this(faction,unlocked,credits,victories,0);
        }
        public State(Battle.Faction faction,int unlocked,long credits,int victories,int researchRank){
            this(faction,unlocked,credits,victories,researchRank,
                    Math.max(0,unlocked-1),0,Project.NONE,0);
        }
        public State {
            if(faction==null||unlocked<1||unlocked>LevelEconomy.LEVEL_COUNT
                    ||credits<0||victories<0||researchRank<0||researchRank>6
                    ||highestCompleted<0||highestCompleted>LevelEconomy.LEVEL_COUNT
                    ||unlocked!=Math.min(LevelEconomy.LEVEL_COUNT,highestCompleted+1)
                    ||speedUpgrades<0||speedUpgrades>4||activeProject==null||projectProgress<0)
                throw new IllegalArgumentException("Invalid campaign progression");
            int maxWork=requiredMissions(activeProject,researchRank,speedUpgrades);
            if((activeProject==Project.NONE&&projectProgress!=0)
                    ||(activeProject!=Project.NONE&&
                    (maxWork==0||projectProgress>=maxWork)))
                throw new IllegalArgumentException("Invalid unfinished research project");
        }
        public boolean available(int level){return level>=1&&level<=unlocked;}
        public boolean completed(int level){
            return level>=1&&level<=highestCompleted;
        }
        public boolean completedMission(int level){return completed(level);}
        public boolean isReplay(int level){return completed(level);}
        public State spend(long cost){
            if(cost<0||cost>credits)
                throw new IllegalArgumentException("Insufficient campaign credits");
            return new State(faction,unlocked,credits-cost,victories,researchRank,
                    highestCompleted,speedUpgrades,activeProject,projectProgress);
        }
        public long nextResearchCost(){
            return researchRank==6?0:LevelEconomy.draftResearchCost(researchRank+1);
        }
        public int nextResearchMissions(){
            return requiredMissions(Project.LICENSE,researchRank,speedUpgrades);
        }
        public long nextAccelerationCost(){
            return speedUpgrades==4?0:LevelEconomy.researchSpeedCost(speedUpgrades+1);
        }
        public int nextAccelerationMilestone(){
            return speedUpgrades==4?0:LevelEconomy.researchSpeedMilestone(speedUpgrades+1);
        }
        public int nextAccelerationMissions(){
            return requiredMissions(Project.ACCELERATOR,researchRank,speedUpgrades);
        }
        public int currentProjectMissions(){
            return switch(activeProject){
                case NONE -> 0;
                case LICENSE -> nextResearchMissions();
                case ACCELERATOR -> nextAccelerationMissions();
            };
        }
        /** Every successful completed mission advances exactly one research turn. */
        public int researchPerVictory(){return 1;}
        public int missionsUntilResearchComplete(){
            return activeProject==Project.NONE?0:Math.max(0,
                    currentProjectMissions()-projectProgress);
        }
        // Compatibility for saved-world/editor code referencing the original
        // project-based research interface introduced with CD-CAMPAIGN-3.
        public boolean researchActive(){return activeProject!=Project.NONE;}
        public int researchTarget(){
            return activeProject==Project.LICENSE?researchRank+1:0;
        }
        public int researchSpeed(){return speedUpgrades;}
        public int researchRemaining(){return missionsUntilResearchComplete();}
        public int researchDuration(){return currentProjectMissions();}
        public int nextSpeedMilestone(){return nextAccelerationMilestone();}
        public long nextSpeedCost(){return nextAccelerationCost();}
        public boolean canStart(Project desired){
            if(desired==null||desired==Project.NONE||activeProject!=Project.NONE)return false;
            return switch(desired){
                case NONE -> false;
                case LICENSE -> researchRank<6&&credits>=nextResearchCost();
                case ACCELERATOR -> speedUpgrades<4&&
                        completed(nextAccelerationMilestone())&&credits>=nextAccelerationCost();
            };
        }
        /** Pay upfront; even maximally accelerated research needs a victorious mission. */
        public State beginResearch(Project desired){
            if(desired==null||desired==Project.NONE)
                throw new IllegalArgumentException("Select a research project");
            if(activeProject!=Project.NONE)
                throw new IllegalArgumentException("Finish current research before starting another");
            if(desired==Project.LICENSE&&researchRank==6)
                throw new IllegalArgumentException("All six technology licenses completed");
            if(desired==Project.ACCELERATOR){
                if(speedUpgrades==4)
                    throw new IllegalArgumentException("All four laboratory upgrades completed");
                if(!completed(nextAccelerationMilestone()))
                    throw new IllegalArgumentException("Complete campaign mission "+
                            nextAccelerationMilestone()+" to research this laboratory upgrade");
            }
            long price=desired==Project.LICENSE?nextResearchCost():nextAccelerationCost();
            if(price>credits)
                throw new IllegalArgumentException("Insufficient campaign credits for research");
            int needed=requiredMissions(desired,researchRank,speedUpgrades);
            return new State(faction,unlocked,credits-price,victories,
                    needed==0&&desired==Project.LICENSE?researchRank+1:researchRank,
                    highestCompleted,
                    needed==0&&desired==Project.ACCELERATOR?speedUpgrades+1:speedUpgrades,
                    needed==0?Project.NONE:desired,0);
        }
        /** Historical entrypoint now starts paid work instead of granting a rank. */
        public State purchaseNextResearch(){return beginResearch(Project.LICENSE);}
        /** Lab speed upgrades themselves must be researched, not purchased instantly. */
        public State purchaseResearchSpeed(){return beginResearch(Project.ACCELERATOR);}
        public State complete(int level,int deployed,int survivors){
            if(!available(level))throw new IllegalArgumentException("Mission is locked");
            if(survivors<1)throw new IllegalArgumentException("No victory without surviving ships");
            long full=LevelEconomy.victoryCredits(
                    LevelEconomy.level(level),deployed,survivors,true);
            long reward=completed(level)?full/4:full;
            int nextHigh=Math.max(highestCompleted,level);
            Project nextProject=activeProject;
            int nextProgress=projectProgress;
            int nextLicense=researchRank,nextSpeed=speedUpgrades;
            if(activeProject!=Project.NONE){
                nextProgress=Math.addExact(projectProgress,1);
                if(nextProgress>=currentProjectMissions()){
                    if(activeProject==Project.LICENSE)nextLicense++;
                    else nextSpeed++;
                    nextProject=Project.NONE;nextProgress=0;
                }
            }
            return new State(faction,Math.min(LevelEconomy.LEVEL_COUNT,nextHigh+1),
                    Math.addExact(credits,reward),Math.addExact(victories,1),
                    nextLicense,nextHigh,nextSpeed,nextProject,nextProgress);
        }
    }
    public static State start(Battle.Faction faction){return new State(faction,1,0,0);}
    public static String encode(State state){
        if(state==null)throw new IllegalArgumentException("Campaign progress required");
        String body=MAGIC+"\n"+state.faction().name()+"\n"+state.unlocked()+"\n"+
                state.credits()+"\n"+state.victories()+"\n"+state.researchRank()+"\n"+
                state.highestCompleted()+"\n"+state.speedUpgrades()+"\n"+
                state.activeProject().name()+"\n"+state.projectProgress()+"\n";
        CRC32 checksum=new CRC32();checksum.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(checksum.getValue())+"\n";
    }
    public static State decode(String save){
        if(save==null||save.length()>800)
            throw new IllegalArgumentException("Campaign save missing or oversized");
        String[] rows=save.split("\n",-1);
        boolean v1=rows.length==7&&V1.equals(rows[0]);
        boolean v2=rows.length==8&&V2.equals(rows[0]);
        boolean v3=rows.length==12&&PREVIOUS.equals(rows[0]);
        boolean v4=rows.length==12&&MAGIC.equals(rows[0]);
        if(!(v1||v2||v3||v4)||!rows[rows.length-1].isEmpty())
            throw new IllegalArgumentException("Unsupported campaign save");
        int checksumLine=rows.length-2;
        StringBuilder body=new StringBuilder();
        for(int i=0;i<checksumLine;i++)body.append(rows[i]).append('\n');
        CRC32 checksum=new CRC32();checksum.update(body.toString().getBytes(StandardCharsets.UTF_8));
        if(!Long.toHexString(checksum.getValue()).equals(rows[checksumLine]))
            throw new IllegalArgumentException("Campaign checksum mismatch");
        try {
            int reached=Integer.parseInt(rows[2]);
            int high=(v3||v4)?Integer.parseInt(rows[6]):Math.max(0,reached-1);
            int licensed=v1?0:Integer.parseInt(rows[5]);
            if(v4)return restored(Battle.Faction.valueOf(rows[1]),reached,
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]),licensed,high,
                    Integer.parseInt(rows[7]),Project.valueOf(rows[8]),
                    Integer.parseInt(rows[9]));
            if(v3){
                // The two experimental v3 layouts used either a numeric target
                // or a named active Project. Recover both, without wiping saves.
                for(Project oldType:Project.values())if(oldType.name().equals(rows[7])){
                    if(!"true".equals(rows[9])&&!"false".equals(rows[9]))
                        throw new IllegalArgumentException("Malformed final-level flag");
                    boolean finalClear=Boolean.parseBoolean(rows[9]);
                    int legacyHigh=reached==500&&finalClear?500:Math.max(0,reached-1);
                    return restored(Battle.Faction.valueOf(rows[1]),reached,
                            Long.parseLong(rows[3]),Integer.parseInt(rows[4]),licensed,
                            legacyHigh,Integer.parseInt(rows[6]),oldType,
                            Integer.parseInt(rows[8]));
                }
                int target=Integer.parseInt(rows[7]),progress=Integer.parseInt(rows[8]),
                        speed=Integer.parseInt(rows[9]);
                if(target!=0&&target!=licensed+1)
                    throw new IllegalArgumentException("Unrecognized prior research target");
                // This experimental v3 format could already have awarded a
                // zero-duration paid license. Keep that earned entitlement.
                if(target>0&&LevelEconomy.researchMissionCost(target)<=speed)
                    return new State(Battle.Faction.valueOf(rows[1]),reached,
                            Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                            licensed+1,high,speed,Project.NONE,0);
                // Historic numeric v3 could grant an INSTANT paid unlock when
                // speed exceeded duration. Honor that ALREADY-earned entitlement
                // without allowing any new zero-turn research under v4.
                if(target>0&&LevelEconomy.researchMissionCost(target)<=speed)
                    return new State(Battle.Faction.valueOf(rows[1]),reached,
                            Long.parseLong(rows[3]),Integer.parseInt(rows[4]),
                            licensed+1,high,speed,Project.NONE,0);
                return restored(Battle.Faction.valueOf(rows[1]),reached,
                        Long.parseLong(rows[3]),Integer.parseInt(rows[4]),licensed,high,
                        speed,target==0?Project.NONE:Project.LICENSE,progress);
            }
            return new State(Battle.Faction.valueOf(rows[1]),reached,
                    Long.parseLong(rows[3]),Integer.parseInt(rows[4]),licensed);
        }catch(RuntimeException wrong){
            throw new IllegalArgumentException("Invalid campaign save fields",wrong);
        }
    }
}
