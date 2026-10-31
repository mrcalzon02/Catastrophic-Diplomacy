package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;

/** Functional 1..200-ship placement and independent preplanned command execution. */
public final class ShipPlacementOrdersTest {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void reject(Runnable call,String message){
        boolean failed=false;
        try{call.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,message);
    }
    public static void main(String[] argv)throws Exception {
        for(int n:new int[]{1,2,8,32,100,200}){
            for(var shape:CampaignDeployment.Formation.values()){
                var load=new CampaignDeployment.Loadout(n,shape);
                for(int i=0;i<n;i++){
                    var p=load.at(i);
                    ok(p.x()>=CampaignDeployment.MIN_X&&p.x()<=CampaignDeployment.MAX_X
                            &&p.y()>=CampaignDeployment.MIN_Y&&p.y()<=CampaignDeployment.MAX_Y,
                            "Default spawn outside zone: "+n+"/"+shape+"/"+i);
                    for(int j=0;j<i;j++){
                        var other=load.at(j);
                        ok(Math.hypot(p.x()-other.x(),p.y()-other.y())>=CampaignDeployment.MIN_SPACING,
                                "Overlapping default ships: "+n+"/"+shape+"/"+i+"/"+j);
                    }
                }
            }
        }
        var start=new CampaignDeployment.Loadout(2,CampaignDeployment.Formation.LINE);
        var placed=start.withPlacement(0,72,92).withPlacement(1,557,587);
        ok(placed.at(0).equals(new CampaignDeployment.Point(72,92))
                &&placed.at(1).equals(new CampaignDeployment.Point(557,587)),
                "Free deployment zone placement not retained");
        reject(()->placed.withPlacement(0,560.1,300),"Out-of-zone x was accepted");
        reject(()->placed.withPlacement(0,200,89),"Out-of-zone y was accepted");
        reject(()->placed.withPlacement(1,74,93),"Overlapping deployment accepted");
        ok(start.at(0).equals(new CampaignDeployment.Loadout(2,
                CampaignDeployment.Formation.LINE).at(0)),"Immutable source changed after drag");

        var editable=new CampaignDeployment.Loadout(3,CampaignDeployment.Formation.LINE);
        for(int i=0;i<250;i++)
            editable=editable.appendOrder(0,ShipCommandQueue.Command.hold(i+1));
        ok(editable.ordersFor(0).size()==250,"Arbitrary-length order program was truncated");
        ok(editable.ordersFor(1).isEmpty(),"Orders accidentally applied to another vessel");
        var moved=editable.appendOrder(1,ShipCommandQueue.Command.move(600,320));
        ok(moved.ordersFor(1).size()==1&&moved.ordersFor(0).size()==250,
                "Per-vessel order independence failed");
        ok(moved.undoOrder(1).ordersFor(1).isEmpty(),"Undo last order failed");
        ok(moved.clearOrders(0).ordersFor(0).isEmpty(),"Clear orders failed");
        reject(()->start.appendOrder(2,ShipCommandQueue.Command.hold(1)),
                "Orders accepted for nonexistent vessel");
        reject(()->ShipCommandQueue.Command.move(1281,55),"Off-map waypoint accepted");
        reject(()->ShipCommandQueue.Command.hold(0),"Zero duration order accepted");

        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design ally=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.TERRAN,true,catalog);
        Shipyard.Design enemy=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.DRAXIAN,false,catalog);
        var configured=new CampaignDeployment.Loadout(3,CampaignDeployment.Formation.WEDGE)
                .withPlacement(0,250,250)
                .appendOrder(0,ShipCommandQueue.Command.move(280,250))
                .appendOrder(0,ShipCommandQueue.Command.hold(3))
                .appendOrder(0,ShipCommandQueue.Command.move(315,250))
                .appendOrder(0,ShipCommandQueue.Command.engage(5))
                .appendOrder(1,ShipCommandQueue.Command.hold(900));
        CampaignEncounter encounter=CampaignEncounter.start(25,configured,ally,enemy,catalog);
        Battle battle=encounter.battle();
        Battle.Ship lead=battle.ships().get(0),wing=battle.ships().get(1);
        ok(lead.x==250&&lead.y==250&&lead.commandQueue.total()==4,
                "Physical deployment did not install leader's complete orders");
        ok(wing.commandQueue.total()==1&&battle.ships().get(2).commandQueue.total()==0,
                "Program incorrectly copied across player ships");
        int maxIndex=0;
        for(int t=0;t<400&&lead.alive();t++){
            battle.step();
            maxIndex=Math.max(maxIndex,lead.commandQueue.nextIndex());
            if(maxIndex==4)break;
        }
        ok(maxIndex==4,"Move, hold, move, engage were not executed in sequence");
        ok(lead.x>296,"Ordered movement was not applied by the live battle simulation");
        ok(wing.commandQueue.nextIndex()==0,"Unrelated vessel's HOLD completed too soon");
        ok(lead.commandQueue.total()==4,"Executed orders were deleted rather than retained");

        // The queue engine itself must preserve exact timing and ordering.
        var queue=new ShipCommandQueue(List.of(ShipCommandQueue.Command.hold(3),
                ShipCommandQueue.Command.engage(2)));
        ok(!queue.tickTimed()&&queue.nextIndex()==0,"Premature 1-tick completion");
        ok(!queue.tickTimed()&&queue.nextIndex()==0,"Premature 2-tick completion");
        ok(queue.tickTimed()&&queue.nextIndex()==1,"Hold completed at wrong tick");
        ok(!queue.tickTimed()&&queue.tickTimed()&&queue.current()==null,
                "Finite ENGAGE failed to end after two ticks");
        System.out.println("PASS placement and individual orders: full 200-ship zones, "+
                "collision rejection, 250 commands, independent queues and live ordered navigation");
    }
}
