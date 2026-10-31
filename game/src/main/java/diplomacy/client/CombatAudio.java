package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import diplomacy.sim.Battle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * One libGDX audio implementation for Android, Windows and Linux.
 * Pure combat data supplies the cues; this class never affects combat or RNG.
 * Limits polyphony aggressively for 200v200 engagements.
 */
public final class CombatAudio implements AutoCloseable {
    private static final int MAX_CUES_PER_FRAME = 4;
    private static final String PREF_NAME = "catastrophic-diplomacy-audio";
    private final Map<CombatAudioCue.Cue, Sound> cues = new EnumMap<>(CombatAudioCue.Cue.class);
    private final long[] lastPlayedTick = new long[CombatAudioCue.Cue.values().length];
    private final SpatialAudioRuntime spatial;
    private final AudioPitchVariants pitchVariants = new AudioPitchVariants();
    private final AnnouncerAudio announcer;
    private final RecordedHullVoiceAudio hullVoice;
    private Sound radioOpen, radioClose, uiHoverSound, uiPressSound, uiBackSound, uiBlockedSound, uiAdjustSound;
    private String lastLogEntry;
    private long lastRadioTick = -1000000L;
    private boolean radioFlip;
    private int radioVoiceSourceId;
    private Preferences prefs;
    private Music battleMusic, ambientMusic, activeMusic;
    private long seenSequence;
    private int seenDestructions;
    private boolean musicOn = true, effectsOn = true, useBattleMusic = true;
    private boolean suspended;
    private float musicVolume = 0.19f, effectsVolume = 0.42f, voiceVolume=1f;
    private long lastHoverSoundNs, lastAdjustSoundNs;

    public CombatAudio() {
        Arrays.fill(lastPlayedTick, -1000000L);
        try {
            prefs = Gdx.app.getPreferences(PREF_NAME);
            musicOn = prefs.getBoolean("music", true);
            effectsOn = prefs.getBoolean("effects", true);
            useBattleMusic = prefs.getBoolean("battleTrack", true);
            voiceVolume=VoiceVolumeControl.clamp(prefs.getFloat("voiceVolume",1f));
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Audio settings unavailable; using defaults", error);
        }
        load(CombatAudioCue.Cue.LASER, "audio/sfx/laser_zap.wav");
        load(CombatAudioCue.Cue.KINETIC, "audio/sfx/metal_wonk.wav");
        load(CombatAudioCue.Cue.PLASMA, "audio/sfx/plasma_pulse.wav");
        load(CombatAudioCue.Cue.PARTICLE, "audio/sfx/particle_arc.wav");
        load(CombatAudioCue.Cue.MISSILE, "audio/sfx/missile_launch.wav");
        load(CombatAudioCue.Cue.TORPEDO, "audio/sfx/torpedo_launch.wav");
        load(CombatAudioCue.Cue.SHIELD, "audio/sfx/shield_hit.wav");
        load(CombatAudioCue.Cue.HULL, "audio/sfx/hollow_metal.wav");
        load(CombatAudioCue.Cue.EXPLOSION, "audio/sfx/grenade.wav");
        load(CombatAudioCue.Cue.UI, "audio/sfx/ui_confirm.wav");
        spatial = new SpatialAudioRuntime(cues);
        announcer = new AnnouncerAudio();
        hullVoice = new RecordedHullVoiceAudio();
        announcer.setVolume(voiceVolume);hullVoice.setVolume(voiceVolume);
        radioOpen = loadRadio("radio_open");
        radioClose = loadRadio("radio_close");
        uiHoverSound=loadRadio("ui_hover");uiPressSound=loadRadio("ui_press");
        uiBackSound=loadRadio("ui_back");uiBlockedSound=loadRadio("ui_blocked");
        uiAdjustSound=loadRadio("ui_adjust");
        ambientMusic = loadMusic("audio/music/cold_unresolved_drone.mp3");
        battleMusic = loadMusic("audio/music/final_surge.mp3");
        switchTrack();
    }

    private void load(CombatAudioCue.Cue cue, String path) {
        try {
            if (!Gdx.files.internal(path).exists())
                throw new IllegalStateException("Missing audio asset: " + path);
            cues.put(cue, Gdx.audio.newSound(Gdx.files.internal(path)));
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Unable to load " + path, error);
        }
    }
    private Sound loadRadio(String name) {
        try {
            com.badlogic.gdx.files.FileHandle file = Gdx.files.local("audio/synth/v2/"+name+".wav");
            byte[] bytes = SynthesizedSounds.wav(name);
            if (!file.exists() || file.length() != bytes.length) file.writeBytes(bytes,false);
            return Gdx.audio.newSound(file);
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Optional radio tone unavailable: "+name,error);
            return null;
        }
    }
    private Music loadMusic(String path) {
        try {
            if (!Gdx.files.internal(path).exists())
                throw new IllegalStateException("Missing soundtrack: " + path);
            Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
            music.setLooping(true);
            music.setVolume(musicVolume);
            return music;
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Unable to load " + path, error);
            return null;
        }
    }

