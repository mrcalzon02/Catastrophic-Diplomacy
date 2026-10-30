package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;

/** GPU-instanced native faction/class silhouettes. Sprite-equipped ships are excluded. */
public final class InstancedShipRenderer implements AutoCloseable {
    private final Mesh[][] hulls =
            new Mesh[Battle.Faction.values().length][Shipyard.Hull.values().length];
    private final ShaderProgram shader;
    private final float[] positions=new float[4096*4];
    public int drawCalls,instances;

    public InstancedShipRenderer() {
        if(Gdx.gl30==null)throw new IllegalStateException("OpenGL ES 3 / GL 3 unavailable");
        boolean mobile=Gdx.app.getType()==com.badlogic.gdx.Application.ApplicationType.Android;
        String vertex=mobile
                ? "#version 300 es\nprecision mediump float;\nin vec3 a_position;\nin vec4 a_instance;\nuniform mat4 u_proj;\nvoid main(){ vec2 p=vec2(a_position.x*a_instance.z-a_position.y*a_instance.w, a_position.x*a_instance.w+a_position.y*a_instance.z)+a_instance.xy; gl_Position=u_proj*vec4(p,0.0,1.0); }"
                : "#version 150\nin vec3 a_position;\nin vec4 a_instance;\nuniform mat4 u_proj;\nvoid main(){ vec2 p=vec2(a_position.x*a_instance.z-a_position.y*a_instance.w, a_position.x*a_instance.w+a_position.y*a_instance.z)+a_instance.xy; gl_Position=u_proj*vec4(p,0.0,1.0); }";
        String fragment=mobile
                ? "#version 300 es\nprecision mediump float;\nuniform vec4 u_color;\nout vec4 fragColor;\nvoid main(){ fragColor=u_color; }"
                : "#version 150\nuniform vec4 u_color;\nout vec4 fragColor;\nvoid main(){ fragColor=u_color; }";
        shader=new ShaderProgram(vertex,fragment);
        if(!shader.isCompiled()) {
            String why=shader.getLog();shader.dispose();throw new IllegalStateException(why);
        }
        try {
            for(Battle.Faction faction:Battle.Faction.values())
                for(Shipyard.Hull type:Shipyard.Hull.values()) {
                    if(type.spriteHull()!=type){
                        Mesh shared=hulls[faction.ordinal()][type.spriteHull().ordinal()];
                        if(shared==null)throw new IllegalStateException("Missing base art mesh for "+type);
                        hulls[faction.ordinal()][type.ordinal()]=shared;
                        continue;
                    }
                    float[] pairs=HullSilhouette.of(faction,type);
                    float[] vertices=new float[pairs.length/2*3];
                    for(int i=0;i<pairs.length/2;i++) {
                        vertices[i*3]=pairs[i*2];
                        vertices[i*3+1]=pairs[i*2+1];
                        vertices[i*3+2]=0;
                    }
                    Mesh mesh=new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO,
                            true,vertices.length/3,0,VertexAttribute.Position());
                    mesh.setVertices(vertices);
                    hulls[faction.ordinal()][type.ordinal()]=mesh;
                    mesh.enableInstancedRendering(false,4096,
                            new VertexAttribute(VertexAttributes.Usage.Generic,4,"a_instance"));
                }
        }catch(RuntimeException broken) {
            close();
            throw broken;
        }
    }

    public void render(Battle battle,OrthographicCamera camera) {
        render(battle,camera,ship -> false);
    }
    /** Avoid duplicate geometry under an actual, successfully loaded sprite. */
    public void render(Battle battle,OrthographicCamera camera,
                       java.util.function.Predicate<Battle.Ship> hasArt) {
        render(battle,camera,hasArt,null);
    }
    public void render(Battle battle,OrthographicCamera camera,
                       java.util.function.Predicate<Battle.Ship> hasArt,
                       BattleRenderPoses poses) {
        drawCalls=0;instances=0;
        CombatViewBounds view=CombatViewBounds.from(camera);
        shader.bind();
        shader.setUniformMatrix("u_proj",camera.combined);
        for(Battle.Faction faction:Battle.Faction.values())
            for(Shipyard.Hull hull:Shipyard.Hull.values()) {
                int n=0;
                for(Battle.Ship ship:battle.ships()) {
                    Shipyard.Hull type=ship.hullClass==null?Shipyard.Hull.CORVETTE:ship.hullClass;
                    if(!ship.alive()||ship.faction!=faction||type!=hull||hasArt.test(ship))continue;
                    if(n>=4096)throw new IllegalStateException("Hull instance batch exceeded 4096");
                    BattleRenderPoses.Pose pose=poses==null?
                            new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                            poses.ship(ship);
                    if(!view.ship(pose,ship.visualScale()))continue;
                    int offset=n++*4;
                    positions[offset]=pose.x();
                    positions[offset+1]=pose.y();
                    float scale=(float)ship.visualScale();
                    // Native shipyard outlines point +Y; combat heading zero points +X.
                    positions[offset+2]=(float)Math.sin(pose.angle())*scale;
                    positions[offset+3]=(float)-Math.cos(pose.angle())*scale;
                }
                if(n==0)continue;
                Mesh mesh=hulls[faction.ordinal()][hull.ordinal()];
                mesh.setInstanceData(positions,0,n*4);
                shader.setUniformf("u_color",faction.r*hull.tintRed(),
                        faction.g*hull.tintGreen(),faction.b*hull.tintBlue(),1f);
                mesh.render(shader,GL20.GL_TRIANGLES);
                drawCalls++;instances+=n;
            }
    }

    @Override public void close() {
        // Chassis variants deliberately alias base-category meshes.
        java.util.Set<Mesh> unique=java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Mesh,Boolean>());
        for(Mesh[] row:hulls)for(Mesh mesh:row)
            if(mesh!=null&&unique.add(mesh))mesh.dispose();
        shader.dispose();
    }
}
