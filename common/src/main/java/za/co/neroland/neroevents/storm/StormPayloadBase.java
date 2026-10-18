package za.co.neroland.neroevents.storm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EngineSettings;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * Shared mechanics of the two storm types ({@code neroevents:solar_storm}, {@code neroevents:planetary_storm}).
 * A storm is NeroEvents' weather: dimension-wide, one per dimension, no block effects and no rewards.
 *
 * <ul>
 *   <li><b>Start:</b> the subclass resolves the variant; another storm in the dimension skips with
 *       {@code storm_active}. The storm is recorded in {@link ActiveStorms}, the {@code neroevents:storm}
 *       channel rises, and every player in the dimension gets a {@link StormSyncPayload}.</li>
 *   <li><b>Every 40 ticks</b> (offset by the instance seed so two dimensions do not align): each player
 *       in the dimension is judged by {@link StormExposure}; a change sends a {@link StormExposurePayload};
 *       exposed players get the variant's effects ({@link StormEffects}). A player not yet synced (entered
 *       mid-storm) gets a {@link StormSyncPayload}; every 200 ticks the sync is refreshed for everyone.</li>
 *   <li><b>Every 200 ticks</b> (draining variants only): one {@link StormEnergyDrain} pass.</li>
 *   <li><b>Timeout:</b> {@code ENDED}. Storms credit nobody, so nothing is paid.</li>
 *   <li><b>Cleanup</b> (idempotent): {@link ActiveStorms} cleared, the channel falls, every player in the
 *       dimension gets the "none" sync, the in-memory exposure map is dropped.</li>
 * </ul>
 *
 * <p>Saved: variant, intensity, start time, wind yaw and the two phase offsets. The exposure map (player
 * UUIDs) is in memory only and never saved.
 */
public abstract class StormPayloadBase implements PayloadType<StormPayloadBase.Config, StormPayloadBase.State> {

    /** Core's space dimension-type tag. */
    public static final Identifier SPACE_DIMENSIONS = Identifier.fromNamespaceAndPath("neroland", "space/dimensions");

    public static final int SWEEP_TICKS = StormVariant.EFFECT_INTERVAL_TICKS;
    public static final int DRAIN_TICKS = 200;
    public static final int REFRESH_TICKS = 200;
    public static final int PROGRESS_TICKS = 200;

    /** Live storm states by instance id, for {@link #forget}. */
    static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();
    private static final Set<Identifier> WARNED_NO_VARIANT = ConcurrentHashMap.newKeySet();

