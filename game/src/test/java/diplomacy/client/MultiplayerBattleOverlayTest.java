package diplomacy.client;

/** Battle overlay must distinguish terminal outcomes and disconnected last-good state. */
public final class MultiplayerBattleOverlayTest {
    private MultiplayerBattleOverlayTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args){
        String live=MultiplayerBattleOverlay.status("",true,0,42);
        check(live.contains("CONNECTED")&&live.contains("FLEET 0")&&
                        live.contains("TAB / BACK"),
                "Live battle overlay lacks visible return/status");
        String lost=MultiplayerBattleOverlay.status("",false,1,99);
        check(lost.contains("CONNECTION ENDED")&&
                        lost.contains("LAST AUTHORITATIVE FRAME"),
                "Disconnect did not preserve/explain the last frame");
        check(MultiplayerBattleOverlay.status("LEFT_VICTORY",true,0,120)
                        .startsWith("VICTORY"),
                "Winner seat not presented as victory");
        check(MultiplayerBattleOverlay.status("LEFT_VICTORY",true,1,120)
                        .startsWith("DEFEAT"),
                "Losing seat not presented as defeat");
        check(MultiplayerBattleOverlay.status("STALEMATE",true,-1,120)
                        .startsWith("STALEMATE")&&
                MultiplayerBattleOverlay.status("TIME_LIMIT",true,-1,120)
                        .startsWith("TIME LIMIT")&&
                MultiplayerBattleOverlay.status("MUTUAL_DESTRUCTION",true,-1,120)
                        .startsWith("MUTUAL DESTRUCTION")&&
                MultiplayerBattleOverlay.status("NO_FIELD_CONTROL",true,-1,120)
                        .startsWith("NO FIELD CONTROL"),
                "Draw/time-limit terminal states were conflated");
        boolean rejected=false;
        try{MultiplayerBattleOverlay.status("UNKNOWN",true,0,1);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Unknown terminal label silently displayed");
        System.out.println("PASS multiplayer battle overlay: visible return, disconnect last-frame and distinct terminal outcomes");
    }
}
