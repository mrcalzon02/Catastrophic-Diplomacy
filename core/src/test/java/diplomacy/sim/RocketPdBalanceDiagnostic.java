package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Non-blocking, measured 1v1 industrial balance ladder. Each side has exactly
 * N installed weapon or PD modules, the SAME research rank and the SAME hull.
 * The rockets still launch and collide via the real Battle code, not a Monte
 * Carlo approximation; defender is stationary to isolate point-defense
 * efficiency, or follows a specified 60-unit lateral evasion trajectory.
 *
 * The diagnostic produces CSV for future tuning and NEVER asserts a preferred
 * win-rate, damage, interception count or relative faction advantage.
 */
public final class RocketPdBalanceDiagnostic {
    private RocketPdBalanceDiagnostic() { }
    private static final int[] TIERS={0,3,6,9};
    private static final int[] MODULES={1,2,4};
    private static final int REPEAT_SEEDS=3;
    private static final int TICKS=210;
    private static final double LAUNCH_RANGE=440;

    private static void require(boolean condition,String why){
        if(!condition)throw new IllegalStateException(why);
    }
    private static Shipyard.Design fitting(TechnologyCatalog catalog,
                                           String component,int modules,int tier,
                                           boolean defense){
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int count=0;
        // PD/FLK are physical weapon hardpoints, not DEFENSE-layer slots.
        // Match the same number of real weapon mounts on both vessels.
        Shipyard.Mount mount=Shipyard.Mount.WEAPON;
        for(Shipyard.Slot slot:fit.hull.slots){
            if(count==modules)break;
            if(slot.mount()!=mount)continue;
            fit=fit.install(slot.index(),"BASE-TER-"+component);
            if(tier>0)fit=fit.stage(slot.index(),
                    defense?Shipyard.Track.SPECIALIST:Shipyard.Track.OUTPUT,tier);
            count++;
        }
        require(count==modules,"Dreadnought lacks matched real modules");
        Shipyard.Report report=Shipyard.inspect(fit,catalog);
        require(report.valid(),"Invalid matched rank "+tier+" fitting: "+report.explanation());
        return fit;
    }
    private record Sample(String label,int tier,int modules,int barrage,
                          RocketBarrage.Motor motor,boolean evasive,boolean pdOnline,
                          int seed,int launched,int intercepted,int collided,
                          int pdShots,int detectionFrames,double firstDetectionSeconds,
                          double initialPdRange,double coldDetection,double hotDetection,
                          double damageToProtection,double targetHullLoss,
                          double launchCostCredits,double nominalRocketSpeed,
                          double rocketHealth,double rocketCrossSection,
                          double rocketSignature,double pdTracking,double pdCycle,
                          double rocketReload,double rocketSingleDamage) {
        String csv(){
            return String.format(Locale.ROOT,
                    "%s,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.0f,%.3f,%.3f,%.3f,%.3f,%.3f,%.4f,%.3f,%.4f",
                    label,tier,modules,barrage,motor,evasive,pdOnline,seed,launched,intercepted,
                    collided,pdShots,detectionFrames,firstDetectionSeconds,initialPdRange,
                    coldDetection,hotDetection,damageToProtection,targetHullLoss,
                    launchCostCredits,nominalRocketSpeed,rocketHealth,rocketCrossSection,
                    rocketSignature,pdTracking,pdCycle,rocketReload,rocketSingleDamage);
        }
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed) {
        Battle battle=new Battle(900000L+seed*131L+tier*11L+modules*7L+
                size.ordinal()*19L+(motor==RocketBarrage.Motor.BOOST_COAST?1:2)+
                (evasive?13001:0));
        Shipyard.Design source=fitting(catalog,size.code,modules,tier,false);
        Shipyard.Design defensive=fitting(catalog,"PD",modules,tier,true);
        battle.spawnWing(0,source,catalog,1,200,300);
        battle.spawnWing(1,defensive,catalog,1,200+LAUNCH_RANGE,300);
        Battle.Ship attacker=battle.ships().get(0),defender=battle.ships().get(1);
        require(attacker.rocketRacks.size()==modules,
                "Real launcher count does not match lab's mounted weapon modules");
        require(defender.pdRange>0&&defender.pointDefenseProfile.damage()>0,
                "Mounted point-defense modules did not create actual laser PD");
        // Disable unrelated hull-gun/drone capabilities to isolate only the
        // equal-numbered competing hardware. No changes to PD or rocket stats.
        attacker.laserDamage=attacker.missileDamage=attacker.kineticDamage=0;
        attacker.plasmaDamage=attacker.particleDamage=attacker.torpedoDamage=0;
        attacker.pdRange=0;attacker.fighterReserve=attacker.droneReserve=0;
        defender.laserDamage=defender.missileDamage=defender.kineticDamage=0;
        defender.plasmaDamage=defender.particleDamage=defender.torpedoDamage=0;
        defender.fighterReserve=defender.droneReserve=0;
        if(!pdOnline)defender.pointDefenseCondition=0;
        // Preserve actual research-derived reactor power, cooling limits
        // and real fire-control condition. Artificial infinite energy would
        // hide whether the 4-vs-4 late-game siege barrage overheats its hull
        // or starves the defender's PD laser banks.
        // The laboratory explicitly PROVISIONS and BILLS kinetic stock
        // required for simultaneous rocket launchers. A normal campaign ship
        // still has finite, purchased ammunition and its original store limit.
        int stockNeeded=modules*barrage*(size.ordinal()+1);
        attacker.kineticAmmo=attacker.maxKineticAmmo=stockNeeded+16;
        double paidFor=stockNeeded*12.0;
        for(RocketBarrage.Rack rack:attacker.rocketRacks)
            battle.setShipRocketBarrage(attacker.id,rack.slot,barrage,motor);
        battle.registerDiagnosticStationaryTarget(defender.id);
        double startHull=defender.hull,startArmor=defender.armor,
                startShield=defender.shields;
        int detectedFrames=0;double firstDetection=-1;
        RocketBarrage.Rack first=attacker.rocketRacks.get(0);
        double pdReach=defender.pdRange;
        // Capture exact sensor envelopes even if a rocket is destroyed by
        // an initial point-defense volley before surviving a render frame.
        double coldDetection=ProjectileModel.acquisitionRange(pdReach,
                defender.sensorsCondition,battle.weather().sensorFactor(),
                defender.pointDefenseProfile,first.body,
                RocketBarrage.hotEmission(motor,false));
        double hotDetection=ProjectileModel.acquisitionRange(pdReach,
                defender.sensorsCondition,battle.weather().sensorFactor(),
                defender.pointDefenseProfile,first.body,
                RocketBarrage.hotEmission(motor,true));
        for(int frame=0;frame<TICKS;frame++){
            // Test-controlled lateral evasion is fully measured and separate
            // from captain AI. The PD ship remains physically damageable and
            // fires ordinary point-defense hardware while its location changes.
            if(evasive)defender.y=300+60*StrictMath.sin(frame*.055);
            // Sample before the real PD sweep can destroy incoming bodies.
            for(Battle.Rocket rocket:battle.rockets()){
                double range=rocket.burning()?hotDetection:coldDetection;
                double dist=StrictMath.hypot(rocket.x-defender.x,rocket.y-defender.y);
                if(dist<=range){
                    detectedFrames++;
                    if(firstDetection<0)firstDetection=frame/30.0;
                }
            }
            battle.step();
        }
        int shots=0;
        for(Battle.VisualEvent e:battle.visualEvents())
            if(e.phase()==Battle.VisualPhase.FIRE&&"POINT_DEFENSE".equals(e.weapon()))
                shots++;
        double protectionLoss=Math.max(0,startShield-defender.shields)
                +Math.max(0,startArmor-defender.armor)
                +Math.max(0,startHull-defender.hull);
        return new Sample(size.name(),tier,modules,barrage,motor,evasive,pdOnline,seed,
                battle.rocketRoundsFired,battle.rocketIntercepts,battle.rocketHits,
                shots,detectedFrames,firstDetection,pdReach,coldDetection,hotDetection,
                protectionLoss,Math.max(0,startHull-defender.hull),paidFor,
                first.body.speed(),first.body.health(),first.body.crossSection(),
                first.body.signature(),defender.pointDefenseProfile.tracking(),
                defender.pdCycle,first.reloadSeconds(),first.damage);
    }
    public static void main(String[] args)throws IOException {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Path output=Path.of("test-output","rocket-pd-balance");
        Files.createDirectories(output);
        List<String> rows=new ArrayList<>();
        List<Sample> all=new ArrayList<>();
        rows.add("size,tier,modules_each,salvo_per_rack,motor,evasive,pd_online,seed,launched,intercepted,hits,pd_shots,rocket_detection_frames,first_detection_sec,pd_nominal_range,coast_acquire_range,burn_acquire_range,damage_to_protection,hull_damage,ammo_credits,nominal_rocket_speed,rocket_hp,rocket_rcs,rocket_signature,pd_tracking,pd_cycle,rocket_reload,rocket_damage");
        int runs=0,launches=0,intercepts=0,hits=0;
        for(RocketBarrage.Size size:RocketBarrage.Size.values())
            for(int tier:TIERS)
                for(int modules:MODULES){
                    int barrage=tier==0?4:tier==3?8:tier==6?8:16;
                    for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                        for(boolean evasive:new boolean[]{false,true})
                            for(int seed=0;seed<REPEAT_SEEDS;seed++){
                                Sample sample=run(tech,size,tier,modules,
                                        barrage,motor,evasive,true,seed);
                                rows.add(sample.csv());
                                all.add(sample);
                                launches+=sample.launched;intercepts+=sample.intercepted;
                                hits+=sample.collided;runs++;
                                // A disabled but IDENTICALLY fitted PD battery
                                // establishes natural projectile miss and dodge
                                // rates independently of successful interception.
                                // One paired baseline seed per configuration
                                // keeps the optional diagnostic quick.
                                if(seed==0){
                                    Sample baseline=run(tech,size,tier,modules,
                                            barrage,motor,evasive,false,seed);
                                    rows.add(baseline.csv());all.add(baseline);runs++;
                                }
                            }
                }
        Files.write(output.resolve("ladder.csv"),rows,StandardCharsets.UTF_8);
        String summary="# Physical rocket versus point-defense balance ladder\n\n"+
                "- Scenarios: "+runs+" (four rocket sizes, 0/3/6/9 research ranks, "+
                "1/2/4 equally fitted modules per side, both motors, stationary/moving, "+
                REPEAT_SEEDS+" seeded live PD runs and one paired offline-PD control per configuration).\n"+
                "- Recorded launches: "+launches+", interceptions: "+intercepts+
                ", real impacts: "+hits+".\n"+
                "- The final late-game row includes rank-IX SIEGE batteries with "+
                "16-round barrages against rank-IX PD of the same module count.\n"+
                "- CSV contains the real sensor acquisition distances, interception "+
                "shots, projectile health/speed/signature, hull and protection damage, "+
                "and charged campaign-kinetic-equivalent ammunition credits.\n"+
                "- Balance results are deliberately **diagnostic**. No target hit "+
                "percentage, win rate or cosmetic change can fail a release.\n";
        StringBuilder detail=new StringBuilder(summary);
        detail.append("\n## Endgame: rank IX SIEGE versus same-rank laser PD, 16 rockets per rack\n\n");
        detail.append("|Modules each|Motor|Target moves|Runs|Rockets|Intercepted|Impact|PD shots|Protection lost|\n");
        detail.append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        for(int modules:MODULES)
            for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                for(boolean moving:new boolean[]{false,true}){
                    int samples=0,fired=0,stopped=0,landed=0,shots=0;
                    double damage=0;
                    for(Sample measure:all)
                        if(measure.label.equals("SIEGE")&&measure.tier==9&&
                                measure.modules==modules&&measure.barrage==16&&
                                measure.motor==motor&&measure.evasive==moving&&measure.pdOnline){
                            samples++;fired+=measure.launched;stopped+=measure.intercepted;
                            landed+=measure.collided;shots+=measure.pdShots;
                            damage+=measure.damageToProtection;
                        }
                    detail.append(String.format(Locale.ROOT,
                            "|%d / %d|%s|%s|%d|%d|%d|%d|%d|%.1f|\n",
                            modules,modules,motor,moving,samples,fired,stopped,
                            landed,shots,damage));
                }
        detail.append("\nReference ratios are empirical outcomes, not pass/fail targets. "+
                "Use offline-PD control rows to separate inertial misses and "+
                "moving-target evasions from genuine laser interception. "+
                "Compare speed, hit points, radar/thermal acquisition envelopes "+
                "and point-defense tracking/reload using the full CSV.\n");
        summary=detail.toString();
        Files.writeString(output.resolve("summary.md"),summary,StandardCharsets.UTF_8);
        System.out.println(summary);
        System.out.println("ROCKET_PD_DIAGNOSTIC_CSV="+output.resolve("ladder.csv"));
    }
}
