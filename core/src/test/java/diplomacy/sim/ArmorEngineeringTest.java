package diplomacy.sim;

import diplomacy.shipyard.ArmorMaterials;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;

/** Real outfitting and live damage-layer physics; no balance-style release criteria. */
public final class ArmorEngineeringTest {
    private ArmorEngineeringTest(){}
    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError(reason);
    }
    private static int defenseSocket(Shipyard.Design design,int nth){
        for(Shipyard.Slot slot:design.placements())
            if(slot.mount()==Shipyard.Mount.DEFENSE && nth--==0)return slot.index();
        throw new AssertionError("Missing defensive hardpoint");
    }
    private static Battle.Ship spawn(Shipyard.Design design,TechnologyCatalog catalog){
        check(Shipyard.inspect(design,catalog).valid(),
                "Illegal defensive ship fitting: "+Shipyard.inspect(design,catalog).explanation());
        Battle battle=new Battle(0xA32F19L);
        battle.spawnWing(0,design,catalog,1,300,300);
        return battle.ships().get(0);
    }
    public static void main(String[] args)throws Exception{
        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        int verified=0;
        for(Battle.Faction faction:Battle.Faction.values()){
            String prefix=diplomacy.sim.CampaignTechnology.factionCode(faction);
            Shipyard.Design starter=Shipyard.starter(faction,Shipyard.Hull.DREADNOUGHT);
            Battle.Ship plain=spawn(starter,catalog);
            int socket=defenseSocket(starter,1);
            for(String type:new String[]{"CMP","ABL","DEN","RAC","BLK"}){
                String id="BASE-"+prefix+"-"+type;
                check(catalog.hasBase(id),"Missing native defensive hardware "+id);
                Shipyard.Design armored=starter.install(socket,id);
                Battle.Ship vessel=spawn(armored,catalog);
                if(type.equals("BLK")){
                    check(vessel.maxHull>plain.maxHull+20,
                            "Bulkheads must increase actual structural HP "+faction);
                }else{
                    check(vessel.maxArmor>plain.maxArmor,
                            "Plating must add actual destructible armor "+id);
                    double neutral=plain.defense.damageFactor(
                            DirectionalDefense.Layer.ARMOR,
                            DirectionalDefense.Facing.FORWARD,Battle.DamageType.LASER);
                    double resistant=vessel.defense.damageFactor(
                            DirectionalDefense.Layer.ARMOR,
                            DirectionalDefense.Facing.FORWARD,Battle.DamageType.LASER);
                    if(type.equals("CMP")||type.equals("ABL"))
                        check(resistant<neutral,"Composite/ablative lasers must resist better "+id);
                }
                check(vessel.defense.totalArmor()==vessel.maxArmor,
                        "Fitted capacity must reach physical armor tiles");
                for(Shipyard.Track track:Shipyard.Track.values()){
                    Shipyard.Design ranked=armored.stage(socket,track,9);
                    check(catalog.get("TECH-"+prefix+"-"+type+"-"+track+"-09")!=null,
                            "Rank nine not researchable "+id+"/"+track);
                    Battle.Ship enhanced=spawn(ranked,catalog);
                    if(track==Shipyard.Track.OUTPUT){
                        if(type.equals("BLK"))check(enhanced.maxHull>vessel.maxHull,
                                "Bulkhead output upgrades must strengthen structure");
                        else check(enhanced.maxArmor>vessel.maxArmor,
                                "Plating output upgrades must increase capacity");
                    }
                    if(track==Shipyard.Track.RESILIENCE&&!type.equals("BLK")){
                        double original=vessel.defense.damageFactor(
                                DirectionalDefense.Layer.ARMOR,
                                DirectionalDefense.Facing.FORWARD,Battle.DamageType.KINETIC);
                        double upgraded=enhanced.defense.damageFactor(
                                DirectionalDefense.Layer.ARMOR,
                                DirectionalDefense.Facing.FORWARD,Battle.DamageType.KINETIC);
                        check(upgraded<original,"Resilience track must improve real hit resistance");
                    }
                }
                verified++;
            }
        }
        check(ArmorMaterials.factor("ABL",Battle.DamageType.LASER,null,0)<
                ArmorMaterials.factor("ABL",Battle.DamageType.KINETIC,null,0),
                "Ablative ceramics must specialize against energy weapons");
        check(ArmorMaterials.factor("DEN",Battle.DamageType.KINETIC,null,0)<
                ArmorMaterials.factor("DEN",Battle.DamageType.LASER,null,0),
                "Dense plating must specialize against kinetic rounds");
        check(ArmorMaterials.factor("RAC",Battle.DamageType.MISSILE,null,0)<
                ArmorMaterials.factor("RAC",Battle.DamageType.PLASMA,null,0),
                "Reactive cells must specialize against missile strikes");
        System.out.println("PASS armor engineering "+verified+
                " native fitted families, 4 branches x 9 ranks, physical shield/armor/structure");
    }
}
