package diplomacy.client;

import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import diplomacy.tech.TechnologyCatalog;

/**
 * Debug-only real rendering acceptance fixture: four independently fitted
 * approved ships whose armor states are produced by actual directional impacts.
 * Never launches a second fake damage simulation or edits normal game modes.
 */
final class ShipSurfaceDamageFixture {
    private ShipSurfaceDamageFixture() {}

    static Battle create(TechnologyCatalog technology) {
        Battle battle=new Battle(0x5355524641434544L);
        Shipyard.Design design=Shipyard.starter(Battle.Faction.VAERITH_PRISM,
                Shipyard.Hull.DREADNOUGHT);
        if(!Shipyard.inspect(design,technology).valid())
            throw new IllegalStateException("Damaged-hull review needs legal fitted dreadnought");
        double[] positions={200,490,780,1070};
        for(int stage=0;stage<positions.length;stage++){
            battle.spawnWing(0,design,technology,1,positions[stage],360);
            Battle.Ship ship=battle.ships().get(stage);
            ship.x=positions[stage];ship.y=360;ship.facing=Math.PI/2;
            ship.name=new String[]{"INTACT","ARMOR SCORED","EXPOSED METAL","PENETRATED"}[stage];
            // Suppress shield overlays equally in each comparison column.
            ship.defense.disruptShields(ship.maxShields);
            if(stage>0){
                double[] depth={0,.38,.90,1.25};
                double hullDamage=0;
                for(int row=4;row<=9;row++)for(int col=5;col<=10;col++){
                    DirectionalDefense.Tile tile=ship.defense.tile(row,col);
                    if(!tile.occupied)continue;
                    double localX=1-(row+.5)*2.0/DirectionalDefense.GRID;
                    double localY=1-(col+.5)*2.0/DirectionalDefense.GRID;
                    double factor=ship.defense.damageFactor(DirectionalDefense.Layer.ARMOR,
                            tile.facing,Battle.DamageType.KINETIC);
                    double energy=tile.maxArmor()/factor*depth[stage];
                    var hit=ship.defense.impact(1,0,localX,localY,energy,Battle.DamageType.KINETIC);
                    hullDamage+=hit.hullDamage();
                }
                ship.hull=Math.max(1,ship.hull-hullDamage);
            }
            ship.shields=ship.defense.totalShields();
            ship.armor=ship.defense.totalArmor();
            if(stage<=2 && ship.hull!=ship.maxHull)
                throw new IllegalStateException("Unpenetrated armor fixture lost hull health");
            if(stage==3 && ship.hull>=ship.maxHull)
                throw new IllegalStateException("Penetration fixture did not reach structure");
        }
        return battle;
    }
}
