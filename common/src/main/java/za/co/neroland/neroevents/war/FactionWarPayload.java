package za.co.neroland.neroevents.war;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.reputation.ReputationApi;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.factions.FactionsBridge;
import za.co.neroland.neroevents.compat.factions.FactionsCompat;
import za.co.neroland.neroevents.config.NeroEventsConfig;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.ServerPayloadContext;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.telemetry.NeroEventsTelemetry;
import za.co.neroland.neroevents.war.WarSides.Side;

/**
 * {@code neroevents:faction_war}: two factions contest N capture beacons placed on a ring around the anchor.
 *
 * <ul>
 *   <li><b>Start:</b> factions with at least {@code warMinMembersOnline} members online are eligible; the top two
 *       by (online members desc, id asc) fight. Fewer than two ⇒ skipped {@code no_factions}. Each objective is
 *       placed only where {@code mayAffectAll} allows a cylinder r 8 and the block is air (the angle is retried
 *       +15° up to six times); none placed ⇒ skipped {@code protected}.</li>
 *   <li><b>Sides</b> are frozen per player at first contact ({@link WarSides}): in both or neither faction ⇒
 *       neutral, and neutrals never capture, score or earn.</li>
 *   <li><b>Scoring</b> ({@link WarScoring}) every 20 ticks; capturers are credited OBJECTIVE 1/n.</li>
 *   <li><b>Resolution:</b> higher score wins ({@code SUCCESS}); a tie is {@code ENDED} with no victor. Credited
 *       participants get reputation with their own faction (only with a real {@code ReputationApi} provider,
 *       capped by {@code rewardReputationMaxDeltaPerEvent}); {@code neroevents:war_resolved} fires per faction.</li>
 * </ul>
 *
 * <p>NeroEvents never writes faction membership: it only reads it. Privacy: player UUIDs live only in the frozen
 * side map and the pending credit queue (memory); nothing player-keyed is saved or logged.
 */
