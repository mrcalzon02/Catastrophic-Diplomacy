package diplomacy.client;
/** Headless render-model regression; never needs an OpenGL context. */
public final class ShipLightProbesTest {
    private ShipLightProbesTest(){}
    static void near(float a,float b){
        if(Math.abs(a-b)>.001f)throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args){
        CombatViewBounds camera=new CombatViewBounds(0,0,800,500);
        ShipLightProbes p=new ShipLightProbes();
        p.reset(camera);
        p.offer(10,0,80,.40f,1,0,0);
        p.offer(1,0,90,.80f,0,1,0);
        p.offer(12000,0,90,1,0,0,1); // must not steal a visible light
        near(p.power(0),.8f);near(p.power(1),.4f);
        near(p.green(0),1f);near(p.red(1),1f);
        p.offer(5,0,80,.9f,0,0,1);
        near(p.power(0),.9f);near(p.power(1),.8f);
        p.reset(camera);
        near(p.power(0),0);near(p.power(1),0);
        // A distant flash cannot be spent as a ship-surface light when it
        // intersects no actual receiver. The same test covers a genuine nearby
        // hull, conservative cell edges and the unfiltered preview API.
        diplomacy.sim.Battle field=new diplomacy.sim.Battle(1944);
        field.addShip(0,diplomacy.sim.Battle.Faction.TERRAN,100,100);
        p.reset(camera);
        p.limitToReceivers(field);
        p.offer(350,190,25,.99f,0,0,1);
        near(p.power(0),0f);
        if(p.sourceRejected()!=1)throw new AssertionError("Empty-space light not culled");
        p.offer(110,110,75,.5f,1,0,0);
        near(p.power(0),.5f);
        if(p.sourceOffers()!=2)throw new AssertionError("Emitter count not tracked");
        p.reset(camera);
        p.offer(350,190,25,.7f,0,0,1);
        near(p.power(0),.7f); // unfiltered editor/previews remain functional
        // The receiver grid is independent of camera world coordinate origin.
        CombatViewBounds offset=new CombatViewBounds(15000,9000,800,500);
        field.ships().get(0).x=15020;field.ships().get(0).y=8980;
        p.reset(offset);
        p.limitToReceivers(field);
        p.offer(15020,8980,30,.8f,0,1,0);
        near(p.power(0),.8f);
        p.offer(15300,9170,20,.99f,1,0,0);
        near(p.power(0),.8f);
        // The same installed laser family must color its beam and nearby hull.
        for(diplomacy.sim.LaserFamilies.Type type:
                diplomacy.sim.LaserFamilies.Type.values()){
            p.reset(camera);
            String event=diplomacy.sim.LaserFamilies.eventWeapon(type);
            p.offerWeapon(12,5,100,.7f,event);
            com.badlogic.gdx.graphics.Color reference=CombatLightingRenderer.eventColor(event);
            near(p.red(0),reference.r);
            near(p.green(0),reference.g);
            near(p.blue(0),reference.b);
        }
        near(ShipLightProbes.detail(new CombatViewBounds(0,0,800,600)),1);
        near(ShipLightProbes.detail(new CombatViewBounds(0,0,4000,500)),0);
        near(ShipLightProbes.detail(new CombatViewBounds(0,0,2500,500)),.5f);
        int pixel=0xe0c0a090;
        if(ShipDamageScorch.darken(pixel,0,.4f)!=pixel)
            throw new AssertionError("Undamaged art changed");
        int damaged=ShipDamageScorch.darken(pixel,.8f,.3f);
        if((damaged&255)!=(pixel&255)||((damaged>>>24)&255)>=((pixel>>>24)&255))
            throw new AssertionError("Scorch failed original alpha/color contract");
        if(ShipDamageScorch.darken(0,.8f,.1f)!=0)
            throw new AssertionError("Transparent source pixel became visible");
        for(String token:new String[]{"varying vec2 v_world;","uniform vec4 u_light0;",
                "uniform vec4 u_light1;","uniform float u_detail;",
                "localLight(u_light0","localLight(u_light1","if(u_detail<0.01)"})
            if(!ShipSurfaceLighting.FRAGMENT.contains(token))
                throw new AssertionError("Local normal-light shader missing "+token);
        System.out.println("PASS: bounded local lights, distance LOD, alpha-safe scorch and shader");
    }
}
