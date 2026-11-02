package diplomacy.client;

/** Headless visual diagnostic; no OpenGL or synthetic gameplay screenshot. */
public final class ShipSurfaceDamageVisualTest {
    private ShipSurfaceDamageVisualTest(){}
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static int alpha(int rgba){return rgba&255;}
    public static void main(String[] args) throws Exception {
        ShipDamageMaskRegionTest.verify();
        for(int width:new int[]{64,96,399,1024,2200}){
            for(int x=0;x<96;x++)
                check(ShipSurfaceSampleGrid.sourcePixel(x,width,96)==x*width/96,
                        "CPU source cache must sample the exact authored art pixel");
        }
        for(int height:new int[]{144,333,768,1800})
            for(int y=0;y<144;y++)
                check(ShipSurfaceSampleGrid.sourcePixel(y,height,144)==y*height/144,
                        "Damage mask row and shield overlay must use identical source UVs");
        check(ShipSurfaceSampleGrid.maskBytes(96,144)==55296,
                "Per-sprite CPU cache must remain exactly one small RGBA mask");
        check(ShipSurfaceSampleGrid.originalBytes(1024,1536)>
                ShipSurfaceSampleGrid.maskBytes(96,144)*100,
                "The source-pixel cache must reduce retained decoded memory by over 100x for large PNGs");
        int source=0xb0a090ff;
        check(ShipSurfaceDamageVisual.pixel(source,0,0,0)==source,"Intact hull pixels changed");
        check(ShipSurfaceDamageVisual.pixel(0x11223300,1,1,0)==0,"Original transparent silhouette expanded");
        int wear=ShipSurfaceDamageVisual.pixel(source,.35f,0,.2f);
        check(alpha(wear)==255 && (wear>>>8)<(source>>>8),
              "Nonpenetrating abrasion must scorch without cutting a hole");
        int crater=ShipSurfaceDamageVisual.pixel(source,.90f,0,0);
        check(alpha(crater)>0 && alpha(crater)<255,"Deep wear should make an irregular opening");
        int hole=ShipSurfaceDamageVisual.pixel(source,1,0,-.5f);
        check(alpha(hole)==0,"Stripped armor must reveal under-hull metal");
        int bypass=ShipSurfaceDamageVisual.pixel(source,.20f,.25f,0);
        check(alpha(bypass)>0 && alpha(bypass)<255,"Structural bypass must allow limited puncture");
        check(ShipSurfaceDamageVisual.pixel(source,0,0,.5f)==source,
              "Noise must not invent an intact ship's damage");
        for(float fraction:new float[]{0f,.35f,.72f,.90f,1f}){
            int semi=ShipSurfaceDamageVisual.pixel(0xb0a09080,fraction,0,.3f);
            check(alpha(semi)<=128,"Output alpha may never exceed original alpha");
        }
        // Armor underlayer and rim must respect both image masks. Never create
        // a glowing ghost at a transparent gap or a missing underlay sample.
        check(ShipSurfaceDamageVisual.interiorPixel(0xffffff00,0xc08040ff)==0,
                "Underlayer must not fill transparent gaps between sprite parts");
        check(ShipSurfaceDamageVisual.interiorPixel(0xc08040ff,0x804020ff)
                        ==0x3b2110ff,
                "Exposed interior must preserve identifiable 40%-lit structural detail");
        check(ShipSurfaceDamageVisual.interiorPixel(0xc0804080,0x00000000)
                        ==0x4d331a80,
                "Missing metal sample falls back to a shadowed ORIGINAL sprite pixel");
        check(ShipSurfaceDamageVisual.breachRim(.5f,0,1,0,1,0,255)==0,
                "Transparent source PNG must never have an emissive rim");
        check(ShipSurfaceDamageVisual.breachRim(.5f,0,1,0,1,255,0)==0,
                "Missing interior sprite pixel must never create a rim");
        check(ShipSurfaceDamageVisual.breachRim(0,0,0,0,0,255,255)==0,
                "Intact plating never glows");
        check(ShipSurfaceDamageVisual.breachRim(1,0,0,0,0,255,255)==0,
                "Completely empty crater center must not glow");
        float hot=ShipSurfaceDamageVisual.breachRim(.5f,.05f,.95f,.02f,.98f,255,255);
        check(hot>.1f,"Actual alpha transition must make a hot torn-metal rim");
        int rim=ShipSurfaceDamageVisual.stampedPixel(source,.90f,.5f,hot,0);
        int plain=ShipSurfaceDamageVisual.stampedPixel(source,.90f,.5f,0);
        check(alpha(rim)==alpha(plain)&&((rim>>>24)&255)>((plain>>>24)&255),
                "Hot edge must brighten plating without changing the original alpha cut");
        check(ShipSurfaceDamageVisual.stampedPixel(0xffaa2200,1,.5f,1f,0)==0,
                "Rim cannot paint pixels where there is no hull artwork");
        // All undamaged pixels must sample the original hull RGB, not a
        // low-resolution duplicate of its texture.
        check(ShipSurfaceDamageVisual.maskPixel(0,0,0)==0x000000ff,
                "Pristine peel must leave original hull unchanged");
        check(ShipSurfaceDamageVisual.maskPixel(1,0,0)==0xff0000ff,
                "Scorch intensity is not a substitute ship color");
        check((ShipSurfaceDamageVisual.maskPixel(0,1,0)&255)==0,
                "Full armor puncture must reveal actual structural underlay");
        check(((ShipSurfaceDamageVisual.maskPixel(0,0,1)>>>16)&255)==255,
                "Heat rim must be independent of armor alpha");
        check(((ShipSurfaceDamageVisual.maskPixel(.2f,0,0,1)>>>8)&255)==255,
                "Metal channel must transmit actual cool exposed edges");
        for(diplomacy.sim.Battle.DamageType type:diplomacy.sim.Battle.DamageType.values()){
            int impact=ShipImpactProfiles.sample(type,8,8,8,8,1,0,17,0);
            int far=ShipImpactProfiles.sample(type,8,9.5f,8,8,1,0,17,0);
            check(impact!=0,"Missing actual weapon damage profile: "+type);
            check(far==0,"Whole-hull cosmetic contamination: "+type);
        }
        int pock=ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.KINETIC,
                8,8,8,8,1,1,17,0);
        int rimChrome=ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.KINETIC,
                8,9.04f,8,8,1,1,17,0);
        check(ShipImpactProfiles.dark(pock)>0&&ShipImpactProfiles.hot(pock)==0,
                "Kinetic needs dark pit with no thermal orange glow");
        check(ShipImpactProfiles.metal(rimChrome)>0,
                "Kinetic must expose a raised bright metallic crater rim");
        // Previous mask clipping prevented fracture spokes reaching plating
        // farther outside the circular hole, even when the profile calculated
        // those ends. This is a nonblocking cosmetic geometry diagnostic.
        float ray=1.22f;
        int fracture=ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.KINETIC,
                8f-ray*.88f,8f+ray*.47f,8f,8f,1f,1f,17,0);
        check(ShipImpactProfiles.metal(fracture)>50,
                "Heavy kinetic raised fracture is still clipped before its outer tip");
        // This is the actual failure that left every kinetic screenshot
        // apparently pristine: the old pock's R/G/B all fell INSIDE a
        // physically transparent ~0.82-cell alpha cutout. Check effects
        // at a pixel that still has original plating after a full breach.
        float outer=1.04f;
        float outsideOpening=ShipArmorStamps.openingAt(8,8+outer,8,8,1,1,17);
        check(outsideOpening<.01f,"Fixture requires source plating OUTSIDE the armor hole");
        for(diplomacy.sim.Battle.DamageType family:new diplomacy.sim.Battle.DamageType[]{
                diplomacy.sim.Battle.DamageType.KINETIC,
                diplomacy.sim.Battle.DamageType.PLASMA,
                diplomacy.sim.Battle.DamageType.PARTICLE,
                diplomacy.sim.Battle.DamageType.MISSILE,
                diplomacy.sim.Battle.DamageType.TORPEDO,
                diplomacy.sim.Battle.DamageType.FIGHTER}){
            int outside=ShipImpactProfiles.sample(family,8,8+outer,8,8,1,1,17,0);
            check(ShipImpactProfiles.dark(outside)>18||
                            ShipImpactProfiles.hot(outside)>18||
                            ShipImpactProfiles.metal(outside)>18,
                    "Effect erased by the real alpha hole: "+family);
            check(ShipImpactProfiles.sample(family,8,9.4f,8,8,1,1,17,0)==0,
                    "Effect must remain within 1.3 actual armor cells: "+family);
        }
        int laser=ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,8,8,8,1,1,17,0);
        check(ShipImpactProfiles.metal(laser)==0,
                "Laser char center must retain its compact blackened appearance");
        // Actual surviving plating immediately outside a deep crater MUST
        // retain a visible cooling oxide band; a laser is not a kinetic dent.
        int annealed=ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,9.04f,8,8,1,1,17,0);
        check(ShipImpactProfiles.metal(annealed)>164&&ShipImpactProfiles.hot(annealed)>0,
                "Surviving laser-heated plating lacks iridescent oxidation data");
        check(ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,9.30f,8,8,1,1,17,0)==0,
                "Laser oxidation must not tint plating outside its 1.27-cell footprint");
        check(ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,9.04f,8,8,0,0,17,0)==0,
                "Laser oxide must not persist on a pristine ship");
        check(ShipSurfaceDamageVisual.blueCoefficient(1f,false)==.56f &&
                ShipSurfaceDamageVisual.blueCoefficient(1f,true)==1f,
                "Tagged laser oxide and kinetic metal must occupy different material ranges");
        // Artistic palette assertions belong ONLY to this nonblocking
        // diagnostic, not the functional release's shipSurfaceSmoke gate.
        for(String token:new String[]{"if(peel.b>0.62)",
                "float anneal=clamp((peel.b-0.64)/0.36",
                "vec3 oxide=mix(","metalStrength=clamp(peel.b/0.56"})
            check(ShipSurfaceLighting.FRAGMENT.contains(token),
                    "Laser temper / kinetic metal shader path missing: "+token);
        // In all severity states, char -> soot -> heat must fade to the
        // EXACT original source pixel before reaching the neighboring tile.
        for(float severity:new float[]{.2f,.5f,1f}){
            float center=ShipDamageScorch.falloffAt(8,8,8,8,severity,0,0);
            float middle=ShipDamageScorch.falloffAt(8,8.34f,8,8,severity,0,0);
            float outside=ShipDamageScorch.falloffAt(8,9.05f,8,8,severity,0,.5f);
            check(center>middle&&middle>0&&outside==0,
                    "Impact scorch must have a small strict radial gradient");
            int centerPixel=ShipSurfaceDamageVisual.stampedPixel(source,center,0,0);
            int midPixel=ShipSurfaceDamageVisual.stampedPixel(source,middle,0,0);
            int untouched=ShipSurfaceDamageVisual.stampedPixel(source,outside,0,0);
            check((centerPixel>>>24)<(midPixel>>>24)&&
                    (midPixel>>>24)<(source>>>24),
                    "Localized scorch must blend smoothly back into real hull color");
            check(untouched==source,"No recoloring outside physical hit footprint");
        }
        check(ShipDamageScorch.falloffAt(8,8,8,8,0,0,.5f)==0,
                "Noise must never create damage on an untouched ship");
        // Actual spatial damage stamps are localized, irregular and physically
        // gated by the armor/structure state of the corresponding plate.
        for(int shipId:new int[]{11,482,1931}){
            for(int row=4;row<10;row++)for(int col=5;col<11;col++){
                float untouched=ShipArmorStamps.openingAt(row,col,row,col,0,0,shipId);
                float scuffed=ShipArmorStamps.openingAt(row,col,row,col,.38f,0,shipId);
                float deep=ShipArmorStamps.openingAt(row,col,row,col,.90f,0,shipId);
                float stripped=ShipArmorStamps.openingAt(row,col,row,col,1,0,shipId);
                float adjacent=ShipArmorStamps.openingAt(row,col+1.2f,row,col,1,0,shipId);
                check(untouched==0&&scuffed==0,
                        "Superficial wear must not invent armor holes");
                check(deep>.95f&&stripped==1f,
                        "Deep erosion must form a transparent crater center");
                check(adjacent==0,"Distant armor must remain fully opaque");
                int shallow=ShipSurfaceDamageVisual.stampedPixel(source,.38f,scuffed,0);
                int punctured=ShipSurfaceDamageVisual.stampedPixel(source,.90f,deep,0);
                check(alpha(shallow)==255&&alpha(punctured)<=13,
                        "The stamped image must show the underlying interior only after deep wear");
                check(ShipSurfaceDamageVisual.stampedPixel(0xaabbcc00,1,1,0)==0,
                        "Original transparent silhouette must stay transparent");
            }
        }
        float bypassStamp=ShipArmorStamps.openingAt(6,7,6,7,.2f,.25f,23);
        check(bypassStamp>0&&bypassStamp<1,
                "Actual armor-bypassing structure damage may make a smaller puncture");
        float tearA=ShipArmorStamps.openingAt(6.58f,7.08f,6,7,1f,0,32);
        float tearB=ShipArmorStamps.openingAt(6.58f,7.08f,6,7,1f,0,33);
        check(tearA!=tearB,"Different impact locations need varied jagged outlines");
        // Source-alpha alignment: only move a visually hidden crater to
        // physically adjacent opaque armor. Never project across empty space.
        int w=96,h=144;
        int[] thinHull=new int[w*h];
        for(int y=4;y<h-4;y++)for(int x=36;x<=58;x++)thinHull[y*w+x]=255;
        var aligned=ShipArmorStampAnchors.fromAlpha(thinHull,w,h);
        check(aligned.row(8,8)==8f&&aligned.col(8,8)==8f,
                "Already opaque armor plate must keep its exact physical anchor");
        check(aligned.col(8,5)>5f&&aligned.col(8,5)<=6f,
                "Nearby narrow hull armor should receive the crater, not transparent padding");
        check(aligned.col(8,0)==0f,
                "A remote disconnected impact cannot teleport its crater across transparent space");
        check(aligned.col(8,3)>5.4f&&aligned.col(8,3)<6.5f,
                "A real hit just outside a thin hull must project to its nearest ACTUAL paint, not disappear");
        check(Math.abs(aligned.row(8,3)-8)<.5,
                "Nearest artwork registration must stay in the same local impact band");
        float shifted=ShipArmorStamps.openingAt(aligned.row(8,5),aligned.col(8,5),
                8,5,aligned.row(8,5),aligned.col(8,5),1,0,77);
        check(shifted==1f,"Aligned physical armor breach must expose the interior");
        int[] transparent=new int[w*h];
        var empty=ShipArmorStampAnchors.fromAlpha(transparent,w,h);
        check(empty.row(8,5)==8&&empty.col(8,5)==5,
                "Missing source artwork must not invent a new hull surface");
        // A transparent exterior and opaque silhouette-clipped sublayer are
        // separate physical render passes, not a cutout into empty space.
        check(alpha(hole)==0 && alpha(0x424c55ff)==255,
              "Structural layer must show through completely peeled exterior");
        check(ShipSurfaceDamageVisual.opening(.5f,0,.5f)==0,
              "Armor wear alone must not be a penetrating crater");
        check(ShipSurfaceDamageVisual.opening(1f,0,-.5f)==1,
              "Full armor depletion must remain visible despite pixel noise");
        // The debug Android comparison must represent four legal fitted ships
        // using actual cell integrity, never invented material-state screenshots.
        var technology=diplomacy.tech.TechnologyCatalog.loadDefault();
        var scene=ShipSurfaceDamageFixture.create(technology);
        check(scene.ships().size()==4,"Review scene must contain exactly four real ships");
        float[] expectedArmor={0f,.38f,.90f,1f};
        for(int i=0;i<4;i++){
            var ship=scene.ships().get(i);
            check(ship.hullClass==diplomacy.shipyard.Shipyard.Hull.DREADNOUGHT,
                    "Stage fixture must render actual fitted approved dreadnought art");
            check(ship.defense.totalShields()==0,
                    "Shield overlay must not obscure any of the comparison stages");
            var cell=ship.defense.tile(6,7);
            check(cell.occupied,"Review damage cell must belong to the real hull");
            check(Math.abs(cell.exposure()-expectedArmor[i])<.001f,
                    "Review stage does not match physical armor depletion "+i);
            if(i<3)check(ship.hull==ship.maxHull && cell.internalDamage()==0,
                    "Nonpenetrating review stage cannot damage structural integrity");
            else check(ship.hull<ship.maxHull && cell.internalDamage()>0,
                    "Penetration stage must actually deplete structure and hull");
        }
        System.out.println("PASS superficial scorch, staged fitted-hull damage, alpha preservation and bypass");
    }
}
