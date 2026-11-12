package diplomacy.client;
import diplomacy.sim.AmmunitionTable;
import java.util.HashSet;
/** Nonblocking source-sprite-to-physical-ammunition audit. */
public final class KineticPayloadVisualTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        var seen=new HashSet<Integer>();
        for(AmmunitionTable.Kinetic type:AmmunitionTable.Kinetic.values()){
            var appearance=KineticPayloadVisual.forType(type);
            check(seen.add(appearance.cell()),"Every fitted kinetic round needs a unique sprite");
            check(appearance.cell()>=0&&appearance.cell()<KineticPayloadVisual.CELLS,
                    "Kinetic sprite atlas index outside approved cuts");
            check(appearance.width()>0&&appearance.width()<50&&appearance.height()>0,
                    "Tiny shell sprites must not obscure ships");
            var fighter=KineticPayloadVisual.forLauncher(appearance,true);
            check(KineticPayloadVisual.forLauncher(appearance,false)==appearance &&
                    fighter.width()<appearance.width()*.11f &&
                    CombatEffectScale.of(fighter.width())<FighterCraftSprites.fighterHeight(
                            diplomacy.sim.FighterModel.Hull.INTERCEPTOR)/2f,
                    "Aircraft cannon shells must be sub-fighter sized");
        }
        check(CombatEffectScale.projectile(
                    KineticPayloadVisual.forType(AmmunitionTable.Kinetic.STANDARD).width())<3.8f &&
                CombatEffectScale.projectile(
                    KineticPayloadVisual.forType(AmmunitionTable.Kinetic.RELATIVISTIC).width())<5.3f,
                "Even heavy kinetic shells must remain small against a hull");
        check(seen.size()==5,"All five arsenal sprite cells must be used");
        System.out.println("PASS five real projectile sprites assigned to five kinetic ammunition types");
    }
}
