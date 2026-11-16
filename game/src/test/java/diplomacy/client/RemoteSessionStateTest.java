package diplomacy.client;

import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import java.util.List;

/** Equal/higher-tick new sessions must never inherit old render mirrors. */
public final class RemoteSessionStateTest {
    private RemoteSessionStateTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static String row(int id,int team,Battle.Faction faction,double x){
        return "SHIP|"+id+"|"+team+"|"+faction+"|"+x+"|300|0|100|50|40|30|20";
    }
    private static BattleConnection.Snapshot snapshot(String session,long tick,
            int id,int team,Battle.Faction faction,double x){
        String raw=row(id,team,faction,x);
        var typed=new BattleConnection.ShipEntity(id,team,faction,
                diplomacy.shipyard.Shipyard.Hull.CORVETTE,
                x,300,0,100,100,50,50,40,40,30,20,0,java.util.Map.of());
        return new BattleConnection.Snapshot(session,tick,0,
                List.of(raw),List.of(typed));
    }
    private static void observe(RemoteEntityPresentation entities, RemoteSessionState state,
            RemoteShipInterpolation interpolation,RemoteFleetMirrors mirrors,
            BattleConnection.Snapshot snapshot){
        boolean changed=state.apply(snapshot,interpolation,mirrors);
        interpolation.observe(snapshot.tick(),
                entities.observe(snapshot,1f).ships());
        mirrors.present(interpolation,changed);
    }
    public static void main(String[] args){
        var entities=new RemoteEntityPresentation();
        RemoteSessionState state=new RemoteSessionState();
        RemoteShipInterpolation interpolation=new RemoteShipInterpolation();
        RemoteFleetMirrors mirrors=new RemoteFleetMirrors();

        var first=snapshot("session-A",300,11,0,Battle.Faction.TERRAN,5);
        observe(entities,state,interpolation,mirrors,first);
        check(interpolation.pose(11).x()==5&&mirrors.byHostId(11).team==0,
                "Initial session mirror incorrect");

        var same=snapshot("session-A",303,11,0,Battle.Faction.TERRAN,100);
        check(!state.apply(same,interpolation,mirrors),
                "Same authoritative session was unnecessarily reset");
        interpolation.observe(same.tick(),entities.observe(same,1f).ships());
        check(interpolation.pose(11).x()<100,
                "Same-session update failed to interpolate from prior pose");

        var equalTickNew=snapshot("session-B",303,11,1,Battle.Faction.DRAXIAN,999);
        check(state.apply(equalTickNew,interpolation,mirrors),
                "Equal-tick new server session was not recognized");
        interpolation.observe(equalTickNew.tick(),entities.observe(equalTickNew,1f).ships());
        mirrors.present(interpolation,true);
        check(interpolation.pose(11).x()==999&&mirrors.byHostId(11).team==1,
                "Equal-tick new session inherited old position/team");

        var higherTickNew=snapshot("session-C",900,11,0,Battle.Faction.TERRAN,700);
        check(state.apply(higherTickNew,interpolation,mirrors),
                "Higher-tick new server session was not recognized");
        interpolation.observe(higherTickNew.tick(),entities.observe(higherTickNew,1f).ships());
        mirrors.present(interpolation,true);
        check(interpolation.pose(11).x()==700&&mirrors.byHostId(11).team==0,
                "Higher-tick new session blended from previous server");
        System.out.println("PASS host session identity resets equal/higher-tick mirrors while same-session reconnect retains continuity");
    }
}
