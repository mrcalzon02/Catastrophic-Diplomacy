package diplomacy.sim;

/** Renderer-independent framing for the shared 1280x720 battle canvas.
 * The top 71 and bottom 50 units are reserved for HUD/control overlays.
 */
public final class BattleView {
    private BattleView() { }
    public static final double WIDTH=1280,HEIGHT=720;
    public record Frame(double centerX,double centerY,double zoom) {
        public double screenX(double worldX) { return WIDTH/2+(worldX-centerX)/zoom; }
        public double screenY(double worldY) { return HEIGHT/2+(worldY-centerY)/zoom; }
    }
    public static Frame of(Battle battle) {
        double loX=Double.POSITIVE_INFINITY,hiX=Double.NEGATIVE_INFINITY;
        double loY=Double.POSITIVE_INFINITY,hiY=Double.NEGATIVE_INFINITY;
        for(Battle.Ship s:battle.ships())if(s.alive()) {
            double radius=14*s.visualScale();
            loX=Math.min(loX,s.x-radius);hiX=Math.max(hiX,s.x+radius);
            loY=Math.min(loY,s.y-radius);hiY=Math.max(hiY,s.y+radius);
        }
        if(!Double.isFinite(loX))return new Frame(640,360,1);
        double needed=StrictMath.max(1,StrictMath.max((hiX-loX+64)/1180,(hiY-loY+85)/555));
        return new Frame((loX+hiX)/2,(loY+hiY)/2,needed);
    }
}
