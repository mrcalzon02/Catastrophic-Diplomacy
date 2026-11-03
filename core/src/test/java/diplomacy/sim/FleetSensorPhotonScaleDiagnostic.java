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
 * Advisory 16v16 and 200v200 post-photon-integration reproducibility profile.
 * Real faction modules and physical battles; no synthetic kills, ships, ghost
 * reports or FPS assumptions. CPU, GC, throughput, balance and heap are CSV
 * evidence only: neither runtime speed nor desired hit rate blocks releases.
 *
 * The 200v200 scenario intentionally uses heavy fitted DREADNOUGHT frames
 * and disables innate auxiliary craft reserves to isolate sensor/ordnance/
 * photon load without claiming a general fleet FPS benchmark.
 */
public final class FleetSensorPhotonScaleDiagnostic {
    private static final int[] SIZES={16,200};
    private static final int[] TICKS={150,75};
    private static final long SEED=2026110307L;
    private static final String[] ROLES={
            "SHR-RES-HLP", "TRK-LNK", "SHR-FCM", "TRK-CAP"
    };
    private FleetSensorPhotonScaleDiagnostic(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int ordinal){
        for(Shipyard.Slot s:hull.slots)
            if(s.mount()==mount&&ordinal--==0)return s.index();
        throw new IllegalStateException("No "+mount+" slot in "+hull);
    }
    private static Shipyard.Design fitted(TechnologyCatalog catalog,
                                          Battle.Faction faction,int role){
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        String prefix="BASE-"+Shipyard.factionId(faction)+"-";
        Shipyard.Design design=Shipyard.bareHull(faction,hull);
        // Weapon sockets must be authoritative and real. All four network
        // families face the same physically fitted offense and acquisition.
        design=design.install(slot(hull,Shipyard.Mount.WEAPON,0),prefix+"LAS");
        design=design.install(slot(hull,Shipyard.Mount.WEAPON,1),prefix+"MSL");
        // The command ship deliberately does not mount long-range RDR.
        // It MUST obtain the distant battlespace through physical SHR/LNK.
        if(role!=2)
            design=design.install(slot(hull,Shipyard.Mount.SYSTEM,0),prefix+"RDR");
        String[] electronics=switch(role){
            case 0 -> new String[]{"SHR","RES"};
            case 1 -> new String[]{"TRK","LNK"};
            case 2 -> new String[]{"SHR","LNK"};
            case 3 -> new String[]{"TRK","CAP"};
            default -> throw new IllegalArgumentException("Unknown sensor family");
        };
        for(int i=0;i<electronics.length;i++)
            design=design.install(slot(hull,Shipyard.Mount.SYSTEM,i+1),
                    prefix+electronics[i]);
        // HLP is actual SUPPORT hardware, NOT SYSTEM electronics. Command
        // cores are also SUPPORT modules with a separate installation role.
        if(role==0)design=design.install(
                slot(hull,Shipyard.Mount.SUPPORT,0),prefix+"HLP");
        if(role==2)design=design.install(
                slot(hull,Shipyard.Mount.SUPPORT,0),prefix+"FCM");

        // The engineering validation is authoritative. Outfitting a fleet
        // with many systems may require real reactor/radiator components.
        // Install paid REA/RAD in spare SYSTEM slots until the approved ship
        // obeys its actual output and thermal budget; never waive the checks.
        int systemSlot=electronics.length+1;
        for(int attempt=0;attempt<6;attempt++){
            Shipyard.Report budget=Shipyard.inspect(design,catalog);
            if(budget.valid())break;
            String utility=budget.demand()>budget.generation()+1e-8?
                    "REA":budget.generatedHeat()>budget.cooling()+1e-8?
                    "RAD":null;
            check(utility!=null,
                    "Invalid real fitted "+faction+"/"+ROLES[role]+": "+
                            budget.explanation());
            design=design.install(slot(hull,Shipyard.Mount.SYSTEM,systemSlot++),
                    prefix+utility);
        }
        Shipyard.Report finalReport=Shipyard.inspect(design,catalog);
        check(finalReport.valid(),
                "Invalid real fitted "+faction+"/"+ROLES[role]+": "+
                        finalReport.explanation());
        return design;
    }
    private static Battle create(TechnologyCatalog catalog,int count,long seed){
        Battle sim=new Battle(seed);
        Battle.Faction[] factions={Battle.Faction.TERRAN,Battle.Faction.DRAXIAN};
        for(int team=0;team<2;team++){
            int allocated=0;
            for(int role=0;role<ROLES.length;role++){
                int part=count/ROLES.length+
                        (role<count%ROLES.length?1:0);
                if(part<=0)continue;
                var design=fitted(catalog,factions[team],role);
                double cx=switch(role){
                    case 0 -> team==0?510:770; // Forward paid scouts
                    case 1,3 -> team==0?445:835; // Armed tracking line
                    case 2 -> team==0?90:1190; // Rear fleet command
                    default -> throw new IllegalStateException("Unknown fleet role");
                };
                double cy=95+role*160;
                sim.spawnWing(team,design,catalog,part,cx,cy);
                allocated+=part;
            }
            check(allocated==count,"Incomplete real fitted deployment");
        }
        check(sim.ships().size()==count*2&&
                        sim.alive(0)==count&&sim.alive(1)==count,
                "Fitted "+count+"v"+count+" deployment failed");
        // Remove only innate fighter/drone reserves so this diagnostic
        // isolates photons, sensor networking and primary ordnance. It
        // neither removes mounted weapons nor invents future sensor data.
        for(Battle.Ship ship:sim.ships()){
            ship.fighterReserve=0;ship.droneReserve=0;
            ship.repairDroneReserve=0;
        }
        return sim;
    }
    private static long usedHeap(){
        Runtime runtime=Runtime.getRuntime();
        return runtime.totalMemory()-runtime.freeMemory();
    }
    private record Result(int count,int ticks,long wallNs,long peakHeap,
                          long checksum,int shots,int hits,
                          int direct,int relayed,int historical,int ghosts,
                          int peakMissiles,int peakBeams,
                          long solverAllocations,long solverExhausted,
                          int aliveA,int aliveB){
        String csv(){
            return count+","+count+","+ticks+","+
                    String.format(Locale.ROOT,"%.1f",wallNs/1e6)+","+
                    peakHeap+","+shots+","+hits+","+direct+","+relayed+","+
                    historical+","+ghosts+","+peakMissiles+","+peakBeams+","+
                    solverAllocations+","+solverExhausted+","+aliveA+","+aliveB+","+
                    Long.toHexString(checksum);
        }
    }
    private static Result measure(TechnologyCatalog catalog,int count,int steps,
                                  long seed,List<String> samples,boolean record){
        Battle battle=create(catalog,count,seed);
        long begin=System.nanoTime();
        long peak=usedHeap();
        int direct=0,relayed=0,historical=0,ghosts=0,shots=0;
        int peakMissiles=0,peakBeams=0;
        int alive0=0,alive1=0;
        for(int frame=1;frame<=steps;frame++){
            if(frame==steps/2){
                // Physically lose selected forward SHR transmitters while
                // LNK-only TRK scouts keep valid stored observations alive.
                // The command viewer must retain historical stale packets,
                // not counterfeit live contacts or instantaneous photon state.
                for(Battle.Ship ship:battle.ships())
                    if(ship.network.sharingCapacity()>0&&
                            ship.network.commandCapacity()==0&&
                            ship.network.trackRelayRange()==0){
                        ship.powerRegen=0;
                        ship.power=0;
                    }
            }
            battle.step();
            for(Battle.Ship ship:battle.ships()){
                check(Double.isFinite(ship.x)&&Double.isFinite(ship.y)&&
                        Double.isFinite(ship.vx)&&Double.isFinite(ship.vy)&&
                        Double.isFinite(ship.power)&&Double.isFinite(ship.heat)&&
                        Double.isFinite(ship.hull)&&Double.isFinite(ship.shields),
                        "Non-finite ship in "+count+"v"+count+" at "+frame);
                check(ship.hull>=0&&ship.power>=0&&
                        BattleArena.contains(ship.x,ship.y),
                        "Illegal physical hull in "+count+"v"+count+" at "+frame);
            }
            for(Battle.VisualEvent event:battle.visualEvents()){
                if(event.tick()!=battle.tick)continue;
                if(event.phase()==Battle.VisualPhase.FIRE)shots++;
            }
            peakMissiles=StrictMath.max(peakMissiles,battle.missiles().size());
            peakBeams=StrictMath.max(peakBeams,battle.pendingBeamPhotonCount());
            if(frame%15==0||frame==steps){
                // Actual FCM-selected command scopes; telemetry is a READ,
                // not a privileged observer who knows enemy truth.
                for(int team=0;team<2;team++){
                    Battle.Ship viewer=battle.tacticalViewer(team);
                    if(viewer==null||!viewer.alive())continue;
                    long beforeHash=battle.fingerprint();
                    var live=battle.tacticalPicture(team,viewer.id);
                    var falseReports=battle.tacticalFalseContacts(team,viewer.id);
                    check(battle.fingerprint()==beforeHash,
                            "Reading command scope mutated authoritative battle");
                    for(Battle.TacticalContact report:live){
                        check(report.observedTick()<=battle.tick&&
                                report.receivedTick()<=battle.tick&&
                                report.emittedTick()<=report.observedTick(),
                                "Command scope saw a future target");
                        if(report.stale())historical++;
                        else if(report.relayed())relayed++;
                        else direct++;
                    }
                    for(Battle.TacticalFalseContact ghost:falseReports){
                        check(ghost.observedTick()<=battle.tick&&
                                ghost.receivedTick()<=battle.tick&&
                                ghost.sourceId()>0&&ghost.emitterId()>0,
                                "False packet lacks physical provenance");
                        ghosts++;
                    }
                }
                peak=StrictMath.max(peak,usedHeap());
                if(record){
                    samples.add(count+","+frame+","+
                            String.format(Locale.ROOT,"%.1f",
                                    (System.nanoTime()-begin)/1e6)+","+
                            usedHeap()+","+battle.alive(0)+","+
                            battle.alive(1)+","+battle.hitCount+","+
                            battle.missiles().size()+","+
                            battle.pendingBeamPhotonCount()+","+
                            direct+","+relayed+","+historical+","+ghosts);
                }
            }
        }
        alive0=battle.alive(0);alive1=battle.alive(1);
        long allocated=0,exhausted=0;
        for(Battle.Ship ship:battle.ships()){
            allocated+=ship.targetCompute.allocations;
            exhausted+=ship.targetCompute.exhausted;
        }
        check(allocated>=0&&exhausted<=allocated,
                "Invalid physically funded fire-control compute counters");
        check(shots>0&&peakBeams>0&&allocated>0,
                "No real charged beam/fire-control use in "+count+"v"+count);
        check(relayed>0,
                "No genuine remote SHR contact reached rear command ship");
        check(historical>0,
                "No finite aged TRK/LNK contact survived the scout outage");
        check(ghosts>0,
                "No emitted historical HLP hypotheses reached any command scope");
        return new Result(count,steps,System.nanoTime()-begin,peak,
                battle.fingerprint(),shots,battle.hitCount,direct,relayed,
                historical,ghosts,peakMissiles,peakBeams,allocated,exhausted,
                alive0,alive1);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Path dir=Path.of("test-output","fleet-sensor-photon-scale");
        Files.createDirectories(dir);
        List<String> samples=new ArrayList<>();
        samples.add("ships_per_side,tick,elapsed_ms,used_heap_bytes,alive0,alive1,"+
                "hits,live_missiles,beam_photons,direct_samples,relayed_samples,"+
                "historical_samples,false_contact_samples");
        List<String> rows=new ArrayList<>();
        rows.add("ships_0,ships_1,ticks,wall_ms,peak_heap_bytes,weapon_fire_events,"+
                "physical_hits,direct_reports,relayed_reports,historical_reports,"+
                "false_hypotheses,peak_missiles,peak_inflight_beam_fronts,"+
                "target_compute_allocations,target_compute_limited,alive0,alive1,hash");
        List<Result> results=new ArrayList<>();
        for(int i=0;i<SIZES.length;i++){
            int count=SIZES[i],ticks=TICKS[i];
            Result measured=measure(catalog,count,ticks,SEED+i,samples,true);
            // Independent identical battle, not a re-read of the first
            // fingerprint. Nondeterministic combat physics is a real bug.
            Result replay=measure(catalog,count,ticks,SEED+i,null,false);
            check(measured.checksum()==replay.checksum(),
                    "Real "+count+"v"+count+" sensor/ordnance replay diverged");
            rows.add(measured.csv());
            results.add(measured);
            System.out.println("PASS "+count+"v"+count+" "+ticks+
                    " ticks, checksum "+Long.toHexString(measured.checksum())+
                    ", wall-ms "+String.format(Locale.ROOT,"%.1f",
                            measured.wallNs()/1e6)+", false-report samples "+
                    measured.ghosts()+", finite-compute allocations "+
                    measured.solverAllocations());
        }
        Files.write(dir.resolve("samples.csv"),samples,StandardCharsets.UTF_8);
        Files.write(dir.resolve("metrics.csv"),rows,StandardCharsets.UTF_8);
        StringBuilder report=new StringBuilder(
                "# Real fitted sensor-network integration at 16v16 and 200v200\n\n"+
                "Two identical-seed independently executed replays per size. "+
                "SHR, RES, TRK, LNK, FCM and CAP modules are installed in real "+
                "paid DREADNOUGHT sockets; RDR radars arm the forward scouts "+
                "and tracking line while the rear FCM has no RDR so relayed "+
                "data is meaningful. LAS/MSL guns are fitted on every vessel. "+
                "One sensor family includes real HLP hologram hardware. "+
                "Forward SHR scouts lose power at midpoint; the separate "+
                "TRK/LNK archive must preserve aged tracks. Innate fighter "+
                "and repair-drone reserves are disabled for this profile. "+
                "All positional, photon, false packet, beam and ship states "+
                "come from actual battles.\n\n"+
                "|Per side|Ticks|Wall ms|Peak heap bytes|Real FIRE events|Hits|"+
                "Direct reports|Relayed|Historical|False samples|"+
                "Peak missiles|Peak beam fronts|Compute allocations|Limited|"+
                "Alive side 0|Alive side 1|Replay hash|\n"+
                "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"+
                "---:|---:|---:|---:|---:|---:|---|\n");
        for(Result value:results)
            report.append("|").append(value.csv().replace(",", "|")
                    .replaceFirst("^\\d+\\|", "")).append("|\n");
        report.append("\nPerformance, peak memory, weapon balance and the "+
                "number of received hypotheses are advisory diagnostics, "+
                "NOT build or release limits. Replays, physical state "+
                "validity, fitted equipment and real weapon emission are "+
                "objective functionality checks inside this optional job. "+
                "The two battle sizes are not FPS claims: runner CPU, "+
                "GC and hardware affect timings. Each sensor sample is "+
                "read-only and reflects measured/sent/received history. "+
                "The 200v200 run deliberately spans a shorter duration "+
                "than the 16v16 case to bound diagnostic cost. "+
                "No package or APK is built.\n");
        Files.writeString(dir.resolve("summary.md"),report.toString(),
                StandardCharsets.UTF_8);
        System.out.println(report);
    }
}
