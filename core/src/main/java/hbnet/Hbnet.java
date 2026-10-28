package hbnet;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.*;

/** hbnet 1.0: Java 17, no central service, no game or rendering dependencies.
 * TLS identity is an explicitly verified certificate pin, never trust-on-first-use.
 * Existing game protocols retain their own authoritative validation after admission.
 */
public final class Hbnet {
    private Hbnet(){}
    public static final int VERSION=1, MAX_FRAME=4096, DEADLINE_MS=5000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Base64.Encoder B64=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder UN64=Base64.getUrlDecoder();
    public static IOException failure(){return new IOException("Connection unavailable (NET-04)");}
    public static String hash(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    public static byte[] random(int size){byte[] b=new byte[size];RANDOM.nextBytes(b);return b;}
    public static String roomKey(){return HexFormat.of().formatHex(random(20));}
    public static void password(char[] value){
        if(value==null||value.length<12||value.length>128)throw new IllegalArgumentException("Password must contain 12 to 128 printable ASCII characters");
        for(char c:value)if(c<32||c>126)throw new IllegalArgumentException("Password must contain printable ASCII only");
    }
    public static String host(String value){
        if(value==null||value.isEmpty()||value.length()>253||value.chars().anyMatch(c->c<33||c>126))throw new IllegalArgumentException("Invalid address (NET-01)");
        String v=value;
        if(v.startsWith("[")&&v.endsWith("]"))v=v.substring(1,v.length()-1);
        if(v.contains(":")){
            if(!v.matches("[0-9A-Fa-f:.]+"))throw new IllegalArgumentException("Invalid IPv6 address (NET-01)");
            try{if(!(InetAddress.getByName(v) instanceof Inet6Address))throw new IllegalArgumentException("Invalid IPv6 address (NET-01)");}
            catch(UnknownHostException e){throw new IllegalArgumentException("Invalid IPv6 address (NET-01)");}
        }else{
            if(!v.matches("[A-Za-z0-9.-]+"))throw new IllegalArgumentException("Invalid hostname (NET-01)");
            for(String label:v.split("\\.",-1))if(label.isEmpty()||label.length()>63||label.startsWith("-")||label.endsWith("-"))throw new IllegalArgumentException("Invalid hostname (NET-01)");
            if(v.matches("[0-9.]+")){
                String[] labels=v.split("\\.");if(labels.length!=4)throw new IllegalArgumentException("Invalid IPv4 address (NET-01)");
                for(String label:labels)if(label.length()>3||Integer.parseInt(label)>255||(label.length()>1&&label.startsWith("0")))throw new IllegalArgumentException("Invalid IPv4 address (NET-01)");
            }
        }
        return v;
    }
    public static int port(String value){
        if(value==null||!value.matches("[0-9]{1,5}"))throw new IllegalArgumentException("Invalid port (NET-02)");
        int n=Integer.parseInt(value);if(n<1||n>65535)throw new IllegalArgumentException("Invalid port (NET-02)");return n;
    }
    public static boolean trustedLan(InetAddress address){return address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress();}
    public static InetAddress lanAddress(String address)throws IOException{InetAddress target=InetAddress.getByName(host(address));if(!trustedLan(target))throw new IOException("Verified TLS required (NET-05)");return target;}
    public static String pin(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Verify the server fingerprint privately (NET-03)");return value;}
    public static void write(OutputStream out,String value)throws IOException{
        byte[] bytes=value.getBytes(StandardCharsets.US_ASCII);
        if(bytes.length>MAX_FRAME||value.chars().anyMatch(c->c<32||c>126))throw failure();
        DataOutputStream d=new DataOutputStream(out);d.writeInt(bytes.length);d.write(bytes);d.flush();
    }
    public static String read(InputStream in)throws IOException{
        DataInputStream d=new DataInputStream(in);int n=d.readInt();if(n<1||n>MAX_FRAME)throw failure();
        byte[] bytes=new byte[n];d.readFully(bytes);for(byte b:bytes)if(b<32||b>126)throw failure();return new String(bytes,StandardCharsets.US_ASCII);
    }
    /** Checksummed, bounded, atomic local data; corrupt primary uses validated backup.
     * Never turns a corrupt identity/friends file into an empty fresh profile.
     */
    public static final class Store {
        private final Path file;
        public Store(Path file){this.file=file;}
        private Properties decode(Path path)throws IOException{
            if(Files.size(path)>1048576)throw failure();
            byte[] all=Files.readAllBytes(path);int split=-1;for(int i=0;i<all.length;i++)if(all[i]=='\n'){split=i;break;}
            if(split!=64)throw failure();byte[] body=Arrays.copyOfRange(all,65,all.length);
            if(!hash(body).equals(new String(all,0,64,StandardCharsets.US_ASCII)))throw failure();
            Properties p=new Properties();p.load(new ByteArrayInputStream(body));if(!"1".equals(p.getProperty("schema")))throw failure();return p;
        }
        public synchronized Properties load()throws IOException{
            if(!Files.exists(file)&&!Files.exists(backup())){Properties p=new Properties();p.setProperty("schema","1");return p;}
            try{return decode(file);}catch(IOException|IllegalArgumentException e){try{return decode(backup());}catch(IOException|IllegalArgumentException bad){throw new IOException("Local profile needs recovery (NET-20)");}}
        }
        private Path backup(){return file.resolveSibling(file.getFileName()+".bak");}
        public synchronized void save(Properties p)throws IOException{
            p.setProperty("schema","1");ByteArrayOutputStream data=new ByteArrayOutputStream();p.store(data,"hbnet v1 private local profile");byte[] body=data.toByteArray();
            if(body.length>1048510)throw failure();Files.createDirectories(file.toAbsolutePath().getParent());
            Path staged=Files.createTempFile(file.toAbsolutePath().getParent(),".hbnet-",".tmp");
            try{
                protect(staged);try(OutputStream out=Files.newOutputStream(staged)){out.write((hash(body)+"\n").getBytes(StandardCharsets.US_ASCII));out.write(body);}
                if(Files.exists(file)){try{decode(file);Files.copy(file,backup(),StandardCopyOption.REPLACE_EXISTING);protect(backup());}catch(IOException corrupt){/* preserve last good backup */}}
                try{Files.move(staged,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(staged,file,StandardCopyOption.REPLACE_EXISTING);}
                protect(file);
            }finally{Files.deleteIfExists(staged);}
        }
    }
    public static void protect(Path file)throws IOException{
        if(Files.getFileStore(file).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(file,PosixFilePermissions.fromString(Files.isDirectory(file)?"rwx------":"rw-------"));
        var acl=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class);
        if(acl!=null){var owner=Files.getOwner(file);acl.setAcl(List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build()));}
        // Keys remain exportable by their OS owner; never claim hardware-backed storage.
    }
    public static final class Identity {
        private final KeyPair keys;
        private final KeyManager[] transportKeys;
        private Identity(KeyPair keys){this(keys,null);}
        private Identity(KeyPair keys,KeyManager[] transportKeys){this.keys=keys;this.transportKeys=transportKeys;}
        /** Standard KeyStore bridge; Android may supply its platform KeyStore/key manager.
         * The same key signs the application challenge and (when present) the TLS proof.
         */
        public static Identity fromKeyStore(KeyStore store,String alias,char[] password)throws GeneralSecurityException{
            PrivateKey key=(PrivateKey)store.getKey(alias,password);KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,password);
            return new Identity(new KeyPair(store.getCertificate(alias).getPublicKey(),key),km.getKeyManagers());
        }
        public static Identity loadTransport(Path root)throws IOException,GeneralSecurityException{
            char[] pass=Files.readString(root.resolve("keystore.pass"),StandardCharsets.US_ASCII).toCharArray();
            try{KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(root.resolve("server.p12"))){store.load(in,pass);}return fromKeyStore(store,"hbnet",pass);}finally{Arrays.fill(pass,'\0');}
        }
        public static Identity create()throws GeneralSecurityException{
            KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));return new Identity(g.generateKeyPair());
        }
        public static Identity load(Path file)throws IOException,GeneralSecurityException{
            Store s=new Store(file);Properties p=s.load();
            if(p.getProperty("private")==null){
                if(p.size()>1)throw failure();Identity id=create();p.setProperty("private",B64.encodeToString(id.keys.getPrivate().getEncoded()));p.setProperty("public",id.publicKey());s.save(p);return id;
            }
            KeyFactory f=KeyFactory.getInstance("EC");KeyPair pair=new KeyPair(Hbnet.publicKey(p.getProperty("public")),f.generatePrivate(new PKCS8EncodedKeySpec(UN64.decode(p.getProperty("private")))));
            Identity id=new Identity(pair);byte[] test=random(32);if(!verify(id.publicKey(),test,id.sign(test)))throw failure();return id;
        }
        public String publicKey(){return B64.encodeToString(keys.getPublic().getEncoded());}
        public String id(){return hash(keys.getPublic().getEncoded());}
        public String sign(byte[] challenge)throws GeneralSecurityException{Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(keys.getPrivate());s.update(challenge);return B64.encodeToString(s.sign());}
        public String card(){String body="HBFRIEND1."+publicKey();return body+"."+hash(body.getBytes(StandardCharsets.US_ASCII));}
        @Override public String toString(){return "Private Player Identity";}
    }
    private static PublicKey publicKey(String encoded)throws GeneralSecurityException{
        if(encoded==null||encoded.length()>256)throw new InvalidKeyException();
        PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(UN64.decode(encoded)));
        if(!(key instanceof java.security.interfaces.ECPublicKey ec)||ec.getParams().getCurve().getField().getFieldSize()!=256)throw new InvalidKeyException();return key;
    }
    public static boolean verify(String key,byte[] challenge,String signature){
        try{if(signature.length()>256)return false;Signature s=Signature.getInstance("SHA256withECDSA");s.initVerify(publicKey(key));s.update(challenge);return s.verify(UN64.decode(signature));}catch(GeneralSecurityException|IllegalArgumentException e){return false;}
    }
    public static String cardKey(String card)throws GeneralSecurityException{
        if(card==null||card.length()>600)throw new InvalidKeyException();String[] p=card.split("\\.");
        if(p.length!=3||!p[0].equals("HBFRIEND1")||!hash((p[0]+"."+p[1]).getBytes(StandardCharsets.US_ASCII)).equals(p[2]))throw new InvalidKeyException("Invalid friend card (NET-21)");publicKey(p[1]);return p[1];
    }
    public static final class Password {
        private final byte[] salt,derived;
        private Password(byte[] salt,byte[] derived){this.salt=salt;this.derived=derived;}
        public static Password create(char[] password)throws GeneralSecurityException{Hbnet.password(password);byte[] salt=random(16);return new Password(salt,derive(password,salt));}
        private static byte[] derive(char[] input,byte[] salt)throws GeneralSecurityException{
            PBEKeySpec p=new PBEKeySpec(input,salt,210000,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(p).getEncoded();}finally{p.clearPassword();}
        }
        public boolean matches(char[] input)throws GeneralSecurityException{byte[] d=derive(input,salt);try{return MessageDigest.isEqual(derived,d);}finally{Arrays.fill(d,(byte)0);}}
        public void save(Path file)throws IOException{Properties p=new Properties();p.setProperty("salt",B64.encodeToString(salt));p.setProperty("verifier",B64.encodeToString(derived));p.setProperty("kdf","PBKDF2-SHA256-210000");new Store(file).save(p);}
        public static Password load(Path file)throws IOException{
            try{Properties p=new Store(file).load();if(!"PBKDF2-SHA256-210000".equals(p.getProperty("kdf")))throw failure();byte[] s=UN64.decode(p.getProperty("salt")),d=UN64.decode(p.getProperty("verifier"));if(s.length!=16||d.length!=32)throw failure();return new Password(s,d);}catch(IllegalArgumentException|NullPointerException e){throw failure();}
        }
    }
    /** Persistent operator-provided PKCS12. Password is read from a protected file, never argv. */
    public static final class Server {
        private final SSLContext context;
        private final Password password;
        private final Set<String> allowlist;
        private final PrivateKey signingKey;
        private final X509Certificate certificate;
        private final Semaphore admissions=new Semaphore(8);
        private final LinkedHashMap<String,long[]> attempts=new LinkedHashMap<>();
        public final String fingerprint;
        public Server(Path keyStore,char[] storePassword,Password password,Set<String> allowlist)throws GeneralSecurityException,IOException{
            KeyStore ks=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(keyStore)){ks.load(in,storePassword);}
            String alias=ks.aliases().nextElement();X509Certificate cert=(X509Certificate)ks.getCertificate(alias);cert.checkValidity();fingerprint=hash(cert.getEncoded());
            signingKey=(PrivateKey)ks.getKey(alias,storePassword);certificate=cert;
            KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,storePassword);context=SSLContext.getInstance("TLSv1.3");
            X509TrustManager playerCertificates=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException();}
                public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{if(chain.length!=1)throw new CertificateException();chain[0].checkValidity();try{chain[0].verify(chain[0].getPublicKey());}catch(GeneralSecurityException e){throw new CertificateException();}}};
            // Self-signed client certificates establish possession, NOT allowlist membership.
            // Admission still checks the application signature, password and explicit ACL below.
            context.init(km.getKeyManagers(),new TrustManager[]{playerCertificates},RANDOM);
            this.password=Objects.requireNonNull(password);this.allowlist=Set.copyOf(allowlist);
        }
        public static Server load(Path root)throws IOException,GeneralSecurityException{
            char[] pass=Files.readString(root.resolve("keystore.pass"),StandardCharsets.US_ASCII).toCharArray();
            try{return new Server(root.resolve("server.p12"),pass,Password.load(root.resolve("room.verifier")),Set.of());}finally{Arrays.fill(pass,'\0');}
        }
        public String invitation(String game,String address,int port,int seconds)throws GeneralSecurityException{
            if(seconds<1||seconds>86400)throw new IllegalArgumentException("Invitation lifetime must be 1-86400 seconds");
            return Invitation.create(game,address,port,System.currentTimeMillis()+seconds*1000L,signingKey,certificate);
        }
        public SSLServerSocket listen(String bind,int port)throws IOException{
            SSLServerSocket s=(SSLServerSocket)context.getServerSocketFactory().createServerSocket();s.setEnabledProtocols(new String[]{"TLSv1.3"});s.setWantClientAuth(true);
            try{s.bind(new InetSocketAddress(InetAddress.getByName(host(bind)),port),16);return s;}catch(IOException|RuntimeException e){s.close();throw failure();}
        }
        private synchronized boolean rate(String ip){
            long now=System.nanoTime();attempts.entrySet().removeIf(e->now-e.getValue()[0]>60_000_000_000L);
            if(!attempts.containsKey(ip)&&attempts.size()>=4096)return false;
            long[] row=attempts.computeIfAbsent(ip,k->new long[]{now,0});return ++row[1]<=12;
        }
        /** Called BEFORE assigning a game seat or emitting any world data. */
        public String admit(Socket raw,String game)throws IOException{
            if(!(raw instanceof SSLSocket socket)||!rate(raw.getInetAddress().getHostAddress())||!admissions.tryAcquire())throw failure();
            try{
                socket.setSoTimeout(DEADLINE_MS);socket.startHandshake();String[] hello=read(socket.getInputStream()).split("\\|",-1);
                if(hello.length!=4||!hello[0].equals("HBNET")||!hello[1].equals("1")||!hello[2].equals(game))throw failure();
                PublicKey key=publicKey(hello[3]);String id=hash(key.getEncoded());String nonce=B64.encodeToString(random(32));
                try{if(!MessageDigest.isEqual(socket.getSession().getPeerCertificates()[0].getPublicKey().getEncoded(),key.getEncoded()))throw failure();}catch(SSLPeerUnverifiedException noCertificate){/* signed application identity remains mandatory */}
                write(socket.getOutputStream(),"CHALLENGE|"+nonce);String[] proof=read(socket.getInputStream()).split("\\|",-1);
                byte[] challenge=("HBNET|1|"+game+"|"+nonce+"|"+hello[3]).getBytes(StandardCharsets.US_ASCII);
                if(proof.length!=3||!proof[0].equals("PROOF")||!verify(hello[3],challenge,proof[1]))throw failure();
                byte[] secret=UN64.decode(proof[2]);char[] chars=new char[secret.length];for(int i=0;i<secret.length;i++)chars[i]=(char)(secret[i]&255);
                boolean accepted;try{Hbnet.password(chars);accepted=password.matches(chars)&&(allowlist.isEmpty()||allowlist.contains(id));}finally{Arrays.fill(chars,'\0');Arrays.fill(secret,(byte)0);}
                if(!accepted)throw failure();write(socket.getOutputStream(),"ACCEPT|1");return id;
            }catch(GeneralSecurityException|RuntimeException|IOException e){try{raw.close();}catch(IOException ignored){}throw failure();}finally{admissions.release();}
        }
    }
    /** Immutable per-connect settings. toString never contains secrets or addresses. */
    public static final class Client {
        public final String fingerprint,game;
        private final Identity identity;
        private final char[] password;
        public Client(String fingerprint,String game,Identity identity,char[] password){this.fingerprint=pin(fingerprint);this.game=game;this.identity=Objects.requireNonNull(identity);Hbnet.password(password);this.password=password.clone();}
        public synchronized void clear(){Arrays.fill(password,'\0');}
        public Socket connect(String address,int port)throws IOException{
            Hbnet.host(address);Hbnet.port(Integer.toString(port));
            SSLSocket socket=null;
            try{
                X509TrustManager trust=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException();}
                    public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{if(chain.length<1)throw new CertificateException();chain[0].checkValidity();if(!hash(chain[0].getEncoded()).equals(fingerprint))throw new CertificateException("Server identity changed (NET-03)");}};
                SSLContext context=SSLContext.getInstance("TLSv1.3");context.init(identity.transportKeys,new TrustManager[]{trust},RANDOM);
                socket=(SSLSocket)context.getSocketFactory().createSocket();socket.setEnabledProtocols(new String[]{"TLSv1.3"});socket.connect(new InetSocketAddress(Hbnet.host(address),port),DEADLINE_MS);socket.setSoTimeout(DEADLINE_MS);socket.startHandshake();
                write(socket.getOutputStream(),"HBNET|1|"+game+"|"+identity.publicKey());String[] challenge=read(socket.getInputStream()).split("\\|",-1);
                if(challenge.length!=2||!challenge[0].equals("CHALLENGE")||!challenge[1].matches("[A-Za-z0-9_-]{43}"))throw failure();
                byte[] bytes; synchronized(this){bytes=new byte[password.length];for(int i=0;i<password.length;i++)bytes[i]=(byte)password[i];}
                try{String signed=identity.sign(("HBNET|1|"+game+"|"+challenge[1]+"|"+identity.publicKey()).getBytes(StandardCharsets.US_ASCII));write(socket.getOutputStream(),"PROOF|"+signed+"|"+B64.encodeToString(bytes));}finally{Arrays.fill(bytes,(byte)0);}
                if(!read(socket.getInputStream()).equals("ACCEPT|1"))throw failure();socket.setSoTimeout(0);return socket;
            }catch(IOException|GeneralSecurityException|RuntimeException e){if(socket!=null)try{socket.close();}catch(IOException ignored){}throw failure();}
        }
        @Override public String toString(){return "Private Connection";}
    }
}
