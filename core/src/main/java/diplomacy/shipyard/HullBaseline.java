package diplomacy.shipyard;

import diplomacy.sim.Battle;

/**
 * Immutable physical starting characteristics of an EMPTY ship hull.
 * No weapon, crew accommodation, repair equipment, PD battery or ammunition
 * is bundled here; those are entirely installed-module capabilities.
 *
 * All values are world/simulation values. The catalog is deterministic,
 * derived from hull class and civilization, and shared by both player and AI.
 */
public final class HullBaseline {
    private HullBaseline(){}
    public record Stats(double structure,double armor,double shields,
                        double shieldRecharge,double speed,double power,
                        double powerRegen,double sensorRange,double radarCrossSection,
                        int fighterBay,int slots) {
        public Stats {
            if(!Double.isFinite(structure)||structure<=0
                    ||!Double.isFinite(armor)||armor<0
                    ||!Double.isFinite(shields)||shields<0
                    ||!Double.isFinite(shieldRecharge)||shieldRecharge<0
                    ||!Double.isFinite(speed)||speed<=0
                    ||!Double.isFinite(power)||power<=0
                    ||!Double.isFinite(powerRegen)||powerRegen<0
                    ||!Double.isFinite(sensorRange)||sensorRange<=0
                    ||!Double.isFinite(radarCrossSection)||radarCrossSection<=0
                    ||fighterBay<0||fighterBay>32||slots<1)
                throw new IllegalArgumentException("Invalid physical hull baseline");
        }
    }
    /**
     * Inherent flight decks are physical hull structures, not installed modules.
     * Ordinary combatants can gain aviation with SUPPORT-slot FTR modules, but
     * a purpose-built carrier keeps a much larger organic complement.
     * No new enum ordinals or blueprint records are introduced.
     */
    public static int builtInFighterCapacity(Shipyard.Hull hull){
        return switch(hull){
            case CARRIER -> 12;
            case CARRIER_COMMAND -> 14;
            case CARRIER_LOGISTICS -> 16;
            case DREADNOUGHT, DREADNOUGHT_SIEGE -> 2;
            case BATTLECRUISER_TENDER -> 2;
            case HEAVY_CRUISER_COMMAND, CRUISER_CONTROLLER -> 1;
            default -> 0;
        };
    }
    /** Independent launch catapults per synchronized sortie cycle. */
    public static int builtInLaunchBays(Shipyard.Hull hull){
        return switch(hull){
            case CARRIER, CARRIER_COMMAND -> 3;
            case CARRIER_LOGISTICS -> 4;
            case DREADNOUGHT, DREADNOUGHT_SIEGE, BATTLECRUISER_TENDER,
                 HEAVY_CRUISER_COMMAND, CRUISER_CONTROLLER -> 1;
            default -> 0;
        };
    }
    /** Class core, before civilization-specific structural engineering. */
    public static Stats forHull(Battle.Faction race,Shipyard.Hull hull){
        if(race==null||hull==null)throw new IllegalArgumentException("Hull identity required");
        double armor=hull.baseHull*0.075;
        double shields=0;
        double recharge=0;
        double speed=(26+14*hull.mobility);
        double power=18+hull.massCapacity*0.10;
        double regeneration=1.5+hull.massCapacity*0.002;
        double sensors=520+90*hull.mobility;
        double signature=0.2+StrictMath.sqrt(hull.massCapacity/120.0);
        int builtInFighters=builtInFighterCapacity(hull);
        switch(hull.spriteHull()){
            case LIGHT_CRUISER,CRUISER,HEAVY_CRUISER -> {shields=8;recharge=0.22;}
            case BATTLECRUISER,DREADNOUGHT -> {shields=15;recharge=0.35;}
            case CARRIER -> {shields=12;recharge=0.30;}
            default -> { }
        }
        // Dedicated torpedo raiders sacrifice even inherited capital-grade shields.
        if(ShipLineages.of(hull).role()==ShipLineages.Role.TORPEDO_STRIKER){
            shields=0;recharge=0;
        }
        double hullFactor=1,armorFactor=1,shieldFactor=1,speedFactor=1;
        double sensorFactor=1,signatureFactor=1;
        switch(race){
            case TERRAN -> {sensorFactor=1.03;}
            case DRAXIAN,VAERITH_PRISM -> {shieldFactor=1.22;sensorFactor=1.07;}
            case CARNELIAN,NYX_BIOLITH -> {hullFactor=1.13;armorFactor=1.22;speedFactor=0.94;}
            case ZIPPLEDROW,UMBRA_FOUNDRY -> {speedFactor=1.14;hullFactor=0.94;signatureFactor=0.91;}
            case UZIPT,ZELTHAR_LUMINARI -> {sensorFactor=1.24;signatureFactor=0.92;}
            case MANDRALLY,AUREATE_SYNOD -> {hullFactor=1.07;armorFactor=1.06;}
        }
        // Naturally shielded hulls remain uncommon, even for energy-based
        // civilizations; shields on unshielded classes still require a module.
        return new Stats(hull.baseHull*hullFactor,armor*armorFactor,
                shields*shieldFactor,recharge*shieldFactor,
                speed*speedFactor,power,regeneration,
                sensors*sensorFactor,signature*signatureFactor,
                builtInFighters,hull.slots.size());
    }
}
