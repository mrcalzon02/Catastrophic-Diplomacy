package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.LevelEconomy;
import diplomacy.sim.ScenarioCatalog;
import java.util.List;

/** Lightweight native-font menu views; all hit regions are owned by the client flow. */
final class GameMenuPanel {
    enum Screen { HOME, SKIRMISH, CAMPAIGN, OPTIONS, BATTLE, RESULTS }
    private static final float W=1280,H=720;
    private static final Color BG=new Color(.018f,.029f,.064f,1);
    private static final Color PANEL=new Color(.055f,.105f,.18f,1);
    private static final Color SELECT=new Color(.12f,.28f,.40f,1);
    private static final Color MUTED=new Color(.57f,.67f,.79f,1);
    private static final String[] HOME_ACTIONS={
        "CONTINUE CAMPAIGN","NEW CAMPAIGN","SKIRMISH","SHIPYARD","OPTIONS","EXIT"
    };

    static boolean hit(float x,float y,float bx,float by,float w,float h){
        return x>=bx&&x<=bx+w&&y>=by&&y<=by+h;
    }
    static int homeHit(float x,float y){
        for(int i=0;i<HOME_ACTIONS.length;i++)
            if(hit(x,y,438,487-i*64,404,50))return i;
        return -1;
    }
    static int skirmishHit(float x,float y){
        ScenarioCatalog.Preset[] values=ScenarioCatalog.Preset.values();
        for(int i=0;i<values.length;i++)
            if(hit(x,y,30,592-i*48,550,44))return i;
        return -1;
    }
    static int mapHit(float x,float y,int page){
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            if(Math.hypot(x-node.x(),y-node.y())<=26)return node.level();
        return -1;
    }
    private static void rectangle(ShapeRenderer shapes,float x,float y,float w,float h,Color color){
        shapes.setColor(color);shapes.rect(x,y,w,h);
    }
    private static void button(ShapeRenderer shapes,float x,float y,float w,float h,boolean selected){
        rectangle(shapes,x,y,w,h,selected?SELECT:PANEL);
    }
    private static void label(SpriteBatch batch,BitmapFont font,String text,float x,float y,Color color){
        font.setColor(color);font.draw(batch,text,x,y);
    }
    private static void title(SpriteBatch batch,BitmapFont font,String text,String subtitle){
        label(batch,font,text,37,680,Color.WHITE);
        label(batch,font,subtitle,38,650,MUTED);
    }
    void draw(Screen screen,ShapeRenderer shapes,SpriteBatch batch,BitmapFont font,
              ScenarioCatalog.Preset preset,Battle.Faction left,Battle.Faction right,
              int fleetCount,CampaignProgress.State progress,int page,int selectedLevel,
              boolean music,boolean effects,boolean won,long reward,int surviving,int deployed){
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        rectangle(shapes,0,0,W,H,BG);
        // Stable UI frame; all text is genuine BitmapFont, never an icon substitute.
        rectangle(shapes,0,622,W,2,SELECT);
        switch(screen){
            case HOME -> {
                for(int i=0;i<HOME_ACTIONS.length;i++)button(shapes,438,487-i*64,404,50,false);
            }
            case SKIRMISH -> {
                rectangle(shapes,610,113,638,524,PANEL);
                ScenarioCatalog.Preset[] presets=ScenarioCatalog.Preset.values();
                for(int i=0;i<presets.length;i++)
                    button(shapes,30,592-i*48,550,44,presets[i]==preset);
                button(shapes,30,35,160,49,false);
                button(shapes,702,35,225,49,false);
                button(shapes,969,35,277,49,true);
                button(shapes,661,332,545,42,false);
                button(shapes,661,271,545,42,false);
                button(shapes,661,210,545,42,false);
            }
            case CAMPAIGN -> {
                rectangle(shapes,39,114,866,491,PANEL);
                rectangle(shapes,921,114,328,491,PANEL);
                List<CampaignMap.SystemNode> nodes=CampaignMap.page(page);
                shapes.setColor(.32f,.54f,.75f,1);
                for(int i=1;i<nodes.size();i++){
                    var a=nodes.get(i-1);var b=nodes.get(i);
                    shapes.rectLine(a.x(),a.y(),b.x(),b.y(),2);
                }
                for(var node:nodes){
                    Color c=node.level()<progress.unlocked()?Color.GREEN:
                        node.level()==progress.unlocked()?Color.CYAN:MUTED;
                    shapes.setColor(node.level()==selectedLevel?Color.GOLD:c);
                    shapes.circle(node.x(),node.y(),node.level()==selectedLevel?19:13,18);
                    shapes.setColor(BG);
                    shapes.circle(node.x(),node.y(),6,12);
                }
                button(shapes,35,35,165,50,false);
                button(shapes,214,35,136,50,false);
                button(shapes,366,35,136,50,false);
                button(shapes,964,35,278,50,true);
            }
            case OPTIONS -> {
                for(int i=0;i<4;i++)button(shapes,405,472-i*78,470,59,false);
            }
            case RESULTS -> {
                rectangle(shapes,300,240,680,270,PANEL);
                button(shapes,351,122,254,58,false);
                button(shapes,673,122,254,58,true);
            }
            default -> {}
        }
        shapes.end();
        batch.begin();
        switch(screen){
            case HOME -> {
                title(batch,font,"CATASTROPHIC DIPLOMACY","TACTICAL FLEET COMMAND");
                for(int i=0;i<HOME_ACTIONS.length;i++)
                    label(batch,font,HOME_ACTIONS[i],474,520-i*64,Color.WHITE);
                label(batch,font,"CAMPAIGN  /  SKIRMISH  /  SHIP ENGINEERING",438,73,MUTED);
            }
            case SKIRMISH -> {
                title(batch,font,"SKIRMISH","CHOOSE A SCENARIO AND DEPLOY A FITTED FLEET");
                int index=0;
                for(var choice:ScenarioCatalog.Preset.values())
                    label(batch,font,choice.title,49,622-(index++)*48,Color.WHITE);
                label(batch,font,"SCENARIO",653,586,Color.CYAN);
                label(batch,font,preset.title,654,549,Color.WHITE);
                label(batch,font,preset.description,654,510,MUTED);
                label(batch,font,"FLEET SIZE  "+(preset==ScenarioCatalog.Preset.CUSTOM?fleetCount:preset.ships)+"v"+
                    (preset==ScenarioCatalog.Preset.CUSTOM?fleetCount:preset.ships),654,453,Color.GOLD);
                label(batch,font,"ENVIRONMENT LEVEL "+preset.environmentLevel,654,409,Color.WHITE);
                label(batch,font,"LEFT FACTION: "+left.displayName+"  >",677,359,Color.WHITE);
                label(batch,font,"RIGHT FACTION: "+right.displayName+"  >",677,298,Color.WHITE);
                label(batch,font,"CUSTOM FLEET SIZE: -  "+fleetCount+"  +",677,237,Color.WHITE);
                label(batch,font,"BACK",70,67,Color.WHITE);
                label(batch,font,"SHIPYARD",743,67,Color.WHITE);
                label(batch,font,"LAUNCH BATTLE",998,67,Color.WHITE);
            }
            case CAMPAIGN -> drawCampaign(batch,font,progress,page,selectedLevel);
            case OPTIONS -> {
                title(batch,font,"OPTIONS","AUDIO AND APPLICATION");
                label(batch,font,"MUSIC: "+(music?"ON":"OFF"),445,510,Color.WHITE);
                label(batch,font,"SOUND EFFECTS: "+(effects?"ON":"OFF"),445,432,Color.WHITE);
                label(batch,font,"CHECK FOR UPDATES",445,354,Color.WHITE);
                label(batch,font,"BACK",445,276,Color.WHITE);
            }
            case RESULTS -> {
                title(batch,font,"AFTER-ACTION REPORT","THE ENGAGEMENT HAS ENDED");
                label(batch,font,won?"MISSION ACCOMPLISHED":"FLEET DEFEATED",416,464,won?Color.GREEN:Color.SCARLET);
                label(batch,font,"SURVIVING SHIPS: "+surviving+" / "+deployed,416,412,Color.WHITE);
                label(batch,font,"CREDITS AWARDED: "+reward,416,359,Color.GOLD);
                label(batch,font,"RETRY",423,157,Color.WHITE);
                label(batch,font,"RETURN TO "+(progress!=null?"CAMPAIGN":"SKIRMISH"),699,157,Color.WHITE);
            }
            default -> {}
        }
        batch.end();
    }
    private static void drawCampaign(SpriteBatch batch,BitmapFont font,
                                      CampaignProgress.State progress,int page,int selected){
        LevelEconomy.Level level=LevelEconomy.level(selected);
        title(batch,font,"CAMPAIGN STAR MAP","SECTOR "+level.sector()+"  |  REACHABLE SYSTEMS AND FLEET MISSIONS");
        label(batch,font,"CREDITS: "+progress.credits()+"   COMPLETED: "+progress.victories(),39,590,Color.GOLD);
        for(CampaignMap.SystemNode node:CampaignMap.page(page))
            label(batch,font,""+node.level(),node.x()-12,node.y()+38,MUTED);
        label(batch,font,"MISSION "+selected,946,568,Color.WHITE);
        label(batch,font,"SECTOR "+level.sector(),946,524,MUTED);
        label(batch,font,"ENEMY: "+CampaignMap.page(CampaignMap.pageOf(selected)).get((selected-1)%10).opponent().name(),946,482,MUTED);
        label(batch,font,"FLEET CAP: "+level.maximumFleet(),946,440,Color.WHITE);
        label(batch,font,"WAVES PLANNED: "+level.waves(),946,398,MUTED);
        label(batch,font,progress.available(selected)?"AVAILABLE":"LOCKED",946,343,
              progress.available(selected)?Color.GREEN:Color.SCARLET);
        label(batch,font,"FACTION: "+progress.faction().name(),946,296,Color.WHITE);
        label(batch,font,"BACK",62,67,Color.WHITE);
        label(batch,font,"PREV",248,67,Color.WHITE);
        label(batch,font,"NEXT",402,67,Color.WHITE);
        label(batch,font,"DEPLOY FLEET",989,67,Color.WHITE);
    }
}
