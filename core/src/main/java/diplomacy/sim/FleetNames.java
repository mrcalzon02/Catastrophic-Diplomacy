package diplomacy.sim;

/** Deterministically varied names with selectable fleet traditions. */
public final class FleetNames {
    private FleetNames(){}
    // Legacy entries retain their ordinals and deterministic output for old saves.
    public enum Style { NAVAL, MYTHIC, CELESTIAL, INDUSTRIAL, ALIEN,
        IMPERIAL, FRONTIER, SCIENTIFIC, CEREMONIAL, ORGANIC, SYNTHETIC, DESIGNATION }
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
        String index=String.format(java.util.Locale.ROOT,"%03d",serial);
        if(style.ordinal()<FIRST.length){
            // Preserve previously commissioned legacy names exactly.
            String call=FIRST[style.ordinal()][i]+" "+LAST[style.ordinal()][j];
            return (style==Style.ALIEN?call:"CDS "+call)+" "+index;
        }
        return switch(style){
            case IMPERIAL -> "HIMS "+pick(new String[]{"Duchess","Emperor","Regent",
                    "Baroness","Margrave","Sovereign","Archduke","Viceroy"},i)
                    +" of "+pick(new String[]{"Ash","Halcyon","Veyr","Crownfall",
                    "Veridian","Istria","Nacre","Eldcrest"},j)+" "+index;
            case FRONTIER -> "Survey "+pick(new String[]{"Perimeter","Dawntrail",
                    "Longreach","Waypoint","Hinterland","Outrider","Farwatch","Lastlight"},i)
                    +"-"+pick(new String[]{"01","02","03","04","05","06","07","08"},j)
                    +" "+index;
            case SCIENTIFIC -> "RSV "+pick(new String[]{"Kepler","Herschel","Faraday",
                    "Curie","Sagan","Cassini","Hubble","Vera"},i)
                    +" "+pick(new String[]{"Array","Observatory","Surveyor","Laboratory",
                    "Prospector","Spectrometer","Probe","Mapper"},j)+" "+index;
            case CEREMONIAL -> "Sanctum of "+pick(new String[]{"First Light","The Vigil",
                    "Embers","Last Mercy","The Witness","Requiem","Dawn","Pilgrims"},i)
                    +" "+pick(new String[]{"I","II","III","IV","V","VI","VII","VIII"},j)
                    +" "+index;
            case ORGANIC -> pick(new String[]{"Vha","Kth","Yrr","Oss","Luum","Thae",
                    "Zhur","Myr"},i)+"'"+pick(new String[]{"brood","spore","root",
                    "mantle","carapace","tendril","husk","bloom"},j)+" "+index;
            case SYNTHETIC -> "AXIOM/"+pick(new String[]{"VX","KH","QY","PT","OM",
                    "XR","CN","TZ"},i)+"-"+pick(new String[]{"CORE","NODE","SYNC",
                    "RELAY","VECTOR","ARRAY","GATE","SHELL"},j)+"-"+index;
            case DESIGNATION -> "FG-"+String.format(java.util.Locale.ROOT,"%02d",i+10)
                    +"-"+String.format(java.util.Locale.ROOT,"%02d",j+1)+"-"+index;
            default -> throw new IllegalArgumentException("Unrecognized naming philosophy");
        };
    }
    private static String pick(String[] options,int index){return options[index];}
}
