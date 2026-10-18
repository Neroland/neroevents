package za.co.neroland.neroevents.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.event.persist.InstanceRecord;
import za.co.neroland.neroevents.participation.ContributionListener;
import za.co.neroland.neroevents.participation.ContributionPolicy;
import za.co.neroland.neroevents.participation.WeightedContributionTable;
import za.co.neroland.neroevents.protect.Footprint;
import za.co.neroland.neroevents.reward.RewardOutcome;

/**
 * One firing of an event definition: id, definition, dimension, anchor, bounds, phase, timers, the
 * in-memory vote and contribution tables, and the payload's state.
 *
 * <p>What survives a restart is only {@link #toRecord()} (no votes, no contributions, no signal log).
 * Phase changes go through {@link #transition}, which refuses anything the {@link EventPhase} table does
 * not allow. Server thread only.
 */
public final class EventInstance {

    /** How many recent signals are kept per instance for status and debugging. */
    public static final int SIGNAL_LOG = 16;

    private static final Function<EventDefinition, ContributionTable> DEFAULT_TABLES =
            def -> new WeightedContributionTable(ContributionPolicy.fromConfig(def), ContributionListener.NONE);
    private static volatile Function<EventDefinition, ContributionTable> contributionTables = DEFAULT_TABLES;

    private final UUID id;
    private final EventDefinition definition;
    private final Identifier dimension;
    private final @Nullable BlockPos anchor;
    private final Footprint bounds;
    private final EventOrigin origin;
    private final long seed;
    private final long createdAt;
    private final RandomGenerator random;
    private final VoteTally votes = new VoteTally();
    private final ContributionTable contributions;
    private PayloadState payloadState;

    private long startsAt;
    private long endsAt;
    private EventPhase phase;
    private long phaseSince;
    private long terminalAt = -1L;
    private @Nullable EventOutcome outcome;
    private @Nullable String reason;
    private boolean cleanedUp;
    private CompoundTag savedState = new CompoundTag();
    private @Nullable VoteTally.Decision voteDecision;
    private long wallCreatedAt = System.currentTimeMillis();
    private long wallStartedAt;
    private RewardOutcome rewardOutcome = RewardOutcome.NONE;

    private int progressDone;
    private int progressTotal;
    private boolean progressDirty;
    private long lastProgressSignalAt = Long.MIN_VALUE;
    private boolean voteDirty;
    private long lastVoteSignalAt = Long.MIN_VALUE;
    private long lastNoteAt = Long.MIN_VALUE;
    private final Deque<Note> notes = new ArrayDeque<>();
    private final Deque<EventSignal> signals = new ArrayDeque<>();

    /** A queued mid-event broadcast. */
    record Note(String key, Object[] args) {
    }

    public EventInstance(UUID id, EventDefinition definition, Identifier dimension, @Nullable BlockPos anchor,
            Footprint bounds, EventOrigin origin, long seed, long createdAt, EventPhase initialPhase) {
        this.id = id;
        this.definition = definition;
        this.dimension = dimension;
        this.anchor = anchor == null ? null : anchor.immutable();
        this.bounds = bounds;
        this.origin = origin;
        this.seed = seed;
        this.createdAt = createdAt;
        this.random = new Random(seed);
        this.contributions = contributionTables.apply(definition);
        this.phase = initialPhase;
        this.phaseSince = createdAt;
        if (initialPhase.terminal()) {
            this.terminalAt = createdAt;
        }
    }

    /** Swaps the contribution implementation per definition (the interface stays). Init-time and tests only. */
    public static void setContributionTables(Function<EventDefinition, ContributionTable> factory) {
        contributionTables = factory == null ? DEFAULT_TABLES : factory;
    }

