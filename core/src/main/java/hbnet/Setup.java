package hbnet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local operator bootstrap. Key/password values never enter process arguments or output. */
public final class Setup {
    private Setup(){}
    public static Hbnet.Server initialize(Path root,char[] manual)throws Exception{
        if(Files.exists(root.resolve("server.p12"))||Files.exists(root.resolve("room.verifier")))throw new IOException("Identity already exists; preserve it on update");
        Files.createDirectories(root);Hbnet.protect(root);Path pass=root.resolve("keystore.pass");
        Files.createFile(pass);Hbnet.protect(pass);Files.writeString(pass,Hbnet.roomKey(),StandardCharsets.US_ASCII);
        String exe=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"keytool.exe":"keytool";
        Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",exe).toString(),"-genkeypair","-alias","hbnet","-keyalg","EC","-groupname","secp256r1","-validity","3650","-dname","CN=Private Game Server","-storetype","PKCS12","-keystore",root.resolve("server.p12").toAbsolutePath().toString(),"-storepass:file",pass.toAbsolutePath().toString()).redirectErrorStream(true).start();
        // Consume bounded operator tool output without logging secret paths or certificate details.
        Thread drain=new Thread(()->{try{p.getInputStream().transferTo(OutputStream.nullOutputStream());}catch(IOException ignored){}});drain.setDaemon(true);drain.start();
        if(!p.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw Hbnet.failure();}if(p.exitValue()!=0)throw Hbnet.failure();Hbnet.protect(root.resolve("server.p12"));
        char[] secret=manual==null?Hbnet.roomKey().toCharArray():manual.clone();
        try{
            Hbnet.Password.create(secret).save(root.resolve("room.verifier"));
            // Explicit protected secret file is the out-of-band key delivery mechanism.
            if(manual==null){Path file=root.resolve("room.secret");Files.createFile(file);Hbnet.protect(file);Files.writeString(file,new String(secret),StandardCharsets.US_ASCII);}
        }finally{Arrays.fill(secret,'\0');}
        Hbnet.Server server=Hbnet.Server.load(root);
        Files.writeString(root.resolve("server.fingerprint"),server.fingerprint,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);Hbnet.protect(root.resolve("server.fingerprint"));return server;
    }
    public static void main(String[] args)throws Exception{
        if(args.length<1||args.length>2)throw new IllegalArgumentException("Use hbnet.Setup data-directory [--manual]");
        char[] manual=null;try{
            if(args.length==2){if(!args[1].equals("--manual")||System.console()==null)throw Hbnet.failure();manual=System.console().readPassword("Room password (12-128 printable ASCII): ");Hbnet.password(manual);}
            initialize(Path.of(args[0]),manual);System.out.println("Private server identity created. Read fingerprint and generated room secret privately from protected operator files.");
        }finally{if(manual!=null)Arrays.fill(manual,'\0');}
    }
}
