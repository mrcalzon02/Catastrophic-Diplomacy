package diplomacy.shipyard;

import diplomacy.sim.CampaignTechnology;
import diplomacy.tech.TechnologyCatalog;
import java.util.Locale;

/**
 * A shipyard view of already researched technology, not a research purchaser.
 * The resulting immutable Shipyard.Design contains the exact per-socket fitted
 * branch and stage: building another hull or researching later never changes it.
 */
public final class ShipTechnologyFit {
    private ShipTechnologyFit(){}

    public enum Scope { SOCKET, MATCHING_COMPONENTS }

    public static String id(Shipyard.Installed part,Shipyard.Track track,int stage){
        if(part==null||track==null||stage<1||stage>9)
            throw new IllegalArgumentException("Select an installed component and technology stage I-IX");
        return part.component.replaceFirst("^BASE-","TECH-")+"-"+track.name()+"-"+
                String.format(Locale.ROOT,"%02d",stage);
    }

    public static boolean available(Shipyard.Design design,TechnologyCatalog catalog,
                                    CampaignTechnology.State discoveries,
                                    int slot,Shipyard.Track track,int stage){
        if(design==null||catalog==null||track==null||slot<0||slot>=design.slots()||
                stage<1||stage>9)return false;
        Shipyard.Installed part=design.at(slot);
        if(part==null||!catalog.hasBase(part.component))return false;
        String techId=id(part,track,stage);
        var tech=catalog.get(techId);
        return tech!=null&&(discoveries==null||
                (discoveries.faction()==design.faction&&discoveries.has(techId)));
    }

    public static int highestAvailable(Shipyard.Design design,TechnologyCatalog catalog,
                                       CampaignTechnology.State discoveries,
                                       int slot,Shipyard.Track track){
        for(int stage=9;stage>=1;stage--)
            if(available(design,catalog,discoveries,slot,track,stage))return stage;
        return 0;
    }

    private static Shipyard.Design replace(Shipyard.Design draft,int slot,
                                           Shipyard.Track track,int stage){
        Shipyard.Installed before=draft.at(slot);
        if(before.branch()==track&&before.stage(track)==stage)return draft;
        // Branches are mutually exclusive on a socket. A deliberate fitting
        // change resets only that socket; sibling weapons are not touched.
        if(before.branch()!=null&&before.branch()!=track)
            draft=draft.stage(slot,before.branch(),0);
        return draft.stage(slot,track,stage);
    }

    /** Apply an explicitly selected, already-owned technology to one socket
     * or each identical base component. Does not debit any research currency. */
    public static Shipyard.Design fit(Shipyard.Design original,TechnologyCatalog catalog,
                                     CampaignTechnology.State discoveries,int slot,
                                     Shipyard.Track track,int stage,Scope scope){
        if(original==null||catalog==null||scope==null||slot<0||slot>=original.slots()||
                track==null||stage<1||stage>9||original.at(slot)==null)
            throw new IllegalArgumentException("Choose a fitted technology target");
        if(!available(original,catalog,discoveries,slot,track,stage))
            throw new IllegalArgumentException("Technology not discovered: "+
                    id(original.at(slot),track,stage));
        String selectedId=original.at(slot).component;
        Shipyard.Design result=original;
        for(int i=0;i<original.slots();i++){
            Shipyard.Installed installed=original.at(i);
            if(installed==null||(scope==Scope.SOCKET&&i!=slot)||
                    (scope==Scope.MATCHING_COMPONENTS&&!selectedId.equals(installed.component)))
                continue;
            result=replace(result,i,track,stage);
        }
        return result;
    }

    /** Ship-wide doctrine preset. Only currently available family/stage unlocks
     * are used. Modules without that discovered branch are left unchanged, not
     * uninstalled or silently downgraded. */
    public static Shipyard.Design doctrine(Shipyard.Design original,TechnologyCatalog catalog,
                                          CampaignTechnology.State discoveries,
                                          Shipyard.Track track){
        if(original==null||catalog==null||track==null)
            throw new IllegalArgumentException("Choose a ship-wide engineering doctrine");
        Shipyard.Design result=original;
        for(int i=0;i<original.slots();i++){
            if(original.at(i)==null)continue;
            int stage=highestAvailable(original,catalog,discoveries,i,track);
            if(stage>0)result=replace(result,i,track,stage);
        }
        return result;
    }


    /** Explicit challenge-tier authorization; never reads campaign discoveries. */
    public static boolean available(Shipyard.Design design,TechnologyCatalog catalog,
                                    SkirmishTechnologyRules rules,
                                    int slot,Shipyard.Track track,int stage){
        return rules!=null&&rules.permits(design,catalog,slot,track,stage);
    }
    public static int highestAvailable(Shipyard.Design design,TechnologyCatalog catalog,
                                       SkirmishTechnologyRules rules,
                                       int slot,Shipyard.Track track){
        for(int stage=9;stage>=1;stage--)
            if(available(design,catalog,rules,slot,track,stage))return stage;
        return 0;
    }
    public static Shipyard.Design fit(Shipyard.Design original,TechnologyCatalog catalog,
                                     SkirmishTechnologyRules rules,int slot,
                                     Shipyard.Track track,int stage,Scope scope){
        if(rules==null||!rules.permits(original,catalog,slot,track,stage))
            throw new IllegalArgumentException("Technology outside this skirmish challenge");
        Shipyard.Design result=fit(original,catalog,
                (CampaignTechnology.State)null,slot,track,stage,scope);
        // Do not require the rest of the DRAFT to be compliant yet:
        // players must be able to downgrade one over-tier socket at a time.
        // Whole-ship authorization is enforced on save/apply/deploy/host.
        return result;
    }
    public static Shipyard.Design doctrine(Shipyard.Design original,TechnologyCatalog catalog,
                                          SkirmishTechnologyRules rules,Shipyard.Track track){
        if(original==null||catalog==null||rules==null||track==null)
            throw new IllegalArgumentException("Skirmish doctrine not available");
        Shipyard.Design result=original;
        for(int slot=0;slot<original.slots();slot++){
            if(original.at(slot)==null)continue;
            int stage=highestAvailable(original,catalog,rules,slot,track);
            if(stage>0)result=replace(result,slot,track,stage);
        }
        // Do not require the rest of the DRAFT to be compliant yet:
        // players must be able to downgrade one over-tier socket at a time.
        // Whole-ship authorization is enforced on save/apply/deploy/host.
        return result;
    }

    public static Shipyard.Design clear(Shipyard.Design original,int slot){
        if(original==null||slot<0||slot>=original.slots()||original.at(slot)==null)
            throw new IllegalArgumentException("No installed technology on that socket");
        Shipyard.Track active=original.at(slot).branch();
        return active==null?original:original.stage(slot,active,0);
    }
}
