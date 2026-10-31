package diplomacy.server;

import diplomacy.shipyard.SkirmishShipExchange;
import diplomacy.shipyard.SkirmishFleet;
import diplomacy.shipyard.SkirmishFleetCodec;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Explicit trusted-Wi-Fi/LAN operator entry. One shared tech catalog; host
 * dictates match rank inclusive. A fleet exported under Tech II can join a
 * Tech VI lobby without changing its own ship equipment. */
public final class OpenLanBattleServer {
    private OpenLanBattleServer(){}
    public static void main(String[] args)throws Exception{
        if(args.length>5||(args.length==4&&!"lobby".equals(args[2])))
            throw new IllegalArgumentException(
                "Use [private-bind] [port] [fleet-size], or [private-bind] [port] [match-tech-0..9] [left.cdfleet] [right.cdfleet]");
        String bind=args.length>0?args[0]:"127.0.0.1";
        int port=args.length>1?hbnet.Hbnet.port(args[1]):27817;
        BattleServer server;
        if(args.length==4&&"lobby".equals(args[2])){
            int ceiling=Integer.parseInt(args[3]);
            server=BattleServer.openLanLobby(bind,port,2479L,
                    SkirmishTechnologyRules.unrestricted().withStage(ceiling));
            System.out.println("HOST LOBBY TECH "+ceiling+"/9; awaiting two fitted .cdfleet submissions.");
        }else if(args.length==5&&args[3].endsWith(".cdfleet")&&args[4].endsWith(".cdfleet")){
            int ceiling=Integer.parseInt(args[2]);
            SkirmishTechnologyRules match=SkirmishTechnologyRules.unrestricted().withStage(ceiling);
            TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
            SkirmishFleet left=SkirmishFleetCodec.decode(
                    Files.readString(Path.of(args[3]),StandardCharsets.UTF_8),catalog);
            SkirmishFleet right=SkirmishFleetCodec.decode(
                    Files.readString(Path.of(args[4]),StandardCharsets.UTF_8),catalog);
            // Never adopt a technology cap supplied by either player's export.
            server=BattleServer.openLan(bind,port,2479L,left,right,match);
            System.out.println("HOST TECH "+ceiling+"/9 (INCLUSIVE). Left "+
                    left.size()+" ships, right "+right.size()+" ships.");
        }else if(args.length==5){
            // Legacy single-design challenge exchange compatibility.
            int count=Integer.parseInt(args[2]);
            TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
            var left=SkirmishShipExchange.decode(
                    Files.readString(Path.of(args[3]),StandardCharsets.UTF_8),catalog);
            var right=SkirmishShipExchange.decode(
                    Files.readString(Path.of(args[4]),StandardCharsets.UTF_8),catalog);
            if(!left.rules().equals(right.rules()))
                throw new IllegalArgumentException("Legacy exports disagree on challenge rules");
            server=BattleServer.openLan(bind,port,2479L,count,left,right);
            System.out.println("Legacy fixed-design LAN challenge loaded.");
        }else{
            int count=args.length>2?Integer.parseInt(args[2]):16;
            server=BattleServer.openLan(bind,port,2479L,count);
        }
        try(server){
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            System.out.println("OPEN_LAN: UNENCRYPTED trusted private network only; Wi-Fi clients must be on trusted LAN.");
            System.out.println("Type status to inspect the authoritative battle; stop to exit.");
            var input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            String line;
            while((line=input.readLine())!=null&&!line.equals("stop")){
                if(line.equals("status"))System.out.println(server.lobbyWaiting()?"Waiting for two legal fleet submissions":"Authoritative tick "+server.tick());
            }
        }
    }
}
