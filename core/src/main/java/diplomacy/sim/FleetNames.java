package diplomacy.sim;

/** Deterministically varied names with selectable fleet traditions. */
public final class FleetNames {
    private FleetNames(){}
    public enum Style { NAVAL, MYTHIC, CELESTIAL, INDUSTRIAL, ALIEN }
    private static final String[][] FIRST={
        {"Resolute","Indomitable","Courageous","Vanguard","Dauntless","Stalwart","Valiant","Guardian"},
        {"Aegis","Valhalla","Orion","Nemesis","Basilisk","Titan","Erebus","Helios"},
        {"Distant","Radiant","Nebula","Eventide","Stellar","Silent","Luminous","Astral"},
        {"Iron","Forge","Copper","Foundry","Hammer","Quarry","Atlas","Cinder"},
        {"Zha","Qel","Thru","Vael","Ix","Korr","Nym","Zyx"}
    };
    private static final String[][] LAST={
        {"Horizon","Victory","Watch","Promise","Defender","Sentinel","Expedition","Crown"},
        {"Spear","Oath","Fury","Aegis","Covenant","Dawn","Judgment","Shield"},
        {"Pilgrim","Horizon","Drifter","Beacon","Current","Whisper","Expanse","Eclipse"},
        {"Engine","Works","Foundry","Anvil","Carrier","Mill","Pillar","Station"},
        {"Keth","Vran","Taal","Sora","Quin","Nex","Draal","Vesh"}
    };
    public static String generate(Style style,long seed,int serial){
        if(style==null||serial<1)throw new IllegalArgumentException("Invalid name seed");
        long scrambled=(seed^(serial*0x9e3779b97f4a7c15L));
        scrambled^=scrambled>>>30;scrambled*=0xbf58476d1ce4e5b9L;
        scrambled^=scrambled>>>27;scrambled*=0x94d049bb133111ebL;
        scrambled^=scrambled>>>31;
        int i=(int)Math.floorMod(scrambled,8),j=(int)Math.floorMod(scrambled>>>8,8);
        String call=FIRST[style.ordinal()][i]+" "+LAST[style.ordinal()][j];
        return style==Style.ALIEN?call:"CDS "+call;
    }
}
