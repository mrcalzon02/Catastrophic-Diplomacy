package diplomacy.client;

import diplomacy.shipyard.OrdnanceFitting;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnanceGuidance;
import diplomacy.sim.OrdnancePropulsion;
import diplomacy.sim.RocketBarrage;
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
        var interceptor=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CRUISER)
                .withFighterHull(FighterModel.Hull.INTERCEPTOR);
        int ftr=-1;
        for(var slot:interceptor.placements())
            if(slot.mount()==Shipyard.Mount.SUPPORT){ftr=slot.index();break;}
        check(ftr>=0,"Missing physical FTR hangar");
        var stockFtr=interceptor.install(ftr,"BASE-TER-FTR");
        check(ShipyardPanel.chooseFighterSeeker(stockFtr).fighterSeeker==
                FighterModel.Seeker.INFRARED,"Unpaid seeker displayed as selectable");
        var advancedFtr=stockFtr.stage(ftr,Shipyard.Track.SPECIALIST,5);
        var opticalFtr=ShipyardPanel.chooseFighterSeeker(advancedFtr);
        var radarFtr=ShipyardPanel.chooseFighterSeeker(opticalFtr);
        check(opticalFtr.fighterSeeker==FighterModel.Seeker.OPTICAL&&
                radarFtr.fighterSeeker==FighterModel.Seeker.RADAR&&
                ShipyardPanel.chooseFighterSeeker(radarFtr).fighterSeeker==
                        FighterModel.Seeker.INFRARED,
                "Fighter touch/key mode cycle did not honor fitted alternatives");
        check(Shipyard.decode(Shipyard.encode(opticalFtr),catalog)
                        .fighterSeeker==FighterModel.Seeker.OPTICAL,
                "Panel-selected optical head was lost on blueprint reload");
        System.out.println("PASS UI fighter seeker selection and saved design");
        var stock=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        // The real approved PNG is the primary view. These interaction tests
        // remain headless: the fallback schematic still accepts the same sockets.
        var editor=new ShipyardPanel(catalog,(side,fit)->{},stock,stock,null);
        editor.show();
        check(editor.prefersHullArt(),"Shipyard must prefer real vessel artwork on open");
        var chosen=stock.placements().get(2);
        check(editor.tap(562f+chosen.x()*2.65f,343f+chosen.y()*2.65f)
                &&editor.selectedHardpoint()==chosen.index(),
                "Hardpoints must remain selectable in the schematic fallback");
        var alternatives=Shipyard.choices(catalog,stock.faction,stock.hull,chosen.index());
        check(!alternatives.isEmpty(),"Selected mount must offer compatible alternative modules");
        String originalFitting=editor.draftDesign().at(chosen.index())==null?null:
                editor.draftDesign().at(chosen.index()).component;
        editor.tap(43,502); // Preview first compatible catalogue entry without fitting
        check(editor.selectedHardpoint()==chosen.index(),
                "Choosing a palette candidate must not switch the selected ship mount");
        check(alternatives.get(0).id.equals(editor.previewedModuleId()),
                "Palette selection must populate a real candidate preview");
        check(java.util.Objects.equals(originalFitting,
                editor.draftDesign().at(chosen.index())==null?null:
                        editor.draftDesign().at(chosen.index()).component),
                "Previewing a module changed the fitted hull without FIT confirmation");
        editor.tap(100,245); // Explicit FIT SELECTED
        check(editor.previewedModuleId()==null&&
                editor.draftDesign().at(chosen.index())!=null&&
                alternatives.get(0).id.equals(editor.draftDesign().at(chosen.index()).component),
                "FIT SELECTED must install only the previewed compatible component");
        editor.tap(260,186); // Separate CLEAR action
        check(editor.draftDesign().at(chosen.index())==null,
                "Clear mount must remove only the selected socket fitting");
        editor.tap(43,502);editor.tap(100,245);
        check(editor.draftDesign().at(chosen.index())!=null,
                "Palette must restore a cleared fitting without switching sockets");
        editor.tap(715,597);
        check(!editor.prefersHullArt(),"Schematic toggle must remain available");
        editor.close();
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
        var terminal=ShipyardPanel.chooseRack(advanced,socket,1,-1);
        check(terminal.launcher(socket).guided().motor()==
                OrdnancePropulsion.Mode.TERMINAL_SPRINT,
                "First backward tap must select the actual last motor enum");
        var mount=ShipyardPanel.chooseRack(terminal,socket,1,-1);
        check(mount.launcher(socket).guided().motor()==OrdnancePropulsion.Mode.SILENT_GHOST,
                "Second backward tap must install Silent Ghost on this socket");
        check(mount.missileOrdnance.equals(OrdnanceFitting.Guided.STANDARD),
                "Rack editing must not change the family-wide legacy profile");
        check(Shipyard.encode(mount).startsWith("CD-SHIP-5|"),
                "Editing an individual launcher must use the socket-specific format");
        check(Shipyard.decode(Shipyard.encode(mount),catalog).launcher(socket).equals(
                mount.launcher(socket)),"Real rack edits must persist and reload");
        // A research-specialized guided hardpoint can be replaced by a rocket
        // without retaining an impossible seeker/warhead loadout on that slot.
        var rocketSwap=mount.install(socket,"BASE-TER-RKM");
        check(!rocketSwap.launcherFittings.containsKey(socket)&&
                        rocketSwap.rocketBarrage(socket).equals(
                                RocketBarrage.defaults(RocketBarrage.Size.MEDIUM)),
                "Replacing guided launcher with rocket retained stale seeker controls");
        var rocketSet=rocketSwap.withRocketBarrage(socket,16,
                RocketBarrage.Motor.BOOST_COAST);
        var guidedSwapBack=rocketSet.install(socket,"BASE-TER-MSL");
        check(!guidedSwapBack.rocketFittings.containsKey(socket)&&
                        !guidedSwapBack.launcherFittings.containsKey(socket)&&
                        guidedSwapBack.launcher(socket).guided().equals(
                                OrdnanceFitting.Guided.STANDARD),
                "Replacing rocket with guided launcher retained stale rocket program");
        check(Shipyard.decode(Shipyard.encode(rocketSet),catalog)
                        .rocketBarrage(socket).equals(rocketSet.rocketBarrage(socket)),
                "Shipyard rocket replacement was not persistable as a blueprint");
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
        // Rocket racks use the SAME touch-sized ordnance navigation without
        // inventing seeker/warhead controls for physically unguided weapons.
        var rocket=stock.install(socket,"BASE-TER-RKM");
        var configuredRocket=ShipyardPanel.chooseRocket(rocket,socket,0,1);
        check(configuredRocket.rocketBarrage(socket).count()==16,
                "Rocket volley selector did not cycle 8 to 16");
        configuredRocket=ShipyardPanel.chooseRocket(configuredRocket,socket,1,1);
        check(configuredRocket.rocketBarrage(socket).motor()==
                        RocketBarrage.Motor.BOOST_COAST,
                "Rocket motor selector did not cycle sustained to boost/coast");
        check(ShipyardPanel.chooseRocket(configuredRocket,socket,0,-1)
                        .rocketBarrage(socket).count()==8,
                "Rocket previous-arrow must reverse the volley selection");
        var savedRocket=Shipyard.decode(Shipyard.encode(configuredRocket),catalog);
        check(savedRocket.rocketBarrage(socket).equals(configuredRocket.rocketBarrage(socket))
                        &&Shipyard.encode(configuredRocket).startsWith("CD-SHIP-6|"),
                "Touch-selected rocket program did not survive shipyard SAVE/LOAD");
        check(rocket.rocketBarrage(socket).equals(RocketBarrage.defaults(
                        RocketBarrage.Size.MEDIUM)),
                "Changing rocket options mutated the original active design");
        reject(()->ShipyardPanel.chooseRocket(stock,socket,0,1),
                "Guided missile socket must reject unguided rocket programming");
        reject(()->ShipyardPanel.chooseRocket(rocket,socket,2,1),
                "Rocket has no seeker or warhead programming axis");
        System.out.println("PASS touch-sized guided shipyard tabs, selection arrows, researched skip,"
                +" salvo/motor/seeker/warhead edits, saved blueprint and compatibility");
    }
}
