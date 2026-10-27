package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * A campaign mission is a sequence of hostile reinforcements in ONE battle.
 * Friendly hull damage, ammunition, shields and launched craft persist between waves.
 * Only completing every wave with survivors qualifies for a campaign victory.
 */
public final class CampaignEncounter {
    public enum Advance { ACTIVE, NEXT_WAVE, VICTORY, DEFEAT }
    private final LevelEconomy.Level level;
    private final int deployed;
    private final Shipyard.Design opponent;
    private final TechnologyCatalog catalog;
    private final Battle battle;
    private final CampaignDeployment.Loadout deployment;
    private int wave = 1;

    private CampaignEncounter(LevelEconomy.Level level,CampaignDeployment.Loadout deployment,
                              Shipyard.Design friendly,Shipyard.Design hostile,
                              TechnologyCatalog catalog) {
        if(level==null||deployment==null||friendly==null||hostile==null||catalog==null)
            throw new IllegalArgumentException("Campaign requires a stage and fitted fleets");
        int deployed=deployment.ships();
        if(deployed>level.maximumFleet())
            throw new IllegalArgumentException("Deployment exceeds mission fleet capacity");
        this.level=level;
        this.deployed=deployed;
        this.deployment=deployment;
        this.opponent=hostile;
        this.catalog=catalog;
        battle=new Battle(level.seed());
        battle.spawnWing(0,friendly,catalog,deployed,310,335);
        deployment.apply(battle);
        deployHostiles();
    }

    public static CampaignEncounter start(int level,int deployed,Shipyard.Design friendly,
                                          Shipyard.Design hostile,TechnologyCatalog catalog) {
        return start(level,new CampaignDeployment.Loadout(deployed,CampaignDeployment.Formation.LINE),
                friendly,hostile,catalog);
    }
    public static CampaignEncounter start(int level,CampaignDeployment.Loadout deployment,
                                          Shipyard.Design friendly,Shipyard.Design hostile,
                                          TechnologyCatalog catalog) {
        return new CampaignEncounter(LevelEconomy.level(level),deployment,friendly,hostile,catalog);
    }

    private void deployHostiles(){
        battle.spawnWing(1,opponent,catalog,enemyCount(level,deployed,wave),970,335);
    }

    /** Deterministic wave sizes scale within each stage's physical fleet cap. */
    public static int enemyCount(LevelEconomy.Level level,int deployed,int wave){
        if(level==null||deployed<1||deployed>level.maximumFleet()
                ||wave<1||wave>level.waves())
            throw new IllegalArgumentException("Invalid wave specification");
        return Math.max(1,Math.min(level.maximumFleet(),deployed+(wave-1)));
    }

    public Battle battle(){return battle;}
    public int wave(){return wave;}
    public int waves(){return level.waves();}
    public int deployed(){return deployed;}
    public CampaignDeployment.Loadout deployment(){return deployment;}
    public int level(){return level.index();}

    /**
     * Must run after each combat tick and before declaring the encounter over.
     * The same Battle instance is retained across reinforcements; it is never reset.
     */
    public Advance advance(){
        // Retreating captains have already surrendered field control, even
        // if their engines have not carried them over the rear line yet.
        if(battle.engaged(0)==0)return Advance.DEFEAT;
        if(battle.alive(1)>0)return Advance.ACTIVE;
        if(wave==level.waves())return Advance.VICTORY;
        wave++;
        deployHostiles();
        return Advance.NEXT_WAVE;
    }
}
