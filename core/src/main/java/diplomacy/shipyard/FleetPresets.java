package diplomacy.shipyard;

import diplomacy.tech.TechnologyCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Platform-neutral fleet persistence; the desktop and Android frontends only supply file I/O. */
public final class FleetPresets {
    private FleetPresets() { }
    public record Fleets(Shipyard.Design left, Shipyard.Design right) { }
    private static final String MAGIC = "CD-FLEETS-1";

    public static String encode(Shipyard.Design left, Shipyard.Design right, TechnologyCatalog catalog) {
        if (left == null || right == null || catalog == null
                || !Shipyard.inspect(left, catalog).valid() || !Shipyard.inspect(right, catalog).valid())
            throw new IllegalArgumentException("Cannot save an invalid fleet fitting");
        String body = MAGIC + "\n" + packed(Shipyard.encode(left)) + "\n"
                + packed(Shipyard.encode(right)) + "\n";
        return body + sha256(body) + "\n";
    }
    public static Fleets decode(String data, TechnologyCatalog catalog) {
        if (data == null || data.length() > 65000 || catalog == null)
            throw new IllegalArgumentException("Invalid fleet file size or technology catalog");
        String[] lines = data.split("\n", -1);
        if (lines.length != 5 || !MAGIC.equals(lines[0]) || !lines[4].isEmpty())
            throw new IllegalArgumentException("Unknown fleet save format");
        String body = lines[0] + "\n" + lines[1] + "\n" + lines[2] + "\n";
        if (!MessageDigest.isEqual(sha256(body).getBytes(StandardCharsets.US_ASCII),
                                   lines[3].getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Fleet save checksum mismatch");
        try {
            return new Fleets(Shipyard.decode(unpacked(lines[1]), catalog),
                    Shipyard.decode(unpacked(lines[2]), catalog));
        } catch (RuntimeException bad) {
            throw new IllegalArgumentException("Fleet file contains invalid ship equipment", bad);
        }
    }
    private static String packed(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String unpacked(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : hash) result.append(Character.forDigit((b >>> 4) & 15, 16))
                    .append(Character.forDigit(b & 15, 16));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java SHA-256 provider unavailable", impossible);
        }
    }
}