    /** The {@code payload} object of a storm definition; every key optional. */
    public record Config(int intensity, int rampTicks, String variant, boolean effectsEnabled, boolean drainEnabled,
            boolean agricultureEnabled, Optional<Integer> windYaw) implements PayloadConfig {

        public static final String AUTO = "auto";

        private static final Codec<String> VARIANT = Codec.STRING.comapFlatMap(StormPayloadBase.Config::checkVariant,
                s -> s);

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.intRange(1, 3).optionalFieldOf("intensity", 2).forGetter(Config::intensity),
                Codec.intRange(100, 2400).optionalFieldOf("ramp_ticks", 600).forGetter(Config::rampTicks),
                VARIANT.optionalFieldOf("variant", AUTO).forGetter(Config::variant),
                Codec.BOOL.optionalFieldOf("effects_enabled", true).forGetter(Config::effectsEnabled),
                Codec.BOOL.optionalFieldOf("drain_enabled", true).forGetter(Config::drainEnabled),
                Codec.BOOL.optionalFieldOf("agriculture_enabled", true).forGetter(Config::agricultureEnabled),
                Codec.intRange(0, 359).optionalFieldOf("wind_yaw").forGetter(Config::windYaw)
        ).apply(inst, Config::new));

        /** The shipped defaults (an empty {@code payload} object). */
        public static Config defaults() {
            return new Config(2, 600, AUTO, true, true, true, Optional.empty());
        }

        private static DataResult<String> checkVariant(String raw) {
            String lower = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (AUTO.equals(lower) || StormVariant.byName(lower).isPresent()) {
                return DataResult.success(lower);
            }
            return DataResult.error(() -> "unknown storm variant " + raw + " (auto, solar, dust, ion, cryo)");
        }
    }

    /** Per-instance state. Only the variant, intensity, times, wind and phases are saved. */
    public static final class State implements PayloadState {
        @Nullable StormVariant variant;
        int intensity;
        long startedAt;
        int windYaw;
        int phase40;
        int phase200;
        long lastProgress;
        long lastRefresh;
        boolean begun;
        /** player UUID → last exposure sent; in memory only, cleared at cleanup, never saved. */
        final Map<UUID, Boolean> exposure = new HashMap<>();

        public @Nullable StormVariant variant() {
            return variant;
        }

        public int intensity() {
            return intensity;
        }

        public int windYaw() {
            return windYaw;
        }

        /** Whether the in-memory exposure map still names the player (erasure probe). */
        boolean holdsExposure(UUID player) {
            return player != null && exposure.containsKey(player);
        }

        /** How many players the in-memory exposure map holds (tests). */
        public int trackedPlayers() {
            return exposure.size();
        }
    }

    protected final StormOps ops;

    protected StormPayloadBase(StormOps ops) {
        this.ops = ops;
    }

    /** The variant this instance runs, or the skip reason. */
    protected record Resolution(@Nullable StormVariant variant, @Nullable String skipReason) {
        static Resolution of(StormVariant variant) {
            return new Resolution(variant, null);
        }

        static Resolution skip(String reason) {
            return new Resolution(null, reason);
        }
    }

    protected abstract Resolution resolveVariant(PayloadContext ctx, Config config);

    @Override
    public MapCodec<Config> configCodec() {
        return Config.CODEC;
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        // Storms are dimension-wide; nothing is placed, so the footprint is a point (the drain asks protection
        // per storage instead).
        return anchor == null ? Footprint.dimension(dimension) : Footprint.point(dimension, anchor);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        State state = new State();
        state.intensity = StormVariant.clampIntensity(config.intensity());
        long seed = instance.seed();
        state.phase40 = (int) Math.floorMod(seed, (long) SWEEP_TICKS);
        state.phase200 = (int) Math.floorMod(seed, (long) DRAIN_TICKS);
        state.windYaw = config.windYaw().orElse((int) Math.floorMod(seed >>> 16, 360L));
        return state;
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        Resolution resolution = resolveVariant(ctx, config);
        if (resolution.variant() == null) {
            String reason = resolution.skipReason() == null ? "no_storm_variant" : resolution.skipReason();
            if ("no_storm_variant".equals(reason) && WARNED_NO_VARIANT.add(ctx.dimension())) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] {} has no storm variant for dimension {}; a planetary "
                        + "storm can only run on Greenxertz, Cindara or Glacira", ctx.instance().definition().id(),
                        ctx.dimension());
            }
            return StartResult.skip(reason);
        }
        Optional<ActiveStorms.StormStatus> held = ActiveStorms.active(ctx.dimension());
        if (held.isPresent() && !held.get().instanceId().equals(ctx.instance().id())) {
            return StartResult.skip("storm_active");
        }
        state.variant = resolution.variant();
        state.startedAt = ctx.now();
        state.lastProgress = ctx.now();
        if (!begin(ctx, config, state)) {
            return StartResult.skip("storm_active");
        }
        NeroEventsCommon.LOGGER.info("[NeroEvents] {} storm started in {} ({} intensity {})",
                state.variant.serializedName(), ctx.dimension(), ctx.instance().shortId(), state.intensity);
        return StartResult.ok();
    }

    /** Records the storm, raises the channel and syncs every player in the dimension. */
    private boolean begin(PayloadContext ctx, Config config, State state) {
        StormVariant variant = state.variant;
        if (variant == null || !ActiveStorms.set(ctx.dimension(), new ActiveStorms.StormStatus(variant,
                state.intensity, ctx.instance().id(), config.agricultureEnabled()))) {
            return false;
        }
        state.begun = true;
        state.exposure.clear();
        LIVE.put(ctx.instance().id(), state);
        StormChannel.start(ctx.dimension(), state.intensity);
        StormSyncPayload sync = sync(ctx, config, state);
        for (UUID player : ops.players(ctx)) {
            ops.send(ctx, player, sync);
        }
        state.lastRefresh = ctx.now();
        return true;
    }

    private static StormSyncPayload sync(PayloadContext ctx, Config config, State state) {
        return StormSyncPayload.active(ctx.dimension(), state.variant, state.intensity, config.rampTicks(),
                state.windYaw);
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        StormVariant variant = state.variant;
        if (variant == null || !state.begun) {
            return TickResult.proceed();
        }
        long now = ctx.now();
        if (Math.floorMod(now, (long) SWEEP_TICKS) == state.phase40) {
            sweep(ctx, config, state, variant, now);
        }
        if (variant.drains() && config.drainEnabled() && Math.floorMod(now, (long) DRAIN_TICKS) == state.phase200) {
            try {
                int drained = ops.drain(ctx, variant, state.intensity);
                if (drained > 0) {
                    NeroEventsCommon.LOGGER.debug("[NeroEvents] storm {} drained {} storage(s)",
                            ctx.instance().shortId(), drained);
                }
            } catch (RuntimeException | LinkageError e) {
                NeroEventsCommon.LOGGER.debug("[NeroEvents] storm drain pass failed", e);
                NeroEventsTelemetry.captureHandledException(e, "payload:storm", "drain");
            }
        }
        if (now - state.lastProgress >= PROGRESS_TICKS) {
            state.lastProgress = now;
            int total = Math.max(1, ctx.instance().definition().durationTicks() / PROGRESS_TICKS);
            int done = (int) Math.min(total, Math.max(0L, (now - state.startedAt) / PROGRESS_TICKS));
            ctx.progress(done, total);
        }
        return TickResult.proceed();
    }

    private void sweep(PayloadContext ctx, Config config, State state, StormVariant variant, long now) {
        EngineSettings settings = ops.settings();
        boolean effects = config.effectsEnabled() && settings.stormEffectsEnabled;
        boolean refresh = now - state.lastRefresh >= REFRESH_TICKS;
        if (refresh) {
            state.lastRefresh = now;
        }
        StormSyncPayload sync = null;
        List<UUID> players = ops.players(ctx);
        Set<UUID> present = new HashSet<>(players);
        state.exposure.keySet().retainAll(present);
        for (UUID player : players) {
            Boolean last = state.exposure.get(player);
            if (last == null || refresh) {
                if (sync == null) {
                    sync = sync(ctx, config, state);
                }
                ops.send(ctx, player, sync);
            }
            boolean exposed = ops.exposed(ctx, player);
            if (last == null || last != exposed) {
                ops.send(ctx, player, new StormExposurePayload(exposed));
            }
            state.exposure.put(player, exposed);
            if (exposed && effects) {
                ops.applyEffects(ctx, player, variant, state.intensity);
            }
        }
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return EventOutcome.ENDED;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        LIVE.remove(ctx.instance().id());
        if (!state.begun) {
            state.exposure.clear();
            return;
        }
        state.begun = false;
        ActiveStorms.clear(ctx.dimension(), ctx.instance().id());
        StormChannel.end(ctx.dimension());
        StormSyncPayload none = StormSyncPayload.none(ctx.dimension());
        Set<UUID> told = new HashSet<>();
        for (UUID player : ops.players(ctx)) {
            if (told.add(player)) {
                ops.send(ctx, player, none);
            }
        }
        state.exposure.clear();
        NeroEventsCommon.LOGGER.info("[NeroEvents] storm ended in {} ({})", ctx.dimension(),
                ctx.instance().shortId());
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        if (state.variant != null) {
            tag.putString("variant", state.variant.serializedName());
        }
        tag.putInt("intensity", state.intensity);
        tag.putLong("startedAt", state.startedAt);
        tag.putInt("windYaw", state.windYaw);
        tag.putInt("phase40", state.phase40);
        tag.putInt("phase200", state.phase200);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = createState(instance, config);
        String name = tag.getStringOr("variant", "");
        if (!name.isEmpty()) {
            state.variant = StormVariant.byName(name)
                    .orElseThrow(() -> new IllegalArgumentException("unknown saved storm variant " + name));
        }
        state.intensity = StormVariant.clampIntensity(tag.getIntOr("intensity", state.intensity));
        state.startedAt = tag.getLongOr("startedAt", 0L);
        state.windYaw = Math.floorMod(tag.getIntOr("windYaw", state.windYaw), 360);
        state.phase40 = Math.floorMod(tag.getIntOr("phase40", state.phase40), SWEEP_TICKS);
        state.phase200 = Math.floorMod(tag.getIntOr("phase200", state.phase200), DRAIN_TICKS);
        state.lastProgress = state.startedAt;
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        if (state.variant == null) {
            Resolution resolution = resolveVariant(ctx, config);
            if (resolution.variant() == null) {
                return ResumeResult.refuse(resolution.skipReason() == null ? "no_storm_variant"
                        : resolution.skipReason());
            }
            state.variant = resolution.variant();
        }
        state.lastProgress = ctx.now();
        if (!begin(ctx, config, state)) {
            return ResumeResult.refuse("storm_active");
        }
        return ResumeResult.ok();
    }

    /** Privacy erasure: drops {@code player} from every live storm's in-memory exposure map. */
    public static void forget(UUID player) {
        if (player == null) {
            return;
        }
        for (State state : new ArrayList<>(LIVE.values())) {
            state.exposure.remove(player);
        }
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
        WARNED_NO_VARIANT.clear();
    }

    /** Live instance count (tests). */
    static int liveCount() {
        return LIVE.size();
    }
}
