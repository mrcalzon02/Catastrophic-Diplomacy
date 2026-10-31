package diplomacy.sim;

/** Seeded, live missile seeker/ECM/PD regression; no renderer or mock projectiles. */
public final class OrdnanceSeekerTest {
    private OrdnanceSeekerTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }
    private static Battle scene(long seed){
        Battle b=new Battle(seed);
        Battle.Ship a=b.addShip(0,Battle.Faction.TERRAN,200,300);
        Battle.Ship t=b.addShip(1,Battle.Faction.CARNELIAN,760,300);
        for(Battle.Ship s:b.ships()){
            s.speed=s.vx=s.vy=0;
            s.laserDamage=s.plasmaDamage=s.particleDamage=s.kineticDamage=0;
            s.missileDamage=s.torpedoDamage=0;
            s.missileAmmo=s.torpedoAmmo=0;
            s.pdRange=0;s.fighterReserve=s.droneReserve=s.repairDroneReserve=0;
            s.maxPower=s.power=20000;s.powerRegen=0;s.maxHeat=20000;s.heat=0;
        }
        a.missileAmmo=1;a.missileDamage=30;
        t.maxHull=t.hull=100000;
        return b;
    }
    private static void lossAndReacquisition(){
        Battle b=scene(908172L);
        Battle.Ship t=b.ships().get(1);
        t.ecmStrength=1;t.ecmCondition=1;
        b.step();
        check(b.missiles().size()==1,"one real missile must launch");
        Battle.Missile m=b.missiles().get(0);
        check(!m.seekerLocked,"hostile ECM must break initial seeker lock");
        check(m.targetId==t.id,"ECM cannot magically retarget the missile");
        double oldY=m.lastKnownY;
        t.y+=240;
        for(int i=0;i<4;i++)b.step();
        check(!m.seekerLocked&&m.lastKnownY==oldY,
                "lost seeker cannot observe a moving target through ECM");
        t.ecmStrength=0;
        boolean recovered=false;
        for(int i=0;i<12;i++){
            b.step();
            if(m.seekerLocked){recovered=true;break;}
        }
        check(recovered,"unobstructed target must be reacquired on a bounded scan");
        check(m.lastKnownY==t.y&&m.targetId==t.id,
                "reacquisition must use actual original target coordinates");
        check(OrdnanceSeeker.canTrack(560,1,0,1,false,false)
                &&!OrdnanceSeeker.canTrack(560,1,1,1,false,false),
                "physical range and ECM must change lock outcome");
        check(!OrdnanceSeeker.canTrack(1300,1,0,1,false,true),
                "new locks require tighter geometry than maintained locks");
    }
    private static void pointDefenseStillWorks(){
        Battle b=scene(7711L);
        Battle.Ship a=b.ships().get(0),t=b.ships().get(1);
        a.setPropulsionMode(Battle.DamageType.MISSILE,
                OrdnancePropulsion.Mode.SILENT_GHOST);
        b.step();
        check(b.missiles().size()==1,"ghost missile launched");
        for(int i=0;i<21;i++)b.step();
        check(b.missiles().size()==1,"coasting missile still physical");
        Battle.Missile m=b.missiles().get(0);
        check(m.thrustPhase==OrdnancePropulsion.Phase.COAST,
                "missile has entered actual silent inertial coast");
        double quiet=ProjectileModel.acquisitionRange(1000,1,1,
                ProjectileModel.STANDARD_PD,m.spec,
                OrdnancePropulsion.emissionMultiplier(m.thrustPhase));
        double hot=ProjectileModel.acquisitionRange(1000,1,1,
                ProjectileModel.STANDARD_PD,m.spec,
                OrdnancePropulsion.emissionMultiplier(OrdnancePropulsion.Phase.BOOST));
        check(quiet>0&&quiet<hot,
                "silent coast remains detectable by nonzero physical cross section");
        t.pdRange=1000;
        t.pointDefenseProfile=new ProjectileModel.Defense(2,4,35);
        for(int i=0;i<20&&b.missileIntercepts==0;i++)b.step();
        check(b.missileIntercepts>0,
                "actual PD must still destroy the physical cold-coasting body");
    }
    private static void replay(){
        Battle a=scene(822L),b=scene(822L);
        for(Battle world:new Battle[]{a,b}){
            world.ships().get(1).ecmStrength=1;
        }
        for(int i=0;i<28;i++){
            for(Battle world:new Battle[]{a,b}){
                Battle.Ship t=world.ships().get(1);
                if(i==6)t.y+=210;
                if(i==13)t.ecmStrength=0;
                if(i==23)t.ecmStrength=1;
                world.step();
            }
            check(a.fingerprint()==b.fingerprint(),
                    "same seed and ECM transitions must replay identical lock state");
        }
    }
    public static void main(String[] args){
        lossAndReacquisition();pointDefenseStillWorks();replay();
        System.out.println("PASS seeded seeker ECM loss/reacquisition, cold-coast PD counterplay and replay");
    }
}
