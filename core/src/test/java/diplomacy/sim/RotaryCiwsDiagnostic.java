package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tests REAL installed rotary CIWS modules, paid bullets and trajectory hits,
 * against real hostile rockets, kinetic rounds and guided weapons. Numeric
 * role/balance outcomes are advisory; mechanical mounting/stock is functional.
 */
public final class RotaryCiwsDiagnostic {
    private RotaryCiwsDiagnostic(){}
    private enum Threat { MISSILE, TORPEDO, KINETIC, ROCKET_BOOST, ROCKET_SUSTAIN,
        FIGHTER_INTERCEPTOR, DRONE }
    private record Trial(Threat threat,int tier,int mounts,int seed,boolean on,
                         int attackLaunched,int rotaryShots,int bulletsSeen,
                         int kineticSpent,int intercepts,int impacts,
                         double protectionLoss,double powerSpent,long firstShot) {
        String csv() {
            return String.format(Locale.ROOT,
                    "%s,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%d",
                    threat,tier,mounts,seed,on,attackLaunched,rotaryShots,
                    bulletsSeen,kineticSpent,intercepts,impacts,
                    protectionLoss,powerSpent,firstShot);
        }
    }
    private static void require(boolean yes,String why){
        if(!yes)throw new IllegalStateException(why);
    }
    private static Shipyard.Design fitting(TechnologyCatalog catalog,int tier,int mounts){
        Shipyard.Design ship=Shipyard.bareHull(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int i=0;
        for(Shipyard.Slot slot:ship.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON||i>=mounts)continue;
            ship=ship.install(slot.index(),"BASE-TER-RCW");
            if(tier>0)ship=ship.stage(slot.index(),Shipyard.Track.SPECIALIST,tier);
            i++;
        }
        require(i==mounts&&Shipyard.inspect(ship,catalog).valid(),
                "Illegal rotary CIWS fitting at tier "+tier);
        return ship;
    }
    private static Shipyard.Design rocketBattery(TechnologyCatalog catalog){
        Shipyard.Design ship=Shipyard.bareHull(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        for(Shipyard.Slot slot:ship.hull.slots){
            if(slot.mount()==Shipyard.Mount.WEAPON)
                ship=ship.install(slot.index(),"BASE-TER-RKM");
        }
        require(Shipyard.inspect(ship,catalog).valid(),"Illegal rocket source fixture");
        return ship;
    }
    /** Real purchased carrier bay: RCW bullets must hit an aircraft's own
     * armor/integrity, never a fabricated missile-with-a-fighter-label. */
    private static Shipyard.Design fighterSource(TechnologyCatalog catalog){
        Shipyard.Design fit=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CRUISER);
        for(Shipyard.Slot slot:fit.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                fit=fit.install(slot.index(),"BASE-TER-FTR")
                        .withFighterHull(FighterModel.Hull.INTERCEPTOR);
                require(Shipyard.inspect(fit,catalog).valid(),
                        "Actual interceptor carrier fitting invalid");
                return fit;
            }
        throw new IllegalStateException("Missing physical FTR launch socket");
    }

