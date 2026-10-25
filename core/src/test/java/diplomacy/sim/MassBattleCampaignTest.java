package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Full 1v1 through 200v200 headless matrix, engineering variants, and replay/endurance gates.
 *  No GPU claim: screen-space position tests prove initial visibility only.
 *  java diplomacy.sim.MassBattleCampaignTest [optional-report.csv]
 */
public final class MassBattleCampaignTest {
    private MassBattleCampaignTest() { }
    private static final String[] WEAPONS={"LAS","KIN","PLA","PAR","MSL","TOR"};
    private static final String[] SUPPORT={"FTR","DRN","MAG"};
    private static final int[] REPLAYS={1,2,4,8,16,32,64,100,150,200};
    private static void check(boolean assertion,String label) { if(!assertion)throw new AssertionError(label); }
    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int nth){
        for(Shipyard.Slot s:hull.slots)if(s.mount()==mount&&nth--==0)return s.index();
        throw new IllegalArgumentException("No "+mount+" slot #"+nth+" on "+hull);
    }
    private static Shipyard.Design configure(Battle.Faction faction,Shipyard.Hull hull,
                                             String first,String second,int driveStage) {
        String prefix="BASE-"+Shipyard.factionId(faction)+"-";
        Shipyard.Design d=Shipyard.starter(faction,hull)
                .install(slot(hull,Shipyard.Mount.WEAPON,0),prefix+first)
                .install(slot(hull,Shipyard.Mount.WEAPON,1),prefix+second);
        if(driveStage>0)d=d.stage(slot(hull,Shipyard.Mount.SYSTEM,1),Shipyard.Track.EFFICIENCY,driveStage);
        return d;
    }
    private static void sane(Battle b,String label){
        for(Battle.Ship s:b.ships()){
            check(Double.isFinite(s.x)&&Double.isFinite(s.y)&&Double.isFinite(s.vx)&&Double.isFinite(s.vy),label+": location/velocity");
            check(Double.isFinite(s.hull)&&Double.isFinite(s.heat)&&Double.isFinite(s.power),label+": hull/thermal");
            check(s.hull>=0&&s.power>=0&&s.shields>=0&&s.armor>=0,label+": negative durability/energy");
            check(s.missileAmmo>=0&&s.torpedoAmmo>=0&&s.fighterReserve>=0&&s.droneReserve>=0,label+": ammunition underrun");
        }
        for(Battle.Missile m:b.missiles())check(Double.isFinite(m.x)&&Double.isFinite(m.y)&&Double.isFinite(m.damage),label+": projectile finite");
        for(Battle.Ballistic p:b.ballistics())
            check(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.vx)
                    &&Double.isFinite(p.vy)&&p.integrity>0,label+": tracked kinetic finite");
        for(Battle.Craft c:b.crafts())check(Double.isFinite(c.x)&&Double.isFinite(c.y)&&Double.isFinite(c.fuel),label+": craft finite");
        for(Battle.Beam beam:b.beams())check(Double.isFinite(beam.x1)&&Double.isFinite(beam.y2)&&beam.type!=null,label+": beam render payload");
    }
    /** Static viewport contract: the actual libGDX render path still needs a desktop/Android screenshot. */
    private static void initiallyVisible(Battle b,int expected,String label){
        check(b.ships().size()==expected,label+": deployed count");
        for(Battle.Ship s:b.ships()){
            double margin=14*s.visualScale();
            check(s.x-margin>=40 && s.x+margin<=1240 && s.y-margin>=50 && s.y+margin<=640,
                    label+": vessel starts outside the battle viewport "+s.name+" at "+s.x+","+s.y);
        }
    }
    private static long run(Battle battle,int ticks,String label){
        for(int i=0;i<ticks;i++)battle.step();
        sane(battle,label);return battle.fingerprint();
    }
    private static int weaponProof(TechnologyCatalog catalog){
        EnumSet<Battle.DamageType> beams=EnumSet.noneOf(Battle.DamageType.class);
        boolean missile=false,torpedo=false,kinetic=false,fastEngine=false,smallEngine=false;
        int validPairs=0,invalidPairs=0,validEngineStages=0,validWeaponTech=0;
        boolean[] engineTracks=new boolean[Shipyard.Track.values().length];
        boolean[] weaponTracks=new boolean[Shipyard.Track.values().length];
        for(Battle.Faction faction:Battle.Faction.values())for(Shipyard.Hull hull:Shipyard.Hull.values()){
            int firstWeapon=slot(hull,Shipyard.Mount.WEAPON,0),secondWeapon=slot(hull,Shipyard.Mount.WEAPON,1);
            for(String left:WEAPONS)for(String right:WEAPONS){
                Shipyard.Design candidate=configure(faction,hull,left,right,0);
                if(!Shipyard.inspect(candidate,catalog).valid()){invalidPairs++;continue;}
                validPairs++;
                Shipyard.Fitted stats=Shipyard.fit(candidate,catalog);
                check(stats.speed()>0&&stats.powerRegen()>0,"engine fitted but not functioning");
                check(stats.laserDamage()+stats.missileDamage()+stats.kineticDamage()+stats.plasmaDamage()
                        +stats.particleDamage()+stats.torpedoDamage()>0,"fitted ship unarmed");
                if(hull==Shipyard.Hull.CORVETTE)smallEngine=true;
                if(hull==Shipyard.Hull.DREADNOUGHT)fastEngine=true;
                // Trigger every weapon with close range under validated loadouts.
                Battle one=new Battle(2101);
                one.spawnWing(0,candidate,catalog,1,400,335);
                one.spawnWing(1,Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),catalog,1,620,335);
                run(one,4,"weapon "+faction+" "+hull+" "+left+"/"+right);
                for(Battle.Beam effect:one.beams())beams.add(effect.type);
                kinetic|=!one.ballistics().isEmpty();
                for(Battle.Missile ordnance:one.missiles()){
                    if(ordnance.torpedo)torpedo=true;else missile=true;
                }
            }
            // Every parallel engine/weapon technology track and all six stages.
            for(Shipyard.Track track:Shipyard.Track.values())for(int level=1;level<=6;level++){
                Shipyard.Design drive=Shipyard.starter(faction,hull)
                        .stage(slot(hull,Shipyard.Mount.SYSTEM,1),track,level);
                if(Shipyard.inspect(drive,catalog).valid()){
                    engineTracks[track.ordinal()]=true;validEngineStages++;
                    Battle trial=new Battle(61+level);
                    trial.spawnWing(0,drive,catalog,1,400,335);
                    trial.spawnWing(1,Shipyard.starter(faction,hull),catalog,1,620,335);
                    run(trial,10,"engine "+track+" stage "+level);
                    double boosted=trial.ships().get(0).speed;
                    check(boosted>0&&Double.isFinite(boosted),"engine technology broke thrust");
                }
                for(String weapon:WEAPONS){
                    Shipyard.Design upgraded=configure(faction,hull,weapon,"LAS",0)
                            .stage(firstWeapon,track,level);
                    if(!Shipyard.inspect(upgraded,catalog).valid())continue;
                    weaponTracks[track.ordinal()]=true;validWeaponTech++;
                    var fitted=Shipyard.fit(upgraded,catalog);
                    check(Double.isFinite(fitted.hull())&&Double.isFinite(fitted.speed()),
                            "technology invalidated fitted combat statistics");
                    Battle trial=new Battle(100+level);
                    trial.spawnWing(0,upgraded,catalog,1,400,335);
                    trial.spawnWing(1,Shipyard.starter(faction,hull),catalog,1,620,335);
                    run(trial,2,"upgraded weapon "+weapon+" "+track+" "+level);
                }
            }
            // Alternate additional drive emplacement on hulls with spare systems bays.
            if(hull!=Shipyard.Hull.CORVETTE){
                Shipyard.Design twin=Shipyard.starter(faction,hull)
                        .install(slot(hull,Shipyard.Mount.SYSTEM,3),"BASE-"+Shipyard.factionId(faction)+"-ENG");
                if(Shipyard.inspect(twin,catalog).valid()){
                    double baseline=Shipyard.fit(Shipyard.starter(faction,hull),catalog).speed();
                    check(Shipyard.fit(twin,catalog).speed()>baseline,"twin thruster arrangement must increase thrust");
                }
            }
            if(hull==Shipyard.Hull.CRUISER || hull==Shipyard.Hull.DREADNOUGHT){
                for(String sub:SUPPORT){
                    Shipyard.Design support=Shipyard.starter(faction,hull)
                            .install(slot(hull,Shipyard.Mount.SUPPORT,0),"BASE-"+Shipyard.factionId(faction)+"-"+sub);
                    if(Shipyard.inspect(support,catalog).valid()){
                        var fit=Shipyard.fit(support,catalog);
                        check(sub.equals("FTR")&&fit.fighterCapacity()>0 || sub.equals("DRN")&&fit.droneCapacity()>0
                                || sub.equals("MAG")&&fit.magazineCapacity()>0,"support loadout actually deploys");
                    }
                }
            }
        }
        check(validPairs>100&&invalidPairs>0,"must exercise extensive valid/rejected weapon arrangements, got "+validPairs+" / "+invalidPairs);
        check(validEngineStages>0&&smallEngine&&fastEngine,"all hull/drive groups need coverage");
        for(Shipyard.Track track:Shipyard.Track.values()){
            check(engineTracks[track.ordinal()],"unused engine track "+track);
            check(weaponTracks[track.ordinal()],"unused weapon track "+track);
        }
        for(Battle.DamageType type:new Battle.DamageType[]{Battle.DamageType.LASER,
                Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE})check(beams.contains(type),"missing visible beam for "+type);
        check(kinetic,"slow kinetic batteries must launch tracked interceptable shells, not hitscan beams");
        check(missile&&torpedo,"both missile and torpedo projectiles must physically spawn");
        System.out.println("PASS weapon/thruster matrix: valid="+validPairs+" rejected="+invalidPairs
                +" upgraded-drives="+validEngineStages+" upgraded-weapons="+validWeaponTech
                +" beams="+beams+" kinetic="+kinetic+" missile="+missile+" torpedo="+torpedo);
        return validPairs;
    }
    /**
     * Shipyard legality is immutable: a test carrier must carry genuine reactors,
     * heat sinks and hangars. Different factions can require different hull mass
     * budgets, so select a valid fitted design rather than bypassing validation.
     */
    private static Shipyard.Design legalFighterCarrier(Battle.Faction faction,
                         FighterModel.Hull craft,TechnologyCatalog catalog){
        String id="BASE-"+Shipyard.factionId(faction)+"-";
        Shipyard.Hull[] frames={Shipyard.Hull.CARRIER,Shipyard.Hull.DREADNOUGHT,
                Shipyard.Hull.BATTLECRUISER,Shipyard.Hull.HEAVY_CRUISER,
                Shipyard.Hull.CRUISER,Shipyard.Hull.FRIGATE};
        for(Shipyard.Hull frame:frames) {
            Shipyard.Design outfitted=Shipyard.starter(faction,frame)
                    .install(slot(frame,Shipyard.Mount.SUPPORT,0),id+"FTR")
                    .withFighterHull(craft);
            if(Shipyard.inspect(outfitted,catalog).valid())return outfitted;
            if(frame.slots.size()>0){
                Shipyard.Design powered=outfitted.install(
                        slot(frame,Shipyard.Mount.SYSTEM,3),id+"REA");
                if(Shipyard.inspect(powered,catalog).valid())return powered;
                if(frame==Shipyard.Hull.CARRIER||frame==Shipyard.Hull.DREADNOUGHT
                        ||frame==Shipyard.Hull.BATTLECRUISER
                        ||frame==Shipyard.Hull.HEAVY_CRUISER){
                    Shipyard.Design cooled=powered.install(
                            slot(frame,Shipyard.Mount.SYSTEM,4),id+"RAD");
                    if(Shipyard.inspect(cooled,catalog).valid())return cooled;
                }
            }
        }
        throw new AssertionError("No legal fighter carrier for "+faction+" / "+craft+
                " (all attempted loadouts were rejected by shipyard)");
    }
    /** Six distinct arms and multiple hull/faction doctrines fight simultaneously at 200v200. */
    private static long mixedFleet(TechnologyCatalog catalog,long seed,int steps) {
        Battle b=new Battle(seed);
        for(int team=0;team<2;team++)for(int group=0;group<6;group++){
            Battle.Faction faction=Battle.Faction.values()[(group+3*team)%6];
            Shipyard.Hull hull=group%2==0?Shipyard.Hull.CRUISER:Shipyard.Hull.FRIGATE;
            Shipyard.Design design=configure(faction,hull,WEAPONS[group],WEAPONS[(group+1)%6],0);
            check(Shipyard.inspect(design,catalog).valid(),"invalid massed mixed design "+group);
            if(hull==Shipyard.Hull.CRUISER){
                Shipyard.Design support=design.install(slot(hull,Shipyard.Mount.SUPPORT,0),
                        "BASE-"+Shipyard.factionId(faction)+"-"+SUPPORT[group%3]);
                if(Shipyard.inspect(support,catalog).valid())design=support;
            }
            int qty=33+(group<2?1:0); // 34+34+33+33+33+33 = 200
            double centerX=(team==0?220:880)+(group%3)*95;
            double centerY=238+(group/3)*190;
            // At least one real carrier of EVERY fighter hull on EACH team.
            // Keep the other 32/33 ships' original mixed weapon/support outfits.
            FighterModel.Hull craft=FighterModel.Hull.values()[group];
            Shipyard.Design carrier=legalFighterCarrier(faction,craft,catalog);
            b.spawnWing(team,carrier,catalog,1,centerX,centerY);
            b.spawnWing(team,design,catalog,qty-1,centerX+15,centerY);
        }
        initiallyVisible(b,400,"200v200 combined-arms deployment");
        EnumSet<Battle.DamageType> visible=EnumSet.noneOf(Battle.DamageType.class);
        boolean missiles=false,torpedoes=false,kinetics=false,craft=false;
        EnumSet<FighterModel.Hull> deployedAircraft=EnumSet.noneOf(FighterModel.Hull.class);
        int peakOrdnance=0,peakCraft=0;
        for(int tick=0;tick<steps;tick++){
            b.step();
            for(Battle.Beam effect:b.beams())visible.add(effect.type);
            for(Battle.Missile payload:b.missiles())if(payload.torpedo)torpedoes=true;else missiles=true;
            kinetics|=!b.ballistics().isEmpty();
            peakOrdnance=Math.max(peakOrdnance,b.missiles().size()+b.ballistics().size());
            peakCraft=Math.max(peakCraft,b.crafts().size());
            craft|=!b.crafts().isEmpty();
            for(Battle.Craft c:b.crafts())if(c.fighter)
                deployedAircraft.add(c.profile.hull());
        }
        sane(b,"200v200 combined-arms "+steps);
        check(b.hitCount>0&&b.destructions>0,"mixed fleet must exchange lethal fire");
        check(visible.contains(Battle.DamageType.LASER)&&kinetics
                &&visible.contains(Battle.DamageType.PLASMA)&&visible.contains(Battle.DamageType.PARTICLE),
                "all energy beams and kinetic tracked payloads in live mass engagement: "+visible);
        check(missiles&&torpedoes,"mixed fleet must fire both ordnance classes");
        check(craft,"mixed fleet carrier or defensive drone should launch craft");
        check(deployedAircraft.size()==FighterModel.Hull.values().length,
                "200v200 must DEPLOY every distinct fighter profile, got "+deployedAircraft);
        check(b.fighterGunShots>0&&b.fighterLaserShots>0&&b.fighterParticleShots>0
                &&b.fighterMissilesLaunched>0&&b.fighterTorpedoesLaunched>0,
                "200v200 must ACTUALLY FIRE all five fighter weapon types: "+
                b.fighterGunShots+"/"+b.fighterLaserShots+"/"+b.fighterParticleShots+"/"+
                b.fighterMissilesLaunched+"/"+b.fighterTorpedoesLaunched);
        System.out.println("PASS 200v200 combined arms: ticks="+steps+" hits="+b.hitCount
                +" destroyed="+b.destructions+" peak-missiles="+peakOrdnance+" peak-craft="+peakCraft
                +" fighter-types="+deployedAircraft
                +" fighter-payloads="+b.fighterGunShots+"/"+b.fighterLaserShots+"/"+
                    b.fighterParticleShots+"/"+b.fighterMissilesLaunched+"/"+b.fighterTorpedoesLaunched
                +" visible-beam-types="+visible+" tracked-kinetics="+kinetics
                +" fingerprint="+Long.toHexString(b.fingerprint()));
        return b.fingerprint();
    }

    public static void main(String[] args)throws IOException {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        int configurations=weaponProof(catalog);
        List<String> csv=new ArrayList<>();
        csv.add("ships_per_team,total_ships,ticks,elapsed_ms,hits,missile_intercepts,destructions,missiles_in_flight,craft_in_flight,fingerprint");
        var roster=Battle.Faction.values();
        var hulls=Shipyard.Hull.values();
        long start=System.nanoTime(),ticks=0;
        for(int count=1;count<=200;count++){
            Battle.Faction left=roster[(count-1)%roster.length];
            Battle.Faction right=roster[(count+1)%roster.length];
            Shipyard.Hull leftHull=hulls[(count-1)%hulls.length];
            Shipyard.Hull rightHull=hulls[(count+1)%hulls.length];
            // Always valid factory fittings, with ranged weapon and engine differences each rung.
            Shipyard.Design a=Shipyard.starter(left,leftHull),b=Shipyard.starter(right,rightHull);
            long seed=100000L+count;
            Battle battle=Battle.duel(seed,count,a,b,catalog);
            initiallyVisible(battle,count*2,"escalation "+count+"v"+count);
            int steps=count<=8?270:count<=64?90:count<=150?45:35;
            long t=System.nanoTime();long hash=run(battle,steps,"escalation "+count);
            double ms=(System.nanoTime()-t)/1e6;
            csv.add(String.format(Locale.ROOT,"%d,%d,%d,%.2f,%d,%d,%d,%d,%d,%016x",count,count*2,steps,ms,
                    battle.hitCount,battle.missileIntercepts,battle.destructions,battle.missiles().size(),battle.crafts().size(),hash));
            ticks+=(long)steps*count*2;
            if(count==1||count==10||count==25||count==50||count==100||count==150||count==200)
                System.out.println("STEP "+count+"v"+count+" ticks="+steps+" hits="+battle.hitCount
                        +" destroyed="+battle.destructions+" runtime-ms="+String.format(Locale.ROOT,"%.2f",ms));
        }
        for(int count:REPLAYS){
            Shipyard.Design a=Shipyard.starter(roster[count%6],hulls[count%4]);
            Shipyard.Design b=Shipyard.starter(roster[(count+2)%6],hulls[(count+1)%4]);
            int duration=count>=100?360:180;
            long first=run(Battle.duel(75331L+count,count,a,b,catalog),duration,"replay first "+count);
            long second=run(Battle.duel(75331L+count,count,a,b,catalog),duration,"replay second "+count);
            check(first==second,"determinism failure at "+count+"v"+count);
        }
        long mixedFirst=mixedFleet(catalog,762244L,480);
        long mixedReplay=mixedFleet(catalog,762244L,480);
        check(mixedFirst==mixedReplay,"200v200 mixed-faction/weapon carrier replay determinism");
        if(args.length>0)Files.write(Path.of(args[0]),csv,StandardCharsets.UTF_8);
        System.out.println("PASS full battle campaign: all sizes 1v1..200v200 (200 rungs), "
                +configurations+" valid weapon/ship arrangements, "+REPLAYS.length+" replay scales, "
                +ticks+" ship-ticks, wall-ms="+String.format(Locale.ROOT,"%.1f",(System.nanoTime()-start)/1e6));
    }
}
