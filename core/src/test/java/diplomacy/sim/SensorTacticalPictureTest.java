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
        b.step();
        Battle.TacticalContact observed=report(b,viewer.id,target.id);
        require(observed!=null&&!observed.stale()&&observed.relayed()
                        &&observed.sourceId()==scout.id
                        &&(observed.channels()&Battle.CONTACT_RADAR)!=0,
                "Powered SHR must supply a real originating radar report");
        require(observed.x()==target.x&&observed.y()==target.y&&
                        observed.observedTick()==b.tick,
                "Live contact did not report actual observed position/time");
        long signature=b.fingerprint();
        int known=viewer.knownTracks.size();
        b.tacticalPicture(0,viewer.id);
        require(b.fingerprint()==signature&&viewer.knownTracks.size()==known,
                "Tactical UI mutated authoritative weapon state or history");
        scout.power=0;
        require(report(b,viewer.id,target.id)==null,
                "Unpowered sender continued supplying a live track");
        System.out.println("PASS SHR relay provenance and loss-of-power cutover");
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
        b.step();
        require(report(b,viewer.id,target.id)!=null,
                "LNK viewer did not receive tracked observation");
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
        b.step();
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
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        sharing(catalog);
        historical(catalog);
        optical(catalog);
        command(catalog);
        System.out.println("PASS bounded read-only real fleet sensor picture");
    }
}
