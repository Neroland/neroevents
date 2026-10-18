package za.co.neroland.neroevents.payload.meteor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventEngine;
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
import za.co.neroland.neroevents.event.world.ServerWorldView;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.payload.PayloadSounds;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.platform.Services;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * {@code neroevents:meteor_shower} — {@code meteor_count} meteors over the duration, spread over the bounds
 * disc (the first lands on the anchor column). Each impact is gated in order by the kill switches
 * ({@code destructiveEventsEnabled}, {@code meteorBlockDamage}, the definition's {@code soft_landing_only}),
 * the per-chunk and per-event crater caps, one fresh protection pre-check of the crater box, and — so a crater never
 * swallows a chest or machine —
 * any block entity within {@code craterBlockEntityMarginBlocks} (or an unloaded chunk) of the box; any of those
 * makes it a <b>soft landing</b> (one loot pocket on the surface). Then every cell is checked again by
 * {@link CraterBuilder}. Protection never makes a shower smaller, only gentler.
 *
 * <p>Mining a pocket credits LOOT once per pocket (queued, drained at the next tick). Rewards are a bonus for
 * miners and pay on {@code SUCCESS} only; the craters stay after the event as the reward surface.
 */
public final class MeteorShowerPayload implements PayloadType<MeteorShowerPayload.Config, MeteorShowerPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "meteor_shower");
    public static final Identifier DEFAULT_POCKET_LOOT = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID,
            "craters/pocket");
    public static final Identifier DEFAULT_ORE = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID,
            "embedded_meteor_ore");
    /** At most this many meteors spawn in one tick; the rest wait. */
    static final int MAX_SPAWNS_PER_TICK = 3;
    /** The impact sound's range in blocks (the flash goes to the clients tracking the impact chunk). */
    static final int FLASH_RANGE = 128;

    private static final Map<UUID, Live> LIVE = new ConcurrentHashMap<>();

    /** A live instance: its state, config and the payload type that owns it. */
    record Live(MeteorShowerPayload type, Config config, State state) {
    }

    /** The {@code payload} object (snake_case; every key optional). */
    public record Config(int meteorCount, double cadenceJitter, int spawnHeight, double fallBlocksPerTick,
            int craterRadiusMin, int craterRadiusMax, int maxCratersPerChunk, int maxCratersPerEvent, int orePerCrater,
            List<Identifier> oreBlocks, Identifier pocketLootTable, long pocketLootBudget, boolean softLandingOnly)
            implements PayloadConfig {

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.intRange(1, 120).optionalFieldOf("meteor_count", 24).forGetter(Config::meteorCount),
                Codec.doubleRange(0.0D, 0.5D).optionalFieldOf("cadence_jitter", 0.25D).forGetter(Config::cadenceJitter),
                Codec.intRange(32, 160).optionalFieldOf("spawn_height", 96).forGetter(Config::spawnHeight),
                Codec.doubleRange(0.8D, 3.0D).optionalFieldOf("fall_blocks_per_tick", 1.6D)
                        .forGetter(Config::fallBlocksPerTick),
                Codec.intRange(1, 4).optionalFieldOf("crater_radius_min", 2).forGetter(Config::craterRadiusMin),
                Codec.intRange(2, 4).optionalFieldOf("crater_radius_max", 4).forGetter(Config::craterRadiusMax),
                Codec.intRange(1, 8).optionalFieldOf("max_craters_per_chunk", 2).forGetter(Config::maxCratersPerChunk),
                Codec.intRange(1, 64).optionalFieldOf("max_craters_per_event", 16).forGetter(Config::maxCratersPerEvent),
                Codec.intRange(0, 6).optionalFieldOf("ore_per_crater", 2).forGetter(Config::orePerCrater),
                Identifier.CODEC.listOf().optionalFieldOf("ore_blocks", List.of(DEFAULT_ORE)).forGetter(Config::oreBlocks),
                Identifier.CODEC.optionalFieldOf("pocket_loot_table", DEFAULT_POCKET_LOOT)
                        .forGetter(Config::pocketLootTable),
                Codec.LONG.optionalFieldOf("pocket_loot_budget", 120L).forGetter(Config::pocketLootBudget),
                Codec.BOOL.optionalFieldOf("soft_landing_only", false).forGetter(Config::softLandingOnly)
        ).apply(inst, Config::new));

        public static final Config DEFAULT = new Config(24, 0.25D, 96, 1.6D, 2, 4, 2, 16, 2, List.of(DEFAULT_ORE),
                DEFAULT_POCKET_LOOT, 120L, false);

        public Config {
            oreBlocks = List.copyOf(oreBlocks);
        }

        public int radiusMin() {
            return craterRadiusMin <= craterRadiusMax ? craterRadiusMin : 2;
        }

        public int radiusMax() {
            return craterRadiusMin <= craterRadiusMax ? craterRadiusMax : 4;
        }

        public long effectiveBudget() {
            return Math.max(0L, Math.min(100_000L, pocketLootBudget));
        }

        /** Ticks a meteor takes to fall {@code spawn_height}. */
        public int fallTicks() {
            return (int) Math.ceil(spawnHeight / fallBlocksPerTick);
        }
    }

    /** Per-instance state. Saved: the schedule, counters, per-chunk crater counts, meteors in flight. */
    public static final class State implements PayloadState {
        @Nullable UUID instanceId;
        @Nullable Identifier dimension;
        long seed;
        long[] spawnAt = new long[0];
        int nextIndex;
        int impactsDone;
        int skipped;
        int craters;
        final Map<Long, Integer> perChunk = new HashMap<>();
        /** Index → meteor UUID, for meteors in flight. */
        final Map<Integer, UUID> inFlight = new LinkedHashMap<>();
        double bearing;
        List<Identifier> ores = List.of();
        final PendingCredits pending = new PendingCredits();

        public int impactsDone() {
            return impactsDone;
        }

        public int skipped() {
            return skipped;
        }

        public int craters() {
            return craters;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public long[] spawnAt() {
            return spawnAt.clone();
        }

        public Map<Integer, UUID> inFlight() {
            return Map.copyOf(inFlight);
        }

        public PendingCredits pending() {
            return pending;
        }

        int cratersInChunk(BlockPos pos) {
            return perChunk.getOrDefault(chunkKey(pos), 0);
        }
    }

    /** What the block-entity scan around a crater box found. */
    public enum BlockEntityScan { NONE, HIT, UNLOADED }

    /** What one impact did (tests). */
    public record ImpactOutcome(boolean soft, CraterBuilder.Result placed) {
    }

    /** The world seam (production: {@link ServerMeteorWorld}). */
    public interface MeteorWorld {
        /** Spawns a meteor; null when the level refused it. */
        @Nullable UUID spawnMeteor(UUID instance, int index, double x, double y, double z, double vx, double vy,
                double vz, int fallTicks);

        boolean meteorLoaded(UUID uuid);

        void discard(UUID uuid);

        /** Block entities in or near {@code box}: never loads a chunk; an unloaded chunk is {@code UNLOADED}. */
        BlockEntityScan scanBlockEntities(Footprint box);

        CraterBuilder.CraterWorld craterWorld(State state, Config config, long seed);

        /** The pure view the protection evaluation runs against. */
        WorldView view();

        /** Packet to nearby clients + the impact sound. */
        void impactFeedback(BlockPos pos, boolean soft, int severityOrdinal);
    }

    /** Resolves the world of a dimension (server null in tests). */
    @FunctionalInterface
    public interface Worlds {
        @Nullable MeteorWorld forDimension(@Nullable MinecraftServer server, Identifier dimension);
    }

    private final Worlds worlds;
    private final Supplier<EngineSettings> settings;
    private final ProtectionQuery protection;
    private final Predicate<Identifier> blockKnown;
    private final Predicate<Identifier> blockPlainSolid;

    /** Production. */
    public MeteorShowerPayload() {
        this(MeteorShowerPayload::serverWorld, MeteorShowerPayload::liveSettings, ProtectionQuery.live(),
                id -> BuiltInRegistries.BLOCK.containsKey(id), CraterBuilder::plainSolidBlock);
    }

    /** Tests: fake world, fixed settings, scripted protection, a block-registry predicate. */
    public MeteorShowerPayload(Worlds worlds, Supplier<EngineSettings> settings, ProtectionQuery protection,
            Predicate<Identifier> blockKnown) {
        this(worlds, settings, protection, blockKnown, id -> true);
    }

    /** Tests: as above, plus which ore blocks count as plain solid blocks. */
    public MeteorShowerPayload(Worlds worlds, Supplier<EngineSettings> settings, ProtectionQuery protection,
            Predicate<Identifier> blockKnown, Predicate<Identifier> blockPlainSolid) {
        this.worlds = worlds;
        this.settings = settings;
        this.protection = protection;
        this.blockKnown = blockKnown;
        this.blockPlainSolid = blockPlainSolid;
    }

    static EngineSettings liveSettings() {
        EventEngine engine = EventEngine.current();
        return engine == null ? EngineSettings.defaults() : engine.settings();
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
        if (config.craterRadiusMin() > config.craterRadiusMax()) {
            out.add("crater_radius_min > crater_radius_max; using 2 and 4");
        }
        for (Identifier ore : config.oreBlocks()) {
            if (!safeKnown(ore)) {
                out.add("ore_blocks entry " + ore + " is not a registered block; ignored");
            } else if (!safePlainSolid(ore)) {
                out.add("ore_blocks entry " + ore + " is not a plain solid block; ignored");
            }
        }
        if (config.pocketLootBudget() < 0L || config.pocketLootBudget() > 100_000L) {
            out.add("pocket_loot_budget is outside 0..100000; clamped");
        }
        return out;
    }

    private boolean safeKnown(Identifier id) {
        try {
            return blockKnown.test(id);
        } catch (RuntimeException | LinkageError e) {
            return true; // no registry here (plain JVM): checked again at start
        }
    }

    /** A solid, full block with no block entity and no fluid: the only kind a crater may lay as ore. */
    private boolean safePlainSolid(Identifier id) {
        try {
            return blockPlainSolid.test(id);
        } catch (RuntimeException | LinkageError e) {
            return true; // no registry here (plain JVM); the crater falls back to meteor ore anyway
        }
    }

    private boolean usableOre(Identifier id) {
        return safeKnown(id) && safePlainSolid(id);
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, 8, anchor.getY() - 8, anchor.getY() + config.spawnHeight());
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        State state = new State();
        state.instanceId = instance.id();
        state.dimension = instance.dimension();
        state.seed = instance.seed();
        return state;
    }

    private @Nullable MeteorWorld world(PayloadContext ctx) {
        return worlds.forDimension(ctx instanceof ServerPayloadContext s ? s.server() : null, ctx.dimension());
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        MeteorWorld world = world(ctx);
        if (world == null || ctx.anchor() == null) {
            return StartResult.skip("no_room");
        }
        if (ctx instanceof ServerPayloadContext s) {
            EventEntityIndex.resetForServer(s.server());
        }
        UUID instance = ctx.instance().id();
        state.instanceId = instance;
        state.dimension = ctx.dimension();
        state.seed = ctx.instance().seed();
        state.ores = config.oreBlocks().stream().filter(this::usableOre).toList();
        state.spawnAt = schedule(ctx.now(), ctx.instance().definition().durationTicks(), config, ctx.random());
        state.bearing = ctx.random().nextDouble() * Math.PI * 2.0D;
        LIVE.put(instance, new Live(this, config, state));
        return StartResult.ok();
    }

    /** Impact spawn times over the duration: the first at {@code start + 20}, evenly spaced with jitter, sorted. */
    static long[] schedule(long start, int durationTicks, Config config, RandomGenerator random) {
        int count = config.meteorCount();
        long span = Math.max(20L, durationTicks - config.fallTicks() - 40L);
        double step = (double) span / count;
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            double jitter = config.cadenceJitter() <= 0.0D ? 0.0D
                    : (random.nextDouble() * 2.0D - 1.0D) * config.cadenceJitter();
            long offset = Math.round(i * step * (1.0D + jitter));
            out[i] = start + 20L + Math.max(0L, Math.min(span, offset));
        }
        out[0] = start + 20L;
        Arrays.sort(out);
        return out;
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext s) {
            state.pending.drainInto(s);
        }
        MeteorWorld world = world(ctx);
        long now = ctx.now();
        int count = state.spawnAt.length;
        if (world != null) {
            int spawned = 0;
            while (state.nextIndex < count && now >= state.spawnAt[state.nextIndex] && spawned < MAX_SPAWNS_PER_TICK) {
                spawnMeteor(ctx, world, config, state, state.nextIndex);
                state.nextIndex++;
                spawned++;
            }
            // A meteor whose chunk unloaded mid-flight is counted as lost, so the shower can still finish.
            var it = state.inFlight.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (!world.meteorLoaded(entry.getValue())) {
                    EventEntityIndex.unbind(entry.getValue());
                    it.remove();
                    state.skipped++;
                }
            }
        }
        if ((now - (count > 0 ? state.spawnAt[0] : 0L)) % 20L == 0L) {
            ctx.progress(Math.min(count, state.impactsDone + state.skipped), count);
        }
        if (state.nextIndex >= count && state.impactsDone + state.skipped >= count && state.inFlight.isEmpty()) {
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        return TickResult.proceed();
    }

    private void spawnMeteor(PayloadContext ctx, MeteorWorld world, Config config, State state, int index) {
        BlockPos anchor = ctx.anchor();
        if (anchor == null) {
            state.skipped++;
            return;
        }
        int radius = ctx.instance().definition().bounds().radius();
        Identifier dim = ctx.dimension();
        RandomGenerator random = ctx.random();
        for (int attempt = 0; attempt < 5; attempt++) {
            int x = anchor.getX();
            int z = anchor.getZ();
            if (index > 0 || attempt > 0) {
                double r = radius * Math.sqrt(random.nextDouble());
                double phi = random.nextDouble() * Math.PI * 2.0D;
                x += (int) Math.round(r * Math.cos(phi));
                z += (int) Math.round(r * Math.sin(phi));
            }
            if (!ctx.world().isChunkLoaded(dim, x >> 4, z >> 4)) {
                continue;
            }
            OptionalInt surface = ctx.world().surfaceY(dim, x, z);
            if (surface.isEmpty()) {
                continue;
            }
            int fallTicks = config.fallTicks();
            double fall = config.fallBlocksPerTick();
            double dx = 0.35D * fall * Math.cos(state.bearing);
            double dz = 0.35D * fall * Math.sin(state.bearing);
            UUID instance = ctx.instance().id();
            UUID meteor = world.spawnMeteor(instance, index, x + 0.5D - dx * fallTicks,
                    surface.getAsInt() + config.spawnHeight(), z + 0.5D - dz * fallTicks, dx, -fall, dz, fallTicks);
            if (meteor == null) {
                break;
            }
            EventEntityIndex.bind(meteor, instance);
            state.inFlight.put(index, meteor);
            return;
        }
        state.skipped++;
    }

    /** One impact, under the four gates. Package-visible for the tests. */
    ImpactOutcome impact(MeteorWorld world, Config config, State state, int index, BlockPos pos) {
        UUID meteor = state.inFlight.remove(index);
        if (meteor != null) {
            EventEntityIndex.unbind(meteor);
        }
        Identifier dim = state.dimension == null ? Identifier.withDefaultNamespace("overworld") : state.dimension;
        EngineSettings s = settings.get();
        Random random = new Random(state.seed * 31L + index * 0x9E3779B97F4A7C15L);
        int min = config.radiusMin();
        int max = config.radiusMax();
        int r = min + random.nextInt(max - min + 1);
        Footprint craterBox = new Footprint(dim, pos.getX() - r - 1, pos.getY() - r, pos.getZ() - r - 1,
                pos.getX() + r + 1, pos.getY() + 2, pos.getZ() + r + 1);
        ProtectionEvaluation evaluation = protection.evaluation(world.view());
        boolean soft = config.softLandingOnly() || !s.meteorBlockDamage || !s.destructiveEventsEnabled
                || state.craters >= config.maxCratersPerEvent()
                || state.cratersInChunk(pos) >= config.maxCratersPerChunk()
                || !evaluation.mayAffectAll(craterBox).allowed()
                || (s.craterAvoidsBlockEntities
                        && world.scanBlockEntities(craterBox.grow(s.craterBlockEntityMarginBlocks)) != BlockEntityScan.NONE);
        CraterPlan plan = CraterPlan.of(pos, r, soft, config.orePerCrater(), state.ores.size(), random);
        CraterBuilder.Result placed = CraterBuilder.apply(world.craterWorld(state, config, state.seed ^ index), plan,
                evaluation, dim);
        world.impactFeedback(pos, soft, 2);
        state.impactsDone++;
        if (!soft && placed.craterBlocks() > 0) {
            state.craters++;
            state.perChunk.merge(chunkKey(pos), 1, Integer::sum);
        }
        if (NeroEventsCommon.LOGGER.isDebugEnabled()) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] meteor impact {} at {} soft={} cells={}", index, pos, soft,
                    placed.craterBlocks());
        }
        return new ImpactOutcome(soft, placed);
    }

    static long chunkKey(BlockPos pos) {
        return ((long) (pos.getX() >> 4) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return state.impactsDone > 0 ? EventOutcome.SUCCESS : EventOutcome.EXPIRED;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        MeteorWorld world = world(ctx);
        for (UUID meteor : state.inFlight.values()) {
            if (world != null) {
                world.discard(meteor);
            }
            EventEntityIndex.unbind(meteor);
        }
        state.inFlight.clear();
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
        tag.putLongArray("spawn_at", state.spawnAt.clone());
        tag.putInt("next_index", state.nextIndex);
        tag.putInt("impacts_done", state.impactsDone);
        tag.putInt("skipped", state.skipped);
        tag.putInt("craters", state.craters);
        long[] keys = new long[state.perChunk.size()];
        int[] counts = new int[state.perChunk.size()];
        int i = 0;
        for (Map.Entry<Long, Integer> e : state.perChunk.entrySet()) {
            keys[i] = e.getKey();
            counts[i] = e.getValue();
            i++;
        }
        tag.putLongArray("per_chunk_keys", keys);
        tag.putIntArray("per_chunk_counts", counts);
        StringBuilder flight = new StringBuilder();
        for (Map.Entry<Integer, UUID> e : state.inFlight.entrySet()) {
            if (flight.length() > 0) {
                flight.append(',');
            }
            flight.append(e.getKey()).append('=').append(e.getValue());
        }
        tag.putString("in_flight", flight.toString());
        tag.putDouble("bearing", state.bearing);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = createState(instance, config);
        state.spawnAt = tag.getLongArray("spawn_at").orElse(new long[0]).clone();
        state.nextIndex = Math.max(0, tag.getIntOr("next_index", 0));
        state.impactsDone = Math.max(0, tag.getIntOr("impacts_done", 0));
        state.skipped = Math.max(0, tag.getIntOr("skipped", 0));
        state.craters = Math.max(0, tag.getIntOr("craters", 0));
        long[] keys = tag.getLongArray("per_chunk_keys").orElse(new long[0]);
        int[] counts = tag.getIntArray("per_chunk_counts").orElse(new int[0]);
        for (int i = 0; i < Math.min(keys.length, counts.length); i++) {
            state.perChunk.put(keys[i], counts[i]);
        }
        String flight = tag.getStringOr("in_flight", "");
        if (!flight.isEmpty()) {
            for (String part : flight.split(",")) {
                int eq = part.indexOf('=');
                state.inFlight.put(Integer.parseInt(part.substring(0, eq)), UUID.fromString(part.substring(eq + 1)));
            }
        }
        state.bearing = tag.getDoubleOr("bearing", 0.0D);
        state.ores = config.oreBlocks().stream().filter(this::usableOre).toList();
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext s) {
            EventEntityIndex.resetForServer(s.server());
        }
        UUID instance = ctx.instance().id();
        state.instanceId = instance;
        MeteorWorld world = world(ctx);
        var it = state.inFlight.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (world == null || !world.meteorLoaded(entry.getValue())) {
                it.remove();
                state.skipped++;
            } else {
                EventEntityIndex.bind(entry.getValue(), instance);
            }
        }
        LIVE.put(instance, new Live(this, config, state));
        return ResumeResult.ok();
    }

    // --- hooks outside the tick ----------------------------------------------------------------------

    /** A meteor of {@code instanceId} hit {@code pos} (server thread). No-op once the event is over. */
    public static void onImpact(MinecraftServer server, UUID instanceId, int index, BlockPos pos) {
        Live live = LIVE.get(instanceId);
        if (live == null) {
            return;
        }
        Identifier dim = live.state().dimension;
        if (dim == null) {
            return;
        }
        try {
            MeteorWorld world = live.type().worlds.forDimension(server, dim);
            if (world != null) {
                live.type().impact(world, live.config(), live.state(), index, pos);
            }
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "payload:meteor_shower", "impact");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] meteor impact failed ({})", e.getClass().getName());
        }
    }

    /** A meteor flew out without touching anything: counted as skipped. */
    public static void onLost(UUID instanceId, int index, UUID meteor) {
        Live live = LIVE.get(instanceId);
        if (live != null && live.state().inFlight.remove(index) != null) {
            live.state().skipped++;
        }
        EventEntityIndex.unbind(meteor);
    }

    /** A player broke a pocket of {@code instanceId}: one LOOT credit per pocket, queued for the next tick. */
    public static void onPocketMined(MinecraftServer server, UUID instanceId, BlockPos pos, ServerPlayer player) {
        onPocketMined(instanceId, pos, player.getUUID());
    }

    /** The pure core of {@link #onPocketMined(MinecraftServer, UUID, BlockPos, ServerPlayer)}. */
    public static void onPocketMined(UUID instanceId, BlockPos pos, UUID player) {
        Live live = LIVE.get(instanceId);
        if (live != null) {
            live.state().pending.loot(player, "pocket:" + pos.asLong());
        }
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
    }

    // --- production world ------------------------------------------------------------------------------

    private static @Nullable MeteorWorld serverWorld(@Nullable MinecraftServer server, Identifier dimension) {
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        return level == null ? null : new ServerMeteorWorld(server, level);
    }

    /** The running level behind the seam. Never loads a chunk. */
    static final class ServerMeteorWorld implements MeteorWorld {
        private final MinecraftServer server;
        private final ServerLevel level;

        ServerMeteorWorld(MinecraftServer server, ServerLevel level) {
            this.server = server;
            this.level = level;
        }

        @Override
        public @Nullable UUID spawnMeteor(UUID instance, int index, double x, double y, double z, double vx,
                double vy, double vz, int fallTicks) {
            MeteorEntity meteor = MeteorEntity.create(level, instance, index, x, y, z, vx, vy, vz, fallTicks);
            return level.addFreshEntity(meteor) ? meteor.getUUID() : null;
        }

        @Override
        public boolean meteorLoaded(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            return entity instanceof MeteorEntity && !entity.isRemoved();
        }

        @Override
        public void discard(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof MeteorEntity meteor) {
                meteor.discard();
            }
        }

        @Override
        public BlockEntityScan scanBlockEntities(Footprint box) {
            for (Footprint.ChunkXZ c : box.chunkCoords()) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(c.x(), c.z());
                if (chunk == null) {
                    return BlockEntityScan.UNLOADED;
                }
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getY() >= box.minY()
                            && pos.getY() <= box.maxY() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()) {
                        return BlockEntityScan.HIT;
                    }
                }
            }
            return BlockEntityScan.NONE;
        }

        @Override
        public CraterBuilder.CraterWorld craterWorld(State state, Config config, long seed) {
            return new CraterBuilder.ServerCraterWorld(level, state.instanceId, config.pocketLootTable(),
                    config.effectiveBudget(), seed, state.ores);
        }

        @Override
        public WorldView view() {
            return new ServerWorldView(server);
        }

        @Override
        public void impactFeedback(BlockPos pos, boolean soft, int severityOrdinal) {
            PayloadSounds.playLayered(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    PayloadSounds.METEOR_IMPACT, FLASH_RANGE);
            ImpactFlashPayload packet = new ImpactFlashPayload(pos.immutable(), (byte) severityOrdinal, soft);
            try {
                // The players whose client tracks the impact chunk (who can see it) — not a position search.
                ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
                for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
                    Services.NETWORK.sendToPlayer(player, packet);
                }
            } catch (RuntimeException | LinkageError e) {
                NeroEventsCommon.LOGGER.debug("[NeroEvents] impact flash not sent ({})", e.getClass().getName());
            }
        }
    }
}
