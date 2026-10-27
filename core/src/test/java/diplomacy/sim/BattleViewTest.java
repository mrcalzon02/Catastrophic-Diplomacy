package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Camera algebra does not depend on graphics hardware or desktop windowing. */
public final class BattleViewTest {
    private static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    private static void assertVisible(Battle b,String label){
        BattleView.Frame frame=BattleView.of(b);
        check(frame.zoom()>=1&&Double.isFinite(frame.zoom()),label+" invalid camera scale");
        int alive=0;
        for(Battle.Ship s:b.ships())if(s.alive()){
            double width=14*s.visualScale()/frame.zoom();
            double x=frame.screenX(s.x),y=frame.screenY(s.y);
            check(x-width>=25&&x+width<=1255&&y-width>=65&&y+width<=649,
                    label+" ship clipped at "+x+","+y+" zoom="+frame.zoom());
            alive++;
        }
        check(alive==b.alive(0)+b.alive(1),label+" lost live ship count");
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        var corvette=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        var capital=Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.DREADNOUGHT);
        for(int size=1;size<=200;size++){
            Battle trial=Battle.duel(8088L,size,corvette,capital,catalog);
            assertVisible(trial,"deployment "+size+"v"+size);
        }
        Battle many=Battle.duel(299L,200,corvette,capital,catalog);
        for(int tick=0;tick<750;tick++){
            many.step();
            if(tick%10==0)assertVisible(many,"moving 200v200 at tick "+tick);
        }
        check(many.hitCount>0,"visual framing tracked live battle");
        System.out.println("PASS dynamic battle camera: every scale 1v1..200v200; 750 moving combat ticks; no living hull clipped under HUD");
    }
}
