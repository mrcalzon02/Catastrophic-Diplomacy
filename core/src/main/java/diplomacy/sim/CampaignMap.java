package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Branching campaign navigation: fifty ten-mission fronts in ten story chapters. */
public final class CampaignMap {
    public static final int PAGE_SIZE=10;
    public static final int PAGE_COUNT=LevelEconomy.LEVEL_COUNT/PAGE_SIZE;
    private CampaignMap(){}
    /** Route connections use mission IDs, not decorative map-only edges. */
    public record RouteLink(int from,int to){}
    private static final float[] MAP_X={145,300,475,650,300,475,650,300,525,820};
    private static final float[] MAP_Y={345,490,490,490,345,345,345,200,200,345};
    /** 1 entry, three operational lanes, 10 finale; side links permit alternate approaches. */
    public static List<RouteLink> links(int page){
        if(page<0||page>=PAGE_COUNT)throw new IllegalArgumentException("Unknown map page");
        int base=page*PAGE_SIZE;
        int[][] pairs={{1,2},{1,5},{1,8},{2,3},{3,4},{3,6},{5,6},
                {6,7},{6,9},{8,9},{4,10},{7,10},{9,10}};
        ArrayList<RouteLink> result=new ArrayList<>(pairs.length);
        for(int[] pair:pairs)result.add(new RouteLink(base+pair[0],base+pair[1]));
        return List.copyOf(result);
    }
    public record SystemNode(int level,int sector,String name,Battle.Faction opponent,
                             float x,float y,boolean sectorFinale,
                             CampaignOpposition.Operation operation,int enemyTier){}
    public static int pageOf(int level){return (LevelEconomy.level(level).index()-1)/PAGE_SIZE;}
    public static List<SystemNode> page(int page){
        if(page<0||page>=PAGE_COUNT)throw new IllegalArgumentException("Unknown map page");
        ArrayList<SystemNode> systems=new ArrayList<>();
        for(int index=0;index<PAGE_SIZE;index++){
            int level=page*PAGE_SIZE+index+1;
            LevelEconomy.Level stage=LevelEconomy.level(level);
            float x=MAP_X[index];
            float y=MAP_Y[index];
            CampaignOpposition.Operation operation=CampaignOpposition.operation(level);
            // Geographic name is authored independently from battle operation.
            // Preserve the operation suffix for readability and existing UI.
            String name=String.format(Locale.ROOT,"%03d %s / %s",
                    level,CampaignStoryAtlas.nodeName(level),operation.title);
            // A ten-system campaign front has one opposing power.
            // Separate map pages can change allegiance without a random
            // opponent switch in the middle of a tactical operation.
            Battle.Faction opponent=Battle.Faction.values()[
                    Math.floorMod(stage.sector()*2+page,Battle.Faction.values().length)];
            systems.add(new SystemNode(level,stage.sector(),name,opponent,x,y,
                    level%50==0,operation,CampaignOpposition.maximumTier(level)));
        }
        return List.copyOf(systems);
    }
}
