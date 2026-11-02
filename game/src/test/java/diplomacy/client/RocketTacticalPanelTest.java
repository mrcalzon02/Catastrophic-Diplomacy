package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.RocketBarrage;
import java.util.List;

/** Objective battle HUD test: real owned rack, count, burn program and 46px
 * touch targets. No cosmetic pixel judgement or desktop-only input path. */
public final class RocketTacticalPanelTest {
    private RocketTacticalPanelTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args){
        for(int i=0;i<RocketTacticalPanel.BUTTONS;i++){
            float[] centerX={395,494,567,640,748,895};
            check(RocketTacticalPanel.action(centerX[i],27)==i,
                    "Rocket button "+i+" does not match the actual touch rectangle");
            check(RocketTacticalPanel.action(centerX[i],4)==i
                    &&RocketTacticalPanel.action(centerX[i],50)==i,
                    "Touch controls must provide >=44px vertical height");
        }
        check(RocketTacticalPanel.action(20,26)<0 &&
                RocketTacticalPanel.action(510,100)<0,
                "Rocket controls stole zoom or ordinary game field hitboxes");
        Battle battle=new Battle(150191L);
        Battle.Ship owned=battle.addShip(0,Battle.Faction.TERRAN,200,200);
        Battle.Ship hostile=battle.addShip(1,Battle.Faction.DRAXIAN,900,200);
        var small=new RocketBarrage.Rack(4,RocketBarrage.Size.SMALL,1,1,1);
        var siege=new RocketBarrage.Rack(8,RocketBarrage.Size.SIEGE,1,1,1);
        owned.rocketRacks=List.of(small,siege);
        hostile.rocketRacks=List.of(
                new RocketBarrage.Rack(4,RocketBarrage.Size.SMALL,1,1,1));
        check(RocketTacticalPanel.selected(owned,0)==small &&
                RocketTacticalPanel.selected(owned,1)==siege &&
                RocketTacticalPanel.selected(owned,2)==small,
                "Rack selector must cycle independent physical mounts");
        check(RocketTacticalPanel.selected(hostile,0)==null,
                "Player may not reconfigure enemy launchers");
        RocketTacticalPanel.apply(battle,owned,1,1);
        check(siege.salvoCount==4&&small.salvoCount==4,
                "Selected siege salvo must not rewrite another rack");
        RocketTacticalPanel.apply(battle,owned,1,3);
        RocketTacticalPanel.apply(battle,owned,1,5);
        check(siege.salvoCount==16&&siege.motor==RocketBarrage.Motor.SUSTAINED_BURN,
                "16-rocket sustained burn was not sent into actual Battle ship rack");
        RocketTacticalPanel.apply(battle,owned,0,2);
        RocketTacticalPanel.apply(battle,owned,0,4);
        check(small.salvoCount==8&&small.motor==RocketBarrage.Motor.BOOST_COAST
                &&siege.salvoCount==16,
                "Live rack controls leaked salvo count or motor between mounted pods");
        siege.salvoRemaining=8;
        boolean rejected=false;
        try{RocketTacticalPanel.apply(battle,owned,1,1);}
        catch(IllegalStateException expected){rejected=true;}
        check(rejected&&siege.salvoCount==16,
                "Cannot switch rocket rounds while a physical salvo is in flight");
        rejected=false;
        try{RocketTacticalPanel.apply(battle,hostile,0,1);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Player must not command an enemy salvo");
        System.out.println("PASS real rocket HUD 46px touch buttons, 4/8/16 selection, two motors, mounted sockets and friendly-only control");
    }
}
