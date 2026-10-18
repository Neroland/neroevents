package za.co.neroland.neroevents.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionSource;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.TestSources.DenyAllSource;
import za.co.neroland.neroevents.testing.TestSources.DenyInBoxSource;
import za.co.neroland.neroevents.testing.creatures.FakeCreatureWorld;

/** The wave payload against a fake NeroCreatures: start/no_room, stagger, protection, wave ends, credit, resume. */
class WavePayloadTest {

    private static final Identifier DIM = FakeWorldView.OVERWORLD;
    private static final Identifier PIRATE = Identifier.fromNamespaceAndPath("nerocreatures", "space_pirate");
    private static final Identifier GHOST = Identifier.fromNamespaceAndPath("nerocreatures", "ghost");
    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);

    private FakeWorldView view;
    private FakeClock clock;
    private FakeCreatureWorld fake;
    private List<ProtectionSource> sources;
    private EventInstance instance;
    private PirateRaidPayload payload;

    @BeforeEach
    void setUp() {
        WavePayloadBase.resetForTests();
        view = new FakeWorldView().allChunksLoaded(DIM).online(DIM, 1);
        clock = new FakeClock(1000L);
        fake = new FakeCreatureWorld();
        sources = new ArrayList<>();
        EventDefinition def = TestInstances.definition("wave_test", "raid", null);
        instance = new EventInstance(UUID.randomUUID(), def, DIM, ANCHOR,
                Footprint.cylinder(DIM, ANCHOR, 64, -64, 319), EventOrigin.ADMIN, 42L, 0L, EventPhase.SCHEDULED);
        TestInstances.phase(instance, EventPhase.ACTIVE);
        payload = new PirateRaidPayload(ctx -> fake, () -> true, id -> !id.equals(GHOST));
    }

    @AfterEach
    void tearDown() {
        WavePayloadBase.resetForTests();
        EventEntityIndex.clearInstance(instance.id());
    }

    private PayloadContext ctx() {
        return new EnginePayloadContext(instance, view, new ProtectionQuery(sources), clock);
    }

    private static WaveTable.Wave wave(Identifier type, int count, int delay, int timeout) {
        return new WaveTable.Wave(List.of(new WaveTable.MobEntry(type, count)), delay, timeout);
    }

    private static WavePayloadBase.Config config(int spawnPerTick, WaveTable.Wave... waves) {
        return new WavePayloadBase.Config(List.of(waves), 0.0D, 24, 16, 28, spawnPerTick, 0.5D, true, false);
    }

    private TickResult tick(WavePayloadBase.Config config, WavePayloadBase.State state) {
        TickResult result = payload.tick(ctx(), config, state);
        clock.advance(1);
        return result;
    }

    /** Ticks until resolved or {@code max} ticks pass; returns the last result. */
    private TickResult run(WavePayloadBase.Config config, WavePayloadBase.State state, int max) {
        TickResult result = TickResult.proceed();
        for (int i = 0; i < max; i++) {
            result = tick(config, state);
            if (result instanceof TickResult.Resolved) {
                return result;
            }
        }
        return result;
    }

    private WavePayloadBase.State started(WavePayloadBase.Config config) {
        WavePayloadBase.State state = payload.createState(instance, config);
        assertTrue(payload.onStart(ctx(), config, state).started());
        return state;
    }

    @Test
    void firstWaveWithNoRoomIsASkipAndNeverSpawns() {
        sources.add(new DenyAllSource("deny"));
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600));
        WavePayloadBase.State state = payload.createState(instance, config);
        StartResult result = payload.onStart(ctx(), config, state);
        assertFalse(result.started());
        assertEquals("no_room", result.skipReason());
        assertEquals(0, fake.spawnCalls, "a denied position never reaches spawnWithin");
        assertEquals(1, fake.closed.size(), "the reserved wave is closed");
        assertTrue(WavePayloadBase.live(instance.id()).isEmpty());
    }

    @Test
    void unavailableCreaturesSkip() {
        payload = new PirateRaidPayload(ctx -> fake, () -> false, id -> true);
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600));
        StartResult result = payload.onStart(ctx(), config, payload.createState(instance, config));
        assertEquals("no_creatures", result.skipReason());
        payload = new PirateRaidPayload(ctx -> null, () -> true, id -> true);
        assertEquals("no_room", payload.onStart(ctx(), config, payload.createState(instance, config)).skipReason());
    }

    @Test
    void deniedPositionsNeverSpawn() {
        sources.add(new DenyInBoxSource("box", 0, -1000, 1000, 1000));
        WavePayloadBase.Config config = config(8, wave(PIRATE, 20, 0, 600));
        started(config);
        assertFalse(fake.spawned.isEmpty());
        assertTrue(fake.spawned.size() < 20, "some positions were refused");
        assertEquals(fake.spawned.size(), fake.spawnCalls);
        fake.spawned.values().forEach(pos -> assertTrue(pos.getX() < 0, "spawned inside the denied box: " + pos));
    }

    @Test
    void placementIsStaggered() {
        WavePayloadBase.Config config = config(1, wave(PIRATE, 16, 0, 600));
        WavePayloadBase.State state = started(config);
        assertEquals(10, fake.spawnCalls, "start places spawn_per_tick × 10 attempts");
        int before = fake.spawnCalls;
        for (int i = 0; i < 6; i++) {
            tick(config, state);
            assertTrue(fake.spawnCalls - before <= 1, "at most spawn_per_tick placements per tick");
            before = fake.spawnCalls;
        }
        assertEquals(16, fake.spawned.size());
        assertEquals(WavePayloadBase.WaveState.FIGHTING, state.waveState());
        assertEquals(16, state.totalPlaced());
    }

    @Test
    void waveEndsWhenAllAreDeadAndTheKillFractionDecides() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        assertEquals(WavePayloadBase.WaveState.FIGHTING, state.waveState());
        List<UUID> mobs = new ArrayList<>(fake.spawned.keySet());
        assertTrue(WavePayloadBase.recordKill(state, mobs.get(0), true));
        assertTrue(WavePayloadBase.recordKill(state, mobs.get(1), true));
        fake.gone.addAll(mobs);
        TickResult result = run(config, state, 40);
        assertInstanceOf(TickResult.Resolved.class, result);
        assertEquals(EventOutcome.SUCCESS, ((TickResult.Resolved) result).outcome(), "2 of 4 ≥ ceil(0.5 × 4)");
        assertEquals(1, fake.closed.size());
        assertEquals(0, state.escaped());
    }

    @Test
    void tooFewKillsFails() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        List<UUID> mobs = new ArrayList<>(fake.spawned.keySet());
        WavePayloadBase.recordKill(state, mobs.get(0), true);
        fake.gone.addAll(mobs);
        TickResult result = run(config, state, 40);
        assertEquals(EventOutcome.FAILURE, ((TickResult.Resolved) result).outcome());
        assertTrue(WavePayloadBase.succeeded(3, 5, 0.5D));
        assertFalse(WavePayloadBase.succeeded(2, 5, 0.5D));
        assertTrue(WavePayloadBase.succeeded(0, 5, 0.0D));
    }

    @Test
    void timeoutEndsTheWaveWithSurvivorsEscaped() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600), wave(PIRATE, 2, 100, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        run(config, state, 600);
        assertEquals(1, state.waveIndex());
        assertEquals(WavePayloadBase.WaveState.WAITING, state.waveState());
        assertEquals(4, state.escaped(), "survivors are counted as escaped");
        assertEquals(1, fake.closed.size(), "closeWave once per wave");
        assertTrue(state.alive().isEmpty());
        // the second wave begins after its delay
        run(config, state, 101);
        assertEquals(2, fake.reserved.size());
    }

    @Test
    void laterWaveWithNoRoomEndsEarly() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 2, 0, 600), wave(GHOST, 3, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        fake.gone.addAll(fake.spawned.keySet());
        TickResult result = run(config, state, 60);
        assertInstanceOf(TickResult.Resolved.class, result, "the empty second wave ends at once");
        assertEquals(1, fake.reserved.size(), "nothing reserved for a wave of unknown types");
        assertEquals(2, state.waveIndex());
    }

    @Test
    void survivalCreditOnlyForPlayersInsideTheBounds() {
        UUID inside = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID outside = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        fake.players.put(inside, new BlockPos(10, 64, 10));
        fake.players.put(outside, new BlockPos(500, 64, 500));
        WavePayloadBase.Config config = config(2, wave(PIRATE, 2, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        fake.gone.addAll(fake.spawned.keySet());
        run(config, state, 40);
        assertEquals(instance.bounds(), fake.lastBounds);
        assertTrue(fake.credits.contains("survival " + inside + " 0"));
        assertFalse(fake.credits.stream().anyMatch(c -> c.contains(outside.toString())));
    }

    @Test
    void killsCountOncePerVictimAndOnlyWhenAttributable() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 2, 0, 600));
        WavePayloadBase.State state = started(config);
        UUID victim = UUID.randomUUID();
        assertTrue(WavePayloadBase.recordKill(state, victim, true));
        assertFalse(WavePayloadBase.recordKill(state, victim, true));
        assertFalse(WavePayloadBase.recordKill(state, UUID.randomUUID(), false));
        assertEquals(1, state.killedByPlayers());
        assertTrue(WavePayloadBase.live(instance.id()).isPresent());
        // every placed mob is in the index, so damage hooks find the instance
        fake.spawned.keySet().forEach(mob -> assertEquals(instance.id(), EventEntityIndex.instanceOf(mob).orElseThrow()));
    }

    @Test
    void cleanupClosesAndSweepsEveryWave() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 2, 0, 600), wave(PIRATE, 2, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        fake.gone.addAll(fake.spawned.keySet());
        run(config, state, 25);
        tick(config, state); // begins wave 2
        assertEquals(2, state.waveIds().size());
        int closedBefore = fake.closed.size();
        payload.onCleanup(ctx(), config, state);
        assertEquals(closedBefore + 1, fake.closed.size(), "the open wave is closed");
        assertEquals(state.waveIds(), fake.swept);
        assertTrue(WavePayloadBase.live(instance.id()).isEmpty());
        assertTrue(fake.spawned.keySet().stream().allMatch(m -> EventEntityIndex.instanceOf(m).isEmpty()));
        payload.onCleanup(ctx(), config, state);
        assertEquals(closedBefore + 1, fake.closed.size(), "cleanup is idempotent");
    }

    @Test
    void saveLoadRoundTripAndResumeRebuildsAlive() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600));
        WavePayloadBase.State state = started(config);
        tick(config, state);
        WavePayloadBase.recordKill(state, UUID.randomUUID(), true);
        CompoundTag tag = payload.saveState(state);
        WavePayloadBase.resetForTests();
        WavePayloadBase.State loaded = payload.loadState(instance, config, tag);
        assertEquals(state.waveIndex(), loaded.waveIndex());
        assertEquals(state.waveState(), loaded.waveState());
        assertEquals(state.waveIds(), loaded.waveIds());
        assertEquals(state.totalPlaced(), loaded.totalPlaced());
        assertEquals(1, loaded.killedByPlayers());
        assertTrue(loaded.alive().isEmpty(), "the alive set is not saved");

        UUID dead = fake.spawned.keySet().iterator().next();
        fake.gone.add(dead);
        assertTrue(payload.onResume(ctx(), config, loaded).resumed());
        assertEquals(3, loaded.alive().size(), "rebuilt from findWave");
        assertTrue(WavePayloadBase.live(instance.id()).isPresent());
        loaded.alive().forEach(m -> assertEquals(instance.id(), EventEntityIndex.instanceOf(m).orElseThrow()));
    }

    @Test
    void resumeWithNothingLeftEndsTheWaveOnTheFirstTick() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 4, 0, 600), wave(PIRATE, 2, 50, 600));
        WavePayloadBase.State state = started(config);
        CompoundTag tag = payload.saveState(state);
        WavePayloadBase.State loaded = payload.loadState(instance, config, tag);
        fake.gone.addAll(fake.spawned.keySet());
        payload.onResume(ctx(), config, loaded);
        tick(config, loaded);
        assertEquals(1, loaded.waveIndex());
        assertEquals(WavePayloadBase.WaveState.WAITING, loaded.waveState());
    }

    @Test
    void validateWarnsOnReservedBlockDamageAndUnknownTypes() {
        WavePayloadBase.Config config = new WavePayloadBase.Config(List.of(wave(GHOST, 2, 0, 600)), 0.25D, 24, 30, 20,
                2, 0.5D, true, true);
        List<String> warnings = payload.validate(config);
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("never break blocks"));
    }

    @Test
    void footprintIsTheSpawnRing() {
        WavePayloadBase.Config config = config(2, wave(PIRATE, 2, 0, 600));
        Footprint f = payload.footprint(instance.definition(), config, DIM, ANCHOR);
        assertEquals(new Footprint(DIM, -28, 56, -28, 28, 80, 28), f);
    }
}
