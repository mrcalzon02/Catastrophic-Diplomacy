package diplomacy.client;

import diplomacy.tech.TechnologyCatalog;
import diplomacy.tech.SurvivalHardwareCatalog;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullProgression;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;
import diplomacy.sim.OrdnanceSubmunitions;
import diplomacy.sim.FighterModel;
import diplomacy.sim.ShipStandingOrders;
import diplomacy.sim.BattleOrders;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.RandomAccess;

/**
 * Read-only, searchable field encyclopedia sourced from live Java rules and the
 * exact technology catalogue used by the game. No separate balance numbers.
 */
final class GameWikiCatalog {
    static final String[] CATEGORIES={
        "ALL","GUIDES","ORDERS","HULLS","MODULES","UPGRADES",
        "AMMUNITION","ORDNANCE","FIGHTERS","MANUFACTURING","FACTIONS"
    };
    static final class Article {
        final String id,category,title;
        final List<String> lines;
        final String searchable;
        Article(String id,String category,String title,String... lines){
            this.id=id;this.category=category;this.title=title;
            this.lines=List.of(lines);
            StringBuilder search=new StringBuilder(id).append(' ').append(category)
                    .append(' ').append(title);
            for(String line:lines)search.append(' ').append(line);
            searchable=search.toString().toLowerCase(Locale.ROOT).replace('_',' ');
        }
    }
    // Every generated research article contains this exact label. Reuse it
    // for guaranteed-all-match searches rather than formatting tens of
    // thousands of temporary articles simply to find the word "research".
    private static final String RESEARCH_COST_LABEL="Research cost ";
    private static final String RESEARCH_COST_SEARCH=
            RESEARCH_COST_LABEL.toLowerCase(Locale.ROOT);
    // All generated research article search text begins with this ID prefix.
    private static final String RESEARCH_ID_SEARCH_PREFIX="upgrade.tech-";
    // Every ordinary component has four independently researched branches
    // and nine stages. A 73,260-entry index can be represented by only 2,035
    // canonical base identities without losing lexicographic ordering.
    private static final String[] SORTED_RESEARCH_TRACKS={
            "EFFICIENCY","OUTPUT","RESILIENCE","SPECIALIST"};
    private static final int RESEARCH_STAGES=9;
    private static final int RESEARCH_PER_COMPONENT=
            SORTED_RESEARCH_TRACKS.length*RESEARCH_STAGES;
    private static final class CompactUpgradeIds extends AbstractList<String>
            implements RandomAccess {
        private final List<TechnologyCatalog.Component> components;
        CompactUpgradeIds(List<TechnologyCatalog.Component> ordered){
            components=List.copyOf(ordered);
        }
        @Override public int size(){return components.size()*RESEARCH_PER_COMPONENT;}
        @Override public String get(int index){
            if(index<0||index>=size())throw new IndexOutOfBoundsException(index);
            TechnologyCatalog.Component base=
                    components.get(index/RESEARCH_PER_COMPONENT);
            int remainder=index%RESEARCH_PER_COMPONENT;
            String track=SORTED_RESEARCH_TRACKS[remainder/RESEARCH_STAGES];
            int rank=remainder%RESEARCH_STAGES+1;
            return "TECH-"+base.faction+"-"+base.subsystem+"-"+track+"-0"+rank;
        }
    }

