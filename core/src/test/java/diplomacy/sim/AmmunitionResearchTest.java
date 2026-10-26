package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.Set;

/** Headless contract: ammo research belongs to the physical fitted socket. */
public final class AmmunitionResearchTest {
    private AmmunitionResearchTest(){}
    private static void check(boolean test,String cause){
        if(!test)throw new AssertionError(cause);
    }
    private static void refused(Runnable command,String what){
        try{command.run();throw new AssertionError("Allowed locked ammunition: "+what);}
        catch(IllegalArgumentException expected){check(!expected.getMessage().isBlank(),"Actionable lockout required");}
    }
    private static int weapon(Shipyard.Hull hull,int number){
        for(Shipyard.Slot p:hull.slots)
            if(p.mount()==Shipyard.Mount.WEAPON && number--==0)return p.index();
        throw new IllegalArgumentException("No weapon slot");
    }
    private static Shipyard.Design design(Battle.Faction faction,String kind,Shipyard.Track track,int stage){
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        String code=Shipyard.factionId(faction);
        var result=Shipyard.starter(faction,hull).install(weapon(hull,0),"BASE-"+code+"-"+kind);
        return stage>0?result.stage(weapon(hull,0),track,stage):result;
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        for(Shipyard.Track track:Shipyard.Track.values()){
            for(int rank=0;rank<=6;rank++){
                var k=AmmunitionResearch.kinetic(track,rank);
                var m=AmmunitionResearch.warheads(Battle.DamageType.MISSILE,track,rank);
                var t=AmmunitionResearch.warheads(Battle.DamageType.TORPEDO,track,rank);
                check(k.contains(AmmunitionTable.Kinetic.STANDARD)
                        &&m.contains(AmmunitionTable.Warhead.STANDARD)
                        &&t.contains(AmmunitionTable.Warhead.STANDARD),
                        "Every fitting retains standard loads");
                if(rank<2)check(k.size()==1&&m.size()==1&&t.size()==1,
                        "Purpose-built ammo locked until research rank 2");
                if(rank==2)check(k.size()==2&&m.size()==2&&t.size()==2,
                        "Rank 2 grants a limited branch-specific specialized type");
                if(rank==6)check(k.size()==3&&m.size()==3&&t.size()==3,
                        "Rank 6 grants both branch-specific purpose rounds");
                if(rank>0){
                    check(k.containsAll(AmmunitionResearch.kinetic(track,rank-1)),
                            "Higher rank cannot revoke earlier kinetic unlocks");
                    check(m.containsAll(AmmunitionResearch.warheads(
                            Battle.DamageType.MISSILE,track,rank-1)),
                            "Higher rank cannot revoke earlier missile unlocks");
                }
            }
        }
        refused(()->AmmunitionResearch.warheads(Battle.DamageType.LASER,
                Shipyard.Track.OUTPUT,3),"Beam must not accept physical warheads");
        refused(()->AmmunitionResearch.kinetic(null,2),"Research branch required at positive rank");
        check(AmmunitionResearch.STOCK.kinetics().equals(Set.of(AmmunitionTable.Kinetic.STANDARD)),
                "Unupgraded chassis only has standard rounds");

        int exercised=0;
        for(Battle.Faction race:Battle.Faction.values()){
            for(String kind:new String[]{"KIN","MSL","TOR"}){
                var base=design(race,kind,Shipyard.Track.OUTPUT,0);
                int first=weapon(base.hull,0);
                var paid=base.stage(first,Shipyard.Track.OUTPUT,5);
                var early=base.stage(first,Shipyard.Track.OUTPUT,1);
                check(Shipyard.ammunitionAt(early,first).equals(AmmunitionResearch.STOCK),
                        "Rank 1 does not unlock specialized ammunition");
                var direct=Shipyard.ammunitionAt(paid,first);
                var aggregate=Shipyard.ammunitionAccess(paid);
                check(direct.equals(aggregate) || (kind.equals("KIN")
                        &&aggregate.kinetics().equals(direct.kinetics())),
                        "Single fitted specialized battery inherits its own unlock table");
                check(Shipyard.inspect(paid,catalog).valid(),
                        "Race's native equipment and research must be buildable: "+race+" "+kind);
                check(Shipyard.decode(Shipyard.encode(paid),catalog).at(first)
                                .stage(Shipyard.Track.OUTPUT)==5,
                        "Research unlock survives saved blueprint roundtrip");
                exercised++;
            }
        }
        check(exercised==33,"All eleven races and three physical ammunition families covered");

        var mixed=design(Battle.Faction.TERRAN,"KIN",Shipyard.Track.OUTPUT,5);
        int extra=weapon(mixed.hull,2);
        mixed=mixed.install(extra,"BASE-TER-KIN");
        check(Shipyard.ammunitionAt(mixed,weapon(mixed.hull,0)).kinetics()
                        .contains(AmmunitionTable.Kinetic.RELATIVISTIC),
                "High-rank gun socket owns exclusive research rounds");
        check(!Shipyard.ammunitionAccess(mixed).kinetics()
                        .contains(AmmunitionTable.Kinetic.RELATIVISTIC),
                "Unupgraded second gun CANNOT borrow first gun's research");
        mixed=mixed.stage(extra,Shipyard.Track.OUTPUT,5);
        check(Shipyard.ammunitionAccess(mixed).kinetics()
                        .contains(AmmunitionTable.Kinetic.RELATIVISTIC),
                "Both participating guns independently licensed advanced ammo");
        mixed=mixed.stage(extra,Shipyard.Track.OUTPUT,0)
                .stage(extra,Shipyard.Track.RESILIENCE,5);
        check(!Shipyard.ammunitionAccess(mixed).kinetics()
                        .contains(AmmunitionTable.Kinetic.RELATIVISTIC),
                "Exclusive off-tree technology cannot silently supply other gun bank");
        check(Shipyard.ammunitionAccess(mixed).kinetics()
                        .contains(AmmunitionTable.Kinetic.HIGH_EXPLOSIVE),
                "Intersection correctly retains purpose rounds independently earned by both");

        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        int missileSlot=weapon(hull,1);
        var left=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .stage(missileSlot,Shipyard.Track.EFFICIENCY,5);
        var right=Shipyard.starter(Battle.Faction.CARNELIAN,hull);
        Battle arena=Battle.duel(4417,1,left,right,catalog);
        var launcher=arena.ships().get(0);
        var target=arena.ships().get(1);
        launcher.x=210;launcher.y=300;launcher.speed=0;
        target.x=520;target.y=300;target.speed=0;target.pdRange=0;
        check(launcher.ammoAccess!=null
                        &&launcher.ammoAccess.missiles().contains(AmmunitionTable.Warhead.EMP),
                "Fitted combat ship inherits shipyard's allowed ammunition");
        refused(()->arena.setShipWarhead(launcher.id,Battle.DamageType.MISSILE,
                AmmunitionTable.Warhead.SHIELD_BREAKER),
                "Specialist/output payload may not load in Efficiency launch rack");
        arena.setShipWarhead(launcher.id,Battle.DamageType.MISSILE,AmmunitionTable.Warhead.EMP);
        arena.setShipLaunchMode(launcher.id,Battle.DamageType.MISSILE,OrdnanceDoctrine.Mode.RIPPLE);
        arena.step();
        check(arena.pendingSalvoCount()>0,"A multi-round salvo must actually be queued");
        arena.setShipWarhead(launcher.id,Battle.DamageType.MISSILE,AmmunitionTable.Warhead.STANDARD);
        for(int i=0;i<12;i++)arena.step();
        check(arena.missiles().stream().filter(m->m.sourceId==launcher.id).count()>=2,
                "Queued multi-round salvo must launch physical bodies");
        check(arena.missiles().stream().filter(m->m.sourceId==launcher.id)
                        .allMatch(m->m.warhead==AmmunitionTable.Warhead.EMP),
                "Changing next loadout may not rewrite warheads already loaded in a salvo");

        var kinetic=design(Battle.Faction.TERRAN,"KIN",Shipyard.Track.OUTPUT,5);
        Battle shells=Battle.duel(1234,1,kinetic,right,catalog);
        var gunner=shells.ships().get(0);
        gunner.x=220;gunner.y=300;gunner.speed=0;
        var victim=shells.ships().get(1);
        victim.x=500;victim.y=300;victim.speed=0;victim.pdRange=0;
        refused(()->shells.setShipKineticAmmunition(gunner.id,
                AmmunitionTable.Kinetic.ARMOR_PIERCING),
                "Off-branch AP shell on OUTPUT-only gun");
        shells.setShipKineticAmmunition(gunner.id,AmmunitionTable.Kinetic.RELATIVISTIC);
        shells.step();
        check(shells.ballistics().stream().anyMatch(b->b.sourceId==gunner.id
                        &&b.ammunition==AmmunitionTable.Kinetic.RELATIVISTIC),
                "Specialized approved ammunition must spawn real fast physical shells");

        System.out.println("PASS research-locked ammo: 11 races, 3 launcher types, 4 exclusive trees x 6 ranks, per-bank intersection, live ballistic rounds and frozen salvo warheads");
    }
}
