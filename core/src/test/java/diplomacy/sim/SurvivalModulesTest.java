package diplomacy.sim;

import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.SurvivalHardwareCatalog;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.SkirmishTechnologyRules;

/** Functional acceptance for survival suites, unsafe power and fleet rescue telemetry. */
public final class SurvivalModulesTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount mount){
        for(Shipyard.Slot s:hull.slots)if(s.mount()==mount)return s.index();
        throw new AssertionError("No "+mount);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design empty=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        var baseline=Shipyard.fit(empty,catalog);
        check(baseline.survival().instability()==0 &&
                baseline.survival().escapePods()==0,
                "Base hull must not inherit unsafe gear or free escape pods");
        for(var family:SurvivalHardwareCatalog.FAMILIES){
            Shipyard.Mount mount=switch(family.category()){
                case "systems" -> Shipyard.Mount.SYSTEM;
                case "habitat" -> Shipyard.Mount.HABITAT;
                case "support" -> Shipyard.Mount.SUPPORT;
                default -> throw new AssertionError("Wrong physical mounting category");
            };
            int index=socket(empty.hull,mount);
            String id="BASE-TER-"+family.code();
            check(Shipyard.accepts(empty.hull,index,catalog.base(id)),
                    "Physical shipyard slot rejects "+id);
            Shipyard.Design ship=empty.install(index,id);
            check(Shipyard.inspect(ship,catalog).valid(),
                    "Survival module cannot fit operating dreadnought: "+id+" "+
                            Shipyard.inspect(ship,catalog).explanation());
            check(Shipyard.decode(Shipyard.encode(ship),catalog).at(index).component.equals(id),
                    "Survival identity lost in blueprint "+id);
            var fitted=Shipyard.fit(ship,catalog);
            check(switch(family.code()){
                case "DMC" -> fitted.damageControl()>baseline.damageControl();
                case "HCR" -> fitted.survival().crewProtection()>0;
                case "CIC" -> fitted.survival().commandProtection()>0;
                case "FBN" -> fitted.survival().fuelBonus()>0;
                case "AMV" -> fitted.magazineCapacity()>baseline.magazineCapacity();
                case "EPD" -> fitted.survival().escapePods()>0;
                case "RCB" -> fitted.survival().recoveryBeacon();
                case "PGO","JPB","IBR","IRG" ->
                    fitted.powerRegen()>baseline.powerRegen() &&
                            fitted.survival().instability()>0;
                case "IST" -> fitted.coolingRate()>baseline.coolingRate()
                        &&fitted.survival().instability()>0;
                default -> false;
            },"Survival module is mechanically inert: "+id);
            if(family.requiredTier()>0){
                boolean blocked=false;
                try{CampaignShipyardRules.requireAllowed(ship,ship.faction,
                        family.requiredTier()-1,1);
                }catch(IllegalArgumentException expected){blocked=true;}
                check(blocked,"Premature campaign unlock "+id);
                blocked=false;
                try{SkirmishTechnologyRules.unrestricted()
                        .withStage(family.requiredTier()-1).requireAllowed(ship,catalog);
                }catch(IllegalArgumentException expected){blocked=true;}
                check(blocked,"Premature skirmish unlock "+id);
            }
        }