    private final List<Article> articles;
    private final List<Article> upgradeArticles;
    private final List<Article> staticArticles;
    private final List<String> upgradeIds;
    private final TechnologyCatalog technology;
    private GameWikiCatalog(List<Article> list,TechnologyCatalog technology){
        list.sort(Comparator.comparing((Article e)->e.category)
                .thenComparing(e->e.title).thenComparing(e->e.id));
        staticArticles=List.copyOf(list);
        this.technology=technology;
        // Derive nine-stage research IDs from a 2,035-element sorted base
        // index instead of retaining 73,260 formatted String objects. This
        // preserves the exact previous lexicographic encyclopedia order and
        // O(log n) binary-search selection (RandomAccess is essential).
        // For imported/nonstandard catalogs that do not obey the uniform
        // nine-stage rule, retain the pre-existing explicit-ID fallback.
        if(technology.upgradeCount()==
                technology.componentCount()*RESEARCH_PER_COMPONENT){
            List<TechnologyCatalog.Component> bases=
                    new ArrayList<>(technology.baseComponents().values());
            bases.sort(Comparator.comparing(c->c.id));
            upgradeIds=new CompactUpgradeIds(bases);
        }else{
            List<String> ids=technology.upgradeIds();
            ids.sort(String::compareTo);
            upgradeIds=List.copyOf(ids);
        }
        if(upgradeIds.size()!=technology.upgradeCount())
            throw new IllegalStateException("Incomplete virtual research encyclopedia");
        // Both ALL and UPGRADES pages retain only a compact canonical-ID index.
        // Article text is generated for the selected/visible rows, not at load.
        upgradeArticles=new AbstractList<>() {
            @Override public int size(){return upgradeIds.size();}
            @Override public Article get(int index){
                return upgradeArticle(technology.get(upgradeIds.get(index)));
            }
        };
        articles=new AbstractList<>() {
            @Override public int size(){return staticArticles.size()+upgradeIds.size();}
            @Override public Article get(int index){
                if(index<0||index>=size())throw new IndexOutOfBoundsException(index);
                if(index<staticArticles.size())return staticArticles.get(index);
                return upgradeArticles.get(index-staticArticles.size());
            }
        };
    }
    /**
     * Filtered results hold only IDs for generated research articles. A broad
     * query such as "research" previously retained tens of thousands of
     * formatted paragraphs and concatenated search strings in a tiny Android
     * heap. Resolve a result only when a page row is actually requested.
     */
    private final class IndexedArticles extends AbstractList<Article> {
        private final List<Article> fixed;
        private final List<String> matchedIds;
        IndexedArticles(List<Article> fixed,List<String> matchedIds){
            this.fixed=List.copyOf(fixed);
            // The canonical research index is already immutable and sorted;
            // do not copy another 73k references for an ALL-category query.
            this.matchedIds=matchedIds==upgradeIds?upgradeIds:List.copyOf(matchedIds);
        }
        @Override public int size(){return fixed.size()+matchedIds.size();}
        @Override public Article get(int index){
            if(index<0||index>=size())throw new IndexOutOfBoundsException(index);
            if(index<fixed.size())return fixed.get(index);
            return upgradeArticle(technology.get(matchedIds.get(index-fixed.size())));
        }
        int indexOfId(String id){
            for(int i=0;i<fixed.size();i++)if(fixed.get(i).id.equals(id))return i;
            if(id.startsWith("upgrade.")){
                String key=id.substring("upgrade.".length());
                // Matched IDs remain sorted because their source index is sorted.
                int i=Collections.binarySearch(matchedIds,key);
                if(i>=0)return fixed.size()+i;
            }
            return -1;
        }
    }
    /** Selection restoration must never instantiate every virtual article. */
    int indexOfId(List<Article> results,String id){
        if(id==null||results==null)return -1;
        if(results instanceof IndexedArticles indexed)return indexed.indexOfId(id);
        if(results==articles||results==upgradeArticles){
            if(id.startsWith("upgrade.")){
                int index=Collections.binarySearch(upgradeIds,id.substring("upgrade.".length()));
                if(index>=0)return results==articles?staticArticles.size()+index:index;
            }
            if(results==articles)
                for(int i=0;i<staticArticles.size();i++)
                    if(staticArticles.get(i).id.equals(id))return i;
            return -1;
        }
        for(int i=0;i<results.size();i++)if(results.get(i).id.equals(id))return i;
        return -1;
    }
    List<Article> articles(){return articles;}
    int size(){return articles.size();}
    static String nice(String name){return name.replace('_',' ');}
    private static String number(double n){
        return String.format(Locale.ROOT,"%.2f",n);
    }
    static GameWikiCatalog build(TechnologyCatalog catalog){
        if(catalog==null)throw new IllegalArgumentException("Wiki requires live technology data");
        List<Article> a=new ArrayList<>(catalog.componentCount()+240);
        guide(a,"START HERE","How battles work",
            "Every encounter is simulated in authoritative 30 Hz ticks. A ship needs fitted machinery: hulls do not magically inherit expensive guns or support projectors.",
            "Weapons must meet their own range, power, heat, ammunition, sensor and firing conditions. Hits reduce directional shields, armor and physical structure. Damage can disable individual systems.",
            "Orders remain active until changed. Retreat preserves surviving ships for repair and future deployment; a withdrawn ship is not an extra free replacement.",
            "In battle, pause with SPACE; zoom with Z/X or touch gestures; open the tactical menu with M or ESC. F1 opens this encyclopedia outside the unattended screensaver.");
        guide(a,"SEARCH AND NAVIGATION","Using the field wiki",
            "Choose a category at the top. Filter by a title, faction, module ID, research branch or exact keyword. Keyboard search accepts typing, BACKSPACE erases; on Android tap SEARCH to use the native keyboard.",
            "The left column lists results, while the right column shows the selected article. PREV/NEXT paginate results or scroll the article. F1 or CLOSE returns to the exact previous screen.",
            "Everything in MODULES and UPGRADES is generated from the same TechnologyCatalog loaded by the shipyard: research IDs, costs, multipliers and prerequisites are not manually invented.",
            "The encyclopedia is read-only. Browsing cannot buy research, alter a ship fitting, unlock technology, restart a match, or spend credits.");
        guide(a,"CAMPAIGN","Campaign, missions and survivors",
            "The campaign contains 500 sequential missions grouped into fifty fronts and ten regions. Players begin with a commissioned corvette; other vessels must be purchased from their own credits.",
            "Surviving ships persist in the commissioned roster. The fleet dock separates actively deployed vessels from reserve ships. Repair, fuel, crew and ammunition replenishment cost resources.",
            "Replaying a cleared mission pays a reduced credit reward. Research uses paid projects and successful mission completions; it never automatically grants a fully equipped replacement ship.",
            "The fleet-size limit grows with the campaign. Missions 494 through 500 are designed for a maximum of 200 ships per fleet in simultaneous engagements.");
        guide(a,"TECHNOLOGY","Shared technology tree and authorization",
            "The same module and hull technology definitions serve campaign, skirmish, and multiplayer. Their *authorization* is different: campaign requires purchased discoveries; skirmish and PvP use a selected tier cap.",
            "Base hull sizes arrive in groups: Tech 0 starter, Tech I escorts, Tech II cruisers, Tech III capitals. Specialist variants follow at IV-VI; later levels expand technology and equipment.",
            "Each physical component has four mutually exclusive research tracks, OUTPUT / EFFICIENCY / RESILIENCE / SPECIALIST. A fitted socket cannot simultaneously take multiple tracks.",
            "The catalogue lists each stage separately with its exact research, industry, physical multiplier and tradeoff. A higher stage requires its direct prerequisite.");
        guide(a,"SHIPYARD","Shipyard, sockets and fitting",
            "Each chassis has its own capacity, mobility, base structure and explicit SYSTEM, WEAPON, DEFENSE, SUPPORT and HABITAT sockets. A slot must contain appropriate installed machinery.",
            "The shipyard checks faction ownership, technology authorization, mass, volume, power, heat and construction feasibility. A drawing or hull model alone confers no combat ability.",
            "Manufacturing pattern and mark are distinct from the research branch and stage; use the Manufacturing section for exact modifiers.",
            "Blueprints can be exported/imported for permitted skirmishes. Campaign refits must modify an actually owned vessel and preserve roster/resource transactions.");
        guide(a,"COMBAT","Directional protection and hull damage",
            "Damage resolves against shields, armor, structure and installed systems. Directional facings track independent energy; hits and recovery use actual simulation state.",
            "Armor can erode locally; physically damaged hulls receive localized scorch and destruction effects. Shield generators use translucent source-art field sectors: unresearched blue, output gold, efficiency green, resilience violet and specialist cyan. Each facing blends toward red as charge falls; recharge and restart scan the actual hex-field artwork.",
            "Repair, shield transfer and drone service require purpose-built fitted support modules and finite charge, power, range and heat resources.");
        guide(a,"WEAPONS","Primary weapon families",
            "Laser fire uses one of five installed laser families with different beam profile, effective behavior and emission. Kinetic guns launch finite physical rounds with chosen ammunition.",
            "Plasma and particle beams have their own damage/defense interactions. Guided missiles and torpedoes are physical, detectable, interceptable objects with seeker, propulsion, fuel and finite stores.",
            "Point-defense choices distinguish threats and ally defense, and are governed by permanent per-vessel doctrine. Visual beam origin must be the actual module hardpoint.");
        guide(a,"SENSORS","Radar, signatures and electronic warfare",
            "Radar detection considers range, target cross section, emissions and active countermeasures; an unpowered coasting missile still has a physical body and radar cross section.",
            "Electronic warfare can break seeker locks. Reacquisition searches are bounded and must observe actual target geometry; they cannot instantly teleport guidance to a new ship.",
            "Point defense must acquire and physically damage threats. A decoy is a separately launched finite-ammunition object that may distract acquisition.");
        guide(a,"LIGHTING","Lighting and battle graphics",
            "Ship normal and emissive maps are inferred from the original approved artwork and follow the hull without changing the authoritative hitbox.",
            "Up to two local live light sources shade nearby hulls; beams, ignited missiles, impacts, shield events and engines create bounded additive flashes. Empty-space sources cannot replace hull-relevant shader lights.",
            "Offscreen and subpixel effects are culled when possible. Paused battles do not generate new smoke or projectile steps. The screenshot and source art are not combat rules.");
        guide(a,"PERFORMANCE","Zoom, effects and profiling",
            "Distant fleets use lower detail and can bypass normal/emissive material sampling. Geometry outside the tactical camera is rejected conservatively; physical battles still simulate offscreen.",
            "Diagnostic CPU counters separately report simulation steps, non-simulation drawing and additive lighting overlays. A successful 400-ship Android emulator smoke does not certify physical-phone FPS.");
        guide(a,"MULTIPLAYER","Skirmish and multiplayer technology limits",
            "A match tier is a ceiling, not a mandatory uniform fit. Any allowed lower-tech ship can join a higher-cap fleet. The host validates each submitted physical design against match rules.",
            "Shared definitions keep equipment physics consistent. Dedicated and local networking require real joined clients and authoritative battle data; presentation-only graphics cannot create a valid ship.");
        guide(a,"SCREENSAVER","Unattended battle exhibition",
            "The screensaver chooses seeded opposing factions, fleet sizes, technology levels, loadouts, ship-specific orders and backgrounds, then launches a new battle when the previous one concludes.",
            "On Android a deliberate double tap reveals controls; a stray single tap does not leave the exhibition. The autonomous camera pans and sometimes follows small craft or guided ordnance.");
        guide(a,"AUDIO","Original hull communications",
            "Ten hull roles have user-recorded, original voice banks with 26 cues apiece. Exact corresponding radio messages are voiced; unmatched events use written traffic and short communication bleeps.",
            "The separate 30-clip announcer is not an ordinary ship captain. The narrative story voice scripts are a distinct, not-yet-recorded production layer.");
        guide(a,"FACTIONS","Eleven civilizations, one engineering framework",
            "Each playable civilization owns its own base component identifiers and individual research catalog entries. Faction-compatible designs and enemy loadouts are checked against real equipment.",
            "Do not confuse faction, hull art category, physical variant, technology rank or current commissioned roster ownership.");
        for(Battle.Faction faction:Battle.Faction.values())
            a.add(new Article("faction."+faction.name(),"FACTIONS",faction.displayName,
                "Playable civilization: "+faction.name()+".",
                "Its base components and exclusive research IDs are listed under MODULES and UPGRADES. Use search for its faction name or engineering prefix.",
                "A hull from another civilization is not a legal campaign purchase without explicit authorization. Different faction art does not replace physical equipment."));
        for(Shipyard.Hull hull:Shipyard.Hull.shipyardOrder()){
            ShipLineages.Lineage role=ShipLineages.of(hull);
            int weapons=0,systems=0,defenses=0,support=0,habitat=0;
            for(Shipyard.Slot socket:hull.slots)switch(socket.mount()){
                case WEAPON -> weapons++;case SYSTEM -> systems++;
                case DEFENSE -> defenses++;case SUPPORT -> support++;
                case HABITAT -> habitat++;
            }
            a.add(new Article("hull."+hull.name(),"HULLS",nice(hull.name()),
                "Role: "+nice(role.role().name())+" - "+role.role().description+".",
                "Size family: "+nice(hull.spriteHull().name())+". Chassis generation "+role.generation()+
                    ", doctrine: "+nice(role.ethos().name())+". "+(HullProgression.base(hull)?"Base hull.":"Specialist chassis variant."),
                "Required technology tier: "+HullProgression.requiredTier(hull)+" ("+
                    HullProgression.tierLabel(HullProgression.requiredTier(hull))+").",
                "Mass capacity "+number(hull.massCapacity)+"; volume capacity "+number(hull.volumeCapacity)+
                    "; base structure "+number(hull.baseHull)+"; mobility factor "+number(hull.mobility)+".",
                "Physical hardpoints: "+systems+" system, "+weapons+" weapon, "+defenses+
                    " defense, "+support+" support, "+habitat+" habitat.",
                "Variant identity and capacity change real available builds. It shares an approved artwork family where noted, not a copied damage profile.",
                "Actual weapons, shields, electronics, repairs and deployed fighters come from installed fitted modules; this chassis is not free equipment."));
        }
        for(TechnologyCatalog.Component m:catalog.baseComponents().values())
            a.add(new Article("module."+m.id,"MODULES",m.name,
                "Exact component ID: "+m.id+" | faction "+m.faction+" | subsystem "+m.subsystem+
                    " | socket category "+m.category+".",
                "Base mass "+number(m.mass)+", power demand "+number(m.power)+
                    ", heat "+number(m.heat)+", integrity "+number(m.integrity)+".",
                "Base effect output "+number(m.effect)+"; industry cost "+m.industryCost+".",
                "Functional purpose: "+subsystemExplanation(m.subsystem),
                "This is a physical installable base component. A hull alone does not supply the effect; select a compatible socket and check capacity, power and heat.",
                "Research tracks: OUTPUT for stronger effect, EFFICIENCY for lower operating burdens, RESILIENCE for survivability, SPECIALIST for role-specific specialization. Choose only one per installed socket.",
                "Find its exact upgrade pages by searching "+m.faction+" "+m.subsystem+" or "+m.id+"."));
        // Upgrade articles are virtual; creating every detailed entry here
        // would nullify the startup-memory savings in TechnologyCatalog.
        for(ModuleVersions.Pattern pattern:ModuleVersions.Pattern.values())
            for(int mark=1;mark<=4;mark++){
                ModuleVersions.Modifiers v=ModuleVersions.of(pattern,mark);
                a.add(new Article("version."+pattern+"."+mark,"MANUFACTURING",
                    nice(pattern.name())+" / Mk "+mark,
                    "Manufacturing selection: "+nice(pattern.name())+", mark "+mark+" of 4.",
                    "Physical multipliers: mass x"+number(v.mass())+", power x"+number(v.power())+
                        ", heat x"+number(v.heat())+", performance x"+number(v.output())+
                        ", precision x"+number(v.precision())+".",
                    "Integrity x"+number(v.integrity())+", maintenance x"+number(v.maintenance())+
                        ", construction cost x"+number(v.cost())+".",
                    "Pattern and mark apply to a fitted module, separate from the four exclusive per-module research branches."));
            }
        for(AmmunitionTable.Kinetic kind:AmmunitionTable.Kinetic.values())
            ammo(a,"KINETIC",nice(kind.name()),AmmunitionTable.kinetic(kind));
        for(AmmunitionTable.Warhead kind:AmmunitionTable.Warhead.values())
            ammo(a,"WARHEAD",nice(kind.name()),AmmunitionTable.warhead(kind));
        for(Battle.DamageType family:new Battle.DamageType[]{Battle.DamageType.MISSILE,Battle.DamageType.TORPEDO})
            for(OrdnanceDoctrine.Mode mode:OrdnanceDoctrine.Mode.values()){
                OrdnanceDoctrine.Pattern p=OrdnanceDoctrine.forWeapon(family,mode);
                a.add(new Article("salvo."+family+"."+mode,"ORDNANCE",
                    nice(family.name())+" / "+nice(mode.name())+" salvo",
                    "Physical launch doctrine: "+p.real()+" guided launch bodies and "+p.decoys()+" decoys in a salvo; total "+p.ammoNeeded()+" units of stored ammunition.",
                    "Spacing "+p.intervalTicks()+" simulation ticks; cold ignition delay "+p.ignitionDelayTicks()+" ticks; reload x"+number(p.reloadMultiplier())+".",
                    "Per-projectile damage x"+number(p.damageMultiplier())+
                        ", velocity x"+number(p.velocityMultiplier())+", durability x"+
                        number(p.durabilityMultiplier())+", radar cross-section x"+
                        number(p.crossSectionMultiplier())+", emissions x"+number(p.signatureMultiplier())+".",
                    "Payload pattern "+nice(p.payload().name())+". "+
                    (OrdnanceSubmunitions.carrier(p.payload())
                        ?"Each surviving carrier releases "+OrdnanceSubmunitions.children(p.payload())+
                            " independently guided and interceptable "+(OrdnanceSubmunitions.ultra(p.payload())
                            ?"ultra-micro":"micro")+" missiles in flight. The carrier has no impact damage when it opens; its original warhead budget is divided among the children. "
                        :"")+
                    "Decoys are real projectiles, not free visual flares. Launcher mounts and missile guidance remain independently fitted."));
            }
        for(OrdnancePropulsion.Mode mode:OrdnancePropulsion.Mode.values())
            a.add(new Article("propulsion."+mode,"ORDNANCE",nice(mode.name())+" motor",
                "Authoritative motor schedule: "+nice(mode.name())+".",
                "Active BOOST / CRUISE / TERMINAL phases consume fuel and provide limited acceleration. COLD EJECT and COAST have no engine thrust or exhaust light.",
                "The flight phase can change with flight ticks, ignition delay, target range and torpedo/missile family; real velocity and remaining delta-v persist across phases.",
                "A physically cold-coasting missile remains detectable to radar according to cross section and emissions. It is not invulnerable to point defense."));
        for(FighterModel.Hull kind:FighterModel.Hull.values()){
            FighterModel.Profile p=FighterModel.standard(kind);
            a.add(new Article("fighter."+kind,"FIGHTERS",nice(kind.name()),
                "Independent launched craft using "+nice(p.weapon().name())+" weapons. They are not cosmetic missiles.",
                "Reference Terran profile: health "+number(p.health())+", armor "+number(p.armor())+
                    ", speed "+number(p.speed())+", sensors "+number(p.sensors())+".",
                "Cross-section "+number(p.crossSection())+", emission "+number(p.signature())+
                    ", fuel "+number(p.fuel())+" seconds.",
                "Weapon damage "+number(p.damage())+", range "+number(p.range())+
                    ", reload "+number(p.cooldown())+" seconds, carried ammo "+p.ammunition()+
                    ", accuracy "+number(p.accuracy())+".",
                "Faction adjustments use the same actual FighterModel profile function. Craft can be detected, targeted and destroyed, and use their own finite resources."));
        }
        for(int i=0;i<ShipStandingOrders.SETTINGS.length;i++){
            String name=ShipStandingOrders.SETTINGS[i];
            a.add(new Article("order."+i,"ORDERS",nice(name),
                orderExplanation(i),
                "Captain doctrine row "+(i+1)+" of "+ShipStandingOrders.SETTINGS.length+
                    ". This is one persistent setting and remains active alongside all other selected policies.",
                "The doctrine is written to that vessel's selected order profile. It is not a one-use command or temporary waypoint."));
        }
        for(BattleOrders.Position pos:BattleOrders.Position.values())
            a.add(new Article("position."+pos,"ORDERS",nice(pos.name())+" positioning",
                "BattleOrders positioning policy: "+nice(pos.name())+".",
                "The actual order implementation controls steering and location. A ship must have a valid anchor/target for formation or escort behavior."));
        for(BattleOrders.Targeting target:BattleOrders.Targeting.values())
            a.add(new Article("target."+target,"ORDERS",nice(target.name())+" targeting",
                "BattleOrders targeting policy: "+nice(target.name())+".",
                "Real target selection still considers sensors, range, installed weapon, hostility and individual vessel orders."));
        for(BattleOrders.PointDefense pd:BattleOrders.PointDefense.values())
            a.add(new Article("pd."+pd,"ORDERS",nice(pd.name())+" point defense",
                "Point-defense strategy "+nice(pd.name())+".",
                "A working point-defense module must be installed to intercept missiles, physical shells or fightercraft as permitted by this mode. Priorities and ally-protection policies also apply."));
        return new GameWikiCatalog(a,catalog);
    }
    private static Article upgradeArticle(TechnologyCatalog.Upgrade u){
        if(u==null)throw new IllegalStateException("Missing authoritative upgrade article");
        return new Article("upgrade."+u.id,"UPGRADES",u.name+" / "+u.id,
            "Exact technology ID: "+u.id+". Faction "+u.faction+", subsystem "+u.subsystem+
                ", branch "+u.track+", stage "+u.stage+" of 9.",
            "Prerequisite: "+u.prerequisite+". "+RESEARCH_COST_LABEL+u.researchCost+
                "; industry cost "+u.industryCost+".",
            "Physical multipliers: mass x"+number(u.mass)+", power x"+number(u.power)+
                ", heat x"+number(u.heat)+", output x"+number(u.output)+
                ", accuracy x"+number(u.accuracy)+".",
            "Additional multipliers: integrity x"+number(u.integrity)+", shields x"+
                number(u.shield)+", thrust x"+number(u.thrust)+", cooling x"+
                number(u.cooling)+", maintenance x"+number(u.maintenance)+".",
            "Research branch purpose: "+branchExplanation(u.track),
            "Authoritative design tradeoff: "+u.tradeoff,
            "The fitted module must own the appropriate branch and stage. This is not a whole-ship automatic buff.");
    }
    private static void guide(List<Article> a,String id,String title,String... body){
        a.add(new Article("guide."+id,"GUIDES",title,body));
    }
    private static String branchExplanation(String branch){
        return switch(branch){
            case "OUTPUT" -> "Increases the installed module's primary physical effect, subject to its own power, heat, mass and cost consequences.";
            case "EFFICIENCY" -> "Optimizes energy use, heat and operating burden rather than granting free extra hardware.";
            case "RESILIENCE" -> "Reinforces the installed subsystem's ability to withstand damage and continue contributing.";
            case "SPECIALIST" -> "Emphasizes the individual module family role; inspect the stage's exact tradeoff and numeric multipliers.";
            default -> "Use the real research multiplier and prerequisite shown for this stage.";
        };
    }
    private static String subsystemExplanation(String code){
        SurvivalHardwareCatalog.Variant variant=SurvivalHardwareCatalog.findVariant(code);
        if(variant!=null)
            return subsystemExplanation(variant.root())+
                    " This separately mounted variant requires Tech "+
                    variant.requiredTier()+"; it changes mass, power, heat, "+
                    "integrity, expense and actual fitted performance."+
                    (variant.name().startsWith("UNSAFE RETROFIT")?
                            " UNSAFE RETROFIT: deterministic hardware faults "+
                            "remain possible under combat stress.":"");
        return switch(code){
            case "KIN" -> "Kinetic battery. Fires physical ballistic rounds; the chosen ammunition alters damage, velocity, penetration, splash and detection. Requires firing power, heat budget and stored rounds.";
            case "MSL" -> "Missile launcher. Spawns physical guided missiles with finite ammunition. Each rack selects its own warhead, salvo pattern, seeker and propulsion options under its technology license.";
            case "TOR" -> "Torpedo launcher. Heavy guided payloads trade salvo size and reload for capital-target damage. Torpedoes have physical signatures, fuel, interception health and launch modes.";
            case "LAS" -> "Laser emitter. Generates actual beam attacks with selectable researched laser family. Continuous or pulsed output is gated by reactor power, cooling, range and hardpoint location.";
            case "PLA" -> "Plasma projector. A dedicated thermal-energy weapon family with its own damage and defense interactions. A plasma module is not a free upgrade to every laser.";
            case "PAR" -> "Particle lance. Directed high-energy particle weapon fitted to a physical weapon socket, with its own range, damage and heat constraints.";
            case "PD" -> "Point-defense battery. Tracks eligible threatening missiles, torpedoes, ballistic rounds or small craft according to persistent interception and protection policies.";
            case "FLK" -> "Flak projector. Physical area-defense module used to counter crowded inbound threats; availability still depends on sensor acquisition, valid target and installed ammunition/energy.";
            case "SHD" -> "Shield generator. Supplies local shield protection and regeneration to its own fitted vessel; does NOT automatically project shield power to other ships.";
            case "SIF" -> "Structural integrity field. Reinforces the vessel's survivability through an installed defense socket; it is not a remote repair beam.";
            case "ARM" -> "Ablative armor. Provides physical damage absorption which can erode locally and expose structure and installed systems to subsequent hits.";
            case "REA" -> "Power plant/reactor. Supplies onboard electrical power for thrust, shields, weapons and service subsystems, while generating heat and mass costs.";
            case "RAD" -> "Radiator/cooling system. Dissipates physical heat accumulated by reactors, engines, weapon volleys and specialist support machinery.";
            case "ENG" -> "Engine and thrusters. Determines ship mobility and maneuvering capability, subject to actual propulsion stats and captain speed-limit orders.";
            case "SEN" -> "Sensor array. Detects targets and ordnance by range, signature and radar cross section. Sensor performance gates real target acquisition.";
            case "ECM" -> "Electronic warfare. Interferes with hostile acquisition and guided seeker locks; countermeasures never erase a ship's physical presence.";
            case "FTR" -> "Fighter hangar. Stores and launches actual autonomous craft with finite fuel, ammunition, weapons, radar signatures and pilot orders.";
            case "DRN" -> "Drone controller. Carries and launches actual drones; their capabilities and reserves derive from installed equipment and finite magazines.";
            case "REP" -> "Damage-control equipment. Restores the owning vessel through installed repair machinery; not the long-range projector or area aura used on allies.";
            case "MAG" -> "Ordnance magazine. Expands physical ammunition logistics and replenishment capacity; an empty launcher is not armed by its cosmetic model.";
            case "XFR" -> "Shield transfer projector. An explicitly fitted, ranged support channel for helping allied shields using actual power, range and heat constraints.";
            case "HPR" -> "Long-range hull restoration projector. Applies measured remote hull repair to a real allied target, subject to distance, reactor power and line-of-service rules.";
            case "APR" -> "Armor restoration projector. Repairs allied armor rather than freely repairing hull, shield and all resource channels at once.";
            case "PWR" -> "Power transfer projector. Sends bounded electrical power to allied vessels, a separate service channel from shield, armor and hull restoration.";
            case "DHL" -> "Hull service drone bay. Launches finite repair drones that physically assist allied hulls. Drone capacity and deployed reserves are not infinite.";
            case "DSH" -> "Shield service drone bay. Launches dedicated shield service drones with a separate finite charge stock and corresponding defense target priorities.";
            case "DAR" -> "Armor service drone bay. Provides specialized drone-based armor restoration with real equipment, ammunition and target constraints.";
            case "DPW" -> "Power service drone bay. Dispatches finite electrical support drones to allied vessels; not a global passive reactor aura.";
            case "AHL" -> "Hull restoration aura. Emits a restrained spherical local support field; its radius and low gain follow installed machinery rather than free fleetwide healing.";
            case "ASH" -> "Shield restoration aura. Offers weak local shield replenishment to vessels in physical range, separately from dedicated active shield projectors.";
            case "AAR" -> "Armor restoration aura. Provides bounded armor support to nearby friendly ships only within its actual radius.";
            case "APW" -> "Power restoration aura. Offers modest local electrical assistance, separate from direct transfer projectors.";
            case "CRW" -> "Crew accommodation. Physical habitat module provides berths; a warship cannot legally commission crew solely from hull art or nominal tonnage.";
            case "LFS" -> "Life-support system. Supports onboard crew through an installed habitat socket. Both accommodation and actual life support matter.";
            case "DMC" -> "Dedicated damage-control equipment. Restores actual hull damage using installed shipboard repair machinery.";
            case "HCR" -> "Hardened crew compartment. Provides protected physical berths and reduces casualties from penetrating strikes.";
            case "CIC" -> "Armored command citadel. Shields command crew and combat coordination against subsystem damage.";
            case "FBN" -> "Armored fuel bunker. Carries extra propellant and reduces real fuel loss after a penetrating hit.";
            case "AMV" -> "Isolated ammunition vault. Increases real magazine capacity and reduces ammunition-loss exposure.";
            case "EPD" -> "Dedicated escape-pod battery. Evacuates up to a finite installed capacity of surviving crew after ship destruction.";
            case "RCB" -> "Recovery beacon. Records real wreck coordinates; only a friendly victory may recover capped hull scrap, scaled by installed beacon effectiveness.";
            case "PGO" -> "UNSAFE RETROFIT: overdriven governor gives more power but increases heat and seeded reactor fault risk.";
            case "JPB" -> "UNSAFE RETROFIT: illegally modified plasma confinement bank gives high output at catastrophic stability and thermal cost.";
            case "IBR" -> "UNSAFE RETROFIT: improvised battery pack gives cheap emergency power with seeded command and sensing failures.";
            case "IST" -> "UNSAFE RETROFIT: improvised thermal shunt increases cooling but risks seeded thermal-system failure.";
            case "IRG" -> "UNSAFE RETROFIT: illicit reactor tap yields surplus power with high reactor and crew danger.";
            default -> "Purpose-built physical subsystem. Read its exact installed category, mass, heat, energy, effect and research tradeoffs; no free hull bonuses.";
        };
    }
    private static void ammo(List<Article> a,String kind,String name,AmmunitionTable.Profile p){
        a.add(new Article("ammo."+kind+"."+name,"AMMUNITION",kind+" / "+name,
            "Multipliers against base fitted weapon: damage x"+number(p.damage())+
                ", speed x"+number(p.speed())+", durability x"+number(p.durability())+".",
            "Radar size x"+number(p.crossSection())+", emissions x"+number(p.emissions())+
                ", armor bypass fraction "+number(p.armorBypass())+".",
            "Area effect radius "+number(p.blastRadius())+", splash fraction "+
                number(p.blastFraction())+", energy drain "+number(p.energyDrain())+
                ", shield drain "+number(p.shieldDrain())+".",
            "Launch energy x"+number(p.powerCost())+", heat x"+number(p.heatCost())+
                ", reload time x"+number(p.reloadCost())+".",
            "This is selected physical ammunition, not a free gun. The installed weapon and its research branch determine what a fleet may load."));
    }
    private static String orderExplanation(int i){
        return switch(i){
            case 0 -> "Aggression controls how readily this captain closes and pursues hostile targets.";
            case 1 -> "Caution biases survival, distance control and engagement decisions.";
            case 2 -> "Below the configured remaining hull percentage, this vessel attempts to withdraw from combat.";
            case 3 -> "Falling below the configured shield energy threshold can trigger fallback without requiring hull damage.";
            case 4 -> "Choose free maneuver, hold, keep moving, cautious or last-stand positional doctrine.";
            case 5 -> "Choose how hostile targets are prioritized according to the authoritative BattleOrders targeting modes.";
            case 6 -> "Select which defensive targets installed point-defense systems may engage.";
            case 7,8,9 -> "Adjust relative priority for fighter, escort or capital-class targets; other priorities remain active.";
            case 10,11,12 -> "Set the preferred engagement distance for the associated fighter, escort or capital-class category.";
            case 13 -> "Limit vessel engine speed to an adjustable fraction of its physical maximum.";
            case 14 -> "Choose the point-defense interception plan, governing threat handling and firing choices.";
            case 15 -> "Choose the point-defense protection scope, including when allied vessels may be protected.";
            case 16 -> "Choose fighter launch and mission doctrine for an actual carried wing.";
            case 17,18,19,20 -> "Bind one of four distinct defend-target vessel slots; each must refer to a real permitted deployment ally.";
            case 21 -> "Assign a real escort target vessel; the captain then adjusts course to protect it.";
            case 22 -> "Configure the escort formation range relative to the assigned real vessel.";
            case 23 -> "Select immediate offensive fire or hold the captain\u0027s offensive weapons while defensive interception remains available.";
            case 24 -> "Choose unrestricted ammunition, automatic rationing, protected reserve, spaced volleys or energy weapons only. Installed magazines and launchers still govern physical availability.";
            case 25 -> "Configure the remaining-ammunition percentage at which automatic rationing begins, without creating additional shells.";
            case 26 -> "Protect a chosen percentage of the actual ammunition magazine from discretionary salvos.";
            case 27 -> "Protect at least this many real rounds when the ammunition reserve policy applies.";
            case 28 -> "Space successive salvos by a minimum number of simulation ticks; do not fabricate additional barrels or ammunition.";
            case 29 -> "Select unrestricted thrust, economy, protected fuel reserve or automatic withdrawal at reserve. All modes use one actual fuel tank.";
            case 30 -> "Specify the fraction of physical onboard fuel reserved from discretionary thrust or used as the automatic withdrawal threshold. It is not a separate fuel tank.";
            case 31 -> "Start reducing acceleration and target speed below this remaining fuel percentage when the chosen fuel policy economizes.";
            default -> throw new IllegalArgumentException("Unknown doctrine row");
        };
    }
    /**
     * Search the exact same ordered text fragments that Article.searchable
     * concatenates, but never construct its large formatted Article object
     * just to test a candidate. KMP state persists across fragment boundaries
     * so arbitrary multi-word/number matches remain exact.
     *
     * The built-in catalog uses ASCII identifiers/metadata; non-ASCII names
     * and tradeoffs fall back to the authoritative article formatter to
     * preserve Locale.ROOT Unicode context-sensitive folding.
     */
    private static final class UpgradeTextProbe {
        private final String needle;
        private final int[] prefix;
        private final boolean digits;
        private final boolean decimals;
        private int matched;
        UpgradeTextProbe(String needle){
            this.needle=needle;
            prefix=new int[needle.length()];
            for(int i=1,k=0;i<needle.length();i++){
                while(k>0&&needle.charAt(i)!=needle.charAt(k))
                    k=prefix[k-1];
                if(needle.charAt(i)==needle.charAt(k))k++;
                prefix[i]=k;
            }
            boolean hasDigit=false;
            for(int i=0;i<needle.length();i++)
                if(needle.charAt(i)>='0'&&needle.charAt(i)<='9'){
                    hasDigit=true;break;
                }
            digits=hasDigit;
            decimals=hasDigit||needle.indexOf('.')>=0;
        }
        void reset(){matched=0;}
        boolean feed(String part){
            // Avoid transient lowercase strings for the overwhelmingly
            // ASCII canonical research rows. The uncommon Unicode case is
            // handled by the exact Article fallback above.
            for(int i=0;i<part.length();i++){
                char c=part.charAt(i);
                if(c>='A'&&c<='Z')c=(char)(c+('a'-'A'));
                else if(c=='_')c=' ';
                while(matched>0&&needle.charAt(matched)!=c)
                    matched=prefix[matched-1];
                if(needle.charAt(matched)==c)matched++;
                if(matched==needle.length())return true;
            }
            return false;
        }
        boolean integer(int value){
            if(!digits){matched=0;return false;}
            return feed(Integer.toString(value));
        }
        boolean decimal(double value){
            // A fixed 2-decimal number always contains ASCII digits and '.'.
            // A query without either cannot match across that fragment.
            if(!decimals){matched=0;return false;}
            return feed(number(value));
        }
    }
    private static boolean needsUnicodeCaseFold(String source){
        // Existing authored technology uses U+2014 em-dashes extensively.
        // Punctuation does not change under Locale.ROOT case folding and
        // must NOT force a 7-line Article allocation for every research row
        // on a 16 MiB Android/Java heap. Custom uppercase Unicode letters
        // still use the authoritative whole-article formatter.
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i);
            if(c>127&&Character.toLowerCase(c)!=c)return true;
        }
        return false;
    }
    private static boolean matchesUpgradeText(TechnologyCatalog.Upgrade u,
                                              String needle,UpgradeTextProbe m){
        if(u==null)throw new IllegalStateException("Missing wiki technology");
        if(needsUnicodeCaseFold(u.name)||needsUnicodeCaseFold(u.tradeoff)||
                needsUnicodeCaseFold(u.prerequisite))
            return upgradeArticle(u).searchable.contains(needle);
        m.reset();
        // Order and punctuation intentionally reproduce exactly:
        // id + category + title + each of the seven article lines.
        return m.feed("upgrade.")||m.feed(u.id)||
                m.feed(" UPGRADES ")||m.feed(u.name)||m.feed(" / ")||m.feed(u.id)||
                m.feed(" Exact technology ID: ")||m.feed(u.id)||
                m.feed(". Faction ")||m.feed(u.faction)||
                m.feed(", subsystem ")||m.feed(u.subsystem)||
                m.feed(", branch ")||m.feed(u.track)||
                m.feed(", stage ")||m.integer(u.stage)||m.feed(" of 9.")||
                m.feed(" Prerequisite: ")||m.feed(u.prerequisite)||
                m.feed(". ")||m.feed(RESEARCH_COST_LABEL)||
                m.integer(u.researchCost)||m.feed("; industry cost ")||
                m.integer(u.industryCost)||m.feed(".")||
                m.feed(" Physical multipliers: mass x")||m.decimal(u.mass)||
                m.feed(", power x")||m.decimal(u.power)||
                m.feed(", heat x")||m.decimal(u.heat)||
                m.feed(", output x")||m.decimal(u.output)||
                m.feed(", accuracy x")||m.decimal(u.accuracy)||
                m.feed(". Additional multipliers: integrity x")||
                m.decimal(u.integrity)||m.feed(", shields x")||
                m.decimal(u.shield)||m.feed(", thrust x")||
                m.decimal(u.thrust)||m.feed(", cooling x")||
                m.decimal(u.cooling)||m.feed(", maintenance x")||
                m.decimal(u.maintenance)||m.feed(".")||
                m.feed(" Research branch purpose: ")||
                m.feed(branchExplanation(u.track))||
                m.feed(" Authoritative design tradeoff: ")||
                m.feed(u.tradeoff)||
                m.feed(" The fitted module must own the appropriate branch and stage. This is not a whole-ship automatic buff.");
    }

    List<Article> search(String category,String query){
        String needle=(query==null?"":query.trim()).toLowerCase(Locale.ROOT).replace('_',' ');
        String selected=(category==null?"ALL":category);
        // Unfiltered views remain a virtual paginated index.
        if(needle.isEmpty()){
            if(selected.equals("ALL"))return articles;
            if(selected.equals("UPGRADES"))return upgradeArticles;
        }
        // Preserve exact authoritative ID lookup without scanning every stage.
        if((selected.equals("ALL")||selected.equals("UPGRADES")) &&
                needle.startsWith("tech-") && needle.length()>15 &&
                Character.isDigit(needle.charAt(needle.length()-1))){
            TechnologyCatalog.Upgrade direct=technology.get(
                    needle.toUpperCase(Locale.ROOT));
            if(direct!=null)return List.of(upgradeArticle(direct));
        }
        List<Article> fixed=new ArrayList<>();
        if(!selected.equals("UPGRADES"))
            for(Article article:staticArticles)
                if((selected.equals("ALL")||article.category.equals(selected))
                        &&(needle.isEmpty()||article.searchable.contains(needle)))
                    fixed.add(article);
        if(!selected.equals("ALL")&&!selected.equals("UPGRADES"))return fixed;

        // Every upgrade Article starts with "upgrade.TECH-" and contains
        // the same research-cost label. Queries contained in either immutable
        // fragment match all 73,260 IDs; reuse the compact ID index rather
        // than retaining tens of thousands of duplicate formatted strings.
        // ALL-category static results are still filtered independently.
        if(RESEARCH_COST_SEARCH.contains(needle)||
                RESEARCH_ID_SEARCH_PREFIX.contains(needle))
            return fixed.isEmpty()?upgradeArticles:new IndexedArticles(fixed,upgradeIds);

        List<String> matchedIds=new ArrayList<>();
        UpgradeTextProbe probe=new UpgradeTextProbe(needle);
        for(String id:upgradeIds){
            // ID matches can skip all expensive virtual technology metadata.
            // Other queries stream the exact authored article text without
            // allocating its 7-line searchable paragraph and formatted page.
            if(id.toLowerCase(Locale.ROOT).contains(needle)||
                    matchesUpgradeText(technology.get(id),needle,probe))
                matchedIds.add(id);
        }
        return new IndexedArticles(fixed,matchedIds);
    }

}
