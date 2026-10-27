package diplomacy.sim;

/**
 * Event-triggered ship-appropriate subtitle traffic. No random autonomous
 * chatter or battleship commands from fighter cockpits. Variant is determined
 * by source identity/tick so replays remain deterministic.
 */
public final class CrewChatter {
    private CrewChatter(){}
    public enum Event { ATTACK, DAMAGE, RETREAT, ESCAPE, COVER, LAUNCH, FIGHTER_ATTACK }
    public enum Scale { FIGHTER, ESCORT, CAPITAL }
    public static Scale scale(ShipyardProxy type){
        if(type==ShipyardProxy.FIGHTER)return Scale.FIGHTER;
        return type==ShipyardProxy.ESCORT?Scale.ESCORT:Scale.CAPITAL;
    }
    public enum ShipyardProxy { FIGHTER, ESCORT, CAPITAL }
    public static Scale scale(diplomacy.shipyard.Shipyard.Hull hull){
        if(hull==null)return Scale.ESCORT;
        return switch(hull){
            case INTERCEPTOR,CORVETTE,MISSILE_CORVETTE,FRIGATE,DESTROYER -> Scale.ESCORT;
            default -> Scale.CAPITAL;
        };
    }
    public static String line(Scale scale,Event situation,long tick,int id){
        if(scale==null||situation==null)throw new IllegalArgumentException("Missing chatter context");
        String[] options=switch(scale){
            case FIGHTER -> switch(situation){
                case ATTACK,FIGHTER_ATTACK -> new String[]{"Fox two, target hot!","Guns on target!","Breaking in for another pass!"};
                case DAMAGE -> new String[]{"I'm hit! Controls are heavy!","Taking fire, breaking off!","Warning, my shields are gone!"};
                case RETREAT,ESCAPE -> new String[]{"Bingo fuel, returning!","Disengaging now!","Heading for recovery!"};
                case COVER -> new String[]{"On your six!","I've got your wing!","Covering your escape!"};
                case LAUNCH -> new String[]{"Wing away, let's move!","Flight checking in!","Launching from the bay!"};
            };
            case ESCORT -> switch(situation){
                case ATTACK,FIGHTER_ATTACK -> new String[]{"Target locked. Fire!","Weapons free on the designated hostile!","Opening fire!"};
                case DAMAGE -> new String[]{"Damage control to forward sections!","We've taken a hit!","Armor breach, seal those decks!"};
                case RETREAT -> new String[]{"Helm, break contact and withdraw!","Withdrawal threshold reached. Turn us home!","Get clear of the engagement zone!"};
                case ESCAPE -> new String[]{"Safe beyond the perimeter. Stand down.","Hull intact. We made it out.","Combat zone cleared. Requesting dock."};
                case COVER -> new String[]{"Screen that allied ship!","Interpose and engage its attackers!","Defensive escort underway!"};
                case LAUNCH -> new String[]{"Flight wing, launch and engage!","Craft away, cover the fleet!","Strike craft cleared for launch!"};
            };
            case CAPITAL -> switch(situation){
                case ATTACK,FIGHTER_ATTACK -> new String[]{"All batteries, engage designated targets!","Fire-control: authorize the main battery.","Bridge to gunnery, commence salvo!"};
                case DAMAGE -> new String[]{"Damage-control parties to affected decks!","Bridge, compartment breach reported!","Engineering: seal bulkheads and stabilize power!"};
                case RETREAT -> new String[]{"All stations: initiate fleet withdrawal procedures!","Bridge to helm: turn the ship to extraction heading!","Evacuation order acknowledged. Maintain defensive fire!"};
                case ESCAPE -> new String[]{"Ship clear of the battle. Begin casualty assessment.","Bridge confirms extraction; proceed to repair dock.","Withdrawal complete. Account for all departments."};
                case COVER -> new String[]{"Escort stations: protect our assigned vessels!","Point-defense teams, shield the friendly formation!","Redirect defensive batteries to allied threats!"};
                case LAUNCH -> new String[]{"Flight control: launch designated squadrons.","Authorize fighter deployment, maintain screening orbit.","Hangar deck: launch all assigned craft!"};
            };
        };
        int pick=(int)Math.floorMod(tick+id*31L,options.length);
        return options[pick];
    }
}
