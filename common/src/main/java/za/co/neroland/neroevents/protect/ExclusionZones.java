package za.co.neroland.neroevents.protect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventEngine;

/**
 * Exclusion zones: XZ boxes in a dimension that events never touch. Two sources feed them — the
 * {@code eventExclusionZones} config key ({@code name@dim:x1,z1,x2,z2;...}, parsed here) and zones added
 * with {@code /neroevents zones add}, which live in the engine's saved state. Zones are places, never
 * people.
 */
public final class ExclusionZones {

    /** Zone names: lower-case letters, digits, {@code _} and {@code -}, 1 to 32 characters. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private static String parsedSpec;
    private static List<ExclusionZone> parsedZones = List.of();

    private ExclusionZones() {
    }

    /** One zone; corners are normalised so min is never above max. */
    public record ExclusionZone(String name, Identifier dimension, int minX, int minZ, int maxX, int maxZ) {

        public static final Codec<ExclusionZone> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("name").forGetter(ExclusionZone::name),
                Identifier.CODEC.fieldOf("dimension").forGetter(ExclusionZone::dimension),
                Codec.INT.fieldOf("min_x").forGetter(ExclusionZone::minX),
                Codec.INT.fieldOf("min_z").forGetter(ExclusionZone::minZ),
                Codec.INT.fieldOf("max_x").forGetter(ExclusionZone::maxX),
                Codec.INT.fieldOf("max_z").forGetter(ExclusionZone::maxZ)
        ).apply(inst, ExclusionZone::of));

        public static ExclusionZone of(String name, Identifier dimension, int x1, int z1, int x2, int z2) {
            return new ExclusionZone(name, dimension, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2),
                    Math.max(z1, z2));
        }

        public boolean intersects(Footprint footprint) {
            return dimension.equals(footprint.dimension())
                    && footprint.intersectsXZ(minX, minZ, maxX, maxZ);
        }
    }

    /**
     * Parses {@code name@dimension:x1,z1,x2,z2} entries separated by {@code ;}. A bad entry is skipped
     * with a warning line added to {@code issues}.
     */
    public static List<ExclusionZone> parseConfig(String spec, List<String> issues) {
        List<ExclusionZone> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return out;
        }
        for (String raw : spec.split(";")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int at = entry.indexOf('@');
            int colon = entry.lastIndexOf(':');
            if (at <= 0 || colon <= at + 1) {
                issues.add("exclusion zone '" + entry + "' is not name@dimension:x1,z1,x2,z2");
                continue;
            }
            String name = entry.substring(0, at).trim();
            Identifier dimension = Identifier.tryParse(entry.substring(at + 1, colon).trim());
            String[] coords = entry.substring(colon + 1).split(",");
            if (!NAME.matcher(name).matches() || dimension == null || coords.length != 4) {
                issues.add("exclusion zone '" + entry + "' is not name@dimension:x1,z1,x2,z2");
                continue;
            }
            try {
                out.add(ExclusionZone.of(name, dimension, Integer.parseInt(coords[0].trim()),
                        Integer.parseInt(coords[1].trim()), Integer.parseInt(coords[2].trim()),
                        Integer.parseInt(coords[3].trim())));
            } catch (NumberFormatException e) {
                issues.add("exclusion zone '" + entry + "' has a bad coordinate");
            }
        }
        return out;
    }

    /** The config zones, re-parsed only when the config string changes (bad entries warned once). */
    public static synchronized List<ExclusionZone> configZones(String spec) {
        String key = spec == null ? "" : spec;
        if (!key.equals(parsedSpec)) {
            List<String> issues = new ArrayList<>();
            parsedZones = List.copyOf(parseConfig(key, issues));
            parsedSpec = key;
            for (String issue : issues) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] eventExclusionZones: {}; skipped.", issue);
            }
        }
        return parsedZones;
    }

    /** Config zones plus the running engine's command zones. */
    public static List<ExclusionZone> active() {
        EventEngine engine = EventEngine.current();
        if (engine == null) {
            return List.of();
        }
        List<ExclusionZone> out = new ArrayList<>(configZones(engine.settings().eventExclusionZones));
        out.addAll(engine.state().zones());
        return out;
    }
}
