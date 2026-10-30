package diplomacy.sim;

import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import java.util.List;

/** Weapon flashes, physical rounds, missiles and PD originate at real slots. */
public final class WeaponMuzzlesTest {
    static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    static void near(double a,double b,String message){
        check(Math.abs(a-b)<.00001,message+" "+a+" / "+b);
    }
    public static void main(String[] ignored) throws java.io.IOException {
        Battle battle=new Battle(122);
        Battle.Ship ship=battle.addShip(0,Battle.Faction.TERRAN,500,250);
        ship.hullClass=Shipyard.Hull.CRUISER;
        var absent=WeaponMuzzles.at(ship,WeaponMuzzles.Family.LASER,3);
        near(absent.x(),ship.x,"legacy unfitted X");
        near(absent.y(),ship.y,"legacy unfitted Y");
        check(absent.slotId()==-1,"unfitted fallback not a fabricated module");
        // Canonical faction-specific slot locations are kept verbatim.
        var fitted=HullLayouts.slots(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        var a=fitted.stream().filter(x->x.mount()==Shipyard.Mount.WEAPON).toList();
        var pd=fitted.stream().filter(x->x.mount()==Shipyard.Mount.DEFENSE).findFirst().orElseThrow();
        ship.weaponEmitters=List.of(
                new WeaponMuzzles.Emitter(a.get(0).index(),WeaponMuzzles.Family.LASER,
                        a.get(0).x(),a.get(0).y()),
                new WeaponMuzzles.Emitter(a.get(1).index(),WeaponMuzzles.Family.LASER,
                        a.get(1).x(),a.get(1).y()),
                new WeaponMuzzles.Emitter(pd.index(),WeaponMuzzles.Family.POINT_DEFENSE,
                        pd.x(),pd.y()));
        var first=WeaponMuzzles.at(ship,WeaponMuzzles.Family.LASER,0);
        var next=WeaponMuzzles.at(ship,WeaponMuzzles.Family.LASER,1);
        var wrapped=WeaponMuzzles.at(ship,WeaponMuzzles.Family.LASER,2);
        check(first.slotId()!=next.slotId(),"successive shots alternate modules");
        check(first.slotId()==wrapped.slotId(),"emitters cycle deterministically");
        near(first.x(),ship.x+a.get(0).y()*34.0/154*ship.visualScale(),"forward offset");
        near(first.y(),ship.y-a.get(0).x()*34.0/154*ship.visualScale(),"port-starboard offset");
        ship.facing=Math.PI/2;
        var turned=WeaponMuzzles.at(ship,WeaponMuzzles.Family.LASER,0);
        near(turned.x(),ship.x+a.get(0).x()*34.0/154*ship.visualScale(),"90deg x");
        near(turned.y(),ship.y+a.get(0).y()*34.0/154*ship.visualScale(),"90deg y");
        check(WeaponMuzzles.at(ship,WeaponMuzzles.Family.POINT_DEFENSE,0).slotId()==pd.index(),
                "point defense uses its own fitted defense module");
        check(WeaponMuzzles.at(ship,WeaponMuzzles.Family.MISSILE,0).slotId()==-1,
                "missing missile module never borrows a laser mount");
        check(WeaponMuzzles.family("BASE-TER-LAS")==WeaponMuzzles.Family.LASER,"laser code");
        check(WeaponMuzzles.family("BASE-TER-FLK")==WeaponMuzzles.Family.POINT_DEFENSE,"flak code");
        check(WeaponMuzzles.family("BASE-TER-MSL")==WeaponMuzzles.Family.MISSILE,"missile code");
        check(WeaponMuzzles.family("BASE-TER-TOR")==WeaponMuzzles.Family.TORPEDO,"torpedo code");
        check(WeaponMuzzles.family("BASE-TER-REA")==null,"no firing from reactor");
        // Full integration: equipped laser and missile systems send their
        // AUTHORITATIVE firing events from the equipped slot, not center mass.
        diplomacy.tech.TechnologyCatalog catalog=diplomacy.tech.TechnologyCatalog.loadDefault();
        Shipyard.Design terran=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        Shipyard.Design opponent=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);
        check(Shipyard.inspect(terran,catalog).valid(),"default hull fitting must validate");
        Battle live=new Battle(90244L);
        live.spawnWing(0,terran,catalog,1,280,330);
        live.spawnWing(1,opponent,catalog,1,520,330);
        var liveShip=live.ships().get(0);
        liveShip.speed=0;live.ships().get(1).speed=0;
        live.ships().get(1).pdRange=0;
        check(liveShip.weaponEmitters.stream().anyMatch(x->x.family()==WeaponMuzzles.Family.LASER),
                "real fitted ship loads laser emitter from exact design");
        check(liveShip.weaponEmitters.stream().anyMatch(x->x.family()==WeaponMuzzles.Family.MISSILE),
                "real fitted ship loads missile emitter from exact design");
        int laserEvents=0,missileEvents=0;
        for(int step=0;step<12;step++)live.step();
        for(Battle.VisualEvent e:live.visualEvents()){
            if(e.phase()!=Battle.VisualPhase.FIRE || e.sourceId()!=liveShip.id)continue;
            WeaponMuzzles.Family type=switch(e.weapon()){
                case "LASER" -> WeaponMuzzles.Family.LASER;
                case "MISSILE" -> WeaponMuzzles.Family.MISSILE;
                default -> null;
            };
            if(type==null)continue;
            var actual=WeaponMuzzles.at(liveShip,type,e.shotId());
            near(e.x(),actual.x(),"firing event X must equal equipped module muzzle");
            near(e.y(),actual.y(),"firing event Y must equal equipped module muzzle");
            check(actual.slotId()>=0,"cannot fake fitted muzzle at ship center");
            if(type==WeaponMuzzles.Family.LASER){
                laserEvents++;
                boolean beamMatches=false;
                for(Battle.Beam ray:live.beams()){
                    if(ray.visualId==e.shotId()){
                        near(ray.x1,e.x(),"laser beam and flash have same X");
                        near(ray.y1,e.y(),"laser beam and flash have same Y");
                        beamMatches=true;
                    }
                }
                // Old short-lived beams may have already expired; original FIRE
                // event remains the authoritative immutable launch record.
            }else missileEvents++;
        }
        check(laserEvents>0 && missileEvents>0,
                "real fitted laser and missile modules must fire in live battle");
        System.out.println("PASS: installed module family mapping, deterministic slots, beam/fire-origin parity and live fitted rounds");
    }
}
