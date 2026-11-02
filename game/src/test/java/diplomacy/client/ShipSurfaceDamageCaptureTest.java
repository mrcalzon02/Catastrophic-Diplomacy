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
            if(i!=8){
                check(scene.battle().isDiagnosticStationaryTarget(scene.targetId()),
                        "Live-fire target was not registered as non-AI: "+scene.key());
                check(scene.target().vx==0&&scene.target().vy==0,
                        "Diagnostic target is moving: "+scene.key());
                check(Double.compare(scene.target().facing,Math.PI/2)==0,
                        "Diagnostic target heading drifted: "+scene.key());
                check(!scene.target().withdrawing&&!scene.target().escaped&&
                        scene.target().lastTargetId==0,
                        "Diagnostic target autonomously navigated or chose a target");
            }
            if(i!=8){
                int impactMarks=0,changed=0;
                for(int row=0;row<diplomacy.sim.DirectionalDefense.GRID;row++)
                    for(int col=0;col<diplomacy.sim.DirectionalDefense.GRID;col++){
                        var tile=scene.target().defense.tile(row,col);
                        if(tile.directImpact())impactMarks++;
                        if(tile.exposure()>.01||tile.internalDamage()>.01)changed++;
                    }
                check(impactMarks<=scene.hits(),
                        "One real weapon hit MUST NOT generate many independent scorch craters: "+scene.key());
                if(i>1&&scene.hits()>0&&changed>0)
                    check(impactMarks>0,
                            "Actual armor damage lacks the original impact point: "+scene.key());
                System.out.printf("ARMOR-IMPACT-SITES %s hits=%d touched-tiles=%d marks=%d%n",
                        scene.key(),scene.hits(),changed,impactMarks);
            }
            if(i==0)check(scene.ticks()==0&&scene.hits()==0,
                    "Pristine reference is already battle-damaged");
            if(i>1&&i!=8)
                check(scene.ticks()>0,"Live-fire scenario never simulated "+scene.key());
            if(i==8)
                check(scene.syntheticStress(),"Rectangle must be labeled as artificial stress");
            if(i==3||i==12){
                check(scene.target().faction==Battle.Faction.TERRAN,
                    "Kinetic pockmark review must use real broad Terran plating");
            }
            if(i>=9){
                diplomacy.sim.Battle.DamageType expected=switch(i){
                    case 9 -> diplomacy.sim.Battle.DamageType.PLASMA;
                    case 10 -> diplomacy.sim.Battle.DamageType.PARTICLE;
                    case 11 -> diplomacy.sim.Battle.DamageType.TORPEDO;
                    default -> diplomacy.sim.Battle.DamageType.KINETIC;
                };
                boolean found=false;
                for(int row=0;row<16;row++)for(int col=0;col<16;col++){
                    var tile=scene.target().defense.tile(row,col);
                    if(tile.directImpact()&&tile.impactType()==expected)found=true;
                }
                if(i==9)check(scene.hits()>0,
                        "Plasma test must fire from within its genuine weapon range");
                if(scene.hits()>0&&scene.wornCells()>0)
                    check(found,"Fitted weapon used wrong surface profile: "+scene.key());
            }
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
        // A separately constructed test scene proves the switch is local and
        // never silently enabled for ordinary combat encounters.
        Battle ordinary=new Battle(202611010140L);
        check(!ordinary.isDiagnosticStationaryTarget(1),
                "Stationary diagnostic behavior leaked into normal battle");
        System.out.println("PASS seeded real-weapon impacts on fixed-heading no-AI targets; baseline battle unaffected");
    }
}
