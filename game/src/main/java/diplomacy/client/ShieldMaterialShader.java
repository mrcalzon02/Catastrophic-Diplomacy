package diplomacy.client;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** One GLES2-compatible source-art recolor for all shield generator families. */
final class ShieldMaterialShader implements Disposable {
    static final String VERTEX=
            "attribute vec4 a_position;\n"+
            "attribute vec4 a_color;\n"+
            "attribute vec2 a_texCoord0;\n"+
            "uniform mat4 u_projTrans;\n"+
            "varying vec4 v_color;\n"+
            "varying vec2 v_uv;\n"+
            "void main(){v_color=a_color;v_color.a*=255.0/254.0;"+
            "v_uv=a_texCoord0;gl_Position=u_projTrans*a_position;}\n";
    static final String FRAGMENT=
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n"+
            "varying vec4 v_color;\n"+
            "varying vec2 v_uv;\n"+
            "uniform sampler2D u_texture;\n"+
            "uniform float u_scan;\n"+
            "uniform float u_tick;\n"+
            "void main(){\n"+
            " vec4 source=texture2D(u_texture,v_uv);\n"+
            " float pattern=max(source.r,max(source.g,source.b));\n"+
            " vec3 color=v_color.rgb*(0.43+0.73*pattern);\n"+
            " float alpha=source.a*v_color.a;\n"+
            " if(u_scan>0.5){\n"+
            "   float wave=fract(v_uv.x*.92+v_uv.y*.38-u_tick*.014);\n"+
            "   float lit=1.0-smoothstep(0.0,.23,abs(wave-.5));\n"+
            "   alpha*=lit;color=min(vec3(1.0),color*1.35);\n"+
            " }\n"+
            " gl_FragColor=vec4(min(vec3(1.0),color),alpha);\n"+
            "}\n";
    private final ShaderProgram program;
    ShieldMaterialShader(){
        program=new ShaderProgram(VERTEX,FRAGMENT);
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
    public void dispose(){program.dispose();}
}