    private static void verifyFittedMechanics(TechnologyCatalog catalog){
        for(String faction:new String[]{"TER","DRA","CAR","ZIP","UZI","MAN",
                "VAE","ZEL","AUR","NYX","UMB"}){
            require(catalog.base("BASE-"+faction+"-RCW")!=null,
                    "Missing native rotary CIWS chassis "+faction);
            for(int tier:new int[]{1,3,6,9})
                require(catalog.get(String.format(Locale.ROOT,
                        "TECH-%s-RCW-SPECIALIST-%02d",faction,tier))!=null,
                        "Missing researched rotary sensor/servo tier "+faction+"/"+tier);
        }
        double prev=0;
        for(int rank:new int[]{0,3,6,9}){
            Shipyard.Design fit=fitting(catalog,rank,2);
            Battle battle=new Battle(71005L+rank);
            battle.spawnWing(1,fit,catalog,1,320,300);
            Battle.Ship s=battle.ships().get(0);
            require(!s.laserPointDefenseEnabled&&s.precisionMounts.isEmpty()&&
                    s.flakMounts.isEmpty(),"Rotary inherited a fictitious laser or flak");
            require(s.rotaryMounts.size()==2,
                    "Real rotary weapon sockets failed to reach Battle");
            require(s.kineticAmmo>0,"No finite physical kinetic stock on rotary vessel");
            double range=s.rotaryMounts.get(0).nominalRange;
            require(range>=35&&range<=155&&range>=prev,
                    "RCW individual researched sensor envelope invalid");
            prev=range;
            for(RotaryPointDefense.Mount mount:s.rotaryMounts){
                require(mount.researchTier==rank,
                        "Fitted rotary research became shipwide rather than per module");
                require(mount.profile.tracking()>0&&mount.cycle>0,
                        "Uninitialized mechanical rotary fire control");
                require(ShipDamageTargeting.subsystem(
                        "BASE-TER-RCW")==Battle.Subsystem.POINT_DEFENSE,
                        "RCW hardpoint damage incorrectly targets main weapons");
                require(WeaponMuzzles.atSlot(s,
                        WeaponMuzzles.Family.ROTARY_CIWS,mount.slotId).slotId()
                        ==mount.slotId,"Rotary muzzle borrowed other gun's socket");
            }
        }
        var a=fitting(catalog,0,1);
        var b=fitting(catalog,9,1);
        var first=Shipyard.defenseAt(a,catalog,a.hull.slots.stream().filter(
                slot->slot.mount()==Shipyard.Mount.WEAPON).findFirst().orElseThrow().index());
        var upgraded=Shipyard.defenseAt(b,catalog,b.hull.slots.stream().filter(
                slot->slot.mount()==Shipyard.Mount.WEAPON).findFirst().orElseThrow().index());
        require(upgraded.tracking()>first.tracking(),
                "Nine ranks did not improve physical rotary tracking");
        var ambient=ProjectileModel.standard(ProjectileModel.Kind.MISSILE);
        Battle bb=new Battle(771L);
        bb.spawnWing(1,a,catalog,1,320,300);
        var gun=bb.ships().get(0).rotaryMounts.get(0);
        double clear=RotaryPointDefense.acquisitionRange(bb.ships().get(0),
                gun,ambient,1.0,1);
        bb.ships().get(0).sensorsCondition=.25;
        double damaged=RotaryPointDefense.acquisitionRange(bb.ships().get(0),
                gun,ambient,1.0,1);
        require(clear>damaged&&damaged>0,
                "RCW sensors fail to respond to actual array damage");
        bb.ships().get(0).power=0;
        require(RotaryPointDefense.acquisitionRange(bb.ships().get(0),
                gun,ambient,1.0,1)==0,
                "Unpowered RCW still sees with an active scanner");
        require(RotaryPointDefense.directDamage(
                ProjectileModel.standard(ProjectileModel.Kind.MISSILE),first)>
                RotaryPointDefense.directDamage(
                ProjectileModel.standard(ProjectileModel.Kind.TORPEDO),first),
                "Rotary cannon lacks physical hardened warhead differentiation");
    }
    private static Trial run(TechnologyCatalog catalog,Threat threat,int tier,
                             int mounts,int seed,boolean on){
        Battle battle=new Battle(2098101L+seed*79L+tier*17L+mounts*31L+threat.ordinal()*107);
        Battle.Ship attacker;
        if(threat==Threat.ROCKET_BOOST||threat==Threat.ROCKET_SUSTAIN){
            battle.spawnWing(0,rocketBattery(catalog),catalog,1,180,300);
            attacker=battle.ships().get(0);
        }else if(threat==Threat.FIGHTER_INTERCEPTOR){
            // A fighter at its normal standoff distance can remain OUTSIDE
            // the RCW close-in envelope. For an anti-craft firing mechanic
            // check, launch within fitted sensor reach, not beyond it.
            battle.spawnWing(0,fighterSource(catalog),catalog,1,300,300);
            attacker=battle.ships().get(0);
        }else attacker=battle.addShip(0,Battle.Faction.TERRAN,
                threat==Threat.DRONE?288:180,300);
        battle.spawnWing(1,fitting(catalog,tier,mounts),catalog,1,355,300);
        Battle.Ship defender=battle.ships().get(1);
        battle.registerDiagnosticStationaryTarget(defender.id);
        defender.speed=defender.vx=defender.vy=0;
        attacker.speed=attacker.vx=attacker.vy=0;
        attacker.maxPower=attacker.power=10000;
        attacker.maxHeat=10000;
        attacker.maxHull=attacker.hull=100000;
        attacker.laserDamage=attacker.plasmaDamage=attacker.particleDamage=0;
        attacker.missileDamage=attacker.kineticDamage=attacker.torpedoDamage=0;
        attacker.fighterReserve=attacker.droneReserve=attacker.repairDroneReserve=0;
        attacker.pdRange=0;
        attacker.kineticAmmo=attacker.missileAmmo=attacker.torpedoAmmo=0;
        if(threat==Threat.ROCKET_BOOST||threat==Threat.ROCKET_SUSTAIN){
            attacker.kineticAmmo=attacker.maxKineticAmmo=1000;
            for(RocketBarrage.Rack rack:attacker.rocketRacks)
                battle.setShipRocketBarrage(attacker.id,rack.slot,8,
                        threat==Threat.ROCKET_BOOST?
                                RocketBarrage.Motor.BOOST_COAST:
                                RocketBarrage.Motor.SUSTAINED_BURN);
        }else if(threat==Threat.MISSILE){
            attacker.missileDamage=35;attacker.missileAmmo=40;
        }else if(threat==Threat.TORPEDO){
            attacker.torpedoDamage=65;attacker.torpedoAmmo=30;
        }else if(threat==Threat.FIGHTER_INTERCEPTOR){
            attacker.fighterReserve=1;
            require(attacker.fighterLaunchBays>0,
                    "Actual aircraft source has no fitted launch hangar");
        }else if(threat==Threat.DRONE){
            attacker.droneReserve=1;
        }else{
            attacker.kineticDamage=27;attacker.kineticAmmo=30;
        }
        battle.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(
                on?(threat==Threat.FIGHTER_INTERCEPTOR||threat==Threat.DRONE?
                        BattleOrders.PointDefense.ANTI_CRAFT:
                        BattleOrders.PointDefense.INTERCEPT_ONLY):
                        BattleOrders.PointDefense.HOLD_FIRE));
        double armor=defender.armor,shield=defender.shields,hull=defender.hull;
        double startPower=defender.power;
        int ammo=defender.kineticAmmo,seen=0;
        long first=-1;
        // Physical rounds only have ~0.1s flight time. Sampling EACH 30Hz
        // frame matters: never reconstruct a synthetic path from hit logs.
        for(int frame=0;frame<420;frame++){
            battle.step();
            seen+=battle.rotaryRounds().size();
            if(first<0)for(Battle.VisualEvent event:battle.visualEvents())
                if(event.team()==1&&"ROTARY_CIWS".equals(event.weapon())&&
                        event.phase()==Battle.VisualPhase.FIRE){
                    first=event.tick();
                    break;
                }
            if(frame==30 && threat!=Threat.ROCKET_BOOST&&
                    threat!=Threat.ROCKET_SUSTAIN)
                attacker.missileReload=attacker.torpedoReload=
                        attacker.kineticReload=10000;
        }
        int fired=0,launched=0,impacts=0,rotaryImpacts=0;
        for(Battle.VisualEvent e:battle.visualEvents()){
            if(e.phase()==Battle.VisualPhase.IMPACT&&e.team()==1&&
                    "ROTARY_CIWS".equals(e.weapon()))rotaryImpacts++;
            if(e.phase()==Battle.VisualPhase.FIRE&&
                    e.team()==1&&"ROTARY_CIWS".equals(e.weapon())){
                fired++;
                boolean paidSocket=false;
                for(RotaryPointDefense.Mount gun:defender.rotaryMounts){
                    var origin=WeaponMuzzles.atSlot(defender,
                            WeaponMuzzles.Family.ROTARY_CIWS,gun.slotId);
                    if(StrictMath.hypot(e.x()-origin.x(),e.y()-origin.y())<.001){
                        paidSocket=true;break;
                    }
                }
                require(paidSocket,"Rotary bullet spawned outside fitted muzzle");
            }
            if(e.team()==0&&e.sourceId()==attacker.id){
                if(e.phase()==Battle.VisualPhase.FIRE&&(
                        "MISSILE".equals(e.weapon())||"TORPEDO".equals(e.weapon())||
                        "KINETIC".equals(e.weapon())||"ROCKET".equals(e.weapon())||
                        "FIGHTER".equals(e.weapon())||"DRONE".equals(e.weapon())))
                    launched++;
                if(e.phase()==Battle.VisualPhase.IMPACT&&e.targetId()==defender.id)
                    impacts++;
            }
        }
        int total=0;
        for(RotaryPointDefense.Mount gun:defender.rotaryMounts)
            total+=gun.roundsFired;
        require(total==fired,"Fitted RCW muzzle shots and physical rounds disagree");
        // Incoming hits can also destroy the defender's loaded magazine.
        // Do not count that loss as additional RCW shots, but verify every
        // emitted tracer has at least one paid kinetic cartridge behind it.
        require(ammo-defender.kineticAmmo>=fired,
                "Rotary shots exceeded paid kinetic stock: "+threat+
                " rank="+tier+" mounts="+mounts+" fired="+fired+
                " storeDepletion="+(ammo-defender.kineticAmmo));
        require(on||fired==0,"HOLD_FIRE launched unapproved rotary rounds");
        require(!on||fired>0,"Powered rotary CIWS failed to fire at "+threat);
        require(!on||first>0,"Rotary weapon skipped physical spin-up time");
        require(launched>0,"Attacker produced no physical "+threat);
        // A near-range round may cross a tiny kinetic shell and vanish
        // within ONE 30Hz tick; end-of-tick visual sampling then correctly
        // sees zero live bullets. Demand either real in-flight frames OR an
        // actual recorded kinetic collision (no fictitious travel tweens).
        require(!on||seen>0||rotaryImpacts>0,
                "No live flight OR physical tracer impact: "+threat+
                " rank="+tier+" mounts="+mounts+" seed="+seed+
                " fired="+fired+" first="+first+" collisions="+rotaryImpacts);
        double loss=StrictMath.max(0,armor-defender.armor)+
                StrictMath.max(0,shield-defender.shields)+
                StrictMath.max(0,hull-defender.hull);
        return new Trial(threat,tier,mounts,seed,on,launched,fired,seen,
                ammo-defender.kineticAmmo,
                defender.intercepted+battle.pointDefenseCraftKills,impacts,
                loss,startPower-defender.power,first);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        verifyFittedMechanics(tech);
        Path root=Path.of("test-output","rotary-ciws");
        Files.createDirectories(root);
        List<String> rows=new ArrayList<>();
        rows.add("threat,defense_stage,rcw_mounts,seed,pd_enabled,launched,"+
                "physical_rcw_shots,live_round_frame_samples,total_kinetic_stock_depletion,"+
                "intercepts,impacts,defender_damage,power_spent,first_fire_tick");
        StringBuilder summary=new StringBuilder(
                "# Physical rotary CIWS firing and detection comparison\n\n");
        summary.append("All guns physically installed; no legacy RCW or synthetic"+
                " per-target kill roll. One kinetic ammo unit per actual fired"+
                " moving projectile. Normal mission power/heat.\n\n");
        summary.append("|Threat|PD tier|RCW mounts|Launched|Shots fired|"+
                "Interceptions|Impacts|PD-off impacts|Ammo used|\n");
        summary.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(Threat threat:Threat.values())for(int tier:new int[]{0,9})
            for(int mounts:new int[]{1,4}){
                int launches=0,shots=0,kills=0,impacts=0,control=0,ammo=0;
                for(int seed=0;seed<3;seed++){
                    Trial live=run(tech,threat,tier,mounts,seed,true);
                    Trial hold=run(tech,threat,tier,mounts,seed,false);
                    rows.add(live.csv());rows.add(hold.csv());
                    launches+=live.attackLaunched;shots+=live.rotaryShots;
                    kills+=live.intercepts;impacts+=live.impacts;
                    control+=hold.impacts;ammo+=live.kineticSpent;
                }
                summary.append(String.format(Locale.ROOT,
                        "|%s|%d|%d|%d|%d|%d|%d|%d|%d|\n",
                        threat,tier,mounts,launches,shots,kills,impacts,control,ammo));
            }
        summary.append("\nBehavior measurements are advisory; actual mounting,"+
                " ammunition spending and travelled-projectile assertions are"+
                " functional. RCW is NOT a laser or a flak fragmentation device.\n");
        Files.write(root.resolve("rotary-trials.csv"),rows,StandardCharsets.UTF_8);
        Files.writeString(root.resolve("summary.md"),summary);
        System.out.print(summary);
        System.out.println("ROTARY_CIWS_PAIRED_TRIALS="+(rows.size()-1)/2);
    }
}
