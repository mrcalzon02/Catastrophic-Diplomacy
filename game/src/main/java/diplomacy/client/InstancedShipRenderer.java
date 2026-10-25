package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import diplomacy.sim.Battle;

/** OpenGL3 version of Malefic Veil's mesh-once / instance-attributes architecture.
 * One hull mesh is uploaded once, then per-faction transform arrays draw whole squadrons.
 * Zero dependency on Beckenburg/Malefic resources. Falls back in CatastrophicGame.
 */
public final class InstancedShipRenderer implements AutoCloseable {
    private final Mesh hull;
    private final ShaderProgram shader;
    private final float[] positions = new float[4096 * 4];
    public int drawCalls, instances;

    public InstancedShipRenderer() {
        if (Gdx.gl30 == null) throw new IllegalStateException("OpenGL ES 3 / GL 3 unavailable");
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        String vertex = mobile
                ? "#version 300 es\nprecision mediump float;\nin vec3 a_position;\nin vec4 a_instance;\nuniform mat4 u_proj;\nvoid main(){ vec2 p=vec2(a_position.x*a_instance.z-a_position.y*a_instance.w, a_position.x*a_instance.w+a_position.y*a_instance.z)+a_instance.xy; gl_Position=u_proj*vec4(p,0.0,1.0); }"
                : "#version 150\nin vec3 a_position;\nin vec4 a_instance;\nuniform mat4 u_proj;\nvoid main(){ vec2 p=vec2(a_position.x*a_instance.z-a_position.y*a_instance.w, a_position.x*a_instance.w+a_position.y*a_instance.z)+a_instance.xy; gl_Position=u_proj*vec4(p,0.0,1.0); }";
        String fragment = mobile
                ? "#version 300 es\nprecision mediump float;\nuniform vec4 u_color;\nout vec4 fragColor;\nvoid main(){ fragColor=u_color; }"
                : "#version 150\nuniform vec4 u_color;\nout vec4 fragColor;\nvoid main(){ fragColor=u_color; }";
        shader = new ShaderProgram(vertex, fragment);
        if (!shader.isCompiled()) { String failure = shader.getLog(); shader.dispose(); throw new IllegalStateException(failure); }
        hull = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, 9, 0, VertexAttribute.Position());
        // Independently authored 2D top-down arrow hull with two stabilizers.
        hull.setVertices(new float[]{
                13,0,0, -8,6,0, -4,0,0,
                13,0,0, -4,0,0, -8,-6,0,
                -4,0,0, -8,6,0, -8,-6,0
        });
        hull.enableInstancedRendering(false, 4096,
                new VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_instance"));
    }

    public void render(Battle battle, OrthographicCamera camera) {
        render(battle,camera,ship -> false);
    }
    /** Do not overdraw a vessel that already has an approved visible hull sprite. */
    public void render(Battle battle, OrthographicCamera camera,
                       java.util.function.Predicate<Battle.Ship> hasArt) {
        drawCalls = 0; instances = 0;
        shader.bind();
        shader.setUniformMatrix("u_proj", camera.combined);
        for (Battle.Faction faction : Battle.Faction.values()) {
            for (int team = 0; team < 2; team++) {
                int n = 0;
                for (Battle.Ship ship : battle.ships()) {
                    if (!ship.alive() || ship.faction != faction || ship.team != team
                            || hasArt.test(ship)) continue;
                    if (n >= 4096) { // Explicit limit, renderer falls back rather than dropping ships.
                        throw new IllegalStateException("Instance batch exceeded 4096 ships");
                    }
                    int offset = n++ * 4;
                    positions[offset] = (float) ship.x; positions[offset + 1] = (float) ship.y;
                    float scale=(float)ship.visualScale();
                    positions[offset + 2] = (float) Math.cos(ship.facing)*scale;
                    positions[offset + 3] = (float) Math.sin(ship.facing)*scale;
                }
                if (n == 0) continue;
                hull.setInstanceData(positions, 0, n * 4);
                shader.setUniformf("u_color", faction.r, faction.g, faction.b, 1f);
                hull.render(shader, GL20.GL_TRIANGLES);
                drawCalls++; instances += n;
            }
        }
    }

    @Override public void close() { hull.dispose(); shader.dispose(); }
}
