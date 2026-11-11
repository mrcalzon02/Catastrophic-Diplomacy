package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.shipyard.*;
import diplomacy.sim.Battle;
import diplomacy.sim.SensorActiveScan;
import diplomacy.sim.SensorScanOrder;
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
    private static void readyAndStart(BattleConnection hostSeat,
                                      BattleConnection other)throws Exception{
        hostSeat.ready(true);
        other.ready(true);
        until(()->hostSeat.lobbyReady(0)&&hostSeat.lobbyReady(1)&&
                other.lobbyReady(0)&&other.lobbyReady(1));
        check(hostSeat.snapshot()==null&&other.snapshot()==null,
                "Ready state spawned battle before explicit host start");
        hostSeat.startBattle();
        until(()->hostSeat.snapshot()!=null&&other.snapshot()!=null);
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
            until(()->a.lobbyAccepted(0)&&a.lobbyAccepted(1));
            check(host.lobbyWaiting()&&host.tick()==0&&a.snapshot()==null&&b.snapshot()==null,
                    "Two legal submissions must wait for ready/start");
            a.scanOrder(0,new SensorScanOrder(SensorActiveScan.Mode.FOCUSED_RADAR,0));
            b.scanOrder(1,new SensorScanOrder(SensorActiveScan.Mode.PASSIVE,-1));
            until(()->a.scanStatus().startsWith("Accepted")&&b.scanStatus().startsWith("Accepted"));
            try{b.startBattle();throw new AssertionError("Non-host start call allowed");}
            catch(java.io.IOException expected){/* client rejects non-host start */}
            readyAndStart(a,b);
            check(!host.lobbyWaiting()&&host.tick()>=0,"Host start did not create battle");
            check(host.scanMode(0,0)==SensorActiveScan.Mode.FOCUSED_RADAR&&
                            host.scanFocusTargetId(0,0)==host.shipIdAt(1,0)&&
                            host.scanMode(1,1)==SensorActiveScan.Mode.PASSIVE,
                    "Authorized prebattle scan orders did not reach real server ships");
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
            until(()->a.lobbyAccepted(0)&&a.lobbyAccepted(1));
            a.ready(true);b.ready(true);
            until(()->a.lobbyReady(0)&&a.lobbyReady(1));
            a.scanOrder(0,new SensorScanOrder(SensorActiveScan.Mode.BOOSTED_RADAR,-1));
            until(()->a.scanStatus().startsWith("Accepted")&&!a.lobbyReady(0));
            a.ready(true);
            until(()->a.lobbyReady(0)&&a.lobbyReady(1));
            a.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t3,t6)),tech));
            until(()->a.lobbyAccepted(0)&&!a.lobbyReady(0));
            a.startBattle();
            Thread.sleep(120);
            check(a.snapshot()==null&&b.snapshot()==null,
                    "Fleet replacement retained stale readiness");
            a.ready(true);
            until(()->a.lobbyReady(0)&&a.lobbyReady(1));
            a.startBattle();
            until(()->a.snapshot()!=null&&b.snapshot()!=null);
            check(a.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()==5,
                    "Tech 6 must preserve mixed lower-tier ships");
            check(t6.at(0)!=null&&basic.slots()>0,"Source designs preserved");
        }
        // A disconnected owner must lose its accepted fleet; an incoming trusted-LAN
        // seat cannot inherit that fleet or trigger an early battle.
        try(BattleServer host=BattleServer.openLanLobby("127.0.0.1",0,252L,low);
            BattleConnection a=BattleConnection.openLan("127.0.0.1",host.port(),false);
            BattleConnection b=BattleConnection.openLan("127.0.0.1",host.port(),false)){
            a.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t3)),tech));
            until(()->b.lobbyAccepted(0)&&!b.lobbyAccepted(1));
            a.close();
            until(()->!b.lobbyAccepted(0)&&!b.lobbyAccepted(1));
            check(host.lobbyWaiting()&&host.tick()==0&&b.snapshot()==null,
                    "Disconnect must invalidate its accepted fleet");
            try(BattleConnection replacement=BattleConnection.openLan("127.0.0.1",host.port(),false)){
                check(replacement.team==0,"LAN disconnect must free the original seat");
                replacement.submitFleet(SkirmishFleetCodec.encode(new SkirmishFleet(List.of(basic,t3)),tech));
                b.submitFleet(SkirmishFleetCodec.encode(SkirmishFleet.repeat(other,2),tech));
                until(()->replacement.lobbyAccepted(0)&&replacement.lobbyAccepted(1));
                readyAndStart(replacement,b);
                check(replacement.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()==4,
                        "Replacement must submit a fresh legal fleet");
            }
        }
        try(BattleServer host=BattleServer.openLanLobby("127.0.0.1",0,303L,low);
            Socket raw=new Socket("127.0.0.1",host.port())){
            raw.setSoTimeout(5000);
            Hbnet.write(raw.getOutputStream(),"PLAYER|1");
            check(Hbnet.read(raw.getInputStream()).startsWith("WELCOME|2|"),"Raw seat handshake");
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
        System.out.println("PASS two-client fleet lobby: accepted fleets, persistent prebattle scan orders, ready/unready, host-only start, readiness invalidation, Tech3/6 and malformed rejection");
    }
}
