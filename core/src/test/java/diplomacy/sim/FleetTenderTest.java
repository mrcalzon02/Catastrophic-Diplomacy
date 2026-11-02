package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.TenderDoctrine;
import diplomacy.tech.TechnologyCatalog;
import java.util.Set;

/**
 * Live allied replenishment: genuine stores leave the tender and arrive on
 * matching, fitted hulls. No free ammo, unbounded tank, enemy aid, or transfer
 * without a physical module. Cosmetic flow is deliberately not release-gated.
 */
public final class FleetTenderTest {
    private FleetTenderTest(){}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int slot(Shipyard.Design ship,Shipyard.Mount mount){
        for(Shipyard.Slot s:ship.placements())if(s.mount()==mount)return s.index();
        throw new AssertionError("Missing "+mount+" mount");
    }
    private static Shipyard.Design target(Shipyard.Design base,String weapon){
        return base.install(slot(base,Shipyard.Mount.WEAPON),"BASE-TER-"+weapon);
    }
    private static int stock(Battle.Ship ship,TenderDoctrine.Cargo type){
        return switch(type){
            case KINETIC -> ship.kineticAmmo;
            case MISSILE -> ship.missileAmmo;
            case TORPEDO -> ship.torpedoAmmo;
            case FUEL -> (int)ship.fuel;
        };
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design base=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        for(String code:new String[]{"KTS","MTS","TTS","FTS"}){
            String research="TECH-TER-"+code+"-";
            for(Shipyard.Track branch:Shipyard.Track.values())
                for(int rank=1;rank<=9;rank++)
                    check(tech.get(research+branch+"-"+
                            String.format(java.util.Locale.ROOT,"%02d",rank))!=null,
                            "Missing paid tender technology stage");
            int support=slot(base,Shipyard.Mount.SUPPORT);
            Shipyard.Design tender=base.install(support,"BASE-TER-"+code);
            check(Shipyard.inspect(tender,tech).valid(),
                    "Tender fitting must be buildable: "+Shipyard.inspect(tender,tech).explanation());
            for(Shipyard.Track branch:Shipyard.Track.values()){
                Shipyard.Design upgraded=tender.stage(support,branch,9);
                check(Shipyard.inspect(upgraded,tech).valid(),
                        "Nine-rank tender module rejected "+code+"/"+branch);
            }
            String gun=switch(code){
                case "KTS" -> "KIN";
                case "MTS" -> "MSL";
                case "TTS" -> "TOR";
                default -> "LAS";
            };
            Shipyard.Design receiver=target(base,gun);
            Battle trial=new Battle(8801);
            trial.spawnWing(0,tender,tech,1,300,300);
            trial.spawnWing(0,receiver,tech,1,345,300);
            Battle.Ship source=trial.ships().get(0),patient=trial.ships().get(1);
            for(Battle.Ship ship:trial.ships()){
                ship.speed=0;ship.vx=ship.vy=0;
            }
            TenderDoctrine.Cargo cargo=switch(code){
                case "KTS" -> TenderDoctrine.Cargo.KINETIC;
                case "MTS" -> TenderDoctrine.Cargo.MISSILE;
                case "TTS" -> TenderDoctrine.Cargo.TORPEDO;
                default -> TenderDoctrine.Cargo.FUEL;
            };
            double beforeSource=switch(cargo){
                case KINETIC -> source.kineticAmmo;
                case MISSILE -> source.missileAmmo;
                case TORPEDO -> source.torpedoAmmo;
                case FUEL -> source.fuel;
            };
            double beforeReceiver=switch(cargo){
                case KINETIC -> patient.kineticAmmo;
                case MISSILE -> patient.missileAmmo;
                case TORPEDO -> patient.torpedoAmmo;
                case FUEL -> patient.fuel;
            };
            switch(cargo){
                case KINETIC -> patient.kineticAmmo-=Math.min(18,patient.kineticAmmo);
                case MISSILE -> patient.missileAmmo-=Math.min(4,patient.missileAmmo);
                case TORPEDO -> patient.torpedoAmmo-=Math.min(3,patient.torpedoAmmo);
                case FUEL -> patient.fuel-=Math.min(28,patient.fuel);
            }
            patient.powerRegen=0;source.powerRegen=0;
            double original=switch(cargo){
                case KINETIC -> patient.kineticAmmo;
                case MISSILE -> patient.missileAmmo;
                case TORPEDO -> patient.torpedoAmmo;
                case FUEL -> patient.fuel;
            };
            for(int i=0;i<300;i++)trial.step();
            double donor=switch(cargo){
                case KINETIC -> source.kineticAmmo;
                case MISSILE -> source.missileAmmo;
                case TORPEDO -> source.torpedoAmmo;
                case FUEL -> source.fuel;
            };
            double received=switch(cargo){
                case KINETIC -> patient.kineticAmmo;
                case MISSILE -> patient.missileAmmo;
                case TORPEDO -> patient.torpedoAmmo;
                case FUEL -> patient.fuel;
            };
            check(received>original+0.001,
                    "Actual live tender failed to replenish "+code);
            check(donor<beforeSource,
                    "Donor stock was not consumed "+code);
            check(Math.abs((beforeSource-donor)-(received-original))<=1e-6,
                    "Ammunition or fuel appeared/disappeared during transfer "+code);
            check(received<=beforeReceiver+1e-7,
                    "Tender exceeded recipient physical inventory capacity "+code);
            check(source.power<source.maxPower,
                    "Fleet tender did not pay energy "+code);
            if(cargo==TenderDoctrine.Cargo.FUEL)
                check(source.fuelDelivered>0,"Propellant transfer counter missing");
            else check(source.roundsDelivered>0,"Ammunition transfer counter missing");
        }
        // The physical fleet supply does NOT jump across the map.
        Battle distant=new Battle(8883);
        Shipyard.Design shells=base.install(slot(base,Shipyard.Mount.SUPPORT),"BASE-TER-KTS");
        distant.spawnWing(0,shells,tech,1,200,300);
        distant.spawnWing(0,target(base,"KIN"),tech,1,1020,300);
        var farDonor=distant.ships().get(0);
        var farTarget=distant.ships().get(1);
        for(Battle.Ship ship:distant.ships()){ship.speed=0;ship.vx=ship.vy=0;}
        farTarget.kineticAmmo=0;
        for(int i=0;i<180;i++)distant.step();
        check(farTarget.kineticAmmo==0&&farDonor.roundsDelivered==0,
                "Tender transferred ammunition beyond fitted range");

        // A rocket-only vessel has no KIN gun, but all unguided rocket
        // bodies consume the SAME paid kinetic stock. A physical KTS tender
        // must recognize that consumer without minting rounds or reloading
        // the separately finite rack magazines.
        for(RocketBarrage.Size size:RocketBarrage.Size.values()){
            Battle rocketFleet=new Battle(8922L+size.ordinal());
            rocketFleet.spawnWing(0,shells,tech,1,250,310);
            rocketFleet.spawnWing(0,target(base,size.code),tech,1,290,310);
            Battle.Ship rocketTender=rocketFleet.ships().get(0);
            Battle.Ship rocketBoat=rocketFleet.ships().get(1);
            for(Battle.Ship ship:rocketFleet.ships()){
                ship.speed=0;ship.vx=ship.vy=0;ship.weaponsCondition=0;
            }
            check(rocketBoat.kineticDamage==0&&rocketBoat.rocketRacks.size()==1,
                    "Rocket-only tender fixture accidentally has kinetic guns");
            int stored=rocketBoat.rocketRacks.get(0).rounds;
            rocketBoat.kineticAmmo=0;
            int donorBefore=rocketTender.kineticAmmo;
            for(int i=0;i<150;i++)rocketFleet.step();
            check(rocketBoat.kineticAmmo>0&&rocketBoat.kineticAmmo<=rocketBoat.maxKineticAmmo,
                    "KTS ignored rocket-only "+size+" hull's shared kinetic stock");
            check(donorBefore-rocketTender.kineticAmmo==rocketBoat.kineticAmmo&&
                            rocketTender.roundsDelivered==rocketBoat.kineticAmmo,
                    "Rocket resupply duplicated or destroyed a paid kinetic round");
            check(rocketBoat.rocketRacks.get(0).rounds==stored,
                    "KTS improperly manufactured additional physical rack magazines");
        }

        // A physical donor cannot refuel an opponent even at close range.
        Battle hostile=new Battle(8884);
        Shipyard.Design tank=base.install(slot(base,Shipyard.Mount.SUPPORT),"BASE-TER-FTS");
        hostile.spawnWing(0,tank,tech,1,300,300);
        hostile.spawnWing(1,base,tech,1,345,300);
        var hostileDonor=hostile.ships().get(0);
        var enemy=hostile.ships().get(1);
        // Isolate tender authorization from combat attrition: after store-breach
        // simulation was introduced, active gunfire can burn the target's fuel.
        // This fixture must test ONLY that no hostile refueling occurs.
        for(Battle.Ship ship:hostile.ships()){
            ship.speed=0;ship.vx=ship.vy=0;
            ship.weaponsCondition=0;
            ship.fighterReserve=0;
            ship.droneReserve=0;
        }
        enemy.fuel-=25;
        double hostileFuel=enemy.fuel;
        for(int i=0;i<180;i++)hostile.step();
        check(Math.abs(enemy.fuel-hostileFuel)<=1e-7
                        &&hostileDonor.fuelDelivered==0,
                "Fleet tender supplied an enemy");

        // Campaign purchase provisions only the paid native fuel. The tender
        // storeroom must be refilled through the existing charged dock service.
        var roster=CampaignFleet.startSolo(Battle.Faction.TERRAN,base,tech,914);
        var purchased=roster.purchase(tank,tech);
        var commissioned=purchased.ships().get(purchased.ships().size()-1);
        double nativeFuel=diplomacy.shipyard.HullBaseline.core(tank.hull).fuel();
        check(Math.abs(commissioned.stores().fuel()-nativeFuel)<1e-7,
                "Purchasing a tender secretly grants its additional fuel cargo");
        check(purchased.cost(commissioned.id(),CampaignFleet.Service.FUEL,tech)>0,
                "Unstocked fuel tender must require paid provisioning");
        var fueled=purchased.service(commissioned.id(),CampaignFleet.Service.FUEL,tech);
        check(fueled.get(commissioned.id()).stores().fuel()>nativeFuel,
                "Purchased dock fuel did not fill installed tender tanks");

        // Battle stores round-trip on a serviced vessel without magically
        // restoring spent inventory when the same physical fitting respawns.
        var persistenceBattle=new Battle(8885);
        persistenceBattle.spawnWing(0,shells,tech,1,300,300);
        var persistent=persistenceBattle.ships().get(0);
        persistent.kineticAmmo-=17;
        persistent.fuel-=23;
        var saved=CampaignFleet.Stores.capture(persistent);
        var loaded=CampaignFleet.fresh(shells,tech);
        saved.apply(loaded);
        check(loaded.kineticAmmo==persistent.kineticAmmo
                        &&Math.abs(loaded.fuel-persistent.fuel)<=1e-7,
                "Fleet recovery replenished tender cargo without a purchase");

        // A bare unmodified hull has no logistics replenishment authority.
        Battle plain=new Battle(8882);
        plain.spawnWing(0,base,tech,1,300,300);
        plain.spawnWing(0,target(base,"KIN"),tech,1,340,300);
        var donor=plain.ships().get(0);var patient=plain.ships().get(1);
        patient.kineticAmmo=0;
        for(Battle.Ship s:plain.ships())s.speed=0;
        for(int i=0;i<120;i++)plain.step();
        check(patient.kineticAmmo==0,
                "Non-tender may not manufacture or distribute ammunition");
        check(donor.roundsDelivered==0&&donor.fuelDelivered==0,
                "Unfitted hull gained tender abilities");
        System.out.println("PASS four stocked fleet tenders, finite 30Hz transfers, nine-rank research and zero free supplies");
    }
}
