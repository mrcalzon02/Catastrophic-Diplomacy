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

        Shipyard.Design illegal=empty.install(socket(empty.hull,Shipyard.Mount.SYSTEM),
                "BASE-TER-IBR");
        Battle a=new Battle(45998L),b=new Battle(45998L);
        a.spawnWing(0,illegal,catalog,1,400,320);
        b.spawnWing(0,illegal,catalog,1,400,320);
        Battle.Ship one=a.ships().get(0),two=b.ships().get(0);
        check(one.hazardInstability>0,"Illegal retrofit did not enter live battle");
        one.hazardInstability=100;two.hazardInstability=100;
        for(int tick=0;tick<510;tick++){a.step();b.step();}
        check(one.hazardFailures>0,
                "Explicitly overloaded unsafe system did not fail under test stress");
        check(one.hazardFailures==two.hazardFailures && a.fingerprint()==b.fingerprint(),
                "Hazard failures must replay deterministically");
        Battle clean=new Battle(45998L);
        clean.spawnWing(0,empty,catalog,1,400,320);
        for(int tick=0;tick<510;tick++)clean.step();
        check(clean.ships().get(0).hazardFailures==0,
                "Undamaged safe bare hull must never roll retrofit faults");
        System.out.println("PASS twelve physical survival and unsafe engineering lines");
    }
}
