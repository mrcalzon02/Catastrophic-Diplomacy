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
    private static final int TICKS=300;
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
                          int seed,int launched,int intercepted,int terminalEntries,int collided,
                          int pdShots,int detectionFrames,double firstDetectionSeconds,
                          double initialPdRange,double coldDetection,double hotDetection,
                          double damageToProtection,double targetHullLoss,
                          double launchCostCredits,double nominalRocketSpeed,
                          double rocketHealth,double rocketCrossSection,
                          double rocketSignature,double pdTracking,double pdCycle,
                          double rocketReload,double rocketSingleDamage,
                          int acquisitions,int slewTicks,int lockWaitTicks) {
        String csv(){
            return String.format(Locale.ROOT,
                    "%s,%d,%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.0f,%.3f,%.3f,%.3f,%.3f,%.3f,%.4f,%.3f,%.4f,%d,%d,%d",
                    label,tier,modules,barrage,motor,evasive,pdOnline,seed,launched,intercepted,
                    terminalEntries,collided,pdShots,detectionFrames,firstDetectionSeconds,initialPdRange,
                    coldDetection,hotDetection,damageToProtection,targetHullLoss,
                    launchCostCredits,nominalRocketSpeed,rocketHealth,rocketCrossSection,
                    rocketSignature,pdTracking,pdCycle,rocketReload,rocketSingleDamage,
                    acquisitions,slewTicks,lockWaitTicks);
        }
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,seed,0);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,seed,pressure,1.0);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure,
                              double acquisitionScale) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,
                seed,pressure,acquisitionScale,true);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure,
                              double acquisitionScale,boolean coordinated) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,
                seed,pressure,acquisitionScale,coordinated,tier);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure,
                              double acquisitionScale,boolean coordinated,int defenderTier) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,
                seed,pressure,acquisitionScale,coordinated,defenderTier,1.0);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure,
                              double acquisitionScale,boolean coordinated,int defenderTier,
                              double sustainedThrustScale) {
        return run(catalog,size,tier,modules,barrage,motor,evasive,pdOnline,
                seed,pressure,acquisitionScale,coordinated,defenderTier,
                sustainedThrustScale,0);
    }
    private static Sample run(TechnologyCatalog catalog,RocketBarrage.Size size,
                              int tier,int modules,int barrage,RocketBarrage.Motor motor,
                              boolean evasive,boolean pdOnline,int seed,double pressure,
                              double acquisitionScale,boolean coordinated,int defenderTier,
                              double sustainedThrustScale,int channelsPerMount) {
        Battle battle=new Battle(900000L+seed*131L+tier*11L+modules*7L+
                size.ordinal()*19L+(motor==RocketBarrage.Motor.BOOST_COAST?1:2)+
                (evasive?13001:0));
        if(pdOnline&&pressure>0)battle.configureRocketPdCrowdingForDiagnostic(pressure);
        if(pdOnline&&acquisitionScale!=1.0)
            battle.configurePrecisionLockForDiagnostic(acquisitionScale);
        if(pdOnline&&!coordinated)
            battle.configurePrecisionReservationsForDiagnostic(false);
        if(sustainedThrustScale!=1.0)
            battle.configureSustainedRocketThrustForDiagnostic(sustainedThrustScale);
        if(pdOnline&&channelsPerMount>0)
            battle.configurePrecisionTrackCapacityForDiagnostic(channelsPerMount);
        Shipyard.Design source=fitting(catalog,size.code,modules,tier,false);
        Shipyard.Design defensive=fitting(catalog,"PD",modules,defenderTier,true);
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
        // Keep the instrumented target alive throughout the entire first
        // launch. Otherwise repeated hits destroy its PD and later rockets
        // become artificially unopposed. The hull still receives real damage.
        defender.maxHull=defender.hull=2_000_000;
        double startHull=defender.hull,startArmor=defender.armor,
                startShield=defender.shields;
        int detectedFrames=0;double firstDetection=-1;
        java.util.HashSet<Long> innerEntries=new java.util.HashSet<>();
        final double innerProtectionRadius=90.0;
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
            // These rockets actually reached the threatened hull's final
            // defensive zone after PD has had its ordinary firing turn.
            for(Battle.Rocket rocket:battle.rockets())
                if(StrictMath.hypot(rocket.x-defender.x,rocket.y-defender.y)
                        <=innerProtectionRadius)
                    innerEntries.add(rocket.visualId);
        }
        require(battle.rockets().isEmpty(),
                "A rocket was still in flight at end of balance measurement");
        int shots=0;
        for(Battle.VisualEvent e:battle.visualEvents()){
            if(e.phase()==Battle.VisualPhase.FIRE&&"POINT_DEFENSE".equals(e.weapon()))
                shots++;
            // An actual physical hit vanishes during updateRockets(), before
            // the post-step living-rocket scan. Include that event exactly
            // once using the real projectile shot ID.
            if(e.phase()==Battle.VisualPhase.IMPACT&&"MISSILE".equals(e.weapon())
                    &&e.team()==attacker.team&&e.sourceId()==attacker.id
                    &&e.targetId()==defender.id)
                innerEntries.add(e.shotId());
        }
        double protectionLoss=Math.max(0,startShield-defender.shields)
                +Math.max(0,startArmor-defender.armor)
                +Math.max(0,startHull-defender.hull);
        int acquisitions=0,slew=0,lockWait=0;
        for(Battle.Ship.PrecisionMount mount:defender.precisionMounts){
            acquisitions+=mount.targetAcquisitions;
            slew+=mount.slewTicks;
            lockWait+=mount.lockWaitTicks;
        }
        return new Sample(size.name(),tier,modules,barrage,motor,evasive,pdOnline,seed,
                battle.rocketRoundsFired,battle.rocketIntercepts,innerEntries.size(),
                battle.rocketHits,
                shots,detectedFrames,firstDetection,pdReach,coldDetection,hotDetection,
                protectionLoss,Math.max(0,startHull-defender.hull),paidFor,
                first.body.speed(),first.body.health(),first.body.crossSection(),
                first.body.signature(),defender.pointDefenseProfile.tracking(),
                defender.pdCycle,first.reloadSeconds(),first.damage,
                acquisitions,slew,lockWait);
    }
    /** Verify actual installed research, cloned live racks and immutable
     * propulsion factor remain aligned, independent of accuracy/reload stats. */
    private static void verifyFittedMotorResearch(TechnologyCatalog tech){
        require(Battle.precisionTrackLoadMultiplier(0,4,4)==1.0,
                "Empty optical channels generated fictitious overload");
        require(Battle.precisionTrackLoadMultiplier(16,4,4)==1.0,
                "Precisely provisioned optical channels were overloaded");
        require(StrictMath.abs(Battle.precisionTrackLoadMultiplier(64,4,4)-2.0)<1e-8,
                "Finite CIWS optical overload does not account for emitter count");
        require(Battle.precisionTrackLoadMultiplier(64,4,0)==1.0,
                "Disabled optical experiment leaked into ordinary combat");
        double previous=0;
        for(int tier:new int[]{0,3,6,9}){
            Shipyard.Design design=fitting(tech,"RKG",4,tier,false);
            RocketBarrage.Rack fitted=Shipyard.rocketRacks(design,tech).get(0);
            double expected=1.0+tier/9.0;
            require(fitted.researchTier==tier,
                    "Rocket rack discarded purchased propulsion research stage");
            require(StrictMath.abs(fitted.sustainedThrustFactor-expected)<1e-10,
                    "Rocket thrust did not scale with installed module research");
            require(fitted.sustainedThrustFactor>=previous,
                    "Rocket research degraded sustained motor thrust");
            RocketBarrage.Rack cloned=new RocketBarrage.Rack(fitted);
            require(cloned.researchTier==tier&&
                    cloned.sustainedThrustFactor==fitted.sustainedThrustFactor,
                    "Rocket rack clone lost researched drive performance");
            Battle test=new Battle(503003L+tier);
            test.spawnWing(0,design,tech,1,200,300);
            RocketBarrage.Rack live=test.ships().get(0).rocketRacks.get(0);
            require(live.researchTier==tier&&
                    live.sustainedThrustFactor==fitted.sustainedThrustFactor,
                    "Real commissioned ship lost researched rocket drive");
            previous=fitted.sustainedThrustFactor;
        }
    }
    public static void main(String[] args)throws IOException {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        verifyFittedMotorResearch(tech);
        Path output=Path.of("test-output","rocket-pd-balance");
        Files.createDirectories(output);
        List<String> rows=new ArrayList<>();
        List<Sample> all=new ArrayList<>();
        rows.add("size,tier,modules_each,salvo_per_rack,motor,evasive,pd_online,seed,launched,intercepted,terminal_entries,hits,pd_shots,rocket_detection_frames,first_detection_sec,pd_nominal_range,coast_acquire_range,burn_acquire_range,damage_to_protection,hull_damage,ammo_credits,nominal_rocket_speed,rocket_hp,rocket_rcs,rocket_signature,pd_tracking,pd_cycle,rocket_reload,rocket_damage,pd_target_acquisitions,"+
                "pd_slew_ticks,pd_lock_wait_ticks");
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
        detail.append("|Modules each|Motor|Target moves|Runs|Rockets|Intercepted|Escaped PD|Escape %|Terminal entry %|Impacts|Offline-PD impacts|PD shots|Protection lost|\n");
        detail.append("|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(int modules:MODULES)
            for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                for(boolean moving:new boolean[]{false,true}){
                    int samples=0,fired=0,stopped=0,landed=0,shots=0,offlineHits=0,terminal=0;
                    double damage=0;
                    for(Sample measure:all){
                        if(measure.label.equals("SIEGE")&&measure.tier==9&&
                                measure.modules==modules&&measure.barrage==16&&
                                measure.motor==motor&&measure.evasive==moving&&!measure.pdOnline)
                            offlineHits+=measure.collided;
                        if(measure.label.equals("SIEGE")&&measure.tier==9&&
                                measure.modules==modules&&measure.barrage==16&&
                                measure.motor==motor&&measure.evasive==moving&&measure.pdOnline){
                            samples++;fired+=measure.launched;stopped+=measure.intercepted;
                            landed+=measure.collided;terminal+=measure.terminalEntries;shots+=measure.pdShots;
                            damage+=measure.damageToProtection;
                        }
                    }
                    detail.append(String.format(Locale.ROOT,
                            "|%d / %d|%s|%s|%d|%d|%d|%d|%.1f%%|%.1f%%|%d|%d|%d|%.1f|\n",
                            modules,modules,motor,moving,samples,fired,stopped,
                            fired-stopped,fired==0?0:100.0*(fired-stopped)/fired,
                            fired==0?0:100.0*terminal/fired,
                            landed,offlineHits,shots,damage));
                }
        detail.append("\nEscaped PD = launched - intercepted after all initial "+
                "bodies resolve (impact or physical flight timeout). This is "+
                "NOT the same as impacts or terminal-zone entry (within 90 world "+ 
                "units of the actual defended hull), which exclude harmless "+ 
                "wide misses. Impacts also depend on inertial spread "+
                "and evasion. Offline-PD impacts represent one paired seed "+
                "against three online seeds. The ~33% desired escape rate "+
                "is a non-blocking balance goal, not an objective release gate.\n");
        // Optional A/B sweep exercises real physical rockets and fitted PD.
        // No proposed overload coefficient affects regular game battles.
        detail.append("\n## Experimental PD track-overload sensitivity (NOT live balance)\n\n");
        detail.append("|PD racks|Motor|Moving target|Crowd pressure|Rockets|Intercepted|Escape %|Terminal entry %|Impacts|\n");
        detail.append("|---:|---|---|---:|---:|---:|---:|---:|---:|\n");
        List<String> sweep=new ArrayList<>();
        sweep.add("modules_each,motor,evasive,pressure,launched,intercepted,escaped,escape_fraction,terminal_entries,terminal_fraction,impacts,shots");
        for(int modules:new int[]{1,4})
            for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                for(boolean moving:new boolean[]{false,true})
                    for(double pressure:new double[]{0,0.15,0.30,0.50,0.75}){
                        int fired=0,intercepted=0,impacted=0,terminal=0,shots=0;
                        for(int seed=0;seed<REPEAT_SEEDS;seed++){
                            Sample sample=run(tech,RocketBarrage.Size.SIEGE,9,modules,
                                    16,motor,moving,true,seed,pressure);
                            fired+=sample.launched;
                            intercepted+=sample.intercepted;
                            impacted+=sample.collided;
                            terminal+=sample.terminalEntries;
                            shots+=sample.pdShots;
                        }
                        double escape=fired==0?0:(fired-intercepted)/(double)fired;
                        double entry=fired==0?0:terminal/(double)fired;
                        sweep.add(String.format(Locale.ROOT,
                                "%d,%s,%s,%.2f,%d,%d,%d,%.6f,%d,%.6f,%d,%d",
                                modules,motor,moving,pressure,fired,intercepted,
                                fired-intercepted,escape,terminal,entry,impacted,shots));
                        if(modules==4)
                            detail.append(String.format(Locale.ROOT,
                                    "|%d|%s|%s|%.2f|%d|%d|%.1f%%|%.1f%%|%d|\n",
                                    modules,motor,moving,pressure,fired,intercepted,
                                    escape*100,entry*100,impacted));
                    }
        Files.write(output.resolve("saturation-sweep.csv"),sweep,StandardCharsets.UTF_8);
        detail.append("\nThis sweep is isolated to laboratory battles and only changes "+
                "the chance of a physical PD shot against rockets when acquired "+
                "inbound tracks exceed four per fitted PD emitter. Its outcomes "+
                "must be measured before enabling any pressure in actual combat.\n");
        // Controlled physical optical-acquisition sweep. The ship's real
        // turret turn, target retention, missiles, defenses and ammunition
        // are unchanged. Only lock dwell duration varies in opt-in laboratory
        // battles; no additional global accuracy rolls are introduced.
        detail.append("\n## Physical precision-CIWS target lock-time sensitivity (laboratory only)\n\n");
        detail.append("|Motor|Moving|Dwell multiplier|Launched|Intercepted|Escaped %|"+
                "Terminal entry %|Actual impacts|PD shots|Target acquisitions|Slew ticks|Lock-wait ticks|\n");
        detail.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        List<String> lockSweep=new ArrayList<>();
        lockSweep.add("motor,evasive,lock_scale,launched,intercepted,escaped,escape_fraction,"+
                "terminal_entries,terminal_fraction,impacts,pd_shots,acquisitions,slew_ticks,lock_wait_ticks");
        for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
            for(boolean moving:new boolean[]{false,true})
                for(double lockScale:new double[]{.35,.50,.65,.80,1.0}){
                    int fired=0,killed=0,terminal=0,impacted=0,shots=0,
                            acquisitions=0,slew=0,lockWait=0;
                    for(int seed=0;seed<REPEAT_SEEDS;seed++){
                        Sample trial=run(tech,RocketBarrage.Size.SIEGE,9,4,16,
                                motor,moving,true,seed,0,lockScale);
                        fired+=trial.launched();killed+=trial.intercepted();
                        terminal+=trial.terminalEntries();impacted+=trial.collided();
                        shots+=trial.pdShots();acquisitions+=trial.acquisitions();
                        slew+=trial.slewTicks();lockWait+=trial.lockWaitTicks();
                    }
                    double escaped=fired==0?0:(fired-killed)/(double)fired;
                    double terminalFraction=fired==0?0:terminal/(double)fired;
                    lockSweep.add(String.format(Locale.ROOT,
                            "%s,%s,%.2f,%d,%d,%d,%.6f,%d,%.6f,%d,%d,%d,%d,%d",
                            motor,moving,lockScale,fired,killed,fired-killed,
                            escaped,terminal,terminalFraction,impacted,shots,
                            acquisitions,slew,lockWait));
                    detail.append(String.format(Locale.ROOT,
                            "|%s|%s|%.2f|%d|%d|%.1f%%|%.1f%%|%d|%d|%d|%d|%d|\n",
                            motor,moving,lockScale,fired,killed,
                            escaped*100,terminalFraction*100,impacted,shots,
                            acquisitions,slew,lockWait));
                }
        Files.write(output.resolve("physical-lock-sweep.csv"),lockSweep,StandardCharsets.UTF_8);
        detail.append("\nThese are real simulated launch/interception trajectories, not a Monte "+
                "Carlo estimator. A dwell multiplier is a duration, NOT a forced "+
                "probability of missing. This sweep does not change normal battles. "+
                "The ~33% siege escape goal is advisory and must be weighed "+
                "against inner-zone breaches, true impacts, and power/thermal costs.\n");
        // Same-seed physical A/B keeps the turret mechanics identical,
        // changing only whether the hull avoids duplicated optical targets.
        detail.append("\n## Physical multi-mount reservation A/B (diagnostic only)\n\n");
        detail.append("|Motor|Moving|Lock scale|Coordinated|Rockets|Intercepted|Escape %|"+
                "Terminal %|Impacts|Acquisitions|Lock-wait ticks|\n");
        detail.append("|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
        List<String> reservationRows=new ArrayList<>();
        reservationRows.add("motor,evasive,lock_scale,coordinated,rockets,intercepted,"+
                "escaped,escape_fraction,terminal_entries,terminal_fraction,impacts,"+
                "acquisitions,lock_wait_ticks");
        for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
            for(boolean moving:new boolean[]{false,true})
                for(double dwell:new double[]{.65,1.0,1.15,1.30,1.45,1.60,1.80,2.0})
                    for(boolean coordinated:new boolean[]{false,true}){
                        // The old duplicate-targeting control is already
                        // measured at .65/1.0; avoid redundant large sweeps.
                        if(dwell>1.0&&!coordinated)continue;
                        int launched=0,killed=0,terminal=0,impacts=0,acquisitions=0,wait=0;
                        for(int seed=0;seed<REPEAT_SEEDS;seed++){
                            Sample trial=run(tech,RocketBarrage.Size.SIEGE,9,4,16,
                                    motor,moving,true,seed,0,dwell,coordinated);
                            launched+=trial.launched();killed+=trial.intercepted();
                            terminal+=trial.terminalEntries();impacts+=trial.collided();
                            acquisitions+=trial.acquisitions();wait+=trial.lockWaitTicks();
                        }
                        double escaped=launched==0?0:(launched-killed)/(double)launched;
                        double entry=launched==0?0:terminal/(double)launched;
                        reservationRows.add(String.format(Locale.ROOT,
                                "%s,%s,%.2f,%s,%d,%d,%d,%.6f,%d,%.6f,%d,%d,%d",
                                motor,moving,dwell,coordinated,launched,killed,
                                launched-killed,escaped,terminal,entry,impacts,acquisitions,wait));
                        detail.append(String.format(Locale.ROOT,
                                "|%s|%s|%.2f|%s|%d|%d|%.1f%%|%.1f%%|%d|%d|%d|\n",
                                motor,moving,dwell,coordinated,launched,killed,
                                escaped*100,entry*100,impacts,acquisitions,wait));
                    }
        Files.write(output.resolve("precision-reservation-ab.csv"),
                reservationRows,StandardCharsets.UTF_8);
        detail.append("\nCoordinated turrets reserve expected damage from real aiming "+
                "mounts, without modifying accuracy, firing cycles or armor. "+
                "The A/B rows are paired seeded physical battles; interpret "+
                "terminal-zone leakage and impacts separately from kills.\n");
        // Attack hardware and defending research must be genuinely installed
        // at the named levels. No stage-IX PD versus legacy unlicensed
        // launcher can masquerade as an IX/IX stage parity test.
        detail.append("\n## Real rocket-versus-PD cross-tier parity (advisory)\n\n");
        detail.append("|Attacker rank|Defender rank|Motor|Moving|PD lock scale|Rockets|"+
                "Intercepted|Escaped %|Terminal %|Impacts|PD shots|\n");
        detail.append("|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        List<String> crossTierRows=new ArrayList<>();
        crossTierRows.add("attack_stage,defense_stage,motor,moving,lock_scale,launched,"+
                "intercepted,escaped,escape_fraction,terminal_entries,"+
                "terminal_fraction,impacts,pd_shots,acquisitions");
        for(int attacking:new int[]{0,9})
            for(int defending:new int[]{0,9})
                for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                    for(boolean moving:new boolean[]{false,true})
                        for(double scale:new double[]{1.0,1.60}){
                            int launched=0,killed=0,inner=0,impacts=0,shots=0,acquisitions=0;
                            int barrage=attacking==0?4:16;
                            for(int seed=0;seed<REPEAT_SEEDS;seed++){
                                Sample trial=run(tech,RocketBarrage.Size.SIEGE,
                                        attacking,4,barrage,motor,moving,
                                        true,seed,0,scale,true,defending);
                                launched+=trial.launched();killed+=trial.intercepted();
                                inner+=trial.terminalEntries();impacts+=trial.collided();
                                shots+=trial.pdShots();acquisitions+=trial.acquisitions();
                            }
                            double escaped=launched==0?0:(launched-killed)/(double)launched;
                            double entry=launched==0?0:inner/(double)launched;
                            crossTierRows.add(String.format(Locale.ROOT,
                                    "%d,%d,%s,%s,%.2f,%d,%d,%d,%.6f,%d,%.6f,%d,%d,%d",
                                    attacking,defending,motor,moving,scale,
                                    launched,killed,launched-killed,escaped,
                                    inner,entry,impacts,shots,acquisitions));
                            detail.append(String.format(Locale.ROOT,
                                    "|%d|%d|%s|%s|%.2f|%d|%d|%.1f%%|%.1f%%|%d|%d|\n",
                                    attacking,defending,motor,moving,scale,
                                    launched,killed,escaped*100,entry*100,impacts,shots));
                        }
        Files.write(output.resolve("real-cross-tier-rockets.csv"),
                crossTierRows,StandardCharsets.UTF_8);
        detail.append("\nThis deliberately tests four actual attacking/defending "+
                "research pairs (0/0, IX/IX, IX/0, 0/IX), with equal four "+
                "socket counts, finite real rocket stock and 3 same-attack-seed "+
                "trials per row. It is NOT a final tuned balance specification.\n");
        // Physical motor A/B, using the same rocket hardware and same seeded
        // no-PD controls at each candidate thrust. No player-side motor change.
        detail.append("\n## Sustained rocket thrust versus fitted precision CIWS (laboratory only)\n\n");
        detail.append("|Attack|Defense|Motor|Moving|Thrust x|Lock x|Launched|Intercepted|"+
                "Escaped %|Terminal %|Actual impacts|PD-off impacts|Protection lost|\n");
        detail.append("|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        List<String> motorRows=new ArrayList<>();
        motorRows.add("attack_tier,defense_tier,motor,moving,thrust_scale,lock_scale,"+
                "launched,intercepted,escaped,escape_fraction,terminal_entries,"+
                "terminal_fraction,impacts,pd_off_impacts,protection_loss");
        for(int[] tiers:new int[][]{{0,0},{9,9},{9,0},{0,9}})
            for(boolean moving:new boolean[]{false,true})
                for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                    for(double thrust:new double[]{1.0,1.30,1.60,2.0,2.50}){
                        if(motor==RocketBarrage.Motor.BOOST_COAST&&thrust!=1.0)
                            continue; // high-thrust control, physically untouched
                        int launched=0,killed=0,terminal=0,impacted=0,offline=0;
                        double protection=0;
                        int salvo=tiers[0]==0?4:16;
                        for(int seed=0;seed<REPEAT_SEEDS;seed++){
                            Sample active=run(tech,RocketBarrage.Size.SIEGE,tiers[0],
                                    4,salvo,motor,moving,true,seed,0,1.60,true,
                                    tiers[1],thrust);
                            Sample noPd=run(tech,RocketBarrage.Size.SIEGE,tiers[0],
                                    4,salvo,motor,moving,false,seed,0,1.60,true,
                                    tiers[1],thrust);
                            require(active.launched()==noPd.launched(),
                                    "Rocket count drift across paired motor cases");
                            launched+=active.launched();killed+=active.intercepted();
                            terminal+=active.terminalEntries();impacted+=active.collided();
                            offline+=noPd.collided();protection+=active.damageToProtection();
                        }
                        double escape=launched==0?0:(launched-killed)/(double)launched;
                        double entry=launched==0?0:terminal/(double)launched;
                        motorRows.add(String.format(Locale.ROOT,
                                "%d,%d,%s,%s,%.2f,1.60,%d,%d,%d,%.6f,%d,%.6f,%d,%d,%.3f",
                                tiers[0],tiers[1],motor,moving,thrust,launched,killed,
                                launched-killed,escape,terminal,entry,impacted,offline,protection));
                        detail.append(String.format(Locale.ROOT,
                                "|%d|%d|%s|%s|%.2f|1.60|%d|%d|%.1f%%|%.1f%%|%d|%d|%.1f|\n",
                                tiers[0],tiers[1],motor,moving,thrust,launched,killed,
                                escape*100,entry*100,impacted,offline,protection));
                    }
        Files.write(output.resolve("motor-thrust-physical-ab.csv"),
                motorRows,StandardCharsets.UTF_8);
        detail.append("\nIn ordinary play, the installed rocket module's research "+
                "tier now supplies a real sustained-thrust factor from 1x (base) "+
                "to 2x (stage IX), while the boost/coast motor is unchanged. "+
                "The displayed Thrust x values are EXTRA laboratory-only factors "+
                "above the researched drive; the 1.00 row measures ordinary play. "+
                "All runs retain fixed unguided headings, research-derived speed "+
                "caps, finite rocket ammunition and real collision physics. "+
                "Compare genuine impacts against each matched PD-off control; "+
                "no interception percentage is a release gate.\n");
        // All physical threat groups share one opt-in optical track-capacity
        // measurement. Unlike an RNG accuracy penalty, extra contacts consume
        // time as the fitted turrets rotate and identify each missile/rocket.
        detail.append("\n## Physical simultaneous CIWS optical-track capacity A/B\n\n");
        detail.append("|Att tier|PD tier|Motor|Moving|Tracks/mount|Rockets|Intercepted|"+
                "Escape %|Terminal %|Hits|No-PD hits|PD shots|\n");
        detail.append("|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        List<String> opticalRows=new ArrayList<>();
        opticalRows.add("attacker_tier,pd_tier,motor,evasive,tracks_per_mount,"+
                "launched,intercepted,escaped,escape_fraction,terminal_entries,"+
                "terminal_fraction,impacts,control_impacts,pd_shots");
        for(int[] stages:new int[][]{{0,0},{9,9},{9,0},{0,9}})
            for(RocketBarrage.Motor motor:RocketBarrage.Motor.values())
                for(boolean moving:new boolean[]{false,true}){
                    int offlineHits=0;
                    int barrage=stages[0]==0?4:16;
                    for(int seed=0;seed<REPEAT_SEEDS;seed++){
                        Sample offline=run(tech,RocketBarrage.Size.SIEGE,stages[0],
                                4,barrage,motor,moving,false,seed,0,1.0,true,
                                stages[1],1.0,0);
                        offlineHits+=offline.collided();
                    }
                    for(int channels:new int[]{0,2,4,6,8}){
                        int launched=0,killed=0,inner=0,impacts=0,shots=0;
                        for(int seed=0;seed<REPEAT_SEEDS;seed++){
                            Sample active=run(tech,RocketBarrage.Size.SIEGE,stages[0],
                                    4,barrage,motor,moving,true,seed,0,1.0,true,
                                    stages[1],1.0,channels);
                            launched+=active.launched();killed+=active.intercepted();
                            inner+=active.terminalEntries();impacts+=active.collided();
                            shots+=active.pdShots();
                        }
                        double escaped=launched==0?0:(launched-killed)/(double)launched;
                        double terminal=launched==0?0:inner/(double)launched;
                        opticalRows.add(String.format(Locale.ROOT,
                                "%d,%d,%s,%s,%d,%d,%d,%d,%.6f,%d,%.6f,%d,%d,%d",
                                stages[0],stages[1],motor,moving,channels,
                                launched,killed,launched-killed,escaped,inner,terminal,
                                impacts,offlineHits,shots));
                        detail.append(String.format(Locale.ROOT,
                                "|%d|%d|%s|%s|%d|%d|%d|%.1f%%|%.1f%%|%d|%d|%d|\n",
                                stages[0],stages[1],motor,moving,channels,
                                launched,killed,escaped*100,terminal*100,
                                impacts,offlineHits,shots));
                    }
                }
        Files.write(output.resolve("optical-track-capacity-ab.csv"),
                opticalRows,StandardCharsets.UTF_8);
        detail.append("\nTracks/mount = 0 is the ACTUAL existing gameplay controller "+
                "with no overload. A positive number is an opt-in laboratory "+
                "model: detection remains per purchased socket, but optical "+
                "lock time scales with sqrt(max(1, visible tracks / "+
                "(fitted emitters * channels per emitter))). No missile receives "+
                "a forced accuracy miss or a free interception; outcomes use "+
                "real 30Hz trajectories, ammunition and hit resolution. "+
                "No result is a release gate.\n");
        summary=detail.toString();
        Files.writeString(output.resolve("summary.md"),summary,StandardCharsets.UTF_8);
        System.out.println(summary);
        System.out.println("ROCKET_PD_DIAGNOSTIC_CSV="+output.resolve("ladder.csv"));
    }
}
