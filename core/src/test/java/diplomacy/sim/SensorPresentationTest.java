package diplomacy.sim;

/** Standalone, no graphics hardware: contact artwork must reflect actual
 * scanner acquisition while the fog itself never modifies combat. */
public final class SensorPresentationTest {
    private static void require(boolean pass,String detail){
        if(!pass)throw new AssertionError(detail);
    }
    public static void main(String[] args){
        double r=390;
        require(FogOfWarModel.visible(0,r)==1
                &&FogOfWarModel.visible(r*.84,r)==1
                &&FogOfWarModel.visible(r*1.14,r)==0
                &&FogOfWarModel.visible(r*1.5,r)==0,
                "Optical fog must have a fully readable core and hidden exterior");
        float a=FogOfWarModel.visible(r*.91,r);
        float b=FogOfWarModel.visible(r*1.05,r);
        require(a>b&&b>0&&a<1,"Fog must have an actual blended falloff");
        require(FogOfWarModel.visible(Double.NaN,r)==0,
                "Invalid ranges must fail closed instead of revealing the map");

        Battle battle=new Battle(505L);
        Battle.Ship own=battle.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship enemy=battle.addShip(1,Battle.Faction.DRAXIAN,700,100);
        own.sensorRange=0;
        own.infraredRange=700;
        own.xrayRange=own.graviticRange=0;
        own.power=100;
        own.sensorsCondition=1;
        int thermal=battle.directSensorChannels(own,enemy,600);
        require((thermal&Battle.CONTACT_INFRARED)!=0
                &&(thermal&Battle.CONTACT_OPTICAL)==0
                &&(thermal&Battle.CONTACT_RADAR)==0,
                "Remote infrared must be a heat-only marker, not a hull reveal");
        require(battle.hasTargetContact(own,enemy,600),
                "Presentation may not invent infrared returns absent from combat");
        enemy.thermalSignature=.2;
        require((battle.directSensorChannels(own,enemy,600)
                &Battle.CONTACT_INFRARED)==0,
                "Thermal dampers must suppress infrared artwork");
        own.infraredRange=0;
        own.sensorRange=700;
        enemy.radarCrossSection=1;
        require((battle.directSensorChannels(own,enemy,600)
                &Battle.CONTACT_RADAR)!=0,
                "Radar return must generate hull-outline contact");
        enemy.radarCrossSection=.06;
        require((battle.directSensorChannels(own,enemy,600)
                &Battle.CONTACT_RADAR)==0,
                "Stealth-coated targets must suppress radar outlines");
        own.sensorRange=0;
        own.xrayRange=800;
        require((battle.directSensorChannels(own,enemy,600)
                &Battle.CONTACT_XRAY)!=0,"X-ray must independently reveal a detection");
        own.xrayRange=0;
        own.graviticRange=800;
        require((battle.directSensorChannels(own,enemy,600)
                &Battle.CONTACT_GRAVITIC)!=0,"Gravitic return requires its own symbol");
        own.power=0;
        require(battle.directSensorChannels(own,enemy,600)==0,
                "Unpowered electronic sensors must not emit phantom contacts");
        enemy.x=own.x+200;
        require((battle.directSensorChannels(own,enemy,200)
                &Battle.CONTACT_OPTICAL)!=0,
                "Close passive optics must survive loss of reactor power");
        enemy.opticalSignature=.2;enemy.power=100;enemy.ecmCondition=1;
        require((battle.directSensorChannels(own,enemy,200)
                &Battle.CONTACT_OPTICAL)==0,
                "Powered optical cloak must conceal the direct hull silhouette");
        System.out.println("PASS fog blending, independent sensor markers and stealth counterplay");
    }
}
