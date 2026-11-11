package diplomacy.net;

import hbnet.Hbnet;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Semantic snapshot validation and last-good publication boundary. */
public final class BattleConnectionSnapshotTest {
    private BattleConnectionSnapshotTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static void rejects(String row,String why){
        try{BattleConnection.decodeEntity(row);throw new AssertionError(why);}
        catch(RuntimeException expected){/* rejected */}
    }
    private static void rejectsV2(String row,String why){
        try{BattleConnection.decodeEntity(row,2);throw new AssertionError(why);}
        catch(RuntimeException expected){/* rejected */}
    }
    private static String ship(long id,int team,String faction,String x){
        return "SHIP|"+id+"|"+team+"|"+faction+"|"+x+"|300|0|100|50|40|30|20";
    }
    private static final class FixtureSocket extends java.net.Socket {
        private final InputStream input;
        private final OutputStream output=new ByteArrayOutputStream();
        FixtureSocket(byte[] frames){input=new ByteArrayInputStream(frames);}
        @Override public InputStream getInputStream(){return input;}
        @Override public OutputStream getOutputStream(){return output;}
        @Override public void setSoTimeout(int ignored){}
        @Override public synchronized void close(){}
    }
    private static BattleConnection fixture(byte[] bytes)throws Exception{
        Constructor<BattleConnection> ctor=BattleConnection.class
                .getDeclaredConstructor(java.net.Socket.class,boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(new FixtureSocket(bytes),false);
    }
    private static void semanticRows(){
        var valid=BattleConnection.decodeEntity(ship(11,0,"TERRAN","200"));
        check(valid instanceof BattleConnection.ShipEntity&&valid.id()==11&&valid.team()==0,
                "Valid typed ship row rejected");
        check(BattleConnection.decodeEntity("MISSILE|9|1|500|300|2.5")
                        instanceof BattleConnection.MissileEntity,
                "Valid typed v1 missile row rejected");
        var v2Ship=BattleConnection.decodeEntity(
                "SHIP|21|1|DRAXIAN|DREADNOUGHT|600|300|0|900|1000|80|120|140|180|90|75",2);
        check(v2Ship instanceof BattleConnection.ShipEntity ship&&
                        ship.hullClass()==diplomacy.shipyard.Shipyard.Hull.DREADNOUGHT&&
                        ship.maxHull()==1000,
                "V2 ship chassis/max-state identity rejected");
        var v2Missile=BattleConnection.decodeEntity(
                "MISSILE|31|0|21|11|500|300|2.5|true|true|false",2);
        check(v2Missile instanceof BattleConnection.MissileEntity missile&&
                        missile.torpedo()&&missile.fighterLaunched()&&!missile.decoy(),
                "V2 guided-body presentation identity rejected");
        var v2Ballistic=BattleConnection.decodeEntity(
                "BALLISTIC|32|0|21|11|500|300|1.5|true",2);
        check(v2Ballistic instanceof BattleConnection.BallisticEntity shell&&
                        shell.fighterLaunched(),
                "V2 ballistic launch identity rejected");
        var v2Craft=BattleConnection.decodeEntity(
                "CRAFT|33|0|21|11|500|300|12|true|false|MISSILE_FIGHTER",2);
        check(v2Craft instanceof BattleConnection.CraftEntity craft&&
                        craft.fighter()&&craft.fighterHull()==
                        diplomacy.sim.FighterModel.Hull.MISSILE_FIGHTER,
                "V2 craft role identity rejected");
        rejects(ship(11,9,"TERRAN","200"),"Invalid team accepted");
        rejects(ship(11,0,"INVALID","200"),"Invalid faction accepted");
        rejects(ship(11,0,"TERRAN","NaN"),"NaN coordinate accepted");
        rejects(ship(11,0,"TERRAN","99999"),"Out-of-arena coordinate accepted");
        rejects("MISSILE|0|1|500|300|2.5","Zero entity ID accepted");
        rejects("CRAFT|4|0|500|300|Infinity","Infinite integrity accepted");
        rejects("BALLISTIC|4|0|500|300","Wrong field count accepted");
        rejectsV2("SHIP|21|1|DRAXIAN|DREADNOUGHT|600|300|0|1001|1000|80|120|140|180|90|75",
                "V2 current hull above max accepted");
        rejectsV2("CRAFT|33|0|21|11|500|300|12|false|false|MISSILE_FIGHTER",
                "Non-fighter craft carried fighter hull identity");
        try{
            BattleConnection.decodeEntities(List.of(
                    "MISSILE|9|1|500|300|2.5",
                    "MISSILE|9|1|510|300|2.4"));
            throw new AssertionError("Duplicate physical identity accepted");
        }catch(RuntimeException expected){/* rejected */}
    }
    private static void lastGoodFrame()throws Exception{
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        Hbnet.write(wire,"WELCOME|1|fixture-session|0|2479|0");
        String good=ship(11,0,"TERRAN","200");
        Hbnet.write(wire,"SNAP|10|123|1");
        Hbnet.write(wire,good);
        Hbnet.write(wire,"END|10|"+Hbnet.hash(good.getBytes(StandardCharsets.US_ASCII)));
        String bad=ship(12,0,"INVALID","220");
        Hbnet.write(wire,"SNAP|11|124|1");
        Hbnet.write(wire,bad);
        Hbnet.write(wire,"END|11|"+Hbnet.hash(bad.getBytes(StandardCharsets.US_ASCII)));
        try(BattleConnection client=fixture(wire.toByteArray())){
            long deadline=System.nanoTime()+2_000_000_000L;
            while(client.connected()&&System.nanoTime()<deadline)Thread.sleep(2);
            BattleConnection.Snapshot snapshot=client.snapshot();
            check(snapshot!=null&&snapshot.tick()==10,
                    "Malformed checksummed frame replaced last-good snapshot");
            check(snapshot.decodedEntities().size()==1&&
                    snapshot.decodedEntities().get(0) instanceof BattleConnection.ShipEntity,
                    "Last-good snapshot was not fully typed");
            check(!client.connected(),"Malformed snapshot did not terminate bad link");
        }
    }
    public static void main(String[] args)throws Exception{
        semanticRows();
        lastGoodFrame();
        System.out.println("PASS v1/v2 typed bounded snapshot decode, presentation identity, duplicate rejection and last-good preservation");
    }
}
