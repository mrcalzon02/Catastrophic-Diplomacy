package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advisory measurement of ALL physical objects the actual Battle PD loops
 * can shoot: shells, guided/decoy/submunition bodies, and hostile craft.
 * The PD defender has real matched research hardware; the legacy launcher
 * fixture uses actual finite-ammo Battle projectiles with provisioned power.
 * No artificial kill probabilities, no forced hits and no balance pass gate.
 */
public final class PointDefenseThreatMatrixDiagnostic {
    private PointDefenseThreatMatrixDiagnostic(){}
    private enum Threat {
        KINETIC_STANDARD, KINETIC_ARMOR_PIERCING, KINETIC_FRAGMENTATION,
        KINETIC_HIGH_EXPLOSIVE, KINETIC_RELATIVISTIC,
        MISSILE_WARHEAD_ARMOR_PIERCING, MISSILE_WARHEAD_FRAGMENTATION,
        MISSILE_WARHEAD_HIGH_EXPLOSIVE, MISSILE_WARHEAD_EMP,
        MISSILE_WARHEAD_SHIELD_BREAKER,
        TORPEDO_WARHEAD_ARMOR_PIERCING, TORPEDO_WARHEAD_FRAGMENTATION,
        TORPEDO_WARHEAD_HIGH_EXPLOSIVE, TORPEDO_WARHEAD_EMP,
        TORPEDO_WARHEAD_SHIELD_BREAKER,
        MISSILE_SINGLE, MISSILE_RIPPLE, MISSILE_SATURATION,
        MISSILE_MICRO_SWARM, MISSILE_HEAVY_STRIKE,
        MISSILE_COLD_LAUNCH, MISSILE_DECOY_CLOUD,
        MISSILE_CLUSTER_CARRIER, MISSILE_CASCADE_SWARM,
        TORPEDO_SINGLE, TORPEDO_SATURATION,
        TORPEDO_HEAVY_STRIKE, TORPEDO_DECOY_CLOUD,
        TORPEDO_CLUSTER_CARRIER, TORPEDO_CASCADE_SWARM,
        FIGHTER_INTERCEPTOR, FIGHTER_ASSAULT_GUNSHIP,
        FIGHTER_LASER_FIGHTER, FIGHTER_PARTICLE_FIGHTER,
        FIGHTER_MISSILE_FIGHTER, FIGHTER_TORPEDO_BOMBER, DRONE, REPAIR_DRONE;
        boolean kinetic(){return name().startsWith("KINETIC_");}
        boolean guided(){return name().startsWith("MISSILE_")||name().startsWith("TORPEDO_");}
        AmmunitionTable.Kinetic kineticRound(){
            return AmmunitionTable.Kinetic.valueOf(name().substring("KINETIC_".length()));
        }
        AmmunitionTable.Warhead warhead(){
            int start=name().indexOf("_WARHEAD_");
            return start<0?AmmunitionTable.Warhead.STANDARD:
                    AmmunitionTable.Warhead.valueOf(name().substring(start+9));
        }
        boolean craft(){return name().startsWith("FIGHTER_")||this==DRONE||this==REPAIR_DRONE;}
        boolean heavy(){return name().startsWith("TORPEDO_");}
        OrdnanceDoctrine.Mode pattern(){
            String n=name();
            return n.contains("_WARHEAD_")?OrdnanceDoctrine.Mode.SINGLE:
                    OrdnanceDoctrine.Mode.valueOf(n.substring(n.indexOf('_')+1));
        }
        FighterModel.Hull fighter(){
            return FighterModel.Hull.valueOf(name().substring("FIGHTER_".length()));
        }
    }
    private record Result(Threat threat,int pdTier,int pdMounts,int separation,int seed,
            BattleOrders.PointDefense mode,int shots,int primaryLaunched,
            int primaryIntercepted,int terminalEntries,int primaryHits,
            int decoysLaunched,int decoysIntercepted,int childrenLaunched,
            int carrierSplits,int secondaryLaunched,int secondaryIntercepted,
            int secondaryHits,int craftLost,int fighterShots,int paidRounds,
            double shieldArmorHullLoss,double powerUsed,double heatPeak,
            double pdTracking,double pdRange) {
        String csv(){
            return String.format(Locale.ROOT,
                "%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                threat,pdTier,pdMounts,separation,seed,mode,shots,primaryLaunched,
                primaryIntercepted,terminalEntries,primaryHits,decoysLaunched,
                decoysIntercepted,childrenLaunched,carrierSplits,
                secondaryLaunched,secondaryIntercepted,secondaryHits,craftLost,fighterShots,
                paidRounds,shieldArmorHullLoss,powerUsed,heatPeak,pdTracking,pdRange);
        }
    }
    private static void require(boolean test,String what){
        if(!test)throw new IllegalStateException(what);
    }
    private static Shipyard.Design pdFitting(TechnologyCatalog catalog,int tier,int mounts){
        return pdFitting(catalog,tier,mounts,"PD");
    }
    private static Shipyard.Design pdFitting(TechnologyCatalog catalog,int tier,int mounts,
                                           String family){
        require("PD".equals(family)||"FLK".equals(family),"Unknown physical defense family");
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int count=0;
        for(Shipyard.Slot slot:fit.hull.slots){
            if(count>=mounts)break;
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            fit=fit.install(slot.index(),"BASE-TER-"+family);
            if(tier>0)fit=fit.stage(slot.index(),Shipyard.Track.SPECIALIST,tier);
            count++;
        }
        require(count==mounts,"Missing physical PD hardpoints");
        require(Shipyard.inspect(fit,catalog).valid(),"Invalid fitted PD module tier "+tier);
        return fit;
    }
    private static Shipyard.Design fighterFitting(FighterModel.Hull aircraft,
                                                   TechnologyCatalog catalog){
        Shipyard.Design fit=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        for(Shipyard.Slot slot:fit.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                fit=fit.install(slot.index(),"BASE-TER-FTR").withFighterHull(aircraft);
                require(Shipyard.inspect(fit,catalog).valid(),
                        "Invalid real fighter deck for "+aircraft);
                return fit;
            }
        throw new IllegalStateException("Missing fighter bay socket");
    }
    private static Result run(TechnologyCatalog catalog,Threat threat,int tier,
                              int mounts,int seed,BattleOrders.PointDefense pdMode){
        return run(catalog,threat,tier,mounts,seed,pdMode,390);
    }
    private static Result run(TechnologyCatalog catalog,Threat threat,int tier,
                              int mounts,int seed,BattleOrders.PointDefense pdMode,
                              int separation){
        return run(catalog,threat,tier,mounts,seed,pdMode,separation,"PD");
    }
    private static Result run(TechnologyCatalog catalog,Threat threat,int tier,
                              int mounts,int seed,BattleOrders.PointDefense pdMode,
                              int separation,String pdFamily){
        return run(catalog,threat,tier,mounts,seed,pdMode,
                separation,pdFamily,true);
    }
    private static Result run(TechnologyCatalog catalog,Threat threat,int tier,
                              int mounts,int seed,BattleOrders.PointDefense pdMode,
                              int separation,String pdFamily,boolean radialFalloff){
        return run(catalog,threat,tier,mounts,seed,pdMode,separation,
                pdFamily,radialFalloff,null);
    }
    private static Result run(TechnologyCatalog catalog,Threat threat,int tier,
                              int mounts,int seed,BattleOrders.PointDefense pdMode,
                              int separation,String pdFamily,boolean radialFalloff,
                              ShipTacticalDirectives.InterceptStrategy interception){
        if(separation!=95&&separation!=220&&separation!=390)
            throw new IllegalArgumentException("Invalid physical diagnostic range");
        Battle battle=new Battle(8312009L+seed*101L+tier*17L+mounts*23L+
                threat.ordinal()*193L);
        // A/B explicitly selects the legacy uniform field or the physical
        // production falloff. Never infer from the battle's current default.
        battle.configureFlakFragmentFalloffForDiagnostic(radialFalloff);
        Battle.Ship attacker;
        if(threat.name().startsWith("FIGHTER_")){
            battle.spawnWing(0,fighterFitting(threat.fighter(),catalog),catalog,1,180,330);
            attacker=battle.ships().get(0);
        }else{
            attacker=battle.addShip(0,Battle.Faction.TERRAN,180,330);
        }
        battle.spawnWing(1,pdFitting(catalog,tier,mounts,pdFamily),catalog,1,
                180+separation,330);
        Battle.Ship defender=battle.ships().get(1);
        battle.registerDiagnosticStationaryTarget(defender.id);
        battle.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(pdMode));
        if(interception!=null)
            battle.setShipStandingOrders(defender.id,new ShipStandingOrders(
                    BattleOrders.DEFAULT.withPointDefense(pdMode),50,50,0,
                    ShipTacticalDirectives.DEFAULT.withStrategy(interception)));
        // Isolation: only the selected threat may launch. The defender has
        // real fitted sensors, reload, power, heat, damage and PD condition.
        attacker.speed=0;attacker.vx=0;attacker.vy=0;attacker.pdRange=0;
        attacker.laserDamage=attacker.kineticDamage=attacker.missileDamage=0;
        attacker.torpedoDamage=attacker.plasmaDamage=attacker.particleDamage=0;
        attacker.fighterReserve=attacker.droneReserve=attacker.repairDroneReserve=0;
        attacker.missileAmmo=attacker.torpedoAmmo=attacker.kineticAmmo=0;
        attacker.maxPower=attacker.power=10000;attacker.maxHeat=10000;
        attacker.powerRegen=0;
        attacker.maxHull=attacker.hull=100000;
        defender.speed=0;defender.vx=0;defender.vy=0;
        defender.maxHull=defender.hull=1000000;
        double initialHull=defender.hull,initialShield=defender.shields,
                initialArmor=defender.armor,initialPower=defender.power;
        double heatPeak=defender.heat;
        int initialRounds=0;
        if(threat.kinetic()){
            attacker.kineticDamage=26;attacker.kineticAmmo=16;
            initialRounds=attacker.kineticAmmo;
            battle.setShipKineticAmmunition(attacker.id,threat.kineticRound());
        }else if(threat.guided()){
            if(threat.heavy()){attacker.torpedoDamage=65;attacker.torpedoAmmo=40;}
            else {attacker.missileDamage=35;attacker.missileAmmo=40;}
            initialRounds=threat.heavy()?attacker.torpedoAmmo:attacker.missileAmmo;
            Battle.DamageType family=threat.heavy()?Battle.DamageType.TORPEDO:
                    Battle.DamageType.MISSILE;
            battle.setShipLaunchMode(attacker.id,family,threat.pattern());
            battle.setShipWarhead(attacker.id,family,threat.warhead());
        }else if(threat==Threat.DRONE){
            attacker.droneReserve=1;
        }else if(threat==Threat.REPAIR_DRONE){
            // Physical support drones target allies rather than a hostile
            // ship. Keep a damaged friendly hull in range to cause a real
            // launch, then observe whether hostile PD can acquire it.
            Battle.Ship casualty=battle.addShip(0,Battle.Faction.TERRAN,192,346);
            casualty.hull=casualty.maxHull*.55;
            casualty.laserDamage=casualty.missileDamage=0;
            casualty.kineticDamage=casualty.torpedoDamage=0;
            casualty.plasmaDamage=casualty.particleDamage=0;
            casualty.fighterReserve=casualty.droneReserve=0;
            casualty.pdRange=0;
            battle.registerDiagnosticStationaryTarget(casualty.id);
            attacker.repairDroneReserve=1;
        }else{
            attacker.fighterReserve=1;
        }

