package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.Objects;

/** Stable cosmetic speech personality selected by canonical ship hull, not ship ID. */
public final class HullVoiceChatter {
    private HullVoiceChatter() { }
    public enum Theme { INTERCEPTOR, CORVETTE, MISSILE_CORVETTE, FRIGATE, DESTROYER, LIGHT_CRUISER, CRUISER, HEAVY_CRUISER, BATTLECRUISER, CARRIER, DREADNOUGHT }
    private static final String[][][] LINES = {
        {
            {"I've got the angle. Going in hot!", "He's mine. Watch this."},
            {"Took a hit. Still flying!", "That shook me up. Controls are responding."},
            {"I'm peeling off. Cover my tail!", "Not dying for one more pass. Breaking away."},
            {"Clear of their guns. I'm heading home.", "I'm out of the envelope. Save me a berth."},
            {"On your six. Keep moving!", "I've got your back. Go, go!"},
            {"Flight away. Let's make this count.", "Off the rail and looking for trouble."},
            {"Bandit in sight. Closing fast!", "Enemy fighter spotted. I'm on him."},
            {"Shield's gone. Flying on nerve now.", "No protection left. Time to get slippery."},
            {"Down to my last burst.", "Almost dry. I'll make it count."},
            {"Fuel light's flashing. Heading back.", "Bingo fuel. No heroics left."},
            {"Lost my wingmate. Somebody mark that location.", "Lead's gone. I have the flight."},
            {"Friendly's clear. Rejoining formation.", "That's your opening. Take it!"},
            {"That's the last one. Let's get home.", "Sky's ours. Anybody need an escort?"}
        },
        {
            {"Right, then. Let's give them something to think about.", "We're in range. Try not to waste the ammunition."},
            {"That one rattled the whole boat. Still moving!", "We've got a new hole. Engineering says it's manageable."},
            {"No sense fighting a mountain. We're leaving.", "Back us out, nice and quick."},
            {"And we're clear. I owe the engines a drink.", "We made it through. Repair dock, please."},
            {"We're small enough to fit in their blind spot. Covering you.", "You run. We'll make some noise."},
            {"Patrol craft away. Stay sharp.", "Small boats out. Bring them back in one piece."},
            {"Little pests on our bow. Swat them.", "Fighters inbound. Give the mounts something to do."},
            {"Shields are cooked. Keep the hull moving.", "Well, armor, your turn."},
            {"We're nearly down to throwing tools.", "Last racks coming up. Make them good."},
            {"Thrusters are sipping the emergency tank.", "Not enough fuel for another chase."},
            {"We've got people hurt. Medic to the forward section.", "Crew down. Somebody cover their stations."},
            {"We're not much, but we're here. Taking your flank.", "Patrol support on scene. What needs doing?"},
            {"Not bad for a little boat.", "Patrol sector's ours. Someone write that down."}
        },
        {
            {"Target painted. Racks coming alive!", "Good lock. Giving them the full package."},
            {"Rack three took a hit. Secure the warheads!", "Armor scored. Keep the guidance online."},
            {"Cease launch cycle. Save the remaining missiles.", "Breaking away. Don't leave armed ordnance in the tubes."},
            {"Clear of hostile tracking. Safing the racks.", "We made it out. Counting every remaining warhead."},
            {"Setting a missile screen between you and them.", "We can keep them busy. Move while they're dodging."},
            {"Guidance crews ready. Launch sequence green.", "Missile division prepared for departure."},
            {"Tiny targets. Multiple seekers. Lovely.", "Fighter tracks acquired. Distributing locks."},
            {"Shields lost. Protect the magazine!", "Barrier gone. Keep their fire off our racks."},
            {"We're on the last loaded cells.", "Empty tubes everywhere. Reload is going to take hours."},
            {"Fuel reserve low. Can't chase the impacts.", "We have missiles, not miracle fuel."},
            {"Magazine crew hit. Suspend bay operations!", "Casualties in fire control. Get relief operators."},
            {"Providing standoff cover. Call your targets.", "Guidance solutions shared. Your strike is protected."},
            {"Look at that impact pattern. Beautiful.", "Enemy formation broken. Every shot accounted for."}
        },
        {
            {"Escort batteries tracking. Commencing fire.", "Hostile in our sector. Engaging."},
            {"Damage parties dispatched. Formation holds.", "We've taken a hit. Escort station maintained."},
            {"Withdrawal ordered. Protect the ships astern.", "Breaking contact in good order."},
            {"Escort vessel clear. Accounting for crew.", "We've cleared the danger zone. Report all systems."},
            {"Move behind our shield arc. We have you.", "Holding the defensive screen. Stay close."},
            {"Escort flight cleared for launch.", "Support craft away. Establishing perimeter."},
            {"Point defense, track incoming fighters.", "Hostile flight approaching. Intercept and screen."},
            {"Shield grid collapsed. Armor teams standing by.", "Shields offline. Maintain defensive spacing."},
            {"Magazines below reserve. Conserving fire.", "Gunnery, prioritize threats. Ammunition is low."},
            {"Maneuver reserve low. Adjust formation speed.", "Engineering recommends reducing pursuit."},
            {"Medical teams to damaged compartments.", "Crew losses reported. Stations being reassigned."},
            {"Friendly vessel protected. Continue your mission.", "We're on station. Call if you need help."},
            {"Assigned sector secure. Escort duty continues.", "Hostiles neutralized. All protected ships, report status."}
        },
        {
            {"Target marked. Kill it.", "Fire control green. Commence engagement."},
            {"Hit received. Hunt continues.", "Compartment breached. Seal it. Keep firing."},
            {"Disengage. Cover our withdrawal.", "Reverse the intercept. Now."},
            {"Out of contact. Reset the batteries.", "Threat envelope cleared. Maintain readiness."},
            {"Weapons toward the threat. Ally behind us.", "I have their approach. Move."},
            {"Strike element out. Track their progress.", "Launch complete. Resume pursuit."},
            {"Fighters incoming. Cut them down.", "Point defense, clear the sky."},
            {"Shields down. Armor takes over.", "Barrier failed. No change to mission."},
            {"Ammo low. Every shot counts.", "Reduce volleys. Preserve the kill capability."},
            {"Fuel critical. End the chase.", "Propulsion reserve at minimum. Return vector."},
            {"Casualties reported. Replace the stations.", "Medics forward. Weapons stay manned."},
            {"Mark the hostile. We'll remove it.", "Covering fire established. Execute."},
            {"Target destroyed. Find the next one.", "Threat eliminated. Sweep for survivors."}
        },
        {
            {"All batteries, show them what we can do.", "Target solution confirmed. Let the squadron see our work."},
            {"Bridge reports a hit. We can handle it.", "Damage control moving. Keep our heading."},
            {"Executing withdrawal. We will fight another day.", "Helm, make it clean. They're watching us."},
            {"We're clear. That was expertly handled.", "Combat zone astern. Well done, everyone."},
            {"We'll take the exposed flank. Stay with us.", "Protecting the lead elements. We have the range."},
            {"Flight deck, send the interceptors.", "Our support wing is away. Good hunting."},
            {"Hostile fighters? Good. Something for the secondaries.", "Defensive guns, engage the incoming flight."},
            {"Shields down. Don't panic. Rebalance power.", "Barrier lost. Helm, give our gunners room."},
            {"Gunnery, reduce expenditure. We need reserves.", "We're burning through the stores too quickly."},
            {"Fuel running low. We need a shorter route.", "Engineering wants us back before reserves vanish."},
            {"Medical teams now. We bring our people home.", "Crew wounded. Secure those compartments."},
            {"We've got this sector. Push forward!", "Assistance arriving. Your flank is secure."},
            {"A fine engagement. Log the result.", "Sector won. The fleet will hear about this one."}
        },
        {
            {"Gunnery, the usual arrangement. Engage.", "We have the range. Commence deliberate fire."},
            {"A regrettable impact. Damage control, report.", "We've survived worse. Correct the list."},
            {"Helm, take us out of this engagement.", "Enough. We will preserve the ship."},
            {"We are clear. Record the damage honestly.", "Withdrawn in good order. Begin the inventory."},
            {"Bring the secondaries around to cover our allies.", "A proper cruiser protects its formation."},
            {"Flight operations, dispatch the assigned wing.", "Support squadron away. Keep them informed."},
            {"Enemy aircraft closing. Deal with them.", "Point defense, remove those interruptions."},
            {"Shield failure confirmed. Keep your composure.", "Armor is there for a reason. Maintain station."},
            {"Reduce the salvo rate. This is not a parade.", "Ammunition is finite. Remind the gunners."},
            {"Engineering reports an inconvenient fuel state.", "We shall return before running dry. Set course."},
            {"Get the injured to medical. No excuses.", "We have casualties. Send the relief crews."},
            {"We are coming alongside. Hold your course.", "Your defense is now our responsibility."},
            {"Well fought. Now inspect the ship.", "Another engagement concluded. Tea may resume."}
        },
        {
            {"Main battery, present broadside. Fire.", "The line stands. Break their center."},
            {"Armor has been tested. It will hold.", "Damage noted. Restore fighting integrity."},
            {"Retire by division. Keep the colors flying.", "We withdraw together. None left exposed."},
            {"Heavy cruiser clear. Muster every division.", "The battle is astern. The duty is not."},
            {"Interpose the armored belt. Guard our consorts.", "We are the wall. Hold behind us."},
            {"Auxiliary flight, proceed with honor.", "Launch stations cleared. The line will cover you."},
            {"Air threats on the beam. Batteries, attend.", "Enemy strike craft approach. Defend the line."},
            {"Barriers have failed. Steel remains.", "Shields gone. We shall not yield."},
            {"Conserve the heavy shells. Aim with purpose.", "Magazine reports dwindling stores. No waste."},
            {"Fuel reserves diminished. Helm, plan an orderly return.", "Machinery needs replenishment. We shall oblige."},
            {"The wounded come first. Attend your shipmates.", "Our losses will be accounted for by name."},
            {"Take shelter within our gun arcs.", "The line does not abandon its own."},
            {"The line has held. Stand proud.", "Enemy guns are silent. Maintain formation."}
        },
        {
            {"Full power to the guns. Take their head off!", "Helm, close the distance. I want decisive fire."},
            {"They think that will stop us? Answer them.", "We've taken a hit. Keep accelerating."},
            {"Disengage at speed. We'll choose the next fight.", "Withdraw now. I want these guns back intact."},
            {"Clear of hostile range. Reset for the next strike.", "We are out. That was a temporary interruption."},
            {"Our broadside is their safe passage. Move!", "Protect the formation. Sweep the attackers away."},
            {"Strike squadrons, away. Keep pace with us.", "Launch all ready craft. The hunt begins."},
            {"Enemy fighter screen sighted. Tear it open.", "Secondaries, make us a corridor."},
            {"Shields gone. Then close faster!", "Barrier down. No one slows this ship."},
            {"How many salvos remain? Make them lethal.", "Magazines running low. No speculative fire."},
            {"Propulsion reserve is falling. End this quickly.", "We're nearly out of fuel. Finish the strike."},
            {"Get the wounded clear. We need every station.", "Medical parties forward. Protect our people."},
            {"Follow our wake. We'll force the opening.", "Your flank is covered. Attack through it!"},
            {"That's how a battle is decided.", "Their line is broken. Press the advantage."}
        },
        {
            {"Strike packages, targets are assigned. Execute.", "All wings, begin coordinated attack."},
            {"Carrier hit. Deck crews, report immediately.", "Damage control, protect the launch facilities."},
            {"Recall available craft. Carrier is withdrawing.", "All wings, recovery priority. We are leaving."},
            {"Carrier clear. Begin the recovery count.", "We're out of range. I want every pilot accounted for."},
            {"Combat air patrol, shield the threatened ship.", "Defensive wings, protect our escorts."},
            {"Launch control, clear the deck. Birds away.", "Flight one, launch authorized. Fly safely."},
            {"Hostile fighters inbound. Interceptors scramble.", "CAP, intercept incoming aircraft now."},
            {"Carrier shields down. Tighten the screen.", "Barrier failure. All wings, protect the ship."},
            {"Strike stores are depleted. Adjust payloads.", "Ordnance crews, report usable munitions."},
            {"Carrier reserve low. Prepare a recovery route.", "Fuel allocation critical. Suspend unnecessary maneuvers."},
            {"Medical teams to flight operations. Now.", "We have casualties on deck. Secure the lifts."},
            {"Recovery corridor open. Friendlies, come home.", "Air cover assigned. You are not alone out there."},
            {"All wings, return to carrier. Beautiful work.", "Hostiles cleared. Let's bring everyone home."}
        },
        {
            {"Primary batteries. You may fire.", "The enemy line is before us. Break it."},
            {"The hull has been struck. Our purpose remains.", "Damage stations, restore full fighting readiness."},
            {"The fleet will withdraw beneath our protection.", "Set the withdrawal course. Hold the rear guard."},
            {"Flagship clear. Begin fleet-wide accounting.", "Our forces have escaped the engagement. Recover the wounded."},
            {"Bring the fortress guns to bear. Shield our allies.", "The fleet will find shelter beneath our fire."},
            {"Flight command, release the reserve wings.", "All assigned squadrons, deploy under fleet authority."},
            {"Hostile craft approach. Defensive grid, engage.", "Air defense stations, clear the threat."},
            {"Primary barriers have fallen. The hull endures.", "Deflector collapse confirmed. Stand to your posts."},
            {"Principal magazines approaching depletion. Fire by command.", "Conserve the heavy guns. Each volley must matter."},
            {"Our movement reserve is failing. Establish a secure bearing.", "Engineering, report remaining propulsion endurance."},
            {"All departments, account for your personnel.", "We will not abandon our wounded. Dispatch medical teams."},
            {"The flagship is with you. Hold steady.", "You are under the protection of the fleet command."},
            {"The enemy is defeated. Let the fleet recover.", "Victory is secured. Honor the fallen, repair the living."}
        }
    };
    static {
        if(LINES.length!=Theme.values().length)throw new AssertionError("Voice theme count");
        for(String[][] b:LINES){
            if(b.length!=CrewChatter.Event.values().length)throw new AssertionError("Voice event count");
            for(String[] r:b)if(r.length!=2||r[0].isBlank()||r[1].isBlank())
                throw new AssertionError("Missing voice variant");
        }
    }
    public static Theme theme(Shipyard.Hull hull){
        if(hull==null)return Theme.CORVETTE;
        return switch(hull.spriteHull()){
            case INTERCEPTOR -> Theme.INTERCEPTOR;
            case CORVETTE -> Theme.CORVETTE;
            case MISSILE_CORVETTE -> Theme.MISSILE_CORVETTE;
            case FRIGATE -> Theme.FRIGATE;
            case DESTROYER -> Theme.DESTROYER;
            case LIGHT_CRUISER -> Theme.LIGHT_CRUISER;
            case CRUISER -> Theme.CRUISER;
            case HEAVY_CRUISER -> Theme.HEAVY_CRUISER;
            case BATTLECRUISER -> Theme.BATTLECRUISER;
            case CARRIER -> Theme.CARRIER;
            case DREADNOUGHT -> Theme.DREADNOUGHT;
            default -> throw new IllegalArgumentException("Unmapped hull voice: "+hull);
        };
    }
    public static String line(Shipyard.Hull hull,CrewChatter.Event event,
                              long tick,int id,CrewChatter.Tone tone){
        Objects.requireNonNull(event,"event");
        Objects.requireNonNull(tone,"tone");
        String[] variants=LINES[theme(hull).ordinal()][event.ordinal()];
        // Register selects phrasing INSIDE the hull's fixed personality bank.
        if(tone==CrewChatter.Tone.PROFESSIONAL)return variants[0];
        if(tone==CrewChatter.Tone.VETERAN)return variants[1];
        // The inherited LIGHTHEARTED setting must never silently collapse
        // into the professional line. Retain actual playful, size-appropriate
        // diction instead of randomly selecting a serious bridge report.
        if(tone==CrewChatter.Tone.LIGHTHEARTED)
            return CrewChatter.line(CrewChatter.scale(hull),event,tick,id,tone);
        long seed=tick^((long)id*0x9e3779b97f4a7c15L)
                ^((long)event.ordinal()*0xd1b54a32d192ed03L);
        seed^=seed>>>30;seed*=0xbf58476d1ce4e5b9L;
        seed^=seed>>>27;seed*=0x94d049bb133111ebL;
        seed^=seed>>>31;
        return variants[(int)(seed&1L)];
    }
}
