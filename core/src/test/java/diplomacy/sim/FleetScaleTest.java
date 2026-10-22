package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Repeatable representative 200-capital ship simulation; reports timing, not a guaranteed FPS. */
public final class FleetScaleTest {
    private static void check(boolean assertion,String text){if(!assertion)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        var left=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE);
        var right=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.FRIGATE);
        long first=0;
        long elapsed=0;
        for(int trial=0;trial<2;trial++){
            Battle battle=Battle.duel(777L,100,left,right,catalog);
            long before=System.nanoTime();
            for(int i=0;i<300;i++)battle.step();
            elapsed+=System.nanoTime()-before;
            check(battle.ships().size()==200,"200 individual ship instances deployed");
            check(battle.hitCount>0&&battle.ships().stream().anyMatch(ship->ship.launched>0),"active large-fleet engagement");
            for(var ship:battle.ships()){
                check(Double.isFinite(ship.x)&&Double.isFinite(ship.y),"finite ship position");
                check(ship.hull>=0&&ship.power>=0&&ship.shields>=0,"bounded combat components");
            }
            if(trial==0)first=battle.fingerprint();
            else check(battle.fingerprint()==first,"large fleet replay must remain deterministic");
        }
        System.out.println("PASS 200-ship headless scale | 2 x 300 ticks | combined-ms="
                + String.format(java.util.Locale.ROOT,"%.1f",elapsed/1e6)
                + " | fingerprint="+Long.toHexString(first));
    }
}
