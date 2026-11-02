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
        // This manually constructed PD fixture needs a real fitted FTR socket
        // to launch its hostile craft. Reserve alone is not flight hardware.
        if(fighters){
            Shipyard.Design deck=Shipyard.starter(Battle.Faction.TERRAN,
                    Shipyard.Hull.CRUISER);
            int socket=-1;
            for(Shipyard.Slot slot:deck.hull.slots)
                if(slot.mount()==Shipyard.Mount.SUPPORT){socket=slot.index();break;}
            if(socket<0)throw new AssertionError("Cruiser missing FTR support socket");
            launcher.fighterBays=Battle.Ship.baysFor(
                    deck.install(socket,"BASE-TER-FTR"));
        }
        launcher.fighterLaunchBays=launcher.fighterBays.size();
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
            boolean animatedShot=b.visualEvents().stream().anyMatch(e->
                    e.phase()==Battle.VisualPhase.FIRE
                            && e.weapon().equals("POINT_DEFENSE") && e.team()==1);
            check(animatedShot==mustFire,"PD firing visuals must follow actual mode and firing event");
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
        b.setTeamPointDefense(0,BattleOrders.PointDefense.INTERCEPT_ONLY);
        check(attacker.orders.position()==BattleOrders.Position.FORMATION
                        &&attacker.orders.anchorShipId()==friend.id,
                "PD-only fleet edits must not silently erase custom formation assignments");
        check(attacker.orders.pointDefense()==BattleOrders.PointDefense.INTERCEPT_ONLY,
                "Individual ships must receive updated PD doctrine");
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
    /** Real captains authorize fire in Battle, never only in menu text. */
    private static void checkResourceOrders(){
        var ration=ShipResourceOrders.DEFAULT
                .withAmmunition(ShipResourceOrders.AmmoPolicy.AUTO_RATION)
                .withReserve(0,1);
        check(ShipFireControl.rateFraction(ration,100,100)==1.0&&
                        ShipFireControl.rateFraction(ration,50,100)==1.0&&
                        Math.abs(ShipFireControl.rateFraction(ration,25,100)-.5)<1e-9&&
                        Math.abs(ShipFireControl.rateFraction(ration,10,100)-.2)<1e-9,
                "Automatic firing ration must be continuous below 50% stock");
        check(ShipFireControl.nextEligible(ration,25,100,2,100)==220,
                "At 25% remaining, firing must be half the ordinary cadence");
        check(!ShipFireControl.mayBegin(ration,true,1,100,0,1,0,0)&&
                        !ShipFireControl.mayBegin(ration,true,9,100,5,4,0,0)&&
                        ShipFireControl.mayBegin(ration,true,9,100,3,4,0,0),
                "Reserved last round or pending committed salvo could be overdrawn");
        var energyOnly=ShipResourceOrders.DEFAULT.withAmmunition(
                ShipResourceOrders.AmmoPolicy.ENERGY_ONLY);
        check(!ShipFireControl.mayBegin(energyOnly,true,100,100,0,1,0,0)&&
                        ShipFireControl.mayBegin(energyOnly,false,0,0,0,0,0,0),
                "Energy-only order must authorize energy but deny expendable ammunition");
        var hold=energyOnly.withFireIntent(ShipResourceOrders.FireIntent.HOLD_OFFENSE);
        check(!ShipFireControl.mayBegin(hold,false,0,0,0,0,0,0),
                "Offensive hold cannot permit laser discharge");

        Battle test=new Battle(90413L);
        Battle.Ship attacker=test.addShip(0,Battle.Faction.TERRAN,180,340);
        Battle.Ship victim=test.addShip(1,Battle.Faction.DRAXIAN,320,340);
        attacker.speed=victim.speed=0;
        attacker.kineticDamage=22;
        attacker.kineticAmmo=8;attacker.maxKineticAmmo=8;
        attacker.missileDamage=attacker.torpedoDamage=0;
        victim.missileDamage=victim.kineticDamage=victim.laserDamage=0;
        victim.plasmaDamage=victim.particleDamage=victim.torpedoDamage=0;
        test.setTeamPointDefense(1,BattleOrders.PointDefense.HOLD_FIRE);
        test.setShipStandingOrders(attacker.id,ShipStandingOrders.DEFAULT.withResources(energyOnly));
        for(int i=0;i<60;i++)test.step();
        check(attacker.kineticAmmo==8,
                "Battle ignored energy-only captain and consumed kinetic stock");
        check(test.visualEvents().stream().anyMatch(e->e.team()==0&&
                        e.phase()==Battle.VisualPhase.FIRE&&e.weapon().startsWith("LASER")),
                "Energy-only captain did not fire available real energy weapon");
        var saveLast=ShipResourceOrders.DEFAULT.withAmmunition(
                ShipResourceOrders.AmmoPolicy.HOLD_RESERVE).withReserve(0,1);
        test.setShipStandingOrders(attacker.id,ShipStandingOrders.DEFAULT.withResources(saveLast));
        for(int i=0;i<500;i++)test.step();
        check(attacker.kineticAmmo>=1,
                "Live salvo fire pierced the captain's protected final round");
    }
    public static void main(String[] args){
        checkResourceOrders();
        checkModes(false);checkModes(true);
        check(BattleOrders.PointDefense.HOLD_FIRE.next()==BattleOrders.PointDefense.BALANCED,
                "PD control must cycle through all four modes");
        checkTargeting();checkReplay();checkPersistence();
        System.out.println("PASS orders: four live PD modes, craft/ordnance discrimination, class priorities, escort safety and deterministic replay");
    }
}
