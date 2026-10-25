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
    private ProceduralStarfield starfield;
    private int skyPreviewLevel = 1;
    private boolean weatherEnabled = true;
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

    @Override public void create() {
        camera = new OrthographicCamera();
        battleCamera = new OrthographicCamera();
        battleCamera.setToOrtho(false,W,H);
        viewport = new FitViewport(W, H, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        shapes = new ShapeRenderer(); batch = new SpriteBatch(); font = new BitmapFont();
        terranSprites = new TerranPlaceholderSprites();
        weaponBeams = new WeaponBeamSprites();
        try {
            starfield = new ProceduralStarfield();
            starfield.useLevel(skyPreviewLevel);
        } catch (RuntimeException error) {
            Gdx.app.error("SKY", "Background generator unavailable; using plain background", error);
            if (starfield != null) starfield.dispose();
            starfield = null;
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
        reset();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (shipyard.open()) return shipyard.key(key);
                if (key == Input.Keys.TAB || key == Input.Keys.Y) { shipyard.show(); return true; }
                if (key == Input.Keys.SPACE) { running = !running; return true; }
                if (key == Input.Keys.R) { reset(); return true; }
                if (key == Input.Keys.W) { weatherEnabled = !weatherEnabled; reset(); return true; }
                if (key == Input.Keys.N) { previewSky(1); return true; }
                if (key == Input.Keys.B) { previewSky(-1); return true; }
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
                // Weather toggle is touch-accessible; selecting a sky stays preview-only.
                if (world.y >= 616 && world.y <= 649 && world.x >= 10 && world.x < 640) {
                    weatherEnabled = !weatherEnabled; reset(); return true;
                }
                // On-device sky preview; does not restart or alter the active battle.
                if (starfield != null && world.y >= 616 && world.y <= 649) {
                    if (world.x >= 1000 && world.x <= 1050) { previewSky(-1); return true; }
                    if (world.x >= 1210 && world.x <= 1280) { previewSky(1); return true; }
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

    /** Preview only, independent of sandbox battle state until campaign selection ships. */
    private void previewSky(int delta) {
        int selected = Math.max(1, Math.min(500, skyPreviewLevel + delta));
        if (selected == skyPreviewLevel || starfield == null) return;
        try {
            starfield.useLevel(selected);
            skyPreviewLevel = selected;
        } catch (RuntimeException error) {
            Gdx.app.error("SKY", "Unable to generate background for level " + selected, error);
        }
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
        if (weatherEnabled) battle.configureWeather(SpaceWeather.forLevel(skyPreviewLevel));
        accumulator = 0;
        running = true;
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
        if (starfield != null) starfield.render(batch, camera, battleCamera, W, H);
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
        drawWeatherOverlay();
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
        if(weaponBeams!=null && weaponBeams.loaded()>0)weaponBeams.render(battle,batch);
        drawArmorDamage();
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Missile m : battle.missiles()) {
            shapes.setColor(m.torpedo?Color.ORANGE:m.team==0?Color.CYAN:Color.SCARLET);
            shapes.circle((float) m.x, (float) m.y, m.torpedo?4.4f:2.8f, 7);
        }
        for (Battle.Craft craft : battle.crafts()) {
            shapes.setColor(craft.team==0?Color.CYAN:Color.CORAL);
            shapes.circle((float)craft.x,(float)craft.y,craft.fighter?3.6f:2.4f,8);
        }
        shapes.end();
        // The HUD belongs to the fixed viewport camera, never the moving battle camera.
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.13f, 0.22f, 0.94f);
        shapes.rect(0, 649, W, 71);
        if (starfield != null) {
            shapes.setColor(0.045f, 0.075f, 0.15f, 0.88f);
            shapes.rect(1000, 616, 280, 33);
        }
        shapes.setColor(0.045f, 0.075f, 0.15f, 0.88f);
        shapes.rect(10, 616, 920, 33);
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
        font.setColor(Color.GOLD);
        SpaceWeather.Preset skyCondition = battle.weather().preset();
        String weatherName = skyCondition.enabled() ? skyCondition.primary().name().replace('_',' ') : "CLEAR";
        if (skyCondition.secondary() != SpaceWeather.Kind.CLEAR)
            weatherName += " + " + skyCondition.secondary().name().replace('_',' ');
        font.draw(batch, "ENV " + (weatherEnabled ? "ON" : "OFF") + "  " + weatherName
                + "   [W: TOGGLE]  [R: APPLY SKY]", 24, 638);
        font.setColor(Color.LIGHT_GRAY);
        if (starfield != null) {
            font.draw(batch, "<", 1016, 638);
            font.draw(batch, "SKY " + skyPreviewLevel + "/500  SECTOR " + ((skyPreviewLevel-1)/50+1), 1056, 638);
            font.draw(batch, ">", 1246, 638);
        }
        font.draw(batch, "FLEETS " + battle.alive(0) + " / " + battle.alive(1)
                + "   T+" + (battle.tick / 30) + "s   HITS " + battle.hitCount
                + "   PD " + battle.missileIntercepts + "   CRAFT " + battle.crafts().size()
                + "   " + renderer + "   " + saveState, 22, 38);
        if (battle.finished()) {
            font.setColor(Color.GOLD);
            font.draw(batch, "ENGAGEMENT COMPLETE  —  " + (battle.alive(0) > 0 ? left.displayName : right.displayName)
                    + " HOLDS THE FIELD", 330, 617);
        }
        batch.end();
    }

    /**
     * Environmental warnings are world-space effects; no asteroid is a physics collider.
     * A meteor is an approaching translucent marker and a short resolved impact flash.
     */
    private void drawWeatherOverlay() {
        SpaceWeather weather = battle.weather();
        if (!weather.preset().enabled()) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(battleCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (weather.neutronActive()) {
            double a = weather.neutronAngle();
            float nx = (float)StrictMath.cos(a), ny = (float)StrictMath.sin(a);
            float px = -ny, py = nx;
            for (int stripe=-2; stripe<=2; stripe++) {
                float d = stripe * 11f;
                shapes.setColor(0.42f, 0.77f, 1f, stripe==0 ? 0.85f : 0.30f);
                shapes.line((float)SpaceWeather.CENTER_X - nx * 1300 + px*d,
                        (float)SpaceWeather.CENTER_Y - ny * 1300 + py*d,
                        (float)SpaceWeather.CENTER_X + nx * 1300 + px*d,
                        (float)SpaceWeather.CENTER_Y + ny * 1300 + py*d);
            }
        }
        if (weather.solarActive()) {
            for (int i=0; i<8; i++) {
                float y = 75 + i * 83 + (float)StrictMath.sin(weather.tick()*0.055 + i)*18;
                shapes.setColor(1f,0.46f,0.13f,0.22f);
                shapes.line(0,y,1280,y+105);
            }
        }
        if (weather.preset().includes(SpaceWeather.Kind.ION_STORM)) {
            for (int i=0; i<5; i++) {
                float x = 160 + i*238;
                float y = 115 + (float)StrictMath.sin(weather.tick()*0.04+i*2)*90;
                shapes.setColor(.25f,.91f,.99f,.18f);
                shapes.line(x,y,x+54,y+22);
                shapes.line(x+54,y+22,x+77,y-9);
            }
        }
        for (SpaceWeather.Meteor meteor : weather.meteors()) {
            float age = Math.max(0f,Math.min(1f,
                    (weather.tick()-meteor.arrivalTick()) /
                            (float)Math.max(1,meteor.strikeTick()-meteor.arrivalTick())));
            float x = (float)meteor.x(),y = (float)meteor.y();
            if (!meteor.resolved()) {
                shapes.setColor(1f,.71f,.28f,.20f + age*.53f);
                shapes.circle(x,y,(float)meteor.radius()+17*(1-age),18);
            } else {
                shapes.setColor(meteor.hit() ? 1f : .55f,
                        meteor.hit() ? .48f : .68f,.30f,.62f);
                shapes.circle(x,y,(float)meteor.radius()+8,18);
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (SpaceWeather.Meteor meteor : weather.meteors()) {
            float age = Math.max(0f,Math.min(1f,
                    (weather.tick()-meteor.arrivalTick()) /
                            (float)Math.max(1,meteor.strikeTick()-meteor.arrivalTick())));
            float opacity = meteor.resolved()
                    ? Math.max(0f, (meteor.expiryTick()-weather.tick()) /
                            (float)Math.max(1,meteor.expiryTick()-meteor.strikeTick()))
                    : age;
            shapes.setColor(meteor.resolved() && meteor.hit() ? 1f : .70f,
                    meteor.resolved() && meteor.hit() ? .61f : .73f,.47f,.65f*opacity);
            shapes.circle((float)meteor.x(),
                    (float)meteor.y() - (meteor.resolved()?0:18*(1-age)),
                    (float)meteor.radius()*(meteor.resolved()?1.45f:0.20f+age*0.80f),12);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
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
        if(starfield!=null)starfield.dispose();
        shapes.dispose(); batch.dispose(); font.dispose();
    }
}