    // --- identity -----------------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    /** The first eight characters of the id, used by commands. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public EventDefinition definition() {
        return definition;
    }

    public Identifier dimension() {
        return dimension;
    }

    public @Nullable BlockPos anchor() {
        return anchor;
    }

    public Footprint bounds() {
        return bounds;
    }

    public EventOrigin origin() {
        return origin;
    }

    public long seed() {
        return seed;
    }

    public long createdAt() {
        return createdAt;
    }

    public RandomGenerator random() {
        return random;
    }

    public VoteTally votes() {
        return votes;
    }

    public ContributionTable contributions() {
        return contributions;
    }

    public PayloadState payloadState() {
        return payloadState;
    }

    void payloadState(PayloadState state) {
        this.payloadState = state;
    }

    // --- timers and phase ---------------------------------------------------------------------

    public long startsAt() {
        return startsAt;
    }

    public long endsAt() {
        return endsAt;
    }

    void timers(long startsAt, long endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public EventPhase phase() {
        return phase;
    }

    public long phaseSince() {
        return phaseSince;
    }

    public long terminalAt() {
        return terminalAt;
    }

    public @Nullable EventOutcome outcome() {
        return outcome;
    }

    void outcome(@Nullable EventOutcome value) {
        this.outcome = value;
    }

    public @Nullable String reason() {
        return reason;
    }

    void reason(@Nullable String value) {
        this.reason = value;
    }

    /** The vote decision cached when voting closed early; {@code null} until then. */
    public @Nullable VoteTally.Decision voteDecision() {
        return voteDecision;
    }

    void voteDecision(@Nullable VoteTally.Decision decision) {
        this.voteDecision = decision;
    }

    /** Epoch ms the instance was created (history only; not persisted). */
    public long wallCreatedAt() {
        return wallCreatedAt;
    }

    /** Epoch ms the instance went ACTIVE, or 0 if it never did (history only; not persisted). */
    public long wallStartedAt() {
        return wallStartedAt;
    }

    void wallStartedAt(long epochMillis) {
        this.wallStartedAt = epochMillis;
    }

    void wallCreatedAt(long epochMillis) {
        this.wallCreatedAt = epochMillis;
    }

    /** What the resolve paid ({@link RewardOutcome#NONE} until then). In memory only. */
    public RewardOutcome rewardOutcome() {
        return rewardOutcome;
    }

    void rewardOutcome(RewardOutcome value) {
        this.rewardOutcome = value == null ? RewardOutcome.NONE : value;
    }

    boolean cleanedUp() {
        return cleanedUp;
    }

    void markCleanedUp() {
        cleanedUp = true;
    }

    /**
     * Moves to {@code to}. Throws {@code IllegalStateException("illegal event phase transition X → Y")} for
     * anything the phase table does not allow.
     */
    public void transition(EventPhase to, long now) {
        if (!EventPhase.canTransition(phase, to)) {
            throw new IllegalStateException("illegal event phase transition " + phase + " → " + to);
        }
        set(to, now);
    }

    /** Sets the phase without the table check: restore and forced cancellation only. */
    void force(EventPhase to, long now) {
        set(to, now);
    }

    private void set(EventPhase to, long now) {
        phase = to;
        phaseSince = now;
        if (to.terminal()) {
            terminalAt = now;
        }
    }

    // --- progress, votes, notes, signals ------------------------------------------------------

    void recordProgress(int done, int total) {
        if (done != progressDone || total != progressTotal) {
            progressDone = done;
            progressTotal = total;
            progressDirty = true;
        }
    }

    /** Whether a PROGRESS signal is due now (and marks it sent). */
    boolean takeProgressSignal(long now, int coalesceTicks) {
        if (!progressDirty || (lastProgressSignalAt != Long.MIN_VALUE && now - lastProgressSignalAt < coalesceTicks)) {
            return false;
        }
        progressDirty = false;
        lastProgressSignalAt = now;
        return true;
    }

    void markVoteChanged() {
        voteDirty = true;
    }

    /** Whether a VOTE_CHANGED signal is due now (and marks it sent). */
    boolean takeVoteSignal(long now, int coalesceTicks) {
        if (!voteDirty || (lastVoteSignalAt != Long.MIN_VALUE && now - lastVoteSignalAt < coalesceTicks)) {
            return false;
        }
        voteDirty = false;
        lastVoteSignalAt = now;
        return true;
    }

    void queueNote(String key, Object... args) {
        if (key != null && notes.size() < 8) {
            notes.add(new Note(key, args == null ? new Object[0] : args.clone()));
        }
    }

    /** The next note if the rate limit allows one now; older queued notes beyond it are dropped. */
    Optional<Note> takeNote(long now, int minGapTicks) {
        if (notes.isEmpty()) {
            return Optional.empty();
        }
        if (lastNoteAt != Long.MIN_VALUE && now - lastNoteAt < minGapTicks) {
            notes.clear();
            return Optional.empty();
        }
        lastNoteAt = now;
        Note note = notes.poll();
        notes.clear();
        return Optional.of(note);
    }

    void logSignal(EventSignal signal) {
        if (signals.size() >= SIGNAL_LOG) {
            signals.removeFirst();
        }
        signals.addLast(signal);
    }

    /** The last {@value #SIGNAL_LOG} signals for this instance. */
    public List<EventSignal> signals() {
        return new ArrayList<>(signals);
    }

    // --- projections --------------------------------------------------------------------------

    /** The public view; {@code now} is the engine time it is taken at. */
    public EventView view(long now) {
        boolean pub = definition.locationPublic();
        return new EventView(id, shortId(), definition.id(), definition.type(), definition.category(),
                definition.severity(), definition.display().title(), definition.display().whatToDo(), dimension,
                phase, origin, startsAt, endsAt, now, contributions.participantCount(), votes.open(), votes.yes(),
                votes.no(), pub, pub ? anchor : null, progressDone, progressTotal, outcome, reason);
    }

    void savedState(CompoundTag tag) {
        this.savedState = tag == null ? new CompoundTag() : tag;
    }

    CompoundTag savedState() {
        return savedState;
    }

    /** The resumable record: timers, phase, anchor and the last saved payload state; no player data. */
    public InstanceRecord toRecord() {
        return new InstanceRecord(id, definition.id(), definition.type(), dimension, Optional.ofNullable(anchor),
                origin, seed, createdAt, startsAt, endsAt, phase, phaseSince, contributions.participantCount(),
                savedState.copy());
    }
}
