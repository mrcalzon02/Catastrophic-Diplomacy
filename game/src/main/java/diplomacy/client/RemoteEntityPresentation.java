package diplomacy.client;

import diplomacy.net.BattleConnection;
import java.util.ArrayList;
import java.util.List;

/** Immutable typed host snapshot partition used by network presentation only. */
final class RemoteEntityPresentation {
    record Frame(List<BattleConnection.ShipEntity> ships,
                 List<BattleConnection.MissileEntity> missiles,
                 List<BattleConnection.BallisticEntity> ballistics,
                 List<BattleConnection.CraftEntity> crafts) {
        Frame{
            ships=List.copyOf(ships);missiles=List.copyOf(missiles);
            ballistics=List.copyOf(ballistics);crafts=List.copyOf(crafts);
        }
    }
    private RemoteEntityPresentation(){}
    static Frame from(BattleConnection.Snapshot snapshot){
        if(snapshot==null)throw new IllegalArgumentException("Snapshot required");
        ArrayList<BattleConnection.ShipEntity> ships=new ArrayList<>();
        ArrayList<BattleConnection.MissileEntity> missiles=new ArrayList<>();
        ArrayList<BattleConnection.BallisticEntity> ballistics=new ArrayList<>();
        ArrayList<BattleConnection.CraftEntity> crafts=new ArrayList<>();
        for(BattleConnection.Entity entity:snapshot.decodedEntities()){
            if(entity instanceof BattleConnection.ShipEntity value)ships.add(value);
            else if(entity instanceof BattleConnection.MissileEntity value)missiles.add(value);
            else if(entity instanceof BattleConnection.BallisticEntity value)ballistics.add(value);
            else if(entity instanceof BattleConnection.CraftEntity value)crafts.add(value);
            else throw new IllegalArgumentException("Unknown typed host entity");
        }
        return new Frame(ships,missiles,ballistics,crafts);
    }
}
