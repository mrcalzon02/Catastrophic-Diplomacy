package diplomacy.sim;

import diplomacy.shipyard.FleetPresets;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/** Functional proof: fighter weapons launch real moving/interceptable projectiles. */
public final class FighterCraftTest {
    private FighterCraftTest(){}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static int support(Shipyard.Hull hull){
        for(Shipyard.Slot slot:hull.slots)if(slot.mount()==Shipyard.Mount.SUPPORT)
            return slot.index();
        throw new AssertionError("Hull has no fighter support socket");
    }
    private static Shipyard.Design carrier(FighterModel.Hull aircraft){
        return Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER)
                .install(support(Shipyard.Hull.CRUISER),"BASE-TER-FTR")
                .withFighterHull(aircraft);
    }
    private static void disarm(Battle.Ship s){
        s.speed=0;s.laserDamage=s.missileDamage=s.kineticDamage=0;
        s.plasmaDamage=s.particleDamage=s.torpedoDamage=0;
        s.pdRange=0;s.missileAmmo=s.torpedoAmmo=0;
        s.maxHull=15000;s.hull=15000;
    }
    private static Battle scenario(TechnologyCatalog tech,FighterModel.Hull kind,long seed){
        Battle b=new Battle(seed);
        b.spawnWing(0,carrier(kind),tech,1,290,350);
        b.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER),
                tech,1,550,350);
        for(Battle.Ship ship:b.ships())disarm(ship);
        return b;
    }
    private static long run(TechnologyCatalog tech,FighterModel.Hull kind,long seed){
        Battle b=scenario(tech,kind,seed);
        Set<FighterModel.Hull> deployed=EnumSet.noneOf(FighterModel.Hull.class);
        boolean physicalPayload=false,hit=false;
        for(int t=0;t<180;t++){
            b.step();
            for(Battle.Craft c:b.crafts())if(c.fighter){
                check(c.profile!=null,"Armed fighter profile missing");
                check(c.maxIntegrity==c.profile.health(),"Real per-type hit points");
                check(c.integrity<=c.maxIntegrity&&c.fuel<=c.profile.fuel()
                      &&c.ammunition>=0,"Fighter fuel, HP and magazine bounded");
                deployed.add(c.profile.hull());
            }
            if(kind==FighterModel.Hull.INTERCEPTOR || kind==FighterModel.Hull.ASSAULT_GUNSHIP){
                physicalPayload|=!b.ballistics().isEmpty();
                for(Battle.Ballistic shot:b.ballistics())
                    check(shot.fighterLaunched,"Fighter cannon must mark compact tracer origin");
            }
            if(kind==FighterModel.Hull.MISSILE_FIGHTER || kind==FighterModel.Hull.TORPEDO_BOMBER){
                physicalPayload|=!b.missiles().isEmpty();
                for(Battle.Missile shot:b.missiles())
                    check(shot.fighterLaunched,"Fighter guided body must mark its launcher");
            }
            if(kind==FighterModel.Hull.LASER_FIGHTER || kind==FighterModel.Hull.PARTICLE_FIGHTER)
                for(Battle.Beam shot:b.beams())
                    check(shot.fighterLaunched,"Fighter beam must mark its launcher");
            for(Battle.VisualEvent e:b.visualEvents())if(e.phase()==Battle.VisualPhase.IMPACT
                    &&e.weapon().startsWith("FIGHTER_"))hit=true;
        }
        check(deployed.contains(kind),kind+" must physically launch in combat");
        check(b.ships().get(0).fightersLaunched>0,"Hangar must consume fighter stock");
        int fired=switch(kind){
            case INTERCEPTOR,ASSAULT_GUNSHIP -> b.fighterGunShots;
            case LASER_FIGHTER -> b.fighterLaserShots;
            case PARTICLE_FIGHTER -> b.fighterParticleShots;
            case MISSILE_FIGHTER -> b.fighterMissilesLaunched;
            case TORPEDO_BOMBER -> b.fighterTorpedoesLaunched;
        };
        check(fired>0,kind+" must fire its actual assigned armament");
        if(kind==FighterModel.Hull.INTERCEPTOR||kind==FighterModel.Hull.ASSAULT_GUNSHIP
                ||kind==FighterModel.Hull.MISSILE_FIGHTER
                ||kind==FighterModel.Hull.TORPEDO_BOMBER)
            check(physicalPayload,kind+" must put physical ordnance into the shared battle");
        check(b.fingerprint()!=0,"Combat replay fingerprint must exist");
        return b.fingerprint();
    }
    private static void sensorAndArmor(){
        var small=FighterModel.standard(FighterModel.Hull.INTERCEPTOR);
        var heavy=FighterModel.standard(FighterModel.Hull.TORPEDO_BOMBER);
        double smallDetect=ProjectileModel.acquisitionRange(180,1,1,
                ProjectileModel.STANDARD_PD,small.radarProfile());
        double heavyDetect=ProjectileModel.acquisitionRange(180,1,1,
                ProjectileModel.STANDARD_PD,heavy.radarProfile());
        check(heavy.health()+heavy.armor()>small.health()+small.armor(),
                "Bomber survives stronger PD fire than interceptor");
        check(heavyDetect>smallDetect,"Radar signatures change actual PD acquisition");
        check(small.speed()>heavy.speed(),"Interceptor has real mobility advantage");
        check(heavy.ammunition()>0 && heavy.weapon()==FighterModel.Weapon.TORPEDO,
                "Bomber carries finite torpedo ammunition");
    }
    private static void airToAir(TechnologyCatalog tech){
        Battle b=new Battle(18801);
        b.spawnWing(0,carrier(FighterModel.Hull.INTERCEPTOR),tech,1,300,330);
        b.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CRUISER)
                .install(support(Shipyard.Hull.CRUISER),"BASE-DRA-FTR")
                .withFighterHull(FighterModel.Hull.INTERCEPTOR),tech,1,515,330);
        for(Battle.Ship s:b.ships())disarm(s);
        boolean lockedEnemyCraft=false;
        for(int t=0;t<200;t++){
            b.step();
            for(Battle.Craft c:b.crafts())
                if(c.fighter && c.targetCraftId>0)lockedEnemyCraft=true;
        }
        check(lockedEnemyCraft,"Interceptors must acquire hostile fighters, not merely ships");
        check(b.fighterGunShots>0,"Dogfight must fire cannon projectiles");
    }
    private static void saves(TechnologyCatalog tech){
        Shipyard.Design original=carrier(FighterModel.Hull.TORPEDO_BOMBER);
        String blue=Shipyard.encode(original);
        check(Shipyard.decode(blue,tech).fighterHull==original.fighterHull,
                "Specialized carrier blueprint round-trip");
        String fleet=FleetPresets.encode(original,carrier(FighterModel.Hull.LASER_FIGHTER),tech);
        FleetPresets.Fleets restored=FleetPresets.decode(fleet,tech);
        check(restored.left().fighterHull==FighterModel.Hull.TORPEDO_BOMBER
                        &&restored.right().fighterHull==FighterModel.Hull.LASER_FIGHTER,
                "Portable Android/desktop fleet saves preserve fighter specifications");
        Shipyard.Design legacy=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        check(Shipyard.encode(legacy).split("\\n")[0].split("\\|",-1).length==4,
                "Existing v1 blueprints retain the original four-field header");
        check(Shipyard.decode(Shipyard.encode(legacy),tech).fighterHull
                        ==FighterModel.Hull.ASSAULT_GUNSHIP,
                "Legacy saves default safely to the original gunship");
        Shipyard.Design nativeCarrier=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CARRIER);
        String oldCarrierSave=Shipyard.encode(nativeCarrier);
        check(oldCarrierSave.split("\\n")[0].split("\\|",-1).length==4,
                "Organic carrier must retain legacy four-field save header");
        Shipyard.Design restoredCarrier=Shipyard.decode(oldCarrierSave,tech);
        Battle savedBattle=new Battle(91918);
        savedBattle.spawnWing(0,restoredCarrier,tech,1,290,350);
        check(savedBattle.ships().get(0).fighterBays.stream()
                        .map(bay->bay.id).toList().equals(
                                java.util.List.of("ORG:0","ORG:1","ORG:2")),
                "Legacy carrier save must reconstruct stable native bay identities");
    }
    /** A fighter launch requires real resolved enemy contact. Light-travel
     * acquisition cannot authorize a first-tick magical carrier sortie.
     * Wait only up to four sim seconds; all inventory/bay assertions remain
     * exact once the actual launch occurs. */
    private static void awaitPhysicalSorties(Battle battle,
                                             Battle.Ship parent,int required){
        for(int t=0;t<120&&parent.fightersLaunched<required;t++)
            battle.step();
        check(parent.fightersLaunched>=required,
                "Real sensor acquisition never enabled purchased fighter sorties"
                +" expected="+required+" actual="+parent.fightersLaunched
                +" tick="+battle.tick);
    }
    /** Physical aviation is hull capacity plus fitted modular hangars. */
    private static void hangarCapacityAndSorties(TechnologyCatalog tech){
        Shipyard.Design fleetCarrier=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CARRIER).withFighterHull(FighterModel.Hull.INTERCEPTOR);
        Shipyard.Design logisticsCarrier=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CARRIER_LOGISTICS);
        Shipyard.Design cruiser=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        // A noncarrier conversion sacrifices its main batteries and shields
        // to fit extra aviation hardware; this is not free capacity.
        Shipyard.Design converted=cruiser;
        for(Shipyard.Slot slot:converted.hull.slots)
            if((slot.mount()==Shipyard.Mount.WEAPON
                    ||slot.mount()==Shipyard.Mount.DEFENSE)
                    &&converted.at(slot.index())!=null)
                converted=converted.install(slot.index(),null);
        int modules=0;
        for(Shipyard.Slot slot:converted.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                converted=converted.install(slot.index(),"BASE-TER-FTR");
                modules++;
            }
        check(modules>=2,"Cruiser must offer multiple sacrificeable hangar slots");
        check(Shipyard.inspect(converted,tech).valid(),
                "Modular cruiser carrier must fit: "+Shipyard.inspect(converted,tech).explanation());
        check(Shipyard.fit(cruiser,tech).fighterCapacity()==0,
                "Standard non-carrier must not spawn complimentary fighters");
        check(Shipyard.fighterLaunchBays(cruiser)==0,
                "Non-carrier must require physical flight deck");
        check(Shipyard.fighterLaunchBays(converted)==modules,
                "Each physical auxiliary hangar adds a flight launch bay");
        Battle moduleBattle=new Battle(91917);
        moduleBattle.spawnWing(0,converted,tech,1,290,350);
        moduleBattle.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,
                Shipyard.Hull.CRUISER),tech,1,550,350);
        for(Battle.Ship ship:moduleBattle.ships())disarm(ship);
        Battle.Ship modular=moduleBattle.ships().get(0);
        check(modular.fighterBays.size()==modules,
                "Every FTR support socket must create its own physical launch lane");
        int firstSocket=-1;
        java.util.Set<String> identities=new java.util.HashSet<>();
        for(Battle.Ship.HangarBay bay:modular.fighterBays){
            check(!bay.organic&&bay.socketIndex>=0&&identities.add(bay.id)
                            &&bay.id.equals("FTR:"+bay.socketIndex),
                    "Distinct installed FTR sockets must retain stable bay identities");
            if(firstSocket<0)firstSocket=bay.socketIndex;
        }
        int paid=modular.fighterReserve;
        String damagedId="FTR:"+firstSocket;
        modular.damageFighterBay(firstSocket,1.0);
        check(!modular.fighterBay(damagedId).operational()
                        &&modular.fighterReserve==paid,
                "Disabling one bay must not erase purchased fighter stock");
        awaitPhysicalSorties(moduleBattle,modular,modules-1);
        check(modular.fightersLaunched==modules-1
                        &&modular.fighterReserve==paid-(modules-1),
                "Damaged FTR bay must block only its own launch lane");
        check(moduleBattle.crafts().stream().filter(c->c.fighter
                &&c.sourceId==modular.id).allMatch(c->!c.launchBayId.equals(damagedId)),
                "No fighter may originate from a disabled physical socket");
        check(Shipyard.fit(converted,tech).fighterCapacity()>0,
                "Auxiliary modules must convert a cruiser into a fighter carrier");
        check(Shipyard.fit(fleetCarrier,tech).fighterCapacity()>
                        Shipyard.fit(converted,tech).fighterCapacity(),
                "Dedicated carrier must outclass improvised cruiser capacity");
        check(Shipyard.fighterLaunchBays(fleetCarrier)>Shipyard.fighterLaunchBays(converted),
                "Dedicated carrier must launch more aircraft per cycle");
        check(Shipyard.fit(logisticsCarrier,tech).fighterCapacity()>
                        Shipyard.fit(fleetCarrier,tech).fighterCapacity(),
                "Logistics carrier must have specialized built-in storage");
        check(Shipyard.fit(Shipyard.starter(Battle.Faction.TERRAN,
                        Shipyard.Hull.DREADNOUGHT),tech).fighterCapacity()>0,
                "Capital with organic auxiliary bays must not need FTR module");
        Battle battle=new Battle(91916);
        battle.spawnWing(0,fleetCarrier,tech,1,290,350);
        battle.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,
                Shipyard.Hull.CRUISER),tech,1,550,350);
        for(Battle.Ship ship:battle.ships())disarm(ship);
        Battle.Ship parent=battle.ships().get(0);
        int initial=parent.fighterReserve;
        check(parent.fighterLaunchBays==3,"Carrier must spawn with three launch bays");
        check(parent.fighterBays.size()==3
                        &&parent.fighterBay("ORG:0")!=null
                        &&parent.fighterBay("ORG:1")!=null
                        &&parent.fighterBay("ORG:2")!=null,
                "Carrier must expose three independent native catapult identities");
        awaitPhysicalSorties(battle,parent,3);
        check(parent.fightersLaunched==3&&parent.fighterReserve==initial-3,
                "Carrier initial launch must deploy exactly one craft per bay");
        check(battle.crafts().stream().filter(c->c.fighter
                &&c.sourceId==parent.id).map(c->c.launchBayId).distinct().count()==3,
                "Each native deck must launch from its own stable bay identity");
        check(battle.crafts().stream().filter(c->c.fighter
                &&c.sourceId==parent.id).count()==3,
                "All three launched fighters are independently simulated");
        check(parent.power>=0&&parent.heat<=parent.maxHeat,
                "Fighter launch must respect power and thermal limits");
        for(int tick=0;tick<240;tick++)battle.step();
        long airborne=battle.crafts().stream()
                .filter(c->c.fighter&&c.sourceId==parent.id).count();
        check(parent.fighterReserve+parent.dockedFighters.size()+airborne<=initial,
                "Launched, docked and ready fighters must not duplicate purchased inventory");
        // Refitting other components cannot confiscate paid aircraft stored in
        // an organic carrier deck. Removing the added module is safe too.
        var original=CampaignFleet.startSolo(Battle.Faction.TERRAN,
                fleetCarrier,tech,917101L);
        long id=original.ships().get(0).id();
        var inReserve=original.setActive(id,false,200);
        check(inReserve.cost(id,CampaignFleet.Service.REPAIR,tech)==0,
                "Pristine native armor must not incur phantom one-credit repairs");
        int purchased=inReserve.get(id).stores().fighters();
        check(purchased==Shipyard.fit(fleetCarrier,tech).fighterCapacity(),
                "Existing carrier's purchased complement must be preserved");
        var refitted=inReserve.refit(id,fleetCarrier.install(
                support(Shipyard.Hull.CARRIER),"BASE-TER-FTR"),tech);
        check(refitted.get(id).stores().fighters()==purchased,
                "Adding FTR to organic carrier must preserve paid fighters");
        // Newly installed machinery raises max structural integrity. The
        // existing paid-service rule requires hull overhaul before another
        // refit; test the actual lawful campaign sequence.
        var overhauled=refitted.service(id,CampaignFleet.Service.REPAIR,tech);
        var removed=overhauled.refit(id,fleetCarrier,tech);
        check(removed.get(id).stores().fighters()==purchased,
                "Removing auxiliary FTR must not erase organic carrier stock");
    }


    private static void expectRejected(Runnable action,String reason){
        boolean rejected=false;
        try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,reason);
    }
    /** A separate persistent manifest, not yet the campaign's live launch authority. */
    private static void flightWingManifest(TechnologyCatalog tech){
        Shipyard.Design empty=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        check(FlightWingManifest.bayCapacities(empty,tech).isEmpty(),
                "Unmodified cruiser must not have phantom fighter storage");
        expectRejected(()->FlightWingManifest.legacy(empty,tech,1),
                "Noncarrier must not manufacture a fighter");
        Shipyard.Design nativeCarrier=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.CARRIER);
        var nativeBays=FlightWingManifest.bayCapacities(nativeCarrier,tech);
        check(nativeBays.size()==3&&nativeBays.values().stream()
                        .mapToInt(Integer::intValue).sum()==12,
                "Three native catapults must divide the actual 12 paid storage slots");
        var nativeStock=FlightWingManifest.legacy(nativeCarrier,tech,7);
        check(nativeStock.paid()==7&&nativeStock.wings().size()==2,
                "Legacy carrier allocation must preserve exact purchased inventory");
        check(FlightWingManifest.decode(nativeStock.encode(),nativeCarrier,tech,7)
                        .wings().equals(nativeStock.wings()),
                "Native flight complement must survive checksum-protected save/load");
        var legacy=Shipyard.decode(Shipyard.encode(nativeCarrier),tech);
        check(FlightWingManifest.legacy(legacy,tech,7).wings().equals(nativeStock.wings()),
                "Four-field legacy ship saves must migrate to real organic bays");
        Shipyard.Design converted=empty;
        // Auxiliary conversion must sacrifice actual weapon/defense sockets,
        // as in the existing live carrier regression, to remain serviceable.
        for(Shipyard.Slot slot:empty.hull.slots)
            if((slot.mount()==Shipyard.Mount.WEAPON||
                    slot.mount()==Shipyard.Mount.DEFENSE)&&
                    converted.at(slot.index())!=null)
                converted=converted.install(slot.index(),null);
        int fitted=0;
        for(Shipyard.Slot slot:empty.hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT&&fitted<2){
                converted=converted.install(slot.index(),"BASE-TER-FTR");
                fitted++;
            }
        check(fitted==2&&Shipyard.inspect(converted,tech).valid(),
                "Two physical FTR sockets must be serviceable");
        var bays=FlightWingManifest.bayCapacities(converted,tech);
        check(bays.size()==2&&bays.keySet().stream().allMatch(id->id.startsWith("FTR:")),
                "Each installed socket must retain its original FTR identity");
        var ids=new java.util.ArrayList<>(bays.keySet());
        var mixed=FlightWingManifest.of(converted,tech,3,java.util.List.of(
                new FlightWingManifest.FlightWing(ids.get(0),FighterModel.Hull.INTERCEPTOR,
                        1,1,0,0,0,0),
                new FlightWingManifest.FlightWing(ids.get(1),FighterModel.Hull.TORPEDO_BOMBER,
                        0,1,0,0,0,2)));
        check(mixed.paid()==3&&mixed.wings().size()==2,
                "Mixed interceptor/bomber bays must account for exactly three paid craft");
        check(FlightWingManifest.decode(mixed.encode(),converted,tech,3)
                        .wings().equals(mixed.wings()),
                "Mixed physical bays and historical destroyed count survive save/load");
        final Shipyard.Design twoBays=converted;
        expectRejected(()->FlightWingManifest.decode(mixed.encode(),twoBays,tech,4),
                "Import must not invent a fourth paid fighter");
        expectRejected(()->FlightWingManifest.of(twoBays,tech,2,java.util.List.of(
                new FlightWingManifest.FlightWing(ids.get(0),FighterModel.Hull.INTERCEPTOR,
                        0,1,0,0,0,0),
                new FlightWingManifest.FlightWing(ids.get(0),FighterModel.Hull.INTERCEPTOR,
                        0,1,0,0,0,0))),"Repeated bay identity must be rejected");
        expectRejected(()->FlightWingManifest.of(twoBays,tech,1,java.util.List.of(
                new FlightWingManifest.FlightWing("FTR:999",FighterModel.Hull.INTERCEPTOR,
                        0,1,0,0,0,0))),"Foreign physical socket must be rejected");
        int over=bays.get(ids.get(0))+1;
        expectRejected(()->FlightWingManifest.of(twoBays,tech,over,java.util.List.of(
                new FlightWingManifest.FlightWing(ids.get(0),FighterModel.Hull.INTERCEPTOR,
                        0,over,0,0,0,0))),"One bay cannot exceed its actual fitted capacity");
        String damaged=mixed.encode().replace("INTERCEPTOR","MISSILE_FIGHTER");
        expectRejected(()->FlightWingManifest.decode(damaged,twoBays,tech,3),
                "Tampering with saved fighter type must fail checksum");
        check(mixed.forRefit(twoBays,twoBays,tech,3).paid()==3,
                "Compatible refit retains all three purchased craft");
        Shipyard.Design removed=twoBays.install(
                Integer.parseInt(ids.get(0).substring(4)),null);
        expectRejected(()->mixed.forRefit(twoBays,removed,tech,3),
                "Removing a stocked hangar must not silently confiscate its wing");
        Battle live=new Battle(99871);
        live.spawnWing(0,twoBays,tech,1,290,350);
        Battle.Ship parent=live.ships().get(0);
        int first=Integer.parseInt(ids.get(0).substring(4));
        parent.damageFighterBay(first,1.0);
        check(!parent.fighterBay(ids.get(0)).operational()&&mixed.paid()==3,
                "Disabled physical lane must retain all three paid manifest aircraft");
    }

    /** An expended fighter must actually disappear into a usable bay.
     * A paid service queue, not a free infinite ammunition generator, feeds
     * subsequent launches from the physical carrier magazines. */
    private static void carrierRecoveryAndRearm(TechnologyCatalog tech){
        Battle stocked=scenario(tech,FighterModel.Hull.MISSILE_FIGHTER,91142);
        Battle.Ship carrier=stocked.ships().get(0);
        int purchased=carrier.fighterReserve;
        double originalCrew=carrier.crewCount;
        carrier.missileAmmo=180;
        carrier.maxMissileAmmo=180;
        boolean sawRecovery=false,sawRearm=false;
        for(int t=0;t<1800;t++){
            stocked.step();
            sawRecovery|=carrier.fightersRecovered>0;
            sawRearm|=carrier.fightersRearmed>0;
            long airborne=stocked.crafts().stream()
                    .filter(c->c.fighter&&c.sourceId==carrier.id).count();
            check(carrier.fighterReserve+carrier.dockedFighters.size()+airborne<=purchased,
                    "Recovery must conserve the initial count of purchased airframes");
            check(carrier.crewCount<=originalCrew+1e-8,
                    "Landing pilots cannot create additional carrier crew");
            if(sawRecovery&&sawRearm)break;
        }
        check(sawRecovery,"Expended fighters failed to return to a physical carrier bay");
        check(sawRearm,"Returned fighter failed paid rearming with available missile stock");
        check(carrier.missileAmmo<180,"Fighter rearming must debit real missile magazines");

        Battle empty=scenario(tech,FighterModel.Hull.MISSILE_FIGHTER,91143);
        Battle.Ship dry=empty.ships().get(0);
        // The disarmed source has zero missile magazines; it must accept
        // its returning aircraft without conjuring a new sortie's payload.
        for(int t=0;t<1200&&dry.fightersRecovered==0;t++)empty.step();
        check(dry.fightersRecovered>0,"Dry carrier failed to recover its aircraft");
        for(int t=0;t<100;t++)empty.step();
        check(dry.fightersRearmed==0&&!dry.dockedFighters.isEmpty(),
                "No-magazine carrier must store aircraft rather than mint full missiles");
    }

    /** Long-range bombers must physically depart the flight deck even when
     * their target is already within the fitted torpedo firing envelope. */
    private static void clearFlightDeck(TechnologyCatalog tech){
        Battle battle=scenario(tech,FighterModel.Hull.TORPEDO_BOMBER,20261106L);
        Battle.Ship parent=battle.ships().get(0);
        awaitPhysicalSorties(battle,parent,1);
        Battle.Craft launched=null;
        for(Battle.Craft craft:battle.crafts())
            if(craft.fighter&&craft.sourceId==parent.id){launched=craft;break;}
        check(launched!=null,"Launch-clearance fixture has no live bomber");
        check(StrictMath.hypot(battle.ships().get(1).x-parent.x,
                battle.ships().get(1).y-parent.y)
                <launched.profile.range()*0.68,
                "Flight-deck test must begin inside the bomber's standoff band");
        int launchedId=launched.id;
        for(int t=0;t<25;t++)battle.step();
        check(battle.crafts().stream().anyMatch(c->c.id==launchedId),
                "Launch-clearance bomber disappeared before flying clear");
        check(StrictMath.hypot(launched.x-parent.x,launched.y-parent.y)>36,
                "Torpedo bomber remains stacked on its carrier after launch");
    }

    public static void main(String[] args)throws IOException {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        sensorAndArmor();
        clearFlightDeck(tech);
        flightWingManifest(tech);
        CampaignFlightWingCodecTest.run(tech);
        saves(tech);
        hangarCapacityAndSorties(tech);
        carrierRecoveryAndRearm(tech);
        for(FighterModel.Hull hull:FighterModel.Hull.values()){
            check(Shipyard.inspect(carrier(hull),tech).valid(),"Carrier fitting "+hull);
            long a=run(tech,hull,2000+hull.ordinal());
            long b=run(tech,hull,2000+hull.ordinal());
            check(a==b,"Deterministic fighter and projectile replay "+hull);
            System.out.println("PASS fighter type "+hull+" -> "+FighterModel.standard(hull).weapon());
        }
        airToAir(tech);
        System.out.println("PASS six armed fighter loadouts, air-to-air targeting, PD signatures, ammo, saves and replay");
    }
}
