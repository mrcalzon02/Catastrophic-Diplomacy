package diplomacy.client;
import java.nio.charset.StandardCharsets;
public final class VoiceMenuControlsTest{
    public static void main(String[] args){
        if(!VoiceVolumeControl.hit(405,544)||!VoiceVolumeControl.hit(875,616)||
                VoiceVolumeControl.hit(876,616)||VoiceVolumeControl.hit(500,617))
            throw new AssertionError("Volume slider hit box");
        near(VoiceVolumeControl.valueAt(427),0);
        near(VoiceVolumeControl.valueAt(853),1);
        near(VoiceVolumeControl.valueAt(-100),0);
        near(VoiceVolumeControl.valueAt(2000),1);
        near(VoiceVolumeControl.step(.5f,1),.55f);
        near(VoiceVolumeControl.step(.5f,-1),.45f);
        near(VoiceVolumeControl.step(1,1),1);
        near(VoiceVolumeControl.step(0,-1),0);
        near(VoiceVolumeControl.clamp(Float.NaN),1);
        for(String name:new String[]{"ui_hover","ui_press","ui_back","ui_blocked","ui_adjust"}){
            byte[] wav=SynthesizedSounds.wav(name);
            if(wav.length<2500||
                    !new String(wav,0,4,StandardCharsets.US_ASCII).equals("RIFF")||
                    !new String(wav,8,4,StandardCharsets.US_ASCII).equals("WAVE"))
                throw new AssertionError("Invalid sound "+name);
            if(!java.util.Arrays.equals(wav,SynthesizedSounds.wav(name)))
                throw new AssertionError("Nondeterministic sound "+name);
        }
        System.out.println("PASS voice slider and UI tones");
    }
    private static void near(float a,float b){
        if(Math.abs(a-b)>.0001f)throw new AssertionError(a+" != "+b);
    }
}
