package diplomacy.client;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.Battle;
/** User-original arsenal cut mapping; shell art never changes hitboxes. */
final class KineticPayloadVisual {
    static final String CURATED="effects/projectile_fx/curated/kinetic_bodies_5.png";
    static final int CELLS=5,WIDTH=64,HEIGHT=40;
    record Appearance(int cell,float width,float height){}
    private KineticPayloadVisual(){}
    static Appearance forBallistic(Battle.Ballistic shell){
        return forLauncher(forType(shell.ammunition),shell.fighterLaunched);
    }
    static Appearance forLauncher(Appearance normal,boolean fighterLaunched){
        if(!fighterLaunched)return normal;
        return new Appearance(normal.cell(),
                normal.width()*CombatEffectScale.FIGHTER_KINETIC,
                normal.height()*CombatEffectScale.FIGHTER_KINETIC);
    }
    static Appearance forType(AmmunitionTable.Kinetic ammo){
        if(ammo==null)throw new IllegalArgumentException("Missing physical ammunition");
        return switch(ammo){
            case STANDARD -> new Appearance(0,25f,13f);
            case ARMOR_PIERCING -> new Appearance(1,27f,12f);
            case FRAGMENTATION -> new Appearance(2,26f,13f);
            case HIGH_EXPLOSIVE -> new Appearance(3,31f,15f);
            case RELATIVISTIC -> new Appearance(4,35f,14f);
        };
    }
}
