package diplomacy.sim;

import diplomacy.shipyard.Shipyard;

/** Finite target-solving work, no hidden free CAP processor entitlement. */
public final class SensorComputeBudgetTest {
    private static void require(boolean b,String message){
        if(!b)throw new AssertionError(message);
    }
    private static SensorLightTimeHistory.Observation report(){
        var sample=new SensorLightTimeHistory.Sample(3,600,100,0,0,true);
        return new SensorLightTimeHistory.Observation(
                1,3,7,Battle.CONTACT_RADAR,sample);
    }
    private static void finiteOneTick(){
        Battle battle=new Battle(93777);
        Battle.Ship s=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        s.power=100;
        SensorComputeBudget.State budget=s.targetCompute;
        var observed=report();
        long initialBattleHash=battle.fingerprint();
        var first=SensorComputeBudget.solve(budget,s,observed,9,600,1,0);
        require(first.spent()>0&&first.remaining()>=0&&
                first.requestedCost()>first.spent()&&first.limited(),
                "Bare ship gained unlimited precision without paid compute");
        require(battle.fingerprint()!=initialBattleHash,
                "Target compute allocations are absent from replay identity");
        long hash=budget.fingerprint();
        require(budget.fingerprint()==hash&&budget.remaining()==first.remaining(),
                "Reading compute telemetry mutated authoritative state");
        var second=SensorComputeBudget.solve(budget,s,observed,9,600,1,0);
        require(second.precision()<=first.precision()&&
                second.spent()<=first.spent()&&second.remaining()==0,
                "Multiple target solutions received free duplicate compute budget");
        var refreshed=SensorComputeBudget.solve(budget,s,observed,10,600,1,0);
        require(refreshed.spent()>0&&refreshed.precision()>0,
                "Finite physical target solver failed to regenerate next tick");
        s.power=0;
        var unpowered=SensorComputeBudget.solve(budget,s,observed,11,600,1,0);
        require(unpowered.spent()==0&&unpowered.precision()==0,
                "Unpowered computing electronics still generated processing capacity");
        System.out.println("PASS finite per-tick work, replay, contention, replenishment and power loss");
    }
    private static void hardwareDifferentiation(){
        Battle battle=new Battle(93779);
        Battle.Ship ship=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        ship.power=100;
        Shipyard.Network baseline=ship.network;
        double base=SensorComputeBudget.capacity(ship);
        ship.network=new Shipyard.Network(
                baseline.sharingRange(),baseline.sharingCapacity(),
                1.45,baseline.trackMemoryTicks(),
                .65,baseline.trackMemoryCapacity(),
                baseline.trackRelayRange(),baseline.trackRelayCapacity(),
                baseline.fusionRange(),baseline.fusionStrength(),
                baseline.commandCapacity(),1);
        double enhanced=SensorComputeBudget.capacity(ship);
        require(enhanced>base,
                "Real fitted RES/TRK hardware did not raise solution compute budget");
        ship.sensorAcuity=1.5;
        require(SensorComputeBudget.capacity(ship)>enhanced,
                "Real ACU acquisition upgrade did not affect compute throughput");
        double cpu=SensorComputeBudget.capacity(ship);
        Shipyard.Network n=ship.network;
        ship.network=new Shipyard.Network(
                n.sharingRange(),n.sharingCapacity(),
                n.resolutionGain(),n.trackMemoryTicks(),
                n.trackingPrecision(),n.trackMemoryCapacity(),
                n.trackRelayRange(),n.trackRelayCapacity(),
                n.fusionRange(),n.fusionStrength(),
                n.commandCapacity(),16);
        require(SensorComputeBudget.capacity(ship)==cpu,
                "CAP engagement slots illegally supplied processor cycles");
        require(SensorComputeBudget.cost(1500,2,4)>
                SensorComputeBudget.cost(150,0,0),
                "Old distant multi-hypothesis contact is not computationally expensive");
        System.out.println("PASS RES/TRK/ACU compute throughput, CAP-only parallel slots and nonlinear age/range load");
    }
    private static void historicalFalseComputeBudget(){
        Battle battle=new Battle(93781);
        Battle.Ship gun=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship ghost=battle.addShip(1,Battle.Faction.DRAXIAN,600,100);
        battle.registerDiagnosticStationaryTarget(gun.id);
        battle.registerDiagnosticStationaryTarget(ghost.id);
        gun.sensorRange=800;gun.power=100;
        ghost.ecmCondition=1;ghost.hologramNoise=.75;
        ghost.maxPower=ghost.power=500;
        for(int i=0;i<8;i++)battle.step();
        var photons=SensorLightTimeHistory.receive(gun,ghost,battle.tick,
                Battle.CONTACT_OPTICAL|Battle.CONTACT_RADAR,0);
        require(photons!=null,"No historical observations for false-work test");
        int extra=battle.targetingHypothesisLoad(gun,ghost,photons);
        require(extra==3,"Three physically emitted phantoms must consume three hypothesis slots");
        var quiet=SensorComputeBudget.solve(new SensorComputeBudget.State(),
                gun,photons,battle.tick,500,1,0);
        var crowded=SensorComputeBudget.solve(new SensorComputeBudget.State(),
                gun,photons,battle.tick,500,1,extra);
        require(crowded.requestedCost()>quiet.requestedCost()&&
                        crowded.precision()<quiet.precision(),
                "Arrived false contacts did not cost real finite compute");
        require(SensorComputeBudget.competingHypotheses(35,3)==32,
                "Competing hypotheses exceeded fixed compute bound");
        ghost.power=0;ghost.hologramNoise=0;
        battle.step();
        var delayed=SensorLightTimeHistory.receive(gun,ghost,battle.tick,
                Battle.CONTACT_OPTICAL|Battle.CONTACT_RADAR,0);
        require(delayed!=null&&
                battle.targetingHypothesisLoad(gun,ghost,delayed)==extra,
                "Present emitter shutdown retroactively refunded historic compute");
        boolean receivedShutdown=false;
        for(int i=0;i<18;i++){
            battle.step();
            var report=SensorLightTimeHistory.receive(gun,ghost,battle.tick,
                    Battle.CONTACT_OPTICAL|Battle.CONTACT_RADAR,0);
            if(report!=null&&report.measuredTick()>8){
                require(battle.targetingHypothesisLoad(gun,ghost,report)==0,
                        "Newly arrived inactive emitter retained imaginary hypotheses");
                receivedShutdown=true;break;
            }
        }
        require(receivedShutdown,"Shutdown photons never entered fire-control workload");
        System.out.println("PASS historical ghost hypotheses spend finite processing and shutdown refunds only after light delay");
    }
    private static void finiteFCMCommandPool(){
        var catalog=diplomacy.tech.TechnologyCatalog.loadDefault();
        var bare=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int support=-1;
        for(Shipyard.Slot slot:bare.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){support=slot.index();break;}
        require(support>=0,"No real FCM support mounting socket");
        var fitted=bare.install(support,"BASE-TER-FCM");
        require(Shipyard.inspect(fitted,catalog).valid(),"Actual FCM fitting invalid");
        var fcm=Shipyard.fit(fitted,catalog).network();

        Battle battle=new Battle(93782);
        Battle.Ship flagship=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship first=battle.addShip(0,Battle.Faction.TERRAN,180,100);
        Battle.Ship second=battle.addShip(0,Battle.Faction.TERRAN,200,110);
        Battle.Ship hostile=battle.addShip(1,Battle.Faction.DRAXIAN,680,100);
        for(Battle.Ship ship:battle.ships()){
            battle.registerDiagnosticStationaryTarget(ship.id);
            ship.power=ship.maxPower=500;
        }
        flagship.network=fcm;
        battle.step();
        require(first.commandSourceId==flagship.id&&
                second.commandSourceId==flagship.id,
                "Fitted finite command cell did not assign nearby allies");
        double capacity=SensorComputeBudget.fleetCapacity(flagship);
        require(capacity>0,"Powered FCM has no finite processor bank");
        var sample=new SensorLightTimeHistory.Sample(0,680,100,0,0,true);
        var report=new SensorLightTimeHistory.Observation(
                0,0,1,Battle.CONTACT_RADAR,sample);
        long initialPool=flagship.fleetCompute.fingerprint();
        var premature=SensorComputeBudget.solveWithCommand(
                new SensorComputeBudget.State(),first,report,battle.tick,
                1100,1,0,flagship);
        require(flagship.fleetCompute.fingerprint()==initialPool,
                "FCM processed target data before reciprocal photon hops");
        require(premature.limited(),"Early solve obtained free perfect guidance");
        for(int i=0;i<4;i++)battle.step();
        var local=SensorComputeBudget.solve(
                new SensorComputeBudget.State(),first,report,battle.tick,1100,1,0);
        var improved=SensorComputeBudget.solveWithCommand(
                new SensorComputeBudget.State(),first,report,battle.tick,1100,1,0,
                flagship);
        require(improved.precision()>local.precision()&&
                improved.spent()>local.spent(),
                "Valid historical FCM datalink failed to lend processor credits");
        var follow=SensorComputeBudget.solveWithCommand(
                new SensorComputeBudget.State(),second,report,battle.tick,1100,1,0,
                flagship);
        double granted=improved.spent()-local.spent()+
                follow.spent()-local.spent();
        require(granted<=capacity+1e-8,
                "FCM duplicated the same shared processor pool per recipient");
        require(flagship.fleetCompute.allocations==2,
                "Allies did not debit the same authoritative command computer");
        require(flagship.fleetCompute.fingerprint()!=initialPool,
                "Shared physical compute missing from deterministic replay");
        flagship.power=0;
        battle.step();
        require(first.commandSourceId==0&&second.commandSourceId==0,
                "Unpowered command core continued directing allied CPU");
        var noFCM=SensorComputeBudget.solveWithCommand(
                new SensorComputeBudget.State(),first,report,battle.tick,1100,1,0,
                flagship);
        var bareLocal=SensorComputeBudget.solve(
                new SensorComputeBudget.State(),first,report,battle.tick,1100,1,0);
        require(noFCM.precision()==bareLocal.precision(),
                "Dead command power granted an unearned processor credit");
        System.out.println("PASS finite paid FCM work, two-way signal delay, allied competition, power loss and replay");
    }
    public static void main(String[] args){
        finiteOneTick();
        hardwareDifferentiation();
        historicalFalseComputeBudget();
        finiteFCMCommandPool();
    }
}
