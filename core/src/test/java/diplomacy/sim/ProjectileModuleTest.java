package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Headless physical-projectile and fire-control regression acceptance. */
public final class ProjectileModuleTest {
    private ProjectileModuleTest(){}
    private static void check(boolean assertion,String reason){
        if(!assertion)throw new AssertionError(reason);
    }
    private static int slot(Shipyard.Hull frame,Shipyard.Mount mount,int nth) {
        for(Shipyard.Slot position:frame.slots)
            if(position.mount()==mount && nth--==0)return position.index();
        throw new IllegalArgumentException("Missing "+mount);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Hull hull=Shipyard.Hull.DREADNOUGHT;
        int missileSlot=slot(hull,Shipyard.Mount.WEAPON,1);
        int kineticSlot=slot(hull,Shipyard.Mount.WEAPON,2);
        int torpedoSlot=slot(hull,Shipyard.Mount.WEAPON,3);
        int pointDefenseSlot=slot(hull,Shipyard.Mount.WEAPON,4);
        int sensorSlot=slot(hull,Shipyard.Mount.SYSTEM,3);
        Shipyard.Design base=Shipyard.starter(Battle.Faction.TERRAN,hull)
                .install(kineticSlot,"BASE-TER-KIN")
                .install(torpedoSlot,"BASE-TER-TOR")
                .install(pointDefenseSlot,"BASE-TER-PD")
                .install(sensorSlot,"BASE-TER-SEN");
        check(Shipyard.inspect(base,catalog).valid(),"physical projectile test loadout must fit");
        Shipyard.Fitted installed=Shipyard.fit(base,catalog);
        check(installed.kineticProjectile().speed()>installed.missileProjectile().speed(),
                "kinetic shell baseline faster than missile");
        check(installed.torpedoProjectile().health()>installed.missileProjectile().health(),
                "torpedo payload armored better than missile");
        check(installed.pointDefense().tracking()>0,"PD module must fit actual fire control");
        ProjectileModel.Spec upgradedArmor=Shipyard.projectileAt(
                base.stage(missileSlot,Shipyard.Track.RESILIENCE,2),catalog,missileSlot);
        ProjectileModel.Spec upgradedGuidance=Shipyard.projectileAt(
                base.stage(missileSlot,Shipyard.Track.SPECIALIST,2),catalog,missileSlot);
        ProjectileModel.Spec normal=Shipyard.projectileAt(base,catalog,missileSlot);
        check(upgradedArmor.health()>normal.health(),
                "RESILIENCE upgrade must increase projectile intercept health");
        check(upgradedArmor.crossSection()>normal.crossSection(),
                "armored ammunition should become slightly larger, balancing resilience");
        check(upgradedGuidance.speed()>normal.speed(),
                "SPECIALIST upgrade must change projectile speed");
        check(upgradedGuidance.signature()<normal.signature(),
                "SPECIALIST technology should reduce emitted targeting signature");
        ProjectileModel.Defense pd=Shipyard.defenseAt(base,catalog,pointDefenseSlot);
        ProjectileModel.Defense upgradedPd=Shipyard.defenseAt(
                base.stage(pointDefenseSlot,Shipyard.Track.SPECIALIST,3),catalog,pointDefenseSlot);
        check(upgradedPd.tracking()>pd.tracking(),
                "upgraded PD mount must track targets more accurately");
        double shortReach=ProjectileModel.acquisitionRange(150,1,1,pd,normal);
        double lowSignature=ProjectileModel.acquisitionRange(150,1,1,pd,
                new ProjectileModel.Spec(normal.speed(),normal.health(),0.12,0.12));
        double largeSignature=ProjectileModel.acquisitionRange(150,1,1,pd,
                new ProjectileModel.Spec(normal.speed(),normal.health(),2.1,2.1));
        check(lowSignature<shortReach&&shortReach<largeSignature,
                "detection range must depend on projectile cross section AND signature");
        check(ProjectileModel.acquisitionRange(150,0.40,1,pd,normal)<shortReach,
                "sensor damage must affect missile detection range");
        check(ProjectileModel.acquisitionRange(150,1,0.55,pd,normal)<shortReach,
                "bad weather must affect missile detection range");
        check(ProjectileModel.interceptionChance(
                new ProjectileModel.Spec(500,1,0.15,1),pd,1,1)
                <ProjectileModel.interceptionChance(
                new ProjectileModel.Spec(100,1,1.8,1),pd,1,1),
                "small/fast shells must be more difficult to track and hit");
        ProjectileModel.Defense hardenedPd=ProjectileModel.defense(1.7,1.4,1.6);
        check(hardenedPd.damage()>pd.damage()
                    && ProjectileModel.acquisitionRange(150,1,1,hardenedPd,normal)>shortReach,
                "higher output should improve PD penetration; sensors should improve detection");
        // Confirm installed module statistics are transferred to the actual world entities.
        Battle b=new Battle(244);
        b.spawnWing(0,base,catalog,1,280,330);
        b.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE),
                catalog,1,510,330);
        Battle.Ship launcher=b.ships().get(0);
        Battle.Ship target=b.ships().get(1);
        launcher.speed=target.speed=0;
        target.pdRange=0;
        check(launcher.missileProjectile.equals(installed.missileProjectile()),
                "ship's missile body must carry fitted module parameters");
        check(launcher.kineticProjectile.equals(installed.kineticProjectile()),
                "ship's ballistic payload must carry fitted module parameters");
        boolean hasMissile=false,hasShell=false,hasTorpedo=false;
        for(int t=0;t<15;t++){
            b.step();
            for(Battle.Missile m:b.missiles()){
                if(m.torpedo){
                    check(m.spec.equals(installed.torpedoProjectile()),
                            "live torpedo must inherit launch module configuration");
                    hasTorpedo=true;
                }else{
                    check(m.spec.equals(installed.missileProjectile()),
                            "live missile must inherit launch module configuration");
                    hasMissile=true;
                }
            }
            for(Battle.Ballistic shell:b.ballistics()){
                check(shell.spec.equals(installed.kineticProjectile()),
                        "live shell must inherit actual kinetic module speed, health and RCS");
                hasShell=true;
            }
        }
        check(hasMissile&&hasShell&&hasTorpedo,
                "fitted battle must spawn all three distinct physical ordnance categories");

        // The same initial physical configuration must reproduce combat deterministically.
        Battle replay=new Battle(244);
        replay.spawnWing(0,base,catalog,1,280,330);
        replay.spawnWing(1,Shipyard.starter(Battle.Faction.DRAXIAN,Shipyard.Hull.CORVETTE),
                catalog,1,510,330);
        replay.ships().get(0).speed=0;replay.ships().get(1).speed=0;
        replay.ships().get(1).pdRange=0;
        for(int t=0;t<15;t++)replay.step();
        check(b.fingerprint()==replay.fingerprint(),
                "projectile and point-defense attributes must preserve deterministic replay");
        System.out.println("PASS projectile modules: fitted speed/health/RCS/signature, sensor range, tracking, launch properties and replay");
    }
}
