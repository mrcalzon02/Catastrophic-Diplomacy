package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import java.util.List;

/** Real emitted false contacts, funded radio slots and non-authorizing transit. */
public final class SensorFalseContactRelayTest {
    private static void check(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }
    private static Shipyard.Network sharing()throws Exception{
        TechnologyCatalog cat=TechnologyCatalog.loadDefault();
        Shipyard.Design bare=Shipyard.bareHull(Battle.Faction.TERRAN,
                Shipyard.Hull.DREADNOUGHT);
        int socket=-1;
        for(Shipyard.Slot slot:bare.hull.slots){
            if(slot.mount()==Shipyard.Mount.SYSTEM){socket=slot.index();break;}
        }
        if(socket<0)throw new AssertionError("Missing real SHR fitting socket");
        var fitted=bare.install(socket,"BASE-"+Shipyard.factionId(
                Battle.Faction.TERRAN)+"-SHR");
        check(Shipyard.inspect(fitted,cat).valid(),"Real SHR equipment rejected");
        return Shipyard.fit(fitted,cat).network();
    }
    private static void boundedRadio(){
        SensorFalseContactRelay buffer=new SensorFalseContactRelay();
        var measured=new SensorLightTimeHistory.Sample(5,700,100,0,0,true);
        int transit=SensorLightTimeHistory.propagationTicks(450);
        check(buffer.delivered(20,550,100,1).isEmpty(),
                "Unsent radio hypothesized a false signal");
        buffer.transmit(80,0,true,measured,10,100,100,1,1);
        long signature=buffer.fingerprint();
        check(buffer.delivered(10+transit-1,550,100,1).isEmpty(),
                "False-contact packet exceeded light speed");
        var first=buffer.delivered(10+transit,550,100,1);
        check(first.size()==1&&first.get(0).observedTick()==5&&
                first.get(0).receivedTick()==10+transit,
                "False contact missing measured/transmitted/received chronology");
        check(buffer.fingerprint()==signature,
                "Read-only false-contact display changed replay identity");
        buffer.transmit(80,0,false,measured,11,100,100,1,1);
        check(!buffer.delivered(10+transit,550,100,1).isEmpty()&&
                buffer.delivered(11+transit,550,100,1).isEmpty(),
                "Delayed shutoff improperly replaced photons already enroute");
        check(buffer.delivered(60,550,100,0).isEmpty(),
                "Unfitted receiver got free phantom memory");
        System.out.println("PASS bounded false-hypothesis packets, delayed shutoff and radio replay");
    }
    private static Battle create(long seed,Shipyard.Network shr){
        Battle b=new Battle(seed);
        Battle.Ship scout=b.addShip(0,Battle.Faction.TERRAN,100,100);
        Battle.Ship viewer=b.addShip(0,Battle.Faction.TERRAN,1250,100);
        Battle.Ship emitter=b.addShip(1,Battle.Faction.DRAXIAN,650,100);
        for(Battle.Ship ship:b.ships()){
            b.registerDiagnosticStationaryTarget(ship.id);
            ship.power=200;ship.maxPower=200;ship.powerRegen=0;
            ship.speed=ship.vx=ship.vy=0;
            ship.laserDamage=ship.kineticDamage=ship.plasmaDamage=
                    ship.particleDamage=ship.missileDamage=ship.torpedoDamage=0;
            ship.pdRange=0;ship.fighterReserve=ship.droneReserve=0;
        }
        scout.network=shr;scout.sensorRange=900;
        viewer.sensorRange=viewer.infraredRange=viewer.xrayRange=
                viewer.graviticRange=0;
        emitter.hologramNoise=.75;emitter.ecmCondition=1;
        return b;
    }
    private static void realFleetTransit()throws Exception{
        var shr=sharing();
        check(shr.sharingCapacity()>=2,
                "SHR needs bandwidth for one measured ship and a false hypothesis");
        Battle b=create(752212,shr);
        Battle.Ship scout=b.ships().get(0);
        Battle.Ship viewer=b.ships().get(1);
        Battle.Ship emitter=b.ships().get(2);
        int echo=2*SensorLightTimeHistory.propagationTicks(550);
        int relay=SensorLightTimeHistory.propagationTicks(1150);
        for(int i=0;i<echo+relay-1;i++)b.step();
        check(b.tacticalFalseContacts(0,viewer.id).isEmpty(),
                "Distant captain received a false contact before the scout and link");
        b.step();
        List<Battle.TacticalFalseContact> ghosts=b.tacticalFalseContacts(0,viewer.id);
        check(!ghosts.isEmpty()&&ghosts.stream().allMatch(
                x->x.relayed()&&x.sourceId()==scout.id&&
                        x.observedTick()<b.tick),
                "Fitted SHR failed to deliver actual historical false hypotheses");
        check(b.tacticalPicture(0,viewer.id).stream().noneMatch(
                x->x.targetId()!=emitter.id),
                "Ghost appeared as independently targetable ship");
        long signature=b.fingerprint();
        b.tacticalFalseContacts(0,viewer.id);
        check(b.fingerprint()==signature,"False-contact UI mutated battle");
        emitter.y+=250;
        b.step();
        var older=b.tacticalFalseContacts(0,viewer.id);
        check(!older.isEmpty()&&older.get(0).y()!=
                DeceptionProjection.ghostY(emitter,older.get(0).index(),b.tick),
                "Enemy's current hologram pose leaked through past link packets");
        emitter.power=0;
        b.step();
        check(!b.tacticalFalseContacts(0,viewer.id).isEmpty(),
                "Emitter shutdown traveled backwards through the physical radio");
        boolean cleared=false;
        for(int i=0;i<55;i++){
            b.step();
            if(b.tacticalFalseContacts(0,viewer.id).isEmpty()){
                cleared=true;break;
            }
        }
        check(cleared,"Powered-down emitter stayed a permanent false fleet contact");
        scout.power=0;
        check(b.tacticalFalseContacts(0,viewer.id).isEmpty(),
                "Dead scout continued broadcasting fake sensor packets");
        System.out.println("PASS real SHR false reports, causal blackout and non-locking phantoms");
    }
    private static void replay()throws Exception{
        var shr=sharing();
        Battle a=create(752213,shr),b=create(752213,shr);
        for(int i=0;i<48;i++){
            if(i==24){a.ships().get(2).power=0;b.ships().get(2).power=0;}
            a.step();b.step();
            check(a.fingerprint()==b.fingerprint(),
                    "Identical false-contact emission histories diverged in replay");
            check(a.tacticalFalseContacts(0,a.ships().get(1).id).equals(
                    b.tacticalFalseContacts(0,b.ships().get(1).id)),
                    "False-contact relay is nondeterministic between observers");
        }
        System.out.println("PASS false-contact deterministic battle replay");
    }
    private record Sent(long tick,boolean active,double sx,double sy){}
    private static void falseContactReference(SensorFalseContactRelay buffer,
            List<Sent> retained,long now,double rx,double ry){
        Sent arrived=null;
        for(int j=retained.size()-1;j>=0;j--){
            Sent row=retained.get(j);
            if(row.tick()>now)continue;
            int transit=SensorLightTimeHistory.propagationTicks(
                    StrictMath.hypot(rx-row.sx(),ry-row.sy()));
            if(now-row.tick()>=transit){arrived=row;break;}
        }
        boolean visible=arrived!=null&&arrived.active()&&
                now-arrived.tick()<=SensorFalseContactRelay.MAX_REPORT_AGE_TICKS;
        long before=buffer.fingerprint();
        List<SensorFalseContactRelay.Packet> result=
                buffer.delivered(now,rx,ry,1);
        check(result.size()==(visible?1:0),
                "Ghost radio changed arrival, tombstone or report TTL at "+now);
        if(visible){
            var entry=result.get(0);
            check(entry.emitterId()==80&&entry.index()==0&&
                    entry.sentTick()==arrived.tick()&&
                    entry.receivedTick()==now&&
                    entry.senderX()==arrived.sx()&&
                    entry.senderY()==arrived.sy(),
                    "False-contact photon arrival reordered moving sender records");
        }
        check(buffer.fingerprint()==before,
                "Read-only false-contact query changed battle fingerprint");
    }
    private static void movingGhostChronology(){
        int checks=0;
        for(boolean outOfOrder:new boolean[]{false,true}){
            SensorFalseContactRelay buffer=new SensorFalseContactRelay();
            List<Sent> retained=new java.util.ArrayList<>();
            var sample=new SensorLightTimeHistory.Sample(0,700,100,0,0,true);
            for(int i=0;i<105;i++){
                long tick=outOfOrder?500+3L*i-(i%7==0?5:0):200+i/3;
                boolean active=i%11!=0;
                double sx=(i%5)*350.0,sy=(i%3)*250.0;
                buffer.transmit(80,0,active,sample,tick,sx,sy,1,1);
                retained.add(new Sent(tick,active,sx,sy));
                if(retained.size()>SensorFalseContactRelay.REPORTS_PER_HYPOTHESIS)
                    retained.remove(0);
                for(double rx:new double[]{0,200,900,1800})
                    for(long now:new long[]{tick-1,tick,tick+5,
                            tick+20,tick+45,tick+55}){
                        falseContactReference(buffer,retained,now,rx,500);
                        checks++;
                    }
            }
        }
        var sample=new SensorLightTimeHistory.Sample(0,700,100,0,0,true);
        var late=new SensorFalseContactRelay();
        late.transmit(80,0,true,sample,Long.MAX_VALUE-25,1800,0,1,1);
        late.transmit(80,0,false,sample,Long.MAX_VALUE-2,0,0,1,1);
        check(late.delivered(Long.MAX_VALUE-1,1800,0,1).size()==1,
                "Overflowed future tombstone erased an arrived ghost");
        check(late.delivered(Long.MAX_VALUE,0,0,1).isEmpty(),
                "Delivered tombstone failed to clear old phantom");
        System.out.println("PASS false-contact light-time "+checks+
                " moving-source references, reorder/wrap, tombstones and TTL");
    }
    public static void main(String[] args)throws Exception{
        boundedRadio();movingGhostChronology();realFleetTransit();replay();
    }
}
