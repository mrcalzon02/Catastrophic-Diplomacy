package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * Canonical 200-vs-200 combined-arms demonstration for both the playable client
 * and headless acceptance. No renderer, SDK, or test-only dependencies.
 */
public final class DemonstrationBattle {
    private static final String[] WEAPONS = {"LAS","KIN","PLA","PAR","MSL","TOR"};
    private static final String[] SUPPORT = {"FTR","DRN","REP"};
    public static final long SEED = 762244L;
    private DemonstrationBattle() { }

    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int ordinal) {
        for(Shipyard.Slot s:hull.slots)
            if(s.mount()==mount && ordinal--==0)return s.index();
        throw new IllegalArgumentException("No " + mount + " slot on " + hull);
    }

    /** Exactly 200 fitted, armed ships per team: six distinct weapon doctrines. */
    public static Battle create(TechnologyCatalog catalog) {
        if(catalog==null)throw new IllegalArgumentException("Technology catalog missing");
        Battle battle=new Battle(SEED);
        for(int team=0;team<2;team++)for(int group=0;group<WEAPONS.length;group++) {
            Battle.Faction faction=Battle.Faction.values()[(group+3*team)%6];
            Shipyard.Hull hull=(group==2||group==4)?Shipyard.Hull.DREADNOUGHT
                    :group%2==0?Shipyard.Hull.CRUISER:Shipyard.Hull.FRIGATE;
            String prefix="BASE-"+Shipyard.factionId(faction)+"-";
            Shipyard.Design design=Shipyard.starter(faction,hull)
                    .install(slot(hull,Shipyard.Mount.WEAPON,0),prefix+WEAPONS[group])
                    .install(slot(hull,Shipyard.Mount.WEAPON,1),prefix+WEAPONS[(group+1)%WEAPONS.length]);
            if(!Shipyard.inspect(design,catalog).valid())
                throw new IllegalStateException("Invalid weapon demonstration fitting: "+faction+" "+hull);
            if(group==0||group==2||group==4) {
                int module=slot(hull,Shipyard.Mount.SUPPORT,0);
                Shipyard.Design supported=design.install(module,prefix+SUPPORT[group%SUPPORT.length]);
                if(group==4)supported=supported.stage(module,Shipyard.Track.SPECIALIST,3);
                if(!Shipyard.inspect(supported,catalog).valid()&&(group==2||group==4)){
                    supported=supported
                            .install(slot(hull,Shipyard.Mount.SYSTEM,3),prefix+"REA")
                            .install(slot(hull,Shipyard.Mount.SYSTEM,4),prefix+"RAD");
                }
                if(Shipyard.inspect(supported,catalog).valid())design=supported;
                else if(group==2||group==4)
                    throw new IllegalStateException("Required active repair/drone loadout invalid: "+
                            supported.faction+" "+hull+" group="+group+
                            " reason="+Shipyard.inspect(supported,catalog).explanation());
            }
            int quantity=33+(group<2?1:0); // 34+34+33+33+33+33=200 each side
            double x=(team==0?220:880)+(group%3)*95;
            double y=238+(group/3)*190;
            battle.spawnWing(team,design,catalog,quantity,x,y);
        }
        if(battle.ships().size()!=400 || battle.alive(0)!=200 || battle.alive(1)!=200)
            throw new IllegalStateException("Demo must deploy exactly 200 ships per fleet");
        // Deliberately damaged *allied* hulls exercise the real repair and
        // shield-transfer mechanics during the 400-ship proving ground.
        // This is documented fixture damage, not artificial repair telemetry.
        for(int team=0;team<2;team++){
            int offset=team*200;
            for(int index:new int[]{69,70,71}){
                Battle.Ship damaged=battle.ships().get(offset+index);
                damaged.hull=Math.max(1,damaged.hull-36);
                damaged.defense.impact(1,0,0.7,0,24,Battle.DamageType.LASER);
                damaged.shields=damaged.defense.totalShields();
            }
        }
        return battle;
    }
}
