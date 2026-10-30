package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.SupportDoctrine;
import diplomacy.tech.TechnologyCatalog;

/** Explicit equipment ownership and powered four-channel fleet logistics. */
public final class FleetLogisticsTest {
    private FleetLogisticsTest(){}
    private static void check(boolean valid,String label){
        if(!valid)throw new AssertionError(label);
    }
    private static int slot(Shipyard.Hull hull,Shipyard.Mount mount,int nth){
        for(var socket:hull.slots)if(socket.mount()==mount&&nth--==0)return socket.index();
        throw new IllegalArgumentException("Missing test mount "+mount);
    }
    private static Shipyard.Design fitted(TechnologyCatalog tech,String module){
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        Shipyard.Design design=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(slot(hull,Shipyard.Mount.SUPPORT,0),"BASE-TER-"+module);
        // Engineering deficiencies must be actually repaired using hardpoints.
        int reactor=3,cooler=4;
        for(int n=0;n<7 && !Shipyard.inspect(design,tech).valid();n++){
            String errors=Shipyard.inspect(design,tech).explanation();
            if(errors.contains("Insufficient reactor output")&&reactor<hull.slots.size()){
                design=design.install(reactor,"BASE-TER-REA");reactor+=3;
            }else if(errors.contains("Thermal rejection insufficient")&&cooler<hull.slots.size()){
                design=design.install(cooler,"BASE-TER-RAD");cooler+=3;
            }else throw new AssertionError("Fitted logistics must be buildable "+module+": "+errors);
        }
        check(Shipyard.inspect(design,tech).valid(),"Invalid purpose-built "+module);
        return design;
    }
    private static void damaged(Battle.Ship target){
        target.hull=target.maxHull-40;
        target.damageControl=0;target.powerRegen=0;
        target.power=0;
        target.defense.disruptShields(target.defense.totalShields());
        target.defense.impact(1,0,.5,0,85,Battle.DamageType.KINETIC);
        target.shields=target.defense.totalShields();
        target.armor=target.defense.totalArmor();
        for(var face:DirectionalDefense.Facing.values())
            target.defense.configureRecharge(face,0,120);
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog tech=TechnologyCatalog.loadDefault();
        Shipyard.Design ordinary=Shipyard.starter(Battle.Faction.TERRAN,Shipyard.Hull.DREADNOUGHT);
        var plain=Shipyard.fit(ordinary,tech);
        check(plain.support().shieldTransferRate()==0 &&
                Shipyard.supportDoctrine(ordinary,tech).equals(SupportDoctrine.NONE),
                "Starter shield is not a support projector");

        String[] direct={"HPR","APR","PWR"};
        String[] drone={"DHL","DSH","DAR","DPW"};
        String[] aura={"AHL","ASH","AAR","APW"};
        for(String code:direct){
            Shipyard.Design d=fitted(tech,code);
            SupportDoctrine doctrine=Shipyard.supportDoctrine(d,tech);
            check(doctrine.direct().size()==1&&doctrine.auras().isEmpty()
                    &&doctrine.drones().isEmpty(),"Remote beam requires one physical projector "+code);
            check(doctrine.direct().get(0).range()>400
                    &&doctrine.direct().get(0).energyPerUnit()>2.5,
                    "Long range should be inefficient "+code);
        }
        for(String code:aura){
            var system=Shipyard.supportDoctrine(fitted(tech,code),tech);
            check(system.auras().size()==1&&system.direct().isEmpty(),
                    "Aura module should not grant long-range beam "+code);
            check(system.auras().get(0).rate()<0.2,
                    "Each vessel's aura assistance must remain very small "+code);
        }
        for(String code:drone){
            var system=Shipyard.supportDoctrine(fitted(tech,code),tech);
            check(system.drones().size()==1&&system.drones().get(0).magazines()>0
                    &&system.drones().get(0).charge()>0&&system.drones().get(0).range()>500,
                    "Distant finite-charge service craft require a bay "+code);
        }
        // A remote armor/power projector must reach a genuinely distant ally,
        // and must not grant shields or hull repairs by accident.
        for(String code:direct){
            Battle battle=new Battle(7983);
            battle.spawnWing(0,fitted(tech,code),tech,1,300,300);
            battle.spawnWing(0,ordinary,tech,1,680,300);
            Battle.Ship source=battle.ships().get(0),friend=battle.ships().get(1);
            source.speed=0;friend.speed=0;source.powerRegen=0;
            damaged(friend);
            double before=switch(code){
                case "HPR" -> friend.hull;
                case "APR" -> friend.armor;
                default -> friend.power;
            };
            double afterOther=code.equals("APR")?friend.power:friend.armor;
            for(int i=0;i<36;i++)battle.step();
            check((code.equals("APR")?friend.armor:friend.power)>before,
                    "Distant fitted projector failed to restore "+code);
            check((code.equals("APR")?friend.power:friend.armor)==afterOther,
                    "Projector must not grant unrelated armor/power "+code);
            check(source.power<source.maxPower,"Remote support must consume the emitter's energy");
        }
        // One of every passive field doctrine: weak per recipient, multiple
        // actual allies and no invisible effect beyond spherical range.
        for(String code:aura){
            Battle battle=new Battle(678);
            battle.spawnWing(0,fitted(tech,code),tech,1,300,320);
            battle.spawnWing(0,ordinary,tech,1,345,320);
            battle.spawnWing(0,ordinary,tech,1,920,320);
            Battle.Ship source=battle.ships().get(0),near=battle.ships().get(1);
            Battle.Ship far=battle.ships().get(2);
            for(Battle.Ship ship:battle.ships()){ship.speed=0;ship.damageControl=0;}
            damaged(near);damaged(far);
            for(int i=0;i<31;i++)battle.step();
            check(source.auraService>0,"Fitted aura provided zero real "+code);
            check(source.auraService<2,"Aura must remain weak at individual scale "+code);
            check(far.armor<far.maxArmor&&far.hull<far.maxHull
                    &&far.power<far.maxPower&&far.shields<far.maxShields,
                    "Far ship incorrectly gained service outside spherical field");
        }
        // Physically operated hull service drone flies to a distant allied
        // vessel, exhausts finite charge, returns and costs power to rearm.
        Battle servicing=new Battle(879);
        var carrierFit=fitted(tech,"DHL");
        servicing.spawnWing(0,carrierFit,tech,1,300,300);
        servicing.spawnWing(0,ordinary,tech,1,660,300);
        Battle.Ship carrier=servicing.ships().get(0),patient=servicing.ships().get(1);
        carrier.speed=0;patient.speed=0;
        carrier.damageControl=0;patient.damageControl=0;
        patient.hull=patient.maxHull-52;
        patient.powerRegen=0;
        int startingBays=carrier.serviceDroneReserve[SupportDoctrine.Channel.HULL.ordinal()];
        check(startingBays>0,"Physical support carrier must have launch slots");
        for(int i=0;i<850;i++)servicing.step();
        check(carrier.repairDronesLaunched>0&&patient.hull>patient.maxHull-52,
                "Purpose-built service drone failed to travel and restore a remote ship");
        check(carrier.serviceDronesRearmed>0,
                "Expended service drones must return and pay for rearm, not disappear");
        check(carrier.serviceDroneReserve[SupportDoctrine.Channel.HULL.ordinal()]<=startingBays,
                "Docking may never clone a carrier's reusable drone complement");
        check(servicing.crafts().stream().filter(c->c.repair).allMatch(c->
                c.repairReserve>=0&&c.repairReserve<=c.serviceMaxCharge+1e-8),
                "Service drone charge exceeded finite onboard stores");

        System.out.println("PASS four-channel logistics: purpose-fitted remote range/cost, "+
                "independent limited drone magazines, gentle spherical auras and no free SHD relay");
    }
}
