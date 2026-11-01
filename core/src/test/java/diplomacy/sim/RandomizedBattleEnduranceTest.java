package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unattended 200v200 finish-to-terminal combat using the actual screensaver
 * faction, hull, engineering and per-captain randomized fleet generator.
 *
 * Each CI matrix job runs one independent replayable seed. On every tick the
 * real Battle simulation advances; the report stores progress, combat metrics,
 * source fleet identities and failure context. Unlike first-frame smoke tests,
 * PASS requires the actual victory/withdrawal terminal state.
 *
 * The watchdog reports a STALL or TIMEOUT as FAILURE, never as victory.
 * Headless only: Android GPU/sprite coverage remains a separate runtime test.
 */
public final class RandomizedBattleEnduranceTest {
    private static final int FLEET_SIZE=200;
    private static final int MAX_TICKS=30000; // 1000 simulated seconds, safety limit only
    private static final int NO_COMBAT_PROGRESS_TICKS=6000;
    private static final int REPORT_INTERVAL=300;
    private static final long MAX_WALL_NS=42L*60L*1000L*1000000L;
    private RandomizedBattleEnduranceTest(){ }

    private static void require(boolean good,String message){
        if(!good)throw new AssertionError(message);
    }
    private static String csv(Object... values){
        StringBuilder row=new StringBuilder();
        for(Object value:values){
            if(row.length()>0)row.append(',');
            String v=String.valueOf(value);
            row.append('"').append(v.replace(""","""")).append('"');
        }
        return row.toString();
    }
    private static void diagnostic(String message,BufferedWriter writer)throws Exception{
        System.out.println(message);
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
    private static void shipCheck(Battle b){
        for(Battle.Ship ship:b.ships()){
            require(Double.isFinite(ship.x)&&Double.isFinite(ship.y)
                    &&Double.isFinite(ship.vx)&&Double.isFinite(ship.vy),
                    "Invalid motion on ship "+ship.id+"/"+ship.name);
            require(Double.isFinite(ship.hull)&&Double.isFinite(ship.shields)
                    &&Double.isFinite(ship.power)&&Double.isFinite(ship.heat),
                    "Invalid damage/energy state on ship "+ship.id+"/"+ship.name);
            require(ship.fighterReserve>=0&&ship.droneReserve>=0
                    &&ship.missileAmmo>=0&&ship.torpedoAmmo>=0,
                    "Negative physical ammunition on ship "+ship.id);
        }
        for(Battle.Craft c:b.crafts())
            require(Double.isFinite(c.x)&&Double.isFinite(c.y)
                    &&Double.isFinite(c.integrity)&&Double.isFinite(c.fuel),
                    "Invalid flying-craft state "+c.id);
        for(Battle.Missile m:b.missiles())
            require(Double.isFinite(m.x)&&Double.isFinite(m.y)&&Double.isFinite(m.damage),
                    "Invalid active missile "+m.visualId);
        for(Battle.Ballistic p:b.ballistics())
            require(Double.isFinite(p.x)&&Double.isFinite(p.y)
                    &&Double.isFinite(p.damage),"Invalid tracked ballistic "+p.visualId);
    }

    private static void run(long seed,Path dir)throws Exception{
        Files.createDirectories(dir);
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        ScreensaverBattle.Round round=ScreensaverBattle.createSized(seed,catalog,FLEET_SIZE);
        Battle b=round.battle();
        b.configureWeather(LevelEnvironment.forLevel(round.environmentLevel()).weather());
        require(b.ships().size()==400 && b.alive(0)==200 && b.alive(1)==200,
                "Randomized deployment did not produce 200 live fitted ships per team");
        Set<Shipyard.Hull> hulls=new HashSet<>();
        Set<String> moduleIds=new HashSet<>();
        Set<ShipStandingOrders> captains=new HashSet<>();
        Set<FighterModel.Hull> stockedTypes=EnumSet.noneOf(FighterModel.Hull.class);
        int flightDecks=0;
        Path roster=dir.resolve("fleets.csv");
        try(BufferedWriter writer=Files.newBufferedWriter(roster,StandardCharsets.UTF_8)){
            writer.write("id,team,faction,hull,name,orders,fighterType,fighterCapacity,slots,modules");
            writer.newLine();
            for(Battle.Ship ship:b.ships()){
                hulls.add(ship.hullClass);
                require(ship.standingOrders!=null,"No individualized standing orders for "+ship.id);
                captains.add(ship.standingOrders);
                if(ship.fighterReserve>0){
                    flightDecks++;
                    stockedTypes.add(ship.fighterProfile.hull());
                }
                List<Shipyard.Design> fleet=ship.team==0?round.leftFleet():round.rightFleet();
                int index=ship.team==0?b.ships().indexOf(ship):
                        b.ships().indexOf(ship)-FLEET_SIZE;
                require(index>=0&&index<200,"Ship-to-blueprint indexing failed");
                Shipyard.Design design=fleet.get(index);
                require(Shipyard.inspect(design,catalog).valid(),"Illegal randomized hull fit "+ship.id);
                StringBuilder modules=new StringBuilder();
                for(int socket=0;socket<design.slots();socket++){
                    Shipyard.Installed installed=design.at(socket);
                    if(installed==null)continue;
                    moduleIds.add(installed.component);
                    if(modules.length()>0)modules.append(';');
                    modules.append(socket).append(':').append(installed.component);
                }
                writer.write(csv(ship.id,ship.team,ship.faction,ship.hullClass,ship.name,
                        ship.standingOrders,ship.fighterProfile.hull(),ship.fighterReserve,
                        design.slots(),modules));
                writer.newLine();
            }
        }
        require(hulls.size()>=6,"Randomized exhibition lacks hull variety");
        require(captains.size()>=20,"Captain orders were not independently randomized");
        require(moduleIds.size()>=8,"Randomized ships did not select varied hardware");
        require(flightDecks>=12,"Not enough real fighter-carrying ships");
        require(stockedTypes.size()==FighterModel.Hull.values().length,
                "All six fighter classes must be installed on live decks: "+stockedTypes);
        long started=System.nanoTime(), lastEvents=0;
        int peakGuided=0,peakBallistic=0,peakCraft=0;
        int lastHit=0,lastLoss=0,lastShots=0,lastProgressTick=0;
        Set<String> weaponsFired=new HashSet<>();
        Set<FighterModel.Hull> fightersFlown=EnumSet.noneOf(FighterModel.Hull.class);
        try(BufferedWriter progress=Files.newBufferedWriter(dir.resolve("progress.log"),
                StandardCharsets.UTF_8,StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)){
            diagnostic("START seed="+seed+" fleet=200v200 "+
                    round.left()+"(tech="+round.leftTier()+") vs "+
                    round.right()+"(tech="+round.rightTier()+") environment="+
                    round.environmentLevel()+" hulls="+hulls.size()+" modules="+
                    moduleIds.size()+" captains="+captains.size()+
                    " flightDecks="+flightDecks+" fittedAircraft="+stockedTypes,progress);
            shipCheck(b);
            while(!b.finished()){
                require(b.tick<MAX_TICKS,"TIMEOUT: no terminal victory after "+
                        MAX_TICKS+" real 30Hz combat ticks");
                require(System.nanoTime()-started<MAX_WALL_NS,
                        "WALL-TIMEOUT: real battle still not terminated at "+
                                b.tick+" ticks");
                b.step();
                peakGuided=Math.max(peakGuided,b.missiles().size());
                peakBallistic=Math.max(peakBallistic,b.ballistics().size());
                peakCraft=Math.max(peakCraft,b.crafts().size());
                for(Battle.Craft craft:b.crafts())
                    if(craft.fighter)fightersFlown.add(craft.profile.hull());
                if(b.tick%30==0){
                    for(Battle.VisualEvent event:b.visualEvents()){
                        if(event.sequence()<=lastEvents)continue;
                        if(event.phase()==Battle.VisualPhase.FIRE)
                            weaponsFired.add(event.weapon());
                        lastEvents=Math.max(lastEvents,event.sequence());
                    }
                }
                int shots=b.fighterGunShots+b.fighterLaserShots+
                        b.fighterParticleShots+b.fighterMissilesLaunched+
                        b.fighterTorpedoesLaunched;
                if(b.hitCount!=lastHit||b.destructions!=lastLoss||shots!=lastShots){
                    lastProgressTick=(int)b.tick;
                    lastHit=b.hitCount;lastLoss=b.destructions;lastShots=shots;
                }
                if(b.tick%REPORT_INTERVAL==0){
                    shipCheck(b);
                    diagnostic("TICK "+b.tick+" alive="+b.alive(0)+"/"+b.alive(1)+
                            " escaped="+b.escaped(0)+"/"+b.escaped(1)+
                            " destroyed="+b.destroyed(0)+"/"+b.destroyed(1)+
                            " hits="+b.hitCount+" fired="+weaponsFired+
                            " fighterShots="+shots+" craft="+b.crafts().size()+
                            " missile="+b.missiles().size()+" ballistic="+b.ballistics().size()+
                            " peakCraft="+peakCraft+" fingerprint="+
                            Long.toHexString(b.fingerprint()),progress);
                }
                require(b.tick-lastProgressTick<NO_COMBAT_PROGRESS_TICKS,
                        "STALL: neither combat hits/losses nor fighter fire advanced for "+
                                NO_COMBAT_PROGRESS_TICKS+" ticks; alive="+b.alive(0)+"/"+b.alive(1));
            }
            shipCheck(b);
            int shots=b.fighterGunShots+b.fighterLaserShots+b.fighterParticleShots+
                    b.fighterMissilesLaunched+b.fighterTorpedoesLaunched;
            double elapsed=(System.nanoTime()-started)/1e9;
            String finalLine="COMPLETE seed="+seed+" ticks="+b.tick+
                    " resultAlive="+b.alive(0)+"/"+b.alive(1)+
                    " escaped="+b.escaped(0)+"/"+b.escaped(1)+
                    " destroyed="+b.destroyed(0)+"/"+b.destroyed(1)+
                    " hits="+b.hitCount+" destructions="+b.destructions+
                    " fighterShots="+shots+" fighterTypesFlown="+fightersFlown+
                    " guidedPeak="+peakGuided+" ballisticPeak="+peakBallistic+
                    " craftPeak="+peakCraft+" fireTypes="+weaponsFired+
                    " durationSeconds="+String.format(Locale.ROOT,"%.2f",elapsed)+
                    " fingerprint="+Long.toHexString(b.fingerprint());
            diagnostic(finalLine,progress);
            Files.writeString(dir.resolve("result.csv"),
                    "seed,factionA,factionB,tierA,tierB,environment,ticks,aliveA,aliveB,"+
                    "escapedA,escapedB,destroyedA,destroyedB,hits,destructions,"+
                    "fighterShots,fighterTypesFlown,peakMissiles,peakBallistics,peakCraft,"+
                    "weaponFamilies,elapsedSeconds,fingerprint\n"+
                    csv(seed,round.left(),round.right(),round.leftTier(),round.rightTier(),
                            round.environmentLevel(),b.tick,b.alive(0),b.alive(1),
                            b.escaped(0),b.escaped(1),b.destroyed(0),b.destroyed(1),
                            b.hitCount,b.destructions,shots,fightersFlown,
                            peakGuided,peakBallistic,peakCraft,weaponsFired,
                            String.format(Locale.ROOT,"%.2f",elapsed),
                            Long.toHexString(b.fingerprint()))+"\n",
                    StandardCharsets.UTF_8);
            require(b.hitCount>0&&b.destructions>0,
                    "Terminal battle ended without authentic damaging combat");
            require(peakCraft>0&&fightersFlown.size()>=3&&shots>0,
                    "Aircraft deployed but did not perform authentic fighter combat");
            require(!weaponsFired.isEmpty(),"No visual FIRE events from the actual simulation");
            require(b.alive(0)==0||b.alive(1)==0,"False PASS without true terminal state");
            diagnostic("PASS real randomized 200v200 battle resolved to terminal state",progress);
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException(
                "Usage: RandomizedBattleEnduranceTest <seed> <report-directory>");
        long seed=Long.parseLong(args[0]);
        Path report=Path.of(args[1]);
        try{
            run(seed,report);
        }catch(Throwable failure){
            Files.createDirectories(report);
            StringWriter trace=new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            String diagnostic="FAIL seed="+seed+" "+trace;
            System.err.println(diagnostic);
            Files.writeString(report.resolve("failure.txt"),diagnostic,StandardCharsets.UTF_8);
            throw new AssertionError("Randomized 200v200 test failed; see artifacts for seed="+seed,failure);
        }
    }
}
