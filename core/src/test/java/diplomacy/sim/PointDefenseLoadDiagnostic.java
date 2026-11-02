package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advisory empirical load testing against real fitted PD, physical stock,
 * thermal cycling and 30 Hz projectiles. Compares incidental opportunities,
 * one concentrated surge, and repeated sustainable volleys. Every PD-enabled
 * run has an identically configured HOLD_FIRE control.
 *
 * This test NEVER requires any particular hit or balance percentage.
 */
public final class PointDefenseLoadDiagnostic {
    private PointDefenseLoadDiagnostic(){}
    private enum Load { INCIDENTAL, SURGE, SUSTAINED }
    private enum Threat {
        KINETIC, MISSILE, TORPEDO, ROCKET_BOOST, ROCKET_SUSTAIN,
        FIGHTER_INTERCEPTOR, FIGHTER_TORPEDO_BOMBER, MIXED;
        boolean fighter(){return name().startsWith("FIGHTER_");}
        FighterModel.Hull fighterHull(){
            return FighterModel.Hull.valueOf(name().substring("FIGHTER_".length()));
        }
    }
    private enum Layout { FOUR_MOUNTS_ONE_HULL, FOUR_INDEPENDENT_HULLS }
    private static final int ACTIVE=660, DRAIN=300;
    private static final int[] BOUNDARY={0,220,440,660,960};

