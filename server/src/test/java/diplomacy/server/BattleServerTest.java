package diplomacy.server;

import hbnet.*;
import diplomacy.net.BattleConnection;
import diplomacy.sim.BattleOrders;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class BattleServerTest {
    private static void until(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+8_000_000_000L;while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(20);}throw new AssertionError("Battle socket deadline");}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("diplomacy-tls-");
        try{
            Hbnet.Server tls=Setup.initialize(root,null);char[] key=Files.readString(root.resolve("room.secret")).toCharArray();Hbnet.Identity one=Hbnet.Identity.create(),two=Hbnet.Identity.create();
            for(int size:new int[]{1,16,200}){
                Hbnet.Client a=new Hbnet.Client(tls.fingerprint,"catastrophic-diplomacy",one,key),b=new Hbnet.Client(tls.fingerprint,"catastrophic-diplomacy",two,key);
                try(BattleServer server=new BattleServer("127.0.0.1",0,2479,size,tls);
                    BattleConnection first=new BattleConnection("localhost",server.port(),a,false);BattleConnection second=new BattleConnection("localhost",server.port(),b,false)){
                    until(()->first.snapshot()!=null&&second.snapshot()!=null);
                    if(first.team!=0||second.team!=1)throw new AssertionError("server-owned fleets");
                    long ack=first.order(BattleOrders.PointDefense.HOLD_FIRE,BattleOrders.Position.HOLD);until(()->first.acknowledged()==ack&&server.pointDefense(0)==BattleOrders.PointDefense.HOLD_FIRE);
                    long ack2=second.order(BattleOrders.PointDefense.ANTI_CRAFT,BattleOrders.Position.KEEP_MOVING);until(()->second.acknowledged()==ack2&&server.pointDefense(1)==BattleOrders.PointDefense.ANTI_CRAFT);
                    until(()->second.snapshot().tick()>15);if(second.snapshot().entities().stream().filter(s->s.startsWith("SHIP|")).count()!=size*2L)throw new AssertionError("snapshot completeness");
                    first.close();Thread.sleep(120);try(BattleConnection resumed=new BattleConnection("127.0.0.1",server.port(),a,false)){if(resumed.team!=0)throw new AssertionError("identity must recover original fleet");until(()->resumed.snapshot()!=null);long next=resumed.order(BattleOrders.PointDefense.BALANCED,BattleOrders.Position.FREE);if(next<=ack)throw new AssertionError("sequence watermark lost");}
                    server.saveReplay(root.resolve("battle-"+size+".replay"));
                    System.out.println("PASS BattleServerTest ships="+(size*2)+" tick="+server.tick()+" max_tick_us="+server.maxTickNanos()/1000+" queued_bytes="+server.bytesQueued());
                }
                a.clear();b.clear();
            }
            Arrays.fill(key,'\0');
        }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
