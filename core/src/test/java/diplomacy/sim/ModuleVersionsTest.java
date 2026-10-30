package diplomacy.sim;

import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

public final class ModuleVersionsTest {
    private ModuleVersionsTest(){}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable call){
        try{call.run();throw new AssertionError("Invalid equipment version was accepted");}
        catch(IllegalArgumentException correct){/* expected */}
    }
    private static int weapon(Shipyard.Design ship){
        for(Shipyard.Slot slot:ship.hull.slots)
            if(slot.mount()==Shipyard.Mount.WEAPON)return slot.index();
        throw new AssertionError("No weapon slot");
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog technology=TechnologyCatalog.loadDefault();
        for(Battle.Faction faction:Battle.Faction.values())
            check(technology.hasBase("BASE-"+Shipyard.factionId(faction)+"-REA"),
                    "Every faction must retain its own native reactor hardware");
        check(technology.componentCount()%Battle.Faction.values().length==0,
                "Module manufacturing roster must span all eleven factions");
        int models=0;
        for(var base:technology.baseComponents().values()){
            for(ModuleVersions.Pattern pattern:ModuleVersions.Pattern.values())
                for(int mark=1;mark<=4;mark++){
                    var m=ModuleVersions.of(pattern,mark);
                    check(m.mass()>0&&m.power()>0&&m.heat()>0
                            &&m.output()>0&&m.integrity()>0,
                            "Stat modifier must be legal for "+base.id);
                    models++;
                }
        }
        check(models==technology.componentCount()*ModuleVersions.Pattern.values().length*4,
                "Every actual module must support every production variant and mark");
        check(ModuleVersions.of(ModuleVersions.Pattern.STANDARD,1).equals(
                new ModuleVersions.Modifiers(1,1,1,1,1,1,1,1)),
                "Legacy standard Mk I must be a precise identity transformation");
        check(ModuleVersions.of(ModuleVersions.Pattern.COMPACT,4).mass()
                <ModuleVersions.of(ModuleVersions.Pattern.STANDARD,1).mass(),
                "Compact sidegrade lowers installed mass");
        check(ModuleVersions.of(ModuleVersions.Pattern.REINFORCED,4).integrity()
                >ModuleVersions.of(ModuleVersions.Pattern.STANDARD,1).integrity(),
                "Reinforced sidegrade raises damage survivability");
        check(ModuleVersions.of(ModuleVersions.Pattern.OVERCHARGED,4).output()
                >ModuleVersions.of(ModuleVersions.Pattern.STANDARD,1).output(),
                "Overcharged production raises real output");
        check(ModuleVersions.of(ModuleVersions.Pattern.LOW_EMISSION,4).heat()
                <ModuleVersions.of(ModuleVersions.Pattern.STANDARD,1).heat(),
                "Low emission sidegrade lowers operating heat");
        rejects(()->ModuleVersions.of(ModuleVersions.Pattern.COMPACT,0));
        rejects(()->ModuleVersions.of(ModuleVersions.Pattern.COMPACT,5));

        var starter=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        int weapon=weapon(starter);
        check(Shipyard.encode(starter).startsWith("CD-SHIP-1|"),
                "Existing blueprints must stay bit-for-bit version 1 when standard Mk I");
        var tuned=starter.withModuleVersion(weapon,ModuleVersions.Pattern.OVERCHARGED,2);
        check(tuned.at(weapon).pattern==ModuleVersions.Pattern.OVERCHARGED
                        &&tuned.at(weapon).mark==2,
                "Only selected socket receives hardware sidegrade");
        check(starter.at(weapon).mark==1,
                "Immutable earlier design cannot be mutated");
        check(Shipyard.inspect(tuned,technology).industry()
                >Shipyard.inspect(starter,technology).industry(),
                "Better manufacturing version must incur a real industry cost");
        check(Shipyard.fit(tuned,technology).laserDamage()
                        >Shipyard.fit(starter,technology).laserDamage(),
                "Overcharged model produces stronger real weapons");
        tuned=tuned.stage(weapon,Shipyard.Track.SPECIALIST,3);
        check(tuned.at(weapon).stage(Shipyard.Track.SPECIALIST)==3
                        &&tuned.at(weapon).pattern==ModuleVersions.Pattern.OVERCHARGED,
                "Research selection and physical generation must remain orthogonal");
        String encoded=Shipyard.encode(tuned);
        check(encoded.startsWith("CD-SHIP-2|"),"Versioned fits require CD-SHIP-2");
        var decoded=Shipyard.decode(encoded,technology);
        check(Shipyard.encode(decoded).equals(encoded),
                "Saved patterned module retains exact version, mark and research branch");
        String corrupt=encoded.replace("|OVERCHARGED|2","|OVERCHARGED|5");
        rejects(()->Shipyard.decode(corrupt,technology));
        var reset=decoded.withModuleVersion(weapon,ModuleVersions.Pattern.STANDARD,1);
        check(reset.at(weapon).stage(Shipyard.Track.SPECIALIST)==3,
                "Version respec cannot silently destroy independent technology upgrades");
        check(Shipyard.encode(reset).startsWith("CD-SHIP-1|"),
                "Returning every module to default hardware restores legacy version");
        var lowHeat=starter.withModuleVersion(weapon,ModuleVersions.Pattern.LOW_EMISSION,2);
        check(Shipyard.inspect(lowHeat,technology).generatedHeat()
                        <Shipyard.inspect(starter,technology).generatedHeat(),
                "Low emission fitted sidegrade reduces actual ship heat budget");
        System.out.println("PASS "+models+" module manufacturing versions, exclusive six-rank research retention, fitted sidegrades and CD-SHIP-1/2 compatibility");
    }
}
