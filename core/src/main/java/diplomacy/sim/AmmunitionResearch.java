package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.EnumSet;
import java.util.Set;

/**
 * Per-installed-weapon ammunition research law. Stock ammunition is free;
 * purpose-built rounds require this SAME socket's one committed 6-rank path.
 * All 11 civilizations share this unlock law and use their own research IDs.
 * Projectile effects/physics remain defined by AmmunitionTable.
 */
public final class AmmunitionResearch {
    private AmmunitionResearch(){}
    public record Access(Set<AmmunitionTable.Kinetic> kinetics,
                         Set<AmmunitionTable.Warhead> missiles,
                         Set<AmmunitionTable.Warhead> torpedoes) {
        public Access {
            if(kinetics==null||missiles==null||torpedoes==null
                    ||!kinetics.contains(AmmunitionTable.Kinetic.STANDARD)
                    ||!missiles.contains(AmmunitionTable.Warhead.STANDARD)
                    ||!torpedoes.contains(AmmunitionTable.Warhead.STANDARD))
                throw new IllegalArgumentException("Every ammo family needs its standard round");
            kinetics=Set.copyOf(kinetics);
            missiles=Set.copyOf(missiles);
            torpedoes=Set.copyOf(torpedoes);
        }
        public boolean allows(AmmunitionTable.Kinetic type){return kinetics.contains(type);}
        public boolean allows(Battle.DamageType family,AmmunitionTable.Warhead type){
            return switch(family){
                case MISSILE -> missiles.contains(type);
                case TORPEDO -> torpedoes.contains(type);
                default -> throw new IllegalArgumentException("No guided warheads for "+family);
            };
        }
    }
    public static final Access STOCK=new Access(
            Set.of(AmmunitionTable.Kinetic.STANDARD),
            Set.of(AmmunitionTable.Warhead.STANDARD),
            Set.of(AmmunitionTable.Warhead.STANDARD));

    private static void valid(Shipyard.Track path,int rank){
        if(rank<0||rank>6||(rank>0&&path==null))
            throw new IllegalArgumentException("Ammunition research must use one branch at rank 0-6");
    }
    /**
     * Rank breakpoints are deliberate: no instant full arsenal at rank 1.
     * The two specialized rounds are unlocked incrementally at the listed
     * ranks in their own mutually exclusive technology branch.
     */
    public static Set<AmmunitionTable.Kinetic> kinetic(Shipyard.Track path,int rank){
        valid(path,rank);
        EnumSet<AmmunitionTable.Kinetic> rounds=EnumSet.of(AmmunitionTable.Kinetic.STANDARD);
        if(rank==0)return Set.copyOf(rounds);
        switch(path){
            case OUTPUT -> {
                if(rank>=2)rounds.add(AmmunitionTable.Kinetic.HIGH_EXPLOSIVE);
                if(rank>=5)rounds.add(AmmunitionTable.Kinetic.RELATIVISTIC);
            }
            case EFFICIENCY -> {
                if(rank>=2)rounds.add(AmmunitionTable.Kinetic.FRAGMENTATION);
                if(rank>=5)rounds.add(AmmunitionTable.Kinetic.ARMOR_PIERCING);
            }
            case RESILIENCE -> {
                if(rank>=2)rounds.add(AmmunitionTable.Kinetic.ARMOR_PIERCING);
                if(rank>=5)rounds.add(AmmunitionTable.Kinetic.HIGH_EXPLOSIVE);
            }
            case SPECIALIST -> {
                if(rank>=2)rounds.add(AmmunitionTable.Kinetic.ARMOR_PIERCING);
                if(rank>=6)rounds.add(AmmunitionTable.Kinetic.RELATIVISTIC);
            }
        }
        return Set.copyOf(rounds);
    }
    public static Set<AmmunitionTable.Warhead> warheads(
            Battle.DamageType family,Shipyard.Track path,int rank){
        valid(path,rank);
        if(family!=Battle.DamageType.MISSILE&&family!=Battle.DamageType.TORPEDO)
            throw new IllegalArgumentException("Guided payloads require MSL or TOR module");
        EnumSet<AmmunitionTable.Warhead> rounds=EnumSet.of(AmmunitionTable.Warhead.STANDARD);
        if(rank==0)return Set.copyOf(rounds);
        switch(path){
            case OUTPUT -> {
                if(rank>=2)rounds.add(family==Battle.DamageType.MISSILE
                        ?AmmunitionTable.Warhead.HIGH_EXPLOSIVE
                        :AmmunitionTable.Warhead.SHIELD_BREAKER);
                if(rank>=5)rounds.add(family==Battle.DamageType.MISSILE
                        ?AmmunitionTable.Warhead.SHIELD_BREAKER
                        :AmmunitionTable.Warhead.HIGH_EXPLOSIVE);
            }
            case EFFICIENCY -> {
                if(rank>=2)rounds.add(AmmunitionTable.Warhead.FRAGMENTATION);
                if(rank>=5)rounds.add(AmmunitionTable.Warhead.EMP);
            }
            case RESILIENCE -> {
                if(rank>=2)rounds.add(AmmunitionTable.Warhead.ARMOR_PIERCING);
                if(rank>=5)rounds.add(family==Battle.DamageType.MISSILE
                        ?AmmunitionTable.Warhead.HIGH_EXPLOSIVE
                        :AmmunitionTable.Warhead.SHIELD_BREAKER);
            }
            case SPECIALIST -> {
                if(rank>=2)rounds.add(AmmunitionTable.Warhead.EMP);
                if(rank>=6)rounds.add(family==Battle.DamageType.MISSILE
                        ?AmmunitionTable.Warhead.SHIELD_BREAKER
                        :AmmunitionTable.Warhead.ARMOR_PIERCING);
            }
        }
        return Set.copyOf(rounds);
    }
}
