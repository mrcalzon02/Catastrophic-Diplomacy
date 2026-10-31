package diplomacy.sim;

/**
 * Named strategic geography shared by the campaign's 500 mission star map.
 * Narrative geography only; legal fittings and battle objectives remain in the
 * existing deterministic campaign systems. Source map: docs/campaign/ATLAS_500.json.
 */
public final class CampaignStoryAtlas {
    private CampaignStoryAtlas() { }
    private static final String[] REGIONS={
        "The Ash Meridian", "The Shattered Compact", "The Pilgrim Span",
        "The Outer Crowns", "The Sanctuary March", "The Aegis Break",
        "The Archive Rift", "The Cradle Systems", "The Hollow Expanse",
        "The Coreward Silence"
    };
    public static String regionName(int mission){
        return REGIONS[LevelEconomy.level(mission).sector()-1];
    }
    private static final String[] FRONTS={
        "Glasswake",
        "Cinder",
        "Lamplight",
        "Treaty",
        "Starlit",
        "Quenched",
        "Red Warrant",
        "Oathbreak",
        "Twin Crown",
        "Relay Crown",
        "Thorn",
        "Pilgrim",
        "Shard",
        "Manifest",
        "Haven Crown",
        "Obsidian",
        "Black Wing",
        "Parley",
        "Vault",
        "Admiral",
        "Lantern",
        "Sundered",
        "Mercy",
        "Cipher",
        "Refuge",
        "Veil",
        "Knife",
        "Ashfall",
        "Blind",
        "Null",
        "Daedalus",
        "Witness",
        "Glass",
        "Quiet",
        "Protocol",
        "Crown",
        "Tribunal",
        "Accord",
        "Bulwark",
        "Citadel",
        "Cold Wake",
        "Shroud",
        "Treaty Engine",
        "Trace",
        "Beacon",
        "Vanguard",
        "Severance",
        "Furnace",
        "Chancery",
        "Article Zero"
    };
    private static final String[] MANDATES={
        "Trace an impossible distress beacon",
        "Verify which navy owns a disputed transponder",
        "Follow raiders crossing unsigned borders",
        "Recover the first surviving original treaty archive",
        "Stop the first sector command fleet",
        "Keep neutral docks open amid sanctions",
        "Find the source of intersystem arrest orders",
        "Prevent allied captains from firing on each other",
        "Separate true command traffic from forged fleet deployments",
        "Take a treaty communications capital intact",
        "Keep essential imports moving",
        "Hold a civilian evacuation corridor",
        "Stop privateers targeting relief ships",
        "Recover freight records without destroying their carriers",
        "Save the great convoy junction",
        "Survey industrial docks building oversized warships",
        "Find a rogue battle group inside a broken navy",
        "Secure a meeting between enemy officers",
        "Recover a capital-ship command memory",
        "Defeat the admiralty capital without destroying the archive",
        "Defend moving civilian sanctuary ships",
        "Open mined supply rails and safe routes",
        "Protect a prisoner and refugee transfer",
        "Trace hostile fleet positioning orders",
        "Hold the corridor's sector capital",
        "Restore long-range radar with autonomous opponents nearby",
        "Protect ships crossing blinded sensor lanes",
        "Recover damaged early-warning stations",
        "Fight through coordinated electronic deception",
        "Keep the final defense capital operational",
        "Locate a transmitter hidden inside shattered diplomatic stations",
        "Guard the last complete record of the first border incident",
        "Copy the original autonomous arbitration protocol",
        "Keep rival diplomats alive long enough to compare signatures",
        "Break the system-wide propaganda signal",
        "Withdraw civilian populations without abandoning defenses",
        "Protect judicial and diplomatic centers under siege",
        "Make former enemy fleet groups fight alongside each other",
        "Hold the outer shield line around the oldest worlds",
        "Break the siege of the sector capital",
        "Investigate warships with no life signs",
        "Destroy automated yards replenishing the enemy",
        "Disrupt treaty-enforcement drone command routes",
        "Gather forces for a coalition counteroffensive",
        "Silence the source of automated reinforcements",
        "Lead the coalition through the forward defense fleets",
        "Open the last navigable route to the arbitration core",
        "Cut logistics to the core by destroying mobile repair docks",
        "Neutralize relay fortresses and false ceasefire orders",
        "Commit the coalition to the final seven linked fleet actions"
    };
    public static String frontObjective(int mission){
        LevelEconomy.level(mission);
        return MANDATES[(mission-1)/10];
    }
    private static final String[] WAYPOINTS={
        "Threshold",
        "Pale Signal",
        "Outer Track",
        "Crossing",
        "Hollow Shelf",
        "Garrison",
        "Broken Arc",
        "Underwatch",
        "Darkward",
        "Heartline"
    };
    private static final String[] FINALES={
        "The Needle Gate",
        "The Broken Crown",
        "Graveyard of Delegates",
        "The Furnace Ring",
        "The Silent Chancery",
        "The Last Armada",
        "Article Zero"
    };
    public static String nodeName(int mission){
        LevelEconomy.level(mission); // validates campaign range
        if(mission>=LevelEconomy.FINALE_START)
            return FINALES[mission-LevelEconomy.FINALE_START];
        int front=(mission-1)/10,waypoint=(mission-1)%10;
        return FRONTS[front]+" "+WAYPOINTS[waypoint];
    }
}
