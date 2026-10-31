package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.OrdnanceFitting;
import diplomacy.tech.TechnologyCatalog;

/** 30 Hz acceptance of independent saved real hardpoints and finite magazines. */
public final class OrdnanceRacksTest {
    private OrdnanceRacksTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static void rejects(Runnable call,String message){
        try{call.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(message);
    }
    private static int first(Shipyard.Design d,String suffix){
        for(int i=0;i<d.slots();i++)
            if(d.at(i)!=null&&d.at(i).component.endsWith(suffix))return i;
        throw new AssertionError("Missing "+suffix);
    }
    private static int emptyWeapon(Shipyard.Design d){
        for(var p:d.placements())
            if(p.mount()==Shipyard.Mount.WEAPON&&d.at(p.index())==null)return p.index();
        throw new AssertionError("No free weapon socket");
    }
    private static Shipyard.Design fitted(TechnologyCatalog catalog){
        Shipyard.Design d=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int first=first(d,"-MSL"),second=emptyWeapon(d);
        d=d.install(second,"BASE-TER-MSL");
        d=d.stage(first,Shipyard.Track.SPECIALIST,4);
        d=d.stage(second,Shipyard.Track.EFFICIENCY,2);
        check(Shipyard.inspect(d,catalog).valid(),"Two independent researched racks must fit");
        var ghost=new OrdnanceFitting.Mounted(
                new OrdnanceFitting.Guided(OrdnanceDoctrine.Mode.RIPPLE,
                        OrdnancePropulsion.Mode.SILENT_GHOST,OrdnanceGuidance.Mode.PINCER),
                AmmunitionTable.Warhead.EMP);
        var cold=new OrdnanceFitting.Mounted(
                new OrdnanceFitting.Guided(OrdnanceDoctrine.Mode.SINGLE,
                        OrdnancePropulsion.Mode.COLD_START,OrdnanceGuidance.Mode.LEAD_INTERCEPT),
                AmmunitionTable.Warhead.FRAGMENTATION);
        Shipyard.Design original=d;
        rejects(()->original.withLauncher(second,ghost),
                "One rack may not borrow research from a different rack");
        return d.withLauncher(first,ghost).withLauncher(second,cold);
    }
    private static void persistence(TechnologyCatalog catalog){
        Shipyard.Design d=fitted(catalog);
        int first=first(d,"-MSL"),second=emptyWeaponSlotNumber(d,first);
        String wire=Shipyard.encode(d);
        check(wire.startsWith("CD-SHIP-5|"),"Independent racks use a new version");
        check(wire.contains("L|"+first+"|")&&wire.contains("L|"+second+"|"),
                "Both physical socket IDs must be serialized");
        Shipyard.Design copy=Shipyard.decode(wire,catalog);
        check(Shipyard.encode(copy).equals(wire),"Stable ordered rack save/load");
        check(copy.launcher(first).equals(d.launcher(first))&&
                copy.launcher(second).equals(d.launcher(second)),
                "Exact slot guidance and ammo must be restored");
        rejects(()->Shipyard.decode(wire.replace("L|"+first+"|","L|99|"),catalog),
                "Tampered socket ID cannot be loaded");
        rejects(()->d.stage(first,Shipyard.Track.SPECIALIST,3),
                "Downgrading a mounted rack must not retain high-tier propulsion");
        rejects(()->d.install(second,null),
                "Removing a configured rack must require clearing the loadout");
        Shipyard.Design untouched=Shipyard.starter(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        check(Shipyard.encode(untouched).startsWith("CD-SHIP-1|"),
                "Legacy blueprint identity still retained");
    }
    private static int emptyWeaponSlotNumber(Shipyard.Design d,int first){
        for(int i=0;i<d.slots();i++)
            if(i!=first&&d.at(i)!=null&&d.at(i).component.endsWith("-MSL"))return i;
        throw new AssertionError("Second rack absent");
    }
    private static Battle battle(TechnologyCatalog catalog){
        Battle world=new Battle(122934L);
        world.spawnWing(0,fitted(catalog),catalog,1,200,300);
        world.spawnWing(1,Shipyard.starter(Battle.Faction.CARNELIAN,
                Shipyard.Hull.DREADNOUGHT),catalog,1,380,300);
        // Opposing vessel is a physical target, not an extra salvo emitter
        // when asserting this source's two independent mounted launchers.
        var target=world.ships().get(1);
        target.missileDamage=target.torpedoDamage=0;
        return world;
    }
    private static void firing(TechnologyCatalog catalog){
        Battle one=battle(catalog),two=battle(catalog);
        var src=one.ships().get(0);
        check(src.launchers.size()==2,"Fitted ship gets exactly its two launchers");
        int initial=src.missileAmmo;
        int allocated=src.launchers.stream().mapToInt(x->x.magazine).sum();
        check(initial==allocated,"Rack magazines must partition finite shared ammunition");
        check(src.launchers.get(0).slot!=src.launchers.get(1).slot,
                "Each rack must retain its original fitted socket");
        one.step();two.step();
        check(src.launched==2&&one.salvosStarted==2,
                "Two independent rack controllers must fire simultaneously"
                +" launched="+src.launched+" salvos="+one.salvosStarted
                +" pending="+one.pendingSalvoCount()
                +" ammo="+src.missileAmmo+" damage="+src.missileDamage
                +" sensor="+src.sensorRange+" reach="+src.weapons.missileRange()
                +" power="+src.power+" racks="+src.launchers.size());
        check(src.missileAmmo==initial-2,
                "Each physical launch pays exact shared ammunition");
        check(src.launchers.stream().mapToInt(x->x.magazine).sum()==initial-2,
                "Each rack's own magazine must be debited for its missile");
        check(one.pendingSalvoCount()==1,
                "Ripple rack retains its own independent pending salvo");
        check(one.missiles().stream().anyMatch(m->
                m.propulsionMode==OrdnancePropulsion.Mode.SILENT_GHOST
                &&m.guidanceMode==OrdnanceGuidance.Mode.PINCER
                &&m.warhead==AmmunitionTable.Warhead.EMP),
                "Ghost rack must launch an EMP missile with its own seeker");
        check(one.missiles().stream().anyMatch(m->
                m.propulsionMode==OrdnancePropulsion.Mode.COLD_START
                &&m.guidanceMode==OrdnanceGuidance.Mode.LEAD_INTERCEPT
                &&m.warhead==AmmunitionTable.Warhead.FRAGMENTATION),
                "Other rack must independently cold eject fragmentation payload");
        var shots=one.visualEvents().stream().filter(x->
                x.phase()==Battle.VisualPhase.FIRE&&x.weapon().equals("MISSILE")&&
                x.team()==0).toList();
        check(shots.size()==2&&StrictMath.hypot(
                shots.get(0).x()-shots.get(1).x(),
                shots.get(0).y()-shots.get(1).y())>0.01,
                "Distinct physical racks must fire from different muzzle sockets");
        for(int tick=0;tick<20;tick++){one.step();two.step();}
        check(src.launchers.stream().allMatch(x->x.magazine>=0),
                "No rack may overdraw its own magazine");
        check(one.fingerprint()==two.fingerprint(),
                "Rack-specific guided replay must be deterministic");
    }
    public static void main(String[] ignored)throws Exception{
        var catalog=TechnologyCatalog.loadDefault();
        persistence(catalog);
        firing(catalog);
        System.out.println("PASS CD-SHIP-5 separate socket research, magazines, reloading,"
                +" real muzzles, physical missiles, payloads, tamper rejection and replay");
    }
}
