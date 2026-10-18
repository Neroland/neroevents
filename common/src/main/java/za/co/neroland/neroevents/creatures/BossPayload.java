package za.co.neroland.neroevents.creatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge;
import za.co.neroland.neroevents.compat.creatures.CreaturesBridge.SummonedBoss;
import za.co.neroland.neroevents.compat.creatures.CreaturesCompat;
import za.co.neroland.neroevents.config.NeroEventsConfig;
import za.co.neroland.neroevents.event.EventDefinition;
import za.co.neroland.neroevents.event.EventInstance;
import za.co.neroland.neroevents.event.EventOutcome;
import za.co.neroland.neroevents.event.PayloadConfig;
import za.co.neroland.neroevents.event.PayloadContext;
import za.co.neroland.neroevents.event.PayloadState;
import za.co.neroland.neroevents.event.PayloadType;
import za.co.neroland.neroevents.event.ResumeResult;
import za.co.neroland.neroevents.event.RewardSpec.RewardLine;
import za.co.neroland.neroevents.event.SchedulerSnapshot;
import za.co.neroland.neroevents.event.StartResult;
import za.co.neroland.neroevents.event.TickResult;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;

/**
 * {@code neroevents:boss}: a NeroCreatures world boss summoned through the bridge.
 *
 * <p>NeroCreatures runs the fight and pays the boss's own drops; NeroEvents decides who contributed (DAMAGE
 * only, through {@link EventDamageHooks}) and pays its currency/XP lines. Identity is checked every 20 ticks,
 * or at once after a {@code nerocreatures:boss_pressure} falling crossing in the dimension:
 * <ul>
 *   <li>defeated ⇒ {@code SUCCESS} (the resolver turns it into {@code ESCAPED} when nobody attributable
 *       fought);</li>
 *   <li>not loaded and its fight no longer running for {@value #MISSING_TICKS} ticks ⇒ {@code ESCAPED};</li>
 *   <li>timeout ⇒ {@code ESCAPED}, and the boss is discarded at cleanup.</li>
 * </ul>
 * Boss definitions carry no item lines (they would be paid twice); one is warned about once per definition.
 */
