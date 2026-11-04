package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** The actual fighter wing radio is neither fleet telemetry nor an auto-lock. */
public final class FighterWingLinkTest {
    private FighterWingLinkTest(){}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable action,String why){
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    private static FighterWingLink.Contact report(int source,int team,
            FighterWingLink.Kind kind,long when,double x,double y){
        return new FighterWingLink.Contact(source,team,kind,100,
                when,when,x,y,225,280,7,-2);
    }
    private static void localRadioRules(){
        check(FighterWingLink.RANGE==60&&
                FighterWingLink.RANGE<
                        FighterModel.standard(FighterModel.Hull.INTERCEPTOR).sensors()/4,
                "Fighter radio became a long-range fleet sensor network");
        var net=new FighterWingLink.Network();
        var fighter=report(15,0,FighterWingLink.Kind.FIGHTER,4,100,100);
        var ship=report(16,0,FighterWingLink.Kind.SHIP,4,102,100);
        var hostile=report(20,1,FighterWingLink.Kind.FIGHTER,4,101,100);
        net.index(List.of(fighter,ship,hostile),4);
        check(net.receive(17,0,110,100,4)==null,
                "Same-tick wing radio supplied instantaneous target data");
        net.index(List.of(fighter,ship,hostile),5);
        var nearby=net.receive(17,0,159.999,100,5);
        check(nearby!=null&&nearby.sourceId()==15&&
                nearby.kind()==FighterWingLink.Kind.FIGHTER&&
                nearby.observedTick()==4,
                "Direct one-tick 60-unit fighter data was not delivered");
        var isolated=new FighterWingLink.Network();
        isolated.index(List.of(fighter),5);
        check(isolated.receive(17,0,159.999,100,5)!=null&&
                isolated.receive(17,0,160.001,100,5)==null,
                "A single fighter transmitter exceeded the 60-unit hard limit");
        check(net.receive(17,0,162.001,100,5)==null,
                "The combined fighter wing radio leaked past all transmitters");
        check(net.receive(17,1,110,100,5)==hostile,
                "Opposing teams consumed one another's wing data");
        check(net.receive(15,0,100,100,5)==ship,
                "Sender treated its own outgoing packet as an incoming report");
        var shipOnly=new FighterWingLink.Network();
        shipOnly.index(List.of(ship),5);
        check(shipOnly.receive(17,0,110,100,5).kind()==FighterWingLink.Kind.SHIP,
                "Non-interceptor ship sightings were not shareable");
        net.index(List.of(fighter,ship,hostile),6);
        check(net.receive(17,0,110,100,6)==null,
                "Expired wing packets were cached as permanent contact locks");
        reject(()->new FighterWingLink.Contact(15,0,FighterWingLink.Kind.FIGHTER,
                100,5,6,100,100,225,280,7,-2),
                "A second-hand/stale track could be broadcast as a fresh local observation");
        reject(()->new FighterWingLink.Contact(15,0,FighterWingLink.Kind.FIGHTER,
                100,5,5,Double.NaN,100,225,280,7,-2),
                "Non-physical sender pose was accepted");
        var other=new FighterWingLink.Network();
        other.index(List.of(ship,fighter,hostile),5);
        check(other.receive(17,0,155,100,5).equals(
                netReport(fighter,ship,hostile,155)),
                "Unrelated broadcast iteration order fabricated different contact");
        System.out.println("PASS 60-unit direct radio, one-tick delay, no team leaks or multi-hop");
    }
    private static FighterWingLink.Contact netReport(FighterWingLink.Contact fighter,
                                                      FighterWingLink.Contact ship,
                                                      FighterWingLink.Contact hostile,
                                                      double x){
        var n=new FighterWingLink.Network();
        n.index(List.of(fighter,ship,hostile),5);
        return n.receive(17,0,x,100,5);
    }
    private static int support(Shipyard.Hull hull){
        for(var slot:hull.slots)if(slot.mount()==Shipyard.Mount.SUPPORT)
            return slot.index();
        throw new AssertionError("No fighter hangar");
    }
    private static Battle physical(TechnologyCatalog tech,long seed){
        var blue=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER)
                .install(support(Shipyard.Hull.CRUISER),"BASE-TER-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        var red=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER)
                .install(support(Shipyard.Hull.CRUISER),"BASE-DRA-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        Battle b=new Battle(seed);
        b.spawnWing(0,blue,tech,2,290,350);
        b.spawnWing(1,red,tech,1,530,350);
        for(var ship:b.ships()){
            ship.speed=0;ship.maxHull=ship.hull=30000;
            ship.pdRange=0;
            ship.laserDamage=ship.missileDamage=ship.kineticDamage=0;
            ship.plasmaDamage=ship.particleDamage=ship.torpedoDamage=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
        }
        int count=0;
        while(count++<120){
            int friends=0,enemies=0;
            for(var craft:b.crafts())if(craft.fighter){
                if(craft.team==0)friends++;else enemies++;
            }
            if(friends>=2&&enemies>=1)return b;
            b.step();
        }
        throw new AssertionError("No real fighter wings physically launched");
    }
    private static void wingmanReacquisition(TechnologyCatalog tech){
        Battle a=physical(tech,448821),b=physical(tech,448821);
        for(Battle scene:new Battle[]{a,b}){
            Battle.Craft scout=null,wingman=null,hostile=null;
            for(var craft:scene.crafts())if(craft.fighter){
                if(craft.team==0){
                    if(scout==null)scout=craft;else if(wingman==null)wingman=craft;
                }else if(hostile==null)hostile=craft;
            }
            check(scout!=null&&wingman!=null&&hostile!=null,
                    "Missing real aircraft");
            double detected=ProjectileModel.acquisitionRange(
                    scout.profile.sensors(),1,scene.weather().sensorFactor(),
                    ProjectileModel.STANDARD_PD,hostile.radarProfile());
            check(detected>80&&detected<700,
                    "Unreasonable physical fighter detector range");
            scout.x=400;scout.y=350;
            wingman.x=448;wingman.y=350;
            hostile.x=400-detected+7;hostile.y=350;
            // Suppress other enemy craft interference in the harness;
            // observation still uses real fighters with autonomous movement.
        }
        a.step();b.step();
        check(a.fingerprint()==b.fingerprint(),
                "Broadcast origin tick was not deterministic");
        a.step();b.step();
        check(a.fingerprint()==b.fingerprint(),
                "Broadcast delivery was not deterministic");
        boolean received=false,notAutoLocked=false;
        for(var craft:a.crafts())if(craft.team==0&&craft.fighter&&
                craft.sharedWingContact!=null&&
                craft.sharedWingContact.kind()==FighterWingLink.Kind.FIGHTER){
            received=true;
            notAutoLocked|=craft.targetCraftId==0;
            check(craft.sharedWingContact.sentTick()==a.tick-1,
                    "Wingman got a future sighting instead of delayed radio");
            // An independent ship sighting is legitimate. Never forward
            // somebody else's historical sensor report as a new one.
            var transmitted=craft.lastLocalWingContact;
            check(transmitted==null||
                    transmitted.sourceId()==craft.id&&
                    transmitted.observedTick()==a.tick&&
                    transmitted.sentTick()==a.tick,
                    "Wingman rebroadcast somebody else's historical sensor report");
        }
        check(received&&notAutoLocked&&a.fighterWingReceipts>0,
                "Live wingmen did not use a nearby scout's fighter-only cue");
        System.out.println("PASS real close-formation fighter scout contact, no remote weapon lock and replay");
    }
    public static void main(String[] args)throws Exception{
        localRadioRules();
        wingmanReacquisition(TechnologyCatalog.loadDefault());
    }
}
