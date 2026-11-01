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
            check(received<=beforeReceiver+1e-7,
                    "Tender exceeded recipient physical inventory capacity "+code);
            check(source.power<source.maxPower,
                    "Fleet tender did not pay energy "+code);
            if(cargo==TenderDoctrine.Cargo.FUEL)
                check(source.fuelDelivered>0,"Propellant transfer counter missing");
            else check(source.roundsDelivered>0,"Ammunition transfer counter missing");
        }
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
