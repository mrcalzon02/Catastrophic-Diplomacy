package diplomacy.client;
import diplomacy.sim.Battle;
public final class BattleRenderPosesTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void close(double a,double b,String m){check(Math.abs(a-b)<.002,m+" "+a+" / "+b);}
    public static void main(String[] ignored){
        Battle b=new Battle(7L);
        Battle.Ship ship=b.addShip(0,Battle.Faction.TERRAN,100,200);
        BattleRenderPoses p=new BattleRenderPoses();
        p.fraction(b,.5f);close(p.ship(ship).x(),100,"new ships start in place");
        p.beforeStep(b);ship.x=140;ship.y=220;ship.facing=(float)Math.toRadians(179);
        p.fraction(b,.5f);
        close(p.ship(ship).x(),120,"half tick x");
        close(p.ship(ship).y(),210,"half tick y");
        close(ship.x,140,"simulation state unchanged");
        p.fraction(b,0);close(p.ship(ship).x(),100,"step beginning");
        p.fraction(b,1);close(p.ship(ship).x(),140,"step end");
        p.beforeStep(b);ship.facing=(float)Math.toRadians(-179);
        p.fraction(b,.5f);check(Math.abs(p.ship(ship).angle())>3,"wrap uses short rotation");
        p.beforeStep(b);
        Battle.Ship newcomer=b.addShip(1,Battle.Faction.DRAXIAN,850,25);
        p.fraction(b,.2f);close(p.ship(newcomer).x(),850,"new entity has no ghost trail");
        ship.x=5000;close(p.ship(ship).x(),5000,"teleport snaps");
        Battle second=new Battle(7L);
        Battle.Ship recycled=second.addShip(0,Battle.Faction.TERRAN,-100,55);
        p.fraction(second,.2f);close(p.ship(recycled).x(),-100,"battle switch clears old ids");
        check(p.trackedShips()==0&&p.trackedRockets()==0,
                "old simulation poses must not leak to a new battle");
        p.beforeStep(second);recycled.x=-80;p.fraction(second,.5f);
        close(p.ship(recycled).x(),-90,"new battle interpolates");
        // A pause draws exact physics; resuming without a new tick must NOT
        // return to the previously cached pose and travel backwards.
        p.snap(second);
        p.fraction(second,.2f);
        close(p.ship(recycled).x(),-80,"resuming a frozen frame must not rewind");
        p.beforeStep(second);recycled.x=-60;p.fraction(second,.5f);
        close(p.ship(recycled).x(),-70,"post-resume movement interpolates");
        close(p.frame(second).centerX(),-70,"camera uses same interpolated hull center");
        close(diplomacy.sim.BattleView.of(second).centerX(),-60,
                "authoritative framing stays exact for headless consumers");
        // The actual catch-up loop can process up to 64 authoritative ticks
        // for one displayed frame. Only the final tick needs a pose snapshot.
        for(int requested:new int[]{1,2,19,80}){
            float accumulated=(float)(Battle.DT*requested);
            int steps=0,captures=0,lastCapture=0;
            while(accumulated>=Battle.DT&&steps++<64){
                if(BattleRenderPoses.captureBeforeLastStep(accumulated,steps,64)){
                    captures++;
                    lastCapture=steps;
                }
                accumulated-=Battle.DT;
            }
            int actualSteps=Math.min(steps,64);
            check(captures==1&&lastCapture==actualSteps,
                    "Multi-tick frame must capture only final step: requested="+requested+
                    " actual="+actualSteps+" captured="+lastCapture);
        }
        Battle multi=new Battle(9L);
        Battle.Ship moving=multi.addShip(0,Battle.Faction.TERRAN,100,200);
        float accumulated=(float)(Battle.DT*19);
        int advanced=0;
        while(accumulated>=Battle.DT&&advanced++<64){
            if(BattleRenderPoses.captureBeforeLastStep(accumulated,advanced,64))
                p.beforeStep(multi);
            moving.x+=10;
            accumulated-=Battle.DT;
        }
        p.fraction(multi,.5f);
        close(p.ship(moving).x(),100+10*(advanced-1)+5,
                "Final-step-only interpolation must preserve half-tick movement");
        System.out.println("PASS pose interpolation, freeze/resume, interpolated camera, catch-up snapshots, heading wrap, spawns and battle reset");
    }
}
