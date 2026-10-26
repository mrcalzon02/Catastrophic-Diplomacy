package diplomacy.sim;

/**
 * Player-authored campaign deployment applied to actual simulated ship coordinates.
 * Skirmish formations remain unchanged. Every point stays inside the friendly
 * half of the 1280x720 battlefield, including full 200-ship fleets.
 */
public final class CampaignDeployment {
    private CampaignDeployment() {}

    public enum Formation {
        LINE("Battle line"), WEDGE("Attack wedge"), ECHELON("Echelon");
        public final String title;
        Formation(String title){this.title=title;}
    }
    public record Point(double x,double y) { }
    public record Loadout(int ships, Formation formation) {
        public Loadout {
            if(ships<1||ships>200||formation==null)
                throw new IllegalArgumentException("Invalid campaign deployment");
        }
        public Loadout withShips(int change,LevelEconomy.Level mission) {
            if(mission==null)throw new IllegalArgumentException("Mission required");
            int selected=Math.max(1,Math.min(mission.maximumFleet(),ships+change));
            return new Loadout(selected,formation);
        }
        public Loadout withFormation(int steps) {
            Formation[] options=Formation.values();
            return new Loadout(ships,options[Math.floorMod(formation.ordinal()+steps,options.length)]);
        }
        public Point at(int index) {
            if(index<0||index>=ships)throw new IllegalArgumentException("Unknown deployed ship");
            // Uniform small groups stay readable; mass formations compress
            // naturally but never overlap the enemy deployment x=970.
            int columns=Math.min(20,(int)Math.ceil(Math.sqrt(ships*1.5)));
            int rows=(ships+columns-1)/columns;
            int col=index%columns, row=index/columns;
            double spacingX=Math.min(25.0,280.0/Math.max(1,rows-1));
            double spacingY=Math.min(27.0,430.0/Math.max(1,columns-1));
            double centered=col-(columns-1)*0.5;
            double x,y;
            switch(formation) {
                case LINE -> {
                    x=425-row*spacingX;
                    y=335+centered*spacingY;
                }
                case WEDGE -> {
                    x=450-row*spacingX-Math.abs(centered)*9;
                    y=335+centered*spacingY;
                }
                case ECHELON -> {
                    x=410-row*spacingX+centered*6;
                    y=335+centered*spacingY;
                }
                default -> throw new IllegalStateException("Unknown formation");
            }
            return new Point(x,y);
        }
        public void apply(Battle battle) {
            if(battle==null)throw new IllegalArgumentException("Battle required");
            int i=0;
            for(Battle.Ship ship:battle.ships())if(ship.team==0) {
                if(i>=ships)throw new IllegalArgumentException("Too many allied ships in battle");
                Point point=at(i++);
                ship.x=point.x();ship.y=point.y();
                ship.vx=0;ship.vy=0;
            }
            if(i!=ships)throw new IllegalArgumentException("Insufficient allied ships in battle");
        }
    }
    public static Loadout defaultFor(LevelEconomy.Level level) {
        if(level==null)throw new IllegalArgumentException("Mission required");
        return new Loadout(Math.min(level.maximumFleet(),4+(level.index()-1)/2),Formation.LINE);
    }
}
