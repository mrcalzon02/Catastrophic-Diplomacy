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
        for(int frame=0;frame<photons;frame++){
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
    public static void main(String[] args){
        delayedPositionAndEcm();deterministicCausalReplay();
    }
}
