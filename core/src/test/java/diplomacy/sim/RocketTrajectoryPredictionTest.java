package diplomacy.sim;

/** Rocket leading physics use the same engine steps as real 4/8/16 salvos. */
public final class RocketTrajectoryPredictionTest {
    private RocketTrajectoryPredictionTest(){}
    private static void require(boolean good,String why){
        if(!good)throw new AssertionError(why);
    }
    private static RocketBarrage.Rack rack(RocketBarrage.Size size,
            RocketBarrage.Motor mode,int tier){
        RocketBarrage.Rack r=new RocketBarrage.Rack(1,size,1,1,1,tier);
        r.configure(8,mode);
        return r;
    }
    private static void engines(){
        for(RocketBarrage.Size size:RocketBarrage.Size.values()){
            for(RocketBarrage.Motor mode:RocketBarrage.Motor.values()){
                for(int tier:new int[]{0,9}){
                    RocketBarrage.Rack r=rack(size,mode,tier);
                    double velocity=78,actualDistance=0;
                    // The real Battle.updateRockets code uses this EXACT
                    // motor integrator and carries velocity between frames.
                    for(int tick=0;tick<90;tick++){
                        velocity=RocketBarrage.nextSpeed(
                                r.size,r.motor,tick,velocity,r.body.speed(),
                                r.sustainedThrustFactor);
                        actualDistance+=velocity*Battle.DT;
                    }
                    double predicted=RocketTrajectoryPrediction.travelled(r,90*Battle.DT);
                    require(StrictMath.abs(predicted-actualDistance)<1e-5,
                            "Predicted rocket travel drifted from simulation step law "+size+"/"+mode+"/"+tier);
                    var eta=RocketTrajectoryPrediction.timeToDistance(r,actualDistance);
                    require(eta.reachable()&&
                                    StrictMath.abs(eta.seconds()-90*Battle.DT)<.00001,
                            "Inverting rocket flight distance broke exact step accounting");
                    require(RocketTrajectoryPrediction.travelled(r,0)==0,
                            "Rocket had travel before launching");
                    var impossible=RocketTrajectoryPrediction.timeToDistance(r,100000);
                    require(!impossible.reachable()&&
                                    impossible.seconds()==RocketBarrage.MAX_FLIGHT_SECONDS,
                            "Finite-life dumbfire body invented an unreachable intercept");
                }
            }
        }
        System.out.println("PASS 16 thrust/research cases share actual rocket velocity and finite lifetime");
    }
    private static void lead(){
        RocketBarrage.Rack boost=rack(RocketBarrage.Size.SMALL,
                RocketBarrage.Motor.BOOST_COAST,0);
        double range=500;
        var boostEta=RocketTrajectoryPrediction.timeToDistance(boost,range);
        require(boostEta.reachable()&&boostEta.seconds()>range/boost.body.speed(),
                "Boost/coast rocket was treated as instantly at terminal speed");
        var physical=RocketTrajectoryPrediction.lead(600,100,0,12,
                40,80,100,100,boost);
        require(physical.reachable()&&physical.y()>100+
                        12*(40*Battle.DT+range/boost.body.speed()),
                "Motor-specific longer flight failed to increase a moving target's lead");
        var source=new Battle.TacticalContact(47,4,Battle.CONTACT_RADAR,
                600,100,0,12,40,.7f,false,false);
        var pip=SensorLeadProjection.predictRocket(source,80,100,100,boost,1);
        require(pip.reachable()&&
                StrictMath.hypot(pip.predictedX()-physical.x(),
                        pip.predictedY()-physical.y())<1e-7,
                "Rendered rocket pip differs from authoritative motor flight prediction");
        var legacy=SensorLeadProjection.predict(source,80,100,100,boost.body.speed(),1);
        require(pip.flightSeconds()>legacy.flightSeconds()&&
                pip.uncertaintyRadius()>legacy.uncertaintyRadius(),
                "Slow motor startup did not increase lead/uncertainty vs nominal terminal speed");
        var far=RocketTrajectoryPrediction.lead(1e6,100,0,0,40,80,100,100,boost);
        var farContact=new Battle.TacticalContact(48,4,Battle.CONTACT_RADAR,
                1e6,100,0,0,40,.6f,false,false);
        var farPip=SensorLeadProjection.predictRocket(farContact,80,100,100,boost,1);
        require(!far.reachable()&&!farPip.reachable(),
                "HUD claimed finite-life rockets could reach an impossibly distant ship");
        RocketBarrage.Rack slow=rack(RocketBarrage.Size.MEDIUM,
                RocketBarrage.Motor.SUSTAINED_BURN,0);
        RocketBarrage.Rack improved=rack(RocketBarrage.Size.MEDIUM,
                RocketBarrage.Motor.SUSTAINED_BURN,9);
        require(RocketTrajectoryPrediction.timeToDistance(improved,750).seconds()<
                        RocketTrajectoryPrediction.timeToDistance(slow,750).seconds(),
                "Researched sustained-burn thrust did not shorten real predicted intercept");
        System.out.println("PASS causal motor lead pips, reachability, accuracy and researched thrust");
    }
    public static void main(String[] args){
        engines();lead();
    }
}
