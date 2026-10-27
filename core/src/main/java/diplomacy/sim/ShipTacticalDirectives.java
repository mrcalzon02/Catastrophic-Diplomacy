package diplomacy.sim;
import java.util.ArrayList;
import java.util.List;

/** Immutable per-ship tactics. References use deployment slots; Battle resolves runtime IDs. */
public record ShipTacticalDirectives(int speedPercent, InterceptStrategy interceptStrategy,
        DefenseScope defenseScope, FighterMission fighterMission,
        List<Integer> defendSlots, int escortSlot, int escortRange) {
    public enum InterceptStrategy { EARLY_INTERCEPT, CLOSE_IN, FASTEST_IMPACT }
    public enum DefenseScope { SELF_ONLY, PROTECTED_SHIPS, ALL_ALLIES }
    public enum FighterMission { STRIKE, SCREEN, PROTECT, RESERVE }
    public static final ShipTacticalDirectives DEFAULT=new ShipTacticalDirectives(100,
            InterceptStrategy.FASTEST_IMPACT,DefenseScope.ALL_ALLIES,FighterMission.STRIKE,
            List.of(-1,-1,-1,-1),-1,250);
    public ShipTacticalDirectives {
        if(speedPercent<25||speedPercent>100||interceptStrategy==null||defenseScope==null||
                fighterMission==null||defendSlots==null||defendSlots.size()!=4||
                escortSlot< -1||escortSlot>=200||escortRange<30||escortRange>1500)
            throw new IllegalArgumentException("Invalid tactical directives");
        defendSlots=List.copyOf(defendSlots);
        for(int i=0;i<4;i++){
            Integer slot=defendSlots.get(i);
            if(slot==null||slot< -1||slot>=200)
                throw new IllegalArgumentException("Invalid protected ship slot");
            if(slot>=0&&defendSlots.indexOf(slot)!=i)
                throw new IllegalArgumentException("Duplicate protected ship");
        }
    }
    public ShipTacticalDirectives withSpeed(int n){return new ShipTacticalDirectives(n,
            interceptStrategy,defenseScope,fighterMission,defendSlots,escortSlot,escortRange);}
    public ShipTacticalDirectives withStrategy(InterceptStrategy n){return new ShipTacticalDirectives(
            speedPercent,n,defenseScope,fighterMission,defendSlots,escortSlot,escortRange);}
    public ShipTacticalDirectives withScope(DefenseScope n){return new ShipTacticalDirectives(
            speedPercent,interceptStrategy,n,fighterMission,defendSlots,escortSlot,escortRange);}
    public ShipTacticalDirectives withFighters(FighterMission n){return new ShipTacticalDirectives(
            speedPercent,interceptStrategy,defenseScope,n,defendSlots,escortSlot,escortRange);}
    public ShipTacticalDirectives withDefendSlot(int index,int ship){
        if(index<0||index>=4)throw new IllegalArgumentException("Invalid defense assignment");
        ArrayList<Integer> changed=new ArrayList<>(defendSlots);changed.set(index,ship);
        return new ShipTacticalDirectives(speedPercent,interceptStrategy,defenseScope,
                fighterMission,changed,escortSlot,escortRange);
    }
    public ShipTacticalDirectives withEscort(int n){return new ShipTacticalDirectives(
            speedPercent,interceptStrategy,defenseScope,fighterMission,defendSlots,n,escortRange);}
    public ShipTacticalDirectives withEscortRange(int n){return new ShipTacticalDirectives(
            speedPercent,interceptStrategy,defenseScope,fighterMission,defendSlots,escortSlot,n);}
}
