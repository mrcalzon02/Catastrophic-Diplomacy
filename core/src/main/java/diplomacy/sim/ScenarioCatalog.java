package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.shipyard.SkirmishFleet;
import diplomacy.tech.TechnologyCatalog;

/** Shared user-facing skirmish presets and real combat fixtures. */
public final class ScenarioCatalog {
    private ScenarioCatalog() {}
    public enum Preset {
        CUSTOM("Custom Engagement","Select factions, fitted hulls and fleet size.",16,1,2479L,Shipyard.Hull.CORVETTE,Shipyard.Hull.CORVETTE,false),
        DUEL("Duel","A decisive one-on-one battle.",1,1,6001L,Shipyard.Hull.CORVETTE,Shipyard.Hull.CORVETTE,false),
        PATROL("Patrol Interception","Fast vessels engage a patrol.",8,7,6002L,Shipyard.Hull.INTERCEPTOR,Shipyard.Hull.FRIGATE,false),
        ESCORT("Escort Clash","Destroyers meet missile corvettes.",16,18,6003L,Shipyard.Hull.DESTROYER,Shipyard.Hull.MISSILE_CORVETTE,false),
        CRUISERS("Cruiser Action","Heavy batteries and layered armor.",32,31,6004L,Shipyard.Hull.CRUISER,Shipyard.Hull.HEAVY_CRUISER,false),
        CARRIERS("Carrier Engagement","Strike craft and defensive screens.",32,57,6005L,Shipyard.Hull.CARRIER,Shipyard.Hull.CARRIER,false),
        CAPITALS("Capital Fleet Battle","Large hulls with extended weapon ranges.",64,86,6006L,Shipyard.Hull.BATTLECRUISER,Shipyard.Hull.DREADNOUGHT,false),
        STORM("Space Weather Battle","Environmental hazards affect the engagement.",64,117,6007L,Shipyard.Hull.FRIGATE,Shipyard.Hull.CRUISER,false),
        ARMADA("Armada Engagement","One hundred vessels on each side.",100,221,6008L,Shipyard.Hull.FRIGATE,Shipyard.Hull.DESTROYER,false),
        HULL_COVERAGE("Hull Coverage 11v11","One ship of every size per side; module/armor inspection.",11,32,HullCoverageBattle.SEED,Shipyard.Hull.INTERCEPTOR,Shipyard.Hull.INTERCEPTOR,true),
        PROVING_GROUND("200v200 Proving Ground","Canonical six-weapon combined-arms test fixture.",200,301,DemonstrationBattle.SEED,Shipyard.Hull.CRUISER,Shipyard.Hull.CRUISER,true);
        public final String title,description;
        public final int ships,environmentLevel;
        public final long seed;
        public final Shipyard.Hull leftHull,rightHull;
        public final boolean demonstration;
        Preset(String title,String description,int ships,int level,long seed,
               Shipyard.Hull leftHull,Shipyard.Hull rightHull,boolean demonstration) {
            this.title=title;this.description=description;this.ships=ships;
            this.environmentLevel=level;this.seed=seed;this.leftHull=leftHull;
            this.rightHull=rightHull;this.demonstration=demonstration;
        }
    }
    public static Shipyard.Design defaultDesign(Preset p,Battle.Faction faction,
                                                 boolean first,TechnologyCatalog tech){
        Shipyard.Hull hull=first?p.leftHull:p.rightHull;
        Shipyard.Design d=Shipyard.starter(faction,hull);
        String weapon=switch(p){
            case CUSTOM,DUEL -> "LAS";
            case PATROL -> first?"LAS":"KIN";
            case ESCORT -> first?"KIN":"MSL";
            case CRUISERS -> first?"PLA":"KIN";
            case CARRIERS -> first?"LAS":"TOR";
            case CAPITALS -> first?"PAR":"TOR";
            case STORM -> first?"PAR":"PLA";
            case ARMADA,PROVING_GROUND,HULL_COVERAGE -> first?"KIN":"MSL";
        };
        for(Shipyard.Slot slot:hull.slots)if(slot.mount()==Shipyard.Mount.WEAPON){
            d=d.install(slot.index(),"BASE-"+Shipyard.factionId(faction)+"-"+weapon);
            break;
        }
        if(p==Preset.CARRIERS)for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==Shipyard.Mount.SUPPORT){
                d=d.install(slot.index(),"BASE-"+Shipyard.factionId(faction)+"-FTR");
                break;
            }
        Shipyard.Report quality=Shipyard.inspect(d,tech);
        // Larger batteries and hangars require real reactor/cooling equipment.
        // Fill spare system hardpoints and reject any hull that cannot power itself.
        for(Shipyard.Slot slot:hull.slots){
            if(quality.valid())break;
            if(slot.mount()!=Shipyard.Mount.SYSTEM||d.at(slot.index())!=null)continue;
            String defects=quality.explanation();
            String machinery=defects.contains("Insufficient reactor output")?"REA":
                    defects.contains("Thermal rejection insufficient")?"RAD":null;
            if(machinery==null)break;
            d=d.install(slot.index(),"BASE-"+Shipyard.factionId(faction)+"-"+machinery);
            quality=Shipyard.inspect(d,tech);
        }
        if(!quality.valid())throw new IllegalStateException(
                "Invalid scenario fitting "+p+"/"+faction+"/"+(first?"LEFT":"RIGHT")+
                ": "+quality.explanation());
        return d;
    }

    /** Challenge tiers are independent of the campaign's permanent research. */
    public static SkirmishTechnologyRules challenge(Preset preset){
        if(preset==null)throw new IllegalArgumentException("Missing skirmish preset");
        int cap=switch(preset){
            case CUSTOM,PROVING_GROUND,HULL_COVERAGE,CAPITALS,ARMADA -> 9;
            case DUEL -> 1;
            case PATROL -> 2;
            case ESCORT -> 3;
            case CRUISERS,STORM -> 4;
            case CARRIERS -> 5;
        };
        // Default challenge ships must remain buildable, while an optional
        // per-challenge exact hull whitelist can further narrow the roster.
        int generation=Math.max(ShipLineages.of(preset.leftHull).generation(),
                ShipLineages.of(preset.rightHull).generation());
        return SkirmishTechnologyRules.unrestricted().withStage(cap)
                .withHullGeneration(preset==Preset.CUSTOM||preset.demonstration?
                        10:generation);
    }

    /** Live skirmish route: validate BOTH players under the same rules before
     * spawning any vessels. The demonstration fixture remains deterministic. */
    /** User-assembled per-vessel fleets, subject to one shared inclusive
     * technology ceiling. Demonstration fixtures remain non-customizable. */
    public static Battle create(Preset preset,SkirmishFleet left,SkirmishFleet right,
                                TechnologyCatalog technology,SkirmishTechnologyRules rules){
        if(preset==null||left==null||right==null||technology==null||rules==null)
            throw new IllegalArgumentException("Missing skirmish fleet or challenge");
        if(preset.demonstration)
            throw new IllegalArgumentException("Verification battle has fixed authored fleets");
        left.requireAllowed(rules,technology);
        right.requireAllowed(rules,technology);
        return Battle.duel(preset.seed,left.ships(),right.ships(),technology);
    }
    public static Battle create(Preset preset,Shipyard.Design left,Shipyard.Design right,
                                TechnologyCatalog technology,int customShips,
                                SkirmishTechnologyRules rules){
        if(rules==null)throw new IllegalArgumentException("Challenge rules required");
        if(!preset.demonstration){
            rules.requireAllowed(left,technology);
            rules.requireAllowed(right,technology);
        }
        return create(preset,left,right,technology,customShips);
    }
    public static Battle create(Preset preset,Shipyard.Design left,Shipyard.Design right,
                                TechnologyCatalog technology,int customShips) {
        if(preset==null||left==null||right==null||technology==null)
            throw new IllegalArgumentException("Scenario, designs and catalog required");
        if(customShips<1||customShips>200)throw new IllegalArgumentException("Fleet size out of range");
        if(preset==Preset.HULL_COVERAGE)return HullCoverageBattle.mixed(technology);
        if(preset.demonstration)return DemonstrationBattle.create(technology);
        return Battle.duel(preset.seed,preset==Preset.CUSTOM?customShips:preset.ships,
                           left,right,technology);
    }
}
