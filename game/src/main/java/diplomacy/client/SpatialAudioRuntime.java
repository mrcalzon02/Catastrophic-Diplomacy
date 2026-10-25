package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spatial event mixer shared by Android, Windows, Linux.
 * Zoom, on-screen distance and strategic significance gate playback.
 * Bounded one-shots and 3 independent local engine voices; no 400-engine cacophony.
 */
final class SpatialAudioRuntime implements AutoCloseable {
    private static final int MAX_EVENTS = 6, MAX_LOCAL_ENGINES = 3, CANDIDATE_LIMIT = 80;
    private final Map<CombatAudioCue.Cue,Sound> library;
    private final Map<String,Sound> synth = new HashMap<>();
    private final Map<String,Long> lastCueTick = new HashMap<>();
    private final Map<Integer,EngineVoice> localEngines = new HashMap<>();
    private final Map<Integer,Double> shieldStates = new HashMap<>();
    private final Set<Integer> seenDead = new HashSet<>();
    private long lastSequence = 0, fleetLoop = -1;
    private int lastDestructions = 0;

    private static final class EngineVoice {
        long voice, lastThrusterTick = -999999;
        double facing;
        EngineVoice(long voice,double facing){this.voice=voice;this.facing=facing;}
    }
    private record Pending(Sound sound,String key,long tick,int cooldown,
                           float gain,float pan,float pitch,double priority) { }

