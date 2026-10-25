package diplomacy.client;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic 22.05 kHz mono PCM sound design for game-internal machinery cues.
 * Produces valid WAVs on first run, so Android/Windows/Linux package the same code
 * rather than ship platform-dependent synthesizers or native audio libraries.
 */
public final class SynthesizedSounds {
    public static final int RATE = 22050;
    public static final String[] NAMES = {
        "engine_loop", "fleet_bed", "thruster", "shield_up", "shield_down",
        "heavy_fire", "reactor_hit", "metal_creak", "pd_tick"
    };
    private SynthesizedSounds() { }

    private static double duration(String name) {
        return switch (name) {
            case "engine_loop" -> 2.4;
            case "fleet_bed" -> 3.2;
            case "thruster" -> 0.54;
            case "shield_up" -> 0.66;
            case "shield_down" -> 0.90;
            case "heavy_fire" -> 1.04;
            case "reactor_hit" -> 1.15;
            case "metal_creak" -> 0.90;
            case "pd_tick" -> 0.20;
            default -> throw new IllegalArgumentException("Unknown synthesized cue: "+name);
        };
    }
    private static double sin(double cycles) { return Math.sin(2*Math.PI*cycles); }
    private static double env(double t,double len,double attack,double release) {
        return Math.min(1,Math.min(t/attack,(len-t)/release));
    }
    private static void ascii(byte[] buf,int offset,String s) {
        byte[] bytes=s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes,0,buf,offset,bytes.length);
    }
    private static void le16(byte[] b,int i,int n){b[i]=(byte)n;b[i+1]=(byte)(n>>>8);}
    private static void le32(byte[] b,int i,int n){
        b[i]=(byte)n;b[i+1]=(byte)(n>>>8);b[i+2]=(byte)(n>>>16);b[i+3]=(byte)(n>>>24);
    }
    public static byte[] wav(String name) {
        double len=duration(name);
        int count=(int)(len*RATE);
        byte[] wav=new byte[44+count*2];
        ascii(wav,0,"RIFF");le32(wav,4,wav.length-8);ascii(wav,8,"WAVE");
        ascii(wav,12,"fmt ");le32(wav,16,16);le16(wav,20,1);le16(wav,22,1);
        le32(wav,24,RATE);le32(wav,28,RATE*2);le16(wav,32,2);le16(wav,34,16);
        ascii(wav,36,"data");le32(wav,40,count*2);
        long state=0x4F6F53797374656DL;
        double noiseSmooth=0;
        for(int i=0;i<count;i++) {
            double t=(double)i/RATE, p=t/len;
            state=state*6364136223846793005L+1442695040888963407L;
            double white=((state>>>32)&0xffffffffL)/2147483648.0-1;
            noiseSmooth=0.977*noiseSmooth+0.023*white;
            double out;
            switch(name) {
                case "engine_loop" -> {
                    double hum=0.46*sin(40*t)+0.22*sin(80*t)+0.14*sin(120*t);
                    out=0.56*hum*(0.91+0.09*sin(5*t))+noiseSmooth*0.47;
                }
                case "fleet_bed" -> {
                    double bass=0.32*sin(32.5*t)+0.21*sin(65*t)+0.12*sin(97.5*t);
                    out=bass*(0.75+0.18*sin(1.25*t))+noiseSmooth*0.85;
                }
                case "thruster" -> {
                    double e=env(t,len,0.025,0.24);
                    out=e*(0.32*sin((92+60*(1-p))*t)+0.22*sin(170*t)
                        +0.45*white*(1-p)+noiseSmooth*0.70);
                }
                case "shield_up" -> {
                    double e=env(t,len,0.014,0.20);
                    double phase=175*t+640*t*t;
                    out=e*(0.30*sin(phase)+0.25*sin(phase*1.51)
                        +0.12*sin(phase*0.51)+0.12*white*(1-p));
                }
                case "shield_down" -> {
                    double e=env(t,len,0.006,0.29);
                    double phase=870*t-490*t*t;
                    out=e*(0.43*sin(phase)+0.24*sin(phase*0.50)
                        +0.18*noiseSmooth+0.19*white*Math.exp(-7*t));
                }
                case "heavy_fire" -> {
                    double e=env(t,len,0.005,0.62);
                    double phase=145*t-52*t*t;
                    out=e*(0.52*sin(phase)+0.27*sin(phase*0.5)
                        +0.26*noiseSmooth+0.28*white*Math.exp(-15*t));
                }
                case "reactor_hit" -> {
                    double e=env(t,len,0.004,0.67);
                    out=e*(0.48*sin(57*t-21*t*t)+0.32*sin(120*t-36*t*t)
                        +0.46*noiseSmooth+0.26*white*Math.exp(-20*t));
                }
                case "metal_creak" -> {
                    double e=env(t,len,0.08,0.23);
                    out=e*(0.35*sin(310*t+85*Math.sin(t*7))
                        +0.23*sin(164*t-35*t*t)+0.28*noiseSmooth);
                }
                case "pd_tick" -> {
                    double e=env(t,len,0.002,0.09);
                    out=e*(0.30*sin(1050*t+130*t*t)+0.20*sin(490*t)
                        +0.16*white*Math.exp(-18*t));
                }
                default -> throw new IllegalArgumentException(name);
            }
            // Multiplying the loop tail by a short seamless crossfade avoids click pops.
            if(name.endsWith("loop")||name.equals("fleet_bed")) {
                double fade= Math.min(1, Math.min(t/0.045,(len-t)/0.045));
                out*=Math.max(0,fade);
            }
            int sample=(int)Math.round(Math.max(-0.93,Math.min(0.93,out))*32767);
            le16(wav,44+i*2,sample);
        }
        return wav;
    }
}
