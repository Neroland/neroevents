package za.co.neroland.neroevents.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.CompatServer;
import za.co.neroland.neroevents.data.NeroEventsData;
import za.co.neroland.neroevents.economy.MarketShockPayload;
import za.co.neroland.neroevents.event.EventScheduler.Eligibility;
import za.co.neroland.neroevents.event.anchor.AnchorPicker;
import za.co.neroland.neroevents.event.announce.Announcer;
import za.co.neroland.neroevents.event.announce.ChatAnnouncer;
import za.co.neroland.neroevents.event.persist.EventEngineState;
import za.co.neroland.neroevents.event.persist.InstanceRecord;
import za.co.neroland.neroevents.event.reward.EventHistorySink;
import za.co.neroland.neroevents.event.reward.RewardResolver;
import za.co.neroland.neroevents.event.world.ServerWorldView;
import za.co.neroland.neroevents.event.world.WorldView;
import za.co.neroland.neroevents.history.EventHistoryState;
import za.co.neroland.neroevents.history.PersistentHistorySink;
import za.co.neroland.neroevents.participation.ContributionPolicy;
import za.co.neroland.neroevents.participation.ParticipationService;
import za.co.neroland.neroevents.participation.PresenceView;
import za.co.neroland.neroevents.participation.ServerPresenceView;
import za.co.neroland.neroevents.participation.WeightedContributionTable;
import za.co.neroland.neroevents.privacy.EventsPrivacy;
import za.co.neroland.neroevents.privacy.EventsPrivacyActions;
import za.co.neroland.neroevents.privacy.PrivacyExport;
import za.co.neroland.neroevents.privacy.PrivacyOptOuts;
import za.co.neroland.neroevents.progression.Milestones;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.protect.ProtectionQuery;
import za.co.neroland.neroevents.protect.ProtectionResult;
import za.co.neroland.neroevents.protect.ProtectionSources;
import za.co.neroland.neroevents.reward.BudgetedRewardResolver;
import za.co.neroland.neroevents.reward.RewardConfig;
import za.co.neroland.neroevents.reward.RewardCrateState;
import za.co.neroland.neroevents.reward.RewardCrates;
import za.co.neroland.neroevents.reward.RewardLedgerState;
import za.co.neroland.neroevents.reward.RewardOutcome;
import za.co.neroland.neroevents.reward.ServerRewardSinks;
import za.co.neroland.neroevents.storm.StormPayloadBase;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;
import za.co.neroland.neroevents.vote.VotePolicy;
import za.co.neroland.neroevents.war.FactionWarPayload;

/**
 * The per-server event orchestrator: ticks live instances through their lifecycle, runs the scheduler
 * pass, handles operator and gameplay requests, and restores resumable instances after a restart.
 *
 * <p>One engine per server, created on the server's first tick ({@link #tick(MinecraftServer)} detects a
 * new server by identity, so no lifecycle events are needed). Everything it decides is computed against
 * a {@link WorldView}, a clock and a seeded random, so the package-private constructor runs the whole
 * lifecycle on a plain JVM.
 *
 * <p><b>Server thread only.</b> No executors, no futures. <b>Privacy:</b> logs name definition ids and
 * random instance short ids, never a player, a vote, a reward or coordinates; votes and contributions
 * live only on the in-memory instance and are erased per player through the {@code live_instances}
 * store eraser.
 */
public final class EventEngine {

    /** PROGRESS and VOTE_CHANGED signals are coalesced to one per instance per this many ticks. */
    public static final int SIGNAL_COALESCE_TICKS = 20;
    /** Mid-event notes are rate-limited to one per instance per this many ticks. */
    public static final int NOTE_GAP_TICKS = 100;

    private static volatile @Nullable EventEngine current;
    private static boolean eraserRegistered;
    /** Run after every scheduler pass on a live server, each isolated from the others. */
    private static final List<Consumer<MinecraftServer>> PASS_HOOKS = new CopyOnWriteArrayList<>();

    private @Nullable MinecraftServer server;
    private final WorldView world;
    private final EngineClock clock;
    private final RandomGenerator random;
    private final EventEngineState state;
    private final Announcer announcer;
    private RewardResolver rewards;
    private EventHistorySink history;
    private final ProtectionQuery protection;
    private final Function<EventInstance, PayloadContext> contexts;
    private final ParticipationService participation;
    private Milestones milestones = Milestones.forServer(null);
    private @Nullable PresenceView presence;
    private LongSupplier wallClock = System::currentTimeMillis;
    private Supplier<RewardConfig> rewardConfig = RewardConfig::defaults;
    private @Nullable RewardCrates crates;

    private Supplier<EngineSettings> settingsSource = EngineSettings::defaults;
    private EngineSettings settings = EngineSettings.defaults();
    private Supplier<Map<Identifier, EventDefinition>> definitionsSource = Map::of;
    private BooleanSupplier onServerThread = () -> true;

    private final List<EventInstance> live = new ArrayList<>();
    private final List<EventInstance> recent = new ArrayList<>();
    private final EngineMetrics metrics = new EngineMetrics();
    private BandTable bands = BandTable.DEFAULT;
    private @Nullable String bandsSpec;
    private boolean passWarned;
    private boolean consentLogged;
    private int warnedGeneration = -1;
    private final Set<String> warnedBands = new HashSet<>();

    EventEngine(WorldView world, EngineClock clock, RandomGenerator random, EventEngineState state, Announcer announcer,
            RewardResolver rewards, EventHistorySink history, ProtectionQuery protection,
            @Nullable Function<EventInstance, PayloadContext> contexts) {
        this.world = world;
        this.clock = clock;
        this.random = random;
        this.state = state;
        this.announcer = announcer;
        this.rewards = rewards;
        this.history = history;
        this.protection = protection;
        this.contexts = contexts != null ? contexts : inst -> new EnginePayloadContext(inst, world, protection, clock);
        this.participation = new ParticipationService(() -> this.milestones,
                () -> this.rewardConfig.get().contributionSweepIntervalTicks);
    }

