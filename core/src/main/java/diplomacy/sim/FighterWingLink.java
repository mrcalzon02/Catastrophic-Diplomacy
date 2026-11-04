package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct fighter-to-fighter contact radio. The 60-unit bubble is shorter
 * than even the smallest fitted fighter's normal sensor range. It is NOT a
 * carrier/fleet datalink and cannot manufacture a lock or route a report.
 *
 * Every broadcast is a locally acquired, immutable historical measurement,
 * transmitted from the actual observing fighter's pose. Another fighter
 * receives it no sooner than the NEXT simulation tick and only if it was
 * within direct radio range of that recorded transmitter. No rebroadcasting,
 * no unlimited bandwidth: one report per fighter per tick; one best incoming
 * report per receiver, and it expires unless a real fresh report arrives.
 */
public final class FighterWingLink {
    private FighterWingLink(){}
    public static final double RANGE=60;
    public enum Kind { FIGHTER, SHIP }
    public record Contact(int sourceId,int team,Kind kind,int targetId,
                          long observedTick,long sentTick,
                          double senderX,double senderY,
                          double x,double y,double vx,double vy){
        public Contact {
            if(sourceId<=0||targetId<=0||kind==null||(team!=0&&team!=1)||
                    observedTick<0||sentTick!=observedTick||
                    !Double.isFinite(senderX)||!Double.isFinite(senderY)||
                    !Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !BattleArena.contains(senderX,senderY)||
                    !BattleArena.contains(x,y))
                throw new IllegalArgumentException("Invalid local fighter sensor broadcast");
        }
        public long fingerprint(){
            long h=0xcbf29ce484222325L;
            h=(h^sourceId)*0x100000001b3L;
            h=(h^team)*0x100000001b3L;
            h=(h^kind.ordinal())*0x100000001b3L;
            h=(h^targetId)*0x100000001b3L;
            h=(h^observedTick)*0x100000001b3L;
            h=(h^Double.doubleToLongBits(senderX))*0x100000001b3L;
            h=(h^Double.doubleToLongBits(senderY))*0x100000001b3L;
            h=(h^Double.doubleToLongBits(x))*0x100000001b3L;
            h=(h^Double.doubleToLongBits(y))*0x100000001b3L;
            h=(h^Double.doubleToLongBits(vx))*0x100000001b3L;
            return (h^Double.doubleToLongBits(vy))*0x100000001b3L;
        }
    }
    /** Bounded spatial index: at most the nine cells around a receiver are
     * checked. Reused buckets avoid per-craft global scanning at 200v200. */
    public static final class Network {
        private static final int COLUMNS=
                (int)StrictMath.ceil((BattleArena.MAX_X-BattleArena.MIN_X)/RANGE)+1;
        private static final int ROWS=
                (int)StrictMath.ceil((BattleArena.MAX_Y-BattleArena.MIN_Y)/RANGE)+1;
        private final List<Contact>[] cells;
        private final int[] touched;
        private int count;
        private long indexedTick=-1;
        @SuppressWarnings("unchecked")
        public Network(){
            cells=(List<Contact>[])new List[COLUMNS*ROWS*2];
            touched=new int[cells.length];
        }
        private static int col(double x){
            return (int)StrictMath.max(0,StrictMath.min(COLUMNS-1,
                    StrictMath.floor((x-BattleArena.MIN_X)/RANGE)));
        }
        private static int row(double y){
            return (int)StrictMath.max(0,StrictMath.min(ROWS-1,
                    StrictMath.floor((y-BattleArena.MIN_Y)/RANGE)));
        }
        private static int key(int team,int cx,int cy){
            return team*COLUMNS*ROWS+cy*COLUMNS+cx;
        }
        public void index(List<Contact> previous,long now){
            if(previous==null||now<0||now<indexedTick)
                throw new IllegalArgumentException("Invalid wing radio exchange tick");
            for(int i=0;i<count;i++)cells[touched[i]].clear();
            count=0;indexedTick=now;
            if(now==0)return;
            for(Contact report:previous){
                if(report==null||report.sentTick()!=now-1)continue;
                int cell=key(report.team(),col(report.senderX()),row(report.senderY()));
                if(cells[cell]==null)cells[cell]=new ArrayList<>();
                if(cells[cell].isEmpty())touched[count++]=cell;
                cells[cell].add(report);
            }
        }
        public Contact receive(int receiverId,int team,
                               double receiverX,double receiverY,long now){
            if(receiverId<=0||(team!=0&&team!=1)||now!=indexedTick||
                    !BattleArena.contains(receiverX,receiverY))
                throw new IllegalArgumentException("Invalid local wing receiver");
            int cx=col(receiverX),cy=row(receiverY);
            Contact best=null;
            double nearest=Double.POSITIVE_INFINITY;
            for(int y=StrictMath.max(0,cy-1);y<=StrictMath.min(ROWS-1,cy+1);y++)
                for(int x=StrictMath.max(0,cx-1);x<=StrictMath.min(COLUMNS-1,cx+1);x++){
                    var bucket=cells[key(team,x,y)];
                    if(bucket==null||bucket.isEmpty())continue;
                    for(Contact report:bucket){
                        if(report.sourceId()==receiverId)continue;
                        double distance=StrictMath.hypot(
                                receiverX-report.senderX(),
                                receiverY-report.senderY());
                        if(distance>RANGE)continue;
                        // Prefer a fighter threat report over an ordinary ship
                        // cue, then the nearest authenticated wingman.
                        if(best==null||
                                report.kind()==Kind.FIGHTER&&best.kind()==Kind.SHIP||
                                report.kind()==best.kind()&&(
                                  distance<nearest-1e-9||
                                  StrictMath.abs(distance-nearest)<1e-9&&
                                  report.sourceId()<best.sourceId())){
                            best=report;nearest=distance;
                        }
                    }
                }
            return best;
        }
    }
}
