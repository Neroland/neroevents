package za.co.neroland.neroevents.economy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.PayloadTypes;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;

/**
 * {@code neroevents:market_shock}: a forecast NeroEconomy price shock, believed only when NeroEconomy confirms
 * it.
 *
 * <p>NeroEvents never touches a price. It <b>asks</b> through {@link MarketShockRequests} (Core's
 * {@code neroevents:market_shock_request} channel) and then listens on {@code neroeconomy:market_shock}
 * ({@link EconomySignals}): a rising crossing for the requested key confirms the shock, a falling one ends it.
 * A request that NeroEconomy does not confirm within {@value #CONFIRM_TICKS} ticks resolves the instance
 * {@link EventOutcome#FAILURE} ({@code unconfirmed}); two misses in a row pause the payload for the rest of the
 * server session ({@link #isPaused()}), with one INFO line.
 *
 * <p>The shock's clock belongs to NeroEconomy; the instance's {@code endEpochMs} (+ one minute of grace) is only
 * a backstop. After a restart the shock (restart-local in NeroEconomy) is requested again for the remaining
 * minutes; an unconfirmed re-request ends the instance {@link EventOutcome#ENDED_EARLY} and does not count
 * toward the pause.
 *
 * <p>Privacy: a shock names goods, never a player. Logs name the definition and counts only.
 */
