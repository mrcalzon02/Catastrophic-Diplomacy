package diplomacy.client;

/** Camera sweep, zoom and audio-envelope tests: no OpenGL/audio device required. */
public final class RadioVoiceProximityTest {
    private static void check(boolean result,String reason){
        if(!result)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        float near=RadioVoiceProximity.target(500,300,.75,500,300);
        float middle=RadioVoiceProximity.target(500,300,.75,840,300);
        float far=RadioVoiceProximity.target(500,300,.75,3000,300);
        check(near>.99f&&middle>0&&middle<near&&far==0,
                "Radio volume must decrease smoothly with camera distance");
        check(RadioVoiceProximity.target(840,300,.75,840,300)>middle,
                "Panning toward a ship fades its voice into the foreground");
        check(RadioVoiceProximity.target(500,300,.75,840,300)<
                RadioVoiceProximity.target(500,300,2.2,840,300),
                "Zoom changes world-space acoustic coverage");
        float gain=0;
        for(int i=0;i<60;i++) {
            float newGain=RadioVoiceProximity.step(gain,near,1f/60);
            check(newGain>=gain && newGain<=near, "Fade-in must be monotonic");
            gain=newGain;
        }
        check(gain>.98f,"Radio should fade in within one second");
        for(int i=0;i<60;i++)gain=RadioVoiceProximity.step(gain,0f,1f/60);
        check(gain<.02f,"Radio should fade out within one second");
        check(RadioVoiceProximity.target(0,0,0,0,0)==0,
                "Invalid zoom must mute radio");
        check(RadioVoiceProximity.target(0,0,1,Double.NaN,0)==0,
                "Invalid ship coordinate must mute radio");
        System.out.println("PASS: ship radio camera-proximity fades in/out smoothly");
    }
}