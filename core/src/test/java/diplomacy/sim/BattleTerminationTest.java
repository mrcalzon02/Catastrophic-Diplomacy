package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Genuine finite auto-battles, no synthetic damage or fabricated victories. */
public final class BattleTerminationTest {
    private BattleTerminationTest(){}
    private static void ok(boolean actual,String message){
        if(!actual)throw new AssertionError(message);
    }
    private static void disarm(Battle.Ship ship){
        ship.laserDamage=ship.plasmaDamage=ship.particleDamage=0;
        ship.kineticDamage=ship.missileDamage=ship.torpedoDamage=0;
        ship.kineticAmmo=ship.missileAmmo=ship.torpedoAmmo=0;
        ship.fighterReserve=ship.droneReserve=0;
    }
    private static void policy(){
        long max=BattleTermination.HARD_LIMIT_TICKS;
        long idle=BattleTermination.DEPLETED_MIN_TICKS;
        ok(BattleTermination.evaluate(idle-30,0,true,false,false)==
                BattleTermination.Reason.NONE,"Ended without minimum observation");
        ok(BattleTermination.evaluate(idle,0,true,true,false)==
                BattleTermination.Reason.NONE,"Killed an incoming physical shell");
        ok(BattleTermination.evaluate(idle,0,true,false,true)==
                BattleTermination.Reason.NONE,"Interrupted viable physical withdrawal");
        ok(BattleTermination.evaluate(idle,0,false,false,false)==
                BattleTermination.Reason.NONE,"Declared an armed fleet disarmed");
        ok(BattleTermination.evaluate(BattleTermination.STALLED_MIN_TICKS,
                BattleTermination.STALLED_MIN_TICKS-30,false,false,false)==
                BattleTermination.Reason.NONE,"Ended shortly after fresh hull damage");
        ok(BattleTermination.evaluate(BattleTermination.STALLED_MIN_TICKS,
                0,false,false,false)==BattleTermination.Reason.STALEMATE,
                "Armed but permanently ineffective stalemate never terminates");
        ok(BattleTermination.evaluate(max-30,max-300,false,false,false)==
                BattleTermination.Reason.NONE,"Ended before hard deadline");
        ok(BattleTermination.evaluate(max,max-1,false,true,true)==
                BattleTermination.Reason.TIME_LIMIT,
                "Maximum duration must terminate even continuously fired ordnance");
        try{BattleTermination.evaluate(-1,0,true,false,false);
            throw new AssertionError("Negative battle clocks accepted");}
        catch(IllegalArgumentException expected){/* correct */}
    }
    private static void realDisarmedBattle(){
        Battle battle=new Battle(76143L);
        Battle.Ship left=battle.addShip(0,Battle.Faction.TERRAN,370,340);
        Battle.Ship right=battle.addShip(1,Battle.Faction.DRAXIAN,890,340);
        disarm(left);disarm(right);
        ok(!battle.finished()&&!battle.unresolved(),"Fresh battle already drawn");
        for(int i=0;i<BattleTermination.DEPLETED_MIN_TICKS+30
                &&!battle.finished();i++)battle.step();
        ok(battle.terminationReason()==BattleTermination.Reason.STALEMATE,
                "Two disarmed physical survivors never reached a draw");
        ok(left.hull>0&&right.hull>0&&battle.alive(0)==1&&battle.alive(1)==1,
                "Stalemate fabricated a hull loss");
        ok(battle.engaged(0)==1&&battle.engaged(1)==1,
                "Stalemate fabricated a flight, escape or retirement");
        long tick=battle.tick,hash=battle.fingerprint();
        battle.step();
        ok(battle.tick==tick&&battle.fingerprint()==hash,
                "Drawn battle continues consuming simulation time/resources");
        ok(battle.destroyed(0)==0&&battle.destroyed(1)==0,
                "Unresolved battle invented a kill");
        System.out.println("PASS real disarmed 1v1 stalemate at tick "+tick);
    }
    private static void campaignDraw() {
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        var left=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var right=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE);
        CampaignEncounter encounter=CampaignEncounter.start(1,1,left,right,technology);
        Battle battle=encounter.battle();
        for(Battle.Ship ship:battle.ships()){
            ship.hull=ship.maxHull;
            ship.shields=ship.maxShields;
            ship.power=ship.maxPower;
        }
        battle.tick=BattleTermination.HARD_LIMIT_TICKS-1;
        battle.step();
        ok(battle.unresolved()&&battle.terminationReason()==
                BattleTermination.Reason.TIME_LIMIT,"Hard finite horizon not enforced");
        ok(encounter.advance()==CampaignEncounter.Advance.DRAW,
                "Campaign granted a wave or false victory at time limit");
        ok(encounter.wave()==1&&battle.engaged(0)>0,
                "Campaign draw changed wave or erased surviving roster");
        Battle physicalWin=new Battle(17);
        physicalWin.addShip(0,Battle.Faction.TERRAN,300,300);
        physicalWin.addShip(1,Battle.Faction.DRAXIAN,800,300).hull=0;
        ok(physicalWin.finished()&&!physicalWin.unresolved(),
                "Actual fleet destruction was mistaken for a draw");
        System.out.println("PASS campaign unresolved time limit, unchanged wave and actual-win distinction");
    }
    public static void main(String[] args){
        policy();
        realDisarmedBattle();
        campaignDraw();
        System.out.println("PASS deterministic finite battle end policy, damage grace, delayed threats and survivor accounting");
    }
}
