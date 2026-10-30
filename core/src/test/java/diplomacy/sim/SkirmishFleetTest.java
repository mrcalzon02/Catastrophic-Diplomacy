package diplomacy.sim;

import diplomacy.shipyard.*;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** A Tech VI match accepts simultaneous I, III and VI fitted vessels.
 * Neither exporting nor selecting a match confers campaign research. */
public final class SkirmishFleetTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void reject(Runnable r,String message){
        try{r.run();}catch(IllegalArgumentException correct){return;}
        throw new AssertionError(message);
    }
    private static Shipyard.Design atTier(Shipyard.Design base,TechnologyCatalog catalog,int tier){
        for(int i=0;i<base.slots();i++){
            if(base.at(i)==null)continue;
            for(Shipyard.Track track:Shipyard.Track.values()){
                if(!ShipTechnologyFit.available(base,catalog,
                        SkirmishTechnologyRules.unrestricted(),i,track,tier))continue;
                try{
                    Shipyard.Design changed=ShipTechnologyFit.fit(base,catalog,
                            SkirmishTechnologyRules.unrestricted(),i,track,tier,
                            ShipTechnologyFit.Scope.SOCKET);
                    if(Shipyard.inspect(changed,catalog).valid())return changed;
                }catch(IllegalArgumentException ignored){/* try the next physical fitting */}
            }
        }
        throw new AssertionError("No constructible faction ship can demonstrate technology tier "+tier);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        var blank=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var tierOne=atTier(blank,catalog,1);
        var tierThree=atTier(blank,catalog,3);
        var tierSix=atTier(blank,catalog,6);
        SkirmishFleet mixed=new SkirmishFleet(List.of(tierOne,tierThree,tierSix));
        SkirmishFleet stock=new SkirmishFleet(List.of(blank,tierOne));
        for(int level=0;level<=6;level++){
            SkirmishTechnologyRules match=SkirmishTechnologyRules.unrestricted().withStage(level);
            if(level==6)mixed.requireAllowed(match,catalog);
            else reject(()->mixed.requireAllowed(match,catalog),
                    "Lower tier accepted a Tier VI ship");
        }
        var tierThreeMatch=SkirmishTechnologyRules.unrestricted().withStage(3);
        new SkirmishFleet(List.of(tierOne,tierThree,blank)).requireAllowed(tierThreeMatch,catalog);
        reject(()->mixed.requireAllowed(tierThreeMatch,catalog),
                "Tech III match incorrectly accepted Tech VI");
        check(mixed.with(0,blank).ship(0)==blank&&mixed.ship(0)==tierOne,
                "Editing one ship mutated the original roster");
        check(mixed.resize(4).size()==4&&mixed.resize(2).size()==2,
                "Fleet slot resize broke individual ships");
        String saved=SkirmishFleetCodec.encode(mixed,catalog);
        var imported=SkirmishFleetCodec.decode(saved,catalog);
        check(imported.size()==3&&
                SkirmishFleetCodec.encode(imported,catalog).equals(saved),
                "Portable mixed-tier fleet did not survive canonical roundtrip");
        reject(()->SkirmishFleetCodec.decode(saved.replace("CD-SKIRMISH-FLEET-1",
                "CD-SKIRMISH-FLEET-2"),catalog),"Corrupt fleet format accepted");
        Battle battle=ScenarioCatalog.create(ScenarioCatalog.Preset.CUSTOM,
                mixed,stock,catalog,SkirmishTechnologyRules.unrestricted().withStage(6));
        check(battle.ships().size()==5&&battle.engaged(0)==3&&battle.engaged(1)==2,
                "Distinct fleet designs not deployed in actual battle");
        check(new CampaignTechnology.State(Battle.Faction.TERRAN).unlocked().isEmpty(),
                "Skirmish participation altered campaign research");
        System.out.println("PASS mixed-tier fleets: inclusive challenge rank, individual designs, battle, portable export");
    }
}
