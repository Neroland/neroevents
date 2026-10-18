package za.co.neroland.neroevents.protect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventEngine;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The ordered registry of {@link ProtectionSource}s. Order (lowest first): config and command exclusion
 * zones (100), spawn protection (200), claim mods (300, reserved), NeroColonies claims (400, reserved),
 * the Nerospace station dimension (500), the protected-namespace pre-scan (600), the NeroSecurity seam
 * (700). Deny wins; there is no allow.
 *
 * <p>Registration is init-time only (common init slot 3 for the built-ins, slot 5 for compat sources).
 */
public final class ProtectionSources {

    public static final int ORDER_EXCLUSION = 100;
    public static final int ORDER_SPAWN = 200;
    public static final int ORDER_OPAC = 300;
    public static final int ORDER_COLONY = 400;
    public static final int ORDER_STATION = 500;
    public static final int ORDER_NAMESPACE = 600;
    public static final int ORDER_SECURITY = 700;

    private static final TreeMap<Integer, List<ProtectionSource>> BY_ORDER = new TreeMap<>();
    private static volatile List<ProtectionSource> ordered = List.of();
    private static final Map<String, String> HEALTH = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static boolean builtIn;

    private ProtectionSources() {
    }

    /** Adds a source at {@code order}; a duplicate id throws. Init-time only. */
    public static synchronized void register(int order, ProtectionSource source) {
        for (ProtectionSource existing : ordered) {
            if (existing.id().equals(source.id())) {
                throw new IllegalStateException("Duplicate protection source id " + source.id());
            }
        }
        BY_ORDER.computeIfAbsent(order, o -> new ArrayList<>()).add(source);
        List<ProtectionSource> flat = new ArrayList<>();
        BY_ORDER.values().forEach(flat::addAll);
        ordered = Collections.unmodifiableList(flat);
        HEALTH.putIfAbsent(source.id(), "ok");
    }

    /** Every source, lowest order first. */
    public static List<ProtectionSource> ordered() {
        return ordered;
    }

    /**
     * Registers the five built-in sources. Their settings are read from the running engine's config
     * snapshot (defaults before a server exists). Idempotent.
     */
    public static synchronized void initBuiltIn() {
        if (builtIn) {
            return;
        }
        builtIn = true;
        register(ORDER_EXCLUSION, new ExclusionZoneSource(ExclusionZones::active));
        register(ORDER_SPAWN, new SpawnProtectionSource(() -> settings().protectSpawnRadiusBlocks));
        register(ORDER_STATION, new StationProtectionSource());
        register(ORDER_NAMESPACE, new NamespaceProtectionSource(() -> settings().protectedBlockNamespaces,
                () -> settings().protectedNamespaceMarginBlocks));
        register(ORDER_SECURITY, new SecurityProtectionSource());
    }

    private static EngineSettings settings() {
        EventEngine engine = EventEngine.current();
        return engine != null ? engine.settings() : EngineSettings.defaults();
    }

    /** {@code id -> "ok" | "failing: ExceptionClass"} for {@code /neroevents status}. */
    public static Map<String, String> health() {
        Map<String, String> out = new LinkedHashMap<>();
        for (ProtectionSource source : ordered) {
            out.put(source.id(), HEALTH.getOrDefault(source.id(), "ok"));
        }
        for (Map.Entry<String, String> entry : HEALTH.entrySet()) {
            out.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return out;
    }

    /** Whether any source is currently failing. */
    public static boolean anyFailing() {
        return HEALTH.values().stream().anyMatch(v -> !"ok".equals(v));
    }

    /** A source threw: it now denies, shows as failing, and is warned about once per exception class. */
    static void markFailing(String id, Throwable failure) {
        HEALTH.put(id, "failing: " + failure.getClass().getSimpleName());
        if (WARNED.add(id + "|" + failure.getClass().getName())) {
            NeroEventsCommon.LOGGER.warn(
                    "[NeroEvents] Protection source '{}' failed ({}); destructive effects it covers are denied.",
                    id, failure.getClass().getName());
            NeroEventsTelemetry.captureHandledException(failure, "engine", "protection_source:" + id);
        }
    }

    /** Clears failure state (a new server starts with every source healthy). */
    public static void resetHealth() {
        HEALTH.replaceAll((k, v) -> "ok");
        WARNED.clear();
    }

    /**
     * Forgets every registered source, the built-in flag and all failure state, so a test can register its
     * own sources from a clean registry. Tests only; never called in game.
     */
    public static synchronized void resetForTests() {
        BY_ORDER.clear();
        ordered = List.of();
        HEALTH.clear();
        WARNED.clear();
        builtIn = false;
    }

    /** How many warnings have been logged (tests). */
    static int warnedCount() {
        return WARNED.size();
    }
}
