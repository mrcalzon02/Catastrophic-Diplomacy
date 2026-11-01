package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * An actual headless firing lane, not manually fabricated damage states:
 * one LASER-only attacker and a completely unarmed target. Repeat the same
 * seeded encounter with each fitted physical defensive material and bulkheads.
 * CSV measurements are observations, not balance/appearance pass thresholds.
 */
public final class ArmorLiveFireTrial {
    private ArmorLiveFireTrial(){}
    private static final int LIMIT=1800;
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static int slot(Shipyard.Design design,Shipyard.Mount mount){
        for(Shipyard.Slot s:design.placements())
            if(s.mount()==mount)return s.index();
        throw new IllegalStateException("Hull has no "+mount+" socket");
    }
    private static int breachedCells(Battle.Ship ship){
        int count=0;
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++){
                var tile=ship.defense.tile(row,col);
                if(tile.occupied&&tile.exposure()>.01)count++;
            }
        return count;
    }
    private static boolean hasInternalDamage(Battle.Ship ship){
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++){
                var tile=ship.defense.tile(row,col);
                if(tile.occupied&&tile.internalDamage()>.01)return true;
            }
        return false;
    }
    public static void main(String[] args)throws Exception{
        var catalog=TechnologyCatalog.loadDefault();
        var faction=Battle.Faction.TERRAN;
        var hull=Shipyard.Hull.CORVETTE;
        var base=Shipyard.bareHull(faction,hull);
        var shooter=base.install(slot(base,Shipyard.Mount.WEAPON),"BASE-TER-LAS");
        check(Shipyard.inspect(shooter,catalog).valid(),"Laser firing-lane fit invalid");
        System.out.println("ARMOR-LIVE-FIRE actual seeded Battle.step() laser versus bare unarmed hull");
        System.out.println("FIT,HITS,FIRST_ARMOR_TICK,FIRST_STRUCTURE_TICK,DEATH_TICK,END_TICK,FINAL_HULL,FRACTION_HULL,FINAL_ARMOR,BREACH_CELLS");
        for(String material:new String[]{"NONE","CMP","ABL","DEN","RAC","BLK"}){
            Shipyard.Design target=material.equals("NONE")?base:
                    base.install(slot(base,Shipyard.Mount.DEFENSE),"BASE-TER-"+material);
            check(Shipyard.inspect(target,catalog).valid(),"Unarmed target fit invalid: "+material);
            Battle battle=new Battle(20261031L);
            battle.spawnWing(0,shooter,catalog,1,380,360);
            battle.spawnWing(1,target,catalog,1,660,360);
            Battle.Ship gun=battle.ships().get(0),victim=battle.ships().get(1);
            check(gun.laserDamage>0&&gun.missileDamage==0&&gun.kineticDamage==0
                            &&gun.plasmaDamage==0&&gun.particleDamage==0&&gun.torpedoDamage==0,
                    "Attack vessel must only have ONE physical laser");
            check(victim.laserDamage==0&&victim.missileDamage==0&&victim.kineticDamage==0
                            &&victim.plasmaDamage==0&&victim.particleDamage==0&&victim.torpedoDamage==0
                            &&victim.pdRange==0&&victim.fighterReserve==0,
                    "Target must truly be unarmed, including fighters and point defense");
            // A target platform is stationary ONLY in this diagnostic;
            // all original fitted hull propulsion remains real elsewhere.
            victim.speed=0;victim.vx=0;victim.vy=0;
            int firstArmor=-1,firstStructure=-1,death=-1,lastHits=0;
            for(int t=0;t<LIMIT&&!battle.finished();t++){
                battle.step();
                if(breachedCells(victim)>0&&firstArmor<0)firstArmor=(int)battle.tick;
                if(hasInternalDamage(victim)&&firstStructure<0)firstStructure=(int)battle.tick;
                if(!victim.alive()&&death<0)death=(int)battle.tick;
                lastHits=battle.hitCount;
            }
            check(lastHits>0,"Actual weapon never hit unarmed target: "+material);
            check(firstArmor>0||victim.hull<victim.maxHull,
                    "Actual shots made no physical armor/structure change: "+material);
            check(Double.isFinite(victim.hull)&&Double.isFinite(victim.armor)&&
                            victim.hull>=0&&victim.armor>=0,
                    "Unphysical negative/nonfinite target damage: "+material);
            System.out.printf(java.util.Locale.ROOT,
                    "%s,%d,%d,%d,%d,%d,%.3f,%.4f,%.3f,%d%n",
                    material,lastHits,firstArmor,firstStructure,death,battle.tick,
                    victim.hull,victim.hull/victim.maxHull,victim.defense.totalArmor(),
                    breachedCells(victim));
        }
        System.out.println("PASS real unarmed-hull weapon impacts and all five installed defensive fits");
    }
}
