package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.shipyard.*;
import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;
import hbnet.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Supported dedicated-JAR TLS lobby path, not direct factory-only coverage. */
public final class TlsFleetLobbyLauncherTest {
    private TlsFleetLobbyLauncherTest(){}
    private static void check(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    private static void until(BooleanSupplier value)throws Exception{
        long end=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<end){
            if(value.getAsBoolean())return;
            Thread.sleep(20);
        }
        throw new AssertionError("TLS fleet lobby deadline");
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("diplomacy-tls-lobby-");
        try{
            Hbnet.Server provisioned=Setup.initialize(root,null);
            char[] room=Files.readString(root.resolve("room.secret")).toCharArray();
            Hbnet.Identity one=Hbnet.Identity.create(),two=Hbnet.Identity.create();
            TechnologyCatalog tech=TechnologyCatalog.loadDefault();
            Shipyard.Design left=Shipyard.starter(
                    Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
            Shipyard.Design right=Shipyard.starter(
                    Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);

            Hbnet.Client aTransport=new Hbnet.Client(provisioned.fingerprint,
                    "catastrophic-diplomacy",one,room);
            Hbnet.Client bTransport=new Hbnet.Client(provisioned.fingerprint,
                    "catastrophic-diplomacy",two,room);
            try(BattleServer host=BattleServer.openConfigured(new String[]{
                        root.toString(),"0","lobby","3","127.0.0.1"});
                BattleConnection a=new BattleConnection(
                        "127.0.0.1",host.port(),aTransport,false);
                BattleConnection b=new BattleConnection(
                        "127.0.0.1",host.port(),bTransport,false)){
                check(host.lobbyWaiting()&&host.tick()==0,
                        "Configured TLS launcher created battle before submitted fleets");
                check(a.team==0&&b.team==1&&a.hostSeat()&&!b.hostSeat(),
                        "TLS launcher did not preserve authenticated host/guest seats");
                check(a.matchRules!=null&&a.matchRules.maxStage()==3&&
                        b.matchRules!=null&&b.matchRules.maxStage()==3,
                        "TLS launcher did not publish host-selected Tech III ceiling");

                String leftWire=SkirmishFleetCodec.encode(
                        SkirmishFleet.repeat(left,2),tech);
                String rightWire=SkirmishFleetCodec.encode(
                        SkirmishFleet.repeat(right,2),tech);
                a.submitFleet(leftWire);b.submitFleet(rightWire);
                until(()->a.lobbyAccepted(0)&&a.lobbyAccepted(1)&&
                        b.lobbyAccepted(0)&&b.lobbyAccepted(1));
                check(a.snapshot()==null&&b.snapshot()==null,
                        "Accepted TLS fleets auto-started without readiness");
                a.ready(true);b.ready(true);
                until(()->a.lobbyReady(0)&&a.lobbyReady(1));
                a.startBattle();
                until(()->a.snapshot()!=null&&b.snapshot()!=null);
                check(!host.lobbyWaiting()&&a.protocolVersion==2&&b.protocolVersion==2,
                        "Configured TLS lobby did not start authoritative v2 battle");
                long ships=a.snapshot().decodedEntities().stream()
                        .filter(entity->entity instanceof BattleConnection.ShipEntity).count();
                check(ships==4,"TLS launcher lost submitted fleet ships");
            }finally{
                aTransport.clear();
                bTransport.clear();
                Arrays.fill(room,'\0');
            }

            try{
                BattleServer.openConfigured(new String[]{
                        root.toString(),"0","lobby","10","127.0.0.1"});
                throw new AssertionError("Invalid TLS lobby tech ceiling accepted");
            }catch(IllegalArgumentException expected){/* bounded config rejected */}
            try{
                BattleServer.openConfigured(new String[]{
                        root.resolve("missing").toString(),"0","lobby","3","127.0.0.1"});
                throw new AssertionError("Missing TLS identity silently downgraded");
            }catch(Exception expected){/* no plaintext fallback */}
            System.out.println("PASS dedicated TLS lobby launcher: persistent pin, Tech III rules, two submitted fleets, ready/start and no plaintext fallback");
        }finally{
            try(var files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(p);
            }
        }
    }
}
