package diplomacy.shipyard;

/** Pointer-identity aware tap versus drag tracking; no Android/libGDX dependency. */
public final class TouchGesture {
    private static final float SLOP = 12f;
    private int pointer = -1;
    private float originX, originY, lastY, remaining;
    private boolean moved;
    public boolean start(int id, float x, float y) {
        if (pointer != -1) return false;
        pointer=id; originX=x; originY=y; lastY=y; remaining=0; moved=false;
        return true;
    }
    /** Number of list rows to scroll. Caller clamps within content bounds. */
    public int drag(int id, float x, float y, float rowHeight, boolean inScrollableList) {
        if (id != pointer) return 0;
        if (!moved && StrictMath.hypot(x-originX,y-originY) >= SLOP) moved=true;
        float distance=lastY-y;
        lastY=y;
        if (!moved || !inScrollableList) return 0;
        remaining += distance;
        int rows = (int)(remaining / rowHeight);
        remaining -= rows*rowHeight;
        return rows;
    }
    /** True only for an intentional tap by the original pointer. */
    public boolean end(int id, float x, float y) {
        if (id != pointer) return false;
        boolean tapped=!moved && StrictMath.hypot(x-originX,y-originY) < SLOP;
        pointer=-1; remaining=0; moved=false;
        return tapped;
    }
    public void cancel() { pointer=-1; remaining=0; moved=false; }
}
