package diplomacy.sim;

import diplomacy.shipyard.HullLayouts;
import diplomacy.shipyard.Shipyard;
import java.util.List;

/** Physics-level proof of module-biased, shield-first layered surface erosion. */
public final class ShipSurfaceDamageTest {
    private ShipSurfaceDamageTest(){}
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static void equal(double a,double b,String label){
        if(StrictMath.abs(a-b)>1e-8)throw new AssertionError(label+" "+a+" != "+b);
    }
    private static int damaged(DirectionalDefense d){
        int count=0;
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++)
                if(d.tile(row,col).occupied&&d.tile(row,col).exposure()>1e-10)count++;
        return count;
    }
    public static void main(String[] args) {
        var slots=HullLayouts.slots(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        check(slots.size()>6,"Actual module positions required");
        var one=ShipDamageTargeting.module(slots,1,0,1337);
        var two=ShipDamageTargeting.module(slots,1,0,1337);
        check(one.equals(two),"Module targeting must be deterministic");
        check(one.slotIndex()>=0,"Hit must resolve a real shipyard socket");
        check(one.x()>=-0.89&&one.x()<=0.89&&one.y()>=-0.71&&one.y()<=0.71,
                "Scatter must remain within bounded hull-local coordinates");
        check(slots.stream().anyMatch(slot->slot.index()==one.slotIndex()),
                "Targeted module must actually exist");
        check(ShipDamageTargeting.module(slots,1,0,1338).slotIndex()>=0,
                "Alternative scattered module target missing");
        var world=ShipDamageTargeting.physical(0,10,StrictMath.PI/2);
        equal(world.x(),.4,"World collision rotates into bow-local coordinates");
        equal(world.y(),0,"World collision respects hull heading");
        check(ShipDamageTargeting.nearestModule(slots,one)==one.slotIndex(),
                "Physical impact must find the fitted module at that exact surface location");
        check(ShipDamageTargeting.subsystem("BASE-TER-REA")==Battle.Subsystem.REACTOR,
                "Damaged fitted reactor must affect reactor condition");
        check(ShipDamageTargeting.subsystem("BASE-TER-LAS")==Battle.Subsystem.WEAPONS,
                "Damaged laser mount must affect weapon systems");
        check(ShipDamageTargeting.subsystem("BASE-TER-FTR")==Battle.Subsystem.HANGAR,
                "Damaged fighter hangar must affect launch machinery");
        check(ShipDamageTargeting.subsystem("BASE-TER-ECM")==Battle.Subsystem.ELECTRONIC_WARFARE,
                "Electronic warfare modules must retain a localized damage identity");
        check(ShipDamageTargeting.radius(Battle.DamageType.TORPEDO)>
                ShipDamageTargeting.radius(Battle.DamageType.LASER),
                "Heavy warheads must affect a wider armor neighborhood");

        var shield=new DirectionalDefense(100,1000,1500);
        double initial=shield.totalArmor();
        var blocked=shield.impactSplash(1,0,.4,0,5,Battle.DamageType.LASER,0,1.15);
        check(blocked.shieldConsumed()>0,"Directional shield must absorb incoming strike");
        equal(blocked.armorConsumed(),0,"Shielded fire may not peel armor");
        equal(shield.totalArmor(),initial,"Unpenetrated shields leave exterior intact");

        var local=new DirectionalDefense(0,1000,1500);
        var fine=local.impactSplash(1,0,.4,0,10,Battle.DamageType.LASER,0,1.15);
        check(fine.armorConsumed()>0,"Unshielded laser peels original armor");
        int narrow=damaged(local);
        check(narrow>0&&narrow<30,"Narrow armor erosion remains localized");
        var distant=local.tileAt(-.75,-.48);
        equal(distant.exposure(),0,"Remote hull armor must not fade globally");

        var large=new DirectionalDefense(0,1000,1500);
        large.impactSplash(1,0,.4,0,10,Battle.DamageType.TORPEDO,0,3.15);
        check(damaged(large)>narrow,"Torpedo footprint must erode more cells than laser");
        check(large.totalArmor()>=0,"Armor cannot become negative");
        String packed=large.saveIntegrity();
        var loaded=new DirectionalDefense(0,1000,1500);
        loaded.restoreIntegrity(packed);
        equal(loaded.totalArmor(),large.totalArmor(),"Exact damage state survives a campaign save");
        for(int row=0;row<DirectionalDefense.GRID;row++)
            for(int col=0;col<DirectionalDefense.GRID;col++)
                equal(large.tile(row,col).exposure(),loaded.tile(row,col).exposure(),
                        "Per-tile erosion persists without spatial drift");

        var a=new DirectionalDefense(0,1000,1500);
        var b=new DirectionalDefense(0,1000,1500);
        for(int i=0;i<25;i++) {
            var point=ShipDamageTargeting.module(slots,i%2==0?1:-1,.3,i*13L);
            a.impactSplash(1,.3,point.x(),point.y(),12,Battle.DamageType.PLASMA,0.1,2.15);
            b.impactSplash(1,.3,point.x(),point.y(),12,Battle.DamageType.PLASMA,0.1,2.15);
        }
        check(a.fingerprint()==b.fingerprint(),"Repeated module impacts must replay exactly");
        System.out.println("PASS ship surface damage: shields-first, module aim, scatter, localized multi-tile peel, saved state and deterministic replay");
    }
}
