package diplomacy.client;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import diplomacy.net.BattleConnection;
import diplomacy.sim.*;
import diplomacy.shipyard.SkirmishFleet;
import diplomacy.shipyard.SkirmishFleetCodec;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.tech.TechnologyCatalog;
import hbnet.*;
import java.util.*;

/** Native in-game local directory/friends and authoritative battle view.
 * Uses the same Java core protocol on desktop/Android; device TLS/IME tests remain separate gates.
 */
final class MultiplayerPanel implements AutoCloseable {
    private final Stage stage=new Stage(new FitViewport(1280,720));
    private final Skin skin=new Skin();
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final InputProcessor previous;
    private final Runnable back;
    private final java.util.function.Consumer<BattleConnection.Snapshot> drawBattle;
    private boolean battleView;
    private final LocalProfile profile;
    private final TextField address,port,pin,password,label,card,notes,fleetFile;
    private final com.badlogic.gdx.scenes.scene2d.ui.List<String> servers,friends,fleetChecks;
    private final Label status,matchRule;
    private volatile BattleConnection connection;
    private volatile boolean busy,closed;
    private Hbnet.Client secure;
    private String joinedAddress;
    private int joinedPort;
    private boolean openLan;
    private boolean copyArmed;
    private final Table root=new Table();
    MultiplayerPanel(BitmapFont font,Runnable back,java.util.function.Consumer<BattleConnection.Snapshot> drawBattle)throws Exception{
        this.back=back;this.drawBattle=drawBattle;previous=Gdx.input.getInputProcessor();
        profile=new LocalProfile(Gdx.files.local("network-profile").file().toPath());
        skin.add("default-font",font);Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(new Color(.08f,.12f,.18f,1));p.fill();Texture texture=new Texture(p);p.dispose();skin.add("pixel",texture);
        var background=new TextureRegionDrawable(new TextureRegion(texture));
        Label.LabelStyle ls=new Label.LabelStyle(font,Color.WHITE);skin.add("default",ls);
        TextButton.TextButtonStyle bs=new TextButton.TextButtonStyle(background,background.tint(Color.DARK_GRAY),background,font);skin.add("default",bs);
        TextField.TextFieldStyle fs=new TextField.TextFieldStyle(font,Color.WHITE,background.tint(Color.WHITE),background.tint(Color.GRAY),background);skin.add("default",fs);
        com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle listStyle=new com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle(font,Color.WHITE,Color.LIGHT_GRAY,background.tint(Color.GRAY));skin.add("default",listStyle);
        skin.add("default",new ScrollPane.ScrollPaneStyle());
        address=field("127.0.0.1",253);port=field("27817",5);pin=field("",64);password=field("",128);label=field("",64);card=field("",600);notes=field("",256);
        fleetFile=field("skirmish-left.cdfleet",128);fleetFile.setPasswordMode(false);
        servers=new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);friends=new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
        fleetChecks=new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
        status=new Label("Known servers only. Presence: Unknown/unreachable.",skin);
        matchRule=new Label("HOST RULE: Connect to a capped PvP battle",skin);
        root.setFillParent(true);root.pad(22);root.defaults().pad(4);root.add(new Label("MULTIPLAYER / PRIVATE DIRECTORY",skin)).colspan(4).left().row();
        button(root,"Stream Safe: "+(profile.streamSafe()?"ON":"OFF"),()->{profile.streamSafe(!profile.streamSafe());refresh();status.setText("Stream Safe "+(profile.streamSafe()?"ON":"OFF"));});button(root,"Back",this::close);button(root,"Battle view (Tab returns)",()->battleView=true);root.row();
        row("DNS/IP (masked)",address,"Port (masked)",port);row("Verified fingerprint",pin,"Room password/key",password);row("Private label",label,"Private notes",notes);
        button(root,"Connect TLS",()->join(false));button(root,"Trusted OPEN_LAN",()->join(true));button(root,"Reconnect TLS",()->reconnect());button(root,"Disconnect",()->{if(connection!=null)connection.close();matchRule.setText("HOST RULE: disconnected");fleetChecks.setItems(new String[0]);});root.row();
        root.add(matchRule).colspan(4).left().row();
        row("Fleet file",fleetFile,"Local directory",new Label("shipyard/ (read-only preview)",skin));
        button(root,"Check fleet vs host",this::checkFleet);
        button(root,"Submit fleet to host",this::submitFleet);
        root.add(new Label("Host validates both fleets before spawning; no ready-up yet",skin)).colspan(2).left().row();
        root.add(new ScrollPane(fleetChecks,skin)).height(78).growX().colspan(4).row();
        root.add(new Label("SAVED SERVERS",skin)).colspan(2);root.add(new Label("FRIENDS / NO GLOBAL PRESENCE",skin)).colspan(2).row();
        root.add(new ScrollPane(servers,skin)).height(95).growX().colspan(2);root.add(new ScrollPane(friends,skin)).height(95).growX().colspan(2).row();
        button(root,"Save / edit server",this::saveServer);button(root,"Select server",this::selectServer);button(root,"Accept friend",()->friendState(LocalProfile.ContactState.ACCEPTED));button(root,"Decline friend",()->friendState(LocalProfile.ContactState.DECLINED));root.row();
        button(root,"Favorite server",()->{var s=selectedServer();if(s!=null)profile.favoriteServer(s.id(),!s.favorite());refresh();});button(root,"Remove server",()->{var s=selectedServer();if(s!=null)profile.removeServer(s.id());refresh();});button(root,"Block friend",()->friendState(LocalProfile.ContactState.BLOCKED));button(root,"Unblock friend",()->friendState(LocalProfile.ContactState.PENDING));root.row();
        root.add(new Label("Friend card (masked)",skin));root.add(card).growX().colspan(3).row();
        button(root,"Import friend",()->{profile.importCard(card.getText(),label.getText());card.setText("");refresh();});button(root,"Private copy: confirm",()->{copyArmed=true;status.setText("Clipboard/other apps may be captured. Press Copy own card to confirm private export.");});button(root,"Copy own card",()->{if(!copyArmed)throw Hbnet.failure();copyArmed=false;Gdx.app.getClipboard().setContents(profile.identity.card());status.setText("Private card copied; clipboard/OS capture remains outside game control.");});button(root,"Remove friend",()->{var f=selectedFriend();if(f!=null)profile.removeFriend(f.id());refresh();});root.row();
        button(root,"PD: balanced",()->order(BattleOrders.PointDefense.BALANCED));button(root,"PD: hold fire",()->order(BattleOrders.PointDefense.HOLD_FIRE));button(root,"PD: anti craft",()->order(BattleOrders.PointDefense.ANTI_CRAFT));button(root,"Edit/favorite friend",()->{var f=selectedFriend();if(f!=null)profile.edit(f.id(),label.getText(),notes.getText(),!f.favorite());refresh();});root.row();
        root.add(status).colspan(4).left().row();root.add(new Label("Authenticated snapshots / no local outcomes or campaign rewards. Esc returns.",skin)).colspan(4).left();
        stage.addActor(root);refresh();Gdx.input.setInputProcessor(stage);
    }
    private TextField field(String value,int max){TextField f=new TextField(value,skin);f.setPasswordMode(true);f.setPasswordCharacter('*');f.setMaxLength(max);f.setTextFieldFilter((field,c)->c>=32&&c<=126);return f;}
    private interface Action{void run()throws Exception;}
    private void button(Table t,String text,Action action){TextButton b=new TextButton(text,skin);b.addListener(new ChangeListener(){public void changed(ChangeEvent e,Actor a){try{action.run();}catch(Exception invalid){status.setText("Action unavailable. Check private input (NET-04).");}}});t.add(b).growX();}
    private void row(String left,Actor a,String right,Actor b){root.add(new Label(left,skin));root.add(a).growX();root.add(new Label(right,skin));root.add(b).growX().row();}
    private LocalProfile.SavedServer selectedServer(){int i=servers.getSelectedIndex();return i<0||i>=profile.servers().size()?null:profile.servers().get(i);}
    private LocalProfile.Contact selectedFriend(){int i=friends.getSelectedIndex();return i<0||i>=profile.friends().size()?null:profile.friends().get(i);}
    private void refresh(){var ss=profile.servers();String[] sa=new String[ss.size()];for(int i=0;i<ss.size();i++)sa[i]=profile.serverLabel(ss.get(i),i)+(ss.get(i).favorite()?" *":"");servers.setItems(sa);var fs=profile.friends();String[] fa=new String[fs.size()];for(int i=0;i<fs.size();i++)fa[i]=profile.friendLabel(fs.get(i),i)+" / "+fs.get(i).state();friends.setItems(fa);}
    private void selectServer(){var s=selectedServer();if(s!=null){address.setText(s.host());port.setText(Integer.toString(s.port()));pin.setText(s.pin());label.setText(s.label());}}
    private void saveServer()throws Exception{var s=selectedServer();profile.server(s==null?null:s.id(),label.getText(),address.getText(),Hbnet.port(port.getText()),pin.getText());refresh();}
    private void friendState(LocalProfile.ContactState state)throws Exception{var f=selectedFriend();if(f!=null)profile.state(f.id(),state);refresh();}
    private void join(boolean lan)throws Exception{
        if(busy)return;if(connection!=null)connection.close();String host=Hbnet.host(address.getText());int p=Hbnet.port(port.getText());
        Hbnet.Client tls=lan?null:new Hbnet.Client(pin.getText(),"catastrophic-diplomacy",profile.identity,password.getText().toCharArray());joinedAddress=host;joinedPort=p;openLan=lan;secure=tls;connectAsync(host,p,tls,lan);
    }
    private void reconnect()throws Exception{if(openLan||secure==null)throw Hbnet.failure();connectAsync(joinedAddress,joinedPort,secure,false);}
    private void connectAsync(String host,int p,Hbnet.Client tls,boolean lan){
        if(busy)return;busy=true;status.setText("Connecting privately...");
        Thread t=new Thread(()->{try{BattleConnection link=lan?BattleConnection.openLan(host,p,false):new BattleConnection(host,p,tls,false);Gdx.app.postRunnable(()->{busy=false;if(closed){link.close();return;}connection=link;fleetChecks.setItems(new String[0]);
            matchRule.setText(link.matchRules==null?"HOST RULE: none published (battle-only server)":
                    "HOST RULE: Tech 0-"+link.matchRules.maxStage()+" inclusive (authoritative)");
            status.setText(lan?"OPEN_LAN: unencrypted trusted-network session":"Pinned TLS session connected");});}catch(Exception e){Gdx.app.postRunnable(()->{busy=false;status.setText("Connection unavailable (NET-04). No insecure fallback.");});}},"diplomacy-connect");t.setDaemon(true);t.start();
    }
    /** A local preview is advisory; the host always revalidates the actual fittings. */
    private String selectedFleetText()throws Exception{
        String name=fleetFile.getText().trim();
        if(!name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,120}\\.cdfleet"))
            throw new IllegalArgumentException("Select a local .cdfleet filename only");
        var source=Gdx.files.local("shipyard/"+name);
        if(!source.exists()||source.length()>120_000L)
            throw new IllegalArgumentException("Missing or oversized network fleet export (120 KB limit)");
        return source.readString("UTF-8");
    }
    private void checkFleet()throws Exception{
        BattleConnection link=connection;
        if(link==null||!link.connected()||link.matchRules==null)throw Hbnet.failure();
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        SkirmishFleet fleet=SkirmishFleetCodec.decode(selectedFleetText(),catalog);
        ArrayList<String> results=new ArrayList<>();int legal=0;
        for(int i=0;i<fleet.size();i++){
            var ship=fleet.ship(i);
            try{
                link.matchRules.requireAllowed(ship,catalog);
                results.add("LEGAL "+(i+1)+" / "+ship.hull);
                legal++;
            }catch(IllegalArgumentException barred){
                results.add("ILLEGAL "+(i+1)+" / "+ship.hull+" / "+barred.getMessage());
            }
        }
        fleetChecks.setItems(results.toArray(new String[0]));
        status.setText("Host Tech "+link.matchRules.maxStage()+": "+legal+"/"+fleet.size()+
                " ships legal. Submit to request host validation.");
    }
    private void submitFleet()throws Exception{
        BattleConnection link=connection;
        if(link==null||!link.connected()||link.matchRules==null)throw Hbnet.failure();
        String saved=selectedFleetText();
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        SkirmishFleet fleet=SkirmishFleetCodec.decode(saved,catalog);
        fleet.requireAllowed(link.matchRules,catalog); // early UI feedback; host checks again
        link.submitFleet(saved);
        status.setText("Fleet submitted for independent host validation");
    }
    private void order(BattleOrders.PointDefense pd)throws Exception{if(connection==null)throw Hbnet.failure();connection.order(pd,BattleOrders.Position.FREE);}
    void draw(){
        if(Gdx.input.isKeyJustPressed(Input.Keys.TAB))battleView=!battleView;
        if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)||Gdx.input.isKeyJustPressed(Input.Keys.BACK)){if(battleView){battleView=false;return;}close();return;}
        if(battleView&&connection!=null&&connection.snapshot()!=null){drawBattle.accept(connection.snapshot());return;}
        Gdx.gl.glClearColor(.015f,.025f,.055f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);stage.getViewport().update(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),true);stage.act(Math.min(.05f,Gdx.graphics.getDeltaTime()));stage.draw();
        BattleConnection link=connection;if(link!=null&&!link.connected())matchRule.setText("HOST RULE: disconnected");
        if(link!=null&&link.snapshot()!=null){var s=link.snapshot();status.setText("Authoritative tick "+s.tick()+" / fleet "+link.team+" / ack "+link.acknowledged()+" / "+(link.connected()?"connected":"connection ended"));}
        else if(link!=null&&link.matchRules!=null)status.setText(link.fleetStatus()+" / "+link.lobbyStatus());
    }
    boolean closed(){return closed;}
    public void close(){if(closed)return;closed=true;if(connection!=null)connection.close();if(secure!=null)secure.clear();Gdx.input.setOnscreenKeyboardVisible(false);Gdx.input.setInputProcessor(previous);stage.dispose();shapes.dispose();skin.get("pixel",Texture.class).dispose();/* shared font belongs to game */back.run();}
}
