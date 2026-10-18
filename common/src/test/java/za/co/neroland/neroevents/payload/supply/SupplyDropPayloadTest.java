package za.co.neroland.neroevents.payload.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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

import za.co.neroland.neroevents.event.ContributionTable;
import za.co.neroland.neroevents.event.EnginePayloadContext;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.testing.FakeClock;
import za.co.neroland.neroevents.testing.FakeWorldView;
import za.co.neroland.neroevents.testing.core.CoreFixtures;

/** The supply drop lifecycle over a fake pod world: skips, landing, opening, credits, resolution, persistence. */
class SupplyDropPayloadTest {

    private static final BlockPos ANCHOR = new BlockPos(100, 64, -40);

    /** A scriptable pod world. */
    static final class FakePods implements SupplyDropPayload.PodWorld {
        boolean sky = true;
        boolean refuse;
        boolean loaded = true;
        boolean present = true;
        boolean landed;
        boolean empty;
        UUID pod = UUID.fromString("00000000-0000-0000-0000-0000000000f0");
        SupplyDropPayload.PodSpec spec;
        final List<UUID> discarded = new ArrayList<>();

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return sky;
        }

        @Override
        public UUID spawnPod(SupplyDropPayload.PodSpec s) {
            this.spec = s;
            return refuse ? null : pod;
        }

        @Override
        public SupplyDropPayload.PodStatus pod(UUID uuid) {
            return present && uuid.equals(pod) ? new SupplyDropPayload.PodStatus(landed, empty, ANCHOR) : null;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return loaded;
        }

