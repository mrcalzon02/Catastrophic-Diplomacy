package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.LevelSky;

/**
 * GPU-colored, procedurally generated background plus three world-anchored parallax
 * star layers. No level-specific image files or simulation-RNG consumption.
 * Draw before ships using the stable HUD camera, even while battle camera moves.
 */
public final class ProceduralStarfield implements Disposable {
    private static final int NOISE_SIZE = 256;
    private static final float CELL = 512f;
    private static final float[] PARALLAX = {0.025f, 0.10f, 0.23f};
    private static final int[] STARS_PER_CELL = {44, 23, 9};

    // ShaderProgram uses the ES2-compatible SpriteBatch attribute contract.
    private static final String VERTEX = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a *= 255.0 / 254.0;
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;
    private static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            uniform vec2 u_offset;
            uniform vec2 u_pan;
            uniform vec3 u_primary;
            uniform vec3 u_secondary;
            uniform vec4 u_shape; // angle, opacity, scale, type index
            uniform float u_zoom;
            void main() {
                vec2 centered = v_texCoords - vec2(0.5);
                vec2 p = (centered * u_zoom + u_pan * 0.00018) * u_shape.z;
                vec4 a = texture2D(u_texture, p * 0.65 + u_offset);
                vec4 b = texture2D(u_texture, p * 1.35 + u_offset * 0.73 + vec2(0.21, 0.14));
                float n = 0.60 * a.r + 0.40 * b.g;
                float wisps = 0.65 * a.g + 0.35 * b.b;
                vec2 normal = vec2(cos(u_shape.x), sin(u_shape.x));
                float dist = abs(dot(centered, normal) + (wisps - 0.5) * 0.32);
                float band = 1.0 - smoothstep(0.10, 0.55, dist);
                float cloud = smoothstep(0.34, 0.72, n);
                float mode = u_shape.w;
                if (mode > 0.5 && mode < 1.5) cloud *= 0.28 + 0.95 * band; // dust lane
                if (mode > 1.5 && mode < 2.5) { // galactic core and dusty spiral
                    float r = length(centered * vec2(1.0, 1.6));
                    cloud = 0.35 * cloud * band + (1.0 - smoothstep(0.04, 0.51, r)) * 0.70;
                }
                if (mode > 2.5 && mode < 3.5) cloud *= 0.22; // deep void
                if (mode > 3.5 && mode < 4.5) cloud *= 0.65; // clustered stars, quiet gas
                if (mode > 4.5) cloud = pow(cloud, 1.4) * (0.45 + 0.9 * wisps); // ion storm
                float glow = clamp(cloud * u_shape.y, 0.0, 0.75);
                vec3 gas = mix(u_primary, u_secondary, smoothstep(0.25, 0.85, wisps));
                vec3 voidColor = vec3(0.007, 0.012, 0.028);
                gl_FragColor = vec4(voidColor + gas * glow * 0.84, 1.0) * v_color;
            }
            """;

    private final Texture pixel;
    private final Texture glow;
    private ShaderProgram nebulaShader;
    private Texture noise;
    private LevelSky.Recipe recipe;

    public ProceduralStarfield() {
        pixel = makePixel();
        glow = makeGlow();
        ShaderProgram candidate = new ShaderProgram(VERTEX, FRAGMENT);
        if (candidate.isCompiled()) nebulaShader = candidate;
        else {
            Gdx.app.error("SKY", "Nebula shader unavailable; star-only fallback: " + candidate.getLog());
            candidate.dispose();
        }
    }

    /** Rebuilds just one 256x256 RGBA field when the selected level changes. */
    public void useLevel(int index) {
        LevelSky.Recipe target = LevelSky.forLevel(index);
        if (target.equals(recipe)) return;
        Texture next = makeNoise(target.seed());
        if (noise != null) noise.dispose();
        noise = next;
        recipe = target;
    }

    public LevelSky.Recipe recipe() { return recipe; }

    public void render(SpriteBatch batch, OrthographicCamera hudCamera,
                       OrthographicCamera battleCamera, float width, float height) {
        if (recipe == null) return;
        batch.setProjectionMatrix(hudCamera.combined);
        if (nebulaShader != null && noise != null) {
            batch.setShader(nebulaShader);
            batch.begin();
            float panX = battleCamera.position.x - width * 0.5f;
            float panY = battleCamera.position.y - height * 0.5f;
            float offsetX = (float)LevelSky.unit(LevelSky.mix(recipe.seed() + 101));
            float offsetY = (float)LevelSky.unit(LevelSky.mix(recipe.seed() + 102));
            nebulaShader.setUniformf("u_offset", offsetX, offsetY);
            nebulaShader.setUniformf("u_pan", panX * 0.035f, panY * 0.035f);
            nebulaShader.setUniformf("u_primary", recipe.primary().red(), recipe.primary().green(), recipe.primary().blue());
            nebulaShader.setUniformf("u_secondary", recipe.secondary().red(), recipe.secondary().green(), recipe.secondary().blue());
            nebulaShader.setUniformf("u_shape", recipe.angle(), recipe.nebulaIntensity(),
                    recipe.cloudScale(), (float)recipe.formation().ordinal());
            nebulaShader.setUniformf("u_zoom", battleCamera.zoom);
            batch.setColor(1, 1, 1, 1);
            batch.draw(noise, 0, 0, width, height);
            batch.end();
            batch.setShader(null);
        }
        // CPU work scales with the viewport, not with 500 levels or world size.
        batch.begin();
        for (int layer = 0; layer < 3; layer++) drawStars(batch, battleCamera, width, height, layer);
        batch.setColor(1, 1, 1, 1);
        batch.end();
    }

    private void drawStars(SpriteBatch batch, OrthographicCamera camera,
                           float width, float height, int layer) {
        float zoom = Math.max(0.05f, camera.zoom);
        float centerX = (camera.position.x - width * 0.5f) * PARALLAX[layer];
        float centerY = (camera.position.y - height * 0.5f) * PARALLAX[layer];
        float halfW = width * 0.5f * zoom, halfH = height * 0.5f * zoom;
        int left = (int)Math.floor((centerX - halfW) / CELL);
        int right = (int)Math.floor((centerX + halfW) / CELL);
        int bottom = (int)Math.floor((centerY - halfH) / CELL);
        int top = (int)Math.floor((centerY + halfH) / CELL);
        int count = Math.max(1, Math.round(STARS_PER_CELL[layer] * recipe.starDensity()));
        // A conservative guard against pathological camera zoom on mobile devices.
        if ((long)(right-left+1)*(top-bottom+1) > 200) return;
        for (int cy = bottom; cy <= top; cy++) for (int cx = left; cx <= right; cx++) {
            long seed = LevelSky.mix(recipe.seed() ^ (long)(layer+1)*0x9e3779b97f4a7c15L
                    ^ (long)cx*0xbf58476d1ce4e5b9L ^ (long)cy*0x94d049bb133111ebL);
            for (int s = 0; s < count; s++) {
                long h = LevelSky.mix(seed + (long)s*0x9e3779b97f4a7c15L);
                float wx = (cx + (float)LevelSky.unit(h)) * CELL;
                float wy = (cy + (float)LevelSky.unit(LevelSky.mix(h + 17))) * CELL;
                float x = (wx - centerX) / zoom + width * 0.5f;
                float y = (wy - centerY) / zoom + height * 0.5f;
                if (x < -12 || x > width + 12 || y < -12 || y > height + 12) continue;
                float variance = (float)LevelSky.unit(LevelSky.mix(h + 29));
                float alpha = (0.24f + variance * 0.62f) * (layer==0 ? 0.50f : layer==1 ? 0.74f : 1f);
                float radius = (layer==0 ? .65f : layer==1 ? 1.0f : 1.4f) * (.6f + variance);
                boolean warm = (h & 15L) == 0;
                batch.setColor(warm ? 1f : .78f, warm ? .81f : .90f, 1f, alpha);
                batch.draw(pixel, x-radius*0.5f, y-radius*0.5f, radius, radius);
                if (layer > 0 && variance > .94f) {
                    batch.setColor(warm ? 1f : .42f, warm ? .75f : .7f, 1f, alpha*.33f);
                    batch.draw(glow, x-6, y-6, 12, 12);
                }
            }
        }
    }

    private static Texture makePixel() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(1, 1, 1, 1); p.fill();
        Texture t = new Texture(p); p.dispose();
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }
    private static Texture makeGlow() {
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
            float r = (float)Math.sqrt((x-7.5f)*(x-7.5f)+(y-7.5f)*(y-7.5f)) / 8f;
            float a = Math.max(0, 1-r);
            p.setColor(1,1,1,a*a*a); p.drawPixel(x,y);
        }
        Texture t = new Texture(p); p.dispose();
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return t;
    }
    private static Texture makeNoise(long seed) {
        Pixmap p = new Pixmap(NOISE_SIZE, NOISE_SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < NOISE_SIZE; y++) for (int x = 0; x < NOISE_SIZE; x++) {
            float a = .62f*valueNoise(x,y,3,seed)+.27f*valueNoise(x,y,7,seed+31)
                    +.11f*valueNoise(x,y,13,seed+67);
            float b = .53f*valueNoise(x,y,5,seed+113)+.34f*valueNoise(x,y,11,seed+151)
                    +.13f*valueNoise(x,y,19,seed+199);
            float c = .54f*valueNoise(x,y,9,seed+251)+.46f*valueNoise(x,y,21,seed+293);
            p.setColor(a, b, c, 1); p.drawPixel(x,y);
        }
        Texture texture = new Texture(p);
        p.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return texture;
    }
    private static float valueNoise(int x, int y, int period, long seed) {
        float px=x*(period/(float)NOISE_SIZE),py=y*(period/(float)NOISE_SIZE);
        int ix=(int)Math.floor(px),iy=(int)Math.floor(py);
        float fx=px-ix,fy=py-iy;
        fx=fx*fx*(3-2*fx); fy=fy*fy*(3-2*fy);
        float a=hash(ix,iy,period,seed),b=hash(ix+1,iy,period,seed);
        float c=hash(ix,iy+1,period,seed),d=hash(ix+1,iy+1,period,seed);
        return (a+(b-a)*fx)*(1-fy)+(c+(d-c)*fx)*fy;
    }
    private static float hash(int x, int y, int period, long seed) {
        long mixed = LevelSky.mix(seed ^ (long)(x%period)*0x9e3779b97f4a7c15L
                ^ (long)(y%period)*0xbf58476d1ce4e5b9L);
        return (float)LevelSky.unit(mixed);
    }
    @Override public void dispose() {
        if (noise != null) noise.dispose();
        if (nebulaShader != null) nebulaShader.dispose();
        pixel.dispose(); glow.dispose();
    }
}
