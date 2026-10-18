package za.co.neroland.neroevents.event.persist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroevents.NeroEventsCommon;
import za.co.neroland.neroevents.protect.ExclusionZones.ExclusionZone;

/**
 * The event engine's saved data, {@code neroevents:engine} in the overworld: live instance records,
 * per-definition cooldowns, operator reweights, resolved counts, the scheduler clock and command-added
 * exclusion zones.
 *
 * <p><b>World data only</b> — no player UUID anywhere (instance ids are random engine-minted UUIDs;
 * entity UUIDs sit inside payload state). It is therefore not a player-keyed store and has no store
 * eraser, but it is still read through Core's {@link SavedDataRecovery} so a corrupt file falls back to
 * the last-known-good backup.
 */
public final class EventEngineState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroEventsCommon.MOD_ID, "engine");
    public static final int SCHEMA_VERSION = 1;

    public static final Codec<EventEngineState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA_VERSION).forGetter(s -> SCHEMA_VERSION),
            InstanceRecord.CODEC.listOf().optionalFieldOf("instances", List.of()).forGetter(s -> s.instances),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldown_until", Map.of())
                    .forGetter(s -> s.cooldownUntil),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("reweights", Map.of())
                    .forGetter(s -> s.reweights),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("resolved_count", Map.of())
                    .forGetter(s -> s.resolvedCount),
            Codec.LONG.optionalFieldOf("total_resolved", 0L).forGetter(s -> s.totalResolved),
            Codec.LONG.optionalFieldOf("last_fired_at", Long.MIN_VALUE).forGetter(s -> s.lastFiredAt),
            Codec.LONG.optionalFieldOf("next_roll_at", 0L).forGetter(s -> s.nextRollAt),
            ExclusionZone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(s -> s.zones)
    ).apply(inst, (schema, instances, cooldowns, reweights, resolved, total, lastFired, nextRoll, zones) -> {
        EventEngineState state = new EventEngineState();
        state.instances.addAll(instances);
        state.cooldownUntil.putAll(cooldowns);
        state.reweights.putAll(reweights);
        state.resolvedCount.putAll(resolved);
        state.totalResolved = total;
        state.lastFiredAt = lastFired;
        state.nextRollAt = nextRoll;
        state.zones.addAll(zones);
        return state;
    }));

    public static final SavedDataType<EventEngineState> TYPE =
            new SavedDataType<>(ID, EventEngineState::new, CODEC, null);

    private final List<InstanceRecord> instances = new ArrayList<>();
    private final Map<Identifier, Long> cooldownUntil = new HashMap<>();
    private final Map<Identifier, Integer> reweights = new HashMap<>();
    private final Map<Identifier, Long> resolvedCount = new HashMap<>();
    private long totalResolved;
    private long lastFiredAt = Long.MIN_VALUE;
    private long nextRollAt;
    private final List<ExclusionZone> zones = new ArrayList<>();

    public EventEngineState() {
    }

    /** The server's engine state, through Core's recovery-aware loader. */
    public static EventEngineState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, EventEngineState::new, ID.toString());
    }

    // --- instances ------------------------------------------------------------------------------

    public List<InstanceRecord> instances() {
        return List.copyOf(instances);
    }

    public void setInstances(List<InstanceRecord> records) {
        instances.clear();
        instances.addAll(records);
        setDirty();
    }

    // --- cooldowns, reweights, counts -------------------------------------------------------------

    public Map<Identifier, Long> cooldowns() {
        return Map.copyOf(cooldownUntil);
    }

    public long cooldownUntil(Identifier definition) {
        return cooldownUntil.getOrDefault(definition, Long.MIN_VALUE);
    }

    public void setCooldownUntil(Identifier definition, long until) {
        cooldownUntil.put(definition, until);
        setDirty();
    }

    public Map<Identifier, Integer> reweights() {
        return Map.copyOf(reweights);
    }

    public Optional<Integer> reweight(Identifier definition) {
        return Optional.ofNullable(reweights.get(definition));
    }

    public void setReweight(Identifier definition, int weight) {
        reweights.put(definition, weight);
        setDirty();
    }

    public boolean clearReweight(Identifier definition) {
        boolean removed = reweights.remove(definition) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public long resolvedCount(Identifier definition) {
        return resolvedCount.getOrDefault(definition, 0L);
    }

    public long totalResolved() {
        return totalResolved;
    }

    /** Counts one resolve for the definition and the world; returns the new world total. */
    public long recordResolved(Identifier definition) {
        resolvedCount.merge(definition, 1L, Long::sum);
        totalResolved++;
        setDirty();
        return totalResolved;
    }

    public long lastFiredAt() {
        return lastFiredAt;
    }

    public void setLastFiredAt(long at) {
        lastFiredAt = at;
        setDirty();
    }

    public long nextRollAt() {
        return nextRollAt;
    }

    public void setNextRollAt(long at) {
        nextRollAt = at;
        setDirty();
    }

    // --- zones --------------------------------------------------------------------------------------

    public List<ExclusionZone> zones() {
        return List.copyOf(zones);
    }

    public Optional<ExclusionZone> zone(String name) {
        return zones.stream().filter(z -> z.name().equals(name)).findFirst();
    }

    public boolean addZone(ExclusionZone zone) {
        if (zone(zone.name()).isPresent()) {
            return false;
        }
        zones.add(zone);
        setDirty();
        return true;
    }

    public boolean removeZone(String name) {
        boolean removed = zones.removeIf(z -> z.name().equals(name));
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
