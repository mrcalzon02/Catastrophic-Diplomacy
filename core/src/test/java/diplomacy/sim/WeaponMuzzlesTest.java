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
    public static void main(String[] ignored){
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
        System.out.println("PASS: installed module family mapping, deterministic alternating sockets, rotated muzzles and fallback");
    }
}
