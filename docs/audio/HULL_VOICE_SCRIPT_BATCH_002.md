# Catastrophic Diplomacy — Hull Voice Chatter Batch 002

**Authored script, not recorded audio.** Each hull family has one voice identity, including all specialist variants sharing `spriteHull()`. Individual ships do not roll different personalities. The central user-recorded announcer (Batch 001) is separate. The runtime presently displays these as text-radio events with quiet bleeps; this script provides stable IDs for recording separate voices later.

**Recording:** One performance/voice theme per hull, in order, with approximately a second between lines. Do not speak IDs. Standard and veteran lines use two registers within the SAME voice personality. Carrier-launched fighter-pilot dialogue stays a distinct cockpit channel.

## INTERCEPTOR — Hotshot pilot

*Delivery:* Quick, cocky, energetic cockpit delivery.

- **INTERCEPTOR_ATTACK_01** — “I've got the angle. Going in hot!”
- **INTERCEPTOR_ATTACK_02** — “He's mine. Watch this.”
- **INTERCEPTOR_DAMAGE_01** — “Took a hit. Still flying!”
- **INTERCEPTOR_DAMAGE_02** — “That shook me up. Controls are responding.”
- **INTERCEPTOR_RETREAT_01** — “I'm peeling off. Cover my tail!”
- **INTERCEPTOR_RETREAT_02** — “Not dying for one more pass. Breaking away.”
- **INTERCEPTOR_ESCAPE_01** — “Clear of their guns. I'm heading home.”
- **INTERCEPTOR_ESCAPE_02** — “I'm out of the envelope. Save me a berth.”
- **INTERCEPTOR_COVER_01** — “On your six. Keep moving!”
- **INTERCEPTOR_COVER_02** — “I've got your back. Go, go!”
- **INTERCEPTOR_LAUNCH_01** — “Flight away. Let's make this count.”
- **INTERCEPTOR_LAUNCH_02** — “Off the rail and looking for trouble.”
- **INTERCEPTOR_FIGHTER_ATTACK_01** — “Bandit in sight. Closing fast!”
- **INTERCEPTOR_FIGHTER_ATTACK_02** — “Enemy fighter spotted. I'm on him.”
- **INTERCEPTOR_SHIELDS_DOWN_01** — “Shield's gone. Flying on nerve now.”
- **INTERCEPTOR_SHIELDS_DOWN_02** — “No protection left. Time to get slippery.”
- **INTERCEPTOR_LOW_AMMO_01** — “Down to my last burst.”
- **INTERCEPTOR_LOW_AMMO_02** — “Almost dry. I'll make it count.”
- **INTERCEPTOR_LOW_FUEL_01** — “Fuel light's flashing. Heading back.”
- **INTERCEPTOR_LOW_FUEL_02** — “Bingo fuel. No heroics left.”
- **INTERCEPTOR_CASUALTIES_01** — “Lost my wingmate. Somebody mark that location.”
- **INTERCEPTOR_CASUALTIES_02** — “Lead's gone. I have the flight.”
- **INTERCEPTOR_SUPPORT_01** — “Friendly's clear. Rejoining formation.”
- **INTERCEPTOR_SUPPORT_02** — “That's your opening. Take it!”
- **INTERCEPTOR_VICTORY_01** — “That's the last one. Let's get home.”
- **INTERCEPTOR_VICTORY_02** — “Sky's ours. Anybody need an escort?”

## CORVETTE — Scrappy patrol crew

*Delivery:* Informal, resilient, practical, irreverent.

