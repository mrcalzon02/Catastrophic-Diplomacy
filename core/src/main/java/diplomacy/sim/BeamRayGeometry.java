package diplomacy.sim;

/** Physical, zero-duration beam/ray intersection with the CURRENT hull surface.
 * The aim direction comes from a historical target solution; only collision
 * uses physical present positions. Flight-time delivery is a later migration.
 */
public final class BeamRayGeometry {
    private BeamRayGeometry(){}
    public record Ray(double x1,double y1,double x2,double y2) { }
    public record Intersection(double fraction,double x,double y) { }
    /** Extend one immutable muzzle direction to the weapon's maximum reach. */
    public static Ray aim(double muzzleX,double muzzleY,double predictedX,
            double predictedY,double maxRange){
        if(!Double.isFinite(muzzleX)||!Double.isFinite(muzzleY)||
                !Double.isFinite(predictedX)||!Double.isFinite(predictedY)||
                !Double.isFinite(maxRange)||maxRange<=0)
            throw new IllegalArgumentException("Invalid physical beam ray");
        double dx=predictedX-muzzleX,dy=predictedY-muzzleY;
        double distance=StrictMath.hypot(dx,dy);
        if(distance<1e-9){dx=1;dy=0;distance=1;}
        return new Ray(muzzleX,muzzleY,muzzleX+dx/distance*maxRange,
                muzzleY+dy/distance*maxRange);
    }
    /** First entry into a physical circular collision silhouette, not the
     * closest center point. Grazing a hull rim counts as genuine contact. */
    public static Intersection intersect(Ray ray,double centerX,
            double centerY,double radius){
        if(ray==null||!Double.isFinite(centerX)||!Double.isFinite(centerY)||
                !Double.isFinite(radius)||radius<=0)
            throw new IllegalArgumentException("Invalid beam collision");
        double dx=ray.x2()-ray.x1(),dy=ray.y2()-ray.y1();
        double ox=ray.x1()-centerX,oy=ray.y1()-centerY;
        double lengthSq=dx*dx+dy*dy;
        if(lengthSq<=1e-12)return null;
        double b=ox*dx+oy*dy;
        double c=ox*ox+oy*oy-radius*radius;
        if(c<=0)return new Intersection(0,ray.x1(),ray.y1());
        double discriminant=b*b-lengthSq*c;
        if(discriminant<0)return null;
        double entry=(-b-StrictMath.sqrt(discriminant))/lengthSq;
        if(entry<0||entry>1)return null;
        return new Intersection(entry,ray.x1()+entry*dx,ray.y1()+entry*dy);
    }
}
