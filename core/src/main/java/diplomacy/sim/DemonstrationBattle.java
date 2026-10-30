package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/**
 * Canonical 200-vs-200 combined-arms demonstration for both the playable client
 * and headless acceptance. No renderer, SDK, or test-only dependencies.
 */
public final class DemonstrationBattle {
    private static final String[] WEAPONS = {"LAS","KIN","PLA","PAR","MSL","TOR"};
    /** One visual civilization PER side; vary the weapons, never the race. */
    public static final Battle.Faction[] SIDES = {
            Battle.Faction.VAERITH_PRISM,Battle.Faction.AUREATE_SYNOD
    };
    // Wing 0 carries fighters; groups 2 and 4 contain ONE purpose-fitted
    // service hull each, never 33 invisible fleet-wide support operators.
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
            Battle.Faction faction=SIDES[team];
            Shipyard.Hull hull=(group==2||group==4)?Shipyard.Hull.DREADNOUGHT
                    :group%2==0?Shipyard.Hull.CRUISER:Shipyard.Hull.FRIGATE;
            String prefix="BASE-"+Shipyard.factionId(faction)+"-";
            Shipyard.Design design=Shipyard.starter(faction,hull)
                    .install(slot(hull,Shipyard.Mount.WEAPON,0),prefix+WEAPONS[group])
                    .install(slot(hull,Shipyard.Mount.WEAPON,1),prefix+WEAPONS[(group+1)%WEAPONS.length]);
            if(!Shipyard.inspect(design,catalog).valid())
                throw new IllegalStateException("Invalid weapon demonstration fitting: "+faction+" "+hull);
            int quantity=33+(group<2?1:0); // 34+34+33+33+33+33 = 200 each
            double x=(team==0?220:880)+(group%3)*95;
            double y=238+(group/3)*190;
            if(group==0){
                // Fighter carrier formation is not a repair/shield-relay fleet.
                Shipyard.Design fighter=design.install(
                        slot(hull,Shipyard.Mount.SUPPORT,0),prefix+"FTR");
                if(Shipyard.inspect(fighter,catalog).valid())design=fighter;
                battle.spawnWing(team,design,catalog,quantity,x,y);
            }else if(group==2||group==4){
                // Keep the ordinary 32 capital ships ordinary. Each side has
                // exactly one specialist drone tender and one repair/relay ship.
                battle.spawnWing(team,design,catalog,quantity-1,x,y);
                int module=slot(hull,Shipyard.Mount.SUPPORT,0);
                Shipyard.Design specialist=design.install(module,
                        prefix+(group==2?"DRN":"REP"));
                if(group==2)
                    specialist=specialist.stage(module,Shipyard.Track.SPECIALIST,3);
                else specialist=specialist.install(
                        slot(hull,Shipyard.Mount.SUPPORT,1),prefix+"XFR");
                if(!Shipyard.inspect(specialist,catalog).valid()){
                    specialist=specialist
                            .install(slot(hull,Shipyard.Mount.SYSTEM,3),prefix+"REA")
                            .install(slot(hull,Shipyard.Mount.SYSTEM,4),prefix+"RAD");
                }
                if(!Shipyard.inspect(specialist,catalog).valid())
                    throw new IllegalStateException("Dedicated demo support ship invalid: "+
                            specialist.faction+" group="+group+" reason="+
                            Shipyard.inspect(specialist,catalog).explanation());
                battle.spawnWing(team,specialist,catalog,1,x+8,y-8);
            }else battle.spawnWing(team,design,catalog,quantity,x,y);
        }
        if(battle.ships().size()!=400 || battle.alive(0)!=200 || battle.alive(1)!=200)
            throw new IllegalStateException("Demo must deploy exactly 200 ships per fleet");
        // Actual supported hulls close to the two purpose-fitted tenders:
        // service effects require true damage/charge deficits, not fabricated
        // repair telemetry. Indices 68+ and 134+ belong to their wings.
        for(int team=0;team<2;team++){
            int offset=team*200;
            for(int index:new int[]{68,69,70,134,135,136}){
                Battle.Ship damaged=battle.ships().get(offset+index);
                damaged.hull=Math.max(1,damaged.hull-52);
                damaged.defense.impact(1,0,0.7,0,55,Battle.DamageType.LASER);
                damaged.shields=damaged.defense.totalShields();
            }
        }
        long operators=battle.ships().stream().filter(ship->
                ship.support.remoteRepairRate()>0||ship.support.shieldTransferRate()>0
                        ||ship.support.repairDroneCapacity()>0).count();
        if(operators!=4)throw new IllegalStateException(
                "200v200 demo should carry only 2 purpose-built service vessels per side, got "+operators);
        return battle;
    }
}