        check(SurvivalHardwareCatalog.VARIANTS.size()==48,
                "Exactly 48 additional survival variants required");
        for(var v:SurvivalHardwareCatalog.VARIANTS){
            var parent=SurvivalHardwareCatalog.find(v.root());
            check(parent!=null&&v.requiredTier()>=parent.requiredTier(),
                    "Variant bypasses parent tier "+v.code());
            Shipyard.Mount mount=switch(parent.category()){
                case "systems" -> Shipyard.Mount.SYSTEM;
                case "habitat" -> Shipyard.Mount.HABITAT;
                case "support" -> Shipyard.Mount.SUPPORT;
                default -> throw new AssertionError("Invalid survival mount");
            };
            int slot=socket(empty.hull,mount);
            String id="BASE-TER-"+v.code();
            var module=catalog.base(id);
            check(module!=null&&Shipyard.accepts(empty.hull,slot,module),
                    "Missing actual physical variant "+id);
            Shipyard.Design design=empty.install(slot,id);
            check(Shipyard.inspect(design,catalog).valid(),"Invalid fitted variant "+id);
            check(Shipyard.decode(Shipyard.encode(design),catalog)
                    .at(slot).component.equals(id),"Variant blueprint lost ID "+id);
            var fitted=Shipyard.fit(design,catalog);
            check(switch(v.root()){
                case "DMC" -> fitted.damageControl()>baseline.damageControl();
                case "HCR" -> fitted.survival().crewProtection()>0;
                case "CIC" -> fitted.survival().commandProtection()>0;
                case "FBN" -> fitted.survival().fuelBonus()>0;
                case "AMV" -> fitted.magazineCapacity()>baseline.magazineCapacity();
                case "EPD" -> fitted.survival().escapePods()>0;
                case "RCB" -> fitted.survival().recoveryBeacon();
                case "PGO","JPB","IBR","IRG" -> fitted.powerRegen()>baseline.powerRegen()
                        &&fitted.survival().instability()>0;
                case "IST" -> fitted.coolingRate()>baseline.coolingRate()
                        &&fitted.survival().instability()>0;
                default -> false;
            },"Survival variant has no real battle effect "+id);
            if(v.requiredTier()>0){
                boolean rejected=false;
                try{CampaignShipyardRules.requireAllowed(design,design.faction,
                        v.requiredTier()-1,1);
                }catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Premature campaign authorization "+id);
                rejected=false;
                try{SkirmishTechnologyRules.unrestricted()
                        .withStage(v.requiredTier()-1).requireAllowed(design,catalog);
                }catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Premature skirmish authorization "+id);
            }
        }
        Shipyard.Design fieldPod=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-EPDP");
        Shipyard.Design advancedPod=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-EPDA");
        check(Shipyard.fit(fieldPod,catalog).survival().escapePods()<
                Shipyard.fit(advancedPod,catalog).survival().escapePods(),
                "Alternate escape hardware must change finite rescue capacity");

        // Beacon variants have different actual post-victory effectiveness:
        // without a surviving friendly vessel no wreck yields salvage.
        Shipyard.Design weakBeacon=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-RCBP");
        Shipyard.Design strongBeacon=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-RCBA");
        Battle weakRecovery=new Battle(8091L),strongRecovery=new Battle(8091L);
        weakRecovery.spawnWing(0,weakBeacon,catalog,1,100,100);
        weakRecovery.spawnWing(0,empty,catalog,1,100,200);
        strongRecovery.spawnWing(0,strongBeacon,catalog,1,100,100);
        strongRecovery.spawnWing(0,empty,catalog,1,100,200);
        check(strongRecovery.ships().get(0).recoveryStrength>
                weakRecovery.ships().get(0).recoveryStrength,
                "Advanced beacon has no physical recovery improvement");
        weakRecovery.destroyHull(weakRecovery.ships().get(0),null);
        strongRecovery.destroyHull(strongRecovery.ships().get(0),null);
        check(strongRecovery.recoverySalvageCredits(0)>
                weakRecovery.recoverySalvageCredits(0) &&
                strongRecovery.recoverySalvageCredits(0)<=8000,
                "Beacon salvage must vary by actual hardware without refunding hull");

