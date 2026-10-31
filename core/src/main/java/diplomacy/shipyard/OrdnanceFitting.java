package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnanceGuidance;
import diplomacy.sim.OrdnancePropulsion;

/** Guided profiles can be shared by legacy family controllers or owned by one rack.
 * The weakest fitted weapon module limits a shared family configuration.
 * Fitted research itself is authorized by the existing game-mode gate.
 */
public final class OrdnanceFitting {
    private OrdnanceFitting(){}
    public record Guided(OrdnanceDoctrine.Mode salvo,
                         OrdnancePropulsion.Mode motor,
                         OrdnanceGuidance.Mode guidance) {
        public static final Guided STANDARD=new Guided(OrdnanceDoctrine.Mode.SINGLE,
                OrdnancePropulsion.Mode.CONSTANT_BURN,OrdnanceGuidance.Mode.PURSUIT);
        public Guided {
            if(salvo==null||motor==null||guidance==null)
                throw new IllegalArgumentException("Incomplete guided launcher profile");
        }
    }
    /** Physical magazine payload and guidance belong to one launcher socket. */
    public record Mounted(Guided guided,diplomacy.sim.AmmunitionTable.Warhead warhead){
        public Mounted {
            if(guided==null)throw new IllegalArgumentException("No guided rack");
            diplomacy.sim.AmmunitionTable.warhead(warhead);
        }
    }
    /** Never let another launcher lend its research to this socket. */
    public static void requireAt(Shipyard.Design design,int slot,Mounted mounted){
        if(design==null||mounted==null||slot<0||slot>=design.slots())
            throw new IllegalArgumentException("Invalid mounted launcher");
        Shipyard.Installed installed=design.at(slot);
        if(installed==null)throw new IllegalArgumentException("Missing mounted launcher");
        Battle.DamageType family=installed.component.endsWith("-MSL")?
                Battle.DamageType.MISSILE:installed.component.endsWith("-TOR")?
                Battle.DamageType.TORPEDO:null;
        if(family==null)throw new IllegalArgumentException("Socket is not guided weaponry");
        Shipyard.Track branch=installed.branch();
        int rank=branch==null?0:installed.stage(branch);
        if(rank<requiredTier(mounted.guided()))
            throw new IllegalArgumentException("Socket "+slot+" needs guided research "
                    +requiredTier(mounted.guided())+" but has "+rank);
        if(!Shipyard.ammunitionAt(design,slot).allows(family,mounted.warhead()))
            throw new IllegalArgumentException("Socket "+slot+" has no warhead research for "
                    +mounted.warhead());
    }
    public static int requiredTier(OrdnanceDoctrine.Mode mode){
        if(mode==null)throw new IllegalArgumentException("No salvo choice");
        return switch(mode){
            case SINGLE -> 0;
            case RIPPLE,COLD_LAUNCH -> 1;
            case SATURATION,HEAVY_STRIKE -> 2;
            case MICRO_SWARM,DECOY_CLOUD -> 3;
        };
    }
    public static int requiredTier(OrdnancePropulsion.Mode mode){
        if(mode==null)throw new IllegalArgumentException("No motor choice");
        return switch(mode){
            case CONSTANT_BURN -> 0;
            case COLD_START -> 1;
            case BOOST_COAST_TERMINAL -> 2;
            case PULSE_BURN,TERMINAL_SPRINT -> 3;
            case SILENT_GHOST -> 4;
        };
    }
    public static int requiredTier(OrdnanceGuidance.Mode mode){
        if(mode==null)throw new IllegalArgumentException("No seeker choice");
        return switch(mode){
            case PURSUIT -> 0;
            case LEAD_INTERCEPT -> 1;
            case DOGLEG -> 2;
            case PINCER -> 3;
            case EVASIVE_WEAVE -> 4;
        };
    }
    public static int requiredTier(Guided profile){
        if(profile==null)throw new IllegalArgumentException("No guided profile");
        return StrictMath.max(requiredTier(profile.salvo()),
                StrictMath.max(requiredTier(profile.motor()),requiredTier(profile.guidance())));
    }
    public static int fittedTier(Shipyard.Design design,Battle.DamageType family){
        if(design==null)throw new IllegalArgumentException("No ship design");
        String suffix=suffix(family);
        int minimum=Integer.MAX_VALUE;
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed mount=design.at(i);
            if(mount==null||!mount.component.endsWith(suffix))continue;
            Shipyard.Track active=mount.branch();
            int level=active==null?0:mount.stage(active);
            minimum=StrictMath.min(minimum,level);
        }
        return minimum==Integer.MAX_VALUE?-1:minimum;
    }
    public static void requireAllowed(Shipyard.Design design,
                                       Battle.DamageType family,Guided profile){
        int available=fittedTier(design,family);
        if(profile==null)throw new IllegalArgumentException("Missing launcher configuration");
        if(available<0){
            if(!Guided.STANDARD.equals(profile))
                throw new IllegalArgumentException("No "+family+" launcher installed");
        }else if(available<requiredTier(profile))
            throw new IllegalArgumentException("Guided "+family+" configuration needs level "
                    +requiredTier(profile)+" technology on every fitted launcher; weakest is "
                    +available);
    }
    public static String suffix(Battle.DamageType family){
        if(family==Battle.DamageType.MISSILE)return "-MSL";
        if(family==Battle.DamageType.TORPEDO)return "-TOR";
        throw new IllegalArgumentException("Not a guided ordnance family: "+family);
    }
}
