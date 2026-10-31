package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.shipyard.Shipyard;

/** Runtime-free battle view input/range correctness; no OpenGL required. */
public final class ShipInspectionPanelTest {
    private ShipInspectionPanelTest(){}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        Battle b=new Battle(88112L);
        Battle.Ship first=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship second=b.addShip(1,Battle.Faction.DRAXIAN,220,100);
        BattleRenderPoses poses=new BattleRenderPoses();
        poses.sync(b);
        check(ShipInspectionPanel.pick(b,poses,580,360,160,100,1)==first.id,
                "World-camera hit tests must select nearest actual ship, not UI pixels");
        check(ShipInspectionPanel.pick(b,poses,700,360,160,100,1)==second.id,
                "Enemy vessels must also be inspectable");
        check(ShipInspectionPanel.pick(b,poses,800,500,160,100,1)==-1,
                "Empty battlefield tap must not select a random vessel");
        check(ShipInspectionPanel.pick(b,poses,580,360,160,100,.5f)==-1,
                "Ship hit tests must scale correctly with camera zoom");
        ShipInspectionPanel panel=new ShipInspectionPanel();
        panel.select(first.id);
        check(panel.selected(b)==first&&panel.enabled(0)&&panel.enabled(4),
                "Selected ship starts with weapon ranges available");
        check(ShipInspectionPanel.contains(1000,300)&&!ShipInspectionPanel.contains(200,300),
                "Large right-hand sheet must capture only its actual bounds");
        check(ShipInspectionPanel.action(1240,585)==1,
                "Close control must be accessible by touch");
        check(ShipInspectionPanel.action(950,375)==10,
                "Laser row must toggle the actual laser range");
        check(ShipInspectionPanel.action(950,260)==15,
                "Torpedo range row must be touch-addressable");
        check(panel.tap(900,375)&&!panel.enabled(0)&&panel.enabled(4),
                "Tapping a weapon must toggle only its own range");
        check(panel.tap(1160,91)&&!panel.enabled(4),
                "Global toggle hides all weapon overlays");
        check(panel.tap(1160,91)&&panel.enabled(0)&&panel.enabled(6),
                "Global toggle restores all weapon overlays");
        check(!panel.tap(200,300),"Clicks outside the sheet must reach battlefield");
        check(ShipInspectionPanel.percent(40,80)==50,"Hull percentage uses live value");
        check(ShipInspectionPanel.percent(-10,80)==0,"Overkill cannot show negative hull");
        check(ShipInspectionPanel.percent(Double.NaN,80)==0,"Invalid reading must not crash UI");
        check(ShipInspectionPanel.percent(40,0)==0,"Absent shields must not divide by zero");
        check(Math.abs(ShipInspectionPanel.range(first,0)-530)<.00001,
                "Laser range must come from authoritative gun behavior");
        check(Math.abs(ShipInspectionPanel.range(first,4)-690)<.00001,
                "Guided range must use physical installed missile fire-control rules");
        first.weapons=new Shipyard.Weapons(1.5,1,1,1,1,1,
                1,1,1,1,1,1,1,1,1,1,1,1);
        check(Math.abs(ShipInspectionPanel.range(first,0)-795)<.00001,
                "Research-adjusted laser reach must change shown range overlay");
        first.missileDamage=0;
        check(ShipInspectionPanel.range(first,4)==0,
                "A ship without missile batteries must never display a phantom missile ring");
        first.missileAmmo=23;
        check(ShipInspectionPanel.ammo(first,4)==23,"Show authoritative finite ammo");
        first.missileReload=1.25;
        check(ShipInspectionPanel.reload(first,4)==1.25,"Show live reload timer");
        panel.tap(1240,585);
        check(panel.selected(b)==null,"Close hides the sheet without modifying the ship");
        panel.select(second.id);
        check(panel.selected(b)==second,"Selecting enemy must replace inspected vessel");
        panel.setMobile(true);
        check(ShipInspectionPanel.mobileAction(635,530,0)==20
                &&ShipInspectionPanel.mobileAction(1050,530,0)==21,
                "Android must have separate large touch targets for STATUS and WEAPONS tabs");
        check(ShipInspectionPanel.mobileAction(900,482,1)==10
                &&ShipInspectionPanel.mobileAction(900,194,1)==16,
                "Android weapon toggles must span >= 42 logical touch pixels per row");
        check(panel.tap(1020,533),"Android WEAPONS page must be selectable by tap");
        check(panel.tap(800,482)&&!panel.enabled(0),
                "Android taps must toggle actual weapon range instead of a cosmetic checkbox");
        check(panel.tap(1120,89)&&!panel.enabled(4),
                "Android show/hide ranges control must affect all real range circles");
        check(panel.tap(1235,580)&&panel.selected(b)==null,
                "Android panel close must be finger-sized and clear selection");
        System.out.println("PASS ship tap, pan-safe picking, right-side sheet hitboxes, live range/ammo/reload, zoom and overlays");
    }
}
