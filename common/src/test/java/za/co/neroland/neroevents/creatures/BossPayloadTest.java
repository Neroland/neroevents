package za.co.neroland.neroevents.creatures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroevents.compat.creatures.CreaturesBridge.SummonedBoss;
import za.co.neroland.neroevents.event.ConsentMode;
import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOrigin;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.EventPhase;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PopulationBand;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.TestInstances;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.creatures.FakeCreatureWorld;

/** The boss payload: preflight budget, start skips, identity rules, the boss_pressure trigger, cleanup, items. */
class BossPayloadTest {

    private static final Identifier DIM = FakeWorldView.OVERWORLD;
    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);
    private static final UUID BOSS = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final UUID FIGHT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID NATURAL_BOSS = UUID.fromString("00000000-0000-0000-0000-0000000000b9");

    private FakeWorldView view;
    private FakeClock clock;
    private FakeCreatureWorld fake;
    private EventInstance instance;
    private BossPayload payload;
    private final BossPayload.Config config = new BossPayload.Config(BossPayload.CINDER_TYRANT, 24);

    @BeforeEach
    void setUp() {
        BossPayload.resetForTests();
        view = new FakeWorldView().allChunksLoaded(DIM);
        clock = new FakeClock(1000L);
        fake = new FakeCreatureWorld();
        fake.bossIds = Set.of(BossPayload.CINDER_TYRANT);
        fake.summonResult = Optional.of(new SummonedBoss(BOSS, FIGHT));
        fake.activeFights.add(FIGHT);
        instance = instanceOf(TestInstances.definition("boss_test", "boss", null));
        payload = new BossPayload(ctx -> fake, () -> true, () -> 150L);
    }

    @AfterEach
    void tearDown() {
        BossPayload.resetForTests();
        EventEntityIndex.clearInstance(instance.id());
    }

    private EventInstance instanceOf(EventDefinition def) {
        EventInstance i = new EventInstance(UUID.randomUUID(), def, DIM, ANCHOR, Footprint.cylinder(DIM, ANCHOR, 64,
                -64, 319), EventOrigin.ADMIN, 7L, 0L, EventPhase.SCHEDULED);
        TestInstances.phase(i, EventPhase.ACTIVE);
        return i;
    }

    private PayloadContext ctx() {
        return new EnginePayloadContext(instance, view, new ProtectionQuery(List.of()), clock);
    }

    private BossPayload.State started() {
        BossPayload.State state = payload.createState(instance, config);
        assertTrue(payload.onStart(ctx(), config, state).started());
        return state;
    }

    private TickResult tick(BossPayload.State state) {
        TickResult result = payload.tick(ctx(), config, state);
        clock.advance(1);
        return result;
    }

    private static SchedulerSnapshot snapshot(Set<String> mods, int online, PopulationBand band, long remaining) {
        return new SchedulerSnapshot(0L, online, band, false, ConsentMode.OFF, true, false, false, Map.of(),
                List.of(), Map.of(), Map.of(), Map.of(), mods, Map.of(), Map.of(), 0, 0L, Map.of(), Map.of(), remaining);
    }

    @Test
    void preflightNeedsCreaturesAndBudgetForExpectedParticipants() {
        PopulationBand band = new PopulationBand("2-4", 2, 4, 1.0D, 3);
        EventDefinition def = instance.definition();
        assertEquals(Optional.of("no_creatures"), payload.preflight(snapshot(Set.of(), 3, band, 10_000L), def));
        // expected participants = band max 4 → 4 × 150 = 600
        assertEquals(Optional.of("budget"), payload.preflight(snapshot(Set.of("nerocreatures"), 3, band, 599L), def));
        assertEquals(Optional.empty(), payload.preflight(snapshot(Set.of("nerocreatures"), 3, band, 600L), def));
        // open top band: max(online, floor) = 12 → 1800
        PopulationBand top = new PopulationBand("10+", 10, -1, 1.0D, 6);
        assertEquals(Optional.of("budget"), payload.preflight(snapshot(Set.of("nerocreatures"), 12, top, 1799L), def));
        assertEquals(Optional.empty(), payload.preflight(snapshot(Set.of("nerocreatures"), 12, top, Long.MAX_VALUE), def));
    }

    @Test
    void startSkips() {
        fake.bossIds = Set.of();
        assertEquals("boss_unknown", payload.onStart(ctx(), config, payload.createState(instance, config)).skipReason());
        fake.bossIds = Set.of(BossPayload.CINDER_TYRANT);
        fake.summonResult = Optional.empty();
        assertEquals("boss_refused", payload.onStart(ctx(), config, payload.createState(instance, config)).skipReason());
        payload = new BossPayload(ctx -> fake, () -> false, () -> 0L);
        assertEquals("no_creatures", payload.onStart(ctx(), config, payload.createState(instance, config)).skipReason());
        assertTrue(BossPayload.live(instance.id()).isEmpty());
    }

    @Test
    void startBindsTheBoss() {
        BossPayload.State state = started();
        assertEquals(BOSS, state.bossUuid());
        assertEquals(FIGHT, state.fightId());
        assertEquals(instance.id(), EventEntityIndex.instanceOf(BOSS).orElseThrow());
        assertTrue(BossPayload.live(instance.id()).isPresent());
    }

    @Test
    void defeatedIsSuccess() {
        BossPayload.State state = started();
        fake.defeated.put(BOSS, Optional.of(false));
        for (int i = 0; i < 40; i++) {
            assertTrue(!(tick(state) instanceof TickResult.Resolved));
        }
        fake.defeated.put(BOSS, Optional.of(true));
        TickResult result = TickResult.proceed();
        for (int i = 0; i < 21 && !(result instanceof TickResult.Resolved); i++) {
            result = tick(state);
        }
        assertInstanceOf(TickResult.Resolved.class, result);
        assertEquals(EventOutcome.SUCCESS, ((TickResult.Resolved) result).outcome());
    }

    @Test
    void missingWithNoFightForAMinuteEscapes() {
        BossPayload.State state = started();
        fake.activeFights.clear(); // not loaded (isDefeated empty) and the fight is over
        TickResult result = TickResult.proceed();
        int ticks = 0;
        while (!(result instanceof TickResult.Resolved) && ticks < 2000) {
            result = tick(state);
            ticks++;
        }
        assertEquals(EventOutcome.ESCAPED, ((TickResult.Resolved) result).outcome());
        assertTrue(ticks >= BossPayload.MISSING_TICKS, "not before " + BossPayload.MISSING_TICKS + " ticks: " + ticks);
        assertTrue(ticks <= BossPayload.MISSING_TICKS + 2 * BossPayload.CHECK_TICKS);
    }

    @Test
    void unloadedButFightActiveKeepsWaiting() {
        BossPayload.State state = started();
        for (int i = 0; i < 2000; i++) {
            assertTrue(!(tick(state) instanceof TickResult.Resolved));
        }
        assertEquals(-1L, state.missingSince());
    }

    @Test
    void bossPressureForcesTheCheckAndANaturalBossIsIgnored() {
        BossPayload.State state = started();
        fake.defeated.put(BOSS, Optional.of(false));
        tick(state); // mid-cadence (the start counts as a check): no check
        int checks = fake.defeatedChecks;
        tick(state); // still mid-cadence: no check
        assertEquals(checks, fake.defeatedChecks);

        // A natural boss (different entity, different fight) dies: boss_pressure falls in this dimension.
        fake.defeated.put(NATURAL_BOSS, Optional.of(true));
        BossPayload.requestCheck(Identifier.fromNamespaceAndPath("nerospace", "cindara"));
        tick(state);
        assertEquals(checks, fake.defeatedChecks, "another dimension's crossing is ignored");
        BossPayload.requestCheck(DIM);
        TickResult result = tick(state);
        assertEquals(checks + 1, fake.defeatedChecks, "the crossing forces the check that tick");
        assertTrue(!(result instanceof TickResult.Resolved), "our boss is still alive");

        // our own boss dies: the next forced check resolves at once
        fake.defeated.put(BOSS, Optional.of(true));
        BossPayload.requestCheck(DIM);
        result = tick(state);
        assertEquals(EventOutcome.SUCCESS, ((TickResult.Resolved) result).outcome());
    }

    @Test
    void cleanupDiscardsTheBossUnlessSuccess() {
        BossPayload.State state = started();
        TestInstances.outcome(instance, EventOutcome.ESCAPED);
        payload.onCleanup(ctx(), config, state);
        assertEquals(List.of(BOSS), fake.discarded);
        assertTrue(EventEntityIndex.instanceOf(BOSS).isEmpty());
        assertTrue(BossPayload.live(instance.id()).isEmpty());

        instance = instanceOf(instance.definition());
        fake.discarded.clear();
        BossPayload.State won = started();
        TestInstances.outcome(instance, EventOutcome.SUCCESS);
        payload.onCleanup(ctx(), config, won);
        assertTrue(fake.discarded.isEmpty());
    }

    @Test
    void timeoutEscapes() {
        BossPayload.State state = started();
        assertEquals(EventOutcome.ESCAPED, payload.onTimeout(ctx(), config, state));
    }

    @Test
    void saveLoadAndResume() {
        BossPayload.State state = started();
        state.missingSince = 1234L;
        CompoundTag tag = payload.saveState(state);
        BossPayload.resetForTests();
        EventEntityIndex.clearInstance(instance.id());
        BossPayload.State loaded = payload.loadState(instance, config, tag);
        assertEquals(BOSS, loaded.bossUuid());
        assertEquals(FIGHT, loaded.fightId());
        assertEquals(1234L, loaded.missingSince());
        assertTrue(payload.onResume(ctx(), config, loaded).resumed());
        assertEquals(instance.id(), EventEntityIndex.instanceOf(BOSS).orElseThrow());
        assertTrue(BossPayload.live(instance.id()).isPresent());
        // a fresh state (never started) round-trips with no ids
        BossPayload.State fresh = payload.loadState(instance, config,
                payload.saveState(payload.createState(instance, config)));
        assertEquals(null, fresh.bossUuid());
    }

    @Test
    void itemLinesAreWarned() {
        // category boss already loses item lines at parse; a boss payload under another category is warned here
        EventDefinition withItems = TestInstances.definition("boss_items", "combat", """
                {"budget": 100, "lines": [{"kind": "currency", "amount": 50},
                                          {"kind": "item", "item": "minecraft:diamond", "count": 1}]}""");
        assertEquals(1, BossPayload.itemLineWarnings(withItems).size());
        assertTrue(BossPayload.itemLineWarnings(withItems).get(0).contains("paid twice"));
        EventDefinition clean = TestInstances.definition("boss_clean", "boss",
                "{\"budget\": 100, \"lines\": [{\"kind\": \"currency\", \"amount\": 50}]}");
        assertTrue(BossPayload.itemLineWarnings(clean).isEmpty());
    }

    @Test
    void footprint() {
        assertEquals(new Footprint(DIM, -16, 60, -16, 16, 88, 16),
                payload.footprint(instance.definition(), config, DIM, ANCHOR));
    }
}
