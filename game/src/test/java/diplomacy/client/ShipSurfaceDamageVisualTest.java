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
                "Legacy 96x144 resolution accounting must remain exact");
        check(ShipSurfaceSampleGrid.maskBytes(192,288)==221184,
                "Native-96 wound runtime overlay must report correct 2x-axis memory");
        check(ShipDamageImageMasks.SIZE==96 && ShipDamageImageMasks.COUNT==6,
                "Live authored wound source must provide six 96px frames per family");
        java.util.HashSet<Integer> angles=new java.util.HashSet<>();
        java.util.HashSet<Integer> sizes=new java.util.HashSet<>();
        for(int seed=0;seed<128;seed++){
            for(diplomacy.sim.Battle.DamageType family:
                    diplomacy.sim.Battle.DamageType.values()){
                int angle=ShipDamageVariants.maskAngleDegrees(seed,family);
                float scale=ShipDamageVariants.maskScale(seed,family);
                check(angle>=0&&angle<360&&scale>=.91f&&scale<=1.10f,
                        "Seeded stamp transform escaped minor scale/rotation range");
                check(angle==ShipDamageVariants.maskAngleDegrees(seed,family)&&
                        scale==ShipDamageVariants.maskScale(seed,family),
                        "Damage stamp transform changed between identical frames");
                float co=ShipDamageVariants.maskCos(angle);
                float si=ShipDamageVariants.maskSin(angle);
                check(Math.abs(co*co+si*si-1f)<.00002f,
                        "Damage micro-rotation must not stretch mask geometry");
                angles.add(angle);sizes.add(Float.floatToIntBits(scale));
            }
        }
        check(angles.size()>20&&sizes.size()>30,
                "Real damage mask variants need both seeded angle and size diversity");
        check(CombatLightingRenderer.emissionStrength(diplomacy.sim.Battle.DamageType.KINETIC)==0f,
                "Kinetic breach material must remain cold without persistent glow");
        check(CombatLightingRenderer.emissionStrength(diplomacy.sim.Battle.DamageType.PLASMA)>
                CombatLightingRenderer.emissionStrength(diplomacy.sim.Battle.DamageType.LASER),
                "Plasma molten edges should visibly outshine narrow laser incision");
        check(CombatLightingRenderer.smokeWisps(diplomacy.sim.Battle.DamageType.TORPEDO)<=2&&
                CombatHitRenderer.sparkCount(diplomacy.sim.Battle.DamageType.PARTICLE,true)<=4,
                "Small punctures and torpedo smoke must respect tiny particle budgets");
        // Appearance criteria are intentionally in the optional visual test,
        // NEVER required by the app release or multiplayer functionality.
        check(CombatLightingRenderer.hotMetalRadius(diplomacy.sim.Battle.DamageType.PLASMA)
                        <=CombatEffectScale.of(3.0f)&&
                CombatLightingRenderer.hotMetalRadius(diplomacy.sim.Battle.DamageType.LASER)
                        <=CombatEffectScale.of(1.8f),
                "Wound illumination must stay smaller than its material damage field");
        check(CombatLightingRenderer.emissionStrength(diplomacy.sim.Battle.DamageType.PLASMA)
                        <=.12f&&
                CombatLightingRenderer.emissionStrength(diplomacy.sim.Battle.DamageType.LASER)
                        <=.06f,
                "Old broad additive hot-metal intensities cannot return");
        check(CombatLightingRenderer.impactFlashRadius(false,true)
                        <=CombatEffectScale.of(19f)&&
                CombatLightingRenderer.impactFlashAlpha(false)<=.18f,
                "Impact flashes must not wash out entire cruiser and frigate hulls");
        check(ShipLightProbes.IMPACT_LIGHT_RADIUS<=18f&&
                ShipLightProbes.BEAM_HIT_LIGHT_RADIUS<=19f&&
                ShipLightProbes.MAX_WEAPON_LIGHT_GAIN<=.13f,
                "Hull shader probes must not revive ship-wide cyan/red tint");
        check(ShipSurfaceLighting.FRAGMENT.contains("float peak=max(strikeLight.r,")&&
                ShipSurfaceLighting.FRAGMENT.contains("0.12/max(peak,0.0001)")&&
                ShipSurfaceLighting.FRAGMENT.contains("attenuation*attenuation*attenuation"),
                "Weapon-light shader must cap and steeply fade combined lighting");
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
                        ==0x29170bff,
                "Exposed interior must preserve identifiable 28%-lit structural detail");
        check(ShipSurfaceDamageVisual.interiorPixel(0xc0804080,0x00000000)
                        ==0x36241280,
                "Missing metal sample must retain dark original-color fallback and alpha");
        int under=ShipSurfaceDamageVisual.interiorPixel(0xc08040ff,0x804020ff);
        check((under&255)==255 && ((under>>>24)&255)<((0x3b2110ff>>>24)&255),
                "Darker structural metal must not affect original alpha or brighten breach");
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
        float outer=.95f;
        float outsideOpening=ShipArmorStamps.openingAt(8,9.04f,8,8,1,1,17);
        check(outsideOpening<.01f,"Fixture requires source plating OUTSIDE the armor hole");
        for(diplomacy.sim.Battle.DamageType family:new diplomacy.sim.Battle.DamageType[]{
                diplomacy.sim.Battle.DamageType.KINETIC,
                diplomacy.sim.Battle.DamageType.PLASMA,
                diplomacy.sim.Battle.DamageType.PARTICLE,
                diplomacy.sim.Battle.DamageType.MISSILE,
                diplomacy.sim.Battle.DamageType.TORPEDO,
                diplomacy.sim.Battle.DamageType.FIGHTER}){
            // A fighter/star pinprick or oblique particle gouge should NOT
            // be forced to paint a full one-cell circular halo. Check
            // surviving material somewhere along its bounded actual wound.
            boolean visible=false;
            for(int n=0;n<32;n++){
                float phi=(float)(n*StrictMath.PI/16.0);
                for(float dist:new float[]{.39f,.58f,.76f,.95f,1.09f}){
                    float xx=(float)StrictMath.cos(phi)*dist;
                    float yy=(float)StrictMath.sin(phi)*dist;
                    int sample=ShipImpactProfiles.sample(family,8+yy,8+xx,
                            8,8,1,1,17,0);
                    if(Math.max(ShipImpactProfiles.dark(sample),
                            Math.max(ShipImpactProfiles.hot(sample),
                            ShipImpactProfiles.metal(sample)))>18){
                        visible=true;break;
                    }
                }
                if(visible)break;
            }
            check(visible,"Real surviving-plating impact material missing: "+family);
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
                8,8.91f,8,8,1,1,17,0);
        check(ShipImpactProfiles.metal(annealed)>164&&ShipImpactProfiles.hot(annealed)>0,
                "Surviving laser-heated plating lacks iridescent oxidation data");
        check(ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,9.30f,8,8,1,1,17,0)==0,
                "Laser oxidation must not tint plating outside its 1.05-cell footprint");
        check(ShipImpactProfiles.sample(diplomacy.sim.Battle.DamageType.LASER,
                8,9.04f,8,8,0,0,17,0)==0,
                "Laser oxide must not persist on a pristine ship");
        // Asymmetric stamps may legitimately extend to one side of a
        // 1.12-cell cardinal sample. Measure the visual envelope over every
        // direction instead of rejecting legitimate directional damage.
        // Cosmetic reach is reported as a diagnostic; only escape beyond
        // the renderer's explicit 1.30-cell clipping radius is invalid.
        for(diplomacy.sim.Battle.DamageType family:
                diplomacy.sim.Battle.DamageType.values()){
            int outerSamples=0;
            float furthest=0f;
            for(int n=0;n<64;n++){
                float angle=(float)(n*StrictMath.PI/32.0);
                float dx=(float)StrictMath.cos(angle);
                float dy=(float)StrictMath.sin(angle);
                for(float radius:new float[]{.90f,1.02f,1.12f,1.20f,1.29f,1.34f}){
                    int mark=ShipImpactProfiles.sample(family,
                            8f+dy*radius,8f+dx*radius,8f,8f,1f,1f,17,0);
                    if(mark!=0){
                        furthest=Math.max(furthest,radius);
                        if(radius>=1.12f)outerSamples++;
                    }
                    if(radius>1.30f)
                        check(mark==0,"Impact escaped physical stamp envelope: "+family);
                }
            }
            System.out.printf(java.util.Locale.ROOT,
                    "ARMOR-VARIANT-REACH family=%s furthest=%.2f outerSamples=%d%n",
                    family,furthest,outerSamples);
        }
        // Dense local material signatures must diverge for each weapon,
        // even with equal damage and identical impact coordinates.
        java.util.HashSet<Long> profiles=new java.util.HashSet<>();
        for(diplomacy.sim.Battle.DamageType family:new diplomacy.sim.Battle.DamageType[]{
                diplomacy.sim.Battle.DamageType.LASER,
                diplomacy.sim.Battle.DamageType.KINETIC,
                diplomacy.sim.Battle.DamageType.MISSILE,
                diplomacy.sim.Battle.DamageType.TORPEDO,
                diplomacy.sim.Battle.DamageType.PLASMA,
                diplomacy.sim.Battle.DamageType.PARTICLE,
                diplomacy.sim.Battle.DamageType.FIGHTER}){
            long signature=0xcbf29ce484222325L;
            for(float radius:new float[]{0f,.27f,.52f,.74f,.94f,1.04f}){
                int pixel=ShipImpactProfiles.sample(family,8f,8f+radius,
                        8f,8f,1f,1f,17,0f);
                signature=(signature^pixel)*0x100000001b3L;
            }
            check(profiles.add(signature),
                    "Weapon profiles indistinguishable: "+family);
        }
        // Six genuinely different seeded variants per weapon, including
        // unequal orientations and asymmetric erosion on the same hull.
        // Cosmetic diagnostics ONLY; do not gate functional releases.
        for(diplomacy.sim.Battle.DamageType type:diplomacy.sim.Battle.DamageType.values()){
            java.util.Set<Integer> selected=new java.util.HashSet<>();
            java.util.Set<Long> shapes=new java.util.HashSet<>();
            for(int seed=1;seed<=120;seed++){
                selected.add(ShipDamageVariants.variant(seed,type));
                if(seed>48)continue;
                long maskSignature=0xcbf29ce484222325L;
                for(int py=-11;py<=11;py++)for(int px=-11;px<=11;px++){
                    float dx=px/12f,dy=py/12f;
                    float cut=ShipDamageVariants.opening(type,dx,dy,1,1,seed);
                    int ink=ShipImpactProfiles.sample(type,8+dy,8+dx,
                            8,8,1,1,seed,0);
                    int code=(Math.round(cut*255f)<<24)|ink;
                    maskSignature=(maskSignature^code)*0x100000001b3L;
                }
                shapes.add(maskSignature);
            }
            check(selected.size()==6,"Weapon lacks six real variants: "+type);
            check(shapes.size()>=5,
                "Seed selection still renders near-identical damage forms: "+type);
            for(int seed:new int[]{1,17,81}){
                check(ShipDamageVariants.opening(type,0,0,0,0,seed)==0,
                    "Variant must never puncture an undamaged cell: "+type);
                check(ShipDamageVariants.opening(type,1.41f,0,1,1,seed)==0,
                    "Variant must never escape its local tile envelope: "+type);
                check(ShipDamageVariants.opening(type,0,1.41f,1,1,seed)==0,
                    "Variant escaped its physical mask bounding box: "+type);
            }
        }
        // Weapon-specific alpha openings stay anchored to actual damage.
        // The rail/particle family makes a needle hole, while a torpedo
        // tears a visibly larger cavity, all clipped to original art.
        int id=17;
        // The angled particle hole is at the terminal end of the
        // entry trough, rather than assumed perfectly centered.
        float particlePeak=0;
        for(int yy=-6;yy<=6;yy++)for(int xx=-6;xx<=6;xx++)
            particlePeak=Math.max(particlePeak,
                    ShipArmorStamps.typedOpeningAt(
                        8+yy*.04f,8+xx*.04f,8,8,8,8,1,1,id,
                        diplomacy.sim.Battle.DamageType.PARTICLE));
        float torpedoCenter=ShipArmorStamps.typedOpeningAt(8,8,8,8,
                8,8,1,1,id,diplomacy.sim.Battle.DamageType.TORPEDO);
        check(particlePeak>.95f&&torpedoCenter>.95f,
                "Physical breaches must expose structure at their actual center");
        // A tilted torn edge must NOT be required to cut through an
        // arbitrary due-east point. Compare full 360-degree circumference.
        int largeBreaches=0,smallBreaches=0;
        for(int n=0;n<64;n++){
            double phi=n*2.0*StrictMath.PI/64.0;
            float px=(float)StrictMath.cos(phi)*.89f;
            float py=(float)StrictMath.sin(phi)*.89f;
            if(ShipArmorStamps.typedOpeningAt(8+py,8+px,8,8,8,8,1,1,
                    id,diplomacy.sim.Battle.DamageType.TORPEDO)>.10f)
                largeBreaches++;
            if(ShipArmorStamps.typedOpeningAt(8+py,8+px,8,8,8,8,1,1,
                    id,diplomacy.sim.Battle.DamageType.PARTICLE)>.10f)
                smallBreaches++;
        }
        check(largeBreaches>6&&smallBreaches==0,
                "Torpedo must physically expose much more plating than particle");
        check(ShipArmorStamps.typedOpeningAt(8,9.40f,8,8,
                8,8,1,1,id,diplomacy.sim.Battle.DamageType.TORPEDO)==0,
                "Torpedo rupture must remain localized");
        // The torpedo edge must actually fracture into open teeth AND
        // closed deep notches. A smooth/lumpy ellipse can no longer pass.
        // This is appearance validation in this OPTIONAL diagnostic only.
        int jaggedTeeth=0,deepNotches=0;
        for(int n=0;n<64;n++){
            double angle=n*2.0*StrictMath.PI/64.0;
            float rad=(float)StrictMath.cos(angle)*1.16f;
            float tang=(float)StrictMath.sin(angle)*1.16f;
            float edge=ShipArmorStamps.typedOpeningAt(8+tang,8+rad,8,8,
                    8,8,1,1,id,diplomacy.sim.Battle.DamageType.TORPEDO);
            if(edge>.15f)jaggedTeeth++;
            else if(edge<.01f)deepNotches++;
        }
        check(jaggedTeeth>=3&&deepNotches>=12,
                "Torpedo rupture must have distinct projecting tears and notches");
        check(ShipArmorStamps.typedOpeningAt(8,8,8,8,
                8,8,0,0,id,diplomacy.sim.Battle.DamageType.TORPEDO)==0,
                "Undamaged hull must not show a torpedo breach");
        boolean particleCrack=false;
        for(int n=0;n<64;n++){
            double phi=n*2.0*StrictMath.PI/64.0;
            float xx=(float)StrictMath.cos(phi)*.58f;
            float yy=(float)StrictMath.sin(phi)*.58f;
            int sample=ShipImpactProfiles.sample(
                    diplomacy.sim.Battle.DamageType.PARTICLE,8+yy,8+xx,
                    8,8,1,1,17,0);
            if(ShipImpactProfiles.dark(sample)>30)particleCrack=true;
        }
        check(particleCrack,"Directional particle puncture lost its stress cracks");
        int kineticOuter=ShipImpactProfiles.sample(
                diplomacy.sim.Battle.DamageType.KINETIC,8,8.87f,
                8,8,1,1,17,0);
        check(ShipImpactProfiles.hot(kineticOuter)==0,
                "Rejected kinetic brown/amber heat mask must not recur");
        check(ShipImpactProfiles.metal(kineticOuter)>0,
                "Earlier approved kinetic raised metal rim must survive rollback");
        check(ShipSurfaceDamageVisual.blueCoefficient(1f,false)==.56f &&
                ShipSurfaceDamageVisual.blueCoefficient(1f,true)==1f,
                "Tagged laser oxide and kinetic metal must occupy different material ranges");
        // Artistic palette assertions belong ONLY to this nonblocking
        // diagnostic, not the functional release's shipSurfaceSmoke gate.
        // Material interpolation is now continuous through the reserved B
        // range. Hard branches fabricated orange/chrome polygons at the 96px
        // laser annealing boundary, despite correct source mask gradients.
        check(!ShipSurfaceLighting.FRAGMENT.contains("if(peel.b>0.62)"),
                "Hard laser material branch would recreate the visible halo seam");
        for(String token:new String[]{
                "float oxideMix=smoothstep(0.56,0.72,peel.b)*smoothstep(0.035,0.10,peel.g)",
                "float thermal=peel.g*(1.0-oxideMix)",
                "float coolPhase=smoothstep(0.40,0.69,phase)",
                "float anneal=oxideMix*max(temper,0.46*coolPhase)",
                "vec3 oxide=mix(", "float coolMetal=1.0-smoothstep(0.005,0.08,peel.g)",
                "metalStrength=clamp(peel.b/0.56",
                "vec3 residualHeat=vec3(0.0)",
                "residualHeat=vec3(0.32,0.105,0.018)*thermal*thermal",
                "lit=mix(albedo.rgb,lit,u_detail)+residualHeat"})
            check(ShipSurfaceLighting.FRAGMENT.contains(token),
                    "Continuous laser/metal shader material path missing: "+token);
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
