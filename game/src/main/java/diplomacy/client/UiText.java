package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;

/** Shared, bounded button typography for every client screen. Render-thread only. */
final class UiText {
    private static final GlyphLayout layout=new GlyphLayout();
    private UiText() { }
    static BitmapFont font() {
        BitmapFont font=new BitmapFont(Gdx.files.internal("fonts/atkinson-17.fnt"));
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
        return font;
    }
    static void button(SpriteBatch batch,BitmapFont font,String text,
                       float x,float y,float width,float height,Color color) {
        float sx=font.getData().scaleX,sy=font.getData().scaleY;
        float r=font.getColor().r,g=font.getColor().g,b=font.getColor().b,a=font.getColor().a;
        try {
            float room=Math.max(1,width-24);
            layout.setText(font,text);
            float fit=Math.max(.86f,Math.min(1,room/Math.max(1,layout.width)));
            font.getData().setScale(sx*fit,sy*fit);
            layout.setText(font,text,0,text.length(),color,room,Align.left,false,"...");
            // Use measured ink width once. No right-aligned layout offsets.
            font.draw(batch,layout,x+(width-layout.width)*.5f,y+(height+layout.height)*.5f);
        } finally {
            font.getData().setScale(sx,sy);
            font.setColor(r,g,b,a);
        }
    }
}
