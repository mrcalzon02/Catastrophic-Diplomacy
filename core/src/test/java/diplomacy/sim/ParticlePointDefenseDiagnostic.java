package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.fx.WeaponFxProfile;
import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real fitted particle-beam hard-kill measurements against actual launched
 * missiles/torpedoes/kinetic rounds. Numerical interception results are
 * ADVISORY; only physically false firing, energy, heat and identity block.
 */
public final class ParticlePointDefenseDiagnostic {
    private ParticlePointDefenseDiagnostic(){}
    private enum Threat { MISSILE, TORPEDO, KINETIC }
    private record Trial(int fired,int launched,int killed,int impacts,
                         int paidTickCount,int peakHeatLockouts,
                         int peakPowerLockouts,double damage,int activeBeams){}
    private static void require(boolean yes,String message){
        if(!yes)throw new IllegalStateException(message);
    }
    private static Shipyard.Design battery(TechnologyCatalog tech,int tier,int n){
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int installed=0;
        for(Shipyard.Slot slot:fit.hull.slots){
            if(installed==n)break;
            if(slot.mount()!=Shipyard.Mount.WEAPON)continue;
            fit=fit.install(slot.index(),"BASE-TER-PPD");
            if(tier>0)
                fit=fit.stage(slot.index(),Shipyard.Track.SPECIALIST,tier);
            installed++;
        }
        require(installed==n&&Shipyard.inspect(fit,tech).valid(),
                "Research-licensed physical PPD battery invalid");
        return fit;
    }
    private static void auditHardware(TechnologyCatalog technology){
        for(String faction:new String[]{"TER","DRA","CAR","ZIP","UZI","MAN",
                "VAE","ZEL","AUR","NYX","UMB"}){
            require(technology.base("BASE-"+faction+"-PPD")!=null,
                    "Missing real PPD base for faction "+faction);
            for(String track:new String[]{"OUTPUT","EFFICIENCY",
                    "RESILIENCE","SPECIALIST"})
                for(int rank:new int[]{1,3,6,9})
                    require(technology.get(String.format(Locale.ROOT,
                            "TECH-%s-PPD-%s-%02d",faction,track,rank))!=null,
                            "Missing PPD researched mechanism "+faction+"/"+track+"/"+rank);
        }
        require(WeaponFxProfile.forWeapon("PARTICLE_PD")!=null&&
                WeaponFxProfile.forWeapon("PARTICLE_PD").firing().present(),
                "Powered PPD has no real particle-beam muzzle effect profile");
        double last=0;
        for(int tier:new int[]{0,3,6,9}){
            Battle b=new Battle(66100+tier);
            b.spawnWing(1,battery(technology,tier,1),technology,1,550,300);
            Battle.Ship s=b.ships().get(0);
            require(s.particlePdMounts.size()==1 && !s.laserPointDefenseEnabled&&
                    s.precisionMounts.isEmpty()&&s.flakMounts.isEmpty()&&
                    s.rotaryMounts.isEmpty()&&s.railMounts.isEmpty(),
                    "PPD borrowed another point-defense family");
            ParticlePointDefense.Mount mount=s.particlePdMounts.get(0);
            require(mount.researchTier==tier&&mount.nominalRange>=last&&
                    mount.nominalRange>=ParticlePointDefense.MIN_RANGE&&
                    mount.nominalRange<=ParticlePointDefense.MAX_RANGE,
                    "Independent researched PPD sensor envelope is invalid");
            last=mount.nominalRange;
            require(ShipDamageTargeting.subsystem("BASE-TER-PPD")==
                    Battle.Subsystem.POINT_DEFENSE,"PPD survives damage as wrong subsystem");
            require(WeaponMuzzles.family("BASE-TER-PPD")==
                    WeaponMuzzles.Family.PARTICLE_PD,
                    "PPD reused an offensive particle weapon muzzle");
            var origin=WeaponMuzzles.atSlot(s,
                    WeaponMuzzles.Family.PARTICLE_PD,mount.slotId);
            require(origin.slotId()==mount.slotId,
                    "PPD beam cannot originate at its actual purchased socket");
            ProjectileModel.Spec torpedo=ProjectileModel.standard(
                    ProjectileModel.Kind.TORPEDO);
            double intact=ProjectileModel.acquisitionRange(mount.nominalRange,
                    s.sensorsCondition,1,mount.profile,torpedo);
            s.sensorsCondition=.35;
            double impaired=ProjectileModel.acquisitionRange(mount.nominalRange,
                    s.sensorsCondition,1,mount.profile,torpedo);
            require(intact>impaired&&impaired>0,
                    "Damaged PPD sensors retained perfect target knowledge");
        }
    }
    private static void thermalAndDwell(){
        ParticlePointDefense.Mount mount=new ParticlePointDefense.Mount(0,0,190,
                ProjectileModel.STANDARD_PD,1);
        require(!mount.track(17,110,300,100,300,0,1,1,1.0/30),
                "Particle beam instantly acquired a fresh target");
        boolean ready=false;
        for(int t=2;t<=18;t++)
            ready=mount.track(17,110,300,100,300,0,1,t,1.0/30);
        require(ready,"Particle turret could not acquire a stable illuminated contact");
        mount.illuminate(1.0/30);
        require(mount.beamDwellSeconds>0,"Actual beam dwell not accumulated");
        require(!mount.track(18,110,300,100,300,0,1,19,1.0/30)&&
                mount.beamDwellSeconds==0,
                "Switched target inherited another projectile's armor exposure");
        for(int i=0;i<90;i++)mount.illuminate(1.0/30);
        require(!mount.available(),"Particle projector never overheated under continuous duty");
        for(int i=0;i<10;i++)mount.cool(1.0/30,1);
        require(!mount.available(),
                "Thermal trip flickered off after only a few cooled frames");
        for(int i=10;i<110;i++)mount.cool(1.0/30,1);
        require(mount.available(),"Cooled particle projector remained falsely locked out");
        double hardened=mount.ablationPerSecond(
                ProjectileModel.standard(ProjectileModel.Kind.TORPEDO));
        require(hardened>0&&Double.isFinite(hardened),
                "Sustained particle ablation never damages intact armored targets");
    }
    /** Real launcher module for equal-research attacker tests. All shots
     * still originate from installed weapon sockets and use actual physical
     * projectile health, speed, motor and ammunition access. */
    private static Shipyard.Design armedAttacker(TechnologyCatalog tech,
                                                 Threat threat,int rank){
        Shipyard.Design design=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        String type=switch(threat){
            case MISSILE -> "MSL";
            case TORPEDO -> "TOR";
            case KINETIC -> "KIN";
        };
        for(Shipyard.Slot slot:design.hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON){
                design=design.install(slot.index(),"BASE-TER-"+type);
                if(rank>0)design=design.stage(slot.index(),
                        Shipyard.Track.SPECIALIST,rank);
                require(Shipyard.inspect(design,tech).valid(),
                        "Rank-matched attacker has an invalid real weapon");
                return design;
            }
        throw new IllegalStateException("No physical launcher hardpoint");
    }
    private static Trial trial(TechnologyCatalog tech,Threat threat,int tier,
                               int count,int seed,boolean on){
        return trial(tech,threat,tier,count,seed,on,-1,0,false);
    }
    private static Trial trial(TechnologyCatalog tech,Threat threat,int tier,
                               int count,int seed,boolean on,
                               int attackingTier,double lateralOffset,boolean moving){
        Battle b=new Battle(73107L+seed*17L+tier*73L+
                threat.ordinal()*991L+count*109L+
                attackingTier*5003L+(long)(lateralOffset*7));
        Battle.Ship attacker;
        if(attackingTier<0)
            attacker=b.addShip(0,Battle.Faction.TERRAN,325,300+lateralOffset);
        else{
            b.spawnWing(0,armedAttacker(tech,threat,attackingTier),tech,
                    1,325,300+lateralOffset);
            attacker=b.ships().get(0);
        }
        b.spawnWing(1,battery(tech,tier,count),tech,1,550,300);
        Battle.Ship defender=b.ships().get(1);
        if(!moving){
            b.registerDiagnosticStationaryTarget(defender.id);
            defender.speed=defender.vx=defender.vy=0;
            attacker.speed=attacker.vx=attacker.vy=0;
        }
        attacker.maxHull=attacker.hull=300000;
        attacker.maxPower=attacker.power=10000;
        attacker.maxHeat=10000;
        attacker.fighterReserve=attacker.droneReserve=attacker.repairDroneReserve=0;
        attacker.pdRange=0;
        if(attackingTier<0){
            // Historical baseline from the first verified 24 A/B cases.
            attacker.laserDamage=attacker.plasmaDamage=attacker.particleDamage=0;
            attacker.kineticDamage=attacker.missileDamage=attacker.torpedoDamage=0;
            attacker.kineticAmmo=attacker.missileAmmo=attacker.torpedoAmmo=0;
            switch(threat){
                case MISSILE -> {attacker.missileDamage=28;attacker.missileAmmo=60;}
                case TORPEDO -> {attacker.torpedoDamage=46;attacker.torpedoAmmo=50;}
                case KINETIC -> {attacker.kineticDamage=22;attacker.kineticAmmo=80;}
            }
        }else{
            // Don't overwrite actual fitted damage or projectile properties.
            double output=switch(threat){
                case MISSILE -> attacker.missileDamage;
                case TORPEDO -> attacker.torpedoDamage;
                case KINETIC -> attacker.kineticDamage;
            };
            require(output>0,"Physically fitted research weapon had no offensive output");
        }
        defender.kineticAmmo=0;
        b.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(
                on?BattleOrders.PointDefense.INTERCEPT_ONLY:
                        BattleOrders.PointDefense.HOLD_FIRE));
        double protectedInitially=defender.shields+defender.armor+defender.hull;
        long previous=0;
        int fired=0,launched=0,impacts=0,activeBeams=0;
        for(int t=0;t<420;t++){
            b.step();
            for(Battle.Beam beam:b.beams())
                if(beam.pointDefense&&beam.type==Battle.DamageType.PARTICLE)
                    activeBeams++;
            for(Battle.VisualEvent e:b.visualEvents()){
                if(e.sequence()<=previous)continue;
                previous=e.sequence();
                if(e.team()==1&&e.phase()==Battle.VisualPhase.FIRE&&
                        "PARTICLE_PD".equals(e.weapon())){
                    fired++;
                    boolean mounted=false;
                    for(ParticlePointDefense.Mount gun:defender.particlePdMounts){
                        var origin=WeaponMuzzles.atSlot(defender,
                                WeaponMuzzles.Family.PARTICLE_PD,gun.slotId);
                        if(StrictMath.hypot(e.x()-origin.x(),e.y()-origin.y())<.0001){
                            mounted=true;break;
                        }
                    }
                    require(mounted,"Particle beam originated outside the actual PPD hardpoint");
                }
                if(e.team()==0&&e.sourceId()==attacker.id){
                    if(e.phase()==Battle.VisualPhase.FIRE&&
                            threat.name().equals(e.weapon()))launched++;
                    if(e.phase()==Battle.VisualPhase.IMPACT&&
                            e.targetId()==defender.id)impacts++;
                }
            }
        }
        int illuminated=0,heatLockouts=0,powerLockouts=0;
        for(ParticlePointDefense.Mount gun:defender.particlePdMounts){
            illuminated+=gun.illuminatedTicks;
            heatLockouts+=gun.heatLockouts;powerLockouts+=gun.powerLockouts;
        }
        int kills=0;
        for(Battle.DefensiveTarget kind:Battle.DefensiveTarget.values())
            kills+=b.defensiveKills(Battle.DefensiveGun.PARTICLE_PD,kind);
        require(launched>0,"Real hostile ordnance never launched");
        require(illuminated==fired,
                "Fitted particle beam visual firing deviated from powered damage ticks");
        require(on||fired==0&&illuminated==0&&kills==0,
                "HOLD_FIRE fired free particle radiation or destroyed a body");
        require(defender.kineticAmmo==0,
                "Energy-only particle beam spent nonexistent kinetic cartridges");
        require(kills<=launched,"Particle PD credited fictional body destructions");
        require(on||activeBeams==0,
                "HOLD_FIRE projected a phantom particle damage beam");
        double after=defender.shields+defender.armor+defender.hull;
        return new Trial(fired,launched,kills,impacts,illuminated,
                heatLockouts,powerLockouts,
                StrictMath.max(0,protectedInitially-after),activeBeams);
    }
    /** Optical contact never authorizes a free beam without ship power.
     * The attacker uses an actual bought missile launcher; this is not an
     * injected projectile or a mocked probability roll. */
    private static void verifyPowerStarvation(TechnologyCatalog technology){
        Battle battle=new Battle(774388L);
        battle.spawnWing(0,armedAttacker(technology,Threat.MISSILE,0),
                technology,1,320,300);
        battle.spawnWing(1,battery(technology,0,1),technology,1,550,300);
        Battle.Ship attacker=battle.ships().get(0);
        Battle.Ship defender=battle.ships().get(1);
        battle.registerDiagnosticStationaryTarget(defender.id);
        attacker.speed=attacker.vx=attacker.vy=0;
        attacker.maxHull=attacker.hull=100000;
        attacker.maxPower=attacker.power=10000;
        defender.power=0;
        defender.powerRegen=0;
        defender.kineticAmmo=0;
        defender.maxHull=defender.hull=100000;
        int missileFires=0,beamFires=0;
        long last=0;
        for(int t=0;t<200;t++){
            battle.step();
            for(Battle.VisualEvent event:battle.visualEvents()){
                if(event.sequence()<=last)continue;
                last=event.sequence();
                if(event.phase()!=Battle.VisualPhase.FIRE)continue;
                if(event.team()==0&&"MISSILE".equals(event.weapon()))
                    missileFires++;
                if(event.team()==1&&"PARTICLE_PD".equals(event.weapon()))
                    beamFires++;
            }
        }
        require(missileFires>0,"Unpowered PPD test had no real incoming missiles");
        require(beamFires==0&&
                defender.particlePdMounts.get(0).illuminatedTicks==0,
                "Powerless particle projector emitted free damage");
        require(defender.particlePdMounts.get(0).powerLockouts>0,
                "Power-starved PPD did not register physical energy lockout");
        require(battle.defensiveKills(Battle.DefensiveGun.PARTICLE_PD,
                Battle.DefensiveTarget.MISSILE)==0,
                "Unpowered PPD fabricated real ordnance destruction");
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        auditHardware(tech);
        thermalAndDwell();
        verifyPowerStarvation(tech);
        Path root=Path.of("test-output","particle-pd");
        Files.createDirectories(root);
        List<String> rows=new ArrayList<>();
        rows.add("threat,tier,mounts,seed,enabled,launched,beam_ticks,real_kills,"+
                "ship_impacts,paid_illumination_ticks,thermal_lockouts,power_lockouts,"+
                "net_protection_loss,beam_frames");
        int totalRealBeams=0,totalPaired=0;
        StringBuilder report=new StringBuilder("# Fitted particle beam physical dwell versus HOLD_FIRE\n\n");
        report.append("Each PPD is a distinct purchased powered gimbal. No "+
                "kinetic cartridges, no pulse-laser shots, no RNG hit deletion. "+
                "Kills require actual accumulated beam exposure of real bodies. "+
                "Damage and anti-saturation rates are advisory, never build gates.\n\n");
        report.append("|Threat|Rank|Mounts|Launched|PPD beam ticks|Real kills|Impacts|Hold impacts|\n");
        report.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for(Threat threat:Threat.values())
            for(int tier:new int[]{0,9})
                for(int count:new int[]{1,4}){
                    int launched=0,fired=0,kills=0,hit=0,holdHits=0;
                    for(int seed=0;seed<2;seed++){
                        Trial active=trial(tech,threat,tier,count,seed,true);
                        Trial hold=trial(tech,threat,tier,count,seed,false);
                        for(boolean on:new boolean[]{true,false}){
                            Trial a=on?active:hold;
                            rows.add(String.format(Locale.ROOT,
                                    "%s,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%d",
                                    threat,tier,count,seed,on,a.launched,a.fired,a.killed,
                                    a.impacts,a.paidTickCount,a.peakHeatLockouts,
                                    a.peakPowerLockouts,a.damage,a.activeBeams));
                        }
                        launched+=active.launched;fired+=active.fired;
                        kills+=active.killed;hit+=active.impacts;
                        holdHits+=hold.impacts;totalPaired++;
                    }
                    totalRealBeams+=fired;
                    report.append(String.format(Locale.ROOT,
                            "|%s|%d|%d|%d|%d|%d|%d|%d|\n",
                            threat,tier,count,launched,fired,kills,hit,holdHits));
                }
        // Equal-rank versus equal-rank is not interchangeable with IX PD
        // against a stock launcher. Exercise real research-matched sockets,
        // including a mobile flanking target instead of only static fixtures.
        List<String> equalTier=new ArrayList<>();
        equalTier.add("threat,attacker_rank,defender_rank,mounts,formation,seed,"+
                "enabled,launched,beam_ticks,physical_kills,ship_impacts,"+
                "thermal_lockouts,power_lockouts,net_protection_loss");
        report.append("\n## Real rank-matched attacker batteries and moving approaches\n\n");
        report.append("|Threat|Ranks attack/defend|PPD mounts|Geometry|Launched|Powered beam ticks|Kills|Impacts|HOLD impacts|\n");
        report.append("|---|---|---:|---|---:|---:|---:|---:|---:|\n");
        int equalPairs=0;
        for(Threat threat:Threat.values())
            for(int rank:new int[]{0,9})
                for(int mounts:new int[]{1,4})
                    for(boolean moving:new boolean[]{false,true}){
                        String formation=moving?"MOBILE_FLANK":"FIXED_APPROACH";
                        double lateral=moving?72:0;
                        int allLaunched=0,allBeams=0,allKills=0,allHits=0,allHoldHits=0;
                        for(int seed=0;seed<2;seed++){
                            Trial live=trial(tech,threat,rank,mounts,seed,true,
                                    rank,lateral,moving);
                            Trial hold=trial(tech,threat,rank,mounts,seed,false,
                                    rank,lateral,moving);
                            for(boolean enabled:new boolean[]{true,false}){
                                Trial a=enabled?live:hold;
                                equalTier.add(String.format(Locale.ROOT,
                                        "%s,%d,%d,%d,%s,%d,%s,%d,%d,%d,%d,%d,%d,%.2f",
                                        threat,rank,rank,mounts,formation,seed,
                                        enabled,a.launched,a.fired,a.killed,
                                        a.impacts,a.peakHeatLockouts,
                                        a.peakPowerLockouts,a.damage));
                            }
                            allLaunched+=live.launched;
                            allBeams+=live.fired;
                            allKills+=live.killed;
                            allHits+=live.impacts;
                            allHoldHits+=hold.impacts;
                            equalPairs++;
                        }
                        report.append(String.format(Locale.ROOT,
                                "|%s|%d/%d|%d|%s|%d|%d|%d|%d|%d|\n",
                                threat,rank,rank,mounts,formation,
                                allLaunched,allBeams,allKills,allHits,allHoldHits));
                    }
        Files.write(root.resolve("equal-tier-moving-geometry.csv"),equalTier,
                StandardCharsets.UTF_8);
        report.append("\nAdditional equal-tier physically fitted launcher/PPD pairs: "+
                equalPairs+". Each same-seed approach includes real engine "+
                "movement and offset geometry; numerical interception rates "+
                "remain diagnostic only.\n");
        require(totalRealBeams>0,
                "No installed particle gun ever produced a real continuous beam");
        report.append("\nVerified paired conditions: "+totalPaired+
                ". Mechanical invariants gate; interception ratios advisory.\n");
        Files.write(root.resolve("measurements.csv"),rows,StandardCharsets.UTF_8);
        Files.writeString(root.resolve("summary.md"),report,StandardCharsets.UTF_8);
        System.out.println(report);
    }
}
