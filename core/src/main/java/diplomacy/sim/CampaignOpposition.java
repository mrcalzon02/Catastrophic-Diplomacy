package diplomacy.sim;

import diplomacy.shipyard.HullProgression;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic campaign threat progression. All mission ships are real,
 * independently fitted faction hulls; the campaign does not give the AI
 * invisible research multipliers or bypass the physical fitting validator.
 * Enemy permissions are per mission, not the player's funded tech inventory.
 */
public final class CampaignOpposition {
    private CampaignOpposition(){}

    public enum Operation {
        PATROL("Patrol",new Shipyard.Hull[]{Shipyard.Hull.INTERCEPTOR,Shipyard.Hull.CORVETTE}),
        INTERDICTION("Interdiction",new Shipyard.Hull[]{Shipyard.Hull.MISSILE_CORVETTE,Shipyard.Hull.FRIGATE,Shipyard.Hull.CORVETTE}),
        SCREEN("Escort screen",new Shipyard.Hull[]{Shipyard.Hull.DESTROYER,Shipyard.Hull.FRIGATE,Shipyard.Hull.INTERCEPTOR}),
        CONVOY("Convoy battle",new Shipyard.Hull[]{Shipyard.Hull.FRIGATE,Shipyard.Hull.LIGHT_CRUISER,Shipyard.Hull.CORVETTE}),
        TORPEDO_RAID("Torpedo raid",new Shipyard.Hull[]{Shipyard.Hull.MISSILE_CORVETTE,Shipyard.Hull.DESTROYER,Shipyard.Hull.FRIGATE}),
        STRIKE_WING("Strike wing",new Shipyard.Hull[]{Shipyard.Hull.CARRIER,Shipyard.Hull.FRIGATE,Shipyard.Hull.MISSILE_CORVETTE}),
        SIEGE("Siege line",new Shipyard.Hull[]{Shipyard.Hull.DREADNOUGHT,Shipyard.Hull.BATTLECRUISER,Shipyard.Hull.CRUISER}),
        BREAKTHROUGH("Breakthrough",new Shipyard.Hull[]{Shipyard.Hull.HEAVY_CRUISER,Shipyard.Hull.DESTROYER,Shipyard.Hull.CORVETTE}),
        COMMAND("Command fleet",new Shipyard.Hull[]{Shipyard.Hull.CARRIER,Shipyard.Hull.LIGHT_CRUISER,Shipyard.Hull.FRIGATE}),
        FLEET_BATTLE("Fleet action",new Shipyard.Hull[]{Shipyard.Hull.BATTLECRUISER,Shipyard.Hull.CRUISER,Shipyard.Hull.DESTROYER}),
        SECTOR_CAPITAL("Sector capital",new Shipyard.Hull[]{Shipyard.Hull.DREADNOUGHT,Shipyard.Hull.CARRIER,Shipyard.Hull.BATTLECRUISER});
        public final String title;
        private final Shipyard.Hull[] preferred;
        Operation(String title,Shipyard.Hull[] preferred){
            this.title=title;this.preferred=preferred;
        }
    }

    private static final Operation[] CIRCUIT={
            Operation.PATROL,Operation.INTERDICTION,Operation.SCREEN,
            Operation.CONVOY,Operation.TORPEDO_RAID,Operation.STRIKE_WING,
            Operation.SIEGE,Operation.BREAKTHROUGH,Operation.COMMAND,
            Operation.FLEET_BATTLE
    };
    private static final String[] BASIC_WEAPONS={"LAS","KIN","MSL"};
    private static final String[] TORPEDO_WEAPONS={"LAS","KIN","MSL","TOR"};
    private static final String[] ADVANCED_WEAPONS={"LAS","KIN","MSL","TOR","PLA","PAR"};

    public static Operation operation(int mission){
        LevelEconomy.Level level=LevelEconomy.level(mission);
        return level.inSector()==50?Operation.SECTOR_CAPITAL:
                CIRCUIT[(level.inSector()-1)%CIRCUIT.length];
    }

    /** Sector I is Tech 0, sectors II-X advance inclusively to Tech IX. */
    public static int maximumTier(int mission){
        return LevelEconomy.level(mission).sector()-1;
    }

    /** The final ships in each wave favor current-tier equipment; older hulls
     * remain viable throughout all 500 missions. */
    private static int fittedTier(Random random,int maximum){
        if(maximum==0)return 0;
        int age=random.nextInt(100);
        return Math.max(0,maximum-(age<56?0:age<84?1:2));
    }

