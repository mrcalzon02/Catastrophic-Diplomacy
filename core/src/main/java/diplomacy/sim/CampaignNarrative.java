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
        "Baseline interceptors and corvettes secure the frontier",
        "Upgraded frigates, destroyers and torpedoes contest the lanes",
        "Cruiser and battlecruiser hulls expand the line of battle",
        "Carriers and dreadnoughts complete the base fleet families",
        "Specialist strike and flak screens complicate every engagement",
        "Electronic warfare and command variants contest targeting",
        "Logistics tenders turn battles into contests of endurance",
        "Tech VII veteran formations defend the old worlds",
        "Tech VIII experimental ships harass every supply route",
        "Tech IX apex fleets challenge the final sector capitals"
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
