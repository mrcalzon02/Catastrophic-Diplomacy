package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.CampaignShipyardRules;
import diplomacy.shipyard.SkirmishTechnologyRules;
import diplomacy.tech.SensorNetworkCatalog;
import diplomacy.tech.TechnologyCatalog;

/** Actual fittings, nine-rank research, independent channel effects and
 * bounded shared contacts. Visual polish remains a non-blocking diagnostic. */
public final class SensorNetworkModulesTest {
    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount mount,int nth){
        for(Shipyard.Slot slot:hull.slots){
            if(slot.mount()!=mount)continue;
            if(nth--==0)return slot.index();
        }
        throw new AssertionError("No "+mount+" socket on "+hull);
    }
    private static Shipyard.Design fit(Shipyard.Design bare,String code){
        Shipyard.Mount mount="FCM".equals(SensorNetworkCatalog.root(code))
                ?Shipyard.Mount.SUPPORT:Shipyard.Mount.SYSTEM;
        return bare.install(socket(bare.hull,mount,0),
                "BASE-"+Shipyard.factionId(bare.faction)+"-"+code);
    }
    private static double capability(String root,Shipyard.Network n){
        return switch(root){
            case "SHR" -> n.sharingRange();
            case "RES" -> n.resolutionGain()-1;
            case "TRK" -> n.trackMemoryTicks();
            case "LNK" -> n.trackRelayRange();
            case "FCM" -> n.fusionStrength();
            case "CAP" -> n.targetCapacity()-1;
            default -> throw new IllegalArgumentException(root);
        };
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        require(SensorNetworkCatalog.FAMILIES.size()==6 &&
                SensorNetworkCatalog.VARIANTS.size()==24,
                "Exactly six information families and four alternatives each");
        for(Battle.Faction faction:Battle.Faction.values()){
            Shipyard.Design bare=Shipyard.bareHull(faction,Shipyard.Hull.DREADNOUGHT);
            Shipyard.Network empty=Shipyard.fit(bare,catalog).network();
            require(empty.sharingRange()==0&&empty.trackMemoryTicks()==0
                    &&empty.trackRelayRange()==0&&empty.fusionRange()==0
                    &&empty.targetCapacity()==1&&empty.resolutionGain()==1,
                    "A bare ship must not receive free fleet electronics");
            for(SensorNetworkCatalog.Family family:SensorNetworkCatalog.FAMILIES){
                String code=family.code();
                Shipyard.Design design=fit(bare,code);
                require(Shipyard.inspect(design,catalog).valid(),
                        "Invalid faction hardware "+faction+"/"+code+": "+
                                Shipyard.inspect(design,catalog).explanation());
                Shipyard.Network network=Shipyard.fit(design,catalog).network();
                require(capability(code,network)>0,
                        code+" fitted but contributes no distinct simulation effect");
                require(catalog.get("TECH-"+Shipyard.factionId(faction)+"-"+code+
                        "-SPECIALIST-09")!=null,
                        code+" must retain all nine independent research stages");
                require(Shipyard.decode(Shipyard.encode(design),catalog)
                        .at(socket(bare.hull,"FCM".equals(code)?
                                Shipyard.Mount.SUPPORT:Shipyard.Mount.SYSTEM,0))
                        .component.equals("BASE-"+Shipyard.factionId(faction)+"-"+code),
                        "Module save identity lost "+faction+"/"+code);
            }
            for(SensorNetworkCatalog.Model variant:SensorNetworkCatalog.VARIANTS){
                String code=variant.code();
                require(catalog.base("BASE-"+Shipyard.factionId(faction)+"-"+code)!=null,
                        "Missing native variant "+faction+"/"+code);
                require(catalog.get("TECH-"+Shipyard.factionId(faction)+"-"+code+
                        "-OUTPUT-09")!=null,
                        "Independent nine-rank variant research missing "+code);
                Shipyard.Design design=fit(bare,code);
                require(Shipyard.inspect(design,catalog).valid(),
                        "Variant over budget "+faction+"/"+code+": "+
                                Shipyard.inspect(design,catalog).explanation());
                require(capability(variant.root(),Shipyard.fit(design,catalog).network())>0,
                        "Hardware variant has no behavior "+code);
                if(variant.requiredTier()>0){
                    try {
                        CampaignShipyardRules.requireAllowed(design,faction,
                                variant.requiredTier()-1,1);
                        throw new AssertionError("Campaign bypassed tier "+code);
                    }catch(IllegalArgumentException expected){ }
                    try {
                        SkirmishTechnologyRules.unrestricted()
                                .withStage(variant.requiredTier()-1)
                                .requireAllowed(design,catalog);
                        throw new AssertionError("Skirmish bypassed tier "+code);
                    }catch(IllegalArgumentException expected){ }
                }
            }
        }

        Shipyard.Design bare=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        for(SensorNetworkCatalog.Family family:SensorNetworkCatalog.FAMILIES){
            String primitive=null,advanced=null;
            for(SensorNetworkCatalog.Model variant:SensorNetworkCatalog.VARIANTS){
                if(!variant.root().equals(family.code()))continue;
                if(variant.requiredTier()==0)primitive=variant.code();
                if(variant.requiredTier()==6)advanced=variant.code();
            }
            require(primitive!=null&&advanced!=null,"Missing generation "+family.code());
            double old=capability(family.code(),
                    Shipyard.fit(fit(bare,primitive),catalog).network());
            double latest=capability(family.code(),
                    Shipyard.fit(fit(bare,advanced),catalog).network());
            require(latest>old,
                    "Advanced "+family.code()+" must outperform primitive hardware");
        }

        // Amplification improves existing physical channels; it cannot create
        // IR, X-ray or gravitic detection from an empty hull.
        Battle duel=new Battle(112L);
        Battle.Ship scanner=duel.addShip(0,Battle.Faction.TERRAN,0,0);
        Battle.Ship enemy=duel.addShip(1,Battle.Faction.DRAXIAN,640,0);
        scanner.sensorRange=0;scanner.infraredRange=600;
        scanner.xrayRange=scanner.graviticRange=0;
        scanner.network=Shipyard.fit(fit(bare,"RES"),catalog).network();
        require((duel.directSensorChannels(scanner,enemy,640)
                &Battle.CONTACT_INFRARED)!=0,
                "Resolution processor must amplify an installed IR detector");
        require((duel.directSensorChannels(scanner,enemy,640)
                &(Battle.CONTACT_XRAY|Battle.CONTACT_GRAVITIC|Battle.CONTACT_RADAR))==0,
                "Processor cannot invent absent sensor channels");
        scanner.power=0;
        require(duel.directSensorChannels(scanner,enemy,640)==0,
                "Loss of power must remove electronic amplification");

        // New SHR transceiver extends the actual limited contact relay; it
        // does NOT merely change the tactical HUD or grant a free local radar.
        Battle relay=new Battle(256L);
        relay.configureWeather(SpaceWeather.forLevel(1));
        Battle.Ship scout=relay.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship receiver=relay.addShip(0,Battle.Faction.TERRAN,1300,100);
        Battle.Ship opponent=relay.addShip(1,Battle.Faction.DRAXIAN,600,100);
        for(Battle.Ship ship:relay.ships()){
            ship.power=100;
            relay.registerDiagnosticStationaryTarget(ship.id);
        }
        scout.sensorRange=700;receiver.sensorRange=0;
        relay.step();
        require(!relay.hasTargetContact(receiver,opponent,700),
                "Original unmodified formation radio must remain range limited");
        scout.network=Shipyard.fit(fit(bare,"SHR"),catalog).network();
        relay.step();
        require(relay.hasTargetContact(receiver,opponent,700),
                "Fitted sharing transmitter must relay a real distant contact");
        scout.power=0;
        relay.step();
        require(!relay.hasTargetContact(receiver,opponent,700),
                "Dead transmitter must not continue a ghost datalink");

        require(Shipyard.fit(fit(bare,"CAP"),catalog).network().targetCapacity()>1,
                "CAP must unlock physical parallel target processing");
        System.out.println("PASS six fleet sensor-network modules, 24 variants, research, "+
                "amplification, relay range, fitting and power failure");
    }
}
