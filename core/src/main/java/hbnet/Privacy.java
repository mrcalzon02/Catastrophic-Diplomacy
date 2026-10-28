package hbnet;

/** Device presentation state only. Never use this value for authentication or authority. */
public final class Privacy {
    private Privacy(){}
    public static volatile boolean enabled=true;
    public static String friend(int index,String label){return enabled?String.format(java.util.Locale.ROOT,"Friend %02d",index+1):label;}
    /** Arbitrary peer chat may contain endpoints/keys; hide the entire transcript in safe mode. */
    public static String message(String text){return enabled?"Private message hidden (Stream Safe)":text;}
}