    private void switchTrack() {
        Music wanted = useBattleMusic ? battleMusic : ambientMusic;
        if (wanted == null) wanted = useBattleMusic ? ambientMusic : battleMusic;
        if (activeMusic != wanted && activeMusic != null) activeMusic.stop();
        activeMusic = wanted;
        if (activeMusic != null) {
            activeMusic.setVolume(musicVolume);
            if (musicOn && !suspended && !activeMusic.isPlaying()) {
                try { activeMusic.play(); }
                catch (RuntimeException error) { Gdx.app.error("AUDIO", "Music playback failed", error); }
            }
        }
    }

    private void persist() {
        if (prefs == null) return;
        try {
            prefs.putBoolean("music", musicOn);
            prefs.putBoolean("effects", effectsOn);
            prefs.putBoolean("battleTrack", useBattleMusic);
            prefs.putFloat("voiceVolume",voiceVolume);
            prefs.flush();
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Cannot persist audio settings", error);
        }
    }

    public boolean musicEnabled() { return musicOn; }
    public boolean effectsEnabled() { return effectsOn; }
    public boolean battleTrack() { return useBattleMusic; }
    public float voiceVolume(){return voiceVolume;}
    /** Drag previews are instantaneous, without a filesystem write on every pixel. */
    public void setVoiceVolume(float gain,boolean save){
        voiceVolume=VoiceVolumeControl.clamp(gain);
        announcer.setVolume(voiceVolume);hullVoice.setVolume(voiceVolume);
        if(save)persist();
    }
    public void stepVoiceVolume(int direction){
        setVoiceVolume(VoiceVolumeControl.step(voiceVolume,direction),true);uiAdjust();
    }
    public void saveVoiceVolume(){persist();}
    public void toggleMusic() {
        musicOn = !musicOn;
        if (!musicOn && activeMusic != null) activeMusic.pause();
        else switchTrack();
        persist();
    }
    public void toggleEffects() { effectsOn = !effectsOn; if(!effectsOn)spatial.silence(); persist(); }
    public void toggleTrack() { useBattleMusic = !useBattleMusic; switchTrack(); persist(); }

    /** Announcements are the game narrator only; ship RADIO events remain text plus quiet bleeps. */
    public void announce(AnnouncerAudio.Cue cue) {
        if (voiceVolume>0f && !suspended) { hullVoice.stop(); announcer.play(cue, false); }
    }
    public void announcePriority(AnnouncerAudio.Cue cue) {
        if (voiceVolume>0f && !suspended) { hullVoice.stop(); announcer.play(cue, true); }
    }

    public void resetForBattle() {
        seenSequence=0;seenDestructions=0;
        Arrays.fill(lastPlayedTick,-1000000L);
        spatial.reset();
        pitchVariants.reset();
        lastLogEntry = null;
        lastRadioTick = -1000000L;
        radioFlip = false;
        radioVoiceSourceId=0;
        hullVoice.reset();
    }

    /**
     * Camera-aware mixer: proximity, zoom and event priority replace global,
     * identical-volume sounds. Music remains independent of combat SFX.
     */
    public void advance(Battle battle,double cameraX,double cameraY,float zoom,
                        boolean battleVisible) {
        announcer.advance();
        hullVoice.advance(nearbyVoice(battle,cameraX,cameraY,zoom,battleVisible),
                Gdx.graphics==null?1f/60f:Gdx.graphics.getDeltaTime());
        spatial.advance(battle,cameraX,cameraY,zoom,battleVisible&&!suspended,
                effectsOn,effectsVolume);
        radioBleep(battle,battleVisible,cameraX,cameraY,zoom);
    }

