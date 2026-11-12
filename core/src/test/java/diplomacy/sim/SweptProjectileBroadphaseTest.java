package diplomacy.sim;

import java.util.Random;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Exact segment-collision parity for the exclusion-only rocket/kinetic
 * broadphase. The original solver remains the sole impact-time authority.
 */
public final class SweptProjectileBroadphaseTest {
    private SweptProjectileBroadphaseTest(){}
    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    private static double original(double ax,double ay,double bx,double by,
                                   double cx,double cy,double radius){
        return Battle.segmentHit(ax,ay,bx,by,cx,cy,radius);
    }
    private static double filtered(double ax,double ay,double bx,double by,
                                   double cx,double cy,double radius){
        if(!Battle.sweptAabbMayHit(StrictMath.min(ax,bx),StrictMath.max(ax,bx),
                StrictMath.min(ay,by),StrictMath.max(ay,by),cx,cy,radius))
            return Double.POSITIVE_INFINITY;
        return original(ax,ay,bx,by,cx,cy,radius);
    }
    private static void parity(double ax,double ay,double bx,double by,
                               double cx,double cy,double radius){
        double exact=original(ax,ay,bx,by,cx,cy,radius);
        double bounded=filtered(ax,ay,bx,by,cx,cy,radius);
        require(Double.doubleToLongBits(exact)==Double.doubleToLongBits(bounded),
                "Broadphase changed collision hit or entry time: "+
                ax+","+ay+" -> "+bx+","+by+" against "+cx+","+cy+
                " r="+radius+" exact="+exact+" filtered="+bounded);
    }
    /** Rail penetrators test the ACTUAL moving other body, not a frozen
     * endpoint. The broadphase must never suppress even an edge tangent. */
    private static double movingFiltered(double ax,double ay,
            double bx,double by,double oldX,double oldY,
            double newX,double newY,double fraction,double radius){
        if(!Battle.movingSweptAabbMayHit(
                StrictMath.min(ax,bx),StrictMath.max(ax,bx),
                StrictMath.min(ay,by),StrictMath.max(ay,by),
                oldX,oldY,newX,newY,radius))
            return Double.POSITIVE_INFINITY;
        return Battle.movingProjectileEntry(ax,ay,bx,by,
                oldX,oldY,newX,newY,fraction,radius);
    }
    private static void movingParity(double ax,double ay,double bx,double by,
            double oldX,double oldY,double newX,double newY,
            double fraction,double radius){
        double exact=Battle.movingProjectileEntry(ax,ay,bx,by,
                oldX,oldY,newX,newY,fraction,radius);
        double fast=movingFiltered(ax,ay,bx,by,
                oldX,oldY,newX,newY,fraction,radius);
        require(Double.doubleToLongBits(exact)==Double.doubleToLongBits(fast),
                "PD swept AABB discarded an exact relative-motion contact: "+
                        exact+" vs "+fast+" radius="+radius+
                        " fraction="+fraction+" start="+ax+","+ay);
    }
    private static void checkMovingBroadphaseParity(Random random){
        int cases=90_000;
        for(int i=0;i<cases;i++){
            double ax=-1200+random.nextDouble()*4700;
            double ay=-720+random.nextDouble()*2600;
            double bx=ax+(random.nextDouble()-.5)*580;
            double by=ay+(random.nextDouble()-.5)*580;
            double radius=.5+random.nextDouble()*70;
            double px,py,nx,ny;
            if(i%5==0){
                // Actual relative crossing: target moves across the shot
                // between its old and new positions.
                double fraction=random.nextDouble();
                px=ax+(bx-ax)*fraction+(random.nextDouble()-.5)*radius;
                py=ay+(by-ay)*fraction+(random.nextDouble()-.5)*radius;
                nx=px+(random.nextDouble()-.5)*350;
                ny=py+(random.nextDouble()-.5)*350;
            }else{
                px=-1300+random.nextDouble()*4800;
                py=-800+random.nextDouble()*2850;
                nx=px+(random.nextDouble()-.5)*500;
                ny=py+(random.nextDouble()-.5)*500;
            }
            movingParity(ax,ay,bx,by,px,py,nx,ny,
                    random.nextDouble(),radius);
        }
        for(double radius:new double[]{.001,.75,2.5,6.0,43.0})
            for(double d:new double[]{-1e-8,-1e-10,0,1e-10,1e-8}){
                // Crossing, zero-length segment, both swept-endpoint
                // tangencies and a near-miss after a partial tick.
                movingParity(0,0,100,0,50,-200,50,200,1,radius);
                movingParity(0,0,0,0,radius+d,0,radius+d,0,1,radius);
                movingParity(0,0,100,0,0,0,100,radius+d,.5,radius);
                movingParity(0,0,100,0,50,radius+d,50,radius+d,1,radius);
            }
        System.out.println("PASS PD moving-sweep broadphase "+cases+
                " seeded cases plus 100 tangent/crossing edge cases");
    }

