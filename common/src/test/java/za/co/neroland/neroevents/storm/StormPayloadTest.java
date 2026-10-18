package za.co.neroland.neroevents.storm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakePayloadContext;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.storm.FakeStormOps;

class StormPayloadTest {

    private static final Identifier CINDARA = Identifier.fromNamespaceAndPath("nerospace", "cindara");
    private static final Identifier GREENXERTZ = Identifier.fromNamespaceAndPath("nerospace", "greenxertz");
    private static final Identifier STATION = Identifier.fromNamespaceAndPath("nerospace", "station");

    private final List<ThresholdEvents.ThresholdCrossing> crossings = new ArrayList<>();
    private FakeStormOps ops;
    private FakeWorldView world;
    private FakeClock clock;
    private EventDefinition def;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ActiveStorms.resetForTests();
        StormPayloadBase.resetForTests();
        StormChannel.sinkForTests(crossings::add);
        ops = new FakeStormOps();
        ops.planets.put(CINDARA, CINDARA);
        ops.planets.put(GREENXERTZ, GREENXERTZ);
        ops.planets.put(STATION, STATION);
        world = new FakeWorldView().dimension(CINDARA).dimension(GREENXERTZ).dimension(STATION)
                .dimension(FakeWorldView.OVERWORLD)
                .tag(CINDARA, StormPayloadBase.SPACE_DIMENSIONS).tag(GREENXERTZ, StormPayloadBase.SPACE_DIMENSIONS)
                .tag(STATION, StormPayloadBase.SPACE_DIMENSIONS);
        clock = new FakeClock(4000L);
        def = TestInstances.definition("storm_test", "hazard", null);
    }

    @AfterEach
    void tearDown() {
        StormChannel.sinkForTests(null);
        ActiveStorms.resetForTests();
        StormPayloadBase.resetForTests();
    }

    private FakePayloadContext ctx(Identifier dimension, long seed) {
        EventInstance instance = new EventInstance(UUID.randomUUID(), def, dimension, null,
                Footprint.dimension(dimension), EventOrigin.ADMIN, seed, 0L, EventPhase.SCHEDULED);
        return new FakePayloadContext(instance, world, clock);
    }

    private static StormPayloadBase.Config config(String variant, int intensity, boolean agriculture) {
        return new StormPayloadBase.Config(intensity, 600, variant, true, true, agriculture, Optional.empty());
    }

    /** Runs {@code ticks} engine ticks. */
    private void run(StormPayloadBase type, FakePayloadContext ctx, StormPayloadBase.Config config,
            StormPayloadBase.State state, int ticks) {
        for (int i = 0; i < ticks; i++) {
            type.tick(ctx, config, state);
            clock.advance(1);
        }
    }

    @Test
    void stormActiveSkipsASecondStormInTheSameDimension() {
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = config("auto", 2, true);
        FakePayloadContext first = ctx(CINDARA, 0L);
        assertTrue(type.onStart(first, cfg, type.createState(first.instance(), cfg)).started());
        FakePayloadContext second = ctx(CINDARA, 1L);
        StartResult result = type.onStart(second, cfg, type.createState(second.instance(), cfg));
        assertFalse(result.started());
        assertEquals("storm_active", result.skipReason());
        assertEquals(first.instance().id(), ActiveStorms.active(CINDARA).orElseThrow().instanceId());
        SolarStormPayload solar = new SolarStormPayload(ops);
        FakePayloadContext third = ctx(CINDARA, 2L);
        assertEquals("storm_active", solar.onStart(third, cfg, solar.createState(third.instance(), cfg)).skipReason(),
                "one storm per dimension across both types");
    }

    @Test
    void solarOutsideSpaceIsNotSpace() {
        SolarStormPayload type = new SolarStormPayload(ops);
        StormPayloadBase.Config cfg = config("auto", 2, true);
        FakePayloadContext ctx = ctx(FakeWorldView.OVERWORLD, 0L);
        StartResult result = type.onStart(ctx, cfg, type.createState(ctx.instance(), cfg));
        assertEquals("not_space", result.skipReason());
        assertTrue(ActiveStorms.all().isEmpty());
        assertTrue(crossings.isEmpty());
    }

    @Test
    void planetaryVariantByPlanetAndNoneOnTheStation() {
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config auto = config("auto", 2, true);
        FakePayloadContext green = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State state = type.createState(green.instance(), auto);
        assertTrue(type.onStart(green, auto, state).started());
        assertEquals(StormVariant.DUST, state.variant());
        FakePayloadContext station = ctx(STATION, 0L);
        assertEquals("no_storm_variant",
                type.onStart(station, auto, type.createState(station.instance(), auto)).skipReason());
        assertTrue(ActiveStorms.active(STATION).isEmpty());
    }

    @Test
    void lifecycleSetsAndClearsActiveStormsAndFiresTheChannel() {
        ops.players.add(alice);
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = config("ion", 3, true);
        FakePayloadContext ctx = ctx(CINDARA, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), cfg);
        assertTrue(type.onStart(ctx, cfg, state).started());

        ActiveStorms.StormStatus status = ActiveStorms.active(CINDARA).orElseThrow();
        assertEquals(StormVariant.ION, status.variant());
        assertEquals(3, status.intensity());
        assertEquals(ctx.instance().id(), status.instanceId());
        assertEquals(1, crossings.size());
        ThresholdEvents.ThresholdCrossing start = crossings.get(0);
        assertEquals(StormChannel.CHANNEL, start.channel());
        assertEquals(CINDARA.toString(), start.scope());
        assertEquals(3L, start.value());
        assertTrue(start.rising());
        List<StormSyncPayload> syncs = ops.sentTo(alice, StormSyncPayload.class);
        assertEquals(1, syncs.size());
        assertEquals(StormVariant.ION.wireId(), syncs.get(0).variant());
        assertEquals(3, syncs.get(0).intensity());
        assertEquals(600, syncs.get(0).rampTicks());

        assertEquals(EventOutcome.ENDED, type.onTimeout(ctx, cfg, state));
        ops.clearRecords();
        type.onCleanup(ctx, cfg, state);
        assertTrue(ActiveStorms.active(CINDARA).isEmpty());
        assertEquals(2, crossings.size());
        ThresholdEvents.ThresholdCrossing end = crossings.get(1);
        assertEquals(0L, end.value());
        assertFalse(end.rising());
        List<StormSyncPayload> none = ops.sentTo(alice, StormSyncPayload.class);
        assertEquals(1, none.size());
        assertEquals(StormVariant.NONE_WIRE_ID, none.get(0).variant());
        assertEquals(0, state.trackedPlayers());

        type.onCleanup(ctx, cfg, state);
        assertEquals(2, crossings.size(), "cleanup is idempotent");
    }

    @Test
    void exposurePacketOnlyOnChangeAndEffectsOnlyWhenExposed() {
        ops.players.add(alice);
        ops.players.add(bob);
        ops.exposedPlayers.add(alice);
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = config("dust", 2, true);
        FakePayloadContext ctx = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), cfg);
        type.onStart(ctx, cfg, state);

        run(type, ctx, cfg, state, 40); // one sweep (now 4000 % 40 == 0)
        assertEquals(List.of(new StormExposurePayload(true)), ops.sentTo(alice, StormExposurePayload.class));
        assertEquals(List.of(new StormExposurePayload(false)), ops.sentTo(bob, StormExposurePayload.class));
        assertEquals(1, ops.applied.size());
        assertEquals(alice, ops.applied.get(0).player());

        run(type, ctx, cfg, state, 40); // nothing changed
        assertEquals(1, ops.sentTo(alice, StormExposurePayload.class).size());
        assertEquals(1, ops.sentTo(bob, StormExposurePayload.class).size());

        ops.exposedPlayers.remove(alice);
        ops.exposedPlayers.add(bob);
        run(type, ctx, cfg, state, 40);
        assertEquals(new StormExposurePayload(false), ops.sentTo(alice, StormExposurePayload.class).get(1));
        assertEquals(new StormExposurePayload(true), ops.sentTo(bob, StormExposurePayload.class).get(1));
    }

    @Test
    void effectsRespectTheDefinitionAndServerSwitches() {
        ops.players.add(alice);
        ops.exposedPlayers.add(alice);
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config off = new StormPayloadBase.Config(2, 600, "dust", false, true, true, Optional.empty());
        FakePayloadContext ctx = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), off);
        type.onStart(ctx, off, state);
        run(type, ctx, off, state, 80);
        assertTrue(ops.applied.isEmpty(), "effects_enabled=false");
        assertEquals(1, ops.sentTo(alice, StormExposurePayload.class).size(), "exposure still reported for the HUD");
        type.onCleanup(ctx, off, state);

        ops.settings.stormEffectsEnabled = false;
        StormPayloadBase.Config on = config("dust", 2, true);
        FakePayloadContext ctx2 = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State state2 = type.createState(ctx2.instance(), on);
        type.onStart(ctx2, on, state2);
        run(type, ctx2, on, state2, 80);
        assertTrue(ops.applied.isEmpty(), "stormEffectsEnabled=false");
    }

    @Test
    void playerEnteringMidStormIsSyncedAndLeaversAreForgotten() {
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = config("cryo", 1, true);
        Identifier glacira = Identifier.fromNamespaceAndPath("nerospace", "glacira");
        world.dimension(glacira);
        FakePayloadContext ctx = ctx(glacira, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), cfg);
        type.onStart(ctx, cfg, state);
        assertTrue(ops.sent.isEmpty(), "nobody there at start");

        ops.players.add(alice);
        run(type, ctx, cfg, state, 40);
        assertEquals(1, ops.sentTo(alice, StormSyncPayload.class).size());
        assertEquals(1, state.trackedPlayers());

        ops.players.remove(alice);
        run(type, ctx, cfg, state, 40);
        assertEquals(0, state.trackedPlayers(), "a player who left is dropped (re-synced on return)");

        ops.players.add(alice);
        run(type, ctx, cfg, state, 40);
        StormPayloadBase.forget(alice);
        assertEquals(0, state.trackedPlayers(), "erasure drops the player from the exposure map");
    }

    @Test
    void drainRunsOnlyForDrainingVariantsAndNeverBreaksTheStorm() {
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config ion = config("ion", 2, true);
        FakePayloadContext ctx = ctx(CINDARA, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), ion);
        type.onStart(ctx, ion, state);
        run(type, ctx, ion, state, 400);
        assertEquals(2, ops.drains, "one pass per 200 ticks");
        ops.throwOnDrain = true;
        assertDoesNotThrow(() -> run(type, ctx, ion, state, 200));
        type.onCleanup(ctx, ion, state);

        ops.throwOnDrain = false;
        ops.drains = 0;
        StormPayloadBase.Config dust = config("dust", 3, true);
        FakePayloadContext green = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State dustState = type.createState(green.instance(), dust);
        type.onStart(green, dust, dustState);
        run(type, green, dust, dustState, 400);
        assertEquals(0, ops.drains);

        StormPayloadBase.Config noDrain = new StormPayloadBase.Config(2, 600, "ion", true, false, true,
                Optional.empty());
        FakePayloadContext cin = ctx(CINDARA, 0L);
        StormPayloadBase.State s3 = type.createState(cin.instance(), noDrain);
        type.onStart(cin, noDrain, s3);
        run(type, cin, noDrain, s3, 400);
        assertEquals(0, ops.drains, "drain_enabled=false");
    }

    @Test
    void agricultureModifierOnlyWhileActiveAndEnabled() {
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = config("dust", 2, true);
        FakePayloadContext ctx = ctx(GREENXERTZ, 0L);
        StormPayloadBase.State state = type.createState(ctx.instance(), cfg);
        assertNull(modifier(GREENXERTZ));
        type.onStart(ctx, cfg, state);
        assertArrayEquals(new float[] {0.6F, 0.8F, 0.7F}, modifier(GREENXERTZ));
        assertNull(modifier(CINDARA), "dimension-wide, and only that dimension");
        type.onCleanup(ctx, cfg, state);
        assertNull(modifier(GREENXERTZ));

        StormPayloadBase.Config off = config("dust", 2, false);
        FakePayloadContext ctx2 = ctx(GREENXERTZ, 0L);
        type.onStart(ctx2, off, type.createState(ctx2.instance(), off));
        assertNull(modifier(GREENXERTZ), "agriculture_enabled=false");
    }

    /** What the agriculture façade computes from the public read side. */
    private static float[] modifier(Identifier dimension) {
        return ActiveStorms.active(dimension).filter(ActiveStorms.StormStatus::agricultureEnabled)
                .map(s -> s.variant().agriculture()).orElse(null);
    }

    @Test
    void saveLoadRoundTripAndResume() {
        ops.players.add(alice);
        PlanetaryStormPayload type = new PlanetaryStormPayload(ops);
        StormPayloadBase.Config cfg = new StormPayloadBase.Config(3, 600, "auto", true, true, true, Optional.of(123));
        FakePayloadContext ctx = ctx(CINDARA, 77L);
        StormPayloadBase.State state = type.createState(ctx.instance(), cfg);
        type.onStart(ctx, cfg, state);
        CompoundTag saved = type.saveState(state);
        assertFalse(saved.toString().contains(alice.toString()), "no player in the saved state");

        // simulated restart
        ActiveStorms.resetForTests();
        StormPayloadBase.resetForTests();
        crossings.clear();
        ops.clearRecords();
        StormPayloadBase.State loaded = type.loadState(ctx.instance(), cfg, saved);
        assertEquals(StormVariant.ION, loaded.variant());
        assertEquals(3, loaded.intensity());
        assertEquals(123, loaded.windYaw());
        assertEquals(state.phase40, loaded.phase40);
        assertEquals(state.phase200, loaded.phase200);
        assertEquals(state.startedAt, loaded.startedAt);

        ResumeResult resumed = type.onResume(ctx, cfg, loaded);
        assertTrue(resumed.resumed());
        assertTrue(ActiveStorms.active(CINDARA).isPresent());
        assertEquals(1, crossings.size());
        assertTrue(crossings.get(0).rising(), "a fresh start on resume");
        assertEquals(1, ops.sentTo(alice, StormSyncPayload.class).size());
    }

    @Test
    void configCodecDefaultsAndRanges() {
        StormPayloadBase.Config defaults = StormPayloadBase.Config.defaults();
        assertEquals(2, defaults.intensity());
        assertEquals(600, defaults.rampTicks());
        assertEquals("auto", defaults.variant());
        assertTrue(defaults.effectsEnabled() && defaults.drainEnabled() && defaults.agricultureEnabled());
        assertTrue(defaults.windYaw().isEmpty());
        FakePayloadContext ctx = ctx(CINDARA, 5L);
        StormPayloadBase.State a = new PlanetaryStormPayload(ops).createState(ctx.instance(), defaults);
        StormPayloadBase.State b = new PlanetaryStormPayload(ops).createState(ctx.instance(), defaults);
        assertEquals(a.windYaw(), b.windYaw(), "wind is deterministic per instance");
        assertTrue(a.windYaw() >= 0 && a.windYaw() < 360);
    }
}
