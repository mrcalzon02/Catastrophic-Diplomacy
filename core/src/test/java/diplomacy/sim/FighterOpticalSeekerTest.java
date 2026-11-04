package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Passive optical missile tests: photon chronology, visible glare and
 * actual equipped carrier-to-carrier combat without counterfeit contacts. */
public final class FighterOpticalSeekerTest {
    private FighterOpticalSeekerTest(){}
    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(why);
    }
    private static FighterInfraredSeeker.Reading scan(
            FighterOpticalSeeker.Head head,FighterInfraredSeeker.History history,
            List<FighterCountermeasures.Cloud> clouds,long tick,
            double receiverX,double headingY){
        return FighterOpticalSeeker.receive(head,history,clouds,20,1,
                receiverX,100,receiverX,100,250,headingY,tick);
    }
    private static void opticalPhotonAndBandIsolation(){
        var scene=new Battle(815801);
        var parent=scene.addShip(0,Battle.Faction.TERRAN,200,200);
        parent.fighterProfile=FighterModel.standard(FighterModel.Hull.INTERCEPTOR);
        var fighter=new Battle.Craft(20,parent,true,0);
        fighter.x=150;fighter.y=100;
        // Target has a recorded visible silhouette independent of engine heat.
        fighter.infraredHistory.capture(0,fighter);
        var history=fighter.infraredHistory;
        var narrow=FighterOpticalSeeker.Head.NARROW_TELESCOPIC;
        var wide=FighterOpticalSeeker.Head.WIDE_FIELD;
        require(FighterOpticalSeeker.installed(3)==narrow&&
                FighterOpticalSeeker.installed(7)==wide&&
                narrow.range>wide.range&&
                narrow.halfAngleRadians<wide.halfAngleRadians,
                "Paid narrow and wide optical hardware lacks tradeoffs");
        boolean denied=false;
        try{FighterOpticalSeeker.installed(2);}
        catch(IllegalArgumentException expected){denied=true;}
        require(denied,"Optical seeker granted free optics to unfitted fighter");
        require(scan(narrow,history,List.of(),0,0,0)==null&&
                scan(narrow,history,List.of(),1,0,0)==null,
                "Optical head read reflected light before one-way transit");
        var visible=scan(narrow,history,List.of(),2,0,0);
        require(visible!=null&&visible.x()==150&&
                visible.emittedTick()==0&&visible.receivedTick()==2&&
                !visible.decoy()&&visible.strength()>0,
                "Actual historical reflected optical photon never arrived");
        require(scan(narrow,history,List.of(),3,0,0)==null,
                "Expired photon front was re-read as fresh fighter image");
        require(FighterOpticalSeeker.receive(narrow,history,List.of(),20,1,
                0,100,0,100,-250,0,2)==null,
                "Narrow optical scope saw through the back of its camera");
        // A physically brighter cloud in exactly the same pixel can saturate
        // optical image acquisition. Chaff has no such visible-light role.
        var glare=new FighterCountermeasures.Cloud(
                881,FighterCountermeasures.Kind.FLARE,20,parent.id,0,
                150,100,0,0,0,1,15);
        var chaff=new FighterCountermeasures.Cloud(
                882,FighterCountermeasures.Kind.CHAFF,20,parent.id,0,
                150,100,0,0,0,66,200);
        var offAxis=new FighterCountermeasures.Cloud(
                883,FighterCountermeasures.Kind.FLARE,20,parent.id,0,
                150,160,0,0,0,48,150);
        require(scan(narrow,history,List.of(chaff),2,0,0)!=null,
                "Radar chaff fabricated visible-optical aircraft obstruction");
        require(scan(narrow,history,List.of(offAxis),2,0,0)!=null,
                "Angularly separate flare blinded optical camera at a distance");
        require(scan(narrow,history,List.of(glare),2,0,0)==null,
                "Actual coincident, arriving high-brightness flare failed to glare");
        var future=new FighterCountermeasures.Cloud(
                884,FighterCountermeasures.Kind.FLARE,20,parent.id,0,
                150,100,0,0,3,48,100);
        require(scan(narrow,history,List.of(future),2,0,0)!=null,
                "Future flare rewrote a previously received optical image");
        // A long-range narrow telescope can see a target outside wide optics.
        fighter.x=265;
        fighter.infraredHistory.capture(7,fighter);
        // The real arena photon step is about 147 units/tick. Do not
        // hardcode a third tick after the 265-unit light front has passed.
        long photonArrival=7+SensorLightTimeHistory.propagationTicks(265);
        var far=FighterOpticalSeeker.receive(narrow,history,List.of(),20,1,
                0,100,0,100,250,0,photonArrival);
        var tooFar=FighterOpticalSeeker.receive(wide,history,List.of(),20,1,
                0,100,0,100,250,0,photonArrival);
        require(far!=null&&far.emittedTick()==7&&tooFar==null,
                "Wide-field optical hardware inherited narrow telescope reach");
        System.out.println("PASS independent optical range/cone, real photon age, glare alignment and band isolation");
    }
    private static int support(Shipyard.Hull hull){
        for(var slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT)return slot.index();
        throw new AssertionError("FTR hangar socket missing");
    }
    private static Battle fitted(TechnologyCatalog tech,long seed,boolean optical){
        var hull=Shipyard.Hull.CRUISER;
        int socket=support(hull);
        var left=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(socket,"BASE-TER-FTR")
                .stage(socket,Shipyard.Track.SPECIALIST,5)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var right=Shipyard.starter(Battle.Faction.DRAXIAN,hull)
                .install(socket,"BASE-DRA-FTR")
                .stage(socket,Shipyard.Track.SPECIALIST,5)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        require(Shipyard.inspect(left,tech).valid()&&
                Shipyard.inspect(right,tech).valid(),
                "Real installed optical carrier needs legal FTR sockets and research");
        Battle battle=new Battle(seed);
        battle.spawnWing(0,left,tech,1,290,350);
        battle.spawnWing(1,right,tech,1,480,350);
        for(var ship:battle.ships()){
            ship.speed=0;
            ship.fighterReserve=1;ship.fighterReload=0;
            ship.droneReserve=ship.repairDroneReserve=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.maxHull=ship.hull=45000;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.pdRange=0;
            if(optical)battle.configureFighterOptical(ship.id,true);
        }
        return battle;
    }
    private static void selectedHardware(TechnologyCatalog tech){
        var b=fitted(tech,815802,true);
        var s=b.ships().get(0);
        require(s.fighterOpticalSelected&&!s.fighterRadarSelected,
                "Selecting optical avionics incorrectly installed radar too");
        b.configureFighterRadar(s.id,true);
        require(s.fighterRadarSelected&&!s.fighterOpticalSelected,
                "Radio-frequency mode failed to displace the optical head");
        b.configureFighterOptical(s.id,true);
        require(s.fighterOpticalSelected&&!s.fighterRadarSelected,
                "Optical camera failed to displace active radar head");
        for(int i=0;i<3;i++)b.step();
        boolean denied=false;
        try{b.configureFighterOptical(s.id,false);}
        catch(IllegalArgumentException expected){denied=true;}
        require(denied,"In-flight seeker selection rewrote installed payloads");

        var reading=new FighterInfraredSeeker.Reading(
                150,100,0,0,0,3,0,1.2);
        var optical=FighterSeekerEstimate.from(reading,24,20,1,
                null,null,FighterOpticalSeeker.Head.NARROW_TELESCOPIC,0,100);
        var wide=FighterSeekerEstimate.from(reading,24,20,1,
                null,null,FighterOpticalSeeker.Head.WIDE_FIELD,0,100);
        var replay=FighterSeekerEstimate.from(reading,24,20,1,
                null,null,FighterOpticalSeeker.Head.NARROW_TELESCOPIC,0,100);
        require(optical.equals(replay)&&
                optical.uncertaintyRadius()<wide.uncertaintyRadius()&&
                StrictMath.hypot(optical.x()-reading.x(),
                        optical.y()-reading.y())<=optical.uncertaintyRadius(),
                "Finite narrow/wide optical targeting error lost deterministic behavior");
        boolean invalid=false;
        try{FighterSeekerEstimate.from(reading,24,20,1,
                FighterInfraredSeeker.Head.FIXED_FORWARD,null,
                FighterOpticalSeeker.Head.WIDE_FIELD,0,100);}
        catch(IllegalArgumentException expected){invalid=true;}
        require(invalid,"Optical seeker and IR enabled on one unlicensed missile");
        System.out.println("PASS exclusive researched FTR optical selection and finite optical measurement");
    }
    private static void live(TechnologyCatalog tech){
        Battle optical=fitted(tech,815803,true);
        Battle ir=fitted(tech,815803,false);
        boolean missile=false,received=false;
        for(int i=0;i<190;i++){
            optical.step();ir.step();
            for(var m:optical.missiles())if(m.targetCraftId>0){
                missile=true;
                require(m.fighterOpticalHead!=null&&
                        m.fighterIrHead==null&&m.fighterRadarHead==null&&
                        m.radarPulses==null&&m.radarBattery==0,
                        "Optical missile received invented radar or thermal head/battery");
                received|=m.lastFighterConsumedTick>=0;
            }
        }
        require(missile&&received&&optical.fighterInterceptorsLaunched>0&&
                ir.fighterInterceptorsLaunched>0,
                "Real FTR optical missiles failed to track any delayed fighter images");
        require(optical.fighterChaffSeekerDiversions==0&&
                optical.fighterFlareSeekerDiversions==0&&
                optical.fighterChaffFuzes==0&&optical.fighterFlareFuzes==0,
                "Optical missiles were falsely lured into IR/radar decoy impact");
        Battle a=fitted(tech,815804,true),b=fitted(tech,815804,true);
        for(int i=0;i<120;i++){
            a.step();b.step();
            require(a.fingerprint()==b.fingerprint(),
                    "Optical seeker physical histories and missile replay diverged");
        }
        System.out.println("PASS real equipped optical interceptors, delayed photons, IR/radar isolation and replay");
    }
    public static void main(String[] args)throws Exception{
        opticalPhotonAndBandIsolation();
        var tech=TechnologyCatalog.loadDefault();
        selectedHardware(tech);
        live(tech);
    }
}