    // --- static entry points ----------------------------------------------------------------------

    /** The engine of {@code server}, created (and restored) on first use; replaced on a server change. */
    public static synchronized EventEngine get(MinecraftServer server) {
        EventEngine engine = current;
        if (engine == null || engine.server != server) {
            // A new server session: bind it for the sibling protection sources (without it they deny
            // everything) and clear the market-shock pause carried from a previous world in this JVM.
            CompatServer.set(server);
            MarketShockPayload.resetSession();
            engine = create(server);
            current = engine;
            ProtectionSources.resetHealth();
            engine.refreshSettings();
            EventDefinitions.cap(engine.settings.schedulerDefinitionsCap);
            engine.restore();
        }
        return engine;
    }

    /** The running engine, or {@code null} before the first server tick. */
    public static @Nullable EventEngine current() {
        return current;
    }

    /**
     * Adds a hook that runs after every scheduler pass on a running server (e.g. wreck decay). Hooks run in
     * registration order, each in its own try/catch: one failing hook never stops the pass or the others.
     * Init-time only.
     */
    public static void onSchedulerPass(Consumer<MinecraftServer> hook) {
        if (hook != null) {
            PASS_HOOKS.add(hook);
        }
    }

    /** Loader hook: every server tick. */
    public static void tick(MinecraftServer server) {
        PayloadTypes.freeze();
        get(server).tickOnce();
        za.co.neroland.neroevents.fx.EventSounds.tick(server);
    }

    /**
     * Registers the {@code live_instances} store eraser (common init slot 4): an erasure request wipes
     * the player's votes and contributions from every live and recent instance. Nothing is on disk, so the
     * backup step is a no-op.
     */
    public static synchronized void registerEraser() {
        if (eraserRegistered) {
            return;
        }
        eraserRegistered = true;
        NeroEventsData.addStore(liveEraser(() -> current));
    }

    /**
     * The {@code live_instances} store eraser over the engine {@code engine} supplies: votes, contributions,
     * session "already paid" markers, the session reward crate and the export cooldown. In memory only.
     */
    public static NeroEventsData.StoreEraser liveEraser(Supplier<EventEngine> engine) {
        return new NeroEventsData.StoreEraser() {
            @Override
            public String name() {
                return "live_instances";
            }

            @Override
            public void erase(MinecraftServer erasingServer, UUID player) {
                EventEngine running = engine.get();
                if (running != null) {
                    running.forget(player);
                }
                // Payload-held, in-memory player state: frozen war sides and storm exposure. Static no-ops
                // when nothing is live.
                FactionWarPayload.forget(player);
                StormPayloadBase.forget(player);
            }

            @Override
            public void backupNow(MinecraftServer erasingServer) {
                // In memory only: nothing on disk to refresh.
            }
        };
    }

    private static EventEngine create(MinecraftServer server) {
        ServerWorldView world = new ServerWorldView(server);
        EngineClock clock = () -> server.overworld().getGameTime();
        ProtectionQuery query = ProtectionQuery.live();
        ChatAnnouncer announcer = new ChatAnnouncer(server);
        EventEngine engine = new EventEngine(world, clock, new Random(), EventEngineState.get(server),
                announcer, RewardResolver.NONE, EventHistorySink.NONE, query,
                inst -> new ServerPayloadContext(server, inst, world, query, clock));
        engine.server = server;
        engine.settingsSource = EngineSettings::fromConfig;
        engine.definitionsSource = () -> EventDefinitions.definitionsForServer(server);
        engine.onServerThread = server::isSameThread;
        ContributionPolicy.useConfig();
        engine.rewardConfig = RewardConfig::fromConfig;
        engine.milestones = Milestones.forServer(server);
        engine.presence = new ServerPresenceView(server);
        RewardCrates crates = new RewardCrates(() -> RewardCrateState.get(server));
        engine.crates = crates;
        engine.rewards = new BudgetedRewardResolver(RewardConfig::fromConfig, new ServerRewardSinks(server, announcer),
                () -> RewardLedgerState.get(server), crates, () -> EventHistoryState.get(server),
                () -> PrivacyOptOuts.get(server), () -> engine.milestones, engine.participation,
                System::currentTimeMillis);
        engine.history = new PersistentHistorySink(() -> EventHistoryState.get(server), System::currentTimeMillis,
                () -> RewardConfig.fromConfig().historyMaxEntries);
        return engine;
    }

    // --- test seams (package-private) --------------------------------------------------------------

    void settings(EngineSettings fixed) {
        this.settings = fixed;
        this.settingsSource = () -> fixed;
    }

    void definitions(Supplier<Map<Identifier, EventDefinition>> source) {
        this.definitionsSource = source;
    }

    void milestones(Milestones value) {
        this.milestones = value;
    }

    void presence(@Nullable PresenceView value) {
        this.presence = value;
    }

    void wallClock(LongSupplier value) {
        this.wallClock = value;
    }

    void rewardConfig(Supplier<RewardConfig> value) {
        this.rewardConfig = value;
    }

    void rewards(RewardResolver value, @Nullable RewardCrates crateLogic) {
        this.rewards = value;
        this.crates = crateLogic;
    }

    void history(EventHistorySink value) {
        this.history = value;
    }

    /** The participation service (the reward resolver shares its session markers). */
    public ParticipationService participation() {
        return participation;
    }

