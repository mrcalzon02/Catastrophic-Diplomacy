package diplomacy.client;

import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import diplomacy.tech.TechnologyCatalog;

/**
 * Actual seeded weapon-lane evidence for the optional visual gallery.
 * Every non-stress damaged target is produced by Battle.step(), with one
 * physically fitted attacker and an unarmed fitted opponent. No painted
 * damage states, altered HP, fake weapon events, or modified combat rules.
 */
final class ShipSurfaceDamageCapture {
    private ShipSurfaceDamageCapture(){}
    static final String[] KEYS={
        "pristine", "shield-contact", "laser-scoring", "kinetic-impact",
        "missile-splash", "laser-repeat", "armor-breach", "structure-hit",
        "rectangle-stress", "plasma-impact", "particle-puncture",
        "torpedo-impact", "kinetic-heavy",
        "kinetic-pristine", "kinetic-heavy-pristine",
        "kinetic-aureate", "kinetic-aureate-pristine",
        "kinetic-zelthar", "kinetic-zelthar-pristine",
        "kinetic-aureate-upper", "kinetic-aureate-upper-pristine",
        "kinetic-terran-upper", "kinetic-terran-upper-pristine",
        "laser-terran-upper", "laser-terran-upper-pristine",
        "missile-terran-upper", "missile-terran-upper-pristine"
    };
    static final long SEED=202611010140L;
    record Snapshot(String key,String weapon,String hull,long seed,Battle battle,
                    int targetId,int ticks,int hits,double armorExposure,
                    double internalExposure,int wornCells,int exposedCells,
                    boolean milestoneReached,boolean syntheticStress,
                    boolean axialAtSpawn) {
        Battle.Ship target(){
            for(Battle.Ship ship:battle.ships())
                if(ship.id==targetId)return ship;
            throw new IllegalStateException("Capture target missing: "+key);
        }
    }
    private static int firstWeaponSlot(Shipyard.Design design){
        for(Shipyard.Slot slot:design.placements())
            if(slot.mount()==Shipyard.Mount.WEAPON)return slot.index();
        throw new IllegalStateException("No real weapon socket");
    }
    private static double maxArmor(Battle.Ship ship){
        double result=0;
        for(int r=0;r<DirectionalDefense.GRID;r++)
            for(int c=0;c<DirectionalDefense.GRID;c++){
                var cell=ship.defense.tile(r,c);
                if(cell.occupied)result=Math.max(result,cell.exposure());
            }
        return result;
    }
    private static double maxInterior(Battle.Ship ship){
        double result=0;
        for(int r=0;r<DirectionalDefense.GRID;r++)
            for(int c=0;c<DirectionalDefense.GRID;c++){
                var cell=ship.defense.tile(r,c);
                if(cell.occupied)result=Math.max(result,cell.internalDamage());
            }
        return result;
    }
    private static Snapshot record(String key,String weapon,long seed,
                                   Battle battle,Battle.Ship ship,
                                   boolean reached,boolean stress,
                                   boolean axialAtSpawn) {
        int worn=0,exposed=0;
        for(int r=0;r<DirectionalDefense.GRID;r++)
            for(int c=0;c<DirectionalDefense.GRID;c++){
                var cell=ship.defense.tile(r,c);
                if(!cell.occupied)continue;
                if(cell.exposure()>.01)worn++;
                if(cell.exposure()>=.90||cell.internalDamage()>.01)exposed++;
            }
        return new Snapshot(key,weapon,ship.hullClass.name(),seed,battle,ship.id,
                (int)battle.tick,battle.hitCount,maxArmor(ship),maxInterior(ship),
                worn,exposed,reached,stress,axialAtSpawn);
    }
    static Snapshot create(TechnologyCatalog technology,int index){
        if(index<0||index>=KEYS.length)throw new IllegalArgumentException("Unknown armor case "+index);
        if(index==8){
            // The former 6x6-cell image is a deliberately artificial STRESS test.
            // Retain it so the workflow catches blocky-grid regressions.
            Battle stress=ShipSurfaceDamageFixture.create(technology);
            return record(KEYS[index],"KINETIC_GRID_STRESS",SEED,stress,
                    stress.ships().get(2),true,true,false);
        }
        final String weapon=switch(index){
            case 3,7,12,13,14,15,16,17,18,19,20,21,22 -> "KINETIC";
            case 4,25,26 -> "MISSILE";
            case 9 -> "PLASMA";
            case 10 -> "PARTICLE";
            case 11 -> "TORPEDO";
            default -> "LASER";
        };
        final String suffix=switch(weapon){
            case "KINETIC" -> "KIN";
            case "MISSILE" -> "MSL";
            case "PLASMA" -> "PLA";
            case "PARTICLE" -> "PAR";
            case "TORPEDO" -> "TOR";
            default -> "LAS";
        };
        final Shipyard.Hull hull=index==3||index==4||index==11||index==13||index>=15?
                Shipyard.Hull.FRIGATE:
                index==7||index==12||index==14?Shipyard.Hull.DESTROYER:Shipyard.Hull.CORVETTE;
        Shipyard.Design bare=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        Shipyard.Design shooter=bare.install(firstWeaponSlot(bare),"BASE-TER-"+suffix);
        // Actual factory-fitted defenses and structural systems, but no guns:
        // otherwise an empty bare hull may have zero shields and make the
        // purported shield-only shot a misleading immediate armor breach.
        // A thin Vaerith wing has room for only a one-pixel metal scar
        // at its actual contact point; it CANNOT display an honest circular
        // pockmark without painting outside approved art. Use broad Terran
        // plating to demonstrate real kinetic pockmark geometry while keeping
        // the genuine fitted shooter, projectile, impact, and physics intact.
        // Compare real approved original source artwork, never artificial
        // brightened sprites. Measure actual source luminance separately.
        Battle.Faction targetFaction=index==15||index==16||index==19||index==20?
                Battle.Faction.AUREATE_SYNOD:
                index==17||index==18?Battle.Faction.ZELTHAR_LUMINARI:
                (index==3||index==12||index==13||index==14||index>=21)?
                Battle.Faction.TERRAN:Battle.Faction.VAERITH_PRISM;
        Shipyard.Design targetDesign=Shipyard.starter(targetFaction,hull);
        for(Shipyard.Slot slot:targetDesign.placements())
            if(slot.mount()==Shipyard.Mount.WEAPON&&targetDesign.at(slot.index())!=null)
                targetDesign=targetDesign.install(slot.index(),null);
        if(!Shipyard.inspect(shooter,technology).valid()||
                !Shipyard.inspect(targetDesign,technology).valid())
            throw new IllegalStateException("Live-fire capture needs real legal fitted hulls: "+weapon+"/"+hull);
        long seed=SEED+switch(weapon){
            case "KINETIC" -> 11;
            case "MISSILE" -> 23;
            case "PLASMA" -> 31;
            case "PARTICLE" -> 37;
            case "TORPEDO" -> 41;
            default -> 0;
        };
        Battle battle=new Battle(seed);
        // Short-range plasma (245 world-unit base range) must begin within
        // its actual firing envelope, unlike the 280-unit standard lane.
        // We move only this disposable diagnostic firing lane; gun range,
        // ship mobility and combat weapon mechanics are unchanged.
        // Match all four upper-panel pristine / weapon lanes with a real
        // bow-on approach: our previous upper-left diagonal still struck
        // the peripheral dark wing pod, not the broad source-art plating.
        // Keep fixed-heading real target physics and autonomous gun firing;
        // only the disposable forensic firing-lane approach changes.
        boolean upperStrike=index>=19;
        battle.spawnWing(0,shooter,technology,1,
                upperStrike?660:index==9?470:380,
                upperStrike?170:360);
        battle.spawnWing(1,targetDesign,technology,1,660,360);
        Battle.Ship gun=battle.ships().get(0),victim=battle.ships().get(1);
        if(gun.laserDamage+gun.kineticDamage+gun.missileDamage+
                gun.plasmaDamage+gun.particleDamage+gun.torpedoDamage<=0)
            throw new IllegalStateException("Live-fire shooter lacks requested fitted weapon: "+weapon);
        if(victim.laserDamage+victim.kineticDamage+victim.missileDamage+
                victim.plasmaDamage+victim.particleDamage+victim.torpedoDamage>0)
            throw new IllegalStateException("Live-fire target must remain unarmed");
        if(index==1 && victim.maxShields<=0)
            throw new IllegalStateException("Shield contact diagnostic requires a physical fitted shield");
        // Capture initial geometry, not the gun AI's later moving position.
        final boolean axialAtSpawn=Math.abs(gun.x-victim.x)<.0001
                &&gun.y<victim.y;
        gun.speed=0;gun.vx=gun.vy=0;
        victim.speed=0;victim.vx=victim.vy=0;
        victim.facing=Math.PI/2;
        victim.name="STATIONARY TEST TARGET / "+KEYS[index].toUpperCase(java.util.Locale.ROOT);
        battle.registerDiagnosticStationaryTarget(victim.id);
        // The shield contact scenario retains real shields. Other lanes
        // disable recharge only in their disposable test battles so repeated
        // ammunition can be observed against armor without changing gameplay.
        if(index!=1){
            victim.defense.disruptShields(victim.maxShields);
            for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values())
                victim.defense.configureRecharge(face,0,2);
            victim.shields=victim.defense.totalShields();
        }
        // Identical target pose across every deterministic screenshot. The
        // attacker remains autonomous and still hits with genuine projectiles.
        final double fixedX=victim.x,fixedY=victim.y,fixedFacing=victim.facing;
        if(index==0||index==13||index==14||index==16||index==18||
                index==20||index==22||index==24||index==26)
            return record(KEYS[index],weapon,seed,battle,victim,true,false,axialAtSpawn);
        boolean reached=false;
        int maxSteps=(index>=6?1500:600);
        for(int i=0;i<maxSteps&&!battle.finished();i++){
            battle.step();
            if(Double.compare(victim.x,fixedX)!=0||
                    Double.compare(victim.y,fixedY)!=0||
                    Double.compare(victim.facing,fixedFacing)!=0)
                throw new IllegalStateException("Stationary target changed pose in "+KEYS[index]+
                        " tick "+battle.tick);
            if(victim.lastTargetId!=0||victim.withdrawing||victim.escaped)
                throw new IllegalStateException("Stationary target ran autonomous orders in "+KEYS[index]);
            double armor=maxArmor(victim),interior=maxInterior(victim);
            reached=switch(index){
                case 1 -> battle.hitCount>=1;
                case 2 -> battle.hitCount>=2&&armor>.01;
                case 3 -> battle.hitCount>=3&&armor>.01;
                case 4 -> battle.hitCount>=2&&armor>.01;
                case 5 -> battle.hitCount>=10&&armor>.10;
                case 6 -> armor>=.72;
                case 7 -> interior>=.01;
                case 9,10,11 -> battle.hitCount>=1&&armor>.01;
                case 12 -> battle.hitCount>=3&&interior>.01;
                case 15,17,19,21 -> battle.hitCount>=3&&armor>.01;
                case 23,25 -> battle.hitCount>=2&&armor>.01;
                default -> false;
            };
            // Never fabricate a surviving ship or repair a dead target simply
            // to make an attractive image. Report an unmet milestone instead.
            if(reached||!victim.alive()||victim.hull<=victim.maxHull*.15)break;
        }
        victim.shields=victim.defense.totalShields();
        victim.armor=victim.defense.totalArmor();
        return record(KEYS[index],weapon,seed,battle,victim,reached,false,axialAtSpawn);
    }
}
