package diplomacy.sim;

/**
 * Detects instantaneous target truth, non-causal lead, misleading certainty
 * and "wrong estimate always misses" regressions without any art dependency.
 */
public final class SensorTargetingSolutionTest {
    private SensorTargetingSolutionTest(){}
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static SensorLightTimeHistory.Observation report(long measured,
            long received,int channels,double x,double y,double vx,double vy){
        var sample=new SensorLightTimeHistory.Sample(measured,x,y,vx,vy,true);
        return new SensorLightTimeHistory.Observation(
                measured-2,measured,received,channels,sample);
    }
    private static void deterministic(){
        var evidence=report(50,60,Battle.CONTACT_RADAR,1500,0,12,0);
        var shot=SensorTargetingSolution.solve(evidence,70,0,0,500,1.4,711);
        var replay=SensorTargetingSolution.solve(evidence,70,0,0,500,1.4,711);
        require(shot.equals(replay),"Targeting estimation is not deterministic");
        require(shot.measuredTick()==50&&shot.ageSeconds()>0,
                "Estimated track silently became fresh target truth");
        require(shot.predictedX()>1500+12*shot.ageSeconds(),
                "Ballistic firing estimate failed to lead observed target velocity");
        require(shot.uncertaintyRadius()>0,
                "Information age and computation incorrectly guarantee perfect aim");
        var better=SensorTargetingSolution.solve(evidence,70,0,0,500,4.0,711);
        require(better.uncertaintyRadius()<shot.uncertaintyRadius(),
                "Higher tracking computation failed to narrow uncertainty");
        var older=SensorTargetingSolution.solve(evidence,100,0,0,500,1.4,711);
        require(older.uncertaintyRadius()>shot.uncertaintyRadius(),
                "Old contact did not increase target uncertainty");
        var xray=SensorTargetingSolution.solve(
                report(50,60,Battle.CONTACT_XRAY,1500,0,12,0),
                70,0,0,500,1.4,711);
        require(xray.uncertaintyRadius()<shot.uncertaintyRadius(),
                "High-resolution X-ray failed to improve target solution");
        System.out.println("PASS bounded deterministic aim, measured velocity lead and finite compute benefit");
    }
    private static void partialErrorsStillStrike(){
        var contact=report(50,60,Battle.CONTACT_OPTICAL,1500,0,0,0);
        int geometricalHits=0,geometricalMisses=0,offCenterHits=0;
        // Actual ballistic tests below consume the same physical ray; this
        // checks that a nonzero aim error does not force a miss by decree.
        for(int shot=1;shot<=160;shot++){
            var estimate=SensorTargetingSolution.solve(
                    contact,70,0,0,500,0,shot);
            double travelX=estimate.aimX(),travelY=estimate.aimY();
            double rayLength=StrictMath.hypot(travelX,travelY);
            double missDistance=StrictMath.abs(1500*travelY/rayLength);
            if(missDistance<=12){
                geometricalHits++;
                if(StrictMath.hypot(estimate.aimX()-1500,
                        estimate.aimY())>0.01)offCenterHits++;
            }else geometricalMisses++;
        }
        require(geometricalHits>0&&geometricalMisses>0&&offCenterHits>0,
                "Imperfect target estimates must yield both intersections and misses");
        System.out.println("PASS imperfect aim can intersect real 12-unit hulls; "+
                "hits="+geometricalHits+" misses="+geometricalMisses+
                " nonzero-error-hits="+offCenterHits);
    }