public final class FactionWarPayload implements PayloadType<FactionWarPayload.Config, FactionWarPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "faction_war");

    static final String TELEMETRY_SOURCE = "payload:faction_war";

    /** The objective footprint radius checked with the protection query. */
    public static final int OBJECTIVE_FOOTPRINT_RADIUS = 8;
    /** Extra angle tries per objective (each +15°). */
    public static final int PLACEMENT_RETRIES = 6;
    /** Ticks between objective-text refreshes. */
    public static final int TEXT_TICKS = 100;

    // --- config ----------------------------------------------------------------------------------

    /** The definition's {@code payload} (all optional). */
    public record Config(int objectiveCount, int objectiveSpread, int captureRadius, int captureTicks,
            int capturePoints, int holdPointsPer100Ticks, int winScore, int reputationWin, int reputationLoss)
            implements PayloadConfig {

        public static final Config DEFAULTS = new Config(3, 48, 6, 600, 10, 1, 0, 10, 5);

        static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.intRange(1, 5).optionalFieldOf("objective_count", 3).forGetter(Config::objectiveCount),
                Codec.intRange(16, 96).optionalFieldOf("objective_spread", 48).forGetter(Config::objectiveSpread),
                Codec.intRange(3, 12).optionalFieldOf("capture_radius", 6).forGetter(Config::captureRadius),
                Codec.intRange(200, 6000).optionalFieldOf("capture_ticks", 600).forGetter(Config::captureTicks),
                Codec.intRange(1, 100).optionalFieldOf("capture_points", 10).forGetter(Config::capturePoints),
                Codec.intRange(0, 20).optionalFieldOf("hold_points_per_100_ticks", 1)
                        .forGetter(Config::holdPointsPer100Ticks),
                Codec.intRange(0, 10000).optionalFieldOf("win_score", 0).forGetter(Config::winScore),
                Codec.intRange(0, 100).optionalFieldOf("reputation_win", 10).forGetter(Config::reputationWin),
                Codec.intRange(0, 100).optionalFieldOf("reputation_loss", 5).forGetter(Config::reputationLoss)
        ).apply(inst, Config::new));

        WarScoring.Rules rules() {
            return new WarScoring.Rules(captureTicks, capturePoints, holdPointsPer100Ticks, winScore);
        }
    }

    // --- state -----------------------------------------------------------------------------------

    /** Per-instance state. Saved: sides, objectives (pos, owner, progress, lastCaptureAt), scores, hold fractions. */
    public static final class State implements PayloadState {
        @Nullable Identifier sideA;
        @Nullable Identifier sideB;
        String nameA = "";
        String nameB = "";
        @Nullable WarScoring scoring;
        @Nullable WarSides sides;
        final PendingCredits pending = new PendingCredits();
        int ticks;
        boolean resolved;
        @Nullable EventOutcome outcome;
        String objectiveText = "";
        /** Restored objectives awaiting the resume check. */
        final List<WarScoring.Objective> restored = new ArrayList<>();

        public @Nullable Identifier sideA() {
            return sideA;
        }

        public @Nullable Identifier sideB() {
            return sideB;
        }

        public @Nullable WarScoring scoring() {
            return scoring;
        }

        public @Nullable WarSides sides() {
            return sides;
        }

        public PendingCredits pending() {
            return pending;
        }

        public List<WarScoring.Objective> objectives() {
            WarScoring s = scoring;
            return s == null ? List.copyOf(restored) : s.objectives();
        }

        public String objectiveText() {
            return objectiveText;
        }
    }

    // --- seams -----------------------------------------------------------------------------------

    /** Where reputation goes. Production is Core's {@link ReputationApi}. */
    public interface Reputation {
        boolean hasRealProvider();

        void adjust(UUID player, Identifier faction, int delta);
    }

    static final Reputation CORE_REPUTATION = new Reputation() {
        @Override
        public boolean hasRealProvider() {
            return ReputationApi.hasRealProvider();
        }

        @Override
        public void adjust(UUID player, Identifier faction, int delta) {
            ReputationApi.adjust(player, faction, delta);
        }
    };

    private static volatile @Nullable FactionsBridge bridge;
    /** Answers {@code null} when there is no running server or no bridge. */
    private static volatile Function<PayloadContext, WarWorld> worlds = FactionWarPayload::serverWorld;
    private static volatile BooleanSupplier available = FactionsCompat::available;
    private static volatile IntSupplier minMembers = NeroEventsConfig.WAR_MIN_MEMBERS_ONLINE::get;
    private static volatile IntSupplier reputationCap = NeroEventsConfig.REWARD_REPUTATION_MAX_DELTA_PER_EVENT::get;
    private static volatile Reputation reputation = CORE_REPUTATION;
    /** Lands queued credits in the instance's table (production: only on a running server). */
    private static volatile BiConsumer<PayloadContext, PendingCredits> drainer = FactionWarPayload::serverDrain;

    /** Live states by instance id (status, HUD text, erasure). */
    static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();

    private static @Nullable WarWorld serverWorld(PayloadContext ctx) {
        FactionsBridge b = bridge;
        return ctx instanceof ServerPayloadContext server && b != null ? new ServerWarWorld(server, b) : null;
    }

    private static void serverDrain(PayloadContext ctx, PendingCredits pending) {
        if (ctx instanceof ServerPayloadContext s) {
            pending.drainInto(s);
        }
    }

    /** Lands every queued capture credit now, so a resolve reads the deciding pass's capturers. */
    private static void drain(PayloadContext ctx, State state) {
        drainer.accept(ctx, state.pending);
    }

    /** Tests only: how queued credits land ({@code null} = production). */
    static void useDrainForTests(@Nullable BiConsumer<PayloadContext, PendingCredits> drain) {
        drainer = drain == null ? FactionWarPayload::serverDrain : drain;
    }

    /** Called by {@link WarPayloads#register}. */
    static void bridge(FactionsBridge factions) {
        bridge = factions;
    }

    static void useForTests(@Nullable Function<PayloadContext, WarWorld> world, boolean isAvailable,
            int members, int cap, @Nullable Reputation rep) {
        worlds = world == null ? FactionWarPayload::serverWorld : world;
        available = () -> isAvailable;
        minMembers = () -> members;
        reputationCap = () -> cap;
        reputation = rep == null ? CORE_REPUTATION : rep;
    }

    static void resetForTests() {
        worlds = FactionWarPayload::serverWorld;
        available = FactionsCompat::available;
        minMembers = NeroEventsConfig.WAR_MIN_MEMBERS_ONLINE::get;
        reputationCap = NeroEventsConfig.REWARD_REPUTATION_MAX_DELTA_PER_EVENT::get;
        reputation = CORE_REPUTATION;
        drainer = FactionWarPayload::serverDrain;
        LIVE.clear();
    }

    /** Erasure (the {@code live_instances} eraser): forget the player's frozen side in every live war. */
    public static void forget(UUID player) {
        if (player == null || LIVE.isEmpty()) {
            return;
        }
        for (State state : LIVE.values()) {
            WarSides sides = state.sides;
            if (sides != null) {
                sides.forget(player);
            }
            if (state.pending.snapshot().stream().anyMatch(p -> player.equals(p.player()))) {
                state.pending.clear(); // at most one pass of credit; erasure wins
            }
        }
    }

    /** The objective line ("A 34 · B 21", faction display names and numbers only) for the HUD / console. */
    public static Optional<String> objectiveText(UUID instanceId) {
        State state = instanceId == null ? null : LIVE.get(instanceId);
        return state == null || state.objectiveText.isEmpty() ? Optional.empty() : Optional.of(state.objectiveText);
    }

    // --- pure helpers (tested) -------------------------------------------------------------------

    /** Online members per faction, counted once (O(online × factions)). */
    static Map<Identifier, Integer> onlineCounts(WarWorld world) {
        Set<Identifier> factions = world.factionIds();
        Map<Identifier, Integer> counts = new HashMap<>();
        for (Identifier faction : factions) {
            counts.put(faction, 0);
        }
        if (factions.isEmpty()) {
            return counts;
        }
        for (UUID player : world.onlinePlayers()) {
            for (Identifier faction : world.membershipOf(player)) {
                counts.computeIfPresent(faction, (f, n) -> n + 1);
            }
        }
        return counts;
    }

    /** The two warring factions: eligible = count ≥ {@code min}; top two by (count desc, id asc). */
    static Optional<List<Identifier>> pickSides(Map<Identifier, Integer> counts, int min) {
        List<Map.Entry<Identifier, Integer>> eligible = new ArrayList<>();
        for (Map.Entry<Identifier, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= Math.max(1, min)) {
                eligible.add(entry);
            }
        }
        if (eligible.size() < 2) {
            return Optional.empty();
        }
        eligible.sort(Comparator.<Map.Entry<Identifier, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(e -> e.getKey().toString()));
        return Optional.of(List.of(eligible.get(0).getKey(), eligible.get(1).getKey()));
    }

    /** The protection footprint of one objective at {@code pos} (pos = first air above the ground). */
    static Footprint objectiveFootprint(Identifier dimension, BlockPos pos) {
        int ground = pos.getY() - 1;
        return Footprint.cylinder(dimension, pos, OBJECTIVE_FOOTPRINT_RADIUS, ground - 2, ground + 3);
    }

    private static boolean placeable(PayloadContext ctx, WarWorld world, BlockPos pos) {
        return ctx.protection().mayAffectAll(objectiveFootprint(ctx.dimension(), pos)).allowed() && world.isAir(pos);
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
    public Optional<String> preflight(SchedulerSnapshot snapshot, EventDefinition definition) {
        return snapshot.loadedMods().contains(FactionsCompat.MOD_ID) ? Optional.empty() : Optional.of("no_factions");
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, OBJECTIVE_FOOTPRINT_RADIUS, anchor.getY() - 3, anchor.getY() + 2);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        return new State();
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        if (!available.getAsBoolean()) {
            return StartResult.skip("no_factions");
        }
        WarWorld world = worlds.apply(ctx);
        if (world == null) {
            return StartResult.skip("no_factions");
        }
        Optional<List<Identifier>> picked = pickSides(onlineCounts(world), minMembers.getAsInt());
        if (picked.isEmpty()) {
            return StartResult.skip("no_factions");
        }
        BlockPos anchor = ctx.anchor();
        if (anchor == null) {
            return StartResult.skip("no_anchor");
        }
        Identifier a = picked.get().get(0);
        Identifier b = picked.get().get(1);
        int colourA = WarColours.of(a);
        int colourB = WarColours.of(b);
        int count = config.objectiveCount();
        double offset = ctx.random().nextDouble() * Math.PI * 2.0D;
        List<WarScoring.Objective> placed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double base = Math.PI * 2.0D * i / count + offset;
            for (int attempt = 0; attempt <= PLACEMENT_RETRIES; attempt++) {
                double angle = base + Math.toRadians(15.0D * attempt);
                int x = anchor.getX() + (int) Math.round(config.objectiveSpread() * Math.cos(angle));
                int z = anchor.getZ() + (int) Math.round(config.objectiveSpread() * Math.sin(angle));
                OptionalInt surface = world.surfaceY(x, z);
                if (surface.isEmpty()) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, surface.getAsInt(), z);
                if (!placeable(ctx, world, pos)) {
                    continue;
                }
                if (world.placeBeacon(pos, ctx.instance().id(), i, colourA, colourB, config.captureTicks(),
                        config.captureRadius())) {
                    placed.add(new WarScoring.Objective(i, pos));
                    break;
                }
            }
        }
        if (placed.isEmpty()) {
            return StartResult.skip("protected");
        }
        state.sideA = a;
        state.sideB = b;
        state.nameA = world.displayName(a).orElse(a.toString());
        state.nameB = world.displayName(b).orElse(b.toString());
        state.scoring = new WarScoring(config.rules(), placed);
        state.sides = new WarSides(a, b);
        state.objectiveText = text(state);
        LIVE.put(ctx.instance().id(), state);
        NeroEventsCommon.LOGGER.info("[NeroEvents] Faction war {} started: {} of {} objectives placed.",
                ctx.instance().shortId(), placed.size(), count);
        ctx.note("event.neroevents.faction_war.started", state.nameA, state.nameB);
        return StartResult.ok();
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        drain(ctx, state);
        if (state.resolved) {
            return TickResult.resolved(state.outcome == null ? EventOutcome.ENDED : state.outcome);
        }
        WarScoring scoring = state.scoring;
        WarSides sides = state.sides;
        if (scoring == null || sides == null) {
            return TickResult.proceed();
        }
        state.ticks++;
        if (state.ticks % WarScoring.PASS_TICKS != 0) {
            return TickResult.proceed();
        }
        WarWorld world = worlds.apply(ctx);
        if (world == null) {
            return TickResult.proceed();
        }
        List<List<UUID>> presence = new ArrayList<>();
        for (WarScoring.Objective o : scoring.objectives()) {
            presence.add(world.playersNear(o.pos(), config.captureRadius()));
        }
        List<WarScoring.Capture> captures = scoring.pass(presence, sides, world::membershipOf, state.pending,
                ctx.now());
        drain(ctx, state); // this pass's capturers must be credited before a win resolves the war
        for (WarScoring.Capture capture : captures) {
            for (WarScoring.Objective o : scoring.objectives()) {
                if (o.index() == capture.index()) {
                    world.playCaptured(o.pos());
                }
            }
            ctx.note("event.neroevents.faction_war.captured", capture.index() + 1,
                    capture.side() == Side.A ? state.nameA : state.nameB);
        }
        for (WarScoring.Objective o : scoring.objectives()) {
            world.syncBeacon(o.pos(), sideByte(o.owner()), o.progress(), o.contested(), sideByte(o.progressSide()));
        }
        if (state.ticks % TEXT_TICKS == 0) {
            state.objectiveText = text(state);
        }
        if (scoring.winReached()) {
            return TickResult.resolved(resolveWar(ctx, config, state));
        }
        return TickResult.progress(scoring.ownedByLeader(), scoring.objectives().size());
    }

    private static int sideByte(@Nullable Side side) {
        return side == Side.A ? WarBeaconBlockEntity.SIDE_A
                : side == Side.B ? WarBeaconBlockEntity.SIDE_B : WarBeaconBlockEntity.NONE;
    }

    private static String text(State state) {
        WarScoring scoring = state.scoring;
        int a = scoring == null ? 0 : scoring.score(Side.A);
        int b = scoring == null ? 0 : scoring.score(Side.B);
        return state.nameA + " " + a + " · " + state.nameB + " " + b;
    }

    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return resolveWar(ctx, config, state);
    }

    /** Resolves once: reputation lines and {@code war_resolved}; returns the outcome. */
    static EventOutcome resolveWar(PayloadContext ctx, Config config, State state) {
        drain(ctx, state);
        if (state.resolved) {
            return state.outcome == null ? EventOutcome.ENDED : state.outcome;
        }
        state.resolved = true;
        WarScoring scoring = state.scoring;
        WarSides sides = state.sides;
        Identifier a = state.sideA;
        Identifier b = state.sideB;
        if (scoring == null || sides == null || a == null || b == null) {
            state.outcome = EventOutcome.ENDED;
            return state.outcome;
        }
        Optional<Side> victor = scoring.victor();
        if (victor.isEmpty()) {
            WarChannel.resolved(a, scoring.score(Side.A), false);
            WarChannel.resolved(b, scoring.score(Side.B), false);
            state.outcome = EventOutcome.ENDED;
            return state.outcome;
        }
        Side win = victor.get();
        Side lose = win.opponent();
        Reputation rep = reputation;
        boolean real;
        try {
            real = rep.hasRealProvider();
        } catch (RuntimeException | LinkageError failure) {
            real = false;
        }
        if (real) {
            List<WarScoring.ReputationLine> lines = WarScoring.reputationLines(ctx.contributions().participants(),
                    sides, win, config.reputationWin(), config.reputationLoss(), reputationCap.getAsInt());
            for (WarScoring.ReputationLine line : lines) {
                try {
                    rep.adjust(line.player(), line.faction(), line.delta());
                } catch (RuntimeException | LinkageError failure) {
                    NeroEventsTelemetry.captureHandledException(failure, TELEMETRY_SOURCE, "reputation");
                }
            }
        }
        Identifier winner = win == Side.A ? a : b;
        Identifier loser = lose == Side.A ? a : b;
        WarChannel.resolved(winner, scoring.score(win), true);
        WarChannel.resolved(loser, scoring.score(lose), false);
        state.outcome = EventOutcome.SUCCESS;
        return state.outcome;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        LIVE.remove(ctx.instance().id());
        state.pending.clear();
        WarWorld world;
        try {
            world = worlds.apply(ctx);
        } catch (RuntimeException | LinkageError failure) {
            NeroEventsTelemetry.captureHandledException(failure, TELEMETRY_SOURCE, "cleanup");
            return;
        }
        if (world == null) {
            return;
        }
        for (WarScoring.Objective o : state.objectives()) {
            world.removeBeacon(o.pos());
        }
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        if (state.sideA != null) {
            tag.putString("sideA", state.sideA.toString());
        }
        if (state.sideB != null) {
            tag.putString("sideB", state.sideB.toString());
        }
        ListTag list = new ListTag();
        for (WarScoring.Objective o : state.objectives()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("index", o.index());
            entry.putInt("x", o.pos().getX());
            entry.putInt("y", o.pos().getY());
            entry.putInt("z", o.pos().getZ());
            entry.putByte("owner", (byte) sideByte(o.owner()));
            entry.putInt("progress", o.progress());
            entry.putLong("lastCaptureAt", o.lastCaptureAt());
            list.add(entry);
        }
        tag.put("objectives", list);
        WarScoring scoring = state.scoring;
        if (scoring != null) {
            tag.putInt("scoreA", scoring.score(Side.A));
            tag.putInt("scoreB", scoring.score(Side.B));
            tag.putDouble("holdFractionA", scoring.holdFraction(Side.A));
            tag.putDouble("holdFractionB", scoring.holdFraction(Side.B));
        }
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State();
        Identifier a = Identifier.tryParse(tag.getStringOr("sideA", ""));
        Identifier b = Identifier.tryParse(tag.getStringOr("sideB", ""));
        if (a == null || b == null || tag.getStringOr("sideA", "").isEmpty() || tag.getStringOr("sideB", "").isEmpty()) {
            throw new IllegalStateException("faction_war state has no sides");
        }
        state.sideA = a;
        state.sideB = b;
        ListTag list = tag.getListOrEmpty("objectives");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            WarScoring.Objective o = new WarScoring.Objective(entry.getIntOr("index", i),
                    new BlockPos(entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0)));
            byte owner = entry.getByteOr("owner", WarBeaconBlockEntity.NONE);
            o.restore(owner == WarBeaconBlockEntity.SIDE_A ? Side.A : owner == WarBeaconBlockEntity.SIDE_B ? Side.B : null,
                    entry.getIntOr("progress", 0), entry.getLongOr("lastCaptureAt", -1L));
            state.restored.add(o);
        }
        WarScoring scoring = new WarScoring(config.rules(), state.restored);
        scoring.restoreScores(tag.getIntOr("scoreA", 0), tag.getIntOr("scoreB", 0),
                tag.getDoubleOr("holdFractionA", 0.0D), tag.getDoubleOr("holdFractionB", 0.0D));
        state.scoring = scoring;
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        WarWorld world = available.getAsBoolean() ? worlds.apply(ctx) : null;
        WarScoring loaded = state.scoring;
        Identifier a = state.sideA;
        Identifier b = state.sideB;
        if (world == null || loaded == null || a == null || b == null) {
            return ResumeResult.refuse("no_factions");
        }
        int colourA = WarColours.of(a);
        int colourB = WarColours.of(b);
        List<WarScoring.Objective> kept = new ArrayList<>();
        for (WarScoring.Objective o : loaded.objectives()) {
            if (world.holdsBeacon(o.pos(), ctx.instance().id())) {
                kept.add(o);
            } else if (placeable(ctx, world, o.pos()) && world.placeBeacon(o.pos(), ctx.instance().id(), o.index(),
                    colourA, colourB, config.captureTicks(), config.captureRadius())) {
                kept.add(o);
            }
        }
        if (kept.isEmpty()) {
            return ResumeResult.refuse("objectives_lost");
        }
        WarScoring scoring = new WarScoring(config.rules(), kept);
        scoring.restoreScores(loaded.score(Side.A), loaded.score(Side.B), loaded.holdFraction(Side.A),
                loaded.holdFraction(Side.B));
        state.scoring = scoring;
        state.restored.clear();
        state.sides = new WarSides(a, b);
        state.nameA = world.displayName(a).orElse(a.toString());
        state.nameB = world.displayName(b).orElse(b.toString());
        state.objectiveText = text(state);
        LIVE.put(ctx.instance().id(), state);
        return ResumeResult.ok();
    }
}
