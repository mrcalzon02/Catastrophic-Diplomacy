package diplomacy.server;

import diplomacy.shipyard.SkirmishShipExchange;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Trusted-LAN battle host. Optional host-reviewed exported ships and their
 * identical challenge policy determine the actual server-side fleets.
 * No automatic unencrypted fallback from authenticated online play. */
public final class OpenLanBattleServer {
    private OpenLanBattleServer(){}
    public static void main(String[] args)throws Exception{
        if(args.length>5||args.length==4)
            throw new IllegalArgumentException(
                    "Use OpenLanBattleServer [private-bind] [port] [ships-per-side] [left.cdship right.cdship]");
        String bind=args.length>0?args[0]:"127.0.0.1";
        int port=args.length>1?hbnet.Hbnet.port(args[1]):27817;
        int count=args.length>2?Integer.parseInt(args[2]):16;
        BattleServer server;
        if(args.length==5){
            TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
            SkirmishShipExchange.Package left=SkirmishShipExchange.decode(
                    Files.readString(Path.of(args[3]),StandardCharsets.UTF_8),catalog);
            SkirmishShipExchange.Package right=SkirmishShipExchange.decode(
                    Files.readString(Path.of(args[4]),StandardCharsets.UTF_8),catalog);
            if(!left.rules().equals(right.rules()))
                throw new IllegalArgumentException("PvP ship exports do not agree on challenge rules");
            server=BattleServer.openLan(bind,port,2479L,count,left,right);
            System.out.println("PvP ships loaded: "+left.design().hull+
                    " versus "+right.design().hull+
                    "; shared technology ceiling "+left.rules().maxStage()+
                    "/6, hull generation "+left.rules().maxHullGeneration()+"/10.");
        }else server=BattleServer.openLan(bind,port,2479L,count);
        try(server){
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            System.out.println("OPEN_LAN: UNENCRYPTED trusted private network only; any Wi-Fi clients must be on the trusted LAN.");
            System.out.println("Type status to inspect the authoritative battle; stop to exit.");
            var input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
            String line;
            while((line=input.readLine())!=null&&!line.equals("stop")){
                if(line.equals("status"))System.out.println("Authoritative tick "+server.tick());
            }
        }
    }
}