    /** Two real paired 12v12 fitted encounters prove neither defensive
     * ammunition spending nor the earliest physical hit can diverge. */
    private static void checkLivePdParity() throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design armed=Shipyard.starter(
                Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        Shipyard.Design defender=Shipyard.bareHull(
                Battle.Faction.DRAXIAN,Shipyard.Hull.DREADNOUGHT);
        int mounted=0;
        for(Shipyard.Slot slot:defender.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON||mounted>=2)continue;
            defender=defender.install(slot.index(),
                    mounted==0?"BASE-DRA-RPD":"BASE-DRA-RCW");
            mounted++;
        }
        require(mounted==2&&Shipyard.inspect(defender,tech).valid(),
                "Real RPD+RCW two-mount defensive fixture is not constructible");
        long candidateChecks=0,narrowChecks=0;
        int totalShots=0;
        for(long seed:new long[]{2026111201L,2026111202L}){
            Battle filtered=Battle.duel(seed,12,armed,defender,tech);
            Battle reference=Battle.duel(seed,12,armed,defender,tech);
            filtered.configurePdProjectileBroadphaseForDiagnostic(true);
            reference.configurePdProjectileBroadphaseForDiagnostic(false);
            for(Battle.Ship ship:filtered.ships())
                if(ship.team==0){
                    ship.missileDamage=Math.max(35,ship.missileDamage);
                    ship.missileAmmo=100;
                    ship.torpedoDamage=Math.max(35,ship.torpedoDamage);
                    ship.torpedoAmmo=40;
                }
            for(Battle.Ship ship:reference.ships())
                if(ship.team==0){
                    ship.missileDamage=Math.max(35,ship.missileDamage);
                    ship.missileAmmo=100;
                    ship.torpedoDamage=Math.max(35,ship.torpedoDamage);
                    ship.torpedoAmmo=40;
                }
            for(int t=0;t<240;t++){
                filtered.step();
                reference.step();
                require(filtered.fingerprint()==reference.fingerprint(),
                        "RPD/RCW live collision broadphase changed physical "+
                        "combat at tick "+filtered.tick+" seed "+seed);
            }
            var pruned=filtered.pdProjectileBroadphaseMetrics();
            var exact=reference.pdProjectileBroadphaseMetrics();
            require(pruned.candidateChecks()==exact.candidateChecks()&&
                    pruned.narrowPhaseChecks()<=exact.narrowPhaseChecks(),
                    "PD A/B candidates differed or filtering increased work");
            candidateChecks+=pruned.candidateChecks();
            narrowChecks+=pruned.narrowPhaseChecks();
            totalShots+=(int)filtered.visualEvents().stream().filter(e->
                    e.phase()==Battle.VisualPhase.FIRE&&
                    ("RAIL_PD".equals(e.weapon())||
                     "ROTARY_CIWS".equals(e.weapon()))).count();
        }
        require(totalShots>0&&candidateChecks>0&&
                narrowChecks<candidateChecks,
                "Fitted PD benchmark did not fire live shots and prune "+
                        "distant hostile collision candidates");
        System.out.println("PASS RPD/RCW exact-source broadphase A/B "+
                "12v12 seeds=2 ticks=240 candidateChecks="+candidateChecks+
                " narrowPhaseChecks="+narrowChecks+
                " avoided="+(candidateChecks-narrowChecks)+
                " realFiringEvents="+totalShots+" (counts diagnostic only)");
    }

    /**
     * The exact mounted acquisition equation is authoritative. A conservative
     * 2.2x upper envelope may reject distance only when NO actual researched
     * missile, torpedo, rocket or ballistic signature could possibly be
     * acquired. Sweep near-equality, varying motor emissions and bad floats.
     */
    private static void checkLaserPdRangeUpperBound(Random random){
        long checks=0,culled=0,exactSquaredCulled=0;
        for(int i=0;i<85_000;i++){
            var spec=new ProjectileModel.Spec(
                    10+random.nextDouble()*1490,
                    .1+random.nextDouble()*199.9,
                    .04+random.nextDouble()*7.96,
                    .04+random.nextDouble()*7.96);
            var pd=new ProjectileModel.Defense(
                    .4+random.nextDouble()*2.6,
                    .3+random.nextDouble()*3.7,
                    .1+random.nextDouble()*39.9);
            double base=random.nextDouble()*1500;
            double sensors=-.2+random.nextDouble()*1.7;
            double weather=-.3+random.nextDouble()*3.2;
            double emission= i%4==0?.001:
                    i%4==1?1.0:i%4==2?4.0:5.2;
            double exact=ProjectileModel.acquisitionRange(
                    base,sensors,weather,pd,spec,emission);
            double bound=ProjectileModel.maxPossibleAcquisitionDistanceSquared(
                    base,sensors,weather,pd);
            // Exact equality and both sides of the physical acquisition
            // threshold. Small epsilons exercise float comparison margins.
            for(double delta:new double[]{-1e-11,0,1e-11,0.5,2000}){
                double distance=StrictMath.max(0,exact+delta);
                double angle=(i*0.067654)% (2*StrictMath.PI);
                double dx=distance*StrictMath.cos(angle);
                double dy=distance*StrictMath.sin(angle);
                boolean rejected=ProjectileModel.definitelyOutsideAcquisition(
                        dx,dy,bound);
                boolean exactSquared=ProjectileModel.definitelyOutsideExactAcquisition(
                        dx,dy,exact);
                double realDistance=StrictMath.hypot(dx,dy);
                require(!(exactSquared&&realDistance<=exact),
                        "Exact squared acquisition broadphase rejected in-range "+
                                "target with radius "+exact+
                                " measuredDistance "+realDistance);
                require(!(rejected&&realDistance<=exact),
                        "Laser PD broadphase lost in-range real projectile "+
                        "at spec="+spec+" emission="+emission+
                        " radius="+exact+" distance="+realDistance);
                if(rejected)culled++;
                if(exactSquared)exactSquaredCulled++;
                checks++;
            }
        }
        require(culled>0&&exactSquaredCulled>culled,
                "Squared range exclusion failed to reject additional far targets");
        double one=ProjectileModel.maxPossibleAcquisitionDistanceSquared(
                220,1,1,ProjectileModel.STANDARD_PD);
        require(!ProjectileModel.definitelyOutsideAcquisition(
                        Double.NaN,0,one)&&
                !ProjectileModel.definitelyOutsideAcquisition(
                        Double.POSITIVE_INFINITY,0,one)&&
                !ProjectileModel.definitelyOutsideAcquisition(
                        10,10,Double.NaN)&&
                !ProjectileModel.definitelyOutsideExactAcquisition(
                        Double.NaN,0,100)&&
                !ProjectileModel.definitelyOutsideExactAcquisition(
                        Double.POSITIVE_INFINITY,0,100),
                "Non-finite geometry must fall through to exact PD equations");
        System.out.println("PASS laser PD conservative acquisition envelope "+
                checks+" seeded boundary / motor signature checks, "+
                culled+" maximum-envelope exclusions, "+
                exactSquaredCulled+" exact-radius exclusions "+
                "(nonblocking work count)");
    }

    private static Battle fittedLaserPd(TechnologyCatalog tech,long seed,
            int count){
        var left=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        var right=Shipyard.bareHull(Battle.Faction.DRAXIAN,
                Shipyard.Hull.DREADNOUGHT);
        boolean fittedLeft=false,fittedRight=false;
        for(Shipyard.Slot slot:left.hull.slots)
            if(!fittedLeft&&slot.mount()==Shipyard.Mount.WEAPON){
                left=left.install(slot.index(),"BASE-TER-MSL");
                fittedLeft=true;
            }
        for(Shipyard.Slot slot:right.hull.slots)
            if(!fittedRight&&slot.mount()==Shipyard.Mount.WEAPON){
                right=right.install(slot.index(),"BASE-DRA-PD");
                fittedRight=true;
            }
        require(fittedLeft&&fittedRight&&
                        Shipyard.inspect(left,tech).valid()&&
                        Shipyard.inspect(right,tech).valid(),
                "Real manufactured missile and laser point-defense designs invalid");
        Battle battle=new Battle(seed);
        battle.spawnWing(0,left,tech,count,460,350);
        battle.spawnWing(1,right,tech,count,740,350);
        for(Battle.Ship ship:battle.ships()){
            ship.fighterReserve=ship.droneReserve=0;
            if(ship.team==0)ship.missileAmmo=100;
        }
        battle.setTeamPointDefense(1,BattleOrders.PointDefense.BALANCED);
        return battle;
    }

    /** No physical launcher, warhead, PD lock, hit or ammo budget may change.
     * Compare exact state after every 30Hz step in two real assembled fleets,
     * then in the canonical 200v200 demonstration with independent PD. */
    private static void checkLaserPdPhysicalParity() throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        long visited=0,excluded=0,exact=0,shots=0;
        for(int scenario=0;scenario<3;scenario++){
            Battle optimized=scenario==0?
                    fittedLaserPd(tech,2026111223L,16):
                    scenario==1?fittedLaserPd(tech,2026111224L,48):
                    DemonstrationBattle.create(tech);
            Battle reference=scenario==0?
                    fittedLaserPd(tech,2026111223L,16):
                    scenario==1?fittedLaserPd(tech,2026111224L,48):
                    DemonstrationBattle.create(tech);
            optimized.configureLaserPdRangeBroadphaseForDiagnostic(true);
            reference.configureLaserPdRangeBroadphaseForDiagnostic(false);
            final int steps=scenario==0?175:scenario==1?115:55;
            for(int t=0;t<steps;t++){
                optimized.step();reference.step();
                require(optimized.fingerprint()==reference.fingerprint(),
                        "Laser PD distant-contact optimization changed combat "+
                        "at scenario="+scenario+" tick="+optimized.tick);
            }
            var fast=optimized.laserPdRangeMetrics();
            var old=reference.laserPdRangeMetrics();
            require(fast.candidates()==old.candidates()&&
                    fast.exactDistanceChecks()+fast.excluded()==
                        fast.candidates()&&
                    old.excluded()==0&&
                    old.exactDistanceChecks()==old.candidates(),
                    "Laser PD broadphase changed actual candidate iteration");
            long fired=optimized.visualEvents().stream().filter(event->
                    event.phase()==Battle.VisualPhase.FIRE&&
                    "POINT_DEFENSE".equals(event.weapon())).count();
            visited+=fast.candidates();excluded+=fast.excluded();
            exact+=fast.exactDistanceChecks();shots+=fired;
            System.out.println("PASS laser PD physical range A/B scenario="+
                    (scenario==0?"fitted 16v16":
                     scenario==1?"fitted 48v48":"canonical 200v200")+
                    " ticks="+steps+" scanned="+fast.candidates()+
                    " excluded="+fast.excluded()+
                    " exact="+fast.exactDistanceChecks()+
                    " PD_firing_events="+fired+" fingerprint="+
                    Long.toHexString(optimized.fingerprint()));
        }
        require(visited>0&&exact>0&&shots>0,
                "Real laser PD range test never saw live physical threats");
        System.out.println("MEASURE laser PD conservative range totals visited="+
                visited+" avoided="+excluded+" exact="+exact+
                " shots="+shots+" (CPU timings advisory only)");
    }

    public static void main(String[] args) throws Exception{
        Random random=new Random(0xB0A0D5E);
        long checks=0,nearby=0;
        for(int i=0;i<150_000;i++){
            double ax=-1200+random.nextDouble()*4700;
            double ay=-700+random.nextDouble()*2600;
            double bx=ax+(random.nextDouble()-.5)*1000;
            double by=ay+(random.nextDouble()-.5)*1000;
            double radius=4.2+random.nextDouble()*42;
            double cx,cy;
            if(i%4==0){
                double t=random.nextDouble();
                cx=ax+(bx-ax)*t+(random.nextDouble()-.5)*radius*2;
                cy=ay+(by-ay)*t+(random.nextDouble()-.5)*radius*2;
                nearby++;
            }else if(i%4==1){
                // Exact tangencies and endpoint/corner boundaries.
                cx=StrictMath.min(ax,bx)-radius+(random.nextDouble()-.5)*1e-7;
                cy=StrictMath.max(ay,by)+radius+(random.nextDouble()-.5)*1e-7;
            }else{
                cx=-1200+random.nextDouble()*4700;
                cy=-700+random.nextDouble()*2600;
            }
            parity(ax,ay,bx,by,cx,cy,radius);checks++;
        }
        for(double radius:new double[]{4.2,5.5,12,48})
            for(double shift:new double[]{-1e-8,-1e-10,0,1e-10,1e-8}){
                parity(10,20,10,20,10+radius+shift,20,radius);checks++;
                parity(10,20,110,20,10-radius+shift,20,radius);checks++;
                parity(10,20,110,20,110+radius+shift,20,radius);checks++;
                parity(10,20,110,20,60,20+radius+shift,radius);checks++;
            }
        final int n=75_000;
        double[] ax=new double[n],ay=new double[n],bx=new double[n],by=new double[n];
        double[] cx=new double[n],cy=new double[n],radius=new double[n];
        for(int i=0;i<n;i++){
            ax[i]=-1000+random.nextDouble()*4200;
            ay[i]=-600+random.nextDouble()*2200;
            bx[i]=ax[i]+(random.nextDouble()-.5)*250;
            by[i]=ay[i]+(random.nextDouble()-.5)*250;
            cx[i]=-1000+random.nextDouble()*4200;
            cy[i]=-600+random.nextDouble()*2200;
            radius[i]=5+random.nextDouble()*22;
        }
        long exactNs=0,filteredNs=0,exactHits=0,filteredHits=0;
        for(int run=0;run<8;run++){
            for(int order=0;order<2;order++){
                boolean useFiltered=((run+order)&1)==0;
                long start=System.nanoTime();
                long hits=0;
                for(int i=0;i<n;i++){
                    double hit=useFiltered?
                            filtered(ax[i],ay[i],bx[i],by[i],cx[i],cy[i],radius[i]):
                            original(ax[i],ay[i],bx[i],by[i],cx[i],cy[i],radius[i]);
                    if(Double.isFinite(hit))hits++;
                }
                long elapsed=System.nanoTime()-start;
                if(run>=2){
                    if(useFiltered){filteredNs+=elapsed;filteredHits+=hits;}
                    else{exactNs+=elapsed;exactHits+=hits;}
                }
            }
        }
        require(exactHits==filteredHits,"Broadphase changed benchmark collision count");
        checkMovingBroadphaseParity(random);
        checkLivePdParity();
        checkLaserPdRangeUpperBound(random);
        checkLaserPdPhysicalParity();
        System.out.println("PASS swept projectile broadphase parity cases="+checks+
                " nearPathCases="+nearby+" hits="+filteredHits+
                " narrowOnlyMs="+exactNs/1e6+
                " broadphaseMs="+filteredNs/1e6+
                " (timings diagnostic only)");
    }
}
