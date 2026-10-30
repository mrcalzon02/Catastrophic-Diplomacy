package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import java.util.ArrayList;
import java.util.List;

/** Real charge transitions only, no GL context, RNG, or combat mutations. */
public final class ShieldFxTimelineTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Battle.VisualEvent hit(long n,long tick,int id,double x,double y,String kind){
        return new Battle.VisualEvent(n,n,tick,Battle.VisualPhase.IMPACT,
                kind,1,99,id,x,y,0,0);
    }
    static boolean has(ShieldFxTimeline fx,ShieldFxTimeline.Kind kind,
                       DirectionalDefense.Facing facing){
        return fx.events().stream().anyMatch(e->e.kind==kind&&e.facing==facing);
    }
    public static void main(String[] args){
        Battle battle=new Battle(302L);
        Battle.Ship ship=battle.addShip(0,Battle.Faction.TERRAN,100,200);
        var front=DirectionalDefense.Facing.FORWARD;
        var port=DirectionalDefense.Facing.PORT;
        ShieldFxTimeline fx=new ShieldFxTimeline();
        fx.beforeStep(battle);
        battle.tick++;
        fx.afterStep(battle,List.of(hit(1,battle.tick,ship.id,120,200,"LASER")));
        check(fx.count()==0,"Undamaged shield cannot trigger on a cosmetic event");
        fx.beforeStep(battle);
        ship.defense.disruptShields(4);
        ship.shields=ship.defense.totalShields();
        battle.tick++;
        fx.afterStep(battle,List.of(hit(2,battle.tick,ship.id,120,200,"LASER")));
        check(fx.count()==1,"Real shield consumption creates one facing impact");
        var first=fx.events().get(0);
        check(first.kind==ShieldFxTimeline.Kind.IMPACT&&first.facing==front,
                "Impact must resolve to consumed forward facing");
        check(first.weapon==Battle.DamageType.LASER,
                "Real laser contact must retain weapon color family");
        check(!has(fx,ShieldFxTimeline.Kind.IMPACT,port),
                "Unstruck port shield must not flash");
        check(Math.abs(first.localAngle)<1,"Forward contact location is ship-local");
        check(fx.flash(ship.id,front)>0&&fx.flash(ship.id,port)==0,
                "Only damaged facing gets material flash");
        float base=ShieldSurfaceModel.opacity(.8f,battle.tick,ship.id,0);
        check(fx.opacity(ship,front,base)>base,"Contact brightens existing shield art");
        check(fx.opacity(ship,front,0)==0,"Collapsed shield cannot have phantom material");
        fx.beforeStep(battle);
        double remain=ship.defense.shields(front);
        ship.defense.disruptShields(remain);
        ship.shields=ship.defense.totalShields();
        battle.tick++;
        fx.afterStep(battle,List.of(hit(3,battle.tick,ship.id,121,201,"PLASMA")));
        check(has(fx,ShieldFxTimeline.Kind.COLLAPSE,front),
                "Zero-energy sector must fracture independently of other facings");
        check(ship.defense.shields(port)>0,"Other shields are still functioning");
        check(fx.events().stream().anyMatch(e->e.weapon==Battle.DamageType.PLASMA),
                "Plasma contact must select its different impact response");
        int paused=fx.count();
        float priorAge=fx.events().get(0).age;
        for(int i=0;i<8;i++)fx.advance(battle,.08f,false);
        check(fx.count()==paused&&fx.events().get(0).age==priorAge,
                "Paused combat must freeze shield FX");
        fx.beforeStep(battle);
        double restored=ship.defense.receiveShieldTransfer(2);
        check(restored>0,"Test fixture actually restores energy");
        ship.shields=ship.defense.totalShields();
        battle.tick++;
        fx.afterStep(battle,List.of());
        check(has(fx,ShieldFxTimeline.Kind.RESTART,front),
                "Reenergized collapsed sector must have local reboot sweep");
        check(!has(fx,ShieldFxTimeline.Kind.RESTART,port),
                "A healthy sector never reboots for another sector");
        check(fx.recharge(ship.id,front)>0,"Actual restoration drives recharge feedback");
        for(int i=0;i<20;i++)fx.advance(battle,.08f,true);
        check(fx.count()==0,"All four effect families expire with bounded lifetimes");
        check(fx.flash(ship.id,front)==0&&fx.recharge(ship.id,front)==0,
                "Charge/intensity decays rather than remaining stuck");
        // Real gain in an already energized sector yields periodic quiet arcs.
        fx.beforeStep(battle);
        ship.defense.receiveShieldTransfer(1);
        ship.shields=ship.defense.totalShields();
        long tick=battle.tick;
        while((tick+ship.id+front.ordinal()*5+1)%18!=0)tick++;
        battle.tick=tick+1;
        fx.afterStep(battle,List.of());
        check(has(fx,ShieldFxTimeline.Kind.RECHARGE,front),
                "Existing sector recharge drives a quiet filament");
        Battle mass=new Battle(303L);
        for(int i=0;i<400;i++)mass.addShip(i%2,Battle.Faction.TERRAN,i*4,0);
        fx.beforeStep(mass);
        List<Battle.VisualEvent> lots=new ArrayList<>();
        mass.tick++;
        for(int i=0;i<400;i++){
            Battle.Ship s=mass.ships().get(i);
            s.defense.disruptShields(4);s.shields=s.defense.totalShields();
            lots.add(hit(i+1,mass.tick,s.id,s.x+20,s.y,"KINETIC"));
        }
        fx.afterStep(mass,lots);
        check(fx.count()==ShieldFxTimeline.MAX_EVENTS,
                "400-ship shield VFX must honor the budget");
        fx.sync(battle);
        check(fx.count()==0&&fx.flash(ship.id,front)==0,
                "Battle switch must clear stale pulses and flash state");
        check(ShieldFxTimeline.centerAngle(front)==0f
                        &&ShieldFxTimeline.centerAngle(port)==90f,
                "Shield sector arcs align with local combat coordinates");
        System.out.println("PASS shield contact, facing, collapse, regeneration, restart, pause, 400-ship cap");
    }
}
