package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.HashSet;
import java.util.Set;

public final class HullVoiceChatterTest {
    public static void main(String[] args){
        Set<HullVoiceChatter.Theme> themes=new HashSet<>();
        int checks=0;
        for(Shipyard.Hull hull:Shipyard.Hull.values()){
            HullVoiceChatter.Theme theme=HullVoiceChatter.theme(hull);
            themes.add(theme);
            if(theme!=HullVoiceChatter.theme(hull.spriteHull()))
                throw new AssertionError("Variant voice mismatch: "+hull);
            for(CrewChatter.Event event:CrewChatter.Event.values()){
                String a=HullVoiceChatter.line(hull,event,123,42,CrewChatter.Tone.MIXED);
                String b=HullVoiceChatter.line(hull,event,123,42,CrewChatter.Tone.MIXED);
                String p=HullVoiceChatter.line(hull,event,123,42,CrewChatter.Tone.PROFESSIONAL);
                String v=HullVoiceChatter.line(hull,event,123,42,CrewChatter.Tone.VETERAN);
                if(!a.equals(b)||a.isBlank()||p.isBlank()||v.isBlank()||(!a.equals(p)&&!a.equals(v)))
                    throw new AssertionError("Unstable or missing script: "+hull+"/"+event);
                checks++;
            }
        }
        if(themes.size()!=11)throw new AssertionError("Expected 11 canonical voice identities");
        System.out.println("PASS: "+checks+" hull/event checks, 11 voice themes, deterministic choices");
    }
}
