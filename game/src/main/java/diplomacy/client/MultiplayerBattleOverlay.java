package diplomacy.client;

/** Pure presentation wording for the authoritative multiplayer battle overlay. */
final class MultiplayerBattleOverlay {
    private MultiplayerBattleOverlay(){}

    static String status(String terminal,boolean connected,int team,long tick){
        if(tick<0||team< -1||team>1)
            throw new IllegalArgumentException("Invalid authoritative battle status");
        if(terminal!=null&&!terminal.isEmpty()){
            String outcome=switch(terminal){
                case "LEFT_VICTORY" -> team==0?"VICTORY":team==1?"DEFEAT":"LEFT VICTORY";
                case "RIGHT_VICTORY" -> team==1?"VICTORY":team==0?"DEFEAT":"RIGHT VICTORY";
                case "STALEMATE" -> "STALEMATE";
                case "TIME_LIMIT" -> "TIME LIMIT";
                case "MUTUAL_DESTRUCTION" -> "MUTUAL DESTRUCTION";
                case "NO_FIELD_CONTROL" -> "NO FIELD CONTROL";
                default -> throw new IllegalArgumentException("Unknown terminal outcome");
            };
            return outcome+"  /  FINAL AUTHORITATIVE TICK "+tick+
                    "  /  RETURN: TAB / BACK";
        }
        if(!connected)
            return "CONNECTION ENDED  /  LAST AUTHORITATIVE FRAME TICK "+tick+
                    "  /  RETURN: TAB / BACK";
        String fleet=team<0?"SPECTATOR":"FLEET "+team;
        return "CONNECTED  /  "+fleet+"  /  AUTHORITATIVE TICK "+tick+
                "  /  RETURN: TAB / BACK";
    }
}
