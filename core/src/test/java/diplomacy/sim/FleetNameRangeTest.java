package diplomacy.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Full naming-culture commissioning stress: names are independent of their
 * sixteen-digit identity, never duplicated among 200 owned ships and
 * deterministic when rerun against the same occupied-name roster. */
public final class FleetNameRangeTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static void reject(Runnable operation,String reason){
        try{operation.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(reason);
    }
    public static void main(String[] args){
        int commissioned=0;
        for(var style:FleetNames.Style.values()){
            for(long seed:new long[]{0L,73L,0x7fecb1493d2510L}){
                List<String> used=new ArrayList<>();
                HashSet<String> lower=new HashSet<>();
                for(int serial=1;serial<=200;serial++){
                    String name=FleetNames.unique(style,seed,serial,used);
                    check(name.equals(FleetNames.unique(style,seed,serial,used)),
                            "Name generation changed on replay");
                    check(!name.isBlank()&&name.length()<=70,
                            "Incorrect name length for "+style+": "+name);
                    check(lower.add(name.toLowerCase(Locale.ROOT)),
                            "Duplicate generated display name: "+style+" "+name);
                    check(!name.matches("[0-9]{16}"),
                            "Visible name is the permanent internal numeric ID");
                    used.add(name);
                    commissioned++;
                }
                check(used.size()==200,"Naming style cannot provision 200 owned vessels");
            }
        }
        reject(()->FleetNames.unique(FleetNames.Style.NAVAL,73L,1,null),
                "Null owned-name registry accepted");
        reject(()->FleetNames.unique(null,73L,1,List.of()),
                "Null naming culture accepted");
        reject(()->FleetNames.generate(FleetNames.Style.NAVAL,73L,0),
                "Invalid zero commission sequence accepted");
        String grim=FleetNames.generate(FleetNames.Style.GRIMDARK,67L,13);
        String neoclassical=FleetNames.generate(FleetNames.Style.NEOCLASSICAL,67L,13);
        String absurd=FleetNames.generate(FleetNames.Style.ABSURDIST,67L,13);
        check(!grim.equals(neoclassical)&&!grim.equals(absurd)&&
                !neoclassical.equals(absurd),"Different name cultures produced identical ship titles");
        System.out.println("PASS cultural ship naming: "+FleetNames.Style.values().length+
                " themes, "+commissioned+" distinct commissioned roster names, "+
                "rerolls, lengths, identity separation and deterministic replay");
    }
}
