package diplomacy.shipyard;

import diplomacy.sim.Battle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * The same build-baked original-PNG socket map is authoritative in the
 * headless host and Android/desktop game. This class uses no graphics or
 * ImageIO classes (which Android cannot supply).
 */
public final class ApprovedHardpointResource {
    private ApprovedHardpointResource(){}
    private static final String FILE="/hardpoints/approved-v1.txt";
    private static final class Lazy {
        static final Map<String,List<Shipyard.Slot>> DATA=read();
    }
    public static boolean available(){return Lazy.DATA!=null;}
    public static void require(){
        if(!available())throw new IllegalStateException(
                "Approved hardpoint coordinate resource missing: "+FILE);
    }
    public static List<Shipyard.Slot> get(Battle.Faction faction,Shipyard.Hull hull){
        Map<String,List<Shipyard.Slot>> map=Lazy.DATA;
        if(map==null)return null; // Standalone, unbundled javac tests only.
        List<Shipyard.Slot> slots=map.get(faction.name()+"/"+hull.name());
        if(slots==null)throw new IllegalStateException(
                "Missing original-art hardpoints for "+faction+"/"+hull);
        return slots;
    }
    /**
     * Parse each authenticated record as it arrives. Android 10 can run this
     * game with a 16 MiB Java heap: never copy the whole hardpoint resource
     * into a second byte array, String, substring and split-array at startup.
     * Hash the exact original UTF-8 bytes including line endings; corrupted
     * signatures, truncated files and extra trailing records fail closed.
     */
    private static Map<String,List<Shipyard.Slot>> read(){
        try(InputStream resource=ApprovedHardpointResource.class.getResourceAsStream(FILE)){
            if(resource==null)return null;
            final int expected=Battle.Faction.values().length*Shipyard.Hull.values().length;
            Map<String,List<Shipyard.Slot>> map=new HashMap<>(expected*4/3+1);
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            java.io.BufferedInputStream stream=new java.io.BufferedInputStream(resource,8192);
            byte[] line=new byte[8192];
            int size=0,bytesRead=0,rows=0;
            boolean signed=false;
            int ch;
            while((ch=stream.read())!=-1){
                if(++bytesRead>2_000_000)
                    throw new IllegalStateException("Invalid approved hardpoint resource size");
                if(signed)throw new IllegalStateException("Trailing data after hardpoint signature");
                if(ch!='\n'){
                    if(size==line.length)
                        throw new IllegalStateException("Oversized baked hardpoint row");
                    line[size++]=(byte)ch;
                    continue;
                }
                String text=new String(line,0,size,StandardCharsets.UTF_8);
                if(text.startsWith("SHA256:")){
                    if(rows!=expected+1||!text.matches("SHA256:[0-9a-f]{64}"))
                        throw new IllegalStateException("Hardpoint resource signature missing");
                    byte[] checksum=hash.digest();
                    StringBuilder hex=new StringBuilder(checksum.length*2);
                    for(byte value:checksum){
                        int unsigned=value&0xff;
                        hex.append(Character.forDigit(unsigned>>>4,16));
                        hex.append(Character.forDigit(unsigned&15,16));
                    }
                    if(!hex.toString().equals(text.substring(7)))
                        throw new IllegalStateException("Approved hardpoint map checksum mismatch");
                    signed=true;
                    size=0;
                    continue;
                }
                if(rows>expected)
                    throw new IllegalStateException("Incorrect baked hardpoint inventory");
                hash.update(line,0,size);
                hash.update((byte)'\n');
                if(rows==0){
                    if(!text.equals("CD-HARDPOINTS-1"))
                        throw new IllegalStateException("Invalid baked hardpoint header");
                }else{
                    String[] fields=text.split("\\|",-1);
                    if(fields.length!=7)
                        throw new IllegalStateException("Malformed hardpoint row "+rows);
                    Battle.Faction faction=Battle.Faction.valueOf(fields[0]);
                    Shipyard.Hull hull=Shipyard.Hull.valueOf(fields[1]);
                    if(!fields[2].matches("ships/[a-z0-9_/.-]+\\.png")
                            ||!fields[3].matches("[0-9a-f]{64}")
                            ||Integer.parseInt(fields[4])<30
                            ||Integer.parseInt(fields[5])<30)
                        throw new IllegalStateException("Invalid source-art signature "+rows);
                    String[] parts=fields[6].split(",",-1);
                    if(parts.length!=hull.slots.size())
                        throw new IllegalStateException("Altered slot count "+faction+"/"+hull);
                    List<Shipyard.Slot> ordered=new ArrayList<>(parts.length);
                    for(int j=0;j<parts.length;j++){
                        String[] values=parts[j].split(":",-1);
                        if(values.length!=4)
                            throw new IllegalStateException("Corrupt socket "+rows+"/"+j);
                        int slot=Integer.parseInt(values[0]);
                        Shipyard.Mount mount=Shipyard.Mount.valueOf(values[1]);
                        int x=Integer.parseInt(values[2]),y=Integer.parseInt(values[3]);
                        if(slot!=j||hull.slots.get(j).index()!=slot
                                ||hull.slots.get(j).mount()!=mount
                                ||Math.abs(x)>500||Math.abs(y)>500)
                            throw new IllegalStateException("Invalid original socket identity "+rows+"/"+j);
                        ordered.add(new Shipyard.Slot(slot,mount,x,y));
                    }
                    String key=faction.name()+"/"+hull.name();
                    if(map.putIfAbsent(key,java.util.Collections.unmodifiableList(ordered))!=null)
                        throw new IllegalStateException("Duplicate source-art hull "+key);
                }
                rows++;
                size=0;
            }
            if(bytesRead<2000||size!=0||!signed||rows!=expected+1||map.size()!=expected)
                throw new IllegalStateException("Missing or truncated approved hardpoint resource");
            return java.util.Collections.unmodifiableMap(map);
        }catch(RuntimeException invalid){throw invalid;}
        catch(Exception error){
            throw new IllegalStateException("Unable to read approved hardpoint map",error);
        }
    }
}
