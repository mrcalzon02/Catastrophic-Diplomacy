package diplomacy.server;

/** Explicit credential-free trusted-LAN operator entrypoint. No automatic secure downgrade. */
public final class OpenLanBattleServer {
    private OpenLanBattleServer(){}
    public static void main(String[] args)throws Exception{
        if(args.length>3)throw new IllegalArgumentException("Use OpenLanBattleServer [explicit-private-bind] [port] [ships-per-side]");
        try(BattleServer server=BattleServer.openLan(args.length>0?args[0]:"127.0.0.1",args.length>1?hbnet.Hbnet.port(args[1]):27817,2479,args.length>2?Integer.parseInt(args[2]):16)){
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));System.out.println("OPEN_LAN: unencrypted, no account authentication. Trusted networks only. Enter stop to exit.");
            var input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in));String line;while((line=input.readLine())!=null&&!line.equals("stop")){if(line.equals("status"))System.out.println("Authoritative tick "+server.tick());}
        }
    }
}
