package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** One GLES2-compatible source-art recolor for all shield generator families. */
final class ShieldMaterialShader implements Disposable {
    // Keep a half-texel clamp exact on the packed 386x484 sheet. ES2
    // devices without highp fragment interpolation use the original material.
    private static final String ATLAS_PRECISION = """
            #ifdef GL_ES
            #define ATLAS_COORD highp
            #else
            #define ATLAS_COORD
            #endif
            """;
    private static final String VERTEX_LAYOUT="""
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_uv;
            """;
    private static final String VERTEX_BODY="""
            void main(){
                v_color=a_color;
                v_color.a*=255.0/254.0;
                gl_Position=u_projTrans*a_position;
            """;
    static final String VERTEX=VERTEX_LAYOUT+VERTEX_BODY+"v_uv=a_texCoord0;}";
    // Keep scan UV in the original 0..1 space. Compute atlas placement per
    // vertex and clamp per fragment to reproduce each original texture edge.
    static final String LAYER_VERTEX=ATLAS_PRECISION+VERTEX_LAYOUT+
            "varying ATLAS_COORD vec2 v_atlas; varying ATLAS_COORD vec4 v_limits;\n"+VERTEX_BODY+"""
                float layer=floor(a_texCoord0.x*0.5);
                v_uv=vec2(a_texCoord0.x-2.0*layer,a_texCoord0.y);
                float face=mod(layer,4.0);
                vec2 size=vec2(192.0,layer<4.0 ? 192.0 : 288.0);
                vec2 first=vec2(0.0), span=size;
                vec2 origin=vec2(0.0,layer<4.0 ? 0.0 : 194.0);
                if(face<0.5){
                    span.y=size.y*0.5+1.0;
                }else if(face<1.5){
                    origin.x=192.0;
                    span.x=97.0;
                }else if(face<2.5){
                    origin.y+=size.y*0.5+1.0;
                    first.y=size.y*0.5-1.0;
                    span.y=size.y*0.5+1.0;
                }else{
                    origin.x=289.0;
                    first.x=95.0;
                    span.x=97.0;
                }
                vec2 sheet=vec2(386.0,484.0);
                v_atlas=(origin+v_uv*size-first)/sheet;
                v_limits=vec4((origin+0.5)/sheet,(origin+span-0.5)/sheet);
            }
            """;
    private static final String FRAGMENT_LAYOUT="""
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_uv;
            uniform sampler2D u_texture;
            uniform float u_scan;
            uniform float u_tick;
            """;
    private static final String SHADE="""
                float pattern=max(source.r,max(source.g,source.b));
                vec3 color=v_color.rgb*(0.43+0.73*pattern);
                float alpha=source.a*v_color.a;
                if(u_scan>0.5){
                    float wave=fract(v_uv.x*.92+v_uv.y*.38-u_tick*.014);
                    float lit=1.0-smoothstep(0.0,.23,abs(wave-.5));
                    alpha*=lit;color=min(vec3(1.0),color*1.35);
                }
                gl_FragColor=vec4(min(vec3(1.0),color),alpha);
            }
            """;
    static final String FRAGMENT=FRAGMENT_LAYOUT+
            "void main(){vec4 source=texture2D(u_texture,v_uv);\n"+SHADE;
    static final String LAYER_FRAGMENT=ATLAS_PRECISION+FRAGMENT_LAYOUT+"""
            varying ATLAS_COORD vec2 v_atlas;
            varying ATLAS_COORD vec4 v_limits;
            void main(){
                vec4 source=texture2D(u_texture,clamp(v_atlas,v_limits.xy,v_limits.zw));
            """+SHADE;
    private final ShaderProgram program;
    private final boolean batched;

    ShieldMaterialShader(){
        ShaderProgram candidate=new ShaderProgram(LAYER_VERTEX,LAYER_FRAGMENT);
        batched=candidate.isCompiled();
        if(!batched){
            Gdx.app.log("SHIELD-FX","Using separate shield textures: "+candidate.getLog());
            candidate.dispose();
            candidate=new ShaderProgram(VERTEX,FRAGMENT);
        }
        program=candidate;
        if(!program.isCompiled()){
            String log=program.getLog();program.dispose();
            throw new IllegalStateException("Shield material shader compile: "+log);
        }
    }
    ShaderProgram program(){return program;}
    void configure(boolean scan,long tick){
        program.setUniformi("u_texture",0);
        program.setUniformf("u_scan",scan?1f:0f);
        program.setUniformf("u_tick",(float)(tick%65536));
    }
    boolean usesAtlas(){return batched;}
    public void dispose(){program.dispose();}
}
