package diplomacy.client;

/** Native menu options must have matching mouse/touch bounds and no dead tabs. */
public final class OptionsTabsTest {
    private static void eq(int actual,int expected,String context){
        if(actual!=expected)throw new AssertionError(context+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args){
        for(int tab=0;tab<GameMenuPanel.OPTION_TABS.length;tab++){
            eq(GameMenuPanel.optionsTabHit(151+tab*248,580),100+tab,"tab "+tab);
            eq(GameMenuPanel.optionsPageAction(151+tab*248,580,0,true),100+tab,"tab switch "+tab);
            eq(GameMenuPanel.optionsPageAction(620,75,tab,true),6,"back "+tab);
            eq(GameMenuPanel.optionsPageAction(640,534,tab,true),-1,"content top gutter "+tab);
            eq(GameMenuPanel.optionsPageAction(45,280,tab,true),-1,"content left gutter "+tab);
        }
        eq(GameMenuPanel.optionsPageAction(125,455,0,true),7,"voice decrease");
        eq(GameMenuPanel.optionsPageAction(530,455,0,true),8,"voice increase");
        eq(GameMenuPanel.optionsPageAction(336,455,0,true),-1,"voice readout inert");
        eq(GameMenuPanel.optionsPageAction(730,455,0,true),21,"music");
        eq(GameMenuPanel.optionsPageAction(160,350,0,true),22,"effects");
        eq(GameMenuPanel.optionsPageAction(730,350,0,true),23,"soundtrack");
        eq(GameMenuPanel.optionsPageAction(160,455,1,true),20,"slow");
        eq(GameMenuPanel.optionsPageAction(730,455,1,true),21,"fast");
        eq(GameMenuPanel.optionsPageAction(160,350,1,true),22,"weather");
        eq(GameMenuPanel.optionsPageAction(160,455,2,true),20,"fps");
        eq(GameMenuPanel.optionsPageAction(730,455,2,true),21,"hud");
        eq(GameMenuPanel.optionsPageAction(160,350,2,true),22,"tactical grid");
        eq(GameMenuPanel.optionsPageAction(735,350,2,true),23,"grid contrast");
        if(!"LOW".equals(GameMenuPanel.gridContrastName(0)) ||
                !"MEDIUM".equals(GameMenuPanel.gridContrastName(1)) ||
                !"HIGH".equals(GameMenuPanel.gridContrastName(2)))
            throw new AssertionError("Grid contrast labels");
        eq(GameMenuPanel.optionsPageAction(160,455,3,true),20,"adaptive");
        eq(GameMenuPanel.optionsPageAction(730,455,3,true),21,"capture");
        eq(GameMenuPanel.optionsPageAction(160,350,3,true),22,"export");
        eq(GameMenuPanel.optionsPageAction(160,455,4,false),-2,"disabled updates");
        eq(GameMenuPanel.optionsPageAction(160,455,4,true),20,"active updates");
        eq(GameMenuPanel.optionsPageAction(730,455,4,false),21,"dev always available");
        eq(GameMenuPanel.optionsPageAction(735,250,4,true),-1,"empty content");
        System.out.println("PASS five tab routes, disabled updater, audio arrows, all options, back and gutters");
    }
}
