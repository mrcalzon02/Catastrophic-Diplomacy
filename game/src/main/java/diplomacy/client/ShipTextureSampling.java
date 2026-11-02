package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

/** Consistent source-art and normal/emissive sampling across GLES2 and GL3.
 * Some older Fire OS GLES2 devices cannot mipmap arbitrary non-power-of-two
 * PNGs; retain their exact source art using ordinary linear filtering. */
final class ShipTextureSampling {
    private ShipTextureSampling() { }
    static boolean mipmapsAvailable() {
        if(Gdx.app==null||Gdx.graphics==null)return false;
        if(Gdx.app.getType()!=com.badlogic.gdx.Application.ApplicationType.Android)
            return true;
        return Gdx.graphics.getGLVersion().getMajorVersion()>=3
                ||Gdx.graphics.supportsExtension("GL_OES_texture_npot");
    }
    static Texture load(String path) {
        boolean useMipmaps=mipmapsAvailable();
        Texture result=new Texture(Gdx.files.internal(path),useMipmaps);
        result.setFilter(useMipmaps?Texture.TextureFilter.MipMapLinearLinear:
                Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        return result;
    }
}
