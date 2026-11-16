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
    private static BattleConnection.Snapshot bodies(String session, long tick, int offset, boolean includeShell) {
        // Entity IDs only identify an object within its own entity kind.
        var missile = new BattleConnection.MissileEntity(9,0,1,2,offset,10,1,false,false,false);
        var shell = new BattleConnection.BallisticEntity(9,0,1,2,offset+100,20,1,false);
        var craft = new BattleConnection.CraftEntity(9,0,1,2,offset+200,30,1,false,true,null);
        List<BattleConnection.Entity> rows = includeShell ? List.of(shell,craft,missile) : List.of(missile,craft);
        return new BattleConnection.Snapshot(session,tick,0,
                java.util.Collections.nCopies(rows.size(),"fixture"),rows);
    }

    private static void projectileContinuity() {
        var presentation = new RemoteEntityPresentation();
        presentation.observe(bodies("alpha", 12, 0, true), 1f);
        var next = bodies("alpha", 15, 12, true);
        var frame = presentation.observe(next, 1f);
        var clock = new RemoteShipInterpolation();
        clock.observe(12, List.of());
        clock.observe(15, List.of());
        for (int display = 1; display <= 6; display++) {
            clock.advance(1f/60);
            float f = clock.fraction();
            check(presentation.observe(next, f) == frame, "Repeated display frame repartitioned host state");
            check(Math.abs(frame.missiles().get(0).x(f)-2*display)<.001 &&
                    Math.abs(frame.ballistics().get(0).x(f)-(100+2*display))<.001 &&
                    Math.abs(frame.crafts().get(0).x(f)-(200+2*display))<.001,
                    "Host motion froze, overshot, or crossed entity kinds");
        }
        var newer = presentation.observe(bodies("alpha",18,24,false),.5f);
        check(newer.missiles().get(0).x(0)==6 && newer.ballistics().isEmpty(),
                "Early arrival jumped or revived a removed projectile");
        check(presentation.observe(bodies("beta",18,900,true),.5f).missiles().get(0).x(0)==900,
                "New session inherited old projectile coordinates");
        check(presentation.observe(bodies("beta",3,2,true),.5f).crafts().get(0).x(0)==202,
                "Rewound host tick inherited old craft coordinates");
    }

    public static void main(String[] args){
        projectileContinuity();
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
        var presentation=new RemoteEntityPresentation();
        var frame=presentation.observe(snapshot,1f);
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
        check(frame.crafts().get(0).entity().fighterHull()==FighterModel.Hull.MISSILE_FIGHTER&&
                frame.missiles().get(0).entity().torpedo()&&
                frame.ballistics().get(0).entity().fighterLaunched(),
                "Remote body presentation metadata was flattened");
        var originalEmitters=d.weaponEmitters;
        interpolation.observe(snapshot.tick()+3,List.of(dread,carrier));
        mirrors.present(interpolation,false);
        check(d.weaponEmitters==originalEmitters,"Stable host fittings rebuilt physical mounts");
        var refit=new BattleConnection.ShipEntity(dread.id(),dread.team(),dread.faction(),dread.hullClass(),
                dread.x(),dread.y(),dread.facing(),dread.hull(),dread.maxHull(),dread.shields(),dread.maxShields(),
                dread.armor(),dread.maxArmor(),dread.power(),dread.fuel(),dread.lastTargetId(),Map.of());
        interpolation.observe(snapshot.tick()+6,List.of(refit,carrier));
        mirrors.present(interpolation,false);
        check(d.weaponEmitters.isEmpty(),"Removed host mounts survived a refit");
        interpolation.observe(snapshot.tick()+9,List.of(dread,carrier));
        mirrors.present(interpolation,false);
        check(d.weaponEmitters.equals(originalEmitters),"Returning host fittings lost their physical mounts");
        System.out.println("PASS remote v2 presentation: dreadnought/carrier chassis, fitted hardpoints, missile, ballistic and fighter identity");
    }
}
