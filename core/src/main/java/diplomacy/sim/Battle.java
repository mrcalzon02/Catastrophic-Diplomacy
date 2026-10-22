package diplomacy.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Deterministic 30 Hz combat simulation. No graphics, files, time or Android APIs. */
public final class Battle {
    public static final double DT = 1.0 / 30.0;
    public enum Faction {
        TERRAN("Terran Confederacy", 0.30f, 0.70f, 0.95f),
        DRAXIAN("Draxian Empire", 0.50f, 0.36f, 0.65f),
        CARNELIAN("Carnelian Furlongs", 0.85f, 0.22f, 0.48f),
        ZIPPLEDROW("Zippledrow", 0.15f, 0.88f, 0.98f),
        UZIPT("Uzipt", 0.32f, 0.64f, 0.85f),
        MANDRALLY("Mandrally", 0.43f, 0.77f, 0.42f);
        public final String displayName;
        public final float r, g, b;
        Faction(String label, float red, float green, float blue) {
            displayName = label; r = red; g = green; b = blue;
        }
    }

    public enum Subsystem { REACTOR, ENGINES, WEAPONS, POINT_DEFENSE, SENSORS, COOLING, COMMAND }

    public static final class Ship {
        public final int id, team;
        public final Faction faction;
        public final String name;
        public double x, y, vx, vy, facing;
        public double hull, maxHull, shields, maxShields, armor, maxArmor, sif, power, maxPower, heat, maxHeat;
        public double engineCondition = 1, reactorCondition = 1, weaponsCondition = 1, pointDefenseCondition = 1;
        public double sensorsCondition = 1, coolingCondition = 1, commandCondition = 1;
        public double damageControl, speed, accuracy, laserDamage, missileDamage, pdRange;
        public double laserReload, missileReload, pdReload;
        public int launched, intercepted, kills;
        public boolean retired;
        public int commanderSkill;
        Ship(int identifier, int side, Faction group, String callSign) {
            id = identifier; team = side; faction = group; name = callSign;
            maxHull = 195; maxShields = 95; maxArmor = 75; maxPower = 120; maxHeat = 100;
            speed = 62; accuracy = 0.65; laserDamage = 19; missileDamage = 52; sif = 0.20;
            pdRange = 115; damageControl = 0.014; commanderSkill = 2;
            switch (group) {
                case TERRAN: maxPower *= 1.13; pdRange *= 1.13; break;
                case DRAXIAN: maxShields *= 1.45; sif += 0.08; laserDamage *= 1.18; break;
                case CARNELIAN: maxHull *= 1.36; maxArmor *= 1.60; speed *= 0.74; break;
                case ZIPPLEDROW: speed *= 1.65; maxHull *= 0.72; maxShields *= 0.68; break;
                case UZIPT: accuracy += 0.17; pdRange *= 1.34; commanderSkill = 4; break;
                case MANDRALLY: damageControl *= 3.0; maxHull *= 1.11; maxHeat *= 0.85; break;
                default: break;
            }
            hull = maxHull; shields = maxShields; armor = maxArmor; power = maxPower;
        }
        public boolean alive() { return hull > 0 && !retired; }
        public double combatReadiness() {
            return Math.max(0, hull / maxHull) * (0.5 + 0.5 * weaponsCondition);
        }
        public String status() {
            return String.format(Locale.ROOT, "%s hull=%.0f shield=%.0f armor=%.0f heat=%.0f",
                    name, hull, shields, armor, heat);
        }
    }

    public static final class Missile {
        public final int sourceId, team;
        public int targetId;
        public double x, y, vx, vy, fuel, damage, integrity;
        Missile(int source, int owner, int target, double sx, double sy, double dx, double dy, double amount) {
            sourceId = source; team = owner; targetId = target; x = sx; y = sy;
            double magnitude = Math.hypot(dx, dy);
            vx = magnitude == 0 ? 1 : dx / magnitude;
            vy = magnitude == 0 ? 0 : dy / magnitude;
            fuel = 10; damage = amount; integrity = 2.5;
        }
    }

    public static final class Beam {
        public final double x1, y1, x2, y2;
        public final int team;
        public double lifetime = 0.13;
        Beam(double startX, double startY, double endX, double endY, int side) {
            x1 = startX; y1 = startY; x2 = endX; y2 = endY; team = side;
        }
    }

    private final Random random;
    private final List<Ship> ships = new ArrayList<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Beam> beams = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private int nextId = 1;
    private final long seed;
    public long tick;
    public int hitCount, missileIntercepts, destructions;

    public Battle(long initialSeed) { seed = initialSeed; random = new Random(initialSeed); }
    public long seed() { return seed; }
    public List<Ship> ships() { return Collections.unmodifiableList(ships); }
    public List<Missile> missiles() { return Collections.unmodifiableList(missiles); }
    public List<Beam> beams() { return Collections.unmodifiableList(beams); }
    public List<String> events() { return Collections.unmodifiableList(events); }

