package diplomacy.sim;

/** Historical seeker illumination must arrive before any guided lock update. */
public final class OrdnanceSeekerPhotonTest {
    private static void require(boolean ok,String description){
        if(!ok)throw new AssertionError(description);
    }
    private static void delayedPositionAndEcm(){
        Battle b=new Battle(461229);
        Battle.Ship observer=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.DRAXIAN,680,100);
        b.registerDiagnosticStationaryTarget(observer.id);
        b.registerDiagnosticStationaryTarget(hostile.id);
        hostile.ecmStrength=0;hostile.ecmCondition=1;
        int photons=SensorLightTimeHistory.propagationTicks(580);
        for(int frame=1;frame<photons;frame++){
            b.step();
            require(OrdnanceSeeker.passiveReturn(hostile,100,100,b.tick)==null,
                    "Seeker read the target before light could reach it");
        }
        b.step();
        OrdnanceSeeker.Return initial=OrdnanceSeeker.passiveReturn(
                hostile,100,100,b.tick);
        require(initial!=null&&initial.sample().x()==680&&
                initial.measuredTick()<initial.receivedTick()&&
                OrdnanceSeeker.canTrackReturn(initial,1,false,false),
                "Seeker did not receive real unobstructed delayed target photons");
        hostile.y=280;hostile.ecmStrength=1;
        b.step();
        OrdnanceSeeker.Return older=OrdnanceSeeker.passiveReturn(
                hostile,100,100,b.tick);
        require(older!=null&&older.sample().y()==100&&
                        OrdnanceSeeker.canTrackReturn(older,1,false,false),
                "Future movement or ECM rewrote an already travelling seeker fix");
        boolean changed=false;
        for(int i=0;i<18;i++){
            b.step();
            OrdnanceSeeker.Return arrived=OrdnanceSeeker.passiveReturn(
                    hostile,100,100,b.tick);
            if(arrived!=null&&arrived.sample().y()==280){
                changed=true;
                require(!OrdnanceSeeker.canTrackReturn(arrived,1,false,false),
                        "Historical powered ECM failed to suppress distant reacquisition");
                break;
            }
        }
        require(changed,"Later movement and ECM never propagated to onboard seeker");
        System.out.println("PASS seeker optical light-cone delay, historical movement and delayed ECM");
    }
    private static void movingReceiverWorldline(){
        Battle battle=new Battle(461234);
        Battle.Ship receiver=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship emitter=battle.addShip(1,Battle.Faction.DRAXIAN,750,100);
        battle.registerDiagnosticStationaryTarget(receiver.id);
        battle.registerDiagnosticStationaryTarget(emitter.id);
        // Four emitted/arriving ticks with stable historical source location.
        for(int i=0;i<4;i++)battle.step();
        int delay=SensorLightTimeHistory.propagationTicks(550);
        require(delay==4,"Fixture no longer spans exactly four light ticks");
        var approaching=OrdnanceSeeker.passiveReturn(emitter,
                200,100,250,100,battle.tick);
        require(approaching!=null&&approaching.measuredTick()==0,
                "Moving receiver missed first actual incoming historical wavefront");
        // A discontinuous/superluminal reposition is not permission to
        // rediscover a pulse which passed the old receiver last frame.
        var staticOracle=OrdnanceSeeker.passiveReturn(emitter,200,100,battle.tick);
        require(staticOracle!=null&&staticOracle.measuredTick()==0,
                "Static control receiver fixture did not observe the pulse");
        var alreadyPassed=OrdnanceSeeker.passiveReturn(emitter,
                700,100,200,100,battle.tick);
        require(alreadyPassed==null,
                "Moved receiver rediscovered previously passed historical photons");
        // Same visible target now, different physically sampled receiver
        // trajectory: the delayed photons must not magically be identical.
        require(OrdnanceSeeker.passiveReturn(emitter,
                200,100,250,100,battle.tick).equals(approaching),
                "Moving photon arrival was nondeterministic");
        System.out.println("PASS finite moving receiver light-front intersection, prior passage and replay");
    }
    private static void deterministicCausalReplay(){
        Battle a=new Battle(461231),b=new Battle(461231);
        for(Battle scene:new Battle[]{a,b}){
            Battle.Ship s=scene.addShip(0,Battle.Faction.TERRAN,100,100);
            Battle.Ship enemy=scene.addShip(1,Battle.Faction.DRAXIAN,520,100);
            scene.registerDiagnosticStationaryTarget(s.id);
            scene.registerDiagnosticStationaryTarget(enemy.id);
        }
        for(int i=0;i<30;i++){
            if(i==12){
                a.ships().get(1).y+=90;
                b.ships().get(1).y+=90;
            }
            a.step();b.step();
            var p=OrdnanceSeeker.passiveReturn(a.ships().get(1),100,100,a.tick);
            var q=OrdnanceSeeker.passiveReturn(b.ships().get(1),100,100,b.tick);
            require((p==null&&q==null)||(p!=null&&q!=null&&p.equals(q)),
                    "Same physical sensor history produced different seeker returns");
            require(a.fingerprint()==b.fingerprint(),
                    "Deterministic replay diverged after historical seeker sampling");
        }
        System.out.println("PASS deterministic seeker observations without current-target oracle");
    }
    private static void emittedHologramCannotChangeInFlight(){
        Battle b=new Battle(461232);
        Battle.Ship observer=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship emitter=b.addShip(1,Battle.Faction.DRAXIAN,760,100);
        b.registerDiagnosticStationaryTarget(observer.id);
        b.registerDiagnosticStationaryTarget(emitter.id);
        emitter.ecmCondition=1;emitter.hologramNoise=.75;
        emitter.power=500;emitter.maxPower=500;
        int light=SensorLightTimeHistory.propagationTicks(660);
        for(int i=0;i<light;i++)b.step();
        var first=OrdnanceSeeker.passiveReturn(emitter,100,100,b.tick);
        require(first!=null&&DeceptionProjection.falseContacts(first.profile())==3,
                "Powered hologram must be observable only after photons arrive");
        int missileId=0,phantom=-1;
        for(int i=1;i<=512;i++){
            int candidate=DeceptionProjection.seekerGhostIndex(
                    emitter.id,first.profile(),i,1,1);
            if(candidate>=0){missileId=i;phantom=candidate;break;}
        }
        require(missileId>0,"Historical powered emitter never generated a false contact");
        double oldX=DeceptionProjection.ghostX(emitter.id,first.sample(),
                emitter.visualScale(),phantom);
        emitter.y=430;emitter.power=0;
        b.step();
        var stillArriving=OrdnanceSeeker.passiveReturn(emitter,100,100,b.tick);
        require(stillArriving!=null&&
                DeceptionProjection.falseContacts(stillArriving.profile())==3,
                "Present-time emitter shutdown rewrote already emitted ghost history");
        require(DeceptionProjection.seekerGhostIndex(emitter.id,
                stillArriving.profile(),missileId,1,1)>=0,
                "Seeker phantom vanished before shutdown photons arrived");
        require(StrictMath.abs(oldX-
                DeceptionProjection.ghostX(emitter.id,first.sample(),
                        emitter.visualScale(),phantom))<1e-9,
                "Historical phantom position was retroactively modified");
        boolean arrived=false;
        for(int i=0;i<28;i++){
            b.step();
            var later=OrdnanceSeeker.passiveReturn(emitter,100,100,b.tick);
            if(later!=null&&later.sample().y()==430){
                require(DeceptionProjection.falseContacts(later.profile())==0,
                        "Historical inactive emitter still produced false contacts");
                require(DeceptionProjection.seekerGhostIndex(emitter.id,
                        later.profile(),missileId,1,1)==-1,
                        "Delayed shutdown was not applied to seeker spoof selection");
                arrived=true;break;
            }
        }
        require(arrived,"No delayed shutdown photons reached the observer");
        System.out.println("PASS historical hologram emission, movement and power-loss delay");
    }
    private static void boundedSeekerNoise(){
        Battle b=new Battle(461233);
        Battle.Ship scout=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,760,100);
        b.registerDiagnosticStationaryTarget(scout.id);
        b.registerDiagnosticStationaryTarget(target.id);
        target.ecmStrength=0;target.ecmCondition=1;target.hologramNoise=0;
        for(int i=0;i<8;i++)b.step();
        var received=OrdnanceSeeker.passiveReturn(target,100,100,b.tick);
        require(received!=null,"No photons for bounded seeker-noise regression");
        var nominal=OrdnanceSeeker.estimate(received,target.id,19,1,-1,target.visualScale());
        var replay=OrdnanceSeeker.estimate(received,target.id,19,1,-1,target.visualScale());
        require(nominal.equals(replay),"Identical seeker and photon history changed estimate");
        require(nominal.uncertaintyRadius()>0&&
                StrictMath.hypot(nominal.x()-received.sample().x(),
                        nominal.y()-received.sample().y())<=
                        nominal.uncertaintyRadius()+1e-8,
                "Seeker coordinate error was either perfect or unbounded");
        var highQuality=OrdnanceSeeker.estimate(received,target.id,19,2,-1,target.visualScale());
        require(highQuality.uncertaintyRadius()<nominal.uncertaintyRadius(),
                "Paid missile guidance failed to improve finite seeker resolution");
        target.ecmStrength=.6;
        b.step();
        var old=OrdnanceSeeker.passiveReturn(target,100,100,b.tick);
        require(old!=null&&old.profile().ecmStrength()==0,
                "Future ECM incorrectly influenced earlier arriving photons");
        var same=OrdnanceSeeker.estimate(old,target.id,19,1,-1,target.visualScale());
        require(same.uncertaintyRadius()==nominal.uncertaintyRadius(),
                "Future ECM retroactively increased old seeker uncertainty");
        boolean newEvidence=false;
        for(int i=0;i<18;i++){
            b.step();
            var later=OrdnanceSeeker.passiveReturn(target,100,100,b.tick);
            if(later!=null&&later.profile().ecmStrength()>.5){
                var degraded=OrdnanceSeeker.estimate(later,target.id,19,1,-1,target.visualScale());
                require(degraded.uncertaintyRadius()>nominal.uncertaintyRadius(),
                        "Observed ECM failed to increase finite seeker error");
                newEvidence=true;break;
            }
        }
        require(newEvidence,"Changed ECM never arrived at actual seeker");
        var distant=OrdnanceSeeker.estimate(
                new OrdnanceSeeker.Return(received.measuredTick(),
                        received.receivedTick(),received.distance()*1.5,
                        received.sample(),received.profile()),
                target.id,19,1,-1,target.visualScale());
        require(distant.uncertaintyRadius()>nominal.uncertaintyRadius(),
                "Distant target failed to increase seeker uncertainty");
        require(!OrdnanceSeeker.estimate(received,target.id,23,1,-1,
                        target.visualScale()).equals(nominal),
                "Distinct missile seekers incorrectly share identical noise");
        System.out.println("PASS bounded finite seeker noise, paid quality, photon-delayed ECM and deterministic replay");
    }
    public static void main(String[] args){
        delayedPositionAndEcm();emittedHologramCannotChangeInFlight();
        movingReceiverWorldline();
        boundedSeekerNoise();deterministicCausalReplay();
    }
}