    SpatialAudioRuntime(Map<CombatAudioCue.Cue,Sound> library) {
        this.library=library;
        for(String name:SynthesizedSounds.NAMES) {
            try {
                FileHandle file=Gdx.files.local("audio/synth/v2/"+name+".wav");
                byte[] bytes=SynthesizedSounds.wav(name);
                if(!file.exists() || file.length()!=bytes.length)
                    file.writeBytes(bytes,false);
                synth.put(name,Gdx.audio.newSound(file));
            }catch(RuntimeException error){
                Gdx.app.error("AUDIO","Optional synthesized cue failed: "+name,error);
            }
        }
    }
    void reset() {
        lastSequence=0;lastDestructions=0;lastCueTick.clear();
        shieldStates.clear();seenDead.clear();stopEngines();
    }
    private void stopEngines() {
        Sound engine=synth.get("engine_loop");
        if(engine!=null)for(EngineVoice voice:localEngines.values()){
            try{engine.stop(voice.voice);}catch(RuntimeException ignored){}
        }
        localEngines.clear();
        Sound fleet=synth.get("fleet_bed");
        if(fleet!=null && fleetLoop!=-1){
            try{fleet.stop(fleetLoop);}catch(RuntimeException ignored){}
        }
        fleetLoop=-1;
    }
    void silence() { stopEngines(); }
    private static float clip(float v){return Math.max(0,Math.min(1,v));}
    private static double wrappedAngle(double a,double b){
        return Math.abs(Math.atan2(Math.sin(a-b),Math.cos(a-b)));
    }
    private Sound selectSound(Battle.VisualEvent event,CombatAudioCue.Cue cue) {
        String weapon=event.weapon();
        if(event.phase()==Battle.VisualPhase.FIRE){
            if("POINT_DEFENSE".equals(weapon))return synth.getOrDefault("pd_tick",library.get(cue));
            if("TORPEDO".equals(weapon)||"PLASMA".equals(weapon))
                return synth.getOrDefault("heavy_fire",library.get(cue));
        }
        return library.get(cue);
    }
    private static SpatialAudioModel.Layer layer(Battle.VisualEvent event, Battle.Ship source,
                                                  Battle.Ship target) {
        if(event.phase()==Battle.VisualPhase.FIRE) {
            if("DRONE".equals(event.weapon())||"FIGHTER".equals(event.weapon())
                    ||"POINT_DEFENSE".equals(event.weapon()))
                return SpatialAudioModel.Layer.DETAIL;
            if("TORPEDO".equals(event.weapon())||"PLASMA".equals(event.weapon()))
                return SpatialAudioModel.Layer.CAPITAL;
            if(source!=null && source.visualScale()>1.42)
                return SpatialAudioModel.Layer.CAPITAL;
            return SpatialAudioModel.Layer.TACTICAL;
        }
        if(event.phase()==Battle.VisualPhase.IMPACT)
            return target!=null && target.visualScale()>1.40
                    ? SpatialAudioModel.Layer.CAPITAL : SpatialAudioModel.Layer.TACTICAL;
        return SpatialAudioModel.Layer.TACTICAL;
    }
    private static double weight(SpatialAudioModel.Layer layer,Battle.VisualPhase phase){
        double priority=switch(layer){
            case DETAIL -> 0.3;
            case TACTICAL -> 1.0;
            case CAPITAL -> 3.3;
            case FLEET -> 4.0;
        };
        return priority*(phase==Battle.VisualPhase.IMPACT?1.7:1.0);
    }
    private void add(List<Pending> list,Sound sound,String key,long tick,int cooldown,
                     float gain,float pan,float pitch,double priority){
        if(sound==null || gain<0.012f)return;
        Pending pending=new Pending(sound,key,tick,cooldown,gain,pan,pitch,priority*gain);
        if(list.size()<CANDIDATE_LIMIT){list.add(pending);return;}
        int smallest=0;
        for(int i=1;i<list.size();i++)
            if(list.get(i).priority<list.get(smallest).priority)smallest=i;
        if(pending.priority>list.get(smallest).priority)list.set(smallest,pending);
    }
    private static Battle.Ship byId(Battle battle,int id) {
        // 400 vessels maximum in the current tactical battle; index built once below
        for(Battle.Ship s:battle.ships())if(s.id==id)return s;
        return null;
    }
    private void event(List<Pending> list,Battle battle,Battle.VisualEvent event,
                       double cx,double cy,double zoom){
        CombatAudioCue.Cue cue=CombatAudioCue.from(event);
        if(cue==null)return;
        Battle.Ship source=byId(battle,event.sourceId());
        Battle.Ship target=byId(battle,event.targetId());
        SpatialAudioModel.Layer layer=layer(event,source,target);
        double x=event.phase()==Battle.VisualPhase.FIRE?event.x():event.x();
        double y=event.y();
        SpatialAudioModel.Mix mix=SpatialAudioModel.at(layer,cx,cy,zoom,x,y);
        if(!mix.audible())return;
        float strength=(event.phase()==Battle.VisualPhase.IMPACT?0.91f:0.68f);
        if(layer==SpatialAudioModel.Layer.CAPITAL)strength*=1.18f;
        String key=event.phase()+":"+event.weapon();
        float pitch=0.91f+(float)Math.floorMod(event.shotId(),7)*0.027f;
        add(list,selectSound(event,cue),key,event.tick(),
                CombatAudioCue.cooldownTicks(cue),mix.gain()*strength,mix.pan(),
                pitch,weight(layer,event.phase()));
    }
    private void shieldChanges(List<Pending> list,Battle battle,double cx,double cy,double zoom) {
        for(Battle.Ship ship:battle.ships())if(ship.alive()) {
            double ratio=ship.maxShields<=0?0:ship.shields/ship.maxShields;
            Double old=shieldStates.put(ship.id,ratio);
            if(old==null)continue; // Full shields at spawn are NOT false recharge events.
            String cue=null;
            if(old>0.10&&ratio<0.06)cue="shield_down";
            else if(old<0.09&&ratio>0.24)cue="shield_up";
            if(cue==null)continue;
            SpatialAudioModel.Layer layer=ship.visualScale()>1.4
                    ? SpatialAudioModel.Layer.CAPITAL : SpatialAudioModel.Layer.TACTICAL;
            SpatialAudioModel.Mix mix=SpatialAudioModel.at(layer,cx,cy,zoom,ship.x,ship.y);
            add(list,synth.get(cue),cue,battle.tick,20,mix.gain()*0.85f,
                    mix.pan(),1f,3.6);
        }
    }
    private void destructions(List<Pending> list,Battle battle,double cx,double cy,double zoom) {
        if(battle.destructions>lastDestructions){
            for(Battle.Ship ship:battle.ships())if(!ship.alive()&&seenDead.add(ship.id)){
                SpatialAudioModel.Mix mix=SpatialAudioModel.at(
                        SpatialAudioModel.Layer.CAPITAL,cx,cy,zoom,ship.x,ship.y);
                // Major kills remain audible at fleet scale, but never unbounded.
                float gain=clip(0.4f+0.7f*mix.gain());
                add(list,synth.getOrDefault("reactor_hit",
                        library.get(CombatAudioCue.Cue.EXPLOSION)),
                        "explosion",battle.tick,13,gain,mix.pan(),0.88f,5.6);
            }
        }
        lastDestructions=battle.destructions;
    }
    private void mixEvents(List<Pending> candidates,float master) {
        candidates.sort(Comparator.comparingDouble(Pending::priority).reversed());
        int total=0,detail=0;
        for(Pending p:candidates){
            if(total>=MAX_EVENTS)break;
            if(p.key.startsWith("FIRE:POINT_DEFENSE") && detail>=1)continue;
            if(p.key.startsWith("FIRE:FIGHTER") && detail>=1)continue;
            long previous=lastCueTick.getOrDefault(p.key,Long.MIN_VALUE/2);
            if(p.tick-previous<p.cooldown)continue;
            try{
                long voice=p.sound.play(clip(master*p.gain),p.pitch,p.pan);
                if(voice<0)continue;
                lastCueTick.put(p.key,p.tick);total++;
                if(p.key.startsWith("FIRE:POINT_DEFENSE")||p.key.startsWith("FIRE:FIGHTER"))detail++;
            }catch(RuntimeException error){
                Gdx.app.error("AUDIO","Spatial voice failed: "+p.key,error);
            }
        }
    }
    /** Select nearest audible 3 engines, and replace all remote thrusters by one fleet bed. */
    private void engines(Battle battle,double cx,double cy,double zoom,float volume,boolean active) {
        if(!active || volume<=0){stopEngines();return;}
        int count=0;
        for(Battle.Ship ship:battle.ships())if(ship.alive())count++;
        Sound fleet=synth.get("fleet_bed");
        float bed=SpatialAudioModel.fleetBedGain(count,zoom)*volume;
        if(fleet!=null&&bed>0.004f)try {
            if(fleetLoop==-1)fleetLoop=fleet.loop(bed);
            if(fleetLoop!=-1)fleet.setVolume(fleetLoop,bed);
        }catch(RuntimeException error){Gdx.app.error("AUDIO","Fleet machinery bed failed",error);}
        else if(fleetLoop!=-1&&fleet!=null){fleet.stop(fleetLoop);fleetLoop=-1;}
        if(!SpatialAudioModel.enginesAudible(zoom)) {
            Sound hum=synth.get("engine_loop");
            if(hum!=null)for(EngineVoice voice:localEngines.values())hum.stop(voice.voice);
            localEngines.clear();return;
        }
        Sound hum=synth.get("engine_loop");
        if(hum==null)return;
        List<Battle.Ship> picks=new ArrayList<>();
        for(Battle.Ship ship:battle.ships())if(ship.alive()&&Math.hypot(ship.vx,ship.vy)>3) {
            SpatialAudioModel.Mix m=SpatialAudioModel.at(
                    SpatialAudioModel.Layer.DETAIL,cx,cy,zoom,ship.x,ship.y);
            if(!m.audible())continue;
            picks.add(ship);
        }
        picks.sort(Comparator.comparingDouble((Battle.Ship s) -> {
            SpatialAudioModel.Mix m=SpatialAudioModel.at(
                    SpatialAudioModel.Layer.DETAIL,cx,cy,zoom,s.x,s.y);
            return m.gain()*(0.65+s.visualScale()*0.35);
        }).reversed());
        Set<Integer> keep=new HashSet<>();
        for(int i=0;i<Math.min(MAX_LOCAL_ENGINES,picks.size());i++){
            Battle.Ship ship=picks.get(i);
            keep.add(ship.id);
            SpatialAudioModel.Mix m=SpatialAudioModel.at(
                    SpatialAudioModel.Layer.DETAIL,cx,cy,zoom,ship.x,ship.y);
            float gain=clip(volume*0.29f*m.gain());
            EngineVoice voice=localEngines.get(ship.id);
            try {
                if(voice==null){
                    long id=hum.loop(gain,0.8f+(float)Math.min(0.38,ship.visualScale()*0.12),m.pan());
                    if(id!=-1){voice=new EngineVoice(id,ship.facing);localEngines.put(ship.id,voice);}
                }else hum.setPan(voice.voice,m.pan(),gain);
                if(voice!=null&&wrappedAngle(ship.facing,voice.facing)>0.11
                        &&battle.tick-voice.lastThrusterTick>=14) {
                    Sound burst=synth.get("thruster");
                    if(burst!=null)burst.play(clip(volume*0.35f*m.gain()),1f,m.pan());
                    voice.lastThrusterTick=battle.tick;
                }
                // Retain the last firing heading; accumulate small steering corrections.
                if(voice!=null&&battle.tick==voice.lastThrusterTick)voice.facing=ship.facing;
            }catch(RuntimeException error){Gdx.app.error("AUDIO","Engine channel failed",error);}
        }
        for(var iter=localEngines.entrySet().iterator();iter.hasNext();){
            var entry=iter.next();
            if(!keep.contains(entry.getKey())){hum.stop(entry.getValue().voice);iter.remove();}
        }
    }
    void advance(Battle battle,double cx,double cy,double zoom,boolean active,
                 boolean effectsOn,float effectsVolume) {
        if(battle==null)return;
        if(!effectsOn){lastSequence=lastSeen(battle);lastDestructions=battle.destructions;
            stopEngines();return;}
        List<Pending> candidates=new ArrayList<>();
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.sequence()<=lastSequence)continue;
            lastSequence=event.sequence();
            if(active)event(candidates,battle,event,cx,cy,zoom);
        }
        if(active){
            shieldChanges(candidates,battle,cx,cy,zoom);
            destructions(candidates,battle,cx,cy,zoom);
            mixEvents(candidates,effectsVolume);
        }else{
            lastDestructions=battle.destructions;
            for(Battle.Ship ship:battle.ships())if(ship.alive())
                shieldStates.put(ship.id,ship.maxShields==0?0:ship.shields/ship.maxShields);
        }
        engines(battle,cx,cy,zoom,effectsVolume,active&&!battle.finished());
    }
    private static long lastSeen(Battle battle){
        List<Battle.VisualEvent> events=battle.visualEvents();
        return events.isEmpty()?0:events.get(events.size()-1).sequence();
    }
    @Override public void close(){stopEngines();for(Sound s:synth.values())s.dispose();synth.clear();}
}
