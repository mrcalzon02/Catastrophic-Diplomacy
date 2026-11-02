package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.tech.TechnologyCatalog;

/** Nonblocking objective provenance check for the screenshot gallery. */
public final class ShipSurfaceDamageCaptureTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        check(ShipSurfaceDamageCapture.KEYS.length>=8,"Missing review scenarios");
        for(int i=0;i<ShipSurfaceDamageCapture.KEYS.length;i++){
            var scene=ShipSurfaceDamageCapture.create(tech,i);
            check(scene.target()!=null,"Missing actual ship "+scene.key());
            check(scene.battle().ships().size()>=2,"No combat scene "+scene.key());
            check(scene.target().hullClass!=null,"No approved hull class "+scene.key());
            if(i==0)check(scene.ticks()==0&&scene.hits()==0,
                    "Pristine reference is already battle-damaged");
            if(i>1&&i<ShipSurfaceDamageCapture.KEYS.length-1)
                check(scene.ticks()>0,"Live-fire scenario never simulated "+scene.key());
            if(i==ShipSurfaceDamageCapture.KEYS.length-1)
                check(scene.syntheticStress(),"Rectangle must be labeled as artificial stress");
            System.out.printf(java.util.Locale.ROOT,
                    "ARMOR-CASE %s weapon=%s seed=%d tick=%d hits=%d armor=%.4f internal=%.4f worn=%d exposed=%d milestone=%s stress=%s%n",
                    scene.key(),scene.weapon(),scene.seed(),scene.ticks(),scene.hits(),
                    scene.armorExposure(),scene.internalExposure(),scene.wornCells(),
                    scene.exposedCells(),scene.milestoneReached(),scene.syntheticStress());
        }
        var a=ShipSurfaceDamageCapture.create(tech,2);
        var b=ShipSurfaceDamageCapture.create(tech,2);
        check(a.ticks()==b.ticks()&&a.hits()==b.hits()&&
                Double.compare(a.armorExposure(),b.armorExposure())==0,
                "Seeded armor replay is not deterministic");
        System.out.println("PASS seeded fitted live-fire scenarios plus independently labeled grid stress");
    }
}
