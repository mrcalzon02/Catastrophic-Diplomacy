package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.List;

/** Actual shield/armor/hull losses produce local effects; harmless shots do not. */
public final class CombatHitFeedbackTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static Battle.VisualEvent hit(long seq,long tick,int target,double x,double y){
        return new Battle.VisualEvent(seq,seq,tick,Battle.VisualPhase.IMPACT,
                "KINETIC",1,99,target,x,y,0,0);
    }
    public static void main(String[] args){
        Battle battle=new Battle(8L);
        Battle.Ship ship=battle.addShip(0,Battle.Faction.CARNELIAN,100,200);
        CombatHitFeedback feedback=new CombatHitFeedback();
        feedback.beforeStep(battle);
        ship.shields-=15;
        battle.tick++;
        feedback.afterStep(battle,List.of(hit(1,battle.tick,ship.id,125,200)));
        check(feedback.count()==1,"actual shield loss yields a pulse");
        var flash=feedback.pulses().get(0);
        check(flash.layer==CombatHitFeedback.Layer.SHIELD,
                "shield impact matches protection layer");
        check(flash.normalX>.99f&&Math.abs(flash.normalY)<.01f,
                "impact located on correct side of ship");
        feedback.beforeStep(battle);
        ship.armor-=8;
        battle.tick++;
        feedback.afterStep(battle,List.of(hit(2,battle.tick,ship.id,75,200)));
        check(feedback.pulses().get(1).layer==CombatHitFeedback.Layer.ARMOR,
                "armor loss creates local plating sparks");
        feedback.beforeStep(battle);
        ship.hull-=5;
        battle.tick++;
        feedback.afterStep(battle,List.of(hit(3,battle.tick,ship.id,100,195)));
        check(feedback.pulses().get(2).layer==CombatHitFeedback.Layer.HULL,
                "penetration yields hull sparks");
        feedback.beforeStep(battle);
        battle.tick++;
        feedback.afterStep(battle,List.of(hit(4,battle.tick,ship.id,105,200)));
        check(feedback.count()==3,"zero damage does not invent a shield hit");
        feedback.afterStep(battle,List.of(hit(4,battle.tick,ship.id,105,200)));
        check(feedback.count()==3,"duplicate event sequence ignored");
        double originalHull=ship.hull;
        for(int i=0;i<8;i++)feedback.advance(battle,.08f,false);
        check(feedback.count()==3,"pausing holds effects");
        for(int i=0;i<8;i++)feedback.advance(battle,.08f,true);
        check(feedback.count()==0,"feedback expires in bounded time");
        check(ship.hull==originalHull,"presentation does not modify damage");
        var mixed=new CombatHitFeedback.Defense(100,100,100,0,0,0,1);
        check(CombatHitFeedback.changedLayer(mixed,90,95,97)==CombatHitFeedback.Layer.HULL,
                "Simultaneous shield/armor/hull loss must show actual penetration");
        check(CombatHitFeedback.changedLayer(mixed,90,95,100)==CombatHitFeedback.Layer.ARMOR,
                "Shield and armor loss must prefer the damaged armor layer");
        check(CombatHitFeedback.changedLayer(mixed,90,100,100)==CombatHitFeedback.Layer.SHIELD,
                "Shield-only loss must remain shield-only");
        check(CombatHitFeedback.changedLayer(mixed,100,100,100)==null,
                "Non-damaging impact must not invent hull smoke or breach glow");
        Battle mass=new Battle(17L);
        for(int i=0;i<400;i++)mass.addShip(i%2,Battle.Faction.TERRAN,
                20*i,10);
        feedback.beforeStep(mass);
        List<Battle.VisualEvent> events=new ArrayList<>();
        mass.tick++;
        for(int i=0;i<400;i++){
            Battle.Ship s=mass.ships().get(i);s.armor-=1;
            events.add(hit(i+1,mass.tick,s.id,s.x+4,s.y));
        }
        feedback.afterStep(mass,events);
        check(feedback.count()==CombatHitFeedback.MAX_PULSES,
                "200v200 damage effects remain capped");
        feedback.sync(battle);
        check(feedback.count()==0,"new battle removes stale hit feedback");
        System.out.println("PASS shield/armor/hull hit layers, location, no phantom hits, 400-ship cap");
    }
}
