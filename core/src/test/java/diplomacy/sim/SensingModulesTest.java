package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Runnable standalone acceptance test: sensing is real hardware, not a UI label. */
public final class SensingModulesTest {
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static int socket(Shipyard.Hull hull,Shipyard.Mount mount) {
        for(Shipyard.Slot slot:hull.slots)
            if(slot.mount()==mount)return slot.index();
        throw new AssertionError("Missing "+mount+" socket");
    }
    public static void main(String[] args)throws Exception {
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Shipyard.Design bare=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        var normal=Shipyard.fit(bare,catalog);
        require(normal.sensing().radarGain()==1 && normal.sensing().xrayRange()==0
                && normal.sensing().hologramNoise()==0,
                "An unfitted hull cannot acquire exotic sensors or project decoys");
        for(String code:new String[]{"RDR","STC","IRT","HTD","VCL","ACU","XRY","GRA","HLP"}) {
            Shipyard.Mount mount=code.equals("STC")?Shipyard.Mount.DEFENSE:
                    code.equals("HLP")?Shipyard.Mount.SUPPORT:Shipyard.Mount.SYSTEM;
            int index=socket(bare.hull,mount);
            var component=catalog.base("BASE-TER-"+code);
            require(component!=null && Shipyard.accepts(bare.hull,index,component),
                    "Module not present in the actual shipyard socket list: "+code);
            Shipyard.Design design=bare.install(index,component.id);
            var report=Shipyard.inspect(design,catalog);
            require(report.valid(),"Sensor hardware has an invalid reactor/heat budget: "+
                    code+" "+report.explanation());
            var sensor=Shipyard.fit(design,catalog).sensing();
            require(switch(code){
                case "RDR" -> sensor.radarGain()>1;
                case "STC" -> sensor.radarSignature()<1;
                case "IRT" -> sensor.infraredRange()>0;
                case "HTD" -> sensor.thermalSignature()<1;
                case "VCL" -> sensor.opticalSignature()<1;
                case "ACU" -> sensor.acuity()>1;
                case "XRY" -> sensor.xrayRange()>0;
                case "GRA" -> sensor.graviticRange()>0;
                case "HLP" -> sensor.hologramNoise()>0;
                default -> false;
            },"Module had no actual simulation effect: "+code);
            var researched=Shipyard.fit(design.stage(index,Shipyard.Track.SPECIALIST,2),catalog)
                    .sensing();
            require(researched!=null,"Research chain must fit "+code);
            require(Shipyard.decode(Shipyard.encode(design),catalog).at(index)!=null,
                    "Sensing module lost in save/load: "+code);
        }
        Battle duel=new Battle(501L);
        duel.spawnWing(0,bare,catalog,1,100,100);
        duel.spawnWing(1,bare,catalog,1,450,100);
        Battle.Ship spotter=duel.ships().get(0),enemy=duel.ships().get(1);
        enemy.x=spotter.x+520;enemy.y=spotter.y;
        spotter.sensorRange=0;spotter.infraredRange=600;
        require(duel.hasTargetContact(spotter,enemy,520),
                "Infrared must see beyond the local optical silhouette");
        enemy.thermalSignature=0.25;
        require(!duel.hasTargetContact(spotter,enemy,520),
                "Thermal suppression must defeat a passive infrared contact");
        spotter.infraredRange=0;spotter.xrayRange=620;
        require(duel.hasTargetContact(spotter,enemy,520),
                "X-ray detector must counter heat damping");
        spotter.xrayRange=0;spotter.graviticRange=800;
        require(duel.hasTargetContact(spotter,enemy,520),
                "Gravitic detector must locate a massive stealth hull");
        spotter.graviticRange=0;enemy.x=spotter.x+300;
        require(duel.hasTargetContact(spotter,enemy,300),
                "Undisguised vessels must remain optically observable nearby");
        enemy.opticalSignature=0.25;
        require(!duel.hasTargetContact(spotter,enemy,300),
                "Powered visual cloak must conceal an optical-only contact");
        enemy.power=0;
        require(duel.hasTargetContact(spotter,enemy,300),
                "Unpowered visual cloak must fail open");
        System.out.println("PASS sensing module fittings, research, persistence and detection");
    }
}