    private static Shipyard.Hull chooseHull(Random random,int tier,Operation mission){
        List<Shipyard.Hull> candidate=new ArrayList<>();
        if(random.nextInt(100)<68)for(Shipyard.Hull hull:mission.preferred)
            if(HullProgression.available(hull,tier))candidate.add(hull);
        if(candidate.isEmpty())for(Shipyard.Hull hull:Shipyard.Hull.shipyardOrder())
            if(HullProgression.base(hull)&&HullProgression.available(hull,tier))
                candidate.add(hull);
        Shipyard.Hull selected=candidate.get(random.nextInt(candidate.size()));
        // Specialist hulls are rarer than basic combat ships and become
        // available no earlier than their real shared hull licenses.
        if(tier>=4&&random.nextInt(100)<24){
            List<Shipyard.Hull> variants=new ArrayList<>();
            for(Shipyard.Hull hull:Shipyard.Hull.shipyardOrder())
                if(!HullProgression.base(hull)&&hull.spriteHull()==selected
                        &&HullProgression.available(hull,tier))
                    variants.add(hull);
            if(!variants.isEmpty())selected=variants.get(random.nextInt(variants.size()));
        }
        return selected;
    }

    private static String weapon(Random random,int tier,Operation mission){
        String[] list=tier==0?BASIC_WEAPONS:
                tier==1?TORPEDO_WEAPONS:ADVANCED_WEAPONS;
        if(mission==Operation.TORPEDO_RAID&&tier>=1&&random.nextInt(3)!=0)return "TOR";
        if(mission==Operation.STRIKE_WING&&random.nextInt(3)!=0)return "MSL";
        if(mission==Operation.SIEGE||mission==Operation.SECTOR_CAPITAL)
            if(tier>=2&&random.nextInt(3)!=0)return random.nextBoolean()?"PAR":"PLA";
        if(mission==Operation.INTERDICTION&&random.nextBoolean())return "MSL";
        return list[random.nextInt(list.length)];
    }

    private static Shipyard.Design validOr(Shipyard.Design current,Shipyard.Design proposed,
                                           TechnologyCatalog catalog){
        return Shipyard.inspect(proposed,catalog).valid()?proposed:current;
    }

    private static Shipyard.Design fit(Random random,Shipyard.Hull hull,
                                       Battle.Faction faction,int tier,Operation operation,
                                       TechnologyCatalog catalog){
        Shipyard.Design design=Shipyard.starter(faction,hull);
        if(!Shipyard.inspect(design,catalog).valid()){
            // Some experimental chassis require unfinished service modules.
            // Keep all actual deployments buildable and never fabricate stats.
            design=Shipyard.starter(faction,hull.spriteHull());
        }
        if(!Shipyard.inspect(design,catalog).valid())
            throw new IllegalStateException("Unbuildable campaign starter "+faction+" "+hull);
        String prefix="BASE-"+Shipyard.factionId(faction)+"-";
        int weaponIndex=0;
        for(Shipyard.Slot slot:design.hull.slots){
            if(slot.mount()!=Shipyard.Mount.WEAPON||weaponIndex++>=2)continue;
            // Retain actual purpose-fitted support/hull weapon configurations
            // only when replacing a module passes full engineering inspection.
            String family=weapon(random,tier,operation);
            design=validOr(design,design.install(slot.index(),prefix+family),catalog);
        }
        if(tier>=3&&(operation==Operation.STRIKE_WING||operation==Operation.COMMAND)
                &&design.hull.spriteHull()==Shipyard.Hull.CARRIER){
            for(Shipyard.Slot slot:design.hull.slots)if(slot.mount()==Shipyard.Mount.SUPPORT){
                design=validOr(design,design.install(slot.index(),prefix+"FTR"),catalog);
                break;
            }
        }
        // Higher technology changes the installed equipment, rather than
        // merely decorating a battle with an unearned tech-tier label.
        if(tier>0)for(Shipyard.Slot slot:design.hull.slots){
            Shipyard.Installed installed=design.at(slot.index());
            if(installed==null||slot.mount()==Shipyard.Mount.HABITAT
                    ||slot.mount()==Shipyard.Mount.SUPPORT)continue;
            int likelihood=switch(slot.mount()){
                case WEAPON -> 80;
                case DEFENSE -> 62;
                case SYSTEM -> 55;
                default -> 0;
            };
            if(random.nextInt(100)>=likelihood)continue;
            int rank=Math.max(1,tier-random.nextInt(Math.min(tier,3)));
            Shipyard.Track track=switch(slot.mount()){
                case WEAPON -> Shipyard.Track.OUTPUT;
                case DEFENSE -> Shipyard.Track.RESILIENCE;
                default -> installed.component.endsWith("-RAD")?
                        Shipyard.Track.EFFICIENCY:Shipyard.Track.OUTPUT;
            };
            for(int stage=rank;stage>=1;stage--){
                try{
                    Shipyard.Design candidate=design.stage(slot.index(),track,stage);
                    if(Shipyard.inspect(candidate,catalog).valid()){
                        design=candidate;break;
                    }
                }catch(IllegalArgumentException unavailable){
                    // This physical component has no compatible branch.
                }
            }
            if(tier>=3&&random.nextInt(100)<24){
                ModuleVersions.Pattern[] patterns=ModuleVersions.Pattern.values();
                try{
                    Shipyard.Design candidate=design.withModuleVersion(slot.index(),
                            patterns[random.nextInt(patterns.length)],
                            1+random.nextInt(Math.min(4,tier+1)));
                    design=validOr(design,candidate,catalog);
                }catch(IllegalArgumentException unavailable){
                    // Retain the proven conventional installed module.
                }
            }
        }
        if(!Shipyard.inspect(design,catalog).valid())
            throw new IllegalStateException("Campaign generated invalid fitting");
        return design;
    }

