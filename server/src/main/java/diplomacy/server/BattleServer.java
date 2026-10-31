package diplomacy.server;

import hbnet.Hbnet;
import diplomacy.sim.*;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.SkirmishShipExchange;
import diplomacy.shipyard.SkirmishFleet;
import diplomacy.shipyard.SkirmishFleetCodec;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.tech.TechnologyCatalog;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone authoritative Java 17 battle. No libGDX, graphics, assets or audio.
 * The host validates imported fleet blueprints against its match technology tier;
 * campaign wallets and client-asserted technology permissions are never trusted.
 */
public final class BattleServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Hbnet.Server transport;
    /** The server's complete immutable match policy, encoded for a bounded welcome frame. */
    private final String matchRulesWire;
    /** Null until both host-validated lobby fleets have been received. Guarded by lock. */
    private Battle battle;
    private final long matchSeed;
    private final SkirmishTechnologyRules hostRules;
    private final SkirmishFleet[] submitted=new SkirmishFleet[2];
    private static final int MAX_FLEET_WIRE=160_000, FLEET_CHUNK=2800;
    private final Object lock=new Object();
    private final String session=UUID.randomUUID().toString();
    private final ExecutorService workers=Executors.newCachedThreadPool();
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor();
    private final Semaphore slots=new Semaphore(8);
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final Map<String,Seat> seats=new HashMap<>();
    private final Set<Peer> peers=new HashSet<>();
    private final List<String> orders=new ArrayList<>();
    private volatile boolean closed;
    private long lastPump=System.nanoTime(),accumulator,maxTickNanos,bytesQueued;
    private static final long STEP=1_000_000_000L/30;
    private static final class Seat {final int team;long sequence;boolean active;Seat(int team){this.team=team;}}
    private static final class Peer {
        final Socket socket;final Seat seat;final ArrayBlockingQueue<List<String>> output=new ArrayBlockingQueue<>(16);
        StringBuilder upload;
        int expectedLength,expectedParts,nextPart;
        String expectedHash;
        Peer(Socket socket,Seat seat){this.socket=socket;this.seat=seat;}
        void clearUpload(){upload=null;expectedLength=expectedParts=nextPart=0;expectedHash=null;}
        void send(List<String> batch){if(!output.offer(batch))try{socket.close();}catch(IOException ignored){}}
    }
    public BattleServer(String bind,int port,long seed,int count,Hbnet.Server transport)throws Exception{
        this(bind,port,seed,count,Objects.requireNonNull(transport),false);
    }
    /** Explicit credential-free trusted-network mode. Never an automatic TLS fallback. */
    public static BattleServer openLan(String bind,int port,long seed,int count)throws Exception{
        InetAddress local=InetAddress.getByName(Hbnet.host(bind));
        if(!local.isLoopbackAddress()&&!local.isSiteLocalAddress()&&!local.isLinkLocalAddress())throw Hbnet.failure();
        return new BattleServer(local.getHostAddress(),port,seed,count,null,true);
    }
    /** LAN challenge: host-selected portable ships are checked by the server,
     * never accepted solely because the remote client says they are legal. */
    public static BattleServer openLan(String bind,int port,long seed,int count,
                                       SkirmishShipExchange.Package left,
                                       SkirmishShipExchange.Package right)throws Exception{
        if(left==null||right==null||!left.rules().equals(right.rules()))
            throw new IllegalArgumentException("Both PvP ships need matching challenge rules");
        InetAddress local=InetAddress.getByName(Hbnet.host(bind));
        if(!local.isLoopbackAddress()&&!local.isSiteLocalAddress()&&!local.isLinkLocalAddress())
            throw Hbnet.failure();
        return new BattleServer(local.getHostAddress(),port,seed,count,null,true,left,right);
    }
    /** Host-selected inclusive tech tier is the sole authority here, NOT
     * whichever rules or tier were used when a player exported a design. */
    public static BattleServer openLan(String bind,int port,long seed,
                                       SkirmishFleet left,SkirmishFleet right,
                                       SkirmishTechnologyRules hostRules)throws Exception{
        if(left==null||right==null||hostRules==null)
            throw new IllegalArgumentException("PvP requires two fleets and a host match tier");
        InetAddress local=InetAddress.getByName(Hbnet.host(bind));
        if(!local.isLoopbackAddress()&&!local.isSiteLocalAddress()&&!local.isLinkLocalAddress())
            throw Hbnet.failure();
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        left.requireAllowed(hostRules,catalog);
        right.requireAllowed(hostRules,catalog);
        Battle match=Battle.duel(seed,left.ships(),right.ships(),catalog);
        return new BattleServer(local.getHostAddress(),port,match,hostRules);
    }
    /** A real two-client fleet-submission lobby. No ships or simulation are created until
     * both authenticated seats submit legal, checksummed fitted blueprints. */
    public static BattleServer openLanLobby(String bind,int port,long seed,
                                             SkirmishTechnologyRules rules)throws Exception{
        InetAddress local=Hbnet.lanAddress(bind);
        return new BattleServer(local.getHostAddress(),port,seed,rules,null,true);
    }
    /** TLS transport remains explicitly pinned/authenticated by hbnet; never downgrade. */
    public static BattleServer openLobby(String bind,int port,long seed,
                                         SkirmishTechnologyRules rules,Hbnet.Server tls)throws Exception{
        return new BattleServer(bind,port,seed,rules,Objects.requireNonNull(tls),false);
    }
    private BattleServer(String bind,int port,long seed,SkirmishTechnologyRules rules,
                         Hbnet.Server tls,boolean trustedLan)throws Exception{
        if(rules==null)throw new IllegalArgumentException("Host match policy required");
        this.transport=tls;this.hostRules=rules;this.matchSeed=seed;
        this.battle=null;
        this.matchRulesWire=Base64.getUrlEncoder().withoutPadding().encodeToString(
                SkirmishTechnologyRules.encode(rules).getBytes(StandardCharsets.UTF_8));
        if(matchRulesWire.length()>3000)throw new IllegalArgumentException("Host rules exceed welcome frame");
        listener=trustedLan?new ServerSocket(port,16,InetAddress.getByName(bind)):tls.listen(bind,port);
        workers.execute(this::accept);
        clock.scheduleWithFixedDelay(this::pump,5,5,TimeUnit.MILLISECONDS);
    }
    private BattleServer(String bind,int port,Battle authoritativeMatch,
                         SkirmishTechnologyRules hostRules)throws Exception{
        if(authoritativeMatch==null||hostRules==null)
            throw new IllegalArgumentException("Missing authoritative PvP battle or host rules");
        this.transport=null;
        this.battle=authoritativeMatch;
        this.matchSeed=authoritativeMatch.seed();this.hostRules=hostRules;
        this.matchRulesWire=Base64.getUrlEncoder().withoutPadding().encodeToString(
                SkirmishTechnologyRules.encode(hostRules).getBytes(StandardCharsets.UTF_8));
        if(matchRulesWire.length()>3000)
            throw new IllegalArgumentException("Host match policy exceeds the bounded welcome frame");
        listener=new ServerSocket(port,16,InetAddress.getByName(bind));
        workers.execute(this::accept);
        clock.scheduleWithFixedDelay(this::pump,5,5,TimeUnit.MILLISECONDS);
    }
    private BattleServer(String bind,int port,long seed,int count,Hbnet.Server transport,
                         boolean trustedLan)throws Exception{
        this(bind,port,seed,count,transport,trustedLan,null,null);
    }
    private BattleServer(String bind,int port,long seed,int count,Hbnet.Server transport,
                         boolean trustedLan,SkirmishShipExchange.Package left,
                         SkirmishShipExchange.Package right)throws Exception{
        if(count<1||count>200)throw new IllegalArgumentException("Fleet size must be 1-200");
        this.transport=transport;
        this.matchSeed=seed;this.hostRules=null;
        this.matchRulesWire=null; // Ordinary battle servers do not claim a PvP lobby policy.
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        if(left!=null||right!=null){
            if(left==null||right==null||!left.rules().equals(right.rules()))
                throw new IllegalArgumentException("PvP challenge rule mismatch");
            left.rules().requireAllowed(left.design(),catalog);
            left.rules().requireAllowed(right.design(),catalog);
        }
        Shipyard.Design first=left==null?
                Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE):left.design();
        Shipyard.Design second=right==null?
                Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE):right.design();
        battle=Battle.duel(seed,count,first,second,catalog);
        listener=trustedLan?new ServerSocket(port,16,InetAddress.getByName(bind)):transport.listen(bind,port);workers.execute(this::accept);
        // At most three catch-up steps per pump; never an unbounded fixed-rate task backlog.
        clock.scheduleWithFixedDelay(this::pump,5,5,TimeUnit.MILLISECONDS);
    }
    public int port(){return listener.getLocalPort();}
    public long tick(){synchronized(lock){return battle==null?0:battle.tick;}}
    public boolean lobbyWaiting(){synchronized(lock){return hostRules!=null&&battle==null;}}
    public long maxTickNanos(){synchronized(lock){return maxTickNanos;}}
    public long bytesQueued(){synchronized(lock){return bytesQueued;}}
    public BattleOrders.PointDefense pointDefense(int team){synchronized(lock){if(battle==null)throw new IllegalStateException("Fleet lobby not ready");return battle.teamOrders(team).pointDefense();}}
    private void accept(){while(!closed)try{Socket s=listener.accept();if(!slots.tryAcquire()){s.close();continue;}sockets.add(s);workers.execute(()->{try{serve(s);}finally{sockets.remove(s);slots.release();}});}catch(IOException e){if(!closed)System.err.println("Listener unavailable (NET-04)");}}
    private void serve(Socket socket){Peer peer=null;String signer=null;
        try(socket){
            signer=transport==null?UUID.randomUUID().toString():transport.admit(socket,"catastrophic-diplomacy");socket.setSoTimeout(30000);
            String request=Hbnet.read(socket.getInputStream());if(!request.equals("PLAYER|1")&&!request.equals("SPECTATE|1"))throw Hbnet.failure();
            synchronized(lock){
                Seat seat=seats.get(signer);
                if(seat==null){
                    int team=request.equals("SPECTATE|1")?-1:seats.values().stream().noneMatch(s->s.team==0)?0:seats.values().stream().noneMatch(s->s.team==1)?1:-1;
                    if(team<0&&!request.equals("SPECTATE|1"))throw Hbnet.failure();
                    if(seats.size()>=64)throw Hbnet.failure();seat=new Seat(team);seats.put(signer,seat);
                }
                if(seat.active)throw Hbnet.failure();seat.active=true;peer=new Peer(socket,seat);
                Hbnet.write(socket.getOutputStream(),"WELCOME|1|"+session+"|"+seat.team+"|"+matchSeed+"|"+seat.sequence+
                        (matchRulesWire==null?"":"|"+matchRulesWire));
                peers.add(peer);peer.send(snapshot());
            }
            final Peer current=peer;
            workers.execute(()->{try{while(!closed&&!socket.isClosed())for(String frame:current.output.take())Hbnet.write(socket.getOutputStream(),frame);}catch(IOException|InterruptedException e){try{socket.close();}catch(IOException ignored){}}});
            long window=System.nanoTime();int rate=0;
            while(!closed){String frame=Hbnet.read(socket.getInputStream());if(System.nanoTime()-window>1_000_000_000L){window=System.nanoTime();rate=0;}if(++rate>60)throw Hbnet.failure();
                synchronized(lock){handle(peer,frame);}
            }
        }catch(IOException|RuntimeException expected){/* neutral close: never echo peer data */}
        finally{if(peer!=null)synchronized(lock){
            peers.remove(peer);peer.seat.active=false;
            if(battle==null&&peer.seat.team>=0){submitted[peer.seat.team]=null;broadcastLobby();}
            if(transport==null)seats.remove(signer); // LAN has no persistent signer; reclaim its seat.
        }if(peer!=null)peer.output.offer(List.of());}
    }
    private void handle(Peer peer,String frame)throws IOException{
        String[] p=frame.split("\\|",-1);
        if(p.length==1&&p[0].equals("SNAPSHOT")){peer.send(snapshot());return;}
        if(p[0].startsWith("FLEET_")){receiveFleet(peer,p);return;}
        if(battle==null){peer.send(List.of("REJECT|NOT_READY"));return;}
        if(p.length!=6||!p[0].equals("ORDER")||!p[1].matches("[0-9]{1,15}")||!p[2].matches("[0-9]{1,15}")||!p[3].matches("[01]"))throw Hbnet.failure();
        long sequence=Long.parseLong(p[1]),at=Long.parseLong(p[2]);int team=Integer.parseInt(p[3]);
        if(sequence<=peer.seat.sequence){peer.send(List.of("REJECT|REPLAY"));return;}
        peer.seat.sequence=sequence;
        if(peer.seat.team!=team||at>battle.tick+3||at+90<battle.tick||orders.size()>=8192){peer.send(List.of("REJECT|ORDER"));return;}
        BattleOrders.PointDefense pd;BattleOrders.Position position;
        try{pd=BattleOrders.PointDefense.valueOf(p[4]);position=BattleOrders.Position.valueOf(p[5]);}catch(IllegalArgumentException e){throw Hbnet.failure();}
        // Existing core policy validates all actual combat/navigation and resource use.
        battle.setTeamOrders(team,battle.teamOrders(team).withPointDefense(pd).withPosition(position));
        orders.add(battle.tick+"|"+team+"|"+pd+"|"+position);
        peer.send(List.of("ACK|"+sequence+"|"+battle.tick));for(Peer p2:peers)p2.send(List.of("ORDERS|"+battle.tick+"|"+team+"|"+pd+"|"+position));
    }
    /** The seat, never a client-declared team, owns each upload. Fragments stay below
     * hbnet's 4096-byte frame cap and are strictly ordered and memory bounded. */
    private void receiveFleet(Peer peer,String[] p){
        if(hostRules==null||battle!=null||peer.seat.team<0){peer.send(List.of("FLEET_REJECT|UNAVAILABLE"));return;}
        int team=peer.seat.team;
        try{
            switch(p[0]){
                case "FLEET_BEGIN" -> {
                    if(p.length!=4||!p[1].matches("[0-9]{1,6}")||
                            !p[2].matches("[0-9]{1,3}")||!p[3].matches("[a-f0-9]{64}"))
                        throw new IllegalArgumentException("Bad fleet header");
                    int length=Integer.parseInt(p[1]),parts=Integer.parseInt(p[2]);
                    if(length<1||length>MAX_FLEET_WIRE||parts!=(length+FLEET_CHUNK-1)/FLEET_CHUNK)
                        throw new IllegalArgumentException("Fleet wire limit");
                    peer.clearUpload();peer.upload=new StringBuilder(length);
                    peer.expectedLength=length;peer.expectedParts=parts;peer.expectedHash=p[3];
                    submitted[team]=null;broadcastLobby();
                }
                case "FLEET_PART" -> {
                    if(p.length!=3||peer.upload==null||!p[1].equals(Integer.toString(peer.nextPart))||
                            !p[2].matches("[A-Za-z0-9_-]{1,2800}")||
                            peer.nextPart>=peer.expectedParts||
                            peer.upload.length()+p[2].length()>peer.expectedLength)
                        throw new IllegalArgumentException("Invalid fleet fragment");
                    peer.upload.append(p[2]);peer.nextPart++;
                }
                case "FLEET_END" -> {
                    if(p.length!=1||peer.upload==null||peer.nextPart!=peer.expectedParts||
                            peer.upload.length()!=peer.expectedLength)
                        throw new IllegalArgumentException("Incomplete fleet");
                    String wire=peer.upload.toString();
                    if(!Hbnet.hash(wire.getBytes(StandardCharsets.US_ASCII)).equals(peer.expectedHash))
                        throw new IllegalArgumentException("Fleet digest mismatch");
                    byte[] raw=Base64.getUrlDecoder().decode(wire);
                    if(raw.length>120_000)throw new IllegalArgumentException("Fleet too large");
                    TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
                    SkirmishFleet fleet=SkirmishFleetCodec.decode(new String(raw,StandardCharsets.UTF_8),catalog);
                    fleet.requireAllowed(hostRules,catalog); // actual fittings, not claimed tier
                    submitted[team]=fleet;peer.clearUpload();
                    peer.send(List.of("FLEET_ACCEPT|"+team+"|"+fleet.size()));
                    broadcastLobby();
                    if(submitted[0]!=null&&submitted[1]!=null){
                        battle=Battle.duel(matchSeed,submitted[0].ships(),submitted[1].ships(),catalog);
                        List<String> initial=snapshot();
                        for(Peer connected:peers){connected.send(List.of("BATTLE_START|"+matchSeed));connected.send(initial);}
                    }
                }
                default -> throw new IllegalArgumentException("Unknown fleet command");
            }
        }catch(IllegalArgumentException invalid){
            peer.clearUpload();submitted[team]=null;
            peer.send(List.of("FLEET_REJECT|INVALID"));broadcastLobby();
        }
    }
    private void broadcastLobby(){
        String state="LOBBY|"+(submitted[0]==null?0:1)+"|"+(submitted[1]==null?0:1);
        for(Peer connected:peers)connected.send(List.of(state));
    }
    private void pump(){
        try{synchronized(lock){long now=System.nanoTime();if(battle==null){lastPump=now;accumulator=0;return;}accumulator=Math.min(accumulator+Math.max(0,now-lastPump),STEP*3);lastPump=now;int steps=0;
            while(accumulator>=STEP&&steps++<3){long start=System.nanoTime();battle.step();maxTickNanos=Math.max(maxTickNanos,System.nanoTime()-start);accumulator-=STEP;
                if(battle.tick%3==0&&!peers.isEmpty()){List<String> snapshot=snapshot();long size=snapshot.stream().mapToLong(s->s.length()+4L).sum();bytesQueued+=size*peers.size();for(Peer p:peers)p.send(snapshot);}
            }
        }}catch(RuntimeException e){System.err.println("Authoritative battle halted (NET-STATE)");close();}
    }
    private List<String> snapshot(){
        if(battle==null)return List.of("LOBBY|"+(submitted[0]==null?0:1)+"|"+(submitted[1]==null?0:1));
        List<String> body=new ArrayList<>();
        for(Battle.Ship s:battle.ships())body.add("SHIP|"+s.id+"|"+s.team+"|"+s.faction+"|"+s.x+"|"+s.y+"|"+s.facing+"|"+s.hull+"|"+s.shields+"|"+s.armor+"|"+s.power+"|"+s.fuel);
        for(Battle.Missile m:battle.missiles())body.add("MISSILE|"+m.visualId+"|"+m.team+"|"+m.x+"|"+m.y+"|"+m.integrity);
        for(Battle.Ballistic m:battle.ballistics())body.add("BALLISTIC|"+m.visualId+"|"+m.team+"|"+m.x+"|"+m.y+"|"+m.integrity);
        for(Battle.Craft m:battle.crafts())body.add("CRAFT|"+m.id+"|"+m.team+"|"+m.x+"|"+m.y+"|"+m.integrity);
        if(body.size()>8192)throw new IllegalStateException("Snapshot capacity exceeded");
        String viewHash=Hbnet.hash(String.join("\n",body).getBytes(StandardCharsets.US_ASCII));
        List<String> result=new ArrayList<>();result.add("SNAP|"+battle.tick+"|"+battle.fingerprint()+"|"+body.size());result.addAll(body);result.add("END|"+battle.tick+"|"+viewHash);return List.copyOf(result);
    }
    /** Versioned checksummed replay export; never modifies a campaign save or trusts a client outcome. */
    public void saveReplay(Path file)throws IOException{synchronized(lock){if(battle==null)throw new IOException("No battle before both accepted fleets");Properties p=new Properties();p.setProperty("game","catastrophic-diplomacy");p.setProperty("seed",Long.toString(battle.seed()));p.setProperty("tick",Long.toString(battle.tick));p.setProperty("fingerprint",Long.toString(battle.fingerprint()));for(int i=0;i<orders.size();i++)p.setProperty("order."+i,orders.get(i));new Hbnet.Store(file).save(p);}}
    public void close(){if(closed)return;closed=true;try{listener.close();}catch(IOException ignored){}for(Socket s:sockets)try{s.close();}catch(IOException ignored){}clock.shutdownNow();workers.shutdownNow();}
    public static void main(String[] args)throws Exception{
        if(args.length<1||args.length>4)throw new IllegalArgumentException("Use BattleServer network-data [port] [ships-per-side] [explicit-bind]");
        Path root=Path.of(args[0]);try(BattleServer server=new BattleServer(args.length>3?args[3]:"127.0.0.1",args.length>1?Hbnet.port(args[1]):27817,2479,args.length>2?Integer.parseInt(args[2]):16,Hbnet.Server.load(root))){
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));System.out.println("Private battle ready. Local console: status, save, stop. Endpoints hidden.");BufferedReader in=new BufferedReader(new InputStreamReader(System.in));String line;
            while((line=in.readLine())!=null){if(line.equals("stop"))break;if(line.equals("status"))System.out.println("Authoritative tick "+server.tick());if(line.equals("save")){server.saveReplay(root.resolve("battle.replay"));System.out.println("Replay saved");}}
            server.saveReplay(root.resolve("battle.replay"));
        }
    }
}
