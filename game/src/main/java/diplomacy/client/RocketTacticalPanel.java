package diplomacy.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import diplomacy.sim.Battle;
import diplomacy.sim.RocketBarrage;

/**
 * Actual selected-ship dumbfire rack controls. Coordinates are HUD-space
 * (1280x720 virtual viewport), with >=44px-high touch targets.
 * Only a friendly physical rack can be edited; never changes saved research,
 * other hulls, guided missiles, or a barrage already leaving its launcher.
 */
final class RocketTacticalPanel {
    private RocketTacticalPanel(){}
    static final int BUTTONS=6;
    static final float Y=4f, H=46f;
    private static final float[] X={335,461,534,607,680,823};
    private static final float[] W={120,67,67,67,137,145};
    private static final String[] NAMES={"NEXT RACK","4","8","16","BOOST/COAST","LONG BURN"};
    static int action(float x,float y){
        if(y<Y||y>Y+H)return -1;
        for(int i=0;i<BUTTONS;i++)
            if(x>=X[i]&&x<=X[i]+W[i])return i;
        return -1;
    }
    static RocketBarrage.Rack selected(Battle.Ship ship,int index){
        if(ship==null||ship.team!=0||ship.rocketRacks.isEmpty())return null;
        return ship.rocketRacks.get(Math.floorMod(index,ship.rocketRacks.size()));
    }
    static void apply(Battle battle,Battle.Ship ship,int index,int action){
        if(battle==null||ship==null||ship.team!=0||!ship.alive())
            throw new IllegalArgumentException("No friendly live ship selected");
        RocketBarrage.Rack rack=selected(ship,index);
        if(rack==null||action<1||action>=BUTTONS)
            throw new IllegalArgumentException("Unknown rocket setting");
        // Preserve the other axis of the setting when choosing salvo or motor.
        int count=switch(action){
            case 1 -> 4;case 2 -> 8;case 3 -> 16;default -> rack.salvoCount;
        };
        RocketBarrage.Motor motor=switch(action){
            case 4 -> RocketBarrage.Motor.BOOST_COAST;
            case 5 -> RocketBarrage.Motor.SUSTAINED_BURN;
            default -> rack.motor;
        };
        battle.setShipRocketBarrage(ship.id,rack.slot,count,motor);
    }
    static void shapes(ShapeRenderer shapes,Battle.Ship ship,int index){
        RocketBarrage.Rack rack=selected(ship,index);
        if(rack==null)return;
        shapes.setColor(.035f,.085f,.15f,.94f);
        shapes.rect(328,0,940,54);
        for(int i=0;i<BUTTONS;i++){
            boolean active=switch(i){
                case 1 -> rack.salvoCount==4;
                case 2 -> rack.salvoCount==8;
                case 3 -> rack.salvoCount==16;
                case 4 -> rack.motor==RocketBarrage.Motor.BOOST_COAST;
                case 5 -> rack.motor==RocketBarrage.Motor.SUSTAINED_BURN;
                default -> false;
            };
            if(rack.salvoRemaining>0)shapes.setColor(.23f,.15f,.09f,.93f);
            else if(active)shapes.setColor(.09f,.25f,.34f,.95f);
            else shapes.setColor(.11f,.17f,.24f,.94f);
            shapes.rect(X[i],Y,W[i],H);
        }
    }
    static void text(SpriteBatch batch,BitmapFont font,Battle.Ship ship,int index){
        RocketBarrage.Rack rack=selected(ship,index);
        if(rack==null)return;
        font.setColor(Color.WHITE);
        for(int i=0;i<BUTTONS;i++)font.draw(batch,NAMES[i],X[i]+5,Y+29);
        boolean enoughStock=rack.rounds>=rack.salvoCount
                &&ship.kineticAmmo>=rack.salvoCount*rack.cheapAmmoUnitsPerRocket;
        font.setColor(rack.salvoRemaining>0?Color.ORANGE:
                !enoughStock?Color.SALMON:Color.CYAN);
        String note=rack.salvoRemaining>0?" FIRE "+rack.salvoRemaining:
                !enoughStock?" NEED "+(rack.salvoCount*rack.cheapAmmoUnitsPerRocket):"";
        font.draw(batch,"R"+(index%ship.rocketRacks.size()+1)+"/"+
                ship.rocketRacks.size()+" "+rack.size.name()+
                " "+rack.rounds+"R "+ship.kineticAmmo+"S "+
                (int)Math.ceil(rack.reload)+"s"+note,979,31);
        font.setColor(Color.WHITE);
    }
}