        Shipyard.Design bunker=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-FBN");
        Shipyard.Design vault=empty.install(socket(empty.hull,Shipyard.Mount.SUPPORT),
                "BASE-TER-AMV");
        Battle storeBattle=new Battle(942L);
        storeBattle.spawnWing(0,empty,catalog,1,100,100);
        storeBattle.spawnWing(0,bunker,catalog,1,100,200);
        storeBattle.spawnWing(0,vault,catalog,1,100,300);
        Battle.Ship ordinary=storeBattle.ships().get(0);
        Battle.Ship armoredFuel=storeBattle.ships().get(1);
        Battle.Ship isolatedMag=storeBattle.ships().get(2);
        double fuelFraction=ordinary.fuel/ordinary.maxFuel;
        Battle.breachStores(ordinary,Battle.DamageType.TORPEDO,250,true);
        Battle.breachStores(armoredFuel,Battle.DamageType.TORPEDO,250,true);
        Battle.breachStores(isolatedMag,Battle.DamageType.TORPEDO,250,true);
        check(armoredFuel.fuel/armoredFuel.maxFuel>ordinary.fuel/ordinary.maxFuel,
                "Armored bunker failed to protect finite propellant");
        // Physical missile losses are integer-valued, but damage to a small
        // magazine may be fractional on a single hit and must accumulate.
        double ordinaryLoss=ordinary.maxMissileAmmo-ordinary.missileAmmo+
                ordinary.missileStoreExposure;
        double protectedLoss=isolatedMag.maxMissileAmmo-isolatedMag.missileAmmo+
                isolatedMag.missileStoreExposure;
        check(ordinaryLoss/ordinary.maxMissileAmmo >
                protectedLoss/isolatedMag.maxMissileAmmo,
                "Magazine vault failed to reduce fractional inventory exposure");
        for(int hit=0;hit<12;hit++){
            Battle.breachStores(ordinary,Battle.DamageType.TORPEDO,250,true);
            Battle.breachStores(isolatedMag,Battle.DamageType.TORPEDO,250,true);
        }
        check(ordinary.missileAmmo<ordinary.maxMissileAmmo,
                "Repeated penetrating impacts never destroyed small-store ammunition");
        check(isolatedMag.missileAmmo>=ordinary.missileAmmo,
                "Protected magazine lost more real ammunition under identical impacts");
        check(fuelFraction>ordinary.fuel/ordinary.maxFuel,
                "Penetrating torpedo strike must remove some unprotected fuel");

