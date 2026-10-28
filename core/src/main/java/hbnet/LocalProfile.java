package hbnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;

/** Local directory and contacts. Presence is unknown unless directly observed.
 * Contact approval alone NEVER grants verified-key admission or administrator rights.
 */
public final class LocalProfile {
    private final Hbnet.Store store;
    private final Properties data;
    private final Map<String,Integer> serverAliases=new HashMap<>(),friendAliases=new HashMap<>();
    public final Hbnet.Identity identity;
    public LocalProfile(Path directory)throws IOException,GeneralSecurityException{
        store=new Hbnet.Store(directory.resolve("network.profile"));data=store.load();
        identity=Hbnet.Identity.load(directory.resolve("player.identity"));
        Privacy.enabled=streamSafe();
    }
    public synchronized boolean streamSafe(){return Boolean.parseBoolean(data.getProperty("streamSafe","true"));}
    public synchronized void streamSafe(boolean value)throws IOException{data.setProperty("streamSafe",Boolean.toString(value));save();Privacy.enabled=value;}
    private void save()throws IOException{store.save(data);}
    public record SavedServer(String id,String label,String host,int port,String pin,boolean favorite,long lastJoined){
        @Override public String toString(){return "Private Server";}
    }
    public synchronized List<SavedServer> servers(){
        List<SavedServer> result=new ArrayList<>();
        for(String key:data.stringPropertyNames())if(key.startsWith("server.")&&key.endsWith(".host")){
            String id=key.substring(7,key.length()-5),prefix="server."+id+".";
            result.add(new SavedServer(id,data.getProperty(prefix+"label","Private Server"),data.getProperty(key),Integer.parseInt(data.getProperty(prefix+"port")),data.getProperty(prefix+"pin",""),Boolean.parseBoolean(data.getProperty(prefix+"favorite","false")),Long.parseLong(data.getProperty(prefix+"joined","0"))));
        }
        result.sort(Comparator.comparing(SavedServer::favorite).reversed().thenComparing(SavedServer::id));return List.copyOf(result);
    }
    private static String text(String value,int max){if(value==null||value.length()>max||value.chars().anyMatch(c->c<32||c>126))throw new IllegalArgumentException("Use bounded printable ASCII text");return value;}
    public synchronized String server(String id,String label,String host,int port,String pin)throws IOException{
        String address=Hbnet.host(host);Hbnet.port(Integer.toString(port));Hbnet.pin(pin);text(label,64);
        if(id==null){if(servers().size()>=256)throw new IllegalArgumentException("Saved server limit reached");id=UUID.randomUUID().toString();}
        else {boolean found=false;for(SavedServer s:servers())if(s.id().equals(id))found=true;if(!found)throw new IllegalArgumentException("Unknown server");}
        String p="server."+id+".";String previous=data.getProperty(p+"pin");
        if(previous!=null&&!previous.equals(pin))throw new IllegalArgumentException("Server identity changed: remove and privately verify again (NET-03)");
        data.setProperty(p+"host",address);data.setProperty(p+"port",Integer.toString(port));data.setProperty(p+"pin",pin);data.setProperty(p+"label",label);save();return id;
    }
    private static String idValue(String s){return s;}
    public synchronized void removeServer(String id)throws IOException{removePrefix("server."+id+".");save();}
    public synchronized void favoriteServer(String id,boolean value)throws IOException{data.setProperty("server."+id+".favorite",Boolean.toString(value));save();}
    public synchronized void joined(String id)throws IOException{data.setProperty("server."+id+".joined",Long.toString(System.currentTimeMillis()));save();}
    public enum ContactState { PENDING, ACCEPTED, DECLINED, BLOCKED }
    public record Contact(String id,String publicKey,String label,String notes,ContactState state,boolean favorite,boolean verified,long observed){
        public String presence(){return observed==0?"Unknown/unreachable":"Last seen "+java.time.Instant.ofEpochMilli(observed);}
        @Override public String toString(){return "Private Friend";}
    }
    public synchronized List<Contact> friends(){
        List<Contact> result=new ArrayList<>();for(String key:data.stringPropertyNames())if(key.startsWith("friend.")&&key.endsWith(".key")){
            String id=key.substring(7,key.length()-4),p="friend."+id+".";
            result.add(new Contact(id,data.getProperty(key),data.getProperty(p+"label","Friend"),data.getProperty(p+"notes",""),ContactState.valueOf(data.getProperty(p+"state","PENDING")),Boolean.parseBoolean(data.getProperty(p+"favorite","false")),Boolean.parseBoolean(data.getProperty(p+"verified","false")),Long.parseLong(data.getProperty(p+"observed","0"))));
        }result.sort(Comparator.comparing(Contact::favorite).reversed().thenComparing(Contact::id));return List.copyOf(result);
    }
    public synchronized String importCard(String card,String label)throws IOException,GeneralSecurityException{
        text(label,64);String key=Hbnet.cardKey(card);String id=Hbnet.hash(Base64.getUrlDecoder().decode(key));
        if(id.equals(identity.id()))throw new IllegalArgumentException("This is your own card");
        if(data.containsKey("friend."+id+".key"))return id;
        if(friends().size()>=512)throw new IllegalArgumentException("Contact limit reached");
        data.setProperty("friend."+id+".key",key);data.setProperty("friend."+id+".label",label);data.setProperty("friend."+id+".state","PENDING");save();return id;
    }
    public synchronized void state(String id,ContactState state)throws IOException{
        Contact c=contact(id);if(c.state()==ContactState.BLOCKED&&state!=ContactState.PENDING)throw new IllegalArgumentException("Unblock to pending before accepting");
        data.setProperty("friend."+id+".state",state.name());save();
    }
    private Contact contact(String id){return friends().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown contact"));}
    public synchronized void edit(String id,String label,String notes,boolean favorite)throws IOException{contact(id);text(label,64);text(notes,256);String p="friend."+id+".";data.setProperty(p+"label",label);data.setProperty(p+"notes",notes);data.setProperty(p+"favorite",Boolean.toString(favorite));save();}
    /** Must be called only with the signer ID returned by authenticated direct transport. */
    public synchronized void observedAuthenticated(String id)throws IOException{Contact c=contact(id);if(c.state()==ContactState.BLOCKED)return;data.setProperty("friend."+id+".verified","true");data.setProperty("friend."+id+".observed",Long.toString(System.currentTimeMillis()));save();}
    public synchronized void removeFriend(String id)throws IOException{removePrefix("friend."+id+".");save();}
    private void removePrefix(String prefix){new ArrayList<>(data.stringPropertyNames()).stream().filter(k->k.startsWith(prefix)).forEach(data::remove);}
    public synchronized Set<String> reviewedAllowlist(){Set<String> ids=new HashSet<>();for(Contact c:friends())if(c.state()==ContactState.ACCEPTED&&c.verified())ids.add(c.id());return Set.copyOf(ids);}
    /** Contact backup excludes keys, endpoints, passwords and verified/allowlist authority. */
    public synchronized String exportContacts()throws IOException{
        Properties p=new Properties();p.setProperty("schema","1");int i=0;
        for(Contact c:friends()){p.setProperty(i+".key",c.publicKey());p.setProperty(i+".label",c.label());p.setProperty(i+".notes",c.notes());i++;}
        p.setProperty("count",Integer.toString(i));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();p.store(out,"Private contacts");byte[] body=out.toByteArray();
        return "HBCONTACTS1."+Base64.getUrlEncoder().withoutPadding().encodeToString(body)+"."+Hbnet.hash(body);
    }
    public synchronized void importContacts(String backup)throws IOException,GeneralSecurityException{
        if(backup==null||backup.length()>524288)throw Hbnet.failure();String[] parts=backup.split("\\.",-1);if(parts.length!=3||!parts[0].equals("HBCONTACTS1"))throw Hbnet.failure();
        byte[] bytes;try{bytes=Base64.getUrlDecoder().decode(parts[1]);}catch(IllegalArgumentException e){throw Hbnet.failure();}if(!Hbnet.hash(bytes).equals(parts[2]))throw Hbnet.failure();
        Properties p=new Properties();p.load(new java.io.ByteArrayInputStream(bytes));if(!"1".equals(p.getProperty("schema")))throw Hbnet.failure();int count=Integer.parseInt(p.getProperty("count","-1"));if(count<0||count>512||friends().size()+count>512)throw Hbnet.failure();
        java.util.List<String[]> rows=new ArrayList<>();for(int i=0;i<count;i++){String key=p.getProperty(i+".key"),label=text(p.getProperty(i+".label"),64),notes=text(p.getProperty(i+".notes"),256);String body="HBFRIEND1."+key;String card=body+"."+Hbnet.hash(body.getBytes(StandardCharsets.US_ASCII));Hbnet.cardKey(card);rows.add(new String[]{card,label,notes});}
        for(String[] row:rows){String id=importCard(row[0],row[1]);Contact c=contact(id);if(c.state()==ContactState.PENDING)edit(id,row[1],row[2],false);}
    }
    public synchronized String serverLabel(SavedServer s,int index){return streamSafe()?String.format(Locale.ROOT,"Private Server %02d",serverAliases.computeIfAbsent(s.id(),key->serverAliases.size()+1)):s.label();}
    public synchronized String friendLabel(Contact c,int index){return streamSafe()?String.format(Locale.ROOT,"Friend %02d",friendAliases.computeIfAbsent(c.id(),key->friendAliases.size()+1)):c.label();}
}
