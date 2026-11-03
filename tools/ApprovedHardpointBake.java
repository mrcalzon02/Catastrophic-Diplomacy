import diplomacy.shipyard.FactionArt;
import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Build-time ONLY: reproduces the approved in-game PNG alpha projector with
 * ImageIO, and bakes every original-art hardpoint into one Java 17 core resource.
 * No Android/desktop graphics dependency and no modification of the source PNG.
 *
 * Run via: java -cp core/build/classes/java/main tools/ApprovedHardpointBake.java
 *               <output file> <root assets> <generated Terran> <generated legacy>
 */
public final class ApprovedHardpointBake {
    private ApprovedHardpointBake(){}
    private record Cloud(int width,int height,int[] opaque){}
    private static String sha(byte[] bytes)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String sourcePath(Battle.Faction faction,Shipyard.Hull hull){
        if(faction!=Battle.Faction.TERRAN)return FactionArt.runtimePath(faction,hull);
        String role=switch(hull.spriteHull()){
            case INTERCEPTOR -> "interceptor";
            case CORVETTE -> "corvette";
            case MISSILE_CORVETTE -> "missile_corvette";
            case FRIGATE -> "frigate";
            case DESTROYER -> "destroyer";
            case LIGHT_CRUISER,CRUISER -> "light_cruiser";
            case HEAVY_CRUISER -> "heavy_cruiser";
            case BATTLECRUISER -> "battlecruiser";
            case CARRIER -> "carrier";
            case DREADNOUGHT -> "dreadnought";
            default -> throw new IllegalStateException("Unrecognized source art for "+hull);
        };
        return "ships/terran/"+role+".png";
    }
    private static Path resolve(String source,List<Path> roots){
        if(source==null||!source.matches("ships/[a-z0-9_/.-]+\\.png"))
            throw new IllegalStateException("Missing approved sprite path: "+source);
        for(Path root:roots){
            Path file=root.resolve(source);
            if(Files.isRegularFile(file))return file;
        }
        throw new IllegalStateException("Missing real source PNG: "+source);
    }
    /** Matches the original Pixmap alpha floor, footprint, sample grid and order. */
    private static Cloud cloud(BufferedImage image){
        int width=image.getWidth(),height=image.getHeight();
        int[] candidates=new int[((width+3)/4)*((height+3)/4)];
        int count=0;
        int clear=Math.max(7,Math.round(height*.028f));
        int firstY=Math.max(clear,Math.round(height*.09f));
        int lastY=Math.min(height-clear,Math.round(height*.87f));
        for(int y=firstY;y<lastY;y+=4)for(int x=clear;x<width-clear;x+=4){
            if(((image.getRGB(x,y)>>>24)&255)<210)continue;
            if(((image.getRGB(x-clear,y)>>>24)&255)<150
                    ||((image.getRGB(x+clear,y)>>>24)&255)<150
                    ||((image.getRGB(x,y-clear)>>>24)&255)<150
                    ||((image.getRGB(x,y+clear)>>>24)&255)<150)continue;
            double localX=154.0*(x+.5-width*.5)/height;
            if(Math.abs(localX)>72)continue;
            candidates[count++]=y*width+x;
        }
        return new Cloud(width,height,java.util.Arrays.copyOf(candidates,count));
    }
    private static int hashSlot(Battle.Faction faction,Shipyard.Hull hull,int socket){
        int key=0x6b8b4567 ^ (faction.ordinal()*0x9e3779b9)
                ^ (hull.ordinal()*0x85ebca6b) ^ (socket*0xc2b2ae35);
        key^=key>>>16;key*=0x7feb352d;key^=key>>>15;
        key*=0x846ca68b;return key^(key>>>16);
    }
    private static List<Shipyard.Slot> project(Battle.Faction faction,Shipyard.Hull hull,
                                    List<Shipyard.Slot> schematic,Cloud cloud){
        if(cloud.opaque().length<schematic.size())
            throw new IllegalStateException("Not enough solid source pixels: "+faction+"/"+hull);
        var outline=HullLayouts.outline(faction,hull);
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE;
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(var point:outline){
            minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
            minY=Math.min(minY,point.y());maxY=Math.max(maxY,point.y());
        }
        int width=cloud.width(),height=cloud.height();
        int minimum=Math.max(17,Math.min(43,(int)Math.round(height*.063)));
        int minSq=minimum*minimum;
        Shipyard.Slot[] placed=new Shipyard.Slot[schematic.size()];
        List<Integer> usedPixels=new ArrayList<>();
        List<Shipyard.Slot> ordered=new ArrayList<>(schematic);
        ordered.sort(Comparator.comparingInt(slot->switch(slot.mount()){
            case WEAPON -> 0;
            case DEFENSE -> 1;
            case SUPPORT -> 2;
            case SYSTEM -> 3;
            case HABITAT -> 4;
        }));
        for(Shipyard.Slot slot:ordered){
            double nx=(slot.x()-minX)/(double)Math.max(1,maxX-minX);
            double ny=(slot.y()-minY)/(double)Math.max(1,maxY-minY);
            int random=hashSlot(faction,hull,slot.index());
            int idealX=(int)Math.round((.10+.80*nx)*(width-1))
                    +Math.floorMod(random,19)-9;
            int idealY=(int)Math.round((.10+.80*(1-ny))*(height-1))
                    +Math.floorMod(random>>>12,19)-9;
            int selected=-1;
            long nearest=Long.MAX_VALUE;
            for(int candidate:cloud.opaque()){
                int cx=candidate%width,cy=candidate/width;
                long dx=cx-idealX,dy=cy-idealY,dist=dx*dx+dy*dy;
                if(dist>=nearest)continue;
                boolean separated=true;
                for(int previous:usedPixels){
                    int px=cx-previous%width,py=cy-previous/width;
                    if(px*px+py*py<minSq){separated=false;break;}
                }
                if(separated){selected=candidate;nearest=dist;}
            }
            if(selected<0)for(int candidate:cloud.opaque()){
                if(usedPixels.contains(candidate))continue;
                int cx=candidate%width,cy=candidate/width;
                long dx=cx-idealX,dy=cy-idealY,dist=dx*dx+dy*dy;
                if(dist<nearest){nearest=dist;selected=candidate;}
            }
            if(selected<0)throw new IllegalStateException("No physical plate for "+faction+"/"+hull+"/"+slot.index());
            usedPixels.add(selected);
            int px=selected%width,py=selected/width;
            int localX=(int)Math.round(154.0*(px+.5-width*.5)/height);
            int localY=(int)Math.round(154.0*(height*.5-py-.5)/height);
            placed[slot.index()]=new Shipyard.Slot(slot.index(),slot.mount(),localX,localY);
        }
        return List.of(placed);
    }
    public static void main(String[] args)throws Exception{
        if(args.length<4)throw new IllegalArgumentException("Expected destination and original-art roots");
        Path destination=Path.of(args[0]);
        List<Path> roots=new ArrayList<>();
        for(int i=1;i<args.length;i++)roots.add(Path.of(args[i]));
        StringBuilder body=new StringBuilder("CD-HARDPOINTS-1\n");
        Set<Path> sources=new HashSet<>();
        int count=0,sockets=0;
        for(Battle.Faction faction:Battle.Faction.values())for(Shipyard.Hull hull:Shipyard.Hull.values()){
            String path=sourcePath(faction,hull);
            Path source=resolve(path,roots);
            byte[] bytes=Files.readAllBytes(source);
            BufferedImage image=ImageIO.read(source.toFile());
            if(image==null||image.getWidth()<30||image.getHeight()<30)
                throw new IllegalStateException("Invalid original sprite: "+source);
            List<Shipyard.Slot> mapped=project(faction,hull,
                    HullLayouts.slots(faction,hull),cloud(image));
            if(mapped.size()!=hull.slots.size())
                throw new IllegalStateException("Altered physical socket inventory");
            body.append(faction.name()).append('|').append(hull.name()).append('|')
                    .append(path).append('|').append(sha(bytes)).append('|')
                    .append(image.getWidth()).append('|').append(image.getHeight()).append('|');
            for(int i=0;i<mapped.size();i++){
                Shipyard.Slot slot=mapped.get(i);
                if(slot.index()!=i||slot.mount()!=hull.slots.get(i).mount())
                    throw new IllegalStateException("Physical socket identity changed");
                if(i>0)body.append(',');
                body.append(slot.index()).append(':').append(slot.mount()).append(':')
                    .append(slot.x()).append(':').append(slot.y());
            }
            body.append('\n');
            sources.add(source.toAbsolutePath().normalize());
            sockets+=mapped.size();
            count++;
        }
        int expected=Battle.Faction.values().length*Shipyard.Hull.values().length;
        if(count!=expected||sources.size()!=110)
            throw new IllegalStateException("Incomplete 11-faction original-art map: "+
                    count+" variants from "+sources.size()+" unique PNGs");
        String signed=body+"SHA256:"+sha(body.toString().getBytes(StandardCharsets.UTF_8))+"\n";
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.writeString(destination,signed,StandardCharsets.UTF_8);
        System.out.println("PASS approved-source hardpoints: "+count+" faction/variant combinations, "+
                sources.size()+" authenticated source PNGs, "+sockets+" physical socket anchors");
    }
}