        // The exact destruction routine used by combat must preserve only
        // finite physical pod capacity and activate ONLY fitted beacons.
        int podSlot=-1,beaconSlot=-1;
        for(Shipyard.Slot slot:empty.hull.slots)if(slot.mount()==Shipyard.Mount.SUPPORT){
            if(podSlot<0)podSlot=slot.index();
            else {beaconSlot=slot.index();break;}
        }
        check(podSlot>=0&&beaconSlot>=0,"Survival test hull needs two support mounts");
        Shipyard.Design rescueFitted=empty.install(podSlot,"BASE-TER-EPD")
                .install(beaconSlot,"BASE-TER-RCB");
        Battle rescues=new Battle(516L);
        rescues.spawnWing(0,rescueFitted,catalog,1,200,200);
        rescues.spawnWing(0,empty,catalog,1,200,300);
        // Victory settlement requires at least one real surviving friendly
        // vessel; two destroyed hulls do not constitute a winning fleet.
        rescues.spawnWing(0,empty,catalog,1,200,400);
        Battle.Ship outfitted=rescues.ships().get(0),unfitted=rescues.ships().get(1);
        int initialCrew=(int)outfitted.crewCount;
        check(outfitted.escapePods>0 && outfitted.recoveryBeacon,
                "Equipped rescue systems did not enter a real combat ship");
        rescues.destroyHull(outfitted,null);
        rescues.destroyHull(unfitted,null);
        check(outfitted.evacuatedCrew>0 && outfitted.evacuatedCrew<=outfitted.escapePods
                &&outfitted.evacuatedCrew<=initialCrew &&
                outfitted.crewCount==0 && outfitted.hull==0,
                "Destroyed ship must evacuate only real crew, never regenerate hull");
        check(outfitted.recoveryBeaconActivated &&
                !unfitted.recoveryBeaconActivated && unfitted.evacuatedCrew==0,
                "Beacon and pod signals must require fitted physical hardware");
        int destroyed=rescues.destructions;
        rescues.destroyHull(outfitted,null);
        check(destroyed==rescues.destructions,
                "Repeated wreck processing must not duplicate destroyed hulls or survivors");
        check(rescues.recoverySalvageCredits(0)>0,
                "Victory must recover limited scrap from beacon-bearing friendly wrecks");
        long once=rescues.recoverySalvageCredits(0);
        check(once==rescues.recoverySalvageCredits(0) && once<=8000L,
                "Salvage must be deterministic, bounded, and not re-buy the hull");
        CampaignProgress.State winnings=CampaignProgress.start(Battle.Faction.TERRAN)
                .complete(1,1,1);
        var recovered=winnings.awardAfterActionSalvage(once);
        check(recovered.credits()==winnings.credits()+once &&
                recovered.victories()==winnings.victories(),
                "Beacon scrap must pay through the existing durable victory ledger");
        Battle contested=new Battle(517L);
        contested.spawnWing(0,rescueFitted,catalog,1,100,100);
        contested.spawnWing(0,empty,catalog,1,100,200);
        contested.spawnWing(1,empty,catalog,1,900,900);
        contested.destroyHull(contested.ships().get(0),null);
        check(contested.recoverySalvageCredits(0)==0,
                "No beacon salvage without actual battlefield victory");
        // Each unsafe architecture must produce distinguishable, deterministic
        // real failures under the same extreme forced stress.
        int[] flags={SurvivalHardwareCatalog.GOVERNOR,
                     SurvivalHardwareCatalog.PLASMA_CELL,
                     SurvivalHardwareCatalog.BATTERY_BRICK,
                     SurvivalHardwareCatalog.THERMAL_SHUNT,
                     SurvivalHardwareCatalog.REACTOR_TAP};
        String[] codes={"PGO","JPB","IBR","IST","IRG"};
        for(int type=0;type<codes.length;type++){
            Shipyard.Design retrofit=empty.install(socket(empty.hull,Shipyard.Mount.SYSTEM),
                    "BASE-TER-"+codes[type]);
            Battle trial=new Battle(6600L+type);
            trial.spawnWing(0,retrofit,catalog,1,400,320);
            Battle.Ship test=trial.ships().get(0);
            check(test.unsafeFlags==flags[type],
                    "Installed unsafe hardware identity was lost for "+codes[type]);
            test.hazardInstability=100;
            for(int tick=0;tick<510;tick++)trial.step();
            check(test.hazardFailures>0 && test.lastFaultOrigin==flags[type],
                    "No distinguishable physical fault from "+codes[type]);
            check(switch(codes[type]){
                case "PGO" -> test.reactorCondition<1;
                case "JPB" -> test.reactorCondition<.95;
                case "IBR" -> test.commandCondition<1 && test.sensorsCondition<1;
                case "IST" -> test.coolingCondition<1;
                case "IRG" -> test.reactorCondition<.80;
                default -> false;
            },"Retrofit has no distinct damage mechanism "+codes[type]);
        }
        Shipyard.Design illegal=empty.install(socket(empty.hull,Shipyard.Mount.SYSTEM),
                "BASE-TER-IBR");
        Battle a=new Battle(45998L),b=new Battle(45998L);
        a.spawnWing(0,illegal,catalog,1,400,320);
        b.spawnWing(0,illegal,catalog,1,400,320);
        Battle.Ship one=a.ships().get(0),two=b.ships().get(0);
        check(one.hazardInstability>0,"Illegal retrofit did not enter live battle");
        one.hazardInstability=100;two.hazardInstability=100;
        for(int tick=0;tick<510;tick++){a.step();b.step();}
        check(one.hazardFailures>0 &&
                one.lastHazardTick>=0 && one.lastHazardTick<=a.tick,
                "Explicitly overloaded unsafe system must produce a timed visible fault");
        check(one.hazardFailures==two.hazardFailures && a.fingerprint()==b.fingerprint(),
                "Hazard failures must replay deterministically");
        Battle clean=new Battle(45998L);
        clean.spawnWing(0,empty,catalog,1,400,320);
        for(int tick=0;tick<510;tick++)clean.step();
        check(clean.ships().get(0).hazardFailures==0,
                "Undamaged safe bare hull must never roll retrofit faults");
        System.out.println("PASS twelve survival roots and 48 physical variants including unsafe engineering");
    }
}
