package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advisory P0: physically simultaneous fighter attacks against fitted PD,
 * fitted FLK and a genuinely mixed 2+2 battery. No synthetic spawn, no
 * probability overrides, no release-blocking balance assertions.
 */
public final class PointDefenseWaveDiagnostic {
    private PointDefenseWaveDiagnostic(){}
    private enum Battery {
        PD_ONE, PD_TWO, FLK_ONE, PD_FOUR, FLK_FOUR, MIXED_TWO_TWO;
        int pd(){return switch(this){
            case PD_ONE -> 1;case PD_TWO, MIXED_TWO_TWO -> 2;case PD_FOUR -> 4;
            default -> 0;};}
        int flak(){return switch(this){
            case FLK_ONE -> 1;case FLK_FOUR -> 4;case MIXED_TWO_TWO -> 2;
            default -> 0;};}
        /** Stable fixture identity; enum reordering must never perturb seeds. */
        int stableSeedCode(){return switch(this){
            case PD_ONE -> 0;
            case FLK_ONE -> 1;
            case PD_FOUR -> 2;
            case FLK_FOUR -> 3;
            case MIXED_TWO_TWO -> 4;
            case PD_TWO -> 5;
        };}
    }
    private enum Threat { INTERCEPTOR, TORPEDO_BOMBER }
    private enum Formation {
        // Matched compact/dispersed A/B: identical initial RNG seed,
        // only the physical carrier spacing differs.
        COMPACT(10.0), DISPERSED(46.0);
        final double lateralSpacing;
        Formation(double spacing){lateralSpacing=spacing;}
    }
    private record Trial(Battery battery,Threat threat,int wave,int distance,
            int rank,int seed,boolean defenseOn,int launched,int craftKills,
            int craftTerminal,int attackShots,int attackImpacts,int torpedoFired,
            int torpedoImpacts,int pdLaserShots,int flakShots,int flakAmmoSpent,
            long firstAircraftAttackTick,double actualDamage,
            double finalPowerFraction,double peakHeatFraction,int pdDistinctSocketsFired,
            int pdAcquisitions,int pdSlewTicks,int pdLockWaitTicks) {
        String csv(){
            return String.format(Locale.ROOT,
                "%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%d,%d,%d,%d",
                battery,threat,wave,distance,rank,seed,defenseOn,launched,
                craftKills,craftTerminal,attackShots,attackImpacts,torpedoFired,
                torpedoImpacts,pdLaserShots,flakShots,flakAmmoSpent,
                firstAircraftAttackTick,actualDamage,finalPowerFraction,
                peakHeatFraction,pdDistinctSocketsFired,pdAcquisitions,
                pdSlewTicks,pdLockWaitTicks);
        }
    }
    private static void require(boolean b,String why){
        if(!b)throw new IllegalStateException(why);
    }
    private static Shipyard.Design defenseFit(TechnologyCatalog c,Battery battery,int rank){
        Shipyard.Design d=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int pd=0,flak=0;
        for(Shipyard.Slot slot:d.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            String family;
            if(pd<battery.pd()&&(battery!=Battery.MIXED_TWO_TWO || pd<=flak)){
                family="PD";pd++;
            }else if(flak<battery.flak()){family="FLK";flak++;}
            else if(pd<battery.pd()){family="PD";pd++;}
            else continue;
            d=d.install(slot.index(),"BASE-TER-"+family);
            if(rank>0)d=d.stage(slot.index(),Shipyard.Track.SPECIALIST,rank);
        }
        require(pd==battery.pd()&&flak==battery.flak(),
                "Not enough real weapon hardpoints for "+battery);
        require(Shipyard.inspect(d,c).valid(),
                "Invalid real fitted "+battery+" rank "+rank);
        return d;
    }
    /** Functional invariants: two lasers + two flak cannot exceed the
     * exact same two lasers' precision reach, tracking, penetration or cycle. */
    private static void verifyHardwareIsolation(TechnologyCatalog catalog){
        Set<Integer> codes=new HashSet<>();
        for(Battery battery:Battery.values())
            require(codes.add(battery.stableSeedCode()),
                    "Duplicate immutable wave battery seed code");
        for(int rank:new int[]{0,9}){
            var pdDesign=defenseFit(catalog,Battery.PD_TWO,rank);
            var mixedDesign=defenseFit(catalog,Battery.MIXED_TWO_TWO,rank);
            var flakDesign=defenseFit(catalog,Battery.FLK_FOUR,rank);
            var pd=Shipyard.fit(pdDesign,catalog);
            var mixed=Shipyard.fit(mixedDesign,catalog);
            var flak=Shipyard.fit(flakDesign,catalog);
            require(Math.abs(pd.pdRange()-mixed.pdRange())<1e-8,
                    "Flak falsely increased mounted laser reach");
            require(Math.abs(pd.pdCycle()-mixed.pdCycle())<1e-8,
                    "Flak falsely accelerated laser fire control");
            require(Math.abs(pd.pointDefense().damage()-mixed.pointDefense().damage())<1e-8,
                    "Flak falsely boosted laser penetration");
            require(Math.abs(pd.pointDefense().tracking()-mixed.pointDefense().tracking())<1e-8,
                    "Flak falsely boosted laser tracking");
            require(Math.abs(pd.pointDefense().detectionMultiplier()-
                    mixed.pointDefense().detectionMultiplier())<1e-8,
                    "Flak falsely increased laser sensing");
            require(flak.pdRange()==0,"Pure flak generated a fictitious laser weapon");
            Battle battle=new Battle(790901L+rank);
            battle.spawnWing(1,mixedDesign,catalog,1,600,300);
            Battle.Ship ship=battle.ships().get(0);
            int lasers=0,flaks=0;
            for(WeaponMuzzles.Emitter emitter:ship.weaponEmitters){
                if(emitter.family()==WeaponMuzzles.Family.POINT_DEFENSE){
                    lasers++;
                    require(WeaponMuzzles.atSlot(ship,WeaponMuzzles.Family.POINT_DEFENSE,
                            emitter.slotId()).slotId()==emitter.slotId(),
                            "Laser did not use its fitted physical hardpoint");
                }
                if(emitter.family()==WeaponMuzzles.Family.FLAK){
                    flaks++;
                    require(WeaponMuzzles.atSlot(ship,WeaponMuzzles.Family.FLAK,
                            emitter.slotId()).slotId()==emitter.slotId(),
                            "Flak did not use its fitted physical hardpoint");
                    boolean borrowed=false;
                    try{
                        WeaponMuzzles.atSlot(ship,WeaponMuzzles.Family.POINT_DEFENSE,
                                emitter.slotId());
                        borrowed=true;
                    }catch(IllegalArgumentException expected){}
                    require(!borrowed,"Precision CIWS borrowed the flak socket");
                }
            }
            require(lasers==2&&flaks==2&&ship.flakMounts.size()==2&&
                    ship.precisionMounts.size()==2,
                    "Mixed fitted defenses failed hardware inventory");
            for(Battle.Ship.PrecisionMount mount:ship.precisionMounts){
                require(mixedDesign.at(mount.slotId).component.endsWith("-PD"),
                        "Precision mount borrowed another component's socket");
                require(Math.abs(mount.cycle-Shipyard.pointDefenseCycleAt(
                        mixedDesign,catalog,mount.slotId))<1e-8,
                        "Per-socket precision research cycle was lost");
            }
            // Independent installed timers must tick without synchronization.
            ship.precisionMounts.get(0).reload=3.0;
            ship.precisionMounts.get(1).reload=1.0;
            battle.step();
            require(Math.abs(ship.precisionMounts.get(0).reload-
                    ship.precisionMounts.get(1).reload-2.0)<1e-8,
                    "Separate precision mount cooldowns were merged");
            for(long shot=0;shot<8;shot++){
                int slot=WeaponMuzzles.at(ship,
                        WeaponMuzzles.Family.POINT_DEFENSE,shot).slotId();
                require(mixedDesign.at(slot).component.endsWith("-PD"),
                        "Precision laser round robin borrowed a flak hardpoint");
            }
        }
    }
    /** Mechanical targeting is actual elapsed tick time, not a global roll to
     * arbitrarily miss individual rockets. Verify initial scan, a target
     * switch, off-axis rotation and loss of contact for the same fitted part. */
    private static void verifyPrecisionTracking(TechnologyCatalog cat){
        Battle battle=new Battle(802410L);
        battle.spawnWing(1,defenseFit(cat,Battery.PD_ONE,0),cat,1,600,300);
        Battle.Ship ship=battle.ships().get(0);
        Battle.Ship.PrecisionMount mount=ship.precisionMounts.get(0);
        require(!battle.acquirePrecisionTarget(ship,mount,5001L,450,300,1),
                "Precision laser skipped initial optical acquisition");
        boolean ready=false;
        for(int t=2;t<20;t++)
            ready=battle.acquirePrecisionTarget(ship,mount,5001L,450,300,t);
        require(ready,"Precision laser never attained a stable target");
        require(!battle.acquirePrecisionTarget(ship,mount,5002L,750,300,20),
                "Precision laser skipped target switch / 180-degree traverse");
        require(mount.slewTicks>0,"Mechanical turret did not take time to rotate");
        ready=false;
        for(int t=21;t<60;t++)
            ready=battle.acquirePrecisionTarget(ship,mount,5002L,750,300,t);
        require(ready,"Off-axis mechanical turret could not traverse");
        require(!battle.acquirePrecisionTarget(ship,mount,5002L,750,300,70),
                "Stale track retained instantaneous lock after contact gap");
        require(mount.targetAcquisitions==2,
                "Stable target reacquisition counted a fictitious new body");
    }
    private static Shipyard.Design carrier(TechnologyCatalog c,Threat threat){
        Shipyard.Design d=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CRUISER);
        for(Shipyard.Slot s:d.hull.slots)
            if(s.mount()==Shipyard.Mount.SUPPORT){
                d=d.install(s.index(),"BASE-TER-FTR")
                        .withFighterHull(FighterModel.Hull.valueOf(threat.name()));
                require(Shipyard.inspect(d,c).valid(),"Invalid real FTR deck");
                return d;
            }
        throw new IllegalStateException("No physical FTR hardpoint");
    }
    private static Trial run(TechnologyCatalog cat,Battery battery,Threat threat,
                             int wave,int distance,Formation formation,int rank,int seed,boolean enabled){
        final long randomSeed=70260101L+threat.ordinal()*900003L+
                wave*10007L+distance*29L+rank*13L+
                battery.stableSeedCode()*11L+seed*173L;
        Battle b=new Battle(randomSeed);
        Shipyard.Design craftFit=carrier(cat,threat);
        final double defenderX=600,defenderY=300;
        List<Battle.Ship> carriers=new ArrayList<>();
        for(int i=0;i<wave;i++){
            double x=defenderX-distance;
            double y=defenderY+(i-(wave-1)/2.0)*formation.lateralSpacing;
            b.spawnWing(0,craftFit,cat,1,x,y);
            Battle.Ship source=b.ships().get(b.ships().size()-1);
            source.speed=source.vx=source.vy=0;
            source.laserDamage=source.kineticDamage=source.missileDamage=0;
            source.torpedoDamage=source.plasmaDamage=source.particleDamage=0;
            source.kineticAmmo=source.missileAmmo=source.torpedoAmmo=0;
            source.pdRange=0;
            source.droneReserve=source.repairDroneReserve=0;
            source.fighterReserve=1;
            require(source.fighterLaunchBays>0,"Test carrier lacks a real fighter bay");
            source.maxPower=source.power=10000;
            source.maxHeat=10000;
            source.maxHull=source.hull=500000;
            carriers.add(source);
        }
        b.spawnWing(1,defenseFit(cat,battery,rank),cat,1,defenderX,defenderY);
        Battle.Ship protectedShip=b.ships().get(b.ships().size()-1);
        protectedShip.speed=protectedShip.vx=protectedShip.vy=0;
        require(protectedShip.precisionMounts.size()==battery.pd(),
                "Precision defense socket inventory differs from purchased modules");
        protectedShip.maxHull=protectedShip.hull=1500000;
        b.registerDiagnosticStationaryTarget(protectedShip.id);
        b.setShipOrders(protectedShip.id,BattleOrders.DEFAULT.withPointDefense(
                enabled?BattleOrders.PointDefense.BALANCED:
                        BattleOrders.PointDefense.HOLD_FIRE));
        final double shields=protectedShip.shields,armor=protectedShip.armor,
                hull=protectedShip.hull;
        final int flakStock=protectedShip.kineticAmmo;
        Set<Integer> innerCraft=new HashSet<>();
        Set<Integer> firedPdSockets=new HashSet<>();
        long seq=0,firstAttack=-1;
        int attackShots=0,attackImpacts=0,torpedoes=0,torpedoHits=0,
                lasers=0,flak=0;
        double peakHeat=protectedShip.heat/Math.max(1,protectedShip.maxHeat);
        for(int tick=0;tick<600;tick++){
            b.step();
            for(Battle.Craft c:b.crafts())
                if(c.team==0&&StrictMath.hypot(c.x-protectedShip.x,
                            c.y-protectedShip.y)<=90)
                    innerCraft.add(c.id);
            peakHeat=Math.max(peakHeat,
                    protectedShip.heat/Math.max(1,protectedShip.maxHeat));
            for(Battle.VisualEvent e:b.visualEvents()){
                if(e.sequence()<=seq)continue;
                seq=e.sequence();
                if(e.team()==1&&e.sourceId()==protectedShip.id&&
                        e.phase()==Battle.VisualPhase.FIRE){
                    if("POINT_DEFENSE".equals(e.weapon())){
                        lasers++;
                        boolean found=false;
                        for(Battle.Ship.PrecisionMount socket:protectedShip.precisionMounts){
                            var origin=WeaponMuzzles.atSlot(protectedShip,
                                    WeaponMuzzles.Family.POINT_DEFENSE,socket.slotId);
                            if(StrictMath.hypot(e.x()-origin.x(),
                                    e.y()-origin.y())<0.00001){
                                firedPdSockets.add(socket.slotId);
                                found=true;break;
                            }
                        }
                        require(found,"Precision PD fired outside a paid physical socket");
                    }
                    if("FLAK".equals(e.weapon()))flak++;
                }
                if(e.team()!=0)continue;
                if(e.phase()==Battle.VisualPhase.FIRE&&
                        e.weapon().startsWith("FIGHTER_")){
                    attackShots++;
                    if(firstAttack<0)firstAttack=e.tick();
                    if("FIGHTER_TORPEDO".equals(e.weapon()))torpedoes++;
                }
                if(e.phase()==Battle.VisualPhase.IMPACT&&
                        e.targetId()==protectedShip.id){
                    attackImpacts++;
                    if("TORPEDO".equals(e.weapon()))torpedoHits++;
                }
            }
        }
        int launched=0;
        for(Battle.Ship c:carriers)launched+=c.fightersLaunched;
        require(launched==wave,"Expected simultaneous real aircraft "+wave+
                " but only "+launched+" launched, "+battery+"/"+threat+
                " at "+distance);
        int craftKills=b.pointDefenseCraftKills;
        require(craftKills>=0&&craftKills<=launched,
                "Craft kills outside physically launched aircraft");
        require(!enabled&&(lasers!=0||flak!=0)?false:true,
                "HOLD_FIRE still fired a PD or FLK gun");
        require(enabled||(craftKills==0),"HOLD_FIRE caused PD craft kills");
        require(torpedoHits<=torpedoes,
                "Torpedo impacts exceeded actual bomber ordnance");
        require(!enabled||battery.pd()>0||lasers==0,
                "FLK-only fit emitted free precision beams");
        require(!enabled||battery.flak()>0||flak==0,
                "PD-only fit emitted free fragmentation shells");
        require(flakStock-protectedShip.kineticAmmo>=flak,
                "Fitted flak fired without paying physical ammunition");
        double damage=Math.max(0,shields-protectedShip.shields)+
                Math.max(0,armor-protectedShip.armor)+
                Math.max(0,hull-protectedShip.hull);
        int acquisitions=0,slew=0,lockWait=0;
        for(Battle.Ship.PrecisionMount pd:protectedShip.precisionMounts){
            acquisitions+=pd.targetAcquisitions;
            slew+=pd.slewTicks;
            lockWait+=pd.lockWaitTicks;
        }
        return new Trial(battery,threat,wave,distance,rank,seed,enabled,
                launched,craftKills,innerCraft.size(),attackShots,attackImpacts,
                torpedoes,torpedoHits,lasers,flak,
                flakStock-protectedShip.kineticAmmo,firstAttack,damage,
                protectedShip.power/Math.max(1,protectedShip.maxPower),
                peakHeat,firedPdSockets.size(),acquisitions,slew,lockWait);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog cat=TechnologyCatalog.loadDefault();
        verifyHardwareIsolation(cat);
        verifyPrecisionTracking(cat);
        Path output=Path.of("test-output","pd-wave-diagnostic");
        Files.createDirectories(output);
        List<String> csv=new ArrayList<>();
        csv.add("formation,battery,aircraft,wave_size,start_distance,pd_stage,seed,pd_enabled,"+
                "fighters_launched,fighters_killed,inner_90_entries,attacker_shots,"+
                "attacker_impacts,torpedoes_launched,torpedo_impacts,"+
                "laser_pd_shots,flak_rounds,flak_kinetic_ammo_spent,"+
                "first_aircraft_attack_tick,defender_damage,"+
                "defender_final_power_fraction,defender_peak_heat_fraction,"+
                "pd_distinct_sockets_fired,pd_acquisitions,pd_slew_ticks,"+
                "pd_lock_wait_ticks");
        StringBuilder md=new StringBuilder("# Simultaneous fighter waves versus "+
                "real fitted laser/flak batteries\n\n");
        md.append("Physical FTR launches, exactly 4 or 8 simultaneous aircraft, "+
                "both interceptor and torpedo-bomber classes. "+
                "Compact 10-unit launch spacing retains historical RNG seeds; "+
                "dispersed 46-unit carrier spacing uses the exact SAME seeds "+
                "so geometry alone changes between paired A/B tests. "+
                "Matched same-seed HOLD_FIRE, no fitted offensive IX attacker; "+
                "three diagnostic seeds per case. "+
                "Never a release balance threshold.\n\n");
        md.append("|Formation|Aircraft|Wave|Start|Battery|Rank|Fighters killed|"+
                "Shots landed|Hold-fire shots landed|Torpedo impacts|"+
                "Hold-fire torpedo impacts|Flak rounds fired|\n");
        md.append("|---|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
        int cases=0,errors=0;
        for(Formation formation:Formation.values())
            for(Threat threat:Threat.values())for(int wave:new int[]{4,8})
            for(int distance:new int[]{95,220,390})for(Battery battery:Battery.values())
            for(int rank:new int[]{0,9}){
                // Add a focused, controlled geometry sample; do not double
                // the entire compact matrix or spawn unnecessary CI work.
                if(formation==Formation.DISPERSED&&
                        (wave!=8||distance==390||
                         (battery!=Battery.PD_FOUR&&battery!=Battery.FLK_ONE&&
                          battery!=Battery.FLK_FOUR&&battery!=Battery.MIXED_TWO_TWO)))
                    continue;
                int kills=0,impacts=0,controlImpacts=0,torpedoHits=0,
                        controlTorpedoHits=0,flakRounds=0;
                for(int seed=0;seed<3;seed++){
                    try{
                        Trial active=run(cat,battery,threat,wave,distance,formation,rank,seed,true);
                        Trial control=run(cat,battery,threat,wave,distance,formation,rank,seed,false);
                        csv.add(formation+","+active.csv());csv.add(formation+","+control.csv());
                        cases++;
                        kills+=active.craftKills();
                        impacts+=active.attackImpacts();
                        controlImpacts+=control.attackImpacts();
                        torpedoHits+=active.torpedoImpacts();
                        controlTorpedoHits+=control.torpedoImpacts();
                        flakRounds+=active.flakShots();
                    }catch(RuntimeException ex){
                        errors++;
                        csv.add("# DIAGNOSTIC_ERROR,"+formation+","+threat+","+wave+","+distance+","+
                                battery+","+rank+","+seed+","+ex);
                        System.err.println("PD fighter-wave case failed: "+ex);
                    }
                }
                md.append(String.format(Locale.ROOT,
                        "|%s|%s|%d|%d|%s|%d|%d|%d|%d|%d|%d|%d|\n",
                        formation,threat,wave,distance,battery,rank,kills,
                        impacts,controlImpacts,torpedoHits,
                        controlTorpedoHits,flakRounds));
            }
        md.append("\nCompleted paired conditions: "+cases+
                "; failed experimental cases: "+errors+
                ". These validations are advisory, not release gates.\n");
        Files.write(output.resolve("wave-trials.csv"),csv,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),md.toString(),StandardCharsets.UTF_8);
        System.out.println(md);
        if(errors>0)throw new IllegalStateException(
                "Fighter-wave diagnostic had "+errors+" invalid conditions; see artifact");
    }
}
