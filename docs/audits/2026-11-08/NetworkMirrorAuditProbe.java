package diplomacy.client;
import java.util.List;
public final class NetworkMirrorAuditProbe {
  static String row(int team,String faction,int x,String hull){
    return "SHIP|11|"+team+"|"+faction+"|"+x+"|0|0|"+hull+"|90|80|70|60";
  }
  static final class FixtureSocket extends java.net.Socket {
    private final java.io.InputStream input;
    private final java.io.OutputStream output=new java.io.ByteArrayOutputStream();
    FixtureSocket(byte[] frames){input=new java.io.ByteArrayInputStream(frames);}
    public java.io.InputStream getInputStream(){return input;}
    public java.io.OutputStream getOutputStream(){return output;}
    public void setSoTimeout(int ignored){}
    public void close(){}
  }
  public static void main(String[] args) throws Exception {
    RemoteShipInterpolation s=new RemoteShipInterpolation();
    s.observe(300,List.of(row(0,"TERRAN",5,"100")));
    s.observe(300,List.of(row(1,"DRAXIAN",999,"200")));
    System.out.println("SAME_TICK_NEW_CONNECTION: x="+s.pose(11).x()+" team="+s.ships().get(0).team()+" (new connection fixture x=999 team=1 discarded)");
    s.observe(303,List.of(row(0,"TERRAN",999,"100")));
    System.out.println("LATER_TICK_NEW_CONNECTION: initial_x="+s.pose(11).x()+" (new connection fixture x=999 blends from previous server)");
    RemoteFleetMirrors mirrors=new RemoteFleetMirrors();
    var b=mirrors.present(s,false);
    System.out.println("MIRROR_DEFAULT: hullClass="+mirrors.byHostId(11).hullClass+" missiles="+b.missiles().size()+" ballistics="+b.ballistics().size()+" crafts="+b.crafts().size());
    RemoteShipInterpolation invalid=new RemoteShipInterpolation();
    invalid.observe(1,List.of(row(9,"TERRAN",5,"NaN")));
    java.io.ByteArrayOutputStream wire=new java.io.ByteArrayOutputStream();
    String invalidFaction=row(0,"INVALID_FACTION",5,"100");
    hbnet.Hbnet.write(wire,"WELCOME|1|fixture-session|0|2479|0");
    hbnet.Hbnet.write(wire,"SNAP|9|123|1");
    hbnet.Hbnet.write(wire,invalidFaction);
    hbnet.Hbnet.write(wire,"END|9|"+hbnet.Hbnet.hash(invalidFaction.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    var ctor=diplomacy.net.BattleConnection.class.getDeclaredConstructor(java.net.Socket.class,boolean.class);
    ctor.setAccessible(true);
    var client=(diplomacy.net.BattleConnection)ctor.newInstance(new FixtureSocket(wire.toByteArray()),false);
    for(int i=0;i<100&&client.snapshot()==null;i++)Thread.sleep(5);
    boolean renderRejected=false;
    try{new RemoteShipInterpolation().observe(client.snapshot().tick(),client.snapshot().entities());}
    catch(IllegalArgumentException failure){renderRejected=true;}
    System.out.println("MALFORMED_SNAPSHOT: accepted_by_connection="+(client.snapshot()!=null)+" throws_in_render_parser="+renderRejected);
    client.close();
    System.out.println("UNVALIDATED_STATE: accepted_team="+invalid.ships().get(0).team()+" accepted_hull="+invalid.ships().get(0).hull());
  }
}
