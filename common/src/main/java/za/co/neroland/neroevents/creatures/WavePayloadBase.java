package za.co.neroland.neroevents.creatures;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroevents.compat.creatures.CreaturesCompat;
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
import za.co.neroland.neroevents.participation.Contributions;
import za.co.neroland.neroevents.payload.EventEntityIndex;
import za.co.neroland.neroevents.payload.PendingCredits;
import za.co.neroland.neroevents.protect.Footprint;

/**
 * Shared raid/invasion mechanics ({@code neroevents:pirate_raid}, {@code neroevents:alien_invasion}): a
 * table of waves placed through NeroCreatures' {@code InvasionBudget}.
 *
 * <p>Each wave is scaled by the dimension's online count, reserved as one budget, and placed a few mobs per
 * tick on a ring around the anchor; every position is protection-checked before {@code spawnWithin}, so a
 * mob never appears inside a claim, colony, exclusion zone or spawn radius. A wave ends when every mob is
 * dead or at its timeout; survivors are discarded by {@code closeWave} (they vanish, no loot) and counted
 * as escaped. The event succeeds when players killed at least {@code success_kill_fraction} of everything
 * placed. Raid creatures never break blocks.
 *
 * <p>Contribution is DAMAGE (through {@link EventDamageHooks}) and SURVIVAL at each wave end for players
 * inside the bounds who already have other credit; standing near a raid earns nothing on its own.
 */
public abstract class WavePayloadBase implements PayloadType<WavePayloadBase.Config, WavePayloadBase.State> {

    /** Telemetry source. */
    static final String SOURCE = "payload:waves";

    /** Ticks between the alive sweeps while a wave is fighting. */
    static final int SWEEP_TICKS = 20;

    /** Placement attempts per spawn tick made synchronously at start ({@code spawn_per_tick × 10}). */
    static final int START_ATTEMPT_FACTOR = 10;

    /** Live states by instance id: how {@link EventDamageHooks} finds a wave outside the tick. */
    private static final Map<UUID, State> LIVE = new ConcurrentHashMap<>();

    private static volatile boolean registryUnavailable;

    private final Identifier id;
    private final Function<PayloadContext, CreatureWorld> worlds;
    private final BooleanSupplier available;
    private final Predicate<Identifier> knownTypes;

    /** Production: the façade's availability and the entity-type registry. */
    protected WavePayloadBase(Identifier id, Function<PayloadContext, CreatureWorld> worlds) {
        this(id, worlds, CreaturesCompat::available, WavePayloadBase::registeredEntityType);
    }

    /** Tests: every seam injected. */
    protected WavePayloadBase(Identifier id, Function<PayloadContext, CreatureWorld> worlds, BooleanSupplier available,
            Predicate<Identifier> knownTypes) {
        this.id = id;
        this.worlds = worlds;
        this.available = available;
        this.knownTypes = knownTypes;
    }

    // --- config -------------------------------------------------------------------------------

