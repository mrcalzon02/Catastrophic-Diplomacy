package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * Live 1v1 observational diagnostic of whether armor breaches are ever seen
 * on a surviving vessel before destruction. Never changes HP or combat stats.
 * This is NOT a pass/fail visual or balance test and requires no GPU device.
 */
public final class ArmorBreachTelemetry {
    private static final int LIMIT=450;
    private ArmorBreachTelemetry(){}

    private static double maxExposure(Battle.Ship ship){
        double maximum=0;
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++){
                var tile=ship.defense.tile(row,col);
                if(tile.occupied)maximum=Math.max(maximum,tile.exposure());
            }
        return maximum;
    }
    private static int breaching(Battle.Ship ship){
        int count=0;
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++){
                var tile=ship.defense.tile(row,col);
                if(tile.occupied&&tile.exposure()>=.90)count++;
            }
        return count;
    }

    public static void main(String[] args){
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        int all=0,aliveBreach=0,diedBeforeBreach=0,diedAfterBreach=0;
        System.out.println("ARMOR-READABILITY: real-fitted seeded duels, "+LIMIT+
                " simulated ticks maximum each. Physical alpha-opening threshold >= 90% local armor loss.");
        System.out.println("HULL, TEAM, MAX_HULL, MAX_ARMOR, FIRST_LIVE_BREACH_TICK, DEATH_TICK, MAX_EXPOSURE, PEAK_BREACH_CELLS");
        for(int size=0;size<HullCoverageBattle.SIZES.size();size++){
            Shipyard.Hull hull=HullCoverageBattle.SIZES.get(size);
            Battle battle=HullCoverageBattle.duel(technology,size);
            int[] first={-1,-1},dead={-1,-1},peak={0,0};
            double[] exposure={0,0};
            for(int tick=1;tick<=LIMIT;tick++){
                battle.step();
                for(Battle.Ship ship:battle.ships()){
                    int team=ship.team;
                    if(team<0||team>1)continue;
                    double highest=maxExposure(ship);
                    exposure[team]=Math.max(exposure[team],highest);
                    peak[team]=Math.max(peak[team],breaching(ship));
                    if(ship.alive()&&highest>=.90&&first[team]<0)
                        first[team]=tick;
                    if(!ship.alive()&&dead[team]<0)dead[team]=tick;
                }
                if(dead[0]>=0&&dead[1]>=0)break;
            }
            for(Battle.Ship ship:battle.ships()){
                int team=ship.team;
                if(team<0||team>1)continue;
                all++;
                if(first[team]>=0)aliveBreach++;
                if(dead[team]>=0){
                    if(first[team]<0)diedBeforeBreach++;
                    else diedAfterBreach++;
                }
                System.out.printf(java.util.Locale.ROOT,
                        "%s,%d,%.2f,%.2f,%d,%d,%.4f,%d%n",
                        hull,team,ship.maxHull,ship.maxArmor,first[team],
                        dead[team],exposure[team],peak[team]);
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "SUMMARY hulls=%d aliveWithVisibleThreshold=%d deathsBeforeBreach=%d deathsAfterBreach=%d%n",
                all,aliveBreach,diedBeforeBreach,diedAfterBreach);
        System.out.println("NOTE: Headless mechanical observability only. Real framebuffer artwork and timing still require inspection.");
    }
}
