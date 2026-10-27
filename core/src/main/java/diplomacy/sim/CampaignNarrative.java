package diplomacy.sim;

/**
 * Ten chapter briefings for the already-playable 500-stage campaign. All prose
 * is descriptive; victory still requires defeating all authored battle waves.
 * The five beats per chapter never secretly alter credits or combat rules.
 */
public final class CampaignNarrative {
    private CampaignNarrative() { }
    public record Brief(int chapter,String title,String intelligence,String beat) { }
    private static final String[] CHAPTERS={
        "Border Signals",
        "A Fractured Alliance",
        "Convoy Wars",
        "The Outer Admiralties",
        "Refugee Corridor",
        "The Broken Shield Line",
        "Signal of Treason",
        "Siege of the Old Worlds",
        "The Silent Armada",
        "Catastrophic Diplomacy"
    };
    private static final String[] INTEL={
        "Small screens contest the frontier",
        "Command vessels join the fighting",
        "Torpedo raids threaten supply lanes",
        "Frigate commands coordinate attacks",
        "Destroyer screens protect convoys",
        "Mobile command hubs hold the line",
        "Cruiser tenders fight attrition",
        "Heavy flagships reclaim strongholds",
        "Battlecruisers cut supply chains",
        "Capital squadrons settle the war"
    };
    private static final String[] BEATS={
        "First contact","Reconnaissance","Escalation","Counterstroke","Sector confrontation"
    };
    public static Brief forLevel(int level){
        LevelEconomy.Level mission=LevelEconomy.level(level);
        int chapter=mission.sector()-1;
        int phase=(mission.inSector()-1)/10;
        return new Brief(chapter+1,CHAPTERS[chapter],INTEL[chapter],BEATS[phase]);
    }
}
