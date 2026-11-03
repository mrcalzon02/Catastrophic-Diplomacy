package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Mechanical checks and advisory same-seed effectiveness results for
 * actual bought rail point-defense penetrators (never instant beam hits). */
public final class RailPointDefenseDiagnostic {
    private RailPointDefenseDiagnostic(){}
    private enum Threat { MISSILE, TORPEDO, KINETIC }
    private record Trial(int launched,int fired,int visualFrames,int impacts,
                         int intercepted,int stockSpent,int powerPaid,
                         long firstShot,int railHits) {}
    private static void require(boolean ok,String why){
        if(!ok)throw new IllegalStateException(why);
    }
    private static Shipyard.Design fitted(TechnologyCatalog tech,int tier,int mounts){
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int n=0;
        for(Shipyard.Slot slot:fit.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON||n==mounts)continue;
            fit=fit.install(slot.index(),"BASE-TER-RPD");
            if(tier>0)fit=fit.stage(slot.index(),Shipyard.Track.SPECIALIST,tier);
            n++;
        }
        require(n==mounts&&Shipyard.inspect(fit,tech).valid(),
                "Invalid actually purchased RPD battery at tier "+tier+" x"+mounts);
        return fit;
    }
    private static void auditTechnology(TechnologyCatalog tech){
        for(String f:new String[]{"TER","DRA","CAR","ZIP","UZI","MAN",
                "VAE","ZEL","AUR","NYX","UMB"}){
            require(tech.base("BASE-"+f+"-RPD")!=null,
                    "Missing native physical railgun module: "+f);
            for(int rank:new int[]{1,3,6,9})
                require(tech.get(String.format(Locale.ROOT,
                        "TECH-%s-RPD-SPECIALIST-%02d",f,rank))!=null,
                        "Missing researched RPD capacitor/sensors: "+f+"/"+rank);
        }
        double last=0;
        for(int tier:new int[]{0,3,6,9}){
            Battle fixture=new Battle(889008L+tier);
            fixture.spawnWing(1,fitted(tech,tier,1),tech,1,370,300);
            Battle.Ship ship=fixture.ships().get(0);
            require(!ship.laserPointDefenseEnabled && ship.flakMounts.isEmpty() &&
                            ship.rotaryMounts.isEmpty()&&ship.railMounts.size()==1,
                    "Railgun unexpectedly borrowed another point-defense family");
            var gun=ship.railMounts.get(0);
            require(gun.researchTier==tier,"RPD per-socket research lost");
            require(gun.nominalRange>=RailPointDefense.MIN_ENGAGEMENT_RANGE&&
                    gun.nominalRange<=335&&gun.nominalRange>=last,
                    "RPD researched fitted sensor reach invalid");
            last=gun.nominalRange;
            require(ShipDamageTargeting.subsystem("BASE-TER-RPD")
                    ==Battle.Subsystem.POINT_DEFENSE,"RPD damage category wrong");
            require(WeaponMuzzles.atSlot(ship,
                    WeaponMuzzles.Family.RAIL_PD,gun.slotId).slotId()==gun.slotId,
                    "Rail penetrator is not fired from purchased hardpoint");
            ProjectileModel.Spec torpedo=ProjectileModel.standard(
                    ProjectileModel.Kind.TORPEDO);
            double intact=RailPointDefense.acquisitionRange(ship,gun,torpedo,1,1);
            ship.sensorsCondition=.4;
            double damaged=RailPointDefense.acquisitionRange(ship,gun,torpedo,1,1);
            require(intact>damaged&&damaged>0,
                    "Long-range rail tracking ignores sensor damage");
            ship.power=0;
            require(RailPointDefense.acquisitionRange(ship,gun,torpedo,1,1)==0,
                    "Unpowered rail sensor magically tracks targets");
        }
        require(RailPointDefense.PENETRATOR_SPEED>
                RotaryPointDefense.BULLET_SPEED,"Rail shot is not truly faster");
        require(RailPointDefense.CARTRIDGES_PER_SHOT>1,
                "Railgun stock not distinct from rotary caliber");
    }
    private static Trial trial(TechnologyCatalog tech,Threat threat,int tier,
                               int mounts,int seed,boolean enabled){
        Battle battle=new Battle(190031L+seed*127L+tier*29L+mounts*43L+
                threat.ordinal()*307L);
        Battle.Ship attacker=battle.addShip(0,Battle.Faction.TERRAN,65,300);
        battle.spawnWing(1,fitted(tech,tier,mounts),tech,1,365,300);
        Battle.Ship defender=battle.ships().get(1);
        battle.registerDiagnosticStationaryTarget(defender.id);
        defender.speed=defender.vx=defender.vy=0;
        attacker.speed=attacker.vx=attacker.vy=0;
        attacker.maxHull=attacker.hull=100000;
        attacker.maxPower=attacker.power=10000;
        attacker.maxHeat=10000;
        attacker.laserDamage=attacker.plasmaDamage=attacker.particleDamage=0;
        attacker.missileDamage=attacker.torpedoDamage=attacker.kineticDamage=0;
        attacker.missileAmmo=attacker.torpedoAmmo=attacker.kineticAmmo=0;
        attacker.fighterReserve=attacker.droneReserve=attacker.repairDroneReserve=0;
        attacker.pdRange=0;
        switch(threat){
            case MISSILE -> {attacker.missileDamage=35;attacker.missileAmmo=40;}
            case TORPEDO -> {attacker.torpedoDamage=65;attacker.torpedoAmmo=30;}
            case KINETIC -> {attacker.kineticDamage=27;attacker.kineticAmmo=30;}
        }
        battle.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(
                enabled?BattleOrders.PointDefense.INTERCEPT_ONLY:
                        BattleOrders.PointDefense.HOLD_FIRE));
        int initialAmmo=defender.kineticAmmo;
        double initialPower=defender.power;
        int liveFrames=0;
        long first=-1;
        for(int frame=0;frame<420;frame++){
            battle.step();
            liveFrames+=battle.railPenetrators().size();
            if(first<0)for(Battle.VisualEvent e:battle.visualEvents())
                if(e.phase()==Battle.VisualPhase.FIRE && e.team()==1 &&
                        "RAIL_PD".equals(e.weapon())){
                    first=e.tick();break;
                }
            if(frame==30)
                attacker.missileReload=attacker.torpedoReload=
                        attacker.kineticReload=10000;
        }
        int launched=0,fire=0,impacts=0,physicalHits=0;
        for(Battle.VisualEvent e:battle.visualEvents()){
            if(e.team()==1&&"RAIL_PD".equals(e.weapon())){
                if(e.phase()==Battle.VisualPhase.FIRE){
                    fire++;
                    boolean realSocket=false;
                    for(RailPointDefense.Mount gun:defender.railMounts){
                        WeaponMuzzles.Origin muzzle=WeaponMuzzles.atSlot(defender,
                                WeaponMuzzles.Family.RAIL_PD,gun.slotId);
                        if(StrictMath.hypot(e.x()-muzzle.x(),e.y()-muzzle.y())<.001){
                            realSocket=true;break;
                        }
                    }
                    require(realSocket,"Railgun did not launch from its actual paid socket");
                }
                if(e.phase()==Battle.VisualPhase.IMPACT)physicalHits++;
            }
            if(e.team()==0&&e.sourceId()==attacker.id){
                if(e.phase()==Battle.VisualPhase.FIRE&&threat.name().equals(e.weapon()))
                    launched++;
                if(e.phase()==Battle.VisualPhase.IMPACT&&e.targetId()==defender.id)
                    impacts++;
            }
        }
        int gunShots=0;
        for(RailPointDefense.Mount mount:defender.railMounts)
            gunShots+=mount.shotsFired;
        require(gunShots==fire,"Fitted railgun launch count differs from physical shot events");
        require(enabled||fire==0,"HOLD_FIRE launched defensive rail penetrators");
        require(launched>0,"Attacker produced no live "+threat+" ordnance");
        require(initialAmmo-defender.kineticAmmo>=
                fire*RailPointDefense.CARTRIDGES_PER_SHOT,
                "Railgun fired unpaid cartridges");
        require(!enabled||fire==0||first>=30,
                "Charged rail gun skipped its physical capacitor warmup");
        require(!enabled||fire==0||liveFrames>0||physicalHits>0,
                "Railgun fired but its penetrator had no world-space flight or hit");
        return new Trial(launched,fire,liveFrames,impacts,defender.intercepted,
                initialAmmo-defender.kineticAmmo,
                (int)StrictMath.ceil(initialPower-defender.power),first,physicalHits);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        auditTechnology(tech);
        Path root=Path.of("test-output","rail-pd");
        Files.createDirectories(root);
        List<String> rows=new ArrayList<>();
        rows.add("threat,research_tier,rail_mounts,seed,enabled,launched,"+
                "rail_fired,live_flight_frames,actual_rail_hits,intercepts,"+
                "actual_impacts,total_kinetic_stock_loss,power_delta,first_shot_tick");
        StringBuilder report=new StringBuilder("# RPD charged rail interceptor physical trials\n\n");
        report.append("All mounts, launchers, target bodies and kinetic cartridge"+
                " budgets are real. Never equate a simulated shot with an automatic"+
                " hit; numeric intercept rates are advisory.\n\n");
        report.append("|Threat|Rail stage|Mounts|Launched|Physical rail shots|"+
                "Interceptions|Attacker hits|HOLD_FIRE hits|Cartridges depleted|\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        int paired=0;
        for(Threat threat:Threat.values())
            for(int tier:new int[]{0,9})
                for(int count:new int[]{1,4}){
                    int launched=0,fired=0,intercepted=0,impacts=0,control=0,spent=0;
                    for(int seed=0;seed<3;seed++){
                        Trial live=trial(tech,threat,tier,count,seed,true);
                        Trial hold=trial(tech,threat,tier,count,seed,false);
                        rows.add(String.format(Locale.ROOT,
                                "%s,%d,%d,%d,true,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                                threat,tier,count,seed,live.launched,live.fired,
                                live.visualFrames,live.railHits,live.intercepted,
                                live.impacts,live.stockSpent,live.powerPaid,live.firstShot));
                        rows.add(String.format(Locale.ROOT,
                                "%s,%d,%d,%d,false,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                                threat,tier,count,seed,hold.launched,hold.fired,
                                hold.visualFrames,hold.railHits,hold.intercepted,
                                hold.impacts,hold.stockSpent,hold.powerPaid,hold.firstShot));
                        launched+=live.launched;fired+=live.fired;
                        intercepted+=live.intercepted;impacts+=live.impacts;
                        control+=hold.impacts;spent+=live.stockSpent;paired++;
                    }
                    report.append(String.format(Locale.ROOT,
                            "|%s|%d|%d|%d|%d|%d|%d|%d|%d|\n",
                            threat,tier,count,launched,fired,intercepted,
                            impacts,control,spent));
                }
        Files.write(root.resolve("rail-trials.csv"),rows,StandardCharsets.UTF_8);
        Files.writeString(root.resolve("summary.md"),report);
        System.out.print(report);
        System.out.println("RAIL_PD_PAIRED_TRIALS="+paired);
    }
}
