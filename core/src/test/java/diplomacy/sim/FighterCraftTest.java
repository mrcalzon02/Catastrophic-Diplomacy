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
            if(kind==FighterModel.Hull.INTERCEPTOR || kind==FighterModel.Hull.ASSAULT_GUNSHIP)
                physicalPayload|=!b.ballistics().isEmpty();
            if(kind==FighterModel.Hull.MISSILE_FIGHTER || kind==FighterModel.Hull.TORPEDO_BOMBER)
                physicalPayload|=!b.missiles().isEmpty();
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
    }
    public static void main(String[] args)throws IOException {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        sensorAndArmor();
        saves(tech);
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
