package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.FactionArt;
import diplomacy.tech.TechnologyCatalog;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Functional headless matrix. GUI/pixel checks are separate diagnostics. */
public final class HullCoverageBattleTest {
    private static void check(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }
    private static long run(Battle battle,int ticks){
        for(int i=0;i<ticks;i++)battle.step();
        for(Battle.Ship ship:battle.ships()){
            check(Double.isFinite(ship.x)&&Double.isFinite(ship.y),"Ship must move finitely");
            check(Double.isFinite(ship.hull)&&ship.hull>=0,"No invalid hull integrity");
            check(Double.isFinite(ship.armor)&&ship.armor>=0,"No invalid armor");
        }
        return battle.fingerprint();
    }
    public static void main(String[] args) throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        check(HullCoverageBattle.SIZES.size()==11,"One actual vessel of each of 11 canonical hull sizes");
        Set<Shipyard.Hull> distinct=new HashSet<>();
        for(Shipyard.Hull hull:HullCoverageBattle.SIZES)
            check(distinct.add(hull)&&hull==hull.spriteHull(),
                    "Fixture must use different actual source-art class "+hull);
        int duels=0,installed=0;
        boolean bow=false,stern=false,port=false,starboard=false;
        Set<String> modulePatterns=new HashSet<>();
        for(int size=0;size<HullCoverageBattle.SIZES.size();size++){
            Battle battle=HullCoverageBattle.duel(tech,size);
            check(battle.ships().size()==2,"1v1 must deploy precisely two actual fitted vessels");
            for(Battle.Ship ship:battle.ships()){
                check(ship.faction==HullCoverageBattle.SIDES[ship.team],
                        "A duel has ships wearing another faction's sprites");
                check(FactionArt.runtimePath(ship.faction,ship.hullClass)!=null,
                        "Duel selected a faction with no approved runtime art");
                check(ship.hullClass==HullCoverageBattle.SIZES.get(size),"Wrong sprite class in duel");
                check(!ship.impactHardpoints.isEmpty(),"Fitted hull must contain real aim points");
                check(!ship.installedSubsystems.isEmpty(),"Fitted hull subsystem locations missing");
                installed+=ship.impactHardpoints.size();
                StringBuilder pattern=new StringBuilder(ship.faction.name()).append(':').append(ship.hullClass);
                for(Shipyard.Slot slot:ship.impactHardpoints){
                    var actual=ship.installedSubsystems.get(slot.index());
                    check(slot.index()>=0&&slot.index()<ship.hullClass.slots.size(),
                            "Module position not linked to valid socket");
                    check(Double.isFinite(slot.x())&&Double.isFinite(slot.y()),
                            "Module coordinates invalid");
                    pattern.append('/').append(slot.index()).append('@')
                            .append(slot.x()).append(',').append(slot.y());
                    bow|=slot.y()>15;stern|=slot.y()<-15;
                    port|=slot.x()<-8;starboard|=slot.x()>8;
                }
                modulePatterns.add(pattern.toString());
                var aimed=ShipDamageTargeting.module(ship.impactHardpoints,1,.2,1234+size);
                check(aimed.slotIndex()>=0,"Module aiming may not silently become center mass");
                check(ship.impactHardpoints.stream().anyMatch(m->m.index()==aimed.slotIndex()),
                        "Module aiming must select installed equipment");
                // Controlled contact probes verify the same directional defense
                // carried by these concrete fitted hulls in the real battle.
                // Probe an independent shield/armor copy; do not modify the
                // actual duel's opening state merely to perform an assertion.
                DirectionalDefense surface=new DirectionalDefense(
                        ship.maxShields,ship.maxArmor,ship.maxHull);
                double before=surface.totalArmor();
                var shielded=surface.impactSplash(1,0,aimed.x(),aimed.y(),
                        .01,Battle.DamageType.LASER,0,1.15);
                check(shielded.armorConsumed()==0,
                        "Active shields must prevent armor peel on "+ship.hullClass);
                check(surface.totalArmor()==before,
                        "No armored pixel should erode from fully absorbed beam");
                surface.disruptShields(surface.totalShields());
                var exposed=surface.impactSplash(1,0,aimed.x(),aimed.y(),
                        3,Battle.DamageType.LASER,0,1.15);
                check(exposed.armorConsumed()>0&&surface.totalArmor()<before,
                        "After shield loss, real localized armor must erode");
                String saved=surface.saveIntegrity();
                DirectionalDefense restored=new DirectionalDefense(
                        ship.maxShields,ship.maxArmor,ship.maxHull);
                restored.restoreIntegrity(saved);
                check(restored.saveIntegrity().equals(surface.saveIntegrity()),
                        "All armor cells and directional shield levels must survive save roundtrip");
            }
            // Rebuild the exact same seed and compare complete real combat replay.
            long first=run(battle,100);
            long second=run(HullCoverageBattle.duel(tech,size),100);
            check(first==second,"1v1 replay drifted for "+HullCoverageBattle.SIZES.get(size));
            duels++;
        }
        check(modulePatterns.size()>=11,"Hull classes need distinct module layouts");
        check(bow&&stern&&port&&starboard,"Mount tests need real coverage across hull regions");

        Battle mixed=HullCoverageBattle.mixed(tech);
        check(mixed.ships().size()==22,"Mixed fixture must be 11 vs 11, not 200 vs 200");
        for(int side=0;side<2;side++){
            EnumSet<Shipyard.Hull> actual=EnumSet.noneOf(Shipyard.Hull.class);
            int ships=0;
            for(Battle.Ship ship:mixed.ships())if(ship.team==side){
                check(ship.faction==HullCoverageBattle.SIDES[side],
                        "Mixed-size fleet is wearing multiple unrelated civilization sheets");
                check(FactionArt.runtimePath(ship.faction,ship.hullClass)!=null,
                        "Actual fleet hull has no faction-matched sprite");
                check(actual.add(ship.hullClass),
                        "Two copies of "+ship.hullClass+" replaced another hull size");
                check(ship.x>40&&ship.x<1240&&ship.y>50&&ship.y<640,
                        "Small mixed battle must start on-screen");
                ships++;
            }
            check(ships==11&&actual.containsAll(HullCoverageBattle.SIZES),
                    "Every team requires one fitted ship of every size");
        }
        long first=run(mixed,180);
        long second=run(HullCoverageBattle.mixed(tech),180);
        check(first==second,"11v11 mixed-size replay must be deterministic");
        System.out.println("PASS hull coverage: "+duels+" x 1v1, 11v11 mixed, "
                +installed+" physical module anchors, "+modulePatterns.size()
                +" varied layouts, shields-first and deterministic fleet replay");
    }
}
