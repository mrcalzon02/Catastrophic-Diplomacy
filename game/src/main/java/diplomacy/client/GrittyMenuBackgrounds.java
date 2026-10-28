package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Existing, textless 1280x720 art with constant-memory, one-texture loading. */
final class GrittyMenuBackgrounds {
    private static final String PREFIX="ui/gritty/backgrounds/";
    private String attempted;
    private Texture texture;

    static String nameFor(GameMenuPanel.Screen screen){
        return switch(screen){
            case HOME, CAMPAIGN_SETUP, DOCK, NAME_THEMES, OPTIONS, RESULTS ->
                    "blank_armor_bulkhead";
            case CAMPAIGN, SQUADRONS, RESEARCH, DEPLOYMENT, SHIP_ORDERS -> "armored_interface";
            case SKIRMISH -> "fleet_approach";
            case BATTLE -> null;
        };
    }
    boolean prepare(GameMenuPanel.Screen screen){return prepareNamed(nameFor(screen));}
    boolean prepareNamed(String name){
        if(name==null){dispose();return false;}
        if(name.equals(attempted))return texture!=null;
        dispose(); attempted=name;
        try{
            var file=Gdx.files.internal(PREFIX+name+".jpg");
            if(!file.exists())return false;
            texture=new Texture(file);
            if(texture.getWidth()!=1280||texture.getHeight()!=720)
                throw new IllegalArgumentException("Unexpected menu background dimensions");
            texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        }catch(RuntimeException failure){
            if(texture!=null)texture.dispose();
            texture=null;
            if(Gdx.app!=null)Gdx.app.error("CD-UI","Background unavailable: "+name,failure);
        }
        return texture!=null;
    }
    void draw(SpriteBatch batch){
        if(texture==null)return;
        float previous=batch.getPackedColor();
        float intensity="fleet_approach".equals(attempted)?0.29f:
                "shipyard_hangar".equals(attempted)?0.34f:0.45f;
        batch.setColor(intensity,intensity,intensity,1f);
        batch.draw(texture,0,0,1280,720);
        batch.setPackedColor(previous);
    }
    void dispose(){
        if(texture!=null)texture.dispose();
        texture=null;attempted=null;
    }
}
