package diplomacy.client;

/** Headless shader interface contract; GL compilation is exercised by emulator. */
public final class ShipSurfaceShaderTest {
    private ShipSurfaceShaderTest(){}
    public static void main(String[] args){
        String n=ShipSurfaceLighting.normalPath("ships/factions/umbra_foundry/battlecruiser.png");
        String e=ShipSurfaceLighting.emissivePath("ships/legacy_factions/draxian/cruiser.png");
        if(!n.equals("ships/factions/umbra_foundry/battlecruiser_n.png")||
                !e.equals("ships/legacy_factions/draxian/cruiser_e.png"))
            throw new AssertionError("UV-matched material path mapping");
        for(String token:new String[]{"uniform sampler2D u_normal;",
                "uniform sampler2D u_emission;","vec2 axis=normalize(v_color.rg",
                "vec3 emitted=texture2D(u_emission,v_uv).rgb*v_color.b",
                "albedo.a*=v_color.a;","vec3 n=normalize(texture2D(u_normal",
                "gl_FragColor=vec4(min(lit,vec3(1.0)),albedo.a)"})
            if(!ShipSurfaceLighting.FRAGMENT.contains(token))
                throw new AssertionError("Missing real surface shader input: "+token);
        if(ShipSurfaceLighting.FRAGMENT.contains("gl_FragColor=vec4(1.0"))
            throw new AssertionError("Flat placeholder shader");
        CombatViewBounds far=new CombatViewBounds(0,0,4100,800);
        CombatViewBounds close=new CombatViewBounds(0,0,900,700);
        if(!ShipSurfaceLighting.flatAt(far)||ShipSurfaceLighting.flatAt(close))
            throw new AssertionError("Strategic flat pass not selected correctly");
        System.out.println("PASS: normal/emissive UVs, shader alpha, strategic unlit path");
    }
}
