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
        require(fx.wreckCount()==0,"corvette must leave no long-lived wreck");
        require(ShipDeathTimeline.capitalHull(diplomacy.shipyard.Shipyard.Hull.CARRIER_COMMAND),
                "capital variants inherit the correct parent class");
        require(!ShipDeathTimeline.capitalHull(diplomacy.shipyard.Shipyard.Hull.DESTROYER),
                "destroyer should not clutter battles with permanent wreckage");
        var burst=fx.bursts().get(0);
        require(burst.ship.id()==destroyed.id,"correct dying vessel");
        require(burst.fragments.size()==8,"eight jagged hull shards");
        require(burst.smoke.size()==10,"ten sparse smoke puffs");
        require(burst.ship.faction()==Battle.Faction.UZIPT,"real faction retained");
        require(survived.alive(),"other team unaffected");
        // Lethal weapon families choose distinct presentation recipes, while
        // exact same physics, hull and death count remain authoritative.
        var snapshot=burst.ship;
        var leftHit=new ShipDeathTimeline.Impact(
                ShipDeathTimeline.Cause.KINETIC,snapshot.x()-25,snapshot.y());
        var rightHit=new ShipDeathTimeline.Impact(
                ShipDeathTimeline.Cause.KINETIC,snapshot.x()+25,snapshot.y());
        var fromLeft=ShipDeathTimeline.create(snapshot,burst.tick,burst.seed,leftHit);
        var fromRight=ShipDeathTimeline.create(snapshot,burst.tick,burst.seed,rightHit);
        require(fromLeft.directionX>.99f&&fromRight.directionX<-.99f,
                "opposite strike faces make opposite directional shatter");
        require(fromLeft.fragments.get(0).vx()>fromRight.fragments.get(0).vx(),
                "fracture inherits the impact direction");
        var laser=ShipDeathTimeline.create(snapshot,burst.tick,burst.seed,
                new ShipDeathTimeline.Impact(ShipDeathTimeline.Cause.LASER,
                        snapshot.x()-25,snapshot.y()));
        var plasma=ShipDeathTimeline.create(snapshot,burst.tick,burst.seed,
                new ShipDeathTimeline.Impact(ShipDeathTimeline.Cause.PLASMA,
                        snapshot.x()-25,snapshot.y()));
        var torpedo=ShipDeathTimeline.create(snapshot,burst.tick,burst.seed,
                new ShipDeathTimeline.Impact(ShipDeathTimeline.Cause.TORPEDO,
                        snapshot.x()-25,snapshot.y()));
        require(Math.abs(torpedo.fragments.get(0).spin())>
                Math.abs(laser.fragments.get(0).spin()),
                "torpedo fracture spins harder than laser splitting");
        require(plasma.smoke.get(0).radius()>laser.smoke.get(0).radius(),
                "plasma produces heavier residual smoke than a laser");
        require(ShipDeathTimeline.Cause.fromWeapon("TORPEDO")==
                ShipDeathTimeline.Cause.TORPEDO,"actual torpedo event maps correctly");
        require(ShipDeathTimeline.Cause.fromWeapon("ENVIRONMENT")==
                ShipDeathTimeline.Cause.OTHER,"unknown cause uses safe fallback");
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
        Set<Integer> uniqueAfterMassDeath=new HashSet<>();
        for(var effect:fx.bursts())
            require(uniqueAfterMassDeath.add(effect.ship.id()),"no duplicate death");
        require(fx.wreckCount()==0,
                "non-capital 400-kill stress must not fill the wreck queue");
        // Full capital-ship stress: make all cruisers and verify both queues
        // respect their independent maximums and timed eviction.
        Battle capitalBattle=new Battle(901L);
        for(int i=0;i<90;i++){
            Battle.Ship s=capitalBattle.addShip(i%2,Battle.Faction.CARNELIAN,
                    i*20,i%2*20);
            s.hullClass=diplomacy.shipyard.Shipyard.Hull.CARRIER;
        }
        fx.beforeStep(capitalBattle);
        for(Battle.Ship s:capitalBattle.ships()){s.hull=0;s.retired=true;}
        capitalBattle.tick++;
        fx.afterStep(capitalBattle);
        require(fx.wreckCount()==ShipDeathTimeline.MAX_WRECKS,
                "wreck count capped even after a capital-fleet catastrophe");
        require(fx.count()==ShipDeathTimeline.MAX_ACTIVE,
                "explosion count remains independently bounded");
        for(int i=0;i<75;i++)fx.advance(capitalBattle,.08f,true);
        require(fx.count()==0,"short blasts expire before wreck sections");
        require(fx.wreckCount()>0,"capital wrecks outlast smoke and flashes");
        for(int i=0;i<180;i++)fx.advance(capitalBattle,.08f,true);
        require(fx.wreckCount()==0,"long-lived wrecks eventually expire");
        require(fx.wreckCount()==0,"expired wrecks no longer retained");
        Battle fresh=new Battle(900L);
        fx.sync(fresh);
        require(fx.count()==0,"new battle removes old wreckage");
        System.out.println("PASS: unique, seeded, closed fracture wedges, 400-loss cap, no physics mutation");
    }
}
