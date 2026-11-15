package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Tests real live SHR / TRK-LNK / FCM command-picture routes.
 * Graphics are deliberately irrelevant to whether a physical observation
 * exists. No tactical query may mutate combat state or create a weapon lock.
 */
public final class SensorTacticalPictureTest {
    private SensorTacticalPictureTest(){}
    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount type,int nth){
        for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==type&&nth--==0)return slot.index();
        throw new AssertionError("No "+type+" socket");
    }
    private static Shipyard.Network network(TechnologyCatalog catalog,
            String... families){
        Shipyard.Design design=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int system=0,support=0;
        for(String family:families){
            Shipyard.Mount type=family.equals("FCM")?
                    Shipyard.Mount.SUPPORT:Shipyard.Mount.SYSTEM;
            int next=type==Shipyard.Mount.SUPPORT?support++:system++;
            design=design.install(socket(design.hull,type,next),
                    "BASE-"+Shipyard.factionId(design.faction)+"-"+family);
        }
        require(Shipyard.inspect(design,catalog).valid(),
                "Actual fitting rejected "+java.util.Arrays.toString(families));
        return Shipyard.fit(design,catalog).network();
    }
    private static Battle.TacticalContact report(Battle battle,int viewer,int id){
        return battle.tacticalPicture(0,viewer).stream()
                .filter(c->c.targetId()==id).findFirst().orElse(null);
    }
    private static void stationary(Battle battle){
        for(Battle.Ship ship:battle.ships()){
            ship.power=100;
            battle.registerDiagnosticStationaryTarget(ship.id);
        }
    }
    private static void sharing(TechnologyCatalog catalog){
        Battle b=new Battle(56771);
        b.configureWeather(SpaceWeather.forLevel(1));
        Battle.Ship scout=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship viewer=b.addShip(0,Battle.Faction.TERRAN,1300,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,600,100);
        stationary(b);
        scout.sensorRange=700;
        viewer.sensorRange=0;
        viewer.infraredRange=viewer.xrayRange=viewer.graviticRange=0;
        b.step();
        require(report(b,viewer.id,target.id)==null,
                "Unlinked captain received an arbitrary fleet-wide sensor picture");
        scout.network=network(catalog,"SHR");
        for(int i=0;i<20;i++)b.step(); // Echo and 1,200-world-unit relay hop.
        Battle.TacticalContact observed=report(b,viewer.id,target.id);
        require(observed!=null&&!observed.stale()&&observed.relayed()
                        &&observed.sourceId()==scout.id
                        &&(observed.channels()&Battle.CONTACT_RADAR)!=0,
                "Powered SHR must supply a real originating radar report");
        require(observed.x()==target.x&&observed.y()==target.y&&
                        observed.observedTick()<b.tick&&
                        observed.emittedTick()<observed.observedTick()&&
                        observed.receivedTick()==b.tick,
                "Radar echo omitted physical emission/reflection/receipt timestamps");
        long signature=b.fingerprint();
        int known=viewer.knownTracks.size();
        b.tacticalPicture(0,viewer.id);
        require(b.fingerprint()==signature&&viewer.knownTracks.size()==known,
                "Tactical UI mutated authoritative weapon state or history");
        target.x+=8; // Fresh hidden target location is NOT the reflected fix.
        b.step();
        Battle.TacticalContact delayed=report(b,viewer.id,target.id);
        require(delayed!=null&&delayed.x()==600&&delayed.x()!=target.x&&
                        delayed.observedTick()<b.tick,
                "Moving enemy's current physics state leaked into a distant radar report");
        scout.power=0;
        require(report(b,viewer.id,target.id)==null,
                "Unpowered sender continued supplying a live track");
        System.out.println("PASS SHR relay provenance and loss-of-power cutover");
    }
    /** A newly powered transmitter cannot rebroadcast events it never saw.
     * Historical enemy snapshots belong to physics, not the scout's memory. */
    private static void lateScoutPowerOn(TechnologyCatalog catalog){
        Battle battle=new Battle(56772);
        Battle.Ship scout=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship captain=battle.addShip(0,Battle.Faction.TERRAN,1300,100);
        Battle.Ship target=battle.addShip(1,Battle.Faction.DRAXIAN,600,100);
        stationary(battle);
        scout.network=network(catalog,"SHR");
        scout.sensorRange=700;
        captain.sensorRange=captain.infraredRange=captain.xrayRange=
                captain.graviticRange=0;
        scout.powerRegen=0;scout.power=0;
        for(int i=0;i<15;i++)battle.step();
        require(report(battle,captain.id,target.id)==null,
                "Unpowered scout manufactured a historical packet");
        scout.power=100;
        battle.step();
        require(report(battle,captain.id,target.id)==null,
                "Newly powered transmitter sent a past echo it never observed");
        boolean received=false;
        for(int i=0;i<25;i++){
            battle.step();
            if(report(battle,captain.id,target.id)!=null){received=true;break;}
        }
        require(received,"Actual late scout report failed after echo and relay travel");
        System.out.println("PASS no retroactive scout acquisition; only genuinely sent delayed packets");
    }

    private static void historical(TechnologyCatalog catalog){
        Battle b=new Battle(56773);
        Battle.Ship scout=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship viewer=b.addShip(0,Battle.Faction.TERRAN,-250,100);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,650,100);
        stationary(b);
        scout.network=network(catalog,"TRK","LNK");
        viewer.network=network(catalog,"LNK");
        scout.sensorRange=700;viewer.sensorRange=0;
        int roundTrip=2*SensorLightTimeHistory.propagationTicks(550);
        int link=SensorLightTimeHistory.propagationTicks(350);
        for(int i=0;i<roundTrip+link-1;i++)b.step();
        require(report(b,viewer.id,target.id)==null,
                "Unreceived LNK history teleported through the fleet");
        require(b.newestValidTrack(viewer)==null,
                "A remote captain navigated on a track packet still in flight");
        b.step();
        require(report(b,viewer.id,target.id)!=null&&
                        b.newestValidTrack(viewer)!=null,
                "LNK viewer did not receive actual tracking packet after transit");
        target.x=1650;
        b.step();
        Battle.TacticalContact historical=report(b,viewer.id,target.id);
        require(historical!=null&&historical.stale()&&historical.relayed()
                        &&historical.channels()==0&&historical.x()==650,
                "TRK/LNK memory leaked current target position or live channels");
        require(historical.observedTick()<b.tick,
                "Stale report falsely marked as freshly scanned");
        require(!b.hasTargetContact(viewer,target,
                        StrictMath.hypot(target.x-viewer.x,target.y-viewer.y)),
                "Stale display track secretly granted a weapon lock");
        scout.power=0;
        require(report(b,viewer.id,target.id)==null,
                "Dead LNK transmitter continued leaking recorded contacts");
        scout.power=100;
        viewer.power=0;
        require(report(b,viewer.id,target.id)==null,
                "Unpowered LNK receiver continued reading shared history");
        viewer.power=100;
        viewer.network=network(catalog,"TRK");
        require(report(b,viewer.id,target.id)==null,
                "TRK processor without a fitted LNK inherited a free relay");
        System.out.println("PASS TRK/LNK stale coordinates, expiry topology and no-lock guarantee");
    }
    private static void optical(TechnologyCatalog catalog){
        Battle b=new Battle(56775);
        Battle.Ship viewer=b.addShip(0,Battle.Faction.TERRAN,100,300);
        Battle.Ship scout=b.addShip(0,Battle.Faction.TERRAN,930,300);
        Battle.Ship target=b.addShip(1,Battle.Faction.DRAXIAN,1000,300);
        stationary(b);
        viewer.sensorRange=viewer.infraredRange=viewer.xrayRange=
                viewer.graviticRange=0;
        scout.sensorRange=0;
        scout.network=network(catalog,"SHR");
        for(int i=0;i<9;i++)b.step(); // One-way optical plus link transmission.
        Battle.TacticalContact observation=report(b,viewer.id,target.id);
        require(observation!=null&&!observation.stale()
                        &&observation.relayed()
                        &&(observation.channels()&Battle.CONTACT_OPTICAL)!=0,
                "Optical scout relay missing");
        require(b.directSensorChannels(viewer,target,900)==0,
                "Remote optical contact somehow changed viewer's local acuity");
        System.out.println("PASS remotely observed optical return stays marked relayed");
    }
    private static void command(TechnologyCatalog catalog){
        Battle b=new Battle(56779);
        Battle.Ship dummy=b.addShip(0,Battle.Faction.TERRAN,-300,100);
        Battle.Ship flagship=b.addShip(0,Battle.Faction.TERRAN,600,100);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.DRAXIAN,700,100);
        stationary(b);
        dummy.sensorRange=0;
        flagship.network=network(catalog,"FCM");
        b.step();
        List<Battle.TacticalContact> chosen=b.tacticalPicture(0);
        require(chosen.stream().anyMatch(x->x.targetId()==hostile.id
                        &&x.sourceId()==flagship.id&&!x.relayed()),
                "Fleet command module not selected as default display anchor");
        flagship.power=0;
        require(b.tacticalPicture(0).stream().noneMatch(x->x.targetId()==hostile.id),
                "Dead command core continued controlling the sensor picture");
        System.out.println("PASS default fitted command-anchor selection and power fallback");
    }
    /** Two physically acquired fixes cannot reveal current hidden state. */
    private static void fusion(TechnologyCatalog catalog){
        Battle b=new Battle(56780);
        b.configureWeather(SpaceWeather.forLevel(1));
        Battle.Ship command=b.addShip(0,Battle.Faction.TERRAN,600,100);
        Battle.Ship scoutA=b.addShip(0,Battle.Faction.TERRAN,550,100);
        Battle.Ship scoutB=b.addShip(0,Battle.Faction.TERRAN,580,120);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.DRAXIAN,750,100);
        stationary(b);
        command.network=network(catalog,"FCM");
        scoutA.network=network(catalog,"SHR");
        scoutB.network=network(catalog,"SHR");
        command.sensorRange=scoutA.sensorRange=scoutB.sensorRange=850;
        for(int i=0;i<28;i++)b.step(); // Real echo plus transmitter and radio delay.
        Battle.TacticalContact merged=report(b,command.id,hostile.id);
        require(merged!=null&&!merged.stale()&&merged.sourceId()==command.id,
                "FCM lost its own real primary observation");
        require(merged.independentSources()==2&&
                        (merged.corroboratingSourceId()==scoutA.id||
                         merged.corroboratingSourceId()==scoutB.id)&&
                        merged.corroboratingObservedTick()<=b.tick,
                "FCM did not correlate a physically arrived independent SHR fix");
        long fingerprint=b.fingerprint();
        b.tacticalPicture(0,command.id);
        require(b.fingerprint()==fingerprint,
                "FCM display changed deterministic firing or track memory");
        // Both independently fitted SHR senders have physical records. A
        // previously delivered packet must not grant independent credit once
        // its fitted transmitter is removed, even before the next battle tick.
        scoutA.network=network(catalog,"TRK");
        Battle.TacticalContact onlyB=report(b,command.id,hostile.id);
        require(onlyB!=null&&onlyB.independentSources()==2&&
                        onlyB.corroboratingSourceId()==scoutB.id,
                "FCM counted an uninstalled SHR or lost the second eligible scout");
        scoutB.network=network(catalog,"TRK");
        Battle.TacticalContact noShr=report(b,command.id,hostile.id);
        require(noShr!=null&&noShr.independentSources()==1&&
                        noShr.corroboratingSourceId()==0&&
                        noShr.confidence()<onlyB.confidence(),
                "FCM used packets after all fitted SHR transmitters were removed");
        scoutA.network=network(catalog,"SHR");
        scoutB.network=network(catalog,"SHR");
        // The source must still have working sensors and the recipient a
        // live, powered FCM. A stored report does not substitute for either.
        scoutA.sensorsCondition=scoutB.sensorsCondition=0;
        Battle.TacticalContact blind=report(b,command.id,hostile.id);
        require(blind!=null&&blind.independentSources()==1,
                "Broken physical scout receivers still boosted command confidence");
        scoutA.sensorsCondition=scoutB.sensorsCondition=1;
        command.network=network(catalog);
        Battle.TacticalContact noFcm=report(b,command.id,hostile.id);
        require(noFcm!=null&&noFcm.independentSources()==1,
                "Uninstalled FCM continued corroborating remote observations");
        command.network=network(catalog,"FCM");
        hostile.x+=450; // Hidden current physics must not enter historical samples.
        Battle.TacticalContact frozen=report(b,command.id,hostile.id);
        require(frozen!=null&&frozen.x()!=hostile.x&&
                        frozen.corroboratingSourceId()==merged.corroboratingSourceId(),
                "FCM used hidden present enemy position");
        scoutA.powerRegen=scoutB.powerRegen=0;
        scoutA.power=scoutB.power=0;
        Battle.TacticalContact alone=report(b,command.id,hostile.id);
        require(alone!=null&&alone.independentSources()==1&&
                        alone.corroboratingSourceId()==0&&
                        merged.confidence()>alone.confidence()&&
                        merged.uncertainty()<alone.uncertainty(),
                "Unpowered scouts provided fictitious FCM confidence");
        scoutA.power=scoutB.power=100;
        command.powerRegen=0;command.power=0;
        Battle.TacticalContact offline=report(b,command.id,hostile.id);
        require(offline==null||offline.independentSources()==1,
                "Unpowered command core continued two-source fusion");
        System.out.println("PASS physical FCM two-source confidence/provenance, hidden-state and power-loss gates");
    }
    /** An old *genuinely transmitted* SHR fix is not a second live vote
     * after its scout loses acquisition. The FCM must age its confidence
     * instead of treating a retained packet as a new enemy coordinate. */
    private static void fusionLateAndConflictingFix(TechnologyCatalog catalog){
        Battle b=new Battle(56781);
        b.configureWeather(SpaceWeather.forLevel(1));
        Battle.Ship command=b.addShip(0,Battle.Faction.TERRAN,600,100);
        Battle.Ship scoutA=b.addShip(0,Battle.Faction.TERRAN,550,100);
        Battle.Ship scoutB=b.addShip(0,Battle.Faction.TERRAN,580,120);
        Battle.Ship blind=b.addShip(0,Battle.Faction.TERRAN,600,-450);
        Battle.Ship hostile=b.addShip(1,Battle.Faction.DRAXIAN,750,100);
        stationary(b);
        command.network=network(catalog,"FCM");
        scoutA.network=network(catalog,"SHR");
        scoutB.network=network(catalog,"SHR");
        command.sensorRange=scoutA.sensorRange=scoutB.sensorRange=850;
        blind.sensorRange=blind.infraredRange=blind.xrayRange=
                blind.graviticRange=0;
        for(int i=0;i<28;i++)b.step();
        Battle.TacticalContact corroborated=report(b,command.id,hostile.id);
        require(corroborated!=null&&corroborated.independentSources()==2,
                "Both functioning fitted scouts must produce a real initial FCM vote");
        require(b.hasTargetContact(blind,hostile,0),
                "Blind captain did not receive the initial genuine SHR acquisition");
        // Move only the true target. The old packets are actual measured
        // history, but these scouts can no longer acquire it at the new range.
        hostile.x=1250;
        scoutA.sensorRange=scoutB.sensorRange=0;
        scoutA.infraredRange=scoutB.infraredRange=0;
        scoutA.xrayRange=scoutB.xrayRange=0;
        scoutA.graviticRange=scoutB.graviticRange=0;
        for(int i=0;i<40;i++)b.step();
        Battle.TacticalContact fresh=report(b,command.id,hostile.id);
        require(fresh!=null&&!fresh.stale()&&fresh.sourceId()==command.id
                        &&fresh.x()==1250&&fresh.observedTick()>corroborated.observedTick(),
                "Command direct scope did not receive its own newer actual enemy fix");
        require(fresh.independentSources()==1&&fresh.corroboratingSourceId()==0
                        &&fresh.confidence()<corroborated.confidence()
                        &&fresh.uncertainty()>corroborated.uncertainty(),
                "Outdated conflicting SHR packets falsely improved new FCM confidence");
        require(!b.hasTargetContact(blind,hostile,0),
                "Retained former scout packets restored a blind captain's weapon lock");
        long fingerprint=b.fingerprint();
        scoutA.powerRegen=scoutB.powerRegen=0;
        scoutA.power=scoutB.power=0;
        Battle.TacticalContact withoutScouts=report(b,command.id,hostile.id);
        require(withoutScouts!=null&&withoutScouts.x()==fresh.x()&&
                        withoutScouts.confidence()==fresh.confidence()&&
                        withoutScouts.uncertainty()==fresh.uncertainty(),
                "An expired but powered scout secretly still voted in new FCM estimate");
        scoutA.power=scoutB.power=100;
        require(b.fingerprint()==fingerprint,
                "Read-only late FCM evaluation changed authoritative simulation state");
        System.out.println("PASS real SHR packet aging/conflict cannot boost FCM or renew blind fire");
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        sharing(catalog);
        lateScoutPowerOn(catalog);
        historical(catalog);
        optical(catalog);
        command(catalog);
        fusion(catalog);
        fusionLateAndConflictingFix(catalog);
        System.out.println("PASS bounded read-only real fleet sensor picture");
    }
}
