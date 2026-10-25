package diplomacy.sim;

import java.util.Arrays;

/**
 * Renderer-independent directional protection model. +X is the ship's bow,
 * +Y is port. All hit coordinates and direction vectors are ship-local.
 * The visual alpha mask may replace the current elliptical occupancy later.
 */
public final class DirectionalDefense {
    public enum Facing { FORWARD, PORT, AFT, STARBOARD }
    public enum Layer { SHIELD, ARMOR, STRUCTURE }
    public static final int GRID = 16;
    private static final double PI = StrictMath.PI;
    private static final int TYPES = Battle.DamageType.values().length;

    public static final class Tile {
        public final Facing facing;
        public final int column, row;
        public final boolean occupied;
        private double armor, maxArmor, structure, maxStructure;
        private Tile(Facing zone, int col, int r, boolean valid) {
            facing=zone; column=col; row=r; occupied=valid;
        }
        public double armor() { return armor; }
        public double maxArmor() { return maxArmor; }
        public double structure() { return structure; }
        public double maxStructure() { return maxStructure; }
        /** Fraction of the exterior armor image that should be hidden. */
        public double exposure() { return !occupied || maxArmor<=0 ? 0 : 1-armor/maxArmor; }
        public double internalDamage() { return !occupied || maxStructure<=0 ? 0 : 1-structure/maxStructure; }
    }
    public record Impact(Facing facing, int column, int row,
                         double shieldConsumed, double armorConsumed,
                         double hullDamage, double structuralTileDamage) { }

    private static final class Zone {
        double shield, maxShield, rechargePerSecond=1.25, rechargeDelay=2.0, delayRemaining;
    }
    private final Zone[] zones = new Zone[4];
    private final Tile[][] tiles = new Tile[GRID][GRID];
    private final double[][][] damageFactor = new double[Layer.values().length][4][TYPES];

