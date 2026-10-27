package diplomacy.sim;

import diplomacy.fx.WeaponFxProfile;
import diplomacy.fx.WeaponFxProfile.Clip;
import java.util.HashSet;
import java.util.Set;

/** Headless contract test: visual stages must never re-run weapon physics or invent impacts. */
public final class WeaponFxPipelineTest {
    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    private static Battle scenario(long seed,boolean missile){
        Battle battle=new Battle(seed);
        Battle.Ship attacker=battle.addShip(0,Battle.Faction.TERRAN,200,350);
        Battle.Ship defender=battle.addShip(1,Battle.Faction.CARNELIAN,490,350);
        attacker.speed=0;defender.speed=0;
        defender.laserDamage=0;defender.missileDamage=0;defender.pdRange=0;
        attacker.pdRange=0;
        attacker.laserDamage=missile?0:18;
        attacker.missileDamage=missile?42:0;
        attacker.missileAmmo=missile?1:0;
        attacker.accuracy=1.8;
        defender.maxHull=99999;defender.hull=99999;
        return battle;
    }
    private static String serialize(Battle battle){
        StringBuilder s=new StringBuilder();
        for(Battle.VisualEvent e:battle.visualEvents())
            s.append(e.sequence()).append('/').append(e.shotId()).append('/')
                    .append(e.tick()).append('/').append(e.phase()).append('/')
                    .append(e.weapon()).append('/').append(e.sourceId()).append('/')
                    .append(e.targetId()).append('/').append(e.x()).append('/').append(e.y())
                    .append(';');
        return s.toString();
    }
    public static void main(String[] args){
        Clip frames=WeaponFxProfile.forType(Battle.DamageType.LASER).firing();
        require(frames.frame(0)==0 && frames.frame(7)==7 && frames.frame(8)==-1,
                "firing frames must terminate, not loop");
        require(frames.path(0).equals("effects/weapon_fx/laser/firing_01.png"),
                "asset path should be relative to the existing runtime assets root");
        Clip payload=WeaponFxProfile.forType(Battle.DamageType.LASER).payload();
        require(payload.looping() && payload.frame(8)==1,"payload animation loops independently");
        require(WeaponFxProfile.forType(Battle.DamageType.MISSILE).motion()
                ==WeaponFxProfile.Motion.GUIDED,"missile payload follows physical ordnance");
        require(WeaponFxProfile.forWeapon("DRONE").firing().present(),"drone launch uses firing slot");
        require(WeaponFxProfile.forWeapon("UNKNOWN")==null,"unknown weapons cannot fabricate assets");

        Battle one=scenario(611,false);
        Battle copy=scenario(611,false);
        for(int i=0;i<150;i++){one.step();copy.step();}
        require(one.fingerprint()==copy.fingerprint(),"art events must not change deterministic physics");
        require(serialize(one).equals(serialize(copy)),"firing and impact events are deterministic");
        Set<Long> fireIds=new HashSet<>();
        boolean hit=false;
        long lastSequence=0;
        for(Battle.VisualEvent e:one.visualEvents()){
            require(e.sequence()>lastSequence,"monotonic notification sequence");
            lastSequence=e.sequence();
            if(e.phase()==Battle.VisualPhase.FIRE && e.weapon().equals("LASER"))
                fireIds.add(e.shotId());
            if(e.phase()==Battle.VisualPhase.IMPACT && e.weapon().equals("LASER")){
                require(fireIds.contains(e.shotId()),"impact must reference its earlier firing");
                require(e.x()==e.targetX()&&e.y()==e.targetY(),"impact anchored to collision target");
                hit=true;
            }
        }
        require(!fireIds.isEmpty() && hit,"actual hits should trigger distinct firing/impact phases");

        Battle ordnance=scenario(921,true);
        for(int i=0;i<210;i++)ordnance.step();
        long missileShot=0;
        boolean terminal=false;
        for(Battle.VisualEvent e:ordnance.visualEvents()){
            if(e.weapon().equals("MISSILE") && e.phase()==Battle.VisualPhase.FIRE){
                require(missileShot==0,"one missile ammo must yield one launch");
                missileShot=e.shotId();
            }
            if(e.weapon().equals("MISSILE") && e.phase()==Battle.VisualPhase.IMPACT){
                require(e.shotId()==missileShot,"impact correlation must survive missile travel");
                terminal=true;
            }
        }
        require(missileShot>0 && terminal,"missile should launch, travel, then impact");
        require(ordnance.missiles().isEmpty(),"projectile payload must disappear on impact");
        require(WeaponFxProfile.forType(Battle.DamageType.MISSILE).intercept().present(),
                "interceptions use distinct terminal art, never a fabricated hull impact");
        System.out.println("PASS modular VFX: per-shot origins, three separate clips, deterministic impacts and real moving ordnance");
    }
}
