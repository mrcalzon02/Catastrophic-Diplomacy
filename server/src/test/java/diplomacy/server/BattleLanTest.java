package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.sim.BattleOrders;
import java.net.*;
import java.util.function.BooleanSupplier;
import hbnet.Hbnet;

/** Credential-free opt-in LAN acceptance, explicitly NOT a secure/WAN test. */
public final class BattleLanTest {
    private static void until(BooleanSupplier c)throws Exception{long end=System.nanoTime()+8_000_000_000L;while(System.nanoTime()<end){if(c.getAsBoolean())return;Thread.sleep(20);}throw new AssertionError("LAN battle deadline");}
    public static void main(String[] args)throws Exception{
        for(int size:new int[]{1,16,200})try(BattleServer server=BattleServer.openLan("127.0.0.1",0,2479,size);
            BattleConnection a=BattleConnection.openLan("localhost",server.port(),false);BattleConnection b=BattleConnection.openLan("localhost",server.port(),false)){
            until(()->a.snapshot()!=null&&b.snapshot()!=null);if(a.team!=0||b.team!=1)throw new AssertionError("fleet authority");
            final long seq=a.order(BattleOrders.PointDefense.HOLD_FIRE,BattleOrders.Position.HOLD);until(()->a.acknowledged()==seq&&server.pointDefense(0)==BattleOrders.PointDefense.HOLD_FIRE);
            final long expected=b.order(BattleOrders.PointDefense.ANTI_CRAFT,BattleOrders.Position.KEEP_MOVING);until(()->b.acknowledged()==expected&&server.pointDefense(1)==BattleOrders.PointDefense.ANTI_CRAFT);
            until(()->b.snapshot().tick()>15);if(b.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()!=size*2L)throw new AssertionError("incomplete snapshot");
            try(Socket malicious=new Socket("127.0.0.1",server.port())){Hbnet.write(malicious.getOutputStream(),"SPECTATE|1");Hbnet.read(malicious.getInputStream());Hbnet.write(malicious.getOutputStream(),"ORDER|1|"+server.tick()+"|0|BALANCED|FREE");Thread.sleep(100);if(server.pointDefense(0)!=BattleOrders.PointDefense.HOLD_FIRE)throw new AssertionError("spectator stole fleet");}
            System.out.println("PASS OPEN_LAN ships="+(size*2)+" tick="+server.tick()+" max_tick_us="+server.maxTickNanos()/1000+" queued_bytes="+server.bytesQueued()+" (trusted loopback only; no TLS claim)");
        }
    }
}