    private static void check(boolean ok,String message){
        if(!ok)throw new IllegalStateException(message);
    }
    private static Shipyard.Design pdHull(TechnologyCatalog catalog,int rank,int mounts){
        Shipyard.Design design=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int count=0;
        for(Shipyard.Slot slot:design.hull.slots){
            if(count==mounts)break;
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            design=design.install(slot.index(),"BASE-TER-PD");
            if(rank>0)design=design.stage(slot.index(),Shipyard.Track.SPECIALIST,rank);
            count++;
        }
        check(count==mounts&&Shipyard.inspect(design,catalog).valid(),
                "Missing genuine rank "+rank+" PD hardware: "+mounts);
        return design;
    }
    private static Shipyard.Design rocketHull(TechnologyCatalog catalog){
        Shipyard.Design design=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        boolean added=false;
        for(Shipyard.Slot slot:design.hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON){
                design=design.install(slot.index(),"BASE-TER-RKG");
                added=true;break;
            }
        check(added&&Shipyard.inspect(design,catalog).valid(),
                "Missing genuine siege rocket rack");
        return design;
    }
    private static Shipyard.Design carrier(TechnologyCatalog catalog,FighterModel.Hull hull){
        Shipyard.Design fit=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        for(Shipyard.Slot slot:fit.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                fit=fit.install(slot.index(),"BASE-TER-FTR").withFighterHull(hull);
                check(Shipyard.inspect(fit,catalog).valid(),
                        "Invalid physical fighter launch bay for "+hull);
                return fit;
            }
        throw new IllegalStateException("No physical fighter hangar socket");
    }
    private record Source(Battle.Ship ship,Threat weapon) { }
    private static void quiet(Battle.Ship s){
        s.speed=s.vx=s.vy=0;
        s.laserDamage=s.plasmaDamage=s.particleDamage=0;
        s.missileDamage=s.torpedoDamage=s.kineticDamage=0;
        s.missileAmmo=s.torpedoAmmo=0;
        s.fighterReserve=s.droneReserve=s.repairDroneReserve=0;
        s.pdRange=0;
        s.maxPower=s.power=20000;
        s.maxHeat=20000;s.heat=0;
        s.powerRegen=17;
        s.maxHull=s.hull=500000;
    }
    private static Source source(Battle battle,TechnologyCatalog catalog,Threat threat,
                                  Load load,int index){
        Battle.Ship s;
        double x=140+index*7,y=300+(index%3-1)*17;
        if(threat.fighter()){
            battle.spawnWing(0,carrier(catalog,threat.fighterHull()),catalog,1,x,y);
            s=battle.ships().get(battle.ships().size()-1);
            int fittedFighters=s.fighterReserve;
            quiet(s);
            check(fittedFighters>0,"No genuine carrier fighter stores");
            s.fighterReserve=load==Load.INCIDENTAL?1:fittedFighters;
        }else if(threat==Threat.ROCKET_BOOST||threat==Threat.ROCKET_SUSTAIN){
            battle.spawnWing(0,rocketHull(catalog),catalog,1,x,y);
            s=battle.ships().get(battle.ships().size()-1);
            quiet(s);
            // A real mounted rack keeps its on-board magazine and reload.
            // The lab provisions and accounts for its paid campaign-equivalent
            // kinetic supply, as the original rocket/PD ladder does; do not
            // leave a zero-stock hull and then measure "no launches".
            int stock=0;
            for(RocketBarrage.Rack rack:s.rocketRacks){
                battle.setShipRocketBarrage(s.id,rack.slot,
                        load==Load.INCIDENTAL?4:16,
                        threat==Threat.ROCKET_BOOST?
                                RocketBarrage.Motor.BOOST_COAST:
                                RocketBarrage.Motor.SUSTAINED_BURN);
                stock+=rack.rounds*rack.cheapAmmoUnitsPerRocket;
            }
            s.kineticAmmo=s.maxKineticAmmo=stock;
        }else{
            s=battle.addShip(0,Battle.Faction.TERRAN,x,y);
            quiet(s);
            if(threat==Threat.KINETIC){
                s.kineticDamage=28;s.kineticAmmo=180;s.maxKineticAmmo=180;
            }else{
                boolean heavy=threat==Threat.TORPEDO;
                if(heavy){s.torpedoDamage=65;s.torpedoAmmo=80;s.maxTorpedoAmmo=80;}
                else{s.missileDamage=38;s.missileAmmo=100;s.maxMissileAmmo=100;}
                battle.setShipLaunchMode(s.id,heavy?Battle.DamageType.TORPEDO:
                        Battle.DamageType.MISSILE,
                        load==Load.INCIDENTAL?OrdnanceDoctrine.Mode.SINGLE:
                                OrdnanceDoctrine.Mode.SATURATION);
            }
        }
        return new Source(s,threat);
    }
    private record Result(Load load,Threat threat,Layout layout,int pdRank,int seed,
                          boolean pdOn,int launched,int intercepted,int impacted,
                          int timedOut,int terminal,int unresolved,int pdShots,
                          int firstShots,int middleShots,int lateShots,int drainShots,
                          int firstKills,int middleKills,int lateKills,int drainKills,
                          int firstLaunched,int middleLaunched,int lateLaunched,
                          double minPowerFraction,double peakHeatFraction,
                          double defenderHullLoss,int completedTicks,
                          int kineticPaid,int missilePaid,int torpedoPaid) {
        String csv(){
            return String.format(Locale.ROOT,
                    "%s,%s,%s,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.5f,%.5f,%.3f,%d,%d,%d,%d",
                    load,threat,layout,pdRank,seed,pdOn,launched,intercepted,impacted,
                    timedOut,terminal,unresolved,pdShots,firstShots,middleShots,
                    lateShots,drainShots,firstKills,middleKills,lateKills,drainKills,
                    firstLaunched,middleLaunched,lateLaunched,minPowerFraction,
                    peakHeatFraction,defenderHullLoss,completedTicks,kineticPaid,
                    missilePaid,torpedoPaid);
        }
    }
    private static int band(long tick){
        for(int i=0;i<BOUNDARY.length-1;i++)
            if(tick>=BOUNDARY[i]&&tick<BOUNDARY[i+1])return i;
        return 3;
    }
    private static Result run(TechnologyCatalog catalog,Load load,Threat threat,
                              Layout layout,int rank,int seed,boolean pdOn){
        long randomSeed=4583007L+load.ordinal()*100003L+
                threat.ordinal()*971L+layout.ordinal()*61L+rank*11L+seed*3L;
        Battle battle=new Battle(randomSeed);
        List<Source> attackers=new ArrayList<>();
        int attackShips=load==Load.INCIDENTAL?1:4;
        if(threat==Threat.MIXED){
            Threat[] pattern={Threat.ROCKET_BOOST,Threat.MISSILE,
                    Threat.KINETIC,Threat.TORPEDO};
            for(int i=0;i<attackShips;i++)
                attackers.add(source(battle,catalog,pattern[i],load,i));
        }else for(int i=0;i<attackShips;i++)
            attackers.add(source(battle,catalog,threat,load,i));
        int kineticBefore=0,missileBefore=0,torpedoBefore=0;
        for(Source src:attackers){
            kineticBefore+=src.ship.kineticAmmo;
            missileBefore+=src.ship.missileAmmo;
            torpedoBefore+=src.ship.torpedoAmmo;
        }

        int defenders=layout==Layout.FOUR_MOUNTS_ONE_HULL?1:4;
        battle.spawnWing(1,pdHull(catalog,rank,layout==Layout.FOUR_MOUNTS_ONE_HULL?4:1),
                catalog,defenders,550,300);
        List<Battle.Ship> guard=new ArrayList<>();
        double originalHull=0;
        for(Battle.Ship ship:battle.ships())if(ship.team==1){
            ship.speed=ship.vx=ship.vy=0;
            ship.maxHull=ship.hull=1500000;
            battle.registerDiagnosticStationaryTarget(ship.id);
            originalHull+=ship.hull;
            guard.add(ship);
        }
        check(guard.size()==defenders,"Missing physical PD defenders");
        battle.setTeamPointDefense(1,pdOn?BattleOrders.PointDefense.BALANCED:
                BattleOrders.PointDefense.HOLD_FIRE);
        long lastEvent=-1;
        Set<Long> launchIds=new HashSet<>(),interceptIds=new HashSet<>();
        Set<Long> impactIds=new HashSet<>(),timeoutIds=new HashSet<>(),terminalIds=new HashSet<>();
        int[] firedByBand=new int[4],pdByBand=new int[4],killsByBand=new int[4];
        int pdShots=0,previousCraftLosses=0;double minPower=1,peakHeat=0;
        Set<Integer> craftTerminal=new HashSet<>();
        for(int tick=0;tick<ACTIVE+DRAIN;tick++){
            for(int index=0;index<attackers.size();index++){
                Source src=attackers.get(index);
                // The source is never restocked. Gate only the installed
                // offensive hardware to create the desired load schedule;
                // all launched bodies remain real and continue flight.
                boolean active=switch(load){
                    case INCIDENTAL -> tick<300&&tick%105<12&&index==0;
                    case SURGE -> tick<36;
                    case SUSTAINED -> tick<ACTIVE;
                };
                src.ship.weaponsCondition=active?1:0;
                // Fighter sorties are independent of gun condition. An
                // incidental/surge order closes the real launch lane after
                // the opening aircraft, without erasing stored craft or
                // interfering with aircraft already flying.
                if(src.weapon.fighter()&&load!=Load.SUSTAINED&&tick>0)
                    src.ship.fighterLaunchBays=0;
            }
            battle.step();
            if(battle.craftLosses>previousCraftLosses){
                killsByBand[band(tick)]+=battle.craftLosses-previousCraftLosses;
                previousCraftLosses=battle.craftLosses;
            }
            for(Battle.Craft fighter:battle.crafts())if(fighter.team==0)
                for(Battle.Ship defender:guard)
                    if(StrictMath.hypot(fighter.x-defender.x,
                            fighter.y-defender.y)<=90)
                        craftTerminal.add(fighter.id);
            for(Battle.Missile m:battle.missiles())if(m.team==0)
                for(Battle.Ship defender:guard)
                    if(StrictMath.hypot(m.x-defender.x,m.y-defender.y)<=90)
                        terminalIds.add(m.visualId);
            for(Battle.Rocket r:battle.rockets())if(r.team==0)
                for(Battle.Ship defender:guard)
                    if(StrictMath.hypot(r.x-defender.x,r.y-defender.y)<=90)
                        terminalIds.add(r.visualId);
            for(Battle.Ballistic b:battle.ballistics())if(b.team==0)
                for(Battle.Ship defender:guard)
                    if(StrictMath.hypot(b.x-defender.x,b.y-defender.y)<=90)
                        terminalIds.add(b.visualId);
            for(Battle.Ship defender:guard){
                minPower=Math.min(minPower,
                        defender.power/Math.max(1,defender.maxPower));
                peakHeat=Math.max(peakHeat,
                        defender.heat/Math.max(1,defender.maxHeat));
            }
            // A bounded visual history: only process records newer than the
            // previously observed sequence, even if its head was truncated.
            List<Battle.VisualEvent> events=battle.visualEvents();
            for(int i=events.size()-1;i>=0;i--){
                Battle.VisualEvent e=events.get(i);
                if(e.sequence()<=lastEvent)break;
                int b=band(e.tick());
                if(e.phase()==Battle.VisualPhase.FIRE &&e.team()==1
                        &&"POINT_DEFENSE".equals(e.weapon())){
                    pdShots++;pdByBand[b]++;
                }
                if(e.team()!=0||!isPhysical(e.weapon()))continue;
                if(e.phase()==Battle.VisualPhase.FIRE){
                    launchIds.add(e.shotId());firedByBand[b]++;
                }else if(e.phase()==Battle.VisualPhase.INTERCEPT){
                    if(interceptIds.add(e.shotId()))killsByBand[b]++;
                }else if(e.phase()==Battle.VisualPhase.IMPACT){
                    impactIds.add(e.shotId());terminalIds.add(e.shotId());
                }else if(e.phase()==Battle.VisualPhase.TIMEOUT)
                    timeoutIds.add(e.shotId());
            }
            if(!events.isEmpty())lastEvent=events.get(events.size()-1).sequence();
        }
        int unresolved=0,kineticPaid=kineticBefore,missilePaid=missileBefore,
                torpedoPaid=torpedoBefore,craftLaunched=0;
        for(Source src:attackers){
            if(src.weapon.fighter())craftLaunched+=src.ship.fightersLaunched;
            kineticPaid-=src.ship.kineticAmmo;
            missilePaid-=src.ship.missileAmmo;
            torpedoPaid-=src.ship.torpedoAmmo;
        }
        for(long id:launchIds)
            if(!interceptIds.contains(id)&&!impactIds.contains(id)&&!timeoutIds.contains(id))
                unresolved++;
        int actualLaunched=launchIds.size()+craftLaunched;
        int actualIntercepted=interceptIds.size()+battle.craftLosses;
        int actualTerminal=terminalIds.size()+craftTerminal.size();
        unresolved+=craftLaunched-battle.craftLosses;
        double endingHull=0;
        for(Battle.Ship defender:guard)endingHull+=defender.hull;
        check(actualLaunched>0,"No actual paid projectile or fitted craft launched");
        check(pdOn||pdShots==0,"PD shots while defensive system was ordered HOLD_FIRE");
        check(actualIntercepted<=actualLaunched,
                "Intercept count exceeds launched physical bodies or craft");
        check(kineticPaid>=0&&missilePaid>=0&&torpedoPaid>=0,
                "A diagnostic source created ammunition");
        return new Result(load,threat,layout,rank,seed,pdOn,actualLaunched,
                actualIntercepted,impactIds.size(),timeoutIds.size(),
                actualTerminal,unresolved,pdShots,
                pdByBand[0],pdByBand[1],pdByBand[2],pdByBand[3],
                killsByBand[0],killsByBand[1],killsByBand[2],killsByBand[3],
                firedByBand[0],firedByBand[1],firedByBand[2],
                minPower,peakHeat,Math.max(0,originalHull-endingHull),
                ACTIVE+DRAIN,kineticPaid,missilePaid,torpedoPaid);
    }
    private static boolean isPhysical(String kind){
        return "KINETIC".equals(kind)||"ROCKET".equals(kind)||
                "MISSILE".equals(kind)||"TORPEDO".equals(kind)||
                "DECOY".equals(kind);
    }
    public static void main(String[] args)throws IOException{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Path out=Path.of("test-output","pd-load-profiles");
        Files.createDirectories(out);
        List<String> rows=new ArrayList<>();
        rows.add("load,threat,pd_layout,pd_tier,seed,pd_enabled,launched,intercepted,impacted,timed_out,terminal_entries,unresolved_at_end,pd_shots,early_pd_shots,middle_pd_shots,late_pd_shots,drain_pd_shots,early_kills,middle_kills,late_kills,drain_kills,early_launched,middle_launched,late_launched,min_reactor_fraction,peak_heat_fraction,hull_damage,simulated_ticks,kinetic_units_spent,missile_units_spent,torpedo_units_spent");
        StringBuilder summary=new StringBuilder("# Incidental, surge and sustained point-defense load\n\n");
        summary.append("One vs four independent PD controllers with the SAME four genuine installed PD modules. ")
                .append("Baseline versus specialist IX. Two paired seeds, PD on/off; all percentages advisory. ")
                .append("One gun controller per ship means four mounts on one hull may have less throughput ")
                .append("than four independently firing hulls.\n\n");
        summary.append("|Profile|Threat|PD layout|Tier|Launches|Intercepted|Kill %|Terminal entries|PD shots|Min power|Peak heat|Unresolved|\n");
        summary.append("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int cases=0,errors=0;
        for(Load load:Load.values())
            for(Threat threat:Threat.values())
                for(Layout layout:Layout.values())
                    for(int rank:new int[]{0,9}){
                        int launched=0,killed=0,terminal=0,pdShots=0,unresolved=0;
                        double minPower=1,peakHeat=0;
                        for(int seed=0;seed<2;seed++){
                            try{
                                Result active=run(catalog,load,threat,layout,rank,seed,true);
                                Result control=run(catalog,load,threat,layout,rank,seed,false);
                                rows.add(active.csv());rows.add(control.csv());
                                launched+=active.launched;killed+=active.intercepted;
                                terminal+=active.terminal;pdShots+=active.pdShots;
                                unresolved+=active.unresolved;
                                minPower=Math.min(minPower,active.minPowerFraction);
                                peakHeat=Math.max(peakHeat,active.peakHeatFraction);
                                cases+=2;
                            }catch(RuntimeException ex){
                                errors++;
                                rows.add("# DIAGNOSTIC_ERROR,"+load+","+threat+","+layout+
                                        ","+rank+","+seed+","+ex.getClass().getSimpleName()+
                                        ":"+ex.getMessage());
                                System.err.println("PD load case "+load+"/"+threat+"/"+layout+
                                        "/"+rank+"/"+seed+": "+ex);
                            }
                        }
                        summary.append(String.format(Locale.ROOT,
                                "|%s|%s|%s|%d|%d|%d|%.1f%%|%d|%d|%.2f|%.2f|%d|\n",
                                load,threat,layout,rank,launched,killed,
                                launched==0?0:100.0*killed/launched,terminal,pdShots,
                                minPower,peakHeat,unresolved));
                    }
        summary.append("\nCompleted paired scenarios: ").append(cases/2)
                .append("; failed diagnostic scenarios: ").append(errors)
                .append(". Failures are reported, NOT release-blocking.\n")
                .append("Incidentals are spaced by 105 ticks; surge opens four simultaneous ")
                .append("ship launcher lanes; sustained permits physical reloads through tick 660 ")
                .append("and resolves in-flight ordnance to tick 960. Window counts are EVENT ")
                .append("throughput, not cohort-matched kill rates. Projectiles not resolved by ")
                .append("tick 960 remain explicitly counted as unresolved.\n");
        Files.write(out.resolve("profiles.csv"),rows,StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.md"),summary.toString());
        System.out.println(summary);
        System.out.println("PD_LOAD_PROFILE_CSV="+out.resolve("profiles.csv"));
    }
}
