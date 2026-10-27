package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Real retreat simulation: no mocked escape flags, counts, or reward events. */
public final class BattleRetreatTest {
    private static void ok(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    private static ShipStandingOrders hullPolicy(double fraction){
        return new ShipStandingOrders(BattleOrders.DEFAULT.withRetreat(fraction),50,50,0);
    }
    private static void escapes(Battle battle,Battle.Ship ship,int limit){
        for(int t=0;t<limit&&ship.alive()&&!ship.escaped;t++)battle.step();
        ok(ship.escaped,"Ship never physically crossed its friendly rear boundary");
        ok(ship.hull>0&&ship.escapeTick>=ship.withdrawalTick&&ship.withdrawalTick>0,
                "Escaped ship was destroyed or had no withdrawal timeline");
        ok(ship.team==0?ship.x<=0:ship.x>=1280,
                "A ship escaped across the enemy's boundary");
    }
    public static void main(String[] argv)throws Exception{
        Battle first=Battle.duel(991,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship ship=first.ships().get(0);
        ship.x=42;ship.y=320;ship.hull=ship.maxHull*.40;
        first.setShipStandingOrders(ship.id,hullPolicy(.5));
        first.setShipCommandQueue(ship.id,List.of(ShipCommandQueue.Command.move(1100,320),
                ShipCommandQueue.Command.hold(300)));
        first.step();
        ok(ship.withdrawing&&ship.withdrawalTick==1,
                "Low hull failed to activate immediately");
        ok(ship.commandQueue.nextIndex()==0,
                "Waypoint consumed while retreating");
        ship.hull=ship.maxHull;ship.shields=ship.maxShields;
        for(int t=0;t<20&&ship.alive();t++)first.step();
        ok(ship.withdrawing,"Shield recharge or repairs stopped a committed retreat");
        escapes(first,ship,800);
        ok(first.escaped(0)==1&&first.destroyed(0)==0&&first.alive(0)==0,
                "Escaped vessel incorrectly counted as active or destroyed");
        ok(first.finished()&&first.alive(1)==1,
                "Full player evacuation did not concede the battlefield");
        ok(ship.commandQueue.nextIndex()==0,"Escaped vessel executed abandoned movement queue");

        Battle rightBattle=Battle.duel(992,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship right=rightBattle.ships().get(1);
        right.x=1238;right.y=320;
        rightBattle.setShipStandingOrders(right.id,
                new ShipStandingOrders(BattleOrders.DEFAULT,50,50,70));
        right.shields=right.maxShields*.5;
        escapes(rightBattle,right,800);
        ok(rightBattle.escaped(1)==1&&rightBattle.destroyed(1)==0&&rightBattle.alive(0)==1,
                "Right-side shield withdrawal failed");

        Battle stand=Battle.duel(993,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship defender=stand.ships().get(0);
        stand.setShipStandingOrders(defender.id,hullPolicy(.95).adjust(4,4));
        defender.hull=defender.maxHull*.4;
        stand.step();
        ok(!defender.withdrawing&&!defender.escaped,
                "Last Stand incorrectly started evacuation");

        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design ally=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.TERRAN,true,catalog);
        Shipyard.Design enemy=ScenarioCatalog.defaultDesign(ScenarioCatalog.Preset.DUEL,
                Battle.Faction.DRAXIAN,false,catalog);
        CampaignEncounter encounter=CampaignEncounter.start(1,
                new CampaignDeployment.Loadout(2,CampaignDeployment.Formation.LINE)
                        .withPlacement(0,80,320).withStanding(0,hullPolicy(.8)),
                ally,enemy,catalog);
        Battle combat=encounter.battle();
        Battle.Ship casualty=combat.ships().get(0);
        casualty.hull=casualty.maxHull*.4;
        escapes(combat,casualty,800);
        ok(combat.alive(0)==1&&combat.escaped(0)==1,
                "Escaped hull incorrectly remains in campaign survivors");
        for(int wave=1;wave<=encounter.waves();wave++){
            for(Battle.Ship hostile:combat.ships())if(hostile.team==1)hostile.hull=0;
            CampaignEncounter.Advance outcome=encounter.advance();
            ok(outcome==(wave==encounter.waves()?
                    CampaignEncounter.Advance.VICTORY:CampaignEncounter.Advance.NEXT_WAVE),
                    "An escaped ally broke sequential campaign wave victory rules");
        }
        ok(LevelEconomy.victoryCredits(LevelEconomy.level(1),2,combat.alive(0),true)
                <LevelEconomy.victoryCredits(LevelEconomy.level(1),2,2,true),
                "Evacuated hull incorrectly received survivor credit");

        CampaignEncounter withdrawn=CampaignEncounter.start(1,
                new CampaignDeployment.Loadout(1,CampaignDeployment.Formation.LINE)
                        .withStanding(0,hullPolicy(.85)),ally,enemy,catalog);
        Battle lone=withdrawn.battle();
        Battle.Ship last=lone.ships().get(0);
        last.x=40;last.hull=last.maxHull*.4;
        escapes(lone,last,800);
        ok(withdrawn.advance()==CampaignEncounter.Advance.DEFEAT,
                "Total fleet evacuation incorrectly won campaign mission");
        boolean error=false;
        try{first.escaped(3);}catch(IllegalArgumentException expected){error=true;}
        ok(error,"Invalid team count accepted");
        System.out.println("PASS withdrawal: physical left/right exits, hull/shield latch, last stand, "+
                "waypoint precedence, wave resolution and credit exclusion");
    }
}
