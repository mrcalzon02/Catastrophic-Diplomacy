package diplomacy.client;

import diplomacy.sim.Battle;

/** Pure, deterministic local damage marks. R=dark, G=hot, B=bright metal. */
final class ShipImpactProfiles {
    private ShipImpactProfiles(){}
    private static final float[] DX={.98f,.50f,-.38f,-.91f,-.46f,.47f,.82f};
    private static final float[] DY={.17f,.87f,.92f,.41f,-.89f,-.88f,-.57f};
    private static float clamp(float n){return Math.max(0f,Math.min(1f,n));}
    private static float smooth(float a,float b,float v){
        float t=clamp((v-a)/(b-a));return t*t*(3f-2f*t);
    }
    private static int pack(float dark,float hot,float metal){
        return Math.round(clamp(dark)*255f)<<16|
               Math.round(clamp(hot)*255f)<<8|Math.round(clamp(metal)*255f);
    }
    static int dark(int v){return v>>>16&255;}
    static int hot(int v){return v>>>8&255;}
    static int metal(int v){return v&255;}
    static int merge(int a,int b){
        return (Math.max(dark(a),dark(b))<<16)|
                (Math.max(hot(a),hot(b))<<8)|Math.max(metal(a),metal(b));
    }
    /** Compact shadowed pit, thin bright sheared rim, raised short fractures. */
    private static int pock(float x,float y,float s,int id,boolean heavy,float scale){
        float r=(float)StrictMath.hypot(x,y);
        float radius=(.22f+.23f*(float)StrictMath.sqrt(s))*scale;
        if(r>radius+.28f*scale)return 0;
        float q=r/Math.max(.01f,radius);
        float dark=s*.88f*(1-smooth(.12f,.56f,q));
        float bright=s*.96f*smooth(.42f,.63f,q)*(1-smooth(.90f,1.10f,q));
        // Break the metallic ring at deterministic chipped sectors.
        int chip=(id*13+((int)(x*42))*31+((int)(y*42))*17)&7;
        if(chip==0)bright*=.66f;
        for(int i=0;i<(heavy?7:4);i++){
            int k=Math.floorMod(id+i,DX.length);
            float along=x*DX[k]+y*DY[k];
            float across=Math.abs(x*DY[k]-y*DX[k]);
            float end=radius+(.10f+(i%3)*.045f)*scale;
            if(along>radius*.77f&&along<end&&across<.07f*scale)
                bright=Math.max(bright,s*.69f*(1-smooth(radius*.7f,end,along)));
        }
        return pack(dark,0,bright);
    }
    static int sample(Battle.DamageType kind,float row,float col,
                      float hr,float hc,float armor,float internal,
                      int seed,float rough){
        float intensity=clamp(Math.max(armor,internal*.65f));
        if(intensity<=0)return 0;
        float x=col-hc,y=row-hr;
        float d=(float)StrictMath.hypot(x,y);
        if(d>1.30f)return 0;
        if(kind==null||kind==Battle.DamageType.LASER)
            return pack(ShipDamageScorch.falloffAt(row,col,hr,hc,armor,internal,rough),0,0);
        switch(kind){
            case KINETIC:{
                boolean heavy=internal>.15f||armor>.86f;
                int p=pock(x,y,intensity,seed,heavy,1f);
                if(heavy){
                    // Heavy hits gain secondary PITS, not diffuse crush smears.
                    p=merge(p,pock(x-.24f,y+.18f,intensity*.68f,seed+19,false,.55f));
                    p=merge(p,pock(x+.23f,y+.27f,intensity*.60f,seed+29,false,.45f));
                }
                return p;
            }
            case MISSILE, TORPEDO:{
                float scale=kind==Battle.DamageType.TORPEDO?1.16f:1f;
                float q=d/scale;
                if(q>1.12f)return 0;
                float soot=intensity*.74f*(1-smooth(.17f,.96f,q));
                float heat=intensity*.32f*smooth(.24f,.46f,q)*(1-smooth(.65f,.9f,q));
                float chips=0;
                for(int k=0;k<5;k++){
                    float px=(k==0?.46f:k==1?-.52f:k==2?.14f:k==3?-.28f:.55f)*scale;
                    float py=(k==0?.14f:k==1?-.24f:k==2?-.66f:k==3?.53f:.41f)*scale;
                    chips=Math.max(chips,1-smooth(.025f,.13f*scale,
                            (float)StrictMath.hypot(x-px,y-py)));
                }
                return pack(soot,heat,chips*intensity*.75f);
            }
            case PLASMA:
                return d>.89f?0:pack(intensity*.31f*(1-smooth(.2f,.56f,d)),
                    intensity*(1-smooth(.17f,.89f,d)),0);
            case PARTICLE:
                return merge(pock(x,y,intensity*.68f,seed,false,.50f),
                        pock(x-.20f,y+.13f,intensity*.45f,seed+5,false,.38f));
            case FIGHTER:
                return pock(x,y,intensity*.48f,seed,false,.48f);
            default:return 0;
        }
    }
}
