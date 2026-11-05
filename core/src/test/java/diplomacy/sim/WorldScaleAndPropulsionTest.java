package diplomacy.sim;

import diplomacy.shipyard.Shipyard;

/** Physical metre/second conversion, installed engines, live thrust and
 * half-size original-art hull + fired muzzle geometry. */
public final class WorldScaleAndPropulsionTest {
    private WorldScaleAndPropulsionTest(){}
    private static void ok(boolean pass,String description){
        if(!pass)throw new AssertionError(description);
    }
    private static void close(double actual,double expected,double epsilon,String why){
        ok(Double.isFinite(actual)&&StrictMath.abs(actual-expected)<=epsilon,
                why+": expected "+expected+" got "+actual);
    }
    private static Shipyard.Slot firstSystem(Shipyard.Hull hull,int nth){
        for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.SYSTEM&&nth--==0)return slot;
        throw new AssertionError("Missing system slot");
    }
    private static void physicalScale(){
        close(WorldScale.PHYSICAL_SECONDS_PER_SIM_SECOND,60,0,"Physical clock");
        close(WorldScale.DIAGONAL_WORLD_UNITS,StrictMath.hypot(3840,2160),
                1e-9,"Fixed arena");
        close(WorldScale.physicalSecondsForLight(WorldScale.DIAGONAL_WORLD_UNITS),
                60,1e-9,"One-way diagonal photon transit");
        close(WorldScale.signalDelayTicks(WorldScale.DIAGONAL_WORLD_UNITS,false),
                30,1e-9,"One-way diagonal in combat ticks");
        close(WorldScale.signalDelayTicks(WorldScale.DIAGONAL_WORLD_UNITS,true),
                60,1e-9,"Two-way radar burst in combat ticks");
        close(WorldScale.simSpeedForLightFraction(.01)/
                WorldScale.C_WORLD_UNITS_PER_SIM_SECOND,.01,1e-15,
                "One percent of c speed mapping");
        ok(WorldScale.SHIP_WORLD_SCALE==.5,
                "All physical and rendering hulls must use shared half-size");
        System.out.println("PASS fixed one-light-minute diagonal and 60:1 compressed clock");
    }
    private static void installedDrives(){
        Shipyard.Design bare=Shipyard.bareHull(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        ok(SublightPropulsion.fitted(bare)==SublightPropulsion.Drive.AUXILIARY_ELECTRIC,
                "A bare hull mysteriously got a paid primary engine");
        Shipyard.Design starter=Shipyard.starter(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        ok(SublightPropulsion.fitted(starter)==SublightPropulsion.Drive.FUSION_TORCH,
                "Real factory ENG installation did not supply fusion");
        int fittedSocket=-1;
        for(Shipyard.Slot slot:starter.hull.slots)
            if(starter.at(slot.index())!=null&&
                    starter.at(slot.index()).component.endsWith("-ENG")){
                fittedSocket=slot.index();break;
            }
        ok(fittedSocket>=0,"Starter lacks physical ENG installation");
        Shipyard.Design specialist=starter.stage(fittedSocket,Shipyard.Track.OUTPUT,4);
        ok(SublightPropulsion.fitted(specialist)==SublightPropulsion.Drive.ADVANCED_FUSION,
                "Engine stage IV did not improve thrust technology");
        Shipyard.Design high=starter.stage(fittedSocket,Shipyard.Track.OUTPUT,7);
        ok(SublightPropulsion.fitted(high)==SublightPropulsion.Drive.HIGH_ENERGY_TORCH,
                "Engine stage VII did not unlock high-energy torch");
        Shipyard.Design two=bare.install(firstSystem(bare.hull,0).index(),
                "BASE-TER-ENG").install(firstSystem(bare.hull,1).index(),
                "BASE-TER-ENG");
        ok(SublightPropulsion.fitted(two)==SublightPropulsion.Drive.ADVANCED_FUSION,
                "Two paid mounted engines did not improve propulsion");
        for(SublightPropulsion.Drive drive:SublightPropulsion.Drive.values()){
            ok(drive.maxFractionOfC>0&&drive.maxFractionOfC<.02,
                    "Ship drive exceeds 2 percent of c: "+drive);
            ok(drive.accelerationGee>0&&drive.accelerationGee<=10,
                    "Crewed drive acceleration implausibly high: "+drive);
        }
        System.out.println("PASS bare, one-ENG, two-ENG and upgraded-drive tiers");
    }
    private static void physicalMovement(){
        Battle b=new Battle(24789);
        Battle.Ship ship=b.addShip(0,Battle.Faction.TERRAN,300,400);
        ship.propulsion=SublightPropulsion.Drive.FUSION_TORCH;
        ship.fuel=500;
        ship.engineCondition=1;
        ship.speed=160; // a formerly implausibly fast installed ship
        double cap=SublightPropulsion.maximumSpeed(ship);
        close(cap,WorldScale.simSpeedForLightFraction(.006),1e-9,
                "Unrestricted 160-unit/s ship was not physically capped");
        double before=ship.fuel;
        double initialX=ship.x;
        for(int i=0;i<150;i++){
            double desired=SublightPropulsion.maximumSpeed(ship);
            SublightPropulsion.advance(ship,desired,0,Battle.DT);
            ok(StrictMath.hypot(ship.vx,ship.vy)<=cap+1e-6,
                    "Ship exceeded its physical drive maximum speed");
            ok(WorldScale.fractionOfLightSpeed(
                    StrictMath.hypot(ship.vx,ship.vy))<=.006+1e-12,
                    "A crewed ship accelerated toward lightspeed");
        }
        ok(ship.x>initialX&&ship.fuel<before,
                "Powered engine failed to produce physical thrust and consume fuel");
        double speedBefore=ship.vx,position=ship.x;
        ship.fuel=0;
        for(int i=0;i<6;i++)
            SublightPropulsion.advance(ship,0,0,Battle.DT);
        close(ship.vx,speedBefore,1e-10,"Fuel-starved ship got free brakes");
        ok(ship.x>position,"Fuel-starved ship failed to coast inertially");

        // A protected threshold is a spending limit on the SAME tank.
        // Thrust cannot borrow even the final fractional unit of fuel.
        Battle.Ship rationed=b.addShip(0,Battle.Faction.TERRAN,300,420);
        rationed.propulsion=SublightPropulsion.Drive.FUSION_TORCH;
        rationed.engineCondition=1;rationed.speed=160;
        rationed.maxFuel=100;rationed.fuel=25.001;
        for(int i=0;i<3000;i++)
            SublightPropulsion.advance(rationed,100,0,Battle.DT,25);
        ok(rationed.fuel>=25-1e-9&&rationed.fuel<25.001,
                "Discretionary thrust crossed the standing fuel reserve");
        double stoppedVelocity=rationed.vx;
        for(int i=0;i<300;i++)
            SublightPropulsion.advance(rationed,100,0,Battle.DT,25);
        close(rationed.vx,stoppedVelocity,1e-9,
                "Protected reserve permitted free delta-V once fuel budget was exhausted");
        SublightPropulsion.advance(rationed,-100,0,Battle.DT);
        ok(rationed.fuel<25,"Physical withdrawal thrust could not access protected fuel");

        rationed.vx=0;rationed.vy=0;rationed.fuel=.000001;
        SublightPropulsion.advance(rationed,100,0,Battle.DT);
        ok(rationed.vx<=.000001/rationed.propulsion.fuelPerDeltaV+1e-9,
                "Final partial fuel unit provided more delta-V than physically available");

        // Economy must physically spend LESS fuel even before a low-gee ship
        // reaches its speed cap. A cosmetic speed setting is not sufficient.
        Battle.Ship unrestricted=b.addShip(0,Battle.Faction.TERRAN,300,450);
        Battle.Ship economical=b.addShip(0,Battle.Faction.TERRAN,300,480);
        unrestricted.speed=160;economical.speed=160;
        unrestricted.maxFuel=100;economical.maxFuel=100;
        unrestricted.fuel=20;economical.fuel=20;
        double fraction=ShipResourceOrders.DEFAULT
                .withFuelPolicy(ShipResourceOrders.FuelPolicy.ECONOMY)
                .withEconomyStart(60).thrustSpeedFactor(economical);
        ok(fraction<1&&fraction>.3,"Economy posture did not throttle low fuel");
        for(int i=0;i<120;i++){
            SublightPropulsion.advance(unrestricted,100,0,Battle.DT);
            SublightPropulsion.advance(economical,100*fraction,0,Battle.DT,0,fraction);
        }
        ok(economical.fuel>unrestricted.fuel&&economical.vx<unrestricted.vx,
                "Economy posture failed to save actual reaction mass during acceleration");
        System.out.println("PASS bounded impulse, one-tank reserve, paid economy thrust and coast");
    }
    /** Enter an actual fitted fleet engagement with already established,
     * physically bounded pre-battle approach momentum. Slow acceleration
     * alone must not leave a newly deployed screen visually frozen. */
    private static void visibleDeploymentMovement() throws Exception {
        Shipyard.Design left=Shipyard.starter(
                Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        Shipyard.Design right=Shipyard.starter(
                Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER);
        Battle fight=Battle.duel(719912,1,left,right,
                diplomacy.tech.TechnologyCatalog.loadDefault());
        Battle.Ship friendly=fight.ships().get(0), hostile=fight.ships().get(1);
        ok(friendly.vx==0&&hostile.vx==0,
                "Headless deployment fixtures should begin stationary");
        fight.initializeBattleApproach();
        ok(friendly.vx>0&&hostile.vx<0,
                "Opposing deployed fleets must retain incoming approach velocities");
        ok(StrictMath.abs(friendly.vx)<=SublightPropulsion.maximumSpeed(friendly)
                        &&StrictMath.abs(hostile.vx)<=SublightPropulsion.maximumSpeed(hostile),
                "Deployment inherited an impossible super-rated ship velocity");
        double startX=friendly.x;
        for(int i=0;i<60;i++)fight.step();
        ok(friendly.x>startX+2,
                "Fitted ship did not visibly advance after two combat seconds");
    }

    private static void halfGeometry() throws Exception {
        Battle b=new Battle(88611);
        Battle.Ship legacy=b.addShip(0,Battle.Faction.TERRAN,350,300);
        double expected=StrictMath.max(.78,StrictMath.min(1.90,
                StrictMath.sqrt(legacy.maxHull/200.0)))*.5;
        close(legacy.visualScale(),expected,1e-10,
                "Legacy physical hull size not 50% of original");
        Shipyard.Design fitted=Shipyard.starter(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        Battle fittedBattle=Battle.duel(99277L,1,fitted,
                Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.DREADNOUGHT),
                diplomacy.tech.TechnologyCatalog.loadDefault());
        Battle.Ship installed=fittedBattle.ships().get(0);
        close(installed.visualScale(),StrictMath.max(.78,StrictMath.min(1.90,
                StrictMath.sqrt(installed.maxHull/200.0)))*
                installed.hullClass.spriteScale()*.5,1e-10,
                "Installed faction original-art sprite not 50%");
        ok(!installed.weaponEmitters.isEmpty(),
                "No original-art hardpoints in installed ship fixture");
        WeaponMuzzles.Emitter emitter=installed.weaponEmitters.stream()
                .filter(x->x.family()==WeaponMuzzles.Family.LASER)
                .findFirst().orElseThrow(()->new AssertionError("No laser hardpoint"));
        WeaponMuzzles.Origin origin=WeaponMuzzles.atSlot(installed,
                WeaponMuzzles.Family.LASER,emitter.slotId());
        double distance=StrictMath.hypot(origin.x()-installed.x,
                origin.y()-installed.y);
        double original=StrictMath.hypot(emitter.localX(),emitter.localY())
                *34.0/154.0*installed.visualScale();
        close(distance,original,1e-8,
                "Muzzle origin disconnected from half-scale original art");
        ok(12*installed.visualScale()<12*installed.visualScale()/.5,
                "Swept-kinetic hull hit radius failed to decrease");
        System.out.println("PASS 50%-size fitted hull, physical kinetic radius and source-art muzzle");
    }
    public static void main(String[] args)throws Exception{
        physicalScale();
        installedDrives();
        physicalMovement();
        visibleDeploymentMovement();
        halfGeometry();
        System.out.println("PASS half-scale ship physical geometry and sublight thrust integration");
    }
}
