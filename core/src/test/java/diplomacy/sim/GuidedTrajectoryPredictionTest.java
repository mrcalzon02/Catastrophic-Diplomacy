package diplomacy.sim;

/** No renderer or target-truth dependency for physical guided trajectory pips. */
public final class GuidedTrajectoryPredictionTest {
    private static void require(boolean good,String message){
        if(!good)throw new AssertionError(message);
    }
    private static Battle.Missile fixture(){
        var m=new Battle.Missile(91,10,1,0,2,100,100,600,0,
                30,false,ProjectileModel.standard(ProjectileModel.Kind.MISSILE),1);
        m.setPropulsion(OrdnancePropulsion.Mode.CONSTANT_BURN);
        // Off-axis, sub-cap initial velocity requires genuinely powered
        // acceleration and steering. A perfectly centered rated-speed body
        // would correctly coast down the same path when out of propellant.
        m.vx=140;m.vy=65;
        m.lastKnownX=640;m.lastKnownY=100;
        m.lastKnownVX=0;m.lastKnownVY=0;
        m.lastObservationTick=8;
        m.seekerUncertainty=7;
        m.seekerLocked=true;
        return m;
    }
    private static void deterministicBounded(){
        var m=fixture();
        var one=GuidedTrajectoryPrediction.predict(m,10);
        var two=GuidedTrajectoryPrediction.predict(m,10);
        require(one.equals(two),"Guided estimate not deterministic");
        require(one.path().size()<=GuidedTrajectoryPrediction.PATH_SEGMENTS+2,
                "Forecast path is not bounded at fleet scale");
        require(one.path().get(0).x()==m.x&&one.path().get(0).y()==m.y,
                "Projected body did not originate at actual missile position");
        require(one.etaSeconds()>0&&one.etaSeconds()<=m.fuel,
                "ETA exceeded physically remaining fuel or failed to advance");
        require(one.closestDistance()>=0&&one.uncertaintyRadius()>m.seekerUncertainty,
                "Physical estimated closest approach or uncertainty invalid");
        double x=m.x,y=m.y,vx=m.vx,vy=m.vy,fuel=m.fuel,dv=m.propellant;
        GuidedTrajectoryPrediction.predict(m,10);
        require(m.x==x&&m.y==y&&m.vx==vx&&m.vy==vy&&
                m.fuel==fuel&&m.propellant==dv,
                "Read-only targeting pip mutated an actual projectile");
        try{one.path().clear();throw new AssertionError("Mutable path leaked");}
        catch(UnsupportedOperationException expected){/* immutable */ }
        System.out.println("PASS finite trajectory path, ETA, determinism and read-only projectile state");
    }
    private static void propulsionAndLockMatters(){
        var m=fixture();
        var powered=GuidedTrajectoryPrediction.predict(m,10);
        m.propellant=0;
        var unpowered=GuidedTrajectoryPrediction.predict(m,10);
        require(!powered.path().equals(unpowered.path()),
                "Forecast granted unpowered missiles free steering/thrust");
        require(unpowered.path().size()>1,"Unpowered body must still coast");
        var start=unpowered.path().get(0);
        var end=unpowered.path().get(unpowered.path().size()-1);
        require(StrictMath.abs(end.x()-
                (start.x()+m.vx*end.seconds()))<1e-6&&
                StrictMath.abs(end.y()-
                (start.y()+m.vy*end.seconds()))<1e-6,
                "Inertial coast path diverged from actual fixed-step velocity");
        m=fixture();
        m.ignitionDelayTicks=18;
        m.setPropulsion(OrdnancePropulsion.Mode.COLD_START);
        var cold=GuidedTrajectoryPrediction.predict(m,10);
        require(cold.path().size()>1&&
                !cold.path().equals(powered.path()),
                "Cold-launch ejection did not change the predicted flight");
        m=fixture();m.lastKnownY=350;
        var diverted=GuidedTrajectoryPrediction.predict(m,10);
        require(!diverted.path().equals(powered.path()),
                "Received false contact did not affect predicted guidance");
        m.seekerLocked=false;
        var blind=GuidedTrajectoryPrediction.predict(m,10);
        require(!blind.path().equals(diverted.path()),
                "Lost seeker lock still received free steering in forecast");
        System.out.println("PASS thrust/coast/cold launch, last-fix deception and lock loss");
    }
    private static void realEnemyCannotLeakIntoForecast(){
        Battle scene=new Battle(777821);
        Battle.Ship launcher=scene.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship enemy=scene.addShip(1,Battle.Faction.DRAXIAN,700,100);
        for(Battle.Ship s:scene.ships()){
            s.speed=s.vx=s.vy=0;
            s.laserDamage=s.plasmaDamage=s.kineticDamage=s.particleDamage=0;
            s.missileAmmo=s.torpedoAmmo=0;s.missileDamage=s.torpedoDamage=0;
            s.pdRange=0;s.fighterReserve=s.droneReserve=0;
            s.maxPower=s.power=20000;s.maxHeat=20000;
        }
        launcher.missileAmmo=1;launcher.missileDamage=35;
        Battle.Missile live=null;
        for(int i=0;i<100&&live==null;i++){
            scene.step();
            if(!scene.missiles().isEmpty())live=scene.missiles().get(0);
        }
        require(live!=null,"No physically launched guided body for no-oracle fixture");
        var original=GuidedTrajectoryPrediction.predict(live,scene.tick);
        enemy.y+=350;enemy.vx=25;enemy.ecmStrength=1;
        var unchanged=GuidedTrajectoryPrediction.predict(live,scene.tick);
        require(original.equals(unchanged),
                "Forecast directly read a target's hidden current pose or ECM");
        System.out.println("PASS guided prediction never queries current hostile truth");
    }
    public static void main(String[] args){
        deterministicBounded();propulsionAndLockMatters();
        realEnemyCannotLeakIntoForecast();
    }
}
