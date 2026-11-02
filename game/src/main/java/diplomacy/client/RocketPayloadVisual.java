package diplomacy.client;

import diplomacy.sim.Battle;

/** Reuses archived, approved missile-body atlas cells for a distinct
 * UNGUIDED rocket effect. There is no extra sprite texture per rocket.
 * All movement and burn states come from the real projectile simulation. */
final class RocketPayloadVisual {
    private RocketPayloadVisual(){}
    record Appearance(int cell,float width,float height,
                      float red,float green,float blue){ }
    static Appearance forRocket(Battle.Rocket rocket){
        if(rocket==null)throw new IllegalArgumentException("Rocket absent");
        // Archived atlas sources: armor-piercing narrow body (cell 2),
        // explosive metal body (cell 5), heavy body (cell 1), siege (cell 4).
        // They are shared texture cuts, not invented cylindrical silhouettes.
        int cell=switch(rocket.size){
            case SMALL -> 2;
            case MEDIUM -> 5;
            case LARGE -> 1;
            case SIEGE -> 4;
        };
        float scale=switch(rocket.size){
            case SMALL -> .48f;
            case MEDIUM -> .72f;
            case LARGE -> 1.04f;
            case SIEGE -> 1.35f;
        };
        float tint=rocket.burning()?1f:.75f;
        return new Appearance(cell,36f*scale,25f*scale,
                tint,tint*.92f,tint*.80f);
    }
}
