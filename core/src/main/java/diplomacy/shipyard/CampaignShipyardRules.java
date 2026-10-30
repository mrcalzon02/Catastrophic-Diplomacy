package diplomacy.shipyard;

import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.tech.TechnologyCatalog;
import java.util.Locale;

/**
 * Shared Android/desktop campaign shipyard authorization.
 * Sandbox/skirmish have no restrictions; campaign licenses and unlocked mission
 * tier are always checked before commissioning a new physical hull.
 */
public final class CampaignShipyardRules {
    private CampaignShipyardRules() { }
    public static void requireAllowed(Shipyard.Design design,Battle.Faction faction,
                                       int researchRank,int unlockedMission){
        if(design==null||faction==null||researchRank<0||researchRank>6)
            throw new IllegalArgumentException("Invalid campaign research authorization");
        if(design.faction!=faction)
            throw new IllegalArgumentException("Campaign shipyard cannot build another faction's vessel");
        if(!ShipLineages.availableInCampaign(design.hull,unlockedMission))
            throw new IllegalArgumentException("Hull generation "+ShipLineages.of(design.hull).generation()
                    +" has not been unlocked by the campaign");
        for(int i=0;i<design.slots();i++){
            Shipyard.Installed installed=design.at(i);
            if(installed==null)continue;
            for(int stage:installed.stages().values())
                if(stage>researchRank)
                    throw new IllegalArgumentException("Module "+(i+1)+
                            " requires purchased research rank "+stage);
            if(installed.mark>Math.min(4,researchRank+1))
                throw new IllegalArgumentException("Hardware Mk "+installed.mark+
                        " exceeds campaign manufacturing license");
            if(installed.pattern!=ModuleVersions.Pattern.STANDARD&&researchRank<2)
                throw new IllegalArgumentException("Advanced manufacturing patterns require rank II");
        }
        if(design.fighterHull!=FighterModel.Hull.ASSAULT_GUNSHIP&&researchRank<2)
            throw new IllegalArgumentException("Specialized fighter hulls require research rank II");
    }
    /** Actual campaign authority: license is a capacity gate, not an unlock.
     * Every positive specialization needs a permanently funded technology ID. */
    public static void requireAllowed(Shipyard.Design design,CampaignProgress.State campaign,
                                      CampaignTechnology.State discoveries,TechnologyCatalog catalog){
        if(campaign==null||discoveries==null||catalog==null||
                campaign.faction()!=discoveries.faction())
            throw new IllegalArgumentException("Campaign engineering authority missing");
        requireAllowed(design,campaign.faction(),campaign.researchRank(),campaign.unlocked());
        discoveries.validateCatalog(catalog);
        for(int slot=0;slot<design.slots();slot++){
            Shipyard.Installed installed=design.at(slot);
            if(installed==null)continue;
            for(Shipyard.Track track:Shipyard.Track.values()){
                int stage=installed.stage(track);
                if(stage<=0)continue;
                String id=installed.component.replaceFirst("^BASE-","TECH-")+
                        "-"+track.name()+"-"+String.format(Locale.ROOT,"%02d",stage);
                if(catalog.get(id)==null||!discoveries.has(id))
                    throw new IllegalArgumentException("Module "+(slot+1)+
                            " needs researched technology "+id);
            }
        }
    }

}
