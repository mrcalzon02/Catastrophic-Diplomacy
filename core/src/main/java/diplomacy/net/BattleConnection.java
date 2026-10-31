package diplomacy.net;

import hbnet.Hbnet;
import diplomacy.sim.BattleOrders;
import diplomacy.shipyard.SkirmishTechnologyRules;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Platform-neutral authoritative snapshot client. Never computes campaign rewards.
 * A missing/hash-invalid snapshot preserves the previous complete view and closes the link.
 */
public final class BattleConnection implements AutoCloseable {
    private final Socket socket;
    public final int team;
    public final long seed;
    /** Null for legacy/battle-only servers; never derived from a local fleet export. */
    public final SkirmishTechnologyRules matchRules;
    private long sequence;
    public record Snapshot(long tick,long simulationHash,List<String> entities){}
    private volatile Snapshot snapshot;
    private volatile long acknowledged;
    private volatile boolean connected=true;
    private volatile String fleetStatus="No fleet submitted",lobbyStatus="Waiting for lobby";
    public String fleetStatus(){return fleetStatus;}
    public String lobbyStatus(){return lobbyStatus;}
    public BattleConnection(String address,int port,Hbnet.Client transport,boolean spectate)throws IOException{
        this(transport.connect(address,port),spectate);
    }
    public static BattleConnection openLan(String address,int port,boolean spectate)throws IOException{
        InetAddress target=InetAddress.getByName(Hbnet.host(address));
        if(!target.isLoopbackAddress()&&!target.isSiteLocalAddress()&&!target.isLinkLocalAddress())throw Hbnet.failure();
        Socket s=new Socket();try{s.connect(new InetSocketAddress(target,port),5000);return new BattleConnection(s,spectate);}catch(IOException e){s.close();throw Hbnet.failure();}
    }
    private BattleConnection(Socket socket,boolean spectate)throws IOException{
        this.socket=socket;
        try{
            socket.setSoTimeout(5000);Hbnet.write(socket.getOutputStream(),spectate?"SPECTATE|1":"PLAYER|1");String[] hello=Hbnet.read(socket.getInputStream()).split("\\|");
            if((hello.length!=6&&hello.length!=7)||!hello[0].equals("WELCOME")||!hello[1].equals("1"))throw Hbnet.failure();
            team=Integer.parseInt(hello[3]);seed=Long.parseLong(hello[4]);sequence=Long.parseLong(hello[5]);
            if(team< -1||team>1||sequence<0)throw Hbnet.failure();
            // The authenticated host (or explicitly trusted OPEN_LAN endpoint) supplies
            // the complete rules. Never accept a client-selected cap or a downgrade.
            matchRules=hello.length==7?SkirmishTechnologyRules.decode(new String(
                    Base64.getUrlDecoder().decode(hello[6]),StandardCharsets.UTF_8)):null;
            socket.setSoTimeout(30000);
        }catch(IOException|RuntimeException e){socket.close();throw Hbnet.failure();}
        Thread reader=new Thread(this::read,"diplomacy-authoritative-events");reader.setDaemon(true);reader.start();
        // A passive lobby player must remain connected beyond the socket's 30-second
        // input deadline. Heartbeats also prevent an idle spectator's server timeout.
        Thread heartbeat=new Thread(()->{
            while(connected){
                try{Thread.sleep(10_000);if(connected)synchronized(this){
                    Hbnet.write(socket.getOutputStream(),"SNAPSHOT");
                }}catch(IOException|InterruptedException stopped){close();return;}
            }
        },"diplomacy-connection-heartbeat");heartbeat.setDaemon(true);heartbeat.start();
    }
    public Snapshot snapshot(){return snapshot;}
    public boolean connected(){return connected;}
    public long acknowledged(){return acknowledged;}
    public synchronized long order(BattleOrders.PointDefense pd,BattleOrders.Position position)throws IOException{
        if(team<0||snapshot==null)throw Hbnet.failure();long next=++sequence;Hbnet.write(socket.getOutputStream(),"ORDER|"+next+"|"+snapshot.tick()+"|"+team+"|"+pd+"|"+position);return next;
    }
    /** Upload a saved .cdfleet over the existing authenticated/pinned-TLS or explicitly
     * trusted-LAN socket. The host independently decodes every ship and owns team IDs. */
    public synchronized void submitFleet(String savedFleet)throws IOException{
        if(!connected||team<0||matchRules==null||snapshot!=null||savedFleet==null)throw Hbnet.failure();
        byte[] raw=savedFleet.getBytes(StandardCharsets.UTF_8);
        if(raw.length<1||raw.length>120_000)throw Hbnet.failure();
        String wire=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        if(wire.length()>160_000)throw Hbnet.failure();
        int parts=(wire.length()+2799)/2800;
        fleetStatus="Submitting to host";
        Hbnet.write(socket.getOutputStream(),"FLEET_BEGIN|"+wire.length()+"|"+parts+"|"+
                Hbnet.hash(wire.getBytes(StandardCharsets.US_ASCII)));
        for(int i=0;i<parts;i++)Hbnet.write(socket.getOutputStream(),
                "FLEET_PART|"+i+"|"+wire.substring(i*2800,Math.min(wire.length(),(i+1)*2800)));
        Hbnet.write(socket.getOutputStream(),"FLEET_END");
    }
    private void read(){try{
        while(connected){String[] p=Hbnet.read(socket.getInputStream()).split("\\|",-1);
            if(p[0].equals("ACK")&&p.length==3){acknowledged=Long.parseLong(p[1]);continue;}
            if(p[0].equals("FLEET_ACCEPT")&&p.length==3){fleetStatus="Accepted by host: "+p[2]+" ships for seat "+p[1];continue;}
            if(p[0].equals("FLEET_REJECT")&&p.length==2){fleetStatus="Rejected by host: "+p[1];continue;}
            if(p[0].equals("LOBBY")&&p.length==3){lobbyStatus="Accepted seats "+p[1]+"/"+p[2];continue;}
            if(p[0].equals("BATTLE_START")&&p.length==2){lobbyStatus="Authoritative battle started";continue;}
            if(!p[0].equals("SNAP"))continue;
            if(p.length!=4)throw Hbnet.failure();long tick=Long.parseLong(p[1]),hash=Long.parseLong(p[2]);int count=Integer.parseInt(p[3]);if(count<0||count>8192)throw Hbnet.failure();
            List<String> body=new ArrayList<>();for(int i=0;i<count;i++){String line=Hbnet.read(socket.getInputStream());if(!(line.startsWith("SHIP|")||line.startsWith("MISSILE|")||line.startsWith("BALLISTIC|")||line.startsWith("CRAFT|")))throw Hbnet.failure();body.add(line);}
            String end=Hbnet.read(socket.getInputStream());if(!end.equals("END|"+tick+"|"+Hbnet.hash(String.join("\n",body).getBytes(StandardCharsets.US_ASCII))))throw Hbnet.failure();
            snapshot=new Snapshot(tick,hash,List.copyOf(body));
        }
    }catch(IOException|RuntimeException e){/* presentation remains at last complete authoritative frame */}finally{close();}}
    public void close(){connected=false;try{socket.close();}catch(IOException ignored){}}
}
