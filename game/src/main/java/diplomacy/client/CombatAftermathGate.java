package diplomacy.client;
import diplomacy.sim.Battle;

/** Delays ordinary battle results long enough to see the last ship break apart. */
final class CombatAftermathGate {
    static final float HOLD_SECONDS=2.8f;
    private Battle tracked;
    private float elapsed;
    boolean ready(Battle battle,float delta,boolean advancing){
        if(tracked!=battle){tracked=battle;elapsed=0;}
        if(battle==null||!battle.finished()){elapsed=0;return false;}
        if(advancing&&Float.isFinite(delta))
            elapsed+=Math.max(0,Math.min(.08f,delta));
        return elapsed>=HOLD_SECONDS;
    }
    float elapsed(){return elapsed;}
}
