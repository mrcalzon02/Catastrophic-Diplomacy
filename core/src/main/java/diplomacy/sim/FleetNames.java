package diplomacy.sim;

/** Deterministically varied names with selectable fleet traditions. */
public final class FleetNames {
    private FleetNames(){}
    // Names are cultural display labels, not permanent vessel identifiers.
    public enum Style { NAVAL, MYTHIC, CELESTIAL, INDUSTRIAL, ALIEN,
        IMPERIAL, FRONTIER, SCIENTIFIC, CEREMONIAL, ORGANIC, SYNTHETIC, DESIGNATION,
        REPUBLIC, PRIVATEER, MERCHANT, NOMADIC, MONASTIC, EXPLORER, FOLKLORE, IRREVERENT,
        GRIMDARK, NEOCLASSICAL, ABSURDIST, GOTHIC, BAROQUE, UTOPIAN, DYSTOPIAN,
        CYBERPUNK, ANCIENT_EMPIRE, ARCHAEOLOGICAL, RETRO_SCI_FI, CORPORATE,
        FOLK_HEROIC, PHILOSOPHICAL, ROMANTIC, OCCULT, SATIRICAL, LOST_COLONY }
    private static final int GRIMDARK_ORDINAL=20;
    private static final String[][] FIRST={
        {"Resolute","Indomitable","Courageous","Vanguard","Dauntless","Stalwart","Valiant","Guardian"},
        {"Aegis","Valhalla","Orion","Nemesis","Basilisk","Titan","Erebus","Helios"},
        {"Distant","Radiant","Nebula","Eventide","Stellar","Silent","Luminous","Astral"},
        {"Iron","Forge","Copper","Foundry","Hammer","Quarry","Atlas","Cinder"},
        {"Zha","Qel","Thru","Vael","Ix","Korr","Nym","Zyx"}
    };
    private static final String[][] LAST={
        {"Horizon","Victory","Watch","Promise","Defender","Sentinel","Expedition","Crown"},
        {"Spear","Oath","Fury","Aegis","Covenant","Dawn","Judgment","Shield"},
        {"Pilgrim","Horizon","Drifter","Beacon","Current","Whisper","Expanse","Eclipse"},
        {"Engine","Works","Foundry","Anvil","Carrier","Mill","Pillar","Station"},
        {"Keth","Vran","Taal","Sora","Quin","Nex","Draal","Vesh"}
    };
    public static String generate(Style style,long seed,int serial){
        if(style==null||serial<1)throw new IllegalArgumentException("Invalid name seed");
        long scrambled=(seed^(serial*0x9e3779b97f4a7c15L));
        scrambled^=scrambled>>>30;scrambled*=0xbf58476d1ce4e5b9L;
        scrambled^=scrambled>>>27;scrambled*=0x94d049bb133111ebL;
        scrambled^=scrambled>>>31;
        int i=(int)Math.floorMod(scrambled,8),j=(int)Math.floorMod(scrambled>>>8,8);
        // The commission counter selects naming vocabulary; it is NOT part
        // of the permanent 16-digit identity and need not appear in the name.
        String index=String.format(java.util.Locale.ROOT,"%03d",serial);
        int a=(int)Math.floorMod(scrambled>>>17,16);
        int b=(int)Math.floorMod(scrambled>>>35,16);
        int variant=(int)Math.floorMod(scrambled>>>49,4);
        if(style.ordinal()<FIRST.length){
            String call=FIRST[style.ordinal()][i]+" "+LAST[style.ordinal()][j];
            // Three-part ceremonial, material and alien names: the original
            // two-word bank alone contained only 64 possible labels.
            if((variant&1)==0){
                String suffix=switch(style){
                    case NAVAL -> "of "+choose(a,
                            "Sol","The Vanguard","The North","Meridian","Courage",
                            "The Starward Line","Dawn","The Old Republic","Winter",
                            "First Light","The Home Fleet","The Reach","Victory",
                            "Perimeter Nine","The Long Watch","The Third Fleet");
                    case MYTHIC -> "of "+choose(a,
                            "The Titans","Olympus","The Fates","Ancient Kings",
                            "The Nine Halls","The Oracle","The Forge","The Hunt",
                            "The Three Moons","Myth","Fire","The Dawn",
                            "The Tempest","The Labyrinth","The First Age","Starlight");
                    case CELESTIAL -> "of "+choose(a,
                            "Sirius","Andromeda","Cygnus","The Pleiades",
                            "Orion","Altair","Deneb","Vega","The Milky Way",
                            "The Red Giant","The Nebula","Polaris","Solstice",
                            "Equinox","The Blue Sun","The Last Star");
                    case INDUSTRIAL -> "works "+choose(a,
                            "Alpha","Beta","Gamma","Delta","Omega","Forty",
                            "Prime","North","East","Outer","Ninth","Zero",
                            "Core","Deep","Red","Black");
                    case ALIEN -> choose(a,
                            "Zharr","Ul'ke","Qorath","Neth","Thyr","Varr",
                            "Zyk","Ol'nu","Khath","Shae","Rix","Draal",
                            "Xeth","Qa'lo","Myr","Vess");
                    default -> throw new IllegalStateException("Unsupported foundational name style");
                };
                String enriched=call+" "+suffix;
                if(enriched.length()<=70)call=enriched;
            }
            return style==Style.ALIEN?call:"CDS "+call;
        }
        String name=switch(style){
            case IMPERIAL -> "HIMS "+pick(new String[]{"Duchess","Emperor","Regent",
                    "Baroness","Margrave","Sovereign","Archduke","Viceroy"},i)
                    +" of "+pick(new String[]{"Ash","Halcyon","Veyr","Crownfall",
                    "Veridian","Istria","Nacre","Eldcrest"},j);
            case FRONTIER -> "Survey "+pick(new String[]{"Perimeter","Dawntrail",
                    "Longreach","Waypoint","Hinterland","Outrider","Farwatch","Lastlight"},i)
                    +"-"+pick(new String[]{"01","02","03","04","05","06","07","08"},j)
                    ;
            case SCIENTIFIC -> "RSV "+pick(new String[]{"Kepler","Herschel","Faraday",
                    "Curie","Sagan","Cassini","Hubble","Vera"},i)
                    +" "+pick(new String[]{"Array","Observatory","Surveyor","Laboratory",
                    "Prospector","Spectrometer","Probe","Mapper"},j);
            case CEREMONIAL -> "Sanctum of "+pick(new String[]{"First Light","The Vigil",
                    "Embers","Last Mercy","The Witness","Requiem","Dawn","Pilgrims"},i)
                    +" "+pick(new String[]{"I","II","III","IV","V","VI","VII","VIII"},j)
                    ;
            case ORGANIC -> pick(new String[]{"Vha","Kth","Yrr","Oss","Luum","Thae",
                    "Zhur","Myr"},i)+"'"+pick(new String[]{"brood","spore","root",
                    "mantle","carapace","tendril","husk","bloom"},j);
            case SYNTHETIC -> "AXIOM/"+pick(new String[]{"VX","KH","QY","PT","OM",
                    "XR","CN","TZ"},i)+"-"+pick(new String[]{"CORE","NODE","SYNC",
                    "RELAY","VECTOR","ARRAY","GATE","SHELL"},j)+"-"+index;
            case DESIGNATION -> "FG-"+String.format(java.util.Locale.ROOT,"%02d",i+10)
                    +"-"+String.format(java.util.Locale.ROOT,"%02d",j+1)+"-"+index;
            case REPUBLIC -> "RNS "+pick(new String[]{"Liberty","Resolve","Unity","Accord",
                    "Freehold","Courage","Assembly","Civic"},i)+" "+pick(new String[]{
                    "Star","Horizon","Concord","Mercy","Guard","Beacon","Legacy","Dawn"},j);
            case PRIVATEER -> pick(new String[]{"Lucky","Red","Wayward","Laughing",
                    "Ragged","Black","Gentle","Last"},i)+" "+pick(new String[]{
                    "Corsair","Kestrel","Wager","Cutlass","Revenge","Gambit","Comet","Rumrunner"},j);
            case MERCHANT -> "MV "+pick(new String[]{"Fair","Open","Copper","Grand",
                    "Fortune","Steady","Blue","Golden"},i)+" "+pick(new String[]{
                    "Exchange","Ledger","Freighter","Dividend","Parcel","Harbor","Venture","Cargo"},j);
            case NOMADIC -> pick(new String[]{"Dust","Long","Far","Wandering",
                    "Moon","Storm","Quiet","Bright"},i)+"-"+pick(new String[]{
                    "Walker","Runner","Pilgrim","Traveler","Herder","Seeker","Path","Keeper"},j);
            case MONASTIC -> "Vessel of "+pick(new String[]{"Vigil","Silence","Grace","Solace",
                    "Dawn","Witness","Mercy","Penance"},i)+" "+pick(new String[]{
                    "Prime","Secundus","Tertius","Quartus","Quintus","Sextus","Septimus","Octavus"},j);
            case EXPLORER -> "CSV "+pick(new String[]{"Uncharted","Farpoint","Drifting","Blue",
                    "Second","Endless","Bold","Quiet"},i)+" "+pick(new String[]{
                    "Horizon","Compass","Latitude","Discovery","Passage","Beacon","Survey","Crossing"},j);
            case FOLKLORE -> pick(new String[]{"Old","Silver","Dancing","Wild",
                    "Morning","Midnight","Laughing","Winter"},i)+" "+pick(new String[]{
                    "Fox","Hare","Stag","Serpent","Raven","Wolf","Witch","Lion"},j);
            case IRREVERENT -> pick(new String[]{"Mostly","Probably","Definitely","Strictly",
                    "Unreasonably","Questionably","Nominally","Temporarily"},i)+" "+pick(new String[]{
                    "Harmless","Insured","Operational","Innocent","Stealthy","Repaired","Neutral","Official"},j);
            case GRIMDARK -> "The "+choose(a,
                    "Gallows","Bleeding","Ashen","Penitent","Forsaken","Blackened",
                    "Ironbound","Funereal","Mourning","Excommunicated","Wretched",
                    "Hollow","Martyr's","Blighted","Final","Unforgiven")+" "+
                    choose(b,"Reliquary","Tribunal","Sepulcher","Penance","Cathedral",
                    "Malediction","Oblivion","Dirge","Inquisition","Tomb","Sentence",
                    "Covenant","Absolution","Requiem","Epitaph","Vigil");
            case NEOCLASSICAL -> choose(a,"Aurelia","Concordia","Pallas","Minerva",
                    "Cassiopeia","Alexandria","Persephone","Ariadne","Thalia","Orpheus",
                    "Praetoria","Calliope","Ionia","Antigone","Elysia","Octavia")
                    +" "+choose(b,"Triumphans","Invicta","Caelestis","Magna",
                    "Eterna","Aeterna","Nobilis","Serena","Imperatrix","Pacifica",
                    "Virtus","Aurora","Lucentia","Maxima","Fidelis","Victoria");
            case ABSURDIST -> choose(a,"A Minor","An Unscheduled","The Suspicious",
                    "My Other","A Perfectly Ordinary","Three","The Unlicensed",
                    "Mostly","An Unexpected","The Entirely","A Very Large",
                    "The Last","A Questionable","Nobody's","The Bureaucratic",
                    "An Extremely Polite")+" "+
                    choose(b,"Incident","Teapot","Apology","Regret","Committee",
                    "Sandwich","Argument","Turnip","Insurance Claim","Goose",
                    "Pothole","Misunderstanding","Desk","Tax Return","Problem","Circus");
            case GOTHIC -> choose(a,"Midnight","Velvet","Crimson","Black",
                    "Shrouded","Weeping","Nocturnal","Raven","Sable","Moonlit",
                    "Pale","Thorned","Hallowed","Obsidian","Silent","Ebon")+" "+
                    choose(b,"Abbey","Bell","Veil","Rose","Chapel","Lament",
                    "Revenant","Crypt","Spire","Choir","Grave","Coronet",
                    "Wraith","Mausoleum","Nocturne","Vesper");
            case BAROQUE -> choose(a,"Golden","Celestial","Magnificent","Grand",
                    "Imperial","Sovereign","Jubilant","Radiant","Illustrious",
                    "Opulent","Glorious","Royal","Pearlescent","Gilded","Splendid",
                    "Exalted")+" "+choose(b,"Procession","Oratorio","Palace",
                    "Fanfare","Symphony","Miracle","Crescendo","Triumph","Basilica",
                    "Archangel","Cavalcade","Ceremony","Revelation","Pavilion",
                    "Monument","Overture");
            case UTOPIAN -> choose(a,"Common","Shared","Open","Bright","Gentle",
                    "Kindred","Renewed","Harmonious","Free","Future","Equal",
                    "Patient","Hopeful","Peaceful","Living","United")+" "+
                    choose(b,"Garden","Horizon","Harbor","Tomorrow","Promise",
                    "Exchange","Accord","Dawn","Commons","Assembly","Light",
                    "Sanctuary","Vision","Concord","Home","Bridge");
            case DYSTOPIAN -> choose(a,"Mandatory","Compliance","Civic","Authorized",
                    "Standard","Quota","Emergency","State","Restricted","Official",
                    "Collective","Temporary","Corrective","Departmental","Silent",
                    "Executive")+" "+choose(b,"Harmony","Observer","Efficiency",
                    "Allocation","Consent","Directive","Enforcement","Audit",
                    "Ration","Ordinance","Monitor","Procedure","Mandate","Quota",
                    "Reassignment","Registry");
            case CYBERPUNK -> choose(a,"Neon","Ghost","Chrome","Broken","Wired",
                    "Encrypted","Blacksite","Zero-Day","Circuit","Synth",
                    "Data","Glass","Void","Quantum","Backdoor","Static")+" "+
                    choose(b,"Runner","Firewall","Jackal","Relay","Pulse",
                    "Cascade","Memory","Wraith","Payload","Protocol","Cipher",
                    "Vault","Interface","Shard","Daemon","Overclock");
            case ANCIENT_EMPIRE -> choose(a,"Throne of","Scepter of","Heir to",
                    "Pillar of","Bastion of","Tribute to","Imperium of",
                    "Legacy of","Crown of","Sanctum of","Seal of","Witness to",
                    "Harrowing of","Remnant of","Aegis of","Temple of")+" "+
                    choose(b,"Aram","Nine Kings","Orichal","The First Sun",
                    "Kadesh","Asterion","Tiamat","Twelve Cities","Nimrud",
                    "Seven Gates","Nara","Stone","The Old Law","Ur","The Dawn",
                    "Forgotten Gods");
            case ARCHAEOLOGICAL -> "RV "+choose(a,"Palimpsest","Tell","Stratum",
                    "Lithic","Midden","Relic","Survey","Anastylosis","Axiom",
                    "Artifact","Codex","Tablet","Digsite","Ruin","Provenance",
                    "Antiquity")+" "+choose(b,"Alpha","Beta","Delta","Prime",
                    "Gamma","North","Outer","Inner","VII","IX","Sigma",
                    "One","Two","Last","Deep","East");
            case RETRO_SCI_FI -> choose(a,"Atomic","Space Age","Rocket","Cosmic",
                    "Galactic","Radiant","Astro","Planetary","Supersonic",
                    "Turbo","Solar","Meteor","Star","Hyper","Moon","Pulsar")+" "+
                    choose(b,"Patrol","Ranger","Queen","Cadet","Voyager","Pioneer",
                    "Express","Defender","Skipper","Beacon","Cruiser",
                    "Rocket","Saucer","Sentinel","Explorer","Adventurer");
            case CORPORATE -> choose(a,"Synergy","Quarterly","Shareholder",
                    "Fiscal","Leveraged","Premium","Enterprise","Brand",
                    "Acquisition","Executive","Market","Strategic",
                    "Return-on","Capital","Efficient","Best-Practice")+" "+
                    choose(b,"Alignment","Outlook","Dividend","Merger","Margin",
                    "Portfolio","Expansion","Revenue","Compliance","Growth",
                    "Solution","Asset","Equity","Forecast","Optimization","Value");
            case FOLK_HEROIC -> choose(a,"Bold","Old","Storm","Bright","Long",
                    "Golden","Wild","Red","Swift","Fair","True","Young",
                    "Wandering","Oak","High","Winter")+" "+
                    choose(b,"Stag","Lion","Knight","Lantern","Sparrow","Heron",
                    "Shield","Oak","Wanderer","Bridge","Song","Fiddle",
                    "Wolf","Bell","Sword","Fox");
            case PHILOSOPHICAL -> choose(a,"The Problem of","The Nature of",
                    "On the Question of","Principle of","The Burden of",
                    "Meditations on","An Argument for","Against","The Paradox of",
                    "Measure of","The Limit of","A Theory of","The Meaning of",
                    "The Absence of","The Weight of","A Case for")+" "+
                    choose(b,"Being","Virtue","Change","Nothingness","Time",
                    "Reason","Freedom","Certainty","Consciousness","Justice",
                    "Order","Motion","Doubt","Memory","Existence","Light");
            case ROMANTIC -> choose(a,"Beloved","Morning","Last","Distant",
                    "Eternal","Soft","Wild","Hidden","Silver","Blue",
                    "Tender","Golden","Autumn","First","Lost","Summer")+" "+
                    choose(b,"Reunion","Promise","Letter","Sonnet","Embrace",
                    "Kiss","Ballad","Memory","Devotion","Dance","Bouquet",
                    "Lullaby","Dream","Lament","Farewell","Hope");
            case OCCULT -> choose(a,"Thirteenth","Nameless","Veiled","Eldritch",
                    "Forbidden","Abyssal","Pale","Hollow","Sealed","Cursed",
                    "Unseen","Starless","Ancient","Cryptic","Hidden","Dark")+" "+
                    choose(b,"Oracle","Threshold","Pentacle","Talisman",
                    "Apostle","Grimoire","Chalice","Rite","Conjunction",
                    "Conclave","Witness","Offering","Watcher","Sign",
                    "Labyrinth","Coven");
            case SATIRICAL -> choose(a,"With All Due","Subject to","As Per",
                    "Absolutely No","Officially","We Regret the",
                    "Terms and","Reasonably","Allegedly","Pending",
                    "Unanimous","Limited","Perpetual","Statutory","Substantial",
                    "Provisional")+" "+choose(b,"Respect","Approval","Procedure",
                    "Fault","Refund","Conditions","Assurance","Liability",
                    "Meeting","Notice","Quorum","Exception","Disclaimer",
                    "Review","Inquiry","Authority");
            case LOST_COLONY -> choose(a,"Last","Forgotten","Second","Distant",
                    "Broken","Old","New","Silent","Homeward","Buried",
                    "Far","Lonely","Return","Remnant","Unclaimed","Settler's")+" "+
                    choose(b,"Hearth","Homestead","Beacon","Orchard","Signal",
                    "Lullaby","Pilgrim","Lantern","Crossing","Wellspring",
                    "Sunrise","Outpost","Valley","Promise","Legacy","Harvest");
            default -> throw new IllegalArgumentException("Unrecognized naming philosophy");
        };
        // A third cultural epithet yields far more combinations than the
        // original two-word lists without copying the vessel's 16-digit ID.
        if(style.ordinal()>=GRIMDARK_ORDINAL&&(variant==1||variant==3)){
            String epithet=switch(style){
                case GRIMDARK,GOTHIC,OCCULT -> "of "+choose((int)((scrambled>>>23)&15),
                        "Dusk","The Last Bell","The Hollow Moon","Broken Saints",
                        "Ash","The End","Silent Graves","Nightfall","The Void",
                        "Forgotten Oaths","The Deep","The Damned","Black Stars",
                        "The Sepulcher","The Choir","No Return");
                case NEOCLASSICAL,BAROQUE,ANCIENT_EMPIRE -> "of "+choose((int)((scrambled>>>23)&15),
                        "Aurea","Arcadia","Helicon","The Seven Isles","Asteria",
                        "Illyria","Elysium","Marmora","Thessalia","Caspia",
                        "The Golden Sea","Athenia","Cyrene","Tyria","Lycia","Corinth");
                case ABSURDIST,IRREVERENT,SATIRICAL,CORPORATE -> choose((int)((scrambled>>>23)&15),
                        "Pending Review","By Appointment","Under Protest","Without Warranty",
                        "At Short Notice","In Theory","For Accounting","On Tuesday",
                        "At Your Peril","Until Further Notice","Almost Certainly",
                        "Terms Apply","No Returns","Due to Circumstances","As Discussed",
                        "More or Less");
                default -> "of "+choose((int)((scrambled>>>23)&15),
                        "The Outer Reach","Lost Suns","The Far Shore","Tomorrow",
                        "The First Dawn","The Ninth Sky","The Boundary",
                        "New Horizons","Last Light","The Long Road","The Quiet",
                        "The Deep Field","The Meridian","Far Places","The Passage",
                        "The Open Sea");
            };
            String candidate=name+" "+epithet;
            if(candidate.length()<=70)name=candidate;
        }
        return name;
    }
    /** Reroll just the display name on a deterministic salted stream. */
    public static String unique(Style style,long seed,int serial,
                                java.util.Collection<String> alreadyUsed){
        if(alreadyUsed==null)throw new IllegalArgumentException("Missing reserved ship names");
        java.util.HashSet<String> reserved=new java.util.HashSet<>();
        for(String label:alreadyUsed){
            if(label==null)throw new IllegalArgumentException("Invalid owned name");
            reserved.add(label.toLowerCase(java.util.Locale.ROOT));
        }
        for(int attempt=0;attempt<2048;attempt++){
            long salt=seed+0x9e3779b97f4a7c15L*attempt;
            String candidate=generate(style,salt,serial);
            if(!reserved.contains(candidate.toLowerCase(java.util.Locale.ROOT)))
                return candidate;
        }
        throw new IllegalArgumentException("Name pool exhausted in selected tradition");
    }
    private static String pick(String[] options,int index){return options[index];}
    private static String choose(int slot,String... options){return options[slot];}
}
