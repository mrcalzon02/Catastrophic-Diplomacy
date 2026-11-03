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
    public static void main(String[] args){
        finiteOneTick();
        hardwareDifferentiation();
        historicalFalseComputeBudget();
    }
}
