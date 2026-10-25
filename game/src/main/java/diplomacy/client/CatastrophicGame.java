package diplomacy.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import diplomacy.sim.Battle;
import diplomacy.sim.BattleView;
import diplomacy.sim.DemonstrationBattle;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.FleetPresets;
import diplomacy.tech.TechnologyCatalog;
import java.io.IOException;

/** Same interactive client on Windows/Linux desktop and Android; no duplicate rules. */
public final class CatastrophicGame extends ApplicationAdapter {
    private static final float W = 1280, H = 720;
    private OrthographicCamera camera, battleCamera;
    private FitViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private InstancedShipRenderer instanced;
    private TerranPlaceholderSprites terranSprites;
    private WeaponBeamSprites weaponBeams;
    private ModularWeaponFxRenderer weaponFx;
    private TacticalUiIcons uiIcons;
    private Battle battle;
    private TechnologyCatalog technology;
    private Shipyard.Design leftDesign, rightDesign;
    private ShipyardPanel shipyard;
    private Battle.Faction left = Battle.Faction.TERRAN, right = Battle.Faction.DRAXIAN;
    private long nextSeed = 2479;
    private float accumulator;
    private boolean running = true;
    private int speed = 1;
    /** Keyboard [ ] and the compact header controls select actual deployed ships per side. */
    private static final int[] FLEET_SIZES = {1,2,4,8,16,32,64,100,150,200};
    private int fleetIndex = 4;
    private String renderer = "software geometry";
    private String saveState = "FLEETS NOT SAVED";
    private static final String FLEET_FILE = "fleet/fleets-v1.dat";
    // Only Android's debuggable launcher can enable this opt-in, read-only probe.
    private final boolean verifyDemo;
    private long lastVerifiedEvent;
    private int verifiedFrames, verifiedShots, verifiedImpacts, maxAnimations;
    private boolean verificationReported;
    private final EnumSet<Battle.DamageType> firedWeapons=EnumSet.noneOf(Battle.DamageType.class);
    private final Set<Long> observedShots=new HashSet<>();

    public CatastrophicGame(){this(false);}
    public CatastrophicGame(boolean verifyDemo){
        this.verifyDemo=verifyDemo;
        if(verifyDemo){fleetIndex=FLEET_SIZES.length-1;speed=8;}
    }