    /** The {@code payload} object of a raid or invasion definition (snake_case keys). */
    public record Config(List<WaveTable.Wave> waves, double scalePerOnline, int maxPerWave, int spawnRadiusMin,
            int spawnRadiusMax, int spawnPerTick, double successKillFraction, boolean survivalCredit,
            boolean blockDamage) implements PayloadConfig {

        public Config {
            waves = List.copyOf(waves);
        }

        public static final MapCodec<Config> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                WaveTable.sized(WaveTable.Wave.CODEC.listOf(), 1, 8, "waves").fieldOf("waves").forGetter(Config::waves),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("scale_per_online", 0.25D).forGetter(Config::scalePerOnline),
                Codec.intRange(1, 64).optionalFieldOf("max_per_wave", 24).forGetter(Config::maxPerWave),
                Codec.intRange(8, 64).optionalFieldOf("spawn_radius_min", 16).forGetter(Config::spawnRadiusMin),
                Codec.intRange(8, 64).optionalFieldOf("spawn_radius_max", 28).forGetter(Config::spawnRadiusMax),
                Codec.intRange(1, 8).optionalFieldOf("spawn_per_tick", 2).forGetter(Config::spawnPerTick),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("success_kill_fraction", 0.5D)
                        .forGetter(Config::successKillFraction),
                Codec.BOOL.optionalFieldOf("survival_credit", true).forGetter(Config::survivalCredit),
                Codec.BOOL.optionalFieldOf("block_damage", false).forGetter(Config::blockDamage)
        ).apply(inst, Config::new));

        int ringMin() {
            return Math.min(spawnRadiusMin, spawnRadiusMax);
        }

        int ringMax() {
            return Math.max(spawnRadiusMin, spawnRadiusMax);
        }
    }

    // --- state --------------------------------------------------------------------------------

    /** Where the current wave is. */
    public enum WaveState { SPAWNING, FIGHTING, WAITING }

    /**
     * Per-instance state. Saved: wave index and state, wave timers, wave ids, placed/killed/escaped counts.
     * In memory only: the wave handle, the placement queue, the alive set, the pending credits and the set of
     * victims already counted as kills. No player identity is ever saved.
     */
    public static final class State implements PayloadState {
        final UUID instanceId;
        final Identifier dimension;
        int waveIndex;
        WaveState waveState = WaveState.SPAWNING;
        long waveStartedAt;
        long waveEndedAt;
        final List<UUID> waveIds = new ArrayList<>();
        int totalPlaced;
        int killedByPlayers;
        int escaped;

        @Nullable Object handle;
        final Deque<Identifier> toPlace = new ArrayDeque<>();
        int placedThisWave;
        final Set<UUID> alive = new LinkedHashSet<>();
        final PendingCredits pending = new PendingCredits();
        final Set<UUID> counted = new HashSet<>();
        long lastSweepAt;
        boolean endOnNextTick;

        State(UUID instanceId, Identifier dimension) {
            this.instanceId = instanceId;
            this.dimension = dimension;
        }

        public int waveIndex() {
            return waveIndex;
        }

        public WaveState waveState() {
            return waveState;
        }

        public int totalPlaced() {
            return totalPlaced;
        }

        public synchronized int killedByPlayers() {
            return killedByPlayers;
        }

        public int escaped() {
            return escaped;
        }

        public Set<UUID> alive() {
            return Set.copyOf(alive);
        }

        public List<UUID> waveIds() {
            return List.copyOf(waveIds);
        }

        public PendingCredits pending() {
            return pending;
        }
    }

    // --- PayloadType --------------------------------------------------------------------------

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public MapCodec<Config> configCodec() {
        return Config.CODEC;
    }

    @Override
    public List<String> validate(Config config) {
        List<String> warnings = new ArrayList<>();
        if (config.blockDamage()) {
            warnings.add("block_damage is reserved: raids never break blocks in 0.5.0");
        }
        if (config.spawnRadiusMin() > config.spawnRadiusMax()) {
            warnings.add("spawn_radius_min is above spawn_radius_max; the two are swapped");
        }
        Set<Identifier> reported = new HashSet<>();
        for (WaveTable.Wave wave : config.waves()) {
            for (WaveTable.MobEntry mob : wave.mobs()) {
                if (!knownTypes.test(mob.type()) && reported.add(mob.type())) {
                    warnings.add("mob type " + mob.type() + " is not registered; its entries are ignored");
                }
            }
        }
        return warnings;
    }

    @Override
    public Optional<BlockPos> pickTarget(PayloadContext ctx, Config config) {
        return ctx instanceof ServerPayloadContext s ? RaidTargets.pick(s, config.ringMax()) : Optional.empty();
    }

    @Override
    public Footprint footprint(EventDefinition definition, Config config, Identifier dimension, BlockPos anchor) {
        return Footprint.cylinder(dimension, anchor, config.ringMax(), anchor.getY() - 8, anchor.getY() + 16);
    }

    @Override
    public State createState(EventInstance instance, Config config) {
        return new State(instance.id(), instance.dimension());
    }

    @Override
    public StartResult onStart(PayloadContext ctx, Config config, State state) {
        CreatureWorld world = worlds.apply(ctx);
        if (world == null || ctx.anchor() == null) {
            return StartResult.skip("no_room");
        }
        if (!available.getAsBoolean()) {
            return StartResult.skip("no_creatures");
        }
        world.resetIndex();
        LIVE.put(state.instanceId, state);
        state.waveIndex = 0;
        beginWave(ctx, world, config, state);
        // The first wave is placed here, bounded, so a wave with no room is an honest skip.
        placeSome(ctx, world, config, state, config.spawnPerTick() * START_ATTEMPT_FACTOR);
        if (state.placedThisWave == 0) {
            if (state.handle != null) {
                world.closeWave(state.handle);
                state.handle = null;
            }
            forget(state);
            return StartResult.skip("no_room");
        }
        return StartResult.ok();
    }

    @Override
    public TickResult tick(PayloadContext ctx, Config config, State state) {
        CreatureWorld world = worlds.apply(ctx);
        if (world == null) {
            return TickResult.proceed(); // the dimension is not loaded: nothing can move
        }
        LIVE.putIfAbsent(state.instanceId, state);
        world.drain(state.pending);
        long now = ctx.now();
        int waves = config.waves().size();
        if (state.endOnNextTick) {
            state.endOnNextTick = false;
            return endWave(ctx, world, config, state);
        }
        switch (state.waveState) {
            case SPAWNING -> {
                placeSome(ctx, world, config, state, config.spawnPerTick());
                if (state.toPlace.isEmpty()) {
                    if (state.placedThisWave == 0) {
                        return endWave(ctx, world, config, state); // a later wave with no room ends at once
                    }
                    state.waveState = WaveState.FIGHTING;
                    state.lastSweepAt = now;
                    ctx.note("event.neroevents.wave.started", state.waveIndex + 1, waves);
                }
                return TickResult.progress(state.waveIndex, waves);
            }
            case FIGHTING -> {
                if (now - state.lastSweepAt >= SWEEP_TICKS) {
                    state.lastSweepAt = now;
                    state.alive.removeIf(world::isGone);
                    if (state.alive.isEmpty()) {
                        return endWave(ctx, world, config, state);
                    }
                }
                WaveTable.Wave wave = config.waves().get(Math.min(state.waveIndex, waves - 1));
                if (now >= state.waveStartedAt + wave.timeoutTicks()) {
                    state.alive.removeIf(world::isGone);
                    return endWave(ctx, world, config, state);
                }
                return TickResult.progress(state.waveIndex, waves);
            }
            case WAITING -> {
                WaveTable.Wave next = config.waves().get(Math.min(state.waveIndex, waves - 1));
                if (now >= state.waveEndedAt + next.delayTicks()) {
                    beginWave(ctx, world, config, state);
                }
                return TickResult.progress(state.waveIndex, waves);
            }
            default -> {
                return TickResult.proceed();
            }
        }
    }

    /** The raid outlasted the defenders. */
    @Override
    public EventOutcome onTimeout(PayloadContext ctx, Config config, State state) {
        return EventOutcome.FAILURE;
    }

    @Override
    public void onCleanup(PayloadContext ctx, Config config, State state) {
        CreatureWorld world = worlds.apply(ctx);
        if (world != null) {
            if (state.handle != null) {
                world.closeWave(state.handle);
            }
            for (UUID waveId : state.waveIds) {
                world.sweepWave(waveId);
            }
        }
        state.handle = null;
        state.toPlace.clear();
        state.alive.clear();
        state.pending.clear();
        forget(state);
    }

    @Override
    public CompoundTag saveState(State state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("waveIndex", state.waveIndex);
        tag.putString("waveState", state.waveState.name());
        tag.putLong("waveStartedAt", state.waveStartedAt);
        tag.putLong("waveEndedAt", state.waveEndedAt);
        tag.putString("waveIds", joinIds(state.waveIds));
        tag.putInt("totalPlaced", state.totalPlaced);
        tag.putInt("killedByPlayers", state.killedByPlayers());
        tag.putInt("escaped", state.escaped);
        return tag;
    }

    @Override
    public State loadState(EventInstance instance, Config config, CompoundTag tag) {
        State state = new State(instance.id(), instance.dimension());
        state.waveIndex = Math.max(0, tag.getIntOr("waveIndex", 0));
        String phase = tag.getStringOr("waveState", WaveState.SPAWNING.name());
        state.waveState = WaveState.valueOf(phase.toUpperCase(Locale.ROOT)); // malformed ⇒ throws ⇒ resume_failed
        state.waveStartedAt = tag.getLongOr("waveStartedAt", 0L);
        state.waveEndedAt = tag.getLongOr("waveEndedAt", 0L);
        state.waveIds.addAll(splitIds(tag.getStringOr("waveIds", "")));
        state.totalPlaced = Math.max(0, tag.getIntOr("totalPlaced", 0));
        state.killedByPlayers = Math.max(0, tag.getIntOr("killedByPlayers", 0));
        state.escaped = Math.max(0, tag.getIntOr("escaped", 0));
        return state;
    }

    @Override
    public ResumeResult onResume(PayloadContext ctx, Config config, State state) {
        LIVE.put(state.instanceId, state);
        CreatureWorld world = worlds.apply(ctx);
        if (world == null) {
            return ResumeResult.ok();
        }
        world.resetIndex();
        if (state.waveState == WaveState.SPAWNING || state.waveState == WaveState.FIGHTING) {
            // The handle did not survive the restart: what is left of the wave is what is loaded and tagged.
            if (state.waveIds.size() > state.waveIndex) {
                UUID current = state.waveIds.get(state.waveIds.size() - 1);
                for (UUID mob : world.findWave(current)) {
                    EventEntityIndex.bind(mob, state.instanceId);
                    state.alive.add(mob);
                }
            }
            state.waveState = WaveState.FIGHTING;
            state.lastSweepAt = ctx.now();
            state.endOnNextTick = state.alive.isEmpty();
        }
        return ResumeResult.ok();
    }

    // --- wave mechanics -----------------------------------------------------------------------

    private void beginWave(PayloadContext ctx, CreatureWorld world, Config config, State state) {
        WaveTable.Wave wave = config.waves().get(state.waveIndex);
        int online = ctx.world().onlinePlayersPerDimension().getOrDefault(ctx.dimension(), 0);
        List<WaveTable.Planned> plan = WaveTable.scaled(wave, online, config.scalePerOnline(), config.maxPerWave(),
                knownTypes);
        int budget = WaveTable.total(plan);
        state.handle = budget > 0 ? world.reserveWave(budget) : null;
        state.toPlace.clear();
        if (state.handle != null) {
            UUID waveId = world.waveId(state.handle);
            if (waveId != null) {
                state.waveIds.add(waveId);
            }
            state.toPlace.addAll(WaveTable.flatten(plan, ctx.random()));
        }
        state.placedThisWave = 0;
        state.waveStartedAt = ctx.now();
        state.waveState = WaveState.SPAWNING;
    }

    /** Up to {@code maxAttempts} placements; a refused position or mob is dropped, never retried forever. */
    private static void placeSome(PayloadContext ctx, CreatureWorld world, Config config, State state, int maxAttempts) {
        BlockPos anchor = ctx.anchor();
        int attempts = 0;
        while (attempts < maxAttempts && !state.toPlace.isEmpty()) {
            Identifier type = state.toPlace.removeFirst();
            attempts++;
            if (anchor == null || state.handle == null) {
                continue;
            }
            BlockPos pos = WaveSpawner.ringPosition(ctx.world(), ctx.dimension(), anchor, config.ringMin(),
                    config.ringMax(), ctx.random());
            if (pos == null || !ctx.protection().mayAffect(ctx.dimension(), pos).allowed()) {
                continue;
            }
            UUID mob = world.spawnWithin(state.handle, type, pos);
            if (mob != null) {
                EventEntityIndex.bind(mob, state.instanceId);
                state.alive.add(mob);
                state.placedThisWave++;
                state.totalPlaced++;
            }
        }
    }

    private static TickResult endWave(PayloadContext ctx, CreatureWorld world, Config config, State state) {
        state.escaped += state.alive.size();
        if (state.handle != null) {
            world.closeWave(state.handle); // survivors vanish: no loot from an escaped raider
            state.handle = null;
        }
        for (UUID mob : state.alive) {
            EventEntityIndex.unbind(mob);
        }
        state.alive.clear();
        state.toPlace.clear();
        if (config.survivalCredit()) {
            for (UUID player : world.playersInBounds(ctx.bounds())) {
                state.pending.survival(player, state.waveIndex);
            }
            world.drain(state.pending); // in the tick: the last wave's credit must land before the resolve
        }
        state.waveIndex++;
        int waves = config.waves().size();
        if (state.waveIndex >= waves) {
            return TickResult.resolved(succeeded(state.killedByPlayers(), state.totalPlaced,
                    config.successKillFraction()) ? EventOutcome.SUCCESS : EventOutcome.FAILURE);
        }
        state.waveState = WaveState.WAITING;
        state.waveEndedAt = ctx.now();
        return TickResult.progress(state.waveIndex, waves);
    }

    /** SUCCESS iff {@code kills ≥ ceil(fraction × placed)}. */
    static boolean succeeded(int kills, int placed, double fraction) {
        return kills >= (int) Math.ceil(fraction * placed);
    }

    // --- damage -------------------------------------------------------------------------------

    /**
     * A hit on a wave mob, from {@link EventDamageHooks}: a {@code LIVE} lookup, then
     * {@code PendingCredits.damage}, and a kill counted once per victim when attributable. No-op when the
     * instance is not a live wave instance.
     */
    public static void onDamage(UUID instanceId, LivingEntity victim, DamageSource source, float amount) {
        State state = instanceId == null ? null : LIVE.get(instanceId);
        if (state == null || victim == null) {
            return;
        }
        state.pending.damage(source, amount);
        if (victim.isDeadOrDying()) {
            MinecraftServer server = victim.level() instanceof ServerLevel level ? level.getServer() : null;
            recordKill(state, victim.getUUID(), Contributions.attributable(server, source == null ? null
                    : source.getEntity()));
        }
    }

    /** Counts a player kill once per victim; non-attributable deaths count nothing. */
    static boolean recordKill(State state, UUID victim, boolean attributable) {
        if (!attributable || victim == null) {
            return false;
        }
        synchronized (state) {
            if (!state.counted.add(victim)) {
                return false;
            }
            state.killedByPlayers++;
            return true;
        }
    }

    /** The live wave state of an instance (hooks and tests). */
    static Optional<State> live(UUID instanceId) {
        return Optional.ofNullable(instanceId == null ? null : LIVE.get(instanceId));
    }

    private static void forget(State state) {
        LIVE.remove(state.instanceId);
        EventEntityIndex.clearInstance(state.instanceId);
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Whether the entity-type registry knows {@code type}; {@code true} when the registry cannot be read. */
    static boolean registeredEntityType(Identifier type) {
        if (registryUnavailable) {
            return true;
        }
        try {
            return BuiltInRegistries.ENTITY_TYPE.containsKey(type);
        } catch (RuntimeException | LinkageError unavailable) {
            registryUnavailable = true; // a plain JVM (tests): skip the check rather than fail the load
            return true;
        }
    }

    static String joinIds(List<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (UUID id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    static List<UUID> splitIds(String raw) {
        List<UUID> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(UUID.fromString(part.trim())); // malformed ⇒ throws ⇒ resume_failed
            }
        }
        return out;
    }

    /** Test isolation. */
    static void resetForTests() {
        LIVE.clear();
    }
}
