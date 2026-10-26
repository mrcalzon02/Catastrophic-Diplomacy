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
                             float x,float y,boolean sectorFinale){}
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
            String name=(level%50==0?"SECTOR CAPITAL ":"SYSTEM ")+
                    String.format(Locale.ROOT,"%03d",level);
            Battle.Faction opponent=Battle.Faction.values()[
                    Math.floorMod(level/4+stage.sector(),Battle.Faction.values().length)];
            systems.add(new SystemNode(level,stage.sector(),name,opponent,x,y,level%50==0));
        }
        return List.copyOf(systems);
    }
}