    @Override public void create() {
        camera = new OrthographicCamera();
        battleCamera = new OrthographicCamera();
        battleCamera.setToOrtho(false,W,H);
        viewport = new FitViewport(W, H, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        shapes = new ShapeRenderer(); batch = new SpriteBatch(); font = new BitmapFont();
        terranSprites = new TerranPlaceholderSprites();
        weaponBeams = new WeaponBeamSprites();
        weaponFx = new ModularWeaponFxRenderer();
        uiIcons = new TacticalUiIcons();
        font.getData().setScale(1.23f);
        try {
            instanced = new InstancedShipRenderer();
            renderer = "OpenGL 3 instancing";
        } catch (RuntimeException error) {
            Gdx.app.error("GPU", "Instanced renderer unavailable; falling back", error);
        }
        try { technology = TechnologyCatalog.loadDefault(); }
        catch (IOException error) { throw new IllegalStateException("Technology resources missing from game distribution", error); }
        leftDesign=Shipyard.starter(left,Shipyard.Hull.CORVETTE);
        rightDesign=Shipyard.starter(right,Shipyard.Hull.CORVETTE);
        restoreFleets();
        shipyard = new ShipyardPanel(technology,(team,design)->{
            if(team==0){leftDesign=design;left=design.faction;} else {rightDesign=design;right=design.faction;}
            saveFleets();
            reset();
        },leftDesign,rightDesign,terranSprites);
        shipyard.setIcons(uiIcons);
        reset();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (shipyard.open()) return shipyard.key(key);
                if (key == Input.Keys.TAB || key == Input.Keys.Y) { shipyard.show(); return true; }
                if (key == Input.Keys.SPACE) { running = !running; return true; }
                if (key == Input.Keys.R) { reset(); return true; }
                if (key == Input.Keys.LEFT_BRACKET) { changeFleetSize(-1); return true; }
                if (key == Input.Keys.RIGHT_BRACKET) { changeFleetSize(1); return true; }
                if (key == Input.Keys.PLUS || key == Input.Keys.EQUALS) { speed = Math.min(16, speed * 2); return true; }
                if (key == Input.Keys.MINUS) { speed = Math.max(1, speed / 2); return true; }
                if (key == Input.Keys.Q) { changeFaction(0); return true; }
                if (key == Input.Keys.E) { changeFaction(1); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                com.badlogic.gdx.math.Vector2 world = viewport.unproject(new com.badlogic.gdx.math.Vector2(x, y));
                if (shipyard.open()) {
                    shipyard.startTouch(world.x,world.y,pointer);
                    return true;
                }
                if (world.y > 650) {
                    if (world.x > 1160) { shipyard.show(); return true; }
                    if (world.x >= 1080) changeFleetSize(1);
                    else if (world.x >= 990) changeFleetSize(-1);
                    else if (world.x < 125) running = !running;
                    else if (world.x < 250) reset();
                    else if (world.x < 365) speed = Math.max(1, speed / 2);
                    else if (world.x < 475) speed = Math.min(16, speed * 2);
                    else if (world.x < 745) changeFaction(0);
                    else changeFaction(1);
                    return true;
                }
                return false;
            }
            @Override public boolean touchDragged(int x,int y,int pointer) {
                if(!shipyard.open())return false;
                com.badlogic.gdx.math.Vector2 world=viewport.unproject(new com.badlogic.gdx.math.Vector2(x,y));
                return shipyard.drag(world.x,world.y,pointer);
            }
            @Override public boolean touchUp(int x,int y,int pointer,int button) {
                com.badlogic.gdx.math.Vector2 world=viewport.unproject(new com.badlogic.gdx.math.Vector2(x,y));
                return shipyard.endTouch(world.x,world.y,pointer);
            }
            @Override public boolean scrolled(float amountX,float amountY) {
                return shipyard.scroll(amountY);
            }
        });
    }

    private void changeFleetSize(int delta) {
        int selected = Math.max(0,Math.min(FLEET_SIZES.length-1,fleetIndex+delta));
        if(selected!=fleetIndex){fleetIndex=selected;reset();}
    }

    private void changeFaction(int team) {
        if(team==0) {
            left=next(left);
            leftDesign=Shipyard.starter(left,leftDesign.hull);
            shipyard.sync(0,leftDesign);
        } else {
            right=next(right);
            rightDesign=Shipyard.starter(right,rightDesign.hull);
            shipyard.sync(1,rightDesign);
        }
        saveFleets();
        reset();
    }

    /** Local storage is private to this app on Android and the user's home directory on desktop. */
    private void restoreFleets() {
        FileHandle primary=Gdx.files.local(FLEET_FILE);
        FileHandle backup=Gdx.files.local(FLEET_FILE+".bak");
        if(!primary.exists()&&!backup.exists())return;
        for(FileHandle file:new FileHandle[]{primary,backup}) {
            if(!file.exists())continue;
            try {
                FleetPresets.Fleets saved=FleetPresets.decode(file.readString("UTF-8"),technology);
                leftDesign=saved.left();rightDesign=saved.right();
                left=leftDesign.faction;right=rightDesign.faction;
                saveState=file==primary?"FLEETS RESTORED":"BACKUP FLEETS RESTORED";
                return;
            }catch(RuntimeException damaged){
                Gdx.app.error("SAVE", "Fleet preset failed checksum/fitting validation: "+file.path(), damaged);
            }
        }
        saveState="SAVES INVALID - STARTER FLEETS USED";
    }
    private void saveFleets() {
        try {
            String data=FleetPresets.encode(leftDesign,rightDesign,technology);
            FileHandle destination=Gdx.files.local(FLEET_FILE);
            FileHandle temporary=Gdx.files.local(FLEET_FILE+".tmp");
            FileHandle backup=Gdx.files.local(FLEET_FILE+".bak");
            temporary.writeString(data,false,"UTF-8");
            FleetPresets.decode(temporary.readString("UTF-8"),technology);
            if(destination.exists()) {
                try {
                    FleetPresets.decode(destination.readString("UTF-8"),technology);
                    destination.copyTo(backup);
                } catch(IllegalArgumentException damaged) {
                    Gdx.app.log("SAVE", "Refusing to overwrite a valid backup with an invalid primary save");
                }
            }
            temporary.moveTo(destination);
            saveState="FLEETS SAVED";
        } catch(RuntimeException failed) {
            Gdx.app.error("SAVE", "Unable to persist fitted fleets", failed);
            saveState="FLEET SAVE FAILED";
        }
    }

    private static Battle.Faction next(Battle.Faction f) {
        Battle.Faction[] values = Battle.Faction.values();
        return values[(f.ordinal() + 1) % values.length];
    }
    private void reset() {
        battle = verifyDemo ? DemonstrationBattle.create(technology) :
                Battle.duel(nextSeed++,FLEET_SIZES[fleetIndex],leftDesign,rightDesign,technology);
        accumulator = 0;
        running = true;
        if(verifyDemo){
            lastVerifiedEvent=0;verifiedFrames=verifiedShots=verifiedImpacts=maxAnimations=0;
            verificationReported=false;firedWeapons.clear();observedShots.clear();
            Gdx.app.log("CD-DEMO-VERIFY","START 200v200 combined-arms; seed="+DemonstrationBattle.SEED);
        }
    }

    @Override public void render() {
        float delta = Math.min(0.08f, Gdx.graphics.getDeltaTime());
        if (running && !shipyard.open() && !battle.finished()) {
            accumulator += delta * speed;
            int turns = 0;
            while (accumulator >= Battle.DT && turns++ < 64) {
                battle.step(); accumulator -= Battle.DT;
            }
            if (turns >= 64) accumulator = 0; // Frame cannot create unbounded catch-up latency.
        }
        Gdx.gl.glClearColor(0.015f, 0.025f, 0.055f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        viewport.apply(); camera.update();
        if (shipyard.open()) {
            shapes.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);
            shipyard.draw(shapes,batch,font);
            return;
        }
        updateBattleCamera();
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.11f, 0.16f, 0.26f, 0.75f);
        for (int x = 40; x <= 1240; x += 40) shapes.line(x, 25, x, 645);
        for (int y = 25; y <= 645; y += 40) shapes.line(40, y, 1240, y);
        for (Battle.Beam beam : battle.beams()) {
            if(weaponBeams!=null && weaponBeams.canDraw(beam,battle.tick))continue;
            shapes.setColor(switch(beam.type) {
                case KINETIC -> Color.GOLD;
                case PLASMA -> Color.ORANGE;
                case PARTICLE -> Color.VIOLET;
                default -> beam.team==0?Color.CYAN:Color.CORAL;
            });
            shapes.line((float) beam.x1, (float) beam.y1, (float) beam.x2, (float) beam.y2);
        }
        // Engine exhaust is displayed by both GPU-instanced and fallback hull paths.
        for (Battle.Ship ship : battle.ships()) if(ship.alive()) {
            double scale=ship.visualScale(),dx=StrictMath.cos(ship.facing),dy=StrictMath.sin(ship.facing);
            shapes.setColor(ship.team==0?Color.SKY:Color.ORANGE);
            shapes.line((float)(ship.x-dx*10*scale),(float)(ship.y-dy*10*scale),
                    (float)(ship.x-dx*23*scale),(float)(ship.y-dy*23*scale));
        }
        shapes.end();
        if (instanced == null) drawShipsFallback();
        else {
            try {
                instanced.render(battle,battleCamera,ship -> terranSprites!=null && terranSprites.hasSprite(ship));
                int expected=0;
                for(Battle.Ship ship:battle.ships())
                    if(ship.alive() && (terranSprites==null || !terranSprites.hasSprite(ship)))expected++;
                if(instanced.instances!=expected)
                    throw new IllegalStateException("GPU instance count disagrees with non-sprite ships");
            }
            catch (RuntimeException error) {
                Gdx.app.error("GPU", "Switching to per-ship rendering", error);
                instanced.close(); instanced = null; renderer = "software geometry";
                drawShipsFallback();
            }
        }
        // The original GPU/geometry path remains the non-Terran and missing-asset fallback.
        batch.setProjectionMatrix(battleCamera.combined);
        if(terranSprites!=null && terranSprites.loaded()>0)terranSprites.render(battle,batch);
        if(weaponFx!=null)weaponFx.render(battle,batch,weaponBeams);
        drawArmorDamage();
        // Query sprite availability before opening ShapeRenderer's GL batch.
        java.util.Set<Long> texturedMissiles=new java.util.HashSet<>();
        if(weaponFx!=null)for(Battle.Missile missile:battle.missiles())
            if(weaponFx.hasMissilePayload(missile,battle.tick))
                texturedMissiles.add(missile.visualId);
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Missile m : battle.missiles()) {
            if(texturedMissiles.contains(m.visualId))continue;
            shapes.setColor(m.torpedo?Color.ORANGE:m.team==0?Color.CYAN:Color.SCARLET);
            shapes.circle((float) m.x, (float) m.y, m.torpedo?4.4f:2.8f, 7);
        }
        for (Battle.Craft craft : battle.crafts()) {
            shapes.setColor(craft.team==0?Color.CYAN:Color.CORAL);
            shapes.circle((float)craft.x,(float)craft.y,craft.fighter?3.6f:2.4f,8);
        }
        shapes.setColor(0.08f, 0.13f, 0.22f, 0.94f);
        shapes.rect(0, 649, W, 71);
        shapes.end();
        batch.setProjectionMatrix(camera.combined); batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, running ? "PAUSE" : "RESUME", 15, 693);
        font.draw(batch, "RESTART", 139, 693);
        font.draw(batch, "SLOW", 270, 693);
        font.draw(batch, "FAST x" + speed, 378, 693);
        font.setColor(left.r, left.g, left.b, 1);
        font.draw(batch, "< " + left.displayName, 505, 693);
        font.setColor(right.r, right.g, right.b, 1);
        font.draw(batch, "< " + right.displayName, 751, 693);
        font.setColor(Color.GOLD);
        font.draw(batch,"-",1000,693);
        font.draw(batch,""+FLEET_SIZES[fleetIndex]+"v"+FLEET_SIZES[fleetIndex],1022,693);
        font.draw(batch,"+",1112,693);
        font.draw(batch,"SHIPYARD",1166,693);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "FLEETS " + battle.alive(0) + " / " + battle.alive(1)
                + "   T+" + (battle.tick / 30) + "s   HITS " + battle.hitCount
                + "   PD " + battle.missileIntercepts + "   CRAFT " + battle.crafts().size()
                + "   " + renderer + "   " + saveState, 22, 38);
        // Optional source-backed ornamentation; labels and pointer hitboxes remain unchanged.
        if (uiIcons != null && uiIcons.available()) {
            uiIcons.draw(batch, "restart", 146, 652, 25);
            uiIcons.draw(batch, "speed", 391, 652, 25);
            uiIcons.draw(batch, "squadron", 1056, 652, 25);
            uiIcons.draw(batch, "shipyard_ship", 1192, 652, 25);
        }
        if (battle.finished()) {
            font.setColor(Color.GOLD);
            font.draw(batch, "ENGAGEMENT COMPLETE  —  " + (battle.alive(0) > 0 ? left.displayName : right.displayName)
                    + " HOLDS THE FIELD", 330, 617);
        }
        batch.end();
        // Observe a completed GL frame, not just headless simulation ticks.
        if(verifyDemo)verifyRenderedDemoFrame();
    }

    /**
     * Debug-only, read-only telemetry. Does not alter damage, projectile motion,
     * ship selection, tick cadence, or the player's permanent fleet blueprints.
     */
    private void verifyRenderedDemoFrame(){
        if(verificationReported)return;
        verifiedFrames++;
        if(weaponFx!=null)maxAnimations=Math.max(maxAnimations,weaponFx.activeAnimations());
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.sequence()<=lastVerifiedEvent)continue;
            lastVerifiedEvent=event.sequence();
            if(event.phase()==Battle.VisualPhase.FIRE){
                verifiedShots++;observedShots.add(event.shotId());
                try {firedWeapons.add(Battle.DamageType.valueOf(event.weapon()));}
                catch(IllegalArgumentException auxiliary){ /* Drone launches are separate from weapon families. */ }
            }
            if(event.phase()==Battle.VisualPhase.IMPACT && observedShots.contains(event.shotId()))
                verifiedImpacts++;
        }
        if(battle.tick<480 && !battle.finished())return;
        StringBuilder error=new StringBuilder();
        if(battle.ships().size()!=400)error.append("wrong deployed ship count; ");
        for(Battle.DamageType type:new Battle.DamageType[]{
                Battle.DamageType.LASER,Battle.DamageType.KINETIC,
                Battle.DamageType.PLASMA,Battle.DamageType.PARTICLE,
                Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO})
            if(!firedWeapons.contains(type))error.append("missing FIRE ").append(type).append("; ");
        if(verifiedFrames<6)error.append("insufficient rendered frames; ");
        if(verifiedShots==0||verifiedImpacts==0||battle.hitCount==0)
            error.append("no correlated weapon fire/impact/damage; ");
        if(maxAnimations==0)error.append("modular animation compositor not exercised; ");
        verificationReported=true;
        if(error.length()>0){
            Gdx.app.error("CD-DEMO-VERIFY","FAIL 200v200 tick="+battle.tick+" "+error);
            throw new IllegalStateException("CD 200v200 APK verification failed: "+error);
        }
        int authored=weaponFx==null?0:weaponFx.loadedTextures();
        int payload=weaponBeams==null?0:weaponBeams.loaded();
        Gdx.app.log("CD-DEMO-VERIFY","PASS 200v200 mechanics+render-loop tick="+battle.tick+
                " frames="+verifiedFrames+" shots="+verifiedShots+" impacts="+verifiedImpacts+
                " weaponFamilies="+firedWeapons+" hits="+battle.hitCount+
                " renderer="+renderer+" composedAnimations="+maxAnimations+
                " authoredTextures="+authored+" beamFrames="+payload);
        if(authored==0||payload==0)
            Gdx.app.log("CD-DEMO-VERIFY","ART_PENDING 200v200: optional curated PNGs missing from this APK; geometry fallback used");
        else Gdx.app.log("CD-DEMO-VERIFY","ART_PRESENT 200v200: loaded authored muzzle/impact or payload textures (pixel-level review still required)");
    }

    /** World camera follows the active engagement; UI coordinates never move. */
    private void updateBattleCamera() {
        BattleView.Frame shot=BattleView.of(battle);
        battleCamera.zoom=(float)shot.zoom();
        battleCamera.position.set((float)shot.centerX(),(float)shot.centerY(),0);
        battleCamera.update();
    }

    /**
     * Temporary ship-local damage indicators until an exterior alpha mask and
     * structural sublayer shader are introduced for the final sprite pipeline.
     */
    private void drawArmorDamage() {
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(Battle.Ship ship:battle.ships())if(ship.alive()) {
            double length=23*ship.visualScale(), breadth=13*ship.visualScale();
            double c=StrictMath.cos(ship.facing),s=StrictMath.sin(ship.facing);
            for(int row=0;row<diplomacy.sim.DirectionalDefense.GRID;row++)
                for(int col=0;col<diplomacy.sim.DirectionalDefense.GRID;col++) {
                    var tile=ship.defense.tile(row,col);
                    if(!tile.occupied || tile.exposure()<0.45)continue;
                    double x=(1-(row+0.5)*2/diplomacy.sim.DirectionalDefense.GRID)*length;
                    double y=(1-(col+0.5)*2/diplomacy.sim.DirectionalDefense.GRID)*breadth;
                    float wx=(float)(ship.x+x*c-y*s),wy=(float)(ship.y+x*s+y*c);
                    float radius=(float)(0.6+tile.exposure()*0.8);
                    shapes.setColor(tile.internalDamage()>0.65?com.badlogic.gdx.graphics.Color.SCARLET:
                            com.badlogic.gdx.graphics.Color.ORANGE);
                    shapes.circle(wx,wy,radius,5);
                }
        }
        shapes.end();
    }

    private void drawShipsFallback() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Ship s : battle.ships()) if (s.alive()
                && (terranSprites==null||!terranSprites.hasSprite(s))) {
            shapes.setColor(s.faction.r, s.faction.g, s.faction.b, 1);
            float x = (float) s.x, y = (float) s.y;
            float scale=(float)s.visualScale();
            float a = (float) Math.cos(s.facing)*scale, b = (float) Math.sin(s.facing)*scale;
            shapes.triangle(x + a * 14, y + b * 14,
                    x - a * 8 - b * 7, y - b * 8 + a * 7,
                    x - a * 8 + b * 7, y - b * 8 - a * 7);
        }
        shapes.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void dispose() {
        if (instanced != null) instanced.close();
        if(terranSprites!=null)terranSprites.dispose();
        if(weaponBeams!=null)weaponBeams.dispose();
        if(weaponFx!=null)weaponFx.dispose();
        if(uiIcons!=null)uiIcons.dispose();
        shapes.dispose(); batch.dispose(); font.dispose();
    }
}