    /**
     * Real Battle.fire + swept projectile regression, not a projected-ray
     * formula-only example. Two universes have identical received photons
     * but different CURRENT target locations at launch. The actual emitted
     * shot must match; where the hull physically stands during flight alone
     * determines a glancing impact versus a clean miss.
     */
    private static Battle kineticFixture(long seed){
        Battle battle=new Battle(seed);
        Battle.Ship shooter=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship victim=battle.addShip(1,Battle.Faction.DRAXIAN,280,100);
        battle.registerDiagnosticStationaryTarget(victim.id);
        shooter.speed=shooter.vx=shooter.vy=0;
        shooter.accuracy=5;
        shooter.maxPower=shooter.power=1000;
        shooter.powerRegen=1000;
        shooter.maxHeat=10000;
        shooter.sensorRange=900;
        shooter.infraredRange=shooter.xrayRange=shooter.graviticRange=0;
        shooter.kineticDamage=40;
        shooter.kineticAmmo=20;
        shooter.maxKineticAmmo=20;
        shooter.kineticReload=999; // Gather actual photons without premature fire.
        shooter.laserDamage=shooter.plasmaDamage=shooter.particleDamage=0;
        shooter.missileDamage=shooter.torpedoDamage=0;
        shooter.fighterReserve=shooter.droneReserve=shooter.repairDroneReserve=0;
        victim.maxHull=victim.hull=100000;
        victim.laserDamage=victim.kineticDamage=victim.plasmaDamage=0;
        victim.particleDamage=victim.missileDamage=victim.torpedoDamage=0;
        victim.fighterReserve=victim.droneReserve=victim.repairDroneReserve=0;
        victim.pdRange=0;
        for(int i=0;i<24;i++)battle.step();
        require(battle.hasTargetContact(shooter,victim,180),
                "Real sensors never delivered a historical contact before fire");
        return battle;
    }
    private static Battle.VisualEvent kineticShot(Battle battle,int shooterId){
        for(Battle.VisualEvent e:battle.visualEvents())
            if(e.sourceId()==shooterId&&e.phase()==Battle.VisualPhase.FIRE&&
                    "KINETIC".equals(e.weapon()))return e;
        throw new AssertionError("Ordinary fire control did not launch a real kinetic round");
    }
    private static boolean impact(Battle battle,long shotId){
        for(Battle.VisualEvent e:battle.visualEvents())
            if(e.shotId()==shotId&&e.phase()==Battle.VisualPhase.IMPACT&&
                    "KINETIC".equals(e.weapon()))return true;
        return false;
    }
    private static void liveCausalGrazingCollision(){
        Battle grazing=kineticFixture(994321),missing=kineticFixture(994321);
        Battle.Ship ga=grazing.ships().get(0),gb=missing.ships().get(0);
        Battle.Ship va=grazing.ships().get(1),vb=missing.ships().get(1);
        // Moving an enemy NOW cannot retroactively change the photons received
        // from its old y=100 pose. These shifts are deliberately opposite.
        va.y+=90;
        vb.y-=90;
        ga.kineticReload=gb.kineticReload=0;
        grazing.step();missing.step();
        var firedA=kineticShot(grazing,ga.id);
        var firedB=kineticShot(missing,gb.id);
        require(firedA.shotId()==firedB.shotId()&&
                        firedA.tick()==firedB.tick()&&
                        firedA.x()==firedB.x()&&firedA.y()==firedB.y()&&
                        firedA.targetX()==firedB.targetX()&&
                        firedA.targetY()==firedB.targetY(),
                "Ordinary kinetic launch consulted unseen current target truth");
        require(va.y!=vb.y,"Live target oracle fixture did not diverge");
        ga.kineticDamage=gb.kineticDamage=0; // Track ONE already-fired round.
        double dx=firedA.targetX()-firedA.x(),dy=firedA.targetY()-firedA.y();
        double length=StrictMath.hypot(dx,dy);
        require(length>80,"Historical estimate emitted an unusable projectile ray");
        double centerX=firedA.x()+dx*.65,centerY=firedA.y()+dy*.65;
        double normalX=-dy/length,normalY=dx/length;
        double radius=12*va.visualScale(); // Exact live swept collision radius.
        va.x=centerX+normalX*radius*.80;
        va.y=centerY+normalY*radius*.80;
        vb.x=centerX+normalX*radius*4;
        vb.y=centerY+normalY*radius*4;
        for(int i=0;i<120;i++){grazing.step();missing.step();}
        require(impact(grazing,firedA.shotId())&&grazing.hitCount>0,
                "Real imperfect kinetic bearing failed to graze the actual hull");
        require(!impact(missing,firedB.shotId())&&missing.hitCount==0,
                "Real physically separated hull was hit by an obsolete target lock");
        System.out.println("PASS same aged-contact fired bearing, real swept hull graze and physical depth-separated miss");
    }
    public static void main(String[] args){
        deterministic();
        partialErrorsStillStrike();
        liveCausalGrazingCollision();
        System.out.println("PASS physically meaningful historical sensor targeting estimation");
    }
}
