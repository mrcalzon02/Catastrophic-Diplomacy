package diplomacy.client;

import java.util.Arrays;

/** Headless zoom/distance, polyphony-policy and PCM synthesis acceptance. */
public final class SpatialAudioTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int le16(byte[] b,int at){return (b[at]&255)|((b[at+1]&255)<<8);}
    private static int le32(byte[] b,int at){
        return (b[at]&255)|((b[at+1]&255)<<8)|((b[at+2]&255)<<16)|((b[at+3]&255)<<24);
    }
    public static void main(String[] args){
        var close=SpatialAudioModel.at(SpatialAudioModel.Layer.DETAIL,500,350,0.35,500,350);
        var medium=SpatialAudioModel.at(SpatialAudioModel.Layer.DETAIL,500,350,1.2,500,350);
        var far=SpatialAudioModel.at(SpatialAudioModel.Layer.DETAIL,500,350,3,500,350);
        require(close.audible(),"Individual thrusters audible under close inspection");
        require(!medium.audible()&&!far.audible(),"No distant individual engines");
        var heavy=SpatialAudioModel.at(SpatialAudioModel.Layer.CAPITAL,500,350,3.4,560,350);
        require(heavy.audible(),"Capital guns remain audible from fleet overview");
        var beyond=SpatialAudioModel.at(SpatialAudioModel.Layer.TACTICAL,500,350,0.5,3600,350);
        require(!beyond.audible(),"Off-screen small weapons culled");
        var left=SpatialAudioModel.at(SpatialAudioModel.Layer.TACTICAL,500,350,0.5,350,350);
        var right=SpatialAudioModel.at(SpatialAudioModel.Layer.TACTICAL,500,350,0.5,650,350);
        require(left.pan()<0&&right.pan()>0,"Stereo spatial panning");
        require(SpatialAudioModel.fleetBedGain(400,2)>SpatialAudioModel.fleetBedGain(4,2),
                "Aggregate fleet machinery scales with ship count");
        require(!SpatialAudioModel.enginesAudible(3),"Strategic camera suppresses individual engines");
        for(String name:SynthesizedSounds.NAMES){
            byte[] wav=SynthesizedSounds.wav(name);
            require(wav.length>3000,"Nonempty "+name);
            require(wav[0]=='R'&&wav[1]=='I'&&wav[8]=='W'&&wav[9]=='A',"WAV header "+name);
            require(le32(wav,4)==wav.length-8&&le16(wav,22)==1,"RIFF size and mono "+name);
            require(le32(wav,24)==22050&&le16(wav,34)==16,"Portable 22 kHz PCM "+name);
            int peak=0;long energy=0;
            for(int i=44;i<wav.length;i+=2){
                int sample=(short)le16(wav,i);peak=Math.max(peak,Math.abs(sample));energy+=(long)sample*sample;
            }
            require(peak>400&&peak<31000&&energy>100000000L,"Audible unclipped "+name);
        }
        require(!Arrays.equals(SynthesizedSounds.wav("engine_loop"),SynthesizedSounds.wav("fleet_bed")),
                "Engine and fleet bed have different timbres");
        System.out.println("PASS spatial zoom culling, stereo, fleet audio and 9 synthesized WAV types");
    }
}
