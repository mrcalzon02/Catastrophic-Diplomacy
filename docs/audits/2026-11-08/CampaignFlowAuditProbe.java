package diplomacy.client;
import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import diplomacy.sim.*;
import diplomacy.shipyard.*;
import diplomacy.tech.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Scratch-only controller reproduction. Not a product test or production edit. */
public final class CampaignFlowAuditProbe {
  static TechnologyCatalog catalog;
  static Path storage;
  static final String LEDGER="campaign/authoritative-ledger-v1.dat";
  static void set(Object obj,String name,Object value)throws Exception {
    Field f=CatastrophicGame.class.getDeclaredField(name);f.setAccessible(true);f.set(obj,value);
  }
  static Object get(Object obj,String name)throws Exception {
    Field f=CatastrophicGame.class.getDeclaredField(name);f.setAccessible(true);return f.get(obj);
  }
  static Object call(Object obj,String name,Class<?>[] types,Object... args)throws Exception{
    Method m=CatastrophicGame.class.getDeclaredMethod(name,types);m.setAccessible(true);
    try{return m.invoke(obj,args);}catch(InvocationTargetException e){throw new RuntimeException(name,e.getCause());}
  }
  static void call(Object obj,String name)throws Exception{call(obj,name,new Class<?>[0]);}
  static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
  static CampaignFleet.Roster solo(){return CampaignFleet.startSolo(Battle.Faction.TERRAN,
    Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),catalog,1234567L);}
  static CatastrophicGame game(CampaignProgress.State progress,CampaignFleet.Roster fleet)throws Exception{
    var game=new CatastrophicGame();set(game,"technology",catalog);set(game,"campaign",progress);
    set(game,"campaignFleet",fleet);set(game,"discoveries",new CampaignTechnology.State(Battle.Faction.TERRAN));
    return game;
  }
  static void filesAt(Path root){
    storage=root;
    Gdx.files=(com.badlogic.gdx.Files)Proxy.newProxyInstance(CampaignFlowAuditProbe.class.getClassLoader(),new Class<?>[]{com.badlogic.gdx.Files.class},(obj,m,args)->{
      if(m.getName().equals("local"))return new FileHandle(storage.resolve((String)args[0]).toFile());
      if(m.getReturnType()==boolean.class)return true;
      if(m.getReturnType()==String.class)return storage.toString();
      return null;
    });
  }
  public static void main(String[] args)throws Exception{
    Gdx.app=(Application)Proxy.newProxyInstance(CampaignFlowAuditProbe.class.getClassLoader(),new Class<?>[]{Application.class},(obj,m,a)->{
      if(m.getName().equals("getType"))return Application.ApplicationType.Desktop;
      if(m.getName().equals("postRunnable")){((Runnable)a[0]).run();return null;}
      if(m.getReturnType()==int.class)return 0;
      if(m.getReturnType()==long.class)return 0L;
      return null;
    });
    catalog=TechnologyCatalog.loadDefault();
    Path temp=java.nio.file.Files.createTempDirectory("campaign-controller-audit-");
    try{
      var first=CampaignProgress.start(Battle.Faction.TERRAN).complete(1,1,1);
      var two=solo().purchase(Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE),catalog);
      var capGame=game(first,two);set(capGame,"selectedMission",31);set(capGame,"dockSelected",1);
      call(capGame,"dockToggle");
      var after=(CampaignFleet.Roster)get(capGame,"campaignFleet");
      require(first.available(31),"fixture mission31 available");
      require(first.unlocked()==2&&LevelEconomy.fleetCap(31)==5,"branch frontier distinct from cap");
      require(after.active().size()==1,"current dock controller denies second ship");
      require(two.setActive(two.ships().get(1).id(),true,5).active().size()==2,"same action valid with correct mission cap");
      System.out.println("REPRODUCED CF-01: mission31 available=true, unlocked=2, actualCap=5; dock keeps active=1; notice="+get(capGame,"dockNotice"));

      var retryGame=game(first,solo());set(retryGame,"screen",GameMenuPanel.Screen.RESULTS);
      set(retryGame,"playedCampaign",true);set(retryGame,"activeMission",1);
      call(retryGame,"tapFrontEnd",new Class<?>[]{float.class,float.class},480f,145f);
      require(get(retryGame,"screen")==GameMenuPanel.Screen.RESULTS,"campaign pointer Retry is no-op");
      require(get(retryGame,"battle")==null,"no replay created");
      System.out.println("REPRODUCED CF-03: actual pointer Retry handler leaves campaign screen=RESULTS, no new battle.");

      var high=new CampaignProgress.State(Battle.Faction.TERRAN,251,1000000000L,250,6,250,0,CampaignProgress.Project.NONE,0);
      var researchGame=game(high,solo());set(researchGame,"screen",GameMenuPanel.Screen.RESEARCH);
      call(researchGame,"openResearch");
      require(((Integer)get(researchGame,"selectedResearchNode"))==6,"initial license7 selection");
      call(researchGame,"requestResearch",new Class<?>[]{CampaignProgress.Project.class},CampaignProgress.Project.LICENSE);
      require(((Integer)get(researchGame,"selectedResearchNode"))==5,"authorization selects license6");
      require(get(researchGame,"armedResearch")==CampaignProgress.Project.LICENSE,"still arms next license");
      System.out.println("REPRODUCED CF-06: authorizing license7 changes selected card to license6; selectedCost="+LevelEconomy.draftResearchCost(6)+"; actualQuote="+high.nextResearchCost());

      Path bad=temp.resolve("bad-authority");java.nio.file.Files.createDirectories(bad.resolve("campaign"));
      java.nio.file.Files.writeString(bad.resolve(LEDGER),"corrupt");java.nio.file.Files.writeString(bad.resolve(LEDGER+".bak"),"corrupt");
      filesAt(bad);
      var corruptGame=game(null,null);set(corruptGame,"screen",GameMenuPanel.Screen.CAMPAIGN_SETUP);
      call(corruptGame,"restoreCampaign");
      require(get(corruptGame,"campaign")==null,"invalid authorities refuse load");
      call(corruptGame,"newCampaign");
      require(get(corruptGame,"campaign")==null&&get(corruptGame,"screen")==GameMenuPanel.Screen.CAMPAIGN_SETUP,"new-game path trapped by invalid authority");
      require(java.nio.file.Files.readString(bad.resolve(LEDGER)).equals("corrupt"),"failure leaves authority untouched");
      System.out.println("REPRODUCED CF-04: invalid primary+backup blocks both restore and actual New Campaign handler; screen=CAMPAIGN_SETUP; notice="+get(corruptGame,"dockNotice"));

      Path settle=temp.resolve("settlement");filesAt(settle);
      var initial=CampaignProgress.start(Battle.Faction.TERRAN);var roster=solo();
      var technology=new CampaignTechnology.State(Battle.Faction.TERRAN);
      CampaignLedger.commit(settle.resolve(LEDGER),new CampaignLedger.Snapshot(1,initial,roster,technology),catalog);
      java.nio.file.Files.createDirectory(settle.resolve(LEDGER+".tmp")); // deterministic local write failure
      var settleGame=game(initial,roster);set(settleGame,"ledgerGeneration",1L);
      set(settleGame,"playedCampaign",true);set(settleGame,"activeMission",1);set(settleGame,"deployedAtLaunch",1);
      var fitting=roster.active().get(0).fitting();
      var battle=Battle.duel(771L,1,fitting,Shipyard.starter(Battle.Faction.values()[1],Shipyard.Hull.CORVETTE),catalog);
      for(var ship:battle.ships())if(ship.team==0){ship.commissionedId=roster.active().get(0).id();ship.name=roster.active().get(0).name();}else ship.hull=0;
      ((List<Long>)get(settleGame,"campaignSortie")).add(roster.active().get(0).id());
      set(settleGame,"battle",battle);set(settleGame,"screen",GameMenuPanel.Screen.BATTLE);
      call(settleGame,"finishEngagement");
      require((Boolean)get(settleGame,"lastWon"),"winning fixture recognized");
      require(get(settleGame,"screen")==GameMenuPanel.Screen.RESULTS,"failed commit still results");
      require(((CampaignProgress.State)get(settleGame,"campaign")).credits()==0,"live wallet remains before settlement");
      require(((Long)get(settleGame,"lastReward"))==0,"uncommitted reward absent");
      java.nio.file.Files.delete(settle.resolve(LEDGER+".tmp")); // remedy disk issue
      call(settleGame,"leaveResults");
      require(get(settleGame,"screen")==GameMenuPanel.Screen.DOCK,"leave uses normal dock path");
      require(CampaignLedger.load(settle.resolve(LEDGER),catalog).orElseThrow().generation()==1,"exit does not retry settlement after recovery");
      System.out.println("REPRODUCED CF-02: real finishEngagement write fault -> victory Results reward0; remove fault -> Return opens dock; ledger remains generation1, earned mission not committed.");
    }finally{
      try(var paths=java.nio.file.Files.walk(temp)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){}});}
    }
  }
}
