package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real connected campaign navigation nodes: ten selectable systems per map page. */
public final class CampaignMap {
    public static final int PAGE_SIZE=10;
    public static final int PAGE_COUNT=LevelEconomy.LEVEL_COUNT/PAGE_SIZE;
    private CampaignMap(){}
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
            long variation=LevelSky.mix(stage.seed());
            float x=112+index*77;
            float y=235+((index*43)%180)+((variation>>>4)&31);
            CampaignOpposition.Operation operation=CampaignOpposition.operation(level);
            // Geographic name is authored independently from battle operation.
            // Preserve the operation suffix for readability and existing UI.
            String name=String.format(Locale.ROOT,"%03d %s / %s",
                    level,CampaignStoryAtlas.nodeName(level),operation.title);
            // A contiguous ten-system campaign front has one opposing power.
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