public final class MarketShockPayload implements PayloadType<MarketShockPayload.Config, MarketShockPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "market_shock");

    /** Ticks NeroEconomy has to confirm a request. */
    public static final int CONFIRM_TICKS = 20;

    /** Wall-clock grace after the expected end before the backstop resolves the instance. */
    public static final long END_GRACE_MS = 60_000L;

    /** Consecutive unconfirmed requests that pause the payload. */
    public static final int PAUSE_AFTER = 2;

    static final String TELEMETRY_SOURCE = "payload:market_shock";

    // --- config ----------------------------------------------------------------------------------

    /** One possible shock: an item id or {@code #tag}, a multiplier, a duration and the narrative noun phrase. */
    public record Shock(String key, double multiplier, int minutes, String narrative) {

        static final Codec<Shock> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("key").forGetter(Shock::key),
                Codec.doubleRange(0.25D, 4.0D).fieldOf("multiplier").forGetter(Shock::multiplier),
                Codec.intRange(1, 1440).fieldOf("minutes").forGetter(Shock::minutes),
                Codec.STRING.fieldOf("narrative").forGetter(Shock::narrative)
        ).apply(inst, Shock::new));

        /** Whether NeroEconomy would accept the key (else the entry is ignored). */
        public boolean usable() {
            return MarketShockRequests.validScope(key);
        }
    }

    /** {@code {"shocks": [...]}} — 1 to 16 entries; one is chosen per instance by the seed. */
    public record Config(List<Shock> shocks) implements PayloadConfig {

        static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Shock.CODEC.listOf().validate(Config::checkSize).fieldOf("shocks").forGetter(Config::shocks)
        ).apply(inst, Config::new));

        public Config {
            shocks = List.copyOf(shocks);
        }

        private static DataResult<List<Shock>> checkSize(List<Shock> list) {
            if (list.isEmpty() || list.size() > 16) {
                return DataResult.error(() -> "shocks must hold 1 to 16 entries");
            }
            return DataResult.success(list);
        }
    }

    // --- state -----------------------------------------------------------------------------------

    /** Per-instance state; {@code key}, {@code multiplier}, {@code minutes}, {@code confirmed}, {@code endEpochMs} are saved. */
    public static final class State implements PayloadState {
        @Nullable String key;
        double multiplier;
        int minutes;
        @Nullable String narrative;
        volatile boolean confirmed;
        volatile boolean ended;
        boolean streakCleared;
        long requestedAt;
        long endEpochMs;
        /** True after a restart re-request: an unconfirmed one ends early and never counts toward the pause. */
        boolean reRequest;
        /** True when the resume found the shock already over. */
        boolean expiredOnResume;
        /** Why the instance failed ({@code unconfirmed}), for tests and the history reason. */
        @Nullable String reason;

        public @Nullable String key() {
            return key;
        }

        public boolean confirmed() {
            return confirmed;
        }

        public boolean ended() {
            return ended;
        }

        public long endEpochMs() {
            return endEpochMs;
        }

        public @Nullable String reason() {
            return reason;
        }
    }

    // --- seams -----------------------------------------------------------------------------------

    /** What the payload asks of NeroEconomy. Production goes through Core's channel; tests use a fake. */
    public interface Market {
        boolean shockActive(String key);

        void apply(String key, double multiplier, long minutes);

        void end(String key);
    }

    /** The production market: reads through {@link NeroEconomyBridge}, requests through {@link MarketShockRequests}. */
    static final Market CORE_MARKET = new Market() {
        @Override
        public boolean shockActive(String key) {
            return EventsEconomy.isActive() && NeroEconomyBridge.shockActive(key);
        }

        @Override
        public void apply(String key, double multiplier, long minutes) {
            MarketShockRequests.apply(key, multiplier, minutes);
        }

        @Override
        public void end(String key) {
            MarketShockRequests.end(key);
        }
    };

    private static volatile Market market = CORE_MARKET;
    private static volatile LongSupplier wallClock = System::currentTimeMillis;
    private static volatile java.util.function.BooleanSupplier economyActive = EventsEconomy::isActive;

    /** Live states by instance id, so the signal hook (outside the tick) can find them. */
    static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();

    private static volatile boolean paused;
    private static volatile int unconfirmedStreak;
    private static volatile WeakReference<Object> sessionOwner = new WeakReference<>(null);
    private static boolean hookRegistered;

    // --- registration and the static surface ------------------------------------------------------

    /** Registers the payload type. Called once by {@code EconomyCompat} when NeroEconomy is loaded. */
    public static synchronized void register() {
        if (PayloadTypes.get(ID).isEmpty()) {
            PayloadTypes.register(new MarketShockPayload());
        }
        registerHook();
    }

    /** Hooks the confirmation listener once per JVM (it only touches states in {@link #LIVE}). */
    static synchronized void registerHook() {
        if (!hookRegistered) {
            hookRegistered = true;
            EconomySignals.addHook(MarketShockPayload::onSignal);
        }
    }

    /** Whether market shocks are paused for this server session (two consecutive unconfirmed requests). */
    public static boolean isPaused() {
        return paused;
    }

    /** Clears the pause and the streak (a new server session). Integration may call it at server start. */
    public static synchronized void resetSession() {
        paused = false;
        unconfirmedStreak = 0;
    }

    /** Clears the session state when {@code owner} (the running server) differs from the last one seen. */
    static synchronized void resetForOwner(@Nullable Object owner) {
        if (owner != null && sessionOwner.get() != owner) {
            sessionOwner = new WeakReference<>(owner);
            resetSession();
        }
    }

    /** The {@code neroeconomy:market_shock} listener: confirm or end every live state for that key. */
    static void onSignal(EconomySignals.EconomySignal signal) {
        if (signal == null || signal.kind() != EconomySignals.Kind.MARKET_SHOCK || signal.scope() == null
                || LIVE.isEmpty()) {
            return;
        }
        String scope = normalise(signal.scope());
        for (State state : LIVE.values()) {
            if (scope.equals(state.key)) {
                if (signal.rising()) {
                    state.confirmed = true;
                } else {
                    state.ended = true;
                }
            }
        }
    }

    static String normalise(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    // --- test seams ---------------------------------------------------------------------------------

    static void useForTests(@Nullable Market fake, @Nullable LongSupplier clock, boolean economy) {
        market = fake == null ? CORE_MARKET : fake;
        wallClock = clock == null ? System::currentTimeMillis : clock;
        economyActive = () -> economy;
    }

    static synchronized void resetForTests() {
        market = CORE_MARKET;
        wallClock = System::currentTimeMillis;
        economyActive = EventsEconomy::isActive;
        LIVE.clear();
        resetSession();
        sessionOwner = new WeakReference<>(null);
    }

    // --- PayloadType ----------------------------------------------------------------------------

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
        for (Shock shock : config.shocks()) {
            if (!shock.usable()) {
                out.add("shock key '" + shock.key() + "' is not an item id or #tag; entry ignored");
            }
        }
        if (config.shocks().stream().noneMatch(Shock::usable)) {
            out.add("no usable shock entry; every instance will be skipped");
        }
        return out;
    }

    @Override
    public Optional<String> preflight(SchedulerSnapshot snapshot, EventDefinition definition) {
        if (!snapshot.loadedMods().contains("neroeconomy")) {
            return Optional.of("no_economy");
        }
        if (paused) {
            return Optional.of("shock_paused");
        }
        return Optional.empty();
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.dimension(dimension); // the economy is server-wide; the dimension is a formality
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        return new State();
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext server) {
            resetForOwner(server.server());
        }
        if (!economyActive.getAsBoolean()) {
            return StartResult.skip("no_economy");
        }
        if (paused) {
            return StartResult.skip("shock_paused");
        }
        List<Shock> usable = new ArrayList<>();
        for (Shock shock : config.shocks()) {
            if (shock.usable()) {
                usable.add(shock);
            }
        }
        if (usable.isEmpty()) {
            return StartResult.skip("payload");
        }
        // Seeded order: the first pick is random, the rest follow in a seeded shuffle.
        for (int i = usable.size() - 1; i > 0; i--) {
            int j = ctx.random().nextInt(i + 1);
            Shock swap = usable.get(i);
            usable.set(i, usable.get(j));
            usable.set(j, swap);
        }
        Shock pick = null;
        for (Shock candidate : usable) {
            boolean active;
            try {
                active = market.shockActive(candidate.key());
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, TELEMETRY_SOURCE, "start");
                active = false;
            }
            if (!active) {
                pick = candidate;
                break;
            }
        }
        if (pick == null) {
            return StartResult.skip("shock_active");
        }
        state.key = normalise(pick.key());
        state.multiplier = pick.multiplier();
        state.minutes = pick.minutes();
        state.narrative = pick.narrative();
        state.confirmed = false;
        state.ended = false;
        state.reRequest = false;
        state.requestedAt = ctx.now();
        LIVE.put(ctx.instance().id(), state);
        registerHook();
        request(state, pick.minutes(), "start");
        state.endEpochMs = wallClock.getAsLong() + pick.minutes() * 60_000L;
        ctx.note("event.neroevents.market_shock.forecast", Component.translatable(pick.narrative()), pick.minutes());
        return StartResult.ok();
    }

    private static void request(State state, long minutes, String operation) {
        try {
            market.apply(state.key, state.multiplier, minutes); // synchronous Core dispatch: confirmation usually lands here
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, TELEMETRY_SOURCE, operation);
        }
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        if (state.expiredOnResume) {
            return TickResult.resolved(EventOutcome.ENDED); // ran out during downtime
        }
        if (!state.confirmed) {
            if (ctx.now() - state.requestedAt > CONFIRM_TICKS) {
                if (state.reRequest) {
                    state.reason = "unconfirmed";
                    return TickResult.resolved(EventOutcome.ENDED_EARLY);
                }
                missed(ctx);
                state.reason = "unconfirmed";
                return TickResult.resolved(EventOutcome.FAILURE);
            }
            return TickResult.proceed();
        }
        if (!state.streakCleared) {
            state.streakCleared = true;
            unconfirmedStreak = 0;
        }
        if (state.ended) {
            return TickResult.resolved(EventOutcome.ENDED);
        }
        if (wallClock.getAsLong() >= state.endEpochMs + END_GRACE_MS) {
            return TickResult.resolved(EventOutcome.ENDED); // NeroEconomy owns the clock; this is the backstop
        }
        return TickResult.proceed();
    }

    private static synchronized void missed(PayloadContext ctx) {
        unconfirmedStreak++;
        if (unconfirmedStreak >= PAUSE_AFTER && !paused) {
            paused = true;
            NeroEventsCommon.LOGGER.info("[NeroEvents] Market shocks paused for this session: NeroEconomy did not "
                    + "confirm {} requests in a row (is neroevents in its shockSources?). Last definition {}.",
                    PAUSE_AFTER, ctx.instance().definition().id());
        }
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return EventOutcome.ENDED;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        LIVE.remove(ctx.instance().id());
        if (state.confirmed && !state.ended && state.key != null) {
            state.ended = true;
            try {
                market.end(state.key); // an admin cancel (or the backstop) ends the shock it asked for
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, TELEMETRY_SOURCE, "cleanup");
            }
        }
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        if (state.key != null) {
            tag.putString("key", state.key);
        }
        tag.putDouble("multiplier", state.multiplier);
        tag.putInt("minutes", state.minutes);
        tag.putBoolean("confirmed", state.confirmed);
        tag.putLong("endEpochMs", state.endEpochMs);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State();
        String key = tag.getStringOr("key", "");
        if (key.isEmpty() || !MarketShockRequests.validScope(key)) {
            throw new IllegalStateException("market_shock state has no valid key");
        }
        state.key = normalise(key);
        state.multiplier = tag.getDoubleOr("multiplier", 1.0D);
        state.minutes = Math.max(1, tag.getIntOr("minutes", 1));
        state.confirmed = tag.getBooleanOr("confirmed", false);
        state.endEpochMs = tag.getLongOr("endEpochMs", 0L);
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        if (ctx instanceof ServerPayloadContext server) {
            resetForOwner(server.server());
        }
        long nowMs = wallClock.getAsLong();
        LIVE.put(ctx.instance().id(), state);
        registerHook();
        if (nowMs >= state.endEpochMs) {
            state.expiredOnResume = true;
            state.confirmed = false;
            return ResumeResult.ok();
        }
        long remaining = (state.endEpochMs - nowMs + 59_999L) / 60_000L;
        state.confirmed = false;
        state.ended = false;
        state.reRequest = true;
        state.streakCleared = true; // a re-request never touches the pause streak
        state.requestedAt = ctx.now();
        request(state, Math.max(1L, remaining), "resume");
        return ResumeResult.ok();
    }
}
