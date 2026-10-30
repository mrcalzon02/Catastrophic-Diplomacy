package diplomacy.client;

import com.badlogic.gdx.graphics.OrthographicCamera;

/**
 * Conservative camera-space rejection for render-only geometry.
 * Bounds include objects intersecting screen edges, not just their centers.
 * Only graphics are culled. Combat AI, collisions and audio remain global.
 */
final class CombatViewBounds {
    final double left,right,bottom,top;
    CombatViewBounds(double cx,double cy,double width,double height){
        if(!Double.isFinite(cx)||!Double.isFinite(cy)||
                !(width>0)||!(height>0)||!Double.isFinite(width)||!Double.isFinite(height))
            throw new IllegalArgumentException("Invalid camera dimensions");
        left=cx-width*.5;right=cx+width*.5;
        bottom=cy-height*.5;top=cy+height*.5;
    }
    static CombatViewBounds from(OrthographicCamera camera){
        return new CombatViewBounds(camera.position.x,camera.position.y,
                camera.viewportWidth*camera.zoom,camera.viewportHeight*camera.zoom);
    }
    boolean circle(double x,double y,double radius){
        return Double.isFinite(x)&&Double.isFinite(y)&&
                Double.isFinite(radius)&&radius>=0&&
                x+radius>=left&&x-radius<=right&&
                y+radius>=bottom&&y-radius<=top;
    }
    /** Conservative segment-AABB overlap: never erases long beams crossing view. */
    boolean segment(double x1,double y1,double x2,double y2,double thickness){
        if(!Double.isFinite(x1)||!Double.isFinite(y1)||
           !Double.isFinite(x2)||!Double.isFinite(y2)||
           !Double.isFinite(thickness)||thickness<0)return false;
        return Math.max(x1,x2)+thickness>=left&&
                Math.min(x1,x2)-thickness<=right&&
                Math.max(y1,y2)+thickness>=bottom&&
                Math.min(y1,y2)-thickness<=top;
    }
    /** Hull imports can be wider than their nominal height; don't clip tips. */
    boolean ship(BattleRenderPoses.Pose position,double visualScale){
        return circle(position.x(),position.y(),80*Math.max(1,visualScale));
    }
}
