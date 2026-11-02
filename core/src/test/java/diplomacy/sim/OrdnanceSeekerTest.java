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
    private static void hologramFalseGuidance(){
        Battle b=scene(272728L);
        Battle.Ship target=b.ships().get(1);
        target.hologramNoise=.75;
        target.ecmCondition=1;
        target.opticalSignature=.38;
        target.power=500;target.maxPower=20000;
        int suitable=-1;
        for(int id=1;id<500;id++)
            if(DeceptionProjection.seekerGhostIndex(target,id,1,1)>=0){
                suitable=id;break;
            }
        check(suitable>0,"Real holographic emitter never deceives a standard seeker");
        int ghost=DeceptionProjection.seekerGhostIndex(target,suitable,1,1);
        check(ghost<DeceptionProjection.falseContacts(target),
                "Seeker selected an emitter contact that is not actually rendered");
        double falseX=DeceptionProjection.ghostX(target,ghost,1);
        double falseY=DeceptionProjection.ghostY(target,ghost,1);
        check(StrictMath.hypot(falseX-target.x,falseY-target.y)>25,
                "Deceived missile must steer toward an actual displaced contact");
        check(ghost==DeceptionProjection.seekerGhostIndex(target,suitable,1,1),
                "Seeker spoofing must be replay deterministic");
        target.power=4;
        check(DeceptionProjection.seekerGhostIndex(target,suitable,1,1)==-1,
                "Power failure must stop holographic seeker misdirection");
        target.power=12;target.maxPower=12;
        double previous=target.power,heat=target.heat;
        for(int frame=0;frame<130;frame++)
            DeceptionProjection.operate(target,1.0/30);
        check(target.power<previous&&target.heat>heat,
                "Powered cloak and holograms must consume real power and emit heat");
        check(target.power>=4&&DeceptionProjection.falseContacts(target)==0,
                "Depleted emitters must stop projecting false contacts");
    }
    private static void realMissilesPursueProjectedContacts(){
        Battle battle=scene(272729L);
        Battle.Ship target=battle.ships().get(1);
        target.power=500;target.ecmCondition=1;target.hologramNoise=.75;
        target.ecmStrength=0;
        // The first missile may pass an ECM check. Other independent
        // physical launchers must be eligible for the decoy, not merely a
        // made-up counter in a status text or a copied visual projectile.
        for(int i=0;i<6;i++){
            Battle.Ship shooter=battle.addShip(0,Battle.Faction.TERRAN,190-i*18,300+i*9);
            shooter.speed=0;
            shooter.laserDamage=shooter.kineticDamage=shooter.plasmaDamage=
                    shooter.particleDamage=shooter.torpedoDamage=0;
            shooter.missileDamage=30;shooter.missileAmmo=1;
            shooter.pdRange=0;shooter.fighterReserve=shooter.droneReserve=0;
            shooter.maxPower=shooter.power=20000;
        }
        battle.step();
        Battle.Missile diverted=null;
        for(Battle.Missile m:battle.missiles())
            if(m.falseContactIndex>=0){diverted=m;break;}
        check(diverted!=null,
                "Live autonomous missile salvos never acquired a real false contact");
        check(diverted.targetId==target.id&&diverted.seekerLocked,
                "Hologram must fool the original lock, not fabricate a second target hull");
        check(target.holoSeekersDiverted>0,
                "Real redirected seekers must affect the actual tactical counter");
        check(StrictMath.hypot(diverted.lastKnownX-target.x,
                diverted.lastKnownY-target.y)>25,
                "Real projectile guidance must be fed the hologram's world position");
        double ghostX=DeceptionProjection.ghostX(target,diverted.falseContactIndex,battle.tick);
        double ghostY=DeceptionProjection.ghostY(target,diverted.falseContactIndex,battle.tick);
        check(diverted.lastKnownX==ghostX&&diverted.lastKnownY==ghostY,
                "Live seeker coordinates must match the very same visible decoy sprite");
        target.power=0;
        battle.step();
        check(diverted.falseContactIndex==-1
                &&diverted.lastKnownX==target.x&&diverted.lastKnownY==target.y,
                "Shutting down the holo emitter must restore live missile guidance");
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
        lossAndReacquisition();hologramFalseGuidance();
        realMissilesPursueProjectedContacts();pointDefenseStillWorks();replay();
        System.out.println("PASS seeded seeker ECM loss/reacquisition, cold-coast PD counterplay and replay");
    }
}