- **CORVETTE_ATTACK_01** — “Right, then. Let's give them something to think about.”
- **CORVETTE_ATTACK_02** — “We're in range. Try not to waste the ammunition.”
- **CORVETTE_DAMAGE_01** — “That one rattled the whole boat. Still moving!”
- **CORVETTE_DAMAGE_02** — “We've got a new hole. Engineering says it's manageable.”
- **CORVETTE_RETREAT_01** — “No sense fighting a mountain. We're leaving.”
- **CORVETTE_RETREAT_02** — “Back us out, nice and quick.”
- **CORVETTE_ESCAPE_01** — “And we're clear. I owe the engines a drink.”
- **CORVETTE_ESCAPE_02** — “We made it through. Repair dock, please.”
- **CORVETTE_COVER_01** — “We're small enough to fit in their blind spot. Covering you.”
- **CORVETTE_COVER_02** — “You run. We'll make some noise.”
- **CORVETTE_LAUNCH_01** — “Patrol craft away. Stay sharp.”
- **CORVETTE_LAUNCH_02** — “Small boats out. Bring them back in one piece.”
- **CORVETTE_FIGHTER_ATTACK_01** — “Little pests on our bow. Swat them.”
- **CORVETTE_FIGHTER_ATTACK_02** — “Fighters inbound. Give the mounts something to do.”
- **CORVETTE_SHIELDS_DOWN_01** — “Shields are cooked. Keep the hull moving.”
- **CORVETTE_SHIELDS_DOWN_02** — “Well, armor, your turn.”
- **CORVETTE_LOW_AMMO_01** — “We're nearly down to throwing tools.”
- **CORVETTE_LOW_AMMO_02** — “Last racks coming up. Make them good.”
- **CORVETTE_LOW_FUEL_01** — “Thrusters are sipping the emergency tank.”
- **CORVETTE_LOW_FUEL_02** — “Not enough fuel for another chase.”
- **CORVETTE_CASUALTIES_01** — “We've got people hurt. Medic to the forward section.”
- **CORVETTE_CASUALTIES_02** — “Crew down. Somebody cover their stations.”
- **CORVETTE_SUPPORT_01** — “We're not much, but we're here. Taking your flank.”
- **CORVETTE_SUPPORT_02** — “Patrol support on scene. What needs doing?”
- **CORVETTE_VICTORY_01** — “Not bad for a little boat.”
- **CORVETTE_VICTORY_02** — “Patrol sector's ours. Someone write that down.”

## MISSILE CORVETTE — Weapons fanatic

*Delivery:* Delights in locks, salvos and timing; technical but excited.

- **MISSILE_CORVETTE_ATTACK_01** — “Target painted. Racks coming alive!”
- **MISSILE_CORVETTE_ATTACK_02** — “Good lock. Giving them the full package.”
- **MISSILE_CORVETTE_DAMAGE_01** — “Rack three took a hit. Secure the warheads!”
- **MISSILE_CORVETTE_DAMAGE_02** — “Armor scored. Keep the guidance online.”
- **MISSILE_CORVETTE_RETREAT_01** — “Cease launch cycle. Save the remaining missiles.”
- **MISSILE_CORVETTE_RETREAT_02** — “Breaking away. Don't leave armed ordnance in the tubes.”
- **MISSILE_CORVETTE_ESCAPE_01** — “Clear of hostile tracking. Safing the racks.”
- **MISSILE_CORVETTE_ESCAPE_02** — “We made it out. Counting every remaining warhead.”
- **MISSILE_CORVETTE_COVER_01** — “Setting a missile screen between you and them.”
- **MISSILE_CORVETTE_COVER_02** — “We can keep them busy. Move while they're dodging.”
- **MISSILE_CORVETTE_LAUNCH_01** — “Guidance crews ready. Launch sequence green.”
- **MISSILE_CORVETTE_LAUNCH_02** — “Missile division prepared for departure.”
- **MISSILE_CORVETTE_FIGHTER_ATTACK_01** — “Tiny targets. Multiple seekers. Lovely.”
- **MISSILE_CORVETTE_FIGHTER_ATTACK_02** — “Fighter tracks acquired. Distributing locks.”
- **MISSILE_CORVETTE_SHIELDS_DOWN_01** — “Shields lost. Protect the magazine!”
- **MISSILE_CORVETTE_SHIELDS_DOWN_02** — “Barrier gone. Keep their fire off our racks.”
- **MISSILE_CORVETTE_LOW_AMMO_01** — “We're on the last loaded cells.”
- **MISSILE_CORVETTE_LOW_AMMO_02** — “Empty tubes everywhere. Reload is going to take hours.”
- **MISSILE_CORVETTE_LOW_FUEL_01** — “Fuel reserve low. Can't chase the impacts.”
- **MISSILE_CORVETTE_LOW_FUEL_02** — “We have missiles, not miracle fuel.”
- **MISSILE_CORVETTE_CASUALTIES_01** — “Magazine crew hit. Suspend bay operations!”
- **MISSILE_CORVETTE_CASUALTIES_02** — “Casualties in fire control. Get relief operators.”
- **MISSILE_CORVETTE_SUPPORT_01** — “Providing standoff cover. Call your targets.”
- **MISSILE_CORVETTE_SUPPORT_02** — “Guidance solutions shared. Your strike is protected.”
- **MISSILE_CORVETTE_VICTORY_01** — “Look at that impact pattern. Beautiful.”
- **MISSILE_CORVETTE_VICTORY_02** — “Enemy formation broken. Every shot accounted for.”

