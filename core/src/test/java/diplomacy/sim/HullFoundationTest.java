package diplomacy.sim;

import diplomacy.shipyard.HullBaseline;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.HashSet;
import java.util.Set;

/** Acceptance: no inherited armament, player/enemy equipment parity, native tech. */
public final class HullFoundationTest {
    private HullFoundationTest(){}
    private static void check(boolean okay,String why){if(!okay)throw new AssertionError(why);}
    private static int slot(Shipyard.Hull hull,Shipyard.Mount type,int number){
        for(var part:hull.slots)if(part.mount()==type&&number--==0)return part.index();
        throw new IllegalArgumentException("Missing "+type);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        check(technology.componentCount()==242,"11 factions x 22 native module chassis");
        check(technology.upgradeCount()==5808,"11 x 22 x four exclusive six-rank tech branches");
        Set<String> factionIds=new HashSet<>();
        int hulls=0;
        for(Battle.Faction faction:Battle.Faction.values()){
            String code=Shipyard.factionId(faction);
            check(factionIds.add(code),"Never alias playable races to another civilization's module ID");
            String weaponId="BASE-"+code+"-LAS";
            check(technology.hasBase(weaponId),"Each civilization owns its weapon family "+code);
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                HullBaseline.Stats intrinsic=HullBaseline.forHull(faction,hull);
                check(intrinsic.structure()>0&&intrinsic.armor()>0&&intrinsic.speed()>0
                        &&intrinsic.power()>0&&intrinsic.sensorRange()>0
                        &&intrinsic.radarCrossSection()>0,"Physical hull has real intrinsic stats");
                check(intrinsic.slots()==hull.slots.size(),"Class slot capacity is physical");
                check(intrinsic.fighterBay()==(hull.spriteHull()==Shipyard.Hull.CARRIER?2:0),
                        "Only special carrier classes have a native fighter bay");
                check(intrinsic.shields()==0 || intrinsic.shieldRecharge()>0,
                        "Intrinsic shields have an intrinsic refresh rate");
                Shipyard.Design starter=Shipyard.starter(faction,hull);
                Shipyard.Design unarmed=starter
                        .install(slot(hull,Shipyard.Mount.WEAPON,0),null)
                        .install(slot(hull,Shipyard.Mount.WEAPON,1),null);
                Shipyard.Report report=Shipyard.inspect(unarmed,technology);
                check(report.valid(),"Unarmed engineering and rescue vessel must be legal: "+report.explanation());
                Shipyard.Fitted fitted=Shipyard.fit(unarmed,technology);
                check(fitted.laserDamage()==0&&fitted.missileDamage()==0
                        &&fitted.kineticDamage()==0&&fitted.particleDamage()==0
                        &&fitted.plasmaDamage()==0&&fitted.torpedoDamage()==0
                        &&fitted.pdRange()==0,
                        "No bare hull has weapons, point defense or magical ammunition");
                check(fitted.baseline().equals(intrinsic)
                        &&fitted.hull()>=intrinsic.structure()
                        &&fitted.armor()>=intrinsic.armor()
                        &&fitted.shields()>=intrinsic.shields()
                        &&fitted.speed()>=intrinsic.speed()
                        &&fitted.maxPower()>=intrinsic.power(),
                        "Fitting adds to, never erases, native hull capabilities");
                check(fitted.fighterCapacity()==intrinsic.fighterBay(),
                        "Unmodified carrier bay survives without fitted fighter hangar");
                Battle battle=new Battle(440);
                battle.spawnWing(0,unarmed,technology,1,280,360);
                Battle.Ship actual=battle.ships().get(0);
                check(actual.laserDamage==0&&actual.missileDamage==0&&actual.pdRange==0,
                        "Module-less combat ships inherit zero armament");
                check(actual.sensorRange==intrinsic.sensorRange()
                        &&actual.radarCrossSection==intrinsic.radarCrossSection(),
                        "Native sensors and signature reach the world simulation");
                hulls++;
            }
            var nativeBase=technology.base(weaponId);
            if(!code.equals("TER")){
                check(nativeBase.faction.equals(code),"Native module manufacturer identity");
            }
            for(Shipyard.Track track:Shipyard.Track.values()){
                String id="TECH-"+code+"-LAS-"+track+"-06";
                check(technology.get(id)!=null,"Full six-level chain "+id);
            }
        }
        check(hulls==Battle.Faction.values().length*Shipyard.Hull.values().length,
                "Not every civilization received the complete expanded physical chassis roster");
        // Same race / same blueprint / both sides -> identical physical machinery;
        // the player may fit everything the enemy fleet can legally equip.
        var design=Shipyard.starter(Battle.Faction.VAERITH_PRISM,Shipyard.Hull.CRUISER);
        var battles=Battle.duel(992,2,design,design,technology);
        var one=battles.ships().get(0);var other=battles.ships().get(2);
        check(one.maxHull==other.maxHull&&one.maxShields==other.maxShields
                        &&one.laserDamage==other.laserDamage&&one.sensorRange==other.sensorRange,
                "No private enemy-only guns or stat overrides");
        System.out.println("PASS physical hull baseline: "+hulls+" chassis/faction combinations, 11 native module rosters, 5808 tech upgrades, unarmed fits, symmetric combat");
    }
}
