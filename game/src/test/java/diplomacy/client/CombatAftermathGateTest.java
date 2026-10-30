package diplomacy.client;
import diplomacy.sim.Battle;
public final class CombatAftermathGateTest {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Battle pair(){
        Battle battle=new Battle(88);
        battle.addShip(0,Battle.Faction.TERRAN,100,100);
        battle.addShip(1,Battle.Faction.UZIPT,300,100);
        return battle;
    }
    public static void main(String[] args){
        Battle battle=pair();
        CombatAftermathGate gate=new CombatAftermathGate();
        require(!gate.ready(battle,.08f,true),"not finished before kill");
        battle.ships().get(1).hull=0;
        require(!gate.ready(battle,.08f,false),"paused result must not advance");
        require(gate.elapsed()==0,"paused elapsed is zero");
        for(int i=0;i<30;i++)require(!gate.ready(battle,.08f,true),
                "keep final hull visible during early aftermath");
        for(int i=0;i<8;i++)gate.ready(battle,.08f,true);
        require(gate.ready(battle,.08f,true),"results eventually finish");
        require(!gate.ready(pair(),.08f,true),"new battle resets old timer");
        System.out.println("PASS final hull aftermath, pause fidelity and round reset");
    }
}
