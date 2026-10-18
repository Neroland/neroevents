package za.co.neroland.neroevents.payload.debris;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

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
import za.co.neroland.neroevents.payload.debris.WreckDecay.WreckRecord;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionEvaluation;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * {@code neroevents:debris_crash} — a hull fragment falls near the anchor and lays one of the wreck layouts
 * (variant and rotation from the instance seed). The wreck footprint is protection-checked at start
 * ({@code protected} skip) and every cell again as it is placed; salvage crates roll their loot on the first
 * open and credit each opener once per crate. The event resolves at the earlier of "every crate opened + 1200
 * ticks" and {@code salvage_window_ticks}; zero crates placed ⇒ {@code FAILURE} (nothing to salvage). Wreck
 * blocks stay and decay through {@link WreckDecay}.
 */
public final class DebrisCrashPayload implements PayloadType<DebrisCrashPayload.Config, DebrisCrashPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "debris_crash");
    public static final Identifier DEFAULT_SALVAGE = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID,
            "wrecks/salvage");
    public static final List<String> ALL_VARIANTS = List.of("wreck_pod_shell", "wreck_strut_field",
            "wreck_cargo_spill", "wreck_hull_rib");
    /** After every crate is opened the event stays this long (ticks). */
    static final long ALL_OPENED_GRACE_TICKS = 1200L;

    private static final Map<UUID, Live> LIVE = new ConcurrentHashMap<>();

    record Live(DebrisCrashPayload type, Config config, State state) {
    }

    /** The {@code payload} object (snake_case; every key optional; {@code variants} must not be empty). */
    public record Config(List<String> variants, int fallHeight, double fallBlocksPerTick, Identifier salvageLootTable,
            long salvageLootBudget, int salvageWindowTicks) implements PayloadConfig {

        private static final Codec<List<String>> VARIANTS = Codec.STRING.listOf().validate(list -> list.isEmpty()
                ? DataResult.error(() -> "variants must not be empty") : DataResult.success(list));

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                VARIANTS.optionalFieldOf("variants", ALL_VARIANTS).forGetter(Config::variants),
                Codec.intRange(24, 128).optionalFieldOf("fall_height", 64).forGetter(Config::fallHeight),
                Codec.doubleRange(0.8D, 3.0D).optionalFieldOf("fall_blocks_per_tick", 1.2D)
                        .forGetter(Config::fallBlocksPerTick),
                Identifier.CODEC.optionalFieldOf("salvage_loot_table", DEFAULT_SALVAGE)
                        .forGetter(Config::salvageLootTable),
                Codec.LONG.optionalFieldOf("salvage_loot_budget", 200L).forGetter(Config::salvageLootBudget),
                Codec.intRange(1200, 72000).optionalFieldOf("salvage_window_ticks", 12000)
                        .forGetter(Config::salvageWindowTicks)
        ).apply(inst, Config::new));

        public static final Config DEFAULT = new Config(ALL_VARIANTS, 64, 1.2D, DEFAULT_SALVAGE, 200L, 12000);

        public Config {
            variants = List.copyOf(variants);
        }

        /** The variants that are known (shipped or loaded), as ids. */
        public List<Identifier> knownVariants() {
            List<Identifier> out = new ArrayList<>();
            for (String name : variants) {
                Identifier id = WreckLayouts.idOf(name);
                if (WreckLayouts.known(id)) {
                    out.add(id);
                }
            }
            return out;
        }

        public long effectiveBudget() {
            return Math.max(0L, Math.min(100_000L, salvageLootBudget));
        }

        public int fallTicks() {
            return (int) Math.ceil(fallHeight / fallBlocksPerTick);
        }
    }

    /** Per-instance state. Saved: the debris UUID, variant, rotation, timers and our own block positions. */
    public static final class State implements PayloadState {
        @Nullable UUID instanceId;
        @Nullable Identifier dimension;
        @Nullable BlockPos anchor;
        long seed;
        @Nullable Identifier variant;
        Rotation rotation = Rotation.NONE;
        @Nullable UUID debrisUuid;
        @Nullable BlockPos lastKnown;
        long placedAt;
        long allOpenedAt;
        boolean failed;
        final List<BlockPos> crates = new ArrayList<>();
        /** Crates seen opened (sticky: an emptied crate broken afterwards still counts). */
        final java.util.Set<BlockPos> opened = new java.util.HashSet<>();
        final List<BlockPos> cells = new ArrayList<>();
        final PendingCredits pending = new PendingCredits();

        public @Nullable Identifier variant() {
            return variant;
        }

        public Rotation rotation() {
            return rotation;
        }

        public long placedAt() {
            return placedAt;
        }

        public boolean failed() {
            return failed;
        }

        public List<BlockPos> crates() {
            return List.copyOf(crates);
        }

        public List<BlockPos> cells() {
            return List.copyOf(cells);
        }

        public PendingCredits pending() {
            return pending;
        }
    }

    /** The world seam (production: {@link ServerDebrisWorld}). */
    public interface DebrisWorld {
        @Nullable WreckLayout layout(Identifier variant);

        @Nullable UUID spawnDebris(UUID instance, double x, double y, double z, double vx, double vy, double vz,
                int fallTicks);

        /** The debris position if it is loaded; null otherwise. */
        @Nullable BlockPos debrisPos(UUID uuid);

        boolean isLoaded(BlockPos pos);

        void discard(UUID uuid);

        /** The top solid block's y at a column; empty when the chunk is not loaded. */
        OptionalInt groundY(int x, int z);

        WreckPlacer.WreckWorld wreckWorld(State state, Config config);

        WorldView view();

        /** Whether the crate at {@code pos} has been opened (its loot rolled). */
        boolean crateOpened(BlockPos pos);

        void recordWreck(WreckRecord record, int max);

        void landingFeedback(BlockPos pos);
    }

    /** Resolves the world of a dimension (server null in tests). */
    @FunctionalInterface
    public interface Worlds {
        @Nullable DebrisWorld forDimension(@Nullable MinecraftServer server, Identifier dimension);
    }

    private final Worlds worlds;
    private final Supplier<EngineSettings> settings;
    private final ProtectionQuery protection;
    private final LongSupplier epochClock;

    /** Production. */
    public DebrisCrashPayload() {
        this(DebrisCrashPayload::serverWorld, DebrisCrashPayload::liveSettings, ProtectionQuery.live(),
                System::currentTimeMillis);
    }

    /** Tests. */
    public DebrisCrashPayload(Worlds worlds, Supplier<EngineSettings> settings, ProtectionQuery protection,
            LongSupplier epochClock) {
        this.worlds = worlds;
        this.settings = settings;
        this.protection = protection;
        this.epochClock = epochClock;
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
        for (String name : config.variants()) {
            if (!WreckLayouts.known(WreckLayouts.idOf(name))) {
                out.add("variants entry " + name + " is not a known wreck layout; ignored");
            }
        }
        if (config.knownVariants().isEmpty()) {
            // Thrown so the loader drops the definition (no variant left to place).
            throw new IllegalArgumentException("no known wreck variant");
        }
        if (config.salvageLootBudget() < 0L || config.salvageLootBudget() > 100_000L) {
            out.add("salvage_loot_budget is outside 0..100000; clamped");
        }
        return out;
    }

    /** The largest selected variant's box around the anchor, grown by 1 (rotation unknown: max(x, z) both ways). */
    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        int horizontal = 1;
        int sy = 1;
        for (Identifier id : config.knownVariants()) {
            WreckLayout layout = WreckLayouts.cached(id).orElse(null);
            int sx = layout == null ? WreckLayouts.MAX_X : layout.sizeX();
            int sz = layout == null ? WreckLayouts.MAX_Z : layout.sizeZ();
            horizontal = Math.max(horizontal, Math.max(sx, sz));
            sy = Math.max(sy, layout == null ? WreckLayouts.MAX_Y : layout.sizeY());
        }
        return box(dimension, anchor, horizontal, sy, horizontal);
    }

    static Footprint box(Identifier dimension, BlockPos anchor, int sx, int sy, int sz) {
        int hx = (sx + 1) / 2;
        int hz = (sz + 1) / 2;
        return new Footprint(dimension, anchor.getX() - hx - 1, anchor.getY() - 2, anchor.getZ() - hz - 1,
                anchor.getX() + hx + 1, anchor.getY() + sy + 1, anchor.getZ() + hz + 1);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        State state = new State();
        state.instanceId = instance.id();
        state.dimension = instance.dimension();
        state.anchor = instance.anchor();
        state.seed = instance.seed();
        List<Identifier> variants = config.knownVariants();
        if (!variants.isEmpty()) {
            state.variant = variants.get(instance.random().nextInt(variants.size()));
        }
        state.rotation = Rotation.values()[instance.random().nextInt(Rotation.values().length)];
        return state;
    }

    private @Nullable DebrisWorld world(PayloadContext ctx) {
        return worlds.forDimension(ctx instanceof ServerPayloadContext s ? s.server() : null, ctx.dimension());
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        DebrisWorld world = world(ctx);
        BlockPos anchor = ctx.anchor();
        Identifier variant = state.variant;
        if (world == null || anchor == null || variant == null) {
            return StartResult.skip("no_room");
        }
        if (ctx instanceof ServerPayloadContext s) {
            EventEntityIndex.resetForServer(s.server());
        }
        WreckLayout layout = world.layout(variant);
        if (layout == null) {
            return StartResult.skip("no_room");
        }
        WreckLayout rotated = layout.rotate(state.rotation);
        if (!ctx.protection().mayAffectAll(box(ctx.dimension(), anchor, rotated.sizeX(), rotated.sizeY(),
                rotated.sizeZ())).allowed()) {
            return StartResult.skip("protected");
        }
        double theta = ctx.random().nextDouble() * Math.PI * 2.0D;
        UUID instance = ctx.instance().id();
        UUID debris = world.spawnDebris(instance, anchor.getX() + 0.5D, anchor.getY() + config.fallHeight(),
                anchor.getZ() + 0.5D, 0.2D * Math.cos(theta), -config.fallBlocksPerTick(), 0.2D * Math.sin(theta),
                config.fallTicks());
        if (debris == null) {
            return StartResult.skip("no_room");
        }
        EventEntityIndex.bind(debris, instance);
        state.instanceId = instance;
        state.dimension = ctx.dimension();
        state.anchor = anchor;
        state.debrisUuid = debris;
        state.lastKnown = anchor.above(config.fallHeight());
        LIVE.put(instance, new Live(this, config, state));
        return StartResult.ok();
    }

    /** The debris landed at {@code pos}: lay the wreck. Package-visible for the tests. */
    WreckPlacer.Result land(DebrisWorld world, Config config, State state, BlockPos pos, long now) {
        UUID debris = state.debrisUuid;
        if (debris != null) {
            EventEntityIndex.unbind(debris);
        }
        Identifier variant = state.variant;
        Identifier dim = state.dimension;
        BlockPos anchor = state.anchor;
        WreckLayout layout = variant == null ? null : world.layout(variant);
        if (layout == null || dim == null || anchor == null || variant == null) {
            state.failed = true;
            return new WreckPlacer.Result(List.of(), List.of());
        }
        WreckLayout rotated = layout.rotate(state.rotation);
        Footprint prechecked = box(dim, anchor, rotated.sizeX(), rotated.sizeY(), rotated.sizeZ());
        OptionalInt landingGround = world.groundY(pos.getX(), pos.getZ());
        OptionalInt anchorGround = world.groundY(anchor.getX(), anchor.getZ());
        BlockPos origin = WreckPlacer.origin(pos, landingGround.orElse(pos.getY() - 1), anchor,
                anchorGround.orElse(anchor.getY() - 1), rotated, prechecked);
        ProtectionEvaluation evaluation = protection.evaluation(world.view());
        WreckPlacer.Result result = WreckPlacer.place(world.wreckWorld(state, config), rotated, origin, evaluation, dim);
        world.landingFeedback(pos);
        state.placedAt = Math.max(1L, now);
        state.crates.clear();
        state.crates.addAll(result.crates());
        state.cells.clear();
        state.cells.addAll(result.cells());
        UUID instance = state.instanceId;
        if (!result.cells().isEmpty() && instance != null) {
            world.recordWreck(new WreckRecord(instance, dim, origin, variant, state.rotation, epochClock.getAsLong(), 0,
                    result.cells()), settings.get().wreckRecordsMax);
        }
        if (result.crates().isEmpty()) {
            state.failed = true;
        }
        if (NeroEventsCommon.LOGGER.isDebugEnabled()) {
            NeroEventsCommon.LOGGER.debug("[NeroEvents] wreck {} placed at {}: {} cell(s), {} crate(s)", variant, origin,
                    result.cells().size(), result.crates().size());
        }
        return result;
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext s) {
            state.pending.drainInto(s);
        }
        if (state.failed) {
            return TickResult.resolved(EventOutcome.FAILURE);
        }
        DebrisWorld world = world(ctx);
        long now = ctx.now();
        if (state.placedAt == 0L) {
            UUID debris = state.debrisUuid;
            if (world == null || debris == null) {
                return TickResult.resolved(EventOutcome.FAILURE);
            }
            BlockPos pos = world.debrisPos(debris);
            if (pos == null) {
                BlockPos last = state.lastKnown;
                return last != null && !world.isLoaded(last) ? TickResult.proceed()
                        : TickResult.resolved(EventOutcome.FAILURE);
            }
            state.lastKnown = pos;
            return TickResult.proceed();
        }
        if (world != null) {
            for (BlockPos crate : state.crates) {
                if (!state.opened.contains(crate) && world.crateOpened(crate)) {
                    state.opened.add(crate);
                }
            }
        }
        int opened = state.opened.size();
        if ((now - state.placedAt) % 20L == 0L) {
            ctx.progress(opened, state.crates.size());
        }
        if (opened == state.crates.size() && state.allOpenedAt == 0L) {
            state.allOpenedAt = Math.max(1L, now);
        }
        if (state.allOpenedAt > 0L && now >= state.allOpenedAt + ALL_OPENED_GRACE_TICKS) {
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        if (now >= state.placedAt + config.salvageWindowTicks()) {
            return TickResult.resolved(opened > 0 ? EventOutcome.SUCCESS : EventOutcome.EXPIRED);
        }
        return TickResult.proceed();
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        DebrisWorld world = world(ctx);
        if (world != null) {
            for (BlockPos crate : state.crates) {
                if (world.crateOpened(crate)) {
                    state.opened.add(crate);
                }
            }
        }
        return state.opened.isEmpty() ? EventOutcome.EXPIRED : EventOutcome.SUCCESS;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        UUID debris = state.debrisUuid;
        if (debris != null) {
            DebrisWorld world = world(ctx);
            if (world != null && state.placedAt == 0L) {
                world.discard(debris);
            }
            EventEntityIndex.unbind(debris);
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
        UUID debris = state.debrisUuid;
        if (debris != null) {
            tag.putString("debris", debris.toString());
        }
        Identifier variant = state.variant;
        if (variant != null) {
            tag.putString("variant", variant.toString());
        }
        tag.putString("rotation", state.rotation.name());
        tag.putLong("placed_at", state.placedAt);
        tag.putLong("all_opened_at", state.allOpenedAt);
        tag.putBoolean("failed", state.failed);
        tag.putLongArray("crates", state.crates.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("cells", state.cells.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("opened", state.opened.stream().mapToLong(BlockPos::asLong).toArray());
        BlockPos last = state.lastKnown;
        if (last != null) {
            tag.putLong("last_known", last.asLong());
        }
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = createState(instance, config);
        String debris = tag.getStringOr("debris", "");
        state.debrisUuid = debris.isEmpty() ? null : UUID.fromString(debris);
        String variant = tag.getStringOr("variant", "");
        if (!variant.isEmpty()) {
            state.variant = Identifier.parse(variant);
        }
        state.rotation = Rotation.valueOf(tag.getStringOr("rotation", Rotation.NONE.name()));
        state.placedAt = tag.getLongOr("placed_at", 0L);
        state.allOpenedAt = tag.getLongOr("all_opened_at", 0L);
        state.failed = tag.getBooleanOr("failed", false);
        for (long packed : tag.getLongArray("crates").orElse(new long[0])) {
            state.crates.add(BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("cells").orElse(new long[0])) {
            state.cells.add(BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("opened").orElse(new long[0])) {
            state.opened.add(BlockPos.of(packed));
        }
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
        UUID debris = state.debrisUuid;
        if (debris != null && state.placedAt == 0L) {
            EventEntityIndex.bind(debris, instance);
        }
        LIVE.put(instance, new Live(this, config, state));
        return ResumeResult.ok();
    }

    // --- hooks outside the tick ----------------------------------------------------------------------

    /** The debris of {@code instanceId} hit the ground at {@code pos} (server thread). */
    public static void onLanded(MinecraftServer server, UUID instanceId, BlockPos pos) {
        Live live = LIVE.get(instanceId);
        Identifier dim = live == null ? null : live.state().dimension;
        if (live == null || dim == null || live.state().placedAt != 0L) {
            return;
        }
        try {
            DebrisWorld world = live.type().worlds.forDimension(server, dim);
            if (world != null) {
                live.type().land(world, live.config(), live.state(), pos, server.overworld().getGameTime());
            }
        } catch (RuntimeException | LinkageError e) {
            live.state().failed = true;
            NeroEventsTelemetry.captureHandledException(e, "payload:debris_crash", "place");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] wreck placement failed ({})", e.getClass().getName());
        }
    }

    /** The debris was lost (fell out of the world): the instance fails. */
    public static void onLost(UUID instanceId) {
        Live live = LIVE.get(instanceId);
        if (live != null && live.state().placedAt == 0L) {
            live.state().failed = true;
        }
    }

    /** A player opened a crate of {@code instanceId}: one LOOT credit per crate per player, queued. */
    public static void onCrateOpened(MinecraftServer server, UUID instanceId, BlockPos pos, ServerPlayer player) {
        onCrateOpened(instanceId, pos, player.getUUID());
    }

    /** The pure core of {@link #onCrateOpened(MinecraftServer, UUID, BlockPos, ServerPlayer)}. */
    public static void onCrateOpened(UUID instanceId, BlockPos pos, UUID player) {
        Live live = LIVE.get(instanceId);
        if (live != null) {
            live.state().pending.loot(player, "crate:" + pos.asLong());
            if (live.state().crates.contains(pos)) {
                live.state().opened.add(pos.immutable());
            }
        }
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
    }

    // --- production world ------------------------------------------------------------------------------

    private static @Nullable DebrisWorld serverWorld(@Nullable MinecraftServer server, Identifier dimension) {
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        return level == null ? null : new ServerDebrisWorld(server, level);
    }

    /** The running level behind the seam. Never loads a chunk. */
    static final class ServerDebrisWorld implements DebrisWorld {
        private final MinecraftServer server;
        private final ServerLevel level;

        ServerDebrisWorld(MinecraftServer server, ServerLevel level) {
            this.server = server;
            this.level = level;
        }

        @Override
        public @Nullable WreckLayout layout(Identifier variant) {
            return WreckLayouts.get(server, variant).orElse(null);
        }

        @Override
        public @Nullable UUID spawnDebris(UUID instance, double x, double y, double z, double vx, double vy, double vz,
                int fallTicks) {
            DebrisEntity debris = DebrisEntity.create(level, instance, x, y, z, vx, vy, vz, fallTicks);
            return level.addFreshEntity(debris) ? debris.getUUID() : null;
        }

        @Override
        public @Nullable BlockPos debrisPos(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            return entity instanceof DebrisEntity && !entity.isRemoved() ? entity.blockPosition() : null;
        }

        @Override
        public boolean isLoaded(BlockPos pos) {
            return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        }

        @Override
        public void discard(UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof DebrisEntity debris) {
                debris.discard();
            }
        }

        @Override
        public OptionalInt groundY(int x, int z) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) - 1);
        }

        @Override
        public WreckPlacer.WreckWorld wreckWorld(State state, Config config) {
            return new WreckPlacer.ServerWreckWorld(level, state.instanceId, config.salvageLootTable(),
                    config.effectiveBudget(), state.seed);
        }

        @Override
        public WorldView view() {
            return new ServerWorldView(server);
        }

        @Override
        public boolean crateOpened(BlockPos pos) {
            if (!isLoaded(pos)) {
                return false;
            }
            return level.getBlockEntity(pos) instanceof SalvageCrateBlockEntity crate && crate.lootRolled();
        }

        @Override
        public void recordWreck(WreckRecord record, int max) {
            WreckState.get(server).add(record, max);
        }

        @Override
        public void landingFeedback(BlockPos pos) {
            PayloadSounds.playLayered(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    PayloadSounds.DEBRIS_IMPACT, 96.0F);
            try {
                // Ground dust where it came down (no flash: a hazard, but not fire).
                net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(pos.below());
                if (!ground.isAir()) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                            pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D,
                            za.co.neroland.neroevents.fx.TrailBudget.DEBRIS_DUST, 0.6D, 0.2D, 0.6D, 0.15D);
                }
            } catch (RuntimeException | LinkageError e) {
                // Dust is decoration: never let it break the landing.
            }
        }
    }
}
