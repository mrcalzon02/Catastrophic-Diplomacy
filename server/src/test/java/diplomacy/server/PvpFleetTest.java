package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.shipyard.*;
import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Trusted loopback PVP: only the host's match ceiling is authoritative. */
public final class PvpFleetTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Throwing action,String why)throws Exception{
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
    public static void main(String[] args)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        var basic=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int laser=-1;
        for(int i=0;i<basic.slots();i++)
            if(basic.at(i)!=null&&"BASE-TER-LAS".equals(basic.at(i).component)){
                laser=i;break;
            }
        check(laser>=0,"No laser slot");
        var rank6=ShipTechnologyFit.fit(basic,tech,SkirmishTechnologyRules.unrestricted(),
                laser,Shipyard.Track.EFFICIENCY,6,ShipTechnologyFit.Scope.SOCKET);
        if(!Shipyard.inspect(rank6,tech).valid())
            throw new AssertionError("Tier VI fixture must be physically serviceable");
        var low=SkirmishTechnologyRules.unrestricted().withStage(3);
        var high=SkirmishTechnologyRules.unrestricted().withStage(6);
        var left=new SkirmishFleet(List.of(basic,rank6,basic));
        var right=SkirmishFleet.repeat(Shipyard.starter(Battle.Faction.DRAXIAN,
                Shipyard.Hull.CORVETTE),2);
        reject(()->BattleServer.openLan("127.0.0.1",0,42L,left,right,low),
                "Tech III host accepted a Tier VI imported ship");
        try(BattleServer host=BattleServer.openLan("127.0.0.1",0,42L,left,right,high);
            BattleConnection playerA=BattleConnection.openLan("127.0.0.1",host.port(),false);
            BattleConnection playerB=BattleConnection.openLan("127.0.0.1",host.port(),false)){
            long deadline=System.nanoTime()+6_000_000_000L;
            while(System.nanoTime()<deadline&&
                    (playerA.snapshot()==null||playerB.snapshot()==null))Thread.sleep(20);
            check(playerA.snapshot()!=null&&playerB.snapshot()!=null,
                    "Host failed to deliver authoritative battle snapshots");
            long ships=playerA.snapshot().entities().stream().filter(x->x.startsWith("SHIP|")).count();
            check(ships==5,"Host did not spawn five individually selected ships");
            check(playerA.team==0&&playerB.team==1,
                    "LAN players were not assigned independent teams");
        }
        System.out.println("PASS PVP inclusive host tier, mixed fleet authorization, real TCP snapshots");
    }
}