    /** The reward crate logic on a running server (empty in tests that pay nothing). */
    public Optional<RewardCrates> crates() {
        return Optional.ofNullable(crates);
    }

    /** Today's remaining reward budget in value units. */
    public long remainingDailyBudget() {
        return rewards.remainingDailyBudget(wallClock.getAsLong());
    }

    /**
     * What the running engine holds about one player, as counts only (the privacy export's {@code live}
     * block): live tables naming them, open tallies with their vote, reminder sets (none yet).
     */
    public PrivacyExport.LiveCounts liveCountsFor(UUID player) {
        int contributing = 0;
        int votes = 0;
        for (EventInstance instance : live) {
            if (instance.contributions().holds(player)) {
                contributing++;
            }
            if (instance.votes().open() && instance.votes().hasVoted(player)) {
                votes++;
            }
        }
        return new PrivacyExport.LiveCounts(contributing, votes, 0);
    }

    /** Conformance probes for the in-memory stores the {@code live_instances} eraser covers. */
    public boolean holdsLiveContributions(UUID player) {
        for (EventInstance instance : live) {
            if (instance.contributions().holds(player)) {
                return true;
            }
        }
        for (EventInstance instance : recent) {
            if (instance.contributions().holds(player)) {
                return true;
            }
        }
        return false;
    }

    public boolean holdsLiveVotes(UUID player) {
        for (EventInstance instance : live) {
            if (instance.votes().hasVoted(player)) {
                return true;
            }
        }
        for (EventInstance instance : recent) {
            if (instance.votes().hasVoted(player)) {
                return true;
            }
        }
        return false;
    }

    public boolean holdsSessionPaid(UUID player) {
        return participation.holds(player);
    }

    public boolean holdsSessionCrate(UUID player) {
        return rewards.holds(player) || (crates != null && crates.holdsSession(player));
    }

    // --- accessors ----------------------------------------------------------------------------------

    public EngineSettings settings() {
        return settings;
    }

    public EngineMetrics metrics() {
        return metrics;
    }

    public EventEngineState state() {
        return state;
    }

    public WorldView world() {
        return world;
    }

    public BandTable bands() {
        return bands;
    }

    public ProtectionQuery protection() {
        return protection;
    }

    public long now() {
        return clock.now();
    }

    public Map<Identifier, EventDefinition> definitions() {
        return definitionsSource.get();
    }

    public List<EventInstance> live() {
        return List.copyOf(live);
    }

    public List<EventInstance> recent() {
        return List.copyOf(recent);
    }

    /** Live and recent instances whose short id starts with {@code prefix}. */
    public List<EventInstance> matching(String prefix) {
        List<EventInstance> out = new ArrayList<>();
        String p = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
        for (EventInstance instance : live) {
            if (instance.shortId().startsWith(p)) {
                out.add(instance);
            }
        }
        for (EventInstance instance : recent) {
            if (instance.shortId().startsWith(p)) {
                out.add(instance);
            }
        }
        return out;
    }

    /** The single instance a short-id prefix names; empty when none or ambiguous. */
    public Optional<EventInstance> byShortId(String prefix) {
        List<EventInstance> found = matching(prefix);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** The consent mode in force right now. */
    public ConsentMode effectiveConsent() {
        return ConsentMode.effective(settings.consentMode, settings.consentVotingEnabled, world.isDedicatedServer(),
                world.onlinePlayers());
    }

    /** A fresh snapshot (status and preview). */
    public SchedulerSnapshot snapshot() {
        return snapshot(clock.now(), definitionsSource.get());
    }

    private SchedulerSnapshot snapshot(long now, Map<Identifier, EventDefinition> defs) {
        long budget;
        try {
            budget = rewards.remainingDailyBudget(wallClock.getAsLong());
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "rewards", "history");
            budget = Long.MAX_VALUE;
        }
        return EventScheduler.snapshot(world, now, settings, bands, defs.values(), live, state, budget);
    }

    // --- the tick -----------------------------------------------------------------------------------