## FRIGATE — Dependable escort

*Delivery:* Steady, loyal and protective, not prone to boasting.

- **FRIGATE_ATTACK_01** — “Escort batteries tracking. Commencing fire.”
- **FRIGATE_ATTACK_02** — “Hostile in our sector. Engaging.”
- **FRIGATE_DAMAGE_01** — “Damage parties dispatched. Formation holds.”
- **FRIGATE_DAMAGE_02** — “We've taken a hit. Escort station maintained.”
- **FRIGATE_RETREAT_01** — “Withdrawal ordered. Protect the ships astern.”
- **FRIGATE_RETREAT_02** — “Breaking contact in good order.”
- **FRIGATE_ESCAPE_01** — “Escort vessel clear. Accounting for crew.”
- **FRIGATE_ESCAPE_02** — “We've cleared the danger zone. Report all systems.”
- **FRIGATE_COVER_01** — “Move behind our shield arc. We have you.”
- **FRIGATE_COVER_02** — “Holding the defensive screen. Stay close.”
- **FRIGATE_LAUNCH_01** — “Escort flight cleared for launch.”
- **FRIGATE_LAUNCH_02** — “Support craft away. Establishing perimeter.”
- **FRIGATE_FIGHTER_ATTACK_01** — “Point defense, track incoming fighters.”
- **FRIGATE_FIGHTER_ATTACK_02** — “Hostile flight approaching. Intercept and screen.”
- **FRIGATE_SHIELDS_DOWN_01** — “Shield grid collapsed. Armor teams standing by.”
- **FRIGATE_SHIELDS_DOWN_02** — “Shields offline. Maintain defensive spacing.”
- **FRIGATE_LOW_AMMO_01** — “Magazines below reserve. Conserving fire.”
- **FRIGATE_LOW_AMMO_02** — “Gunnery, prioritize threats. Ammunition is low.”
- **FRIGATE_LOW_FUEL_01** — “Maneuver reserve low. Adjust formation speed.”
- **FRIGATE_LOW_FUEL_02** — “Engineering recommends reducing pursuit.”
- **FRIGATE_CASUALTIES_01** — “Medical teams to damaged compartments.”
- **FRIGATE_CASUALTIES_02** — “Crew losses reported. Stations being reassigned.”
- **FRIGATE_SUPPORT_01** — “Friendly vessel protected. Continue your mission.”
- **FRIGATE_SUPPORT_02** — “We're on station. Call if you need help.”
- **FRIGATE_VICTORY_01** — “Assigned sector secure. Escort duty continues.”
- **FRIGATE_VICTORY_02** — “Hostiles neutralized. All protected ships, report status.”

## DESTROYER — Hardened hunter

