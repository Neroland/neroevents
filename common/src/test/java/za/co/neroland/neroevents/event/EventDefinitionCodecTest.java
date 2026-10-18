package za.co.neroland.neroevents.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.RewardSpec.RewardLine;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/** Definition decoding and validation: what is dropped, what is reset, and what is kept. */
class EventDefinitionCodecTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroevents_test", "codec_case");

    private final List<ValidationIssue> issues = new ArrayList<>();

    @BeforeEach
    void setUp() {
        PayloadTypes.resetForTests();
        PayloadTypes.register(new RecordingPayloadType("codec"));
        PayloadTypes.register(new DebugMarkerPayload());
    }

    @AfterEach
    void tearDown() {
        PayloadTypes.resetForTests();
    }

    private static JsonElement resource(String path) throws Exception {
        try (InputStream in = EventDefinitionCodecTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static JsonObject base() {
        return JsonParser.parseString("""
                {"type": "neroevents_test:codec", "category": "hazard", "weight": 5,
                 "display": {"title": "a.title", "what_to_do": "a.what", "announce": "a.announce"}}""")
                .getAsJsonObject();
    }

    private static JsonObject with(String overrides) {
        JsonObject json = base();
        JsonObject extra = JsonParser.parseString(overrides).getAsJsonObject();
        extra.entrySet().forEach(e -> json.add(e.getKey(), e.getValue()));
        return json;
    }

    private Optional<EventDefinition> load(JsonElement json) {
        return load(json, i -> true, m -> true);
    }

    private Optional<EventDefinition> load(JsonElement json, java.util.function.Predicate<Identifier> items,
            java.util.function.Predicate<String> mods) {
        Optional<EventDefinition> parsed = EventDefinitions.parse(ID, json, issues);
        if (parsed.isEmpty()) {
            return parsed;
        }
        return Optional.ofNullable(EventDefinitions.validate(Map.of(ID, parsed.get()), items, mods, PayloadTypes::get,
                issues).get(ID));
    }

    private boolean droppedAt(String key) {
        return issues.stream().anyMatch(i -> i.dropped() && i.key().equals(key));
    }

    private boolean ignoredAt(String key) {
        return issues.stream().anyMatch(i -> !i.dropped() && i.key().equals(key));
    }

    @Test
    void shippedDebugMarkerDecodes() throws Exception {
        EventDefinition def = load(resource("data/neroevents/neroevents/events/debug_marker.json")).orElseThrow();
        assertEquals(0, def.weight());
        assertFalse(def.enabled());
        assertEquals(EventCategory.HAZARD, def.category());
        assertEquals(DebugMarkerPayload.ID, def.type());
        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void fullExampleDecodesEveryField() throws Exception {
        EventDefinition def = load(resource("events/full_example.json")).orElseThrow();
        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(Severity.REWARD, def.severity());
        assertEquals(72000L, def.cooldownTicks());
        assertEquals(6000, def.announceTicks());
        assertEquals(12000, def.durationTicks());
        assertTrue(def.calmExempt());
        assertTrue(def.locationPublic());
        assertEquals(2, def.conditions().dimensions().size());
        assertEquals(Optional.of(List.of("0-1", "2-4", "5-9", "10+")), def.conditions().bands());
        assertTrue(def.conditions().requiresGate().isPresent());
        assertEquals(EventBounds.Kind.RADIUS, def.bounds().kind());
        assertEquals(96, def.bounds().radius());
        assertEquals(500L, def.rewards().budget());
        assertEquals(4, def.rewards().lines().size());
        assertEquals(RewardLine.Kind.REPUTATION, def.rewards().lines().get(3).kind());
        assertEquals(1, def.rewards().topBonus().get(0).rank());
        assertNotNull(def.payloadType());
    }

    @Test
    void missingTypeIsDroppedNamingType() {
        JsonObject json = base();
        json.remove("type");
        assertTrue(load(json).isEmpty());
        assertTrue(droppedAt("type"));
    }

    @Test
    void unknownPayloadTypeIsDropped() {
        assertTrue(load(with("{\"type\": \"neroevents_test:nope\"}")).isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.dropped() && i.message().startsWith("unknown payload type")));
    }

    @Test
    void weightOutOfRangeIsDropped() {
        assertTrue(load(with("{\"weight\": 1001}")).isEmpty());
        assertTrue(droppedAt("weight"));
    }

    @Test
    void negativeCooldownIsResetToTheDefault() {
        EventDefinition def = load(with("{\"cooldown_ticks\": -5}")).orElseThrow();
        assertEquals(EventDefinition.DEFAULT_COOLDOWN_TICKS, def.cooldownTicks());
        assertTrue(ignoredAt("cooldown_ticks"));
    }

    @Test
    void dimensionsAndTagTogetherAreDropped() {
        assertTrue(load(with("{\"conditions\": {\"dimensions\": [\"minecraft:overworld\"], "
                + "\"dimension_tag\": \"neroland:space/dimensions\"}}")).isEmpty());
        assertTrue(droppedAt("conditions.dimensions"));
    }

    @Test
    void destructiveConsentExemptIsDropped() {
        assertTrue(load(with("{\"destructive\": true, \"consent_exempt\": true}")).isEmpty());
        assertTrue(droppedAt("consent_exempt"));
    }

    @Test
    void maxBelowMinOnlineIsDropped() {
        assertTrue(load(with("{\"conditions\": {\"min_online\": 5, \"max_online\": 2}}")).isEmpty());
        assertTrue(droppedAt("conditions.max_online"));
    }

    @Test
    void unknownKeyIsIgnoredAndNamed() {
        assertTrue(load(with("{\"colour\": \"red\"}")).isPresent());
        assertTrue(ignoredAt("colour"));
    }

    @Test
    void partialDecodeIsDropped() {
        assertTrue(load(with("{\"duration_ticks\": \"long\"}")).isEmpty());
        assertTrue(droppedAt("codec"));
        issues.clear();
        assertTrue(load(with("{\"bounds\": {\"kind\": \"sphere\"}}")).isEmpty());
        assertTrue(droppedAt("codec"));
    }

    @Test
    void unregisteredItemLineIsRemovedAndTheDefinitionKept() {
        EventDefinition def = load(with("{\"rewards\": {\"lines\": [{\"kind\": \"item\", \"item\": \"minecraft:missing\", "
                + "\"count\": 1}, {\"kind\": \"xp\", \"amount\": 5}]}}"),
                id -> !id.getPath().equals("missing"), m -> true).orElseThrow();
        assertEquals(1, def.rewards().lines().size());
        assertEquals(RewardLine.Kind.XP, def.rewards().lines().get(0).kind());
        assertTrue(ignoredAt("rewards.lines"));
    }

    @Test
    void unmetRequiredModIsDroppedAtLoad() {
        assertTrue(load(with("{\"conditions\": {\"requires_mods\": [\"nerocreatures\"]}}"), i -> true,
                m -> !m.equals("nerocreatures")).isEmpty());
        assertTrue(droppedAt("conditions.requires_mods"));
    }

    @Test
    void severityDefaultsFromCategoryAndUnknownSeverityIsIgnored() {
        assertEquals(Severity.HAZARD, load(base()).orElseThrow().severity());
        EventDefinition def = load(with("{\"category\": \"boss\", \"severity\": \"LOUD\"}")).orElseThrow();
        assertEquals(Severity.COMBAT, def.severity());
        assertTrue(ignoredAt("severity"));
    }

    @Test
    void definitionIdsComeFromThePath() {
        assertEquals(Identifier.fromNamespaceAndPath("neroevents", "core/supply_drop"), EventDefinitions.toDefinitionId(
                Identifier.fromNamespaceAndPath("neroevents", "neroevents/events/core/supply_drop.json")));
    }
}
