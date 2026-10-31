package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.shipyard.*;
import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;
import hbnet.Hbnet;
import java.net.Socket;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Real loopback clients: host never spawns until BOTH independently legal fleets. */
public final class FleetLobbyTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static void until(BooleanSupplier yes)throws Exception{
        long end=System.nanoTime()+8_000_000_000L;
        while(System.nanoTime()<end){if(yes.getAsBoolean())return;Thread.sleep(20);}
        throw new AssertionError("Fleet lobby deadline");
    }
    private static Shipyard.Design rank(Shipyard.Design basic,TechnologyCatalog tech,int stage){
        int laser=-1;
        for(int i=0;i<basic.slots();i++)
            if(basic.at(i)!=null&&"BASE-TER-LAS".equals(basic.at(i).component)){laser=i;break;}
        check(laser>=0,"Missing fitting socket");
        return ShipTechnologyFit.fit(basic,tech,SkirmishTechnologyRules.unrestricted(),
                laser,Shipyard.Track.EFFICIENCY,stage,ShipTechnologyFit.Scope.SOCKET);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        var basic=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var other=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);
        var t3=rank(basic,tech,3);var t4=rank(basic,tech,4);var t6=rank(basic,tech,6);
        var low=SkirmishTechnologyRules.unrestricted().withStage(3);
        var high=SkirmishTechnologyRules.unrestricted().withStage(6);
        try(BattleServer host=BattleServer.openLanLobby("127.0.0.1",0,101L,low);
            BattleConnection a=BattleConnection.openLan("127.0.0.1",host.port(),false);
            BattleConnection b=BattleConnection.openLan("127.0.0.1",host.port(),false)){
            check(a.team==0&&b.team==1,"Server-owned independent team seats");
            check(low.equals(a.matchRules)&&low.equals(b.matchRules),"One host policy");
            check(host.lobbyWaiting()&&host.tick()==0&&a.snapshot()==null&&b.snapshot()==null,
                    "No battle before both legal fleets");
            a.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t4)),tech));
            until(()->a.fleetStatus().startsWith("Rejected"));
            check(host.tick()==0&&a.snapshot()==null,"Tech 4 must not spawn at Tech 3");
            a.submitFleet("malformed export");
            until(()->a.fleetStatus().startsWith("Rejected"));
            a.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t3)),tech));
            until(()->a.fleetStatus().startsWith("Accepted"));
            check(host.lobbyWaiting()&&b.snapshot()==null,"One accepted seat must not spawn");
            b.submitFleet(SkirmishFleetCodec.encode(SkirmishFleet.repeat(other,2),tech));
            until(()->a.snapshot()!=null&&b.snapshot()!=null);
            check(!host.lobbyWaiting()&&host.tick()>=0,"Two legal submissions start battle");
            check(a.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()==4,
                    "Authoritative battle must contain four fitted ships");
        }
        try(BattleServer host=BattleServer.openLanLobby("127.0.0.1",0,202L,high);
            BattleConnection a=BattleConnection.openLan("127.0.0.1",host.port(),false);
            BattleConnection b=BattleConnection.openLan("127.0.0.1",host.port(),false)){
            check(high.equals(a.matchRules),"Tech 6 ceiling published");
            a.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t3,t6)),tech));
            until(()->a.fleetStatus().startsWith("Accepted"));
            check(a.snapshot()==null,"No premature mixed-tier spawn");
            b.submitFleet(SkirmishFleetCodec.encode(SkirmishFleet.repeat(other,2),tech));
            until(()->a.snapshot()!=null&&b.snapshot()!=null);
            check(a.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()==5,
                    "Tech 6 must preserve mixed lower-tier ships");
            check(t6.at(0)!=null&&basic.slots()>0,"Source designs preserved");
        }
        try(BattleServer host=BattleServer.openLanLobby("127.0.0.1",0,303L,low);
            Socket raw=new Socket("127.0.0.1",host.port())){
            raw.setSoTimeout(5000);
            Hbnet.write(raw.getOutputStream(),"PLAYER|1");
            check(Hbnet.read(raw.getInputStream()).startsWith("WELCOME|1|"),"Raw seat handshake");
            Hbnet.write(raw.getOutputStream(),"FLEET_BEGIN|160001|58|"+
                    "a".repeat(64)); // oversized payload rejected before allocation
            boolean rejected=false;
            for(int i=0;i<4;i++)if(Hbnet.read(raw.getInputStream()).equals("FLEET_REJECT|INVALID")){rejected=true;break;}
            check(rejected&&host.lobbyWaiting(),"Oversize header rejected, lobby intact");
            Hbnet.write(raw.getOutputStream(),"FLEET_BEGIN|0|0|0|1"); // spoofed team/format
            rejected=false;
            for(int i=0;i<4;i++)if(Hbnet.read(raw.getInputStream()).equals("FLEET_REJECT|INVALID")){rejected=true;break;}
            check(rejected&&host.tick()==0,"Spoofed team header rejected");
        }
        System.out.println("PASS two-client fleet lobby Tech3/6, mixed tiers, bad/oversize/spoof rejection and real battle snapshots");
    }
}