*Delivery:* Terse, disciplined, relentlessly aggressive.

- **DESTROYER_ATTACK_01** — “Target marked. Kill it.”
- **DESTROYER_ATTACK_02** — “Fire control green. Commence engagement.”
- **DESTROYER_DAMAGE_01** — “Hit received. Hunt continues.”
- **DESTROYER_DAMAGE_02** — “Compartment breached. Seal it. Keep firing.”
- **DESTROYER_RETREAT_01** — “Disengage. Cover our withdrawal.”
- **DESTROYER_RETREAT_02** — “Reverse the intercept. Now.”
- **DESTROYER_ESCAPE_01** — “Out of contact. Reset the batteries.”
- **DESTROYER_ESCAPE_02** — “Threat envelope cleared. Maintain readiness.”
- **DESTROYER_COVER_01** — “Weapons toward the threat. Ally behind us.”
- **DESTROYER_COVER_02** — “I have their approach. Move.”
- **DESTROYER_LAUNCH_01** — “Strike element out. Track their progress.”
- **DESTROYER_LAUNCH_02** — “Launch complete. Resume pursuit.”
- **DESTROYER_FIGHTER_ATTACK_01** — “Fighters incoming. Cut them down.”
- **DESTROYER_FIGHTER_ATTACK_02** — “Point defense, clear the sky.”
- **DESTROYER_SHIELDS_DOWN_01** — “Shields down. Armor takes over.”
- **DESTROYER_SHIELDS_DOWN_02** — “Barrier failed. No change to mission.”
- **DESTROYER_LOW_AMMO_01** — “Ammo low. Every shot counts.”
- **DESTROYER_LOW_AMMO_02** — “Reduce volleys. Preserve the kill capability.”
- **DESTROYER_LOW_FUEL_01** — “Fuel critical. End the chase.”
- **DESTROYER_LOW_FUEL_02** — “Propulsion reserve at minimum. Return vector.”
- **DESTROYER_CASUALTIES_01** — “Casualties reported. Replace the stations.”
- **DESTROYER_CASUALTIES_02** — “Medics forward. Weapons stay manned.”
- **DESTROYER_SUPPORT_01** — “Mark the hostile. We'll remove it.”
- **DESTROYER_SUPPORT_02** — “Covering fire established. Execute.”
- **DESTROYER_VICTORY_01** — “Target destroyed. Find the next one.”
- **DESTROYER_VICTORY_02** — “Threat eliminated. Sweep for survivors.”

## LIGHT CRUISER — Young command officer

*Delivery:* Energetic but measured, ambitious and eager to prove command.

