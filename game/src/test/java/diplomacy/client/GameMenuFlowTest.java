package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.CampaignMap;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.ScenarioCatalog;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.tech.TechnologyCatalog;

/** Functional hit-target routing for the desktop/Android front-end (no graphics required). */
public final class GameMenuFlowTest {
    private static void check(boolean pass,String detail) {
        if(!pass)throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        check(Math.abs(CombatPace.DEFAULT-0.20f)<0.0001f,
                "Normal battle must start five times slower than legacy full speed");
        check(Math.abs(CombatPace.faster(CombatPace.DEFAULT)-0.40f)<0.0001f,
                "FAST must double fractional pacing, not jump to legacy 1x");
        check(Math.abs(CombatPace.slower(CombatPace.DEFAULT)-0.10f)<0.0001f,
                "SLOW must permit actual sub-1x tactical pacing");
        check(Math.abs(CombatPace.slower(CombatPace.MINIMUM)-CombatPace.MINIMUM)<0.0001f,
                "SLOW must clamp to minimum safe pace");
        check(Math.abs(CombatPace.faster(CombatPace.MAXIMUM)-CombatPace.MAXIMUM)<0.0001f,
                "FAST must retain maximum bounded simulation pace");
        check("x0.20".equals(CombatPace.label(CombatPace.DEFAULT)),
                "HUD must disclose real simulation pacing");
        // Background selection must not replace simulation-owned battle or map content.
        check("blank_armor_bulkhead".equals(GrittyMenuBackgrounds.nameFor(
                        GameMenuPanel.Screen.HOME)),
                "Home must use a text-friendly industrial plate");
        check("armored_interface".equals(GrittyMenuBackgrounds.nameFor(
                        GameMenuPanel.Screen.SQUADRONS)),
                "Squadron screens need low-detail industrial texture");
        check("armored_interface".equals(GrittyMenuBackgrounds.nameFor(
                        GameMenuPanel.Screen.CAMPAIGN)),
                "Campaign map must not use a fake pre-painted star chart");
        check(GrittyMenuBackgrounds.nameFor(GameMenuPanel.Screen.BATTLE)==null,
                "Live combat must not be obscured by illustrative art");
        // The exact coordinates that users tap must resolve to the displayed action.
        for(int i=0;i<6;i++)
            check(GameMenuPanel.homeHit(600,512-i*64)==i,"Home action "+i);
        check(GameMenuPanel.homeHit(15,650)==-1,"Off-menu tap activated an action");
        check(GameMenuPanel.Screen.valueOf("CAMPAIGN_SETUP")!=null,
                "New-campaign confirmation view is missing");
        ScenarioCatalog.Preset[] presets=ScenarioCatalog.Preset.values();
        for(int i=0;i<presets.length;i++)
            check(GameMenuPanel.skirmishHit(110,615-i*48)==i,
                    "Skirmish preset hit target "+presets[i]);
        check(GameMenuPanel.skirmishHit(700,600)==-1,"Out-of-list skirmish selection");

        Battle.Faction[] factions=Battle.Faction.values();
        int rows=(factions.length+1)/2;
        for(int i=0;i<factions.length;i++){
            float x=101+(i/rows)*570;
            float y=528-(i%rows)*62;
            check(GameMenuPanel.factionHit(x,y)==factions[i],
                    "Campaign faction hit target "+factions[i]);
        }
        check(GameMenuPanel.factionHit(1250,650)==null,"Invalid faction hit region");

        // Campaign tech-tree node selection is separated from payment.
        check(GameMenuPanel.researchAction(100,489)==100,
                "License I is not selectable on the technology tree");
        check(GameMenuPanel.researchAction(595,397)==105,
                "License VI is not selectable on the technology tree");
        check(GameMenuPanel.researchAction(101,290)==200&&
                GameMenuPanel.researchAction(635,290)==203,
                "Laboratory branches are not selectable");
        check(GameMenuPanel.researchAction(1050,60)==2&&
                GameMenuPanel.researchAction(740,60)==1&&
                GameMenuPanel.researchAction(125,60)==0,
                "Campaign research authorization/return controls are unmapped");
        check(GameMenuPanel.researchAction(790,490)==-1,
                "Research editor hits outside actual buttons");
        var budget=new CampaignProgress.State(Battle.Faction.values()[0],1,1000000,0);
        var license=CampaignBudgetQuotes.research(budget,CampaignProgress.Project.LICENSE);
        check(license.allowed()&&license.cost()==budget.nextResearchCost()&&
                        license.remaining()==budget.credits()-license.cost(),
                "License quote diverges from campaign ledger");
        check(!CampaignBudgetQuotes.research(budget,
                        CampaignProgress.Project.ACCELERATOR).allowed(),
                "Laboratory bypassed its campaign milestone requirement");
        var spent=budget.beginResearch(CampaignProgress.Project.LICENSE);
        check(spent.credits()==license.remaining()&&
                        !CampaignBudgetQuotes.research(spent,
                                CampaignProgress.Project.LICENSE).allowed(),
                "Research budget quote permits duplicate active projects");
        var broke=new CampaignProgress.State(Battle.Faction.values()[0],1,0,0);
        check(!CampaignBudgetQuotes.research(broke,
                        CampaignProgress.Project.LICENSE).allowed(),
                "Research quote did not reject insufficient funds");
        check(GameMenuPanel.Screen.valueOf("DOCK")!=null,
                "Separate campaign active/reserve dock is missing");
        check(GameMenuPanel.dockAction(88,60,0,4)==0,"Cannot exit owned fleet dock");
        check(GameMenuPanel.dockAction(1060,60,0,4)==1,"Cannot deploy owned active fleet");
        check(GameMenuPanel.dockAction(400,60,0,4)==15,
                "Campaign dock shipyard must have a real selectable control");
        check(GameMenuPanel.dockAction(620,60,0,4)==16,
                "Purchased research must have an independently clickable ledger");
        check(GameMenuPanel.Screen.valueOf("SQUADRONS")!=null,
                "Squadron management requires a functional native view");
        check(GameMenuPanel.dockAction(800,55,0,4)==18,
                "Dock squadron button is inert");
        for(int group=0;group<CampaignFleet.SQUADRON_COUNT;group++)
            check(GameMenuPanel.squadronAction(90,546-group*55,0)==100+group,
                    "Squadron row "+group+" is not selectable");
        check(GameMenuPanel.squadronAction(800,475,3)==1,
                "Assign selected ship control inert");
        check(GameMenuPanel.squadronAction(800,395,3)==2,
                "Name gallery from squadron control inert");
        check(GameMenuPanel.squadronAction(800,315,3)==3,
                "Squadron voice control inert");
        check(GameMenuPanel.squadronAction(800,239,3)==4,
                "Squadron naming action inert");
        check(GameMenuPanel.squadronAction(820,148,3)==5,
                "Activate squadron touch control missing");
        check(GameMenuPanel.squadronAction(1080,148,3)==6,
                "Reserve squadron touch control missing");
        check(GameMenuPanel.squadronAction(950,148,3)==-1&&
                GameMenuPanel.squadronAction(820,195,3)==-1,
                "Squadron mobilization buttons overlap or spill into inactive space");
        check(GameMenuPanel.squadronAction(800,395,0)==-1&&
                GameMenuPanel.squadronAction(800,315,0)==-1,
                "Inherited fleet defaults must not expose editable overrides");
        check(GameMenuPanel.squadronAction(88,60,3)==0,
                "Squadron editor exit inert");
        check(GameMenuPanel.Screen.valueOf("NAME_THEMES")!=null,
                "Naming cultures must have their own native menu");
        check(GameMenuPanel.nameThemePages()==3,"38 themes should occupy 3 pages");
        for(int ordinal=0;ordinal<diplomacy.sim.FleetNames.Style.values().length;ordinal++){
            int page=ordinal/GameMenuPanel.NAME_THEME_PAGE_SIZE;
            int slot=ordinal%GameMenuPanel.NAME_THEME_PAGE_SIZE;
            int col=slot/7,row=slot%7;
            check(GameMenuPanel.nameThemeAction(75+col*604,540-row*55,page)==100+ordinal,
                    "Naming culture "+ordinal+" is not selectable");
            check(GameMenuPanel.nameThemeAt(page,slot)==
                    diplomacy.sim.FleetNames.Style.values()[ordinal],
                    "Naming page/ordinal mismatch");
        }
        check(GameMenuPanel.nameThemeAt(2,10)==null,
                "Empty final-page cells should not render nonexistent styles");
        check(GameMenuPanel.nameThemeAction(480,62,0)==2,"Next theme page inert");
        check(GameMenuPanel.nameThemeAction(310,62,1)==1,"Previous theme page inert");
        check(GameMenuPanel.nameThemeAction(480,62,2)==-1,
                "Final theme page must not expose next");
        check(GameMenuPanel.nameThemeAction(310,62,0)==-1,
                "First theme page must not expose previous");
        check(GameMenuPanel.nameThemeAction(1000,62,2)==3,"Apply theme inert");
        check(GameMenuPanel.nameThemeAction(75,62,1)==0,"Cancel theme inert");
        check(GameMenuPanel.nameThemeAction(900,300,2)==-1,
                "Empty selector area should not accidentally select a theme");
        check(GameMenuPanel.Screen.valueOf("RESEARCH")!=null,
                "Paid research view missing");
        check(GameMenuPanel.dockAction(587,585,0,4)==2,"Previous roster page is inert");
        check(GameMenuPanel.dockAction(683,585,0,4)==3,"Next roster page is inert");
        for(int i=0;i<4;i++)
            check(GameMenuPanel.dockAction(300,545-i*43,0,4)==100+i,
                    "Owned vessel has no selectable roster row "+i);
        for(int i=0;i<10;i++)
            check(GameMenuPanel.dockAction(1000,546-i*46,0,4)==4+i,
                    "Fleet service/purchase/name control missing "+i);
        check(GameMenuPanel.dockAction(1000,546-9*46,0,4)==13,
                "Service All must be a usable button, not only a price label");
        check(GameMenuPanel.dockAction(1110,546-7*46,0,4)==17,
                "Radio voice selection must respond to a real dock button");
        check(GameMenuPanel.dockAction(1000,546-7*46,0,4)==11,
                "Name-theme selection displaced by the new radio button");
        check(GameMenuPanel.dockAction(1024,546-7*46,0,4)==-1,
                "Name-theme and radio-voice buttons must not overlap");
        check(GameMenuPanel.dockAction(1110,546-8*46,0,4)==14,
                "Manual renaming must have a separate native text-input action");
        check(GameMenuPanel.dockAction(1024,546-8*46,0,4)==-1,
                "Split rename controls must not have overlapping hit zones");
        check(GameMenuPanel.dockAction(900,135,0,4)==13,
                "Paid service-all control moved or lost its touch action");
        check(GameMenuPanel.dockAction(1100,135,0,4)==19,
                "Refit selected ship button has no native touch route");
        check(GameMenuPanel.dockAction(1024,135,0,4)==-1,
                "Refit and service-all hitboxes overlap");
        var workshop=TechnologyCatalog.loadDefault();
        var sample=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        int systemSlot=sample.placements().stream().filter(slot->
                        slot.mount()==Shipyard.Mount.SYSTEM&&sample.at(slot.index())!=null)
                .findFirst().orElseThrow().index();
        var improved=sample.withModuleVersion(systemSlot,ModuleVersions.Pattern.STANDARD,2);
        var roster=CampaignFleet.start(Battle.Faction.TERRAN,sample,workshop);
        long refitId=roster.ships().get(0).id();
        var reserve=roster.setActive(refitId,false,20);
        var licensed=new CampaignProgress.State(Battle.Faction.TERRAN,1,500000,0,2);
        var quoted=CampaignBudgetQuotes.refit(licensed,reserve,refitId,improved,workshop);
        check(quoted.allowed()&&quoted.cost()>0&&
                        quoted.remaining()==licensed.credits()-quoted.cost(),
                "Campaign refit budget misquoted commissioned reserve hull");
        check(!CampaignBudgetQuotes.refit(licensed,roster,refitId,improved,workshop).allowed(),
                "Campaign refit quote permitted active deployed hull");
        var unlicensed=new CampaignProgress.State(Battle.Faction.TERRAN,1,500000,0,0);
        check(!CampaignBudgetQuotes.refit(unlicensed,reserve,refitId,improved,workshop).allowed(),
                "Campaign refit quote bypassed hardware license");
        var poor=new CampaignProgress.State(Battle.Faction.TERRAN,1,0,0,2);
        check(!CampaignBudgetQuotes.refit(poor,reserve,refitId,improved,workshop).allowed(),
                "Campaign refit quote permitted a negative wallet");
        check(GameMenuPanel.dockAction(1000,96,0,4)==-1,
                "Service All tap must not overlap the campaign navigation");
        check(GameMenuPanel.dockAction(400,500,0,0)==-1,
                "Empty roster offers a selectable phantom ship");
        check(GameMenuPanel.Screen.valueOf("DEPLOYMENT")!=null,
                "Pre-battle deployment screen missing");
        float[][] deploymentTargets={
                {90,61},{894,452},{1151,452},{895,344},
                {1142,344},{990,242},{1076,60}};
        for(int i=0;i<deploymentTargets.length;i++){
            float[] point=deploymentTargets[i];
            check(GameMenuPanel.deploymentAction(point[0],point[1])==i,
                    "Deployment control has no functional touch target "+i);
        }
        check(GameMenuPanel.deploymentAction(605,480)==-1,
                "Deployment preview area must not trigger a button");
        check(GameMenuPanel.deploymentAction(1000,171)==7,
                "Fleet order editor has no real touch target");
        check(GameMenuPanel.Screen.valueOf("SHIP_ORDERS")!=null,
                "Per-ship order editor state missing");
        check(GameMenuPanel.ordersAction(700,580)==0,
                "Previous ship standing-orders control unavailable");
        check(GameMenuPanel.ordersAction(1160,580)==1,
                "Next ship standing-orders control unavailable");
        for(int i=0;i<13;i++){
            float y=534-i*29;
            check(GameMenuPanel.ordersAction(700,y)==2+i,
                    "Cannot reduce standing doctrine row "+i);
            check(GameMenuPanel.ordersAction(1150,y)==2+i,
                    "Cannot increase standing doctrine row "+i);
        }
        check(GameMenuPanel.ordersAction(1050,60)==30,
                "Cannot return from standing-orders editor");
        check(GameMenuPanel.ordersAction(900,580)==31,
                "Advanced orders page button is inert");
        for(int i=13;i<diplomacy.sim.ShipStandingOrders.SETTINGS.length;i++){
            float y=534-(i-13)*29;
            check(GameMenuPanel.ordersAction(700,y,1)==2+i,
                    "Cannot decrease advanced behavior "+i);
            check(GameMenuPanel.ordersAction(1150,y,1)==2+i,
                    "Cannot increase advanced behavior "+i);
        }
        check(GameMenuPanel.ordersAction(1120,505,0)==3,
                "Original doctrine page no longer handles persistent caution");
        check(GameMenuPanel.ordersAction(385,376)==-1,
                "Map selection must not alter a doctrine setting");
        float[][] groupButtons={{110,580},{240,580},{370,580},{515,580}};
        for(int i=0;i<groupButtons.length;i++)
            check(GameMenuPanel.groupAction(groupButtons[i][0],groupButtons[i][1])==i,
                    "Fleet group action has no real touch surface "+i);
        check(GameMenuPanel.groupAction(385,375)==-1,
                "Ship selection surface should not activate a group command");
        check(GameMenuPanel.groupAction(700,580)==-1,
                "Captain navigation overlaps group controls");
        var formation=new diplomacy.sim.CampaignDeployment.Loadout(8,
                diplomacy.sim.CampaignDeployment.Formation.LINE);
        for(int i=0;i<formation.ships();i++){
            var point=formation.at(i);
            check(GameMenuPanel.shipHit(GameMenuPanel.placementUiX(point.x()),
                    GameMenuPanel.placementUiY(point.y()),formation,false)==i,
                    "Ship not selectable on drag-and-place canvas: "+i);
            check(GameMenuPanel.shipHit(GameMenuPanel.ordersUiX(point.x()),
                    GameMenuPanel.ordersUiY(point.y()),formation,true)==i,
                    "Ship not selectable for persistent standing doctrine: "+i);
        }
        check(Math.abs(GameMenuPanel.placementWorldX(GameMenuPanel.placementUiX(280))-280)<.01,
                "Deployment drag projection has no accurate inverse");
        check(Math.abs(GameMenuPanel.ordersWorldY(GameMenuPanel.ordersUiY(540))-540)<.01,
                "Standing orders map projection inverse failed");

        for(int page=0;page<CampaignMap.PAGE_COUNT;page++)
            for(var node:CampaignMap.page(page))
                check(GameMenuPanel.mapHit(node.x(),node.y(),page)==node.level(),
                        "Map selection did not return the displayed mission: "+node.level());
        check(GameMenuPanel.afterActionAction(860,274)==-1,
                "Previous report page has no touch action");
        check(GameMenuPanel.afterActionAction(1060,274)==1,
                "Next report page has no touch action");
        check(GameMenuPanel.afterActionAction(700,274)==0,
                "Results report text accidentally activates navigation");
        check(GameMenuPanel.afterActionAction(700,155)==0,
                "After-action pages overlap retry/exit");
        System.out.println("PASS functional menu hit regions: six home actions, "+
                presets.length+" skirmishes, "+factions.length+" factions, 500 map nodes");
    }
}
