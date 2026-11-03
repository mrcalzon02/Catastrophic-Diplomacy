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
    public static void main(String[] args){
        delayedPositionAndEcm();emittedHologramCannotChangeInFlight();
        deterministicCausalReplay();
    }
}
