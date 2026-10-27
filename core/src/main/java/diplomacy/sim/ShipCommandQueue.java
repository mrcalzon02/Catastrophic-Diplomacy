package diplomacy.sim;

import java.util.List;
import java.util.ArrayList;

/**
 * Ordered, per-ship tactical program. Commands are executed in list order; ships
 * continue autonomous target acquisition, firing and point defense throughout.
 * The command count is not artificially capped.
 */
public final class ShipCommandQueue {
    public enum Type { MOVE, HOLD, ENGAGE }
    public record Command(Type type,double x,double y,int ticks) {
        public Command {
            if(type==null||!Double.isFinite(x)||!Double.isFinite(y)||ticks<0)
                throw new IllegalArgumentException("Invalid tactical command");
            if(type==Type.MOVE && (x<0||x>1280||y<0||y>720))
                throw new IllegalArgumentException("Waypoint outside battlefield");
            if(type!=Type.MOVE && ticks<1)
                throw new IllegalArgumentException("Timed orders must last at least one tick");
        }
        public static Command move(double x,double y){return new Command(Type.MOVE,x,y,0);}
        public static Command hold(int ticks){return new Command(Type.HOLD,0,0,ticks);}
        public static Command engage(int ticks){return new Command(Type.ENGAGE,0,0,ticks);}
    }
    private final List<Command> commands=new ArrayList<>();
    private int index,ticksSpent;
    public ShipCommandQueue(List<Command> source) {
        if(source==null||source.stream().anyMatch(x->x==null))
            throw new IllegalArgumentException("Missing ship orders");
        commands.addAll(source);
    }
    public Command current(){return index<commands.size()?commands.get(index):null;}
    public int nextIndex(){return index;}
    public int total(){return commands.size();}
    public void complete(){if(current()!=null){index++;ticksSpent=0;}}
    public boolean tickTimed(){
        Command c=current();
        if(c==null||c.type()==Type.MOVE)throw new IllegalStateException("Not a timed command");
        if(++ticksSpent>=c.ticks()){complete();return true;}
        return false;
    }
}
