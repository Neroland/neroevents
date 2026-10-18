package za.co.neroland.neroevents.event;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.RewardSpec.RewardLine;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.reward.ValueTable;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * The active event definitions, loaded from datapacks: one JSON per definition under
 * {@code data/<namespace>/neroevents/events/<path>.json}, id {@code <namespace>:<path>}.
 *
 * <p>Lifecycle: no per-loader reload listener. Definitions are read lazily from the running server's
 * {@link ResourceManager} and cached against the server <i>and</i> that resource-manager instance;
 * {@code /reload} replaces the instance, so {@link #refreshIfReloaded} notices with one reference
 * comparison. The engine calls it once per scheduler pass.
 *
 * <p><b>Nothing here crashes on bad content.</b> A file with a missing required field, a broken rule or
 * an unknown payload type is <b>dropped</b> with one warning naming the id and the key; an out-of-range
 * optional field is <b>reset</b> to its default with a warning. A partially decodable file is dropped —
 * half an event is worse than none. Each complaint is kept as a {@link ValidationIssue} for the commands.
 *
 * <p><b>Privacy:</b> only resource ids, keys and codec messages are logged; definitions are not player data.
 */
public final class EventDefinitions {

    private static final String DIRECTORY = "neroevents/events";
    private static final String EXTENSION = ".json";
    public static final int DEFAULT_CAP = 256;

    private static MinecraftServer loadedFor;
    private static ResourceManager loadedFrom;
    private static int generation;
    private static int cap = DEFAULT_CAP;
    private static Map<Identifier, EventDefinition> definitions = Map.of();
    private static List<ValidationIssue> issues = List.of();

    private EventDefinitions() {
    }

    // --- lifecycle ------------------------------------------------------------------------------

    public static synchronized Map<Identifier, EventDefinition> definitionsForServer(MinecraftServer server) {
        ensureLoaded(server);
        return definitions;
    }

    public static synchronized List<ValidationIssue> issuesForServer(MinecraftServer server) {
        ensureLoaded(server);
        return issues;
    }

    /** Re-reads iff the datapacks were reloaded (or this is another server). One comparison otherwise. */
    public static synchronized boolean refreshIfReloaded(MinecraftServer server) {
        if (server == loadedFor && server.getResourceManager() == loadedFrom) {
            return false;
        }
        loadFrom(server);
        return true;
    }

    public static synchronized void reload(MinecraftServer server) {
        loadFrom(server);
    }

    public static synchronized int generation() {
        return generation;
    }

    public static synchronized void forgetServer() {
        loadedFor = null;
        loadedFrom = null;
    }

    /** Sets the definition cap ({@code schedulerDefinitionsCap}); applies at the next load. */
    public static synchronized void cap(int newCap) {
        cap = Math.max(1, newCap);
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (server != loadedFor || server.getResourceManager() != loadedFrom) {
            loadFrom(server);
        }
    }

    private static void loadFrom(MinecraftServer server) {
        List<ValidationIssue> collected = new ArrayList<>();
        Map<Identifier, EventDefinition> loaded;
        try {
            Map<Identifier, EventDefinition> parsed = read(server.getResourceManager(), collected);
            loaded = validate(parsed, BuiltInRegistries.ITEM::containsKey, Services.PLATFORM::isModLoaded,
                    PayloadTypes::get, collected, cap);
        } catch (RuntimeException e) {
            NeroEventsCommon.LOGGER.warn("[NeroEvents] Event definition load failed ({}); no events are active.",
                    e.getClass().getName());
            NeroEventsTelemetry.captureHandledException(e, "engine", "definition_load");
            loaded = Map.of();
        }
        definitions = loaded;
        issues = List.copyOf(collected);
        loadedFor = server;
        loadedFrom = server.getResourceManager();
        generation++;
        NeroEventsCommon.LOGGER.info("[NeroEvents] Loaded {} event definition(s){}", definitions.size(),
                issues.isEmpty() ? "" : " with " + issues.size() + " issue(s)");
    }

    // --- accessors --------------------------------------------------------------------------------

    public static Map<Identifier, EventDefinition> definitions() {
        return definitions;
    }

    public static Optional<EventDefinition> definition(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public static List<ValidationIssue> validationIssues() {
        return issues;
    }

    // --- reading --------------------------------------------------------------------------------------

    private static Map<Identifier, EventDefinition> read(ResourceManager resources, List<ValidationIssue> out) {
        Map<Identifier, EventDefinition> parsed = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> file
                : resources.listResources(DIRECTORY, path -> path.getPath().endsWith(EXTENSION)).entrySet()) {
            Identifier id = toDefinitionId(file.getKey());
            if (id == null) {
                continue;
            }
            try (BufferedReader reader = file.getValue().openAsReader()) {
                parse(id, JsonParser.parseReader(reader), out).ifPresent(def -> parsed.put(id, def));
            } catch (Exception e) {
                drop(out, id, "file", "could not be read (" + e.getClass().getSimpleName() + ")");
            }
        }
        return parsed;
    }

    /** {@code <ns>:neroevents/events/core/x.json -> <ns>:core/x}. */
    static Identifier toDefinitionId(Identifier file) {
        String path = file.getPath();
        if (!path.startsWith(DIRECTORY + "/") || !path.endsWith(EXTENSION)) {
            return null;
        }
        return Identifier.fromNamespaceAndPath(file.getNamespace(),
                path.substring(DIRECTORY.length() + 1, path.length() - EXTENSION.length()));
    }

    // --- parsing (registry-free) ----------------------------------------------------------------------

    /**
     * Decodes one file. Required keys are checked first so the warning can name them; the codec then
     * decodes the rest, and any codec complaint drops the file.
     */
    static Optional<EventDefinition> parse(Identifier id, JsonElement json, List<ValidationIssue> out) {
        if (json == null || !json.isJsonObject()) {
            return drop(out, id, "file", "not a JSON object");
        }
        JsonObject obj = json.getAsJsonObject();
        for (String key : obj.keySet()) {
            if (!EventDefinition.KEYS.contains(key)) {
                ignore(out, id, key, "unknown key", "ignored");
            }
        }
        warnUnknown(out, id, obj, "display", EventDefinition.Display.KEYS);
        warnUnknown(out, id, obj, "conditions", EventConditions.KEYS);
        warnUnknown(out, id, obj, "bounds", EventBounds.KEYS);
        warnUnknown(out, id, obj, "rewards", RewardSpec.KEYS);

        if (!obj.has("type") || !isString(obj.get("type")) || Identifier.tryParse(obj.get("type").getAsString()) == null) {
            return drop(out, id, "type", "missing or not an identifier");
        }
        if (!obj.has("category") || !isString(obj.get("category"))
                || EventCategory.byName(obj.get("category").getAsString()).isEmpty()) {
            return drop(out, id, "category", "missing or not one of reward, hazard, combat, raid, invasion, boss, economy, war");
        }
        if (!obj.has("weight")) {
            return drop(out, id, "weight", "missing");
        }

        List<String> errors = new ArrayList<>(1);
        DataResult<EventDefinition.Raw> decoded = EventDefinition.CODEC.parse(JsonOps.INSTANCE, obj);
        decoded.error().ifPresent(err -> errors.add(err.message()));
        Optional<EventDefinition.Raw> raw = decoded.result();
        if (!errors.isEmpty() || raw.isEmpty()) {
            return drop(out, id, "codec", errors.isEmpty() ? "not a readable event definition" : errors.get(0));
        }
        return normalise(id, obj, raw.get(), out);
    }

    private static Optional<EventDefinition> normalise(Identifier id, JsonObject obj, EventDefinition.Raw raw,
            List<ValidationIssue> out) {
        EventCategory category = EventCategory.byName(raw.category()).orElseThrow();
        Severity severity = category.defaultSeverity();
        if (raw.severity().isPresent()) {
            Optional<Severity> named = Severity.byName(raw.severity().get());
            if (named.isPresent()) {
                severity = named.get();
            } else {
                ignore(out, id, "severity", "unknown severity", severity.name());
            }
        }
        EventDefinition.Display display = raw.display();
        if (display.title().isBlank()) {
            return drop(out, id, "display.title", "missing or blank");
        }
        if (display.whatToDo().isBlank()) {
            return drop(out, id, "display.what_to_do", "missing or blank");
        }
        if (display.announce().isBlank()) {
            return drop(out, id, "display.announce", "missing or blank");
        }
        int weight = raw.weight().orElse(-1);
        if (weight < 0 || weight > 1000) {
            return drop(out, id, "weight", "must be 0 to 1000");
        }
        long cooldown = raw.cooldownTicks();
        if (cooldown < 0) {
            ignore(out, id, "cooldown_ticks", "must be >= 0", Long.toString(EventDefinition.DEFAULT_COOLDOWN_TICKS));
            cooldown = EventDefinition.DEFAULT_COOLDOWN_TICKS;
        }
        int announce = raw.announceTicks();
        if (announce < 0 || announce > 72000) {
            ignore(out, id, "announce_ticks", "must be 0 to 72000", Integer.toString(EventDefinition.DEFAULT_ANNOUNCE_TICKS));
            announce = EventDefinition.DEFAULT_ANNOUNCE_TICKS;
        }
        int duration = raw.durationTicks();
        if (duration < 20 || duration > 1728000) {
            ignore(out, id, "duration_ticks", "must be 20 to 1728000", Integer.toString(EventDefinition.DEFAULT_DURATION_TICKS));
            duration = EventDefinition.DEFAULT_DURATION_TICKS;
        }
        EventDefinition.Flags flags = raw.flags();
        if (flags.destructive() && flags.consentExempt()) {
            return drop(out, id, "consent_exempt", "a destructive event can never be consent-exempt");
        }

        EventConditions conditions = raw.conditions();
        JsonObject condJson = obj.has("conditions") && obj.get("conditions").isJsonObject()
                ? obj.getAsJsonObject("conditions") : new JsonObject();
        if (!conditions.dimensions().isEmpty() && conditions.dimensionTag().isPresent()) {
            return drop(out, id, "conditions.dimensions", "dimensions and dimension_tag cannot both be set");
        }
        if (conditions.maxOnline() >= 0 && conditions.maxOnline() < conditions.minOnline()) {
            return drop(out, id, "conditions.max_online", "is below min_online");
        }
        int holders = conditions.requiredGateHolders();
        if (holders < 1) {
            ignore(out, id, "conditions.required_gate_holders", "must be >= 1", "1");
            holders = 1;
        }
        if (condJson.has("required_gate_holders") && conditions.requiresGate().isEmpty()) {
            ignore(out, id, "conditions.required_gate_holders", "has no meaning without requires_gate", "ignored");
        }
        Optional<EventConditions.TimeWindow> window = conditions.timeOfDay();
        if (window.isPresent() && !window.get().valid()) {
            ignore(out, id, "conditions.time_of_day", "from and to must be 0 to 24000", "always");
            window = Optional.empty();
        }
        conditions = new EventConditions(conditions.dimensions(), conditions.dimensionTag(), conditions.minOnline(),
                conditions.maxOnline(), window, conditions.bands(), conditions.requiresGate(), holders,
                conditions.requiresMods(), conditions.allowEmptyDimension());

        EventBounds bounds = raw.bounds();
        if (!bounds.radiusValid()) {
            ignore(out, id, "bounds.radius", "must be 8 to 512", Integer.toString(EventBounds.DEFAULT_RADIUS));
            bounds = new EventBounds(bounds.kind(), EventBounds.DEFAULT_RADIUS, bounds.fallbackToDensity());
        }

        RewardSpec rewards = RewardSpec.fromRaw(raw.rewards(), problem -> ignore(out, id, "rewards", problem, "skipped"));
        if (category == EventCategory.BOSS && rewards.lines().stream().anyMatch(l -> l.kind() == RewardLine.Kind.ITEM)
                || category == EventCategory.BOSS
                        && rewards.topBonus().stream().anyMatch(l -> l.kind() == RewardLine.Kind.ITEM)) {
            // A boss's item drops come from the boss mod; NeroEvents never pays items on top of them.
            ignore(out, id, "rewards.lines", "a boss event cannot have item lines", "item lines removed");
            rewards = rewards.withoutItems();
        }
        for (String warning : rewards.valueWarnings(ValueTable.DEFAULT)) {
            ignore(out, id, "rewards", warning, "kept");
        }
        if (announce == 0 && !flags.consentExempt()) {
            ignore(out, id, "announce_ticks", "0 leaves no time to vote; under consentMode=vote this event is "
                    + "always skipped (vote_failed)", "0");
        }

        return Optional.of(new EventDefinition(id, raw.type(), category, severity, display, weight, cooldown, announce,
                duration, flags.enabled(), flags.destructive(), flags.consentExempt(), flags.calmExempt(),
                flags.locationPublic(), conditions, bounds, rewards, raw.payload(), null, null, List.of()));
    }

    // --- validation (registry-free) -------------------------------------------------------------------

    static Map<Identifier, EventDefinition> validate(Map<Identifier, EventDefinition> parsed,
            Predicate<Identifier> itemRegistered, Predicate<String> modLoaded,
            Function<Identifier, Optional<PayloadType<?, ?>>> payloadTypes, List<ValidationIssue> out) {
        return validate(parsed, itemRegistered, modLoaded, payloadTypes, out, DEFAULT_CAP);
    }

    /**
     * Resolves payload types, decodes payload configs, enforces {@code requires_mods} and prunes reward
     * lines naming unregistered items; keeps at most {@code cap} definitions (by id).
     */
    static Map<Identifier, EventDefinition> validate(Map<Identifier, EventDefinition> parsed,
            Predicate<Identifier> itemRegistered, Predicate<String> modLoaded,
            Function<Identifier, Optional<PayloadType<?, ?>>> payloadTypes, List<ValidationIssue> out, int cap) {
        TreeMap<Identifier, EventDefinition> sorted = new TreeMap<>(parsed);
        Map<Identifier, EventDefinition> result = new LinkedHashMap<>();
        for (EventDefinition def : sorted.values()) {
            // The cap is checked before any payload config is decoded, so an oversized pack costs nothing.
            if (result.size() >= cap) {
                drop(out, def.id(), "id", "definition cap (" + cap + ") reached");
                continue;
            }
            Optional<PayloadType<?, ?>> type = payloadTypes.apply(def.type());
            if (type.isEmpty()) {
                drop(out, def.id(), "type", "unknown payload type " + def.type());
                continue;
            }
            String missingMod = null;
            for (String mod : def.conditions().requiresMods()) {
                if (!modLoaded.test(mod)) {
                    missingMod = mod;
                    break;
                }
            }
            if (missingMod != null) {
                drop(out, def.id(), "conditions.requires_mods", "requires mod " + missingMod);
                continue;
            }
            Optional<Decoded> decoded = decode(type.get(), def.payloadJson(), def.id(), out);
            if (decoded.isEmpty()) {
                continue;
            }
            for (String warning : decoded.get().warnings()) {
                ignore(out, def.id(), "payload", warning, "kept");
            }
            EventDefinition resolved = def.withPayload(type.get(), decoded.get().config(), decoded.get().warnings());
            resolved = pruneItems(resolved, itemRegistered, out);
            result.put(def.id(), resolved);
        }
        return Collections.unmodifiableMap(result);
    }

    private record Decoded(PayloadConfig config, List<String> warnings) {
    }

    private static <C extends PayloadConfig> Optional<Decoded> decode(PayloadType<C, ?> type, JsonElement json,
            Identifier id, List<ValidationIssue> out) {
        DataResult<C> result = type.configCodec().codec().parse(JsonOps.INSTANCE, json);
        Optional<String> error = result.error().map(e -> e.message());
        if (error.isPresent() || result.result().isEmpty()) {
            drop(out, id, "payload", error.orElse("not a readable payload"));
            return Optional.empty();
        }
        C config = result.result().get();
        List<String> warnings;
        try {
            warnings = List.copyOf(type.validate(config));
        } catch (RuntimeException e) {
            drop(out, id, "payload", "validation failed (" + e.getClass().getSimpleName() + ")");
            return Optional.empty();
        }
        return Optional.of(new Decoded(config, warnings));
    }

    private static EventDefinition pruneItems(EventDefinition def, Predicate<Identifier> itemRegistered,
            List<ValidationIssue> out) {
        List<RewardLine> lines = keepRegistered(def, def.rewards().lines(), itemRegistered, out, "rewards.lines");
        List<RewardLine> bonus = keepRegistered(def, def.rewards().topBonus(), itemRegistered, out, "rewards.top_bonus");
        if (lines.size() == def.rewards().lines().size() && bonus.size() == def.rewards().topBonus().size()) {
            return def;
        }
        return def.withRewards(def.rewards().withLines(lines, bonus));
    }

    private static List<RewardLine> keepRegistered(EventDefinition def, List<RewardLine> lines,
            Predicate<Identifier> itemRegistered, List<ValidationIssue> out, String key) {
        List<RewardLine> kept = new ArrayList<>(lines.size());
        for (RewardLine line : lines) {
            if (line.kind() == RewardLine.Kind.ITEM && line.item().isPresent() && !itemRegistered.test(line.item().get())) {
                ignore(out, def.id(), key, "item " + line.item().get() + " is not registered", "line removed");
            } else {
                kept.add(line);
            }
        }
        return kept;
    }

    // --- issue plumbing -------------------------------------------------------------------------------

    private static void warnUnknown(List<ValidationIssue> out, Identifier id, JsonObject obj, String section,
            Set<String> known) {
        if (obj.has(section) && obj.get(section).isJsonObject()) {
            for (String key : obj.getAsJsonObject(section).keySet()) {
                if (!known.contains(key)) {
                    ignore(out, id, section + "." + key, "unknown key", "ignored");
                }
            }
        }
    }

    private static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static <T> Optional<T> drop(List<ValidationIssue> out, Identifier id, String key, String reason) {
        NeroEventsCommon.LOGGER.warn("[NeroEvents] Skipping event definition {}: {} — {}", id, key, reason);
        out.add(ValidationIssue.dropped(id, key, reason));
        return Optional.empty();
    }

    private static void ignore(List<ValidationIssue> out, Identifier id, String key, String reason, String using) {
        NeroEventsCommon.LOGGER.warn("[NeroEvents] Event definition {}: {} — {}; using {}", id, key, reason, using);
        out.add(ValidationIssue.ignored(id, key, reason));
    }
}
