package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Textless, edge-filtered original button art. Eight palette variants are
 * selected from CD_Gritty_UI_Button_Sheets_v2, not the held/cropped controls.
 * Native labels remain live BitmapFont glyphs on top of these surfaces.
 * Art is optional: MilitaryUiTheme's procedural hit-aligned buttons survive
 * a missing/unreadable sprite atlas.
 */
final class GrittyUiChrome {
    static final String PATH="ui/gritty/buttons_v1.png";
    static final int TILES=8, COLUMNS=2, TILE_WIDTH=224, TILE_HEIGHT=72;
    static final int SPRITE_WIDTH=212, SPRITE_HEIGHT=64;
    static final int NORMAL=0, SELECTED=1, PRIMARY=2,
            CONFIRM=3, WARNING=4, DANGER=5, TELEMETRY=6, SECONDARY=7;
    private Texture texture;
    private final NinePatch[] tiles=new NinePatch[TILES];

    GrittyUiChrome(){
        try{
            if(!Gdx.files.internal(PATH).exists())return;
            texture=new Texture(Gdx.files.internal(PATH));
            if(texture.getWidth()!=COLUMNS*TILE_WIDTH||
                    texture.getHeight()!=4*TILE_HEIGHT)
                throw new IllegalArgumentException("Grimy UI atlas dimensions");
            texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            for(int index=0;index<TILES;index++){
                int x=(index%COLUMNS)*TILE_WIDTH+6;
                int y=(index/COLUMNS)*TILE_HEIGHT+4;
                NinePatch patch=new NinePatch(
                        new TextureRegion(texture,x,y,SPRITE_WIDTH,SPRITE_HEIGHT),
                        28,28,20,20);
                patch.scale(.52f,.52f);
                tiles[index]=patch;
            }
        }catch(RuntimeException problem){
            dispose();
            if(Gdx.app!=null)
                Gdx.app.error("CD-UI","Optional vetted button atlas unavailable",problem);
        }
    }

    boolean loaded(){return texture!=null;}

    void draw(SpriteBatch batch,int type,float x,float y,float width,float height){
        if(texture==null||type<0||type>=TILES||width<65||height<25)return;
        tiles[type].draw(batch,x,y,width,height);
    }

    void dispose(){
        if(texture!=null)texture.dispose();
        texture=null;
    }
}
