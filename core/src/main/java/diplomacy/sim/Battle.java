package diplomacy.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

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

    public enum Subsystem { REACTOR, ENGINES, WEAPONS, POINT_DEFENSE, SENSORS, COOLING, COMMAND, HANGAR, MAGAZINE, ELECTRONIC_WARFARE }
    public enum DamageType { LASER, KINETIC, PLASMA, PARTICLE, MISSILE, TORPEDO, FIGHTER }

    public static final class Ship implements SpaceWeather.Target {
        public final int id, team;
        public final Faction faction;
        public final String name;
        public Shipyard.Hull hullClass;
        /** Directional powered shields, localized armor plates and exposed structure. */
        public DirectionalDefense defense;
        public double x, y, vx, vy, facing;
        public double hull, maxHull, shields, maxShields, armor, maxArmor, sif, power, maxPower, heat, maxHeat;
        public double engineCondition = 1, reactorCondition = 1, weaponsCondition = 1, pointDefenseCondition = 1;
        public double sensorsCondition = 1, coolingCondition = 1, commandCondition = 1;
        public double damageControl, speed, accuracy, laserDamage, missileDamage, pdRange;
        /** Installed machinery determines operating headroom and thermal rejection. */
        public double powerRegen = 16, coolingRate = 19, passiveHeat;
        public double laserReload, missileReload, pdReload;
        public double kineticDamage, plasmaDamage, particleDamage, torpedoDamage, ecmStrength;
        public double kineticReload, plasmaReload, particleReload, torpedoReload, fighterReload, droneReload;
        public double hangarCondition=1, magazineCondition=1, ecmCondition=1;
        public int missileAmmo=60, torpedoAmmo, fighterReserve, droneReserve;
        public int fightersLaunched, dronesLaunched, launched, intercepted, kills;
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
            defense=new DirectionalDefense(maxShields,maxArmor,maxHull);
        }
        public boolean alive() { return hull > 0 && !retired; }
        public int weatherId() { return id; }
        public double weatherX() { return x; }
        public double weatherY() { return y; }
        /** Visible top-down hull scale; larger fitted capital hulls read as larger ships. */
        public double visualScale() { return Math.max(0.78, Math.min(1.90, StrictMath.sqrt(maxHull / 200.0))); }
        public double combatReadiness() {
            return Math.max(0, hull / maxHull) * (0.5 + 0.5 * weaponsCondition);
        }
        public String status() {
            return String.format(Locale.ROOT, "%s hull=%.0f shield=%.0f armor=%.0f heat=%.0f",
                    name, hull, shields, armor, heat);
        }
    }

    public static final class Missile {
        public final long visualId, spawnTick;
        public final int sourceId, team;
        public int targetId;
        public double x, y, vx, vy, fuel, damage, integrity;
        public final boolean torpedo;
        Missile(long visualId,long spawnTick, int source, int owner, int target, double sx, double sy, double dx, double dy, double amount,
                boolean heavy) {
            this.visualId=visualId;this.spawnTick=spawnTick;sourceId = source; team = owner; targetId = target; x = sx; y = sy;torpedo=heavy;
            double magnitude = StrictMath.hypot(dx, dy);
            vx = magnitude == 0 ? 1 : dx / magnitude;
            vy = magnitude == 0 ? 0 : dy / magnitude;
            fuel = heavy?14:10; damage = amount; integrity = heavy?5.1:2.5;
        }
    }

    /** Fighters attack hostile hulls. Defensive drones deliberately hunt hostile ordnance. */
    public static final class Craft {
        public final int id, sourceId, team;
        public final boolean fighter;
        public double x, y, integrity, fuel, reload;
        public int targetId;
        Craft(int identifier, Ship source, boolean offensive, int target) {
            id=identifier;sourceId=source.id;team=source.team;fighter=offensive;
            targetId=target;x=source.x;y=source.y;
            integrity=offensive?24:15; fuel=offensive?28:22;
        }
    }

    public static final class Beam {
        public final long visualId, spawnTick;
        public final double x1, y1, x2, y2;
        public final int team;
        public final DamageType type;
        public double lifetime = 0.13;
        Beam(long visualId,long spawnTick, double startX, double startY, double endX, double endY, int side, DamageType weapon) {
            this.visualId=visualId;this.spawnTick=spawnTick;x1 = startX; y1 = startY; x2 = endX; y2 = endY; team = side; type = weapon;
        }
    }

    /** Read-only transient visual notifications; never change damage or RNG. */
    public enum VisualPhase { FIRE, IMPACT, INTERCEPT, TIMEOUT }
    public record VisualEvent(long sequence, long shotId, long tick, VisualPhase phase,
                              String weapon, int team, int sourceId, int targetId,
                              double x, double y, double targetX, double targetY) { }
    private static final int VISUAL_HISTORY_MAX=8192;

    private final Random random;
    private final List<Ship> ships = new ArrayList<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final List<Craft> crafts = new ArrayList<>();
    private int nextCraftId=1;
    private final List<Beam> beams = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<VisualEvent> visualEvents=new ArrayList<>();
    private long nextVisualShotId=1, nextVisualSequence=1;
    private int nextId = 1;
    private final long seed;
    public long tick;
    public int hitCount, missileIntercepts, destructions, environmentalHits;
    private SpaceWeather weather = new SpaceWeather(SpaceWeather.clear());

    public Battle(long initialSeed) { seed = initialSeed; random = new Random(initialSeed); }
    public long seed() { return seed; }
    public SpaceWeather weather() { return weather; }
    /** Select weather before step 1; default is CLEAR for backward compatibility. */
    public void configureWeather(SpaceWeather.Preset preset) {
        if (tick != 0) throw new IllegalStateException("Weather must be configured before battle starts");
        weather = new SpaceWeather(preset);
    }
    public List<Ship> ships() { return Collections.unmodifiableList(ships); }
    public List<Missile> missiles() { return Collections.unmodifiableList(missiles); }
    public List<Craft> crafts() { return Collections.unmodifiableList(crafts); }
    public List<Beam> beams() { return Collections.unmodifiableList(beams); }
    public List<String> events() { return Collections.unmodifiableList(events); }
    /** History allows renderers to catch up when more than one tick elapses per frame. */
    public List<VisualEvent> visualEvents() { return Collections.unmodifiableList(visualEvents); }
    private void visual(long shotId, VisualPhase phase, String weapon, int team,
                        int sourceId,int targetId,double x,double y,double tx,double ty) {
        visualEvents.add(new VisualEvent(nextVisualSequence++,shotId,tick,phase,weapon,
                team,sourceId,targetId,x,y,tx,ty));
        if(visualEvents.size()>VISUAL_HISTORY_MAX)
            visualEvents.subList(0,visualEvents.size()-VISUAL_HISTORY_MAX).clear();
    }

    public Ship addShip(int team, Faction faction, double x, double y) {
        if (team != 0 && team != 1) throw new IllegalArgumentException("Only two teams in the skirmish mode");
        Ship ship = new Ship(nextId++, team, faction, faction.name().substring(0, 3) + "-" + nextId);
        ship.x = x; ship.y = y; ship.facing = team == 0 ? 0 : Math.PI;
        ships.add(ship); return ship;
    }
    /** Custom hull installations alter real combat stats; invalid fittings never enter battle. */
    private Ship addShip(int team, Shipyard.Design design, Shipyard.Fitted machinery, double x, double y) {
        if (machinery == null || design == null) throw new IllegalArgumentException("A fitted design is required");
        Ship s = addShip(team, design.faction, x, y);
        s.hullClass = design.hull;
        s.maxHull = machinery.hull(); s.hull = s.maxHull;
        s.maxShields = machinery.shields(); s.shields = s.maxShields;
        s.maxArmor = machinery.armor(); s.armor = s.maxArmor;
        s.sif = machinery.sif();
        s.defense = new DirectionalDefense(s.maxShields,s.maxArmor,s.maxHull);
        s.maxPower = machinery.maxPower(); s.power = s.maxPower;
        s.maxHeat = machinery.maxHeat();
        s.powerRegen = machinery.powerRegen();
        s.coolingRate = machinery.coolingRate(); s.passiveHeat = machinery.passiveHeat();
        s.speed = machinery.speed(); s.accuracy = machinery.accuracy();
        s.laserDamage = machinery.laserDamage(); s.missileDamage = machinery.missileDamage();
        s.pdRange = machinery.pdRange(); s.damageControl = machinery.damageControl();
        s.kineticDamage=machinery.kineticDamage();s.plasmaDamage=machinery.plasmaDamage();
        s.particleDamage=machinery.particleDamage();s.torpedoDamage=machinery.torpedoDamage();
        s.ecmStrength=machinery.ecmStrength();
        s.fighterReserve=machinery.fighterCapacity();s.droneReserve=machinery.droneCapacity();
        s.missileAmmo=s.missileDamage>0?8+machinery.magazineCapacity():0;
        s.torpedoAmmo=s.torpedoDamage>0?3+machinery.magazineCapacity()/3:0;
        return s;
    }
    public void spawnWing(int team, Shipyard.Design design, TechnologyCatalog catalog,
                          int count, double centerX, double centerY) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Invalid fleet size");
        Shipyard.Fitted machinery = Shipyard.fit(design, catalog);
        for (int i=0;i<count;i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team,design,machinery,point[0],point[1]);
        }
        log(design.faction.displayName+" deployed "+count+" fitted "+design.hull+"s");
    }
    public static Battle duel(long seed, int shipsPerSide, Shipyard.Design left,
                              Shipyard.Design right, TechnologyCatalog catalog) {
        Battle battle = new Battle(seed);
        battle.spawnWing(0,left,catalog,shipsPerSide,310,335);
        battle.spawnWing(1,right,catalog,shipsPerSide,970,335);
        return battle;
    }
    public void spawnWing(int team, Faction faction, int count, double centerX, double centerY) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Invalid fleet size");
        for (int i = 0; i < count; i++) {
            double[] point = formationPosition(i,count,centerX,centerY,random);
            addShip(team, faction, point[0], point[1]);
        }
        log(faction.displayName + " deployed " + count + " combatants");
    }
    public static Battle duel(long seed, int shipsPerSide, Faction left, Faction right) {
        Battle battle = new Battle(seed);
        battle.spawnWing(0, left, shipsPerSide, 310, 335);
        battle.spawnWing(1, right, shipsPerSide, 970, 335);
        return battle;
    }
    /** Formation coordinates are bounded for 1–200 ships per side within a 1280×720 view.
     * Wider fleets compress their spacing instead of losing entire flanks off-screen.
     * The injected Random keeps old deterministic seed behavior. */
    private static double[] formationPosition(int index, int count, double cx, double cy, Random rng) {
        int columns = Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(count)));
        int rows = (count + columns - 1) / columns;
        double spacingX = Math.min(31, 440.0 / Math.max(1, columns-1));
        double spacingY = Math.min(41, 495.0 / Math.max(1, rows-1));
        double jitter = Math.min(2.0, Math.min(spacingX, spacingY) * 0.07);
        double x = cx + ((index % columns) - (columns - 1) / 2.0) * spacingX
                + (rng.nextDouble() - 0.5) * jitter;
        double y = cy + ((index / columns) - (rows - 1) / 2.0) * spacingY
                + (rng.nextDouble() - 0.5) * jitter;
        return new double[]{x,y};
    }
    public int alive(int team) { int count = 0; for (Ship s : ships) if (s.team == team && s.alive()) count++; return count; }
    public boolean finished() { return alive(0) == 0 || alive(1) == 0; }

    public void step() {
        tick++;
        for (int i = beams.size() - 1; i >= 0; i--) {
            if ((beams.get(i).lifetime -= DT) <= 0) beams.remove(i);
        }
        // Environment is deterministic, separately seeded, and uses the same damage rules.
        for (SpaceWeather.Impact effect : weather.advance(tick, ships)) {
            Ship victim = findShip(effect.targetId());
            if (victim == null || !victim.alive()) continue;
            DamageType kind = switch (effect.type()) {
                case KINETIC -> DamageType.KINETIC;
                case PARTICLE -> DamageType.PARTICLE;
                case PLASMA -> DamageType.PLASMA;
            };
            hitAt(victim, effect.damage(), null, kind, effect.fromX(), effect.fromY());
            environmentalHits++;
        }
        for (Ship s : ships) if (s.alive()) {
            replenish(s);
            Ship target = chooseTarget(s);
            if (target != null) {
                steer(s, target);
                fire(s, target);
                launchAuxiliary(s, target);
            } else {
                if (weather.preset().enabled()) {
                    // Low sensor visibility must not cause a permanent standstill.
                    double heading = s.team == 0 ? 1 : -1;
                    s.vx += (heading * s.speed * s.engineCondition * 0.38 - s.vx) * 0.055;
                }
                s.x += s.vx * DT; s.y += s.vy * DT;
            }
        }
        updateMissiles();
        updateCraft();
    }

    private void replenish(Ship s) {
        s.power = Math.max(0, Math.min(s.maxPower * s.reactorCondition,
                s.power + s.powerRegen * DT * s.reactorCondition - weather.powerDrainPerSecond() * DT));
        s.heat = Math.max(0, s.heat + (s.passiveHeat + weather.heatPerSecond()
                - s.coolingRate * s.coolingCondition * weather.coolingFactor()) * DT);
        // Regenerate individual shield facings, subject to cooldown, reactor and power headroom.
        double rechargeCost=s.defense.recharge(DT,StrictMath.max(0,s.power-3),
                s.reactorCondition>0.15 && s.heat<s.maxHeat*0.9);
        s.power-=rechargeCost;
        s.shields=s.defense.totalShields();
        if (s.power > 5 && s.damageControl > 0) {
            // All repair-equipped fleets can restore hull slowly. Mandrally regenerative
            // ecology receives the established fivefold field-repair advantage.
            double restoration = s.damageControl * DT * 30
                    * (s.faction == Faction.MANDRALLY ? 1.0 : 0.20);
            s.hull = Math.min(s.maxHull, s.hull + restoration);
        }
        s.laserReload = Math.max(0, s.laserReload - DT);
        s.missileReload = Math.max(0, s.missileReload - DT);
        s.pdReload = Math.max(0, s.pdReload - DT);
        s.kineticReload=Math.max(0,s.kineticReload-DT);
        s.plasmaReload=Math.max(0,s.plasmaReload-DT);
        s.particleReload=Math.max(0,s.particleReload-DT);
        s.torpedoReload=Math.max(0,s.torpedoReload-DT);
        s.fighterReload=Math.max(0,s.fighterReload-DT);
        s.droneReload=Math.max(0,s.droneReload-DT);
    }

    private Ship chooseTarget(Ship actor) {
        Ship choice = null;
        double best = Double.POSITIVE_INFINITY;
        for (Ship s : ships) if (s.team != actor.team && s.alive()) {
            double distance = StrictMath.hypot(s.x - actor.x, s.y - actor.y);
            if (weather.preset().enabled() && distance > 1100.0 * actor.sensorsCondition
                    * weather.sensorFactor()) continue;
            // Inexperienced commanders misjudge target resilience; tactical aggression
            // sometimes wins over a conservative point-value estimate.
            double estimationError = (random.nextDouble() - 0.5) * 0.52 / (1 + actor.commanderSkill * 0.4);
            double assessed = distance * (1 + estimationError) + s.combatReadiness() * 32;
            if (assessed < best) { best = assessed; choice = s; }
        }
        return choice;
    }

    private static double wrap(double angle) { return StrictMath.atan2(StrictMath.sin(angle), StrictMath.cos(angle)); }
    private void steer(Ship s, Ship target) {
        double dx = target.x - s.x, dy = target.y - s.y;
        double range = StrictMath.hypot(dx, dy);
        double heading = StrictMath.atan2(dy, dx);
        if (range < 210) heading += (s.team == 0 ? 1 : -1) * Math.PI * 0.48;
        double change = Math.max(-2.8 * DT, Math.min(2.8 * DT, wrap(heading - s.facing)));
        s.facing = wrap(s.facing + change);
        double thrust = s.speed * s.engineCondition;
        double desiredVx = StrictMath.cos(s.facing) * thrust, desiredVy = StrictMath.sin(s.facing) * thrust;
        double smoothing = Math.min(1, 1.6 * DT);
        s.vx += (desiredVx - s.vx) * smoothing;
        s.vy += (desiredVy - s.vy) * smoothing;
        s.x += s.vx * DT; s.y += s.vy * DT;
    }

    private void fire(Ship s, Ship target) {
        double distance = StrictMath.hypot(target.x - s.x, target.y - s.y);
        double lock = Math.max(0, 1 - distance / 1000) * s.sensorsCondition
                * weather.sensorFactor() * (1 - target.ecmStrength*target.ecmCondition);
        double weatherAim = weather.accuracyFactor();
        if (s.laserDamage > 0 && distance < 530 && s.laserReload == 0 && s.power > 9 && s.heat < s.maxHeat * 0.91) {
            s.laserReload = 1.05 / Math.max(0.3, s.weaponsCondition);
            s.power -= 9; s.heat += 7;
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x, s.y, target.x, target.y, s.team,DamageType.LASER));
            visual(shotId,VisualPhase.FIRE,"LASER",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            double chance = Math.min(0.98, s.accuracy * weatherAim * (0.7 + lock * 0.45));
            if (random.nextDouble() < chance) hit(target,s.laserDamage,s,DamageType.LASER,shotId);
        }
        if (s.missileDamage > 0 && s.missileAmmo > 0 && distance < 690 && distance > 80 && s.missileReload == 0 && s.power > 13 && s.weaponsCondition > 0.15 && s.magazineCondition > 0.15) {
            s.missileReload = 3.2 / Math.max(0.4, s.weaponsCondition);
            s.power -= 13; s.heat += 5;
            long shotId=nextVisualShotId++;
            missiles.add(new Missile(shotId,tick,s.id, s.team, target.id, s.x, s.y,
                    target.x - s.x, target.y - s.y, s.missileDamage,false));
            visual(shotId,VisualPhase.FIRE,"MISSILE",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            s.launched++;s.missileAmmo--;
        }
        if (s.kineticDamage > 0 && distance < 445 && s.kineticReload <= 0 && s.power>5 && s.heat<s.maxHeat*0.95) {
            s.kineticReload=0.75/Math.max(0.25,s.weaponsCondition);
            s.power-=5;s.heat+=4;
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.KINETIC));
            visual(shotId,VisualPhase.FIRE,"KINETIC",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<Math.min(0.97,s.accuracy*weatherAim*(0.72+lock*0.4)))
                hit(target,s.kineticDamage,s,DamageType.KINETIC,shotId);
        }
        if (s.plasmaDamage > 0 && distance < 245 && s.plasmaReload <= 0 && s.power>14 && s.heat<s.maxHeat*0.86) {
            s.plasmaReload=1.8/Math.max(0.25,s.weaponsCondition);
            s.power-=14;s.heat+=17;
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.PLASMA));
            visual(shotId,VisualPhase.FIRE,"PLASMA",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<Math.min(0.94,s.accuracy*weatherAim*(0.80+lock*0.4)))
                hit(target,s.plasmaDamage,s,DamageType.PLASMA,shotId);
        }
        if (s.particleDamage > 0 && distance < 565 && s.particleReload <= 0 && s.power>17 && s.heat<s.maxHeat*0.9) {
            s.particleReload=2.1/Math.max(0.25,s.weaponsCondition);
            s.power-=17;s.heat+=10;
            long shotId=nextVisualShotId++;
            beams.add(new Beam(shotId,tick,s.x,s.y,target.x,target.y,s.team,DamageType.PARTICLE));
            visual(shotId,VisualPhase.FIRE,"PARTICLE",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
            if(random.nextDouble()<Math.min(0.98,s.accuracy*weatherAim*(0.76+lock*0.45)))
                hit(target,s.particleDamage,s,DamageType.PARTICLE,shotId);
        }
        if (s.torpedoDamage > 0 && s.torpedoAmmo>0 && distance<840 && distance>110
                && s.torpedoReload<=0 && s.power>18 && s.magazineCondition>0.15) {
            s.torpedoReload=6.4/Math.max(0.25,s.weaponsCondition);
            s.power-=18;s.heat+=9;s.torpedoAmmo--;s.launched++;
            long shotId=nextVisualShotId++;
            missiles.add(new Missile(shotId,tick,s.id,s.team,target.id,s.x,s.y,
                    target.x-s.x,target.y-s.y,s.torpedoDamage,true));
            visual(shotId,VisualPhase.FIRE,"TORPEDO",s.team,s.id,target.id,s.x,s.y,target.x,target.y);
        }
    }

    private void launchAuxiliary(Ship ship, Ship enemy) {
        if(ship.hangarCondition<0.20 || ship.power<7 || ship.heat>ship.maxHeat*0.9)return;
        if(ship.fighterReserve>0 && ship.fighterReload<=0 && crafts.size()<2000) {
            crafts.add(new Craft(nextCraftId++,ship,true,enemy.id));
            visual(nextVisualShotId++,VisualPhase.FIRE,"FIGHTER",ship.team,ship.id,enemy.id,
                    ship.x,ship.y,enemy.x,enemy.y);
            ship.fighterReserve--;ship.fightersLaunched++;ship.fighterReload=4.5/ship.hangarCondition;
            ship.power-=7;ship.heat+=3;
        }
        if(ship.droneReserve>0 && ship.droneReload<=0 && crafts.size()<2000) {
            crafts.add(new Craft(nextCraftId++,ship,false,enemy.id));
            visual(nextVisualShotId++,VisualPhase.FIRE,"DRONE",ship.team,ship.id,enemy.id,
                    ship.x,ship.y,enemy.x,enemy.y);
            ship.droneReserve--;ship.dronesLaunched++;ship.droneReload=5.5/ship.hangarCondition;
            ship.power-=5;ship.heat+=2;
        }
    }

    private void updateCraft() {
        for(int i=crafts.size()-1;i>=0;i--) {
            Craft craft=crafts.get(i);
            if((craft.fuel-=DT)<=0 || craft.integrity<=0){crafts.remove(i);continue;}
            if(craft.fighter) {
                Ship target=findShip(craft.targetId);
                if(target==null || !target.alive()) {
                    Ship source=findShip(craft.sourceId);
                    target=source==null?null:chooseTarget(source);
                    if(target!=null)craft.targetId=target.id;
                }
                if(target==null)continue;
                double dx=target.x-craft.x,dy=target.y-craft.y;
                double range=StrictMath.hypot(dx,dy);
                if(range>0) {
                    craft.x+=dx/range*175*DT;craft.y+=dy/range*175*DT;
                }
                if((craft.reload-=DT)<=0 && range<115){
                    craft.reload=1.6;
                    if(random.nextDouble()<0.64*weather.accuracyFactor()*(1-target.ecmStrength*target.ecmCondition))
                        hit(target,9,findShip(craft.sourceId),DamageType.FIGHTER);
                }
            } else {
                Missile nearest=null;double range=150;
                for(Missile missile:missiles)if(missile.team!=craft.team){
                    double d=StrictMath.hypot(missile.x-craft.x,missile.y-craft.y);
                    if(d<range){range=d;nearest=missile;}
                }
                if(nearest!=null){
                    double dx=nearest.x-craft.x,dy=nearest.y-craft.y;
                    if(range>0){craft.x+=dx/range*205*DT;craft.y+=dy/range*205*DT;}
                    if(range<28){
                        nearest.integrity-=3;
                        if(nearest.integrity<=0 && missiles.remove(nearest)){
                            missileIntercepts++;
                            visual(nearest.visualId,VisualPhase.INTERCEPT,
                                    nearest.torpedo?"TORPEDO":"MISSILE",nearest.team,nearest.sourceId,
                                    nearest.targetId,nearest.x,nearest.y,nearest.x,nearest.y);
                        }
                        crafts.remove(i);
                    }
                } else {
                    Ship source=findShip(craft.sourceId);
                    if(source!=null){
                        double dx=source.x-craft.x,dy=source.y-craft.y;
                        double distance=StrictMath.hypot(dx,dy);
                        if(distance>35){craft.x+=dx/distance*95*DT;craft.y+=dy/distance*95*DT;}
                    }
                }
            }
        }
    }

    private void updateMissiles() {
        // Defensive fire is evaluated before missile movement/impact.
        for (Ship defender : ships) if (defender.alive() && defender.pdReload <= 0 && defender.pointDefenseCondition > 0.20) {
            for (int i = missiles.size() - 1; i >= 0; i--) {
                Missile missile = missiles.get(i);
                if (missile.team == defender.team) continue;
                if (StrictMath.hypot(missile.x - defender.x, missile.y - defender.y) > defender.pdRange * weather.sensorFactor()) continue;
                defender.pdReload = 0.16 / defender.pointDefenseCondition;
                if (defender.power < 1.5) break;
                defender.power -= 1.5; defender.heat += 0.8;
                if (random.nextDouble() < 0.68 * defender.pointDefenseCondition * weather.accuracyFactor()) {
                    missile.integrity -= 2.7;
                    if (missile.integrity <= 0) {
                        missiles.remove(i); defender.intercepted++; missileIntercepts++;
                        visual(missile.visualId,VisualPhase.INTERCEPT,
                                missile.torpedo?"TORPEDO":"MISSILE",missile.team,missile.sourceId,
                                missile.targetId,missile.x,missile.y,missile.x,missile.y);
                    }
                }
                break;
            }
        }
        for(Ship defender:ships)if(defender.alive()&&defender.pdReload<=0
                &&defender.pointDefenseCondition>0.20&&defender.pdRange>0&&defender.power>1.5){
            for(int i=crafts.size()-1;i>=0;i--) {
                Craft craft=crafts.get(i);
                if(craft.team==defender.team || StrictMath.hypot(craft.x-defender.x,craft.y-defender.y)>defender.pdRange*weather.sensorFactor())continue;
                defender.power-=1.5;defender.heat+=0.8;
                defender.pdReload=0.18/defender.pointDefenseCondition;
                if(random.nextDouble()<0.67*defender.pointDefenseCondition*weather.accuracyFactor()) {
                    craft.integrity-=20;
                    if(craft.integrity<=0)crafts.remove(i);
                }
                break;
            }
        }
        for (int i = missiles.size() - 1; i >= 0; i--) {
            Missile m = missiles.get(i);
            Ship target = findShip(m.targetId);
            if (target == null || !target.alive() || (m.fuel -= DT) <= 0) {
                missiles.remove(i);
                visual(m.visualId,VisualPhase.TIMEOUT,m.torpedo?"TORPEDO":"MISSILE",
                        m.team,m.sourceId,m.targetId,m.x,m.y,m.x,m.y);
                continue;
            }
            double dx = target.x - m.x, dy = target.y - m.y, range = StrictMath.hypot(dx, dy);
            if (range < 13) {
                hit(target,m.damage,findShip(m.sourceId),
                        m.torpedo?DamageType.TORPEDO:DamageType.MISSILE,m.visualId);
                missiles.remove(i);continue;
            }
            if (range > 0) {
                double guidance=(m.torpedo?0.025:0.04)*weather.guidanceFactor()*(1-target.ecmStrength*target.ecmCondition);
                m.vx = (1-guidance)*m.vx + guidance*dx/range;
                m.vy = (1-guidance)*m.vy + guidance*dy/range;
                double magnitude = StrictMath.hypot(m.vx, m.vy);
                m.x += (m.vx / magnitude) * (m.torpedo?135:220) * DT;
                m.y += (m.vy / magnitude) * (m.torpedo?135:220) * DT;
            }
        }
    }
    private Ship findShip(int id) { for (Ship ship : ships) if (ship.id == id) return ship; return null; }

    private void hit(Ship defender, double raw, Ship attacker, DamageType kind) {
        hit(defender,raw,attacker,kind,0);
    }
    private void hit(Ship defender, double raw, Ship attacker,DamageType kind,long shotId) {
        if (!defender.alive()) return;
        visual(shotId,VisualPhase.IMPACT,kind.name(),attacker==null?-1:attacker.team,
                attacker==null?0:attacker.id,defender.id,defender.x,defender.y,
                defender.x,defender.y);
        double dx=attacker==null?-StrictMath.cos(defender.facing):attacker.x-defender.x;
        double dy=attacker==null?-StrictMath.sin(defender.facing):attacker.y-defender.y;
        hitAt(defender,raw,attacker,kind,dx,dy);
    }
    /** Environmental impacts share directional protection with actual weapons. */
    private void hitAt(Ship defender,double raw,Ship attacker,DamageType kind,double dx,double dy) {
        if (!defender.alive()) return;
        hitCount++;
        double c=StrictMath.cos(defender.facing),s=StrictMath.sin(defender.facing);
        double localX=dx*c+dy*s, localY=-dx*s+dy*c;
        double distance=StrictMath.hypot(localX,localY);
        if(distance<1e-9){localX=1;localY=0;distance=1;}
        double radialX=localX/distance,radialY=localY/distance;
        DirectionalDefense.Impact impact=defender.defense.impact(localX,localY,
                radialX*0.85,radialY*0.65,raw,kind);
        defender.shields=defender.defense.totalShields();
        defender.armor=defender.defense.totalArmor();
        double damage=impact.hullDamage();
        if(damage<=0)return;
        if (defender.power > 3 && defender.sif > 0 && defender.reactorCondition > 0.15) {
            double fieldAbsorb = damage * defender.sif;
            defender.power -= Math.min(defender.power, fieldAbsorb * 0.6);
            damage -= fieldAbsorb;
        }
        defender.hull -= damage;
        if(kind==DamageType.PARTICLE){
            defender.power=Math.max(0,defender.power-damage*0.45);
            defender.sensorsCondition=Math.max(0,defender.sensorsCondition-damage/defender.maxHull*0.15);
        }
        if(kind==DamageType.PLASMA)defender.heat+=damage*0.35;
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
                case HANGAR: defender.hangarCondition = Math.max(0,defender.hangarCondition-loss); break;
                case MAGAZINE: defender.magazineCondition=Math.max(0,defender.magazineCondition-loss); break;
                case ELECTRONIC_WARFARE: defender.ecmCondition=Math.max(0,defender.ecmCondition-loss); break;
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
        hash = mix(hash, environmentalHits); hash = mix(hash, weather.preset().seed());
        hash = mix(hash, weather.meteorHits()); hash = mix(hash, weather.meteorMisses());
        hash = mix(hash, weather.neutronHits());
        for (SpaceWeather.Meteor meteor : weather.meteors()) {
            hash = mix(hash, meteor.targetId()); hash = mix(hash, meteor.strikeTick());
            hash = mix(hash, meteor.resolved() ? 1 : 0);
        }
        for (Ship s : ships) {
            hash = mix(hash, s.id); hash = mix(hash, Double.doubleToLongBits(s.x));
            hash = mix(hash, Double.doubleToLongBits(s.y));
            hash = mix(hash, Double.doubleToLongBits(s.hull));
            hash = mix(hash, Double.doubleToLongBits(s.shields));
            hash = mix(hash, Double.doubleToLongBits(s.armor));
            hash = mix(hash,s.defense.fingerprint());
            hash = mix(hash, Double.doubleToLongBits(s.power));
            hash = mix(hash,s.missileAmmo);hash=mix(hash,s.torpedoAmmo);
            hash = mix(hash,s.fighterReserve);hash=mix(hash,s.droneReserve);
            hash=mix(hash,Double.doubleToLongBits(s.hangarCondition));
            hash=mix(hash,Double.doubleToLongBits(s.magazineCondition));
        }
        for (Missile m : missiles) { hash = mix(hash, m.sourceId); hash = mix(hash,Double.doubleToLongBits(m.y)); hash = mix(hash, Double.doubleToLongBits(m.x)); }
        for (Craft craft : crafts) { hash=mix(hash,craft.id);hash=mix(hash,Double.doubleToLongBits(craft.x));hash=mix(hash,Double.doubleToLongBits(craft.y)); }
        return hash;
    }
    private static long mix(long hash, long n) { return (hash ^ n) * 0x100000001b3L; }
}
