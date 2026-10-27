package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.BattleOrders;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * Original pre-battle tactical order editor. Both fleets can independently tune
 * defensive firing, three target-size priorities, engagement distance and AI.
 * Per-ship escort/formation leaders are managed through Battle.setShipOrders;
 * this fleet-level editor deliberately excludes anchor-less follow orders.
 */
final class FleetOrdersPanel {
    private static final int ROWS=10;
    private static final BattleOrders.Position[] TEAM_POSITIONS={
            BattleOrders.Position.FREE,BattleOrders.Position.HOLD,
            BattleOrders.Position.KEEP_MOVING,BattleOrders.Position.CAUTIOUS,
            BattleOrders.Position.LAST_STAND
    };
    private final IntFunction<BattleOrders.Orders> get;
    private final BiConsumer<Integer,BattleOrders.Orders> apply;
    private boolean visible;
    private TacticalUiIcons uiIcons;
    private static final String[] ROW_ICONS={
        "shield", "target", "engine", "drone", "speed",
        "fleet", "speed", "armor", "weapons", "back"
    };
    void setIcons(TacticalUiIcons vetted) { uiIcons = vetted; }
    FleetOrdersPanel(IntFunction<BattleOrders.Orders> fetch,
                     BiConsumer<Integer,BattleOrders.Orders> commit){
        get=fetch;apply=commit;
    }
    boolean open(){return visible;}
    void show(){visible=true;}
    void close(){visible=false;}
    private static int index(BattleOrders.Position mode){
        for(int i=0;i<TEAM_POSITIONS.length;i++)if(TEAM_POSITIONS[i]==mode)return i;
        return 0;
    }
    private static int limit(int value,int lower,int upper){
        return Math.min(upper,Math.max(lower,value));
    }
    private static BattleOrders.Orders modify(BattleOrders.Orders current,int row,int step){
        return switch(row) {
            case 0 -> {
                BattleOrders.PointDefense[] modes=BattleOrders.PointDefense.values();
                int i=Math.floorMod(current.pointDefense().ordinal()+step,modes.length);
                yield current.withPointDefense(modes[i]);
            }
            case 1 -> {
                BattleOrders.Targeting[] modes=BattleOrders.Targeting.values();
                int i=Math.floorMod(current.targeting().ordinal()+step,modes.length);
                yield current.withTargeting(modes[i]);
            }
            case 2 -> current.withPosition(TEAM_POSITIONS[
                    Math.floorMod(index(current.position())+step,TEAM_POSITIONS.length)]);
            case 3 -> current.withEngagement(BattleOrders.Size.FIGHTER,
                    limit(current.fighters().priority()+step*10,0,100),current.fighters().range());
            case 4 -> current.withEngagement(BattleOrders.Size.FIGHTER,
                    current.fighters().priority(),limit((int)current.fighters().range()+step*30,30,1500));
            case 5 -> current.withEngagement(BattleOrders.Size.ESCORT,
                    limit(current.escorts().priority()+step*10,0,100),current.escorts().range());
            case 6 -> current.withEngagement(BattleOrders.Size.ESCORT,
                    current.escorts().priority(),limit((int)current.escorts().range()+step*30,30,1500));
            case 7 -> current.withEngagement(BattleOrders.Size.CAPITAL,
                    limit(current.capitals().priority()+step*10,0,100),current.capitals().range());
            case 8 -> current.withEngagement(BattleOrders.Size.CAPITAL,
                    current.capitals().priority(),limit((int)current.capitals().range()+step*30,30,1500));
            case 9 -> current.withRetreat(
                    limit((int)Math.round(current.retreatAtHullFraction()*100)+step*5,0,100)/100.0);
            default -> current;
        };
    }
    boolean tap(float x,float y){
        if(!visible)return false;
        if(y>=622){close();return true;}
        if(y<135||y>585)return true;
        int side=x<640?0:1;
        float origin=side==0?32:650;
        if(x<origin||x>origin+598)return true;
        for(int row=0;row<ROWS;row++){
            float bottom=550-row*44;
            if(y<bottom||y>bottom+38)continue;
            int delta=x<origin+299?-1:1;
            BattleOrders.Orders before=get.apply(side);
            BattleOrders.Orders after=modify(before,row,delta);
            if(!after.equals(before))apply.accept(side,after);
            return true;
        }
        return true;
    }
    private static String[] values(BattleOrders.Orders o) {
        return new String[]{
                o.pointDefense().name().replace('_',' '),
                o.targeting().name().replace('_',' '),
                o.position().name().replace('_',' '),
                o.fighters().priority()+"%",
                Math.round(o.fighters().range())+" units",
                o.escorts().priority()+"%",
                Math.round(o.escorts().range())+" units",
                o.capitals().priority()+"%",
                Math.round(o.capitals().range())+" units",
                Math.round(100*o.retreatAtHullFraction())+"% hull"};
    }
    private static final String[] NAMES={
        "POINT DEFENSE","TARGET SELECTION","MOVEMENT",
        "FIGHTERS PRIORITY","FIGHTERS RANGE","ESCORTS PRIORITY","ESCORTS RANGE",
        "CAPITALS PRIORITY","CAPITALS RANGE","CAUTIOUS RETREAT"
    };
    void draw(ShapeRenderer shapes,SpriteBatch batch,BitmapFont font){
        if(!visible)return;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.018f,0.029f,0.053f,1);
        shapes.rect(0,0,1280,720);
        for(int side=0;side<2;side++){
            float x=side==0?32:650;
            shapes.setColor(0.05f,0.09f,0.15f,1);
            shapes.rect(x,118,598,483);
            for(int i=0;i<ROWS;i++){
                shapes.setColor(i%2==0?0.09f:0.07f,0.13f,0.22f,1);
                shapes.rect(x+5,550-i*44,588,38);
            }
        }
        shapes.end();
        batch.begin();
        if(uiIcons != null){
            uiIcons.drawButton(batch,"secondary",1116,629,150,55);
            uiIcons.draw(batch,"close",1128,643,24);
        }
        font.setColor(Color.WHITE);
        font.draw(batch,"FLEET TACTICAL ORDERS",39,687);
        font.draw(batch,"CLOSE / BACK",1159,665);
        for(int side=0;side<2;side++){
            float x=side==0?39:657;
            font.setColor(side==0?Color.CYAN:Color.CORAL);
            font.draw(batch,side==0?"LEFT FLEET":"RIGHT FLEET",x,613);
            String[] values=values(get.apply(side));
            for(int i=0;i<ROWS;i++){
                float y=550-i*44;
                if(uiIcons != null) uiIcons.draw(batch,ROW_ICONS[i],x+12,y+7,24);
                font.setColor(Color.LIGHT_GRAY);
                font.draw(batch,"<  "+NAMES[i],x+43,y+25);
                font.setColor(Color.WHITE);
                font.draw(batch,values[i]+"  >",x+305,y+25);
            }
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"Tap left/right halves of rows to adjust. Zero target priority disables that class.",39,83);
        font.draw(batch,"Individual escort leaders & formation anchors require ship-specific orders.",39,51);
        batch.end();
    }
}
