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
    private static Map<String,List<Shipyard.Slot>> read(){
        try(InputStream stream=ApprovedHardpointResource.class.getResourceAsStream(FILE)){
            if(stream==null)return null;
            // Android API 26-32 has no InputStream.readAllBytes(). Read the
            // resource in bounded chunks to support older devices and reject
            // oversized or damaged baked maps before allocating more memory.
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream(65536);
            byte[] buffer=new byte[8192];
            int read;
            while((read=stream.read(buffer))!=-1){
                if(output.size()+read>2_000_000)
                    throw new IllegalStateException("Invalid approved hardpoint resource size");
                output.write(buffer,0,read);
            }
            byte[] bytes=output.toByteArray();
            if(bytes.length<2000)
                throw new IllegalStateException("Invalid approved hardpoint resource size");
            String record=new String(bytes,StandardCharsets.UTF_8);
            int digestAt=record.lastIndexOf("SHA256:");
            if(digestAt<=0||record.charAt(digestAt-1)!='\n'
                    ||record.charAt(record.length()-1)!='\n')
                throw new IllegalStateException("Hardpoint resource signature missing");
            String body=record.substring(0,digestAt);
            String expected=record.substring(digestAt+7,record.length()-1);
            byte[] checksum=MessageDigest.getInstance("SHA-256").digest(
                    body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(checksum.length*2);
            for(byte value:checksum){
                int unsigned=value&0xff;
                hex.append(Character.forDigit(unsigned>>>4,16));
                hex.append(Character.forDigit(unsigned&15,16));
            }
            String actual=hex.toString();
            if(!actual.equals(expected))
                throw new IllegalStateException("Approved hardpoint map checksum mismatch");
            String[] lines=body.split("\n");
            if(lines.length!=1+Battle.Faction.values().length*Shipyard.Hull.values().length
                    ||!lines[0].equals("CD-HARDPOINTS-1"))
                throw new IllegalStateException("Incorrect baked hardpoint inventory");
            Map<String,List<Shipyard.Slot>> map=new HashMap<>();
            for(int i=1;i<lines.length;i++){
                String[] fields=lines[i].split("\\|",-1);
                if(fields.length!=7)
                    throw new IllegalStateException("Malformed hardpoint row "+i);
                Battle.Faction faction=Battle.Faction.valueOf(fields[0]);
                Shipyard.Hull hull=Shipyard.Hull.valueOf(fields[1]);
                if(!fields[2].matches("ships/[a-z0-9_/.-]+\\.png")
                        ||!fields[3].matches("[0-9a-f]{64}")
                        ||Integer.parseInt(fields[4])<30
                        ||Integer.parseInt(fields[5])<30)
                    throw new IllegalStateException("Invalid source-art signature "+i);
                String[] parts=fields[6].split(",",-1);
                if(parts.length!=hull.slots.size())
                    throw new IllegalStateException("Altered slot count "+faction+"/"+hull);
                List<Shipyard.Slot> ordered=new ArrayList<>(parts.length);
                for(int j=0;j<parts.length;j++){
                    String[] values=parts[j].split(":",-1);
                    if(values.length!=4)throw new IllegalStateException("Corrupt socket "+i+"/"+j);
                    int slot=Integer.parseInt(values[0]);
                    Shipyard.Mount mount=Shipyard.Mount.valueOf(values[1]);
                    int x=Integer.parseInt(values[2]),y=Integer.parseInt(values[3]);
                    if(slot!=j||hull.slots.get(j).index()!=slot
                            ||hull.slots.get(j).mount()!=mount
                            ||Math.abs(x)>500||Math.abs(y)>500)
                        throw new IllegalStateException("Invalid original socket identity "+i+"/"+j);
                    ordered.add(new Shipyard.Slot(slot,mount,x,y));
                }
                String key=faction.name()+"/"+hull.name();
                if(map.putIfAbsent(key,List.copyOf(ordered))!=null)
                    throw new IllegalStateException("Duplicate source-art hull "+key);
            }
            return Map.copyOf(map);
        }catch(RuntimeException invalid){throw invalid;}
        catch(Exception error){
            throw new IllegalStateException("Unable to read approved hardpoint map",error);
        }
    }
}
