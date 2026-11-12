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
            try{
                launcher.fighterBays=Battle.Ship.baysFor(
                        deck.install(socket,"BASE-TER-FTR"),
                        diplomacy.tech.TechnologyCatalog.loadDefault());
            }catch(java.io.IOException missingTechnology){
                throw new AssertionError("Technology catalogue unavailable",missingTechnology);
            }
            // Unlike missile ammo, a fighter must carry an actual pilot.
            // Manually created legacy ships do not come with crew onboard.
            launcher.crewRequired=16;
            launcher.crewBerths=24;
            launcher.lifeSupport=24;
            launcher.crewCount=24;
            // Isolate anti-aircraft doctrine: a cannon fighter fires physical
            // bullets which INTERCEPT_ONLY correctly engages as ordnance.
            // The laser-armed craft cannot pollute this craft-only fixture
            // with independent interceptable missile or ballistic projectiles.
            launcher.fighterProfile=FighterModel.standard(FighterModel.Hull.LASER_FIGHTER);
            // New live bay authority owns both launchable stock and fighter
            // type. Initialize this deliberately synthetic fixture through the
            // same legacy migration bridge instead of mutating only the old
            // aggregate reserve counter.
            launcher.setLegacyFighterReserve(1);
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
            boolean mustFire=craft?mode.craft():mode.ordnance();
            boolean animatedShot=false,realThreat=false;
            // Sensors may not see the launch before its light reaches the
            // defender. Accumulate actual physical intercepts, not a reload
            // state on tick one before ordnance even exists.
            for(int frame=0;frame<160;frame++){
                b.step();
                realThreat|=craft?b.ships().get(0).fightersLaunched>0:
                        b.visualEvents().stream().anyMatch(e->
                                e.team()==0&&e.phase()==Battle.VisualPhase.FIRE&&
                                e.weapon().equals("MISSILE"));
                animatedShot|=b.visualEvents().stream().anyMatch(e->
                        e.phase()==Battle.VisualPhase.FIRE&&
                                e.weapon().equals("POINT_DEFENSE")&&e.team()==1);
                if(animatedShot)break;
            }
            check(realThreat,"fixture must actually launch "+(craft?"fighters":"missiles"));
            check(animatedShot==mustFire,
                    mode+" must "+(mustFire?"fire at":"ignore")+" "+(craft?"craft":"missiles"));
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
        // Category priorities apply to contacts the ship actually receives.
        // A capital at 290 world units cannot be selected from omniscient
        // first-tick enemy coordinates before its sensor photons arrive.
        for(int frame=0;frame<100&&attacker.lastTargetId!=capital.id;frame++)b.step();
        check(attacker.lastTargetId==capital.id,
                "category zero priority must select the first causally acquired capital");
        check(nearby.orders.equals(BattleOrders.DEFAULT),"ship order must not overwrite enemy policies");
        check(capital.orders.equals(BattleOrders.DEFAULT),"unchanged ship orders remain default");

        // Equal class priorities are now a strict nearest-contact ordering,
        // with stable ID tie-breaks and no random/readiness score perturbation.
        Battle nearest=new Battle(450);
        Battle.Ship selector=nearest.addShip(0,Battle.Faction.TERRAN,100,300);
        Battle.Ship far=nearest.addShip(1,Battle.Faction.DRAXIAN,390,300);
        Battle.Ship close=nearest.addShip(1,Battle.Faction.ZIPPLEDROW,260,300);
        selector.speed=far.speed=close.speed=0;
        for(int frame=0;frame<100&&selector.lastTargetId!=close.id;frame++)nearest.step();
        check(selector.lastTargetId==close.id,
                "Default targeting must deterministically choose the nearest received contact");

        // A deliberate class priority is lexicographic, not a weighted soup:
        // higher configured priority overrides distance; zero remains exclusion.
        nearest.setShipOrders(selector.id,BattleOrders.DEFAULT
                .withEngagement(BattleOrders.Size.ESCORT,50,210)
                .withEngagement(BattleOrders.Size.CAPITAL,100,210));
        far.hullClass=Shipyard.Hull.DREADNOUGHT;
        close.hullClass=Shipyard.Hull.FRIGATE;
        for(int frame=0;frame<18&&selector.lastTargetId!=far.id;frame++)nearest.step();
        check(selector.lastTargetId==far.id,
                "Explicit class priority must override nearest-distance ordering");
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
    /** All four valid protected assets plus the escort share one priority
     * evaluation without allocating a temporary boxed-list per captain turn. */
    private static void checkFullProtectedThreatRoster(){
        Battle battle=new Battle(2026111102L);
        Battle.Ship captain=battle.addShip(0,Battle.Faction.TERRAN,100,330);
        Battle.Ship near=battle.addShip(1,Battle.Faction.DRAXIAN,240,330);
        Battle.Ship far=battle.addShip(1,Battle.Faction.ZIPPLEDROW,380,330);
        Battle.Ship[] protectedShips=new Battle.Ship[4];
        for(int i=0;i<protectedShips.length;i++)
            protectedShips[i]=battle.addShip(0,Battle.Faction.TERRAN,
                    110+i*9,385);
        Battle.Ship escort=battle.addShip(0,Battle.Faction.TERRAN,160,410);
        for(Battle.Ship ship:battle.ships()){
            ship.speed=0;
            ship.laserDamage=ship.kineticDamage=ship.plasmaDamage=0;
            ship.particleDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.fighterReserve=0;ship.droneReserve=0;
        }
        captain.standingOrders=ShipStandingOrders.DEFAULT;
        battle.setShipOrders(captain.id,
                BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD));
        for(int i=0;i<100&&captain.lastTargetId!=near.id;i++)battle.step();
        check(captain.lastTargetId==near.id,
                "Baseline captain failed to select nearby observed hostile");
        protectedShips[3].lastAttackerId=far.id;
        battle.setShipProtection(captain.id,escort.id,
                java.util.List.of(protectedShips[0].id,protectedShips[1].id,
                        protectedShips[2].id,protectedShips[3].id));
        for(int i=0;i<120&&captain.lastTargetId!=far.id;i++)battle.step();
        check(captain.lastTargetId==far.id,
                "Protected-asset attacker failed to override nearest hostile");
        check(captain.defendedShipIds.size()==4&&
                        captain.escortShipId==escort.id,
                "Full protected roster or escort identity was lost");
    }
    /**
     * A captain must select the nearest physically received contact, obey an
     * explicit higher-priority class order, and stop starting offensive shots
     * as soon as its only live sensor fails. An old selected target ID is
     * retention for decision cadence, NOT permission to keep shooting.
     */
    private static void checkCausalCaptainFire(){
        Battle battle=new Battle(202611100630L);
        Battle.Ship captain=battle.addShip(0,Battle.Faction.TERRAN,100,330);
        // Deliberately add the farther hostile FIRST: ship ID must not win
        // over measured distance when captain priorities are equal.
        Battle.Ship far=battle.addShip(1,Battle.Faction.DRAXIAN,340,330);
        Battle.Ship near=battle.addShip(1,Battle.Faction.ZIPPLEDROW,250,330);
        for(Battle.Ship hostile:new Battle.Ship[]{far,near}){
            battle.registerDiagnosticStationaryTarget(hostile.id);
            hostile.power=100;
            hostile.ecmCondition=1;
            hostile.opticalSignature=.01;
            hostile.hull=hostile.maxHull=10000;
        }
        captain.sensorRange=700;
        captain.infraredRange=captain.xrayRange=captain.graviticRange=0;
        captain.laserDamage=2;
        captain.kineticDamage=captain.plasmaDamage=captain.particleDamage=0;
        captain.missileDamage=captain.torpedoDamage=0;
        battle.setShipOrders(captain.id,
                BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD));
        check(!battle.hasTargetContact(captain,near,150),
                "Captain obtained a radar target before any echo arrived");
        for(int tick=0;tick<90;tick++)battle.step();
        check(battle.hasTargetContact(captain,near,150)&&
                        battle.hasTargetContact(captain,far,240),
                "Fixture never established actual radar-only live contacts");
        check(captain.lastTargetId==near.id,
                "Default captain ignored the nearer received hostile");
        long fires=battle.visualEvents().stream().filter(event->
                event.sourceId()==captain.id&&
                event.phase()==Battle.VisualPhase.FIRE&&
                event.weapon().startsWith("LASER")).count();
        check(fires>0,"Fixture never exercised physical offensive laser firing");

        far.hullClass=Shipyard.Hull.DREADNOUGHT;
        near.hullClass=Shipyard.Hull.FRIGATE;
        battle.setShipOrders(captain.id,BattleOrders.DEFAULT
                .withPosition(BattleOrders.Position.HOLD)
                .withEngagement(BattleOrders.Size.ESCORT,20,210)
                .withEngagement(BattleOrders.Size.CAPITAL,100,400));
        for(int tick=0;tick<18;tick++)battle.step();
        check(captain.lastTargetId==far.id,
                "Higher explicit capital priority did not override nearest contact");

        // The power plant remains live and the captain has a retained ID.
        // Only its radar/other live channels fail; cloaked hostiles remain
        // beyond their actual passive-optical acquisition footprint.
        captain.sensorsCondition=0;
        check(captain.power>3&&
                        !battle.hasTargetContact(captain,near,150)&&
                        !battle.hasTargetContact(captain,far,240),
                "Damaged electronics still supplied live targeting");
        long before=battle.visualEvents().stream().filter(event->
                event.sourceId()==captain.id&&
                event.phase()==Battle.VisualPhase.FIRE&&
                event.weapon().startsWith("LASER")).count();
        for(int tick=0;tick<24;tick++)battle.step();
        long after=battle.visualEvents().stream().filter(event->
                event.sourceId()==captain.id&&
                event.phase()==Battle.VisualPhase.FIRE&&
                event.weapon().startsWith("LASER")).count();
        check(captain.power>3&&after==before,
                "Stale selected hostile authorized a fresh laser shot");
        System.out.println("PASS captain contact: nearest, explicit class order and no stale-lock firing");
    }

    /**
     * Two captains with the same received hostile history must steer identically
     * even when the hidden current target coordinates differ. They may diverge
     * only after photons from those different positions actually arrive.
     */
    private static void checkCausalNavigation(){
        Battle left=new Battle(2026111101L);
        Battle right=new Battle(2026111101L);
        Battle.Ship a=left.addShip(0,Battle.Faction.TERRAN,100,330);
        Battle.Ship b=right.addShip(0,Battle.Faction.TERRAN,100,330);
        Battle.Ship hiddenA=left.addShip(1,Battle.Faction.DRAXIAN,320,330);
        Battle.Ship hiddenB=right.addShip(1,Battle.Faction.DRAXIAN,320,330);
        for(Battle.Ship captain:new Battle.Ship[]{a,b}){
            captain.sensorRange=1200;
            captain.infraredRange=captain.xrayRange=captain.graviticRange=0;
            captain.laserDamage=captain.kineticDamage=captain.plasmaDamage=0;
            captain.particleDamage=captain.missileDamage=captain.torpedoDamage=0;
            captain.maxFuel=captain.fuel=500;
        }
        for(Battle.Ship hostile:new Battle.Ship[]{hiddenA,hiddenB}){
            hostile.speed=0;hostile.vx=hostile.vy=0;
            hostile.laserDamage=hostile.kineticDamage=hostile.plasmaDamage=0;
            hostile.particleDamage=hostile.missileDamage=hostile.torpedoDamage=0;
            hostile.hull=hostile.maxHull=10000;
        }
        for(int frame=0;frame<120&&(a.lastTargetId==0||b.lastTargetId==0);frame++){
            left.step();right.step();
        }
        check(a.lastTargetId==hiddenA.id&&b.lastTargetId==hiddenB.id&&
                        a.navigationFixTick>=0&&
                        a.navigationFixTick==b.navigationFixTick,
                "Causal navigation fixture never established the same received fix");
        check(Math.abs(a.x-b.x)<1e-10&&Math.abs(a.y-b.y)<1e-10&&
                        Math.abs(a.facing-b.facing)<1e-10,
                "Identical received histories diverged before hidden-position test");

        // Move the real hostile bodies to different unobserved locations.
        // The very next captain step must not react to this hidden truth.
        hiddenA.x=450;hiddenA.y=260;
        hiddenB.x=850;hiddenB.y=470;
        left.step();right.step();
        check(Math.abs(a.x-b.x)<1e-10&&Math.abs(a.y-b.y)<1e-10&&
                        Math.abs(a.vx-b.vx)<1e-10&&Math.abs(a.vy-b.vy)<1e-10&&
                        Math.abs(a.facing-b.facing)<1e-10,
                "Captain steering leaked the hostile's current hidden coordinates");

        boolean diverged=false;
        for(int frame=0;frame<45;frame++){
            left.step();right.step();
            if(Math.hypot(a.x-b.x,a.y-b.y)>.05||
                    Math.abs(a.facing-b.facing)>.01){
                diverged=true;break;
            }
        }
        check(diverged&&
                        (a.navigationFixX!=b.navigationFixX||
                         a.navigationFixY!=b.navigationFixY),
                "Captains failed to react after different observations physically arrived");
        System.out.println("PASS causal navigation: hidden truth ignored until received observations diverge");
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
    /** A FREE captain must maneuver within gun range; only HOLD may park. */
    private static void checkFreeMovement(){
        Battle battle=new Battle(20261105L);
        Battle.Ship captain=battle.addShip(0,Battle.Faction.TERRAN,390,330);
        Battle.Ship opponent=battle.addShip(1,Battle.Faction.DRAXIAN,560,330);
        battle.setShipStandingOrders(captain.id,ShipStandingOrders.DEFAULT);
        battle.setShipCommandQueue(opponent.id,
                java.util.List.of(ShipCommandQueue.Command.hold(1000)));
        // Preserve the contact, not a random patrol waypoint, for this test.
        captain.laserDamage=captain.kineticDamage=captain.plasmaDamage=0;
        captain.particleDamage=captain.missileDamage=captain.torpedoDamage=0;
        opponent.laserDamage=opponent.kineticDamage=opponent.plasmaDamage=0;
        opponent.particleDamage=opponent.missileDamage=opponent.torpedoDamage=0;
        for(int t=0;t<250&&captain.lastTargetId!=opponent.id;t++)
            battle.step();
        check(captain.lastTargetId==opponent.id,
                "Free-navigation fixture never acquired the nearby opponent");
        // Eliminate approach momentum so the old brake-to-zero bug cannot
        // masquerade as tactical movement in the engagement band.
        captain.vx=0;captain.vy=0;
        double x=captain.x,y=captain.y,fuel=captain.fuel;
        for(int t=0;t<120;t++)battle.step();
        check(StrictMath.hypot(captain.x-x,captain.y-y)>.1,
                "FREE doctrine parked a ship inside its engagement range");
        check(captain.fuel<fuel,"FREE maneuver did not spend physical fuel");
        battle.setShipStandingOrders(captain.id,new ShipStandingOrders(
                BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD),50,50,0));
        captain.vx=0;captain.vy=0;
        x=captain.x;y=captain.y;
        for(int t=0;t<45;t++)battle.step();
        check(StrictMath.hypot(captain.x-x,captain.y-y)<1e-6,
                "Explicit HOLD doctrine moved the ship");
    }
    /** Every autonomous posture must keep moving after entering gun range. */
    private static void checkMobilePostures(){
        check(BattleOrders.DEFAULT.position()==BattleOrders.Position.FREE,
                "Free maneuver must be the default order");
        for(BattleOrders.Position posture:new BattleOrders.Position[]{
                BattleOrders.Position.FREE,BattleOrders.Position.KEEP_MOVING,
                BattleOrders.Position.CAUTIOUS,BattleOrders.Position.LAST_STAND}){
            Battle b=new Battle(7020+posture.ordinal());
            Battle.Ship mover=b.addShip(0,Battle.Faction.TERRAN,390,330);
            Battle.Ship contact=b.addShip(1,Battle.Faction.DRAXIAN,560,330);
            b.setShipOrders(mover.id,BattleOrders.DEFAULT.withPosition(posture));
            b.setShipCommandQueue(contact.id,
                    java.util.List.of(ShipCommandQueue.Command.hold(1000)));
            mover.laserDamage=mover.kineticDamage=mover.plasmaDamage=0;
            mover.particleDamage=mover.missileDamage=mover.torpedoDamage=0;
            contact.laserDamage=contact.kineticDamage=contact.plasmaDamage=0;
            contact.particleDamage=contact.missileDamage=contact.torpedoDamage=0;
            for(int t=0;t<250&&mover.lastTargetId!=contact.id;t++)b.step();
            check(mover.lastTargetId==contact.id,
                    "Autonomous posture failed to acquire target: "+posture);
            mover.vx=0;mover.vy=0;
            double x=mover.x,y=mover.y,fuel=mover.fuel;
            for(int t=0;t<180;t++)b.step();
            check(StrictMath.hypot(mover.x-x,mover.y-y)>.1,
                    "Non-hold posture parked at weapon range: "+posture);
            check(mover.fuel<fuel,"Autonomous maneuver used no reaction mass: "+posture);
        }
    }

    /** Standing hold-range is different from the existing hold-position order. */
    private static void checkHoldFiringRange(){
        Battle b=new Battle(99231);
        Battle.Ship keeper=b.addShip(0,Battle.Faction.TERRAN,360,330);
        Battle.Ship contact=b.addShip(1,Battle.Faction.DRAXIAN,565,330);
        keeper.laserDamage=keeper.kineticDamage=keeper.plasmaDamage=0;
        keeper.particleDamage=keeper.missileDamage=keeper.torpedoDamage=0;
        contact.laserDamage=contact.kineticDamage=contact.plasmaDamage=0;
        contact.particleDamage=contact.missileDamage=contact.torpedoDamage=0;
        b.setShipOrders(keeper.id,
                BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD_RANGE));
        b.setShipCommandQueue(contact.id,
                java.util.List.of(ShipCommandQueue.Command.hold(1000)));
        for(int t=0;t<180;t++)b.step();
        check(keeper.lastTargetId==contact.id,"Hold-range did not acquire opponent");
        check(StrictMath.hypot(keeper.x-360,keeper.y-330)<1e-6,
                "HOLD_RANGE moved a vessel already within its stand-off band");
        var original=BattleOrders.DEFAULT.withPosition(BattleOrders.Position.HOLD_RANGE);
        check(BattleOrdersCodec.decode(BattleOrdersCodec.encode(original,BattleOrders.DEFAULT))
                        .left().equals(original),
                "Hold-firing-range policy failed fleet order save roundtrip");
    }

    /** FREE searchers circle a reached rally point while contact is absent. */
    private static void checkMovingWithoutContact(){
        Battle b=new Battle(34421);
        Battle.Ship searcher=b.addShip(0,Battle.Faction.TERRAN,545,445);
        b.addShip(1,Battle.Faction.DRAXIAN,2000,1200);
        searcher.sensorRange=0;
        searcher.infraredRange=searcher.xrayRange=searcher.graviticRange=0;
        double fuel=searcher.fuel;
        for(int t=0;t<210;t++)b.step();
        check(searcher.lastTargetId==0,"Contactless patrol saw a phantom enemy");
        check(StrictMath.hypot(searcher.x-545,searcher.y-445)>.1,
                "Default search stopped within rally-point tolerance");
        check(searcher.fuel<fuel,"Mobile patrol obtained free thrust");
    }


    /**
     * L01 audit: observable movement and contact timing on real deterministic
     * 1v1 / 16v16 / 200v200 fixtures. All numbers are diagnostics, not FPS
     * gates. Sampling occurs AFTER the timed authoritative battle.step().
     * Never patch ship speed, fuel, AI orders, sensors or projectile rules here.
     */
    private static void reportCombatMovementAudit() throws Exception {
        var catalog=diplomacy.tech.TechnologyCatalog.loadDefault();
        for(int perSide:new int[]{1,16,200}){
            long seed=perSide==200?DemonstrationBattle.SEED:20261111L+perSide;
            Battle battle=perSide==200?DemonstrationBattle.create(catalog):
                    Battle.duel(seed,perSide,
                            Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT),
                            Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.DREADNOUGHT),
                            catalog);
            var ships=battle.ships();
            int count=ships.size();
            double[] previousX=new double[count],previousY=new double[count];
            double[] path=new double[count];
            for(int i=0;i<count;i++){
                previousX[i]=ships.get(i).x;
                previousY[i]=ships.get(i).y;
            }
            long[] stepNanos=new long[480];
            int sampled=0,firstTarget=-1,firstFix=-1,firstFire=-1,firstHit=-1;
            int fires=0,peakCraft=0;
            long lastVisualSequence=-1;
            long nominalMoving=0,nominalStill=0;
            for(int frame=0;frame<480&&!battle.finished();frame++){
                long start=System.nanoTime();
                battle.step();
                stepNanos[sampled++]=System.nanoTime()-start;
                for(int i=0;i<count;i++){
                    Battle.Ship ship=ships.get(i);
                    double travel=StrictMath.hypot(ship.x-previousX[i],
                            ship.y-previousY[i]);
                    if(Double.isFinite(travel))path[i]+=travel;
                    previousX[i]=ship.x;
                    previousY[i]=ship.y;
                    if(ship.alive()&&ship.speed>0&&ship.fuel>0&&
                            (ship.orders.position()==BattleOrders.Position.FREE||
                             ship.orders.position()==BattleOrders.Position.KEEP_MOVING)){
                        nominalMoving++;
                        if(travel<0.005)nominalStill++;
                    }
                    if(firstTarget<0&&ship.lastTargetId!=0)firstTarget=(int)battle.tick;
                    if(firstFix<0&&ship.navigationFixTick>=0)firstFix=(int)battle.tick;
                }
                if(firstHit<0&&battle.hitCount>0)firstHit=(int)battle.tick;
                var visuals=battle.visualEvents();
                for(int i=visuals.size()-1;i>=0;i--){
                    var event=visuals.get(i);
                    if(event.sequence()<=lastVisualSequence)break;
                    if(event.phase()==Battle.VisualPhase.FIRE){
                        fires++;
                        if(firstFire<0)firstFire=(int)battle.tick;
                    }
                }
                if(!visuals.isEmpty())
                    lastVisualSequence=visuals.get(visuals.size()-1).sequence();
                peakCraft=Math.max(peakCraft,battle.crafts().size());
            }
            double totalCapital=0,totalEscort=0;
            int capitals=0,escorts=0;
            for(int i=0;i<count;i++){
                if(BattleOrders.size(ships.get(i))==BattleOrders.Size.CAPITAL){
                    totalCapital+=path[i];
                    capitals++;
                }else{
                    totalEscort+=path[i];
                    escorts++;
                }
            }
            java.util.Arrays.sort(stepNanos,0,sampled);
            double p50=sampled==0?0:stepNanos[(sampled-1)/2]/1e6;
            double p95=sampled==0?0:
                    stepNanos[(int)Math.ceil(sampled*.95)-1]/1e6;
            double stillPercent=nominalMoving==0?0:
                    100.0*nominalStill/nominalMoving;
            System.out.printf(java.util.Locale.ROOT,
                    "CD-L01-MOTION perSide=%d seed=%d ticks=%d ships=%d "+
                    "capitals=%d escorts=%d meanCapitalPath=%.5f "+
                    "meanEscortPath=%.5f nominalStillPct_lt0.005PerTick=%.2f "+
                    "firstTargetTick=%d firstObservedFixTick=%d "+
                    "firstFireEventTick=%d firstHitTick=%d fireEvents=%d "+
                    "hits=%d peakCraft=%d stepP50ms=%.4f stepP95ms=%.4f "+
                    "candidateVisitCount=NOT_INSTRUMENTED cpuOnly=true fpsGate=false%n",
                    perSide,seed,battle.tick,count,capitals,escorts,
                    capitals==0?0:totalCapital/capitals,
                    escorts==0?0:totalEscort/escorts,
                    stillPercent,firstTarget,firstFix,firstFire,firstHit,
                    fires,battle.hitCount,peakCraft,p50,p95);
        }
    }

    public static void main(String[] args) throws Exception{
        checkFreeMovement();
        checkMobilePostures();
        checkHoldFiringRange();
        checkMovingWithoutContact();
        checkResourceOrders();
        checkModes(false);checkModes(true);
        check(BattleOrders.PointDefense.HOLD_FIRE.next()==BattleOrders.PointDefense.BALANCED,
                "PD control must cycle through all four modes");
        checkTargeting();checkFullProtectedThreatRoster();checkCausalCaptainFire();checkCausalNavigation();
        checkReplay();checkPersistence();
        reportCombatMovementAudit();
        System.out.println("PASS orders: four live PD modes, craft/ordnance discrimination, class priorities, escort safety and deterministic replay");
    }
}
