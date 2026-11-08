package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Scratch audit only. Uses the unmodified 0159042 compiled production classes. */
public final class CombatAuditProbe {
    record World(Battle b, Battle.Ship observer, Battle.Ship target) {}
    static void disarm(Battle.Ship s) {
        s.laserDamage=s.plasmaDamage=s.particleDamage=0;
        s.kineticDamage=s.missileDamage=s.torpedoDamage=0;
        s.kineticAmmo=s.missileAmmo=s.torpedoAmmo=0;
        s.fighterReserve=s.droneReserve=s.repairDroneReserve=0;
        s.pdRange=0;
    }
    static World world() {
        Battle b=new Battle(240191);
        Battle.Ship observer=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,600,100);
        b.registerDiagnosticStationaryTarget(target.id);
        disarm(observer); disarm(target);
        observer.sensorRange=1000;
        observer.infraredRange=observer.xrayRange=observer.graviticRange=0;
        observer.maxPower=observer.power=10000;
        observer.maxHeat=10000;
        observer.speed=0;
        for(int t=0;t<2*SensorLightTimeHistory.propagationTicks(500)+4;t++)b.step();
        return new World(b,observer,target);
    }
    static Battle.TacticalContact observed(World w) {
        return w.b.tacticalPicture(0,w.observer.id).stream()
                .filter(c->c.targetId()==w.target.id).findFirst().orElseThrow();
    }
    static void nav() {
        World a=world(),b=world();
        if(!observed(a).equals(observed(b)))throw new AssertionError("Precondition: histories differ");
        // Change only world truth AFTER the two identical immutable histories.
        b.target.y+=150;
        var ca=observed(a);var cb=observed(b);
        if(!ca.equals(cb))throw new AssertionError("Observation already changed before photons arrived");
        if(!a.b.hasTargetContact(a.observer,a.target,0)||
                !b.b.hasTargetContact(b.observer,b.target,0))throw new AssertionError("No established causal contact");
        double range=a.observer.orders.forSize(BattleOrders.size(a.target)).range();
        double radial=StrictMath.max(-1.5,StrictMath.min(2.5,(500/range-.85)*3));
        double hand=((a.observer.id&1)==0?1.0:-1.0)*(a.observer.team==0?1.0:-1.0);
        a.observer.facing=b.observer.facing=StrictMath.atan2(hand,radial);
        a.observer.speed=b.observer.speed=80;
        a.b.step();b.b.step();
        var afterA=observed(a);var afterB=observed(b);
        boolean sameFix=afterA.x()==afterB.x()&&afterA.y()==afterB.y()&&
                afterA.vx()==afterB.vx()&&afterA.vy()==afterB.vy()&&
                afterA.observedTick()==afterB.observedTick();
        double delta=StrictMath.hypot(a.observer.vx-b.observer.vx,
                a.observer.vy-b.observer.vy);
        System.out.printf("NAV tick=%d sameDelayedFix=%s fixA=(%.3f,%.3f,t%d) fixB=(%.3f,%.3f,t%d) enemyTruthA=(%.3f,%.3f) enemyTruthB=(%.3f,%.3f)%n",
                a.b.tick,sameFix,afterA.x(),afterA.y(),afterA.observedTick(),
                afterB.x(),afterB.y(),afterB.observedTick(),a.target.x,a.target.y,b.target.x,b.target.y);
        System.out.printf("NAV observer velocity A=(%.9f,%.9f) B=(%.9f,%.9f) delta=%.9f headings=(%.9f,%.9f)%n",
                a.observer.vx,a.observer.vy,b.observer.vx,b.observer.vy,delta,a.observer.facing,b.observer.facing);
        if(!sameFix)throw new AssertionError("Fixture failed causal-identical observation constraint");
        if(delta<=1e-9)throw new AssertionError("Did not reproduce nav truth leak");
        System.out.println("CONFIRMED navigation reacted to changed present truth while the measured enemy fix stayed identical");
    }
    static void particle(boolean withdrawing)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design fit=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        for(Shipyard.Slot slot:fit.hull.slots)if(slot.mount()==Shipyard.Mount.WEAPON){
            fit=fit.install(slot.index(),"BASE-TER-PPD").stage(slot.index(),Shipyard.Track.SPECIALIST,9);break;
        }
        if(!Shipyard.inspect(fit,tech).valid())throw new AssertionError("Invalid real fitted PPD ship");
        Battle b=new Battle(73107);
        Battle.Ship attacker=b.addShip(0,Battle.Faction.TERRAN,325,300);
        b.spawnWing(1,fit,tech,1,550,300);
        Battle.Ship defender=b.ships().get(1);
        b.registerDiagnosticStationaryTarget(defender.id);
        disarm(attacker);
        attacker.missileDamage=28;attacker.missileAmmo=60;
        attacker.maxHull=attacker.hull=300000;
        attacker.maxPower=attacker.power=10000;attacker.maxHeat=10000;
        attacker.speed=attacker.vx=attacker.vy=0;
        defender.maxHull=defender.hull=300000;
        defender.maxPower=defender.power=10000;defender.maxHeat=10000;
        defender.kineticAmmo=0;
        defender.withdrawing=withdrawing;
        b.setShipOrders(defender.id,BattleOrders.DEFAULT.withPointDefense(BattleOrders.PointDefense.INTERCEPT_ONLY));
        int launched=0;long lastSequence=0;
        for(int t=0;t<420;t++){
            b.step();
            for(var e:b.visualEvents())if(e.sequence()>lastSequence){
                if(e.team()==0&&e.phase()==Battle.VisualPhase.FIRE&&e.weapon().equals("MISSILE"))launched++;
                lastSequence=e.sequence();
            }
        }
        int beamTicks=defender.particlePdMounts.stream().mapToInt(m->m.illuminatedTicks).sum();
        System.out.printf("PPD withdrawing=%s realIncomingLaunches=%d paidBeamTicks=%d alive=%s escaped=%s%n",
                withdrawing,launched,beamTicks,defender.alive(),defender.escaped);
        if(launched==0)throw new AssertionError("No real incoming missile threat");
        if(!withdrawing&&beamTicks==0)throw new AssertionError("Control PPD never engaged");
        if(withdrawing&&beamTicks!=0)throw new AssertionError("Withdrawal suppression was not reproduced");
    }
    static void commandCompute()throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        var fit=Shipyard.bareHull(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        for(var slot:fit.hull.slots)if(slot.mount()==Shipyard.Mount.SUPPORT){
            fit=fit.install(slot.index(),"BASE-TER-FCM");break;
        }
        if(!Shipyard.inspect(fit,tech).valid())throw new AssertionError("Invalid real FCM fit");
        Battle battle=new Battle(93782);
        battle.spawnWing(0,fit,tech,1,100,100);
        var command=battle.ships().get(0);
        var recipient=battle.addShip(0,Battle.Faction.TERRAN,180,100);
        var hostile=battle.addShip(1,Battle.Faction.DRAXIAN,680,100);
        battle.registerDiagnosticStationaryTarget(command.id);
        battle.registerDiagnosticStationaryTarget(hostile.id);
        disarm(hostile);disarm(command);
        recipient.speed=0;
        recipient.sensorRange=1000;
        recipient.infraredRange=recipient.xrayRange=recipient.graviticRange=0;
        recipient.fighterReserve=recipient.droneReserve=recipient.repairDroneReserve=0;
        for(var ship:battle.ships()){
            ship.maxPower=ship.power=10000;ship.maxHeat=10000;
            ship.maxHull=ship.hull=300000;
        }
        for(int t=0;t<600;t++)battle.step();
        double capacity=SensorComputeBudget.fleetCapacity(command);
        System.out.printf("FCM tick=%d donor=%d assignedDonor=%d capacity=%.6f separation=%.6f localAllocations=%d localExhausted=%d sharedAllocations=%d sharedExhausted=%d%n",
                battle.tick,command.id,recipient.commandSourceId,capacity,
                StrictMath.hypot(recipient.x-command.x,recipient.y-command.y),
                recipient.targetCompute.allocations,recipient.targetCompute.exhausted,
                command.fleetCompute.allocations,command.fleetCompute.exhausted);
        if(recipient.commandSourceId!=command.id||capacity<=0||recipient.targetCompute.exhausted==0)
            throw new AssertionError("FCM fixture did not create real assigned compute demand");
        if(command.fleetCompute.allocations!=0)throw new AssertionError("FCM sharing was operational");
        System.out.println("CONFIRMED noncolocated FCM donor is never debited despite assigned ally with real exhausted firing compute");
    }
    public static void main(String[] args)throws Exception {
        nav();particle(false);particle(true);commandCompute();
        System.out.println("CONFIRMED withdrawing PPD suppresses all beam ticks despite an otherwise armed, powered, targetable defensive fixture");
    }
}