    private float nearbyVoice(Battle battle,double cx,double cy,float zoom,boolean visible){
        if(!visible||suspended||battle==null||radioVoiceSourceId==0)return 0;
        for(var ship:battle.ships())if(ship.id==radioVoiceSourceId)
            return ship.alive()?RadioVoiceProximity.target(cx,cy,zoom,ship.x,ship.y):0;
        return 0;
    }
    private void radioBleep(Battle battle, boolean visible,double cx,double cy,float zoom) {
        if (battle == null) return;
        java.util.List<String> log = battle.events();
        if (log.isEmpty()) return;
        int start = 0;
        if (lastLogEntry != null) {
            for (int i=log.size()-1;i>=0;i--) {
                if (lastLogEntry.equals(log.get(i))) { start=i+1; break; }
            }
        }
        // Consume unseen entries even while muted, paused, or viewing menus.
        lastLogEntry = log.get(log.size()-1);
        if (!visible || suspended) return;
        boolean received = false;
        String hullCue = null;
        int closestSourceId=0;
        float closestGain=.09f;
        for (int i=start;i<log.size();i++) {
            String line = log.get(i);
            if (line.startsWith("RADIO ")) {
                received = true;
                var origin=battle.radioSourceAt(i);
                if(origin!=null&&origin.alive()&&
                        RecordedHullVoiceAudio.assetPathForRadio(line)!=null){
                    float proximity=RadioVoiceProximity.target(cx,cy,zoom,origin.x,origin.y);
                    if(proximity>closestGain){
                        hullCue=line;closestSourceId=origin.id;closestGain=proximity;
                    }
                }
            }
        }
        // The original recording is rare cockpit speech, not 400 simultaneous pilots.
        // Ordinary ship messages continue to receive quiet radio bleeps.
        if(voiceVolume>0f&&hullCue!=null&&!announcer.playing()&&hullVoice.play(hullCue,battle.tick))
            radioVoiceSourceId=closestSourceId;
        if (!effectsOn || !received || battle.tick-lastRadioTick<45) return;
        Sound tone = radioFlip ? radioClose : radioOpen;
        radioFlip = !radioFlip;
        lastRadioTick = battle.tick;
        if (tone != null) {
            try {
                tone.play(effectsVolume*0.28f,
                        pitchVariants.next(radioFlip?"radio_open":"radio_close",
                                AudioPitchVariants.Profile.RADIO),0f);
            }
            catch (RuntimeException error) { Gdx.app.error("AUDIO", "Radio cue failed",error); }
        }
    }

    public void ui() {
        // UI events have no simulation tick; they intentionally bypass battle cooldown.
        if (!effectsOn || suspended) return;
        Sound click = cues.get(CombatAudioCue.Cue.UI);
        if (click != null) {
            try {
                click.play(effectsVolume * 0.42f,
                        pitchVariants.next("ui_confirm",AudioPitchVariants.Profile.UI),0f);
            }
            catch (RuntimeException error) { Gdx.app.error("AUDIO", "UI cue failed", error); }
        }
    }

    private void uiSound(Sound sound,float level,String cue){
        if(!effectsOn||suspended||sound==null)return;
        try{
            sound.play(effectsVolume*level,
                    pitchVariants.next(cue,AudioPitchVariants.Profile.UI),0f);
        }catch(RuntimeException err){
            if(Gdx.app!=null)Gdx.app.error("AUDIO","UI feedback failed",err);
        }
    }
    public void uiHover(){
        long now=System.nanoTime();
        if(now-lastHoverSoundNs<90_000_000L)return;
        lastHoverSoundNs=now;uiSound(uiHoverSound,.16f,"ui_hover");
    }
    public void uiPress(){uiSound(uiPressSound,.35f,"ui_press");}
    public void uiBack(){uiSound(uiBackSound,.34f,"ui_back");}
    public void uiBlocked(){uiSound(uiBlockedSound,.29f,"ui_blocked");}
    public void uiAdjust(){
        long now=System.nanoTime();
        if(now-lastAdjustSoundNs<65_000_000L)return;
        lastAdjustSoundNs=now;uiSound(uiAdjustSound,.28f,"ui_adjust");
    }
    public void pause() {
        suspended = true;
        spatial.silence();
        announcer.pause();
        hullVoice.pause();
        if (activeMusic != null) activeMusic.pause();
    }
    public void resume() { suspended = false; announcer.resume(); hullVoice.resume(); switchTrack(); }

    @Override public void close() {
        announcer.close();
        hullVoice.close();
        spatial.close();
        if (radioOpen != null) radioOpen.dispose();
        if (radioClose != null) radioClose.dispose();
        if(uiHoverSound!=null)uiHoverSound.dispose();
        if(uiPressSound!=null)uiPressSound.dispose();
        if(uiBackSound!=null)uiBackSound.dispose();
        if(uiBlockedSound!=null)uiBlockedSound.dispose();
        if(uiAdjustSound!=null)uiAdjustSound.dispose();
        if (activeMusic != null) activeMusic.stop();
        for (Sound sound : cues.values()) sound.dispose();
        cues.clear();
        if (battleMusic != null) battleMusic.dispose();
        if (ambientMusic != null && ambientMusic != battleMusic) ambientMusic.dispose();
    }
}
