package diplomacy.fx;

import diplomacy.sim.Battle;
import java.util.EnumMap;
import java.util.Map;

/**
 * Three independently authored visual stages for one weapon family.
 * This is a deterministic timeline specification, not a weapon damage model.
 * All imagery references pre-existing or curated artwork; no primitive blobs are generated.
 */
public final class WeaponFxProfile {
    private WeaponFxProfile() { }
    public enum Stage { FIRING, PAYLOAD, IMPACT }
    public enum Motion { HITSCAN, GUIDED, BALLISTIC, CRAFT }
    /**
     * Source path pattern inside Gdx.files.internal assets; "%02d" expands to 1-based frame.
     * An empty pattern disables this stage until its source-art cutouts are reviewed.
     */
    public record Clip(String pattern, int frames, int ticksPerFrame, boolean looping,
                       float width, float height) {
        public Clip {
            if(pattern==null || (pattern.isEmpty() && frames!=0)
                    || (!pattern.isEmpty() && (!pattern.startsWith("effects/") || !pattern.contains("%02d")
                         || pattern.contains("..") || frames<1 || frames>96))
                    || ticksPerFrame<1 || ticksPerFrame>120 || width<0 || height<0
                    || !Float.isFinite(width) || !Float.isFinite(height))
                throw new IllegalArgumentException("Invalid FX clip metadata");
        }
        public boolean present(){return frames>0;}
        public int frame(long elapsedTicks){
            if(!present() || elapsedTicks<0)return -1;
            long position=elapsedTicks/ticksPerFrame;
            if(!looping && position>=frames)return -1;
            return (int)(looping?position%frames:position);
        }
        public String path(int index){
            if(index<0 || index>=frames)throw new IllegalArgumentException("Frame out of bounds");
            return String.format(java.util.Locale.ROOT,pattern,index+1);
        }
    }
    public record Profile(String id, Motion motion, Clip firing, Clip payload, Clip impact,
                          Clip intercept) {
        public Profile {
            if(id==null||id.isBlank()||motion==null||firing==null||payload==null
                    ||impact==null||intercept==null)throw new IllegalArgumentException("Incomplete FX profile");
        }
        public Clip stage(Stage stage){
            return switch(stage){case FIRING -> firing;case PAYLOAD -> payload;case IMPACT -> impact;};
        }
    }
    public static final Clip NONE = new Clip("",0,1,false,0,0);
    private static Clip seq(String family,String phase,int frames,int step,float width,float height){
        return new Clip("effects/weapon_fx/"+family+"/"+phase+"_%02d.png",frames,step,false,width,height);
    }
    private static Clip beam(String color){
        return new Clip("effects/beams/"+color+"_%02d.png",7,1,true,0,0);
    }
    // The existing 35 beam PNGs are payload art; uncurated firing/impact assets
    // are optional, NOT substituted with a stretched projectile or procedural pill.
    private static final Map<Battle.DamageType,Profile> DEFAULTS=build();
    private static Map<Battle.DamageType,Profile> build(){
        EnumMap<Battle.DamageType,Profile> byType=new EnumMap<>(Battle.DamageType.class);
        byType.put(Battle.DamageType.LASER,
                new Profile("laser",Motion.HITSCAN,seq("laser","firing",8,1,24,24),beam("blue"),
                        seq("laser","impact",8,1,34,34),NONE));
        byType.put(Battle.DamageType.KINETIC,
                new Profile("kinetic",Motion.BALLISTIC,seq("kinetic","firing",8,1,20,20),
                        new Clip("effects/weapon_fx/kinetic/payload_%02d.png",8,2,true,25,10),
                        seq("kinetic","impact",8,1,28,28),
                        new Clip("effects/weapon_fx/kinetic/impact_%02d.png",8,1,false,17,17)));
        byType.put(Battle.DamageType.PLASMA,
                new Profile("plasma",Motion.HITSCAN,seq("plasma","firing",7,1,28,28),beam("red"),
                        seq("plasma","impact",8,1,42,42),NONE));
        byType.put(Battle.DamageType.PARTICLE,
                new Profile("particle",Motion.HITSCAN,seq("particle","firing",8,1,25,25),beam("violet"),
                        seq("particle","impact",8,1,36,36),NONE));
        byType.put(Battle.DamageType.MISSILE,
                new Profile("missile",Motion.GUIDED,seq("missile","firing",6,1,22,22),
                        new Clip("effects/weapon_fx/missile/payload_%02d.png",1,1,true,36,18),seq("missile","impact",10,1,42,42),
                        seq("missile","intercept",8,1,25,25)));
        byType.put(Battle.DamageType.TORPEDO,
                new Profile("torpedo",Motion.GUIDED,seq("torpedo","firing",8,1,28,28),
                        new Clip("effects/weapon_fx/torpedo/payload_%02d.png",1,1,true,48,22),seq("torpedo","impact",12,1,58,58),
                        seq("torpedo","intercept",8,1,33,33)));
        byType.put(Battle.DamageType.FIGHTER,
                new Profile("fighter",Motion.CRAFT,seq("fighter","firing",8,1,24,24),
                        NONE,seq("fighter","impact",7,1,22,22),NONE));
        return Map.copyOf(byType);
    }
    public static Profile forType(Battle.DamageType type){return DEFAULTS.get(type);}
    public static Profile forWeapon(String weapon){
        if(weapon==null)return null;
        if(weapon.equals("POINT_DEFENSE"))
            return new Profile("point_defense",Motion.HITSCAN,
                    new Clip("effects/weapon_fx/laser/firing_%02d.png",8,1,false,14,14),
                    NONE,NONE,NONE);
        if(weapon.equals("DRONE"))return new Profile("drone",Motion.CRAFT,
                seq("drone","firing",8,1,22,22),NONE,NONE,NONE);
        try{return forType(Battle.DamageType.valueOf(weapon));}
        catch(IllegalArgumentException unknown){return null;}
    }
}
