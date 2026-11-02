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
        KINETIC,
        MISSILE_SINGLE, MISSILE_RIPPLE, MISSILE_SATURATION,
        MISSILE_MICRO_SWARM, MISSILE_HEAVY_STRIKE,
        MISSILE_COLD_LAUNCH, MISSILE_DECOY_CLOUD,
        MISSILE_CLUSTER_CARRIER, MISSILE_CASCADE_SWARM,
        TORPEDO_SINGLE, TORPEDO_SATURATION,
        TORPEDO_HEAVY_STRIKE, TORPEDO_DECOY_CLOUD,
        TORPEDO_CLUSTER_CARRIER, TORPEDO_CASCADE_SWARM,
        FIGHTER_INTERCEPTOR, FIGHTER_ASSAULT_GUNSHIP,
        FIGHTER_LASER_FIGHTER, FIGHTER_PARTICLE_FIGHTER,
        FIGHTER_MISSILE_FIGHTER, FIGHTER_TORPEDO_BOMBER, DRONE;
        boolean guided(){return name().startsWith("MISSILE_")||name().startsWith("TORPEDO_");}
        boolean craft(){return name().startsWith("FIGHTER_")||this==DRONE;}
        boolean heavy(){return name().startsWith("TORPEDO_");}
        OrdnanceDoctrine.Mode pattern(){
            String n=name();
            return OrdnanceDoctrine.Mode.valueOf(n.substring(n.indexOf('_')+1));
        }
        FighterModel.Hull fighter(){
            return FighterModel.Hull.valueOf(name().substring("FIGHTER_".length()));
        }
    }
    private record Result(Threat threat,int pdTier,int pdMounts,int seed,
            BattleOrders.PointDefense mode,int shots,int primaryLaunched,
            int primaryIntercepted,int terminalEntries,int primaryHits,
            int decoysLaunched,int decoysIntercepted,int childrenLaunched,
            int carrierSplits,int secondaryLaunched,int secondaryIntercepted,
            int craftLost,int fighterShots,int paidRounds,
            double shieldArmorHullLoss,double powerUsed,double heatPeak,
            double pdTracking,double pdRange) {
        String csv(){
            return String.format(Locale.ROOT,
                "%s,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                threat,pdTier,pdMounts,seed,mode,shots,primaryLaunched,
                primaryIntercepted,terminalEntries,primaryHits,decoysLaunched,
                decoysIntercepted,childrenLaunched,carrierSplits,
                secondaryLaunched,secondaryIntercepted,craftLost,fighterShots,
                paidRounds,shieldArmorHullLoss,powerUsed,heatPeak,pdTracking,pdRange);
        }
    }
    private static void require(boolean test,String what){
        if(!test)throw new IllegalStateException(what);
    }
    private static Shipyard.Design pdFitting(TechnologyCatalog catalog,int tier,int mounts){
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int count=0;
        for(Shipyard.Slot slot:fit.hull.slots){
            if(count>=mounts)break;
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            fit=fit.install(slot.index(),"BASE-TER-PD");
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
        Battle battle=new Battle(8312009L+seed*101L+tier*17L+mounts*23L+
                threat.ordinal()*193L);
        Battle.Ship attacker;
        if(threat.name().startsWith("FIGHTER_")){
            battle.spawnWing(0,fighterFitting(threat.fighter(),catalog),catalog,1,180,330);
            attacker=battle.ships().get(0);
        }else{
            attacker=battle.addShip(0,Battle.Faction.TERRAN,180,330);
        }
        battle.spawnWing(1,pdFitting(catalog,tier,mounts),catalog,1,570,330);
        Battle.Ship defender=battle.ships().get(1);
        battle.registerDiagnosticStationaryTarget(defender.id);
        battle.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(pdMode));
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
        if(threat==Threat.KINETIC){
            attacker.kineticDamage=26;attacker.kineticAmmo=16;
            initialRounds=attacker.kineticAmmo;
        }else if(threat.guided()){
            if(threat.heavy()){attacker.torpedoDamage=65;attacker.torpedoAmmo=40;}
            else {attacker.missileDamage=35;attacker.missileAmmo=40;}
            initialRounds=threat.heavy()?attacker.torpedoAmmo:attacker.missileAmmo;
            battle.setShipLaunchMode(attacker.id,
                    threat.heavy()?Battle.DamageType.TORPEDO:Battle.DamageType.MISSILE,
                    threat.pattern());
        }else if(threat==Threat.DRONE){
            attacker.droneReserve=1;
        }else{
            attacker.fighterReserve=1;
        }

        Map<Long,String> bodies=new HashMap<>();
        Set<Long> terminal=new HashSet<>();
        Set<Integer> craftTerminal=new HashSet<>();
        int craftLostBefore=battle.craftLosses;
        final int observationTicks=600;
        for(int tick=0;tick<observationTicks;tick++){
            battle.step();
            if(tick==0){
                // Let only the initial full guided volley fire through the
                // real queued launcher. No later automatic volleys.
                attacker.missileReload=attacker.torpedoReload=10000;
                attacker.fighterReserve=0;
                attacker.droneReserve=0;
            }
            if(tick==65&&threat==Threat.KINETIC)
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
                secondary=0,secondaryIntercepts=0,fighterShots=0;
        int decoys=0,decoyKills=0;
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.phase()==Battle.VisualPhase.FIRE && event.sourceId()==attacker.id
                    &&event.team()==0){
                String weapon=event.weapon();
                boolean launched=threat.craft()?
                        ("FIGHTER".equals(weapon)||"DRONE".equals(weapon)):
                        (threat==Threat.KINETIC?"KINETIC".equals(weapon):
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
                    &&"POINT_DEFENSE".equals(event.weapon()))pdShots++;
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
                }else if(threat.craft()&&event.phase()==Battle.VisualPhase.INTERCEPT)
                    secondaryIntercepts++;
            }
        // The craft themselves use integer craft IDs rather than projectile
        // shot IDs. Their PD damage is recorded as POINT_DEFENSE IMPACT.
        int craftKilled=threat.craft()?battle.craftLosses-craftLostBefore:0;
        int terminalCount=threat.craft()?craftTerminal.size():terminal.size();
        int spent=threat==Threat.KINETIC?initialRounds-attacker.kineticAmmo:
                threat.guided()?initialRounds-(threat.heavy()?attacker.torpedoAmmo:
                        attacker.missileAmmo):attacker.fightersLaunched+attacker.dronesLaunched;
        double protectionLoss=Math.max(0,initialShield-defender.shields)+
                Math.max(0,initialArmor-defender.armor)+
                Math.max(0,initialHull-defender.hull);
        require(primary>0,"Real projectile/craft did not launch for "+threat);
        if(pdMode==BattleOrders.PointDefense.HOLD_FIRE)
            require(pdShots==0&&primaryIntercepts==0&&craftKilled==0,
                    "Disabled PD still intercepted "+threat);
        require(decoys==battle.decoysLaunched,
                "Decoy event count not equal to real physical launches");
        require(decoyKills<=decoys&&primaryIntercepts<=primary,
                "Invalid interceptor accounting for "+threat);
        return new Result(threat,tier,mounts,seed,pdMode,pdShots,primary,
                primaryIntercepts,terminalCount,primaryImpacts,decoys,decoyKills,
                battle.submunitionsDeployed,battle.carriersSplit,secondary,
                secondaryIntercepts,craftKilled,fighterShots,spent,
                protectionLoss,initialPower-defender.power,heatPeak,
                defender.pointDefenseProfile.tracking(),defender.pdRange);
    }
    public static void main(String[] args)throws IOException{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Path output=Path.of("test-output","pd-interception-matrix");
        Files.createDirectories(output);
        List<String> csv=new ArrayList<>();
        csv.add("threat,pd_rank,pd_mounts,seed,pd_mode,pd_shots,launched,intercepted,terminal_entries,impacts,decoys,decoys_intercepted,children_launched,carriers_split,secondary_launched,secondary_intercepted,craft_lost,fighter_shots,paid_units,protection_loss,power_spent_net,heat_peak,pd_tracking,pd_range");
        StringBuilder summary=new StringBuilder("# Full point-defense interception matrix\n\n");
        summary.append("All results are measured in real headless 30 Hz Battle simulation. ")
                .append("The ship-launched projectile test source is a legacy fixture; ")
                .append("defenders use real installed research-tier PD modules. ")
                .append("No-PD controls use the same installed hardware with HOLD_FIRE. ")
                .append("Balance outcomes never block release.\n\n");
        summary.append("|Threat|PD tier|Mounts|Trials|Launched|Intercepted|Intercept %|90-unit entries|Impact|PD shots|No-PD impacts|\n");
        summary.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int cases=0;
        for(Threat threat:Threat.values())
            for(int rank:new int[]{0,9})
                for(int mounts:new int[]{1,4}){
                    int fired=0,intercepts=0,entries=0,impacts=0,shots=0,
                            baselineImpact=0;
                    for(int seed=0;seed<2;seed++){
                        Result sample=run(catalog,threat,rank,mounts,seed,
                                BattleOrders.PointDefense.BALANCED);
                        csv.add(sample.csv());cases++;
                        fired+=sample.primaryLaunched;
                        intercepts+=threat.craft()?sample.craftLost:sample.primaryIntercepted;
                        entries+=sample.terminalEntries;
                        impacts+=sample.primaryHits;
                        shots+=sample.shots;
                        if(seed==0){
                            Result control=run(catalog,threat,rank,mounts,seed,
                                    BattleOrders.PointDefense.HOLD_FIRE);
                            csv.add(control.csv());cases++;
                            baselineImpact+=control.primaryHits;
                        }
                    }
                    double interception=fired==0?0:100.0*intercepts/fired;
                    summary.append(String.format(Locale.ROOT,
                            "|%s|%d|%d|2|%d|%d|%.1f%%|%d|%d|%d|%d|\n",
                            threat,rank,mounts,fired,intercepts,interception,
                            entries,impacts,shots,baselineImpact));
                }
        summary.append("\n**Interception vs penetration:** 'intercepted' is a ")
                .append("physical destroyed body, not an evasive miss. ")
                .append("90-unit entries indicate threats that reached the ")
                .append("terminal defensive zone, not guaranteed hull hits. ")
                .append("Guided cluster children are included as independent ")
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
