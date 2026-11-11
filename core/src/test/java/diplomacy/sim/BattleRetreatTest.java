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
    private static Shipyard.Design oneWeapon(TechnologyCatalog tech,String module){
        Shipyard.Design design=Shipyard.bareHull(
                Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        for(Shipyard.Slot slot:design.hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON){
                design=design.install(slot.index(),module);
                ok(Shipyard.inspect(design,tech).valid(),
                        "Invalid fitted withdrawal-defense fixture "+module);
                return design;
            }
        throw new AssertionError("Dreadnought has no weapon socket for "+module);
    }
    private record WithdrawalPdResult(int missileFires,int beamFires,
                                      int illuminatedTicks,double powerSpent,
                                      boolean withdrew) {}
    private static WithdrawalPdResult withdrawalParticleDefense(
            TechnologyCatalog tech,BattleOrders.PointDefense mode,boolean powered){
        Battle battle=new Battle(778811L+mode.ordinal()+(powered?17:0));
        battle.spawnWing(0,oneWeapon(tech,"BASE-TER-MSL"),tech,1,700,320);
        battle.spawnWing(1,oneWeapon(tech,"BASE-TER-PPD"),tech,1,900,320);
        Battle.Ship attacker=battle.ships().get(0);
        Battle.Ship defender=battle.ships().get(1);
        attacker.speed=0;attacker.vx=attacker.vy=0;
        attacker.maxPower=attacker.power=10000;attacker.powerRegen=0;
        attacker.maxHeat=10000;attacker.heat=0;
        attacker.fighterReserve=attacker.droneReserve=attacker.repairDroneReserve=0;
        defender.powerRegen=0;
        if(!powered)defender.power=0;
        double beforePower=defender.power;
        battle.setShipStandingOrders(defender.id,new ShipStandingOrders(
                BattleOrders.DEFAULT.withPointDefense(mode).withRetreat(.95),
                50,50,0));
        defender.hull=defender.maxHull*.60;
        int missileFires=0,beamFires=0;
        long seen=0;
        for(int t=0;t<180&&defender.alive()&&!defender.escaped;t++){
            battle.step();
            for(Battle.VisualEvent event:battle.visualEvents()){
                if(event.sequence()<=seen)continue;
                seen=event.sequence();
                if(event.team()==attacker.team&&
                        event.phase()==Battle.VisualPhase.FIRE&&
                        "MISSILE".equals(event.weapon()))missileFires++;
                if(event.team()==defender.team&&
                        event.sourceId()==defender.id&&
                        event.phase()==Battle.VisualPhase.FIRE&&
                        "PARTICLE_PD".equals(event.weapon()))beamFires++;
            }
        }
        int illuminated=0;
        for(ParticlePointDefense.Mount gun:defender.particlePdMounts)
            illuminated+=gun.illuminatedTicks;
        return new WithdrawalPdResult(missileFires,beamFires,illuminated,
                StrictMath.max(0,beforePower-defender.power),defender.withdrawing);
    }
    public static void main(String[] argv)throws Exception{
        // A commanded HOLD should arrest momentum by coasting, not
        // continuously charge a fuel store for movement with no engine burn.
        Battle drifting=new Battle(990L);
        Battle.Ship inert=drifting.addShip(0,Battle.Faction.TERRAN,140,300);
        Battle.Ship distant=drifting.addShip(1,Battle.Faction.DRAXIAN,1700,300);
        inert.weaponsCondition=0;distant.weaponsCondition=0;
        inert.fuel=42;inert.vx=90;
        drifting.setShipCommandQueue(inert.id,List.of(ShipCommandQueue.Command.hold(300)));
        for(int t=0;t<40;t++)drifting.step();
        ok(inert.fuel==42,"A coasting HOLD command incorrectly consumes thrust fuel");

        Battle first=Battle.duel(991,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship ship=first.ships().get(0);
        // Start near the friendly edge to validate a physical crossing
        // within this finite smoke-test budget under gee-limited thrust.
        ship.x=5;ship.y=320;ship.hull=ship.maxHull*.40;
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

        // Cautious battle orders must order a real extraction BEFORE a damaged
        // captain can burn its last fuel fleeing thousands of units away.
        Battle cautious=Battle.duel(994,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship cautiousHull=cautious.ships().get(0);
        cautiousHull.x=5;cautiousHull.y=315;
        cautious.setShipOrders(cautiousHull.id,BattleOrders.DEFAULT
                .withPosition(BattleOrders.Position.CAUTIOUS).withRetreat(.65));
        cautiousHull.hull=cautiousHull.maxHull*.42;
        cautious.step();
        ok(cautiousHull.withdrawing&&cautiousHull.withdrawalTick==1,
                "Cautious low-hull officer continued runaway search instead of real retreat");
        escapes(cautious,cautiousHull,800);
        ok(cautious.destroyed(0)==0,"Cautious extraction fabricated a ship destruction");

        Battle rightBattle=Battle.duel(992,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship right=rightBattle.ships().get(1);
        right.x=1275;right.y=320;
        rightBattle.setShipStandingOrders(right.id,
                new ShipStandingOrders(BattleOrders.DEFAULT,50,50,70));
        right.defense.disruptShields(right.maxShields*.7);
        right.shields=right.defense.totalShields();
        ok(right.shields<right.maxShields*.7,
                "Directional shields were not actually drained for escape test");
        rightBattle.step();
        ok(right.withdrawing&&right.withdrawalTick==1,
                "Actual shield-loss threshold did not initiate first-tick withdrawal");
        escapes(rightBattle,right,800);
        ok(rightBattle.escaped(1)==1&&rightBattle.destroyed(1)==0&&rightBattle.alive(0)==1,
                "Right-side shield withdrawal failed");

        // Protected fuel is an order controlling thrust, not another tank.
        Battle fuelGuard=Battle.duel(995,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship guard=fuelGuard.ships().get(0);
        guard.weaponsCondition=0;fuelGuard.ships().get(1).weaponsCondition=0;
        guard.fuel=25.03;guard.maxFuel=100;
        guard.propulsion=SublightPropulsion.Drive.FUSION_TORCH;
        var guardOrder=ShipStandingOrders.DEFAULT.withResources(
                ShipResourceOrders.DEFAULT
                        .withFuelPolicy(ShipResourceOrders.FuelPolicy.PROTECT_RESERVE)
                        .withFuelReserve(25).withEconomyStart(60));
        fuelGuard.setShipStandingOrders(guard.id,guardOrder);
        fuelGuard.setShipCommandQueue(guard.id,
                List.of(ShipCommandQueue.Command.move(1100,320)));
        for(int t=0;t<1200&&guard.alive();t++)fuelGuard.step();
        ok(guard.fuel>=25-1e-7&&guard.fuel<25.03,
                "Real navigation ignored the protected fuel fraction");
        ok(!guard.withdrawing,
                "A hold-reserve order incorrectly forced automatic withdrawal");
        ok(guardOrder.resources().thrustSpeedFactor(guard)<1,
                "Economy band was not enforced during low-fuel navigation");

        // Auto-withdraw uses the saved threshold, aborts waypoint pursuit,
        // and unlocks the SAME protected tank to cross the friendly boundary.
        Battle fuelEscape=Battle.duel(996,1,Battle.Faction.TERRAN,Battle.Faction.DRAXIAN);
        Battle.Ship escapeFuel=fuelEscape.ships().get(0);
        escapeFuel.x=5;escapeFuel.y=320;
        escapeFuel.maxFuel=100;escapeFuel.fuel=25;
        var autoFuel=ShipStandingOrders.DEFAULT.withResources(
                ShipResourceOrders.DEFAULT
                        .withFuelPolicy(ShipResourceOrders.FuelPolicy.WITHDRAW_AT_RESERVE)
                        .withFuelReserve(25));
        fuelEscape.setShipStandingOrders(escapeFuel.id,autoFuel);
        fuelEscape.setShipCommandQueue(escapeFuel.id,
                List.of(ShipCommandQueue.Command.move(1100,320)));
        fuelEscape.step();
        ok(escapeFuel.withdrawing&&escapeFuel.withdrawalTick==1
                        &&escapeFuel.fuel<25,
                "Fuel threshold did not commit an immediate paid physical retreat");
        escapes(fuelEscape,escapeFuel,800);
        ok(escapeFuel.commandQueue.nextIndex()==0,
                "Fuel-triggered retreat executed an abandoned waypoint");

        TechnologyCatalog pdTech=TechnologyCatalog.loadDefault();
        WithdrawalPdResult defensive=withdrawalParticleDefense(
                pdTech,BattleOrders.PointDefense.INTERCEPT_ONLY,true);
        ok(defensive.withdrew()&&defensive.missileFires()>0,
                "Withdrawal PPD fixture never entered retreat under real missile attack");
        ok(defensive.beamFires()>0&&defensive.illuminatedTicks()>0&&
                        defensive.powerSpent()>0,
                "Withdrawing ship failed to perform paid particle point defense");
        WithdrawalPdResult hold=withdrawalParticleDefense(
                pdTech,BattleOrders.PointDefense.HOLD_FIRE,true);
        ok(hold.missileFires()>0&&hold.beamFires()==0&&hold.illuminatedTicks()==0,
                "HOLD_FIRE withdrawal emitted particle defense");
        WithdrawalPdResult powerless=withdrawalParticleDefense(
                pdTech,BattleOrders.PointDefense.INTERCEPT_ONLY,false);
        ok(powerless.missileFires()>0&&powerless.beamFires()==0&&
                        powerless.illuminatedTicks()==0,
                "Powerless withdrawing PPD emitted free defensive damage");

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
        CampaignEncounter encounter=CampaignEncounter.start(10,
                new CampaignDeployment.Loadout(2,CampaignDeployment.Formation.LINE)
                        .withPlacement(0,80,320).withStanding(0,hullPolicy(.8)),
                ally,enemy,catalog);
        Battle combat=encounter.battle();
        Battle.Ship casualty=combat.ships().get(0);
        casualty.x=5; // Position is only for extraction mechanics, not deployment.
        casualty.hull=casualty.maxHull*.4;
        escapes(combat,casualty,800);
        ok(combat.alive(0)==1&&combat.escaped(0)==1&&combat.engaged(0)==1,
                "Escaped hull incorrectly remains in campaign survivors");
        for(int wave=1;wave<=encounter.waves();wave++){
            for(Battle.Ship hostile:combat.ships())if(hostile.team==1)hostile.hull=0;
            CampaignEncounter.Advance outcome=encounter.advance();
            ok(outcome==(wave==encounter.waves()?
                    CampaignEncounter.Advance.VICTORY:CampaignEncounter.Advance.NEXT_WAVE),
                    "An escaped ally broke sequential campaign wave victory rules");
        }
        ok(LevelEconomy.victoryCredits(LevelEconomy.level(10),2,combat.alive(0),true)
                <LevelEconomy.victoryCredits(LevelEconomy.level(10),2,2,true),
                "Evacuated hull incorrectly received survivor credit");

        // If the last active captain is only still INSIDE the field while
        // retreating, a just-destroyed enemy is not a free mission victory.
        CampaignEncounter unearned=CampaignEncounter.start(1,
                new CampaignDeployment.Loadout(1,CampaignDeployment.Formation.LINE)
                        .withPlacement(0,75,350).withStanding(0,hullPolicy(.85)),
                ally,enemy,catalog);
        Battle lastFight=unearned.battle();
        lastFight.ships().get(0).hull=lastFight.ships().get(0).maxHull*.4;
        lastFight.step();
        ok(lastFight.alive(0)==1&&lastFight.engaged(0)==0&&lastFight.withdrawing(0)==1,
                "Retreating ship falsely classified as field-holding");
        for(Battle.Ship foe: lastFight.ships())if(foe.team==1)foe.hull=0;
        ok(unearned.advance()==CampaignEncounter.Advance.DEFEAT,
                "Committed but not-yet-escaped retreat wrongly earned victory");
        CampaignEncounter withdrawn=CampaignEncounter.start(1,
                new CampaignDeployment.Loadout(1,CampaignDeployment.Formation.LINE)
                        .withStanding(0,hullPolicy(.85)),ally,enemy,catalog);
        Battle lone=withdrawn.battle();
        Battle.Ship last=lone.ships().get(0);
        last.x=5;last.hull=last.maxHull*.4;
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
