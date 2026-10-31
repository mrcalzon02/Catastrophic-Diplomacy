package diplomacy.client;

import diplomacy.fx.WeaponFxProfile;
import diplomacy.sim.Battle;
import java.util.HashSet;

/** Visual diagnostic for real source FX clips; never a release gate. */
public final class WeaponStageArtTest {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        var seen=new HashSet<Integer>();
        for(Battle.DamageType type:new Battle.DamageType[]{
                Battle.DamageType.LASER,Battle.DamageType.KINETIC,
                Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE}){
            var weapon=WeaponFxProfile.forType(type);
            int fire=WeaponStageArt.row(weapon.firing());
            int hit=WeaponStageArt.row(weapon.impact());
            check(fire>=0&&fire<8&&hit>=0&&hit<8,
                    "Weapon must map both original firing and impact frames");
            check(seen.add(fire)&&seen.add(hit),
                    "Each authored weapon/phase combination needs distinct atlas row");
            for(int frame=0;frame<weapon.firing().frames();frame++)
                check(frame<WeaponStageArt.COLS,"Atlas missing an actual firing frame");
            for(int frame=0;frame<weapon.impact().frames();frame++)
                check(frame<WeaponStageArt.COLS,"Atlas missing an actual impact frame");
        }
        check(seen.size()==8,"All 64 real FX cells must have reachable physical type mapping");
        check(WeaponStageArt.row(WeaponFxProfile.forType(
                Battle.DamageType.MISSILE).firing())==-1,
                "Do not fake a missile launch using unrelated laser FX");
        System.out.println("PASS all authored laser/kinetic/plasma/particle muzzle and impact atlas rows");
    }
}
