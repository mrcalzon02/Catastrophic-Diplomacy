package diplomacy.sim;

import java.util.HashMap;
import java.util.Map;

/** Real beam intersections, glancing hits, misses and authoritative launch rays. */
public final class BeamRayGeometryTest {
    private BeamRayGeometryTest(){}
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    private static void near(double a,double b,String message){
        require(StrictMath.abs(a-b)<1e-5,message+" "+a+" != "+b);
    }
    private static void geometry(){
        var ray=BeamRayGeometry.aim(0,0,50,0,100);
        near(ray.x2(),100,"Beam does not extend to real range");
        near(ray.y2(),0,"Beam changed commanded bearing");
        var tangent=BeamRayGeometry.intersect(ray,50,10,10);
        require(tangent!=null,"Actual hull-rim graze did not collide");
        near(tangent.x(),50,"Tangent contact entered at wrong physical point");
        require(BeamRayGeometry.intersect(ray,50,10.01,10)==null,
                "Beam near-miss erroneously inflicted damage");
        require(BeamRayGeometry.intersect(ray,150,0,10)==null,
                "A beam exceeded its finite mounted range");
        var first=BeamRayGeometry.intersect(ray,40,0,8);
        var second=BeamRayGeometry.intersect(ray,70,0,8);
        require(first!=null&&second!=null&&first.fraction()<second.fraction(),
                "Ray failed first-hull occlusion ordering");
        var slightlyWrong=BeamRayGeometry.aim(0,0,100,2,100);
        require(BeamRayGeometry.intersect(slightlyWrong,60,0,5)!=null,
                "Slightly inaccurate bearing cannot hit a physical hull");
        var off=BeamRayGeometry.aim(0,0,100,30,100);
        require(BeamRayGeometry.intersect(off,60,0,5)==null,
                "A significantly wrong bearing automatically hit anyway");
        System.out.println("PASS physical beam grazing, bounded reach, occlusion and imperfect-aim hit/miss");
    }
    private static void live(Battle.DamageType type,long seed){
        Battle b=new Battle(seed);
        Battle.Ship gun=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship victim=b.addShip(1,Battle.Faction.DRAXIAN,260,100);
        // Only the receiving dummy is NO-AI. Registering the gun ship as
        // diagnostic-stationary suppresses its entire captain/fire loop.
        b.registerDiagnosticStationaryTarget(victim.id);
        gun.speed=0; // Shooter stays nearby but still runs real fire control.
        gun.power=100;gun.powerRegen=100;
        // Saturate the legacy lock roll to isolate physical beam geometry
        // from unrelated stochastic hit-chance and crew balance tuning.
        gun.accuracy=3.0;
        gun.sensorRange=500;gun.infraredRange=gun.xrayRange=gun.graviticRange=0;
        gun.laserDamage=type==Battle.DamageType.LASER?12:0;
        gun.plasmaDamage=type==Battle.DamageType.PLASMA?12:0;
        gun.particleDamage=type==Battle.DamageType.PARTICLE?12:0;
        gun.kineticDamage=gun.missileDamage=gun.torpedoDamage=0;
        victim.laserDamage=victim.plasmaDamage=victim.particleDamage=0;
        victim.kineticDamage=victim.missileDamage=victim.torpedoDamage=0;
        double initialHull=victim.hull;
        for(int t=0;t<180;t++)b.step();
        Map<Long,Battle.VisualEvent> fires=new HashMap<>();
        int confirmed=0;
        for(Battle.VisualEvent e:b.visualEvents()){
            if(e.sourceId()!=gun.id||e.phase()!=Battle.VisualPhase.FIRE||
                    !e.weapon().startsWith(type.name()))continue;
            fires.put(e.shotId(),e);
        }
        for(Battle.VisualEvent event:b.visualEvents()){
            if(event.phase()!=Battle.VisualPhase.IMPACT||event.sourceId()!=gun.id||
                    !event.weapon().startsWith(type.name()))continue;
            var fire=fires.get(event.shotId());
            require(fire!=null,"Beam impact was not caused by an actual fired ray");
            require(event.tick()>fire.tick(),
                    "Main beam hit before light could leave its muzzle");
            // FIRE endpoint defines fixed finite-range bearing, not a
            // prophetic collision coordinate for the later moving target.
            double dx=fire.targetX()-fire.x();
            double dy=fire.targetY()-fire.y();
            double u=((event.x()-fire.x())*dx+
                    (event.y()-fire.y())*dy)/(dx*dx+dy*dy);
            require(u>=0&&u<=1,"Beam struck beyond mounted ray range");
            near(fire.x()+u*dx,event.x(),"Delayed impact left ray X");
            near(fire.y()+u*dy,event.y(),"Delayed impact left ray Y");
            double radius=12*victim.visualScale();
            require(StrictMath.hypot(event.x()-victim.x,event.y()-victim.y)<=radius+1e-4,
                    "Real beam impact fell outside the struck hull silhouette");
            confirmed++;
        }
        require(!fires.isEmpty()&&confirmed>0,
                "Installed "+type+" failed live beam trial: fire="+fires.size()+
                " impacts="+confirmed+" tick="+b.tick+" gunPower="+gun.power+
                " reload="+(type==Battle.DamageType.LASER?gun.laserReload:
                type==Battle.DamageType.PLASMA?gun.plasmaReload:gun.particleReload));
        require(victim.hull<=initialHull,
                "A physical beam collision increased target hull integrity");
        System.out.println("PASS live "+type+" physical beam fire/impact parity; "+
                fires.size()+" rays, "+confirmed+" hull intersections");
    }
    public static void main(String[] args){
        geometry();
        live(Battle.DamageType.LASER,90131);
        live(Battle.DamageType.PLASMA,90137);
        live(Battle.DamageType.PARTICLE,90139);
    }
}
