package diplomacy.client;

import diplomacy.tech.TechnologyCatalog;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.HullProgression;
import diplomacy.shipyard.ShipLineages;
import diplomacy.shipyard.ModuleVersions;
import diplomacy.sim.AmmunitionTable;
import diplomacy.sim.OrdnanceDoctrine;
import diplomacy.sim.OrdnancePropulsion;
import diplomacy.sim.FighterModel;
import diplomacy.sim.ShipStandingOrders;
import diplomacy.sim.BattleOrders;
import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private final List<Article> articles;
    private GameWikiCatalog(List<Article> list){
        list.sort(Comparator.comparing((Article e)->e.category)
                .thenComparing(e->e.title).thenComparing(e->e.id));
        articles=List.copyOf(list);
    }
    List<Article> articles(){return articles;}
    int size(){return articles.size();}
    static String nice(String name){return name.replace('_',' ');}
    private static String number(double n){
        return String.format(Locale.ROOT,"%.2f",n);
    }
    static GameWikiCatalog build(TechnologyCatalog catalog){
        if(catalog==null)throw new IllegalArgumentException("Wiki requires live technology data");
        List<Article> a=new ArrayList<>(catalog.upgradeCount()+catalog.componentCount()+160);
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
            "Armor can erode locally; physically damaged hulls receive localized scorch and destruction effects. Shield flashes and beam corona are render-only feedback, not extra gameplay damage.",
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
                "This is a physical installable base component. A hull alone does not supply the effect; select a compatible socket and check capacity, power and heat.",
                "Research tracks: OUTPUT for stronger effect, EFFICIENCY for lower operating burdens, RESILIENCE for survivability, SPECIALIST for role-specific specialization. Choose only one per installed socket.",
                "Find its exact upgrade pages by searching "+m.faction+" "+m.subsystem+" or "+m.id+"."));
        for(TechnologyCatalog.Upgrade u:catalog.upgrades().values())
            a.add(new Article("upgrade."+u.id,"UPGRADES",u.name+" / "+u.id,
                "Exact technology ID: "+u.id+". Faction "+u.faction+", subsystem "+u.subsystem+
                    ", branch "+u.track+", stage "+u.stage+" of 9.",
                "Prerequisite: "+u.prerequisite+". Research cost "+u.researchCost+
                    "; industry cost "+u.industryCost+".",
                "Physical multipliers: mass x"+number(u.mass)+", power x"+number(u.power)+
                    ", heat x"+number(u.heat)+", output x"+number(u.output)+
                    ", accuracy x"+number(u.accuracy)+".",
                "Additional multipliers: integrity x"+number(u.integrity)+", shields x"+
                    number(u.shield)+", thrust x"+number(u.thrust)+", cooling x"+
                    number(u.cooling)+", maintenance x"+number(u.maintenance)+".",
                "Authoritative design tradeoff: "+u.tradeoff,
                "The fitted module must own the appropriate branch and stage. This is not a whole-ship automatic buff."));
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
                    "Physical launch doctrine: "+p.real()+" armed projectiles and "+p.decoys()+" decoys in a salvo; total "+p.ammoNeeded()+" units of stored ammunition.",
                    "Spacing "+p.intervalTicks()+" simulation ticks; cold ignition delay "+p.ignitionDelayTicks()+" ticks; reload x"+number(p.reloadMultiplier())+".",
                    "Per-projectile damage x"+number(p.damageMultiplier())+
                        ", velocity x"+number(p.velocityMultiplier())+", durability x"+
                        number(p.durabilityMultiplier())+", radar cross-section x"+
                        number(p.crossSectionMultiplier())+", emissions x"+number(p.signatureMultiplier())+".",
                    "Payload pattern "+nice(p.payload().name())+". Decoys are real projectiles, not free visual flares. Launcher mounts and missile guidance remain independently fitted."));
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
        return new GameWikiCatalog(a);
    }
    private static void guide(List<Article> a,String id,String title,String... body){
        a.add(new Article("guide."+id,"GUIDES",title,body));
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
            default -> throw new IllegalArgumentException("Unknown doctrine row");
        };
    }
    List<Article> search(String category,String query){
        String needle=(query==null?"":query.trim()).toLowerCase(Locale.ROOT).replace('_',' ');
        String selected=(category==null?"ALL":category);
        List<Article> hits=new ArrayList<>();
        for(Article article:articles)
            if((selected.equals("ALL")||article.category.equals(selected))
                    &&(needle.isEmpty()||article.searchable.contains(needle)))
                hits.add(article);
        return hits;
    }
}
