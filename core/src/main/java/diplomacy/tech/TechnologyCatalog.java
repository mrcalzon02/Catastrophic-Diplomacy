package diplomacy.tech;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifiable, data-driven upgrade catalogue. Strict input parser, no external dependencies. */
public final class TechnologyCatalog {
    public static final String UPGRADES_RESOURCE = "/technology/upgrades-v0.1.csv";
    public static final String COMPONENTS_RESOURCE = "/technology/components-v0.1.csv";
    private static final int COLS = 20;
    private static final Set<String> TRACKS = Set.of("OUTPUT", "EFFICIENCY", "RESILIENCE", "SPECIALIST");
    private static final Set<String> FACTIONS = Set.of("TER", "DRA", "CAR", "ZIP", "UZI", "MAN");

    public static final class Upgrade {
        public final String id, faction, subsystem, track, name, prerequisite, tradeoff;
        public final int stage, researchCost, industryCost;
        public final double mass, power, heat, output, accuracy, integrity, shield, thrust, cooling, maintenance;

        Upgrade(List<String> cells) {
            id = cells.get(0); faction = cells.get(1); subsystem = cells.get(2);
            track = cells.get(3); stage = integer(cells.get(4), "stage");
            name = cells.get(5); prerequisite = cells.get(6);
            researchCost = integer(cells.get(7), "research cost");
            industryCost = integer(cells.get(8), "industry cost");
            mass = positive(cells.get(9)); power = positive(cells.get(10));
            heat = positive(cells.get(11)); output = positive(cells.get(12));
            accuracy = positive(cells.get(13)); integrity = positive(cells.get(14));
            shield = positive(cells.get(15)); thrust = positive(cells.get(16));
            cooling = positive(cells.get(17)); maintenance = positive(cells.get(18));
            tradeoff = cells.get(19);
            if (!FACTIONS.contains(faction) || !TRACKS.contains(track) || stage < 1 || stage > 6
                    || subsystem.isBlank() || name.isBlank() || tradeoff.isBlank()
                    || researchCost <= 0 || industryCost <= 0)
                throw new IllegalArgumentException("Invalid upgrade metadata: " + id);
            String expected = "TECH-" + faction + "-" + subsystem + "-" + track + "-" + String.format(java.util.Locale.ROOT, "%02d", stage);
            if (!expected.equals(id)) throw new IllegalArgumentException("Noncanonical technology ID: " + id);
        }
    }

    private final Map<String, Upgrade> upgrades;
    private final Set<String> components;
    private TechnologyCatalog(Map<String, Upgrade> map, Set<String> base) {
        upgrades = Collections.unmodifiableMap(map);
        components = Collections.unmodifiableSet(base);
    }
    public int upgradeCount() { return upgrades.size(); }
    public int componentCount() { return components.size(); }
    public Upgrade get(String id) { return upgrades.get(id); }
    public boolean hasBase(String id) { return components.contains(id); }
    public Map<String, Upgrade> upgrades() { return upgrades; }

    public static TechnologyCatalog loadDefault() throws IOException {
        try (InputStream upgrades = TechnologyCatalog.class.getResourceAsStream(UPGRADES_RESOURCE);
             InputStream components = TechnologyCatalog.class.getResourceAsStream(COMPONENTS_RESOURCE)) {
            if (upgrades == null || components == null) throw new IOException("Bundled technology resources missing");
            return load(upgrades, components);
        }
    }
    public static TechnologyCatalog load(InputStream upgradeStream, InputStream componentStream) throws IOException {
        if (upgradeStream == null || componentStream == null) throw new IllegalArgumentException("Both inputs required");
        List<List<String>> bases = readCsv(componentStream, 13);
        List<List<String>> rows = readCsv(upgradeStream, COLS);
        if (bases.size() < 2 || rows.size() < 2) throw new IllegalArgumentException("Missing technology table data");
        if (!bases.get(0).get(0).equals("Base ID") || !rows.get(0).get(0).equals("Tech ID")
                || !rows.get(0).get(6).equals("Prerequisite ID") || !rows.get(0).get(19).equals("Tradeoff"))
            throw new IllegalArgumentException("Unexpected technology CSV schema");
        Set<String> baseIds = new HashSet<>();
        for (int i = 1; i < bases.size(); i++) {
            String id = bases.get(i).get(0);
            if (!id.equals("BASE-" + bases.get(i).get(1) + "-" + bases.get(i).get(2)) || !baseIds.add(id))
                throw new IllegalArgumentException("Invalid/duplicate base component ID: " + id);
        }
        Map<String, Upgrade> upgrades = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            Upgrade upgrade = new Upgrade(rows.get(i));
            if (upgrades.putIfAbsent(upgrade.id, upgrade) != null)
                throw new IllegalArgumentException("Duplicate technology ID: " + upgrade.id);
        }
        for (Upgrade u : upgrades.values()) {
            String expected = u.stage == 1
                    ? "BASE-" + u.faction + "-" + u.subsystem
                    : "TECH-" + u.faction + "-" + u.subsystem + "-" + u.track + "-" + String.format(java.util.Locale.ROOT, "%02d", u.stage - 1);
            if (!expected.equals(u.prerequisite))
                throw new IllegalArgumentException("Broken parallel-track chain for " + u.id);
            if ((u.stage == 1 && !baseIds.contains(expected))
                    || (u.stage > 1 && !upgrades.containsKey(expected)))
                throw new IllegalArgumentException("Missing prerequisite for " + u.id);
        }
        return new TechnologyCatalog(upgrades, baseIds);
    }

    private static int integer(String text, String field) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid " + field + ": " + text); }
    }
    private static double positive(String text) {
        double x;
        try { x = Double.parseDouble(text); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid modifier: " + text); }
        if (!Double.isFinite(x) || x <= 0) throw new IllegalArgumentException("Unsafe modifier: " + text);
        return x;
    }

    /** RFC4180-style quoted fields; field/row size and count are bounded. */
    private static List<List<String>> readCsv(InputStream input, int width) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder field = new StringBuilder();
            List<String> row = new ArrayList<>();
            boolean quoted = false, afterQuote = false;
            int ch;
            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                if (quoted) {
                    if (c == '"') {
                        reader.mark(1); int next = reader.read();
                        if (next == '"') field.append('"');
                        else { quoted = false; afterQuote = true; if (next != -1) reader.reset(); }
                    } else field.append(c);
                } else if (c == '"') {
                    if (!afterQuote && field.length() == 0) quoted = true;
                    else throw new IllegalArgumentException("Unexpected quotation in CSV");
                } else if (c == ',' || c == '\n') {
                    row.add(field.toString()); field.setLength(0); afterQuote = false;
                    if (row.size() > width) throw new IllegalArgumentException("Too many CSV columns");
                    if (c == '\n') {
                        if (row.size() != width) throw new IllegalArgumentException("Wrong CSV width at row " + (result.size()+1));
                        result.add(row); row = new ArrayList<>();
                    }
                } else if (c == '\r') {
                    // CSV emitter uses LF. For CRLF files the next LF completes the row.
                } else {
                    if (afterQuote) throw new IllegalArgumentException("Unexpected data after CSV closing quote");
                    field.append(c);
                }
                if (field.length() > 10000 || result.size() > 20000)
                    throw new IllegalArgumentException("CSV input exceeds safe limits");
            }
            if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
            if (field.length() > 0 || !row.isEmpty()) {
                row.add(field.toString());
                if (row.size() != width) throw new IllegalArgumentException("Wrong final CSV width");
                result.add(row);
            }
        }
        return result;
    }
}