public final class BossPayload implements PayloadType<BossPayload.Config, BossPayload.State> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "boss");

    /** The default boss. */
    public static final Identifier CINDER_TYRANT = Identifier.fromNamespaceAndPath("nerocreatures", "cinder_tyrant");

    /** How long a boss may be unloaded with no running fight before it counts as gone. */
    public static final int MISSING_TICKS = 1200;

    static final int CHECK_TICKS = 20;
    static final String SOURCE = "payload:boss";

    private static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();
    private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();

    private final Function<PayloadContext, CreatureWorld> worlds;
    private final BooleanSupplier available;
    private final LongSupplier valuePerParticipant;

    public BossPayload(CreaturesBridge bridge) {
        this(ServerCreatureWorld.factory(bridge, SOURCE), CreaturesCompat::available,
                NeroEventsConfig.BOSS_CREATURES_REWARD_VALUE_PER_PARTICIPANT::get);
    }

    /** Tests: every seam injected. */
    public BossPayload(Function<PayloadContext, CreatureWorld> worlds, BooleanSupplier available,
            LongSupplier valuePerParticipant) {
        this.worlds = worlds;
        this.available = available;
        this.valuePerParticipant = valuePerParticipant;
    }

    /** {@code {"boss_id": id, "arena_radius": 8..48}}. */
    public record Config(Identifier bossId, int arenaRadius) implements PayloadConfig {

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Identifier.CODEC.optionalFieldOf("boss_id", CINDER_TYRANT).forGetter(Config::bossId),
                Codec.intRange(8, 48).optionalFieldOf("arena_radius", 24).forGetter(Config::arenaRadius)
        ).apply(inst, Config::new));
    }

    /** Saved: the boss entity UUID, the fight id and the missing-since timer. Never a player. */
    public static final class State implements PayloadState {
        final UUID instanceId;
        final Identifier dimension;
        @Nullable UUID bossUuid;
        @Nullable UUID fightId;
        long missingSince = -1L;

        final PendingCredits pending = new PendingCredits();
        volatile boolean checkNow;
        long lastCheckAt = Long.MIN_VALUE;

        State(UUID instanceId, Identifier dimension) {
            this.instanceId = instanceId;
            this.dimension = dimension;
        }

        public @Nullable UUID bossUuid() {
            return bossUuid;
        }

        public @Nullable UUID fightId() {
            return fightId;
        }

        public long missingSince() {
            return missingSince;
        }

        public PendingCredits pending() {
            return pending;
        }
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return Config.CODEC;
    }

    /** Eligibility: NeroCreatures loaded, and the daily budget can carry the boss's own drops per participant. */
    @Override
    public Optional<String> preflight(SchedulerSnapshot snapshot, EventDefinition definition) {
        if (!snapshot.loadedMods().contains(CreaturesCompat.MOD_ID)) {
            return Optional.of("no_creatures");
        }
        long perParticipant = Math.max(0L, valuePerParticipant.getAsLong());
        long expected = Math.max(1, snapshot.expectedParticipants());
        long needed = perParticipant > Long.MAX_VALUE / expected ? Long.MAX_VALUE : perParticipant * expected;
        return snapshot.remainingDailyBudget() < needed ? Optional.of("budget") : Optional.empty();
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, 16, anchor.getY() - 4, anchor.getY() + 24);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        EventDefinition def = instance.definition();
        if (WARNED.add(def.id())) {
            for (String warning : itemLineWarnings(def)) {
                NeroEventsCommon.LOGGER.warn("[NeroEvents] {}: {}", def.id(), warning);
            }
        }
        return new State(instance.id(), instance.dimension());
    }

    /** The item-line rule: NeroCreatures already drops the boss's items, so item lines would pay twice. */
    public static List<String> itemLineWarnings(EventDefinition definition) {
        List<String> out = new ArrayList<>();
        boolean items = definition.rewards().lines().stream().anyMatch(l -> l.kind() == RewardLine.Kind.ITEM)
                || definition.rewards().topBonus().stream().anyMatch(l -> l.kind() == RewardLine.Kind.ITEM);
        if (items) {
            out.add("item lines on a boss payload are paid twice - NeroCreatures already drops boss items");
        }
        return out;
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        CreatureWorld world = worlds.apply(ctx);
        BlockPos anchor = ctx.anchor();
        if (world == null || anchor == null) {
            return StartResult.skip("no_room");
        }
        if (!available.getAsBoolean()) {
            return StartResult.skip("no_creatures");
        }
        if (!world.bossIds().contains(config.bossId())) {
            return StartResult.skip("boss_unknown");
        }
        world.resetIndex();
        Optional<SummonedBoss> summoned = world.summon(anchor, config.bossId());
        if (summoned.isEmpty() || summoned.get().entityUuid() == null) {
            return StartResult.skip("boss_refused");
        }
        state.bossUuid = summoned.get().entityUuid();
        state.fightId = summoned.get().fightId();
        state.missingSince = -1L;
        state.lastCheckAt = ctx.now();
        EventEntityIndex.bind(state.bossUuid, state.instanceId);
        LIVE.put(state.instanceId, state);
        ctx.progress(0, 1);
        return StartResult.ok();
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        CreatureWorld world = worlds.apply(ctx);
        if (world == null) {
            return TickResult.proceed();
        }
        LIVE.putIfAbsent(state.instanceId, state);
        world.drain(state.pending);
        long now = ctx.now();
        boolean due = state.checkNow || state.lastCheckAt == Long.MIN_VALUE || now - state.lastCheckAt >= CHECK_TICKS;
        if (!due || state.bossUuid == null) {
            return TickResult.proceed();
        }
        state.checkNow = false;
        state.lastCheckAt = now;
        Optional<Boolean> defeated = world.isDefeated(state.bossUuid);
        if (defeated.isPresent() && defeated.get()) {
            return TickResult.resolved(EventOutcome.SUCCESS);
        }
        if (defeated.isEmpty() && (state.fightId == null || !world.isFightActive(state.fightId))) {
            if (state.missingSince < 0L) {
                state.missingSince = now;
            }
            if (now - state.missingSince >= MISSING_TICKS) {
                return TickResult.resolved(EventOutcome.ESCAPED); // unloaded or discarded for a minute: gone
            }
        } else {
            state.missingSince = -1L;
            EventEntityIndex.bind(state.bossUuid, state.instanceId); // loaded again (after a restart)
        }
        return TickResult.progress(0, 1);
    }

    /** Time ran out: the boss got away (no {@code boss_defeated}, no {@code boss_contributor}). */
    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return EventOutcome.ESCAPED;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        if (state.bossUuid != null && ctx.instance().outcome() != EventOutcome.SUCCESS) {
            CreatureWorld world = worlds.apply(ctx);
            if (world != null) {
                world.discard(state.bossUuid);
            }
        }
        if (state.bossUuid != null) {
            EventEntityIndex.unbind(state.bossUuid);
        }
        EventEntityIndex.clearInstance(state.instanceId);
        state.pending.clear();
        LIVE.remove(state.instanceId);
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        if (state.bossUuid != null) {
            tag.putString("bossUuid", state.bossUuid.toString());
        }
        if (state.fightId != null) {
            tag.putString("fightId", state.fightId.toString());
        }
        tag.putLong("missingSince", state.missingSince);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State(instance.id(), instance.dimension());
        state.bossUuid = uuid(tag.getStringOr("bossUuid", ""));
        state.fightId = uuid(tag.getStringOr("fightId", ""));
        state.missingSince = tag.getLongOr("missingSince", -1L);
        return state;
    }

    /** Always resumes: a boss that vanished during downtime resolves ESCAPED through the missing rule. */
    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        LIVE.put(state.instanceId, state);
        CreatureWorld world = worlds.apply(ctx);
        if (world != null) {
            world.resetIndex();
        }
        if (state.bossUuid != null) {
            EventEntityIndex.bind(state.bossUuid, state.instanceId);
        }
        state.lastCheckAt = ctx.now();
        return ResumeResult.ok();
    }

    private static @Nullable UUID uuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim()); // malformed ⇒ resume_failed
    }

    // --- hooks --------------------------------------------------------------------------------

    /**
     * A hit on the boss, from {@link EventDamageHooks}: a {@code LIVE} lookup, then
     * {@code PendingCredits.damage}. No-op when the instance is not a live boss instance.
     */
    public static void onDamage(UUID instanceId, LivingEntity victim, DamageSource source, float amount) {
        State state = instanceId == null ? null : LIVE.get(instanceId);
        if (state == null) {
            return;
        }
        state.pending.damage(source, amount);
    }

    /**
     * The {@code nerocreatures:boss_pressure} falling crossing for {@code dimension} (registered by
     * {@code CreaturesCompat}): every live boss state in that dimension runs its identity check on the next
     * tick instead of waiting for the 20-tick cadence.
     */
    public static void requestCheck(Identifier dimension) {
        if (dimension == null) {
            return;
        }
        for (State state : LIVE.values()) {
            if (dimension.equals(state.dimension)) {
                state.checkNow = true;
            }
        }
    }

    /** The live boss state of an instance (tests and diagnostics). */
    static Optional<State> live(UUID instanceId) {
        return Optional.ofNullable(instanceId == null ? null : LIVE.get(instanceId));
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
        WARNED.clear();
    }
}
