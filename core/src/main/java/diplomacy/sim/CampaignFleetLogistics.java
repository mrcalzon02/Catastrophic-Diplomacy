package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Deterministic actual resource shortages, paid depot services and new hull commissioning. */
public final class CampaignFleetLogistics {
    private CampaignFleetLogistics(){}
    public record Quote(long repairs,long crew,long ammunition,long fuel,long craft) {
        public long total(){return Math.addExact(Math.addExact(repairs,crew),
                Math.addExact(Math.addExact(ammunition,fuel),craft));}
    }
    public record Transaction(CampaignFleetRoster.Hull vessel,long creditsSpent) { }
    public static Battle.Ship fitted(Shipyard.Design design,TechnologyCatalog catalog){
        Battle encounter=Battle.duel(492891,1,design,
                Shipyard.starter(design.faction,Shipyard.Hull.CORVETTE),catalog);
        return encounter.ships().get(0);
    }
    public static CampaignFleetRoster.Hull commissioned(long id,String name,
                                                        Shipyard.Design design,
                                                        TechnologyCatalog catalog){
        Battle.Ship ship=fitted(design,catalog);
        ship.name=name;
        return new CampaignFleetRoster.Hull(id,name,Shipyard.encode(design),
                ship.hull,ship.shields,ship.defense.totalArmor(),ship.fuel,ship.crewCount,
                ship.kineticAmmo,ship.missileAmmo,ship.torpedoAmmo,
                ship.fighterReserve,ship.droneReserve,ship.defense.saveIntegrity(),0,true);
    }
    public static void apply(CampaignFleetRoster.Hull current,Battle.Ship ship){
        if(current==null||ship==null)throw new IllegalArgumentException("Missing roster vessel");
        if(current.hull()>ship.maxHull+1e-6||current.shields()>ship.maxShields+1e-6||
                current.armor()>ship.maxArmor+1e-6||
                current.fuel()>ship.maxFuel+1e-6||
                current.crew()>Math.max(ship.crewBerths,ship.lifeSupport)+1e-6||
                current.kinetic()>ship.kineticAmmo||current.missiles()>ship.missileAmmo||
                current.torpedoes()>ship.torpedoAmmo||
                current.fighters()>ship.fighterReserve||current.drones()>ship.droneReserve)
            throw new IllegalArgumentException("Fleet resource snapshot exceeds fitted equipment");
        ship.defense.restoreIntegrity(current.integrity());
        ship.name=current.name();
        ship.hull=current.hull();
        ship.shields=current.shields();
        ship.armor=current.armor();
        ship.fuel=current.fuel();
        ship.crewCount=current.crew();
        ship.kineticAmmo=current.kinetic();
        ship.missileAmmo=current.missiles();
        ship.torpedoAmmo=current.torpedoes();
        ship.fighterReserve=current.fighters();
        ship.droneReserve=current.drones();
    }
    public static Quote quote(CampaignFleetRoster.Hull stored,Shipyard.Design design,
                              TechnologyCatalog catalog){
        if(stored==null||!Shipyard.encode(design).equals(stored.fitting()))
            throw new IllegalArgumentException("Fitted hull identity changed");
        Battle.Ship max=fitted(design,catalog);
        double damage=Math.max(0,max.maxHull-stored.hull())+
                Math.max(0,max.maxShields-stored.shields())*.5+
                Math.max(0,max.maxArmor-stored.armor());
        long repair=(long)Math.ceil(damage*14);
        long crew=(long)Math.ceil(Math.max(0,max.crewCount-stored.crew())*115);
        long ammo=Math.max(0,max.kineticAmmo-stored.kinetic())*3L+
                Math.max(0,max.missileAmmo-stored.missiles())*35L+
                Math.max(0,max.torpedoAmmo-stored.torpedoes())*90L;
        long fuel=(long)Math.ceil(Math.max(0,max.maxFuel-stored.fuel())*4);
        long craft=Math.max(0,max.fighterReserve-stored.fighters())*280L+
                Math.max(0,max.droneReserve-stored.drones())*95L;
        return new Quote(repair,crew,ammo,fuel,craft);
    }
    public static Transaction service(CampaignFleetRoster.Hull stored,Shipyard.Design design,
                                      TechnologyCatalog catalog){
        Quote invoice=quote(stored,design,catalog);
        Battle.Ship restored=fitted(design,catalog);
        return new Transaction(new CampaignFleetRoster.Hull(stored.id(),stored.name(),
                stored.fitting(),restored.hull,restored.shields,restored.defense.totalArmor(),
                restored.maxFuel,restored.crewCount,restored.kineticAmmo,
                restored.missileAmmo,restored.torpedoAmmo,restored.fighterReserve,
                restored.droneReserve,restored.defense.saveIntegrity(),
                stored.missions(),stored.inService()),invoice.total());
    }
    public static long purchasePrice(Shipyard.Design design,TechnologyCatalog catalog){
        Battle.Ship ship=fitted(design,catalog);
        return 1000L+(long)Math.ceil(ship.maxHull*35+
                ship.maxShields*12+ship.maxArmor*10+
                ship.crewCount*110+ship.fighterReserve*280+
                ship.droneReserve*95);
    }
}
