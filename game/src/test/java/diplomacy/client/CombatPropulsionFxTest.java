package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;

/** Headless motor-driven FX: cold is dark, coast is silent, terminal relights. */
public final class CombatPropulsionFxTest {
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    private static Battle scenario(OrdnanceDoctrine.Mode mode){
        Battle b=new Battle(8251L);
        Battle.Ship attacker=b.addShip(0,Battle.Faction.TERRAN,200,360);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,820,360);
        attacker.speed=0;target.speed=0;
        attacker.pdRange=target.pdRange=0;
        attacker.setLaunchMode(Battle.DamageType.MISSILE,mode);
        attacker.laserDamage=attacker.kineticDamage=attacker.plasmaDamage=0;
        attacker.particleDamage=attacker.torpedoDamage=0;
        attacker.missileAmmo=12;attacker.missileDamage=22;
        target.laserDamage=target.missileDamage=target.kineticDamage=0;
        target.plasmaDamage=target.particleDamage=target.torpedoDamage=0;
        target.missileAmmo=target.kineticAmmo=target.torpedoAmmo=0;
        target.maxHull=100000;target.hull=target.maxHull;
        return b;
    }
    public static void main(String[] ignored){
        Battle cold=scenario(OrdnanceDoctrine.Mode.COLD_LAUNCH);
        CombatLightingModel fx=new CombatLightingModel();
        boolean sawCold=false,sawBurn=false,sawIgnition=false;
        for(int tick=0;tick<90;tick++){
            cold.step();fx.update(cold);
            for(Battle.Missile m:cold.missiles()){
                if(m.thrustPhase==OrdnancePropulsion.Phase.COLD_EJECT){
                    sawCold=true;
                    check(fx.smokeCount()==0 && fx.ignitionCount()==0,
                            "ejecting hardware must not draw a lit rocket engine");
                }
                if(OrdnancePropulsion.burning(m.thrustPhase))sawBurn=true;
            }
            if(fx.ignitionCount()>0)sawIgnition=true;
        }
        check(sawCold&&sawBurn&&sawIgnition,
                "cold launcher must transition from dark eject to visible ignition");
        Battle heavy=scenario(OrdnanceDoctrine.Mode.HEAVY_STRIKE);
        CombatLightingModel trail=new CombatLightingModel();
        Battle.Missile missile=null;
        for(int i=0;i<45 && missile==null;i++){
            heavy.step();trail.update(heavy);
            if(!heavy.missiles().isEmpty())missile=heavy.missiles().get(0);
        }
        check(missile!=null,"physical heavy-strike projectile launched");
        check(missile.propulsionMode==OrdnancePropulsion.Mode.BOOST_COAST_TERMINAL,
                "heavy-strike launch stores real propulsion mode");
        // Feed a controlled sequence of actual engine states to the render
        // timeline, independent of GPU and unrelated battle AI randomness.
        missile.thrustPhase=OrdnancePropulsion.Phase.COAST;
        int smokeBefore=trail.smokeCount();
        for(int i=0;i<8;i++){
            heavy.tick++;missile.x+=7;
            trail.update(heavy);
        }
        check(trail.smokeCount()<=smokeBefore,
                "silent coast cannot emit new exhaust particles");
        int ignitionBefore=trail.ignitionCount();
        missile.thrustPhase=OrdnancePropulsion.Phase.TERMINAL;
        heavy.tick++;trail.update(heavy);
        check(trail.ignitionCount()==ignitionBefore+1,
                "terminal motor restart creates precisely one ignition");
        boolean terminal=false;
        for(CombatLightingModel.Ignition ignition:trail.ignitions())
            if(ignition.terminal())terminal=true;
        check(terminal,"terminal ignition is visually distinguishable");
        int unchanged=trail.ignitionCount();
        trail.update(heavy);
        check(trail.ignitionCount()==unchanged,"two render frames cannot restart motor");
        for(int i=0;i<80;i++){heavy.tick++;trail.update(heavy);}
        check(trail.smokeCount()==0&&trail.ignitionCount()==0,
                "bounded effects expire regardless of frame rate");
        trail.update(scenario(OrdnanceDoctrine.Mode.SINGLE));
        check(trail.smokeCount()==0,"new battle resets retained trails");
        System.out.println("PASS cold-launch, coasting gaps, terminal restart and bounded FX timelines");
    }
}
