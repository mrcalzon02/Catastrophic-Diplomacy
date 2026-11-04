package diplomacy.sim;

import diplomacy.shipyard.FleetPresets;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Actual researched hangar doctrine survives fleet blueprints and combat. */
public final class FighterSeekerBlueprintTest {
    private FighterSeekerBlueprintTest(){}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable r,String why){
        try{r.run();}catch(IllegalArgumentException accepted){return;}
        throw new AssertionError(why);
    }
    private static int hangar(Shipyard.Design d){
        for(var slot:d.placements())
            if(slot.mount()==Shipyard.Mount.SUPPORT)return slot.index();
        throw new AssertionError("No fitted FTR hangar");
    }
    private static Shipyard.Design fitting(Battle.Faction faction,int tier){
        var d=Shipyard.starter(faction,Shipyard.Hull.CRUISER)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        int slot=hangar(d);
        return d.install(slot,"BASE-"+Shipyard.factionId(faction)+"-FTR")
                .stage(slot,Shipyard.Track.SPECIALIST,tier);
    }
    private static void persistence(TechnologyCatalog tech){
        var stock=fitting(Battle.Faction.TERRAN,0);
        check(stock.fighterSeeker==FighterModel.Seeker.INFRARED&&
                !Shipyard.encode(stock).startsWith("CD-SHIP-7|"),
                "Default fighter altered legacy blueprint");
        reject(()->stock.withFighterSeeker(FighterModel.Seeker.OPTICAL),
                "Unresearched optics were accepted");
        reject(()->stock.withFighterSeeker(FighterModel.Seeker.RADAR),
                "Unresearched radar was accepted");
        var rank3=fitting(Battle.Faction.TERRAN,3);
        var optical=rank3.withFighterSeeker(FighterModel.Seeker.OPTICAL);
        reject(()->rank3.withFighterSeeker(FighterModel.Seeker.RADAR),
                "Rank 3 FTR gained unlicensed radar");
        var rank5=fitting(Battle.Faction.TERRAN,5);
        var radar=rank5.withFighterSeeker(FighterModel.Seeker.RADAR);
        check(Shipyard.inspect(optical,tech).valid()&&
                Shipyard.inspect(radar,tech).valid(),
                "Legally researched fighter heads were not deployable");
        reject(()->optical.stage(hangar(optical),Shipyard.Track.SPECIALIST,2),
                "FTR downgrade kept optical head");
        reject(()->radar.install(hangar(radar),null),
                "Stripped FTR kept active radar");
        check(optical.withFighterHull(FighterModel.Hull.LASER_FIGHTER).fighterSeeker==
                FighterModel.Seeker.INFRARED,
                "Non-interceptor craft inherited interceptor-only avionics");
        var updated=optical.withModuleVersion(hangar(optical),
                diplomacy.shipyard.ModuleVersions.Pattern.STANDARD,1);
        check(updated.fighterSeeker==FighterModel.Seeker.OPTICAL,
                "Unrelated module fitting lost selected seeker");
        String encoded=Shipyard.encode(optical);
        check(encoded.startsWith("CD-SHIP-7|")&&encoded.contains("|OPTICAL\n"),
                "Saved optical head missing from new blueprint");
        var decoded=Shipyard.decode(encoded,tech);
        check(decoded.fighterSeeker==FighterModel.Seeker.OPTICAL&&
                decoded.fighterSeekerTier()==3&&
                Shipyard.encode(decoded).equals(encoded),
                "CD-SHIP-7 changed fitted research or lost mode on decode");
        reject(()->Shipyard.decode(encoded.replace("|OPTICAL\n","|RADAR\n"),tech),
                "Tampered blueprint bypassed paid radar research");
        var restored=FleetPresets.decode(FleetPresets.encode(optical,radar,tech),tech);
        check(restored.left().fighterSeeker==FighterModel.Seeker.OPTICAL&&
                restored.right().fighterSeeker==FighterModel.Seeker.RADAR,
                "Portable fleet codec discarded distinct fighter heads");
        System.out.println("PASS CD-SHIP-7, FTR licences, legacy defaults, mutation and fleet export");
    }
    private static Battle battle(TechnologyCatalog tech,long seed){
        Battle scene=new Battle(seed);
        var optical=fitting(Battle.Faction.TERRAN,5)
                .withFighterSeeker(FighterModel.Seeker.OPTICAL);
        var radar=fitting(Battle.Faction.DRAXIAN,5)
                .withFighterSeeker(FighterModel.Seeker.RADAR);
        scene.spawnWing(0,Shipyard.decode(Shipyard.encode(optical),tech),tech,1,290,350);
        scene.spawnWing(1,Shipyard.decode(Shipyard.encode(radar),tech),tech,1,480,350);
        for(var ship:scene.ships()){
            check(ship.fighterRadarSelected!=ship.fighterOpticalSelected,
                    "Saved avionics not installed on carrier");
            ship.speed=0;ship.fighterReserve=1;ship.fighterReload=0;
            ship.droneReserve=ship.repairDroneReserve=0;
            ship.missileAmmo=ship.torpedoAmmo=0;
            ship.maxHull=ship.hull=45000;
            ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
            ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.pdRange=0;
        }
        return scene;
    }
    private static void live(TechnologyCatalog tech){
        Battle a=battle(tech,91831),b=battle(tech,91831);
        boolean optical=false,radar=false,opticalFix=false,radarFix=false;
        for(int i=0;i<190;i++){
            a.step();b.step();
            check(a.fingerprint()==b.fingerprint(),
                    "Loaded seeker mode is not deterministic during real battle");
            for(var missile:a.missiles())if(missile.targetCraftId>0){
                if(missile.team==0){
                    optical=true;
                    check(missile.fighterOpticalHead!=null&&
                            missile.fighterRadarHead==null&&missile.fighterIrHead==null,
                            "Saved optical missile launched wrong seeker");
                    opticalFix|=missile.lastFighterConsumedTick>=0;
                }else{
                    radar=true;
                    check(missile.fighterRadarHead!=null&&
                            missile.fighterOpticalHead==null&&
                            missile.radarPulses!=null,
                            "Saved radar missile lacks active pulses");
                    radarFix|=missile.lastFighterConsumedTick>=0;
                }
            }
        }
        check(optical&&radar&&opticalFix&&radarFix,
                "No real optical/radar missile contact from saved blueprints");
        System.out.println("PASS persisted FTR doctrine, real distinct missile heads, delayed photons and replay");
    }
    public static void main(String[] args)throws Exception{
        var tech=TechnologyCatalog.loadDefault();
        persistence(tech);live(tech);
    }
}
