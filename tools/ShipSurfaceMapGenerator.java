import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Build-time, deterministic tangent-space surface maps from APPROVED sprites.
 * No invented hull pixels. Alpha never exceeds the original PNG silhouette.
 * java tools/ShipSurfaceMapGenerator.java <out-root> <ships-source-dir>...
 */
public final class ShipSurfaceMapGenerator {
    private ShipSurfaceMapGenerator() {}
    private static int clamp(double v) { return Math.max(0, Math.min(255,(int)Math.round(v))); }
    static float height(BufferedImage a,int x,int y,int cx,int cy){
        int p=a.getRGB(Math.min(a.getWidth()-1,Math.max(0,x)),
                       Math.min(a.getHeight()-1,Math.max(0,y)));
        if((p>>>24)<16) p=a.getRGB(cx,cy); // zero-alpha margins must not bevel opaque neighbors
        float r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
        float l=(.2126f*r+.7152f*g+.0722f*b)/255f;
        return (float)Math.sqrt(l);
    }
    static BufferedImage[] derive(BufferedImage original){
        int w=original.getWidth(),h=original.getHeight();
        BufferedImage n=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        BufferedImage e=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int src=original.getRGB(x,y),alpha=src>>>24;
            if(alpha==0){n.setRGB(x,y,0);e.setRGB(x,y,0);continue;}
            // Sobel derivatives with bounded strength; no contour art is erased.
            float gx=height(original,x+1,y-1,x,y)+2*height(original,x+1,y,x,y)
                    +height(original,x+1,y+1,x,y)-height(original,x-1,y-1,x,y)
                    -2*height(original,x-1,y,x,y)-height(original,x-1,y+1,x,y);
            float gy=height(original,x-1,y+1,x,y)+2*height(original,x,y+1,x,y)
                    +height(original,x+1,y+1,x,y)-height(original,x-1,y-1,x,y)
                    -2*height(original,x,y-1,x,y)-height(original,x+1,y-1,x,y);
            float nx=-gx*.45f,ny=-gy*.45f,nz=1f;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            int normal=(alpha<<24)|(clamp((nx/len*.5+.5)*255)<<16)
                     |(clamp((ny/len*.5+.5)*255)<<8)|clamp((nz/len*.5+.5)*255);
            n.setRGB(x,y,normal);
            int r=(src>>>16)&255,g=(src>>>8)&255,b=src&255;
            int maximum=Math.max(r,Math.max(g,b)),minimum=Math.min(r,Math.min(g,b));
            float bright=maximum/255f, saturation=(maximum-minimum)/(float)Math.max(1,maximum);
            // Bright, colorful source details: navigation lights/windows/hot equipment.
            // Dark-painted plating stays dark; no made-up engine positions.
            float emitted=Math.max(0,Math.min(1,(bright-.37f)*2.2f))
                         *Math.max(0,Math.min(1,(saturation-.12f)*2.1f));
            // Neutral pale pinlights are allowed, but never bright unpainted hull plating.
            if(bright>.83f && minimum>.68f*255)
                emitted=Math.max(emitted,(bright-.83f)*1.1f);
            int er=clamp(r*emitted),eg=clamp(g*emitted),eb=clamp(b*emitted);
            e.setRGB(x,y,(alpha<<24)|(er<<16)|(eg<<8)|eb);
        }
        return new BufferedImage[]{n,e};
    }
    // Material data needs far less resolution than painted albedo at tactical
    // zoom. Capping at 320px avoids tripling GPU memory on Android/Fire OS.
    private static final int MAX_AXIS=320;
    static BufferedImage bounded(BufferedImage input){
        int width=input.getWidth(),height=input.getHeight();
        if(Math.max(width,height)<=MAX_AXIS)return input;
        double scale=MAX_AXIS/(double)Math.max(width,height);
        int w=Math.max(2,(int)Math.round(width*scale));
        int h=Math.max(2,(int)Math.round(height*scale));
        BufferedImage target=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=target.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(input,0,0,w,h,null);
        }finally{g.dispose();}
        return target;
    }
    static void generate(Path source,Path destination) throws IOException{
        BufferedImage image=ImageIO.read(source.toFile());
        if(image==null || image.getWidth()<2||image.getHeight()<2)
            throw new IOException("Invalid approved hull PNG "+source);
        BufferedImage[] maps=derive(bounded(image));
        Files.createDirectories(destination.getParent());
        for(int i=0;i<2;i++){
            String name=destination.getFileName().toString();
            Path target=destination.resolveSibling(name.substring(0,name.length()-4)
                    +(i==0?"_n.png":"_e.png"));
            if(!ImageIO.write(maps[i],"PNG",target.toFile()))
                throw new IOException("PNG encoder unavailable "+target);
        }
    }
    /** Structural pixel regression for transparent margins, painted lights and normals. */
    static void selfTest(){
        BufferedImage sample=new BufferedImage(7,7,BufferedImage.TYPE_INT_ARGB);
        for(int y=1;y<6;y++)for(int x=1;x<6;x++)
            sample.setRGB(x,y,0xff3b4652);
        sample.setRGB(3,3,0xff25d6f8);
        BufferedImage[] maps=derive(sample);
        if(maps[0].getRGB(0,0)!=0||maps[1].getRGB(0,0)!=0)
            throw new AssertionError("Transparent ship margins must remain transparent");
        if((maps[0].getRGB(3,3)>>>24)!=255||
                (maps[1].getRGB(3,3)>>>24)!=255)
            throw new AssertionError("Source alpha lost in material maps");
        if((maps[1].getRGB(3,3)&0x00ffffff)==0||
                (maps[1].getRGB(1,1)&0x00ffffff)!=0)
            throw new AssertionError("Source-driven emission misclassified");
        if(((maps[0].getRGB(2,3)>>>16)&255)==128)
            throw new AssertionError("Painted detail did not produce a surface normal");
        BufferedImage oversized=new BufferedImage(640,320,BufferedImage.TYPE_INT_ARGB);
        BufferedImage limited=bounded(oversized);
        if(limited.getWidth()!=320||limited.getHeight()!=160)
            throw new AssertionError("Material texture cap/aspect is invalid");
    }
    public static void main(String[] args) throws Exception{
        selfTest();
        if(args.length<2)throw new IllegalArgumentException("Output and >=1 ship source root required");
        Path output=Path.of(args[0]);
        Map<String,Path> sources=new TreeMap<>();
        for(int i=1;i<args.length;i++){
            Path root=Path.of(args[i]);
            if(!Files.isDirectory(root))continue;
            try(var walk=Files.walk(root)){
                walk.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".png"))
                    .filter(p->!p.getFileName().toString().matches(".*_[ne]\\.png"))
                    .forEach(p->{
                        String relative=root.relativize(p).toString().replace('\\','/');
                        // Accept only the game's original faction, fighter and Terran fleet families.
                        if(relative.startsWith("terran/")||relative.startsWith("factions/")
                                ||relative.startsWith("fighters/")||relative.startsWith("legacy_factions/")) {
                            Path prior=sources.putIfAbsent(relative,p);
                            if(prior!=null){
                                try{
                                    if(!Arrays.equals(Files.readAllBytes(prior),Files.readAllBytes(p)))
                                        throw new IllegalStateException("Two different approved sprites: "+relative);
                                }catch(IOException ex){throw new IllegalStateException(ex);}
                            }
                        }
                    });
            }
        }
        long terran=sources.keySet().stream().filter(p->p.startsWith("terran/")).count();
        long modern=sources.keySet().stream().filter(p->p.startsWith("factions/")).count();
        long fighters=sources.keySet().stream().filter(p->p.startsWith("fighters/")).count();
        long legacy=sources.keySet().stream().filter(p->p.startsWith("legacy_factions/")).count();
        if(terran<10||modern<50||fighters<50||legacy<50)
            throw new IllegalStateException("Incomplete original art: terran="+terran+
                    " approved factions="+modern+" fighters="+fighters+" legacy="+legacy);
        for(var item:sources.entrySet())generate(item.getValue(),output.resolve("ships").resolve(item.getKey()));
        System.out.println("PASS: derived "+sources.size()+" paired original-alpha normal/emissive maps");
    }
}