- **LIGHT_CRUISER_ATTACK_01** — “All batteries, show them what we can do.”
- **LIGHT_CRUISER_ATTACK_02** — “Target solution confirmed. Let the squadron see our work.”
- **LIGHT_CRUISER_DAMAGE_01** — “Bridge reports a hit. We can handle it.”
- **LIGHT_CRUISER_DAMAGE_02** — “Damage control moving. Keep our heading.”
- **LIGHT_CRUISER_RETREAT_01** — “Executing withdrawal. We will fight another day.”
- **LIGHT_CRUISER_RETREAT_02** — “Helm, make it clean. They're watching us.”
- **LIGHT_CRUISER_ESCAPE_01** — “We're clear. That was expertly handled.”
- **LIGHT_CRUISER_ESCAPE_02** — “Combat zone astern. Well done, everyone.”
- **LIGHT_CRUISER_COVER_01** — “We'll take the exposed flank. Stay with us.”
- **LIGHT_CRUISER_COVER_02** — “Protecting the lead elements. We have the range.”
- **LIGHT_CRUISER_LAUNCH_01** — “Flight deck, send the interceptors.”
- **LIGHT_CRUISER_LAUNCH_02** — “Our support wing is away. Good hunting.”
- **LIGHT_CRUISER_FIGHTER_ATTACK_01** — “Hostile fighters? Good. Something for the secondaries.”
- **LIGHT_CRUISER_FIGHTER_ATTACK_02** — “Defensive guns, engage the incoming flight.”
- **LIGHT_CRUISER_SHIELDS_DOWN_01** — “Shields down. Don't panic. Rebalance power.”
- **LIGHT_CRUISER_SHIELDS_DOWN_02** — “Barrier lost. Helm, give our gunners room.”
- **LIGHT_CRUISER_LOW_AMMO_01** — “Gunnery, reduce expenditure. We need reserves.”
- **LIGHT_CRUISER_LOW_AMMO_02** — “We're burning through the stores too quickly.”
- **LIGHT_CRUISER_LOW_FUEL_01** — “Fuel running low. We need a shorter route.”
- **LIGHT_CRUISER_LOW_FUEL_02** — “Engineering wants us back before reserves vanish.”
- **LIGHT_CRUISER_CASUALTIES_01** — “Medical teams now. We bring our people home.”
- **LIGHT_CRUISER_CASUALTIES_02** — “Crew wounded. Secure those compartments.”
- **LIGHT_CRUISER_SUPPORT_01** — “We've got this sector. Push forward!”
- **LIGHT_CRUISER_SUPPORT_02** — “Assistance arriving. Your flank is secure.”
- **LIGHT_CRUISER_VICTORY_01** — “A fine engagement. Log the result.”
- **LIGHT_CRUISER_VICTORY_02** — “Sector won. The fleet will hear about this one.”

## CRUISER — Veteran naval captain

*Delivery:* Measured, dry, authoritative and experienced.

- **CRUISER_ATTACK_01** — “Gunnery, the usual arrangement. Engage.”
- **CRUISER_ATTACK_02** — “We have the range. Commence deliberate fire.”
- **CRUISER_DAMAGE_01** — “A regrettable impact. Damage control, report.”
- **CRUISER_DAMAGE_02** — “We've survived worse. Correct the list.”
- **CRUISER_RETREAT_01** — “Helm, take us out of this engagement.”
- **CRUISER_RETREAT_02** — “Enough. We will preserve the ship.”
- **CRUISER_ESCAPE_01** — “We are clear. Record the damage honestly.”
- **CRUISER_ESCAPE_02** — “Withdrawn in good order. Begin the inventory.”
- **CRUISER_COVER_01** — “Bring the secondaries around to cover our allies.”
- **CRUISER_COVER_02** — “A proper cruiser protects its formation.”
- **CRUISER_LAUNCH_01** — “Flight operations, dispatch the assigned wing.”
- **CRUISER_LAUNCH_02** — “Support squadron away. Keep them informed.”
- **CRUISER_FIGHTER_ATTACK_01** — “Enemy aircraft closing. Deal with them.”
- **CRUISER_FIGHTER_ATTACK_02** — “Point defense, remove those interruptions.”
- **CRUISER_SHIELDS_DOWN_01** — “Shield failure confirmed. Keep your composure.”
- **CRUISER_SHIELDS_DOWN_02** — “Armor is there for a reason. Maintain station.”
- **CRUISER_LOW_AMMO_01** — “Reduce the salvo rate. This is not a parade.”
- **CRUISER_LOW_AMMO_02** — “Ammunition is finite. Remind the gunners.”
- **CRUISER_LOW_FUEL_01** — “Engineering reports an inconvenient fuel state.”
- **CRUISER_LOW_FUEL_02** — “We shall return before running dry. Set course.”
- **CRUISER_CASUALTIES_01** — “Get the injured to medical. No excuses.”
- **CRUISER_CASUALTIES_02** — “We have casualties. Send the relief crews.”
- **CRUISER_SUPPORT_01** — “We are coming alongside. Hold your course.”
- **CRUISER_SUPPORT_02** — “Your defense is now our responsibility.”
- **CRUISER_VICTORY_01** — “Well fought. Now inspect the ship.”
- **CRUISER_VICTORY_02** — “Another engagement concluded. Tea may resume.”

