package za.co.neroland.neroevents.payload.supply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.payload.LootBudget;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;

/**
 * {@code neroevents:supply_drop} — a parachuted pod lands near the anchor; the first player to open it claims
 * the drop (LOOT credit + the {@code supply_claimed} gate), later openers are credited once each.
 * The pod's contents are rolled once at landing (deterministic per instance, clamped to {@code pod_loot_budget}
 * VU). Rewards pay on {@code SUCCESS} only: a pod nobody opened pays nobody.
 *
 * <p>World access goes through the {@link PodWorld} seam (production: {@link ServerPodWorld}), so the
 * lifecycle is plain-JVM testable. Credits earned outside the tick (a pod opened) are queued in the state's
 * {@link PendingCredits} and drained at the top of the next tick. The saved state holds timers, counters, the
 * pod UUID and its last known block position — never who opened it.
 */
public final class SupplyDropPayload implements PayloadType<SupplyDropPayload.Config, SupplyDropPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "supply_drop");
    public static final Identifier DEFAULT_LOOT = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID,
            "pods/supply_tier1");
    /** After the pod is emptied, it stays this long before the event resolves (ticks). */
    static final long EMPTY_GRACE_TICKS = 600L;

    /** States of instances that are running on this server, so the pod's interaction hook can find them. */
    private static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();

    /** Rolls a loot table into stacks (production: {@code LootRolls::roll} bound to the level and the pod). */
    @FunctionalInterface
    public interface LootRoller<T> {
        List<T> roll(Identifier table, int rolls, long seed);
    }

    /** The {@code payload} object (snake_case; every key optional). */
    public record Config(int tier, int podDropHeight, double descentBlocksPerTick, double driftBlocksPerTick,
            Identifier lootTable, int lootRolls, long podLootBudget, int podLingerTicks, int unopenedLingerTicks)
            implements PayloadConfig {

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("tier", 1).forGetter(Config::tier),
                Codec.intRange(32, 160).optionalFieldOf("pod_drop_height", 80).forGetter(Config::podDropHeight),
                Codec.doubleRange(0.15D, 0.6D).optionalFieldOf("descent_blocks_per_tick", 0.35D)
                        .forGetter(Config::descentBlocksPerTick),
                Codec.doubleRange(0.0D, 0.05D).optionalFieldOf("drift_blocks_per_tick", 0.02D)
                        .forGetter(Config::driftBlocksPerTick),
                Identifier.CODEC.optionalFieldOf("loot_table", DEFAULT_LOOT).forGetter(Config::lootTable),
                Codec.intRange(1, 6).optionalFieldOf("loot_rolls", 3).forGetter(Config::lootRolls),
                Codec.LONG.optionalFieldOf("pod_loot_budget", 300L).forGetter(Config::podLootBudget),
                Codec.intRange(600, 72000).optionalFieldOf("pod_linger_ticks", 6000).forGetter(Config::podLingerTicks),
                Codec.intRange(600, 72000).optionalFieldOf("unopened_linger_ticks", 12000)
                        .forGetter(Config::unopenedLingerTicks)
        ).apply(inst, Config::new));

        public static final Config DEFAULT = new Config(1, 80, 0.35D, 0.02D, DEFAULT_LOOT, 3, 300L, 6000, 12000);

        /** The asset tier actually used (out-of-range values fall back to 1 with a validation warning). */
        public int effectiveTier() {
            return tier >= 1 && tier <= 3 ? tier : 1;
        }

        /** The budget actually used (clamped to 0..100000). */
        public long effectiveBudget() {
            return Math.max(0L, Math.min(100_000L, podLootBudget));
        }
    }

    /** Per-instance state. Saved: pod UUID, timers, open count, drift, last known pod position. */
    public static final class State implements PayloadState {
        @Nullable UUID instanceId;
        @Nullable UUID podUuid;
        long spawnedAt;
        long landedAt;
        long firstOpenedAt;
        int openCount;
        double driftX;
        double driftZ;
        @Nullable BlockPos lastKnown;
        final PendingCredits pending = new PendingCredits();

        public @Nullable UUID podUuid() {
            return podUuid;
        }

        public int openCount() {
            return openCount;
        }

        public long landedAt() {
            return landedAt;
        }

        public PendingCredits pending() {
            return pending;
        }
    }

    /** What the pod looks like to the payload this tick. */
    public record PodStatus(boolean landed, boolean empty, BlockPos pos) {
    }

    /** Everything a spawned pod needs to know. */
    public record PodSpec(UUID instanceId, double x, double y, double z, int tier, double descent, double driftX,
            double driftZ, Identifier lootTable, int lootRolls, long lootBudget, long seedBase) {
    }

    /** The world seam (production: {@link ServerPodWorld}; tests: a fake). */
    public interface PodWorld {
        boolean canSeeSky(BlockPos pos);

        /** Spawns the pod; null when the level refused it. */
        @Nullable UUID spawnPod(PodSpec spec);

        /** The pod if it is loaded; null when it is not (gone, or its chunk is unloaded). */
        @Nullable PodStatus pod(UUID uuid);

        boolean isLoaded(BlockPos pos);

        void discard(UUID uuid);
    }

    /** Maps a context to its world; returns null when there is no level (the payload then skips/ends). */
    private final Function<PayloadContext, PodWorld> worlds;

    /** Production: the running level behind {@link ServerPayloadContext}. */
    public SupplyDropPayload() {
        this(ctx -> {
            if (ctx instanceof ServerPayloadContext s) {
                ServerLevel level = s.level();
                return level == null ? null : new ServerPodWorld(level);
            }
            return null;
        });
    }

    /** Tests: a fake world. */
    public SupplyDropPayload(Function<PayloadContext, PodWorld> worlds) {
        this.worlds = worlds;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return Config.CODEC;
    }

    @Override
    public List<String> validate(Config config) {
        List<String> out = new ArrayList<>();
        if (config.tier() < 1 || config.tier() > 3) {
            out.add("tier " + config.tier() + " is outside 1..3; using 1");
        }
        if (config.podLootBudget() < 0L || config.podLootBudget() > 100_000L) {
            out.add("pod_loot_budget is outside 0..100000; clamped");
        }
        if (!NeroEventsCommon.MOD_ID.equals(config.lootTable().getNamespace())) {
            out.add("loot table outside the neroevents namespace is not validated at load");
        }
        return out;
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, 8, anchor.getY() - 64, anchor.getY() + config.podDropHeight() + 16);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        State state = new State();
        state.instanceId = instance.id();
        return state;
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        PodWorld world = worlds.apply(ctx);
        BlockPos anchor = ctx.anchor();
        if (world == null || anchor == null) {
            return StartResult.skip("no_room");
        }
        if (ctx instanceof ServerPayloadContext s) {
            EventEntityIndex.resetForServer(s.server());
        }
        if (!world.canSeeSky(anchor.above())) {
            return StartResult.skip("no_sky");
        }
        double theta = ctx.random().nextDouble() * Math.PI * 2.0D;
        state.driftX = Math.cos(theta) * config.driftBlocksPerTick();
        state.driftZ = Math.sin(theta) * config.driftBlocksPerTick();
        UUID instance = ctx.instance().id();
        UUID pod = world.spawnPod(new PodSpec(instance, anchor.getX() + 0.5D, anchor.getY() + config.podDropHeight(),
                anchor.getZ() + 0.5D, config.effectiveTier(), config.descentBlocksPerTick(), state.driftX, state.driftZ,
                config.lootTable(), config.lootRolls(), config.effectiveBudget(), ctx.instance().seed()));
        if (pod == null) {
            return StartResult.skip("no_room");
        }
        EventEntityIndex.bind(pod, instance);
        state.instanceId = instance;
        state.podUuid = pod;
        state.spawnedAt = ctx.now();
        state.lastKnown = anchor.above(config.podDropHeight());
        LIVE.put(instance, state);
        return StartResult.ok();
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext s) {
            state.pending.drainInto(s);
        }
        long now = ctx.now();
        PodWorld world = worlds.apply(ctx);
        UUID podId = state.podUuid;
        if (world == null || podId == null) {
            return TickResult.resolved(state.openCount > 0 ? EventOutcome.SUCCESS : EventOutcome.FAILURE);
        }
        PodStatus pod = world.pod(podId);
        if (pod == null) {
            BlockPos last = state.lastKnown;
            if (last != null && !world.isLoaded(last)) {
                return TickResult.proceed(); // the pod sits in an unloaded chunk: keep waiting for the timers
            }
            return TickResult.resolved(state.openCount > 0 ? EventOutcome.SUCCESS : EventOutcome.FAILURE);
        }
        state.lastKnown = pod.pos();
        if (pod.landed() && state.landedAt == 0L) {
            state.landedAt = Math.max(1L, now);
            ctx.progress(1, 2);
        }
        if (state.openCount > 0 && now >= state.firstOpenedAt + config.podLingerTicks()) {
            world.discard(podId);
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        if (state.openCount == 0 && state.landedAt > 0L && now >= state.landedAt + config.unopenedLingerTicks()) {
            world.discard(podId);
            return TickResult.resolved(EventOutcome.EXPIRED);
        }
        if (pod.landed() && pod.empty() && state.openCount > 0 && now >= state.firstOpenedAt + EMPTY_GRACE_TICKS) {
            world.discard(podId);
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        if ((now - state.spawnedAt) % 20L == 0L) {
            ctx.progress(pod.landed() ? 1 : 0, 2);
        }
        return TickResult.proceed();
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return state.openCount > 0 ? EventOutcome.SUCCESS : EventOutcome.EXPIRED;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        UUID podId = state.podUuid;
        if (podId != null) {
            PodWorld world = worlds.apply(ctx);
            if (world != null) {
                world.discard(podId);
            }
            EventEntityIndex.unbind(podId);
        }
        UUID instance = state.instanceId;
        if (instance != null) {
            LIVE.remove(instance);
            EventEntityIndex.clearInstance(instance);
        }
        state.pending.clear();
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        UUID pod = state.podUuid;
        if (pod != null) {
            tag.putString("pod", pod.toString());
        }
        tag.putLong("spawned_at", state.spawnedAt);
        tag.putLong("landed_at", state.landedAt);
        tag.putLong("first_opened_at", state.firstOpenedAt);
        tag.putInt("open_count", state.openCount);
        tag.putDouble("drift_x", state.driftX);
        tag.putDouble("drift_z", state.driftZ);
        BlockPos last = state.lastKnown;
        if (last != null) {
            tag.putLong("last_known", last.asLong());
        }
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State();
        state.instanceId = instance.id();
        String pod = tag.getStringOr("pod", "");
        if (!pod.isEmpty()) {
            state.podUuid = UUID.fromString(pod); // malformed ⇒ throws ⇒ resume_failed
        }
        state.spawnedAt = tag.getLongOr("spawned_at", 0L);
        state.landedAt = tag.getLongOr("landed_at", 0L);
        state.firstOpenedAt = tag.getLongOr("first_opened_at", 0L);
        state.openCount = Math.max(0, tag.getIntOr("open_count", 0));
        state.driftX = tag.getDoubleOr("drift_x", 0.0D);
        state.driftZ = tag.getDoubleOr("drift_z", 0.0D);
        if (tag.contains("last_known")) {
            state.lastKnown = BlockPos.of(tag.getLongOr("last_known", 0L));
        }
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext s) {
            EventEntityIndex.resetForServer(s.server());
        }
        UUID instance = ctx.instance().id();
        state.instanceId = instance;
        UUID pod = state.podUuid;
        if (pod == null) {
            return ResumeResult.refuse("resume_failed");
        }
        EventEntityIndex.bind(pod, instance);
        LIVE.put(instance, state);
        return ResumeResult.ok();
    }

    // --- hooks outside the tick ----------------------------------------------------------------------

    /** A player opened a pod (server thread). First opener claims the drop; later openers get a loot credit. */
    public static void onPodOpened(MinecraftServer server, UUID instanceId, UUID podUuid, ServerPlayer player) {
        onPodOpened(instanceId, podUuid, player.getUUID(), server.overworld().getGameTime());
    }

    /** The pure core of {@link #onPodOpened(MinecraftServer, UUID, UUID, ServerPlayer)}. No-op when not live. */
    public static void onPodOpened(UUID instanceId, UUID podUuid, UUID player, long now) {
        State state = LIVE.get(instanceId);
        if (state == null || player == null || !podUuid.equals(state.podUuid)) {
            return;
        }
        if (state.openCount == 0) {
            state.pending.supplyClaim(player, podUuid);
            state.firstOpenedAt = now;
        } else {
            state.pending.loot(player, "pod:" + podUuid);
        }
        state.openCount++;
    }

    /** Whether an instance is live on this server (the pod's hooks are no-ops otherwise). */
    public static boolean isLive(UUID instanceId) {
        return instanceId != null && LIVE.containsKey(instanceId);
    }

    /** Rolls a pod's loot and clamps it to the budget: the gate "a pod loot roll stays within budget". */
    public static <T> List<T> rollPod(LootRoller<T> roller, ToLongFunction<T> value, Identifier table, int rolls,
            long seed, long budget) {
        return LootBudget.clamp(roller.roll(table, rolls, seed), budget, value);
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
    }

    // --- production world ------------------------------------------------------------------------------

    /** The running level behind the seam. Never loads a chunk. */
    static final class ServerPodWorld implements PodWorld {
        private final ServerLevel level;

        ServerPodWorld(ServerLevel level) {
            this.level = level;
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return isLoaded(pos) && level.canSeeSky(pos);
        }

        @Override
        public @Nullable UUID spawnPod(PodSpec spec) {
            SupplyPodEntity pod = SupplyPodEntity.create(level, spec);
            if (pod == null || !level.addFreshEntity(pod)) {
                return null;
            }
            return pod.getUUID();
        }

        @Override
        public @Nullable PodStatus pod(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof SupplyPodEntity pod && !pod.isRemoved()) {
                return new PodStatus(pod.isLanded(), pod.contentsEmpty(), pod.blockPosition());
            }
            return null;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        }

        @Override
        public void discard(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof SupplyPodEntity pod) {
                pod.discard();
            }
        }
    }
}
