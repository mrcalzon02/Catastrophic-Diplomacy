package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional 200v200 headless performance and combat-physics diagnostic.
 *
 * Both sides use real fitted and manufactured hardware; the same seed and
 * deployments are compared with the defender's PD enabled versus HOLD_FIRE.
 * This is NOT a release gate and never enforces a desired hit rate, CPU time,
 * memory footprint, or resource-balance percentage.
 */
public final class PointDefenseFleetScaleDiagnostic {
    private PointDefenseFleetScaleDiagnostic(){}
    private static final int SHIPS_PER_FAMILY=50;
    private static final int TICKS=600; // 20 simulated seconds at 30 Hz
    private static final String[] OFFENSE={
            "BASE-TER-MSL","BASE-TER-TOR","BASE-TER-KIN","BASE-TER-RKG"};
    private static final String[] DEFENSE={
            "BASE-TER-PD","BASE-TER-FLK","BASE-TER-RCW","BASE-TER-RPD"};

    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException(message);
    }
    private static Shipyard.Design install(TechnologyCatalog tech,String id){
        Shipyard.Design design=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        boolean mounted=false;
        for(Shipyard.Slot slot:design.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            design=design.install(slot.index(),id);
            mounted=true;
            break;
        }
        require(mounted && Shipyard.inspect(design,tech).valid(),
                "Invalid paid fleet-scale weapon mount: "+id);
        return design;
    }
    private record Result(String mode,long wallNanos,long peakHeapBytes,
                          int pd,int flak,int rotary,int rail,int maxBodies,
                          int peakFlak,int flakBursts,int flakTimeouts,int intercepted,int defensiveCartridges,int netKineticLoss,
                          double netProtectionLoss,int aliveAttack,
                          int aliveDefense,int cappedEventTicks) {
        String csv(){
            return mode+","+TICKS+","+
                    String.format(Locale.ROOT,"%.3f",wallNanos/1e6)+","+
                    peakHeapBytes+","+pd+","+flak+","+rotary+","+rail+","+
                    maxBodies+","+peakFlak+","+flakBursts+","+flakTimeouts+","+
                    intercepted+","+defensiveCartridges+","+
                    netKineticLoss+","+(netKineticLoss-defensiveCartridges)+","+
                    String.format(Locale.ROOT,"%.2f",netProtectionLoss)+","+
                    aliveAttack+","+aliveDefense+","+cappedEventTicks;
        }
    }
    private static long heapBytes(){
        Runtime rt=Runtime.getRuntime();
        return rt.totalMemory()-rt.freeMemory();
    }
    private static double remainingProtection(Battle.Ship ship){
        return Math.max(0,ship.shields)+Math.max(0,ship.armor)+
                Math.max(0,ship.hull);
    }
    private static Result run(TechnologyCatalog tech,boolean enabled,
                              boolean allowAuxiliaries,List<String> samples,
                              List<String> familyKills){
        String mode=(allowAuxiliaries?"MIXED":"ORDNANCE_ONLY")+
                (enabled?"_ACTIVE":"_HOLD_FIRE");
        Battle battle=new Battle(2026110204L);
        for(int family=0;family<OFFENSE.length;family++){
            double y=135+family*155;
            battle.spawnWing(0,install(tech,OFFENSE[family]),tech,
                    SHIPS_PER_FAMILY,525,y);
            battle.spawnWing(1,install(tech,DEFENSE[family]),tech,
                    SHIPS_PER_FAMILY,875,y);
        }
        require(battle.ships().size()==400 && battle.alive(0)==200 &&
                battle.alive(1)==200,"200v200 fitted setup was incomplete");
        // Every bare dreadnought has intrinsic fighter storage. Preserve it
        // in MIXED, but disable ONLY reserves in ORDNANCE_ONLY so target
        // specialization is not contaminated by automatic baseline wings.
        // Real fitted PD sensors, power, guns and incoming stock stay intact.
        if(!allowAuxiliaries)for(Battle.Ship ship:battle.ships()){
            ship.fighterReserve=0;
            ship.droneReserve=0;
            ship.repairDroneReserve=0;
        }
        int[] physicalMounts=new int[4];
        int stockBefore=0;
        double protectionBefore=0;
        for(Battle.Ship ship:battle.ships())if(ship.team==1){
            physicalMounts[0]+=ship.precisionMounts.size();
            physicalMounts[1]+=ship.flakMounts.size();
            physicalMounts[2]+=ship.rotaryMounts.size();
            physicalMounts[3]+=ship.railMounts.size();
            stockBefore+=ship.kineticAmmo;
            protectionBefore+=remainingProtection(ship);
        }
        for(int n:physicalMounts)
            require(n==SHIPS_PER_FAMILY,
                    "Expected 50 real individually fitted mounts per PD family");
        battle.setTeamPointDefense(1,enabled?
                BattleOrders.PointDefense.BALANCED:
                BattleOrders.PointDefense.HOLD_FIRE);

        int pd=0,flak=0,rotary=0,rail=0,maxBodies=0,capped=0;
        int peakCraft=0,peakFlak=0,totalFlakBursts=0,totalFlakTimeouts=0;
        long peakHeap=heapBytes();
        long begin=System.nanoTime();
        long windowBegin=begin;
        int windowPd=0,windowFlak=0,windowRotary=0,windowRail=0;
        int windowFlakBursts=0,windowFlakTimeouts=0;
        for(int frame=0;frame<TICKS;frame++){
            battle.step();
            peakCraft=Math.max(peakCraft,battle.crafts().size());
            List<Battle.VisualEvent> events=battle.visualEvents();
            // The rolling visual ring is capped at 8192 entries. Count only
            // this simulation tick, newest first, to avoid replaying history.
            if(events.size()==8192 && !events.isEmpty() &&
                    events.get(0).tick()==battle.tick)capped++;
            for(int e=events.size()-1;e>=0;e--){
                Battle.VisualEvent event=events.get(e);
                if(event.tick()!=battle.tick)break;
                if(event.team()!=1)continue;
                if(event.phase()==Battle.VisualPhase.IMPACT&&
                        "FLAK_BURST".equals(event.weapon())){
                    totalFlakBursts++;windowFlakBursts++;continue;
                }
                if(event.phase()==Battle.VisualPhase.TIMEOUT&&
                        "FLAK".equals(event.weapon())){
                    totalFlakTimeouts++;windowFlakTimeouts++;continue;
                }
                if(event.phase()!=Battle.VisualPhase.FIRE)continue;
                switch(event.weapon()){
                    case "POINT_DEFENSE" -> {pd++;windowPd++;}
                    case "FLAK" -> {flak++;windowFlak++;}
                    case "ROTARY_CIWS" -> {rotary++;windowRotary++;}
                    case "RAIL_PD" -> {rail++;windowRail++;}
                    default -> { }
                }
            }
            int liveFlak=battle.flakRounds().size();
            peakFlak=Math.max(peakFlak,liveFlak);
            int bodies=battle.missiles().size()+battle.rockets().size()+
                    battle.ballistics().size()+battle.rotaryRounds().size()+
                    battle.railPenetrators().size()+liveFlak;
            maxBodies=Math.max(maxBodies,bodies);
            if(frame%30==29){
                peakHeap=Math.max(peakHeap,heapBytes());
                long now=System.nanoTime();
                int intercepted=battle.missileIntercepts+
                        battle.rocketIntercepts+battle.ballisticIntercepts;
                samples.add(mode+","+
                        (frame+1)+","+String.format(Locale.ROOT,"%.3f",
                        (now-windowBegin)/1e6)+","+heapBytes()+","+
                        bodies+","+maxBodies+","+battle.alive(0)+","+
                        battle.alive(1)+","+windowPd+","+windowFlak+","+
                        windowRotary+","+windowRail+","+intercepted+","+
                        liveFlak+","+peakFlak+","+windowFlakBursts+","+
                        windowFlakTimeouts);
                windowPd=windowFlak=windowRotary=windowRail=0;
                windowFlakBursts=windowFlakTimeouts=0;
                windowBegin=now;
            }
        }
        long elapsed=System.nanoTime()-begin;
        int stockAfter=0;
        double protectionAfter=0;
        for(Battle.Ship ship:battle.ships())if(ship.team==1){
            stockAfter+=ship.kineticAmmo;
            protectionAfter+=remainingProtection(ship);
        }
        int interceptions=battle.missileIntercepts+
                battle.rocketIntercepts+battle.ballisticIntercepts;
        int attributedOrdnanceKills=0,attributedCraftKills=0;
        for(Battle.DefensiveGun gun:Battle.DefensiveGun.values())
            for(Battle.DefensiveTarget target:Battle.DefensiveTarget.values()){
                int kills=battle.defensiveKills(gun,target);
                require(kills>=0,"Negative physical defense attribution");
                familyKills.add(mode+","+
                        gun+","+target+","+kills);
                if(target==Battle.DefensiveTarget.CRAFT)
                    attributedCraftKills+=kills;
                else attributedOrdnanceKills+=kills;
            }
        // All 200 defensive hulls have only these four paid physical PD
        // families. These counters follow actual removal, not hit attempts.
        // Other attack or non-PD systems must never get phantom PD credit.
        require(attributedOrdnanceKills==interceptions,
                "Actual projectile kills were not attributed exactly once");
        require(attributedCraftKills==battle.pointDefenseCraftKills,
                "Actual craft kills were not attributed exactly once");
        require(enabled || (attributedOrdnanceKills==0&&attributedCraftKills==0),
                "HOLD_FIRE attributed nonexistent physical PD kills");
        require(allowAuxiliaries || (peakCraft==0&&attributedCraftKills==0),
                "Ordnance-only fleet spawned unexpected fighter/drone craft");
        require(enabled || (pd==0&&flak==0&&rotary==0&&rail==0),
                "HOLD_FIRE emitted real point-defense shots in 200v200");
        require(totalFlakBursts+totalFlakTimeouts<=flak,
                "More physical flak shells resolved than were paid and launched");
        require(enabled || (peakFlak==0&&totalFlakBursts==0&&totalFlakTimeouts==0),
                "HOLD_FIRE spawned an unapproved live flak shell");
        int paidCartridges=flak+rotary+
                rail*RailPointDefense.CARTRIDGES_PER_SHOT;
        int netLoss=stockBefore-stockAfter;
        // Fitted FLK/RCW each use one real round; RPD uses three.
        // Further stock loss can arise from actual incoming hits destroying
        // magazine inventory, not just active point-defense fire.
        require(netLoss>=paidCartridges,
                "Defensive physical shots were not paid by finite stock");
        return new Result(mode,elapsed,peakHeap,
                pd,flak,rotary,rail,maxBodies,
                peakFlak,totalFlakBursts,totalFlakTimeouts,interceptions,
                paidCartridges,netLoss,
                Math.max(0,protectionBefore-protectionAfter),
                battle.alive(0),battle.alive(1),capped);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Path root=Path.of("test-output","pd-fleet-scale");
        Files.createDirectories(root);
        List<String> samples=new ArrayList<>();
        samples.add("mode,tick,wall_window_ms,heap_bytes,active_bodies,"+
                "peak_bodies,alive_attack,alive_defense,laser_shots,flak_shots,"+
                "rotary_shots,rail_shots,ordnance_interceptions,"+
                "flak_live,peak_flak_live,flak_bursts_window,flak_expiry_window");
        List<String> familyKills=new ArrayList<>();
        familyKills.add("mode,gun,target,physical_kills");
        Result active=run(tech,true,true,samples,familyKills);
        Result hold=run(tech,false,true,samples,familyKills);
        Result ordnanceActive=run(tech,true,false,samples,familyKills);
        Result ordnanceHold=run(tech,false,false,samples,familyKills);
        Files.write(root.resolve("samples.csv"),samples,StandardCharsets.UTF_8);
        Files.write(root.resolve("family-target-kills.csv"),familyKills,
                StandardCharsets.UTF_8);
        String report="# Real fitted 200v200 four-family point-defense load\n\n"+
                "600 physical 30-Hz simulation ticks per mode, identical initial "+
                "ship positions and RNG seed. Each fleet includes 50 live "+
                "missile, torpedo, kinetic and siege-rocket attackers versus "+
                "50 laser PD, flak, rotary CIWS and charged rail interceptors. "+
                "All guns occupy paid WEAPON sockets; no fabricated damage "+
                "or free interception events. MIXED retains the bare-hull "+
                "intrinsic fighter inventory. ORDNANCE_ONLY clears all "+
                "fighter/drone reserves before tick one, but does not "+
                "change any PD mount, attacking launcher or ammunition.\n\n"+
                "|Mode|Ticks|Wall ms|Peak used heap bytes|PD|FLK|RCW|RPD|"+
                "Peak active bodies|Peak flak in flight|Flak fuze bursts|Flak expiries|"+
                "Real ordnance intercepts|Paid PD cartridges|"+
                "Net kinetic stock reduction|Other stock loss|Net protection loss|"+
                "Attackers alive|Defenders alive|Capped visual ticks|\n"+
                "|---|"+"---:|".repeat(19)+"\n"+
                "|"+active.csv().replace(",", "|")+"|\n"+
                "|"+hold.csv().replace(",", "|")+"|\n"+
                "|"+ordnanceActive.csv().replace(",", "|")+"|\n"+
                "|"+ordnanceHold.csv().replace(",", "|")+"|\n\n"+
                "This is an advisory initial sustained-load profile, NOT "+
                "a hit-rate, execution-speed, or memory release gate. The "+
                "HOLD_FIRE case changes combat outcomes, so wall-clock "+
                "difference is not isolated point-defense CPU cost. "+
                "Paid defensive cartridges are independently derived from "+
                "actual FLK/RCW/RPD FIRE events; net kinetic stock loss "+
                "also includes magazine damage and other stock effects. "+
                "Peak active bodies now INCLUDE actual moving flak shells. "+
                "A flak burst is counted only on physical fuze arrival; "+
                "expiry is distinct from a successful fragmentation event. "+
                "Net protection loss is remaining shield+armor+hull "+
                "depletion, not cumulative pre-regeneration damage. "+
                "Each row in samples.csv is one 30-tick measurement window. "+
                "Event history has an 8192-event ring and capped windows "+
                "must be interpreted cautiously. CPU timings depend on the "+
                "GitHub runner. No APK or desktop packaging is involved.\n"+
                "\n## Per-family physical kill attribution\n\n"+
                "Only real removed ordnance/craft bodies count as kills. "+
                "Unsuccessful hits, harmless missed ordnance, electronic "+
                "diversions and ordinary projectile expiry are excluded.\n\n"+
                "|Mode|Defense|Target body|Destroyed|\n"+
                "|---|---|---|---:|\n";
        for(int i=1;i<familyKills.size();i++)
            report+="|"+familyKills.get(i).replace(",","|")+"|\n";
        Files.writeString(root.resolve("summary.md"),report);
        System.out.print(report);
        System.out.println("PD_FLEET_SCALE_200v200_TICKS_PER_MODE="+TICKS);
    }
}