## HEAVY CRUISER — Old-school line officer

*Delivery:* Resolute, traditional, unflinching.

- **HEAVY_CRUISER_ATTACK_01** — “Main battery, present broadside. Fire.”
- **HEAVY_CRUISER_ATTACK_02** — “The line stands. Break their center.”
- **HEAVY_CRUISER_DAMAGE_01** — “Armor has been tested. It will hold.”
- **HEAVY_CRUISER_DAMAGE_02** — “Damage noted. Restore fighting integrity.”
- **HEAVY_CRUISER_RETREAT_01** — “Retire by division. Keep the colors flying.”
- **HEAVY_CRUISER_RETREAT_02** — “We withdraw together. None left exposed.”
- **HEAVY_CRUISER_ESCAPE_01** — “Heavy cruiser clear. Muster every division.”
- **HEAVY_CRUISER_ESCAPE_02** — “The battle is astern. The duty is not.”
- **HEAVY_CRUISER_COVER_01** — “Interpose the armored belt. Guard our consorts.”
- **HEAVY_CRUISER_COVER_02** — “We are the wall. Hold behind us.”
- **HEAVY_CRUISER_LAUNCH_01** — “Auxiliary flight, proceed with honor.”
- **HEAVY_CRUISER_LAUNCH_02** — “Launch stations cleared. The line will cover you.”
- **HEAVY_CRUISER_FIGHTER_ATTACK_01** — “Air threats on the beam. Batteries, attend.”
- **HEAVY_CRUISER_FIGHTER_ATTACK_02** — “Enemy strike craft approach. Defend the line.”
- **HEAVY_CRUISER_SHIELDS_DOWN_01** — “Barriers have failed. Steel remains.”
- **HEAVY_CRUISER_SHIELDS_DOWN_02** — “Shields gone. We shall not yield.”
- **HEAVY_CRUISER_LOW_AMMO_01** — “Conserve the heavy shells. Aim with purpose.”
- **HEAVY_CRUISER_LOW_AMMO_02** — “Magazine reports dwindling stores. No waste.”
- **HEAVY_CRUISER_LOW_FUEL_01** — “Fuel reserves diminished. Helm, plan an orderly return.”
- **HEAVY_CRUISER_LOW_FUEL_02** — “Machinery needs replenishment. We shall oblige.”
- **HEAVY_CRUISER_CASUALTIES_01** — “The wounded come first. Attend your shipmates.”
- **HEAVY_CRUISER_CASUALTIES_02** — “Our losses will be accounted for by name.”
- **HEAVY_CRUISER_SUPPORT_01** — “Take shelter within our gun arcs.”
- **HEAVY_CRUISER_SUPPORT_02** — “The line does not abandon its own.”
- **HEAVY_CRUISER_VICTORY_01** — “The line has held. Stand proud.”
- **HEAVY_CRUISER_VICTORY_02** — “Enemy guns are silent. Maintain formation.”

## BATTLECRUISER — Aggressive fleet commander

*Delivery:* Bold, impatient, proud of speed and firepower.

