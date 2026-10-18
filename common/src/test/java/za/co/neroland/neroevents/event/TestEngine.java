package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.persist.EventEngineState;
import za.co.neroland.neroevents.event.reward.RewardResolver;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.reward.RewardOutcome;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.RecordingAnnouncer;
import za.co.neroland.neroevents.testing.RecordingPayloadType;

/**
 * Builds an engine over fakes: a fake world (one overworld player, every chunk loaded), a settable
 * clock starting at 1, a seeded random, a recording announcer, and definitions parsed from JSON through
 * the real parse + validate path. The scheduler is off and the calm window is 0 unless a test changes it.
 */
final class TestEngine implements AutoCloseable {

    final FakeWorldView world = new FakeWorldView();
    final FakeClock clock = new FakeClock(1L);
    final RecordingAnnouncer announcer = new RecordingAnnouncer();
    final EngineSettings settings = EngineSettings.defaults();
    final Map<Identifier, EventDefinition> defs = new LinkedHashMap<>();
    final List<EventSignal> signals = new ArrayList<>();
    final List<EventView> history = new ArrayList<>();
    final List<String> rewarded = new ArrayList<>();
    EventEngineState state = new EventEngineState();
    final List<ProtectionSource> sources;
    EventEngine engine;
    private final Consumer<EventSignal> listener = signals::add;

    TestEngine() {
        this(List.of());
    }

    TestEngine(List<ProtectionSource> sources) {
        this.sources = sources;
        PayloadTypes.resetForTests();
        settings.schedulerEnabled = false;
        settings.calmWindowHours = 0;
        settings.protectSpawnRadiusBlocks = 0;
        world.online(FakeWorldView.OVERWORLD, 1).allChunksLoaded(FakeWorldView.OVERWORLD)
                .denseCell(FakeWorldView.OVERWORLD, 0, 0, 1);
        EventSignals.subscribe(listener);
        build();
    }

    /** (Re)builds the engine over the current state (a restart keeps the state object). */
    EventEngine build() {
        engine = new EventEngine(world, clock, new Random(7L), state, announcer,
                (RewardResolver) instance -> {
                    rewarded.add(instance.shortId());
                    return RewardOutcome.NONE;
                }, history::add,
                new ProtectionQuery(sources), null);
        engine.settings(settings);
        engine.definitions(() -> defs);
        return engine;
    }

    /** Registers the type (if needed) and adds a definition: base JSON overlaid with {@code overrides}. */
    EventDefinition define(String path, RecordingPayloadType type, String overrides) {
        if (PayloadTypes.get(type.id()).isEmpty()) {
            PayloadTypes.register(type);
        }
        JsonObject json = JsonParser.parseString("""
                {
                  "type": "%s",
                  "category": "reward",
                  "display": {"title": "t.title", "what_to_do": "t.what", "announce": "t.announce"},
                  "weight": 10,
                  "cooldown_ticks": 1000,
                  "announce_ticks": 100,
                  "duration_ticks": 200,
                  "conditions": {"allow_empty_dimension": true},
                  "bounds": {"kind": "radius", "radius": 16}
                }""".formatted(type.id())).getAsJsonObject();
        if (overrides != null && !overrides.isBlank()) {
            JsonObject extra = JsonParser.parseString(overrides).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : extra.entrySet()) {
                json.add(entry.getKey(), entry.getValue());
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("neroevents_test", path);
        List<ValidationIssue> issues = new ArrayList<>();
        EventDefinition parsed = EventDefinitions.parse(id, json, issues).orElseThrow(
                () -> new AssertionError("definition did not parse: " + issues));
        Map<Identifier, EventDefinition> valid = EventDefinitions.validate(Map.of(id, parsed), item -> true,
                mod -> true, PayloadTypes::get, issues);
        EventDefinition def = valid.get(id);
        if (def == null) {
            throw new AssertionError("definition did not validate: " + issues);
        }
        defs.put(id, def);
        return def;
    }

    /** Ticks the engine {@code n} times, advancing the clock by one after each. */
    void tick(int n) {
        for (int i = 0; i < n; i++) {
            engine.tickOnce();
            clock.advance(1);
        }
    }

    /** Starts a definition as an operator would; fails the test on refusal. */
    EventInstance start(EventDefinition def) {
        RequestResult result = engine.request(new EventRequest(def.id(), FakeWorldView.OVERWORLD, EventOrigin.ADMIN, null));
        if (result instanceof RequestResult.Queued queued) {
            return queued.instance();
        }
        throw new AssertionError("refused: " + result);
    }

    List<EventSignal.Kind> kinds() {
        return signals.stream().map(EventSignal::kind).toList();
    }

    @Override
    public void close() {
        EventSignals.unsubscribe(listener);
        PayloadTypes.resetForTests();
    }
}
