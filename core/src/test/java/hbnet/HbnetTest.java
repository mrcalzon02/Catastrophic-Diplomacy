package hbnet;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Real TLS sockets, private identities, storage recovery and masked UI regression. */
public final class HbnetTest {
    private static int passed;
    private static void check(boolean b){if(!b)throw new AssertionError("hbnet acceptance case "+passed);passed++;}
    private static void denied(Callable<?> action)throws Exception{try{action.call();throw new AssertionError("Accepted invalid input");}catch(IOException|IllegalArgumentException|GeneralSecurityException expected){passed++;}}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("hbnet-test-");ExecutorService worker=Executors.newSingleThreadExecutor();
        try{
            for(String bad:List.of("https://private.test","user@host","host\n","host\u200b","h\u043est","127.1","999.0.0.1","127.0.0.01","host;cmd","a b"))denied(()->Hbnet.host(bad));
            check(Hbnet.host("[::1]").equals("::1"));check(Hbnet.host("xn--example.test").equals("xn--example.test"));
            denied(()->Hbnet.port(" 123"));denied(()->Hbnet.port("65536"));denied(()->{Hbnet.password("twelve\nchars!".toCharArray());return null;});
            char[] exact="  manual key | spaces  ".toCharArray();Hbnet.Password pw=Hbnet.Password.create(exact);check(pw.matches(exact));check(!pw.matches(new String(exact).trim().toCharArray()));
            Hbnet.Identity stored=Hbnet.Identity.load(dir.resolve("a.identity"));check(Hbnet.Identity.load(dir.resolve("a.identity")).id().equals(stored.id()));
            Setup.initialize(dir.resolve("player-tls"),null);Hbnet.Identity a=Hbnet.Identity.loadTransport(dir.resolve("player-tls")),b=Hbnet.Identity.create();
            byte[] nonce=Hbnet.random(32);String sig=a.sign(nonce);check(Hbnet.verify(a.publicKey(),nonce,sig));check(!Hbnet.verify(b.publicKey(),nonce,sig));check(!Hbnet.verify(a.publicKey(),Hbnet.random(32),sig));
            check(Hbnet.cardKey(a.card()).equals(a.publicKey()));denied(()->Hbnet.cardKey(a.card()+"x"));
            LocalProfile profile=new LocalProfile(dir.resolve("profile"));check(profile.streamSafe());String contact=profile.importCard(a.card(),"private-friend-sentinel");check(profile.importCard(a.card(),"other").equals(contact));
            profile.state(contact,LocalProfile.ContactState.ACCEPTED);check(profile.reviewedAllowlist().isEmpty());profile.observedAuthenticated(contact);check(profile.reviewedAllowlist().contains(contact));profile.state(contact,LocalProfile.ContactState.BLOCKED);check(profile.reviewedAllowlist().isEmpty());profile.state(contact,LocalProfile.ContactState.PENDING);profile.state(contact,LocalProfile.ContactState.DECLINED);
            Hbnet.Server server=Setup.initialize(dir.resolve("host"),null);char[] secret=Files.readString(dir.resolve("host/room.secret")).toCharArray();
            String id=profile.server(null,"private-server-sentinel","localhost",12345,server.fingerprint);check(!profile.servers().get(0).toString().contains("localhost"));check(!profile.serverLabel(profile.servers().get(0),0).contains("sentinel"));
            denied(()->profile.server(id,"changed","localhost",12345,"0".repeat(64)));
            profile.streamSafe(false);check(!new LocalProfile(dir.resolve("profile")).streamSafe());profile.streamSafe(true);
            try{
                Class<?> ui=Class.forName("hbnet.NetworkPanel");
                javax.swing.SwingUtilities.invokeAndWait(()->{try{
                    java.util.function.Consumer<LocalProfile.SavedServer> callback=s->{};
                    javax.swing.JPanel panel=(javax.swing.JPanel)ui.getConstructor(LocalProfile.class,java.util.function.Consumer.class).newInstance(profile,callback);
                    panel.setSize(800,500);panel.doLayout();panel.paint(new java.awt.image.BufferedImage(800,500,java.awt.image.BufferedImage.TYPE_INT_RGB).getGraphics());
                    javax.swing.JPasswordField field=(javax.swing.JPasswordField)ui.getMethod("masked",String.class,int.class).invoke(null,"private-endpoint-sentinel",32);check(field.getEchoChar()!=0);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            }catch(ClassNotFoundException platformUi){System.out.println("SKIP Swing controls: platform uses native game UI");}
            String invite=server.invitation("test-game","localhost",12345,60);check(Invitation.parse(invite,"test-game").fingerprint().equals(server.fingerprint));denied(()->Invitation.parse(invite+"x","test-game"));denied(()->Invitation.parse(invite,"other-game"));
            String backup=profile.exportContacts();LocalProfile imported=new LocalProfile(dir.resolve("imported"));imported.importContacts(backup);check(imported.friends().size()==1&&!imported.friends().get(0).verified());
            Hbnet.Store localStore=new Hbnet.Store(dir.resolve("local-recovery"));Properties localProps=new Properties();localProps.setProperty("value","first");localStore.save(localProps);localProps.setProperty("value","second");localStore.save(localProps);Files.writeString(dir.resolve("local-recovery"),"corrupt");check(localStore.load().getProperty("value").equals("first"));Files.writeString(dir.resolve("local-recovery.bak"),"corrupt");denied(localStore::load);
            if(args.length==1&&args[0].equals("local")){System.out.println("PASS local-only hbnet cases="+passed+"; TLS socket acceptance explicitly NOT RUN");return;}
            String bind=System.getProperty("hbnet.test.bind","127.0.0.1"),address=System.getProperty("hbnet.test.host","localhost");
            try(var listener=server.listen(bind,0)){
                Future<?> serving=worker.submit(()->{for(int i=0;i<5;i++)try(Socket s=listener.accept()){
                    String signer=server.admit(s,"test-game");Hbnet.write(s.getOutputStream(),signer);check(Hbnet.read(s.getInputStream()).equals("ACTION|1"));
                }catch(IOException expected){}});
                for(int i=0;i<2;i++)try(Socket s=new Hbnet.Client(server.fingerprint,"test-game",a,secret).connect(address,listener.getLocalPort())){check(Hbnet.read(s.getInputStream()).equals(a.id()));Hbnet.write(s.getOutputStream(),"ACTION|1");check(((javax.net.ssl.SSLSocket)s).getSession().getProtocol().equals("TLSv1.3"));}
                denied(()->new Hbnet.Client("0".repeat(64),"test-game",a,secret).connect(address,listener.getLocalPort()));
                denied(()->new Hbnet.Client(server.fingerprint,"wrong-game",a,secret).connect(address,listener.getLocalPort()));
                denied(()->new Hbnet.Client(server.fingerprint,"test-game",a,"wrong password key".toCharArray()).connect(address,listener.getLocalPort()));serving.get(15,TimeUnit.SECONDS);
            }
            ByteArrayOutputStream malformed=new ByteArrayOutputStream();new DataOutputStream(malformed).writeInt(Integer.MAX_VALUE);denied(()->Hbnet.read(new ByteArrayInputStream(malformed.toByteArray())));
            Hbnet.Store store=new Hbnet.Store(dir.resolve("recovery"));Properties props=new Properties();props.setProperty("safe","first");store.save(props);props.setProperty("safe","second");store.save(props);Files.writeString(dir.resolve("recovery"),"corrupt");check(store.load().getProperty("safe").equals("first"));Files.writeString(dir.resolve("recovery.bak"),"corrupt");denied(store::load);
            Arrays.fill(secret,'\0');System.out.println("PASS hbnet cases="+passed+" TLS1.3 pin/password/game rejection, signed identities, local contacts, atomic recovery, masked input");
        }finally{worker.shutdownNow();try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
