package diplomacy.net;

import hbnet.Hbnet;
import diplomacy.sim.Battle;
import diplomacy.sim.BattleArena;
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
    /** Server-instance match identity. Equal/higher ticks from another session
     * must never inherit interpolation or entity mirrors from this one. */
    public final String sessionId;
    /** Null for legacy/battle-only servers; never derived from a local fleet export. */
    public final SkirmishTechnologyRules matchRules;
    private long sequence;
    public enum EntityKind { SHIP, MISSILE, BALLISTIC, CRAFT }
    public sealed interface Entity permits ShipEntity, BodyEntity {
        EntityKind kind();
        long id();
        int team();
        double x();
        double y();
    }
    public record ShipEntity(long id,int team,Battle.Faction faction,
                             double x,double y,double facing,double hull,
                             double shields,double armor,double power,double fuel)
            implements Entity {
        @Override public EntityKind kind(){return EntityKind.SHIP;}
    }
    public record BodyEntity(EntityKind kind,long id,int team,
                             double x,double y,double integrity)
            implements Entity {
        public BodyEntity{
            if(kind==null||kind==EntityKind.SHIP)
                throw new IllegalArgumentException("Invalid body entity kind");
        }
    }
    public record Snapshot(String sessionId,long tick,long simulationHash,
                           List<String> entities,List<Entity> decodedEntities){
        public Snapshot{
            if(sessionId==null||
                    !sessionId.matches("[A-Za-z0-9._:-]{1,128}")||
                    tick<0||entities==null||decodedEntities==null||
                    entities.size()!=decodedEntities.size())
                throw new IllegalArgumentException("Invalid authoritative snapshot");
            entities=List.copyOf(entities);
            decodedEntities=List.copyOf(decodedEntities);
        }
    }
    private volatile Snapshot snapshot;
    private volatile long acknowledged;
    private volatile boolean connected=true;
    private volatile String fleetStatus="No fleet submitted",lobbyStatus="Waiting for lobby";
    /** Host-issued real outcome, independent from whichever ship remains on screen. */
    private volatile String terminalOutcome="";
    public String fleetStatus(){return fleetStatus;}
    public String lobbyStatus(){return lobbyStatus;}
    public String terminalOutcome(){return terminalOutcome;}
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
            sessionId=hello[2];
            team=Integer.parseInt(hello[3]);seed=Long.parseLong(hello[4]);sequence=Long.parseLong(hello[5]);
            if(!sessionId.matches("[A-Za-z0-9._:-]{1,128}")||
                    team< -1||team>1||sequence<0)throw Hbnet.failure();
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
    private static IllegalArgumentException malformed(){
        return new IllegalArgumentException("Malformed authoritative snapshot");
    }
    private static long positiveId(String raw){
        long id=Long.parseLong(raw);
        if(id<=0)throw malformed();
        return id;
    }
    private static int team(String raw){
        int value=Integer.parseInt(raw);
        if(value<0||value>1)throw malformed();
        return value;
    }
    private static double finite(String raw,double low,double high){
        double value=Double.parseDouble(raw);
        if(!Double.isFinite(value)||value<low||value>high)throw malformed();
        return value;
    }
    private static double coordinate(String raw,boolean x){
        double value=finite(raw,-100_000,100_000);
        if(x){
            if(value<BattleArena.MIN_X||value>BattleArena.MAX_X)throw malformed();
        }else if(value<BattleArena.MIN_Y||value>BattleArena.MAX_Y)throw malformed();
        return value;
    }
    static Entity decodeEntity(String line){
        if(line==null)throw malformed();
        String[] p=line.split("\\|",-1);
        try{
            return switch(p[0]){
                case "SHIP" -> {
                    if(p.length!=12)throw malformed();
                    long id=positiveId(p[1]);
                    int side=team(p[2]);
                    Battle.Faction faction=Battle.Faction.valueOf(p[3]);
                    double x=coordinate(p[4],true),y=coordinate(p[5],false);
                    double facing=finite(p[6],-StrictMath.PI-1e-9,StrictMath.PI+1e-9);
                    double hull=finite(p[7],0,1_000_000_000);
                    double shields=finite(p[8],0,1_000_000_000);
                    double armor=finite(p[9],0,1_000_000_000);
                    double power=finite(p[10],0,1_000_000_000);
                    double fuel=finite(p[11],0,1_000_000_000);
                    yield new ShipEntity(id,side,faction,x,y,facing,
                            hull,shields,armor,power,fuel);
                }
                case "MISSILE","BALLISTIC","CRAFT" -> {
                    if(p.length!=6)throw malformed();
                    EntityKind kind=EntityKind.valueOf(p[0]);
                    long id=positiveId(p[1]);
                    int side=team(p[2]);
                    double x=coordinate(p[3],true),y=coordinate(p[4],false);
                    double integrity=finite(p[5],0,1_000_000_000);
                    yield new BodyEntity(kind,id,side,x,y,integrity);
                }
                default -> throw malformed();
            };
        }catch(IllegalArgumentException invalid){
            throw malformed();
        }
    }
    static List<Entity> decodeEntities(List<String> rows){
        if(rows==null||rows.size()>8192)throw malformed();
        ArrayList<Entity> decoded=new ArrayList<>(rows.size());
        HashSet<String> identities=new HashSet<>();
        for(String row:rows){
            Entity entity=decodeEntity(row);
            String key=entity.kind().name()+":"+entity.id();
            if(!identities.add(key))throw malformed();
            decoded.add(entity);
        }
        return List.copyOf(decoded);
    }

    private void read(){try{
        while(connected){String[] p=Hbnet.read(socket.getInputStream()).split("\\|",-1);
            if(p[0].equals("ACK")&&p.length==3){acknowledged=Long.parseLong(p[1]);continue;}
            if(p[0].equals("FLEET_ACCEPT")&&p.length==3){fleetStatus="Accepted by host: "+p[2]+" ships for seat "+p[1];continue;}
            if(p[0].equals("FLEET_REJECT")&&p.length==2){fleetStatus="Rejected by host: "+p[1];continue;}
            if(p[0].equals("LOBBY")&&p.length==3){lobbyStatus="Accepted seats "+p[1]+"/"+p[2];continue;}
            if(p[0].equals("BATTLE_START")&&p.length==2){lobbyStatus="Authoritative battle started";continue;}
            if(p[0].equals("RESULT")&&p.length==3){
                // Preserve the authoritative, fully parsed last snapshot.
                // Reject unknown winner labels and broken frame sequences.
                if(snapshot==null||Long.parseLong(p[2])!=snapshot.tick()||
                        !java.util.Set.of("STALEMATE","TIME_LIMIT",
                                "LEFT_VICTORY","RIGHT_VICTORY",
                                "MUTUAL_DESTRUCTION","NO_FIELD_CONTROL").contains(p[1]))
                    throw Hbnet.failure();
                terminalOutcome=p[1];
                continue;
            }
            if(!p[0].equals("SNAP"))continue;
            if(p.length!=4)throw Hbnet.failure();
            long tick=Long.parseLong(p[1]),hash=Long.parseLong(p[2]);
            int count=Integer.parseInt(p[3]);
            if(tick<0||count<0||count>8192||
                    (snapshot!=null&&tick<snapshot.tick()))throw Hbnet.failure();
            List<String> body=new ArrayList<>(count);
            for(int i=0;i<count;i++)body.add(Hbnet.read(socket.getInputStream()));
            // Semantic decode happens before publication. A checksummed frame
            // with invalid enums, nonfinite values or duplicate identities
            // never becomes the current authoritative view.
            List<Entity> decoded=decodeEntities(body);
            String end=Hbnet.read(socket.getInputStream());
            if(!end.equals("END|"+tick+"|"+Hbnet.hash(
                    String.join("\n",body).getBytes(StandardCharsets.US_ASCII))))
                throw Hbnet.failure();
            snapshot=new Snapshot(sessionId,tick,hash,body,decoded);
        }
    }catch(IOException|RuntimeException e){/* presentation remains at last complete authoritative frame */}finally{close();}}
    public void close(){connected=false;try{socket.close();}catch(IOException ignored){}}
}