    public Ship addShip(int team, Faction faction, double x, double y) {
        if (team != 0 && team != 1) throw new IllegalArgumentException("Only two teams in the skirmish mode");
        Ship ship = new Ship(nextId++, team, faction, faction.name().substring(0, 3) + "-" + nextId);
        ship.x = x; ship.y = y; ship.facing = team == 0 ? 0 : Math.PI;
        ships.add(ship); return ship;
    }
    public void spawnWing(int team, Faction faction, int count, double centerX, double centerY) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Invalid fleet size");
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        for (int i = 0; i < count; i++) {
            double x = centerX + ((i % columns) - (columns - 1) / 2.0) * 31 + random.nextDouble() * 3;
            double y = centerY + ((i / columns) - (count / columns) / 2.0) * 41 + random.nextDouble() * 3;
            addShip(team, faction, x, y);
        }
        log(faction.displayName + " deployed " + count + " combatants");
    }
    public static Battle duel(long seed, int shipsPerSide, Faction left, Faction right) {
        Battle battle = new Battle(seed);
        battle.spawnWing(0, left, shipsPerSide, 200, 335);
        battle.spawnWing(1, right, shipsPerSide, 1070, 335);
        return battle;
    }
    public int alive(int team) { int count = 0; for (Ship s : ships) if (s.team == team && s.alive()) count++; return count; }
    public boolean finished() { return alive(0) == 0 || alive(1) == 0; }

    public void step() {
        tick++;
        for (int i = beams.size() - 1; i >= 0; i--) {
            if ((beams.get(i).lifetime -= DT) <= 0) beams.remove(i);
        }
        for (Ship s : ships) if (s.alive()) {
            replenish(s);
            Ship target = chooseTarget(s);
            if (target != null) {
                steer(s, target);
                fire(s, target);
            } else { s.x += s.vx * DT; s.y += s.vy * DT; }
        }
        updateMissiles();
    }

    private void replenish(Ship s) {
        s.power = Math.min(s.maxPower * s.reactorCondition, s.power + 16 * DT * s.reactorCondition);
        s.heat = Math.max(0, s.heat - (9 + 10 * s.coolingCondition) * DT);
        if (s.power > 3 && s.shields < s.maxShields && s.heat < s.maxHeat * 0.9) {
            double gain = Math.min(s.maxShields - s.shields, 5 * DT);
            s.shields += gain; s.power -= gain * 0.6;
        }
        if (s.faction == Faction.MANDRALLY && s.power > 5) {
            s.hull = Math.min(s.maxHull, s.hull + s.damageControl * DT * 30);
        }
        s.laserReload = Math.max(0, s.laserReload - DT);
        s.missileReload = Math.max(0, s.missileReload - DT);
        s.pdReload = Math.max(0, s.pdReload - DT);
    }

    private Ship chooseTarget(Ship actor) {
        Ship choice = null;
        double best = Double.POSITIVE_INFINITY;
        for (Ship s : ships) if (s.team != actor.team && s.alive()) {
            double distance = Math.hypot(s.x - actor.x, s.y - actor.y);
            // Inexperienced commanders misjudge target resilience; tactical aggression
            // sometimes wins over a conservative point-value estimate.
            double estimationError = (random.nextDouble() - 0.5) * 0.52 / (1 + actor.commanderSkill * 0.4);
            double assessed = distance * (1 + estimationError) + s.combatReadiness() * 32;
            if (assessed < best) { best = assessed; choice = s; }
        }
        return choice;
    }

    private static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
    private void steer(Ship s, Ship target) {
        double dx = target.x - s.x, dy = target.y - s.y;
        double range = Math.hypot(dx, dy);
        double heading = Math.atan2(dy, dx);
        if (range < 210) heading += (s.team == 0 ? 1 : -1) * Math.PI * 0.48;
        double change = Math.max(-2.8 * DT, Math.min(2.8 * DT, wrap(heading - s.facing)));
        s.facing = wrap(s.facing + change);
        double thrust = s.speed * s.engineCondition;
        double desiredVx = Math.cos(s.facing) * thrust, desiredVy = Math.sin(s.facing) * thrust;
        double smoothing = Math.min(1, 1.6 * DT);
        s.vx += (desiredVx - s.vx) * smoothing;
        s.vy += (desiredVy - s.vy) * smoothing;
        s.x += s.vx * DT; s.y += s.vy * DT;
    }

    private void fire(Ship s, Ship target) {
        double distance = Math.hypot(target.x - s.x, target.y - s.y);
        double lock = Math.max(0, 1 - distance / 1000) * s.sensorsCondition;
        if (distance < 530 && s.laserReload == 0 && s.power > 9 && s.heat < s.maxHeat * 0.91) {
            s.laserReload = 1.05 / Math.max(0.3, s.weaponsCondition);
            s.power -= 9; s.heat += 7;
            beams.add(new Beam(s.x, s.y, target.x, target.y, s.team));
            double chance = Math.min(0.98, s.accuracy * (0.7 + lock * 0.45));
            if (random.nextDouble() < chance) hit(target, s.laserDamage, s);
        }
        if (distance < 690 && distance > 80 && s.missileReload == 0 && s.power > 13 && s.weaponsCondition > 0.15) {
            s.missileReload = 3.2 / Math.max(0.4, s.weaponsCondition);
            s.power -= 13; s.heat += 5;
            missiles.add(new Missile(s.id, s.team, target.id, s.x, s.y,
                    target.x - s.x, target.y - s.y, s.missileDamage));
            s.launched++;
        }
    }

    private void updateMissiles() {
        // Defensive fire is evaluated before missile movement/impact.
        for (Ship defender : ships) if (defender.alive() && defender.pdReload <= 0 && defender.pointDefenseCondition > 0.20) {
            for (int i = missiles.size() - 1; i >= 0; i--) {
                Missile missile = missiles.get(i);
                if (missile.team == defender.team) continue;
                if (Math.hypot(missile.x - defender.x, missile.y - defender.y) > defender.pdRange) continue;
                defender.pdReload = 0.16 / defender.pointDefenseCondition;
                if (defender.power < 1.5) break;
                defender.power -= 1.5; defender.heat += 0.8;
                if (random.nextDouble() < 0.68 * defender.pointDefenseCondition) {
                    missile.integrity -= 2.7;
                    if (missile.integrity <= 0) {
                        missiles.remove(i); defender.intercepted++; missileIntercepts++;
                    }
                }
                break;
            }
        }
        for (int i = missiles.size() - 1; i >= 0; i--) {
            Missile m = missiles.get(i);
            Ship target = findShip(m.targetId);
            if (target == null || !target.alive() || (m.fuel -= DT) <= 0) { missiles.remove(i); continue; }
            double dx = target.x - m.x, dy = target.y - m.y, range = Math.hypot(dx, dy);
            if (range < 13) { hit(target, m.damage, findShip(m.sourceId)); missiles.remove(i); continue; }
            if (range > 0) {
                m.vx = 0.96 * m.vx + 0.04 * dx / range;
                m.vy = 0.96 * m.vy + 0.04 * dy / range;
                double magnitude = Math.hypot(m.vx, m.vy);
                m.x += (m.vx / magnitude) * 220 * DT;
                m.y += (m.vy / magnitude) * 220 * DT;
            }
        }
    }
    private Ship findShip(int id) { for (Ship ship : ships) if (ship.id == id) return ship; return null; }

    private void hit(Ship defender, double raw, Ship attacker) {
        if (!defender.alive()) return;
        hitCount++;
        double damage = raw;
        double shieldHit = Math.min(defender.shields, damage);
        defender.shields -= shieldHit; damage -= shieldHit;
        if (damage <= 0) return;
        double armorAbsorb = Math.min(defender.armor, damage * 0.35);
        defender.armor -= armorAbsorb; damage -= armorAbsorb;
        if (defender.power > 3 && defender.sif > 0 && defender.reactorCondition > 0.15) {
            double fieldAbsorb = damage * defender.sif;
            defender.power -= Math.min(defender.power, fieldAbsorb * 0.6);
            damage -= fieldAbsorb;
        }
        defender.hull -= damage;
        if (damage > 5 && random.nextDouble() < 0.24) {
            Subsystem part = Subsystem.values()[random.nextInt(Subsystem.values().length)];
            double loss = Math.min(0.36, damage / defender.maxHull * 0.7);
            switch (part) {
                case REACTOR: defender.reactorCondition = Math.max(0, defender.reactorCondition - loss); break;
                case ENGINES: defender.engineCondition = Math.max(0, defender.engineCondition - loss); break;
                case WEAPONS: defender.weaponsCondition = Math.max(0, defender.weaponsCondition - loss); break;
                case POINT_DEFENSE: defender.pointDefenseCondition = Math.max(0, defender.pointDefenseCondition - loss); break;
                case SENSORS: defender.sensorsCondition = Math.max(0, defender.sensorsCondition - loss); break;
                case COOLING: defender.coolingCondition = Math.max(0, defender.coolingCondition - loss); break;
                case COMMAND: defender.commandCondition = Math.max(0, defender.commandCondition - loss); break;
                default: break;
            }
        }
        if (defender.hull <= 0) {
            defender.hull = 0; defender.retired = true; destructions++;
            if (attacker != null) attacker.kills++;
            log(defender.name + " shattered at T+" + tick / 30 + "s");
        }
    }
    private void log(String message) { events.add(message); if (events.size() > 90) events.remove(0); }

    /** Stable across machines using Java's specified Double.doubleToLongBits. */
    public long fingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, tick); hash = mix(hash, hitCount);
        for (Ship s : ships) {
            hash = mix(hash, s.id); hash = mix(hash, Double.doubleToLongBits(s.x));
            hash = mix(hash, Double.doubleToLongBits(s.y));
            hash = mix(hash, Double.doubleToLongBits(s.hull));
            hash = mix(hash, Double.doubleToLongBits(s.shields));
            hash = mix(hash, Double.doubleToLongBits(s.armor));
            hash = mix(hash, Double.doubleToLongBits(s.power));
        }
        for (Missile m : missiles) { hash = mix(hash, m.sourceId); hash = mix(hash, Double.doubleToLongBits(m.x)); }
        return hash;
    }
    private static long mix(long hash, long n) { return (hash ^ n) * 0x100000001b3L; }
}
