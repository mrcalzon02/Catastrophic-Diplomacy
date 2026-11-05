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
        // Newly created PD rounds have no prior physics sample: presentation
        // must begin at their real physical muzzle, with no phantom motion.
        Battle.FlakRound freshFlak=new Battle.FlakRound(0,moving.id,0,
                501,0,100,200,150,200,2,3);
        diplomacy.sim.RotaryPointDefense.Round freshRotary=
                new diplomacy.sim.RotaryPointDefense.Round(
                        502,0,moving.id,0,0,100,200,0,3,500);
        diplomacy.sim.RailPointDefense.Penetrator freshRail=
                new diplomacy.sim.RailPointDefense.Penetrator(
                        503,0,0,moving.id,0,100,200,0,500,3,101,3);
        p.fraction(multi,.5f);
        close(p.flak(freshFlak).x(),100,"flak muzzle on spawn");
        close(p.rotary(freshRotary).x(),100,"rotary muzzle on spawn");
        close(p.rail(freshRail).x(),100,"rail muzzle on spawn");
        // Simulate a real launch after beforeStep: the round travels within
        // its birth tick and otherwise jumps from the muzzle to post-tick x.
        Battle birth=new Battle(407L);
        Battle.Ship source=birth.addShip(0,Battle.Faction.TERRAN,100,200);
        BattleRenderPoses present=new BattleRenderPoses();
        present.beforeStep(birth);
        birth.tick++;
        Battle.FlakRound flak=new Battle.FlakRound(0,source.id,0,701,birth.tick,
                100,200,340,200,2,3);
        flak.advance(Battle.DT);
        diplomacy.sim.RotaryPointDefense.Round rotary=
                new diplomacy.sim.RotaryPointDefense.Round(
                        702,birth.tick,source.id,0,0,100,200,0,3,500);
        rotary.x+=rotary.vx*Battle.DT;
        diplomacy.sim.RailPointDefense.Penetrator rail=
                new diplomacy.sim.RailPointDefense.Penetrator(
                        703,birth.tick,0,source.id,0,100,200,0,500,3,101,3);
        rail.x+=rail.vx*Battle.DT;
        present.fraction(birth,0);
        close(present.flak(flak).x(),100,"flak leaves real muzzle");
        close(present.rotary(rotary).x(),100,"rotary leaves real muzzle");
        close(present.rail(rail).x(),100,"rail leaves real muzzle");
        present.fraction(birth,.5f);
        close(present.flak(flak).x(),(100+flak.x)*.5,"flak birth half tick");
        close(present.rotary(rotary).x(),(100+rotary.x)*.5,"rotary birth half tick");
        close(present.rail(rail).x(),(100+rail.x)*.5,"rail birth half tick");
        present.snap(birth);
        present.fraction(birth,0);
        close(present.rail(rail).x(),rail.x,"pause never rewinds launch");
        // A real acceleration change has continuous render-frame positions,
        // while frame-rate-independent camera easing avoids death/reframe snaps.
        Battle smooth=new Battle(81L);
        Battle.Ship turning=smooth.addShip(0,Battle.Faction.TERRAN,100,200);
        BattleRenderPoses easing=new BattleRenderPoses();
        easing.beforeStep(smooth);
        turning.vx=60;
        turning.x+=60*Battle.DT;
        smooth.tick++;
        easing.fraction(smooth,.25f);
        double early=easing.ship(turning).x();
        easing.fraction(smooth,.75f);
        double late=easing.ship(turning).x();
        check(early>=100&&late>early&&late<=turning.x,
                "Accelerating ship must advance monotonically across rendered frames");
        float camera=100;
        for(int i=0;i<30;i++){
            float next=CombatCameraSmoothing.toward(camera,400,1f/30f);
            check(next>camera&&next<=400,"Camera must approach without teleporting");
            camera=next;
        }
        check(camera>399&&camera<400.01,
                "Camera must almost settle at target within a real second");
        close(CombatCameraSmoothing.toward(100,400,0),400,
                "Manual camera reframe must remain immediate");
        // Host snapshots arrive at ~10Hz, but real render frames arrive
        // much more often. Presentation must not jump on receipt, reorder
        // ships by index or change authoritative tick positions.
        RemoteShipInterpolation remote=new RemoteShipInterpolation();
        String a0="SHIP|11|0|TERRAN|0|5|3.124139|100|10|10|10|10";
        String a3="SHIP|11|0|TERRAN|30|5|-3.124139|100|10|10|10|10";
        remote.observe(0,java.util.List.of(a0));
        close(remote.pose(11).x(),0,"first remote snapshot snaps");
        remote.observe(3,java.util.List.of(a3));
        close(remote.pose(11).x(),0,"new network snapshot must not teleport");
        remote.advance(.05f);
        close(remote.pose(11).x(),15,"network midpoint at 10Hz");
        check(Math.abs(remote.pose(11).facing())>3.12,
                "Remote heading must use shortest angle through 180 degrees");
        remote.observe(6,java.util.List.of(
                "SHIP|22|1|DRAXIAN|400|20|0|100|10|10|10|10",
                "SHIP|11|0|TERRAN|60|5|0|100|10|10|10|10"));
        close(remote.pose(11).x(),15,"reordered snapshot continuity");
        close(remote.pose(22).x(),400,"new ship spawns in correct host location");
        remote.advance(.05f);
        close(remote.pose(11).x(),37.5,"remote second midpoint");
        remote.advance(.05f);
        close(remote.pose(11).x(),60,"remote endpoint");
        remote.observe(6,java.util.List.of(a0));
        close(remote.pose(11).x(),60,"duplicate server tick cannot rewind");
        remote.observe(2,java.util.List.of(
                "SHIP|11|0|TERRAN|7|5|0|100|10|10|10|10"));
        close(remote.pose(11).x(),7,"session restart resets network tracking");
        check(remote.tracked()==1,"old ship tracks leaked across restarted session");
        System.out.println("PASS low-speed muzzle, Hermite ship continuity, camera easing, network 10Hz tween, pause/resume and catch-up");
    }
}
