package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.HashSet;
import java.util.Set;

/** Death events must be exact, fractal seams closed, bounded and simulation-neutral. */
public final class ShipDeathTimelineTest {
    static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static void near(float actual,float expected,String why){
        require(Math.abs(actual-expected)<.00001f,why+": "+actual+" / "+expected);
    }
    public static void main(String[] ignored){
        Battle battle=new Battle(4992L);
        Battle.Ship destroyed=battle.addShip(0,Battle.Faction.UZIPT,87,54);
        Battle.Ship survived=battle.addShip(1,Battle.Faction.TERRAN,700,55);
        ShipDeathTimeline fx=new ShipDeathTimeline();
        fx.beforeStep(battle);
        // Simulate the authoritative damage outcome: render code must only observe.
        destroyed.hull=0;destroyed.retired=true;
        battle.tick++;
        fx.afterStep(battle);
        require(fx.count()==1,"one shattered hull");
        var burst=fx.bursts().get(0);
        require(burst.ship.id()==destroyed.id,"correct dying vessel");
        require(burst.fragments.size()==8,"eight jagged hull shards");
        require(burst.smoke.size()==10,"ten sparse smoke puffs");
        require(burst.ship.faction()==Battle.Faction.UZIPT,"real faction retained");
        require(survived.alive(),"other team unaffected");
        require(destroyed.hull==0,"no resurrection or changed hit status");
        // Shared cut lines join at the exact same kink: no cracks in the
        // sprite before breakup or overlapping duplicated hull slices.
        for(int i=0;i<burst.fragments.size();i++){
            float[] cur=burst.fragments.get(i).vertices();
            float[] next=burst.fragments.get((i+1)%8).vertices();
            near(cur[0],next[0],"common center X");
            near(cur[1],next[1],"common center Y");
            near(cur[6],next[4],"shared rim X");
            near(cur[7],next[5],"shared rim Y");
            near(cur[8],next[2],"shared kink X");
            near(cur[9],next[3],"shared kink Y");
            for(float f:cur)require(f>=-.01&&f<=1.01,"valid texture UV");
        }
        ShipDeathTimeline repeat=new ShipDeathTimeline();
        var same=ShipDeathTimeline.create(burst.ship,burst.tick,burst.seed);
        for(int i=0;i<8;i++)near(same.fragments.get(i).spin(),
                burst.fragments.get(i).spin(),"stable seeded shrapnel");
        fx.beforeStep(battle);
        battle.tick++;
        fx.afterStep(battle);
        require(fx.count()==1,"no repeated death across subsequent ticks");
        for(int i=0;i<200;i++)fx.advance(battle,.06f,true);
        require(fx.count()==0,"bounded lifetime");
        // 200 vs 200 worst-case simultaneous kills stay within render budget.
        Battle massive=new Battle(899L);
        for(int i=0;i<400;i++)massive.addShip(i%2,Battle.Faction.DRAXIAN,i*4,i%2*10);
        fx.beforeStep(massive);
        for(Battle.Ship s:massive.ships()){s.hull=0;s.retired=true;}
        massive.tick++;
        fx.afterStep(massive);
        require(fx.count()==ShipDeathTimeline.MAX_ACTIVE,
                "bounded visual count even after 400 deaths");
        Set<Integer> unique=new HashSet<>();
        for(var effect:fx.bursts())require(unique.add(effect.ship.id()),"no duplicate death");
        Battle fresh=new Battle(900L);
        fx.sync(fresh);
        require(fx.count()==0,"new battle removes old wreckage");
        System.out.println("PASS: unique, seeded, closed fracture wedges, 400-loss cap, no physics mutation");
    }
}
