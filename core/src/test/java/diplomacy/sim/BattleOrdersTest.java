package diplomacy.sim;

import diplomacy.shipyard.Shipyard;

/** Headless regression: point-defense switches and ship orders are gameplay rules. */
public final class BattleOrdersTest {
    private BattleOrdersTest(){}
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static Battle threat(long seed,boolean fighters,BattleOrders.PointDefense mode){
        Battle battle=new Battle(seed);
        Battle.Ship launcher=battle.addShip(0,Battle.Faction.TERRAN,220,340);
        Battle.Ship defender=battle.addShip(1,Battle.Faction.CARNELIAN,400,340);
        launcher.speed=0;defender.speed=0;defender.pdRange=220;
        launcher.laserDamage=launcher.kineticDamage=launcher.plasmaDamage=launcher.particleDamage=0;
        launcher.torpedoDamage=0;
        launcher.missileDamage=fighters?0:52;
        launcher.missileAmmo=fighters?0:1;
        launcher.fighterReserve=fighters?1:0;
        defender.laserDamage=defender.missileDamage=0;
        defender.kineticDamage=defender.plasmaDamage=defender.particleDamage=defender.torpedoDamage=0;
        defender.missileAmmo=0;
        battle.setTeamPointDefense(1,mode);
        return battle;
    }
    private static void checkModes(boolean craft){
        for(BattleOrders.PointDefense mode:BattleOrders.PointDefense.values()){
            Battle b=threat(117,craft,mode);
            b.step();
            boolean mustFire=craft?mode.craft():mode.ordnance();
            check((b.ships().get(1).pdReload>0)==mustFire,
                    mode+" must "+(mustFire?"fire at":"ignore")+" "+(craft?"craft":"missiles"));
            check(b.teamOrders(1).pointDefense()==mode,"fleet orders exposed");
            check(b.ships().get(1).orders.pointDefense()==mode,"orders applied to deployed ships");
        }
    }
    private static void checkTargeting(){
        Battle b=new Battle(449);
        Battle.Ship attacker=b.addShip(0,Battle.Faction.TERRAN,100,300);
        Battle.Ship nearby=b.addShip(1,Battle.Faction.ZIPPLEDROW,270,300);
        Battle.Ship capital=b.addShip(1,Battle.Faction.DRAXIAN,390,300);
        nearby.hullClass=Shipyard.Hull.FRIGATE;
        capital.hullClass=Shipyard.Hull.DREADNOUGHT;
        attacker.speed=nearby.speed=capital.speed=0;
        attacker.missileDamage=nearby.missileDamage=capital.missileDamage=0;
        BattleOrders.Orders antiCapital=BattleOrders.DEFAULT
                .withEngagement(BattleOrders.Size.ESCORT,0,210)
                .withEngagement(BattleOrders.Size.CAPITAL,100,400);
        b.setShipOrders(attacker.id,antiCapital);
        b.step();
        check(attacker.lastTargetId==capital.id,"category zero priority disables smaller targets");
        check(nearby.orders.equals(BattleOrders.DEFAULT),"ship order must not overwrite enemy policies");
        check(capital.orders.equals(BattleOrders.DEFAULT),"unchanged ship orders remain default");
        // Friendly escort links are validated rather than silently allowing enemy anchors.
        try{
            b.setShipOrders(attacker.id,antiCapital.withAnchor(capital.id,140));
            throw new AssertionError("Enemy escort anchor accepted");
        }catch(IllegalArgumentException correct){/* expected */}
        Battle.Ship friend=b.addShip(0,Battle.Faction.TERRAN,125,340);
        b.setShipOrders(attacker.id,antiCapital.withAnchor(friend.id,140)
                .withPosition(BattleOrders.Position.FORMATION));
        check(attacker.anchorOffsetX==-25&&attacker.anchorOffsetY==-40,
                "formation anchor must capture actual deployed offset");
    }
    private static void checkReplay(){
        Battle a=threat(682,false,BattleOrders.PointDefense.INTERCEPT_ONLY);
        Battle b=threat(682,false,BattleOrders.PointDefense.INTERCEPT_ONLY);
        for(int tick=0;tick<80;tick++){a.step();b.step();}
        check(a.fingerprint()==b.fingerprint(),"mode controlled combat must remain replay deterministic");
        Battle future=new Battle(991);
        future.setTeamPointDefense(0,BattleOrders.PointDefense.HOLD_FIRE);
        check(future.addShip(0,Battle.Faction.TERRAN,100,100).orders.pointDefense()
                        ==BattleOrders.PointDefense.HOLD_FIRE,
                "team PD templates must affect future deployments");
    }
    private static void checkPersistence(){
        BattleOrders.Orders left=BattleOrders.DEFAULT
                .withPointDefense(BattleOrders.PointDefense.INTERCEPT_ONLY)
                .withTargeting(BattleOrders.Targeting.COOPERATE)
                .withEngagement(BattleOrders.Size.CAPITAL,80,480);
        BattleOrders.Orders right=BattleOrders.DEFAULT
                .withPointDefense(BattleOrders.PointDefense.HOLD_FIRE)
                .withPosition(BattleOrders.Position.CAUTIOUS)
                .withRetreat(0.35);
        String saved=BattleOrdersCodec.encode(left,right);
        BattleOrdersCodec.Pair restored=BattleOrdersCodec.decode(saved);
        check(restored.left().equals(left)&&restored.right().equals(right),
                "tactical doctrine saves must roundtrip exactly");
        String tampered=saved.replace("INTERCEPT_ONLY","BALANCED");
        try{
            BattleOrdersCodec.decode(tampered);
            throw new AssertionError("A changed orders file passed checksum validation");
        }catch(IllegalArgumentException expected){/* checksum protects source */}
    }
    public static void main(String[] args){
        checkModes(false);checkModes(true);
        check(BattleOrders.PointDefense.HOLD_FIRE.next()==BattleOrders.PointDefense.BALANCED,
                "PD control must cycle through all four modes");
        checkTargeting();checkReplay();checkPersistence();
        System.out.println("PASS orders: four live PD modes, craft/ordnance discrimination, class priorities, escort safety and deterministic replay");
    }
}