        Map<Long,String> bodies=new HashMap<>();
        Set<Long> terminal=new HashSet<>();
        Set<Integer> craftTerminal=new HashSet<>();
        int craftLostBefore=battle.craftLosses;
        final int observationTicks=600;
        boolean firstSalvoScheduled=false;
        for(int tick=0;tick<observationTicks;tick++){
            battle.step();
            if(tick==0){
                // Only the auxiliary stock is suppressed at first tick.
                // Guided launch authorization needs arrived historical
                // photons, which may not exist until several ticks later.
                attacker.fighterReserve=0;
                attacker.droneReserve=0;
                attacker.repairDroneReserve=0;
            }
            if(threat.guided()&&!firstSalvoScheduled&&battle.salvosStarted>0){
                // Stop SECOND volleys only after the first real fire-control
                // solution has scheduled a salvo. Existing PendingLaunch
                // bodies continue with their original measured targeting
                // data, paid ammo, physical missile travel and PD exposure.
                attacker.missileReload=attacker.torpedoReload=10000;
                firstSalvoScheduled=true;
            }
            if(tick==65&&threat.kinetic())
                attacker.kineticDamage=0; // End realistic initial gun train.
            for(Battle.Missile m:battle.missiles())
                if(m.team==0 && StrictMath.hypot(m.x-defender.x,m.y-defender.y)<=90)
                    terminal.add(m.visualId);
            for(Battle.Ballistic shell:battle.ballistics())
                if(shell.team==0 && StrictMath.hypot(shell.x-defender.x,shell.y-defender.y)<=90)
                    terminal.add(shell.visualId);
            for(Battle.Craft craft:battle.crafts())
                if(craft.team==0&&StrictMath.hypot(craft.x-defender.x,craft.y-defender.y)<=90)
                    craftTerminal.add(craft.id);
            heatPeak=Math.max(heatPeak,defender.heat);
        }
        int pdShots=0,primary=0,primaryIntercepts=0,primaryImpacts=0,
                secondary=0,secondaryIntercepts=0,secondaryHits=0,fighterShots=0;
        int decoys=0,decoyKills=0;
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.phase()==Battle.VisualPhase.FIRE && event.sourceId()==attacker.id
                    &&event.team()==0){
                String weapon=event.weapon();
                boolean launched=threat.craft()?
                        ("FIGHTER".equals(weapon)||"DRONE".equals(weapon)||
                         "REPAIR_DRONE".equals(weapon)):
                        (threat.kinetic()?"KINETIC".equals(weapon):
                        "MISSILE".equals(weapon)||"TORPEDO".equals(weapon)||
                        "DECOY".equals(weapon));
                if(launched){
                    bodies.put(event.shotId(),weapon);
                    primary++;
                    if("DECOY".equals(weapon))decoys++;
                }else if(weapon.startsWith("FIGHTER_")){
                    secondary++;
                    fighterShots++;
                }
            }
            if(event.phase()==Battle.VisualPhase.FIRE && event.team()==1
                    &&event.sourceId()==defender.id
                    &&("POINT_DEFENSE".equals(event.weapon())||
                        "FLAK".equals(event.weapon())))pdShots++;
        }
        for(Battle.VisualEvent event:battle.visualEvents())
            if(event.team()==0 && event.sourceId()==attacker.id){
                if(bodies.containsKey(event.shotId())){
                    if(event.phase()==Battle.VisualPhase.INTERCEPT){
                        primaryIntercepts++;
                        if("DECOY".equals(bodies.get(event.shotId())))decoyKills++;
                    }
                    if(event.phase()==Battle.VisualPhase.IMPACT){
                        primaryImpacts++;
                        terminal.add(event.shotId());
                    }
                }else if(threat.craft()){
                    if(event.phase()==Battle.VisualPhase.INTERCEPT)
                        secondaryIntercepts++;
                    if(event.phase()==Battle.VisualPhase.IMPACT)
                        secondaryHits++;
                }
            }
        // The craft themselves use integer craft IDs rather than projectile
        // shot IDs. Their PD damage is recorded as POINT_DEFENSE IMPACT.
        int craftKilled=threat.craft()?battle.pointDefenseCraftKills:0;
        int naturalCraftLosses=battle.craftLosses-craftLostBefore-craftKilled;
        require(naturalCraftLosses>=0,
                "Non-defense craft attrition counter underflow");
        int terminalCount=threat.craft()?craftTerminal.size():terminal.size();
        int spent=threat.kinetic()?initialRounds-attacker.kineticAmmo:
                threat.guided()?initialRounds-(threat.heavy()?attacker.torpedoAmmo:
                        attacker.missileAmmo):attacker.fightersLaunched+attacker.dronesLaunched;
        double protectionLoss=Math.max(0,initialShield-defender.shields)+
                Math.max(0,initialArmor-defender.armor)+
                Math.max(0,initialHull-defender.hull);
        require(primary>0,"Real projectile/craft did not launch for "+threat+
                " at rank="+tier+" mounts="+mounts+" seed="+seed+
                " range="+separation+" family="+pdFamily+
                " pd="+pdMode+" tick="+battle.tick+
                " salvos="+battle.salvosStarted+
                " rounds="+battle.roundsFiredBySalvos+
                " ammoLeft="+attacker.missileAmmo+"/"+attacker.torpedoAmmo);
        if(pdMode==BattleOrders.PointDefense.HOLD_FIRE)
            require(pdShots==0&&primaryIntercepts==0&&craftKilled==0,
                    "Disabled PD still intercepted "+threat);
        require(decoys==battle.decoysLaunched,
                "Decoy event count not equal to real physical launches");
        require(decoyKills<=decoys&&primaryIntercepts<=primary,
                "Invalid interceptor accounting for "+threat);
        return new Result(threat,tier,mounts,separation,seed,pdMode,pdShots,primary,
                primaryIntercepts,terminalCount,primaryImpacts,decoys,decoyKills,
                battle.submunitionsDeployed,battle.carriersSplit,secondary,
                secondaryIntercepts,secondaryHits,craftKilled,fighterShots,spent,
                protectionLoss,initialPower-defender.power,heatPeak,
                defender.pointDefenseProfile.tracking(),defender.pdRange);
    }
    /** P0 family comparison: real fitted existing PD versus existing FLK.
     * Deliberately records actual current combat behavior before the redesign.
     * Attackers remain legacy base-tier physical launch fixtures, so these
     * results are NOT IX attacker versus IX defender parity measurements. */
    private static void familyP0(TechnologyCatalog catalog)throws IOException{
        require(Battle.flakFragmentCoverage(0,20,0)==1,
                "Flak core lost full physical fragment density");
        require(Battle.flakFragmentCoverage(20,20,0)==0,
                "Flak outside edge created free damage");
        require(Battle.flakFragmentCoverage(10,20,0)>.5&&
                Battle.flakFragmentCoverage(10,20,0)<1,
                "Flak radial fragment field has no spatial gradient");
        require(Battle.flakFragmentCoverage(25,20,10)>0,
                "Physically extended aircraft do not overlap flak rim");
        Path output=Path.of("test-output","pd-family-p0");
        Files.createDirectories(output);
        List<String> csv=new ArrayList<>();
        csv.add("defense_family,threat,attack_stage,pd_stage,mounts,separation,seeds,launched,physical_intercepts,intercept_pct,terminal_entries,actual_impacts,hold_fire_impacts,pd_shots,defender_damage,hold_fire_damage,result_status");
        StringBuilder summary=new StringBuilder("# Physical PD versus FLK: P0 comparison\n\n");
        summary.append("All defender modules are genuine equipped Terran weapon hardpoints; PD and FLK use their actual existing combat paths. ")
                .append("The attacker is an unupgraded real physical launcher fixture, not a stage-IX fitted attacker. ")
                .append("All balance readings are advisory. No other PD families are fabricated.\n\n");
        summary.append("|Threat|Range|Defense|Def tier|Mounts|Launched|Intercepted|Kill %|Terminal|Impacts|Hold-fire impacts|PD shots|\n");
        summary.append("|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        Threat[] threats={Threat.KINETIC_STANDARD,Threat.KINETIC_RELATIVISTIC,
                Threat.MISSILE_MICRO_SWARM,Threat.MISSILE_CASCADE_SWARM,
                Threat.TORPEDO_HEAVY_STRIKE,Threat.FIGHTER_INTERCEPTOR,
                Threat.FIGHTER_TORPEDO_BOMBER};
        final int measurementSeeds=8;
        for(Threat threat:threats)
            for(int range:threat.craft()?new int[]{95,220,390}:new int[]{390})
            for(String family:new String[]{"PD","FLK"})
                for(int rank:new int[]{0,9})
                    for(int mounts:new int[]{1,4}){
                        int fired=0,killed=0,entries=0,impacts=0,controlImpacts=0,shots=0;
                        double damage=0,controlDamage=0;
                        for(int seed=0;seed<measurementSeeds;seed++){
                            Result active=run(catalog,threat,rank,mounts,seed,
                                    BattleOrders.PointDefense.BALANCED,range,family);
                            Result control=run(catalog,threat,rank,mounts,seed,
                                    BattleOrders.PointDefense.HOLD_FIRE,range,family);
                            fired+=active.primaryLaunched;
                            killed+=threat.craft()?active.craftLost:active.primaryIntercepted;
                            entries+=active.terminalEntries;
                            impacts+=threat.craft()?active.secondaryHits:active.primaryHits;
                            controlImpacts+=threat.craft()?control.secondaryHits:control.primaryHits;
                            shots+=active.shots;
                            damage+=active.shieldArmorHullLoss;
                            controlDamage+=control.shieldArmorHullLoss;
                        }
                        require(fired>0,"No real physical launch in P0 "+threat+"/"+family);
                        double rate=100.0*killed/fired;
                        csv.add(String.format(Locale.ROOT,
                                "%s,%s,0,%d,%d,%d,%d,%d,%d,%.3f,%d,%d,%d,%d,%.4f,%.4f,MEASURED_LEGACY_ATTACKER",
                                family,threat,rank,mounts,range,measurementSeeds,
                                fired,killed,rate,
                                entries,impacts,controlImpacts,shots,damage,controlDamage));
                        summary.append(String.format(Locale.ROOT,
                                "|%s|%d|%s|%d|%d|%d|%d|%.1f%%|%d|%d|%d|%d|\n",
                                threat,range,family,rank,mounts,fired,killed,rate,
                                entries,impacts,controlImpacts,shots));
                    }
        // Directly measured radial-fragment cloud behavior against legacy
        // same-seed full-density bursts. Actual FLK hardware and ammunition
        // are identical between arms. One batch per research tier, mount count
        // and weapon role; fighter ranges use the real 220-unit carrier run.
        List<String> radialCsv=new ArrayList<>();
        radialCsv.add("threat,defense_stage,flak_mounts,separation,seeds,"+
                "launched,legacy_kills,radial_kills,legacy_actual_impacts,"+
                "radial_actual_impacts,legacy_pd_shots,radial_pd_shots,"+
                "legacy_damage,radial_damage,role");
        summary.append("\n## Measured FLK burst falloff A/B (8 paired attack seeds)\n\n");
        summary.append("Normal gameplay now uses radial fragmentation. Both"+
                " configurations fire real timed FLK shells and consume"+
                " normal kinetic ammunition. The legacy full-density field"+
                " is retained only as a diagnostic control. Laser PD, rockets"+
                " and ECM are not modified.\n\n");
        summary.append("|Threat|Range|Tier|FLK mounts|Launched|Legacy killed|"+
                "Falloff killed|Legacy impacts|Falloff impacts|"+
                "Legacy FLK shots|Falloff FLK shots|\n");
        summary.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(Threat threat:new Threat[]{Threat.KINETIC_STANDARD,
                Threat.KINETIC_RELATIVISTIC,Threat.MISSILE_MICRO_SWARM,
                Threat.MISSILE_CASCADE_SWARM,Threat.TORPEDO_HEAVY_STRIKE,
                Threat.FIGHTER_INTERCEPTOR,Threat.FIGHTER_TORPEDO_BOMBER}){
            int range=threat.craft()?220:390;
            for(int rank:new int[]{0,9})for(int mounts:new int[]{1,4}){
                int launched=0,oldKills=0,newKills=0,oldImpacts=0,
                        newImpacts=0,oldShots=0,newShots=0;
                double oldDamage=0,newDamage=0;
                for(int seed=0;seed<measurementSeeds;seed++){
                    Result legacy=run(catalog,threat,rank,mounts,seed,
                            BattleOrders.PointDefense.BALANCED,range,"FLK",false);
                    Result radial=run(catalog,threat,rank,mounts,seed,
                            BattleOrders.PointDefense.BALANCED,range,"FLK",true);
                    require(legacy.primaryLaunched()==radial.primaryLaunched(),
                            "Flak spatial model changed actual attacking launches");
                    launched+=legacy.primaryLaunched();
                    oldKills+=threat.craft()?legacy.craftLost():
                            legacy.primaryIntercepted();
                    newKills+=threat.craft()?radial.craftLost():
                            radial.primaryIntercepted();
                    oldImpacts+=threat.craft()?legacy.secondaryHits():
                            legacy.primaryHits();
                    newImpacts+=threat.craft()?radial.secondaryHits():
                            radial.primaryHits();
                    oldShots+=legacy.shots();newShots+=radial.shots();
                    oldDamage+=legacy.shieldArmorHullLoss();
                    newDamage+=radial.shieldArmorHullLoss();
                }
                String role=threat.craft()?"AIRCRAFT":threat.kinetic()?
                        "KINETIC":threat.heavy()?"ARMORED_ORDNANCE":"SWARM";
                radialCsv.add(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%s",
                        threat,rank,mounts,range,measurementSeeds,
                        launched,oldKills,newKills,oldImpacts,newImpacts,
                        oldShots,newShots,oldDamage,newDamage,role));
                summary.append(String.format(Locale.ROOT,
                        "|%s|%d|%d|%d|%d|%d|%d|%d|%d|%d|%d|\n",
                        threat,range,rank,mounts,launched,oldKills,newKills,
                        oldImpacts,newImpacts,oldShots,newShots));
            }
        }
        Files.write(output.resolve("flak-radial-ab.csv"),radialCsv,
                StandardCharsets.UTF_8);
        summary.append("\nFLK radial falloff is the current production"+
                " behavior; the original full-strength rim is an explicit"+
                " pre-battle A/B control. Measurements use real physical"+
                " vessel and aircraft combat, not synthetic accuracy rolls.\n");
        // Verify the three existing doctrine orderings mechanically before
        // interpreting seed outcomes. FLK now uses defensiveScore for ordnance,
        // while aerial targets remain its naturally preferred assignment.
        Battle doctrineFixture=new Battle(118733L);
        Battle.Ship doctrineShip=doctrineFixture.addShip(1,
                Battle.Faction.TERRAN,600,300);
        doctrineFixture.setShipStandingOrders(doctrineShip.id,
                new ShipStandingOrders(BattleOrders.DEFAULT,50,50,0,
                        ShipTacticalDirectives.DEFAULT.withStrategy(
                                ShipTacticalDirectives.InterceptStrategy.EARLY_INTERCEPT)));
        require(doctrineFixture.defensiveScore(doctrineShip,300,3)<
                doctrineFixture.defensiveScore(doctrineShip,100,1),
                "EARLY_INTERCEPT fails to prefer distant physical contact");
        doctrineFixture.setShipStandingOrders(doctrineShip.id,
                new ShipStandingOrders(BattleOrders.DEFAULT,50,50,0,
                        ShipTacticalDirectives.DEFAULT.withStrategy(
                                ShipTacticalDirectives.InterceptStrategy.CLOSE_IN)));
        require(doctrineFixture.defensiveScore(doctrineShip,100,3)<
                doctrineFixture.defensiveScore(doctrineShip,300,1),
                "CLOSE_IN fails to prefer closest physical contact");
        doctrineFixture.setShipStandingOrders(doctrineShip.id,
                new ShipStandingOrders(BattleOrders.DEFAULT,50,50,0,
                        ShipTacticalDirectives.DEFAULT.withStrategy(
                                ShipTacticalDirectives.InterceptStrategy.FASTEST_IMPACT)));
        require(doctrineFixture.defensiveScore(doctrineShip,300,1)<
                doctrineFixture.defensiveScore(doctrineShip,100,3),
                "FASTEST_IMPACT fails to prefer earliest approaching body");

        List<String> doctrineCsv=new ArrayList<>();
        doctrineCsv.add("family,threat,tier,mounts,distance,strategy,seeds,"+
                "launched,killed,actual_impacts,shots,defender_damage,role");
        summary.append("\n## Three live fire-control doctrines: physical target-role matrix\n\n");
        summary.append("Tests preserve independent equipped guns and their real"+
                " stock/reload/thermal constraints. EARLY_INTERCEPT chooses the"+
                " farthest detected ordnance, CLOSE_IN the nearest, and"+
                " FASTEST_IMPACT the shortest closing-time estimate."+
                " FLK prefers hostile aircraft when craft engagement is enabled."+
                " Values from four identical attack seeds per strategy.\n\n");
        summary.append("|Threat|Defense|Rank|Mounts|Doctrine|Launched|Destroyed|"+
                "Threat impacts|Defense shots|Damage incurred|\n");
        summary.append("|---|---|---:|---:|---|---:|---:|---:|---:|---:|\n");
        for(Threat threat:new Threat[]{Threat.KINETIC_STANDARD,
                Threat.MISSILE_MICRO_SWARM,Threat.MISSILE_CASCADE_SWARM,
                Threat.TORPEDO_HEAVY_STRIKE,Threat.FIGHTER_INTERCEPTOR,
                Threat.FIGHTER_TORPEDO_BOMBER})
            for(String family:new String[]{"PD","FLK"})
                for(int rank:new int[]{0,9})
                    for(int mounts:new int[]{1,4})
                        for(var doctrine:ShipTacticalDirectives.InterceptStrategy.values()){
                            int launches=0,kills=0,impacts=0,shots=0;
                            double damage=0;
                            for(int seed=0;seed<4;seed++){
                                Result result=run(catalog,threat,rank,mounts,seed,
                                        BattleOrders.PointDefense.BALANCED,
                                        threat.craft()?220:390,family,true,doctrine);
                                launches+=result.primaryLaunched();
                                kills+=threat.craft()?result.craftLost():
                                        result.primaryIntercepted();
                                impacts+=threat.craft()?result.secondaryHits():
                                        result.primaryHits();
                                shots+=result.shots();
                                damage+=result.shieldArmorHullLoss();
                            }
                            String role=threat.craft()?"ANTI_AIR":
                                    threat.kinetic()?"SHELL":
                                    threat.heavy()?"HARDENED":"SWARM";
                            doctrineCsv.add(String.format(Locale.ROOT,
                                    "%s,%s,%d,%d,%d,%s,4,%d,%d,%d,%d,%.4f,%s",
                                    family,threat,rank,mounts,
                                    threat.craft()?220:390,doctrine,
                                    launches,kills,impacts,shots,damage,role));
                            summary.append(String.format(Locale.ROOT,
                                    "|%s|%s|%d|%d|%s|%d|%d|%d|%d|%.1f|\n",
                                    threat,family,rank,mounts,doctrine,launches,
                                    kills,impacts,shots,damage));
                        }
        Files.write(output.resolve("fire-control-doctrine-matrix.csv"),
                doctrineCsv,StandardCharsets.UTF_8);
        summary.append("\nDoctrines are actual ship captain orders, not invented"+
                " simulated probabilities. High interceptions and high shots"+
                " do not alone indicate damage prevention or cost efficiency."+
                " These are diagnostic-only balance comparisons, not release gates.\n");
        Files.write(output.resolve("p0.csv"),csv,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),summary.toString(),StandardCharsets.UTF_8);
        System.out.print(summary);
        System.out.println("PD_FAMILY_P0_ROWS="+(csv.size()-1));
    }
    /** Focused functional proof before attempting the giant optional P0 /
     * threat-matrix balance tables. No synthetic missiles or fake PD rolls. */
    static void armorPiercingAndMicroSwarmSmoke(TechnologyCatalog catalog){
        for(Threat threat:new Threat[]{
                Threat.MISSILE_WARHEAD_ARMOR_PIERCING,
                Threat.MISSILE_MICRO_SWARM}){
            int expected=threat==Threat.MISSILE_MICRO_SWARM?8:1;
            for(int tier:new int[]{0,9})
                for(int mounts:new int[]{1,4})
                    for(int seed=0;seed<2;seed++){
                        Result control=run(catalog,threat,tier,mounts,seed,
                                BattleOrders.PointDefense.HOLD_FIRE);
                        Result active=run(catalog,threat,tier,mounts,seed,
                                BattleOrders.PointDefense.BALANCED);
                        require(control.primaryLaunched()==expected&&
                                active.primaryLaunched()==expected,
                                "Initial causal salvo launch mismatch for "+threat+
                                " rank="+tier+" mounts="+mounts+" seed="+seed+
                                " control="+control.primaryLaunched()+
                                " active="+active.primaryLaunched());
                        require(control.paidRounds()==expected&&
                                active.paidRounds()==expected,
                                "Salvo used free ammo or changed paid magazine for "+
                                threat+" seed="+seed);
                        require(control.shots()==0&&
                                control.primaryIntercepted()==0,
                                "HOLD_FIRE intercepted actual "+threat+" objects");
                        require(active.primaryIntercepted()<=expected&&
                                active.primaryIntercepted()>=0&&
                                control.primaryHits()<=expected,
                                "Physical PD or impact accounting out of range");
                        if(threat==Threat.MISSILE_MICRO_SWARM)
                            require(active.childrenLaunched()==0&&
                                    control.childrenLaunched()==0,
                                    "Micro-swarm falsely reclassified as a cluster child");
                        System.out.println("PASS "+threat+" rank="+tier+
                                " mounts="+mounts+" seed="+seed+
                                " real="+expected+" pdShots="+active.shots()+
                                " intercepted="+active.primaryIntercepted()+
                                " holdHits="+control.primaryHits()+
                                " defendedHits="+active.primaryHits());
                    }
        }
        System.out.println("PASS AP warhead and 8-body micro swarm: 32 matched active/hold-fire real-physics trials");
    }
    public static void main(String[] args)throws IOException{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        if(args.length==1&&"--ap-micro".equals(args[0])){
            armorPiercingAndMicroSwarmSmoke(catalog);return;
        }
        if(args.length==1&&"--p0".equals(args[0])){familyP0(catalog);return;}
        require(args.length==0,"Unrecognized point-defense diagnostic argument");
        Path output=Path.of("test-output","pd-interception-matrix");
        Files.createDirectories(output);
        List<String> csv=new ArrayList<>();
        csv.add("threat,pd_rank,pd_mounts,launch_distance,seed,pd_mode,pd_shots,launched,intercepted,terminal_entries,impacts,decoys,decoys_intercepted,children_launched,carriers_split,secondary_launched,secondary_intercepted,secondary_hits,craft_lost,fighter_shots,paid_units,protection_loss,power_spent_net,heat_peak,pd_tracking,pd_range");
        StringBuilder summary=new StringBuilder("# Full point-defense interception matrix\n\n");
        summary.append("All results are measured in real headless 30 Hz Battle simulation. ")
                .append("The ship-launched projectile test source is a legacy fixture; ")
                .append("defenders use real installed research-tier PD modules. ")
                .append("Every seed has a paired no-PD control using the same installed hardware with HOLD_FIRE. ")
                .append("Balance outcomes never block release.\n\n");
        summary.append("|Threat|Range|PD tier|Mounts|Trials|Launched|Intercepted|Intercept %|90-unit entries|Impact|PD shots|No-PD impacts|\n");
        summary.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int cases=0;
        for(Threat threat:Threat.values())
            for(int separation:threat.craft()?new int[]{220,390}:new int[]{390})
            for(int rank:new int[]{0,9})
                for(int mounts:new int[]{1,4}){
                    int fired=0,intercepts=0,entries=0,impacts=0,shots=0,
                            baselineImpact=0;
                    for(int seed=0;seed<2;seed++){
                        Result sample=run(catalog,threat,rank,mounts,seed,
                                BattleOrders.PointDefense.BALANCED,separation);
                        csv.add(sample.csv());cases++;
                        fired+=sample.primaryLaunched;
                        intercepts+=threat.craft()?sample.craftLost:sample.primaryIntercepted;
                        entries+=sample.terminalEntries;
                        impacts+=threat.craft()?sample.secondaryHits:sample.primaryHits;
                        shots+=sample.shots;
                        // Every online trial has an identically seeded
                        // offline-PD control, so raw impacts have the same
                        // denominator and can be compared directly.
                        Result control=run(catalog,threat,rank,mounts,seed,
                                BattleOrders.PointDefense.HOLD_FIRE,separation);
                        csv.add(control.csv());cases++;
                        baselineImpact+=threat.craft()?control.secondaryHits:control.primaryHits;
                    }
                    double interception=fired==0?0:100.0*intercepts/fired;
                    summary.append(String.format(Locale.ROOT,
                            "|%s|%d|%d|%d|2|%d|%d|%.1f%%|%d|%d|%d|%d|\n",
                            threat,separation,rank,mounts,fired,intercepts,interception,
                            entries,impacts,shots,baselineImpact));
                }
        // Tactical PD orders must select the real physical target class.
        // These are functional guards, not a tuning pass-rate threshold.
        Result antiCraftOnly=run(catalog,Threat.MISSILE_SINGLE,0,1,0,
                BattleOrders.PointDefense.ANTI_CRAFT);
        require(antiCraftOnly.shots==0&&antiCraftOnly.primaryIntercepted==0,
                "Anti-craft-only PD improperly engaged ship-launched missiles");
        Result ordnanceOnly=run(catalog,Threat.FIGHTER_LASER_FIGHTER,0,1,0,
                BattleOrders.PointDefense.INTERCEPT_ONLY);
        require(ordnanceOnly.shots==0&&ordnanceOnly.craftLost==0,
                "Ordnance-only PD improperly engaged energy-armed aircraft");
        Result noBallistics=run(catalog,Threat.KINETIC_STANDARD,0,1,0,
                BattleOrders.PointDefense.ANTI_CRAFT);
        require(noBallistics.shots==0&&noBallistics.primaryIntercepted==0,
                "Anti-craft-only PD improperly engaged kinetic shells");
        summary.append("\nMode exclusions: ANTI_CRAFT against missile/kinetic "
                + "and INTERCEPT_ONLY against energy-armed fighter "
                + "were explicitly checked for zero unauthorized shots.\n");
        summary.append("\n**Interception vs penetration:** 'intercepted' is a ")
                .append("physical destroyed body, not an evasive miss. ")
                .append("90-unit entries indicate threats that reached the ")
                .append("terminal defensive zone, not guaranteed hull hits. ")
                .append("Fighter-launched kinetic/guided secondary weapons are tracked " +
                "separately from the aircraft shot down. " +
                "Guided cluster children are included as independent ")
                .append("physical FIRE objects; carrier splits and actual ")
                .append("child counts are preserved per CSV row. Fighters and ")
                .append("drones are counted by physical craft losses, not ")
                .append("by their own shots. No-PD controls isolate misses.\n")
                .append("\nScenarios: ").append(cases)
                .append(". PD modes and source stock remain physical; ")
                .append("runtime tuning thresholds are descriptive only.\n");
        Files.write(output.resolve("matrix.csv"),csv,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),summary.toString());
        System.out.println(summary);
        System.out.println("PD_INTERCEPTION_MATRIX_CSV="+output.resolve("matrix.csv"));
    }
}
