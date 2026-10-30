package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

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
    private int hostileSerial;

    private CampaignEncounter(LevelEconomy.Level level,CampaignDeployment.Loadout deployment,
                              List<Shipyard.Design> friendlies,Shipyard.Design hostile,
                              TechnologyCatalog catalog) {
        if(level==null||deployment==null||friendlies==null||hostile==null||catalog==null)
            throw new IllegalArgumentException("Campaign requires a stage and fitted fleets");
        int deployed=deployment.ships();
        if(deployed>level.maximumFleet()||friendlies.size()!=deployed||
                friendlies.stream().anyMatch(x->x==null))
            throw new IllegalArgumentException("Deployment or commissioned fleet invalid");
        this.level=level;
        this.deployed=deployed;
        this.deployment=deployment;
        this.opponent=hostile;
        this.catalog=catalog;
        battle=new Battle(level.seed());
        // Each owned ship has its own fitting; do not clone one generic design.
        for(Shipyard.Design friendly:friendlies)
            battle.spawnWing(0,friendly,catalog,1,310,335);
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
        return new CampaignEncounter(LevelEconomy.level(level),deployment,
                java.util.Collections.nCopies(deployment.ships(),friendly),hostile,catalog);
    }
    public static CampaignEncounter start(int level,CampaignDeployment.Loadout deployment,
                                          List<Shipyard.Design> friendlies,Shipyard.Design hostile,
                                          TechnologyCatalog catalog){
        return new CampaignEncounter(LevelEconomy.level(level),deployment,friendlies,hostile,catalog);
    }

    private void deployHostiles(){
        int first=battle.ships().size();
        var fittings=CampaignOpposition.fleet(level.index(),wave,
                enemyCount(level,deployed,wave),opponent.faction,catalog);
        // Actual independent ship designs, not a wing of copies of one template.
        battle.spawnFleet(1,fittings,catalog,970,335);
        CampaignOpposition.assignOrders(battle,first,level.index(),wave);
        FleetNames.Style[] styles=FleetNames.Style.values();
        FleetNames.Style naming=styles[Math.floorMod(
                level.sector()+opponent.faction.ordinal(),styles.length)];
        for(int i=first;i<battle.ships().size();i++){
            Battle.Ship hostile=battle.ships().get(i);
            hostile.name=FleetNames.generate(naming,level.seed(),++hostileSerial);
        }
    }

    /** Mission-role-dependent wave sizes within each stage's physical fleet cap. */
    public static int enemyCount(LevelEconomy.Level level,int deployed,int wave){
        if(level==null||deployed<1||deployed>level.maximumFleet()
                ||wave<1||wave>level.waves())
            throw new IllegalArgumentException("Invalid wave specification");
        // A carrier/command/siege mission trades numbers for equipment,
        // while a screen or sector-capital confrontation fields more hulls.
        int percent=switch(CampaignOpposition.operation(level.index())){
            case PATROL -> 100;
            case INTERDICTION,TORPEDO_RAID,STRIKE_WING -> 85;
            case SCREEN,BREAKTHROUGH -> 110;
            case CONVOY,COMMAND -> 90;
            case SIEGE -> 80;
            case FLEET_BATTLE -> 115;
            case SECTOR_CAPITAL -> 125;
        };
        int initial=Math.max(1,(deployed*percent+99)/100);
        return Math.min(level.maximumFleet(),initial+wave-1);
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
