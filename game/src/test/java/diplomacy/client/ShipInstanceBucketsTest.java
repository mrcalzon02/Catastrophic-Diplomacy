package diplomacy.client;

import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;

/** Headless oracle for preserved fallback rendering order and sprite exclusion. */
public final class ShipInstanceBucketsTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args){
        Battle battle=new Battle(71L);
        Battle.Ship first=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship second=battle.addShip(0,Battle.Faction.TERRAN,110,100);
        Battle.Ship third=battle.addShip(0,Battle.Faction.TERRAN,120,100);
        Battle.Ship fourth=battle.addShip(1,Battle.Faction.DRAXIAN,300,100);
        Battle.Ship art=battle.addShip(1,Battle.Faction.TERRAN,310,100);
        third.hullClass=Shipyard.Hull.FRIGATE;
        ShipInstanceBuckets buckets=new ShipInstanceBuckets();
        int[] checks={0};
        buckets.collect(battle,ship->{checks[0]++;return ship==art;});
        check(checks[0]==5,"sprite eligibility must be checked once per live ship");
        var corvettes=buckets.group(Battle.Faction.TERRAN,Shipyard.Hull.CORVETTE);
        check(corvettes.size()==2&&corvettes.get(0)==first&&corvettes.get(1)==second,
                "original ship insertion order must survive batching");
        check(buckets.group(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE).get(0)==third,
                "different hull class was merged with corvettes");
        check(buckets.group(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE).get(0)==fourth,
                "different faction was merged");
        check(!corvettes.contains(art),"sprite-equipped ship was drawn twice");
        buckets.collect(battle,ship->false);
        check(corvettes.size()==3&&corvettes.get(2)==art,
                "collect must not retain old sprite visibility decisions");
        Battle next=new Battle(73L);
        next.addShip(0,Battle.Faction.TERRAN,0,0);
        buckets.collect(next,ship->false);
        check(corvettes.size()==1&&
                buckets.group(Battle.Faction.TERRAN,Shipyard.Hull.FRIGATE).isEmpty()&&
                buckets.group(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE).isEmpty(),
                "new battle must discard prior battle ship references");
        System.out.println("PASS single-pass hull grouping, exact order, sprite exclusion, and reset");
    }
}
