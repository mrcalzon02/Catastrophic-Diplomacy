package diplomacy.client;

import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import diplomacy.shipyard.Shipyard;
import java.util.List;
import java.util.Map;

/** Typed host state must preserve chassis, fittings and all transmitted body classes. */
public final class RemoteNetworkPresentationTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var dread=new BattleConnection.ShipEntity(11,0,Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT,200,300,0,900,1000,80,120,140,180,
                90,75,12,Map.of(0,"BASE-TER-LAS",1,"BASE-TER-MSL"));
        var carrier=new BattleConnection.ShipEntity(12,1,Battle.Faction.DRAXIAN,
                Shipyard.Hull.CARRIER,800,300,3.0,800,900,100,130,120,160,
                100,80,11,Map.of(0,"BASE-DRA-PAR"));
        var missile=new BattleConnection.MissileEntity(
                31,0,11,12,450,300,2.5,true,false,false);
        var shell=new BattleConnection.BallisticEntity(
                32,1,12,11,600,300,1.4,true);
        var craft=new BattleConnection.CraftEntity(
                33,1,12,11,700,320,25,true,false,FighterModel.Hull.MISSILE_FIGHTER);
        var snapshot=new BattleConnection.Snapshot("presentation",90,123,
                List.of("a","b","c","d","e"),
                List.of(dread,carrier,missile,shell,craft));
        var frame=RemoteEntityPresentation.from(snapshot);
        check(frame.ships().size()==2&&frame.missiles().size()==1&&
                frame.ballistics().size()==1&&frame.crafts().size()==1,
                "Typed host entity classes disappeared from presentation frame");
        RemoteShipInterpolation interpolation=new RemoteShipInterpolation();
        interpolation.observe(snapshot.tick(),frame.ships());
        RemoteFleetMirrors mirrors=new RemoteFleetMirrors();
        Battle rendered=mirrors.present(interpolation,true);
        Battle.Ship d=mirrors.byHostId(11),c=mirrors.byHostId(12);
        check(rendered.ships().size()==2&&d!=null&&c!=null,
                "Host ships were not mirrored");
        check(d.hullClass==Shipyard.Hull.DREADNOUGHT&&
                c.hullClass==Shipyard.Hull.CARRIER,
                "Remote renderer collapsed chassis identity to CORVETTE");
        check(d.maxHull==1000&&d.lastTargetId==12&&d.weaponEmitters.size()==2,
                "Remote mirror lost host max-state, target or fitted hardpoints");
        check(frame.crafts().get(0).fighterHull()==FighterModel.Hull.MISSILE_FIGHTER&&
                frame.missiles().get(0).torpedo()&&
                frame.ballistics().get(0).fighterLaunched(),
                "Remote body presentation metadata was flattened");
        System.out.println("PASS remote v2 presentation: dreadnought/carrier chassis, fitted hardpoints, missile, ballistic and fighter identity");
    }
}