        @Override
        public void discard(UUID uuid) {
            discarded.add(uuid);
            present = false;
        }
    }

    private FakePods pods;
    private SupplyDropPayload type;
    private FakeClock clock;
    private EventInstance instance;
    private EnginePayloadContext ctx;
    private SupplyDropPayload.Config config;
    private SupplyDropPayload.State state;

    @BeforeEach
    void setUp() {
        SupplyDropPayload.resetForTests();
        pods = new FakePods();
        type = new SupplyDropPayload(c -> pods);
        clock = new FakeClock(1000L);
        config = SupplyDropPayload.Config.DEFAULT;
        EventDefinition def = CoreFixtures.definition(type, config, 18000, 96);
        instance = CoreFixtures.instance(def, ANCHOR, 42L);
        ctx = CoreFixtures.context(instance, new FakeWorldView().allChunksLoaded(CoreFixtures.DIM), clock);
        state = type.createState(instance, config);
    }

    @AfterEach
    void tearDown() {
        SupplyDropPayload.resetForTests();
    }

    private TickResult tick() {
        return type.tick(ctx, config, state);
    }

    @Test
    void noSkySkips() {
        pods.sky = false;
        StartResult result = type.onStart(ctx, config, state);
        assertEquals(StartResult.skip("no_sky"), result);
        assertNull(pods.spec);
    }

    @Test
    void refusedSpawnSkipsNoRoom() {
        pods.refuse = true;
        assertEquals("no_room", type.onStart(ctx, config, state).skipReason());
    }

    @Test
    void podSpawnsAboveTheAnchorWithTheConfiguredDescent() {
        assertTrue(type.onStart(ctx, config, state).started());
        assertEquals(ANCHOR.getY() + config.podDropHeight(), pods.spec.y(), 1e-9);
        assertEquals(config.descentBlocksPerTick(), pods.spec.descent(), 1e-9);
        assertEquals(1, pods.spec.tier());
        assertEquals(config.effectiveBudget(), pods.spec.lootBudget());
    }

    @Test
    void landedThenOpenedResolvesSuccessAfterTheLinger() {
        type.onStart(ctx, config, state);
        assertInstanceOf(TickResult.Continue.class, tick());
        pods.landed = true;
        tick();
        assertTrue(state.landedAt() > 0L);
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        clock.advance(config.podLingerTicks() - 1L);
        assertInstanceOf(TickResult.Continue.class, tick());
        clock.advance(1L);
        assertEquals(TickResult.resolved(EventOutcome.SUCCESS), tick());
        assertEquals(List.of(pods.pod), pods.discarded);
    }

    @Test
    void unopenedPodExpiresAfterTheUnopenedLinger() {
        type.onStart(ctx, config, state);
        pods.landed = true;
        tick();
        clock.advance(config.unopenedLingerTicks() - 1L);
        assertInstanceOf(TickResult.Continue.class, tick());
        clock.advance(1L);
        assertEquals(TickResult.resolved(EventOutcome.EXPIRED), tick());
    }

    @Test
    void emptiedPodResolvesAfterTheGrace() {
        type.onStart(ctx, config, state);
        pods.landed = true;
        tick();
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        pods.empty = true;
        clock.advance(SupplyDropPayload.EMPTY_GRACE_TICKS);
        assertEquals(TickResult.resolved(EventOutcome.SUCCESS), tick());
    }

    @Test
    void timeoutIsSuccessOnlyWhenOpened() {
        type.onStart(ctx, config, state);
        assertEquals(EventOutcome.EXPIRED, type.onTimeout(ctx, config, state));
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        assertEquals(EventOutcome.SUCCESS, type.onTimeout(ctx, config, state));
    }

    @Test
    void firstOpenerClaimsAndLaterOpenersGetAOnceKeyedLootCredit() {
        type.onStart(ctx, config, state);
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.BOB, clock.now());
        List<PendingCredits.Pending> queued = state.pending().snapshot();
        assertEquals(2, queued.size());
        assertEquals(CoreFixtures.ALICE, queued.get(0).player());
        assertEquals(pods.pod, queued.get(0).attribution()); // supply claim: the pod UUID rides as attribution
        assertEquals(CoreFixtures.BOB, queued.get(1).player());
        assertEquals(ContributionTable.Kind.LOOT, queued.get(1).kind());
        assertEquals("pod:" + pods.pod, queued.get(1).onceKey());
        assertNull(queued.get(1).attribution());
        assertEquals(2, state.openCount());
    }

    @Test
    void openingAfterTheEventCreditsNobody() {
        type.onStart(ctx, config, state);
        type.onCleanup(ctx, config, state);
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        assertEquals(0, state.pending().size());
        assertEquals(0, state.openCount());
    }

    @Test
    void rolledStacksNeverExceedBudget() {
        SupplyDropPayload.LootRoller<Integer> roller = (table, rolls, seed) -> {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                out.add(25);
            }
            return out;
        };
        Identifier table = SupplyDropPayload.DEFAULT_LOOT;
        List<Integer> kept = SupplyDropPayload.rollPod(roller, Integer::longValue, table, 3, 1L, config.effectiveBudget());
        assertTrue(kept.stream().mapToLong(Integer::longValue).sum() <= config.effectiveBudget());
        assertEquals(12, kept.size());
        assertEquals(40, SupplyDropPayload.rollPod(roller, Integer::longValue, table, 3, 1L, 0L).size());
    }

    /**
     * A roller shaped like the shipped pod tables: every roll of the table yields one material stack (pool A,
     * 20–120 VU) and one extra (pool B, 10–40 VU). At the default rolls and budget at least one big material
     * stack must survive the clamp — the budget trims the haul, it must not strip the good stacks.
     */
    @Test
    void bigStackSurvivesTheDefaultBudget() {
        long[] materials = {120L, 80L, 60L, 120L, 100L, 20L};
        long[] extras = {30L, 10L, 40L, 40L, 20L, 10L};
        SupplyDropPayload.LootRoller<Long> shaped = (table, rolls, seed) -> {
            List<Long> out = new ArrayList<>();
            for (int r = 0; r < rolls; r++) {
                out.add(materials[r % materials.length]);
                out.add(extras[r % extras.length]);
            }
            return out;
        };
        SupplyDropPayload.LootRoller<Long> worst = (table, rolls, seed) -> {
            List<Long> out = new ArrayList<>();
            for (int r = 0; r < rolls; r++) {
                out.add(120L);
                out.add(40L);
            }
            return out;
        };
        Identifier table = SupplyDropPayload.DEFAULT_LOOT;
        for (SupplyDropPayload.LootRoller<Long> roller : List.of(shaped, worst)) {
            List<Long> kept = SupplyDropPayload.rollPod(roller, Long::longValue, table, config.lootRolls(), 1L,
                    config.effectiveBudget());
            assertTrue(kept.stream().mapToLong(Long::longValue).sum() <= config.effectiveBudget());
            assertTrue(kept.stream().anyMatch(v -> v >= 60L), "a big material stack survives: " + kept);
        }
    }

    /** The pod tables roll the material pool once per {@code loot_rolls}, so the definition's rolls are the knob. */
    @Test
    void podTablesRollEachPoolOncePerLootRoll() throws Exception {
        for (int tier = 1; tier <= 3; tier++) {
            String path = "/data/neroevents/loot_table/pods/supply_tier" + tier + ".json";
            try (java.io.InputStream in = SupplyDropPayloadTest.class.getResourceAsStream(path)) {
                assertTrue(in != null, path);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (com.google.gson.JsonElement pool : root.getAsJsonArray("pools")) {
                    assertEquals(1, pool.getAsJsonObject().get("rolls").getAsInt(), path);
                }
            }
        }
    }

    @Test
    void saveLoadRoundTrip() {
        type.onStart(ctx, config, state);
        pods.landed = true;
        tick();
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, 1234L);
        CompoundTag tag = type.saveState(state);
        SupplyDropPayload.State loaded = type.loadState(instance, config, tag);
        assertEquals(state.podUuid(), loaded.podUuid());
        assertEquals(state.openCount(), loaded.openCount());
        assertEquals(state.landedAt(), loaded.landedAt());
        assertEquals(tag, type.saveState(loaded));
        assertTrue(type.onResume(ctx, config, loaded).resumed());
    }

    @Test
    void podGoneNeverOpenedIsFailure() {
        type.onStart(ctx, config, state);
        pods.present = false;
        assertEquals(TickResult.resolved(EventOutcome.FAILURE), tick());
    }

    @Test
    void podGoneAfterOpeningIsSuccess() {
        type.onStart(ctx, config, state);
        SupplyDropPayload.onPodOpened(instance.id(), pods.pod, CoreFixtures.ALICE, clock.now());
        pods.present = false;
        assertEquals(TickResult.resolved(EventOutcome.SUCCESS), tick());
    }

    @Test
    void podInAnUnloadedChunkKeepsWaiting() {
        type.onStart(ctx, config, state);
        pods.present = false;
        pods.loaded = false;
        assertInstanceOf(TickResult.Continue.class, tick());
    }

    @Test
    void outOfRangeTierWarnsAndFallsBack() {
        SupplyDropPayload.Config bad = new SupplyDropPayload.Config(7, 80, 0.35D, 0.02D, SupplyDropPayload.DEFAULT_LOOT, 3,
                300L, 6000, 12000);
        assertEquals(1, type.validate(bad).size());
        assertEquals(1, bad.effectiveTier());
    }
}
