package diplomacy.sim;

/**
 * Deterministic, event-driven radio subtitles. Distinct cockpit, light-hull,
 * frigate, destroyer, cruiser and capital vocabulary. Three intentionally
 * different voices plus a replay-stable mixed voice; no unprompted radio spam.
 */
public final class CrewChatter {
    private CrewChatter(){}
    public enum Event {
        ATTACK, DAMAGE, RETREAT, ESCAPE, COVER, LAUNCH, FIGHTER_ATTACK,
        SHIELDS_DOWN, LOW_AMMO, LOW_FUEL, CASUALTIES, SUPPORT, VICTORY
    }
    public enum Tone { PROFESSIONAL, VETERAN, LIGHTHEARTED, MIXED }
    public enum Scale { FIGHTER, ESCORT, CAPITAL, CORVETTE, FRIGATE, DESTROYER, CRUISER }
    public enum ShipyardProxy { FIGHTER, ESCORT, CAPITAL }
    public static Scale scale(ShipyardProxy type){
        if(type==null)throw new IllegalArgumentException("Missing ship scale");
        return switch(type){
            case FIGHTER -> Scale.FIGHTER;
            case ESCORT -> Scale.ESCORT;
            case CAPITAL -> Scale.CAPITAL;
        };
    }
    public static Scale scale(diplomacy.shipyard.Shipyard.Hull hull){
        if(hull==null)return Scale.ESCORT;
        return switch(hull.spriteHull()){
            case INTERCEPTOR,CORVETTE,MISSILE_CORVETTE -> Scale.CORVETTE;
            case FRIGATE -> Scale.FRIGATE;
            case DESTROYER -> Scale.DESTROYER;
            case LIGHT_CRUISER,CRUISER,HEAVY_CRUISER,BATTLECRUISER -> Scale.CRUISER;
            case CARRIER,DREADNOUGHT -> Scale.CAPITAL;
            default -> throw new IllegalArgumentException("Unknown radio class "+hull);
        };
    }
    public static String line(Scale scale,Event situation,long tick,int id){
        return line(scale,situation,tick,id,Tone.MIXED);
    }
    public static String line(Scale scale,Event situation,long tick,int id,Tone preferred){
        if(scale==null||situation==null||preferred==null)
            throw new IllegalArgumentException("Missing radio context");
        long seed=mix(tick ^ ((long)id*0x9e3779b97f4a7c15L)
                ^ ((long)situation.ordinal()*0xd1b54a32d192ed03L));
        Tone actual=preferred;
        if(actual==Tone.MIXED){
            int voice=(int)Math.floorMod(seed,10);
            actual=voice<5?Tone.PROFESSIONAL:voice<8?Tone.VETERAN:Tone.LIGHTHEARTED;
        }
        String text=switch(actual){
            case PROFESSIONAL -> professional(scale,situation);
            case VETERAN -> veteran(scale,situation);
            case LIGHTHEARTED -> light(scale,situation);
            case MIXED -> throw new IllegalStateException("Unresolved chatter mix");
        };
        String[] variants=text.split("\\|",-1);
        return variants[(int)Math.floorMod(seed>>>11,variants.length)];
    }
    private static long mix(long v){
        v^=v>>>30;v*=0xbf58476d1ce4e5b9L;
        v^=v>>>27;v*=0x94d049bb133111ebL;
        return v^(v>>>31);
    }
    private static String professional(Scale size,Event event){
        return switch(size){
            case FIGHTER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Target acquired. Guns hot.|Flight engaging designated contact.";
                case DAMAGE -> "I'm hit; control response degraded.|Flight reporting damage. Breaking right.";
                case RETREAT -> "Fuel state critical. Returning to carrier.|Flight withdrawing from engagement.";
                case ESCAPE -> "Clear of hostile envelope. Returning to base.|Flight has exited threat range.";
                case COVER -> "On your wing. Watch your six.|Covering your withdrawal.";
                case LAUNCH -> "Flight airborne. Forming on lead.|Cleared for launch. Moving to intercept.";
                case SHIELDS_DOWN -> "My shield readout is zero.|Deflector offline; taking evasive action.";
                case LOW_AMMO -> "Running low on rounds. One pass remaining.|Flight reports low ordnance.";
                case LOW_FUEL -> "Bingo fuel. Returning now.|My fuel reserve is below minimum.";
                case CASUALTIES -> "Flight lead unresponsive. I have command.|Wingmate down. Mark the position.";
                case SUPPORT -> "Friendlies covered. Returning to formation.|Screening your approach.";
                case VICTORY -> "Hostiles clear. Flight regrouping.|Area secure. Request recovery vector.";
            };
            case ESCORT,CORVETTE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Weapons officer: contact locked. Engage.|Patrol craft opening fire.";
                case DAMAGE -> "Hull strike registered. Compensating thrust.|Forward plating damaged; maintaining course.";
                case RETREAT -> "Helm, turn to withdrawal bearing.|Corvette disengaging under power.";
                case ESCAPE -> "Clear of the engagement zone.|Patrol ship safe. Requesting repair dock.";
                case COVER -> "Moving across the friendly ship's firing lane.|Screening the assigned contact.";
                case LAUNCH -> "Auxiliary flight away. Rejoin our screen.|Launch acknowledged. Clearing deck.";
                case SHIELDS_DOWN -> "Shield generator out. Armor taking load.|Corvette shields have collapsed.";
                case LOW_AMMO -> "Small-caliber magazines near empty.|Missile rack nearing reserve limits.";
                case LOW_FUEL -> "Reaction mass low. Planning withdrawal.|Helm reports insufficient maneuver fuel.";
                case CASUALTIES -> "Bridge reports casualties. Medics needed.|Crew losses reported; stations reassigned.";
                case SUPPORT -> "Screening friendly approach.|Escort support acknowledged.";
                case VICTORY -> "Patrol sector clear.|Target group eliminated. Returning to station.";
            };
            case FRIGATE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Frigate batteries: engage marked hostile.|Fire-control solution set; commence salvo.";
                case DAMAGE -> "Compartment damage reported amidships.|Damage-control section mobilized.";
                case RETREAT -> "Command, we are breaking line and withdrawing.|Helm, clear the squadron's flank.";
                case ESCAPE -> "Frigate clear. Begin crew and armor checks.|Withdrawal complete, reporting to squadron command.";
                case COVER -> "Escort detail: cover our assigned vessel.|Bring point defense across the ally's bearing.";
                case LAUNCH -> "Flight section, launch on assigned vector.|Hangar reports support craft away.";
                case SHIELDS_DOWN -> "Frigate shield grid offline.|Power routing to armor integrity systems.";
                case LOW_AMMO -> "Forward magazines approaching minimum.|Gunnery requesting ammunition conservation.";
                case LOW_FUEL -> "Engineering reports restricted maneuver reserve.|Fuel warning; reducing approach speed.";
                case CASUALTIES -> "Casualty teams to damaged compartments.|Bridge reports crew losses aboard.";
                case SUPPORT -> "Remote repair team responding to escort request.|Frigate maintaining the defensive cordon.";
                case VICTORY -> "Assigned sector secure; standing by.|Frigate reports hostile contacts neutralized.";
            };
            case DESTROYER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Destroyer fire-control: launch strike package.|Main mounts tracking; commence intercept.";
                case DAMAGE -> "Destroyer taking hull damage. Counterflood and seal.|Engineering reports a localized breach.";
                case RETREAT -> "Destroyer breaking contact; screen withdrawal.|All stations, execute egress maneuver.";
                case ESCAPE -> "Destroyer clear of threat envelope.|Survived engagement; requesting combat service.";
                case COVER -> "Interposing our batteries to protect the formation.|Destroyer screening allied withdrawal.";
                case LAUNCH -> "Hangar deck, dispatch the assigned flight.|Strike flight away; destroyer maintaining cover.";
                case SHIELDS_DOWN -> "Destroyer shield grid collapsed.|Energy barriers down; armor teams on alert.";
                case LOW_AMMO -> "Magazine officer reports critical guided-munition stock.|Conserve main-battery rounds.";
                case LOW_FUEL -> "Destroyer operating on reduced reaction mass.|Engineering recommending withdrawal fuel reserve.";
                case CASUALTIES -> "Damage-control teams report deck casualties.|Medical parties to affected stations.";
                case SUPPORT -> "Diverting point-defense arcs across our ally.|Assisting designated escort ship.";
                case VICTORY -> "Destroyer reports engagement complete.|Hostile batteries silenced; maintain screen.";
            };
            case CRUISER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Cruiser gunnery: release the battery salvo.|Flag relay confirms hostile targeting.";
                case DAMAGE -> "Bridge to damage control: contain compartment damage.|Cruiser sustaining hits along the armor belt.";
                case RETREAT -> "Helm, execute commander's withdrawal order.|Cruiser leaving battle line under cover.";
                case ESCAPE -> "Cruiser extraction confirmed. Account for personnel.|Bridge reports clear of combat perimeter.";
                case COVER -> "Cruiser defense sections, reinforce the allied screen.|Redirect secondary mounts to friendly protection.";
                case LAUNCH -> "Flight operations: deploy support squadron.|Cruiser hangar confirms craft launched.";
                case SHIELDS_DOWN -> "Cruiser shielding offline; reroute power.|Bridge confirms primary barrier collapse.";
                case LOW_AMMO -> "Ammunition officer reports depleted magazines.|Cruiser reducing salvo rate to conserve ordnance.";
                case LOW_FUEL -> "Engineering requests reduction in cruising power.|Cruiser reaction-mass reserve is low.";
                case CASUALTIES -> "Deck chiefs reporting losses. Mobilize medics.|Bridge requests full casualty report.";
                case SUPPORT -> "Repair and defensive detachments assisting ally.|Cruiser forming a protective fire corridor.";
                case VICTORY -> "Cruiser reporting battle line secure.|Command confirms hostile retreat from sector.";
            };
            case CAPITAL -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "All batteries: engage designated enemy formation.|Flag bridge authorizes primary-gun salvos.";
                case DAMAGE -> "Damage-control parties to affected decks.|Flag bridge: compartment breach reported.";
                case RETREAT -> "All departments, execute capital-ship withdrawal protocol.|Helm, set the fleet extraction bearing.";
                case ESCAPE -> "Flag bridge confirms extraction. Begin casualty accounting.|Capital ship clear; summon repair tenders.";
                case COVER -> "Redirect broadside defense to the allied battle line.|Fleet screen stations: protect the assigned ships.";
                case LAUNCH -> "Hangar command, deploy designated squadrons.|Flight control authorizes carrier launch.";
                case SHIELDS_DOWN -> "Flagship primary shield grid has failed.|Fleet command, barrier collapse confirmed.";
                case LOW_AMMO -> "Flag gunnery reports depleted principal magazines.|Fleet stores officer requests ordnance replenishment.";
                case LOW_FUEL -> "Engineering: capital maneuver reserve approaching minimum.|Flag bridge orders reduced propulsion expenditure.";
                case CASUALTIES -> "All department heads: account for missing crew.|Casualty stations activated across affected decks.";
                case SUPPORT -> "Dispatch fleet tenders to the designated ally.|Shipwide support stations, reinforce fleet defenses.";
                case VICTORY -> "Fleet command confirms the battle line is secure.|Flag bridge records sector victory; commence recovery.";
            };
        };
    }
    private static String veteran(Scale size,Event event){
        return switch(size){
            case FIGHTER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Tally one. Rolling in hot.|Got a firing solution. Taking the shot.";
                case DAMAGE -> "Hit on my left. Still flying.|Took a round; compensating.";
                case RETREAT,LOW_FUEL -> "Bingo fuel; heading home.|That's my reserve. I'm out.";
                case ESCAPE -> "Clear skies behind me.|Made it through. Heading for the hangar.";
                case COVER,SUPPORT -> "Stay on my wing.|I've got your tail. Keep moving.";
                case LAUNCH -> "Flight's out. Keep the channel clean.|Wheels up, heading in.";
                case SHIELDS_DOWN -> "Shield just went flat. Dodging now.|No barrier left. Taking distance.";
                case LOW_AMMO -> "Last belt. Better make it count.|Running dry. One more attack.";
                case CASUALTIES -> "Lost a wingmate. Regroup on me.|Flight lead is down. Taking over.";
                case VICTORY -> "Clean sweep. Let's bring everyone back.|No more hostiles. Heading home.";
            };
            case ESCORT,CORVETTE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Target's in range. Let it have the rack.|Small hull, good aim. Firing.";
                case DAMAGE -> "That one shook the whole boat.|We can still steer. Keep moving.";
                case RETREAT -> "We're too small to trade those hits. Breaking off.|Heading back while we've got a hull.";
                case ESCAPE -> "Out of range and in one piece.|Patrol's over. Send the repair bill.";
                case COVER,SUPPORT -> "Tucking in beside our friend.|Their flank is ours. Keep them covered.";
                case LAUNCH -> "Little birds away. Good hunting.|Hangar says flight's out.";
                case SHIELDS_DOWN -> "Shields are toast. Armor's next.|No blue glow left. Keep us moving.";
                case LOW_AMMO -> "Last few missiles in the rack.|Magazines nearly dry. Make them count.";
                case LOW_FUEL -> "Thrusters are running on fumes.|Helm says we're close to empty.";
                case CASUALTIES -> "We've lost hands at stations. Send help.|Crew's hurt. Get a medic.";
                case VICTORY -> "That'll do for this patrol.|We held our corner. Stand down.";
            };
            case FRIGATE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Frigate line has their range. Firing.|Take the shot before they close.";
                case DAMAGE -> "That cracked armor. Damage teams moving.|Ship took one amidships, holding course.";
                case RETREAT -> "Break the screen; we've done our part.|Frigate heading for the safe lane.";
                case ESCAPE -> "Out clean enough to make dock.|Good withdrawal. Count the crew.";
                case COVER,SUPPORT -> "We're their escort; hold that line.|Bring the defense mounts around.";
                case LAUNCH -> "Launch the flight. They'll need cover.|Support craft cleared. Keep them alive.";
                case SHIELDS_DOWN -> "Shield grid's had enough.|Armor's doing the heavy lifting now.";
                case LOW_AMMO -> "Gunners are down to the last crates.|Conserve ordnance; the next wave's coming.";
                case LOW_FUEL -> "We're low on maneuvering propellant.|Helm wants a shorter route home.";
                case CASUALTIES -> "Crew hit. Medics moving forward.|Report losses and keep stations manned.";
                case VICTORY -> "Screen held. Well fought.|Frigate held station; hostiles are gone.";
            };
            case DESTROYER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Our guns have a clean line. Send it.|Destroyer has the range. Hammer them.";
                case DAMAGE -> "That tore up the plating. Seal it.|Hard hit; keep the guns responding.";
                case RETREAT -> "We did the damage. Time to disengage.|Cover the retreat and follow.";
                case ESCAPE -> "We're out. Get repair on the horn.|Well executed withdrawal, bridge.";
                case COVER,SUPPORT -> "Our defense arcs are theirs now.|Cover their retreat; we can take it.";
                case LAUNCH -> "Flight away. Don't waste their opening.|Hangar launches confirmed.";
                case SHIELDS_DOWN -> "Barriers are gone. Brace for hull strikes.|Shield deck went dark.";
                case LOW_AMMO -> "We're running low on the big ones.|Magazine warning. Pick the next target.";
                case LOW_FUEL -> "Engines need some reserve to bring us home.|Destroyer fuel is down to margin.";
                case CASUALTIES -> "Crew down on the gun deck. Medic.|We're losing people. Seal that section.";
                case VICTORY -> "Enemy firing line broken.|Destroyer held the flank. Sector's ours.";
            };
            case CRUISER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Cruiser guns are tracking. Take the salvo.|Their formation is opening. Fire.";
                case DAMAGE -> "Bridge felt that impact. Damage control's moving.|Armor took a pounding. Still operational.";
                case RETREAT -> "Order the withdrawal before we lose the ship.|Bring the escort line out with us.";
                case ESCAPE -> "Clear of the threat. Start the damage report.|Crew accounted for? Begin repair estimates.";
                case COVER,SUPPORT -> "Secondary guns, cover that ally.|We can screen them. Bring batteries around.";
                case LAUNCH -> "Flight section away; give them room.|Launch bays clear. Keep the fire lane open.";
                case SHIELDS_DOWN -> "Primary barriers down. Get armor crews ready.|Shields quit. Hull's holding.";
                case LOW_AMMO -> "Big racks running thin. Select targets.|Cruiser magazines can't feed another broadside.";
                case LOW_FUEL -> "Engineering says keep some fuel for home.|Cruiser engines need reserve.";
                case CASUALTIES -> "We took crew losses. Medical teams forward.|Casualties reported. Hold stations.";
                case VICTORY -> "Battle line held, and so did we.|Cruiser reports the shooting's over.";
            };
            case CAPITAL -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "All big guns have the mark. Let them speak.|That formation isn't going to break itself. Fire.";
                case DAMAGE -> "Whole deck shook. Dispatch damage control.|Hull breach aboard. Secure those bulkheads.";
                case RETREAT -> "Order fleet extraction. Keep the screen in place.|Turn this mountain around. We're withdrawing.";
                case ESCAPE -> "Flag ship is clear. Count every deck.|We made it. Damage control, take inventory.";
                case COVER,SUPPORT -> "Put the broadside between them and our people.|Fleet defense to the friendly flank.";
                case LAUNCH -> "Hangar decks: birds away. All squadrons.|Launch the wings and hold our position.";
                case SHIELDS_DOWN -> "Primary shield net has gone silent.|Bridge, the capital barriers are down.";
                case LOW_AMMO -> "Main batteries are running low. No wasted salvos.|Fleet magazines need immediate resupply.";
                case LOW_FUEL -> "We're a long way from drydock. Conserve fuel.|Engineering says the helm reserve is nearly gone.";
                case CASUALTIES -> "Crews down across multiple decks. Medical report.|This is a big ship with real people. Count them.";
                case VICTORY -> "Fleet line intact. Now bring everyone home.|Flagship reports the field is ours.";
            };
        };
    }
    private static String light(Scale size,Event event){
        return switch(size){
            case FIGHTER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "I'd like to return this missile personally.|Tiny ship, big enthusiasm. Going in!";
                case DAMAGE -> "That paint was expensive!|I'm still flying; that's the good news!";
                case RETREAT,LOW_FUEL -> "Fuel gauge says the party's over.|Home sounds fantastic right about now.";
                case ESCAPE -> "Landing gear still attached. A victory in itself.|I have survived another landing report.";
                case COVER,SUPPORT -> "Your six is looking fashionable again.|I'll be your very temporary umbrella.";
                case LAUNCH -> "Off we go. Please keep the hangar open!|Flight departing, snacks postponed.";
                case SHIELDS_DOWN -> "No shields. I suddenly feel very aerodynamic.|Deflector's out; I'm choosing speed.";
                case LOW_AMMO -> "I've got optimism and roughly one missile.|Save a target for my last round.";
                case CASUALTIES -> "Somebody find our wingmate. Now.|Laugh later. We lost one of ours.";
                case VICTORY -> "I vote we land before they change their minds.|Flight clear. Someone owes me lunch.";
            };
            case ESCORT,CORVETTE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Little hull, very loud weapons!|They may be bigger. Not impressed.";
                case DAMAGE -> "That's going on the maintenance invoice.|Somebody's scratched the ship again.";
                case RETREAT -> "Going home before this becomes a submarine.|We are withdrawing, with enthusiasm.";
                case ESCAPE -> "We still have all the important corners.|Someone tell the dock to put the kettle on.";
                case COVER,SUPPORT -> "We brought a shield-shaped attitude.|Our little boat can cover yours.";
                case LAUNCH -> "Support flight away. Be nice to them!|Flight launched; don't ding the paint.";
                case SHIELDS_DOWN -> "The shield light is no longer encouraging.|We have switched to the 'don't get hit' plan.";
                case LOW_AMMO -> "Gunners request fewer targets per minute.|Turns out ammunition is a finite resource.";
                case LOW_FUEL -> "Our fuel budget has opinions.|Low fuel. The thrusters would like a nap.";
                case CASUALTIES -> "Jokes off. Crew need medical assistance.|Damage team needs a medic, now.";
                case VICTORY -> "Patrol boat, capital-size confidence.|Small ship reports an enormous relief.";
            };
            case FRIGATE -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "These batteries were not installed for decoration.|Range acquired; let's make a point.";
                case DAMAGE -> "Engineering, add another dent to the list.|That felt distinctly nonstandard.";
                case RETREAT -> "Frigate departing while departure is possible.|A tactical exit is still tactical.";
                case ESCAPE -> "The repair dock knows our number.|Made it out. I'll call that competent navigation.";
                case COVER,SUPPORT -> "Premium escort service, no refunds.|We stand between friends and poor decisions.";
                case LAUNCH -> "Flight away; try to return the craft!|Hangar says everyone remembered their helmets.";
                case SHIELDS_DOWN -> "The shields have taken an early retirement.|Armor, you're up.";
                case LOW_AMMO -> "Our magazines did not plan for overtime.|Gunnery is counting rounds, not targets.";
                case LOW_FUEL -> "Helm has found the bottom of the fuel tank.|Fuel economy suddenly matters.";
                case CASUALTIES -> "Crew hurt. Serious business; medics forward.|This one isn't funny. Medical team, move.";
                case VICTORY -> "We made this sector slightly quieter.|Frigate's still here. We'll take it.";
            };
            case DESTROYER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Destroyer guns, justify the procurement budget.|Our missiles have requested an introduction.";
                case DAMAGE -> "We just gained a new ventilation shaft.|That repair estimate is going to be colorful.";
                case RETREAT -> "Exiting before they add another invoice.|Destroyer executing the very sensible retreat plan.";
                case ESCAPE -> "We survived. Procurement won't be pleased.|Heading to dock. Reserve some welding wire.";
                case COVER,SUPPORT -> "Our point defense comes with excellent manners.|Keeping our friends off the target list.";
                case LAUNCH -> "Strike flight away. Try not to break the keys.|Our flight has officially left the nest.";
                case SHIELDS_DOWN -> "Armor department, you're now the favorite.|Barrier collapsed; this is not an optional exercise.";
                case LOW_AMMO -> "Missile count is now mostly nostalgia.|Guns need rounds, not encouragement.";
                case LOW_FUEL -> "We've got more guns than fuel.|Engines are negotiating with the reserve tank.";
                case CASUALTIES -> "Medic to the gun teams. No jokes.|People are hurt. Get them clear.";
                case VICTORY -> "Destroyer reports favorable noise reduction.|Their guns stopped. Ours can too.";
            };
            case CRUISER -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Cruiser battery, introduce yourselves.|The enemy has a pressing appointment with gunnery.";
                case DAMAGE -> "That dent will need an entire committee.|Bridge requests fewer surprises of that caliber.";
                case RETREAT -> "We are elegantly leaving this disagreement.|Helm, a dignified withdrawal, please.";
                case ESCAPE -> "Another victory for ships that can turn around.|Repair estimate pending; morale cautiously intact.";
                case COVER,SUPPORT -> "Our secondary battery does protective work too.|Giving our allies the deluxe defensive package.";
                case LAUNCH -> "Hangar deck, please return the squadrons in one piece.|Another flight, another logistics headache.";
                case SHIELDS_DOWN -> "Cruiser shielding has filed for leave.|Those alarms are decidedly unmusical.";
                case LOW_AMMO -> "The gunnery officer requests a larger budget.|Magazines emptying faster than our paperwork.";
                case LOW_FUEL -> "Cruiser engines would like to discuss economy.|Fuel reserves have become a management issue.";
                case CASUALTIES -> "Medical teams required. Bridge, no commentary.|We have wounded. Prioritize recovery.";
                case VICTORY -> "Cruiser reports the opposition has reconsidered.|We'll file the victory paperwork at dock.";
            };
            case CAPITAL -> switch(event){
                case ATTACK,FIGHTER_ATTACK -> "Main guns, make the shipyard proud.|The entire broadside is quite committed to this argument.";
                case DAMAGE -> "That repair invoice just acquired its own chapter.|Damage control, the bridge would like fewer holes.";
                case RETREAT -> "Move the flagship. Yes, all of it.|We are tactically relocating the mountain.";
                case ESCAPE -> "The flagship made it. Bring a very large wrench.|A complete accounting may require two ledgers.";
                case COVER,SUPPORT -> "This is why we brought an entire fleet screen.|Give our allies the deluxe broadside umbrella.";
                case LAUNCH -> "Hangar, release the flock. Kindly count them.|We paid for the squadrons. Please return them.";
                case SHIELDS_DOWN -> "Flagship shielding has left the conversation.|Bridge suggests everyone now appreciate the armor.";
                case LOW_AMMO -> "A battleship-sized shopping list is growing.|Procurement will have questions about this salvo.";
                case LOW_FUEL -> "Moving this ship is becoming expensive.|Engineering wants the biggest fuel tanker available.";
                case CASUALTIES -> "Medical decks need support immediately. No levity.|All decks report casualties. Prioritize lives.";
                case VICTORY -> "The fleet wins; the accountants lose.|The flag bridge requests a celebratory maintenance day.";
            };
        };
    }
}
