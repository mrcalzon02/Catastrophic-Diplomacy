package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.sim.BattleOrders;

/** Independent JVM acceptance client for explicitly unencrypted localhost test sessions. */
public final class BattleSocketProbe {
    public static void main(String[] args)throws Exception{
        try(BattleConnection client=BattleConnection.openLan("127.0.0.1",Integer.parseInt(args[0]),false)){
            long deadline=System.nanoTime()+8_000_000_000L;while(client.snapshot()==null&&System.nanoTime()<deadline)Thread.sleep(20);
            if(client.snapshot()==null)throw new AssertionError("Missing authoritative initial snapshot");
            long sequence=client.order(client.team==0?BattleOrders.PointDefense.HOLD_FIRE:BattleOrders.PointDefense.ANTI_CRAFT,BattleOrders.Position.KEEP_MOVING);
            while((client.acknowledged()!=sequence||client.snapshot().tick()<30)&&System.nanoTime()<deadline)Thread.sleep(20);
            if(client.acknowledged()!=sequence||client.snapshot().tick()<30)throw new AssertionError("Independent player order/snapshot reconciliation");
            System.out.println("PASS independent battle client team="+client.team+" tick="+client.snapshot().tick()+" entities="+client.snapshot().entities().size());
        }
    }
}