    /**
     * Generate one wave without reading or modifying the player's campaign
     * licenses, save file, purchased roster, or persistent research.
     */
    public static List<Shipyard.Design> fleet(int mission,int wave,int count,
                                             Battle.Faction faction,TechnologyCatalog catalog){
        LevelEconomy.Level level=LevelEconomy.level(mission);
        if(wave<1||wave>level.waves()||count<1||count>level.maximumFleet()
                ||faction==null||catalog==null)
            throw new IllegalArgumentException("Invalid campaign enemy wave");
        Random random=new Random(LevelSky.mix(level.seed()^
                ((long)wave*0x9e3779b97f4a7c15L)^((long)faction.ordinal()<<40)));
        int tier=maximumTier(mission);
        Operation operation=operation(mission);
        List<Shipyard.Design> ships=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            int fittedTier=fittedTier(random,tier);
            // The commander's flagship is at the mission's full engineering ceiling.
            if(i==0&&wave==level.waves())fittedTier=tier;
            Shipyard.Hull hull=chooseHull(random,fittedTier,operation);
            ships.add(fit(random,hull,faction,fittedTier,operation,catalog));
        }
        return List.copyOf(ships);
    }

    /** Mission doctrine changes actual captain AI without rewriting fleet saves. */
    public static void assignOrders(Battle battle,int firstShipIndex,int mission,int wave){
        if(battle==null||firstShipIndex<0||firstShipIndex>=battle.ships().size())
            throw new IllegalArgumentException("Invalid campaign reinforcement batch");
        Operation operation=operation(mission);
        for(int i=firstShipIndex;i<battle.ships().size();i++){
            Battle.Ship ship=battle.ships().get(i);
            if(ship.team!=1)throw new IllegalArgumentException("Enemy reinforcements expected");
            BattleOrders.Targeting targeting=switch(operation){
                case PATROL,INTERDICTION -> BattleOrders.Targeting.NEAREST;
                case SCREEN,CONVOY,COMMAND -> BattleOrders.Targeting.PROTECT;
                case TORPEDO_RAID,STRIKE_WING,BREAKTHROUGH -> BattleOrders.Targeting.VULTURE;
                case SIEGE,FLEET_BATTLE,SECTOR_CAPITAL -> BattleOrders.Targeting.COOPERATE;
            };
            BattleOrders.Position movement=switch(operation){
                case SCREEN,CONVOY,COMMAND -> BattleOrders.Position.CAUTIOUS;
                case SIEGE,SECTOR_CAPITAL -> BattleOrders.Position.LAST_STAND;
                case PATROL -> BattleOrders.Position.FREE;
                default -> BattleOrders.Position.KEEP_MOVING;
            };
            BattleOrders.Orders tactics=BattleOrders.DEFAULT.withTargeting(targeting)
                    .withPosition(movement)
                    .withRetreat(movement==BattleOrders.Position.LAST_STAND?0:0.12);
            battle.setShipStandingOrders(ship.id,new ShipStandingOrders(tactics,
                    45+(i%5)*9,35+(i%4)*10,0));
        }
    }
}