- **BATTLECRUISER_ATTACK_01** — “Full power to the guns. Take their head off!”
- **BATTLECRUISER_ATTACK_02** — “Helm, close the distance. I want decisive fire.”
- **BATTLECRUISER_DAMAGE_01** — “They think that will stop us? Answer them.”
- **BATTLECRUISER_DAMAGE_02** — “We've taken a hit. Keep accelerating.”
- **BATTLECRUISER_RETREAT_01** — “Disengage at speed. We'll choose the next fight.”
- **BATTLECRUISER_RETREAT_02** — “Withdraw now. I want these guns back intact.”
- **BATTLECRUISER_ESCAPE_01** — “Clear of hostile range. Reset for the next strike.”
- **BATTLECRUISER_ESCAPE_02** — “We are out. That was a temporary interruption.”
- **BATTLECRUISER_COVER_01** — “Our broadside is their safe passage. Move!”
- **BATTLECRUISER_COVER_02** — “Protect the formation. Sweep the attackers away.”
- **BATTLECRUISER_LAUNCH_01** — “Strike squadrons, away. Keep pace with us.”
- **BATTLECRUISER_LAUNCH_02** — “Launch all ready craft. The hunt begins.”
- **BATTLECRUISER_FIGHTER_ATTACK_01** — “Enemy fighter screen sighted. Tear it open.”
- **BATTLECRUISER_FIGHTER_ATTACK_02** — “Secondaries, make us a corridor.”
- **BATTLECRUISER_SHIELDS_DOWN_01** — “Shields gone. Then close faster!”
- **BATTLECRUISER_SHIELDS_DOWN_02** — “Barrier down. No one slows this ship.”
- **BATTLECRUISER_LOW_AMMO_01** — “How many salvos remain? Make them lethal.”
- **BATTLECRUISER_LOW_AMMO_02** — “Magazines running low. No speculative fire.”
- **BATTLECRUISER_LOW_FUEL_01** — “Propulsion reserve is falling. End this quickly.”
- **BATTLECRUISER_LOW_FUEL_02** — “We're nearly out of fuel. Finish the strike.”
- **BATTLECRUISER_CASUALTIES_01** — “Get the wounded clear. We need every station.”
- **BATTLECRUISER_CASUALTIES_02** — “Medical parties forward. Protect our people.”
- **BATTLECRUISER_SUPPORT_01** — “Follow our wake. We'll force the opening.”
- **BATTLECRUISER_SUPPORT_02** — “Your flank is covered. Attack through it!”
- **BATTLECRUISER_VICTORY_01** — “That's how a battle is decided.”
- **BATTLECRUISER_VICTORY_02** — “Their line is broken. Press the advantage.”

## CARRIER — Flight-control director

*Delivery:* Precise, protective of pilots and careful with recovery.

- **CARRIER_ATTACK_01** — “Strike packages, targets are assigned. Execute.”
- **CARRIER_ATTACK_02** — “All wings, begin coordinated attack.”
- **CARRIER_DAMAGE_01** — “Carrier hit. Deck crews, report immediately.”
- **CARRIER_DAMAGE_02** — “Damage control, protect the launch facilities.”
- **CARRIER_RETREAT_01** — “Recall available craft. Carrier is withdrawing.”
- **CARRIER_RETREAT_02** — “All wings, recovery priority. We are leaving.”
- **CARRIER_ESCAPE_01** — “Carrier clear. Begin the recovery count.”
- **CARRIER_ESCAPE_02** — “We're out of range. I want every pilot accounted for.”
- **CARRIER_COVER_01** — “Combat air patrol, shield the threatened ship.”
- **CARRIER_COVER_02** — “Defensive wings, protect our escorts.”
- **CARRIER_LAUNCH_01** — “Launch control, clear the deck. Birds away.”
- **CARRIER_LAUNCH_02** — “Flight one, launch authorized. Fly safely.”
- **CARRIER_FIGHTER_ATTACK_01** — “Hostile fighters inbound. Interceptors scramble.”
- **CARRIER_FIGHTER_ATTACK_02** — “CAP, intercept incoming aircraft now.”
- **CARRIER_SHIELDS_DOWN_01** — “Carrier shields down. Tighten the screen.”
- **CARRIER_SHIELDS_DOWN_02** — “Barrier failure. All wings, protect the ship.”
- **CARRIER_LOW_AMMO_01** — “Strike stores are depleted. Adjust payloads.”
- **CARRIER_LOW_AMMO_02** — “Ordnance crews, report usable munitions.”
- **CARRIER_LOW_FUEL_01** — “Carrier reserve low. Prepare a recovery route.”
- **CARRIER_LOW_FUEL_02** — “Fuel allocation critical. Suspend unnecessary maneuvers.”
- **CARRIER_CASUALTIES_01** — “Medical teams to flight operations. Now.”
- **CARRIER_CASUALTIES_02** — “We have casualties on deck. Secure the lifts.”
- **CARRIER_SUPPORT_01** — “Recovery corridor open. Friendlies, come home.”
- **CARRIER_SUPPORT_02** — “Air cover assigned. You are not alone out there.”
- **CARRIER_VICTORY_01** — “All wings, return to carrier. Beautiful work.”
- **CARRIER_VICTORY_02** — “Hostiles cleared. Let's bring everyone home.”

