package diplomacy.client;

import diplomacy.shipyard.OrdnanceFitting;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnanceGuidance;
import diplomacy.sim.OrdnancePropulsion;
import diplomacy.tech.TechnologyCatalog;

/** Real shipyard control mapping; pure logic, no OpenGL or fake taps. */
public final class GuidedShipyardPanelTest {
    private GuidedShipyardPanelTest(){}
    private static void check(boolean test,String message){
        if(!test)throw new AssertionError(message);
    }
    private static void reject(Runnable task,String message){
        try{task.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(message);
    }
    private static int missileSocket(Shipyard.Design design){
        for(int i=0;i<design.slots();i++)
            if(design.at(i)!=null&&design.at(i).component.endsWith("-MSL"))return i;
        throw new AssertionError("No missile hardpoint");
    }
    public static void main(String[] args)throws Exception{
        check(ShipyardPanel.guidedTabHit(815,590)==0,
                "MODULES tab cannot open from desktop or touch");
        check(ShipyardPanel.guidedTabHit(970,590)==2,
                "GUIDED tab has no touch target");
        check(ShipyardPanel.guidedTabHit(1135,590)==1,
                "TECH tab cannot open from guided editor");
        check(ShipyardPanel.guidedTabHit(1100,609)==-1,
                "Gutter cannot activate a hidden tab");
        for(int i=0;i<4;i++){
            float y=490-i*100;
            check(ShipyardPanel.guidedOptionHit(831,y)==i&&
                    ShipyardPanel.guidedOptionHit(1210,y)==i&&
                    ShipyardPanel.guidedOptionHit(980,y)==i,
                    "Row "+i+" must have full-width previous, next, and tap hit areas");
            check(ShipyardPanel.guidedOptionHit(985,y-62)==-1,
                    "Guided rows must not overlap their gutters");
        }
        check(ShipyardPanel.guidedOptionHit(700,490)==-1&&
                ShipyardPanel.guidedOptionHit(985,98)==-1,
                "Clicking outside the controls must not change a weapon");
        var catalog=TechnologyCatalog.loadDefault();
        var stock=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int socket=missileSocket(stock);
        var locked=ShipyardPanel.chooseGuided(stock,socket,1,1);
        check(locked==stock,"Stock launchers cannot acquire advanced motors");
        reject(()->ShipyardPanel.chooseGuided(stock,0,0,1),
                "Reactor cannot install guided-weapon modes");
        var advanced=stock.stage(socket,Shipyard.Track.SPECIALIST,4);
        check(Shipyard.inspect(advanced,catalog).valid(),
                "Tier-four missile module must physically fit");
        var salvo=ShipyardPanel.chooseGuided(advanced,socket,0,1);
        check(salvo.missileOrdnance.salvo()==OrdnanceDoctrine.Mode.RIPPLE,
                "Salvo row must actually change launch doctrine");
        var motor=ShipyardPanel.chooseGuided(salvo,socket,1,-1);
        check(motor.missileOrdnance.motor()==OrdnancePropulsion.Mode.TERMINAL_SPRINT,
                "Motor row backwards selection must select the last physical schedule");
        var seeker=ShipyardPanel.chooseGuided(motor,socket,2,-1);
        check(seeker.missileOrdnance.guidance()==OrdnanceGuidance.Mode.EVASIVE_WEAVE,
                "Seeker row must select advanced steering");
        var payload=ShipyardPanel.chooseGuided(seeker,socket,3,1);
        check(payload.ammoSelection.missile()==AmmunitionTable.Warhead.EMP,
                "Warhead row must skip locked warheads and select an allowed payload");
        check(stock.missileOrdnance.equals(OrdnanceFitting.Guided.STANDARD)
                &&stock.ammoSelection.missile()==AmmunitionTable.Warhead.STANDARD,
                "Editor must not mutate the original saved blueprint");
        var mount=ShipyardPanel.chooseRack(advanced,socket,1,-1);
        check(mount.launcher(socket).guided().motor()==OrdnancePropulsion.Mode.SILENT_GHOST,
                "Per-hardpoint selector must install a physical motor on the socket");
        check(mount.missileOrdnance.equals(OrdnanceFitting.Guided.STANDARD),
                "Rack editing must not change the family-wide legacy profile");
        check(Shipyard.encode(mount).startsWith("CD-SHIP-5|"),
                "Editing an individual launcher must use the socket-specific format");
        check(Shipyard.decode(Shipyard.encode(mount),catalog).launcher(socket).equals(
                mount.launcher(socket)),"Real rack edits must persist and reload");
        check(ShipyardPanel.chooseRack(stock,socket,1,1)==stock,
                "Stock socket must skip unavailable specialized rack engines");
        String encoded=Shipyard.encode(payload);
        var restored=Shipyard.decode(encoded,catalog);
        check(restored.missileOrdnance.equals(seeker.missileOrdnance)
                &&restored.ammoSelection.missile()==AmmunitionTable.Warhead.EMP,
                "Interactive missile choices must survive a real blueprint reload");
        check(ShipyardPanel.chooseGuided(payload,socket,0,-1)
                .missileOrdnance.salvo()==OrdnanceDoctrine.Mode.SINGLE,
                "Back arrow must restore the original salvo in one step");
        System.out.println("PASS touch-sized guided shipyard tabs, selection arrows, researched skip,"
                +" salvo/motor/seeker/warhead edits, saved blueprint and compatibility");
    }
}
