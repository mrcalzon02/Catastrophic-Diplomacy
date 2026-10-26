package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
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
            case ARMADA,PROVING_GROUND -> first?"KIN":"MSL";
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
        if(!quality.valid())throw new IllegalStateException("Invalid scenario fitting: "+quality.explanation());
        return d;
    }

    public static Battle create(Preset preset,Shipyard.Design left,Shipyard.Design right,
                                TechnologyCatalog technology,int customShips) {
        if(preset==null||left==null||right==null||technology==null)
            throw new IllegalArgumentException("Scenario, designs and catalog required");
        if(customShips<1||customShips>200)throw new IllegalArgumentException("Fleet size out of range");
        if(preset.demonstration)return DemonstrationBattle.create(technology);
        return Battle.duel(preset.seed,preset==Preset.CUSTOM?customShips:preset.ships,
                           left,right,technology);
    }
}
