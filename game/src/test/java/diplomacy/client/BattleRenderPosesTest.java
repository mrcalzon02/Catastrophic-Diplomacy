package diplomacy.client;
import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import diplomacy.shipyard.Shipyard;
import java.util.Map;
public final class BattleRenderPosesTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void close(double a,double b,String m){check(Math.abs(a-b)<.002,m+" "+a+" / "+b);}
    static BattleConnection.ShipEntity remoteShip(int id,int team,Battle.Faction faction,
                                                   double x,double y,double facing){
        return new BattleConnection.ShipEntity(id,team,faction,Shipyard.Hull.CORVETTE,
                x,y,facing,100,100,10,10,10,10,10,10,0,Map.of());
    }

    /**
     * L03 viewport-level playback diagnostic: reuses the actual 30Hz
     * accumulator policy, fixed-step world, ship pose interpolation, auto-fit
     * framing and camera smoothing. Reports what an observer would see on a
     * 1280x720 tactical canvas at 30 display frames/second.
     *
     * This is neither a GPU screenshot nor a device FPS measurement. It does
     * not change campaign scale, physical movement, orders, or default pace.
     */
    private static void reportScreenMotion() throws Exception {
        var catalog=diplomacy.tech.TechnologyCatalog.loadDefault();
        for(int shipsPerSide:new int[]{16,200}){
            for(float displayedSpeed:new float[]{1f,2f}){
                Battle battle=shipsPerSide==200
                        ?diplomacy.sim.DemonstrationBattle.create(catalog)
                        :Battle.duel(20261127L,shipsPerSide,
                                Shipyard.starter(Battle.Faction.TERRAN,
                                        Shipyard.Hull.DREADNOUGHT),
                                Shipyard.starter(Battle.Faction.DRAXIAN,
                                        Shipyard.Hull.DREADNOUGHT),catalog);
                BattleRenderPoses poses=new BattleRenderPoses();
                int count=battle.ships().size();
                double[][] priorX=new double[2][count],priorY=new double[2][count];
                boolean[][] priorVisible=new boolean[2][count];
                double[] cameraX=new double[2],cameraY=new double[2];
                float[] cameraZoom=new float[2];
                double[] travelledPixels=new double[2],maxStepPixels=new double[2];
                double[] zoomSum=new double[2],cameraPath=new double[2];
                long[] comparedVisible=new long[2],stillVisible=new long[2];
                long[] visibleSamples=new long[2],eligibleSamples=new long[2];
                int frames=0,ticks=0;
                float accumulator=0;
                final float wallFrameSeconds=1f/30f;
                final int maximumFrames=600; // 20 displayed seconds at 30 FPS
                for(int frame=0;frame<maximumFrames&&!battle.finished();frame++){
                    accumulator+=wallFrameSeconds*
                            CombatPace.simulationMultiplier(displayedSpeed);
                    int steps=0;
                    while(accumulator>=Battle.DT&&steps++<64){
                        if(BattleRenderPoses.captureBeforeLastStep(
                                accumulator,steps,64))poses.beforeStep(battle);
                        battle.step();
                        accumulator-=Battle.DT;
                        ticks++;
                    }
                    poses.fraction(battle,(float)(accumulator/Battle.DT));
                    diplomacy.sim.BattleView.Frame fit=poses.frame(battle);
                    for(int mode=0;mode<2;mode++){
                        // 0: existing whole-fleet auto-fit.
                        // 1: same moving center with a 50% manual zoom factor.
                        float wantedZoom=(float)Math.max(.16,Math.min(8,
                                fit.zoom()*(mode==0?1.0:.5)));
                        float oldX=(float)cameraX[mode],oldY=(float)cameraY[mode];
                        if(frame==0){
                            cameraX[mode]=fit.centerX();
                            cameraY[mode]=fit.centerY();
                            cameraZoom[mode]=wantedZoom;
                        }else{
                            cameraX[mode]=CombatCameraSmoothing.toward(
                                    oldX,(float)fit.centerX(),wallFrameSeconds);
                            cameraY[mode]=CombatCameraSmoothing.toward(
                                    oldY,(float)fit.centerY(),wallFrameSeconds);
                            cameraZoom[mode]=CombatCameraSmoothing.toward(
                                    cameraZoom[mode],wantedZoom,wallFrameSeconds);
                            cameraPath[mode]+=Math.hypot(
                                    cameraX[mode]-oldX,cameraY[mode]-oldY);
                        }
                        zoomSum[mode]+=cameraZoom[mode];
                        for(int i=0;i<count;i++){
                            Battle.Ship ship=battle.ships().get(i);
                            if(!ship.alive()||
                                    diplomacy.sim.BattleOrders.size(ship)!=
                                    diplomacy.sim.BattleOrders.Size.CAPITAL){
                                priorVisible[mode][i]=false;
                                continue;
                            }
                            eligibleSamples[mode]++;
                            BattleRenderPoses.Pose shown=poses.ship(ship);
                            double sx=640+(shown.x()-cameraX[mode])/cameraZoom[mode];
                            double sy=360+(shown.y()-cameraY[mode])/cameraZoom[mode];
                            // Shared HUD excludes the top 71 and bottom 50 px.
                            boolean visible=sx>=0&&sx<=1280&&sy>=50&&sy<=649;
                            if(visible)visibleSamples[mode]++;
                            if(visible&&priorVisible[mode][i]){
                                double step=Math.hypot(sx-priorX[mode][i],
                                        sy-priorY[mode][i]);
                                check(Double.isFinite(step),
                                        "Nonfinite screen-space ship motion");
                                travelledPixels[mode]+=step;
                                maxStepPixels[mode]=Math.max(
                                        maxStepPixels[mode],step);
                                comparedVisible[mode]++;
                                if(step<.20)stillVisible[mode]++;
                            }
                            priorX[mode][i]=sx;
                            priorY[mode][i]=sy;
                            priorVisible[mode][i]=visible;
                        }
                    }
                    frames++;
                }
                check(ticks==battle.tick,"Viewport diagnostic changed tick ownership");
                check(frames>0&&ticks>0,"Empty playback trace");
                for(int mode=0;mode<2;mode++){
                    check(comparedVisible[mode]>0&&
                            Double.isFinite(travelledPixels[mode]),
                            "No visible comparable capital motion");
                    double pxPerWallSecond=travelledPixels[mode]*
                            30.0/comparedVisible[mode];
                    double stillPercent=stillVisible[mode]*100.0/
                            comparedVisible[mode];
                    double visibility=eligibleSamples[mode]==0?0:
                            visibleSamples[mode]*100.0/eligibleSamples[mode];
                    System.out.printf(java.util.Locale.ROOT,
                            "CD-L03-SCREEN perSide=%d seed=%d pace=%.1f "+
                            "zoomMode=%s wallFrames=%d simulatedTicks=%d "+
                            "meanCameraZoom=%.4f visibleCapitalPct=%.2f "+
                            "meanVisibleCapitalPixelsPerWallSecond=%.4f "+
                            "stillFramesUnder0.2pxPct=%.2f maxStepPixels=%.4f "+
                            "cameraPathWorld=%.3f synthetic30fps=true "+
                            "renderedGpu=false diagnosticOnly=true%n",
                            shipsPerSide,shipsPerSide==200
                                    ?diplomacy.sim.DemonstrationBattle.SEED
                                    :20261127L,displayedSpeed,
                            mode==0?"AUTO_FIT":"ZOOM_50_PERCENT",
                            frames,ticks,zoomSum[mode]/frames,visibility,
                            pxPerWallSecond,stillPercent,
                            maxStepPixels[mode],cameraPath[mode]);
                }
            }
        }
    }

    public static void main(String[] ignored) throws Exception{
        Battle b=new Battle(7L);
        Battle.Ship ship=b.addShip(0,Battle.Faction.TERRAN,100,200);
        BattleRenderPoses p=new BattleRenderPoses();
        p.fraction(b,.5f);close(p.ship(ship).x(),100,"new ships start in place");
        p.beforeStep(b);ship.x=140;ship.y=220;ship.facing=(float)Math.toRadians(179);
        p.fraction(b,.5f);
        close(p.ship(ship).x(),120,"half tick x");
        close(p.ship(ship).y(),210,"half tick y");
        BattleRenderPoses.Pose shared=p.ship(ship);
        check(shared==p.ship(ship),
                "Camera, hull and turret passes must share one ship pose per frame");
        close(ship.x,140,"simulation state unchanged");
        p.fraction(b,0);close(p.ship(ship).x(),100,"step beginning");
        check(p.ship(ship)!=shared,
                "New presentation fraction reused a stale ship pose");
        p.fraction(b,1);close(p.ship(ship).x(),140,"step end");
        p.beforeStep(b);ship.facing=(float)Math.toRadians(-179);
        p.fraction(b,.5f);check(Math.abs(p.ship(ship).angle())>3,"wrap uses short rotation");
        p.beforeStep(b);
        Battle.Ship newcomer=b.addShip(1,Battle.Faction.DRAXIAN,850,25);
        p.fraction(b,.2f);close(p.ship(newcomer).x(),850,"new entity has no ghost trail");
        BattleRenderPoses.Pose cachedBeforeRelocation=p.ship(ship);
        ship.x=5000;close(p.ship(ship).x(),5000,"teleport snaps");
        check(p.ship(ship)!=cachedBeforeRelocation,
                "Real physical change within one frame reused stale cached pose");
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
        var a0=remoteShip(11,0,Battle.Faction.TERRAN,0,5,3.124139);
        var a3=remoteShip(11,0,Battle.Faction.TERRAN,30,5,-3.124139);
        remote.observe(0,java.util.List.of(a0));
        close(remote.pose(11).x(),0,"first remote snapshot snaps");
        remote.observe(3,java.util.List.of(a3));
        close(remote.pose(11).x(),0,"new network snapshot must not teleport");
        remote.advance(.05f);
        close(remote.pose(11).x(),15,"network midpoint at 10Hz");
        check(Math.abs(remote.pose(11).facing())>3.12,
                "Remote heading must use shortest angle through 180 degrees");
        remote.observe(6,java.util.List.of(
                remoteShip(22,1,Battle.Faction.DRAXIAN,400,20,0),
                remoteShip(11,0,Battle.Faction.TERRAN,60,5,0)));
        close(remote.pose(11).x(),15,"reordered snapshot continuity");
        close(remote.pose(22).x(),400,"new ship spawns in correct host location");
        remote.advance(.05f);
        close(remote.pose(11).x(),37.5,"remote second midpoint");
        RemoteFleetMirrors mirrors=new RemoteFleetMirrors();
        Battle firstHostView=mirrors.present(remote,false);
        Battle.Ship hosted11=mirrors.byHostId(11);
        Battle.Ship hosted22=mirrors.byHostId(22);
        check(hosted11!=null&&hosted22!=null&&
                hosted11.team==0&&hosted22.team==1&&
                hosted11.faction==Battle.Faction.TERRAN&&
                hosted22.faction==Battle.Faction.DRAXIAN,
                "Network mirror must respect authoritative team/faction by host ID");
        close(hosted11.x,37.5,"Network visual mirror must use interpolated position");
        // Repeating the same host tick must NOT rebuild source objects or
        // reparse the entire 400-ship wire data every single graphics frame.
        check(mirrors.present(remote,false)==firstHostView&&
                mirrors.byHostId(11)==hosted11,
                "Network mirror was rebuilt for an unchanged host snapshot");
        remote.advance(.05f);
        close(remote.pose(11).x(),60,"remote endpoint");
        remote.observe(6,java.util.List.of(a0));
        close(remote.pose(11).x(),60,"duplicate server tick cannot rewind");
        remote.observe(2,java.util.List.of(
                remoteShip(11,0,Battle.Faction.TERRAN,7,5,0)));
        close(remote.pose(11).x(),7,"session restart resets network tracking");
        check(remote.tracked()==1,"old ship tracks leaked across restarted session");
        mirrors.present(remote,true);
        Battle.Ship restarted11=mirrors.byHostId(11);
        check(restarted11!=hosted11&&mirrors.byHostId(22)==null,
                "New network session must rebuild the previous sprite identities");
        remote.observe(9,java.util.List.of(
                remoteShip(11,0,Battle.Faction.TERRAN,72,5,0),
                remoteShip(22,1,Battle.Faction.DRAXIAN,420,20,0)));
        mirrors.present(remote,false);
        check(mirrors.byHostId(11)==restarted11&&mirrors.byHostId(22)!=null,
                "Roster update altered surviving host ship identity");
        check(remote.ships().size()==2&&remote.ships().get(0).id()==11,
                "Parsed host ship roster not refreshed on new tick");
        // A short-lived real rail/kinetic shot can launch and impact inside
        // one 30Hz step: the live projectile list will never expose it.
        // Drive the visual path only from authoritative paired event positions.
        ShortProjectileFlight shortLife=new ShortProjectileFlight();
        shortLife.beginTick(40);
        shortLife.consume(new Battle.VisualEvent(1,9001,40,
                Battle.VisualPhase.FIRE,"RAIL_PD",0,1,2,
                100,200,250,200));
        shortLife.consume(new Battle.VisualEvent(2,9001,40,
                Battle.VisualPhase.IMPACT,"RAIL_PD",0,1,2,
                140,200,140,200));
        check(shortLife.flights().size()==1,"Same-tick real flight missing");
        ShortProjectileFlight.Path segment=shortLife.flights().get(0);
        close(ShortProjectileFlight.at(segment,0).x(),100,"real muzzle");
        close(ShortProjectileFlight.at(segment,.5f).x(),120,"real midpoint");
        close(ShortProjectileFlight.at(segment,1).x(),140,"real impact position");
        check(shortLife.has(9001,40),"Real terminal event not paired");
        shortLife.consume(new Battle.VisualEvent(3,9002,40,
                Battle.VisualPhase.IMPACT,"KINETIC",0,1,2,
                140,200,140,200));
        check(shortLife.flights().size()==1,
                "Orphan impact fabricated a moving projectile");
        shortLife.beginTick(41);
        check(shortLife.flights().isEmpty()&&!shortLife.has(9001,40),
                "Old tick projectile trail leaked into the next tick");
        // Visual history must not incur O(8192) traversal on every GL frame
        // when the world is running at only 1.5-3 physics ticks/real second.
        java.util.List<Battle.VisualEvent> history=new java.util.ArrayList<>();
        for(int i=0;i<8192;i++)
            history.add(new Battle.VisualEvent(i+100,9000+i,i,
                    Battle.VisualPhase.FIRE,"KINETIC",0,1,2,
                    100,200,150,200));
        check(CombatEventCursor.firstUnseen(history,99)==0,
                "First available history entry was skipped");
        check(CombatEventCursor.firstUnseen(history,101)==2,
                "Existing history entry was re-consumed");
        check(CombatEventCursor.firstUnseen(history,4195)==4096,
                "Event cursor cannot index mid-history");
        check(CombatEventCursor.firstUnseen(history,8291)==8192,
                "Repeated display frame re-scanned prior events");
        check(CombatEventCursor.firstUnseen(history,10)==0,
                "Pruned old event history must start at retained head");
        check(CombatEventCursor.firstUnseen(java.util.List.of(),0)==0,
                "Empty combat event history must be legal");
        // One tick at 0.5x can span ~40 rendered frames at 60 FPS.
        // Active authored clips must be checked once, not once per frame;
        // a same-tick event arriving after a prior render still gets seen.
        check(CombatEventCursor.needsRefresh(Long.MIN_VALUE,8291,20,history),
                "Fresh VFX renderer must process the first tick");
        for(int frame=0;frame<40;frame++)
            check(!CombatEventCursor.needsRefresh(20,8291,20,history),
                    "Unchanged render frame redundantly refreshed effects");
        check(CombatEventCursor.needsRefresh(20,8291,21,history),
                "New physics tick must advance animation expiry");
        java.util.List<Battle.VisualEvent> laterEvents=new java.util.ArrayList<>(history);
        laterEvents.add(new Battle.VisualEvent(8292,20000,20,Battle.VisualPhase.FIRE,
                "KINETIC",0,1,2,200,300,240,300));
        check(CombatEventCursor.needsRefresh(20,8291,20,laterEvents) &&
                CombatEventCursor.firstUnseen(laterEvents,8291)==8192,
                "Late event in the same simulation tick was skipped");
        reportScreenMotion();
        System.out.println("PASS low-speed muzzle, network tween, real projectile replay, event history cursor and camera easing");
    }
}
