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
import diplomacy.sim.SpaceWeather;
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
    private ProceduralStarfield starfield;
    private BattlefieldWeatherVfx weatherVfx;
    private int battlefieldLevel = 1;
    private boolean weatherEnabled = true;
    private TacticalUiIcons uiIcons;
    private Battle battle;
    private TechnologyCatalog technology;
    private Shipyard.Design leftDesign, rightDesign;
    private ShipyardPanel shipyard;
    private Battle.Faction left = Battle.Faction.TERRAN, right = Battle.Faction.DRAXIAN;
    private long nextSeed = 2479;
    private float accumulator;
    private boolean running = true;
    private boolean tacticalMenuOpen;
    private boolean resumeAfterMenu;
    private int speed = 1;
    /** Keyboard [ ] and the compact header controls select actual deployed ships per side. */
    private static final int[] FLEET_SIZES = {1,2,4,8,16,32,64,100,150,200};
    private int fleetIndex = 4;
    private String renderer = "software geometry";
    private String saveState = "FLEETS NOT SAVED";
    private static final String FLEET_FILE = "fleet/fleets-v1.dat";

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
        try {
            starfield = new ProceduralStarfield();
            starfield.useLevel(battlefieldLevel);
        } catch (RuntimeException error) {
            Gdx.app.error("SKY", "Starfield disabled; continuing in plain background", error);
            if (starfield != null) starfield.dispose();
            starfield = null;
        }
        try {
            weatherVfx = new BattlefieldWeatherVfx();
        } catch (RuntimeException error) {
            Gdx.app.error("WEATHER-FX", "Visual hazard renderer unavailable", error);
            weatherVfx = null;
        }
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
                if (tacticalMenuOpen) {
                    if (key == Input.Keys.ESCAPE || key == Input.Keys.M) closeTacticalMenu();
                    return true;
                }
                if (shipyard.open()) return shipyard.key(key);
                if (key == Input.Keys.M || key == Input.Keys.ESCAPE) { openTacticalMenu(); return true; }
                if (key == Input.Keys.TAB || key == Input.Keys.Y) { shipyard.show(); return true; }
                if (key == Input.Keys.SPACE) { running = !running; return true; }
                if (key == Input.Keys.R) { reset(); return true; }
                if (key == Input.Keys.N) { changeBattlefieldLevel(1); return true; }
                if (key == Input.Keys.B) { changeBattlefieldLevel(-1); return true; }
                if (key == Input.Keys.W) { weatherEnabled = !weatherEnabled; reset(); return true; }
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
                if (tacticalMenuOpen) { tapTacticalMenu(world.x,world.y); return true; }
                if (!shipyard.open() && world.x >= 1140 && world.y >= 71 && world.y <= 124) {
                    openTacticalMenu(); return true;
                }
                if (shipyard.open()) {
                    shipyard.startTouch(world.x,world.y,pointer);
                    return true;
                }
                if (world.y >= 613 && world.y < 649) {
                    if (world.x < 830) { weatherEnabled = !weatherEnabled; reset(); return true; }
                    if (world.x >= 900 && world.x < 1040) { changeBattlefieldLevel(-1); return true; }
                    if (world.x >= 1170) { changeBattlefieldLevel(1); return true; }
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

    private void openTacticalMenu() {
        if (tacticalMenuOpen || shipyard.open()) return;
        resumeAfterMenu = running;
        running = false;
        tacticalMenuOpen = true;
    }
    private void closeTacticalMenu() {
        if (!tacticalMenuOpen) return;
        tacticalMenuOpen = false;
        running = resumeAfterMenu;
    }
    private void tapTacticalMenu(float x, float y) {
        if (x < 420 || x > 860) { closeTacticalMenu(); return; }
        if (y >= 462 && y < 510) closeTacticalMenu();
        else if (y >= 402 && y < 450) { reset(); closeTacticalMenu(); }
        else if (y >= 342 && y < 390) {
            closeTacticalMenu();
            shipyard.show();
        }
        else if (y >= 282 && y < 330) changeFleetSize(-1);
        else if (y >= 222 && y < 270) changeFleetSize(1);
        else if (y >= 162 && y < 210) closeTacticalMenu();
    }
    private void drawTacticalMenu() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.008f,0.014f,0.034f,0.97f);
        shapes.rect(408,136,464,423);
        shapes.setColor(0.10f,0.20f,0.32f,1);
        shapes.rect(421,536,438,2);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch,"TACTICAL COMMAND",518,530);
        String[] labels = {"RESUME ENGAGEMENT","RESTART ENGAGEMENT","OPEN SHIPYARD",
                "REDUCE FLEET SIZE","INCREASE FLEET SIZE","CLOSE MENU"};
        for (int i=0;i<labels.length;i++) {
            float y=462-60*i;
            if(uiIcons!=null) uiIcons.drawButton(batch,i==1?"danger":i==2?"special":"primary",438,y,405,46);
            font.setColor(Color.WHITE);
            font.draw(batch,labels[i],476,y+30);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch,"DEPLOYED " + FLEET_SIZES[fleetIndex] + " VERSUS " + FLEET_SIZES[fleetIndex],487,150);
        batch.end();
    }

    /**
     * Actual environment selection. Every change restarts the current encounter
     * with the newly selected level's weather; backgrounds never remain preview-only.
     * Campaign waves/economy are separate future work.
     */
    private void changeBattlefieldLevel(int delta) {
        int selected = Math.max(1,Math.min(500,battlefieldLevel+delta));
        if (selected == battlefieldLevel) return;
        battlefieldLevel = selected;
        if (starfield != null) {
            try { starfield.useLevel(selected); }
            catch (RuntimeException err) { Gdx.app.error("SKY","Cannot select level background "+selected,err); }
        }
        reset();
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
        battle = Battle.duel(nextSeed++,FLEET_SIZES[fleetIndex],leftDesign,rightDesign,technology);
        if (weatherEnabled) battle.configureWeather(SpaceWeather.forLevel(battlefieldLevel));
        // Background and hazards select the same 1..500 level on first launch and after resets.
        if (starfield != null) starfield.useLevel(battlefieldLevel);
        accumulator = 0;
        running = true;
    }

    @Override public void render() {
        float delta = Math.min(0.08f, Gdx.graphics.getDeltaTime());
        if (running && !tacticalMenuOpen && !shipyard.open() && !battle.finished()) {
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
        // World-space sky & hazard layers render before tactical grid, weapons and hulls.
        if (starfield != null) starfield.render(batch,camera,battleCamera,W,H);
        if (weatherVfx != null) weatherVfx.behind(shapes,batch,battleCamera,battle.weather());
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
        if (weatherVfx != null) weatherVfx.above(shapes,batch,battleCamera,battle.weather());
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
        shapes.end();
        // HUD is screen-anchored; do not draw fixed bars in the moving battle camera.
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.13f, 0.22f, 0.94f);
        shapes.rect(0, 649, W, 71);
        shapes.setColor(0.035f, 0.085f, 0.15f, 0.93f);
        shapes.rect(0, 613, W, 36);
        shapes.end();
        batch.setProjectionMatrix(camera.combined); batch.begin();
        if (uiIcons != null) {
            uiIcons.drawButton(batch,running?"primary":"special",4,663,121,38);
            uiIcons.drawButton(batch,"secondary",132,663,117,38);
            uiIcons.drawButton(batch,"secondary",262,663,106,38);
            uiIcons.drawButton(batch,"secondary",374,663,104,38);
            uiIcons.drawButton(batch,"special",1158,663,120,38);
            uiIcons.drawButton(batch,"primary",1138,78,132,41);
        }
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
        font.setColor(Color.WHITE);
        font.draw(batch,"MENU (M)",1156,107);
        font.setColor(Color.GOLD);
        SpaceWeather.Preset activeWeather = battle.weather().preset();
        String condition = activeWeather.enabled()
                ? activeWeather.primary().name().replace('_',' ') : "CLEAR";
        if (activeWeather.secondary() != SpaceWeather.Kind.CLEAR)
            condition += " + " + activeWeather.secondary().name().replace('_',' ');
        font.draw(batch,"ENV " + (weatherEnabled ? "ON" : "OFF") + "   " + condition +
                "   (W / TAP TO TOGGLE)",22,638);
        font.setColor(Color.CYAN);
        font.draw(batch,"<",945,638);
        font.draw(batch,"LEVEL " + battlefieldLevel + "/500",998,638);
        font.draw(batch,">",1235,638);
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
        if (tacticalMenuOpen) drawTacticalMenu();
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
