package diplomacy.sim;

import diplomacy.fx.WeaponFxProfile;
import diplomacy.tech.TechnologyCatalog;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** The APK demo and headless proving ground must run the same 400-ship fixture. */
public final class DemonstrationBattleTest {
    private static void check(boolean good,String detail) {
        if(!good)throw new AssertionError(detail);
    }
    private static long exercise(TechnologyCatalog catalog) {
        Battle battle=DemonstrationBattle.create(catalog);
        // There must be only two purpose-fitted support operators per side:
        // a specialist DRN tender and an independent REP+XFR relay craft.
        int[] service={0,0},relays={0,0},repairs={0,0},repairDrones={0,0};
        for(Battle.Ship ship:battle.ships()){
            if(ship.support.remoteRepairRate()>0)repairs[ship.team]++;
            if(ship.support.shieldTransferRate()>0)relays[ship.team]++;
            if(ship.support.repairDroneCapacity()>0)repairDrones[ship.team]++;
            if(ship.support.remoteRepairRate()>0||ship.support.shieldTransferRate()>0
                    ||ship.support.repairDroneCapacity()>0)service[ship.team]++;
        }
        for(int side=0;side<2;side++){
            check(service[side]==2,"Only 2 dedicated support hulls per 200, team "+side);
            check(relays[side]==1&&repairs[side]==1&&repairDrones[side]==1,
                    "Specialist capabilities must not spread across ordinary ships");
        }
        EnumSet<Battle.DamageType> fired=EnumSet.noneOf(Battle.DamageType.class);
        Set<Long> fireIds=new HashSet<>();
        long last=0;
        int impacts=0,peakOrdnance=0,peakCraft=0;
        for(int tick=0;tick<480;tick++) {
            battle.step();
            peakOrdnance=Math.max(peakOrdnance,battle.missiles().size());
            peakCraft=Math.max(peakCraft,battle.crafts().size());
            for(Battle.VisualEvent event:battle.visualEvents()) {
                if(event.sequence()<=last)continue;
                check(event.sequence()>last,"visual event ordering");
                last=event.sequence();
                Battle.DamageType type;
                if(event.phase()==Battle.VisualPhase.FIRE)
                    fireIds.add(event.shotId());
                try { type=Battle.DamageType.valueOf(event.weapon()); }
                catch(IllegalArgumentException notWeapon) { continue; }
                if(event.phase()==Battle.VisualPhase.FIRE) {
                    fired.add(type);
                    check(WeaponFxProfile.forType(type)!=null,"missing modular weapon profile "+type);
                }
                if(event.phase()==Battle.VisualPhase.IMPACT) {
                    check(event.shotId()==0||fireIds.contains(event.shotId()),
                            "impact without a corresponding physical firing event");
                    check(Double.isFinite(event.x())&&Double.isFinite(event.y()),"invalid impact position");
                    impacts++;
                }
            }
        }
        for(Battle.DamageType type:new Battle.DamageType[]{
                Battle.DamageType.LASER,Battle.DamageType.KINETIC,
                Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE,
                Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO})
            check(fired.contains(type),"200v200 demo never fired "+type);
        check(impacts>0 && battle.hitCount>0,"demo failed to resolve weapon hits");
        check(peakOrdnance>0 && peakCraft>0,"demo failed to launch missiles or craft");
        System.out.println("PASS demo 200v200: six weapon families fired, impacts="+impacts+
                " hits="+battle.hitCount+" craft="+peakCraft+" ordnance="+peakOrdnance+
                " fingerprint="+Long.toHexString(battle.fingerprint()));
        return battle.fingerprint();
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        check(exercise(catalog)==exercise(catalog),"massed demonstration replay must be deterministic");
    }
}
