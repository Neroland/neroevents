package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/**
 * Builds definitions and instances for tests outside the {@code event} package (the reward, history and
 * privacy tests), through the real parse + validate path, and reaches the package-private setters.
 */
public final class TestInstances {

    private static final RecordingPayloadType TYPE = new RecordingPayloadType("reward_test");

    private TestInstances() {
    }

    /** A definition of {@code category} with the given {@code rewards} JSON (and optional extra keys). */
    public static EventDefinition definition(String path, String category, String rewardsJson) {
        if (PayloadTypes.get(TYPE.id()).isEmpty()) {
            PayloadTypes.register(TYPE);
        }
        JsonObject json = JsonParser.parseString("""
                {
                  "type": "%s",
                  "category": "%s",
                  "display": {"title": "t.title", "what_to_do": "t.what", "announce": "t.announce"},
                  "weight": 10,
                  "announce_ticks": 100,
                  "duration_ticks": 200,
                  "bounds": {"kind": "radius", "radius": 16}
                }""".formatted(TYPE.id(), category)).getAsJsonObject();
        if (rewardsJson != null) {
            JsonElement rewards = JsonParser.parseString(rewardsJson);
            json.add("rewards", rewards);
        }
        Identifier id = Identifier.fromNamespaceAndPath("neroevents_test", path);
        List<ValidationIssue> issues = new ArrayList<>();
        EventDefinition parsed = EventDefinitions.parse(id, json, issues)
                .orElseThrow(() -> new AssertionError("definition did not parse: " + issues));
        EventDefinition def = EventDefinitions.validate(Map.of(id, parsed), item -> true, mod -> true,
                PayloadTypes::get, issues).get(id);
        if (def == null) {
            throw new AssertionError("definition did not validate: " + issues);
        }
        return def;
    }

    /** An ACTIVE-then-resolving instance of {@code def} with {@code outcome} set. */
    public static EventInstance instance(EventDefinition def, EventOutcome outcome) {
        EventInstance instance = new EventInstance(UUID.randomUUID(), def, FakeWorldView.OVERWORLD, BlockPos.ZERO,
                Footprint.dimension(FakeWorldView.OVERWORLD), EventOrigin.ADMIN, 1L, 0L, EventPhase.SCHEDULED);
        instance.force(EventPhase.RESOLVING, 10L);
        instance.outcome(outcome);
        return instance;
    }

    /** Forces a phase (for contribution helpers that only work while ACTIVE). */
    public static void phase(EventInstance instance, EventPhase phase) {
        instance.force(phase, 0L);
    }

    public static void outcome(EventInstance instance, EventOutcome outcome) {
        instance.outcome(outcome);
    }
}
