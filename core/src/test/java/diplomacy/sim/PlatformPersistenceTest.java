package diplomacy.sim;

import diplomacy.shipyard.FleetPresets;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.TouchGesture;
import diplomacy.tech.TechnologyCatalog;

public final class PlatformPersistenceTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed fleet save was accepted");
    }
    public static void main(String[] args) throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design left=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER);
        Shipyard.Design right=Shipyard.starter(Battle.Faction.MANDRALLY,Shipyard.Hull.DREADNOUGHT);
        String serialized=FleetPresets.encode(left,right,catalog);
        FleetPresets.Fleets restored=FleetPresets.decode(serialized,catalog);
        require(Shipyard.encode(restored.left()).equals(Shipyard.encode(left)),"left vessel persistence");
        require(Shipyard.encode(restored.right()).equals(Shipyard.encode(right)),"right vessel persistence");
        reject(()->FleetPresets.decode(serialized.replace("CD-FLEETS-1","CD-FLEETS-2"),catalog));
        reject(()->FleetPresets.decode(serialized.replace("T", "U"),catalog));
        reject(()->FleetPresets.decode(serialized.substring(0,serialized.length()-1),catalog));
        reject(()->FleetPresets.decode(serialized+"untrusted",catalog));
        TouchGesture touch=new TouchGesture();
        touch.start(0,80,400);
        require(touch.drag(1,80,280,26,true)==0,"other finger cannot scroll");
        require(touch.end(1,80,280)==false,"other finger cannot activate");
        require(touch.drag(0,80,394,26,true)==0,"small movement is still a tap");
        require(touch.end(0,80,394),"tap survives minor movement");
        touch.start(0,80,400);
        require(touch.drag(0,80,348,26,true)==2,"swipe advances two rows");
        require(!touch.end(0,80,348),"swipe never activates underlying slot");
        touch.start(0,800,660);
        require(touch.drag(0,800,590,26,false)==0,"toolbar drag never scrolls slot list");
        require(!touch.end(0,800,590),"toolbar drag never presses a button");
        System.out.println("PASS shared platform persistence: checksum, fittings, touch gesture/pointer safety");
    }
}
