package diplomacy.sim;

import java.lang.reflect.Field;
import java.util.List;

/** Headless maximum-fleet, movement and physical projectile lifecycle checks. */
public final class BattleArenaLimitsTest {
    private BattleArenaLimitsTest(){}
    private static void ok(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    private static void rejected(Runnable action,String message){
        try{action.run();}catch(IllegalArgumentException correct){return;}
        throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked")
    private static void inject(Battle b,String field,Object projectile)throws Exception{
        Field member=Battle.class.getDeclaredField(field);
        member.setAccessible(true);
        ((List<Object>)member.get(b)).add(projectile);
    }
    private static Battle stationary(long seed){
        Battle b=new Battle(seed);
        Battle.Ship a=b.addShip(0,Battle.Faction.TERRAN,100,300);
        Battle.Ship z=b.addShip(1,Battle.Faction.DRAXIAN,500,300);
        b.registerDiagnosticStationaryTarget(a.id);
        b.registerDiagnosticStationaryTarget(z.id);
        return b;
    }
    private static void addSix(Battle b,long start,double x,double y)throws Exception{
        Battle.Ship a=b.ships().get(0),z=b.ships().get(1);
        Battle.Missile m=new Battle.Missile(101,start,a.id,0,z.id,x,y,500,0,20,
                false,ProjectileModel.standard(ProjectileModel.Kind.MISSILE),1);
        m.fuel=999;m.seekerLocked=false;m.lastKnownX=BattleArena.MAX_X+1000;
        m.lastKnownY=y;
        inject(b,"missiles",m);
        Battle.Ballistic shell=new Battle.Ballistic(102,start,a.id,0,z.id,x,y,
                0,10,ProjectileModel.standard(ProjectileModel.Kind.KINETIC));
        shell.life=999;
        inject(b,"ballistics",shell);
        Battle.Rocket rocket=new Battle.Rocket(103,start,a,
                new RocketBarrage.Rack(0,RocketBarrage.Size.SMALL,1,1,1),
                z.id,x,y,0);
        rocket.life=999;
        inject(b,"rockets",rocket);
        RotaryPointDefense.Round rotary=new RotaryPointDefense.Round(104,start,
                a.id,0,0,x,y,0,3,220);
        rotary.life=999;
        inject(b,"rotaryRounds",rotary);
        // A synthetic arena-lifetime fixture supplies its own reservation
        // metadata; real RPD shots use the actual tracked incoming body ID.
        RailPointDefense.Penetrator rail=new RailPointDefense.Penetrator(105,start,
                0,a.id,0,x,y,0,200,3,105,3);
        rail.life=999;
        inject(b,"railPenetrators",rail);
        // Physical flak has independent world-space travel, arena bounds
        // and finite lifetime. It can neither burst outside the world nor
        // remain as a forever-pending invisible scheduled explosion.
        Battle.FlakRound flak=new Battle.FlakRound(0,a.id,0,106,0,
                x,y,x+300,y,20,6);
        if(start<0)flak.life=-100;
        inject(b,"pendingFlak",flak);
    }
    private static void noProjectiles(Battle b,String context){
        ok(b.missiles().isEmpty(),context+" guided missile survived");
        ok(b.rockets().isEmpty(),context+" unguided rocket survived");
        ok(b.ballistics().isEmpty(),context+" kinetic shell survived");
        ok(b.rotaryRounds().isEmpty(),context+" rotary CIWS round survived");
        ok(b.railPenetrators().isEmpty(),context+" rail penetrator survived");
        ok(b.flakRounds().isEmpty(),context+" flak projectile survived");
    }
    public static void main(String[] args)throws Exception{
        ok(BattleArena.MAX_X-BattleArena.MIN_X==3840 &&
                BattleArena.MAX_Y-BattleArena.MIN_Y==2160,"Arena dimensions changed");
        ok(BattleArena.MIN_X<=-1000 && BattleArena.MAX_X>=2280 &&
                BattleArena.MIN_Y<=-600 && BattleArena.MAX_Y>=1320,
                "Insufficient fleet maneuvering space");
        Battle large=Battle.duel(815L,200,Battle.Faction.TERRAN,
                Battle.Faction.DRAXIAN);
        ok(large.ships().size()==400 && large.alive(0)==200 &&
                large.alive(1)==200,"200v200 must remain supported");
        for(Battle.Ship ship:large.ships())
            ok(BattleArena.contains(ship.x,ship.y),
                    "Large fleet spawned outside the fixed world");
        rejected(()->large.addShip(0,Battle.Faction.TERRAN,100,300),
                "201st ship accepted");
        rejected(()->large.spawnWing(0,Battle.Faction.TERRAN,1,100,300),
                "Oversized wing request must fail atomically");
        ok(large.ships().size()==400,"Invalid wing partially created ships");
        rejected(()->Battle.duel(816L,201,Battle.Faction.TERRAN,
                Battle.Faction.DRAXIAN),"Oversized duel accepted");
        rejected(()->new Battle(817).addShip(0,Battle.Faction.TERRAN,
                BattleArena.MAX_X+1,300),"Out-of-world spawn accepted");
        Battle drift=new Battle(818L);
        Battle.Ship wandering=drift.addShip(0,Battle.Faction.TERRAN,
                BattleArena.MAX_X-1,BattleArena.MAX_Y-1);
        Battle.Ship opponent=drift.addShip(1,Battle.Faction.DRAXIAN,100,300);
        drift.registerDiagnosticStationaryTarget(opponent.id);
        wandering.sensorRange=0;wandering.fuel=0;
        wandering.vx=1800;wandering.vy=1800;
        drift.step();
        ok(wandering.x==BattleArena.MAX_X && wandering.y==BattleArena.MAX_Y
                &&wandering.vx==0 && wandering.vy==0,
                "Ship coasted past arena border");
        Battle aged=stationary(819L);
        addSix(aged,-10000,300,400);aged.step();
        noProjectiles(aged,"Hard maximum age");
        Battle outbound=stationary(820L);
        addSix(outbound,0,BattleArena.MAX_X-1,600);outbound.step();
        noProjectiles(outbound,"World-edge crossing");
        Battle.Ballistic slow=new Battle.Ballistic(106,0,1,0,2,200,300,0,4,
                new ProjectileModel.Spec(10,1.6,.24,.54));
        ok(slow.life<=BattleArena.MAX_BALLISTIC_SECONDS,
                "Slow shells may not live indefinitely");
        Battle.Missile torpedo=new Battle.Missile(107,0,1,0,2,200,300,300,0,8,
                true,ProjectileModel.standard(ProjectileModel.Kind.TORPEDO),
                1,OrdnanceDoctrine.Payload.STANDARD,90);
        ok(torpedo.fuel<=BattleArena.MAX_GUIDED_SECONDS,
                "Cold launch exceeded maximum flight time");
        System.out.println("PASS fixed arena, 200v200, ship bounds, six projectile types and timeouts");
    }
}
