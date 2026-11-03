package diplomacy.sim;

/** Historical detection truth belongs to photon reflection, not now. */
public final class SensorHistoricalSignaturesTest {
    private static void require(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    private static void freeze(Battle battle){
        for(Battle.Ship ship:battle.ships())
            battle.registerDiagnosticStationaryTarget(ship.id);
    }
    private static void radarCannotSeeNewStealthInPast(){
        Battle battle=new Battle(94217);
        Battle.Ship scout=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,600,100);
        freeze(battle);
        scout.sensorRange=680;
        scout.infraredRange=scout.xrayRange=scout.graviticRange=0;
        target.radarCrossSection=4;
        int one=SensorLightTimeHistory.propagationTicks(500);
        for(int t=0;t<=one;t++)battle.step();
        // The earlier wave reflected while the hull was large and exposed.
        target.radarCrossSection=.06;
        target.x=1700; // Present physics is now outside all scout ranges.
        for(int t=one+1;t<2*one;t++)battle.step();
        require(battle.hasTargetContact(scout,target,1500),
                "A real in-flight radar echo was rejected using current stealth/position");
        var fix=battle.tacticalPicture(0,scout.id).stream()
                .filter(x->x.targetId()==target.id).findFirst().orElse(null);
        require(fix!=null&&fix.x()==600&&fix.x()!=target.x&&
                (fix.channels()&Battle.CONTACT_RADAR)!=0,
                "Historical radar reflected position or signature was replaced with truth");
        for(int t=0;t<35;t++)battle.step();
        require(!battle.hasTargetContact(scout,target,1500),
                "Distant cloaked hull kept an impossible live radar solution");
        System.out.println("PASS delayed RCS/range uses real reflected profile, not current hull truth");
    }
    private static void opticalCloakDoesNotRewriteOldPhotons(){
        Battle battle=new Battle(94223);
        Battle.Ship scout=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,390,100);
        freeze(battle);
        scout.sensorRange=scout.infraredRange=scout.xrayRange=
                scout.graviticRange=0;
        target.power=100;
        target.opticalSignature=1;
        int light=SensorLightTimeHistory.propagationTicks(290);
        for(int t=0;t<light;t++)battle.step();
        require(battle.hasTargetContact(scout,target,290),
                "Ordinary visual silhouette invisible after light arrived");
        target.opticalSignature=.02;
        target.ecmCondition=1;
        require(battle.hasTargetContact(scout,target,290),
                "Toggling an optical cloak erased already travelling photons");
        for(int t=0;t<light+4;t++)battle.step();
        require(!battle.hasTargetContact(scout,target,290),
                "Cloaked target remained visible after its new signature arrived");
        System.out.println("PASS optical cloak timing retains older light without future truth");
    }
    private static void installedRadarCannotEmitBeforeInstallation(){
        Battle battle=new Battle(94229);
        Battle.Ship scout=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,600,100);
        freeze(battle);
        scout.sensorRange=0;
        scout.infraredRange=scout.xrayRange=scout.graviticRange=0;
        for(int t=0;t<12;t++)battle.step();
        scout.sensorRange=850;
        battle.step();
        require(!battle.hasTargetContact(scout,target,500),
                "New sensor hardware emitted radar echoes before installation");
        int echo=2*SensorLightTimeHistory.propagationTicks(500);
        for(int t=1;t<echo;t++){
            battle.step();
            require(!battle.hasTargetContact(scout,target,500),
                    "Retroactively acquired radar contact using uninstalled emitter");
        }
        battle.step();
        require(battle.hasTargetContact(scout,target,500),
                "Newly powered and fitted scanner failed at actual echo arrival");
        System.out.println("PASS active sensor fitting cannot create past radar illumination");
    }
    public static void main(String[] args){
        radarCannotSeeNewStealthInPast();
        opticalCloakDoesNotRewriteOldPhotons();
        installedRadarCannotEmitBeforeInstallation();
        System.out.println("PASS temporal RCS, optical cloak, movement and sender hardware profiles");
    }
}
