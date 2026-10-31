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

    /**
     * Resolve interactive body art from the *existing* authored atlas.
     * No procedural glow frames. Normal/selected states are sprite-based,
     * as are hover/press variants. The semantic intent remains visible at
     * rest; a pressed button is also physically inset like a real switch.
     */
    static int stateVariant(int base,boolean hover,boolean down){
        if(base<0)return SECONDARY; // disabled, visibly dimmed by drawState
        if(base>=TILES)throw new IllegalArgumentException("Unknown button variant");
        if(down)return switch(base){
            case NORMAL,SELECTED,TELEMETRY,SECONDARY -> PRIMARY;
            case WARNING -> DANGER;
            default -> base; // preserve confirm/primary/danger semantic coloring
        };
        if(hover)return switch(base){
            case NORMAL -> SELECTED;
            case SELECTED -> TELEMETRY;
            case WARNING -> PRIMARY;
            case DANGER -> WARNING;
            case SECONDARY -> SELECTED;
            default -> base; // retain the warning/confirmation intent artwork
        };
        return base;
    }

    void drawState(SpriteBatch batch,int base,boolean hover,boolean down,
                   float x,float y,float width,float height){
        if(texture==null||width<65||height<25)return;
        int variant=stateVariant(base,hover,down);
        float color=batch.getPackedColor();
        // Button body, not a painted border, shows each state. The pressed
        // surface moves inward and darkens; the active touch rectangle does not.
        if(base<0)batch.setColor(.46f,.46f,.46f,.68f);
        else if(down)batch.setColor(.78f,.78f,.78f,1f);
        else if(hover&&variant==base)batch.setColor(1f,1f,.88f,1f);
        else batch.setColor(1f,1f,1f,1f);
        float inset=down?2f:0f;
        tiles[variant].draw(batch,x+inset,y+inset,
                width-2f*inset,height-2f*inset);
        batch.setPackedColor(color);
    }

    void draw(SpriteBatch batch,int type,float x,float y,float width,float height){
        if(texture==null||type<0||type>=TILES||width<65||height<25)return;
        tiles[type].draw(batch,x,y,width,height);
    }

    void dispose(){
        if(texture!=null)texture.dispose();
        texture=null;
    }
}