    /** One engine tick. */
    void tickOnce() {
        assertServerThread();
        metrics.beginTick();
        long now = clock.now();
        for (EventInstance instance : new ArrayList<>(live)) {
            try {
                tickInstance(instance, now);
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "engine", "transition");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] {} {}: engine error ({}); cancelling.",
                        instance.definition().id(), instance.shortId(), failure.getClass().getName());
                cancel(instance, "engine_error", now);
            }
        }
        moveTerminal();
        recent.removeIf(i -> {
            boolean gone = now >= i.terminalAt() + settings.instanceRetainTicks;
            if (gone) {
                participation.forgetInstance(i.id()); // its session "paid" markers can never be needed again
            }
            return gone;
        });
        participation.tick(live, now, presence);
        if (now % Math.max(1, settings.schedulerIntervalTicks) == 0) {
            schedulerPass(now);
            if (server != null) {
                EventsPrivacy.tick(server, now);
            }
        }
        if (!live.isEmpty() && now % Math.max(1, settings.resumableSaveIntervalTicks) == 0) {
            refreshPayloadStates();
            persist();
        }
        metrics.endTick();
    }

    private void schedulerPass(long now) {
        metrics.beginPass();
        try {
            refreshSettings();
            if (server != null) {
                EventDefinitions.cap(settings.schedulerDefinitionsCap);
                EventDefinitions.refreshIfReloaded(server);
            }
            Map<Identifier, EventDefinition> defs = definitionsSource.get();
            warnUnknownBands(defs);
            if (settings.schedulerEnabled && now >= state.nextRollAt()) {
                roll(now, defs);
            }
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "scheduler_pass");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] Scheduler pass failed ({}).", failure.getClass().getName());
            state.setNextRollAt(now + settings.schedulerRetryTicks);
        }
        long nanos = metrics.endPass();
        if (!passWarned && nanos / 1000L > settings.schedulerPassWarnMicros) {
            passWarned = true;
            NeroEventsCommon.LOGGER.debug("[NeroEvents] A scheduler pass took {} µs (threshold {} µs).",
                    nanos / 1000L, settings.schedulerPassWarnMicros);
        }
        runPassHooks();
    }

    private void runPassHooks() {
        MinecraftServer running = server;
        if (running == null) {
            return;
        }
        for (Consumer<MinecraftServer> hook : PASS_HOOKS) {
            try {
                hook.accept(running);
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "engine", "scheduler_hook");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] A scheduler-pass hook failed ({}).",
                        failure.getClass().getName());
            }
        }
    }

    private void refreshSettings() {
        settings = settingsSource.get();
        if (!settings.schedulerBands.equals(bandsSpec)) {
            bandsSpec = settings.schedulerBands;
            bands = BandTable.parse(bandsSpec, settings.maxConcurrentEvents);
            for (String issue : bands.issues()) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] schedulerBands: {}; using the default table.", issue);
            }
        }
        if (!consentLogged) {
            consentLogged = true;
            if (settings.consentModeInvalid) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] consentMode is not off, vote or admin; using off.");
            } else if (settings.consentMode == ConsentMode.VOTE && !settings.consentVotingEnabled) {
                NeroEventsCommon.LOGGER.info("[NeroEvents] consentMode=vote but consentVotingEnabled=false; "
                        + "events run without a vote.");
            }
        }
    }

    private void warnUnknownBands(Map<Identifier, EventDefinition> defs) {
        int generation = EventDefinitions.generation();
        if (generation != warnedGeneration) {
            warnedGeneration = generation;
            warnedBands.clear();
        }
        for (EventDefinition def : defs.values()) {
            for (String name : def.conditions().bands().orElse(List.of())) {
                if (!bands.hasBand(name) && warnedBands.add(def.id() + "|" + name)) {
                    NeroEventsCommon.LOGGER.warn("[NeroEvents] Event definition {}: conditions.bands — unknown band "
                            + "'{}'; using ignored", def.id(), name);
                }
            }
        }
    }

    private void roll(long now, Map<Identifier, EventDefinition> defs) {
        SchedulerSnapshot snap = snapshot(now, defs);
        Map<Identifier, Eligibility> eligible = new TreeMap<>();
        Map<Identifier, Integer> weights = new TreeMap<>();
        for (EventDefinition def : new TreeMap<>(defs).values()) {
            Eligibility e = EventScheduler.eligibility(def, snap, settings);
            if (e.eligible() && e.effectiveWeight() > 0) {
                eligible.put(def.id(), e);
                weights.put(def.id(), e.effectiveWeight());
            }
        }
        Optional<Identifier> chosen = EventScheduler.pick(weights, random);
        if (chosen.isEmpty()) {
            state.setNextRollAt(now + settings.schedulerRetryTicks);
            return;
        }
        EventDefinition def = defs.get(chosen.get());
        Identifier dimension = EventScheduler.pickDimension(eligible.get(def.id()).candidateDimensions(),
                snap.onlinePerDimension(), random);
        createInstance(def, dimension, EventOrigin.SCHEDULER, null, now);
        state.setNextRollAt(EventScheduler.nextRollAt(now, settings, snap.band(), random));
    }

    // --- creation and requests ------------------------------------------------------------------------

    private record Creation(@Nullable EventInstance instance, @Nullable String code, @Nullable String detail) {
    }

    /**
     * An operator ({@code ADMIN}) or gameplay ({@code REQUEST}) start. {@code ADMIN} bypasses every rule
     * but the kill switches, protection of an explicit anchor and a failed anchor pick, and never votes.
     * {@code REQUEST} bypasses request-only, the definition cooldown and voting, but honours the kill
     * switches, the calm window, the band and type caps and {@code maxConcurrentEvents}.
     */
    public RequestResult request(EventRequest request) {
        assertServerThread();
        long now = clock.now();
        Map<Identifier, EventDefinition> defs = definitionsSource.get();
        EventDefinition def = defs.get(request.definitionId());
        if (def == null) {
            return new RequestResult.Refused("unknown_definition", null);
        }
        if (def.payloadType() == null || PayloadTypes.get(def.type()).isEmpty()) {
            return new RequestResult.Refused("payload_missing", def.type().toString());
        }
        Optional<String> kill = EventScheduler.killSwitch(def, settings);
        if (kill.isPresent()) {
            return new RequestResult.Refused("kill_switch", kill.get());
        }
        if (!world.loadedDimensions().contains(request.dimension())) {
            return new RequestResult.Refused("unknown_dimension", request.dimension().toString());
        }
        if (request.origin() == EventOrigin.REQUEST) {
            if (!def.enabled() || settings.eventsDisabled.contains(def.id())) {
                return new RequestResult.Refused("disabled", null);
            }
            SchedulerSnapshot snap = snapshot(now, defs);
            if (snap.calmWindow() && !def.calmExempt()) {
                return new RequestResult.Refused("calm_window", null);
            }
            if (EventScheduler.bandCapReached(snap, settings)) {
                return new RequestResult.Refused("band_cap", null);
            }
            if (EventScheduler.typeCapReached(def.category(), snap, settings)
                    || EventScheduler.bossFreeDimensions(def, snap, List.of(request.dimension())).isEmpty()) {
                return new RequestResult.Refused("type_cap", null);
            }
        }
        Creation created = createInstance(def, request.dimension(), request.origin(), request.requestedAnchor(), now);
        if (created.instance() == null) {
            return new RequestResult.Refused(created.code(), created.detail());
        }
        return new RequestResult.Queued(created.instance());
    }

    private Creation createInstance(EventDefinition def, Identifier dimension, EventOrigin origin,
            @Nullable BlockPos requestedAnchor, long now) {
        PayloadType<PayloadConfig, PayloadState> type = def.payload();
        PayloadConfig config = def.payloadConfig();
        long seed = random.nextLong();
        BlockPos anchor = null;
        String failure = null;
        String detail = null;
        if (requestedAnchor != null) {
            ProtectionResult result = protection.evaluation(world)
                    .mayAffectAll(type.footprint(def, config, dimension, requestedAnchor));
            if (result.allowed()) {
                anchor = requestedAnchor;
            } else {
                failure = "protected";
                detail = result.deniedBy();
            }
        } else {
            switch (def.bounds().kind().strategy()) {
                case NONE -> {
                }
                case TARGET -> {
                    Optional<BlockPos> target = pickTarget(def, type, config, dimension, origin, seed, now);
                    if (target.isPresent()) {
                        // The engine, not the payload, enforces the protection contract: a denied target
                        // counts as no target at all.
                        ProtectionResult verdict = protection.evaluation(world)
                                .mayAffectAll(type.footprint(def, config, dimension, target.get()));
                        if (verdict.allowed()) {
                            anchor = target.get();
                        } else {
                            detail = verdict.deniedBy();
                        }
                    }
                    if (anchor == null && def.bounds().fallbackToDensity()) {
                        AnchorPicker.Result picked = pickDensity(def, type, config, dimension, seed);
                        if (picked instanceof AnchorPicker.Found found) {
                            anchor = found.anchor();
                            detail = null;
                        } else {
                            failure = "no_anchor";
                            String denied = ((AnchorPicker.Empty) picked).deniedBy();
                            detail = denied != null ? denied : detail;
                        }
                    } else if (anchor == null) {
                        failure = "no_target";
                    }
                }
                case DENSITY -> {
                    AnchorPicker.Result picked = pickDensity(def, type, config, dimension, seed);
                    if (picked instanceof AnchorPicker.Found found) {
                        anchor = found.anchor();
                    } else {
                        failure = "no_anchor";
                        detail = ((AnchorPicker.Empty) picked).deniedBy();
                    }
                }
            }
        }
        if (failure != null && origin != EventOrigin.SCHEDULER) {
            return new Creation(null, failure, detail);
        }
        EventInstance instance = new EventInstance(UUID.randomUUID(), def, dimension, anchor,
                boundsFor(def, dimension, anchor), origin, seed, now,
                failure != null ? EventPhase.SKIPPED : EventPhase.SCHEDULED);
        instance.timers(now + def.announceTicks(), now + def.announceTicks() + def.durationTicks());
        attach(instance);
        try {
            PayloadState payloadState = type.createState(instance, config);
            instance.payloadState(payloadState);
            if (failure == null) {
                instance.savedState(type.saveState(payloadState));
            }
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "engine", "payload_start");
            if (origin != EventOrigin.SCHEDULER) {
                return new Creation(null, "payload_error", null);
            }
            if (failure == null) {
                instance.force(EventPhase.SKIPPED, now);
                failure = "payload_error";
            }
        }
        if (failure != null) {
            instance.reason(failure);
            halveCooldown(def, now);
            recent.add(instance);
            logPhase(instance, "(new)", EventPhase.SKIPPED, failure);
            publish(instance, EventSignal.Kind.SKIPPED, now);
            recordHistory(instance, now);
            return new Creation(instance, failure, detail);
        }
        state.setLastFiredAt(now);
        state.setCooldownUntil(def.id(), now + def.cooldownTicks());
        live.add(instance);
        logPhase(instance, "(new)", EventPhase.SCHEDULED, null);
        publish(instance, EventSignal.Kind.SCHEDULED, now);
        persist();
        return new Creation(instance, null, null);
    }

    private Optional<BlockPos> pickTarget(EventDefinition def, PayloadType<PayloadConfig, PayloadState> type,
            PayloadConfig config, Identifier dimension, EventOrigin origin, long seed, long now) {
        EventInstance probe = new EventInstance(UUID.randomUUID(), def, dimension, null, Footprint.dimension(dimension),
                origin, seed, now, EventPhase.SCHEDULED);
        try {
            return type.pickTarget(contexts.apply(probe), config);
        } catch (RuntimeException | LinkageError e) {
            NeroEventsTelemetry.captureHandledException(e, "engine", "payload_start");
            return Optional.empty();
        }
    }

    /**
     * What {@code /neroevents preview} shows for a {@code kind: target} definition: the target the payload
     * would pick (built the same way as a real creation) and the protection verdict on its footprint.
     * Changes nothing.
     */
    public record TargetPreview(Optional<BlockPos> target, @Nullable ProtectionResult verdict) {
    }

    /** The dry run of the target anchor path; see {@link TargetPreview}. Server thread. */
    public TargetPreview previewTarget(EventDefinition def, Identifier dimension, long seed) {
        PayloadType<PayloadConfig, PayloadState> type = def.payload();
        PayloadConfig config = def.payloadConfig();
        Optional<BlockPos> target = pickTarget(def, type, config, dimension, EventOrigin.ADMIN, seed, clock.now());
        if (target.isEmpty()) {
            return new TargetPreview(target, null);
        }
        return new TargetPreview(target, protection.evaluation(world)
                .mayAffectAll(type.footprint(def, config, dimension, target.get())));
    }

    private AnchorPicker.Result pickDensity(EventDefinition def, PayloadType<PayloadConfig, PayloadState> type,
            PayloadConfig config, Identifier dimension, long seed) {
        long start = System.nanoTime();
        AnchorPicker.Result result = AnchorPicker.pick(world, protection, dimension,
                a -> type.footprint(def, config, dimension, a), seed, settings.anchorDensityCellBlocks,
                settings.anchorAttemptsPerCell, settings.anchorMaxCellRolls);
        metrics.anchorNanos(System.nanoTime() - start);
        return result;
    }

    private Footprint boundsFor(EventDefinition def, Identifier dimension, @Nullable BlockPos anchor) {
        if (anchor == null || def.bounds().kind() == EventBounds.Kind.DIMENSION) {
            return Footprint.dimension(dimension);
        }
        return Footprint.cylinder(dimension, anchor, def.bounds().radius(), world.minBuildHeight(dimension),
                world.maxBuildHeight(dimension));
    }

    // --- per-instance lifecycle -----------------------------------------------------------------------

    private void tickInstance(EventInstance instance, long now) {
        switch (instance.phase()) {
            case SCHEDULED -> {
                change(instance, EventPhase.ANNOUNCED, now, null);
                announcer.announced(instance.view(now));
                publish(instance, EventSignal.Kind.ANNOUNCED, now);
                if (wouldVote(instance)) {
                    openVote(instance, now);
                }
                maybeStart(instance, now);
            }
            case ANNOUNCED, VOTING -> {
                if (instance.phase() == EventPhase.VOTING && instance.takeVoteSignal(now, SIGNAL_COALESCE_TICKS)) {
                    publish(instance, EventSignal.Kind.VOTE_CHANGED, now);
                }
                if (instance.phase() == EventPhase.VOTING) {
                    maybeCloseEarly(instance, now);
                }
                maybeStart(instance, now);
            }
            case ACTIVE -> tickActive(instance, now);
            case REWARDED -> finish(instance, now);
            default -> {
            }
        }
    }

    private void openVote(EventInstance instance, long now) {
        change(instance, EventPhase.VOTING, now, null);
        instance.votes().openVoting();
        publish(instance, EventSignal.Kind.VOTING_OPENED, now);
    }

    /** Ends voting once every online player has voted; the decision is cached and applied at the countdown's end. */
    private void maybeCloseEarly(EventInstance instance, long now) {
        if (presence == null || instance.voteDecision() != null || !instance.votes().open()) {
            return;
        }
        VotePolicy policy = VotePolicy.fromSettings(settings);
        Set<UUID> online = presence.onlineIds();
        if (policy.shouldCloseEarly(instance.votes(), online)) {
            instance.votes().close();
            instance.voteDecision(policy.decide(instance.votes(), online));
            publish(instance, EventSignal.Kind.VOTE_CLOSED, now);
        }
    }

    private VoteTally.Decision voteDecision(EventInstance instance) {
        if (instance.voteDecision() != null) {
            return instance.voteDecision();
        }
        instance.votes().close();
        if (presence != null) {
            return VotePolicy.fromSettings(settings).decide(instance.votes(), presence.onlineIds());
        }
        return instance.votes().decide(world.onlinePlayers(), settings.voteQuorumPlayers, settings.voteQuorumFraction,
                settings.voteMajorityFraction);
    }

    private boolean wouldVote(EventInstance instance) {
        return instance.origin() == EventOrigin.SCHEDULER && !instance.definition().consentExempt()
                && effectiveConsent() == ConsentMode.VOTE;
    }

    private void maybeStart(EventInstance instance, long now) {
        if (now < instance.startsAt()) {
            return;
        }
        if (instance.phase() == EventPhase.VOTING) {
            VoteTally.Decision decision = voteDecision(instance);
            if (decision != VoteTally.Decision.PASSED) {
                skip(instance, "vote_failed", now);
                return;
            }
        }
        EventDefinition def = instance.definition();
        StartResult result;
        try {
            result = def.payload().onStart(contexts.apply(instance), def.payloadConfig(), instance.payloadState());
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_start");
            skip(instance, "payload_error", now);
            return;
        }
        if (!result.started()) {
            skip(instance, result.skipReason(), now);
            return;
        }
        change(instance, EventPhase.ACTIVE, now, null);
        instance.wallStartedAt(wallClock.getAsLong());
        instance.timers(instance.startsAt(), now + def.durationTicks());
        announcer.started(instance.view(now));
        publish(instance, EventSignal.Kind.STARTED, now);
    }

    private void tickActive(EventInstance instance, long now) {
        EventDefinition def = instance.definition();
        PayloadContext ctx = contexts.apply(instance);
        TickResult result;
        try {
            result = def.payload().tick(ctx, def.payloadConfig(), instance.payloadState());
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_tick");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] {} {}: payload tick failed ({}).", def.id(), instance.shortId(),
                    failure.getClass().getName());
            cancel(instance, "payload_error", now);
            return;
        }
        if (result instanceof TickResult.Progress progress) {
            instance.recordProgress(progress.done(), progress.total());
        }
        afterPayload(instance, now);
        if (result instanceof TickResult.Resolved resolved) {
            resolve(instance, resolved.outcome() == null ? EventOutcome.SUCCESS : resolved.outcome(), now);
        } else if (now >= instance.endsAt()) {
            EventOutcome outcome;
            try {
                outcome = def.payload().onTimeout(ctx, def.payloadConfig(), instance.payloadState());
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_tick");
                outcome = EventOutcome.EXPIRED;
            }
            resolve(instance, outcome == null ? EventOutcome.EXPIRED : outcome, now);
        }
    }

    private void afterPayload(EventInstance instance, long now) {
        if (instance.takeProgressSignal(now, SIGNAL_COALESCE_TICKS)) {
            publish(instance, EventSignal.Kind.PROGRESS, now);
        }
        instance.takeNote(now, NOTE_GAP_TICKS).ifPresent(note -> announcer.note(instance.view(now), note.key(), note.args()));
    }

    private void resolve(EventInstance instance, EventOutcome outcome, long now) {
        EventDefinition def = instance.definition();
        instance.outcome(outcome);
        change(instance, EventPhase.RESOLVING, now, outcome.key());
        // Rewards first: a boss nobody fought turns into ESCAPED before any signal or history sees it.
        RewardOutcome paid = RewardOutcome.NONE;
        try {
            paid = rewards.resolve(instance);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "transition");
        }
        instance.rewardOutcome(paid);
        if (paid != null && paid.escaped()) {
            instance.outcome(EventOutcome.ESCAPED);
        }
        publish(instance, EventSignal.Kind.RESOLVED, now);
        announcer.resolved(instance.view(now));
        // The quest contract: the value is the world's total of resolved events (this one included).
        long worldTotal = state.recordResolved(def.id());
        try {
            milestones.fireEventResolved(def.id(), worldTotal);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "transition");
        }
        change(instance, EventPhase.REWARDED, now, null);
        publish(instance, EventSignal.Kind.REWARDED, now);
        recordHistory(instance, now);
    }

    private void recordHistory(EventInstance instance, long now) {
        try {
            history.record(instance, now);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "rewards", "history");
        }
    }

    private void finish(EventInstance instance, long now) {
        change(instance, EventPhase.CLEANUP, now, null);
        runCleanup(instance);
        publish(instance, EventSignal.Kind.CLEANED, now);
        change(instance, EventPhase.DONE, now, null);
        publish(instance, EventSignal.Kind.DONE, now);
    }

    private void runCleanup(EventInstance instance) {
        if (instance.cleanedUp()) {
            return;
        }
        instance.markCleanedUp();
        EventDefinition def = instance.definition();
        try {
            def.payload().onCleanup(contexts.apply(instance), def.payloadConfig(), instance.payloadState());
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_cleanup");
            NeroEventsCommon.LOGGER.warn("[NeroEvents] {} {}: cleanup failed ({}).", def.id(), instance.shortId(),
                    failure.getClass().getName());
        }
    }

    private void skip(EventInstance instance, String reason, long now) {
        boolean announced = instance.phase() != EventPhase.SCHEDULED;
        change(instance, EventPhase.SKIPPED, now, reason);
        instance.votes().close();
        halveCooldown(instance.definition(), now);
        if (announced) {
            announcer.skipped(instance.view(now), reason);
        }
        publish(instance, EventSignal.Kind.SKIPPED, now);
        recordHistory(instance, now);
    }

    /** Cancels a live instance (any phase): cleanup runs once, the cooldown is halved. */
    private boolean cancel(EventInstance instance, String reason, long now) {
        if (!instance.phase().live()) {
            return false;
        }
        EventPhase from = instance.phase();
        instance.reason(reason);
        if (EventPhase.canTransition(from, EventPhase.CANCELLED)) {
            instance.transition(EventPhase.CANCELLED, now);
        } else {
            instance.force(EventPhase.CANCELLED, now);
        }
        logPhase(instance, from.name(), EventPhase.CANCELLED, reason);
        instance.votes().close();
        runCleanup(instance);
        halveCooldown(instance.definition(), now);
        if (from != EventPhase.SCHEDULED) {
            announcer.cancelled(instance.view(now), reason);
        }
        publish(instance, EventSignal.Kind.CANCELLED, now);
        recordHistory(instance, now);
        persist();
        return true;
    }

    /** {@code /neroevents cancel}: a live instance → CANCELLED. */
    public boolean cancel(UUID instanceId, String reason) {
        assertServerThread();
        long now = clock.now();
        for (EventInstance instance : new ArrayList<>(live)) {
            if (instance.id().equals(instanceId)) {
                boolean done = cancel(instance, reason, now);
                moveTerminal();
                return done;
            }
        }
        return false;
    }

    /** {@code /neroevents skip}: every SCHEDULED/ANNOUNCED/VOTING instance → CANCELLED; ACTIVE untouched. */
    public int skipUpcoming(String reason) {
        assertServerThread();
        long now = clock.now();
        int count = 0;
        for (EventInstance instance : new ArrayList<>(live)) {
            if (instance.phase().upcoming() && cancel(instance, reason, now)) {
                count++;
            }
        }
        moveTerminal();
        return count;
    }

    /** A player's vote on an instance (online players only — the command is player-only). */
    public VoteTally.CastResult vote(EventInstance instance, UUID voter, boolean yes) {
        assertServerThread();
        if (instance.phase() != EventPhase.VOTING) {
            return VoteTally.CastResult.CLOSED;
        }
        VoteTally.CastResult result = instance.votes().cast(voter, yes);
        if (result != VoteTally.CastResult.CLOSED) {
            instance.markVoteChanged();
        }
        return result;
    }

    private void halveCooldown(EventDefinition def, long now) {
        state.setCooldownUntil(def.id(), now + def.cooldownTicks() / 2L);
    }

    private void change(EventInstance instance, EventPhase to, long now, @Nullable String reason) {
        EventPhase from = instance.phase();
        instance.transition(to, now);
        if (reason != null && to.terminal()) {
            instance.reason(reason);
        }
        logPhase(instance, from.name(), to, reason);
        persist();
    }

    private static void logPhase(EventInstance instance, String from, EventPhase to, @Nullable String reason) {
        NeroEventsCommon.LOGGER.info("[NeroEvents] {} {}: {} → {}{}", instance.definition().id(), instance.shortId(),
                from, to, reason == null ? "" : " (" + reason + ")");
    }

    private void publish(EventInstance instance, EventSignal.Kind kind, long now) {
        EventSignal signal = new EventSignal(kind, instance.view(now), now);
        instance.logSignal(signal);
        EventSignals.publish(signal);
    }

    private void moveTerminal() {
        boolean moved = false;
        for (EventInstance instance : new ArrayList<>(live)) {
            if (instance.phase().terminal()) {
                live.remove(instance);
                recent.add(instance);
                moved = true;
            }
        }
        if (moved) {
            persist();
        }
    }

    private void refreshPayloadStates() {
        for (EventInstance instance : live) {
            if (instance.payloadState() == null) {
                continue;
            }
            try {
                instance.savedState(instance.definition().payload().saveState(instance.payloadState()));
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_tick");
            }
        }
    }

    private void persist() {
        List<InstanceRecord> records = new ArrayList<>();
        for (EventInstance instance : live) {
            if (instance.phase().live()) {
                records.add(instance.toRecord());
            }
        }
        state.setInstances(records);
    }

    /** Wires a new instance's contribution table to this engine's participation service. */
    private void attach(EventInstance instance) {
        instance.wallCreatedAt(wallClock.getAsLong());
        if (instance.contributions() instanceof WeightedContributionTable table) {
            table.listener(participation.listenerFor(instance));
        }
    }

    /**
     * Erasure: drops a player's votes and contributions from every live and recent instance, the session
     * "already paid" markers, the session reward crate and the export cooldown.
     */
    void forget(UUID player) {
        for (EventInstance instance : live) {
            instance.votes().forget(player);
            instance.contributions().forget(player);
        }
        for (EventInstance instance : recent) {
            instance.votes().forget(player);
            instance.contributions().forget(player);
        }
        participation.forget(player);
        rewards.forget(player);
        if (crates != null) {
            crates.forgetSession(player);
        }
        EventsPrivacyActions.cooldown().forget(player);
    }

    private void assertServerThread() {
        if (!onServerThread.getAsBoolean()) {
            throw new IllegalStateException("The NeroEvents engine must be used on the server thread");
        }
    }

    // --- restore ------------------------------------------------------------------------------------------

    /** Puts saved instances back after a restart (called once when the engine is created). */
    void restore() {
        long now = clock.now();
        Map<Identifier, EventDefinition> defs = definitionsSource.get();
        for (InstanceRecord record : state.instances()) {
            EventDefinition def = defs.get(record.definitionId());
            if (def == null || def.payloadType() == null || PayloadTypes.get(def.type()).isEmpty()) {
                NeroEventsCommon.LOGGER.info("[NeroEvents] Dropped a saved {} instance: definition no longer exists",
                        record.payloadType());
                continue;
            }
            try {
                restoreOne(def, record, now);
            } catch (RuntimeException | LinkageError failure) {
                NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_resume");
                NeroEventsCommon.LOGGER.warn("[NeroEvents] Could not restore a saved {} instance ({}).",
                        def.id(), failure.getClass().getName());
            }
        }
        moveTerminal();
        persist();
    }

    private void restoreOne(EventDefinition def, InstanceRecord record, long now) {
        PayloadType<PayloadConfig, PayloadState> type = def.payload();
        EventInstance instance = new EventInstance(record.id(), def, record.dimension(), record.anchor().orElse(null),
                boundsFor(def, record.dimension(), record.anchor().orElse(null)), record.origin(), record.seed(),
                record.createdAt(), record.phase());
        attach(instance);
        instance.timers(record.startsAt(), record.endsAt());
        instance.force(record.phase(), record.phaseSince());
        instance.savedState(record.payloadState());
        switch (record.phase()) {
            case SCHEDULED, ANNOUNCED, VOTING -> {
                instance.payloadState(type.createState(instance, def.payloadConfig()));
                instance.force(EventPhase.ANNOUNCED, now);
                live.add(instance);
                if (wouldVote(instance) && instance.startsAt() - now < settings.voteMinTicks) {
                    skip(instance, "restart", now);
                    return;
                }
                announcer.announced(instance.view(now));
                publish(instance, EventSignal.Kind.ANNOUNCED, now);
                if (wouldVote(instance)) {
                    openVote(instance, now);
                }
            }
            case ACTIVE -> {
                live.add(instance);
                try {
                    instance.payloadState(type.loadState(instance, def.payloadConfig(), record.payloadState()));
                } catch (RuntimeException | LinkageError failure) {
                    NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_resume");
                    instance.payloadState(type.createState(instance, def.payloadConfig()));
                    cancel(instance, "resume_failed", now);
                    return;
                }
                ResumeResult resumed;
                try {
                    resumed = type.onResume(contexts.apply(instance), def.payloadConfig(), instance.payloadState());
                } catch (RuntimeException | LinkageError failure) {
                    NeroEventsTelemetry.captureHandledException(failure, "engine", "payload_resume");
                    resumed = ResumeResult.refuse("resume_failed");
                }
                if (!resumed.resumed()) {
                    cancel(instance, resumed.cancelReason() == null ? "resume_failed" : resumed.cancelReason(), now);
                } else {
                    logPhase(instance, "(restart)", EventPhase.ACTIVE, null);
                }
            }
            case RESOLVING, REWARDED, CLEANUP -> {
                try {
                    instance.payloadState(type.loadState(instance, def.payloadConfig(), record.payloadState()));
                } catch (RuntimeException | LinkageError failure) {
                    instance.payloadState(type.createState(instance, def.payloadConfig()));
                }
                runCleanup(instance);
                instance.outcome(EventOutcome.INTERRUPTED);
                instance.force(EventPhase.DONE, now);
                logPhase(instance, record.phase().name(), EventPhase.DONE, "interrupted");
                recordHistory(instance, now);
                recent.add(instance);
            }
            default -> {
            }
        }
    }
}
