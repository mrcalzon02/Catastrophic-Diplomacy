package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Real outbound radar photons, reflected chaff and live fighter missiles. */
public final class FighterRadarSeekerTest {
    private FighterRadarSeekerTest(){}
    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static int ftrSocket(Shipyard.Hull hull){
        for(var slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT)return slot.index();
        throw new AssertionError("FTR socket missing");
    }
    private static void photonChronology(){
        Battle fixture=new Battle(815001);
        Battle.Ship carrier=fixture.addShip(0,Battle.Faction.TERRAN,200,200);
        carrier.fighterProfile=FighterModel.standard(FighterModel.Hull.INTERCEPTOR);
        Battle.Craft fighter=new Battle.Craft(20,carrier,true,0);
        fighter.x=250;fighter.y=100;
        for(int t=1;t<=5;t++)fighter.infraredHistory.capture(t,fighter);
        var pulses=new FighterRadarSeeker.PulseHistory();
        var narrow=FighterRadarSeeker.Head.NARROW_BEAM;
        var wide=FighterRadarSeeker.Head.WIDE_AREA;
        require(FighterRadarSeeker.installed(5)==narrow&&
                FighterRadarSeeker.installed(9)==wide&&
                narrow.range>wide.range&&
                wide.halfAngleRadians>narrow.halfAngleRadians,
                "Research fitted identical narrow and wide radar sensors");
        boolean denied=false;
        try{FighterRadarSeeker.installed(4);}
        catch(IllegalArgumentException expected){denied=true;}
        require(denied,"Active radar requires real tier-five fitting");
        require(echo(narrow,fighter,pulses,List.of(),4)==null,
                "Radar acquired fighter without a physical transmission");
        pulses.emit(0,0,100,250,0);
        require(echo(narrow,fighter,pulses,List.of(),3)==null,
                "Radar returned before outbound and inbound photon travel");
        var real=echo(narrow,fighter,pulses,List.of(),4);
        require(real!=null&&!real.decoy()&&real.transmittedTick()==0&&
                real.reflectedTick()==2&&real.receivedTick()==4&&real.x()==250,
                "Radar echo failed three-stage historical chronology");
        require(echo(wide,fighter,pulses,List.of(),4)==null,
                "Wide-area radar borrowed narrow-band acquisition range");
        var chaff=new FighterCountermeasures.Cloud(
                991,FighterCountermeasures.Kind.CHAFF,fighter.id,
                carrier.id,carrier.team,235,108,0,0,0,66,12);
        var flare=new FighterCountermeasures.Cloud(
                992,FighterCountermeasures.Kind.FLARE,fighter.id,
                carrier.id,carrier.team,235,108,0,0,0,48,50);
        var fooled=echo(narrow,fighter,pulses,List.of(chaff,flare),4);
        require(fooled!=null&&fooled.decoy()&&fooled.chaffId()==chaff.id(),
                "Real radar chaff did not compete with historical fighter echo");
        require(!echo(narrow,fighter,pulses,List.of(flare),4).decoy(),
                "Infrared flare fabricated an active radar reflection");
        require(echo(narrow,fighter,pulses,List.of(chaff),5)==null,
                "Already-received radar echo was recreated a tick later");
        var other=new FighterCountermeasures.Cloud(
                993,FighterCountermeasures.Kind.CHAFF,31,
                carrier.id,carrier.team,235,108,0,0,0,66,60);
        require(!echo(narrow,fighter,pulses,List.of(other),4).decoy(),
                "Chaff from a different fighter was accepted globally");
        System.out.println("PASS causal active radar transmission/return, geometry, chaff vs flare bands");
    }
    private static FighterRadarSeeker.Echo echo(
            FighterRadarSeeker.Head head,Battle.Craft craft,
            FighterRadarSeeker.PulseHistory pulses,
            List<FighterCountermeasures.Cloud> clouds,long now){
        return FighterRadarSeeker.receive(head,craft.infraredHistory,
                craft.profile.crossSection(),clouds,pulses,craft.id,1,
                0,100,0,100,250,0,now);
    }
    private static Battle fitted(TechnologyCatalog tech,long seed,boolean radar){
        var hull=Shipyard.Hull.CRUISER;
        int ftr=ftrSocket(hull);
        var left=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(ftr,"BASE-TER-FTR")
                .stage(ftr,Shipyard.Track.SPECIALIST,5)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var right=Shipyard.starter(Battle.Faction.DRAXIAN,hull)
                .install(ftr,"BASE-DRA-FTR")
                .stage(ftr,Shipyard.Track.SPECIALIST,5)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        require(Shipyard.inspect(left,tech).valid()&&
                Shipyard.inspect(right,tech).valid(),
                "FTR specialist radar fighter designs must actually fit");
        Battle b=new Battle(seed);
        b.spawnWing(0,left,tech,1,290,350);
        b.spawnWing(1,right,tech,1,480,350);
        for(var ship:b.ships()){
            ship.speed=0;
            ship.fighterReserve=1;ship.fighterReload=0;
            ship.droneReserve=ship.repairDroneReserve=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.maxHull=ship.hull=45000;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.pdRange=0;
            if(radar)b.configureFighterRadar(ship.id,true);
        }
        return b;
    }
    private static void live(TechnologyCatalog tech){
        Battle radar=fitted(tech,815002,true);
        Battle ir=fitted(tech,815002,false);
        boolean missile=false,gotEcho=false;
        for(int i=0;i<190;i++){
            radar.step();ir.step();
            for(var m:radar.missiles())if(m.targetCraftId>0){
                missile=true;
                require(m.fighterRadarHead!=null&&
                        m.fighterIrHead==null&&m.radarPulses!=null&&
                        m.radarBattery>=0,
                        "Paid active missile lacks real radar head/finite battery");
                gotEcho|=m.lastFighterConsumedTick>=0;
            }
        }
        require(missile&&radar.fighterInterceptorsLaunched>0&&
                ir.fighterInterceptorsLaunched>0,
                "Paid FTR interceptors did not launch real air-to-air missiles");
        require(gotEcho,"Real flying radar seeker never received a reflected photon");
        require(radar.fighterChaffDeployed>0&&radar.fighterFlaresDeployed==0,
                "Radar-only attack did not trigger chaff or incorrectly triggered flares");
        require(ir.fighterFlaresDeployed>0,
                "Existing infrared countermeasure behavior was lost");
        require(radar.fighterChaffSeekerDiversions>0,
                "Fitted radar missile was never diverted by a real chaff echo");
        Battle a=fitted(tech,815003,true),b=fitted(tech,815003,true);
        for(int i=0;i<120;i++){
            a.step();b.step();
            require(a.fingerprint()==b.fingerprint(),
                    "Radar pulse/chaff physical replay differs between identical seeded battles");
        }
        System.out.println("PASS fitted radar fighter missiles, chaff, IR isolation, replay");
    }
    public static void main(String[] args)throws Exception{
        photonChronology();
        live(TechnologyCatalog.loadDefault());
    }
}
