package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Independent Java2D diagnostic raster of real simulation geometry; NOT a libGDX GPU screenshot.
 * Use it to prove that 400 generated ship triangles have visible pixels at the deployed coords.
 */
public final class BattleGeometryRasterTest {
    private static final int W=1280,H=720;
    private static void verify(boolean good,String message){if(!good)throw new AssertionError(message);}
    private static Color faction(Battle.Faction f){return new Color(f.r,f.g,f.b);}
    private static int screenY(double y){return H-1-(int)Math.round(y);}
    public static void main(String[] args)throws IOException{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Battle battle=Battle.duel(924L,200,Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),
                Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.DREADNOUGHT),catalog);
        verify(battle.ships().size()==400,"400 individual simulated hulls required");
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(5,9,23));g.fillRect(0,0,W,H);
        g.setColor(new Color(24,34,48));
        for(int x=40;x<1240;x+=40)g.drawLine(x,70,x,650);
        for(int y=70;y<650;y+=40)g.drawLine(40,y,1240,y);
        int visible=0;
        for(Battle.Ship ship:battle.ships()){
            double a=Math.cos(ship.facing)*ship.visualScale(),b=Math.sin(ship.facing)*ship.visualScale();
            double x=ship.x,y=ship.y;
            // Identical fallback-hull triangle coordinate contract to CatastrophicGame.
            Polygon triangle=new Polygon(
                    new int[]{(int)Math.round(x+a*14),(int)Math.round(x-a*8-b*7),(int)Math.round(x-a*8+b*7)},
                    new int[]{screenY(y+b*14),screenY(y-b*8+a*7),screenY(y-b*8-a*7)},3);
            if(triangle.getBounds().x>=25 && triangle.getBounds().x+triangle.getBounds().width<=1250
                    && triangle.getBounds().y>=60 && triangle.getBounds().y+triangle.getBounds().height<=660)visible++;
            g.setColor(faction(ship.faction));g.fillPolygon(triangle);
            g.setStroke(new BasicStroke(1.2f));g.setColor(ship.team==0?Color.CYAN:Color.ORANGE);
            g.drawLine((int)Math.round(x-a*10),screenY(y-b*10),(int)Math.round(x-a*23),screenY(y-b*23));
        }
        verify(visible==400,"one or more actual ship polygons outside the field of view: "+visible);
        int coloredPixels=0;
        for(int yy=60;yy<=660;yy++)for(int xx=30;xx<=1250;xx++){
            int rgb=image.getRGB(xx,yy)&0xffffff;
            int red=(rgb>>16)&255,green=(rgb>>8)&255,blue=rgb&255;
            if(red>45&&green>60&&blue>75)coloredPixels++;
        }
        verify(coloredPixels>4000,"ship meshes did not produce visible colored pixels: "+coloredPixels);
        g.setColor(new Color(15,24,41));g.fillRect(0,0,W,50);
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,19));
        g.drawString("200 v 200 — headless hull-visibility diagnostic",22,30);
        g.setColor(Color.LIGHT_GRAY);g.setFont(new Font("SansSerif",Font.PLAIN,14));
        g.drawString("HEADLESS GEOMETRY — NOT GAMEPLAY FOOTAGE",910,29);
        g.dispose();
        if(args.length>0)ImageIO.write(image,"png",Path.of(args[0]).toFile());
        System.out.println("PASS rasterized 400/400 ship polygons inside 1280x720 battlefield | ship-color pixels="+coloredPixels);
    }
}