    public DirectionalDefense(double shieldCapacity, double armorCapacity, double structuralCapacity) {
        valid(shieldCapacity); valid(armorCapacity); valid(structuralCapacity);
        for (Facing f:Facing.values()) {
            Zone z = zones[f.ordinal()] = new Zone();
            z.shield = z.maxShield = shieldCapacity/4.0;
            for (Layer layer:Layer.values())
                Arrays.fill(damageFactor[layer.ordinal()][f.ordinal()],1.0);
            damageFactor[Layer.SHIELD.ordinal()][f.ordinal()][Battle.DamageType.KINETIC.ordinal()]=1.3;
            damageFactor[Layer.SHIELD.ordinal()][f.ordinal()][Battle.DamageType.PLASMA.ordinal()]=0.65;
            damageFactor[Layer.SHIELD.ordinal()][f.ordinal()][Battle.DamageType.PARTICLE.ordinal()]=1.4;
            damageFactor[Layer.SHIELD.ordinal()][f.ordinal()][Battle.DamageType.TORPEDO.ordinal()]=1.5;
            damageFactor[Layer.ARMOR.ordinal()][f.ordinal()][Battle.DamageType.KINETIC.ordinal()]=1.25;
            damageFactor[Layer.ARMOR.ordinal()][f.ordinal()][Battle.DamageType.TORPEDO.ordinal()]=1.5;
            damageFactor[Layer.ARMOR.ordinal()][f.ordinal()][Battle.DamageType.LASER.ordinal()]=0.8;
            damageFactor[Layer.STRUCTURE.ordinal()][f.ordinal()][Battle.DamageType.PLASMA.ordinal()]=1.15;
            damageFactor[Layer.STRUCTURE.ordinal()][f.ordinal()][Battle.DamageType.PARTICLE.ordinal()]=1.3;
            damageFactor[Layer.STRUCTURE.ordinal()][f.ordinal()][Battle.DamageType.TORPEDO.ordinal()]=1.3;
        }
        int[] counts = new int[4];
        for (int row=0;row<GRID;row++) for(int col=0;col<GRID;col++) {
            // Sprite faces up: nose is row zero; port is left on screen.
            double x=1-(row+0.5)*2/GRID, y=1-(col+0.5)*2/GRID;
            boolean occupied=x*x + (y/0.78)*(y/0.78) <= 1.0;
            Facing facing=facingFor(x,y);
            tiles[row][col]=new Tile(facing,col,row,occupied);
            if(occupied)counts[facing.ordinal()]++;
        }
        for(Tile[] line:tiles)for(Tile tile:line)if(tile.occupied) {
            int count=counts[tile.facing.ordinal()];
            tile.armor=tile.maxArmor=armorCapacity/4.0/count;
            tile.structure=tile.maxStructure=structuralCapacity/count/4.0;
        }
    }
    private static void valid(double d) {
        if(!Double.isFinite(d)||d<0)throw new IllegalArgumentException("Nonfinite or negative protection");
    }
    public static Facing facingFor(double localX,double localY) {
        if(!Double.isFinite(localX)||!Double.isFinite(localY))
            throw new IllegalArgumentException("Nonfinite impact direction");
        double a=StrictMath.atan2(localY,localX);
        if(a>=-PI/4 && a<PI/4)return Facing.FORWARD;
        if(a>=PI/4 && a<3*PI/4)return Facing.PORT;
        if(a<=-PI/4 && a>-3*PI/4)return Facing.STARBOARD;
        return Facing.AFT;
    }
    public Tile tile(int row,int column) {
        if(row<0||row>=GRID||column<0||column>=GRID)throw new IndexOutOfBoundsException();
        return tiles[row][column];
    }
    public Tile tileAt(double localX,double localY) {
        if(!Double.isFinite(localX)||!Double.isFinite(localY))
            throw new IllegalArgumentException("Nonfinite impact point");
        int row=(int)StrictMath.floor((1-localX)*GRID/2);
        int col=(int)StrictMath.floor((1-localY)*GRID/2);
        row=StrictMath.max(0,StrictMath.min(GRID-1,row));
        col=StrictMath.max(0,StrictMath.min(GRID-1,col));
        Tile selected=tiles[row][col];
        if(selected.occupied)return selected;
        double best=Double.POSITIVE_INFINITY;
        for(Tile[] line:tiles)for(Tile candidate:line)if(candidate.occupied) {
            double dx=candidate.row-row,dy=candidate.column-col;
            double distance=dx*dx+dy*dy;
            if(distance<best){best=distance;selected=candidate;}
        }
        return selected;
    }
    public void addArmor(Facing facing,double capacity) {
        valid(capacity);
        int count=0;
        for(Tile[] line:tiles)for(Tile t:line)if(t.occupied&&t.facing==facing)count++;
        if(count==0)return;
        for(Tile[] line:tiles)for(Tile t:line)if(t.occupied&&t.facing==facing) {
            t.maxArmor+=capacity/count; t.armor+=capacity/count;
        }
    }
    public void addShield(Facing facing,double capacity) {
        valid(capacity);
        Zone z=zones[facing.ordinal()]; z.maxShield+=capacity; z.shield+=capacity;
    }
    public void configureRecharge(Facing facing,double perSecond,double delay) {
        valid(perSecond);valid(delay);
        Zone z=zones[facing.ordinal()];z.rechargePerSecond=perSecond;z.rechargeDelay=delay;
    }
    /** 1.0 is neutral; values below 1 are resistance, above 1 vulnerability. */
    public void setDamageFactor(Layer layer,Facing facing,Battle.DamageType type,double factor) {
        if(!Double.isFinite(factor)||factor<0.05||factor>8)
            throw new IllegalArgumentException("Damage multiplier outside supported bounds");
        damageFactor[layer.ordinal()][facing.ordinal()][type.ordinal()]=factor;
    }
    public double damageFactor(Layer layer,Facing facing,Battle.DamageType type) {
        return damageFactor[layer.ordinal()][facing.ordinal()][type.ordinal()];
    }
    public double shields(Facing facing) { return zones[facing.ordinal()].shield; }
    public double maxShields(Facing facing) { return zones[facing.ordinal()].maxShield; }
    public double totalShields() {
        double sum=0;for(Zone z:zones)sum+=z.shield;return sum;
    }
    /**
     * External shield-transfer energy replenishes depleted directional facings.
     * It does not increase maximum capacity, ignore destruction of the ship, or
     * force the ordinary recharge timer to expire. Returns actual shield gained.
     * The caller must charge the originating support ship for that gain.
     */
    public double receiveShieldTransfer(double offered) {
        valid(offered);
        double left=offered;
        for(int iteration=0;iteration<zones.length && left>1e-10;iteration++){
            Zone chosen=null;
            double lowestFraction=Double.POSITIVE_INFINITY;
            for(Zone zone:zones){
                if(zone.maxShield<=0||zone.shield>=zone.maxShield-1e-10)continue;
                double fraction=zone.shield/zone.maxShield;
                if(fraction<lowestFraction){lowestFraction=fraction;chosen=zone;}
            }
            if(chosen==null)break;
            double added=StrictMath.min(left,chosen.maxShield-chosen.shield);
            chosen.shield+=added;left-=added;
        }
        return offered-left;
    }
    public double totalArmor() {
        double sum=0;for(Tile[] line:tiles)for(Tile t:line)sum+=t.armor;return sum;
    }
    public double armor(Facing facing) {
        double sum=0;for(Tile[] line:tiles)for(Tile t:line)
            if(t.occupied&&t.facing==facing)sum+=t.armor;return sum;
    }
    public double maxArmor() {
        double sum=0;for(Tile[] line:tiles)for(Tile t:line)sum+=t.maxArmor;return sum;
    }
    /**
     * Uses available ship power to refresh only non-disabled shield facings.
     * Returns actual energy spent; each regenerated shield point costs 0.6 power.
     */
    public double recharge(double seconds,double availablePower,boolean powered) {
        valid(seconds);valid(availablePower);
        double used=0;
        for(Zone z:zones) {
            z.delayRemaining=StrictMath.max(0,z.delayRemaining-seconds);
            if(!powered || z.delayRemaining>0)continue;
            double desired=StrictMath.min(z.maxShield-z.shield,z.rechargePerSecond*seconds);
            double gained=StrictMath.min(desired,StrictMath.max(0,availablePower-used)/0.6);
            z.shield+=gained;used+=gained*0.6;
        }
        return used;
    }
    /**
     * Hit coordinates identify an armor tile independently of the incoming angle.
     * layer values are actual integrity consumed; hullDamage is passed to the ship.
     */
    public Impact impact(double incomingX,double incomingY,double hitLocalX,double hitLocalY,
                         double raw,Battle.DamageType kind) {
        valid(raw);
        Facing facing=facingFor(incomingX,incomingY);
        Tile t=tileAt(hitLocalX,hitLocalY);
        // The impacted geometric tile, not a whole global armor pool, absorbs damage.
        Zone z=zones[facing.ordinal()];
        double shieldFactor=damageFactor(Layer.SHIELD,facing,kind);
        double shieldUsed=StrictMath.min(z.shield,raw*shieldFactor);
        z.shield-=shieldUsed;
        if(raw>0)z.delayRemaining=z.rechargeDelay;
        double remaining=StrictMath.max(0,raw-shieldUsed/shieldFactor);
        double armorFactor=damageFactor(Layer.ARMOR,t.facing,kind);
        double armorUsed=StrictMath.min(t.armor,remaining*armorFactor);
        t.armor-=armorUsed;
        remaining=StrictMath.max(0,remaining-armorUsed/armorFactor);
        double structural=remaining*damageFactor(Layer.STRUCTURE,t.facing,kind);
        double tileLoss=StrictMath.min(t.structure,structural);
        t.structure-=tileLoss;
        return new Impact(facing,t.column,t.row,shieldUsed,armorUsed,structural,tileLoss);
    }
    /** Include every affected tile and facing in deterministic replay fingerprints. */
    public long fingerprint() {
        long hash=0xcbf29ce484222325L;
        for(Zone z:zones) {
            hash=mix(hash,z.shield); hash=mix(hash,z.maxShield);
            hash=mix(hash,z.delayRemaining);
        }
        for(Tile[] line:tiles)for(Tile t:line)if(t.occupied) {
            hash=mix(hash,t.armor);hash=mix(hash,t.structure);
        }
        return hash;
    }
    private static long mix(long h,double x) {
        return (h^Double.doubleToLongBits(x))*0x100000001b3L;
    }
}