## DREADNOUGHT — Monumental flag command

*Delivery:* Deep, very deliberate, intimidatingly calm.

- **DREADNOUGHT_ATTACK_01** — “Primary batteries. You may fire.”
- **DREADNOUGHT_ATTACK_02** — “The enemy line is before us. Break it.”
- **DREADNOUGHT_DAMAGE_01** — “The hull has been struck. Our purpose remains.”
- **DREADNOUGHT_DAMAGE_02** — “Damage stations, restore full fighting readiness.”
- **DREADNOUGHT_RETREAT_01** — “The fleet will withdraw beneath our protection.”
- **DREADNOUGHT_RETREAT_02** — “Set the withdrawal course. Hold the rear guard.”
- **DREADNOUGHT_ESCAPE_01** — “Flagship clear. Begin fleet-wide accounting.”
- **DREADNOUGHT_ESCAPE_02** — “Our forces have escaped the engagement. Recover the wounded.”
- **DREADNOUGHT_COVER_01** — “Bring the fortress guns to bear. Shield our allies.”
- **DREADNOUGHT_COVER_02** — “The fleet will find shelter beneath our fire.”
- **DREADNOUGHT_LAUNCH_01** — “Flight command, release the reserve wings.”
- **DREADNOUGHT_LAUNCH_02** — “All assigned squadrons, deploy under fleet authority.”
- **DREADNOUGHT_FIGHTER_ATTACK_01** — “Hostile craft approach. Defensive grid, engage.”
- **DREADNOUGHT_FIGHTER_ATTACK_02** — “Air defense stations, clear the threat.”
- **DREADNOUGHT_SHIELDS_DOWN_01** — “Primary barriers have fallen. The hull endures.”
- **DREADNOUGHT_SHIELDS_DOWN_02** — “Deflector collapse confirmed. Stand to your posts.”
- **DREADNOUGHT_LOW_AMMO_01** — “Principal magazines approaching depletion. Fire by command.”
- **DREADNOUGHT_LOW_AMMO_02** — “Conserve the heavy guns. Each volley must matter.”
- **DREADNOUGHT_LOW_FUEL_01** — “Our movement reserve is failing. Establish a secure bearing.”
- **DREADNOUGHT_LOW_FUEL_02** — “Engineering, report remaining propulsion endurance.”
- **DREADNOUGHT_CASUALTIES_01** — “All departments, account for your personnel.”
- **DREADNOUGHT_CASUALTIES_02** — “We will not abandon our wounded. Dispatch medical teams.”
- **DREADNOUGHT_SUPPORT_01** — “The flagship is with you. Hold steady.”
- **DREADNOUGHT_SUPPORT_02** — “You are under the protection of the fleet command.”
- **DREADNOUGHT_VICTORY_01** — “The enemy is defeated. Let the fleet recover.”
- **DREADNOUGHT_VICTORY_02** — “Victory is secured. Honor the fallen, repair the living.”

## Runtime rules

The 11 canonical hull banks cover all 13 existing radio event categories with two lines each, 286 individual cues. Every ship variant inherits the `spriteHull()` voice, including new chassis variants. Fleet tone settings change line register within the bank, never the underlying voice personality. Fighter craft launched by a carrier use the existing fighter-only channel, not the carrier bridge. Dialogue is cosmetic and does not alter combat RNG. Never play hundreds of simultaneous voices in large battles.
