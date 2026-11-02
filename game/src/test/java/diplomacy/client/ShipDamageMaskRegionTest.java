package diplomacy.client;

import java.util.Arrays;

/** No OpenGL required. The GPU upload area must contain every changed tile. */
final class ShipDamageMaskRegionTest {
    private static void check(boolean pass,String why){
        if(!pass)throw new AssertionError(why);
    }
    static void verify(){
        final int w=96,h=144;
        int[] pristine=new int[16*16*2];
        var initial=ShipDamageMaskRegion.changed(null,pristine,w,h);
        check(initial.x()==0&&initial.y()==0&&initial.width()==w
                &&initial.height()==h,"New masks upload the whole original sprite area");
        check(ShipDamageMaskRegion.changed(pristine,pristine.clone(),w,h)==null,
                "Unchanged protection cells must not allocate or upload a damage mask");
        int[] hit=pristine.clone();
        hit[(8*16+8)*2]=115;
        var local=ShipDamageMaskRegion.changed(pristine,hit,w,h);
        check(local!=null&&local.pixels()<w*h/4,
                "Single tile hit must upload substantially less than the entire mask");
        int tileX=(int)((8.5)*w/16),tileY=(int)((8.5)*h/16);
        check(local.x()<=tileX&&local.x()+local.width()>tileX
                &&local.y()<=tileY&&local.y()+local.height()>tileY,
                "Updated rectangle must contain the true impact center");
        // A physically real hit may target a transparent gap in a narrow
        // alien silhouette. Preserve the compact default, but enlarge only
        // if its nearest ACTUAL source pixel is outside that rectangle.
        int[] narrowAlpha=new int[w*h];
        for(int yy=4;yy<h-4;yy++)for(int xx=36;xx<=58;xx++)
            narrowAlpha[yy*w+xx]=255;
        var mapped=ShipArmorStampAnchors.fromAlpha(narrowAlpha,w,h);
        int[] shifted=pristine.clone();
        shifted[(8*16+3)*2]=115;
        var resized=ShipDamageMaskRegion.changed(pristine,shifted,w,h,mapped);
        int projected=(int)((mapped.col(8,3)+.5f)*w/16);
        check(resized.x()<=projected&&resized.x()+resized.width()>projected,
                "Dirty update must include the registered real source pixel");
        var unchangedAnchored=ShipDamageMaskRegion.changed(pristine,pristine,w,h,mapped);
        check(unchangedAnchored==null,
                "Source alpha alignment cannot invent GPU updates without physical damage");
        int[] puncture=hit.clone();
        puncture[(8*16+8)*2+1]=80;
        var internal=ShipDamageMaskRegion.changed(hit,puncture,w,h);
        check(internal!=null&&internal.equals(local),
                "Structural penetration and armor erosion invalidate the same material area");
        int[] edges=pristine.clone();edges[0]=128;
        var corner=ShipDamageMaskRegion.changed(pristine,edges,w,h);
        check(corner.x()==0&&corner.y()==0&&corner.width()<=24
                &&corner.height()<=36,"Corner damage must clamp its upload safely");
        int[] spread=pristine.clone();
        spread[0]=128;spread[spread.length-2]=128;
        var all=ShipDamageMaskRegion.changed(pristine,spread,w,h);
        check(all.x()==0&&all.y()==0&&all.width()==w&&all.height()==h,
                "Two distant damaged plates must update the union of both footprints");
        int[] repaired=hit.clone();
        Arrays.fill(repaired,0);
        var restore=ShipDamageMaskRegion.changed(hit,repaired,w,h);
        check(restore!=null&&restore.equals(local),
                "Armor repairs must also restore the previously transparent region");
        System.out.println("PASS cropped damage-mask uploads, hit centers, structural wear, repairs, edges");
    }
}
