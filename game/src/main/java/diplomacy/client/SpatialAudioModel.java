package diplomacy.client;

/** Pure renderer-independent mixing rules; never touches the simulation RNG. */
public final class SpatialAudioModel {
    public enum Layer { DETAIL, TACTICAL, CAPITAL, FLEET }
    public record Mix(float gain, float pan) {
        public boolean audible() { return gain > 0.012f; }
    }
    private SpatialAudioModel() { }
    private static float clamp(double value) {
        return (float)Math.max(0, Math.min(1, value));
    }
    public static Mix at(Layer layer, double cameraX, double cameraY, double cameraZoom,
                         double soundX, double soundY) {
        if (layer == null || !Double.isFinite(cameraX) || !Double.isFinite(cameraY)
                || !Double.isFinite(cameraZoom) || cameraZoom <= 0
                || !Double.isFinite(soundX) || !Double.isFinite(soundY))
            return new Mix(0, 0);
        double zoom = Math.max(0.12, cameraZoom);
        double horizontal = (soundX-cameraX)/(640*zoom);
        double vertical = (soundY-cameraY)/(330*zoom);
        double distance = Math.hypot(horizontal,vertical);
        float gate = switch(layer) {
            case DETAIL -> clamp((1.0-zoom)/0.58);
            case TACTICAL -> clamp((2.7-zoom)/1.8);
            case CAPITAL, FLEET -> 1f;
        };
        double range = switch(layer) {
            case DETAIL -> 0.58;
            case TACTICAL -> 1.18;
            case CAPITAL -> 1.95;
            case FLEET -> 3.2;
        };
        float near = clamp(1 - distance/range);
        float gain = gate * near * near;
        float pan = (float)Math.max(-0.85,Math.min(0.85,horizontal*0.70));
        return new Mix(gain,pan);
    }
    /** Close engine channels are physically impossible in strategic overview mode. */
    public static boolean enginesAudible(double zoom) {
        return Double.isFinite(zoom) && zoom < 0.95 && zoom > 0;
    }
    public static float fleetBedGain(int aliveShips, double zoom) {
        if(aliveShips < 4 || !Double.isFinite(zoom))return 0;
        double coverage = Math.min(1,aliveShips/48.0);
        // A broad, mixed machinery bed replaces hundreds of individual thruster channels.
        return (float)(0.13*coverage*(0.65+0.35*Math.min(1,Math.max(0,zoom))));
    }
}
