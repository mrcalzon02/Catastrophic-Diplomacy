package diplomacy.server;

import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import diplomacy.sim.BattleTermination;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

/**
 * Actual trusted-LAN sockets, bounded frames, one terminal result and late
 * spectator status. The core's separate BattleTerminationTest proves physical
 * clocks; only the terminal state is injected here to avoid a 60-minute test.
 */
public final class BattleTerminalProtocolTest {
    private BattleTerminalProtocolTest(){}
    private static void ok(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static void until(BooleanSupplier predicate)throws Exception{
        long deadline=System.nanoTime()+8_000_000_000L;
        while(System.nanoTime()<deadline){
            if(predicate.getAsBoolean())return;
            Thread.sleep(15);
        }
        throw new AssertionError("Timed out awaiting actual LAN outcome");
    }
    private static void run(String expected)throws Exception{
        try(BattleServer server=BattleServer.openLan("127.0.0.1",0,19019L,1);
            BattleConnection first=BattleConnection.openLan("127.0.0.1",server.port(),false)){
            until(()->first.snapshot()!=null&&first.snapshot().tick()>0);
            // White-box fixture exclusively for transport testing. Never an
            // exported simulation control or a manufactured campaign reward.
            Field lockField=BattleServer.class.getDeclaredField("lock");
            lockField.setAccessible(true);
            Field battleField=BattleServer.class.getDeclaredField("battle");
            battleField.setAccessible(true);
            Field reasonField=Battle.class.getDeclaredField("terminationReason");
            reasonField.setAccessible(true);
            Object lock=lockField.get(server);
            synchronized(lock){
                Battle real=(Battle)battleField.get(server);
                ok(real.alive(0)>0&&real.alive(1)>0,"Live test fixture already ended");
                if(expected.equals("LEFT_VICTORY")||
                        expected.equals("NO_FIELD_CONTROL")){
                    for(Battle.Ship ship:real.ships()){
                        if(ship.team==1)ship.hull=0;
                        if(ship.team==0&&expected.equals("NO_FIELD_CONTROL"))
                            ship.withdrawing=true;
                    }
                }else{
                    reasonField.set(real,BattleTermination.Reason.valueOf(expected));
                }
            }
            until(()->expected.equals(first.terminalOutcome()));
            long terminalTick=server.tick(),terminalHash=first.snapshot().simulationHash();
            Thread.sleep(120);
            ok(server.tick()==terminalTick,"Finished authoritative server kept simulating");
            ok(first.snapshot().simulationHash()==terminalHash,
                    "Authoritative terminal frame drifted after completion");
            try(BattleConnection spectator=BattleConnection.openLan(
                    "127.0.0.1",server.port(),true)){
                until(()->spectator.snapshot()!=null);
                until(()->expected.equals(spectator.terminalOutcome()));
                ok(spectator.snapshot().tick()==terminalTick,
                        "Late spectator lost the final tick");
                ok(spectator.team==-1,"Late spectator stole a player's fleet seat");
            }
            System.out.println("PASS LAN terminal "+expected+
                    " first player and late spectator, tick "+terminalTick);
        }
    }
    public static void main(String[] args)throws Exception{
        run("STALEMATE");
        run("TIME_LIMIT");
        run("LEFT_VICTORY");
        run("NO_FIELD_CONTROL");
    }
}
