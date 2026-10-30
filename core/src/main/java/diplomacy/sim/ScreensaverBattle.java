package diplomacy.sim;

import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seed-replayable, save-free, unattended exhibition battles using real fitted vessels. */
public final class ScreensaverBattle {
    public static final float RESULT_PAUSE_SECONDS=4f;
    private ScreensaverBattle(){}

    public record Round(long seed, Battle battle, Battle.Faction left, Battle.Faction right,
                        int leftTier, int rightTier, int environmentLevel,
                        List<Shipyard.Design> leftFleet, List<Shipyard.Design> rightFleet) {
        public Round {
            if(battle==null||left==null||right==null||left==right||
                    leftTier<1||leftTier>6||rightTier<1||rightTier>6||
                    environmentLevel<1||environmentLevel>LevelEconomy.LEVEL_COUNT)
                throw new IllegalArgumentException("Invalid screensaver round");
            leftFleet=List.copyOf(leftFleet);rightFleet=List.copyOf(rightFleet);
        }
    }

    public static Round create(long seed,TechnologyCatalog catalog){
        if(catalog==null)throw new IllegalArgumentException("Missing engineering catalog");
        Random random=new Random(seed);
        Battle.Faction[] factions=Battle.Faction.values();
        Battle.Faction left=factions[random.nextInt(factions.length)];
        Battle.Faction right=factions[random.nextInt(factions.length-1)];
        if(right.ordinal()>=left.ordinal())right=factions[right.ordinal()+1];
        int tierA=1+random.nextInt(6),tierB=1+random.nextInt(6);
        int countA=fleetSize(random),countB=fleetSize(random);
        int environment=1+random.nextInt(LevelEconomy.LEVEL_COUNT);
        List<Shipyard.Design> first=fleet(random,catalog,left,tierA,countA);
        List<Shipyard.Design> second=fleet(random,catalog,right,tierB,countB);
        Battle battle=Battle.duel(seed,first,second,catalog);
        FleetNames.Style[] traditions=FleetNames.Style.values();
        FleetNames.Style firstStyle=traditions[random.nextInt(traditions.length)];
        FleetNames.Style secondStyle=traditions[random.nextInt(traditions.length)];
        int[] serial={0,0};
        for(Battle.Ship ship:battle.ships()){
            int team=ship.team;
            ship.name=FleetNames.generate(team==0?firstStyle:secondStyle,
                    seed^(team*0x9e3779b97f4a7c15L),++serial[team]);
        }
        battle.setTeamRadioTone(0,CrewChatter.Tone.values()[
                random.nextInt(CrewChatter.Tone.values().length)]);
        battle.setTeamRadioTone(1,CrewChatter.Tone.values()[
                random.nextInt(CrewChatter.Tone.values().length)]);
        return new Round(seed,battle,left,right,tierA,tierB,environment,first,second);
    }

    /** Small patrols are more frequent than massive engagements; 200/side is possible. */
    private static int fleetSize(Random random){
        int roll=random.nextInt(100);
        if(roll<30)return 2+random.nextInt(15);
        if(roll<75)return 17+random.nextInt(32);
        if(roll<95)return 49+random.nextInt(72);
        return 121+random.nextInt(80);
    }

    private static List<Shipyard.Design> fleet(Random random,TechnologyCatalog catalog,
                                               Battle.Faction faction,int tier,int size){
        List<Shipyard.Hull> eligible=new ArrayList<>();
        for(Shipyard.Hull hull:Shipyard.Hull.shipyardOrder()){
            try{
                if(Shipyard.inspect(Shipyard.starter(faction,hull),catalog).valid())
                    eligible.add(hull);
            }catch(IllegalArgumentException unusable){ /* Never deploy invalid hulls. */ }
        }
        if(eligible.isEmpty())throw new IllegalStateException("No buildable hulls for "+faction);
        List<Shipyard.Design> ships=new ArrayList<>(size);
        for(int i=0;i<size;i++){
            Shipyard.Design design=Shipyard.starter(faction,
                    eligible.get(random.nextInt(eligible.size())));
            int vesselTier=1+random.nextInt(tier);
            // Prototype support systems are optional: do not turn every ship
            // into a fleet repair/shield transmitter simply by selecting its hull.
            for(Shipyard.Slot slot:design.hull.slots)
                if(slot.mount()==Shipyard.Mount.SUPPORT&&design.at(slot.index())!=null
                        &&random.nextDouble()<0.97)
                    design=ifValid(design,design.install(slot.index(),null),catalog);

            for(Shipyard.Slot slot:design.hull.slots){
                double frequency=switch(slot.mount()){
                    case WEAPON -> 0.91;
                    case DEFENSE -> 0.48;
                    case SYSTEM -> slot.index()<3?0:0.43;
                    case SUPPORT -> 0.035; // Rare purpose-equipped auxiliaries only.
                    case HABITAT -> 0; // Never gamble away crew or life support.
                };
                if(random.nextDouble()>=frequency)continue;
                List<TechnologyCatalog.Component> choices=
                        Shipyard.choices(catalog,faction,slot.mount());
                if(choices.isEmpty())continue;
                for(int attempt=0;attempt<3;attempt++){
                    String id=choices.get(random.nextInt(choices.size())).id;
                    Shipyard.Design candidate=design.install(slot.index(),id);
                    if(Shipyard.inspect(candidate,catalog).valid()){
                        design=candidate;break;
                    }
                }
            }
            for(int slot=0;slot<design.slots();slot++){
                if(design.at(slot)==null||random.nextDouble()>=0.52)continue;
                Shipyard.Track[] tracks=Shipyard.Track.values();
                Shipyard.Track branch=tracks[random.nextInt(tracks.length)];
                int stage=1+random.nextInt(vesselTier);
                try{
                    design=ifValid(design,design.stage(slot,branch,stage),catalog);
                }catch(IllegalArgumentException unavailable){ /* Preserve approved fitting. */ }
                if(vesselTier>=2&&random.nextDouble()<0.19){
                    ModuleVersions.Pattern[] patterns=ModuleVersions.Pattern.values();
                    ModuleVersions.Pattern pattern=patterns[random.nextInt(patterns.length)];
                    int mark=1+random.nextInt(Math.min(4,vesselTier+1));
                    try{
                        design=ifValid(design,
                                design.withModuleVersion(slot,pattern,mark),catalog);
                    }catch(IllegalArgumentException unavailable){ /* Base hardware remains valid. */ }
                }
            }
            if(!Shipyard.inspect(design,catalog).valid())
                throw new IllegalStateException("Screensaver generated unbuildable fitting");
            ships.add(design);
        }
        return List.copyOf(ships);
    }
    private static Shipyard.Design ifValid(Shipyard.Design original,
                                          Shipyard.Design candidate,TechnologyCatalog catalog){
        return Shipyard.inspect(candidate,catalog).valid()?candidate:original;
    }
}
